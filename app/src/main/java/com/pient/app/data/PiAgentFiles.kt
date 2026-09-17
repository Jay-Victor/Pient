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
     * 返回**内容是否真有变化**（不是「写成功」）—— 供配置页决定要不要让 pi 通道重读，见 [write]。
     */
    fun writeModels(context: Context, configs: Collection<ProviderConfig>): Boolean = runCatching {
        // 旧文件：用来**保留应用不管理的模型字段**（`thinkingLevelMap` / `cost` / pi 将来新增的键）。
        // 旧实现整条重建 —— 用户手写的 `thinkingLevelMap`（砍档、加 xhigh/max）撞上应用启动那次落盘
        // 就被静默抹掉（2026-09-17 真机实测：夹具里的映射开机后消失）。
        val oldProviders = readJson(modelsFile(context))?.optJSONObject("providers")
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
            // 分流（2026-09-17）：pi 内置目录里**有**这个 id → 写 `modelOverrides[id]`（逐字段覆盖，
            // 目录里的 `thinkingLevelMap`/`cost`/`name`/`api`/`baseUrl` 全部保住）；目录里没有
            // （用户自建模型）→ 仍写整条 `models[]`。原因见 [catalogModels]。
            val models = JSONArray()
            val overrides = JSONObject()
            val oldPj = oldProviders?.optJSONObject(c.providerId)
            val oldModels = oldPj?.optJSONArray("models")
            val oldOverrides = oldPj?.optJSONObject("modelOverrides")
            val known = catalogModelIds(context)[c.providerId].orEmpty()
            c.models.forEach { entry ->
                val id = entry.substringBefore('=').trim()
                if (id.isNotEmpty() && id in known) {
                    overrides.put(id, overrideJson(entry, c, oldOverrides))
                } else {
                    models.put(modelJson(entry, c, oldModels))
                }
            }
            if (models.length() > 0) pj.put("models", models)
            if (overrides.length() > 0) pj.put("modelOverrides", overrides)
            providers.put(c.providerId, pj)
        }
        root.put("providers", providers)
        val changed = write(modelsFile(context), root.toString(2))
        if (changed) Log.i(TAG, "models.json 已写入：${configs.size} 个服务商 → ${modelsFile(context).absolutePath}")
        changed
    }.getOrElse {
        Log.w(TAG, "models.json 写入失败：${it.message}")
        false
    }

    /** 写 `auth.json`：API key 一律用 `type=api_key` 的形态（与 pi `/login` 落盘的一致）；返回内容是否真有变化 */
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
        val changed = write(authFile(context), root.toString(2))
        if (changed) Log.i(TAG, "auth.json 已写入：${configs.count { it.apiKey.isNotBlank() }} 个凭据")
        changed
    }.getOrElse {
        Log.w(TAG, "auth.json 写入失败：${it.message}")
        false
    }

    /**
     * 合并写 `settings.json`：只动我们负责的键（`compaction` / `defaultTools`），
     * 其余键（pi 自己写的 `shellPath`、`skills`、`packages` 等）原样保留。
     * `compaction` 取配置页里第一家服务商的设置（页面是逐服务商编辑的，pi 侧是全局一份；页面侧由
     * `AiConfigStore.normalizeGlobals()` + 配置页的「全局四项」镜像保证各家一致）。
     * 返回**内容是否真有变化**（不是「写成功」）—— 见 [write]。
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
        val changed = write(settingsFile(context), root.toString(2))
        if (changed) Log.i(TAG, "settings.json 已合并写入（compaction + defaultTools=${PI_DEFAULT_TOOLS.size} 项）")
        changed
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
                configs[id] = ProviderConfig(
                    providerId = id,
                    endpoint = pj.optString("baseUrl", ""),
                    // 页面读回的「API 类型」= 文件里那一份（用户在页面上选过 / 手改过都在这里）
                    apiType = pj.optString("api", ""),
                    apiKey = "",
                    modelList = buildModelList(pj.optJSONArray("models"), pj.optJSONObject("modelOverrides")),
                    // 窗口 / 识图 / 采样在 pi 里都是 **per-model** 的（`models[].contextWindow` 等）：
                    // 逐条读进 modelSettings；卡面上那三个字段是「新加入列表的模型的默认值」，
                    // pi 文件里没有这个概念 —— 由 ai_config.json 里存着的那份填（见 loadExtras）。
                    // 旧实现取 `models[0]` 当整家的值：多模型时页面只看得到第一个、改一次全覆盖。
                    modelSettings = modelSettingsOf(pj.optJSONArray("models")) +
                        modelSettingsOf(pj.optJSONObject("modelOverrides")),
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
     * 应用自己管理的 `models[]` 键：写盘时按页面值重写（留空 = 不写，所以是**先清后写**，不是合并）。
     * 不在表里的键（`thinkingLevelMap` / `cost` / pi 将来新增的）**原样保留** —— 见 [modelJson]。
     */
    private val MANAGED_MODEL_KEYS = setOf(
        "id", "name", "contextWindow", "maxTokens", "input", "reasoning", "samplingParams", "compat",
    )

    /**
     * `modelOverrides` 条目里应用自己管理的键（= [MANAGED_MODEL_KEYS] 去掉 id —— 覆盖对象的 id 是键，
     * api / baseUrl 则由内置目录那份提供）。
     */
    private val MANAGED_OVERRIDE_KEYS = setOf(
        "name", "contextWindow", "maxTokens", "input", "reasoning", "samplingParams", "compat",
    )

    /** 目录里某个模型的档位相关事实（pi 的 `getSupportedThinkingLevels` 只看这两样） */
    data class CatalogModel(val reasoning: Boolean, val thinkingLevelMap: Map<String, String?>)

    /** pi 内置目录（进程内缓存一次）：providerId → modelId → 事实 */
    private var catalogCache: Map<String, Map<String, CatalogModel>>? = null

    /**
     * pi 内置目录里有定义的模型 id（`providerId → ids`），用来决定一个模型**怎么写进 models.json**：
     *
     * - 目录里有 → `modelOverrides[id]`：pi 的 `applyModelOverride` 是逐字段合并（`override.x ?? model.x`，
     *   `thinkingLevelMap` 还是浅合并）⇒ 目录里的档位表 / cost / name / api / baseUrl **全部保住**；
     * - 目录里没有（用户自建模型）→ 仍写整条 `models[]`。
     *
     * 为什么不能一律写条目：`applyModelsJson` 对同 id 的条目是**整体替换**，而 `modelFromJson` 里
     * `thinkingLevelMap: definition.thinkingLevelMap` / `cost ?? {0,0,0,0}` / `contextWindow ?? 128000`
     * **都不回落到目录**（2026-09-17 对着 pi 0.85.1 源码 + 设备 rootfs 里 39 个目录 JSON 核对）——
     * 应用写过的 `deepseek-v4-pro` 就这样从目录里的 3 档（off/high/max）被抹成 5 档。
     *
     * 读不到目录（pi 没装 / 布局变了）→ 空表 = 退回「整条写」的老行为，不会写坏文件。
     */
    private fun catalogModels(context: Context): Map<String, Map<String, CatalogModel>> = catalogCache ?: runCatching {
        val root = PiRuntime.rootfsDir(context)
        val candidates = listOf(
            "usr/lib/node_modules/@earendil-works/pi-coding-agent/node_modules/@earendil-works/pi-ai/dist/providers/data",
            "usr/lib/node_modules/@earendil-works/pi-ai/dist/providers/data",
        )
        val dataDir = candidates.map { File(root, it) }.firstOrNull { it.isDirectory }
            ?: findProvidersDataDir(File(root, "usr/lib/node_modules"), 0)
        val out = HashMap<String, Map<String, CatalogModel>>()
        // ① 随包目录（pi-ai 包里的 providers/data/*.json，随版本冻结）
        dataDir?.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach { f ->
            runCatching {
                val o = JSONObject(f.readText())
                val models = HashMap<String, CatalogModel>()
                for (api in o.keys()) {
                    val group = o.optJSONObject(api) ?: continue
                    for (id in group.keys()) {
                        val m = group.optJSONObject(id) ?: continue
                        if (id.isBlank()) continue
                        models[id] = CatalogModel(m.optBoolean("reasoning", false), thinkingMapOf(m))
                    }
                }
                if (models.isNotEmpty()) out[f.name.removeSuffix(".json")] = models
            }
        }
        // ② **联网刷新后的目录（`models-store.json`，pi 真正用的就是它）** 覆盖随包那份。
        //    2026-09-17 实测踩坑：V4.1 Flash（id `deepseek-flash`）**只存在于刷新目录**，
        //    随包那份没有 ⇒ 只读随包会把用户的模型误判成「自建模型」，连它的档位表一起丢掉
        //    （表现：本该 3 停位的模型画出默认的 4 停位）。
        var storeCount = 0
        runCatching {
            val store = readJson(File(agentDir(context), "models-store.json")) ?: return@runCatching
            for (pid in store.keys()) {
                val arr = store.optJSONObject(pid)?.optJSONArray("models") ?: continue
                val merged = HashMap(out[pid] ?: emptyMap())
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val id = m.optString("id", "").trim()
                    if (id.isEmpty()) continue
                    merged[id] = CatalogModel(m.optBoolean("reasoning", false), thinkingMapOf(m))
                    storeCount++
                }
                if (merged.isNotEmpty()) out[pid] = merged
            }
        }
        Log.i(
            TAG,
            "pi 目录：随包 ${dataDir?.path?.substringAfterLast('/') ?: "未找到"} + 刷新目录 $storeCount 条" +
                " ⇒ ${out.size} 家 / ${out.values.sumOf { it.size }} 个模型",
        )
        out
    }.getOrElse {
        Log.w(TAG, "读 pi 内置目录失败（按自建模型处理）：${it.message}")
        emptyMap()
    }.also { catalogCache = it }

    /** 目录里的模型 id 集（写盘分流用） */
    private fun catalogModelIds(context: Context): Map<String, Set<String>> =
        catalogModels(context).mapValues { (_, v) -> v.keys }

    /**
     * **离线算该模型的档位表**（不依赖 pi 通道）—— 与 pi 的 `getSupportedThinkingLevels`
     * （models.ts:915）逐字同规则：`!reasoning → ["off"]`；否则按 `thinkingLevelMap` 过滤
     * （值为 null = 砍掉；`xhigh`/`max` 只有显式给了才有），再叠上 [CATALOG_FIXES] 勘误。
     *
     * 为什么要它：面板原来只认 pi 对「通道**当前**跑的模型」的回答，pi 没答（没通道 / 刚切完模型、
     * 通道还没重启）就退回一张万能 4 档回退表 —— 于是「明明 3 档的模型画出 4 档」、切模型也不重画。
     * 目录里有这个模型 → 直接用目录事实；没有（自建模型）→ 用应用自己写的 `reasoning`（三态里没显式
     * 设过就跟写盘同一口径：按模型名推断写法，推断得出 = 支持）。
     */
    fun effectiveThinkingLevels(context: Context, c: ProviderConfig, entry: String): List<String> {
        val id = entry.substringBefore('=').trim()
        val cat = catalogModels(context)[c.providerId]?.get(id)
        val reasoning: Boolean
        val base: Map<String, String?>
        if (cat != null) {
            reasoning = cat.reasoning
            base = cat.thinkingLevelMap
        } else {
            val s = c.settingOf(id)
            val fmt = if (c.reasoningFormat == ReasoningFormat.AUTO) {
                AiBackend.inferReasoningFormat(id)
            } else {
                c.reasoningFormat
            }
            reasoning = s.reasoning ?: (fmt != ReasoningFormat.NONE)
            base = emptyMap()
        }
        val map = HashMap<String, String?>(base).also { it.putAll(catalogFixOf(c, id)) }
        return levelsOf(reasoning, map)
    }

    /** `thinkingLevelMap` 解析：键保留、值 `null` 记成 null（= 砍掉该档），缺席即「没写」 */
    private fun thinkingMapOf(m: JSONObject): Map<String, String?> =
        m.optJSONObject("thinkingLevelMap")?.let { mp ->
            HashMap<String, String?>().also { t ->
                for (k in mp.keys()) t[k] = if (mp.isNull(k)) null else mp.optString(k, "")
            }
        }.orEmpty()

    /** pi 的档位全集顺序（models.ts 的 EXTENDED_THINKING_LEVELS） */
    private val THINKING_LEVEL_ORDER =
        listOf("off", "minimal", "low", "medium", "high", "xhigh", "max")

    private fun levelsOf(reasoning: Boolean, map: Map<String, String?>): List<String> {
        if (!reasoning) return listOf("off")
        return THINKING_LEVEL_ORDER.filter { lv ->
            val has = map.containsKey(lv)
            when {
                has && map[lv] == null -> false
                lv == "xhigh" || lv == "max" -> has
                else -> true
            }
        }
    }

    /** 兜底：在 node_modules 里按名字找 `providers/data`（pi 的目录布局换过就别硬编码路径） */
    private fun findProvidersDataDir(dir: File, depth: Int): File? {
        if (depth > 4 || !dir.isDirectory) return null
        val kids = dir.listFiles() ?: return null
        kids.firstOrNull { it.isDirectory && it.name == "data" && it.parentFile?.name == "providers" }
            ?.let { return it }
        for (k in kids) if (k.isDirectory) findProvidersDataDir(k, depth + 1)?.let { return it }
        return null
    }

    /**
     * 单个模型的 JSON（页面字段 → pi 字段）。
     * 模型条目语法：`id` 或 **`id=别名`**（别名写进 pi 的 `models[].name` —— 它用作 `--model` 匹配
     * 与副标题展示；`id` 本身才是发给服务商的东西，两者不要混）。
     */
    private fun modelJson(entry: String, c: ProviderConfig, oldModels: JSONArray? = null): JSONObject {
        val id = entry.substringBefore('=').trim()
        val old = oldModels?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                .firstOrNull { it.optString("id").trim() == id }
        }
        val m = modelFieldsJson(entry, c, old, MANAGED_MODEL_KEYS)
        m.put("id", id)
        return m
    }

    /**
     * 目录里已有的模型 → `modelOverrides[id]` 的内容：与 [modelJson] 同一套字段，但**不写 id**
     * （id 是对象键）、也不写 api / baseUrl（这两样由目录那份提供）。
     */
    private fun overrideJson(entry: String, c: ProviderConfig, oldOverrides: JSONObject?): JSONObject {
        val id = entry.substringBefore('=').trim()
        val old = oldOverrides?.optJSONObject(id)
        val m = modelFieldsJson(entry, c, old, MANAGED_OVERRIDE_KEYS)
        // 目录勘误（见 [CATALOG_FIXES]）：pi 目录里写错的档位表，用覆盖层就地修正。
        // pi 的 applyModelOverride 对 thinkingLevelMap 是**浅合并**（{...目录, ...覆盖}），
        // 所以只写要改的那几个键：字符串 = 补/改一档，JSON null = 砍一档。
        val fix = catalogFixOf(c, id)
        if (fix.isNotEmpty()) {
            val map = JSONObject()
            // 旧文件里已有的键先留住（用户手写的、以及上一次落盘的勘误）
            old?.optJSONObject("thinkingLevelMap")?.let { o ->
                for (k in o.keys()) map.put(k, o.get(k))
            }
            for ((k, v) in fix) map.put(k, if (v == null) JSONObject.NULL else v)
            m.put("thinkingLevelMap", map)
        }
        return m
    }

    /** 该模型的勘误项（api 级规则 + 逐模型条目叠加）；空 = 不动它的档位表 */
    private fun catalogFixOf(c: ProviderConfig, modelId: String): Map<String, String?> {
        val out = LinkedHashMap<String, String?>()
        val api = c.apiType.trim().ifBlank { ProviderCatalog.apiOf(c.providerId) }
        CATALOG_FIXES_BY_API[api]?.let { out.putAll(it) }
        CATALOG_FIXES[c.providerId to modelId]?.let { out.putAll(it) }
        return out
    }

    /**
     * **pi 内置目录的勘误表**（2026-09-17 对着各家官方文档逐条核对后加）。
     *
     * pi 的档位表在 `providers/data/ 下的 *.json` 的 `thinkingLevelMap` 里，是应用唯一的事实源。
     * 核对下来总体质量很高（k3 / glm-5.3 / gpt-5.5 / kimi-k2.7-code 等逐条对上），但有几处确凿的错：
     *
     * - `deepseek-v4-pro`：pi 砍掉了 `low`，而官方 Thinking Mode 页明写 `low/high/max` 三档、
     *   且「**两个 V4 模型映射完全一致**」⇒ 补回 `low`（不补的话，用户选 low 会被 pi 夹到 high）。
     * - `MiniMax-M2.7` / `-highspeed`（官方域名与 .cn 两家）：官方 responses-create 页写
     *   *For M2.x models, reasoning cannot be disabled* ⇒ 砍掉 `off`（不砍的话面板会摆一个关不掉的开关）。
     * - `claude-fable-5` / `-5-1`（Anthropic 与 OpenRouter 两侧）：官方 effort 页 + Opus 5 迁移说明
     *   写得很清楚——thinking 能关（`{"type":"disabled"}`），只是 effort 在 xhigh/max 时不允许 ⇒
     *   恢复 `off`。Anthropic 侧 `off` 的值只被用来判「非 null」（anthropic-messages.ts:1149 固定发
     *   `{type:"disabled"}`，不发 map 里的值）；OpenRouter 侧走 `reasoning.effort = map.off ?? "none"`，
     *   所以给它写 `"none"`。
     * - Anthropic Messages 协议（api 级）：官方 effort 只有 `low/medium/high/xhigh/max`，**没有 minimal**
     *   ⇒ 无 map 的 Anthropic 模型默认 5 档里那个 `minimal` 砍掉（pi 本来也会把它回落成 low，
     *   线上行为不变，只是界面不再多一档）。
     *
     * 生效方式：写进 `modelOverrides[id].thinkingLevelMap`（只对**目录里已有的模型**生效——自建模型
     * 没有目录那份可合并）。**上游 pi 修好目录后，这里对应的条目应删掉**；每条都注了官方依据与核对日期。
     */
    private val CATALOG_FIXES: Map<Pair<String, String>, Map<String, String?>> = mapOf(
        ("deepseek" to "deepseek-v4-pro") to mapOf("low" to "low"),
        ("minimax" to "MiniMax-M2.7") to mapOf("off" to null),
        ("minimax" to "MiniMax-M2.7-highspeed") to mapOf("off" to null),
        ("minimax-cn" to "MiniMax-M2.7") to mapOf("off" to null),
        ("minimax-cn" to "MiniMax-M2.7-highspeed") to mapOf("off" to null),
        ("anthropic" to "claude-fable-5") to mapOf("off" to "off"),
        ("anthropic" to "claude-fable-5-1") to mapOf("off" to "off"),
        ("openrouter" to "anthropic/claude-fable-5") to mapOf("off" to "none"),
        ("openrouter" to "anthropic/claude-fable-5.1") to mapOf("off" to "none"),
    )

    /** api 级勘误（对某个协议下所有模型生效） */
    private val CATALOG_FIXES_BY_API: Map<String, Map<String, String?>> = mapOf(
        "anthropic-messages" to mapOf("minimal" to null),
    )

    /** 页面字段 → pi 字段（整条条目与覆盖对象共用；`id` 由调用方补） */
    private fun modelFieldsJson(
        entry: String,
        c: ProviderConfig,
        old: JSONObject?,
        managed: Set<String>,
    ): JSONObject {
        val id = entry.substringBefore('=').trim()
        val alias = entry.substringAfter('=', "").trim()
        // 逐模型参数（窗口 / 识图 / 采样）：有该模型的条目用它，没有则用卡面默认值
        val s = c.settingOf(id)
        val m = JSONObject()
        // 先按 id 搬回旧条目里**应用不管理**的字段（`thinkingLevelMap` / `cost` / pi 新增键）——
        // 应用管理的键不能这样合并（页面留空 = 不写该键，合并会把旧值留下），所以分两张表。
        old?.let { o ->
            for (k in o.keys()) if (k !in managed) m.put(k, o.get(k))
            // compat 是混合的：应用只写 thinkingFormat，其余（chatTemplateKwargs 等）留住
            o.optJSONObject("compat")?.let { oc ->
                val keep = JSONObject()
                for (k in oc.keys()) if (k != "thinkingFormat") keep.put(k, oc.get(k))
                if (keep.length() > 0) m.put("compat", keep)
            }
        }
        if (alias.isNotEmpty()) m.put("name", alias)
        s.ctxLenK.trim().toIntOrNull()?.takeIf { it > 0 }?.let { m.put("contextWindow", it * 1000) }
        s.maxOutK.trim().toIntOrNull()?.takeIf { it > 0 }?.let { m.put("maxTokens", it * 1000) }
        // 识图：**勾上**写 `["text","image"]`、**取消就整个键不写**（照 pi-web ModelsConfig 的 imageInput：`v ? ["text","image"] : undefined`）。
        // 为什么不写 `["text"]`：models.json 的条目会**整条替换**同 id 的内置目录条目 —— 写死 text 会把内置目录里
        // 「这个模型能读图」的事实盖掉，pi 的 read 工具随后就不再返回图片内容了。取消后不写 = 由 pi 自己按目录判。
        if (s.image) m.put("input", JSONArray().put("text").put("image"))
        // 思考：pi 用 `model.reasoning` 标记「支持扩展思考」，**不写 = 不支持**（`provider-composer` 的
        // `modelFromJson` 是 `definition.reasoning ?? false`，并不会回落到内置目录的既定事实）—— 旧实现
        // 把 AUTO 当「留给 pi 自己判」是错的：pi 没得判，模型会一直停在「不支持思考」。现在：
        // - 页面选了**具体写法**（非 NONE / AUTO）→ 写 true（写法在 provider 级 compat，见 writeModels）；
        // - **AUTO** → 按模型名现推 [AiBackend.inferReasoningFormat]（与界面「自动识别 → 当前生效：X」
        //   同一份规则）：推得出 → 写 true + **模型级** compat.thinkingFormat（provider 级那条是 AUTO 时没有的）；
        //   推不出 → 当 NONE 处理（不写 = pi 侧「该模型不支持思考」）。
        val fmt = if (c.reasoningFormat == ReasoningFormat.AUTO) {
            AiBackend.inferReasoningFormat(id)
        } else {
            c.reasoningFormat
        }
        // 逐模型三态：显式 开/关 优先，null 才走写法推断（= 2026-09-17 之前的旧行为）。
        // 「不支持」**要显式写出 false**：pi 侧 false 与不写等价（`definition.reasoning ?? false`），
        // 但不写会被回读成「未设置」→ 下次又跟着写法推断跑（用户的选择被静默丢掉）。
        val reasoningOn = s.reasoning ?: (fmt != ReasoningFormat.NONE)
        if (reasoningOn) {
            m.put("reasoning", true)
            if (fmt != c.reasoningFormat) {
                // 并入（不是覆盖）：旧 compat 里保留下来的键不能丢
                reasoningCompat(fmt)?.let { compat ->
                    val merged = m.optJSONObject("compat")?.let { JSONObject(it.toString()) } ?: JSONObject()
                    for (k in compat.keys) merged.put(k, compat[k])
                    m.put("compat", merged)
                }
            }
        } else if (s.reasoning == false) {
            m.put("reasoning", false)
        }
        // 采样：逐模型的值，**留空 = 不传该参数**（照 pi-web：值 undefined 就不写键）
        val sampling = JSONObject()
        s.temperature.trim().toDoubleOrNull()?.let { sampling.put("temperature", it) }
        s.topK.trim().toIntOrNull()?.let { sampling.put("top_k", it) }
        s.topP.trim().toDoubleOrNull()?.let { sampling.put("top_p", it) }
        if (sampling.length() > 0) m.put("samplingParams", sampling)
        return m
    }

    /** 思考写法 → pi 的 `compat.thinkingFormat`（拿不准的返回 null = 不写这个键）
     *
     *  `OPENAI → "openai"`（2026-09-17 修）：pi 的 models.json schema 里 thinkingFormat 的合法字面量
     *  是 `openai`；旧的 `reasoning_effort` 只在 pi 文档里出现过、代码枚举里没有。实测（用随包 pi
     *  的 `dist/core/model-config.js` + typebox 1.3.7 跑真校验）它**碰巧也能过**——compat 是三选一
     *  union 且不禁止额外键，怪值落进「OpenAI 风格」那末支，效果与 `openai` 相同；但那是运气，
     *  改用规范字面量。
     */
    private fun reasoningCompat(format: ReasoningFormat): Map<String, Any>? = when (format) {
        ReasoningFormat.OPENAI -> mapOf("thinkingFormat" to "openai")
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
            // 旧版 Pient 写过 `reasoning_effort`（读侧继续认，避免老配置被降级成 AUTO）
            "openai", "reasoning_effort" -> ReasoningFormat.OPENAI
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

    /**
     * 模型清单 → 页面输入框口径：`id`，别名不同时写 `id=别名`（与 [modelJson] 同一套语法）。
     * 两个来源都算：`models[]`（自建模型整条）+ `modelOverrides`（目录已有模型的覆盖，id 是键）。
     */
    private fun buildModelList(models: JSONArray?, overrides: JSONObject? = null): String {
        val ids = ArrayList<String>()
        if (models != null) for (i in 0 until models.length()) {
            val o = models.optJSONObject(i) ?: continue
            val id = o.optString("id", "")
            if (id.isBlank()) continue
            val name = o.optString("name", "").trim()
            ids += if (name.isNotEmpty() && name != id) "$id=$name" else id
        }
        if (overrides != null) for (id in overrides.keys()) {
            if (id.isBlank()) continue
            val name = overrides.optJSONObject(id)?.optString("name", "")?.trim().orEmpty()
            ids += if (name.isNotEmpty() && name != id) "$id=$name" else id
        }
        return ids.joinToString(";")
    }

    /**
     * 模型条目里的 tokens → 页面输入框口径（**0/缺省 = 空串**，2026-09-17 改）。
     *
     * 改理由：旧版回退 "200"/"64" 会让「页面上没填过」的模型被**默默写成 200K 上下文 /
     * 64K 输出**（保存一次即落盘），而真实窗口小的模型会被 pi 当成 200K —— 压缩触发过晚、
     * 有服务端上下文超限的风险。现在的口径：**空 = 不写该键**，由 pi 用它自己的默认
     * （models.json 未写时 pi 取 contextWindow 128000 / maxTokens 16384）。
     */
    private fun kTokens(value: Int): String = if (value > 0) (value / 1000).toString() else ""

    /**
     * `models[]` → 逐模型参数（[ModelSetting]）。
     *
     * 关键口径：**没写的键一律存成空串 / false**（= 回写时不写该键）—— 「文件里没写」与「页面上留空」
     * 因此完全等价，回写不会凭空给模型补上默认值（旧的 `?: 1.0` / `?: 0` 兜底就会补）。
     */
    private fun modelSettingsOf(models: JSONArray?): Map<String, ModelSetting> {
        if (models == null) return emptyMap()
        val out = LinkedHashMap<String, ModelSetting>()
        for (i in 0 until models.length()) {
            val o = models.optJSONObject(i) ?: continue
            val id = o.optString("id", "").trim()
            if (id.isEmpty()) continue
            val sp = o.optJSONObject("samplingParams")
            out[id] = ModelSetting(
                ctxLenK = kTokens(o.optInt("contextWindow", 0)),
                maxOutK = kTokens(o.optInt("maxTokens", 0)),
                image = o.optJSONArray("input")?.let { arr ->
                    (0 until arr.length()).any { arr.optString(it) == "image" }
                } ?: false,
                temperature = if (sp?.has("temperature") == true) sp.opt("temperature").toString() else "",
                topK = if (sp?.has("top_k") == true) sp.opt("top_k").toString() else "",
                topP = if (sp?.has("top_p") == true) sp.opt("top_p").toString() else "",
                // 三态：键在 = 显式支持/不支持（写盘时 false 也会写出来，才回读得到）；键不在 = 未设置
                reasoning = if (o.has("reasoning")) o.optBoolean("reasoning") else null,
            )
        }
        return out
    }

    /** [modelSettingsOf] 的覆盖重载：`modelOverrides` 是 `id → 字段` 的对象 */
    private fun modelSettingsOf(overrides: JSONObject?): Map<String, ModelSetting> {
        if (overrides == null) return emptyMap()
        val arr = JSONArray()
        for (id in overrides.keys()) {
            val o = overrides.optJSONObject(id) ?: continue
            arr.put(JSONObject(o.toString()).put("id", id))
        }
        return modelSettingsOf(arr)
    }

    private fun readJson(f: File): JSONObject? = runCatching {
        if (f.isFile) JSONObject(f.readText()) else null
    }.getOrNull()

    /**
     * 原子写（临时文件 + rename），目录不存在时创建。
     * 返回**内容是否真的有变化**（一样就跳过 I/O）—— 调用方（`AiConfigStore.save`）用它决定要不要
     * 让 pi 重读（pi 只在进程启动时读这些文件，见 `PiRpc.markConfigDirty`）。
     */
    private fun write(f: File, text: String): Boolean {
        val changed = !(f.isFile && f.readText() == text)
        if (!changed) return false
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) {
            f.writeText(text)
            tmp.delete()
        }
        return true
    }
}
