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
 * - 模型列表为原型 mock（按服务商代表性模型预填）。
 */
data class ProviderInfo(
    val id: String,
    val name: String,
    @DrawableRes val logoRes: Int,
    val monoLogo: Boolean,
    val defaultEndpoint: String,
    val endpoints: List<String>,
    val models: List<String>,
    /** 暗色主题专用 logo（0 = 复用 logoRes）。用于固有色在暗色卡片上不可见的图标 */
    @DrawableRes val logoResDark: Int = 0,
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
        models = emptyList(),
    )

    val all: List<ProviderInfo> = listOf(
        // ── OpenAI 系 ──
        p("openai", "OpenAI", R.drawable.provider_openai, mono = true,
            "https://api.openai.com/v1",
            listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna", "gpt-5.6", "gpt-5.6-mini")),
        p("openai-codex", "OpenAI Codex", R.drawable.provider_openai, mono = true,
            "https://chatgpt.com/backend-api",
            listOf("gpt-5.6-codex", "gpt-5.3-codex-spark", "gpt-5.2-codex-high")),
        p("azure-openai-responses", "Azure OpenAI", R.drawable.provider_azure, mono = true,
            "https://<resource>.openai.azure.com/openai",
            listOf("gpt-5.6-sol", "gpt-5.4")),
        // ── Anthropic 系 ──
        p("anthropic", "Anthropic", R.drawable.provider_anthropic, mono = true,
            "https://api.anthropic.com",
            listOf("claude-sonnet-4-6", "claude-opus-4-6-thinking", "claude-opus-4-5-thinking", "claude-haiku-4-5")),
        p("amazon-bedrock", "Amazon Bedrock", R.drawable.provider_amazon_bedrock, mono = false,
            "https://bedrock-runtime.<region>.amazonaws.com",
            listOf("eu.anthropic.claude-opus-4-6-v1", "anthropic.claude-sonnet-4-6-v1"),
            logoResDark = R.drawable.provider_amazon_bedrock_dark),
        // ── Google 系 ──
        p("google", "Google", R.drawable.provider_google, mono = false,
            "https://generativelanguage.googleapis.com/v1beta",
            listOf("gemini-3.1-pro-preview", "gemini-3.1-pro-preview-customtools", "gemini-3.1-flash-lite-preview")),
        p("google-vertex", "Google Vertex AI", R.drawable.provider_google, mono = false,
            "https://<location>-aiplatform.googleapis.com/v1",
            listOf("gemini-3.1-pro-preview", "gemini-3.1-flash-lite-preview")),
        // ── 国内大模型 ──
        p("deepseek", "DeepSeek", R.drawable.provider_deepseek, mono = false,
            "https://api.deepseek.com",
            listOf("deepseek-v4-pro", "deepseek-v4-flash", "deepseek-chat")),
        p("kimi-coding", "Kimi For Coding", R.drawable.provider_kimi_coding, mono = false,
            "https://api.kimi.com/coding",
            listOf("kimi-for-coding", "kimi-latest"),
            logoResDark = R.drawable.provider_kimi_coding_dark),
        p("moonshotai", "Moonshot AI", R.drawable.provider_moonshot, mono = true,
            "https://api.moonshot.ai/v1",
            listOf("kimi-k2", "kimi-latest")),
        p("moonshotai-cn", "Moonshot AI CN", R.drawable.provider_moonshot, mono = true,
            "https://api.moonshot.cn/v1",
            listOf("kimi-k2", "kimi-latest")),
        p("minimax", "MiniMax", R.drawable.provider_minimax, mono = true,
            "https://api.minimax.io/anthropic",
            listOf("minimax-m2.5", "minimax-m2.5-free")),
        p("minimax-cn", "MiniMax CN", R.drawable.provider_minimax, mono = true,
            "https://api.minimaxi.com/anthropic",
            listOf("minimax-m2.5", "minimax-m2.5-free")),
        p("ant-ling", "Ant Ling", R.drawable.provider_ant_ling, mono = true,
            "https://api.ant-ling.com/v1",
            listOf("Ling-2.6-flash", "Ling-2.6-1T", "Ring-2.6-1T")),
        p("zai", "Z.AI", R.drawable.provider_zai, mono = true,
            "https://api.z.ai/api/coding/paas/v4",
            listOf("glm-5", "glm-4.6")),
        p("zai-coding-cn", "Z.AI Coding CN", R.drawable.provider_zai, mono = true,
            "https://open.bigmodel.cn/api/coding/paas/v4",
            listOf("glm-5", "glm-4.6")),
        p("qwen-token-plan", "Qwen Token Plan", R.drawable.provider_qwen, mono = true,
            "https://token-plan.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1",
            listOf("qwen3.8-max", "qwen3.8-max-preview", "qwen-chat-template")),
        p("qwen-token-plan-cn", "Qwen Token Plan CN", R.drawable.provider_qwen, mono = true,
            "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
            listOf("qwen3.8-max", "qwen3.8-max-preview")),
        p("qwen-token-plan-individual", "Qwen Token Plan Individual", R.drawable.provider_qwen, mono = true,
            "https://token-plan.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1",
            listOf("qwen3.8-max", "qwen-chat-template")),
        p("xiaomi", "Xiaomi", R.drawable.provider_xiaomi, mono = true,
            "https://api.xiaomimimo.com/v1",
            listOf("mimo-v2-omni", "mimo-v2-flash")),
        p("xiaomi-token-plan-ams", "Xiaomi Token Plan AMS", R.drawable.provider_xiaomi, mono = true,
            "https://token-plan-ams.xiaomimimo.com/v1",
            listOf("mimo-v2-omni", "mimo-v2-flash")),
        p("xiaomi-token-plan-cn", "Xiaomi Token Plan CN", R.drawable.provider_xiaomi, mono = true,
            "https://token-plan-cn.xiaomimimo.com/v1",
            listOf("mimo-v2-omni", "mimo-v2-flash")),
        p("xiaomi-token-plan-sgp", "Xiaomi Token Plan SGP", R.drawable.provider_xiaomi, mono = true,
            "https://token-plan-sgp.xiaomimimo.com/v1",
            listOf("mimo-v2-omni", "mimo-v2-flash")),
        // ── 海外大模型 ──
        p("mistral", "Mistral", R.drawable.provider_mistral, mono = false,
            "https://api.mistral.ai",
            listOf("mistral-medium-3.5", "mistral-small-latest", "mistral-small-2603")),
        p("xai", "xAI", R.drawable.provider_xai, mono = true,
            "https://api.x.ai/v1",
            listOf("grok-4", "grok-3")),
        p("groq", "Groq", R.drawable.provider_groq, mono = false,
            "https://api.groq.com/openai/v1",
            listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant")),
        // ── 聚合 / 网关 / 推理平台 ──
        p("openrouter", "OpenRouter", R.drawable.provider_openrouter, mono = true,
            "https://openrouter.ai/api/v1",
            listOf("openrouter/fusion", "openrouter/auto", "anthropic/claude-sonnet-4-6")),
        p("github-copilot", "GitHub Copilot", R.drawable.provider_github_copilot, mono = true,
            "https://api.individual.githubcopilot.com",
            listOf("gpt-5.1-codex", "gpt-5.1-codex-max", "claude-sonnet-4-6")),
        p("cloudflare-ai-gateway", "Cloudflare AI Gateway", R.drawable.provider_cloudflare, mono = false,
            "https://gateway.ai.cloudflare.com/v1/{ACCOUNT_ID}/{GATEWAY_ID}/anthropic",
            endpoints = listOf(
                "https://gateway.ai.cloudflare.com/v1/{ACCOUNT_ID}/{GATEWAY_ID}/anthropic",
                "https://gateway.ai.cloudflare.com/v1/{ACCOUNT_ID}/{GATEWAY_ID}/openai",
                "https://gateway.ai.cloudflare.com/v1/{ACCOUNT_ID}/{GATEWAY_ID}/compat",
            ),
            models = listOf("claude-sonnet-4-6", "gpt-5.6", "deepseek-v4-pro")),
        p("cloudflare-workers-ai", "Cloudflare Workers AI", R.drawable.provider_cloudflare, mono = false,
            "https://api.cloudflare.com/client/v4/accounts/{CLOUDFLARE_ACCOUNT_ID}/ai/v1",
            listOf("@cf/meta/llama-3.3-70b-instruct-fp8-fast")),
        p("vercel-ai-gateway", "Vercel AI Gateway", R.drawable.provider_vercel, mono = true,
            "https://ai-gateway.vercel.sh",
            listOf("gpt-5.6", "claude-sonnet-4-6")),
        p("fireworks", "Fireworks", R.drawable.provider_fireworks, mono = false,
            "https://api.fireworks.ai/inference",
            listOf("fireworks-llama-v3p3-70b", "deepseek-v3")),
        p("together", "Together", R.drawable.provider_together, mono = false,
            "https://api.together.ai/v1",
            listOf("llama-3.3-70b-instruct-turbo", "deepseek-v3")),
        p("huggingface", "Hugging Face", R.drawable.provider_huggingface, mono = false,
            "https://router.huggingface.co/v1",
            listOf("Qwen/Qwen2.5-72B-Instruct", "meta-llama/Llama-3.3-70B-Instruct")),
        p("cerebras", "Cerebras", R.drawable.provider_cerebras, mono = false,
            "https://api.cerebras.ai/v1",
            listOf("llama-3.3-70b", "llama-3.1-8b")),
        p("nvidia", "NVIDIA", R.drawable.provider_nvidia, mono = false,
            "https://integrate.api.nvidia.com/v1",
            listOf("nvidia/llama-3.3-nemotron-super-49b-v1")),
        p("baseten", "Baseten", R.drawable.provider_baseten, mono = true,
            "https://inference.baseten.co/v1",
            listOf("llama-3.3-70b-instruct", "mistral-7b")),
        p("opencode", "OpenCode Zen", R.drawable.provider_opencode, mono = true,
            "https://opencode.ai/zen",
            listOf("opencode/fusion", "opencode/auto")),
        p("opencode-go", "OpenCode Go", R.drawable.provider_opencode, mono = true,
            "https://opencode.ai/zen",
            listOf("opencode/fusion", "gpt-5.6-sol")),
    )

    val byId: Map<String, ProviderInfo> = all.associateBy { it.id }

    fun find(id: String): ProviderInfo = byId[id] ?: custom

    private fun p(
        id: String,
        name: String,
        @DrawableRes logoRes: Int,
        mono: Boolean,
        defaultEndpoint: String,
        models: List<String> = emptyList(),
        endpoints: List<String> = emptyList(),
        @DrawableRes logoResDark: Int = 0,
    ) = ProviderInfo(
        id, name, logoRes, mono, defaultEndpoint,
        if (endpoints.isEmpty()) listOf(defaultEndpoint) else endpoints,
        models,
        logoResDark,
    )
}
