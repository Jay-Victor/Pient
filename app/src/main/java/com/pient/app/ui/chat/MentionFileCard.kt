package com.pient.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.data.FileNode
import com.pient.app.ui.files.PREVIEW_IMAGE_EXTS
import com.pient.app.ui.files.fileIcon
import com.pient.app.ui.theme.PientPanel

/** @ 引用候选文件（mock 文件树扁平化产物） */
data class MentionFile(
    val name: String,
    val path: String,   // 项目相对路径（@ 引用格式用）
    /**
     * 小写扩展名（FileNode.ext 同源）。图标与类型判定必须走文件树同一函数
     * `fileIcon(ext)` —— 此前本模型自带 `isImage` 布尔 + 各处各写一份图标
     * （非图片一律 Description），与文件树（video=MOVIE / audio=MUSIC_NOTE /
     * 其它=INSERT_DRIVE_FILE）不一致，@ 引用 chip 的图案与文件树对不上。
     */
    val ext: String = "",
) {
    val isImage: Boolean get() = ext in PREVIEW_IMAGE_EXTS
}

/** 文本中已提交的 @ 引用路径（含起止下标；供 chip 派生与输入框高亮共用） */
data class MentionPathMatch(
    val start: Int,          // '@' 下标
    val endExclusive: Int,   // '@路径' 末尾下标
    val file: MentionFile,
)

/**
 * 扫描输入文本中的 @ 引用：每个 '@' 后尝试匹配已知文件路径（最长优先）。
 * 仅匹配完整路径（中途未输完/非法 token 不高亮不建 chip）。
 */
fun findMentionPathMatches(text: String, files: List<MentionFile>): List<MentionPathMatch> {
    if (text.isBlank() || files.isEmpty()) return emptyList()
    val byPath = files.sortedByDescending { it.path.length }
    val result = mutableListOf<MentionPathMatch>()
    var i = 0
    while (i < text.length) {
        val at = text.indexOf('@', i)
        if (at < 0) break
        val rest = text.substring(at + 1)
        val match = byPath.firstOrNull { rest.startsWith(it.path) }
        if (match != null) {
            result += MentionPathMatch(at, at + 1 + match.path.length, match)
            i = at + 1 + match.path.length
        } else {
            i = at + 1
        }
    }
    return result
}

/** 遍历项目文件树，扁平化为 @ 引用候选（仅文件，含相对路径） */
fun buildMentionFiles(node: FileNode, prefix: String = ""): List<MentionFile> {
    val result = mutableListOf<MentionFile>()
    for (child in node.children) {
        val p = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
        if (child.isDir) {
            result += buildMentionFiles(child, p)
        } else {
            result += MentionFile(
                name = child.name,
                path = p,
                ext = child.ext,
            )
        }
    }
    return result
}

/** 图标一律走文件树共享函数：见 ui/files/FilesPanel.kt 的 fileIcon(ext)（原 IMAGE_EXTS 清单已废弃） */

/** 光标处正在输入的 @ 引用查询：[@ 下标, 光标) 区间 + 查询串（可能为空 = 刚敲下 @） */
data class MentionQuery(
    val start: Int,          // '@' 下标
    val endExclusive: Int,   // 光标下标（= 查询串终点，不含）
    val text: String,        // '@' 与光标之间的筛选文本
)

/**
 * 光标处是否有正在输入的 @ 引用查询。
 * 从光标向前找最近的「词首 @」（行首或空白之后），且它与光标之间不含空白/换行 —— 这段文本即查询串；
 * 途中先遇到空白 = 引用已提交（"@路径 " 之后）或不是引用 → 返回 null；
 * '@' 出现在词中（邮箱等）也返回 null。
 * 返回值带区间，供选文件时把「@ + 已输入筛选字符」整段替换为 "@路径 "。
 */
fun findMentionQueryAt(value: TextFieldValue): MentionQuery? {
    if (!value.selection.collapsed) return null
    val text = value.text
    val cursor = value.selection.start.coerceIn(0, text.length)
    var i = cursor - 1
    while (i >= 0) {
        val c = text[i]
        when {
            c == '@' -> {
                val prev = if (i == 0) ' ' else text[i - 1]
                if (!prev.isWhitespace()) return null
                return MentionQuery(i, cursor, text.substring(i + 1, cursor))
            }
            c.isWhitespace() -> return null
            else -> i--
        }
    }
    return null
}

/**
 * 按查询串筛选 @ 候选：大小写不敏感；文件名前缀命中 > 文件名包含 > 路径包含，
 * 同级保持文件树原顺序（sortedBy 稳定）。查询串为空 = 原样列出全部。
 */
fun filterMentionFiles(files: List<MentionFile>, query: String): List<MentionFile> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return files
    return files.mapNotNull { f ->
        val name = f.name.lowercase()
        val path = f.path.lowercase()
        val rank = when {
            name.startsWith(q) -> 0
            name.contains(q) -> 1
            path.contains(q) -> 2
            else -> return@mapNotNull null
        }
        rank to f
    }.sortedBy { it.first }.map { it.second }
}

/**
 * 光标处是否有完整的 @ 引用 token 收尾（含或不含尾随空格）。
 * 返回整个 token 的删除范围（起点含 '@'，终点含尾随空格）——与 Operit
 * MentionTokenUtils.findMentionTokenEndingAtCursor 同语义：光标停在
 * "@路径" 末尾或 "@路径 " 末尾都算。用于"一键删除整段 @ 引用"。
 */
fun findMentionTokenEndingAtCursor(
    text: String,
    cursor: Int,
    files: List<MentionFile>,
): IntRange? {
    val safeCursor = cursor.coerceIn(0, text.length)
    return findMentionPathMatches(text, files).firstOrNull { m ->
        val contentEnd = m.endExclusive
        val hasTrailingSpace = contentEnd < text.length && text[contentEnd] == ' '
        safeCursor == contentEnd || (hasTrailingSpace && safeCursor == contentEnd + 1)
    }?.let { m ->
        val end = m.endExclusive +
            (if (m.endExclusive < text.length && text[m.endExclusive] == ' ') 1 else 0)
        m.start until end
    }
}

/**
 * @ 引用删除归一化（参照 Operit ChatViewModel.normalizeMentionDeletion）：
 * 检测"单字符退格且光标停在某 @ 引用 token 末尾"，把整个 token
 * （含尾随空格）一次删掉、光标移到 token 起点。其余编辑原样放行。
 */
fun normalizeMentionDeletion(
    previous: TextFieldValue,
    proposed: TextFieldValue,
    files: List<MentionFile>,
): TextFieldValue {
    if (previous.selection.start != previous.selection.end) return proposed
    if (proposed.selection.start != proposed.selection.end) return proposed
    if (previous.text.length != proposed.text.length + 1) return proposed
    val oldCursor = previous.selection.start.coerceIn(0, previous.text.length)
    val newCursor = proposed.selection.start.coerceIn(0, proposed.text.length)
    if (newCursor != oldCursor - 1) return proposed
    if (previous.text.removeRange(newCursor, oldCursor) != proposed.text) return proposed
    val range = findMentionTokenEndingAtCursor(previous.text, oldCursor, files) ?: return proposed
    return proposed.copy(
        text = previous.text.removeRange(range.first, range.last + 1),
        selection = TextRange(range.first),
    )
}

/**
 * @ 引用文件卡片（2026-08-28 新增，2026-09-12 加高度上限与实时筛选）：输入框里输入 "@" 后以悬浮浮层
 * 出现在输入框左上方（覆盖聊天内容，不挤压布局）；卡片列项目文件夹内的文件（图标 + 文件名 + 相对路径），
 * 点选后把「@ + 已输入筛选字符」整段替换为 "@路径 " 内联引用（pi @ 提及语义）。
 * 点外关闭由父级透明遮罩处理。
 *
 * - 高度上限 = 屏幕高 40%（与模型选择器同口径）：候选多时表头固定、候选列表内部滚动，卡片不会顶到屏外；
 * - [query] = 光标处已输入的筛选文本（"@" 之后的部分），仅用于空结果文案（筛选本身在调用处做，见
 *   filterMentionFiles —— 候选列表随输入实时收窄）。
 */
@Composable
fun MentionFileCard(
    files: List<MentionFile>,
    onPick: (String) -> Unit,
    bottomOffset: Dp = 8.dp, // 弹窗底部到屏幕底的距离（与模型选择器同口径）
    query: String = "",
    modifier: Modifier = Modifier,
) {
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp * 0.40f).dp
    PientPanel(
        modifier = modifier
            .padding(start = 6.dp, bottom = bottomOffset)
            .width(268.8.dp) // 与右下浮层同宽；左对齐、左距屏 6dp 对称
            .heightIn(max = maxCardHeight),
        shape = RoundedCornerShape(12.dp),
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            "引用文件",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        )
        if (files.isEmpty()) {
            Text(
                if (query.isBlank()) "项目文件夹内暂无文件" else "无匹配文件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        } else {
            // fill = false：候选少时卡片随内容收缩，多时占满上限后内部滚动
            LazyColumn(Modifier.padding(top = 2.dp).weight(1f, fill = false)) {
                items(files) { f -> MentionFileRow(f) { onPick(f.path) } }
            }
        }
    }
    }
}

@Composable
private fun MentionFileRow(f: MentionFile, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Icon(
            iconFor(f), null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Text(
                f.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                f.path,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** @ 候选行图标 = 文件树同一函数（fileIcon(ext)：图片/视频/音频/其它四态一致） */
private fun iconFor(f: MentionFile): ImageVector = fileIcon(f.ext)
