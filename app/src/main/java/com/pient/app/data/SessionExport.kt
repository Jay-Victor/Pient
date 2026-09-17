package com.pient.app.data

import com.pient.app.data.i18n.L
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 会话导出（2026-09-16；此前项目管理页的「导出会话」是原型占位 Toast）。
 *
 * 形态 = **Markdown**（pi 的会话条目本就是文本消息，导出成可读文本最通用、也能回灌给模型）：
 * 每个会话一个二级标题 + 项目/时间，逐条消息按角色输出；思考折叠成引用块，工具调用/结果各一行。
 *
 * 落点 = 系统「下载/Pient/」（实现见 [DownloadsOut]；API 29+ 免权限、系统文件管理器可见）。
 */
object SessionExport {

    /**
     * 一组会话 → 一个 Markdown 文档。
     * @param picks 会话记录（跨项目按 id 取；顺序即文档顺序）
     */
    fun markdown(chat: ChatState, picks: List<Session>, title: String): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("# ").append(title).append("\n\n")
        sb.append(L.project.exportTime).append(stamp).append(L.project.exportSessionCount).append(picks.size).append("\n\n")
        picks.forEachIndexed { idx, s ->
            sb.append("## ").append(s.title.ifBlank { L.project.untitledSession }).append("\n\n")
            sb.append(L.project.exportProjectLabel).append(s.project)
                .append(L.project.exportUpdatedAt).append(formatTime(s.updatedAt))
                .append(L.project.exportMessageCount).append(chat.messagesForExport(s.id).size).append("\n\n")
            val msgs = chat.messagesForExport(s.id)
            if (msgs.isEmpty()) {
                sb.append(L.project.exportNoMessages)
            } else {
                msgs.forEach { appendMsg(sb, it) }
            }
            if (idx != picks.lastIndex) sb.append("---\n\n")
        }
        return sb.toString()
    }

    /**
     * 写给系统「下载/Pient/<fileName>」，返回可展示的落点描述（失败返回 null）。
     * 实现见 [DownloadsOut]（与日志导出共用一份：同一语义不写第二遍）。
     */
    fun writeToDownloads(context: Context, fileName: String, content: String): String? =
        DownloadsOut.writeText(context, fileName, "text/markdown", content)

    /** 文件名：pient-sessions-20260916-0102.md（毫秒 + 会话数，避免重名覆盖） */
    fun fileName(count: Int): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "pient-sessions-$ts-${count}.md"
    }

    private fun appendMsg(sb: StringBuilder, m: Msg) {
        when (m) {
            is Msg.User -> {
                sb.append(L.project.exportUserHeading)
                m.quote?.let { q ->
                    sb.append(L.project.exportQuote).append(if (q.role == "assistant") L.project.exportQuoteAi else L.project.exportQuoteUser).append("：\n")
                    q.text.trim().lines().forEach { sb.append("> ").append(it).append('\n') }
                    sb.append('\n')
                }
                sb.append(m.text.trim()).append("\n\n")
                if (m.attachments.isNotEmpty()) {
                    sb.append(L.project.exportAttachments).append(m.attachments.joinToString("、") { it.name }).append("\n\n")
                }
            }
            is Msg.Assistant -> {
                sb.append("### AI").append(m.model?.let { "（$it）" } ?: "").append("\n\n")
                sb.append(m.markdown.trim().ifEmpty { L.project.exportEmptyAnswer }).append("\n\n")
            }
            is Msg.Thinking -> {
                val text = m.text.trim()
                if (text.isNotEmpty()) {
                    sb.append(L.project.exportThinkingSummary)
                    sb.append(text).append("\n\n</details>\n\n")
                }
            }
            is Msg.ToolCall -> {
                sb.append(L.project.exportToolCallHeading).append(m.name).append("`\n\n")
                sb.append("```json\n").append(m.params.trim()).append("\n```\n\n")
                m.detail?.takeIf { it.isNotBlank() }?.let { sb.append(it.trim()).append("\n\n") }
            }
            is Msg.ToolResult -> {
                sb.append(L.project.exportToolResultHeading).append(m.toolName).append("`\n\n")
                sb.append("```\n").append((m.full ?: m.preview).trim()).append("\n```\n\n")
            }
            is Msg.Compaction -> {
                sb.append(L.project.exportCompaction).append(m.tokensBefore).append(L.project.exportCompactionSaved).append(m.saved)
                    .append(" tokens").append(m.reason?.let { "（$it）" } ?: "").append("\n\n")
                m.summary.trim().takeIf { it.isNotEmpty() }?.let { sb.append(it).append("\n\n") }
            }
        }
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))
}
