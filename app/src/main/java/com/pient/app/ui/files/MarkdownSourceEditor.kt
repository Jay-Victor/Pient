package com.pient.app.ui.files

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.pient.app.data.ChatState
import com.pient.app.data.CodeLanguages
import com.pient.app.data.FileNode

/**
 * Markdown 源码编辑器（2026-09-11 新增）：正文编辑区 + 底部格式工具栏 + 搜索卡。
 *
 * 结构 = `Box { Column { 编辑区(weight 1f) + MarkdownEditorToolbar } + 搜索浮层 }`：
 * 工具栏只在源码模式出现（渲染模式走 MarkdownText），随系统键盘一起上移（IME 避让由工具栏承担）；
 * 搜索卡贴在标签栏下方（整宽贴顶），点卡片外关闭。
 *
 * 编辑语义参照 `Refences/Mdcito-1.2.0`（`ui/editor/EditorViewModel.kt`）：
 * - 格式动作：行内包裹（有选区包裹选区、无选区插入占位文字并选中）/ 行前缀（同类前缀再点即取消、
 *   异类前缀替换）/ 光标插入（分割线、代码块、链接、图片、表格）
 * - 撤销栈：快照式（Mdcito pushUndo 同思路），连续输入 1s 内合并为一步，工具栏动作每次一步，
 *   新改动清空重做栈，栈深 100
 * - 搜索：卡内查询/替换/三个开关/命中列表/跳转与替换（见 MarkdownSearchCard.kt）
 *
 * 已知边界：撤销栈、搜索卡状态为「本标签会话态」（切标签或切渲染模式即重置；编辑缓冲在 ChatState 里不丢）。
 */

/** 连续输入合并窗口（Mdcito 无合并——每次 updateContent 入栈；Pient 按输入节奏合并为一步撤销） */
private const val MdUndoTypingWindowMs = 1000L
private const val MdUndoDepth = 100

/** 占位文字（取 Mdcito strings.xml：文本 / 链接文本 / 代码） */
private const val MdPlaceholderText = "文本"
private const val MdPlaceholderLinkText = "链接文本"
private const val MdPlaceholderCode = "代码"
private const val MdPlaceholderImageAlt = "图片描述"
private const val MdPlaceholderImageUrl = "图片路径"
private const val MdPlaceholderUnused = "内容"
private const val MdDefaultLinkUrl = "https://example.com"

@Composable
internal fun MarkdownSourceEditor(chatState: ChatState, node: FileNode, text: String) {
    val key = chatState.fileKey(node)
    val context = LocalContext.current

    // ── 编辑缓冲：TextFieldValue（选区是格式插入/搜索跳转的前提）──
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

    // ── 撤销 / 重做栈（快照式）──
    val undoStack = remember(key) { mutableStateListOf<String>() }
    val redoStack = remember(key) { mutableStateListOf<String>() }
    var lastEditAt by remember(key) { mutableLongStateOf(0L) }

    fun pushSnapshot(prev: String) {
        if (undoStack.lastOrNull() != prev) {
            if (undoStack.size >= MdUndoDepth) undoStack.removeAt(0)
            undoStack.add(prev)
        }
        redoStack.clear()
    }

    fun setText(next: String) {
        value = TextFieldValue(
            text = next,
            selection = TextRange(value.selection.start.coerceIn(0, next.length)),
        )
        chatState.editDraft(node, next)
    }

    /** 键盘输入：1s 内的连续输入合并为一步撤销（Mdcito 每击键入栈，此处按输入节奏合并） */
    fun onTyping(new: TextFieldValue) {
        val now = System.currentTimeMillis()
        if (now - lastEditAt > MdUndoTypingWindowMs) pushSnapshot(value.text)
        lastEditAt = now
        value = new
        chatState.editDraft(node, new.text)
    }

    /** 工具栏 / 替换等结构动作：先入栈（保证每次动作都可单独撤销） */
    fun onAction(new: TextFieldValue) {
        if (new.text == value.text) return
        pushSnapshot(value.text)
        lastEditAt = 0L
        value = new
        chatState.editDraft(node, new.text)
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.add(value.text)
        lastEditAt = 0L
        value = TextFieldValue(prev, TextRange(value.selection.start.coerceIn(0, prev.length)))
        chatState.editDraft(node, prev)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(value.text)
        lastEditAt = 0L
        value = TextFieldValue(next, TextRange(value.selection.start.coerceIn(0, next.length)))
        chatState.editDraft(node, next)
    }

    // ── 搜索状态（卡在标签栏下方展开；本标签会话态）──
    var searchOpen by remember(key) { mutableStateOf(false) }
    var options by remember(key) { mutableStateOf(MdSearchOptions()) }
    var currentMatchIndex by remember(key) { mutableIntStateOf(-1) }
    var selectedMatches by remember(key) { mutableStateOf(emptySet<Int>()) }
    var scrollRequest by remember(key) { mutableStateOf<Int?>(null) }

    val matches = remember(value.text, options) { findMdMatches(value.text, options) }
    val matchedLines = remember(value.text, matches) { buildMdMatchedLines(value.text, matches) }
    // 命中集变化 → 校正当前项与勾选集（删除文字/改查询后不残留越界索引）
    LaunchedEffect(matches) {
        if (currentMatchIndex !in matches.indices) currentMatchIndex = matches.indices.firstOrNull() ?: -1
        val pruned = selectedMatches.filter { it in matches.indices }.toSet()
        if (pruned != selectedMatches) selectedMatches = pruned
    }

    fun jumpTo(matchIndex: Int) {
        val range = matches.getOrNull(matchIndex) ?: return
        currentMatchIndex = matchIndex
        value = value.copy(selection = TextRange(range.first, range.last + 1))
        scrollRequest = range.first
    }

    /** 替换指定命中（调用方保证 indices 非空；「全部」= 先全选、再点「替换」，不再有「空 = 全部」路径） */
    fun replaceMatches(indices: Collection<Int>) {
        val targets = indices.sorted()
        if (targets.isEmpty() || matches.isEmpty()) return
        val targetSet = targets.toSet()
        val sb = StringBuilder()
        var pos = 0
        matches.forEachIndexed { i, r ->
            if (i in targetSet) {
                if (r.first > pos) sb.append(value.text, pos, r.first)
                sb.append(options.replacement)
                pos = (r.last + 1).coerceAtMost(value.text.length)
            }
        }
        if (pos < value.text.length) sb.append(value.text, pos, value.text.length)
        if (sb.toString() == value.text) return
        onAction(TextFieldValue(sb.toString(), TextRange(pos.coerceAtMost(sb.length))))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                EditableTextView(
                    value = value,
                    onValueChange = { onTyping(it) },
                    showLineNumbers = true,
                    codeLanguage = CodeLanguages.forExtension(node.ext) ?: CodeLanguages.generic,
                    contentKey = key,
                    searchMatches = if (searchOpen) matches else emptyList(),
                    currentMatchIndex = currentMatchIndex,
                    scrollRequest = scrollRequest,
                    onScrollRequestConsumed = { scrollRequest = null },
                    // IME 抬升交给底部工具栏（本页工具栏随键盘上移），编辑器不再自带 IME 留白
                    applyImePadding = false,
                )
                // 搜索卡展开时：编辑区内点外关闭（透明层只铺编辑区——铺满整页会把底部工具栏也吃掉，
                // 变成「点撤销只会关卡片」；工具栏此时仍可正常点）
                if (searchOpen) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) { detectTapGestures { searchOpen = false } },
                    )
                }
            }
            MarkdownEditorToolbar(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                onUndo = { undo() },
                onRedo = { redo() },
                onFormat = { format -> onAction(applyMdFormat(value, format)) },
                onSearch = { searchOpen = !searchOpen },
            )
        }

        if (searchOpen) {
            MarkdownSearchCard(
                options = options,
                onOptionsChange = { options = it },
                matchedLines = matchedLines,
                matchCount = matches.size,
                selectedIndices = selectedMatches,
                onJumpToMatch = { jumpTo(it) },
                // 勾/取消一行 = 该行的全部命中一起选（一行可含多处命中，勾上就一起改）
                onToggleRowSelection = { indices ->
                    val group = indices.toSet()
                    selectedMatches = if (selectedMatches.containsAll(group)) selectedMatches - group
                    else selectedMatches + group
                },
                // 「替换」= 只改勾选中的结果；一项都没勾选 → 不改正文，Toast 说明（浮层内反馈必须可见）
                onReplace = {
                    val checked = selectedMatches.toList()
                    if (checked.isEmpty()) {
                        Toast.makeText(context, "请先勾选要替换的结果", Toast.LENGTH_SHORT).show()
                    } else {
                        replaceMatches(checked)
                        selectedMatches = emptySet()
                    }
                },
                // 「全部」= 全选（把结果行全部勾上，不是全部替换）；已全选时再点 = 取消全选
                onSelectAll = {
                    val all = matchedLines.flatMap { it.matchIndices }.toSet()
                    selectedMatches = if (all.isNotEmpty() && selectedMatches.containsAll(all)) emptySet() else all
                },
                // 位置：文件预览页标签栏正下方、整宽贴顶（Mdcito EditorSearchModal 顶部落下同款）
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

// ───────────────────────────── 格式插入（Mdcito EditorViewModel 同款语义） ─────────────────────────────

internal fun applyMdFormat(value: TextFieldValue, format: MdFormat): TextFieldValue = when (format) {
    is MdFormat.Heading -> mdLinePrefix(value, "#".repeat(format.level) + " ")
    MdFormat.Italic -> mdInlineFormat(value, "*", "*", MdPlaceholderText)
    MdFormat.Bold -> mdInlineFormat(value, "**", "**", MdPlaceholderText)
    MdFormat.BoldItalic -> mdInlineFormat(value, "***", "***", MdPlaceholderText)
    MdFormat.Strike -> mdInlineFormat(value, "~~", "~~", MdPlaceholderText)
    MdFormat.InlineCode -> mdInlineFormat(value, "`", "`", MdPlaceholderCode)
    MdFormat.Rule -> mdInsertAtCursor(value, "\n---\n")
    MdFormat.UnorderedList -> mdLinePrefix(value, "- ")
    MdFormat.OrderedList -> mdLinePrefix(value, "1. ")
    MdFormat.TaskList -> mdLinePrefix(value, "- [ ] ")
    MdFormat.Quote -> mdLinePrefix(value, "> ")
    MdFormat.CodeBlock -> mdCodeBlock(value)
    MdFormat.Link -> mdLink(value)
    MdFormat.Image -> mdImage(value)
    MdFormat.Table -> mdTable(value)
}

/** 行内包裹：有选区 → 包裹选区、光标落在包裹之后；无选区 → 插入占位文字并选中（Mdcito insertFormatting） */
private fun mdInlineFormat(value: TextFieldValue, prefix: String, suffix: String, placeholder: String): TextFieldValue {
    val text = value.text
    val start = value.selection.start.coerceIn(0, text.length)
    val end = value.selection.end.coerceIn(0, text.length)
    return if (start != end) {
        val selected = text.substring(start, end)
        TextFieldValue(
            text = text.substring(0, start) + prefix + selected + suffix + text.substring(end),
            selection = TextRange(start + prefix.length + selected.length),
        )
    } else {
        TextFieldValue(
            text = text.substring(0, start) + prefix + placeholder + suffix + text.substring(start),
            selection = TextRange(start + prefix.length, start + prefix.length + placeholder.length),
        )
    }
}

/** 前缀识别（Mdcito insertLinePrefix 同款判定顺序：任务 → 有序 → 无序 → 标题 → 引用） */
private val MdTaskPrefix = Regex("""^-\s\[[ x]\]\s""")
private val MdOrderedPrefix = Regex("""^\d+\.\s""")
private val MdListPrefix = Regex("""^[-*+]\s""")
private val MdHeadingPrefix = Regex("""^#{1,6}\s""")
private val MdQuotePrefix = Regex("""^>\s""")

/**
 * 行前缀（标题 / 列表 / 引用）：当前行已有同类前缀 → 再点即取消；已有异类前缀 → 替换；
 * 无前缀 → 在行首插入（Mdcito insertLinePrefix）。
 */
private fun mdLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
    val text = value.text
    val cursor = value.selection.start.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', cursor - 1).let { if (it == -1) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
    val line = text.substring(lineStart, lineEnd)

    val existing = when {
        MdTaskPrefix.containsMatchIn(line) -> MdTaskPrefix.find(line)?.value
        MdOrderedPrefix.containsMatchIn(line) -> MdOrderedPrefix.find(line)?.value
        MdListPrefix.containsMatchIn(line) -> MdListPrefix.find(line)?.value
        MdHeadingPrefix.containsMatchIn(line) -> MdHeadingPrefix.find(line)?.value
        MdQuotePrefix.containsMatchIn(line) -> MdQuotePrefix.find(line)?.value
        else -> null
    }

    val newText: String
    val newCursor: Int
    if (existing != null) {
        val rest = line.removePrefix(existing)
        if (existing == prefix) {
            newText = text.substring(0, lineStart) + rest + text.substring(lineEnd)
            newCursor = (cursor - prefix.length).coerceAtLeast(lineStart)
        } else {
            newText = text.substring(0, lineStart) + prefix + rest + text.substring(lineEnd)
            newCursor = cursor - existing.length + prefix.length
        }
    } else {
        newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
        newCursor = cursor + prefix.length
    }
    return TextFieldValue(newText, TextRange(newCursor.coerceIn(0, newText.length)))
}

/** 光标插入（分割线等）：Mdcito insertAtCursor 同款换行守卫，避免与相邻行粘连 */
private fun mdInsertAtCursor(value: TextFieldValue, insert: String): TextFieldValue {
    val text = value.text
    val start = value.selection.start.coerceIn(0, text.length)
    val end = value.selection.end.coerceIn(0, text.length)
    var piece = insert
    if (!piece.startsWith("\n") && start > 0 && text.getOrNull(start - 1) != '\n') piece = "\n$piece"
    if (!piece.endsWith("\n") && start < text.length && text.getOrNull(start) != '\n') piece = "$piece\n"
    val newText = text.substring(0, start) + piece + text.substring(end.coerceAtLeast(start))
    return TextFieldValue(newText, TextRange(start + piece.length))
}

/** 代码块（Mdcito insertCodeBlock 同款：有选区围栏包裹；无选区插占位并选中「代码」二字） */
private fun mdCodeBlock(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val start = value.selection.start.coerceIn(0, text.length)
    val end = value.selection.end.coerceIn(0, text.length)
    return if (start != end) {
        val selected = text.substring(start, end)
        val lead = if (start > 0 && text.getOrNull(start - 1) != '\n') "\n" else ""
        val trail = if (end < text.length && text.getOrNull(end) != '\n') "\n" else ""
        val block = "$lead```\n$selected\n```$trail"
        TextFieldValue(
            text = text.substring(0, start) + block + text.substring(end),
            selection = TextRange(start + block.length),
        )
    } else {
        val lead = if (start > 0 && text.getOrNull(start - 1) != '\n') "\n" else ""
        val trail = if (start < text.length && text.getOrNull(start) != '\n') "\n" else ""
        val block = "$lead```\n$MdPlaceholderCode\n```$trail"
        TextFieldValue(
            text = text.substring(0, start) + block + text.substring(start),
            selection = TextRange(
                start + lead.length + 4,
                start + lead.length + 4 + MdPlaceholderCode.length,
            ),
        )
    }
}

/** 链接（Mdcito insertLink：有选区 → [选区](默认地址) 并选中地址；无选区 → [链接文本](默认地址)） */
private fun mdLink(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val start = value.selection.start.coerceIn(0, text.length)
    val end = value.selection.end.coerceIn(0, text.length)
    return if (start != end) {
        val selected = text.substring(start, end)
        val link = "[$selected]($MdDefaultLinkUrl)"
        TextFieldValue(
            text = text.substring(0, start) + link + text.substring(end),
            selection = TextRange(start + selected.length + 3, start + selected.length + 3 + MdDefaultLinkUrl.length),
        )
    } else {
        val link = "[$MdPlaceholderLinkText]($MdDefaultLinkUrl)"
        TextFieldValue(
            text = text.substring(0, start) + link + text.substring(start),
            selection = TextRange(start + 1, start + 1 + MdPlaceholderLinkText.length),
        )
    }
}

/** 图片：`![描述](路径)`（Pient 暂不做图片选择器，插入后选中「图片路径」让用户直接改） */
private fun mdImage(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val start = value.selection.start.coerceIn(0, text.length)
    val end = value.selection.end.coerceIn(0, text.length)
    val snippet = "![$MdPlaceholderImageAlt]($MdPlaceholderImageUrl)"
    val urlStart = start + 2 + MdPlaceholderImageAlt.length + 2
    return TextFieldValue(
        text = text.substring(0, start) + snippet + text.substring(end),
        selection = TextRange(urlStart, urlStart + MdPlaceholderImageUrl.length),
    )
}

/** 表格：3 列 × 1 数据行（Mdcito insertTable 同构，列名/内容取占位文字） */
private fun mdTable(value: TextFieldValue): TextFieldValue {
    val text = value.text
    val start = value.selection.start.coerceIn(0, text.length)
    val end = value.selection.end.coerceIn(0, text.length)
    val cols = 3
    val header = "| " + (1..cols).joinToString(" | ") { "列 $it" } + " |"
    val separator = "| " + (1..cols).joinToString(" | ") { "-----" } + " |"
    val row = "| " + (1..cols).joinToString(" | ") { MdPlaceholderUnused } + " |"
    val prefix = if (start > 0 && text.getOrNull(start - 1) != '\n') "\n" else ""
    val table = "$prefix$header\n$separator\n$row\n"
    return TextFieldValue(
        text = text.substring(0, start) + table + text.substring(end),
        selection = TextRange(start + table.length),
    )
}
