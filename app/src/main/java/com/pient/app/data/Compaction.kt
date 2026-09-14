package com.pient.app.data

/**
 * **内核自实现的上下文压缩**（2026-09-14 起，取代随宿主冻结的 pi 原生 compaction）。
 *
 * 背景：压缩原本完全由 pi 在 agent 循环里做（判阈值、切片、生成 checkpoint 摘要、写
 * `firstKeptEntryId`）。宿主冻结后它随之下线 —— 长会话会直接顶到上下文上限，这是唯一一处
 * 「功能倒退」，所以按同一口径在内核里重做。
 *
 * | 轴 | 口径（与 pi 一致） | 实现 |
 * | --- | --- | --- |
 * | 触发 | `估算 tokens > 上下文窗口 − reserveTokens` | [shouldCompact] |
 * | 切片 | 从最新往回累计到 `keepRecentTokens` 处切 | [prefixToSummarize] |
 * | 摘要 | 结构化 checkpoint（Goal / Constraints / Progress / Key Decisions / Next Steps / Critical Context） | [CHECKPOINT_PROMPT] + [summarize] |
 * | 落地 | 摘要进 `Msg.Compaction` 卡；之后的请求从该条目起算 | [ContextPolicy.sliceFromLastCompaction]（已有） |
 *
 * token 是**粗算**（1 token ≈ 2 字符，与 [ChatState.trimToContextBudget] 同一口径），只用于判阈值
 * 与切片；真实占用仍以服务商回的 usage 为准（用量卡显示的是后者）。
 */
object Compaction {

    private const val CHARS_PER_TOKEN = 2

    /** 摘要提示词：结构照 pi 的 checkpoint（pi `compaction` 的 summary 形态） */
    const val CHECKPOINT_PROMPT = """You are compacting a long conversation for an on-device coding agent.
Summarize the conversation below into a structured checkpoint so the agent can continue without the original messages.
Use exactly these sections (keep the headings):
## Goal
## Constraints
## Progress
## Key Decisions
## Next Steps
## Critical Context
Rules: keep file paths, commands, versions and identifiers verbatim; drop pleasantries and repetition;
never invent facts that are not in the conversation. Answer in the same language the user used."""

    /** 粗算 token：整段历史的字符数 ÷ 2 */
    fun estimateTokens(history: List<Pair<String, String>>): Int =
        history.sumOf { (_, text) -> text.length } / CHARS_PER_TOKEN

    /** 触发线 = 上下文窗口 − reserveTokens；配置非法（窗口 ≤ 0 / 预留过大）时返回 null = 不判触发 */
    fun compactLimitTokens(ctxLenK: String, reserveTokens: Int): Int? {
        val k = ctxLenK.trim().toIntOrNull() ?: return null
        if (k <= 0) return null
        return (k * 1000 - reserveTokens.coerceAtLeast(0)).takeIf { it > 0 }
    }

    /** 是否该压缩（开关关掉 = 永不自动压缩，与配置页的开关同义） */
    fun shouldCompact(
        history: List<Pair<String, String>>,
        ctxLenK: String,
        reserveTokens: Int,
        enabled: Boolean,
    ): Boolean {
        if (!enabled) return false
        val limit = compactLimitTokens(ctxLenK, reserveTokens) ?: return false
        return estimateTokens(history) > limit
    }

    /**
     * 切点：保留最近 [keepRecentTokens] 的条目，返回**需要被摘要的前缀**。
     * 前缀少于两条（或历史太短）时返回 null —— 没什么可压，别造一张空卡。
     */
    fun prefixToSummarize(
        history: List<Pair<String, String>>,
        keepRecentTokens: Int,
    ): List<Pair<String, String>>? {
        if (history.size < 4) return null
        var tailTokens = 0
        var cut = history.size
        while (cut > 0 && tailTokens < keepRecentTokens) {
            cut -= 1
            tailTokens += history[cut].second.length / CHARS_PER_TOKEN
        }
        if (cut <= 1) return null
        return history.subList(0, cut)
    }

    /**
     * 调一次模型生成摘要（用本应用自己的请求路径，不依赖任何宿主）。
     * 失败/空摘要一律返回 null —— 调用方保持原样，**不落假卡**。
     */
    suspend fun summarize(
        cfg: ProviderConfig,
        prefix: List<Pair<String, String>>,
        instructions: String?,
    ): String? {
        val turns = prefix.map { (role, text) -> ChatTurn.Text(role, text) }
        val extra = instructions?.trim()?.takeIf { it.isNotEmpty() }
        val prompt = if (extra == null) CHECKPOINT_PROMPT else "$CHECKPOINT_PROMPT\n\n额外要求：$extra"
        val res = runCatching { AiBackend.chat(cfg, prompt, turns, null, null) }.getOrNull() ?: return null
        return res.text.trim().takeIf { it.isNotEmpty() }
    }
}
