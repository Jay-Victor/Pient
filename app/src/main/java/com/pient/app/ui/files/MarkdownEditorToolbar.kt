package com.pient.app.ui.files

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
 * Markdown 源码模式底部格式工具栏（2026-09-11 新增）。
 *
 * 参照 `Refences/Mdcito-1.2.0`（`ui/editor/EditorToolbar.kt`）逐值对齐；容器/按键/分组竖线
 * 与代码符号工具栏共用一份实现（见 `EditorToolbarCommon.kt`）：
 * - 分组：撤销/取消撤销 ｜ H1-H6 ｜ 斜体/粗体/粗斜体 ｜ 删除线/分割线 ｜ 无序/有序/任务列表 ｜
 *   行内代码/代码块/引用 ｜ 链接/图片/表格 ｜（右侧固定）搜索
 *
 * 相对 Mdcito 的差异：①数学公式组（∑ / ∑∑）未移植——Pient 的 Markdown 渲染层无公式支持，
 * 插进去也渲染不出来；②未做长按提示气泡（Mdcito 的 SmartToolbarTooltip），改用 contentDescription 无障碍名。
 */

/** 工具栏可触发的 markdown 格式动作（与 Mdcito EditorToolbar 回调一一对应） */
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
        // 可横向滚动的格式按钮区（Mdcito：weight(1f) + horizontalScroll）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        ) {
            EditorToolbarButton(icon = Icons.AutoMirrored.Outlined.Undo, desc = "撤销", enabled = canUndo, onClick = onUndo)
            EditorToolbarButton(icon = Icons.AutoMirrored.Outlined.Redo, desc = "取消撤销", enabled = canRedo, onClick = onRedo)

            EditorToolbarDivider()

            for (level in 1..6) {
                EditorToolbarButton(
                    text = "H$level",
                    desc = "$level 级标题",
                    onClick = { onFormat(MdFormat.Heading(level)) },
                )
            }

            EditorToolbarDivider()

            EditorToolbarButton(icon = Icons.Outlined.FormatItalic, desc = "斜体", onClick = { onFormat(MdFormat.Italic) })
            EditorToolbarButton(icon = Icons.Outlined.FormatBold, desc = "粗体", onClick = { onFormat(MdFormat.Bold) })
            EditorToolbarButton(text = "B I", boldText = true, desc = "粗斜体", onClick = { onFormat(MdFormat.BoldItalic) })

            EditorToolbarDivider()

            EditorToolbarButton(icon = Icons.Outlined.FormatStrikethrough, desc = "删除线", onClick = { onFormat(MdFormat.Strike) })
            EditorToolbarButton(icon = Icons.Outlined.HorizontalRule, desc = "分割线", onClick = { onFormat(MdFormat.Rule) })

            EditorToolbarDivider()

            EditorToolbarButton(icon = Icons.AutoMirrored.Outlined.FormatListBulleted, desc = "无序列表", onClick = { onFormat(MdFormat.UnorderedList) })
            EditorToolbarButton(icon = Icons.Outlined.FormatListNumbered, desc = "有序列表", onClick = { onFormat(MdFormat.OrderedList) })
            EditorToolbarButton(icon = Icons.Outlined.CheckBox, desc = "任务列表", onClick = { onFormat(MdFormat.TaskList) })

            EditorToolbarDivider()

            EditorToolbarButton(icon = Icons.Outlined.Code, desc = "行内代码", onClick = { onFormat(MdFormat.InlineCode) })
            EditorToolbarButton(icon = Icons.Outlined.DataObject, desc = "代码块", onClick = { onFormat(MdFormat.CodeBlock) })
            EditorToolbarButton(icon = Icons.Outlined.FormatQuote, desc = "引用", onClick = { onFormat(MdFormat.Quote) })

            EditorToolbarDivider()

            EditorToolbarButton(icon = Icons.Outlined.InsertLink, desc = "链接", onClick = { onFormat(MdFormat.Link) })
            EditorToolbarButton(icon = Icons.Outlined.Image, desc = "图片", onClick = { onFormat(MdFormat.Image) })
            EditorToolbarButton(icon = Icons.Outlined.TableChart, desc = "表格", onClick = { onFormat(MdFormat.Table) })
        }

        // 搜索键固定在右侧，不随滚动隐藏（Mdcito 同款）
        EditorToolbarDivider()
        EditorToolbarButton(icon = Icons.Outlined.Search, desc = "搜索", onClick = onSearch)
    }
}
