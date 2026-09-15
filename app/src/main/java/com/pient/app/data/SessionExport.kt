package com.pient.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 会话导出（2026-09-16；此前项目管理页的「导出会话」是原型占位 Toast）。
 *
 * 形态 = **Markdown**（pi 的会话条目本就是文本消息，导出成可读文本最通用、也能回灌给模型）：
 * 每个会话一个二级标题 + 项目/时间，逐条消息按角色输出；思考折叠成引用块，工具调用/结果各一行。
 *
 * 落点 = 系统「下载/Pient/」（MediaStore；API 29+ 免权限、系统文件管理器可见）；
 *       API 26~28 退到应用自己的外部下载目录（同样免权限，但路径在 Android/data 下）。
 */
object SessionExport {
    private const val TAG = "PientExport"
    private const val REL_DIR = "Pient"

    /**
     * 一组会话 → 一个 Markdown 文档。
     * @param picks 会话记录（跨项目按 id 取；顺序即文档顺序）
     */
    fun markdown(chat: ChatState, picks: List<Session>, title: String): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("# ").append(title).append("\n\n")
        sb.append("导出时间：").append(stamp).append("　·　会话数：").append(picks.size).append("\n\n")
        picks.forEachIndexed { idx, s ->
            sb.append("## ").append(s.title.ifBlank { "未命名会话" }).append("\n\n")
            sb.append("- 项目：").append(s.project)
                .append("　·　最后更新：").append(formatTime(s.updatedAt))
                .append("　·　消息数：").append(chat.messagesForExport(s.id).size).append("\n\n")
            val msgs = chat.messagesForExport(s.id)
            if (msgs.isEmpty()) {
                sb.append("（该会话没有可导出的消息）\n\n")
            } else {
                msgs.forEach { appendMsg(sb, it) }
            }
            if (idx != picks.lastIndex) sb.append("---\n\n")
        }
        return sb.toString()
    }

    /**
     * 写给系统「下载/Pient/<fileName>」，返回可展示的落点描述（失败返回 null）。
     */
    fun writeToDownloads(context: Context, fileName: String, content: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/markdown")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + REL_DIR)
            }
            val uri: Uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching null
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                ?: return@runCatching null
            Log.i(TAG, "已导出到下载/$REL_DIR/$fileName")
            "下载/$REL_DIR/$fileName"
        } else {
            // API 26~28：公共下载目录需要 WRITE_EXTERNAL_STORAGE，不引权限 → 落应用外部下载目录
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return@runCatching null
            File(dir, fileName).writeText(content)
            Log.i(TAG, "已导出到 ${File(dir, fileName).absolutePath}")
            File(dir, fileName).absolutePath
        }
    }.onFailure { Log.w(TAG, "导出失败：${it.message}") }.getOrNull()

    /** 文件名：pient-sessions-20260916-0102.md（毫秒 + 会话数，避免重名覆盖） */
    fun fileName(count: Int): String {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "pient-sessions-$ts-${count}.md"
    }

    private fun appendMsg(sb: StringBuilder, m: Msg) {
        when (m) {
            is Msg.User -> {
                sb.append("### 用户\n\n")
                m.quote?.let { q ->
                    sb.append("> 引用").append(if (q.role == "assistant") " AI 回答" else "用户消息").append("：\n")
                    q.text.trim().lines().forEach { sb.append("> ").append(it).append('\n') }
                    sb.append('\n')
                }
                sb.append(m.text.trim()).append("\n\n")
                if (m.attachments.isNotEmpty()) {
                    sb.append("附件：").append(m.attachments.joinToString("、") { it.name }).append("\n\n")
                }
            }
            is Msg.Assistant -> {
                sb.append("### AI").append(m.model?.let { "（$it）" } ?: "").append("\n\n")
                sb.append(m.markdown.trim().ifEmpty { "（空回答）" }).append("\n\n")
            }
            is Msg.Thinking -> {
                val text = m.text.trim()
                if (text.isNotEmpty()) {
                    sb.append("<details><summary>思考过程</summary>\n\n")
                    sb.append(text).append("\n\n</details>\n\n")
                }
            }
            is Msg.ToolCall -> {
                sb.append("### 工具调用 `").append(m.name).append("`\n\n")
                sb.append("```json\n").append(m.params.trim()).append("\n```\n\n")
                m.detail?.takeIf { it.isNotBlank() }?.let { sb.append(it.trim()).append("\n\n") }
            }
            is Msg.ToolResult -> {
                sb.append("### 工具结果 `").append(m.toolName).append("`\n\n")
                sb.append("```\n").append((m.full ?: m.preview).trim()).append("\n```\n\n")
            }
            is Msg.Compaction -> {
                sb.append("> 上下文压缩：").append(m.tokensBefore).append(" → 省 ").append(m.saved)
                    .append(" tokens").append(m.reason?.let { "（$it）" } ?: "").append("\n\n")
                m.summary.trim().takeIf { it.isNotEmpty() }?.let { sb.append(it).append("\n\n") }
            }
        }
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))
}
