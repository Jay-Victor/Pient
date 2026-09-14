package com.pient.app.runtime

import android.content.Context
import android.util.Log
import org.json.JSONObject
import com.pient.app.tools.system.SystemPart
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

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
 * **这一层只是传输**：收到请求后交给系统命令层执行 —— 通道选择（`su` → Shizuku 用户服务 →
 * 标准应用身份）的唯一实现在 `tools/system/SystemPart.kt`，回桥不再自己判断通道。
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

    /** 执行一条系统命令（**转交系统命令层**：通道选择与执行在 tools/system/SystemPart.kt） */
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

        // 执行交系统命令层（唯一实现：su → Shizuku 用户服务 → 标准；见 tools/system/SystemPart.kt）
        return SystemPart.execute(appContext, command, cwd, timeoutMs)
    }
}

