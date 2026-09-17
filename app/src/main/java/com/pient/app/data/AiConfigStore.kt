package com.pient.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pient.app.runtime.PiRpc
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 单个服务商的真实配置（2026-09-09 实现 AI 接入；替换原型期 mock）。
 *
 * **持久化（2026-09-14 用户拍板「pi 原生文件为唯一真相源」）**：
 * - 服务商 / 模型 / 密钥 / 思考写法 / 上下文压缩 → 写进 guest 的
 *   `~/.pi/agent/{models.json, auth.json, settings.json}`（见 [PiAgentFiles]），pi 直接读它；
 * - **Pient 自有、pi 没有对应概念**的字段（媒体直发三开关、上下文媒体裁剪回合数、
 *   手动压缩指令、模型定价覆盖、汇率、配置完成标记）→ 留在 `files/pient_data/ai_config.json`。
 */
/**
 * **单个模型**的窗口 / 识图 / 采样参数 —— 就是 pi `models.json` 里 per-model 的那几个键
 * （`contextWindow` / `maxTokens` / `input` / `samplingParams`）。
 *
 * 每个字段**留空 = 不写该键**（pi 用它自己那一层的默认：窗口 128000 / 输出 16384 / 采样交给服务商）。
 * pi-web 的 ModelsConfig 也是逐模型编辑这几个字段（`v ? ["text","image"] : undefined` 同款口径）。
 */
data class ModelSetting(
    val ctxLenK: String = "",
    val maxOutK: String = "",
    val image: Boolean = false,
    val temperature: String = "",
    val topK: String = "",
    val topP: String = "",
    /**
     * **该模型是否支持扩展思考**（pi `models[].reasoning`，三态）：
     * - `null` = 未单独设置 → 沿用「思考设置」卡那套（写法非 NONE / AUTO 按模型名推断）；
     * - `true` / `false` = 用户在铅笔浮层里显式指定（写盘为 `"reasoning": true|false`）。
     *
     * 为什么必须逐模型：pi 的档位表与请求字段都由这个标记决定（`getSupportedThinkingLevels` 里
     * `!model.reasoning → ["off"]`、openai/anthropic 请求体每条 thinking 分支都 `&& model.reasoning`）——
     * 服务商里「思考模型 + 纯文本模型」混装时，标错就会让界面开关可点、pi 真把 thinking 字段发出去。
     */
    val reasoning: Boolean? = null,
)

data class ProviderConfig(
    val providerId: String,
    val endpoint: String = "",
    val apiKey: String = "",
    /**
     * **API 类型**（pi-ai 的 `api`，2026-09-15 要求 3 加）：空串 = 跟随服务商预设（[ProviderCatalog.apiOf]）。
     * pi 的 `api` 是 per-model 的、provider 级是默认值；配置页把它做成显式旋钮，
     * 网关/自建端点（一个 URL 能说多种协议）才有得选。
     */
    val apiType: String = "",
    /** 模型列表（英文分号分隔，与配置页输入框同口径） */
    val modelList: String = "",
    // 上下文长度 / 最大输出 / 采样参数**不在这里**：pi 的 `models.json` 把它们放在**每个模型**上
    // （`models[].contextWindow` / `maxTokens` / `samplingParams`，没有服务商级这个概念）——
    // 页面改成在「模型选择列表 → 铅笔」里逐个模型设（见 [modelSettings] / [ModelSetting]）。
    // 2026-09-17 用户指出「卡面字段与逐模型浮层重复」，拍板**只留逐模型**这一处。
    /**
     * 思考参数写法（2026-09-12 真实化）：决定「思考模式开关」在线上怎么表达——
     * 关闭 = 显式禁用字面量、开启 = 显式启用（详见 [ReasoningFormat]）。
     * 落盘到 pi 的 `compat.thinkingFormat`。
     */
    val reasoningFormat: ReasoningFormat = ReasoningFormat.AUTO,

    // ── 媒体能力（照 Operit 的 direct-processing 口径；默认关 = 不直发）──
    // 口径（2026-09-14 用户拍板「照 Operit 对齐」；2026-09-17 收口为**只有图片**）：
    // - **开** = 图片转成内容部件**直发**给模型（pi RPC `prompt.images`）；
    //   **关** = 不直发，附件仍以「名称 · 路径」留在正文里，消息照常发送。
    // - 音频 / 视频两个开关**已删**：pi 的用户消息内容类型只有 text / image（`ImageContent`），
    //   音频 / 视频在 pi 通道里根本发不出去 —— 开着只会把附件行从请求里抹掉（比关着更糟）。
    // 识图开关本身也是**逐模型**的（pi `models[].input`）—— 放在「模型选择列表 → 铅笔」里设，
    // 一个服务商下「文本模型 + 视觉模型」并存时本来就得分开（见 [ModelSetting.image]）。

    // ── 上下文管理（**pi 原生口径**；写进 settings.json 的 compaction 块）──
    /** 自动压缩上下文（pi `compaction.enabled`，默认 true）；关掉后仍能手动压缩 */
    val compactionEnabled: Boolean = ContextPolicy.DEFAULT_COMPACTION_ENABLED,
    /** 摘要后保留的最近 tokens（pi `compaction.keepRecentTokens`，默认 20000） */
    val keepRecentTokens: String = ContextPolicy.DEFAULT_KEEP_RECENT_TOKENS.toString(),
    /** 给模型回复预留的 tokens（pi `compaction.reserveTokens`，默认 16384） */
    val reserveTokens: String = ContextPolicy.DEFAULT_RESERVE_TOKENS.toString(),
    /** 手动压缩时交给 pi 的指令（`compact` 的 `customInstructions`；Pient 侧字段） */
    val compactInstructions: String = "",

    /**
     * **逐模型参数**（键 = 模型 id，值见 [ModelSetting]）。
     *
     * 真源在 pi 的 `models.json`（那边本来就是 per-model）：载入时按 `models[].id` 逐条填，
     * 用户在「模型选择列表 → 铅笔」里改的就是这里。**没有条目的模型** = 文件里什么都没写
     * = pi 用自己的默认（见 [settingOf]）。
     */
    val modelSettings: Map<String, ModelSetting> = emptyMap(),
) {
    val models: List<String>
        // distinct：同一条模型写两遍会让「模型选择弹窗」的 LazyColumn key 重复（Key ... was already used → 崩溃），
        // 也会在 models.json 里落两条同 id（pi 只保留后一条）—— 这里就地收口。
        get() = modelList.split(";").map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /**
     * 某个模型**生效**的窗口 / 识图 / 采样（单一口径：pi 的 `models.json` 就是逐模型的）。
     *
     * 没有条目 = 该模型还没被编辑过 = 什么都没写 → 返回全空 = pi 用自己那一层的默认
     * （窗口 128000 / 输出 16384 / 采样交给服务商）——不发明「服务商级默认值」这种 pi 没有的概念。
     *
     * `modelId` 允许是 `id=别名` 形态（模型列表的写法），内部按 `id` 取。
     */
    fun settingOf(modelId: String): ModelSetting =
        modelSettings[modelId.substringBefore('=').trim()] ?: ModelSetting()

    /** 生效的「保留最近 tokens」（非法输入回退 pi 默认 20000） */
    val keepRecentTokensValue: Int
        get() = keepRecentTokens.trim().toIntOrNull()?.coerceIn(1000, 2_000_000)
            ?: ContextPolicy.DEFAULT_KEEP_RECENT_TOKENS

    /** 生效的「为回复预留 tokens」（非法输入回退 pi 默认 16384） */
    val reserveTokensValue: Int
        get() = reserveTokens.trim().toIntOrNull()?.coerceIn(1000, 1_000_000)
            ?: ContextPolicy.DEFAULT_RESERVE_TOKENS
}

object AiConfigStore {

    /** 已配置服务商 → 配置 */
    val configs = mutableStateMapOf<String, ProviderConfig>()

    /**
     * **显式维护的添加顺序**（下拉顺序、以及「写 settings.json 取哪家」的依据）。
     *
     * 为什么不能直接拿 `configs.keys`：`mutableStateMapOf` 是哈希序（不是插入序）—— 同一份配置在
     * 增删之后顺序会跳，页面下拉看起来「随机排序」，`values.firstOrNull()` 也不是真正的第一家。
     * 落盘在 `ai_config.json` 的 `providerOrder`；缺这份表（老配置）时按 models.json 的文件序补。
     */
    val providerOrder = mutableStateListOf<String>()

    /** 按添加顺序的服务商 id（过滤掉已删除的；表里没有的兜底追加在后面） */
    fun orderedIds(): List<String> {
        val inOrder = providerOrder.filter { it in configs }
        return inOrder + configs.keys.filter { it !in inOrder }
    }

    /** 「第一家」= 添加顺序的第一家（写 settings.json 的 compaction 用它；四处已镜像，取哪家都一样） */
    fun primaryConfig(): ProviderConfig? = orderedIds().firstOrNull()?.let { configs[it] }

    /** 载入/迁移后同步顺序表：按给定顺序补齐 + 丢掉不存在的（幂等） */
    fun syncOrder(ids: List<String> = configs.keys.toList()) {
        val keep = providerOrder.filter { it in configs }
        providerOrder.clear()
        providerOrder.addAll(keep + ids.filter { it in configs && it !in keep } + configs.keys.filter { it !in keep && it !in ids })
    }

    /** AI 是否通过连接测试（聊天页首次引导第二步；测试连接成功即置真并持久化） */
    var aiConfigured by mutableStateOf(false)

    // ── 模型定价（2026-09-11 按 Operit 的处理方式实现）──
    // 内置表（ModelPricingDefaults：assets/model_pricing.tsv）+ 用户覆盖（此处的 pricing）。
    // 覆盖键 = "provider:model"（Operit providerModel 同款键形态）；有覆盖用覆盖，否则用内置默认。
    val pricing = mutableStateMapOf<String, ModelPricing>()

    /** 美元 → 人民币汇率（内置价为 USD 的模型按此折算展示；Operit 汇率设置同款） */
    var usdToCnyRate by mutableStateOf(ModelPricingDefaults.DEFAULT_USD_TO_CNY_RATE)

    /** 覆盖键：`provider:model`（provider 小写） */
    fun pricingKey(providerId: String, model: String): String =
        "${providerId.trim().lowercase()}:${model.trim()}"

    /** 生效定价：用户覆盖 > 内置表（三级查找） */
    fun effectivePricing(providerId: String, model: String): ModelPricing =
        pricing[pricingKey(providerId, model)] ?: ModelPricingDefaults.defaultFor(providerId, model)

    fun setPricing(providerId: String, model: String, value: ModelPricing) {
        pricing[pricingKey(providerId, model)] = value
    }

    /** 恢复该模型为内置默认（清除覆盖） */
    fun clearPricing(providerId: String, model: String) {
        pricing.remove(pricingKey(providerId, model))
    }

    // ─────────────────────────── 落盘 ───────────────────────────

    /** Pient 自有数据（pi 没有对应概念的那些）——仍落 pient_data/ai_config.json */
    private fun legacyFile(context: Context) = File(context.filesDir, "pient_data/ai_config.json")

    fun load(context: Context) {
        try {
            val (fromPi, keys) = PiAgentFiles.read(context)
            if (fromPi.isEmpty() && legacyFile(context).isFile) {
                // ① 首次运行 / 迁移：旧的 ai_config.json 里还带着完整服务商配置
                if (migrateFromLegacy(context)) return
            }
            fromPi.forEach { (id, c) -> configs[id] = c.copy(apiKey = keys[id].orEmpty()) }
            syncOrder(fromPi.keys.toList())   // models.json 的文件序当初始顺序（老配置没有 providerOrder 时）
            loadExtras(context)
            syncOrder(providerOrder.toList())
            normalizeGlobals()
        } catch (e: Exception) {
            // 配置损坏：忽略（按未配置处理）
        }
    }

    /**
     * **全局四项**（压缩三参 + 压缩指令）在 pi 侧只有一份（`settings.json` 的 `compaction`；压缩指令
     * 是 Pient 侧字段，但画在同一张「上下文压缩（全局）」卡里）—— 它们在 [ProviderConfig] 里却是
     * 逐服务商存的。载入后统一成**第一家**的值，免得「卡上显示的」与「pi 实际用的（写 settings.json
     * 时取第一家）」对不上（旧格式迁移 / 老数据里各家可能不同）。
     */
    fun normalizeGlobals() {
        val first = configs.values.firstOrNull() ?: return
        configs.keys.toList().forEach { id ->
            val c = configs[id] ?: return@forEach
            configs[id] = c.copy(
                compactionEnabled = first.compactionEnabled,
                reserveTokens = first.reserveTokens,
                keepRecentTokens = first.keepRecentTokens,
                compactInstructions = first.compactInstructions,
            )
        }
    }

    /**
     * 从旧格式迁移（`providers` 数组 + 完整字段）：写一份 pi 原生文件，之后 ai_config.json
     * 只剩 Pient 附加数据。迁移只做一次（迁移后 pi 文件在手，[load] 不会再走这条路）。
     */
    private fun migrateFromLegacy(context: Context): Boolean = runCatching {
        val root = JSONObject(legacyFile(context).readText())
        val arr = root.optJSONArray("providers") ?: return@runCatching false
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("providerId")
            if (id.isBlank()) continue
            // 老格式是**服务商级**的窗口 / 采样 / 识图（2026-09-17 起只留逐模型）—— 迁移时把它们
            // 展开到该服务商**每一个列出的模型**上（老语义本来就是「对该服务商所有模型生效」，
            // 展开后逐模型的值与原来完全等价，且与 pi 的文件结构一致）。
            val legacyCtx = o.optString("ctxLenK", "")
            val legacyMaxOut = o.optString("maxOutK", "")
            val legacyImage = o.optBoolean("imageDirectEnabled", false)
            val legacyTemp = if (o.optBoolean("tempEnabled", false)) o.optString("tempValue", "1.0") else ""
            val legacyTopK = if (o.optBoolean("topKEnabled", false)) o.optString("topKValue", "0") else ""
            val legacyTopP = if (o.optBoolean("topPEnabled", false)) o.optString("topPValue", "1.0") else ""
            val legacySetting = ModelSetting(
                ctxLenK = legacyCtx,
                maxOutK = legacyMaxOut,
                image = legacyImage,
                temperature = legacyTemp,
                topK = legacyTopK,
                topP = legacyTopP,
            )
            configs[id] = ProviderConfig(
                providerId = id,
                endpoint = o.optString("endpoint"),
                apiKey = o.optString("apiKey"),
                modelList = o.optString("modelList"),
                modelSettings = o.optString("modelList")
                    .split(";").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    .associate { it.substringBefore('=').trim() to legacySetting },
                reasoningFormat = runCatching { ReasoningFormat.valueOf(o.optString("reasoningFormat", "")) }
                    .getOrNull()?.takeUnless { it == ReasoningFormat.AUTO }
                    ?: ProviderCatalog.byId[id]?.reasoningFormat
                    ?: ReasoningFormat.AUTO,
                compactionEnabled = o.optBoolean("compactionEnabled", ContextPolicy.DEFAULT_COMPACTION_ENABLED),
                keepRecentTokens = o.optString("keepRecentTokens", ContextPolicy.DEFAULT_KEEP_RECENT_TOKENS.toString()),
                reserveTokens = o.optString("reserveTokens", ContextPolicy.DEFAULT_RESERVE_TOKENS.toString()),
                compactInstructions = o.optString("compactInstructions", ""),
            )
        }
        aiConfigured = root.optBoolean("aiConfigured", false)
        usdToCnyRate = root.optDouble("usdToCnyRate", ModelPricingDefaults.DEFAULT_USD_TO_CNY_RATE)
        readPricing(root.optJSONObject("pricing"))
        if (configs.isEmpty()) return@runCatching false
        syncOrder(configs.keys.toList())
        normalizeGlobals()   // 老格式里各家可能不同；pi 侧只有一份（写盘取第一家）→ 统一
        // 立刻把 pi 原生文件写出来，并把 ai_config.json 收敛成「只含附加数据」
        save(context)
        android.util.Log.i("PientConfig", "已从 ai_config.json 迁移到 pi 原生配置（${configs.size} 个服务商）")
        true
    }.getOrDefault(false)

    /** 读取 Pient 附加数据（媒体开关/压缩指令/定价/汇率/完成标记），合并进已有 configs */
    private fun loadExtras(context: Context) {
        val f = legacyFile(context)
        if (!f.isFile) return
        val root = JSONObject(f.readText())
        aiConfigured = root.optBoolean("aiConfigured", aiConfigured)
        usdToCnyRate = root.optDouble("usdToCnyRate", usdToCnyRate)
        readPricing(root.optJSONObject("pricing"))
        root.optJSONArray("providerOrder")?.let { arr ->
            val ids = (0 until arr.length())
                .map { arr.optString(it) }
                .filter { it.isNotBlank() && it in configs }
            if (ids.isNotEmpty()) {
                // **落盘的那份顺序为准**（它才是用户添加序；上面的 syncOrder 只是按 models.json
                // 的文件序兜底，而文件是按 configs 的哈希序写的 —— 不覆盖就等于是哈希序）
                providerOrder.clear()
                providerOrder.addAll(ids)
                // 顺序表里没有、文件里有的（手改过 models.json / 老表）：追加在后面
                configs.keys.filter { it !in ids }.forEach { providerOrder.add(it) }
            }
        }
        root.optJSONArray("providers")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("providerId")
                if (id.isBlank()) continue
                val key = o.optString("apiKey")
                val cur = configs[id]
                if (cur == null) {
                    // 只在附加数据里出现的服务商（理论上不该有）：按最小配置恢复
                    configs[id] = ProviderConfig(
                        providerId = id,
                        apiKey = key,
                        compactInstructions = o.optString("compactInstructions", ""),
                    )
                } else {
                    configs[id] = cur.copy(
                        compactInstructions = o.optString("compactInstructions", cur.compactInstructions),
                        // 逐模型的那批（窗口/识图/采样）真值只在 models.json —— 这里不镜像、不兜底
                    )
                }
            }
        }
    }

    private fun readPricing(obj: JSONObject?) {
        obj ?: return
        for (key in obj.keys()) {
            val v = obj.optJSONObject(key) ?: continue
            pricing[key] = ModelPricing(
                billingMode = if (v.optString("mode").equals("COUNT", ignoreCase = true)) {
                    BillingMode.COUNT
                } else {
                    BillingMode.TOKEN
                },
                inputPerMillion = v.optDouble("input", 0.0),
                outputPerMillion = v.optDouble("output", 0.0),
                cachedInputPerMillion = v.optDouble("cachedInput", 0.0),
                pricePerRequest = v.optDouble("pricePerRequest", 0.0),
                cacheWritePerMillion = v.optDouble("cacheWrite", 0.0),
                currency = if (v.optString("currency").equals("USD", ignoreCase = true)) {
                    PricingCurrency.USD
                } else {
                    PricingCurrency.CNY
                },
            )
        }
    }

    /** 落盘：pi 原生三件套（真相源）+ Pient 附加数据 */
    fun save(context: Context) {
        try {
            var changed = false
            if (PiAgentFiles.available(context)) {
                val list = configs.values.toList()
                // 三个 write 的返回值 = **内容是否真有变化**（不是「写成功」）—— 用它决定要不要让 pi 重读
                changed = PiAgentFiles.writeModels(context, list) || changed
                changed = PiAgentFiles.writeAuth(context, list) || changed
                changed = PiAgentFiles.writeSettings(context, primaryConfig()) || changed
            }
            changed = saveExtras(context) || changed
            // pi 只在进程启动时读 models.json / auth.json / settings.json（RPC 没有 reload 命令）→
            // 内容真变了就打脏标记：下次 `PiRpc.start()` 重启通道（与切模型 / 换项目同一条既有路径），
            // 配置页的改动在下一轮对话里就生效，不用用户自己去切模型或重启应用。
            if (changed) PiRpc.markConfigDirty()
        } catch (e: Exception) {
            // 落盘失败不阻断使用
        }
    }

    /** Pient 附加数据（pi 没有对应概念）：定价/汇率/完成标记 + 每个服务商的 Pient 侧字段。
     *  返回内容是否真有变化（不算进「pi 通道要不要重读」—— 这里没有 pi 读的字段）。 */
    private fun saveExtras(context: Context): Boolean {
        try {
            val root = JSONObject()
            root.put("aiConfigured", aiConfigured)
            root.put("usdToCnyRate", usdToCnyRate)
            val arr = JSONArray()
            for (c in configs.values) {
                arr.put(
                    JSONObject()
                        .put("providerId", c.providerId)
                        .put("compactInstructions", c.compactInstructions),
                )
            }
            root.put("providers", arr)
            root.put("providerOrder", JSONArray(orderedIds()))
            root.put("pricing", JSONObject().apply {
                for ((key, p) in pricing) {
                    put(
                        key,
                        JSONObject()
                            .put("mode", p.billingMode.name)
                            .put("input", p.inputPerMillion)
                            .put("output", p.outputPerMillion)
                            .put("cachedInput", p.cachedInputPerMillion)
                            .put("cacheWrite", p.cacheWritePerMillion)
                            .put("pricePerRequest", p.pricePerRequest)
                            .put("currency", p.currency.code),
                    )
                }
            })
            val f = legacyFile(context)
            val text = root.toString()
            if (f.isFile && f.readText() == text) return false
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, "ai_config.json.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) {
                f.writeText(text)
                tmp.delete()
            }
            return true
        } catch (e: Exception) {
            // 落盘失败不阻断使用
        }
        return false
    }
}
