package com.pient.app.data

import androidx.annotation.DrawableRes
import com.pient.app.R

/**
 * 服务商目录（2026-08-30 服务商与模型配置页数据源）：
 * - 服务商清单与显示名对齐 pi-0.84.2 `packages/ai/src/providers/`（官方 name 字段）；
 * - 默认 API 端点对齐各 provider 官方 `baseUrl` 字段；
 * - 服务商 logo 对齐 pi-web `ModelsConfig.tsx` 的 `PROVIDER_ICONS` 映射
 *   （lobehub/icons，Mono 单色 = hasColor:false，用主题文字色着色；
 *    Color 多彩 = hasColor:true，原色渲染）；
 * - 无模型列表数据：模型列表由用户在配置页填写或经「刷新」从服务商 /models 拉取（2026-09-10）。
 */
data class ProviderInfo(
    val id: String,
    val name: String,
    @DrawableRes val logoRes: Int,
    val monoLogo: Boolean,
    val defaultEndpoint: String,
    val endpoints: List<String>,
    /** 暗色主题专用 logo（0 = 复用 logoRes）。用于固有色在暗色卡片上不可见的图标 */
    @DrawableRes val logoResDark: Int = 0,
    /**
     * 该服务商**确定的**思考参数写法（2026-09-12）：新增服务商时预置为 `ProviderConfig.reasoningFormat`；
     * AUTO 只留给「自定义」服务商（按模型名推断）。
     */
    val reasoningFormat: ReasoningFormat = ReasoningFormat.NONE,
    /**
     * 该服务商的**档位词表/预算**覆盖（2026-09-12）：null = 用格式默认（见 `AiBackend.levelWire`）。
     * 用于「格式默认猜不出该家档位」的情形——例如某家只有 2/3 档、或档位用预算而非词表。
     * `ThinkingLevels(words=null, budgets=null)` = 明确声明「该家不支持档位调节」（UI 置灰滑轨）。
     */
    val thinkingLevels: ThinkingLevels? = null,
)

/**
 * 服务商的档位表达（ProviderCatalog 声明；[AiBackend.levelWire] 里按 5 档等距采样取值）。
 * - [words]：词表型（如 DeepSeek 官方只有 low/high/max）
 * - [budgets]：预算型（如 Anthropic budget_tokens 的阶梯）
 * - 两者都空 = 不支持档位调节（只有开 / 关）
 */
data class ThinkingLevels(
    val words: List<String>? = null,
    val budgets: List<Int>? = null,
)

object ProviderCatalog {

    /** 自定义服务商 id（pi-web "custom" 卡片语义） */
    const val CUSTOM_ID = "custom"

    val custom = ProviderInfo(
        id = CUSTOM_ID,
        name = "自定义（OpenAI / Anthropic 兼容）",
        logoRes = 0,
        monoLogo = true,
        defaultEndpoint = "",
        endpoints = emptyList(),
        // 自定义服务商没有预设：按模型名推断（识别不出则不发送）
        reasoningFormat = ReasoningFormat.AUTO,
    )

    val all: List<ProviderInfo> = listOf(
        // ── OpenAI 系 ──
        p("openai", "OpenAI", R.drawable.provider_openai, mono = true,
            "https://api.openai.com/v1", reasoningFormat = ReasoningFormat.OPENAI),
        p("openai-codex", "OpenAI Codex", R.drawable.provider_openai, mono = true,
            "https://chatgpt.com/backend-api", reasoningFormat = ReasoningFormat.OPENAI),
        p("azure-openai-responses", "Azure OpenAI", R.drawable.provider_azure, mono = false,
            "https://<resource>.openai.azure.com/openai", reasoningFormat = ReasoningFormat.OPENAI),
        // ── Anthropic 系 ──
        p("anthropic", "Anthropic", R.drawable.provider_anthropic, mono = true,
            "https://api.anthropic.com", reasoningFormat = ReasoningFormat.ANTHROPIC),
        p("amazon-bedrock", "Amazon Bedrock", R.drawable.provider_amazon_bedrock, mono = false,
            "https://bedrock-runtime.<region>.amazonaws.com"),
        // ── Google 系 ──
        p("google", "Google", R.drawable.provider_google, mono = false,
            "https://generativelanguage.googleapis.com/v1beta"),
        p("google-vertex", "Google Vertex AI", R.drawable.provider_google, mono = false,
            "https://<location>-aiplatform.googleapis.com/v1"),
        // ── 国内大模型 ──
        p("deepseek", "DeepSeek", R.drawable.provider_deepseek, mono = false,
            "https://api.deepseek.com", reasoningFormat = ReasoningFormat.DEEPSEEK),
        p("kimi-coding", "Kimi For Coding", R.drawable.provider_kimi_coding, mono = false,
            "https://api.kimi.com/coding", reasoningFormat = ReasoningFormat.DEEPSEEK,
            thinkingLevels = ThinkingLevels(),   // Kimi 思考版无档位（要么不思考、要么一直思考）
            logoResDark = R.drawable.provider_kimi_coding_dark),
        p("moonshotai", "Moonshot AI", R.drawable.provider_moonshot, mono = true,
            "https://api.moonshot.ai/v1", reasoningFormat = ReasoningFormat.DEEPSEEK,
            thinkingLevels = ThinkingLevels()),
        p("moonshotai-cn", "Moonshot AI CN", R.drawable.provider_moonshot, mono = true,
            "https://api.moonshot.cn/v1", reasoningFormat = ReasoningFormat.DEEPSEEK,
            thinkingLevels = ThinkingLevels()),
        p("minimax", "MiniMax", R.drawable.provider_minimax, mono = false,
            "https://api.minimax.io/anthropic", reasoningFormat = ReasoningFormat.ANTHROPIC),
        p("minimax-cn", "MiniMax CN", R.drawable.provider_minimax, mono = false,
            "https://api.minimaxi.com/anthropic", reasoningFormat = ReasoningFormat.ANTHROPIC),
        p("ant-ling", "Ant Ling", R.drawable.provider_ant_ling, mono = false,
            "https://api.ant-ling.com/v1"),
        p("zai", "Z.AI", R.drawable.provider_zai, mono = true,
            "https://api.z.ai/api/coding/paas/v4", reasoningFormat = ReasoningFormat.ZAI),
        p("zai-coding-cn", "Z.AI Coding CN", R.drawable.provider_zai, mono = true,
            "https://open.bigmodel.cn/api/coding/paas/v4", reasoningFormat = ReasoningFormat.ZAI),
        p("qwen-token-plan", "Qwen Token Plan", R.drawable.provider_qwen, mono = false,
            "https://token-plan.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1",
            reasoningFormat = ReasoningFormat.QWEN),
        p("qwen-token-plan-cn", "Qwen Token Plan CN", R.drawable.provider_qwen, mono = false,
            "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
            reasoningFormat = ReasoningFormat.QWEN),
        p("qwen-token-plan-individual", "Qwen Token Plan Individual", R.drawable.provider_qwen, mono = false,
            "https://token-plan.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1",
            reasoningFormat = ReasoningFormat.QWEN),
        p("xiaomi", "Xiaomi", R.drawable.provider_xiaomi, mono = true,
            "https://api.xiaomimimo.com/v1"),
        p("xiaomi-token-plan-ams", "Xiaomi Token Plan AMS", R.drawable.provider_xiaomi, mono = true,
            "https://token-plan-ams.xiaomimimo.com/v1"),
        p("xiaomi-token-plan-cn", "Xiaomi Token Plan CN", R.drawable.provider_xiaomi, mono = true,
            "https://token-plan-cn.xiaomimimo.com/v1"),
        p("xiaomi-token-plan-sgp", "Xiaomi Token Plan SGP", R.drawable.provider_xiaomi, mono = true,
            "https://token-plan-sgp.xiaomimimo.com/v1"),
        // ── 海外大模型 ──
        p("mistral", "Mistral", R.drawable.provider_mistral, mono = false,
            "https://api.mistral.ai"),
        p("xai", "xAI", R.drawable.provider_xai, mono = true,
            "https://api.x.ai/v1"),
        p("groq", "Groq", R.drawable.provider_groq, mono = true,
            "https://api.groq.com/openai/v1"),
        // ── 聚合 / 网关 / 推理平台 ──
        // 说明（2026-09-12）：以上/以下未显式标注的服务商一律 **NONE = 不发送思考参数**——
        // 它们的思考参数写法未核实（或模型架构性不可关，如 Gemini 3 系），宁可开关只作用于展示，
        // 也不猜字段导致 400；用户可在配置页「思考设置」里按需覆盖为具体写法。
        p("openrouter", "OpenRouter", R.drawable.provider_openrouter, mono = true,
            "https://openrouter.ai/api/v1", reasoningFormat = ReasoningFormat.OPENROUTER),
        p("github-copilot", "GitHub Copilot", R.drawable.provider_github_copilot, mono = true,
            "https://api.individual.githubcopilot.com"),
        p("cloudflare-ai-gateway", "Cloudflare AI Gateway", R.drawable.provider_cloudflare, mono = false,
            "https://gateway.ai.cloudflare.com/v1/{ACCOUNT_ID}/{GATEWAY_ID}/anthropic",
            endpoints = listOf(
                "https://gateway.ai.cloudflare.com/v1/{ACCOUNT_ID}/{GATEWAY_ID}/anthropic",
                "https://gateway.ai.cloudflare.com/v1/{ACCOUNT_ID}/{GATEWAY_ID}/openai",
                "https://gateway.ai.cloudflare.com/v1/{ACCOUNT_ID}/{GATEWAY_ID}/compat",
            ),
            reasoningFormat = ReasoningFormat.ANTHROPIC),
        p("cloudflare-workers-ai", "Cloudflare Workers AI", R.drawable.provider_cloudflare, mono = false,
            "https://api.cloudflare.com/client/v4/accounts/{CLOUDFLARE_ACCOUNT_ID}/ai/v1"),
        p("vercel-ai-gateway", "Vercel AI Gateway", R.drawable.provider_vercel, mono = true,
            "https://ai-gateway.vercel.sh"),
        p("fireworks", "Fireworks", R.drawable.provider_fireworks, mono = false,
            "https://api.fireworks.ai/inference"),
        p("together", "Together", R.drawable.provider_together, mono = false,
            "https://api.together.ai/v1"),
        p("huggingface", "Hugging Face", R.drawable.provider_huggingface, mono = false,
            "https://router.huggingface.co/v1"),
        p("cerebras", "Cerebras", R.drawable.provider_cerebras, mono = false,
            "https://api.cerebras.ai/v1",
            logoResDark = R.drawable.provider_cerebras_dark),
        p("nvidia", "NVIDIA", R.drawable.provider_nvidia, mono = false,
            "https://integrate.api.nvidia.com/v1"),
        p("baseten", "Baseten", R.drawable.provider_baseten, mono = true,
            "https://inference.baseten.co/v1"),
        p("opencode", "OpenCode Zen", R.drawable.provider_opencode, mono = true,
            "https://opencode.ai/zen"),
        p("opencode-go", "OpenCode Go", R.drawable.provider_opencode, mono = true,
            "https://opencode.ai/zen"),
        // Radius：pi 官方的**动态网关**（provider 级 `oauth: "radius"` + 用户自备 baseUrl，api = pi-messages）。
        // 补它是因为 pi-ai 的 provider 表里有这一家而 Pient 目录缺（2026-09-15 要求 3）。
        // 思考写法给 AUTO：网关后面挂什么模型都可能，按模型名推断、识别不出就不发参数。
        p("radius", "Radius（pi 网关）", R.drawable.provider_radius, mono = true,
            "", reasoningFormat = ReasoningFormat.AUTO),
    )

    /**
     * **provider → pi 的 api 类型**（事实表，2026-09-14 从 pi-0.85.1 各 provider 的 .ts 源文件核出）。
     *
     * pi 的 `api` 是 **per-model** 的（同一 provider 可以挂多种：opencode 挂 4 种、fireworks/github-copilot
     * 挂 2–3 种），Pient 页面按服务商配置，所以这里取该家的**主用法**写在 provider 级；
     * 生成 `models.json` 时用它（模型级仍可在 JSON 里单独覆盖）。
     * 表里没有的一律 `openai-completions`（最兼容；自定义服务商也走这条）。
     */
    private val API_BY_ID: Map<String, String> = mapOf(
        "openai" to "openai-responses",
        "openai-codex" to "openai-codex-responses",
        "azure-openai-responses" to "azure-openai-responses",
        "anthropic" to "anthropic-messages",
        "amazon-bedrock" to "bedrock-converse-stream",
        "google" to "google-generative-ai",
        "google-vertex" to "google-vertex",
        "mistral" to "mistral-conversations",
        "kimi-coding" to "anthropic-messages",
        "minimax" to "anthropic-messages",
        "minimax-cn" to "anthropic-messages",
        "vercel-ai-gateway" to "anthropic-messages",
        "xai" to "openai-responses",
        "radius" to "pi-messages",
    )

    /** 该服务商在 pi 里使用的 api 类型（见 [API_BY_ID]） */
    fun apiOf(id: String): String = API_BY_ID[id] ?: "openai-completions"

    /**
     * pi-ai 的 api 类型全集（`docs/models.md`「Supported APIs」+ 各 provider 源码里的 api 集合）。
     * 配置页的「API 类型」下拉用它；**默认取 [apiOf]（= pi 侧的事实表）**，只在用户显式改过时才写别的值。
     */
    val API_TYPES = listOf(
        "openai-completions",
        "openai-responses",
        "openai-codex-responses",
        "azure-openai-responses",
        "anthropic-messages",
        "google-generative-ai",
        "google-vertex",
        "bedrock-converse-stream",
        "mistral-conversations",
        "pi-messages",
    )

    /**
     * 一个服务商**可用的** api 类型（pi-ai 里 api 是 per-model 的，provider 给出可用集合）。
     * 多 api 的家（网关/代理类）按 §4.1 事实表列出；其余只有一个默认值。
     */
    private val API_SETS: Map<String, List<String>> = mapOf(
        "fireworks" to listOf("anthropic-messages", "openai-completions"),
        "github-copilot" to listOf("anthropic-messages", "openai-completions", "openai-responses"),
        "opencode" to listOf("anthropic-messages", "google-generative-ai", "openai-completions", "openai-responses"),
        "opencode-go" to listOf("anthropic-messages", "openai-completions", "openai-responses"),
        "openrouter" to listOf("anthropic-messages", "openai-completions"),
        "cloudflare-ai-gateway" to listOf("anthropic-messages", "openai-completions", "openai-responses"),
    )

    /** 下拉里给某个服务商列出的候选：可用集合 ∪ 当前值（保证当前值一定在列表里） */
    fun apiOptions(id: String, current: String = ""): List<String> {
        val base = API_SETS[id] ?: listOf(apiOf(id))
        val cur = current.trim()
        return if (cur.isNotEmpty() && cur !in base) base + cur else base
    }

    val byId: Map<String, ProviderInfo> = all.associateBy { it.id }

    fun find(id: String): ProviderInfo = byId[id] ?: custom

    private fun p(
        id: String,
        name: String,
        @DrawableRes logoRes: Int,
        mono: Boolean,
        defaultEndpoint: String,
        endpoints: List<String> = emptyList(),
        @DrawableRes logoResDark: Int = 0,
        /**
         * 该服务商**确定的**思考参数写法（2026-09-12）：新增该服务商时直接按它预置
         * `ProviderConfig.reasoningFormat`，用户不必自己选。写法来源 = pi `thinkingFormat`
         * 枚举 + Operit 各 Provider 类；拿不准的一律 NONE（不发参数，绝不猜）。
         */
        reasoningFormat: ReasoningFormat = ReasoningFormat.NONE,
        /** 档位词表/预算覆盖（null = 用格式默认）；`ThinkingLevels()` = 该家不支持档位调节 */
        thinkingLevels: ThinkingLevels? = null) = ProviderInfo(
        id, name, logoRes, mono, defaultEndpoint,
        if (endpoints.isEmpty()) listOf(defaultEndpoint) else endpoints,
        logoResDark,
        reasoningFormat,
        thinkingLevels,
    )
}
