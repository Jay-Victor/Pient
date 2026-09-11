package com.pient.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 单次回复的用量台账（2026-09-11 真实化：用量页的数据源，替换原 UsageMock 演示数据）。
 * 每条 = 一次完成的助手回复：完成时刻 + 服务商/模型 + 输入/输出/缓存 token
 * （token 口径与 pi 一致：输入**不含**缓存读取，缓存单独计）+ 服务商返回的费用。
 */
data class UsageRecord(
    val at: Long,
    val provider: String,
    val model: String,
    val inTokens: Int,
    val outTokens: Int,
    val cacheTokens: Int,
    val cacheWriteTokens: Int,
    /** 服务商直接返回的费用（人民币；当前两家协议都不返回，留 0 → 页面按配置单价计算） */
    val cost: Double,
)

/** 单模型单日用量（用量页堆叠图数据；金额一律为人民币折算值，原生币种另见 currency） */
data class ModelDayUsage(
    val tokens: Long,
    val requests: Int,
    /** 输入侧全部 token（非缓存输入 + 缓存读取 + 缓存写入），与 pi totalTokens 口径一致 */
    val inputTokens: Long,
    val outputTokens: Long,
    /** 该模型当日费用（人民币；USD 计价模型已按汇率折算） */
    val cost: Double,
    /** 输入侧费用（含缓存读取/写入）与输出侧费用——单模型费用柱按此堆叠（按次计费时为 0/全额） */
    val inputCost: Double,
    val outputCost: Double,
    /** 该模型的计费方式与计价币种（定价弹窗/排行行摘要用） */
    val billingMode: BillingMode,
    val currency: PricingCurrency,
)

/**
 * 用量台账存储：filesDir/pient_data/usage.json，落盘由 PientApp 的 snapshotFlow
 * 防抖触发（与 ChatStore 同款）。费用不落盘、按配置单价实时计算 —— 修改
 * 「模型费用信息」里的单价后，历史用量金额同步重算。
 */
object UsageStore {

    /** 全部用量记录（内存态，Compose 可观察） */
    val records = mutableStateListOf<UsageRecord>()

    private fun file(context: Context) = File(context.filesDir, "pient_data/usage.json")

    /** 记录一次完成的回复；usage 为空 = 服务商未返回用量（错误回复/未配置模型）→ 不记 */
    fun record(
        provider: String,
        model: String,
        usage: Usage?,
        at: Long = System.currentTimeMillis(),
    ) {
        if (usage == null) return
        if (usage.inTokens == 0 && usage.outTokens == 0 &&
            usage.cacheTokens == 0 && usage.cacheWriteTokens == 0
        ) return
        records += UsageRecord(
            at = at,
            provider = provider,
            model = model,
            inTokens = usage.inTokens,
            outTokens = usage.outTokens,
            cacheTokens = usage.cacheTokens,
            cacheWriteTokens = usage.cacheWriteTokens,
            cost = usage.costUsd,
        )
    }

    /** 台账里出现过的模型名（按首次使用时间排序；同名模型跨服务商合并展示） */
    fun modelOrder(): List<String> =
        records.sortedBy { it.at }.map { it.model }.distinct()

    /** 台账中最早的记录日期（「全部」时间维度的起点；无记录返回 null） */
    fun firstDate(): LocalDate? =
        records.minOfOrNull { it.at }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

    /**
     * 日期 → 模型 → 当日用量（堆叠图数据源）。
     * 费用口径按 Operit：先取生效定价（用户覆盖 > 内置表三级查找），
     * 按 Token 计费 = 输入×输入价 + 缓存读取×缓存输入价 + 缓存写入×输入价 + 输出×输出价（每百万 tokens）；
     * 按次计费 = 每次请求价（一条台账记录 = 一次请求）。USD 计价模型按汇率折算成人民币。
     * 服务商直接返回了费用（OpenAI 兼容网关如 OpenRouter 的 usage.cost，USD）时优先用它。
     */
    fun daily(): Map<LocalDate, Map<String, ModelDayUsage>> {
        val out = sortedMapOf<LocalDate, MutableMap<String, ModelDayUsage>>()
        val zone = ZoneId.systemDefault()
        val rate = AiConfigStore.usdToCnyRate
        for (r in records) {
            val date = Instant.ofEpochMilli(r.at).atZone(zone).toLocalDate()
            val perDay = out.getOrPut(date) { mutableMapOf() }

            val pricing = AiConfigStore.effectivePricing(r.provider, r.model)
            val currency = pricing.currency
            val inputTok = (r.inTokens + r.cacheTokens + r.cacheWriteTokens).toLong()
            val outputTok = r.outTokens.toLong()

            // 按 Token 计费的原生金额拆输入/输出两侧
            val inNative = (r.inTokens * pricing.inputPerMillion +
                r.cacheTokens * pricing.cachedInputPerMillion +
                r.cacheWriteTokens * (pricing.cacheWritePerMillion.takeIf { it > 0.0 }
                    ?: pricing.inputPerMillion)) / PER_MILLION
            val outNative = r.outTokens * pricing.outputPerMillion / PER_MILLION
            val tokenNative = inNative + outNative
            val nativeTotal = when {
                r.cost > 0 -> r.cost                                        // 服务商直接返回的费用（USD）
                pricing.billingMode == BillingMode.COUNT -> pricing.pricePerRequest
                else -> tokenNative
            }
            val costCny = toCny(nativeTotal, currency, rate)
            // 展示用的输入/输出拆分：按次计费不拆（图例显示「按次」）；服务商只给总额时按 token 占比拆
            val (inCny, outCny) = when {
                pricing.billingMode == BillingMode.COUNT -> 0.0 to costCny
                r.cost > 0 -> {
                    val total = (inputTok + outputTok).toDouble()
                    val share = if (total > 0) inputTok / total else 0.0
                    costCny * share to costCny * (1 - share)
                }
                else -> toCny(inNative, currency, rate) to toCny(outNative, currency, rate)
            }

            val cur = perDay[r.model]
            perDay[r.model] = if (cur == null) {
                ModelDayUsage(
                    tokens = inputTok + outputTok,
                    requests = 1,
                    inputTokens = inputTok,
                    outputTokens = outputTok,
                    cost = costCny,
                    inputCost = inCny,
                    outputCost = outCny,
                    billingMode = pricing.billingMode,
                    currency = currency,
                )
            } else {
                cur.copy(
                    tokens = cur.tokens + inputTok + outputTok,
                    requests = cur.requests + 1,
                    inputTokens = cur.inputTokens + inputTok,
                    outputTokens = cur.outputTokens + outputTok,
                    cost = cur.cost + costCny,
                    inputCost = cur.inputCost + inCny,
                    outputCost = cur.outputCost + outCny,
                )
            }
        }
        return out
    }

    // ───────────────────────── 持久化 ─────────────────────────

    fun load(context: Context) {
        records.clear()
        val f = file(context)
        if (!f.exists()) return
        try {
            val arr = JSONObject(f.readText()).optJSONArray("records") ?: return
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                records += UsageRecord(
                    at = o.optLong("at"),
                    provider = o.optString("provider"),
                    model = o.optString("model"),
                    inTokens = o.optInt("in"),
                    outTokens = o.optInt("out"),
                    cacheTokens = o.optInt("cache"),
                    cacheWriteTokens = o.optInt("cacheWrite"),
                    cost = o.optDouble("cost"),
                )
            }
        } catch (e: Exception) {
            // 台账损坏：忽略（按无记录处理）
        }
    }

    fun save(context: Context) {
        try {
            val arr = JSONArray()
            for (r in records) {
                arr.put(
                    JSONObject()
                        .put("at", r.at)
                        .put("provider", r.provider)
                        .put("model", r.model)
                        .put("in", r.inTokens)
                        .put("out", r.outTokens)
                        .put("cache", r.cacheTokens)
                        .put("cacheWrite", r.cacheWriteTokens)
                        .put("cost", r.cost),
                )
            }
            val f = file(context)
            f.parentFile?.mkdirs()
            val text = JSONObject().put("records", arr).toString()
            val tmp = File(f.parentFile, "usage.json.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) {
                f.writeText(text)
                tmp.delete()
            }
        } catch (e: Exception) {
            // 落盘失败不阻断使用
        }
    }

    private const val PER_MILLION = 1_000_000.0
}
