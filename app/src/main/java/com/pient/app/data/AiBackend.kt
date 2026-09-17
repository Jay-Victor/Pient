package com.pient.app.data

import com.pient.app.data.i18n.L
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

    /**
     * 「测试连接」逐一测模型专用客户端：诊断场景要**快速失败** —— 不能沿用对话那套
     * 300s 读超时（十几个模型串行跑会挂上几十分钟、界面一直转圈）。
     */
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

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

    // ───────────────────────── 模型逐一测试（配置页「测试连接」） ─────────────────────────

    /**
     * 端点走哪套协议（决定「逐一测试」怎么发请求）—— **逐条对齐该 api 在 pi 里的真实形态**：
     * - `openai-completions` → `POST {baseUrl}/chat/completions`；
     * - `openai-responses` → `POST {baseUrl}/responses`（pi 的 openai / xai 主用法就是这条；旧实现
     *   一律按 chat/completions 探活 —— 路径不同，配置正常也会被报成 ✕）；
     * - `anthropic-messages` → `POST {baseUrl}/v1/messages`；
     * - `azure-openai-responses`（要 `api-version` 查询参数 + `api-key` 头）与
     *   `openai-codex-responses`（ChatGPT 后端 + OAuth）应用内无法忠实复现 → 如实报「测不了」，
     *   不假装失败；google / bedrock / mistral / pi-messages 同理；未知 api 才回落到「端点含 anthropic」。
     */
    enum class ChatProtocol { OPENAI, RESPONSES, ANTHROPIC, UNSUPPORTED }

    fun chatProtocol(cfg: ProviderConfig): ChatProtocol {
        val api = cfg.apiType.trim().ifBlank { ProviderCatalog.apiOf(cfg.providerId) }
        return when (api) {
            "anthropic-messages" -> ChatProtocol.ANTHROPIC
            "openai-responses" -> ChatProtocol.RESPONSES
            "openai-completions" -> ChatProtocol.OPENAI
            "azure-openai-responses", "openai-codex-responses" -> ChatProtocol.UNSUPPORTED
            else -> if (isAnthropicProtocol(cfg.endpoint)) ChatProtocol.ANTHROPIC else ChatProtocol.UNSUPPORTED
        }
    }

    /** 单个模型的测试结果（[ok] = 真发过一次推理请求并拿到 2xx；[detail] = 失败原因） */
    data class ModelProbe(val model: String, val ok: Boolean, val detail: String? = null)

    /** pi 把 `models[].samplingParams` 写进请求体的 API 类型（其余路径不读，见 [samplingSupported]） */
    private val SAMPLING_APIS = setOf("openai-completions", "openai-responses", "azure-openai-responses")

    /**
     * pi 会不会把 `models[].samplingParams`（温度 / Top-K / Top-P）真的写进请求体。
     *
     * 口径（2026-09-17 读 pi-0.85.1 源码）：只有 **openai-completions / openai-responses /
     * azure-openai-responses** 三条路径 `Object.assign(params, options.samplingParams)`；
     * anthropic-messages / google(±vertex) / bedrock / mistral / openai-codex-responses 虽然都调
     * `buildBaseOptions`（把 samplingParams 并进 options），但请求体构造里**不读它** ——
     * 配了也不生效。页面据此置灰并说明，不摆「看着能调、其实不转发」的假旋钮。
     */
    fun samplingSupported(cfg: ProviderConfig): Boolean {
        val api = cfg.apiType.trim().ifBlank { ProviderCatalog.apiOf(cfg.providerId) }
        return api in SAMPLING_APIS
    }

    /** 探测请求的输出上限：只要「服务端认这个模型 + 鉴权通过」，不需要真回答 */
    private const val PROBE_MAX_TOKENS = 16

    /**
     * 对**单个模型**发一条极短的推理请求（`max_tokens=16`）。
     *
     * 为什么不能只 GET `/models`（2026-09-17 用户口径）：那条只证明「能列模型」——
     * 套餐不含该模型、模型名写错、权限不对都发现不了。逐模型真发一次才叫「可用」。
     *
     * URL 口径**逐字照 pi**（pi 把 baseUrl 原样交给官方 SDK，由 SDK 拼路径），见 [chatProtocol]：
     * openai 兼容 = `{endpoint}/chat/completions`、responses = `{endpoint}/responses`、
     * anthropic = `{endpoint}/v1/messages`。
     */
    suspend fun testModel(cfg: ProviderConfig, modelEntry: String): ModelProbe {
        // 页面语法 `id=别名`：发给服务商的是 id（别名只进 pi 的 models[].name）
        val id = modelEntry.substringBefore('=').trim()
        val base = cfg.endpoint.trim().trimEnd('/')
        val body = JSONObject()
            .put("model", id)
            .put("max_tokens", PROBE_MAX_TOKENS)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "ping")))
        return try {
            val req = when (chatProtocol(cfg)) {
                ChatProtocol.ANTHROPIC -> Request.Builder()
                    .url("$base/v1/messages")
                    .post(body.toString().toRequestBody(JSON))
                    .apply { authHeaders(cfg, this) }
                    .build()
                ChatProtocol.RESPONSES -> Request.Builder()
                    // pi 的 openai / xai 走的就是这条：POST {baseUrl}/responses（{model, input, max_output_tokens}）
                    .url("$base/responses")
                    .post(
                        JSONObject()
                            .put("model", id)
                            .put("input", "ping")
                            .put("max_output_tokens", PROBE_MAX_TOKENS)
                            .toString().toRequestBody(JSON),
                    )
                    .apply { authHeaders(cfg, this) }
                    .build()
                else -> Request.Builder()
                    .url("$base/chat/completions")
                    // stream=false：非流式才是一次完整请求-响应，SSE 断在半路不好判
                    .post(body.put("stream", false).toString().toRequestBody(JSON))
                    .apply { authHeaders(cfg, this) }
                    .build()
            }
            val resp = execute(req, probeClient)
            withContext(Dispatchers.IO) {
                resp.use { r ->
                    if (r.isSuccessful) ModelProbe(id, true)
                    else ModelProbe(id, false, httpError(r.code, r.body?.string().orEmpty()))
                }
            }
        } catch (e: Exception) {
            ModelProbe(id, false, e.message ?: L.common.unknownError)
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
     * AUTO 的推断规则（**唯一一份**：界面显示、[effectiveReasoningFormat]、写 models.json 三处共用）：
     * deepseek → DEEPSEEK、glm/zhipu → ZAI、qwen/qwq/通义 → QWEN，**识别不出 = NONE**
     * （维持「不发参数」的老行为——对未知端点发它不认识的字段会直接 400，宁可保守）。
     */
    fun inferReasoningFormat(modelName: String): ReasoningFormat {
        val m = modelName.lowercase()
        return when {
            m.contains("deepseek") -> ReasoningFormat.DEEPSEEK
            m.contains("glm") || m.contains("zhipu") || m.contains("chatglm") -> ReasoningFormat.ZAI
            m.contains("qwen") || m.contains("qwq") || m.contains("tongyi") -> ReasoningFormat.QWEN
            else -> ReasoningFormat.NONE
        }
    }

    /** 生效的思考参数写法：显式配置优先；AUTO 按模型名推断（见 [inferReasoningFormat]） */
    fun effectiveReasoningFormat(cfg: ProviderConfig): ReasoningFormat {
        if (cfg.reasoningFormat != ReasoningFormat.AUTO) return cfg.reasoningFormat
        return inferReasoningFormat(modelNameOf(cfg))
    }

    /**
     * 档位采样：把 5 档按比例落到「对方的 n 个位置」上，`round(ordinal × (n−1) / 4)`。
     * **只服务两条路**（都不再是常用路径，见 [levelWire] 的说明）：① 服务商在 ProviderCatalog 里
     * 声明的预算表；② Anthropic 协议的 `budget_tokens` 阶梯（pi 那边是 `adjustMaxTokensForThinking`
     * ＋可选的 ThinkingBudgets 表，应用只能给个近似值，界面已标「预计」）。
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

    /**
     * 档位 → 线上形态（**请求体与 UI 提示共用同一判断**，不会出现「面板说 A、实际发 B」）：
     * Anthropic 协议固定预算；其余按格式的词表/预算；都不支持则 [LevelWire.Unsupported]。
     */
    fun levelWire(cfg: ProviderConfig, level: ThinkingLevel): LevelWire {
        if (cfg.reasoningFormat == ReasoningFormat.NONE) return LevelWire.Unsupported
        if (isAnthropicProtocol(cfg.endpoint)) {
            return LevelWire.Budget(BUDGET_LADDER[sampleIndex(level, BUDGET_LADDER.size)])
        }
        val format = effectiveReasoningFormat(cfg)
        // **原样报档位名**（2026-09-17 真机实测后改）：pi 的请求体是
        // `model.thinkingLevelMap?.[档位] ?? 档位`（openai-completions.ts:882），而应用管理的模型**不写**
        // thinkingLevelMap ⇒ pi 发出去的就是档位名本身。此前按「服务商词表」等距采样，会出现
        // 真值行和估算行**互相矛盾**的情况（实测：偏好 low，pi 答「实际收到：low」，估算却写「high」
        // —— 那是 DeepSeek 词表 low/high/max 按五档比例采出来的中档）。这行既然叫
        // 「预计服务商收到」，口径就必须是 pi 的线上值。
        if (format == ReasoningFormat.NONE) return LevelWire.Unsupported
        return LevelWire.Word(level.piValue)
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

    private suspend fun execute(request: Request, client: OkHttpClient = this.client): Response =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(AiException(e.message ?: L.runtime.networkRequestFailed))
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response)
                }
            })
        }
}
