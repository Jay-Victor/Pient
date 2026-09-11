package com.pient.app.ui.files

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.ui.theme.PientPanel

/**
 * Markdown 源码模式底部格式工具栏（2026-09-11 新增）。
 *
 * 参照 `Refences/Mdcito-1.2.0`（`ui/editor/EditorToolbar.kt`）逐值对齐：
 * - 容器：顶部圆角 20dp、顶部 0.5dp 分隔线（outline 10%）、触摸屏高 64dp（小屏 <600dp）/ 平板 56dp、
 *   内边距水平 8dp / 垂直 10dp（平板 6dp）
 * - 按钮：44dp（平板 36dp）方块、8dp 圆角、按下缩放 0.85（tween 100ms）、
 *   图标 20dp（平板 18dp）、文字型按钮 13sp（"B I" 11sp 粗体）
 * - 分组：撤销/取消撤销 ｜ H1-H6 ｜ 斜体/粗体/粗斜体 ｜ 删除线/分割线 ｜ 无序/有序/任务列表 ｜
 *   行内代码/代码块/引用 ｜ 链接/图片/表格 ｜（右侧固定）搜索
 * - 分组之间竖线：1dp × 24dp、outline 30%、左右各 4dp
 *
 * 相对 Mdcito 的差异：①数学公式组（∑ / ∑∑）未移植——Pient 的 Markdown 渲染层无公式支持，
 * 插进去也渲染不出来；②未做长按提示气泡（Mdcito 的 SmartToolbarTooltip），改用 contentDescription 无障碍名；
 * ③容器用 PientPanel（Pient 统一材质：纯色面板底 + 1dp 描边，不用阴影）而非 Mdcito 的 Surface 阴影。
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

/** 工具栏高度（触摸屏 64dp / 平板 56dp，Mdcito 同口径；FAB 抬升量按此计算） */
@Composable
internal fun mdToolbarHeight(): Dp =
    if (LocalConfiguration.current.smallestScreenWidthDp < 600) 64.dp else 56.dp

private data class MdToolbarItem(
    val format: MdFormat? = null,
    val icon: ImageVector? = null,
    val text: String? = null,
    val boldText: Boolean = false,
    val desc: String,
)

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
    val isTouch = LocalConfiguration.current.smallestScreenWidthDp < 600
    val toolbarHeight = mdToolbarHeight()

    PientPanel(
        // navigationBarsPadding → imePadding 的顺序与聊天页输入栏 dock 一致：IME 抬起时整条工具栏
        // 随键盘上移（面板底色覆盖到屏底，圆角顶边贴在键盘上沿）
        modifier = modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column {
            // 顶部 0.5dp 分隔线（Mdcito：outline 10%，压在面板上沿）
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(toolbarHeight)
                    .padding(horizontal = 8.dp, vertical = if (isTouch) 10.dp else 6.dp),
            ) {
                // 可横向滚动的格式按钮区（Mdcito：weight(1f) + horizontalScroll）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                ) {
                    MdToolbarButton(icon = Icons.AutoMirrored.Outlined.Undo, desc = "撤销", enabled = canUndo, onClick = onUndo)
                    MdToolbarButton(icon = Icons.AutoMirrored.Outlined.Redo, desc = "取消撤销", enabled = canRedo, onClick = onRedo)

                    MdToolbarDivider()

                    for (level in 1..6) {
                        MdToolbarButton(
                            text = "H$level",
                            desc = "$level 级标题",
                            onClick = { onFormat(MdFormat.Heading(level)) },
                        )
                    }

                    MdToolbarDivider()

                    MdToolbarButton(icon = Icons.Outlined.FormatItalic, desc = "斜体", onClick = { onFormat(MdFormat.Italic) })
                    MdToolbarButton(icon = Icons.Outlined.FormatBold, desc = "粗体", onClick = { onFormat(MdFormat.Bold) })
                    MdToolbarButton(text = "B I", boldText = true, desc = "粗斜体", onClick = { onFormat(MdFormat.BoldItalic) })

                    MdToolbarDivider()

                    MdToolbarButton(icon = Icons.Outlined.FormatStrikethrough, desc = "删除线", onClick = { onFormat(MdFormat.Strike) })
                    MdToolbarButton(icon = Icons.Outlined.HorizontalRule, desc = "分割线", onClick = { onFormat(MdFormat.Rule) })

                    MdToolbarDivider()

                    MdToolbarButton(icon = Icons.AutoMirrored.Outlined.FormatListBulleted, desc = "无序列表", onClick = { onFormat(MdFormat.UnorderedList) })
                    MdToolbarButton(icon = Icons.Outlined.FormatListNumbered, desc = "有序列表", onClick = { onFormat(MdFormat.OrderedList) })
                    MdToolbarButton(icon = Icons.Outlined.CheckBox, desc = "任务列表", onClick = { onFormat(MdFormat.TaskList) })

                    MdToolbarDivider()

                    MdToolbarButton(icon = Icons.Outlined.Code, desc = "行内代码", onClick = { onFormat(MdFormat.InlineCode) })
                    MdToolbarButton(icon = Icons.Outlined.DataObject, desc = "代码块", onClick = { onFormat(MdFormat.CodeBlock) })
                    MdToolbarButton(icon = Icons.Outlined.FormatQuote, desc = "引用", onClick = { onFormat(MdFormat.Quote) })

                    MdToolbarDivider()

                    MdToolbarButton(icon = Icons.Outlined.InsertLink, desc = "链接", onClick = { onFormat(MdFormat.Link) })
                    MdToolbarButton(icon = Icons.Outlined.Image, desc = "图片", onClick = { onFormat(MdFormat.Image) })
                    MdToolbarButton(icon = Icons.Outlined.TableChart, desc = "表格", onClick = { onFormat(MdFormat.Table) })
                }

                // 搜索键固定在右侧，不随滚动隐藏（Mdcito 同款）
                MdToolbarDivider()
                MdToolbarButton(icon = Icons.Outlined.Search, desc = "搜索", onClick = onSearch)
            }
        }
    }
}

@Composable
private fun MdToolbarButton(
    icon: ImageVector? = null,
    text: String? = null,
    desc: String? = null,
    boldText: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val isTouch = LocalConfiguration.current.smallestScreenWidthDp < 600
    val btnSize = if (isTouch) 40.dp else 36.dp
    val iconSize = if (isTouch) 20.dp else 18.dp
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = tween(100),
        label = "md-toolbar-btn",
    )
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    }

    Box(
        modifier = Modifier
            .size(btnSize)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (text != null) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontSize = if (boldText) 11.sp else 13.sp,
                fontWeight = if (boldText) FontWeight.Bold else FontWeight.Normal,
                color = tint,
            )
        } else if (icon != null) {
            Icon(icon, desc, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

/** 分组竖线（Mdcito ToolbarDivider 1dp × 24dp / outline 30%；左右内边距由 4dp 收到 2dp = 按键间距收窄） */
@Composable
private fun MdToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 8.dp)
            .size(width = 1.dp, height = 24.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    )
}
