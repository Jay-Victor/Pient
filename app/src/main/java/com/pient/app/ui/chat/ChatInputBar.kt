package com.pient.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.data.AttachmentKind
import com.pient.app.data.ChatState
import com.pient.app.ui.components.ContextIndicator
import com.pient.app.ui.theme.PientPanel

/**
 * 输入栏 dock（P1 核心，设计计划 3.4）：
 * 玻璃容器；多行输入框（2–6 行，中文全拼/IME 组合态）；左下方
 * 模型选择器 + 上下文指示器；右下方 "+" 与发送键；流式中发送键
 * 变停止（abort），模型选择器禁用置灰。
 */
@Composable
fun ChatInputBar(
    chatState: ChatState,
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    mentionFiles: List<MentionFile> = emptyList(), // @ 引用候选（chip 派生 + 输入框高亮共用）
    onOpenModelSelector: () -> Unit,
    onOpenAttach: () -> Unit,
    onToggleContextCard: () -> Unit,
    onToggleSystemPrompt: () -> Unit,
    onSend: (String) -> Unit,
    onAbort: () -> Unit,
    modelSelectorOpen: Boolean = false, // 模型弹窗开合状态：箭头上下指示
    onChipPositioned: (Float) -> Unit = {}, // 模型按键上缘 y（root 坐标，px）：供弹窗底部锚定
    onDockTopPositioned: (Float) -> Unit = {}, // dock 上缘 y（root 坐标，px）：供 @ 引用卡锚定
) {
    var fullscreenOpen by rememberSaveable { mutableStateOf(false) }
    val streaming = chatState.isStreaming
    // @ 引用高亮（Operit MentionVisualTransformation 同款：主色 14% 底 + 主色字 + 0.88x + Medium）
    val mentionTransformation = rememberMentionVisualTransformation(mentionFiles)

    PientPanel(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .onGloballyPositioned { onDockTopPositioned(it.positionInRoot().y) },
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth() // ★ 高度必须 wrap：fillMaxSize 会占满父级全部高度，把消息区挤没
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // ── 附件 chip 行（输入框上方；Hermes AttachmentPill 规格 2026-08-28） ──
        // 左侧 = "+" 菜单附件；右侧 = @ 引用文件 chip（由输入文本派生，单一事实源）
        val refMatches = remember(text.text, mentionFiles) { findMentionPathMatches(text.text, mentionFiles) }
        if (chatState.attachments.isNotEmpty() || refMatches.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chatState.attachments.forEachIndexed { i, att ->
                    AttachmentPill(
                        label = att.name,
                        icon = attachmentIcon(att.kind),
                        onRemove = { chatState.attachments.removeAt(i) },
                    )
                }
                refMatches.forEach { m ->
                    AttachmentPill(
                        label = m.file.path,
                        icon = if (m.file.isImage) Icons.Outlined.Image else Icons.Outlined.Description,
                        onRemove = {
                            // 移除整个 "@路径"（连同尾随空格），光标移到 token 起点
                            var end = m.endExclusive
                            if (end < text.text.length && text.text[end] == ' ') end++
                            val newText = text.text.removeRange(m.start, end)
                            onTextChange(TextFieldValue(newText, TextRange(m.start.coerceAtMost(newText.length))))
                        },
                    )
                }
            }
        }

        // ── 输入框（多行自适应 2–6 行；IME 组合态原生支持）+ 右上角全屏输入入口 ──
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                visualTransformation = mentionTransformation,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                minLines = 1,
                maxLines = 6,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (text.text.isEmpty()) {
                        Text(
                            "给 Agent 派个任务…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                        )
                    }
                    inner()
                },
            )
            // 全屏输入入口（设计计划 3.4；点开 FullscreenInputDialog）
            IconButton(
                onClick = { fullscreenOpen = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.OpenInFull, "全屏输入",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // ── 底部控件行 ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            // 模型选择器（流式中禁用置灰；不再替换为 steer/followUp——用户反馈
            // 发送后模型名"变掉"很困惑，2026-08-27 移除替换段控件）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .clickable(enabled = !streaming, onClick = onOpenModelSelector)
                    .onGloballyPositioned { onChipPositioned(it.positionInRoot().y) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(
                    chatState.selectedModel?.name ?: "未选择模型",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (streaming) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                    // 模型名过长时截断：Text 无上限会按固有宽度把 chip 撑到整行，
                    // 挤掉右侧上下文指示器/发送键（2026-09-09 修复）
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
                // 箭头用 Material 图标（与模型弹窗卡片内一致）并可上下指示开合；
                // 之前用文本字符 " ▾"，字形与弹窗内图标不一致（2026-08-27 修复）
                Icon(
                    if (modelSelectorOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp).size(16.dp),
                )
            }

            // 上下文指示器（收起态：环 + 百分比；点击开合用量卡浮层）
            ContextIndicator(
                percent = chatState.contextPercent,
                onToggle = onToggleContextCard,
            )

            // 系统提示词只读按键（2026-09-01，pi-web system 面板同款：
            // 文件图标；systemPrompt 非空时 accent 高亮，点击弹出只读全文浮层）
            Icon(
                Icons.Outlined.Description, "系统提示词",
                tint = if (chatState.systemPrompt.isNotEmpty()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(20.dp)
                    .clickable(onClick = onToggleSystemPrompt),
            )

            Spacer(Modifier.weight(1f))

            // "+" 附件
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenAttach),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Add, "添加附件",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }

            // 发送 / 停止（46dp 触控目标）
            val canSend = text.text.isNotBlank() || chatState.attachments.isNotEmpty()
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (streaming) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary.copy(alpha = if (canSend) 1f else 0.3f),
                    )
                    .clickable(enabled = streaming || canSend, onClick = {
                        if (streaming) onAbort()
                        else if (canSend) {
                            val t = text.text
                            onTextChange(TextFieldValue(""))
                            onSend(t)
                        }
                    }),
                contentAlignment = Alignment.Center,
            ) {
                if (streaming) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(3.dp)),
                    )
                } else {
                    Icon(
                        Icons.Outlined.Send, "发送",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
    }

    // ── 全屏输入弹窗（文本与输入栏共享状态） ──
    if (fullscreenOpen) {
        FullscreenInputDialog(
            text = text,
            onTextChange = onTextChange,
            canSend = !streaming && (text.text.isNotBlank() || chatState.attachments.isNotEmpty()),
            onSend = {
                val t = text.text
                onTextChange(TextFieldValue(""))
                fullscreenOpen = false
                onSend(t)
            },
            onDismiss = { fullscreenOpen = false },
        )
    }
}

/** 附件类型 → 图标（chip 展示；消息气泡附件 chip 与输入栏 pill 共用） */
internal fun attachmentIcon(kind: AttachmentKind): ImageVector = when (kind) {
    AttachmentKind.IMAGE -> Icons.Outlined.Image
    AttachmentKind.FILE -> Icons.Outlined.Description
    AttachmentKind.URL -> Icons.Outlined.Link
}

/**
 * 附件 pill（Hermes AttachmentPill 规格）：12dp 圆角 + hairline 边框 +
 * 28dp 圆角图标容器 + 文件名 + 常驻删除 ×。
 */
@Composable
private fun AttachmentPill(
    label: String,
    icon: ImageVector,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(12.dp),
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                RoundedCornerShape(12.dp),
            )
            .padding(6.dp),
    ) {
        // 类型图标容器（Hermes size-8 rounded-lg + border + muted 底）
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(8.dp),
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 8.dp)
                .widthIn(max = 220.dp),
        )
        Icon(
            Icons.Outlined.Close, "移除附件",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(24.dp)
                .clickable(onClick = onRemove)
                .padding(4.dp),
        )
    }
}

/**
 * @ 引用文字高亮（参照 Operit MentionVisualTransformation.kt）：
 * 对已匹配的完整 "@路径" 施加 主色文字 + 主色 14% 背景 + 0.88x 字号 + Medium 字重。
 * 只高亮已知文件路径（未输完的 @ 不高亮）。
 */
@Composable
private fun rememberMentionVisualTransformation(
    files: List<MentionFile>,
): VisualTransformation {
    val primary = MaterialTheme.colorScheme.primary
    val baseFontSize = MaterialTheme.typography.bodyLarge.fontSize
    return remember(files, primary, baseFontSize) {
        VisualTransformation { text ->
            val matches = findMentionPathMatches(text.text, files)
            if (matches.isEmpty()) {
                TransformedText(text, OffsetMapping.Identity)
            } else {
                TransformedText(
                    buildAnnotatedString {
                        append(text)
                        matches.forEach { m ->
                            addStyle(
                                SpanStyle(
                                    color = primary,
                                    background = primary.copy(alpha = 0.14f),
                                    fontSize = baseFontSize * 0.88f,
                                    fontWeight = FontWeight.Medium,
                                ),
                                m.start,
                                m.endExclusive,
                            )
                        }
                    },
                    OffsetMapping.Identity,
                )
            }
        }
    }
}
