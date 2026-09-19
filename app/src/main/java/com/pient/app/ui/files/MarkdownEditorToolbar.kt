package com.pient.app.ui.files

import com.pient.app.data.i18n.L
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertLink
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Markdown 源码模式底部格式工具栏。
 *
 * 容器/按键/分组竖线与代码符号工具栏共用一份实现（见 `EditorToolbarCommon.kt`）：
 * - 分组：撤销/取消撤销 ｜ H1-H6 ｜ 斜体/粗体/粗斜体 ｜ 删除线/分割线 ｜ 无序/有序/任务列表 ｜
 *   行内代码/代码块/引用 ｜ 链接/图片/表格 ｜（右侧固定）搜索
 *
 * 有意不做：①数学公式组（∑ / ∑∑）——Pient 的 Markdown 渲染层无公式支持，插进去也渲染不出来；
 * ②长按提示气泡——改用 contentDescription 无障碍名。
 */

/** 工具栏可触发的 markdown 格式动作（与工具栏按键一一对应） */
internal sealed interface MdFormat {
    data class Heading(val level: Int) : MdFormat
    data object Italic : MdFormat
    data object Bold : MdFormat
    data object BoldItalic : MdFormat
    data object Strike : MdFormat
    data object Rule : MdFormat
    data object UnorderedList : MdFormat
    data object OrderedList : MdFormat
    data object TaskList : MdFormat
    data object InlineCode : MdFormat
    data object CodeBlock : MdFormat
    data object Quote : MdFormat
    data object Link : MdFormat
    data object Image : MdFormat
    data object Table : MdFormat
}

@Composable
internal fun MarkdownEditorToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFormat: (MdFormat) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EditorToolbarContainer(modifier) {
        // 可横向滚动的格式按钮区（weight(1f) + horizontalScroll）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        ) {
            EditorToolbarButton(icon = Icons.AutoMirrored.Outlined.Undo, desc = L.common.undo, enabled = canUndo, onClick = onUndo)
            EditorToolbarButton(icon = Icons.AutoMirrored.Outlined.Redo, desc = L.files.redo, enabled = canRedo, onClick = onRedo)

            EditorToolbarDivider()

            for (level in 1..6) {
                EditorToolbarButton(
                    text = "H$level",
                    desc = L.files.headingLevel(level),
                    onClick = { onFormat(MdFormat.Heading(level)) },
                )
            }

            EditorToolbarDivider()

            EditorToolbarButton(icon = Icons.Outlined.FormatItalic, desc = L.files.italic, onClick = { onFormat(MdFormat.Italic) })
            EditorToolbarButton(icon = Icons.Outlined.FormatBold, desc = L.files.bold, onClick = { onFormat(MdFormat.Bold) })
            EditorToolbarButton(text = "B I", boldText = true, desc = L.files.boldItalic, onClick = { onFormat(MdFormat.BoldItalic) })

            EditorToolbarDivider()

            EditorToolbarButton(icon = Icons.Outlined.FormatStrikethrough, desc = L.files.strikethrough, onClick = { onFormat(MdFormat.Strike) })
            EditorToolbarButton(icon = Icons.Outlined.HorizontalRule, desc = L.files.horizontalRule, onClick = { onFormat(MdFormat.Rule) })

            EditorToolbarDivider()

            EditorToolbarButton(icon = Icons.AutoMirrored.Outlined.FormatListBulleted, desc = L.files.bulletList, onClick = { onFormat(MdFormat.UnorderedList) })
            EditorToolbarButton(icon = Icons.Outlined.FormatListNumbered, desc = L.files.numberedList, onClick = { onFormat(MdFormat.OrderedList) })
            EditorToolbarButton(icon = Icons.Outlined.CheckBox, desc = L.files.taskList, onClick = { onFormat(MdFormat.TaskList) })

            EditorToolbarDivider()

            EditorToolbarButton(icon = Icons.Outlined.Code, desc = L.files.inlineCode, onClick = { onFormat(MdFormat.InlineCode) })
            EditorToolbarButton(icon = Icons.Outlined.DataObject, desc = L.files.codeBlock, onClick = { onFormat(MdFormat.CodeBlock) })
            EditorToolbarButton(icon = Icons.Outlined.FormatQuote, desc = L.files.quote, onClick = { onFormat(MdFormat.Quote) })

            EditorToolbarDivider()

            EditorToolbarButton(icon = Icons.Outlined.InsertLink, desc = L.files.link, onClick = { onFormat(MdFormat.Link) })
            EditorToolbarButton(icon = Icons.Outlined.Image, desc = L.common.image, onClick = { onFormat(MdFormat.Image) })
            EditorToolbarButton(icon = Icons.Outlined.TableChart, desc = L.files.table, onClick = { onFormat(MdFormat.Table) })
        }

        // 搜索键固定在右侧，不随滚动隐藏
        EditorToolbarDivider()
        EditorToolbarButton(icon = Icons.Outlined.Search, desc = L.common.search, onClick = onSearch)
    }
}
