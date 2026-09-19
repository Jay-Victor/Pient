package com.pient.app.runtime

import com.pient.app.data.i18n.L
import com.pient.app.data.PientLog
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
 * **App ↔ pi 的通道**：直接和 **Ubuntu(guest) 里的 pi 进程**讲官方 RPC。
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
        // 同时进应用日志：内存尾巴只服务 UI 的 40 行提示，落盘这份才留得住
        //（pi 在 RPC 模式下不写日志文件，stderr 就是它唯一的报错出口）
        PientLog.w("PiStderr", line)
    }

    // ─────────────────────── 就绪判断 / 启停 ───────────────────────

    /** 通道**此刻真的在跑**（可用 ≠ 在跑：usable 还含「能起来」）。用于「要不要为换工作区重启它」。 */
    fun running(): Boolean = process?.isAlive == true

    /** 通道可用 = 在跑，或**能起来**（rootfs + 预置 pi 都在）。 */
    fun usable(): Boolean {
        process?.let { if (it.isAlive) return true }
        val ctx = AppCtx.get() ?: return false
        return PiRuntime.rootfsReady(ctx) && PiRuntime.piReady(ctx)
    }

    /**
     * **配置页改动了 pi 原生文件**（models.json / auth.json / settings.json 的内容真有变化）→ 置位。
     * 由 `AiConfigStore.save()` 打标（**内容比对**，不是「保存过就置位」——否则开屏那次统一落盘会白重启一次）。
     *
     * 为什么需要它：pi 只在**进程启动**时读这些文件（RPC 里没有 reload 命令，`session.reload()` 只由扩展
     * 的 `ctx.reload()` 触发）—— 不重启的话配置页改完要用户自己去切一次模型或重启应用才生效。
     */
    @Volatile
    private var configDirty = false

    fun markConfigDirty() {
        configDirty = true
        PientLog.i(TAG, "配置已改动：pi 通道将在下次启动时重启（重读 models.json / auth.json / settings.json）")
    }

    /**
     * 起（或复用）通道。**provider/model 变了、或配置改过就重启** —— 默认模型是 `--provider/--model`
     * 传进去的（比让 pi 自己选更可控；也避免「上一次选的服务商又生效」）。
     *
     * **必须同步**：并发调用会各起一个 pi 进程（双份事件流）。
     */
    @Synchronized
    fun start(provider: String, model: String): Boolean {
        PientLog.i(TAG, "start() 进入：key=[$provider/$model](len=${provider.length + 1 + model.length})；现有进程 alive=${process?.isAlive} 状态=${_state.value}")
        val key = "$provider/$model"
        process?.let { p ->
            val st = _state.value
            val cur = (st as? PiRpcState.Running)?.let { "${it.provider}/${it.model}" }
            // 键相同**且配置没改过**才复用：配置页改过 pi 原生文件时不复用（pi 只在进程启动时读它们）
            if (p.isAlive && cur == key && !configDirty) {
                return true
            }
            // 诊断：通道反复启停无从查起 —— 把原因打出来
            val why = when {
                !p.isAlive -> "旧进程已退出（exit=" + (runCatching { p.exitValue() }.getOrNull()?.toString() ?: "?") + "）"
                configDirty -> "配置页改过 pi 原生文件（重读 models.json / auth.json / settings.json）"
                else -> "alive=true 但状态/键不匹配：st=$st cur=[$cur](len=${cur?.length}) key=[$key](len=${key.length})"
            }
            val caller = Throwable().stackTrace
                .firstOrNull { it.className.startsWith("com.pient.app") && !it.className.endsWith("PiRpc") }
                ?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }.orEmpty()
            PientLog.i(TAG, "通道重启：$why；调用方=$caller")
        }
        configDirty = false   // 无论走哪条路，这次启动之后读到的都是新文件
        stop()
        val ctx = AppCtx.get() ?: return false
        if (!PiRuntime.rootfsReady(ctx) || !PiRuntime.piReady(ctx)) {
            _state.value = PiRpcState.Failed(L.runtime.rpcNotReady)
            return false
        }
        PiRuntime.prepareTerminal(ctx)   // DNS / 启动器 / 执行环境（与终端页同源）
        val shell = PiRuntime.shellPath(ctx)
        if (!shell.isFile) {
            _state.value = PiRpcState.Failed(L.runtime.rpcLauncherMissing(shell.absolutePath))
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
                // cwd = 当前项目目录（guest 里就是 /workspace 的绑定源；没设过项目时退回随包 app/）
                .directory(PiRuntime.workspaceDir(ctx))
                .redirectErrorStream(false)
                // **pi 自己永远在 Ubuntu 里跑**（与用户选的 exec_env 解耦）：node 与 pi 都装在那棵 rootfs 里，
                // 跟着 exec_env=android 走会把通道整体打断（见 PiRuntime.guestEnv 的说明）。
                .also { it.environment().putAll(PiRuntime.guestEnv(ctx)) }
                .start()
            process = proc
            writer = OutputStreamWriter(proc.outputStream, StandardCharsets.UTF_8)
            synchronized(stderrTail) { stderrTail.clear() }
            Thread({ readStdout(proc) }, "pient-pi-rpc-out").apply { isDaemon = true }.start()
            Thread({ readStderr(proc) }, "pient-pi-rpc-err").apply { isDaemon = true }.start()
            _state.value = PiRpcState.Running(provider, model)
            PientLog.i(TAG, "pi 通道已启动：$cmd")
            true
        } catch (t: Throwable) {
            process = null
            writer = null
            _state.value = PiRpcState.Failed(t.message ?: t.javaClass.simpleName)
            PientLog.w(TAG, "pi 通道启动失败：${t.message}")
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
        PientLog.i(TAG, "pi 通道已停止")
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
            PientLog.w(TAG, "命令发送失败：${t.message}")
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

    // ─────────────── 会话 / 树 / 分叉（会话映射用）───────────────
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
     * 让 pi 重扫技能 / 插件 / 设置（扩展命令 `/pient-reload` → 扩展 API `ctx.reload()`）。
     *
     * 为什么走扩展命令：0.85.1 的 RPC 面里**没有** reload（只有扩展的 `ctx.reload()`），
     * 而扩展命令在 pi 里是**立即执行、不落会话条目、不进 LLM 上下文**的
     * （`agent-session.ts` 的 `prompt()` 先走 `_tryExecuteExtensionCommand`，handled 即 return）。
     * 调用点：技能页导入、插件页装 / 删 / 更新完成之后（见 `PiCommands.reloadAsync`）。
     *
     * @return true = pi 收下并执行（`success: true`）
     */
    suspend fun reloadResources(): Boolean =
        prompt("/pient-reload")?.optBoolean("success") == true

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

    /**
     * **手动压缩上下文**（pi 官方 RPC `compact`，= 桌面端的 `/compact`）。
     *
     * 压缩整体归 pi：App 不判触发、不切片、不生成摘要。
     * `customInstructions` = 自定义摘要指令（配置页的「压缩指令」）。
     * pi 侧 `compact` 会调一次模型生成 checkpoint 摘要，并把 `agent.state.messages` 重建为
     * 压缩后的形态 —— 所以调用方随后要 `refreshPiTree()` + `syncMessagesFromPi()` 才能看到结果。
     * 返回 null = 通道没起来（未发送）。
     */
    suspend fun compact(customInstructions: String? = null): JSONObject? {
        val cmd = JSONObject().put("type", "compact")
        if (!customInstructions.isNullOrBlank()) cmd.put("customInstructions", customInstructions)
        return send(cmd, awaitMs = 180_000)
    }

    /**
     * 即时切换**自动压缩**（pi 官方 RPC `set_auto_compaction`）。与 settings.json 的
     * `compaction.enabled` 是同一件事，这里用于改配置后不让用户去重启宿主。
     */
    suspend fun setAutoCompaction(enabled: Boolean): JSONObject? {
        val res = send(JSONObject().put("type", "set_auto_compaction").put("enabled", enabled))
        // 留一行回包（这命令没有别处可观察的副作用：pi 只把开关状态放进 `get_state.autoCompactionEnabled`）——
        // 验「配置页拨开关 → 运行中的会话真的热改」时读这一行。
        PientLog.i(TAG, "set_auto_compaction($enabled) 回包：${res?.toString()?.take(200) ?: "无响应（通道没起）"}")
        return res
    }

    // ─────────────── 思考档位（界面开关/滑轨作用到 pi）───────────────

    /**
     * 设 pi 侧的思考档位（`off`/`minimal`/`low`/`medium`/`high`/`xhigh`/`max`）。
     *
     * pi 会按模型能力**自动夹取**（`session.setThinkingLevel` → `clampThinkingLevel`），不会报错；
     * 且只在档位真的变化时才往会话里 append 一条 `thinking_level_change`
     * —— 所以重复推同一档位是 no-op、不污染会话文件。
     */
    suspend fun setThinkingLevel(level: String): JSONObject? =
        send(JSONObject().put("type", "set_thinking_level").put("level", level))

    /** 当前模型支持的档位（含 `off`；无推理能力的模型只回 `["off"]`）。通道不可用 = null */
    suspend fun availableThinkingLevels(): List<String>? =
        dataOf(send(JSONObject().put("type", "get_available_thinking_levels")))
            ?.optJSONArray("levels")
            ?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }

    /** pi 此刻的档位（`get_state.thinkingLevel`）；未知 = null */
    suspend fun thinkingLevelNow(): String? =
        dataOf(send(JSONObject().put("type", "get_state")))
            ?.optString("thinkingLevel")
            ?.takeIf { it.isNotBlank() && it != "null" }

    /**
     * 起得来 ≠ 活着：rootfs 不可执行 / proot 报错时进程会**秒退**，
     * 而 `start()` 只看 ProcessBuilder 是否成功 → 会误报可用。这里给进程一个露馅窗口。
     */
    suspend fun aliveAfterStartup(timeoutMs: Long = 2500): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val p = process ?: return false
            if (!p.isAlive) return false
            kotlinx.coroutines.delay(120)
        }
        return process?.isAlive == true
    }

    /** 当前进程是否还在跑（诊断/界面上报错用） */
    fun processAlive(): Boolean = process?.isAlive == true

    /**
     * pi 侧此刻是否在跑一轮（`get_state.isStreaming`）。超时/通道不可用 = null（未知）。
     *
     * 用途：中止一轮之后要等 pi 收尾（它得把已生成的部分落成 aborted 条目、把叶定下来）
     * 再对位，不能在中间态上对账（见 `ChatState.abort`）。等待窗口给短一点，别把
     * 界面线程或后台协程挂住 —— 这是轮询判据，不是请求-响应。
     */
    suspend fun isStreamingNow(timeoutMs: Long = 1500): Boolean? =
        dataOf(send(JSONObject().put("type", "get_state"), awaitMs = timeoutMs))
            ?.optBoolean("isStreaming")

    // ─────────────────────── 读线程 ───────────────────────

    /**
     * 读 stdout：**按字节攒够一行、整行用 UTF-8 解码**。
     *
     * 逐字节 `b.toChar()` 等于把 UTF-8 当 Latin-1 解：ASCII 看不出问题，
     * 一旦模型回中文就变成「ãåæ¯æµè¯åå¤」。UTF-8 的多字节序列不可能含 0x0A，
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
            PientLog.w(TAG, "读 stdout 结束：${t.message}")
            // 进程退出的现场：exit 码 + stderr 末行 —— 通道 churn 排查靠它
            val code = runCatching { proc.exitValue() }.getOrNull()
            val tail = stderrText().lines().lastOrNull { it.isNotBlank() }.orEmpty()
            PientLog.w(TAG, "pi 进程 stdout 结束（exit=$code）" + if (tail.isBlank()) "" else " · stderr 末行：$tail")
            _events.tryEmit(JSONObject().put("type", "channel_closed"))
        }
    }

    private fun readStderr(proc: Process) {
        runCatching {
            proc.errorStream.bufferedReader(StandardCharsets.UTF_8).forEachLine {
                // 落盘/内存尾巴都在 noteStderr 里（一处写，别在这里再记一遍）
                noteStderr(it)
            }
        }
    }

    private fun dispatch(text: String) {
        val obj = runCatching { JSONObject(text) }.getOrElse {
            PientLog.w(TAG, "非 JSON 行：${text.take(200)}")
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
