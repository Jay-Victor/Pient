package com.pient.app.data

import android.content.Context
import android.util.Log
import com.pient.app.runtime.PiRuntime
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * **pi 原生配置文件**的读写（2026-09-14 用户拍板：`~/.pi/agent/` 下的三份文件是配置页的唯一真相源）。
 *
 * 落点：`<rootfs>/root/.pi/agent/{models.json, auth.json, settings.json}` ——
 * 因为 pi 就跑在那棵 Ubuntu 里，它的 `HOME=/root`（见 `runtime/terminal/pient-shell.sh`）。
 * 宿主侧只当普通文件读写（不需要 PRoot）；pi 每次打开 `/model` 会重读 models.json，改完无需重启。
 *
 * 三份文件的分工（对照 pi 的文档 `docs/models.md` / `docs/providers.md` / `docs/settings.md`）：
 * - `models.json` —— provider（baseUrl / api / compat）+ 模型清单（contextWindow / maxTokens /
 *   input / samplingParams）；
 * - `auth.json` —— 凭据（`{"<provider>":{"type":"api_key","key":"…"}}`），0600；
 * - `settings.json` —— 全局设置，这里合并写 `compaction`（pi 原生上下文压缩）与 `defaultTools`
 *   （不写就只激活 read/bash/edit/write 四个工具 —— grep/find/ls 要显式列出来）。
 *
 * **Pient 自己的数据**（模型定价覆盖、汇率、媒体直发开关、上下文媒体裁剪回合数、配置完成标记）
 * 不属于 pi 的概念，仍留在 `files/pient_data/ai_config.json`（`AiConfigStore` 负责），
 * 所以那个文件不会被删，只是不再是「服务商与模型」的真相源。
 */
object PiAgentFiles {

    private const val TAG = "PientConfig"

    /** pi 的七工具（写进 `settings.json` 的 `defaultTools`；不写就只激活其中四个） */
    val PI_DEFAULT_TOOLS = listOf("read", "write", "edit", "bash", "grep", "find", "ls")

    fun agentDir(context: Context): File = File(PiRuntime.rootfsDir(context), "root/.pi/agent")
    fun modelsFile(context: Context): File = File(agentDir(context), "models.json")
    fun authFile(context: Context): File = File(agentDir(context), "auth.json")
    fun settingsFile(context: Context): File = File(agentDir(context), "settings.json")

    /** 配置文件是否已经落在 guest 里（环境没解包时为 false） */
    fun available(context: Context): Boolean = PiRuntime.rootfsReady(context)

    // ─────────────────────────── 写 ───────────────────────────

    /**
     * 写 `models.json`：逐个 provider 落 baseUrl / api / 模型清单。
     * `api` 取服务商目录的事实表（[ProviderCatalog.apiOf]）—— pi 的 api 类型是 per-model 的，
     * 但 Pient 页面是按服务商配置的，所以写在 provider 级（模型级仍可在 JSON 里单独覆盖）。
     */
    fun writeModels(context: Context, configs: Collection<ProviderConfig>): Boolean = runCatching {
        val root = JSONObject()
        val providers = JSONObject()
        configs.forEach { c ->
            if (c.providerId.isBlank()) return@forEach
            val pj = JSONObject()
            if (c.endpoint.isNotBlank()) pj.put("baseUrl", c.endpoint.trim())
            pj.put("api", ProviderCatalog.apiOf(c.providerId))
            reasoningCompat(c.reasoningFormat)?.let { compat ->
                pj.put("compat", JSONObject(compat))
            }
            val models = JSONArray()
            c.models.forEach { id -> models.put(modelJson(id, c)) }
            pj.put("models", models)
            providers.put(c.providerId, pj)
        }
        root.put("providers", providers)
        write(modelsFile(context), root.toString(2))
        Log.i(TAG, "models.json 已写入：${configs.size} 个服务商 → ${modelsFile(context).absolutePath}")
        true
    }.getOrElse {
        Log.w(TAG, "models.json 写入失败：${it.message}")
        false
    }

    /** 写 `auth.json`：API key 一律用 `type=api_key` 的形态（与 pi `/login` 落盘的一致） */
    fun writeAuth(context: Context, configs: Collection<ProviderConfig>): Boolean = runCatching {
        // 保留文件里已有的其它凭据（例如 pi 自己 /login 存的 OAuth）——只 upsert 我们的
        val root = readJson(authFile(context)) ?: JSONObject()
        configs.forEach { c ->
            if (c.providerId.isBlank()) return@forEach
            val key = c.apiKey.trim()
            if (key.isBlank()) {
                root.remove(c.providerId)
            } else {
                root.put(c.providerId, JSONObject().put("type", "api_key").put("key", key))
            }
        }
        write(authFile(context), root.toString(2))
        Log.i(TAG, "auth.json 已写入：${configs.count { it.apiKey.isNotBlank() }} 个凭据")
        true
    }.getOrElse {
        Log.w(TAG, "auth.json 写入失败：${it.message}")
        false
    }

    /**
     * 合并写 `settings.json`：只动我们负责的键（`compaction` / `defaultTools`），
     * 其余键（pi 自己写的 `shellPath`、`skills`、`packages` 等）原样保留。
     * `compaction` 取配置页里第一家服务商的设置（页面是逐服务商编辑的，pi 侧是全局一份）。
     */
    fun writeSettings(context: Context, compactionSource: ProviderConfig?): Boolean = runCatching {
        val root = readJson(settingsFile(context)) ?: JSONObject()
        if (compactionSource != null) {
            root.put(
                "compaction",
                JSONObject()
                    .put("enabled", compactionSource.compactionEnabled)
                    .put("reserveTokens", compactionSource.reserveTokensValue)
                    .put("keepRecentTokens", compactionSource.keepRecentTokensValue),
            )
        }
        root.put("defaultTools", JSONArray(PI_DEFAULT_TOOLS))
        // 项目信任（2026-09-15）：pi 的**项目级资源**（`.pi/skills`、`.pi/settings.json` 的 packages、
        // `.pi/extensions`）默认要先经用户交互确认「信任这个项目」才会加载；RPC 模式下没有人能回答
        // 那个提问 → 项目级技能/插件**静默不生效**（表现为"装到项目了但 AI 看不到"）。
        // Pient 的项目都是应用自己创建/绑定的（不存在别人仓库那种风险），所以把这条口径固定成
        // 「总是信任」；用户若在桌面 pi 里显式设过别的值，这里不覆盖。
        if (!root.has("defaultProjectTrust")) root.put("defaultProjectTrust", "always")
        write(settingsFile(context), root.toString(2))
        Log.i(TAG, "settings.json 已合并写入（compaction + defaultTools=${PI_DEFAULT_TOOLS.size} 项）")
        true
    }.getOrElse {
        Log.w(TAG, "settings.json 写入失败：${it.message}")
        false
    }

    // ─────────────────────────── 读 ───────────────────────────

    /** 读三份文件 → (每个 provider 的字段, 该 provider 的 apiKey)；文件不存在返回空 */
    fun read(context: Context): Pair<Map<String, ProviderConfig>, Map<String, String>> {
        val configs = LinkedHashMap<String, ProviderConfig>()
        val keys = LinkedHashMap<String, String>()
        val compaction = readJson(settingsFile(context))?.optJSONObject("compaction")

        readJson(modelsFile(context))?.optJSONObject("providers")?.let { providers ->
            for (id in providers.keys()) {
                val pj = providers.optJSONObject(id) ?: continue
                val first = pj.optJSONArray("models")?.optJSONObject(0)
                configs[id] = ProviderConfig(
                    providerId = id,
                    endpoint = pj.optString("baseUrl", ""),
                    apiKey = "",
                    modelList = buildModelList(pj.optJSONArray("models")),
                    ctxLenK = kTokens(first?.optInt("contextWindow", 0) ?: 0),
                    maxOutK = kTokens(first?.optInt("maxTokens", 0) ?: 0),
                    tempEnabled = first?.optJSONObject("samplingParams")?.has("temperature") == true,
                    tempValue = first?.optJSONObject("samplingParams")?.opt("temperature")?.toString() ?: "1.0",
                    topKEnabled = first?.optJSONObject("samplingParams")?.has("top_k") == true,
                    topKValue = first?.optJSONObject("samplingParams")?.opt("top_k")?.toString() ?: "0",
                    topPEnabled = first?.optJSONObject("samplingParams")?.has("top_p") == true,
                    topPValue = first?.optJSONObject("samplingParams")?.opt("top_p")?.toString() ?: "1.0",
                    imageDirectEnabled = first?.optJSONArray("input")?.let { arr ->
                        (0 until arr.length()).any { arr.optString(it) == "image" }
                    } ?: false,
                    reasoningFormat = reasoningFormatOf(pj.optJSONObject("compat")),
                    compactionEnabled = compaction?.optBoolean("enabled", ContextPolicy.DEFAULT_COMPACTION_ENABLED)
                        ?: ContextPolicy.DEFAULT_COMPACTION_ENABLED,
                    keepRecentTokens = (compaction?.optInt("keepRecentTokens")
                        ?: ContextPolicy.DEFAULT_KEEP_RECENT_TOKENS).toString(),
                    reserveTokens = (compaction?.optInt("reserveTokens")
                        ?: ContextPolicy.DEFAULT_RESERVE_TOKENS).toString(),
                )
            }
        }
        readJson(authFile(context))?.let { auth ->
            for (id in auth.keys()) {
                val key = auth.optJSONObject(id)?.optString("key", "").orEmpty()
                if (key.isNotBlank()) keys[id] = key
            }
        }
        return configs to keys
    }

    // ─────────────────────────── 内部 ───────────────────────────

    /** 单个模型的 JSON（页面字段 → pi 字段） */
    private fun modelJson(id: String, c: ProviderConfig): JSONObject {
        val m = JSONObject().put("id", id)
        c.ctxLenK.trim().toIntOrNull()?.takeIf { it > 0 }?.let { m.put("contextWindow", it * 1000) }
        c.maxOutK.trim().toIntOrNull()?.takeIf { it > 0 }?.let { m.put("maxTokens", it * 1000) }
        val input = JSONArray().put("text")
        if (c.imageDirectEnabled) input.put("image")
        m.put("input", input)
        val sampling = JSONObject()
        if (c.tempEnabled) sampling.put("temperature", c.tempValue.trim().toDoubleOrNull() ?: 1.0)
        if (c.topKEnabled) sampling.put("top_k", c.topKValue.trim().toIntOrNull() ?: 0)
        if (c.topPEnabled) sampling.put("top_p", c.topPValue.trim().toDoubleOrNull() ?: 1.0)
        if (sampling.length() > 0) m.put("samplingParams", sampling)
        return m
    }

    /** 思考写法 → pi 的 `compat.thinkingFormat`（拿不准的返回 null = 不写这个键） */
    private fun reasoningCompat(format: ReasoningFormat): Map<String, Any>? = when (format) {
        ReasoningFormat.OPENAI -> mapOf("thinkingFormat" to "reasoning_effort")
        ReasoningFormat.DEEPSEEK -> mapOf("thinkingFormat" to "deepseek")
        ReasoningFormat.ZAI -> mapOf("thinkingFormat" to "zai")
        ReasoningFormat.QWEN -> mapOf("thinkingFormat" to "qwen")
        ReasoningFormat.OPENROUTER -> mapOf("thinkingFormat" to "openrouter")
        // 硅基流动 = enable_thinking + thinking_budget → 走 pi 的 qwen 写法 + 顶层预算字段
        ReasoningFormat.SILICONFLOW -> mapOf(
            "thinkingFormat" to "qwen",
            "thinkingTokenBudgetField" to "thinking_budget",
        )
        // ANTHROPIC / AUTO / NONE：anthropic-messages 有自己的 thinking 块，其余交给 pi 默认
        else -> null
    }

    private fun reasoningFormatOf(compat: JSONObject?): ReasoningFormat {
        val wire = compat?.optString("thinkingFormat", "").orEmpty()
        return when (wire) {
            "reasoning_effort" -> ReasoningFormat.OPENAI
            "deepseek" -> ReasoningFormat.DEEPSEEK
            "zai" -> ReasoningFormat.ZAI
            "qwen" -> if (compat?.has("thinkingTokenBudgetField") == true) {
                ReasoningFormat.SILICONFLOW
            } else {
                ReasoningFormat.QWEN
            }
            "openrouter" -> ReasoningFormat.OPENROUTER
            else -> ReasoningFormat.AUTO
        }
    }

    private fun buildModelList(models: JSONArray?): String {
        if (models == null) return ""
        val ids = ArrayList<String>()
        for (i in 0 until models.length()) {
            models.optJSONObject(i)?.optString("id", "")?.takeIf { it.isNotBlank() }?.let { ids += it }
        }
        return ids.joinToString(";")
    }

    private fun kTokens(value: Int): String = if (value > 0) (value / 1000).toString() else "200"

    private fun readJson(f: File): JSONObject? = runCatching {
        if (f.isFile) JSONObject(f.readText()) else null
    }.getOrNull()

    /** 原子写（临时文件 + rename），目录不存在时创建 */
    private fun write(f: File, text: String) {
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) {
            f.writeText(text)
            tmp.delete()
        }
    }
}
