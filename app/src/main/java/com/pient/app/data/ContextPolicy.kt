package com.pient.app.data

import com.pient.app.data.i18n.L
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
        "manual" -> L.runtime.compactReasonManual
        "threshold" -> L.runtime.compactReasonThreshold
        "overflow" -> L.runtime.compactReasonOverflow
        else -> L.runtime.compactReasonDefault
    }

    /**
     * 本条用户消息的请求文本：正文 + 附件清单（附件以「名称 · 路径」进请求；媒体本体由
     * [MediaInline] 按媒体开关直发，不在这里）。历史媒体不再由 App 裁剪 —— 上下文整体归 pi。
     */
    fun promptTextFor(msg: Msg.User): String {
        if (msg.attachments.isEmpty()) return msg.text
        return msg.text + "\n\n" + msg.attachments.joinToString("\n") { attachmentLine(it) }
    }

    // ─────────── 附件清单的「写—读」两端（同源；见 [splitAttachments]）───────────

    /** 附件清单行的前缀 */
    const val ATTACH_PREFIX = "[附件] "

    /** 未直发说明行的前缀（超上限 / 读取失败） */
    const val ATTACH_OMIT_PREFIX = "[附件未直发] "

    /** 媒体未直发的占位文案（Operit strings.xml 原文：`openai_image_omitted` / `openai_audio_video_omitted`） */
    const val OMIT_IMAGE = "图片内容已省略，当前模型不支持图片处理"
    const val OMIT_MEDIA = "音视频内容已省略，当前模型不支持音视频处理"

    private fun attachmentLine(a: Attachment): String =
        ATTACH_PREFIX + a.name + (a.path?.let { " · $it" } ?: "")

    /**
     * [promptTextFor] 的逆运算（2026-09-17）：把 pi 侧那条用户消息的文本拆回 (正文, 附件清单)。
     *
     * pi 的会话文件里用户消息**只有文本** —— 附件是以「尾部两段元数据」拼进去的：
     * `正文 \n\n [附件] 名称 · 路径（一行一个） \n\n [附件未直发]/省略说明（可能没有）`。
     * 从尾部往前剥这两段；**形成不了「整段都是元数据行」就原样返回**，所以用户自己打的
     * "[附件] …" 不会被误认成附件。
     *
     * 谁用：按 pi 重建上屏流（`ChatState.syncMessagesFromPi`）与画布节点预览 ——
     * 不还原的话聊天页气泡、画布卡片都会把这份清单当正文显示。
     */
    fun splitAttachments(text: String): Pair<String, List<Attachment>> {
        val blocks = text.split("\n\n").toMutableList()
        fun isOmitBlock(b: String): Boolean = b.lines().all { l ->
            val t = l.trim()
            t == OMIT_IMAGE || t == OMIT_MEDIA || t.startsWith(ATTACH_OMIT_PREFIX)
        }
        fun isAttachBlock(b: String): Boolean =
            b.lines().all { it.trim().startsWith(ATTACH_PREFIX) }

        if (blocks.size > 1 && isOmitBlock(blocks.last())) blocks.removeAt(blocks.lastIndex)
        if (blocks.size > 1 && isAttachBlock(blocks.last())) {
            val atts = blocks.removeAt(blocks.lastIndex).lines().mapNotNull { parseAttachmentLine(it) }
            if (atts.isNotEmpty()) return blocks.joinToString("\n\n").trim() to atts
        }
        return text to emptyList()
    }

    /** `[附件] 名称 · 路径` → [Attachment]（类型按扩展名判，与文件树/预览共用同一份家族表） */
    private fun parseAttachmentLine(line: String): Attachment? {
        val body = line.trim().removePrefix(ATTACH_PREFIX)
        val name = body.substringBefore(" · ").trim()
        if (name.isBlank()) return null
        val path = body.substringAfter(" · ", "").trim().takeIf { it.isNotBlank() }
        val kind = if (extOf(name) in MEDIA_IMAGE_EXTS) AttachmentKind.IMAGE else AttachmentKind.FILE
        return Attachment(name, kind, path)
    }
}
