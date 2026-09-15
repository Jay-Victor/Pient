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

    /** 会话记录的宿主落点（pi 给的是 guest 绝对路径，如 /root/.pi/agent/sessions/xx.jsonl） */
    fun hostSessionFile(context: Context, guestPath: String): File =
        File(PiRuntime.rootfsDir(context), guestPath.trimStart('/'))

    /**
     * 删除 pi 侧的会话记录（2026-09-16）：Pient 删会话时同步清掉那份 jsonl。
     *
     * 不清的后果（用户点名要修的缺口）：agent 目录里越堆越多孤儿会话文件 —— 一旦会话记录被
     * 重建映射（或画布/fork 重新绑定），旧文件里的上下文又被拉回来，等于「删了没删干净」。
     * 返回是否真的删掉了一个文件（文件本就不存在时返回 false，不算失败）。
     */
    fun deleteSessionFile(context: Context, guestPath: String): Boolean = runCatching {
        val f = hostSessionFile(context, guestPath)
        f.exists() && f.delete()
    }.getOrDefault(false)

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
            // api：页面显式选过就用它，否则用事实表（= pi-ai 的 provider 级默认值）
            pj.put("api", c.apiType.trim().ifBlank { ProviderCatalog.apiOf(c.providerId) })
            // radius = pi 的 OAuth 网关：provider 级要写 `oauth` 标记（docs/models.md「Provider Configuration」）
            if (c.providerId == "radius") pj.put("oauth", "radius")
            reasoningCompat(c.reasoningFormat)?.let { compat ->
                pj.put("compat", JSONObject(compat))
            }
            val models = JSONArray()
            c.models.forEach { entry -> models.put(modelJson(entry, c)) }
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
                // 只删**我们写的那种** api_key 条目：auth.json 里还可能有别的形态（pi 自己写的带 env 的
                // api_key、或别的工具留下的条目）—— 那些不是 Pient 写的，一律不动，
                // 免得「把输入框清空」顺手删掉别人的凭据。
                val cur = root.optJSONObject(c.providerId)
                if (cur != null && cur.optString("type") == "api_key" && !cur.has("env")) {
                    root.remove(c.providerId)
                }
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
                    // 页面读回的「API 类型」= 文件里那一份（用户在页面上选过 / 手改过都在这里）
                    apiType = pj.optString("api", ""),
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

    /**
     * 单个模型的 JSON（页面字段 → pi 字段）。
     * 模型条目语法：`id` 或 **`id=别名`**（别名写进 pi 的 `models[].name` —— 它用作 `--model` 匹配
     * 与副标题展示；`id` 本身才是发给服务商的东西，两者不要混）。
     */
    private fun modelJson(entry: String, c: ProviderConfig): JSONObject {
        val id = entry.substringBefore('=').trim()
        val alias = entry.substringAfter('=', "").trim()
        val m = JSONObject().put("id", id)
        if (alias.isNotEmpty()) m.put("name", alias)
        c.ctxLenK.trim().toIntOrNull()?.takeIf { it > 0 }?.let { m.put("contextWindow", it * 1000) }
        c.maxOutK.trim().toIntOrNull()?.takeIf { it > 0 }?.let { m.put("maxTokens", it * 1000) }
        val input = JSONArray().put("text")
        if (c.imageDirectEnabled) input.put("image")
        m.put("input", input)
        // 思考：pi 用 `model.reasoning` 标记「支持扩展思考」，不写 = 不支持。
        // 页面选了**具体写法**（非 NONE / AUTO）就标上；AUTO（按模型名推断）与 NONE 留给 pi 自己判 —— 
        // 内置目录里已有的事实不该被我们覆盖成 false（那会把思考能力关掉）。
        if (c.reasoningFormat != ReasoningFormat.NONE && c.reasoningFormat != ReasoningFormat.AUTO) {
            m.put("reasoning", true)
        }
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

    /** 模型清单 → 页面输入框口径：`id`，别名不同时写 `id=别名`（与 [modelJson] 同一套语法） */
    private fun buildModelList(models: JSONArray?): String {
        if (models == null) return ""
        val ids = ArrayList<String>()
        for (i in 0 until models.length()) {
            val o = models.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            if (id.isBlank()) continue
            val name = o.optString("name", "").trim()
            ids += if (name.isNotEmpty() && name != id) "$id=$name" else id
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
