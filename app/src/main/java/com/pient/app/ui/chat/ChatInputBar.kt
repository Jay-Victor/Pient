package com.pient.app.ui.chat

import com.pient.app.data.i18n.L
import com.pient.app.runtime.PiCommands
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.pient.app.data.InputBarStyle
import com.pient.app.data.Quote
import com.pient.app.data.SettingsStore
import com.pient.app.ui.components.ContextIndicator
import com.pient.app.ui.files.nodeIcon
import com.pient.app.ui.theme.PientGlassSurface
import com.kyant.backdrop.Backdrop

/**
 * 输入栏 dock（P1 核心，设计计划 3.4）：
 * 多行输入框（2–6 行，中文全拼/IME 组合态）；左下方
 * 模型选择器 + 上下文指示器；右下方 "+" 与发送键；流式中发送键
 * 变停止（abort），模型选择器禁用置灰。
 *
 * 外观由「主题与外观 → 输入框设置」决定（2026-09-12）：
 * - 输入框样式：贴底（与屏幕底边齐平、上两角 16dp）/ 悬浮（四角全圆角、四周留白浮起）；
 * - 输入框材质：默认（纯色面板）/ 磨砂玻璃 / 液态玻璃（见 [PientGlassSurface]）。
 */
@Composable
fun ChatInputBar(
    chatState: ChatState,
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    mentionFiles: List<MentionFile> = emptyList(), // @ 引用候选（chip 派生 + 输入框高亮共用）
    slashCommands: List<PiCommands.Item> = emptyList(), // pi 命令面（`/命令` token 的 pill 与高亮共用）
    onOpenModelSelector: () -> Unit,
    onOpenAttach: () -> Unit,
    onToggleContextCard: () -> Unit,
    onToggleSystemPrompt: () -> Unit,
    onSend: (String) -> Unit,
    onAbort: () -> Unit,
    modelSelectorOpen: Boolean = false, // 模型弹窗开合状态：箭头上下指示
    /** 待发送的引用块（引用某条消息追问；null = 无引用）。发送后由外部清空。 */
    quote: Quote? = null,
    onRemoveQuote: () -> Unit = {},
    /** 递增计数：变化即聚焦输入框（菜单选「引用」后直接接着打字追问） */
    focusTick: Int = 0,
    onChipPositioned: (Float) -> Unit = {}, // 模型按键上缘 y（root 坐标，px）：供弹窗底部锚定
    onDockTopPositioned: (Float) -> Unit = {}, // dock 上缘 y（root 坐标，px）：供 @ 引用卡锚定
    /** 输入栏背后内容层的 backdrop（2026-09-12）：玻璃采样「内容滑过输入栏」的实时画面 */
    backdrop: Backdrop? = null,
) {
    var fullscreenOpen by rememberSaveable { mutableStateOf(false) }
    val streaming = chatState.isStreaming
    // 润色提示词（2026-09-16，用户 spec）：润色期间输入框只读 —— 用户不得在润色进行中改动提示词，
    // 直到结果回来（润色键此时转圈、发送键与全屏输入一并禁用）
    val polishing = chatState.polishing
    val polishRevertable = chatState.polishRevertTarget != null
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusTick) {
        if (focusTick > 0) runCatching { focusRequester.requestFocus() }
    }
    // 输入框 token 高亮（@ 引用照 Operit MentionVisualTransformation 同款规格，2026-09-19 起 `/命令` 同款：
    // 主色 14% 底 + 主色字 + 0.88x + Medium）—— 用户要求「用 / 调用技能也要像 @ 那样有个块」
    val tokenTransformation = rememberTokenVisualTransformation(mentionFiles, slashCommands)

    // 输入框设置（2026-09-12）：贴底 / 悬浮 + 材质（默认 / 磨砂玻璃 / 液态玻璃）
    val floating = SettingsStore.inputBarStyle == InputBarStyle.FLOATING
    val dockShape = if (floating) RoundedCornerShape(28.dp)
    else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

    PientGlassSurface(
        material = SettingsStore.inputBarMaterial,
        shape = dockShape,
        floating = floating,
        transparency = SettingsStore.inputBarTransparency,
        frostIntensity = SettingsStore.inputBarFrostIntensity,
        extraBackdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            // 悬浮：四周留白（圆角矩形整体浮起）；贴底：仅保留上两角圆角、贴齐底边
            .padding(
                start = if (floating) 12.dp else 0.dp,
                end = if (floating) 12.dp else 0.dp,
                bottom = if (floating) 10.dp else 0.dp,
            )
            // ★ 整块面板 = 输入框的点击面（2026-09-12 修「有时点输入框键盘不弹」）：
            //   面板高 289px 里原先只有输入框那一条 126px 响应该点，其余全是死区
            //   （左右 12dp 内边距、面板上/下内边距、输入框与控件行之间的空白条、
            //   控件行中央 Spacer 约 81dp×48dp）——空面板看着像「一个大输入框」，
            //   用户点在哪儿都以为点的是输入框。这里让面板空白处也算输入框：
            //   聚焦 + 显式拉起键盘（文本框已聚焦但键盘被 BACK 收起时，只 requestFocus
            //   不会重新弹键盘，必须补 show()）。
            //   面板内的按键（模型选择器 / 上下文指示器 / 系统提示词 / + / 发送 /
            //   全屏输入 / 附件 × ）都是本节点的子节点，Main pass 子节点先消费事件，
            //   所以不会误触发这里；长按选词同理（子节点消费后本手势自动取消）。
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        runCatching { focusRequester.requestFocus() }
                        keyboard?.show()
                    },
                )
            }
            .onGloballyPositioned { onDockTopPositioned(it.positionInRoot().y) },
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth() // ★ 高度必须 wrap：fillMaxSize 会占满父级全部高度，把消息区挤没
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // ── 引用块卡（引用某条消息追问；在附件 chip 行之上，2026-09-11） ──
        if (quote != null) {
            QuoteCard(
                quote = quote,
                onRemove = onRemoveQuote,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        // ── 附件 chip 行（输入框上方；Hermes AttachmentPill 规格 2026-08-28） ──
        // 左侧 = "+" 菜单附件；右侧 = @ 引用文件 chip（由输入文本派生，单一事实源）
        val refMatches = remember(text.text, mentionFiles) { findMentionPathMatches(text.text, mentionFiles) }
        // `/命令` token（整条消息以 `/` 开头且命中 pi 命令面）：与 @ 引用同款，出一枚可删的 pill
        val slashToken = remember(text.text, slashCommands) { findSlashToken(text.text, slashCommands) }
        if (chatState.attachments.isNotEmpty() || refMatches.isNotEmpty() || slashToken != null) {
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
                        label = m.label,   // 带行范围后缀（如 tooltest/sample.py:6-9），别只显示路径
                        icon = nodeIcon(m.file.isDir, m.file.ext),
                        onRemove = {
                            // 移除整个 "@路径"（连同尾随空格），光标移到 token 起点
                            var end = m.endExclusive
                            if (end < text.text.length && text.text[end] == ' ') end++
                            val newText = text.text.removeRange(m.start, end)
                            onTextChange(TextFieldValue(newText, TextRange(m.start.coerceAtMost(newText.length))))
                        },
                    )
                }
                slashToken?.let { tk ->
                    AttachmentPill(
                        label = tk.item.label,   // 技能 = 去掉 `skill:` 前缀的名字（与 `/` 卡同一显示名）
                        icon = slashCommandIcon(tk.item.kind),
                        onRemove = {
                            // token 在整条消息开头：移除 "[0, endExclusive)"（连同尾随空格），光标回到起点
                            var end = tk.endExclusive
                            if (end < text.text.length && text.text[end] == ' ') end++
                            onTextChange(TextFieldValue(text.text.removeRange(0, end), TextRange(0)))
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
                visualTransformation = tokenTransformation,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                readOnly = polishing,
                minLines = 1,
                maxLines = 6,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    if (text.text.isEmpty()) {
                        Text(
                            L.chat.inputPlaceholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                        )
                    }
                    inner()
                },
            )
            // 润色提示词（2026-09-16，用户 spec）：
            // 润色 → 结果回填、本键变回退键 → 用户一改（ChatScreen 清回退态）就变回润色键；
            // 润色进行中：本键转圈、不可点，输入框只读。
            // **常显**（2026-09-17 用户口径：空框也要在位，只是灰态、不可点）——
            // 键位固定、不随打字闪烁；灰态取仓库既有的「禁用图标」写法 onSurfaceVariant 30%。
            val canPolish = text.text.isNotBlank()
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !polishing && (polishRevertable || canPolish)) {
                        if (polishRevertable) chatState.revertPolish()
                        else chatState.polishInput(text.text)
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    polishing -> Box(
                        modifier = Modifier
                            .size(18.dp)
                            .semantics { contentDescription = L.chat.polishing },
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    polishRevertable -> Icon(
                        Icons.Outlined.Undo, L.chat.polishUndo,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    else -> Icon(
                        Icons.Outlined.AutoFixHigh, L.chat.polish,
                        // 空框 = 无内容可润色：灰态（不可点）；有内容 = 与相邻全屏输入键同色
                        tint = if (canPolish) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // 全屏输入入口（设计计划 3.4；点开 FullscreenInputDialog）
            // 润色中禁用：那是同一份文本的另一个编辑器（润色期间不许改动提示词）
            IconButton(
                onClick = { fullscreenOpen = true },
                enabled = !polishing,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.OpenInFull, L.chat.fullscreenInput,
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
                    chatState.selectedModel?.name ?: L.chat.noModelSelected,
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
                Icons.Outlined.Description, L.chat.systemPrompt,
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
                    Icons.Outlined.Add, L.chat.addAttachment,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }

            // 发送 / 停止（46dp 触控目标）
            // 润色中不可发送：正文正在被改写，此刻发出去的只会是半成品
            val canSend = (text.text.isNotBlank() || chatState.attachments.isNotEmpty()) && !polishing
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
                        Icons.Outlined.Send, L.chat.send,
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

/** `/命令` token 的 pill 图标（对应命令面三类：技能 / 提示模板 / 扩展命令） */
private fun slashCommandIcon(kind: PiCommands.Kind): ImageVector = when (kind) {
    PiCommands.Kind.SKILL -> Icons.Outlined.AutoAwesome
    PiCommands.Kind.PROMPT -> Icons.Outlined.Description
    PiCommands.Kind.COMMAND -> Icons.Outlined.Terminal
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
            Icons.Outlined.Close, L.chat.removeAttachment,
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
 * 输入框 token 高亮（参照 Operit MentionVisualTransformation.kt）：
 * 对已匹配的完整 "@路径" 与开头的 "/命令" 施加 主色文字 + 主色 14% 背景 + 0.88x 字号 + Medium 字重。
 * 只高亮已知文件路径与 pi 真认的命令（未输完的 @ / 半截 `/` 不高亮）。
 */
@Composable
private fun rememberTokenVisualTransformation(
    files: List<MentionFile>,
    slashCommands: List<PiCommands.Item>,
): VisualTransformation {
    val primary = MaterialTheme.colorScheme.primary
    val baseFontSize = MaterialTheme.typography.bodyLarge.fontSize
    return remember(files, slashCommands, primary, baseFontSize) {
        VisualTransformation { text ->
            val matches = findMentionPathMatches(text.text, files)
            val slash = findSlashToken(text.text, slashCommands)
            if (matches.isEmpty() && slash == null) {
                TransformedText(text, OffsetMapping.Identity)
            } else {
                TransformedText(
                    buildAnnotatedString {
                        append(text)
                        val tokenStyle = SpanStyle(
                            color = primary,
                            background = primary.copy(alpha = 0.14f),
                            fontSize = baseFontSize * 0.88f,
                            fontWeight = FontWeight.Medium,
                        )
                        matches.forEach { m -> addStyle(tokenStyle, m.start, m.endExclusive) }
                        slash?.let { addStyle(tokenStyle, 0, it.endExclusive) }
                    },
                    OffsetMapping.Identity,
                )
            }
        }
    }
}
