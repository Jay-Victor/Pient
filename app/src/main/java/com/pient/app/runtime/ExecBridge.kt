package com.pient.app.runtime

import android.content.Context
import com.pient.app.data.PientLog
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

/**
 * **Android shell 回桥**。
 *
 * 为什么需要一条回桥：AI 的主体（pi）跑在 **Ubuntu(PRoot) 的 guest 里**，
 * 而 Shizuku 的 ADB 级通道**只能在 Java 侧调用**（`IShizukuService.newProcess` 是这个 binder 的方法，
 * shell 链路根本到不了）。所以「guest 里的 pi 工具 → Android 命令」只有一条可行路径：
 * guest → `127.0.0.1:<port>`（PRoot 与应用共享网络命名空间）→ 应用（Java）执行 → 回包。
 *
 * 形态：应用里一个只绑 **loopback** 的最小 HTTP 服务，
 *   `POST /exec`  body `{"token":"…","cmd":"…","timeoutMs":30000}` → `{ok,backend,exit,stdout,stderr,note}`
 *   `GET  /ping` → `{ok:true,backend:"…"}`
 * 端点与令牌写在 **guest 可见的位置**：`<rootfs>/root/.pi/agent/.pient-exec-bridge.json`
 * （写进 rootfs 里才行 —— `files/pient-rt/` 是 guest 根目录的**上一层**，guest 看不见它），
 * 由 `assets/pient-pi-extension.ts` 的 `android_shell` 工具读取。
 *
 * 令牌：随机 32 位十六进制，随端点一起写在应用私有目录里的文件里（其它应用读不到）；
 * 端口是临时的、只在 loopback 上监听 —— 双保险。
 */
object ExecBridge {
    private const val TAG = "PientExecBridge"

    /** 端点文件（guest 侧路径 `/root/.pi/agent/.pient-exec-bridge.json`） */
    fun endpointFile(context: Context): File =
        File(PiRuntime.rootfsDir(context), "root/.pi/agent/.pient-exec-bridge.json")

    @Volatile
    private var server: ServerSocket? = null

    @Volatile
    private var token: String = ""

    @Volatile
    private var started = false

    fun isRunning(): Boolean = started && server?.isClosed == false

    /** 幂等启动（应用启动时调一次；rootfs 未就绪时端点文件先不写，等就绪后再写） */
    @Synchronized
    fun ensureStarted(context: Context) {
        if (started) {
            writeEndpointFile(context)
            return
        }
        val ss = runCatching {
            ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        }.getOrElse {
            PientLog.w(TAG, "回桥启动失败（无法绑定 loopback）：${it.message}")
            return
        }
        token = randomToken()
        server = ss
        started = true
        Thread({ acceptLoop(context.applicationContext, ss) }, "pient-exec-bridge").apply {
            isDaemon = true
        }.start()
        PientLog.i(TAG, "Android shell 回桥已启动：127.0.0.1:${ss.localPort}")
        writeEndpointFile(context)
    }

    /**
     * 把端点写进 guest 可见的位置。**每次请求都重写一遍**（很便宜）——
     * 档位/授权变化后扩展侧下一次调用就能看到新的 backend，不需要重启 pi。
     */
    fun writeEndpointFile(context: Context) {
        val port = server?.localPort ?: return
        val dir = endpointFile(context).parentFile
        if (dir == null || !dir.exists()) return          // rootfs 未就绪：等就绪后的调用再写
        val json = JSONObject()
            .put("port", port)
            .put("token", token)
            .put("backend", AndroidShell.backend(context).id)
            .put("updatedAt", System.currentTimeMillis())
        runCatching {
            val tmp = File(dir, endpointFile(context).name + ".tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(endpointFile(context))) {
                endpointFile(context).writeText(json.toString())
                tmp.delete()
            }
        }.onFailure { PientLog.w(TAG, "端点文件写入失败：${it.message}") }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ───────────────────────── HTTP ─────────────────────────

    private fun acceptLoop(context: Context, ss: ServerSocket) {
        while (!ss.isClosed) {
            val socket = runCatching { ss.accept() }.getOrNull() ?: continue
            Thread({ handle(context, socket) }, "pient-exec-req").apply { isDaemon = true }.start()
        }
    }

    private fun handle(context: Context, socket: Socket) {
        socket.use { s ->
            s.soTimeout = 5_000
            runCatching {
                val input = s.getInputStream()
                val head = readHead(input) ?: return
                val (requestLine, headers) = head
                val parts = requestLine.split(' ')
                val method = parts.getOrNull(0).orEmpty()
                val path = parts.getOrNull(1).orEmpty()
                val len = headers["content-length"]?.trim()?.toIntOrNull() ?: 0
                val body = if (len > 0) String(readExactly(input, len), Charsets.UTF_8) else ""

                when {
                    method == "GET" && path.startsWith("/ping") ->
                        respond(s.getOutputStream(), 200, JSONObject().put("ok", true).put("backend", AndroidShell.backend(context).id))

                    method == "POST" && path.startsWith("/exec") -> {
                        val json = runCatching { JSONObject(body) }.getOrNull()
                        val reqToken = json?.optString("token").orEmpty()
                        if (reqToken != token) {
                            respond(s.getOutputStream(), 403, JSONObject().put("ok", false).put("note", "token 不匹配"))
                            return
                        }
                        val cmd = json?.optString("cmd").orEmpty()
                        val timeout = json?.optLong("timeoutMs", 30_000) ?: 30_000
                        if (cmd.isBlank()) {
                            respond(s.getOutputStream(), 400, JSONObject().put("ok", false).put("note", "cmd 为空"))
                            return
                        }
                        val r = AndroidShell.execBlocking(context, cmd, timeout.coerceIn(1_000, 600_000))
                        // 顺便刷新端点文件（backend 可能刚变化）
                        writeEndpointFile(context)
                        respond(
                            s.getOutputStream(),
                            200,
                            JSONObject()
                                .put("ok", r.ok)
                                .put("backend", r.backend.id)
                                .put("exit", r.exit)
                                .put("stdout", r.stdout)
                                .put("stderr", r.stderr)
                                .put("timeout", r.timeout)
                                .put("note", r.note),
                        )
                    }

                    else -> respond(s.getOutputStream(), 404, JSONObject().put("ok", false).put("note", "unknown path"))
                }
            }.onFailure { PientLog.w(TAG, "回桥请求处理失败：${it.message}") }
        }
    }

    /** 读请求行 + 头（到空行为止） */
    private fun readHead(input: InputStream): Pair<String, Map<String, String>>? {
        val line1 = readLine(input) ?: return null
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        return line1 to headers
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var b = input.read()
        if (b < 0) return null
        while (b >= 0 && b != '\n'.code) {
            sb.append(b.toChar())
            b = input.read()
        }
        return sb.toString().trimEnd('\r')
    }

    private fun readExactly(input: InputStream, len: Int): ByteArray {
        val out = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = input.read(out, read, len - read)
            if (n < 0) break
            read += n
        }
        return if (read == len) out else out.copyOf(read)
    }

    private fun respond(out: OutputStream, code: Int, json: JSONObject) {
        val body = json.toString().toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 ").append(code).append(" OK\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)
        runCatching {
            out.write(head)
            out.write(body)
            out.flush()
        }
    }
}
