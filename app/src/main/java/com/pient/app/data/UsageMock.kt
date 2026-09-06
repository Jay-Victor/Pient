package com.pient.app.data

import java.time.LocalDate

/** 模型用量 mock：模型条目（单价 ¥/百万 tokens，参照官方价格量级） */
data class UsageModel(
    val name: String,
    val pricePerM: Float,
)

/** 单模型单日用量（2026-09-01 拆输入/输出，供单模型视图堆叠展示） */
data class ModelDayUsage(
    val tokens: Long,
    val requests: Int,
    val inputTokens: Long,
    val outputTokens: Long,
)

/**
 * 模型用量信息页 mock 数据（原型期；真实数据 v1 接入 Pi 会话计费记录）。
 * 固定种子随机 → 每次启动数据一致，避免验收时数值漂移。
 * 近 90 天整体呈增长趋势（0.55× → 1.0×），堆叠柱状图更有层次。
 */
object UsageMock {
    val models = listOf(
        UsageModel("deepseek-chat", 4f),        // ¥4 / 1M tokens（输入输出均价）
        UsageModel("deepseek-reasoner", 14.4f), // ¥14.4 / 1M
        UsageModel("claude-sonnet-4-5", 57.6f), // ¥57.6 / 1M
    )

    val totalDays = 90

    /** 日期 → 每模型用量 */
    val daily: Map<LocalDate, Map<String, ModelDayUsage>> = run {
        val rnd = kotlin.random.Random(42)
        val today = LocalDate.now()
        val start = today.minusDays((totalDays - 1).toLong())
        val map = mutableMapOf<LocalDate, Map<String, ModelDayUsage>>()
        for (i in 0 until totalDays) {
            val date = start.plusDays(i.toLong())
            val grow = 0.55f + 0.45f * i / (totalDays - 1f)
            map[date] = mapOf(
                "deepseek-chat" to dayUsage(1_000_000 + rnd.nextInt(2_500_000), grow, rnd),
                "deepseek-reasoner" to dayUsage(300_000 + rnd.nextInt(1_400_000), grow, rnd),
                "claude-sonnet-4-5" to dayUsage(100_000 + rnd.nextInt(800_000), grow, rnd),
            )
        }
        map
    }

    private fun dayUsage(tokens: Int, grow: Float, rnd: kotlin.random.Random): ModelDayUsage {
        val t = (tokens * grow).toLong()
        // 输入占比 60%..80%（对话场景输入通常多于输出）
        val inputShare = 0.6f + rnd.nextInt(21) / 100f
        val input = (t * inputShare).toLong()
        return ModelDayUsage(
            tokens = t,
            requests = (t / 22_000).toInt().coerceAtLeast(1),
            inputTokens = input,
            outputTokens = t - input,
        )
    }
}
