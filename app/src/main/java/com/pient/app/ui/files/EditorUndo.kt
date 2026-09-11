package com.pient.app.ui.files

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 编辑器撤销 / 重做栈（markdown 源码编辑器与代码编辑器**共用一份实现**，2026-09-11 抽出）。
 *
 * 粒度 = **编辑动作**，不看时间（用户口径：「根据用户一次在文件中输入的内容、每次删除一个字符、
 * 选中剪切某一部分，而不是根据用户在某一时间段内操作的内容」）：
 * - 连续输入（同一处逐字接续的整段）合并为一步
 * - IME 组合态（拼音改写 / 挑候选字）并入当前段
 * - 每次删除、选区剪切、粘贴（一次插多字）、替换、工具栏动作、搜索替换各算一步
 * - 光标移动（只改选区）不入栈，但作为动作边界
 *
 * 每步快照带光标 / 选区，**撤销与重做都整步恢复（重做后光标回到撤销前的位置）**。
 * 快照式栈：新改动清空重做栈，栈深 [EditorUndoDepth]。
 *
 * 用法（两个编辑器一致）：
 * ```
 * val history = remember(key) { EditorUndoController() }
 * onValueChange = { history.onTextChange(value, it); value = it; ... }   // 键盘输入
 * onAction = { new -> history.push(value); value = new; ... }            // 工具栏 / 替换等结构动作
 * onUndo = { history.undo(value)?.let { value = it; ... } }
 * ```
 */
@Stable
internal class EditorUndoController {

    private val undoStack = mutableStateListOf<EditorSnapshot>()
    private val redoStack = mutableStateListOf<EditorSnapshot>()

    // 当前「连续输入段」：仍在进行 + 该段文本末尾偏移。只有逐字接续的输入才并入同一段，
    // 删除 / 剪切 / 粘贴 / 结构动作 / 光标移动都会结束它
    private var runActive by mutableStateOf(false)
    private var runEnd by mutableIntStateOf(0)

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * 结构动作（工具栏格式插入 / 搜索替换 / 符号键插入）前调用：
     * 把当前状态入栈，保证每个动作都能单独撤销。
     */
    fun push(current: TextFieldValue) {
        if (undoStack.lastOrNull()?.text != current.text) {
            if (undoStack.size >= EditorUndoDepth) undoStack.removeAt(0)
            undoStack.add(EditorSnapshot(current.text, current.selection))
        }
        redoStack.clear()
    }

    /**
     * 键盘输入（每次 onValueChange）时调用，按编辑动作决定这次改动是否并入上一步：
     * 逐字接续的输入 / IME 组合态 → 并入；删除、粘贴、替换 → 各自新开一步；只改选区 → 边界。
     */
    fun onTextChange(old: TextFieldValue, new: TextFieldValue) {
        val edit = textEdit(old.text, new.text)
        if (edit == null) {
            runActive = false               // 仅选区变化（光标移动）= 动作边界，不入栈
            return
        }
        val composing = new.composition != null || old.composition != null
        val pureInsert = edit.removed == 0 && edit.inserted > 0
        // 同一段的继续：纯插入且紧接上次输入末尾逐字打字，或正处于 IME 组合态
        val continuesRun = runActive && pureInsert &&
            ((edit.inserted == 1 && edit.start == runEnd) || composing)
        if (!continuesRun) push(old)
        runActive = pureInsert && (edit.inserted == 1 || composing)
        runEnd = edit.start + edit.inserted
    }

    /** 撤销：返回要恢复的状态（文本 + 光标），当前状态连同光标压入重做栈；栈空返回 null */
    fun undo(current: TextFieldValue): TextFieldValue? {
        val prev = undoStack.removeLastOrNull() ?: return null
        redoStack.add(EditorSnapshot(current.text, current.selection))
        runActive = false
        return TextFieldValue(prev.text, clampTextSelection(prev.selection, prev.text.length))
    }

    /** 重做：返回被撤销掉的状态（文本 + 光标）——光标回到撤销前的位置；栈空返回 null */
    fun redo(current: TextFieldValue): TextFieldValue? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.add(EditorSnapshot(current.text, current.selection))
        runActive = false
        return TextFieldValue(next.text, clampTextSelection(next.selection, next.text.length))
    }
}

/** 撤销栈深度（快照式；条数上限，超出丢最旧） */
internal const val EditorUndoDepth = 100

/** 撤销 / 重做栈里的一步：文本 + 当时的光标 / 选区 */
internal data class EditorSnapshot(val text: String, val selection: TextRange)

/** 一次文本编辑的差异（相对旧文本：从 [start] 起删掉 [removed] 个字符、插入 [inserted] 个字符） */
internal data class EditorEdit(val start: Int, val removed: Int, val inserted: Int)

/** 选区按新文本长度夹取（撤销目标文本可能比当前短） */
internal fun clampTextSelection(range: TextRange, length: Int): TextRange = TextRange(
    range.start.coerceIn(0, length),
    range.end.coerceIn(0, length),
)

/**
 * 求前后文本的单段差异（公共前缀 + 公共后缀之间的那段）。
 * IME 组合态下每次按键只是改写同一段（拼音 → 候选字），单段假设成立；
 * 个别多段改写会被合成一段处理——对撤销粒度无影响。
 */
internal fun textEdit(old: String, new: String): EditorEdit? {
    if (old == new) return null
    val maxCommon = minOf(old.length, new.length)
    var prefix = 0
    while (prefix < maxCommon && old[prefix] == new[prefix]) prefix++
    var suffix = 0
    while (suffix < maxCommon - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
    return EditorEdit(prefix, old.length - prefix - suffix, new.length - prefix - suffix)
}
