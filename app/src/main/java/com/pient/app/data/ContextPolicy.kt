package com.pient.app.data

/**
 * 上下文管理策略（**2026-09-14 改为 pi 原生口径**；此前的 Operit 总结式管线已移除）。
 *
 * 依据 = pi 自己的机制（`Refences/pi-0.85.1/packages/coding-agent/docs/compaction.md`）：
 *
 * | 轴 | pi 原生 | Pient 的位置 |
 * | --- | --- | --- |
 * | 触发 | `contextTokens > contextWindow − reserveTokens`（默认 16384） | **不由 App 判定**：pi 在 agent 循环里自己查阈值，另支持手动 `compact` |
 * | 切片 | 从最新往回累计到 `keepRecentTokens`（默认 20000）处切，切点写 `firstKeptEntryId` | **不由 App 切片**：pi 用摘要 + `firstKeptEntryId` 之后的消息重建上下文 |
 * | 摘要 | pi 的结构化 checkpoint（Goal / Constraints / Progress / Key Decisions / Next Steps / Critical Context）+ `<read-files>` / `<modified-files>` | 摘要文本原样进卡展示，**App 不再自己生成摘要** |
 * | 配置 | `~/.pi/agent/settings.json` 的 `compaction: {enabled, reserveTokens, keepRecentTokens}` | 配置页三旋钮 → [PiConfig.syncSettings] 合并写；`enabled` 另经官方 RPC `set_auto_compaction` 即时生效 |
 * | 手动 | `/compact [instructions]`（命令行） | **Android 没有命令行** → 上下文用量卡里的「压缩上下文」动作（RPC `compact{customInstructions}`） |
 * | 事件 | `compaction_start/end`（`reason` = manual / threshold / overflow） | 直接落成聊天页的压缩卡（含原因），不再由 App 造条目 |
 *
 * 为什么删掉 Operit 那套（0.70 占比阈值 / 16 条阈值 / Operit 摘要提示词 / App 侧生成摘要）：
 * ① pi 的上下文是"会话对象内的原生机制"，App 侧再造一套必然会与 pi 的切点/格式打架（两套真相）；
 * ② 那套阈值属于 Operit 的产品形态，与 pi 的 `contextWindow − reserveTokens` 不是同一个口径，
 *    同时存在时用户看到的"何时压缩"取决于谁先命中，不可解释；
 * ③ 项目红线①要求 pi 原生机制优先，摘要格式也以 pi 的 checkpoint 为准（用户要别的规则用
 *    [ProviderConfig.compactInstructions]，它进 pi 的 `customInstructions`）。
 *
 * 保留下来的两部分**不是上下文管理**，而是「直连路径拼请求文本」必须自己做的事
 * （宿主路径由 pi 管会话，用不上）：历史切片入口 [sliceFromLastCompaction]、媒体保留窗口
 * [attachmentWindows] / [promptTextFor]。
 */
object ContextPolicy {

    // ─────────── pi `compaction` 三旋钮的默认值（settings.json 同值） ───────────

    const val DEFAULT_COMPACTION_ENABLED = true

    /** 为模型回复预留的 tokens（pi `compaction.reserveTokens`，默认 16384） */
    const val DEFAULT_RESERVE_TOKENS = 16384

    /** 摘要后保留的最近 tokens（pi `compaction.keepRecentTokens`，默认 20000） */
    const val DEFAULT_KEEP_RECENT_TOKENS = 20000

    // ─────────── 直连路径的媒体保留（Android 侧拼请求文本用） ───────────

    const val DEFAULT_MAX_IMAGE_HISTORY_TURNS = 2
    const val DEFAULT_MAX_MEDIA_HISTORY_TURNS = 1

    /** 媒体被裁掉后写进请求文本的占位 */
    const val OMITTED_IMAGE_HINT = "（历史图片已省略）"
    const val OMITTED_MEDIA_HINT = "（历史音视频已省略）"

    /**
     * 自动压缩触发线（占上下文窗口的百分比）：`contextTokens > contextWindow − reserveTokens`。
     * 用量卡据此显示「≥ N% 时自动压缩」，让用户知道 pi 何时会动手（移动端看不到 footer 的 pi 提示）。
     */
    fun autoCompactThresholdPercent(reserveTokens: Int, contextWindowKTok: Int): Int? {
        val window = contextWindowKTok * 1000
        if (window <= 0 || reserveTokens <= 0 || reserveTokens >= window) return null
        return ((window - reserveTokens) * 100 + window - 1) / window   // 向上取整
    }

    /** 压缩原因 → 中文标签（pi `compaction_start/end` 的 `reason`） */
    fun compactReasonLabel(reason: String?): String = when (reason) {
        "manual" -> "手动"
        "threshold" -> "上下文接近上限"
        "overflow" -> "超出上限"
        else -> "上下文压缩"
    }

    // ─────────── 请求上下文切片（只在没有 pi 的直连路径上用） ───────────

    /**
     * 请求上下文切片：最后一条压缩摘要（**含**）之后的全部条目。
     * 直连路径没有 pi 管会话，但历史里可能有**宿主时代**留下的压缩条目（`Msg.Compaction`）——
     * 那条摘要代表 pi 已经不再发送的原文，所以拼请求时必须从它起算，否则等于把摘要又展开一遍。
     */
    fun sliceFromLastCompaction(messages: List<Msg>): List<Msg> {
        val last = messages.indexOfLast { it is Msg.Compaction }
        return if (last < 0) messages else messages.subList(last, messages.size)
    }

    // ─────────── 历史媒体裁剪（同上：只作用于直连路径的请求文本） ───────────

    /**
     * 每个用户回合的附件可见性：只有最近 N 个用户回合保留图片/音视频，更早的在请求文本里
     * 替换为「已省略」占位（Pient 的附件是 chip + 路径，不走正文，所以这里返回可见性表，
     * 由 [promptTextFor] 决定写路径还是写占位）。
     *
     * @return 与 [messages] 等长的可见性表（非用户消息恒为 (true, true)，不参与裁剪）
     */
    fun attachmentWindows(
        messages: List<Msg>,
        imageTurns: Int,
        mediaTurns: Int,
    ): List<Pair<Boolean, Boolean>> {
        val imgLimit = imageTurns.coerceAtLeast(0)
        val avLimit = mediaTurns.coerceAtLeast(0)
        val totalUserTurns = messages.count { it is Msg.User }
        val imgFrom = (totalUserTurns - imgLimit).coerceAtLeast(0)
        val avFrom = (totalUserTurns - avLimit).coerceAtLeast(0)
        var turn = -1
        return messages.map { m ->
            if (m !is Msg.User) return@map true to true
            turn += 1
            ((imgLimit > 0 && turn >= imgFrom) to (avLimit > 0 && turn >= avFrom))
        }
    }

    /**
     * 用户消息的请求文本：正文 + 附件清单（附件以「名称 · 路径」进请求，pi 的 read 工具据此打开文件），
     * 历史回合的媒体按 [window] 替换为占位文案。
     */
    fun promptTextFor(msg: Msg.User, window: Pair<Boolean, Boolean>): String {
        if (msg.attachments.isEmpty()) return msg.text
        val (keepImages, keepMedia) = window
        val lines = msg.attachments.map { a ->
            val ext = extOf(a.path ?: a.name)
            when {
                ext in MEDIA_IMAGE_EXTS -> if (keepImages) attachmentLine(a) else OMITTED_IMAGE_HINT
                ext in MEDIA_AV_EXTS -> if (keepMedia) attachmentLine(a) else OMITTED_MEDIA_HINT
                else -> attachmentLine(a)
            }
        }
        return msg.text + "\n\n" + lines.joinToString("\n")
    }

    private fun attachmentLine(a: Attachment): String =
        "[附件] ${a.name}" + (a.path?.let { " · $it" } ?: "")
}
