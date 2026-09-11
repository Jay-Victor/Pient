package com.pient.app.ui.files

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.pient.app.data.ChatState
import com.pient.app.data.CodeLanguage
import com.pient.app.data.FileNode

/**
 * 代码文件编辑器（2026-09-11 新增）：正文（行号 + 语法着色 + 缩进标记）+ 底部符号工具栏。
 *
 * 结构 = `Column { 编辑区(weight 1f) + CodeSymbolToolbar }`，与 `MarkdownSourceEditor` 同构：
 * 工具栏随系统键盘一起上移（IME 避让由工具栏承担，编辑区传 `applyImePadding = false`）。
 *
 * 缓冲/保存链路与其它文件一致（`ChatState.editDraft` → 标签栏未保存圆点 → 保存键写回磁盘）。
 * 与 markdown 源码编辑器的差异：本栏只做符号插入，不做格式动作，也没有撤销栈
 * （markdown 那套 undo/redo 属其编辑器专用）。
 */
@Composable
internal fun CodeSourceEditor(
    chatState: ChatState,
    node: FileNode,
    text: String,
    codeLanguage: CodeLanguage,
) {
    val key = chatState.fileKey(node)

    // ── 编辑缓冲：TextFieldValue（选区是符号配对插入的前提）──
    var value by remember(key) { mutableStateOf(TextFieldValue(text)) }
    // 外部文本变化（首次读取播种、保存后重读）→ 同步文本，选区按新长度夹取
    LaunchedEffect(text) {
        if (value.text != text) {
            value = TextFieldValue(
                text = text,
                selection = TextRange(
                    value.selection.start.coerceIn(0, text.length),
                    value.selection.end.coerceIn(0, text.length),
                ),
            )
        }
    }

    // 底部符号工具栏可见性汇报 → FilesPanel 据此把右下 FAB 抬到工具栏之上。
    // 用「进入置真 / 离开复位」而不是静态类型推测：二进制、超限文件走提示分支不显示工具栏，
    // 只有本编辑器知道；切到其它文件或其它预览分支时本组件离开组合，标志自动回落。
    SideEffect { chatState.symbolToolbarVisible = true }
    DisposableEffect(Unit) { onDispose { chatState.symbolToolbarVisible = false } }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            EditableTextView(
                value = value,
                onValueChange = {
                    value = it
                    chatState.editDraft(node, it.text)
                },
                showLineNumbers = true,
                codeLanguage = codeLanguage,
                contentKey = key,
                applyImePadding = false,
            )
        }
        CodeSymbolToolbar(
            onSymbol = { symbol ->
                val next = insertCodeSymbol(value, symbol)
                if (next.text != value.text) {
                    value = next
                    chatState.editDraft(node, next.text)
                }
            },
        )
    }
}
