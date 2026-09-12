package com.pient.app.runtime

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * pi 宿主（工具层底座）的应用级单例。
 *
 * 形态：内嵌 Node 子进程加载 pi 官方包，经 **官方 RPC 协议**（JSONL over stdio）收发命令与事件
 * ——官方 `rpc.md` 规定 stdio 是主形态；后续若把宿主挪进前台服务/独立进程，只需把这里的
 * 传输层换成同名消息的本地 socket（帧格式不变），上层无感。
 *
 * 现状（2026-09-12）：宿主由 App 进程直接拉起，随应用存活；前台服务托管与保活是下一步。
 * 模型/会话尚未经它流转——本步目标是「宿主可用 + RPC 通道可信」，工具调用事件接聊天流在后续步骤。
 */
object PiHost {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var client: PiRpcClient? = null

    private val _state = MutableStateFlow<PiHostState>(PiHostState.Stopped)
    val state: StateFlow<PiHostState> = _state.asStateFlow()

    /** 宿主最近一次 get_state 的快照（data 段） */
    private val _lastState = MutableStateFlow<JSONObject?>(null)
    val lastState: StateFlow<JSONObject?> = _lastState.asStateFlow()

    /** agent 事件流（工具调用 / 消息增量…） */
    private val _events = MutableSharedFlow<JSONObject>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<JSONObject> = _events.asSharedFlow()

    private fun impl(context: Context): PiRpcClient {
        val ctx = context.applicationContext
        appContext = ctx
        return synchronized(lock) {
            client ?: PiRpcClient(ctx).also { created ->
                scope.launch { created.state.collect { _state.value = it } }
                scope.launch { created.lastState.collect { _lastState.value = it } }
                scope.launch { created.events.collect { _events.emit(it) } }
                client = created
            }
        }
    }

    /**
     * 启动宿主（幂等）；启动后刷新状态快照，并按 Pient 的思考开关设置宿主的思考档位。
     * 启动前先按当前「服务商与模型配置」生成 pi 的 models.json/auth.json（模型接线）。
     *
     * [thinkingLevel] 取 pi 的档位字面量（`off`/`minimal`/`low`/`medium`/`high`/`xhigh`）；
     * Pient 关闭思考时传 `off`——pi 侧默认档位是 medium（实测），不显式关会给默认思考的模型照发推理。
     */
    fun ensureStarted(
        context: Context,
        provider: String? = null,
        model: String? = null,
        thinkingLevel: String? = null,
    ): Boolean {
        val ctx = context.applicationContext
        val c = impl(ctx)
        PiRuntime.syncAgentAssets(ctx)   // 权限守门扩展 + 默认工具策略
        val providers = PiConfig.sync(ctx)
        // 终端层准备：guest 的 DNS + root 侧启动器 + 档位→模式文件（PRoot / chroot）
        PiRuntime.prepareTerminal(ctx)
        val started = c.start(provider, model)
        if (started) {
            scope.launch {
                c.refreshState()
                if (!thinkingLevel.isNullOrBlank()) {
                    c.request("set_thinking_level", JSONObject().put("level", thinkingLevel))
                    // 回读一次：首次 refresh 拿的是设置前的档位（pi 启动默认 medium）
                    c.refreshState()
                }
            }
        }
        // 无服务商配置：宿主即使起来了也是 unknown 模型（只有 RPC bash 这类直通命令可用）
        if (providers == 0) _state.value = PiHostState.MissingModel
        return started
    }

    suspend fun refresh(): JSONObject? = appContext?.let { impl(it).refreshState() }

    /** 发一条 RPC 命令（超时返回 null） */
    suspend fun request(
        type: String,
        payload: JSONObject = JSONObject(),
        timeoutMs: Long = 20_000,
    ): JSONObject? = appContext?.let { impl(it).request(type, payload, timeoutMs) }

    /** 回扩展的 UI 请求（权限守门扩展的授权询问走这里） */
    fun respondUi(id: String, value: String? = null, confirmed: Boolean? = null, cancelled: Boolean = false) {
        appContext?.let { impl(it).sendUiResponse(id, value, confirmed, cancelled) }
    }

    /** 应用 Context（App 启动时记录；供 PiSessions 之类需要落盘的运行时组件使用） */
    fun appContextOrNull(): Context? = appContext

    fun stop() {
        client?.stop()
    }
}
