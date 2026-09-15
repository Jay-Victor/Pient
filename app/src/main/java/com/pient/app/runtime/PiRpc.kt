package com.pient.app.runtime

import android.util.Log
import com.pient.app.AppCtx
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "PiRpc"

/** pi 通道（guest 里的 `pi --mode rpc`）生命周期 */
sealed class PiRpcState {
    data object Stopped : PiRpcState()
    data object Starting : PiRpcState()
    data class Running(val provider: String, val model: String) : PiRpcState()
    data class Failed(val message: String) : PiRpcState()
}

/**
 * **App ↔ pi 的通道**（2026-09-14）：直接和 **Ubuntu(guest) 里的 pi 进程**讲官方 RPC。
 *
 * 为什么是 RPC 而不是接 SDK：pi 跑在 PRoot 的 Ubuntu 里（自带 node 环境、自带工具链），
 * App 不需要再嵌一层 node；官方 `--mode rpc` 就是为「外部客户端驱动」设计的
 * （JSONL over stdin/stdout，见 pi `docs/rpc.md`）。
 *
 * 进程链：`libpient_shell.so`（原生启动器）→ PRoot → guest `/bin/bash -c "exec pi --mode rpc …"`，
 * 与终端页同一条链（同一个 rootfs、同一个 HOME=/root、同一个 /workspace），
 * 所以**会话、配置、工具都跟终端里手敲 pi 完全一致**。
 *
 * 分帧：一条 JSON = 一行，**只用 LF（`\n`）**，容忍行尾 `\r`；不用会额外吞 U+2028/U+2029 的
 * 通用行读取器（那两个字符在 JSON 字符串里合法）。
 */
object PiRpc {

    private val seq = AtomicInteger(0)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    private val _state = MutableStateFlow<PiRpcState>(PiRpcState.Stopped)
    val state: StateFlow<PiRpcState> = _state.asStateFlow()

    /** agent 事件流（`message_update` / `tool_execution_*` / `agent_settled` …），语义由上层解释 */
    private val _events = MutableSharedFlow<JSONObject>(
        extraBufferCapacity = 2048,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<JSONObject> = _events.asSharedFlow()

    private var process: Process? = null
    private var writer: Writer? = null
    private val writeLock = Any()

    /** stderr 尾巴（诊断用：pi 启动失败/崩溃时给用户看的那几行） */
    private val stderrTail = ArrayDeque<String>()
    fun stderrText(): String = synchronized(stderrTail) { stderrTail.joinToString("\n") }

    private fun noteStderr(line: String) {
        synchronized(stderrTail) {
            stderrTail.addLast(line)
            while (stderrTail.size > 40) stderrTail.removeFirst()
        }
    }

    // ─────────────────────── 就绪判断 / 启停 ───────────────────────

    /** 通道可用 = 在跑，或**能起来**（rootfs + 预置 pi 都在）。 */
    fun usable(): Boolean {
        process?.let { if (it.isAlive) return true }
        val ctx = AppCtx.get() ?: return false
        return PiRuntime.rootfsReady(ctx) && PiRuntime.piReady(ctx)
    }

    /**
     * 起（或复用）通道。**provider/model 变了就重启** —— 默认模型是 `--provider/--model`
     * 传进去的（比让 pi 自己选更可控；也避免「上一次选的服务商又生效」）。
     *
     * **必须同步**：并发调用会各起一个 pi 进程（双份事件流）。
     */
    @Synchronized
    fun start(provider: String, model: String): Boolean {
        val key = "$provider/$model"
        process?.let { p ->
            if (p.isAlive && (_state.value as? PiRpcState.Running)?.let { "$it.provider/${it.model}" } == key) {
                return true
            }
        }
        stop()
        val ctx = AppCtx.get() ?: return false
        if (!PiRuntime.rootfsReady(ctx) || !PiRuntime.piReady(ctx)) {
            _state.value = PiRpcState.Failed("Ubuntu/pi 未就绪")
            return false
        }
        PiRuntime.prepareTerminal(ctx)   // DNS / 启动器 / 执行环境（与终端页同源）
        val shell = PiRuntime.shellPath(ctx)
        if (!shell.isFile) {
            _state.value = PiRpcState.Failed("缺少终端启动器：${shell.absolutePath}")
            return false
        }
        val cmd = buildString {
            append("exec pi --mode rpc")
            if (provider.isNotBlank() && model.isNotBlank()) {
                append(" --provider ").append(provider).append(" --model ").append(provider).append('/').append(model)
            }
        }
        _state.value = PiRpcState.Starting
        return try {
            val proc = ProcessBuilder(shell.absolutePath, "-c", cmd)
                .directory(PiRuntime.appDir(ctx))
                .redirectErrorStream(false)
                .also { it.environment().putAll(PiRuntime.environment(ctx)) }
                .start()
            process = proc
            writer = OutputStreamWriter(proc.outputStream, StandardCharsets.UTF_8)
            synchronized(stderrTail) { stderrTail.clear() }
            Thread({ readStdout(proc) }, "pient-pi-rpc-out").apply { isDaemon = true }.start()
            Thread({ readStderr(proc) }, "pient-pi-rpc-err").apply { isDaemon = true }.start()
            _state.value = PiRpcState.Running(provider, model)
            Log.i(TAG, "pi 通道已启动：$cmd")
            true
        } catch (t: Throwable) {
            process = null
            writer = null
            _state.value = PiRpcState.Failed(t.message ?: t.javaClass.simpleName)
            Log.w(TAG, "pi 通道启动失败：${t.message}")
            false
        }
    }

    @Synchronized
    fun stop() {
        val proc = process ?: return
        process = null
        writer = null
        pending.values.forEach { it.cancel() }
        pending.clear()
        runCatching {
            proc.outputStream.close()
            proc.destroy()
            if (!proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) proc.destroyForcibly()
        }
        _state.value = PiRpcState.Stopped
        Log.i(TAG, "pi 通道已停止")
    }

    // ─────────────────────── 命令 ───────────────────────

    /** 发一条命令并等它的 `response` 回包（超时/无 id 命令返回 null） */
    suspend fun send(command: JSONObject, awaitMs: Long = 20_000): JSONObject? {
        val w = writer ?: return null
        val hasId = command.has("id")
        if (!hasId) command.put("id", "pient-${seq.incrementAndGet()}")
        val id = command.optString("id")
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        return try {
            val line = command.toString() + "\n"
            synchronized(writeLock) {
                w.write(line)
                w.flush()
            }
            withTimeoutOrNull(awaitMs) { deferred.await() }
        } catch (t: Throwable) {
            Log.w(TAG, "命令发送失败：${t.message}")
            null
        } finally {
            pending.remove(id)
        }
    }

    /** 一轮对话（会话上下文由 pi 自己维护：这里只送新消息） */
    suspend fun prompt(message: String, images: List<JSONObject> = emptyList()): JSONObject? {
        val cmd = JSONObject().put("type", "prompt").put("message", message)
        if (images.isNotEmpty()) {
            cmd.put("images", org.json.JSONArray().also { arr -> images.forEach { arr.put(it) } })
        }
        return send(cmd, awaitMs = 60_000)
    }

    suspend fun abort(): JSONObject? = send(JSONObject().put("type", "abort"), awaitMs = 10_000)

    // ─────────────── 会话 / 树 / 分叉（2026-09-14 会话映射用）───────────────
    // pi 官方 RPC 提供：get_tree / get_entries / get_fork_messages / fork / clone /
    // new_session / switch_session / set_session_name / get_session_stats / get_commands。
    // **唯一缺的是"移动活跃叶"**（TUI 的 /tree）——那走我们预置的扩展命令 `/pient-nav`
    // （见 PiRuntime.installPiExtension 与 assets/pient-pi-extension.ts），用 prompt 触发。

    /** data 段（命令成功时的负载）；失败/超时返回 null */
    private fun dataOf(resp: JSONObject?): JSONObject? =
        if (resp != null && resp.optBoolean("success", false)) resp.optJSONObject("data") else null

    /** 会话树（节点 = entry，含 id/parentId；leafId = 当前活跃叶） */
    suspend fun getTree(): JSONObject? = dataOf(send(JSONObject().put("type", "get_tree")))

    /** 当前活跃路径上的消息（`get_messages`；切分支/切会话后用来重建界面消息流） */
    suspend fun getMessages(): JSONObject? = dataOf(send(JSONObject().put("type", "get_messages")))

    /** 全部条目（append 序；传 since = 增量拉取，pi 用它当游标） */
    suspend fun getEntries(since: String? = null): JSONObject? {
        val cmd = JSONObject().put("type", "get_entries")
        if (!since.isNullOrBlank()) cmd.put("since", since)
        return dataOf(send(cmd))
    }

    /** 可 fork 的用户消息（会话外分支的候选点） */
    suspend fun getForkMessages(): JSONObject? = dataOf(send(JSONObject().put("type", "get_fork_messages")))

    /** **会话外分支**：从活跃分支上的某条用户消息开一个新会话文件（pi 口径 = /fork） */
    suspend fun fork(entryId: String): JSONObject? =
        dataOf(send(JSONObject().put("type", "fork").put("entryId", entryId)))

    /** **会话外分支（另一种）**：把当前活跃分支复制成新会话文件（pi 口径 = /clone） */
    suspend fun clone(): JSONObject? = dataOf(send(JSONObject().put("type", "clone")))

    /** 会话统计（含 sessionFile / sessionId / contextUsage —— 会话映射靠它拿文件名） */
    suspend fun getSessionStats(): JSONObject? = dataOf(send(JSONObject().put("type", "get_session_stats")))

    /** 新建会话（可指定父会话文件 = 从某个会话派生） */
    suspend fun newSession(parentSession: String? = null): JSONObject? {
        val cmd = JSONObject().put("type", "new_session")
        if (!parentSession.isNullOrBlank()) cmd.put("parentSession", parentSession)
        return dataOf(send(cmd))
    }

    /** 切到某个会话文件（Pient 切会话时用） */
    suspend fun switchSession(sessionPath: String): JSONObject? =
        dataOf(send(JSONObject().put("type", "switch_session").put("sessionPath", sessionPath)))

    /** 给会话起名（把 Pient 的会话标题同步给 pi） */
    suspend fun setSessionName(name: String): JSONObject? =
        dataOf(send(JSONObject().put("type", "set_session_name").put("name", name)))

    /** 可用命令（扩展命令也在里面 —— 含我们的 /pient-nav） */
    suspend fun getCommands(): JSONObject? = dataOf(send(JSONObject().put("type", "get_commands")))

    /**
     * **会话内分支**：把活跃叶移动到 entryId（= TUI 的 /tree 选择）。
     * 走扩展命令 `/pient-nav <id> [--summarize] [--label x] [--instructions y]`
     * —— pi 的 RPC 文档写明扩展命令「available for invocation via prompt」。
     */
    suspend fun navigate(
        entryId: String,
        summarize: Boolean = false,
        label: String? = null,
        instructions: String? = null,
    ): JSONObject? {
        val sb = StringBuilder("/pient-nav ").append(entryId)
        if (summarize) sb.append(" --summarize")
        if (!label.isNullOrBlank()) sb.append(" --label ").append(label.replace(' ', '_'))
        if (!instructions.isNullOrBlank()) sb.append(" --instructions ").append(instructions)
        return send(JSONObject().put("type", "prompt").put("message", sb.toString()), awaitMs = 120_000)
    }
    suspend fun newSession(): JSONObject? = send(JSONObject().put("type", "new_session"))
    suspend fun getState(): JSONObject? = send(JSONObject().put("type", "get_state"))

    // ─────────────────────── 读线程 ───────────────────────

    /**
     * 读 stdout：**按字节攒够一行、整行用 UTF-8 解码**。
     *
     * 曾经的写法是逐字节 `b.toChar()`——那等于把 UTF-8 当 Latin-1 解：ASCII 看不出问题，
     * 一旦模型回中文就变成「ãåæ¯æµè¯åå¤」（实测踩过）。UTF-8 的多字节序列不可能含 0x0A，
     * 所以「按 LF 切行、整行解码」天然不会切坏字符。
     */
    private fun readStdout(proc: Process) {
        val input = proc.inputStream
        val buf = ByteArray(8192)
        val line = java.io.ByteArrayOutputStream()
        try {
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                var start = 0
                for (i in 0 until n) {
                    if (buf[i].toInt() != 0x0A) continue   // LF = 唯一的行界
                    line.write(buf, start, i - start)
                    start = i + 1
                    var bytes = line.toByteArray()
                    line.reset()
                    // 行尾 CR（CRLF）丢弃
                    if (bytes.isNotEmpty() && bytes.last() == 0x0D.toByte()) {
                        bytes = bytes.copyOf(bytes.size - 1)
                    }
                    val text = String(bytes, StandardCharsets.UTF_8)
                    if (text.isNotBlank()) dispatch(text)
                }
                line.write(buf, start, n - start)
            }
            if (line.size() > 0) {
                dispatch(String(line.toByteArray(), StandardCharsets.UTF_8).trimEnd('\r'))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "读 stdout 结束：${t.message}")
        } finally {
            _events.tryEmit(JSONObject().put("type", "channel_closed"))
        }
    }

    private fun readStderr(proc: Process) {
        runCatching {
            proc.errorStream.bufferedReader(StandardCharsets.UTF_8).forEachLine {
                noteStderr(it)
                Log.w(TAG, "stderr: $it")
            }
        }
    }

    private fun dispatch(text: String) {
        val obj = runCatching { JSONObject(text) }.getOrElse {
            Log.w(TAG, "非 JSON 行：${text.take(200)}")
            return
        }
        if (obj.optString("type") == "response") {
            val id = obj.optString("id")
            pending.remove(id)?.complete(obj)
            return
        }
        _events.tryEmit(obj)
    }
}
