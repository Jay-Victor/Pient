package com.pient.app.data

import android.content.Context

/** 计费方式（Operit BillingMode 同款：按 Token 计费 / 按次计费） */
enum class BillingMode(val label: String) {
    TOKEN("按Token计费"),
    COUNT("按次计费"),
}

/** 计价币种（Operit PricingCurrency 同款） */
enum class PricingCurrency(val code: String, val symbol: String) {
    CNY("CNY", "¥"),
    USD("USD", "$"),
}

/**
 * 模型定价（对齐 Operit ModelPricingDefaults）：
 * 按 Token 计费用三档单价（输入 / 输出 / 缓存输入，每百万 tokens），
 * 按次计费用单次请求价；币种决定金额是否按汇率折算成人民币展示。
 */
data class ModelPricing(
    val billingMode: BillingMode = BillingMode.TOKEN,
    val inputPerMillion: Double = 0.0,
    val outputPerMillion: Double = 0.0,
    val cachedInputPerMillion: Double = 0.0,
    val pricePerRequest: Double = 0.0,
    val currency: PricingCurrency = PricingCurrency.CNY,
    /** 缓存写入单价（每百万 tokens）；0 = 未提供，按输入价计 */
    val cacheWritePerMillion: Double = 0.0,
)

/** 折算成人民币（Operit convertToCny 同款） */
fun toCny(amount: Double, currency: PricingCurrency, usdToCnyRate: Double): Double =
    if (currency == PricingCurrency.USD) amount * usdToCnyRate else amount

/** 人民币 → 模型币种（定价弹窗按 ¥ 输入、按模型币种存，Operit convertCnyToPricingCurrency 同款） */
fun fromCny(amountCny: Double, currency: PricingCurrency, usdToCnyRate: Double): Double =
    if (currency == PricingCurrency.USD && usdToCnyRate > 0.0) amountCny / usdToCnyRate else amountCny

/**
 * 内置模型价格表（2026-09-11 按 Operit 的处理方式实现）：
 * 数据 = `assets/model_pricing.tsv`（移植自 Operit `ScrapedModelPricingRowsCollect` 的 548 行抓取表，
 * 服务商键已映射到 Pient 服务商 id；`*` 开头的行只参与「模型名回退」），
 * 行格式 `provider|model|计费方式|输入价|输出价|缓存输入价(或按次价)|币种`（每百万 tokens）。
 *
 * 查找顺序与 Operit `ModelPricingDefaultsCollect.getDefaultPricing` 一致：
 * ① `provider:model` 精确匹配 → ② 模型名回退（国内服务商优先 CNY、其余优先 USD，取不到取第一条）
 * → ③ 服务商兜底（国内 = CNY 零价 / 海外 = USD 零价）。
 */
object ModelPricingDefaults {

    /** 默认美元汇率（Operit DEFAULT_USD_TO_CNY_RATE 同款） */
    const val DEFAULT_USD_TO_CNY_RATE = 7.2

    private const val ASSET = "model_pricing.tsv"

    /** 国内服务商（按 ProviderCatalog 里的实际端点判定：z.ai / moonshot.ai 是国际站 → 美元）——决定回退币种 */
    private val domesticProviders = setOf(
        "deepseek", "kimi-coding", "moonshotai-cn",
        "zai-coding-cn", "minimax-cn", "ant-ling",
        "qwen-token-plan", "qwen-token-plan-cn", "qwen-token-plan-individual",
        "xiaomi", "xiaomi-token-plan-ams", "xiaomi-token-plan-cn", "xiaomi-token-plan-sgp",
    )

    private val zeroCny = ModelPricing(currency = PricingCurrency.CNY)
    private val zeroUsd = ModelPricing(currency = PricingCurrency.USD)

    private var exact: Map<String, ModelPricing> = emptyMap()
    private var byName: Map<String, List<ModelPricing>> = emptyMap()
    private var byNormalizedName: Map<String, List<ModelPricing>> = emptyMap()

    /**
     * 模型名归一化（宽松匹配用，两侧同法归一后比对）：
     * 小写 → 去厂商前缀（`author/slug` 取 slug）→ 去版本段（`-v4` / `-v3.1`）→ 去日期戳（`-20250617` / `-2025-06-17`）。
     * 例：用户填 `deepseek-flash` ↔ 内置表 `deepseek-v4-flash` → 两侧都归一为 `deepseek-flash` 而命中。
     * 只做这两种「明确是版本/日期」的裁剪，避免把 `claude-opus-4-5` 这类数字段名字裁成同一键造成误配。
     */
    private fun normalizeModelName(raw: String): String {
        var s = raw.trim().lowercase().substringAfterLast('/')
        s = s.replace(Regex("-v\\d+(\\.\\d+)*"), "-")
        s = s.replace(Regex("-\\d{4}-\\d{2}-\\d{2}"), "")
        s = s.replace(Regex("-20\\d{6}"), "")
        s = s.replace(Regex("-{2,}"), "-").trim('-')
        return s
    }

    fun isDomestic(providerId: String): Boolean = providerId.trim().lowercase() in domesticProviders

    /** 载入内置表（PientApp 启动时调一次；失败则退化为「按服务商兜底」） */
    fun load(context: Context) {
        if (exact.isNotEmpty()) return
        val rows = try {
            context.assets.open(ASSET).bufferedReader().use { it.readLines() }
        } catch (e: Exception) {
            return
        }
        val exactMap = mutableMapOf<String, ModelPricing>()
        val nameMap = mutableMapOf<String, MutableList<ModelPricing>>()
        val normalizedMap = mutableMapOf<String, MutableList<ModelPricing>>()
        for (line in rows) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue   // 表头注释（数据来源/口径说明）
            val c = trimmed.split('|')
            if (c.size < 7) continue
            val provider = c[0].trim().lowercase()
            val model = c[1].trim()
            if (model.isEmpty()) continue
            val currency = if (c[6].trim().equals("CNY", ignoreCase = true)) {
                PricingCurrency.CNY
            } else {
                PricingCurrency.USD
            }
            val input = c[3].trim().toDoubleOrNull() ?: 0.0
            val output = c[4].trim().toDoubleOrNull() ?: 0.0
            val cachedOrCount = c[5].trim().toDoubleOrNull() ?: 0.0
            val pricing = if (c[2].trim().equals("COUNT", ignoreCase = true)) {
                // 按次计费：第 6 列是单次请求价
                ModelPricing(
                    billingMode = BillingMode.COUNT,
                    inputPerMillion = input,
                    outputPerMillion = output,
                    cachedInputPerMillion = cachedOrCount,
                    pricePerRequest = cachedOrCount,
                    currency = currency,
                )
            } else {
                // 按 Token 计费：第 6 列是缓存输入价，未标注（0）时按输入价计（Operit 同口径）；第 8 列可选=缓存写入价
                ModelPricing(
                    billingMode = BillingMode.TOKEN,
                    inputPerMillion = input,
                    outputPerMillion = output,
                    cachedInputPerMillion = cachedOrCount.takeIf { it > 0.0 } ?: input,
                    currency = currency,
                    cacheWritePerMillion = c.getOrNull(7)?.trim()?.toDoubleOrNull() ?: 0.0,
                )
            }
            if (provider != "*") exactMap["$provider:${model.lowercase()}"] = pricing
            nameMap.getOrPut(model.lowercase()) { mutableListOf() } += pricing
            normalizedMap.getOrPut(normalizeModelName(model)) { mutableListOf() } += pricing
        }
        exact = exactMap
        byName = nameMap
        byNormalizedName = normalizedMap
    }

    /** 内置默认定价（三级查找 + 归一化宽松匹配，Operit 同款结构 + 2026-09-11 容错层） */
    fun defaultFor(providerId: String, model: String): ModelPricing {
        val provider = providerId.trim().lowercase()
        val name = model.trim()
        if (name.isNotEmpty()) {
            exact["$provider:${name.lowercase()}"]?.let { return it }
            val preferred = if (isDomestic(provider)) PricingCurrency.CNY else PricingCurrency.USD
            pickPreferred(byName[name.lowercase()], preferred)?.let { return it }
            // 宽松匹配：`deepseek-flash` ↔ 表内 `deepseek-v4-flash`（去版本段/日期戳后同名）
            pickPreferred(byNormalizedName[normalizeModelName(name)], preferred)?.let { return it }
        }
        return if (isDomestic(provider)) zeroCny else zeroUsd
    }

    /** 同一模型名可能有多条（不同服务商/不同币种）：优先取与服务商币种一致的一条，否则取第一条 */
    private fun pickPreferred(
        candidates: List<ModelPricing>?,
        preferred: PricingCurrency,
    ): ModelPricing? = when {
        candidates.isNullOrEmpty() -> null
        else -> candidates.firstOrNull { it.currency == preferred } ?: candidates.first()
    }

    /** 内置表行数（验证/自检用） */
    fun rowCount(): Int = exact.size + byName.size
}
