package com.pient.app.runtime

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * 宿主回桥（loopback HTTP）—— 给 pi 扩展里的**系统命令工具**用。
 *
 * 为什么需要它：扩展工具在 pi 宿主的 Node 进程里执行，而 Android 系统命令要的权限通道
 * （Shizuku 的 `IShizukuService.newProcess`、`su`）**只能在 Java/binder 侧调用**——
 * shell 链路到不了（实测结论）。所以：扩展工具 POST 一条命令到本机回桥，App 侧执行完把
 * 输出回给扩展，扩展再作为工具结果交给模型。
 *
 * 绑定 `127.0.0.1` + 随机端口；端口经环境变量 `PIENT_EXEC_ENDPOINT` 交给宿主与扩展
 * （[PiRuntime.environment]）。**只有本应用的宿主拿得到这个端口**，且回桥只执行不外泄。
 *
 * 通道选择（按能力就高）：
 * 1. `su` 可用 → `su -c <命令>`（root 身份，Root 档）；
 * 2. Shizuku 已授权 → **待接入**（需要对 binder 直接 transact，参考 Operit `DebuggerShellExecutor`）；
 * 3. 都没有 → 返回 `no-privilege` 错误（标准权限下不给系统命令，与「Android shell 需
 *    Shizuku / Root 解锁」的口径一致）。
 */
object PiExecServer {

    private const val TAG = "PiHost"
    private const val MAX_OUTPUT = 200_000

    @Volatile
    private var server: ServerSocket? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    var port: Int = 0
        private set

    /** 启动回桥（幂等）；返回可用端口，0 = 启动失败 */
    @Synchronized
    fun ensureStarted(context: Context): Int {
        appContext = context.applicationContext
        server?.let { return port }
        return runCatching {
            val ss = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
            server = ss
            port = ss.localPort
            Thread({ acceptLoop(ss) }, "pient-exec-bridge").apply { isDaemon = true }.start()
            Log.i(TAG, "宿主回桥已启动：127.0.0.1:$port")
            port
        }.getOrElse {
            Log.w(TAG, "宿主回桥启动失败：${it.message}")
            0
        }
    }

    /** 交给宿主的端点（启动失败返回 null，扩展侧会看到「通道未就绪」） */
    fun endpoint(context: Context): String? = ensureStarted(context).takeIf { it > 0 }?.let { "http://127.0.0.1:$it" }

    private fun acceptLoop(ss: ServerSocket) {
        while (true) {
            val socket = runCatching { ss.accept() }.getOrNull() ?: break
            Thread({ runCatching { handle(socket) }.onFailure { Log.w(TAG, "回桥请求失败：${it.message}") } })
                .apply { isDaemon = true }
                .start()
        }
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            val input = s.getInputStream().bufferedReader(Charsets.UTF_8)
            val requestLine = input.readLine().orEmpty()
            var contentLength = 0
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0 && line.substring(0, idx).equals("Content-Length", ignoreCase = true)) {
                    contentLength = line.substring(idx + 1).trim().toIntOrNull() ?: 0
                }
            }
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = input.read(buf, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                String(buf, 0, read)
            } else ""

            val json = when {
                requestLine.startsWith("POST /exec") -> execute(body)
                else -> JSONObject().put("error", "not-found").put("message", "只支持 POST /exec")
            }
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            val header = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            s.getOutputStream().apply {
                write(header.toByteArray(Charsets.UTF_8))
                write(bytes)
                flush()
            }
        }
    }

    /** 执行一条系统命令（按可用通道）；返回响应 JSON */
    private fun execute(body: String): JSONObject {
        val req = runCatching { JSONObject(body) }.getOrElse {
            return JSONObject().put("error", "bad-request").put("message", "请求体不是合法 JSON")
        }
        val command = req.optString("command").trim()
        if (command.isEmpty()) {
            return JSONObject().put("error", "bad-request").put("message", "缺少 command")
        }
        val timeoutMs = req.optLong("timeoutMs", 120_000)
        val cwd = req.optString("cwd").takeIf { it.isNotBlank() }

        // 通道 1：su（Root 档）
        if (hasSu()) return runSu(command, cwd, timeoutMs)

        // 通道 2：Shizuku 用户服务（选型 B：ADB 级 uid 2000，无需 Root）
        val ctx = appContext
        if (ctx != null && runCatching { com.pient.app.data.ShizukuGateway.authorized() }.getOrDefault(false)) {
            if (ShizukuShellChannel.ensureBound(ctx)) {
                val res = ShizukuShellChannel.exec(command, cwd, timeoutMs)
                if (res != null) return res
                return JSONObject()
                    .put("error", "shizuku-call-failed")
                    .put("message", ShizukuShellChannel.lastError ?: "用户服务调用失败")
            }
            return JSONObject()
                .put("error", "shizuku-unavailable")
                .put("message", "Shizuku 已授权，但用户服务不可用：" +
                    (ShizukuShellChannel.lastError ?: "未知原因"))
        }

        // 通道 3：标准权限（应用身份跑 /system/bin/sh）—— 对齐 Operit `StandardShellExecutor`：
        // `isAvailable()` 恒真、无需任何授权。能跑 shell/toybox 基础命令；`am`/`pm`/`dumpsys` 这类
        // 多数会被系统拒绝 —— 报错**原样回传**（不假装成功、也不吞掉），模型据此知道是权限问题。
        return runStandard(command, cwd, timeoutMs)
    }

    /** 标准档：应用身份（u0_aXXX）的 /system/bin/sh —— 永远可用，能力受限但真实 */
    private fun runStandard(command: String, cwd: String?, timeoutMs: Long): JSONObject {
        val full = if (cwd.isNullOrBlank()) command else "cd ${shellQuote(cwd)} && $command"
        return runCatching {
            val p = ProcessBuilder("/system/bin/sh", "-c", full).redirectErrorStream(true).start()
            val text = StringBuilder()
            val reader = Thread {
                runCatching { p.inputStream.bufferedReader().forEachLine { text.append(it).append('\n') } }
            }
            reader.isDaemon = true
            reader.start()
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                runCatching { p.destroy() }
                return@runCatching JSONObject().put("error", "timeout")
                    .put("channel", "standard")
                    .put("message", "命令超时（${timeoutMs}ms）")
            }
            reader.join(1000)
            val out = text.toString().let { if (it.length > MAX_OUTPUT) it.takeLast(MAX_OUTPUT) else it }
            JSONObject()
                .put("channel", "standard")
                .put("code", p.exitValue())
                .put("output", out.trimEnd())
        }.getOrElse {
            JSONObject().put("error", "exec-failed").put("channel", "standard")
                .put("message", it.message ?: it.javaClass.simpleName)
        }
    }

    private fun hasSu(): Boolean = SU_PATHS.any { runCatching { File(it).exists() }.getOrDefault(false) } ||
        runCatching {
            ProcessBuilder("sh", "-c", "command -v su").redirectErrorStream(true).start()
                .let { p -> p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0 }
        }.getOrDefault(false)

    private fun runSu(command: String, cwd: String?, timeoutMs: Long): JSONObject {
        val full = if (cwd.isNullOrBlank()) command else "cd ${shellQuote(cwd)} && $command"
        return runCatching {
            val p = ProcessBuilder("su", "-c", full).redirectErrorStream(true).start()
            val text = StringBuilder()
            val reader = Thread {
                runCatching { p.inputStream.bufferedReader().forEachLine { text.append(it).append('\n') } }
            }
            reader.isDaemon = true
            reader.start()
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                runCatching { p.destroy() }
                return@runCatching JSONObject().put("error", "timeout").put("message", "命令超时（${timeoutMs}ms）")
            }
            reader.join(1000)
            val out = text.toString().let { if (it.length > MAX_OUTPUT) it.takeLast(MAX_OUTPUT) else it }
            JSONObject()
                .put("channel", "su")
                .put("code", p.exitValue())
                .put("output", out.trimEnd())
        }.getOrElse {
            JSONObject().put("error", "exec-failed").put("message", it.message ?: it.javaClass.simpleName)
        }
    }

    /** 单引号包裹（su -c 走 sh 解析，路径里有空格/中文时要防拆） */
    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private val SU_PATHS = arrayOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/debug_ramdisk/su", "/data/adb/ksu/bin/su", "/data/adb/ap/bin/su",
    )
}
