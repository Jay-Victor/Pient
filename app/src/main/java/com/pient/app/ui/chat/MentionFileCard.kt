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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.data.FileNode
import com.pient.app.ui.theme.PientPanel

/** @ 引用候选文件（mock 文件树扁平化产物） */
data class MentionFile(
    val name: String,
    val path: String,   // 项目相对路径（@ 引用格式用）
    val isImage: Boolean = false,
)

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
                isImage = child.imageHint != null || child.ext in IMAGE_EXTS,
            )
        }
    }
    return result
}

private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "gif")

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
 * @ 引用文件卡片（2026-08-28 新增）：输入框以 "@" 结尾时以悬浮浮层出现在
 * 输入框左上方（覆盖聊天内容，不挤压布局）；卡片列项目文件夹内的文件
 * （图标 + 文件名 + 相对路径），点选后 "@" 替换为 "@路径" 内联引用
 * （pi @ 提及语义）。点外关闭由父级透明遮罩处理。
 */
@Composable
fun MentionFileCard(
    files: List<MentionFile>,
    onPick: (String) -> Unit,
    bottomOffset: Dp = 8.dp, // 弹窗底部到屏幕底的距离（与模型选择器同口径）
    modifier: Modifier = Modifier,
) {
    PientPanel(
        modifier = modifier
            .padding(start = 6.dp, bottom = bottomOffset)
            .width(268.8.dp), // 与右下浮层同宽；左对齐、左距屏 6dp 对称
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
        Column(Modifier.padding(top = 2.dp)) {
            if (files.isEmpty()) {
                Text(
                    "项目文件夹内暂无文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            files.forEach { f ->
                MentionFileRow(f) { onPick(f.path) }
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

private fun iconFor(f: MentionFile): ImageVector =
    if (f.isImage) Icons.Outlined.Image else Icons.Outlined.Description
