package com.pient.app.data

import com.pient.app.tools.ToolCall

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** AI 请求失败（网络/HTTP/协议错误；message 面向用户展示） */
class AiException(message: String) : Exception(message)

/** 流式事件 */
sealed class ChatEvent {
    data class TextDelta(val text: String) : ChatEvent()

    /** 思考/推理增量（Anthropic thinking_delta / OpenAI 兼容 reasoning[_content|_text]） */
    data class ThinkingDelta(val text: String) : ChatEvent()
    data class UsageEvent(val usage: Usage) : ChatEvent()

    /** 本轮模型请求的工具调用（原生 `tool_calls` / Anthropic `tool_use`；可能多个） */
    data class ToolCallsEvent(val calls: List<ToolCall>) : ChatEvent()
    data object Done : ChatEvent()
    data class Failed(val message: String) : ChatEvent()
}

/**
 * 一次请求里的一个回合（比 `List<Pair<role, content>>` 更宽：工具调用与工具结果需要独立字段）。
 * - [Text]：普通文本回合（历史都是这种）；
 * - [Rich]：带**直发附件**的回合（媒体能力开关开启时，[MediaInline.parts] 产出内容部件）；
 * - [AssistantCalls]：模型上一轮回的工具调用（原生协议要求原样带回历史）；
 * - [ToolOutput]：工具执行结果（OpenAI = role:"tool" + tool_call_id；Anthropic = user 里的 tool_result）。
 */
sealed interface ChatTurn {
    data class Text(val role: String, val content: String) : ChatTurn
    data class Rich(val role: String, val text: String, val parts: List<WirePart>) : ChatTurn
    data class AssistantCalls(val text: String, val calls: List<ToolCall>) : ChatTurn
    data class ToolOutput(val callId: String, val name: String, val content: String) : ChatTurn
}

/** 直发附件的一个部件（[type]：image / audio / video；[base64] 不含 data URL 前缀） */
data class WirePart(val type: String, val mime: String, val base64: String)

/**
 * AI 后端（2026-09-09 实现 AI 接入）：直连服务商 HTTP API 的对话能力。
 * 协议二选一（按端点自动判定）：
 * - OpenAI 兼容：`POST {endpoint}/chat/completions`（Bearer 鉴权，SSE 流式）；
 * - Anthropic 兼容：`POST {endpoint}/v1/messages`（x-api-key 鉴权，SSE 事件流）。
 * 思考级别映射：Anthropic = thinking.budget_tokens；OpenAI = reasoning_effort。
 * 工具调用（2026-09-14）：`tools` 由调用方按「模型能力」开关决定是否下发；回包解析
 * 原生 `tool_calls` / Anthropic `tool_use`（流式分片按 index 聚合）。
 * 仍不支持：Bedrock SigV4 签名（端点仍可配置，请求会报鉴权错误）。
 */
object AiBackend {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** 端点是否走 Anthropic Messages 协议（其余一律 OpenAI chat/completions） */
    fun isAnthropicProtocol(endpoint: String): Boolean {
        val e = endpoint.trim().trimEnd('/')
        return e.contains("anthropic.com") || e.endsWith("/anthropic")
    }

    // 档位映射见下方 levelWire()/sampleIndex()（2026-09-12）：五档 → 服务商实际档位，
    // 旧的 thinkingBudget()/reasoningEffort() 一对一映射已被它取代（不再有 xhigh→high 这种硬收敛）。

    private fun chatUrl(cfg: ProviderConfig): String {
        val e = cfg.endpoint.trim().trimEnd('/')
        return if (isAnthropicProtocol(e)) {
            if (e.endsWith("/v1")) "$e/messages" else "$e/v1/messages"
        } else "$e/chat/completions"
    }

    private fun modelsUrl(cfg: ProviderConfig): String {
        val e = cfg.endpoint.trim().trimEnd('/')
        return if (isAnthropicProtocol(e)) {
            if (e.endsWith("/v1")) "$e/models" else "$e/v1/models"
        } else "$e/models"
    }

    private fun authHeaders(cfg: ProviderConfig, builder: Request.Builder) {
        if (isAnthropicProtocol(cfg.endpoint)) {
            builder.header("x-api-key", cfg.apiKey)
            builder.header("anthropic-version", "2023-06-01")
        } else {
            builder.header("Authorization", "Bearer ${cfg.apiKey}")
        }
    }

    // ───────────────────────── 模型列表 ─────────────────────────

    /** GET /models 拉取服务商可用模型 id 列表（配置页「刷新」与「测试连接」共用） */
    suspend fun listModels(cfg: ProviderConfig): List<String> {
        val req = Request.Builder().url(modelsUrl(cfg)).get().apply {
            authHeaders(cfg, this)
        }.build()
        val resp = execute(req)
        // ★ 响应体读取与 JSON 解析必须在 IO 线程：execute 恢复后协程继续跑在 Main 上，
        //   慢网络/大响应会把整段下载+解析塞进主线程 → 卡顿/ANR（2026-09-09 修复）
        return withContext(Dispatchers.IO) {
            resp.use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw AiException(httpError(r.code, body))
                val root = JSONObject(body)
                val arr = root.optJSONArray("data") ?: return@withContext emptyList()
                val out = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val id = arr.optJSONObject(i)?.optString("id")?.trim()
                    if (!id.isNullOrEmpty() && id !in out) out += id
                }
                out
            }
        }
    }

    // ───────────────────────── 对话（非流式） ─────────────────────────

    data class ChatResult(
        val text: String,
        val usage: Usage?,
        val thinking: String? = null,
        /** 原生工具调用（无工具调用时为空表） */
        val toolCalls: List<ToolCall> = emptyList(),
    )

    suspend fun chat(
        cfg: ProviderConfig,
        systemPrompt: String?,
        turns: List<ChatTurn>,
        thinkingLevel: ThinkingLevel?,
        tools: JSONArray? = null,
    ): ChatResult {
        val body = buildRequestBody(cfg, systemPrompt, turns, thinkingLevel, stream = false, tools = tools)
        val req = Request.Builder().url(chatUrl(cfg)).post(body.toString().toRequestBody(JSON)).apply {
            authHeaders(cfg, this)
        }.build()
        val resp = execute(req)
        // ★ 响应体读取与 JSON 解析必须在 IO 线程（同 listModels，2026-09-09 修复）：
        //   非流式 = 一次性读完整个响应，慢网络/长回答下在主线程做会卡死 UI → ANR/退出
        return withContext(Dispatchers.IO) {
            resp.use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw AiException(httpError(r.code, text))
                if (isAnthropicProtocol(cfg.endpoint)) {
                    parseAnthropicFull(JSONObject(text))
                } else {
                    parseOpenAiFull(JSONObject(text))
                }
            }
        }
    }

    // ───────────────────────── 对话（流式 SSE） ─────────────────────────

    fun chatStream(
        cfg: ProviderConfig,
        systemPrompt: String?,
        turns: List<ChatTurn>,
        thinkingLevel: ThinkingLevel?,
        tools: JSONArray? = null,
    ): Flow<ChatEvent> = callbackFlow {
        val body = buildRequestBody(cfg, systemPrompt, turns, thinkingLevel, stream = true, tools = tools)
        val req = Request.Builder().url(chatUrl(cfg)).post(body.toString().toRequestBody(JSON)).apply {
            authHeaders(cfg, this)
        }.build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySend(ChatEvent.Failed(e.message ?: "网络请求失败"))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    if (!r.isSuccessful) {
                        val text = r.body?.string().orEmpty()
                        trySend(ChatEvent.Failed(httpError(r.code, text)))
                        close()
                        return
                    }
                    try {
                        val source = r.body?.source() ?: run {
                            trySend(ChatEvent.Failed("空响应"))
                            close()
                            return
                        }
                        if (isAnthropicProtocol(cfg.endpoint)) {
                            parseAnthropicStream(source) { trySend(it) }
                        } else {
                            parseOpenAiStream(source) { trySend(it) }
                        }
                        trySend(ChatEvent.Done)
                    } catch (e: Exception) {
                        trySend(ChatEvent.Failed(e.message ?: "响应解析失败"))
                    }
                    close()
                }
            }
        })
        awaitClose { }
    }

    // ───────────────────────── 请求体 ─────────────────────────

    /**
     * 生效的思考参数写法：显式配置优先；AUTO 按模型名推断（deepseek → DEEPSEEK、
     * glm/zhipu → ZAI、qwen/qwq/通义 → QWEN），**识别不出 = NONE**（维持「不发参数」的
     * 老行为——对未知端点发它不认识的字段会直接 400，宁可保守）。
     */
    fun effectiveReasoningFormat(cfg: ProviderConfig): ReasoningFormat {
        if (cfg.reasoningFormat != ReasoningFormat.AUTO) return cfg.reasoningFormat
        val m = modelNameOf(cfg).lowercase()
        return when {
            m.contains("deepseek") -> ReasoningFormat.DEEPSEEK
            m.contains("glm") || m.contains("zhipu") || m.contains("chatglm") -> ReasoningFormat.ZAI
            m.contains("qwen") || m.contains("qwq") || m.contains("tongyi") -> ReasoningFormat.QWEN
            else -> ReasoningFormat.NONE
        }
    }

    /**
     * 档位采样（2026-09-12 映射层）：**服务商的档位数量未必是 5**——DeepSeek 官方只有
     * `low/high/max`、OpenAI 只有 4 档（无 xhigh）、有的服务商根本没有档位（只有开关）。
     * 采样规则 = 把我们的 5 档按比例落到对方的 n 档上：`round(ordinal × (n−1) / 4)`。
     * 同思路的参考实现：Operit `DeepseekProvider.resolveDeepseekThinkingEffort`
     * （五档 → `listOf("low","high","max","max","max")`）、Hermes 的最近邻收敛
     * （`xhigh→high`、`minimal→low`、否则 medium、否则第一个）。
     */
    private fun sampleIndex(level: ThinkingLevel, size: Int): Int =
        if (size <= 1) 0 else (level.ordinal * (size - 1) + 2) / 4

    /** 预算型档位阶梯（Anthropic `budget_tokens` / 千问·硅基流动 `thinking_budget`） */
    private val BUDGET_LADDER = listOf(1024, 2048, 4096, 8192, 16384)

    /** 档位在该服务商 + 格式下的线上形态 */
    sealed interface LevelWire {
        /** 词表型：写进 `reasoning_effort` / `reasoning.effort` */
        data class Word(val value: String) : LevelWire

        /** 预算型：写进 `budget_tokens` / `thinking_budget` */
        data class Budget(val tokens: Int) : LevelWire

        /** 该服务商/格式不支持档位（只支持开 / 关）——UI 据此置灰滑轨 */
        data object Unsupported : LevelWire
    }

    /** 各格式的档位词表（未列出且非预算型 = 不支持档位） */
    private fun levelWordsFor(format: ReasoningFormat): List<String>? = when (format) {
        ReasoningFormat.OPENAI -> listOf("minimal", "low", "medium", "high")      // OpenAI 无 xhigh 档
        ReasoningFormat.DEEPSEEK -> listOf("low", "high", "max")                   // DeepSeek 官方词表
        ReasoningFormat.OPENROUTER -> listOf("minimal", "low", "medium", "high")
        else -> null
    }

    /** 档位走预算写的格式 */
    private fun levelUsesBudget(format: ReasoningFormat): Boolean =
        format == ReasoningFormat.QWEN || format == ReasoningFormat.SILICONFLOW

    /**
     * 档位 → 线上形态（**请求体与 UI 提示共用同一判断**，不会出现「面板说 A、实际发 B」）：
     * Anthropic 协议固定预算；其余按格式的词表/预算；都不支持则 [LevelWire.Unsupported]。
     * 服务商若声明了 `thinkingLevels` 覆盖（ProviderCatalog），优先用它。
     */
    fun levelWire(cfg: ProviderConfig, level: ThinkingLevel): LevelWire {
        if (cfg.reasoningFormat == ReasoningFormat.NONE) return LevelWire.Unsupported
        val override = ProviderCatalog.byId[cfg.providerId]?.thinkingLevels
        if (override != null) {
            override.words?.let { return LevelWire.Word(it[sampleIndex(level, it.size)]) }
            override.budgets?.let { return LevelWire.Budget(it[sampleIndex(level, it.size)]) }
            return LevelWire.Unsupported
        }
        if (isAnthropicProtocol(cfg.endpoint)) {
            return LevelWire.Budget(BUDGET_LADDER[sampleIndex(level, BUDGET_LADDER.size)])
        }
        val format = effectiveReasoningFormat(cfg)
        levelWordsFor(format)?.let { return LevelWire.Word(it[sampleIndex(level, it.size)]) }
        if (levelUsesBudget(format)) return LevelWire.Budget(BUDGET_LADDER[sampleIndex(level, BUDGET_LADDER.size)])
        return LevelWire.Unsupported
    }

    /**
     * 写入思考参数（2026-09-12 真实化）：**关闭思考模式 = 显式禁用；开启 = 显式启用**，
     * 不再靠「省略参数」假装关闭（省略只对「默认不思考」的模型有效）。
     *
     * 各服务商写法取自 pi `thinkingFormat` 枚举（packages/ai/src/types.ts:578）与 Operit
     * 各 Provider 类的实测口径（DeepseekProvider/KimiProvider/DoubaoAIProvider 发
     * `thinking:{"type":"disabled"}`；Qwen/Nvidia/MNN 发 `enable_thinking=false`）：
     * - OPENAI：`reasoning_effort`=档位词 / `"none"`
     * - DEEPSEEK：`thinking.enabled` + `reasoning_effort`（词表 low/high/max）/ `thinking.disabled`
     * - ZAI：`thinking.enabled` / `thinking.disabled`（智谱不下发档位——GLM-4.5/4.6 的
     *   `reasoning_effort` 不认，仅 GLM-5.2+ 支持；不发即不报错，UI 会置灰滑轨说明）
     * - QWEN / SILICONFLOW：`enable_thinking` 布尔 +（开启时）`thinking_budget` 预算
     * - OPENROUTER：`reasoning.effort` / `reasoning.enabled=false`
     * - ANTHROPIC（OpenAI 兼容端点上的等价形态）：只发 `thinking.type`
     * - NONE：什么都不发（模型自带推理且不吃禁用字面量时的逃生口）
     *
     * Anthropic Messages 协议固定用官方 `thinking.type`（enabled+budget / disabled），
     * 但仍受 NONE 逃生口约束。
     */
    private fun applyReasoningParams(
        body: JSONObject,
        cfg: ProviderConfig,
        thinkingLevel: ThinkingLevel?,
    ) {
        if (cfg.reasoningFormat == ReasoningFormat.NONE) return
        if (isAnthropicProtocol(cfg.endpoint)) {
            body.put(
                "thinking",
                if (thinkingLevel != null) {
                    val budget = (levelWire(cfg, thinkingLevel) as? LevelWire.Budget)?.tokens
                        ?: BUDGET_LADDER.last()
                    JSONObject().put("type", "enabled").put("budget_tokens", budget)
                } else {
                    JSONObject().put("type", "disabled")
                },
            )
            return
        }
        // 未开启 = 不发档位；不支持档位的服务商（Unsupported）也只发开关
        val word = thinkingLevel?.let { (levelWire(cfg, it) as? LevelWire.Word)?.value }
        val budget = thinkingLevel?.let { (levelWire(cfg, it) as? LevelWire.Budget)?.tokens }
        when (effectiveReasoningFormat(cfg)) {
            ReasoningFormat.NONE, ReasoningFormat.AUTO -> Unit   // AUTO 已被 effectiveReasoningFormat 解析
            ReasoningFormat.OPENAI -> body.put("reasoning_effort", word ?: if (thinkingLevel == null) "none" else "medium")
            ReasoningFormat.DEEPSEEK -> {
                body.put("thinking", JSONObject().put("type", if (thinkingLevel != null) "enabled" else "disabled"))
                if (word != null) body.put("reasoning_effort", word)
            }
            ReasoningFormat.ZAI ->
                body.put("thinking", JSONObject().put("type", if (thinkingLevel != null) "enabled" else "disabled"))
            ReasoningFormat.QWEN, ReasoningFormat.SILICONFLOW -> {
                body.put("enable_thinking", thinkingLevel != null)
                if (budget != null) body.put("thinking_budget", budget)
            }
            // Anthropic 写法在 OpenAI 兼容端点上的等价形态（中转/代理端常见）
            ReasoningFormat.ANTHROPIC ->
                body.put("thinking", JSONObject().put("type", if (thinkingLevel != null) "enabled" else "disabled"))
            ReasoningFormat.OPENROUTER ->
                body.put(
                    "reasoning",
                    if (thinkingLevel != null) {
                        JSONObject().put("effort", word ?: "medium")
                    } else {
                        JSONObject().put("enabled", false)
                    },
                )
        }
    }

    private fun buildRequestBody(
        cfg: ProviderConfig,
        systemPrompt: String?,
        turns: List<ChatTurn>,
        thinkingLevel: ThinkingLevel?,
        stream: Boolean,
        tools: JSONArray?,
    ): JSONObject {
        val maxTokens = cfg.maxOutK.toIntOrNull()?.let { (it * 1024).coerceIn(1, 128000) }
        val thinking = thinkingLevel != null
        val hasTools = tools != null && tools.length() > 0
        return if (isAnthropicProtocol(cfg.endpoint)) {
            JSONObject().apply {
                put("model", modelNameOf(cfg))
                if (maxTokens != null) put("max_tokens", maxTokens)
                if (!systemPrompt.isNullOrBlank()) put("system", systemPrompt)
                put("messages", anthropicMessages(turns))
                put("stream", stream)
                if (hasTools) put("tools", tools)
                applyReasoningParams(this, cfg, thinkingLevel)
                if (thinking) {
                    // Anthropic：思考开启时 temperature 必须为 1 且不可传 top_p/top_k
                    put("temperature", 1.0)
                } else {
                    if (cfg.tempEnabled) cfg.tempValue.toFloatOrNull()?.let { put("temperature", it) }
                    if (cfg.topPEnabled) cfg.topPValue.toFloatOrNull()?.let { put("top_p", it) }
                    if (cfg.topKEnabled) cfg.topKValue.toIntOrNull()?.let { put("top_k", it) }
                }
            }
        } else {
            JSONObject().apply {
                put("model", modelNameOf(cfg))
                put("messages", openAiMessages(systemPrompt, turns))
                put("stream", stream)
                if (maxTokens != null) put("max_tokens", maxTokens)
                if (hasTools) put("tools", tools)
                applyReasoningParams(this, cfg, thinkingLevel)
                if (cfg.tempEnabled) cfg.tempValue.toFloatOrNull()?.let { put("temperature", it) }
                if (cfg.topPEnabled) cfg.topPValue.toFloatOrNull()?.let { put("top_p", it) }
            }
        }
    }

    // ───────────────────────── 回合 → 协议报文 ─────────────────────────

    /** OpenAI 兼容：system 作为首条消息；工具调用/结果用 `tool_calls` / `role:"tool"` */
    private fun openAiMessages(systemPrompt: String?, turns: List<ChatTurn>): JSONArray {
        val msgs = JSONArray()
        if (!systemPrompt.isNullOrBlank()) {
            msgs.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        for (t in turns) {
            when (t) {
                is ChatTurn.Text -> msgs.put(JSONObject().put("role", t.role).put("content", t.content))
                is ChatTurn.Rich -> msgs.put(
                    JSONObject().put("role", t.role).put("content", openAiContent(t)),
                )
                is ChatTurn.AssistantCalls -> {
                    val calls = JSONArray()
                    for (c in t.calls) {
                        calls.put(
                            JSONObject().put("id", c.id).put("type", "function").put(
                                "function",
                                JSONObject().put("name", c.name).put("arguments", c.arguments),
                            ),
                        )
                    }
                    msgs.put(
                        JSONObject().put("role", "assistant")
                            .put("content", t.text.ifBlank { JSONObject.NULL })
                            .put("tool_calls", calls),
                    )
                }
                is ChatTurn.ToolOutput -> msgs.put(
                    JSONObject().put("role", "tool")
                        .put("tool_call_id", t.callId)
                        .put("content", t.content),
                )
            }
        }
        return msgs
    }

    /**
     * 直发附件的 OpenAI 兼容形态（与 Operit `OpenAIProvider.buildContentField` 逐形对齐）：
     * 图片 `image_url`（data URL）、音频 `input_audio`（base64 + format）、视频 `video_url`（data URL）。
     */
    private fun openAiContent(turn: ChatTurn.Rich): Any {
        if (turn.parts.isEmpty()) return turn.text
        val arr = JSONArray()
        for (p in turn.parts) {
            when (p.type) {
                "image" -> arr.put(
                    JSONObject().put("type", "image_url")
                        .put("image_url", JSONObject().put("url", "data:${p.mime};base64,${p.base64}")),
                )
                "audio" -> arr.put(
                    JSONObject().put("type", "input_audio")
                        .put(
                            "input_audio",
                            JSONObject().put("data", p.base64).put("format", audioFormat(p.mime)),
                        ),
                )
                "video" -> arr.put(
                    JSONObject().put("type", "video_url")
                        .put("video_url", JSONObject().put("url", "data:${p.mime};base64,${p.base64}")),
                )
            }
        }
        if (turn.text.isNotBlank()) arr.put(JSONObject().put("type", "text").put("text", turn.text))
        return arr
    }

    private fun audioFormat(mime: String): String = when (mime.lowercase()) {
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/ogg" -> "ogg"
        "audio/webm" -> "webm"
        else -> mime.substringAfter("/", "wav")
    }

    /** Anthropic Messages：system 独立字段；工具结果放 user 消息的 `tool_result` 块（协议规定） */
    private fun anthropicMessages(turns: List<ChatTurn>): JSONArray {
        val msgs = JSONArray()
        for (t in turns) {
            when (t) {
                is ChatTurn.Text -> msgs.put(JSONObject().put("role", t.role).put("content", t.content))
                is ChatTurn.Rich -> {
                    val blocks = JSONArray()
                    for (p in t.parts) {
                        // Anthropic Messages 只吃图片；音频/视频无对应块（Operit 同样只给 OpenAI 兼容端发）
                        if (p.type != "image") continue
                        blocks.put(
                            JSONObject().put("type", "image").put(
                                "source",
                                JSONObject().put("type", "base64")
                                    .put("media_type", p.mime).put("data", p.base64),
                            ),
                        )
                    }
                    if (t.text.isNotBlank()) blocks.put(JSONObject().put("type", "text").put("text", t.text))
                    msgs.put(JSONObject().put("role", t.role).put("content", blocks))
                }
                is ChatTurn.AssistantCalls -> {
                    val blocks = JSONArray()
                    if (t.text.isNotBlank()) blocks.put(JSONObject().put("type", "text").put("text", t.text))
                    for (c in t.calls) {
                        val input = runCatching { JSONObject(c.arguments.ifBlank { "{}" }) }
                            .getOrElse { JSONObject() }
                        blocks.put(
                            JSONObject().put("type", "tool_use")
                                .put("id", c.id).put("name", c.name).put("input", input),
                        )
                    }
                    msgs.put(JSONObject().put("role", "assistant").put("content", blocks))
                }
                is ChatTurn.ToolOutput -> msgs.put(
                    JSONObject().put("role", "user").put(
                        "content",
                        JSONArray().put(
                            JSONObject().put("type", "tool_result")
                                .put("tool_use_id", t.callId)
                                .put("content", t.content),
                        ),
                    ),
                )
            }
        }
        return msgs
    }

    private fun modelNameOf(cfg: ProviderConfig): String = cfg.models.firstOrNull().orEmpty()

    // ───────────────────────── 响应解析 ─────────────────────────

    /**
     * null 安全取值：org.json 的 optString 对 JSON null 返回字面量 "null"
     * （JSONObject.NULL.toString()），推理模型流式分片常见 "content": null，
     * 直接拼接会把 "null" 写进回复正文（2026-09-09 实测 bug）——必须判 isNull。
     */
    private fun JSONObject.strOrEmpty(key: String): String =
        if (isNull(key)) "" else optString(key)

    /**
     * OpenAI 协议 usage → Usage（pi 同口径，见 packages/ai/src/api/openai-completions.ts）：
     * 输入 = prompt_tokens − 缓存读取 − 缓存写入（缓存单独计费）；缓存读取取值链
     * prompt_tokens_details.cached_tokens → prompt_cache_hit_tokens（DeepSeek）→ cached_tokens（Kimi）。
     */
    private fun openAiUsage(u: JSONObject): Usage {
        val details = u.optJSONObject("prompt_tokens_details")
        val cacheRead = details?.optInt("cached_tokens")
            ?: u.optInt("prompt_cache_hit_tokens").takeIf { it > 0 }
            ?: u.optInt("cached_tokens")
        val cacheWrite = details?.optInt("cache_write_tokens") ?: 0
        return Usage(
            inTokens = maxOf(0, u.optInt("prompt_tokens") - cacheRead - cacheWrite),
            outTokens = u.optInt("completion_tokens"),
            cacheTokens = cacheRead,
            costUsd = 0.0,
            cacheWriteTokens = cacheWrite,
        )
    }

    /** Anthropic 协议 usage → Usage（input_tokens 本身不含缓存读取；缓存写入 = cache_creation_input_tokens） */
    private fun anthropicUsage(u: JSONObject): Usage = Usage(
        inTokens = u.optInt("input_tokens"),
        outTokens = u.optInt("output_tokens"),
        cacheTokens = u.optInt("cache_read_input_tokens"),
        costUsd = 0.0,
        cacheWriteTokens = u.optInt("cache_creation_input_tokens"),
    )

    private fun parseOpenAiFull(root: JSONObject): ChatResult {
        val message = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
        val text = message?.let { it.strOrEmpty("content") } ?: ""
        val usage = root.optJSONObject("usage")?.let { openAiUsage(it) }
        // 非流式：推理内容同样按 pi 的字段优先级取第一个非空（见 reasoningField）
        val thinking = message?.let { m ->
            REASONING_FIELDS.firstNotNullOfOrNull { f -> m.strOrEmpty(f).takeIf { it.isNotEmpty() } }
        }
        return ChatResult(text, usage, thinking, openAiToolCalls(message))
    }

    /** OpenAI 兼容的 `message.tool_calls` → 调用列表（arguments 是 JSON 字符串，原样带） */
    private fun openAiToolCalls(message: JSONObject?): List<ToolCall> {
        val arr = message?.optJSONArray("tool_calls") ?: return emptyList()
        val out = ArrayList<ToolCall>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val fn = o.optJSONObject("function") ?: continue
            val name = fn.strOrEmpty("name").trim()
            if (name.isEmpty()) continue
            out.add(
                ToolCall(
                    id = o.strOrEmpty("id").ifBlank { "call_${i}" },
                    name = name,
                    arguments = fn.strOrEmpty("arguments").ifBlank { "{}" },
                ),
            )
        }
        return out
    }

    /**
     * OpenAI 兼容协议的推理字段优先级（逐项对齐 pi `openai-completions.ts` 的
     * OPENAI_COMPLETIONS_REASONING_FIELDS）：llama.cpp 走 reasoning_content、多数
     * 国内服务商走 reasoning_content（DeepSeek）/ reasoning（GLM 等）、少数走
     * reasoning_text。**每个分片只取第一个非空字段**——有的端点同时回两个同值字段
     * （chutes.ai），不按优先级取会把思考文本拼两遍。
     */
    private val REASONING_FIELDS = listOf("reasoning_content", "reasoning", "reasoning_text")

    private fun parseAnthropicFull(root: JSONObject): ChatResult {
        val sb = StringBuilder()
        val thinking = StringBuilder()
        val calls = ArrayList<ToolCall>()
        val content = root.optJSONArray("content")
        if (content != null) {
            for (i in 0 until content.length()) {
                val b = content.optJSONObject(i) ?: continue
                when (b.optString("type")) {
                    "text" -> sb.append(b.strOrEmpty("text"))
                    // 非流式思考块（Anthropic content 里的 thinking block）
                    "thinking" -> thinking.append(b.strOrEmpty("thinking"))
                    // 原生工具调用：input 已经是对象，转成 JSON 字符串（与 OpenAI 的 arguments 同形）
                    "tool_use" -> {
                        val name = b.strOrEmpty("name").trim()
                        if (name.isNotEmpty()) {
                            calls.add(
                                ToolCall(
                                    id = b.strOrEmpty("id").ifBlank { "call_$i" },
                                    name = name,
                                    arguments = b.optJSONObject("input")?.toString() ?: "{}",
                                ),
                            )
                        }
                    }
                }
            }
        }
        val usage = root.optJSONObject("usage")?.let { anthropicUsage(it) }
        return ChatResult(sb.toString(), usage, thinking.toString().takeIf { it.isNotEmpty() }, calls)
    }

    private fun parseOpenAiStream(source: okio.BufferedSource, emit: (ChatEvent) -> Unit) {
        var usage: Usage? = null
        val tools = ToolCallAcc()
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break
            try {
                val o = JSONObject(payload)
                val choices = o.optJSONArray("choices") ?: continue
                val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: continue
                val content = delta.strOrEmpty("content")
                if (content.isNotEmpty()) emit(ChatEvent.TextDelta(content))
                // 推理增量（思考模式开启时服务商才会回；按字段优先级取第一个非空）
                for (f in REASONING_FIELDS) {
                    val t = delta.strOrEmpty(f)
                    if (t.isNotEmpty()) {
                        emit(ChatEvent.ThinkingDelta(t))
                        break
                    }
                }
                // 原生工具调用分片（按 index 聚合：id/name 只来一次、arguments 逐片拼）
                delta.optJSONArray("tool_calls")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val tc = arr.optJSONObject(i) ?: continue
                        val idx = if (tc.has("index")) tc.optInt("index") else i
                        val fn = tc.optJSONObject("function")
                        tools.merge(
                            index = idx,
                            id = tc.strOrEmpty("id"),
                            name = fn?.strOrEmpty("name").orEmpty(),
                            argFragment = fn?.strOrEmpty("arguments").orEmpty(),
                        )
                    }
                }
                o.optJSONObject("usage")?.let { usage = openAiUsage(it) }
            } catch (_: Exception) {
                // 忽略无法解析的分片
            }
        }
        usage?.let { emit(ChatEvent.UsageEvent(it)) }
        tools.build().takeIf { it.isNotEmpty() }?.let { emit(ChatEvent.ToolCallsEvent(it)) }
    }

    /**
     * 流式工具调用分片聚合器（OpenAI 兼容口径）：`delta.tool_calls[]` 里 id/name 只出现在首片、
     * `arguments` 逐片拼；同一 index 的片必须按到达顺序拼（顺序错 = 参数 JSON 坏）。
     */
    private class ToolCallAcc {
        private val order = LinkedHashMap<Int, MutableList<Any>>() // index → [id, name, StringBuilder]

        fun merge(index: Int, id: String, name: String, argFragment: String) {
            val slot = order.getOrPut(index) { mutableListOf("", "", StringBuilder()) }
            if (id.isNotEmpty()) slot[0] = id
            if (name.isNotEmpty()) slot[1] = name
            (slot[2] as StringBuilder).append(argFragment)
        }

        fun build(): List<ToolCall> = order.entries
            .sortedBy { it.key }
            .mapNotNull { (idx, slot) ->
                val name = (slot[1] as String).trim()
                if (name.isEmpty()) return@mapNotNull null
                ToolCall(
                    id = (slot[0] as String).ifBlank { "call_$idx" },
                    name = name,
                    arguments = (slot[2] as StringBuilder).toString().ifBlank { "{}" },
                )
            }
    }

    private fun parseAnthropicStream(source: okio.BufferedSource, emit: (ChatEvent) -> Unit) {
        var inTokens = 0
        var outTokens = 0
        var cacheTokens = 0
        var cacheWriteTokens = 0
        val tools = ToolCallAcc()
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            try {
                val o = JSONObject(payload)
                when (o.optString("type")) {
                    // 工具调用块开始：{index, content_block:{type:"tool_use", id, name}}
                    "content_block_start" -> {
                        val block = o.optJSONObject("content_block")
                        if (block?.optString("type") == "tool_use") {
                            tools.merge(
                                index = o.optInt("index"),
                                id = block.strOrEmpty("id"),
                                name = block.strOrEmpty("name"),
                                argFragment = "",
                            )
                        }
                    }
                    "content_block_delta" -> {
                        val delta = o.optJSONObject("delta") ?: continue
                        when (delta.optString("type")) {
                            "text_delta" -> {
                                val t = delta.strOrEmpty("text")
                                if (t.isNotEmpty()) emit(ChatEvent.TextDelta(t))
                            }
                            // 思考增量（思考模式开启时 Anthropic 回 thinking_delta）
                            "thinking_delta" -> {
                                val t = delta.strOrEmpty("thinking")
                                if (t.isNotEmpty()) emit(ChatEvent.ThinkingDelta(t))
                            }
                            // 工具参数分片：partial_json 逐片拼成完整 arguments
                            "input_json_delta" -> tools.merge(
                                index = o.optInt("index"),
                                id = "",
                                name = "",
                                argFragment = delta.strOrEmpty("partial_json"),
                            )
                        }
                    }
                    "message_start" -> o.optJSONObject("message")?.optJSONObject("usage")?.let {
                        inTokens = it.optInt("input_tokens")
                        cacheTokens = it.optInt("cache_read_input_tokens")
                        cacheWriteTokens = it.optInt("cache_creation_input_tokens")
                    }
                    "message_delta" -> o.optJSONObject("usage")?.let {
                        outTokens = it.optInt("output_tokens")
                    }
                    "error" -> throw AiException(
                        o.optJSONObject("error")?.strOrEmpty("message") ?: "未知错误",
                    )
                    "message_stop" -> break
                }
            } catch (e: AiException) {
                throw e
            } catch (_: Exception) {
                // 忽略无法解析的分片
            }
        }
        if (inTokens > 0 || outTokens > 0 || cacheTokens > 0 || cacheWriteTokens > 0) {
            emit(ChatEvent.UsageEvent(Usage(inTokens, outTokens, cacheTokens, 0.0, cacheWriteTokens)))
        }
        tools.build().takeIf { it.isNotEmpty() }?.let { emit(ChatEvent.ToolCallsEvent(it)) }
    }

    // ───────────────────────── 基础工具 ─────────────────────────

    /** HTTP 错误体 → 用户可读消息（OpenAI/Anthropic error 结构均可解析） */
    private fun httpError(code: Int, body: String): String {
        val msg = try {
            val o = JSONObject(body)
            val e = o.optJSONObject("error")
            when {
                e != null && e.strOrEmpty("message").isNotBlank() -> e.strOrEmpty("message")
                o.strOrEmpty("message").isNotBlank() -> o.strOrEmpty("message")
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        return "HTTP $code${msg?.let { "：$it" } ?: ""}"
    }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(AiException(e.message ?: "网络请求失败"))
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response)
                }
            })
        }
}
