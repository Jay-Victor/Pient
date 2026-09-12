package com.pient.app.runtime

import android.content.Context
import android.util.Log
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
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "PiHost"

/** pi 宿主进程的生命周期状态 */
sealed class PiHostState {
    /** 宿主未启动 */
    data object Stopped : PiHostState()

    /** 运行时未部署到设备（缺 Node / RPC 入口 / rg / fd） */
    data class MissingRuntime(val summary: String) : PiHostState()

    /** 宿主在跑，但没有任何服务商/模型配置（agent 循环不可用） */
    data object MissingModel : PiHostState()

    data object Starting : PiHostState()

    data class Running(val sessionId: String?) : PiHostState()

    /** 启动失败或进程异常退出 */
    data class Failed(val message: String) : PiHostState()
}

/**
 * pi 官方 RPC 客户端（JSONL over stdin/stdout）。
 *
 * 协议口径照 pi 官方 `packages/coding-agent/docs/rpc.md`：
 * - **命令**：一行一个 JSON 对象写 stdin（`{"id":...,"type":...,...}`）；
 * - **响应**：`{"id":...,"type":"response","command":...,"success":bool,"data":{...}}`；
 * - **事件**：其余 JSON 行即 agent 事件（`tool_execution_start/update/end`、`message_update` 等）；
 * - **分帧：只用 LF（`\n`）**，容忍行尾 `\r`（CRLF 输入），**不得**用 `readline` 一类
 *   会把 U+2028/U+2029 也当换行的通用行读取器（那两个字符在 JSON 字符串里是合法内容）。
 *
 * 进程形态：内嵌 Node 子进程（宿主）加载 pi 包自带的 `rpc-entry.js`。开发期运行时由
 * 部署脚本摊在 `files/pient-rt/`，见 [PiRuntime]。
 */
class PiRpcClient(private val context: Context) {

    private val seq = AtomicInteger(0)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    private val _state = MutableStateFlow<PiHostState>(PiHostState.Stopped)
    val state: StateFlow<PiHostState> = _state.asStateFlow()

    /** agent 事件流（工具调用、消息增量…）。RPC 客户端只负责分帧与解析，语义由上层解释。 */
    private val _events = MutableSharedFlow<JSONObject>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<JSONObject> = _events.asSharedFlow()

    private var process: Process? = null
    private val writeLock = Any()
    private var writer: Writer? = null

    /** 最近一次 get_state 的 data 段（宿主状态快照） */
    private val _lastState = MutableStateFlow<JSONObject?>(null)
    val lastState: StateFlow<JSONObject?> = _lastState.asStateFlow()

    /**
     * 启动宿主；幂等——已在运行时直接返回 true。
     * **必须同步**：并发调用（例如 UI 与诊断探针同时拉起）会各起一个 Node 进程，
     * 表现为「两个宿主、双份 get_state 回包」（实测踩过）。
     *
     * [provider]/[model] 非空时以 `--provider/--model`（`provider/model` 形态）指定默认模型；
     * 不传时宿主以 `model=unknown` 启动（agent 循环起不来，只有 RPC `bash` 这类直通命令可用）。
     */
    @Synchronized
    fun start(provider: String? = null, model: String? = null): Boolean {
        process?.let { if (it.isAlive) return true }
        val readiness = PiRuntime.check(context)
        if (!readiness.ready) {
            _state.value = PiHostState.MissingRuntime(readiness.summary)
            Log.w(TAG, "运行时未就绪：${readiness.summary}")
            return false
        }
        _state.value = PiHostState.Starting

        // HOME/TMPDIR 目录要先存在，否则 pi 写配置/会话时失败
        listOf(PiRuntime.homeDir(context), PiRuntime.tmpDir(context)).forEach { it.mkdirs() }

        val args = mutableListOf("--mode", "rpc")
        // 会话持久化（2026-09-12）：不再用 --no-session（那是一次性内存会话，重启即丢、
        // 且与桌面 pi 无法互操作）。会话落到 HOME 下的 ~/.pi/agent/sessions（与桌面 pi/pi-web
        // 同构），为「Pient 会话 ↔ pi 会话文件」映射打底（映射本身是下一步）。
        args += listOf("--session-dir", PiRuntime.sessionsDir(context).absolutePath)
        if (!provider.isNullOrBlank() && !model.isNullOrBlank()) {
            args += listOf("--provider", provider, "--model", "$provider/$model")
        }

        val command = mutableListOf(PiRuntime.nodeBinary(context).absolutePath)
        command += PiRuntime.rpcEntry(context).absolutePath
        command += args

        return try {
            val pb = ProcessBuilder(command)
                .directory(PiRuntime.appDir(context))
                .redirectErrorStream(false)
            PiRuntime.environment(context).forEach { (k, v) -> pb.environment()[k] = v }
            val proc = pb.start()
            process = proc
            writer = OutputStreamWriter(proc.outputStream, StandardCharsets.UTF_8)
            Log.i(TAG, "宿主启动 node=${PiRuntime.nodeBinary(context)} model=${model ?: "unknown"}")

            Thread({ readStdout(proc) }, "pi-host-stdout").apply { isDaemon = true }.start()
            Thread({ readStderr(proc) }, "pi-host-stderr").apply { isDaemon = true }.start()
            _state.value = PiHostState.Running(null)
            true
        } catch (t: Throwable) {
            process = null
            _state.value = PiHostState.Failed(t.message ?: t.javaClass.simpleName)
            Log.w(TAG, "宿主启动失败：${t.message}")
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
        _state.value = PiHostState.Stopped
        Log.i(TAG, "宿主已停止")
    }

    /**
     * 发一条命令并等它的响应。返回 null = 超时或宿主不可用（调用方自行降级）。
     * 事件继续从 [events] 流里出来，与命令响应互不阻塞。
     */
    suspend fun request(
        type: String,
        payload: JSONObject = JSONObject(),
        timeoutMs: Long = 20_000,
    ): JSONObject? {
        val w = writer ?: return null
        val id = "req-${seq.incrementAndGet()}"
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred

        val cmd = JSONObject()
        payload.keys().forEach { k -> cmd.put(k, payload.get(k)) }
        cmd.put("type", type)
        cmd.put("id", id)

        val written = runCatching {
            synchronized(writeLock) {
                w.write(cmd.toString())
                w.write("\n")
                w.flush()
            }
            true
        }.getOrElse { t ->
            pending.remove(id)
            Log.w(TAG, "命令写入失败 $type：${t.message}")
            false
        }
        if (!written) return null

        val response = withTimeoutOrNull(timeoutMs) { deferred.await() }
        if (response == null) pending.remove(id)
        if (response != null && !response.optBoolean("success", true)) {
            Log.w(TAG, "命令失败 $type：${response.optString("error")}")
        }
        return response
    }

    /**
     * 回扩展的 UI 请求（`extension_ui_request` → `extension_ui_response`）。
     * select/input/editor 用 [value]；confirm 用 [confirmed]；取消传 cancelled = true
     * （扩展侧收到 `undefined`/`false`）。写入失败只记日志——宿主那边有超时兜底。
     */
    fun sendUiResponse(
        id: String,
        value: String? = null,
        confirmed: Boolean? = null,
        cancelled: Boolean = false,
    ) {
        val w = writer ?: return
        val resp = JSONObject().put("type", "extension_ui_response").put("id", id)
        when {
            cancelled -> resp.put("cancelled", true)
            confirmed != null -> resp.put("confirmed", confirmed)
            value != null -> resp.put("value", value)
            else -> resp.put("cancelled", true)
        }
        runCatching {
            synchronized(writeLock) {
                w.write(resp.toString())
                w.write("\n")
                w.flush()
            }
        }.onFailure { Log.w(TAG, "扩展 UI 响应写入失败：${it.message}") }
    }

    /** 取宿主状态快照（get_state 的 data 段） */
    suspend fun refreshState(): JSONObject? {
        val response = request("get_state") ?: return null
        val data = response.optJSONObject("data") ?: return null
        _lastState.value = data
        _state.value = PiHostState.Running(data.optString("sessionId").ifEmpty { null })
        val model = data.optJSONObject("model")?.optString("id").orEmpty().ifEmpty { "unknown" }
        Log.i(
            TAG,
            "get_state ok: session=${data.optString("sessionId")} model=$model " +
                "thinking=${data.optString("thinkingLevel")} messages=${data.optInt("messageCount")}",
        )
        return data
    }

    // ---- 内部：读流与分帧 ----

    private fun readStdout(proc: Process) {
        val line = ByteArrayOutputStream(256)
        val buf = ByteArray(16 * 1024)
        try {
            val input = proc.inputStream
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                for (i in 0 until n) {
                    val b = buf[i]
                    if (b == LF) {
                        emit(line.toByteArray())
                        line.reset()
                    } else {
                        line.write(b.toInt())
                    }
                }
            }
            emit(line.toByteArray()) // 收尾：最后一行可能没有换行
        } catch (t: Throwable) {
            Log.w(TAG, "stdout 读取中断：${t.message}")
        } finally {
            onProcessGone(proc)
        }
    }

    private fun readStderr(proc: Process) {
        try {
            proc.errorStream.bufferedReader(StandardCharsets.UTF_8).forEachLine { line ->
                if (line.isNotBlank()) Log.w(TAG, "stderr: $line")
            }
        } catch (_: Throwable) {
            // 进程退出时流关闭属正常
        }
    }

    private fun emit(raw: ByteArray) {
        if (raw.isEmpty()) return
        val len = if (raw.last() == CR) raw.size - 1 else raw.size
        if (len <= 0) return
        val text = String(raw, 0, len, StandardCharsets.UTF_8).trim()
        if (text.isEmpty()) return
        val json = runCatching { JSONObject(text) }.getOrElse {
            Log.w(TAG, "非法 JSON 行（已忽略）：${text.take(200)}")
            return
        }
        if (json.optString("type") == "response") {
            val id = json.optString("id").ifEmpty { null }
            val waiter = id?.let { pending.remove(it) }
            if (waiter != null) {
                waiter.complete(json)
                return
            }
        }
        if (!_events.tryEmit(json)) Log.w(TAG, "事件缓冲已满，丢弃一条：${json.optString("type")}")
    }

    private fun onProcessGone(proc: Process) {
        if (process !== proc) return
        process = null
        writer = null
        pending.values.forEach { it.cancel() }
        pending.clear()
        val code = runCatching { proc.waitFor() }.getOrDefault(-1)
        if (code == 0) {
            _state.value = PiHostState.Stopped
            Log.i(TAG, "宿主退出（exit=0）")
        } else {
            _state.value = PiHostState.Failed("宿主进程退出（exit=$code）")
            Log.w(TAG, "宿主异常退出 exit=$code")
        }
    }

    private companion object {
        const val LF: Byte = 0x0A
        const val CR: Byte = 0x0D
    }
}
