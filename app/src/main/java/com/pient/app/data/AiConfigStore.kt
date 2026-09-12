package com.pient.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File

/**
 * 单个服务商的真实配置（2026-09-09 实现 AI 接入；替换原型期 mock）：
 * 配置页编辑即写入 AiConfigStore.configs（Compose state），snapshotFlow 自动落盘
 * filesDir/pient_data/ai_config.json；AI 是否配置完成（聊天页引导第二步）一并持久化。
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
     * 老配置缺该字段 → AUTO（按模型名推断，识别不出则维持「不发参数」的老行为）。
     */
    val reasoningFormat: ReasoningFormat = ReasoningFormat.AUTO,
) {
    val models: List<String>
        get() = modelList.split(";").map { it.trim() }.filter { it.isNotEmpty() }
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

    private fun file(context: Context) = File(context.filesDir, "pient_data/ai_config.json")

    fun load(context: Context) {
        val f = file(context)
        if (!f.exists()) return
        try {
            val root = JSONObject(f.readText())
            aiConfigured = root.optBoolean("aiConfigured", false)
            usdToCnyRate = root.optDouble("usdToCnyRate", ModelPricingDefaults.DEFAULT_USD_TO_CNY_RATE)
            // 用户覆盖定价（键 = provider:model；无 key 时用内置表默认）
            root.optJSONObject("pricing")?.let { obj ->
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
            val arr = root.optJSONArray("providers") ?: return
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
                    reasoningFormat = runCatching {
                        ReasoningFormat.valueOf(o.optString("reasoningFormat", "AUTO"))
                    }.getOrDefault(ReasoningFormat.AUTO),
                )
            }
        } catch (e: Exception) {
            // 配置损坏：忽略（按未配置处理）
        }
    }

    fun save(context: Context) {
        try {
            val root = JSONObject()
            root.put("aiConfigured", aiConfigured)
            val arr = org.json.JSONArray()
            for (c in configs.values) {
                arr.put(
                    JSONObject()
                        .put("providerId", c.providerId)
                        .put("endpoint", c.endpoint)
                        .put("apiKey", c.apiKey)
                        .put("modelList", c.modelList)
                        .put("ctxLenK", c.ctxLenK)
                        .put("maxOutK", c.maxOutK)
                        .put("tempEnabled", c.tempEnabled)
                        .put("tempValue", c.tempValue)
                        .put("topKEnabled", c.topKEnabled)
                        .put("topKValue", c.topKValue)
                        .put("topPEnabled", c.topPEnabled)
                        .put("topPValue", c.topPValue)
                        .put("reasoningFormat", c.reasoningFormat.name),
                )
            }
            root.put("providers", arr)
            root.put("usdToCnyRate", usdToCnyRate)
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
            val f = file(context)
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
