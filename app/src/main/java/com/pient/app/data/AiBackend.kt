package com.pient.app.data

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
    data object Done : ChatEvent()
    data class Failed(val message: String) : ChatEvent()
}

/**
 * AI 后端（2026-09-09 实现 AI 接入）：直连服务商 HTTP API 的基本对话能力。
 * 协议二选一（按端点自动判定）：
 * - OpenAI 兼容：`POST {endpoint}/chat/completions`（Bearer 鉴权，SSE 流式）；
 * - Anthropic 兼容：`POST {endpoint}/v1/messages`（x-api-key 鉴权，SSE 事件流）。
 * 思考级别映射：Anthropic = thinking.budget_tokens；OpenAI = reasoning_effort。
 * 暂不支持：工具调用、多模态输入、Bedrock SigV4 签名（端点仍可配置，请求会报鉴权错误）。
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

    /** 思考级别 → Anthropic thinking budget（tokens） */
    fun thinkingBudget(level: ThinkingLevel): Int = when (level) {
        ThinkingLevel.MINIMAL -> 1024
        ThinkingLevel.LOW -> 2048
        ThinkingLevel.MEDIUM -> 4096
        ThinkingLevel.HIGH -> 8192
        ThinkingLevel.XHIGH -> 16384
    }

    /** 思考级别 → OpenAI reasoning_effort（xhigh 无对应档，收敛为 high） */
    fun reasoningEffort(level: ThinkingLevel): String = when (level) {
        ThinkingLevel.MINIMAL -> "minimal"
        ThinkingLevel.LOW -> "low"
        ThinkingLevel.MEDIUM -> "medium"
        ThinkingLevel.HIGH -> "high"
        ThinkingLevel.XHIGH -> "high"
    }

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

    data class ChatResult(val text: String, val usage: Usage?, val thinking: String? = null)

    suspend fun chat(
        cfg: ProviderConfig,
        systemPrompt: String?,
        history: List<Pair<String, String>>, // role → content（不含错误消息）
        thinkingLevel: ThinkingLevel?,
    ): ChatResult {
        val body = buildRequestBody(cfg, systemPrompt, history, thinkingLevel, stream = false)
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
        history: List<Pair<String, String>>,
        thinkingLevel: ThinkingLevel?,
    ): Flow<ChatEvent> = callbackFlow {
        val body = buildRequestBody(cfg, systemPrompt, history, thinkingLevel, stream = true)
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

    private fun buildRequestBody(
        cfg: ProviderConfig,
        systemPrompt: String?,
        history: List<Pair<String, String>>,
        thinkingLevel: ThinkingLevel?,
        stream: Boolean,
    ): JSONObject {
        val maxTokens = cfg.maxOutK.toIntOrNull()?.let { (it * 1024).coerceIn(1, 128000) }
        val thinking = thinkingLevel != null
        return if (isAnthropicProtocol(cfg.endpoint)) {
            JSONObject().apply {
                put("model", modelNameOf(cfg))
                if (maxTokens != null) put("max_tokens", maxTokens)
                if (!systemPrompt.isNullOrBlank()) put("system", systemPrompt)
                val msgs = org.json.JSONArray()
                history.forEach { (role, content) ->
                    msgs.put(JSONObject().put("role", role).put("content", content))
                }
                put("messages", msgs)
                put("stream", stream)
                if (thinking) {
                    // Anthropic：思考开启时 temperature 必须为 1 且不可传 top_p/top_k
                    put("thinking", JSONObject().put("type", "enabled")
                        .put("budget_tokens", thinkingBudget(thinkingLevel)))
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
                val msgs = org.json.JSONArray()
                if (!systemPrompt.isNullOrBlank()) {
                    msgs.put(JSONObject().put("role", "system").put("content", systemPrompt))
                }
                history.forEach { (role, content) ->
                    msgs.put(JSONObject().put("role", role).put("content", content))
                }
                put("messages", msgs)
                put("stream", stream)
                if (maxTokens != null) put("max_tokens", maxTokens)
                if (thinking) put("reasoning_effort", reasoningEffort(thinkingLevel))
                if (cfg.tempEnabled) cfg.tempValue.toFloatOrNull()?.let { put("temperature", it) }
                if (cfg.topPEnabled) cfg.topPValue.toFloatOrNull()?.let { put("top_p", it) }
            }
        }
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
        return ChatResult(text, usage, thinking)
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
        val content = root.optJSONArray("content")
        if (content != null) {
            for (i in 0 until content.length()) {
                val b = content.optJSONObject(i) ?: continue
                when (b.optString("type")) {
                    "text" -> sb.append(b.strOrEmpty("text"))
                    // 非流式思考块（Anthropic content 里的 thinking block）
                    "thinking" -> thinking.append(b.strOrEmpty("thinking"))
                }
            }
        }
        val usage = root.optJSONObject("usage")?.let { anthropicUsage(it) }
        return ChatResult(sb.toString(), usage, thinking.toString().takeIf { it.isNotEmpty() })
    }

    private fun parseOpenAiStream(source: okio.BufferedSource, emit: (ChatEvent) -> Unit) {
        var usage: Usage? = null
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
                o.optJSONObject("usage")?.let { usage = openAiUsage(it) }
            } catch (_: Exception) {
                // 忽略无法解析的分片
            }
        }
        usage?.let { emit(ChatEvent.UsageEvent(it)) }
    }

    private fun parseAnthropicStream(source: okio.BufferedSource, emit: (ChatEvent) -> Unit) {
        var inTokens = 0
        var outTokens = 0
        var cacheTokens = 0
        var cacheWriteTokens = 0
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            try {
                val o = JSONObject(payload)
                when (o.optString("type")) {
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
