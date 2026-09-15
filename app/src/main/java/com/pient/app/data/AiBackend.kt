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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** AI 请求失败（网络/HTTP/协议错误；message 面向用户展示） */
class AiException(message: String) : Exception(message)

/** 直发附件的一个部件（[type]：image / audio / video；[base64] 不含 data URL 前缀） */
data class WirePart(val type: String, val mime: String, val base64: String)

/**
 * AI 后端（2026-09-09 实现 AI 接入）：直连服务商 HTTP API 的对话能力。
 * 协议二选一（按端点自动判定）：
 * - OpenAI 兼容：`POST {endpoint}/chat/completions`（Bearer 鉴权，SSE 流式）；
 * - Anthropic 兼容：`POST {endpoint}/v1/messages`（x-api-key 鉴权，SSE 事件流）。
 * 思考级别映射：Anthropic = thinking.budget_tokens；OpenAI = reasoning_effort。
 * 2026-09-14 用户拍板：工具能力整体移除 —— 请求不再带 `tools`，回包也不解析工具调用。
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
    )

    // ───────────────────────── 对话（流式 SSE） ─────────────────────────

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

    // ───────────────────────── 回合 → 协议报文 ─────────────────────────

    // ───────────────────────── 响应解析 ─────────────────────────

    /**
     * null 安全取值：org.json 的 optString 对 JSON null 返回字面量 "null"
     * （JSONObject.NULL.toString()），推理模型流式分片常见 "content": null，
     * 直接拼接会把 "null" 写进回复正文（2026-09-09 实测 bug）——必须判 isNull。
     */
    private fun JSONObject.strOrEmpty(key: String): String =
        if (isNull(key)) "" else optString(key)

    /**
     * OpenAI 兼容协议的推理字段优先级（逐项对齐 pi `openai-completions.ts` 的
     * OPENAI_COMPLETIONS_REASONING_FIELDS）：llama.cpp 走 reasoning_content、多数
     * 国内服务商走 reasoning_content（DeepSeek）/ reasoning（GLM 等）、少数走
     * reasoning_text。**每个分片只取第一个非空字段**——有的端点同时回两个同值字段
     * （chutes.ai），不按优先级取会把思考文本拼两遍。
     */
    private val REASONING_FIELDS = listOf("reasoning_content", "reasoning", "reasoning_text")

    // ───────────────────────── 基础工具 ─────────────────────────

    /** 服务商配置里的首个模型名（思考参数写法推断 / 历史代码共用） */
    private fun modelNameOf(cfg: ProviderConfig): String = cfg.models.firstOrNull().orEmpty()

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
