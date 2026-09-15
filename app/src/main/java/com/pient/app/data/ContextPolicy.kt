package com.pient.app.data

/**
 * 上下文管理策略（**pi 原生口径**；2026-09-15 收口）。
 *
 * 上下文（切片、压缩、媒体保留、历史裁剪）**整体归 pi**：压缩配置写进 pi 的 settings.json
 * （`compaction{enabled,reserveTokens,keepRecentTokens}`，见 [PiAgentFiles]），手动压缩走
 * 官方 RPC `compact`（见 [ChatState.compactNow]）。App 不判触发、不切片、不生成摘要 ——
 * 旧的内核自实现（Operit 总结式管线 + `data/Compaction.kt` + 拼历史）已整体删除。
 *
 * 这里只剩两件「必须在 App 侧做」的事：
 * ① [autoCompactThresholdPercent] —— 用量卡把 pi 的触发线换算成百分比只读展示；
 * ② [promptTextFor] —— 本条用户消息的请求文本（正文 + 附件清单）。
 */
object ContextPolicy {

    // ─────────── pi `compaction` 三旋钮的默认值（settings.json 同值） ───────────

    const val DEFAULT_COMPACTION_ENABLED = true

    /** 为模型回复预留的 tokens（pi `compaction.reserveTokens`，默认 16384） */
    const val DEFAULT_RESERVE_TOKENS = 16384

    /** 摘要后保留的最近 tokens（pi `compaction.keepRecentTokens`，默认 20000） */
    const val DEFAULT_KEEP_RECENT_TOKENS = 20000

    /**
     * 自动压缩触发线（占上下文窗口的百分比）：`contextTokens > contextWindow − reserveTokens`。
     * 用量卡据此显示「≥ N% 时自动压缩」，让用户知道 pi 何时会动手（移动端看不到 pi 的 footer 提示）。
     */
    fun autoCompactThresholdPercent(reserveTokens: Int, contextWindowKTok: Int): Int? {
        val window = contextWindowKTok * 1000
        if (window <= 0 || reserveTokens <= 0 || reserveTokens >= window) return null
        return ((window - reserveTokens) * 100 + window - 1) / window   // 向上取整
    }

    /** 压缩原因 → 中文标签（pi `compaction_start/end` 的 `reason`；老会话里的压缩卡也用它） */
    fun compactReasonLabel(reason: String?): String = when (reason) {
        "manual" -> "手动"
        "threshold" -> "上下文接近上限"
        "overflow" -> "超出上限"
        else -> "上下文压缩"
    }

    /**
     * 本条用户消息的请求文本：正文 + 附件清单（附件以「名称 · 路径」进请求；媒体本体由
     * [MediaInline] 按媒体开关直发，不在这里）。历史媒体不再由 App 裁剪 —— 上下文整体归 pi。
     */
    fun promptTextFor(msg: Msg.User): String {
        if (msg.attachments.isEmpty()) return msg.text
        return msg.text + "\n\n" + msg.attachments.joinToString("\n") { attachmentLine(it) }
    }

    private fun attachmentLine(a: Attachment): String =
        "[附件] ${a.name}" + (a.path?.let { " · $it" } ?: "")
}
