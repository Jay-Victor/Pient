package com.pient.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
data class ProviderConfig(
    val providerId: String,
    val endpoint: String = "",
    val apiKey: String = "",
    /** 模型列表（英文分号分隔，与配置页输入框同口径） */
    val modelList: String = "",
    /** 上下文长度（K Tokens；字符串承载输入框态） */
    val ctxLenK: String = "200",
    /** 最大输出长度（K Tokens） */
    val maxOutK: String = "64",
    val tempEnabled: Boolean = false,
    val tempValue: String = "1.0",
    val topKEnabled: Boolean = false,
    val topKValue: String = "0",
    val topPEnabled: Boolean = false,
    val topPValue: String = "1.0",
    /**
     * 思考参数写法（2026-09-12 真实化）：决定「思考模式开关」在线上怎么表达——
     * 关闭 = 显式禁用字面量、开启 = 显式启用（详见 [ReasoningFormat]）。
     * 落盘到 pi 的 `compat.thinkingFormat`。
     */
    val reasoningFormat: ReasoningFormat = ReasoningFormat.AUTO,

    // ── 媒体能力（照 Operit 的三个 direct-processing 开关；默认关 = 不直发）──
    // 口径（2026-09-14 用户拍板「照 Operit 全量对齐」）：
    // - **开** = 该类型媒体转成内容部件**直发**给模型（图片 `image_url` / 音频 `input_audio` /
    //   视频 `video_url`）；**关** = 不直发，附件行后跟一行占位文案，消息照常发送。
    // 注：图片开关同时写进 pi 的 `models[].input`（text/image）；音频与视频 pi 没有对应概念，
    // 只留在 ai_config.json（Pient 自己的请求侧行为）。
    /** 模型支持识图（Operit `enableDirectImageProcessing`，默认 false） */
    val imageDirectEnabled: Boolean = false,
    /** 模型支持音频解析（Operit `enableDirectAudioProcessing`，默认 false） */
    val audioDirectEnabled: Boolean = false,
    /** 模型支持视频解析（Operit `enableDirectVideoProcessing`，默认 false） */
    val videoDirectEnabled: Boolean = false,

    // ── 上下文管理（**pi 原生口径**；写进 settings.json 的 compaction 块）──
    /** 自动压缩上下文（pi `compaction.enabled`，默认 true）；关掉后仍能手动压缩 */
    val compactionEnabled: Boolean = ContextPolicy.DEFAULT_COMPACTION_ENABLED,
    /** 摘要后保留的最近 tokens（pi `compaction.keepRecentTokens`，默认 20000） */
    val keepRecentTokens: String = ContextPolicy.DEFAULT_KEEP_RECENT_TOKENS.toString(),
    /** 给模型回复预留的 tokens（pi `compaction.reserveTokens`，默认 16384） */
    val reserveTokens: String = ContextPolicy.DEFAULT_RESERVE_TOKENS.toString(),
    /** 手动压缩时交给 pi 的指令（`compact` 的 `customInstructions`；Pient 侧字段） */
    val compactInstructions: String = "",
    /** 历史中保留图片附件的最近用户回合数（请求侧附件裁剪用，Pient 侧字段） */
    val maxImageHistoryTurns: String = "2",
    /** 历史中保留音视频附件的最近用户回合数（同上） */
    val maxMediaHistoryTurns: String = "1",
) {
    val models: List<String>
        get() = modelList.split(";").map { it.trim() }.filter { it.isNotEmpty() }

    /** 生效的「保留最近 tokens」（非法输入回退 pi 默认 20000） */
    val keepRecentTokensValue: Int
        get() = keepRecentTokens.trim().toIntOrNull()?.coerceIn(1000, 2_000_000)
            ?: ContextPolicy.DEFAULT_KEEP_RECENT_TOKENS

    /** 生效的「为回复预留 tokens」（非法输入回退 pi 默认 16384） */
    val reserveTokensValue: Int
        get() = reserveTokens.trim().toIntOrNull()?.coerceIn(1000, 1_000_000)
            ?: ContextPolicy.DEFAULT_RESERVE_TOKENS

    /** 生效的图片保留回合数（非法输入回退 Operit 默认 2） */
    val maxImageHistoryTurnsValue: Int
        get() = maxImageHistoryTurns.trim().toIntOrNull()?.coerceIn(0, 50)
            ?: ContextPolicy.DEFAULT_MAX_IMAGE_HISTORY_TURNS

    /** 生效的音视频保留回合数（非法输入回退 Operit 默认 1） */
    val maxMediaHistoryTurnsValue: Int
        get() = maxMediaHistoryTurns.trim().toIntOrNull()?.coerceIn(0, 50)
            ?: ContextPolicy.DEFAULT_MAX_MEDIA_HISTORY_TURNS
}

object AiConfigStore {

    /** 已配置服务商 → 配置（插入序 = 配置页展示序） */
    val configs = mutableStateMapOf<String, ProviderConfig>()

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
            loadExtras(context)
        } catch (e: Exception) {
            // 配置损坏：忽略（按未配置处理）
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
            configs[id] = ProviderConfig(
                providerId = id,
                endpoint = o.optString("endpoint"),
                apiKey = o.optString("apiKey"),
                modelList = o.optString("modelList"),
                ctxLenK = o.optString("ctxLenK", "200"),
                maxOutK = o.optString("maxOutK", "64"),
                tempEnabled = o.optBoolean("tempEnabled", false),
                tempValue = o.optString("tempValue", "1.0"),
                topKEnabled = o.optBoolean("topKEnabled", false),
                topKValue = o.optString("topKValue", "0"),
                topPEnabled = o.optBoolean("topPEnabled", false),
                topPValue = o.optString("topPValue", "1.0"),
                reasoningFormat = runCatching { ReasoningFormat.valueOf(o.optString("reasoningFormat", "")) }
                    .getOrNull()?.takeUnless { it == ReasoningFormat.AUTO }
                    ?: ProviderCatalog.byId[id]?.reasoningFormat
                    ?: ReasoningFormat.AUTO,
                imageDirectEnabled = o.optBoolean("imageDirectEnabled", false),
                audioDirectEnabled = o.optBoolean("audioDirectEnabled", false),
                videoDirectEnabled = o.optBoolean("videoDirectEnabled", false),
                compactionEnabled = o.optBoolean("compactionEnabled", ContextPolicy.DEFAULT_COMPACTION_ENABLED),
                keepRecentTokens = o.optString("keepRecentTokens", ContextPolicy.DEFAULT_KEEP_RECENT_TOKENS.toString()),
                reserveTokens = o.optString("reserveTokens", ContextPolicy.DEFAULT_RESERVE_TOKENS.toString()),
                compactInstructions = o.optString("compactInstructions", ""),
                maxImageHistoryTurns = o.optString("maxImageHistoryTurns", ContextPolicy.DEFAULT_MAX_IMAGE_HISTORY_TURNS.toString()),
                maxMediaHistoryTurns = o.optString("maxMediaHistoryTurns", ContextPolicy.DEFAULT_MAX_MEDIA_HISTORY_TURNS.toString()),
            )
        }
        aiConfigured = root.optBoolean("aiConfigured", false)
        usdToCnyRate = root.optDouble("usdToCnyRate", ModelPricingDefaults.DEFAULT_USD_TO_CNY_RATE)
        readPricing(root.optJSONObject("pricing"))
        if (configs.isEmpty()) return@runCatching false
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
                        audioDirectEnabled = o.optBoolean("audioDirectEnabled", false),
                        videoDirectEnabled = o.optBoolean("videoDirectEnabled", false),
                        compactInstructions = o.optString("compactInstructions", ""),
                        maxImageHistoryTurns = o.optString("maxImageHistoryTurns", "2"),
                        maxMediaHistoryTurns = o.optString("maxMediaHistoryTurns", "1"),
                    )
                } else {
                    configs[id] = cur.copy(
                        audioDirectEnabled = o.optBoolean("audioDirectEnabled", cur.audioDirectEnabled),
                        videoDirectEnabled = o.optBoolean("videoDirectEnabled", cur.videoDirectEnabled),
                        compactInstructions = o.optString("compactInstructions", cur.compactInstructions),
                        maxImageHistoryTurns = o.optString("maxImageHistoryTurns", cur.maxImageHistoryTurns),
                        maxMediaHistoryTurns = o.optString("maxMediaHistoryTurns", cur.maxMediaHistoryTurns),
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
            if (PiAgentFiles.available(context)) {
                val list = configs.values.toList()
                PiAgentFiles.writeModels(context, list)
                PiAgentFiles.writeAuth(context, list)
                PiAgentFiles.writeSettings(context, list.firstOrNull())
            }
            saveExtras(context)
        } catch (e: Exception) {
            // 落盘失败不阻断使用
        }
    }

    /** Pient 附加数据（pi 没有对应概念）：定价/汇率/完成标记 + 每个服务商的 Pient 侧字段 */
    private fun saveExtras(context: Context) {
        try {
            val root = JSONObject()
            root.put("aiConfigured", aiConfigured)
            root.put("usdToCnyRate", usdToCnyRate)
            val arr = JSONArray()
            for (c in configs.values) {
                arr.put(
                    JSONObject()
                        .put("providerId", c.providerId)
                        .put("audioDirectEnabled", c.audioDirectEnabled)
                        .put("videoDirectEnabled", c.videoDirectEnabled)
                        .put("compactInstructions", c.compactInstructions)
                        .put("maxImageHistoryTurns", c.maxImageHistoryTurns)
                        .put("maxMediaHistoryTurns", c.maxMediaHistoryTurns),
                )
            }
            root.put("providers", arr)
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
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, "ai_config.json.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(root.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            // 落盘失败不阻断使用
        }
    }
}
