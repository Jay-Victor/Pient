package com.pient.app.ui.terminal

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pient.app.data.ChatState
import androidx.compose.ui.platform.LocalContext
import com.pient.app.data.TerminalLine
import com.pient.app.data.TerminalLineKind
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.theme.LocalPientIsDark
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.PientPanel
import com.pient.app.ui.theme.TerminalDark
import com.pient.app.ui.theme.TerminalLight
import com.pient.app.runtime.PiTerminal
import kotlinx.coroutines.launch

/**
 * 终端页面（UI 重设计 2026-08-28，Operit TerminalHome 参考），自上而下：
 * 1. 顶部工具栏：[会话 ×] 标签 + 右端「+」新建会话；
 * 2. 输出区（等宽、无玻璃、选择复制、自动吸底）；
 * 3. 快捷按键栏：Ctrl+C 中断 / Ctrl+L 清屏，右端「环境配置」进入环境配置页；
 * 4. 输入栏：`~ $` 提示符 + 命令输入，右端 ⌨ 唤出 14 键额外按键栏
 *    （2×7：ESC / - HOME ↑ END PGUP / TAB CTRL ALT ← ↓ → PGDN，Operit 默认布局）。
 */
@Composable
fun TerminalPanel(chatState: ChatState, nav: NavController) {
    val termColors = if (LocalPientIsDark.current) TerminalDark else TerminalLight
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var showExtraKeys by remember { mutableStateOf(false) }
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    // 会话删除二次确认（Operit onTabCloseRequest 同款：弹窗确认后删除）
    var closeConfirmIndex by remember { mutableStateOf<Int?>(null) }
    val outputScroll = rememberScrollState()
    val keyScope = rememberCoroutineScope()
    // 命令历史（↑/↓ 回溯；mock）
    val history = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }

    // 真实会话引擎：首个会话在首次进入时建立（要起子进程，首帧先渲染空盒）
    val context = LocalContext.current
    LaunchedEffect(Unit) { PiTerminal.ensure(context) }
    val session = PiTerminal.sessions.getOrNull(chatState.terminalIndex) ?: PiTerminal.sessions.firstOrNull()
    if (session == null) return Box(Modifier.fillMaxSize())

    // 吸底：新输出自动跟随（用户上滚后可暂停）
    LaunchedEffect(session.lines.size) {
        outputScroll.scrollTo(outputScroll.maxValue)
    }

    fun runCommand(cmd: String) {
        if (cmd.isBlank()) return
        session.lines += TerminalLine("~ \$ $cmd", TerminalLineKind.COMMAND)
        PiTerminal.write(session, cmd)
        history.add(cmd)
        historyIndex = -1
        input = TextFieldValue("")
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ── 1. 顶部工具栏：会话标签 + 右端「+」新建会话 ──
            PientPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(0.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        PiTerminal.sessions.forEachIndexed { i, s ->
                            val sel = i == chatState.terminalIndex
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .height(30.dp)
                                    .widthIn(max = 120.dp)
                                    .background(
                                        if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                        else MaterialTheme.colorScheme.surfaceContainerLow,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .border(
                                        1.dp,
                                        if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .clickable(onClick = { chatState.terminalIndex = i })
                                    .padding(horizontal = 8.dp),
                            ) {
                                Text(
                                    s.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sel) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // 仅剩一个会话时不可关闭（Operit 同款约束，防空列表）
                                if (PiTerminal.sessions.size > 1) {
                                    Icon(
                                        Icons.Outlined.Close, "关闭终端会话",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(start = 4.dp)
                                            .size(12.dp)
                                            .clickable(onClick = { closeConfirmIndex = i }),
                                    )
                                }
                            }
                        }
                    }
                    Icon(
                        Icons.Outlined.Add, "新建终端会话",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(onClick = {
                            PiTerminal.newSession(context)
                            chatState.terminalIndex = PiTerminal.sessions.lastIndex
                        })
                            .padding(2.dp),
                    )
                }
            }

            // ── 2. 输出区（等宽、无玻璃、选择复制；用户上滚不打断） ──
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(termColors["bg"]!!),
            ) {
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(outputScroll),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        session.lines.forEach { line ->
                            TerminalLineRow(line, termColors)
                        }
                    }
                }
            }

            // ── 底部操作区：快捷按键栏 + 输入行 + 14 键栏 ──
            // ★ imePadding 必须加在本容器上（消息页 ChatInputBar 同款机制）：
            //   IME 弹出时整块抬到键盘之上；若只加在输入行上，排在其后的
            //   14 键栏会被 IME 遮住（本机 API36 adjustResize 未生效，
            //   窗口不收缩，insets 持续报告 → 14 键栏渲染在 Gboard 后方）。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(termColors["bg"]!!)
                    .imePadding(),
            ) {
                // ── 3. 快捷按键栏：Ctrl+C 中断 / Ctrl+L 清屏 · 右端环境配置 ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    QuickKey("Ctrl+C", "中断", termColors) {
                        PiTerminal.interrupt(context, session)
                    }
                    QuickKey("Ctrl+L", "清屏", termColors) {
                        session.lines.clear()
                    }
                    Spacer(Modifier.weight(1f))
                    QuickKey("环境配置", null, termColors, primary = true) {
                        nav.navigate("terminal_setup")
                    }
                }

                // ── 4. 输入行：`~ $` 提示符 + 命令输入 + 右端 ⌨ ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        "~ \$",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = MonoFont,
                            color = termColors["green"]!!,
                        ),
                    )
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = MonoFont,
                            color = termColors["white"]!!,
                            fontSize = 13.sp,
                        ),
                        cursorBrush = SolidColor(termColors["white"]!!),
                        singleLine = true,
                        // 终端输入必须关掉自动大写与纠错：Gboard 默认给首字母大写，
                        // 实测 `cd /workspace` 变成 `CD /workspace` → bash: CD: command not found
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Send,
                        ),
                        keyboardActions = KeyboardActions(onSend = { runCommand(input.text) }),
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    // ⌨：唤出/收起 14 键额外按键栏
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                if (showExtraKeys) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                                else Color.Transparent,
                                RoundedCornerShape(6.dp),
                            )
                            .clickable { showExtraKeys = !showExtraKeys },
                    ) {
                        Icon(
                            Icons.Outlined.Keyboard, "额外按键栏",
                            tint = if (showExtraKeys) MaterialTheme.colorScheme.primary
                            else termColors["black"]!!,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // ── 5. 14 键额外按键栏（Operit 默认 2×7 布局） ──
                if (showExtraKeys) {
                    ExtraKeysBar(
                        termColors = termColors,
                        ctrlActive = ctrlActive,
                        altActive = altActive,
                        onKey = { key ->
                            when (key) {
                                "ESC" -> input = TextFieldValue("")
                                "HOME" -> input = input.copy(selection = TextRange(0))
                                "END" -> input = input.copy(selection = TextRange(input.text.length))
                                "←" -> input = input.copy(selection = TextRange((input.selection.start - 1).coerceAtLeast(0)))
                                "→" -> {
                                    val p = (input.selection.start + 1).coerceAtMost(input.text.length)
                                    input = input.copy(selection = TextRange(p))
                                }
                                "↑" -> {
                                    if (history.isNotEmpty()) {
                                        val i = (if (historyIndex < 0) history.lastIndex else historyIndex - 1)
                                            .coerceIn(0, history.lastIndex)
                                        historyIndex = i
                                        input = TextFieldValue(history[i], TextRange(history[i].length))
                                    }
                                }
                                "↓" -> {
                                    if (historyIndex >= 0) {
                                        val i = historyIndex + 1
                                        if (i >= history.size) {
                                            historyIndex = -1
                                            input = TextFieldValue("")
                                        } else {
                                            historyIndex = i
                                            input = TextFieldValue(history[i], TextRange(history[i].length))
                                        }
                                    }
                                }
                                "PGUP" -> {
                                    val target = (outputScroll.value - outputScroll.viewportSize).coerceAtLeast(0)
                                    keyScope.launch { outputScroll.animateScrollTo(target) }
                                }
                                "PGDN" -> {
                                    val target = (outputScroll.value + outputScroll.viewportSize)
                                        .coerceAtMost(outputScroll.maxValue)
                                    keyScope.launch { outputScroll.animateScrollTo(target) }
                                }
                                else -> {
                                    // 文本键：插入到光标处（TAB 用四个空格，Operit 发送 \t）
                                    val v = if (key == "TAB") "    " else key
                                    val start = input.selection.start.coerceIn(0, input.text.length)
                                    input = TextFieldValue(
                                        input.text.substring(0, start) + v + input.text.substring(start),
                                        TextRange(start + v.length),
                                    )
                                }
                            }
                        },
                        onToggleCtrl = { ctrlActive = !ctrlActive },
                        onToggleAlt = { altActive = !altActive },
                    )
                }
            }
        }

        // ── 会话关闭二次确认（Operit onTabCloseRequest 同款弹窗） ──
        closeConfirmIndex?.let { i ->
            PiTerminal.sessions.getOrNull(i)?.let { target ->
                PientDialog(
                    title = "关闭终端会话",
                    onDismiss = { closeConfirmIndex = null },
                    confirmText = "删除",
                    onConfirm = {
                        PiTerminal.close(target)
                        if (chatState.terminalIndex >= PiTerminal.sessions.size) {
                            chatState.terminalIndex = 0
                        }
                        closeConfirmIndex = null
                    },
                    showClose = false,
                ) {
                    Text(
                        "确定要删除会话「${target.name}」吗？会话中的数据将丢失。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/** 快捷按键（Operit TerminalToolbar 同款：等宽键名 + 灰色中文标签） */
@Composable
private fun QuickKey(
    key: String,
    label: String?,
    colors: Map<String, Color>,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val textColor = if (primary) MaterialTheme.colorScheme.primary else colors["white"]!!
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                key,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont),
                color = textColor,
            )
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors["black"]!!,
                )
            }
        }
    }
}

/** 14 键额外按键栏（Operit VirtualKeyboard：2 行 × 7 列，CTRL/ALT 为切换键） */
@Composable
private fun ExtraKeysBar(
    termColors: Map<String, Color>,
    ctrlActive: Boolean,
    altActive: Boolean,
    onKey: (String) -> Unit,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
) {
    val rows = listOf(
        listOf("ESC", "/", "-", "HOME", "↑", "END", "PGUP"),
        listOf("TAB", "CTRL", "ALT", "←", "↓", "→", "PGDN"),
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(termColors["bg"]!!)
            .padding(horizontal = 10.dp)
            .padding(bottom = 8.dp),
    ) {
        rows.forEach { rowKeys ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowKeys.forEach { k ->
                    val active = (k == "CTRL" && ctrlActive) || (k == "ALT" && altActive)
                    val onClick = when (k) {
                        "CTRL" -> onToggleCtrl
                        "ALT" -> onToggleAlt
                        else -> ({ onKey(k) })
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(4.dp),
                            )
                            .clickable(onClick = onClick),
                    ) {
                        Text(
                            k,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont),
                            color = if (active) MaterialTheme.colorScheme.onPrimary else termColors["white"]!!,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalLineRow(line: TerminalLine, colors: Map<String, Color>) {
    val color = when (line.kind) {
        TerminalLineKind.COMMAND -> colors["green"]!!
        TerminalLineKind.PROMPT -> colors["green"]!!
        TerminalLineKind.BANNER -> colors["blue"]!!   // ASCII-Art Logo：品牌蓝
        TerminalLineKind.SLOGAN -> colors["cyan"]!!   // Slogan：青
        TerminalLineKind.OUTPUT -> colors["white"]!!
    }
    Text(
        line.text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = MonoFont,
            // BANNER/SLOGAN 用 11sp：56 字符 slogan 在手机屏宽下单行完整不折行
            fontSize = if (line.kind == TerminalLineKind.BANNER || line.kind == TerminalLineKind.SLOGAN) 11.sp else 12.sp,
            lineHeight = 16.sp,
            color = color,
        ),
        modifier = Modifier.padding(vertical = 1.dp),
    )
}
