package com.pient.app.ui.files

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 代码文件底部符号工具栏（2026-09-11 新增）。
 *
 * 触发范围（`FileContentView` 分流）：预览区是**代码 / 标记语言源文件**时显示——即除
 * 图片 / 视频 / 音频 / 文档（doc·docx·xls·xlsx·pdf）/ 二进制（无法按 UTF-8 解码或 >2MB）
 * 与 txt（纯文本，无行号无着色）之外的可预览文本：kt / kts / py / java / js / ts / json /
 * xml / yaml / css / sh / html（源码模式）…（扩展名表见 `data/CodeSyntax.kt`，通用回退 =
 * 未收录的其它文本）。
 * markdown 有专用的格式工具栏（[MarkdownEditorToolbar]），txt 两类工具栏都不显示。
 *
 * 外壳与按键尺寸与 markdown 工具栏**共用一份实现**（[EditorToolbarContainer] /
 * [EditorToolbarButton] / [EditorToolbarDivider]，见 `EditorToolbarCommon.kt`），
 * 按键为等宽字体的文字键（代码符号看等宽字形更直观）。
 *
 * 插入语义（代码编辑器惯例，同 VSCode / Acode 的自动配对）：
 * - 成对符号（`(` `[` `{` `"` `'` `` ` ``）：无选区 → 插入一对、光标落在中间；有选区 → 包裹选区
 * - 其余符号：插入到光标处（有选区 → 替换选区）
 *
 * 键距（2026-09-11 用户口径，两轮收紧）：**同种符号之间更紧、其余间距不变**——
 * 成对键（同一 [CodeSymbol.kind]，如 `()` `[]` `{}` `<>`）键宽收窄 14dp + 字形向心内移
 * [EditorToolbarPairShift]（3.5dp）：「同种」字形间距 32dp → 11dp；「异种」与分组竖线两侧
 * 的距离经守恒计算保持原样（推导见 [EditorToolbarButton]）。
 */

/**
 * 符号键：[text] = 插入文本，[close] = 成对符号的闭合符（null = 非成对），[desc] = 无障碍名/点击语义，
 * [kind] = 「同种符号」标识（同类符号相邻排布时工具栏会把后者横向收窄，视觉上成对贴合：`()` `[]` `{}` `<>`）。
 */
internal data class CodeSymbol(
    val text: String,
    val close: String? = null,
    val desc: String,
    val kind: String? = null,
)

/**
 * 符号分组（分组之间有竖线分隔；组内顺序 = 屏上顺序）。
 * 「同种符号」= 同一 [CodeSymbol.kind] 的相邻键（括号的开/闭为一对），组内成对贴合、其余键距不变。
 */
internal val CodeSymbolGroups: List<List<CodeSymbol>> = listOf(
    // 括号族（尖括号在代码里同属括号语义：泛型 / HTML 标签 / 比较）
    listOf(
        CodeSymbol("(", ")", "左圆括号", kind = "round"),
        CodeSymbol(")", null, "右圆括号", kind = "round"),
        CodeSymbol("[", "]", "左方括号", kind = "square"),
        CodeSymbol("]", null, "右方括号", kind = "square"),
        CodeSymbol("{", "}", "左花括号", kind = "curly"),
        CodeSymbol("}", null, "右花括号", kind = "curly"),
        CodeSymbol("<", null, "小于号", kind = "angle"),
        CodeSymbol(">", null, "大于号", kind = "angle"),
    ),
    // 引号族（成对插入）
    listOf(
        CodeSymbol("\"", "\"", "双引号"),
        CodeSymbol("'", "'", "单引号"),
        CodeSymbol("`", "`", "反引号"),
    ),
    // 运算符
    listOf(
        CodeSymbol("+", null, "加号"),
        CodeSymbol("-", null, "减号"),
        CodeSymbol("*", null, "乘号"),
        CodeSymbol("/", null, "除号"),
        CodeSymbol("%", null, "百分号"),
        CodeSymbol("=", null, "等号"),
    ),
    // 常见组合符（多字符，手机键盘上最难打的一类）
    listOf(
        CodeSymbol("==", null, "等于"),
        CodeSymbol("!=", null, "不等于"),
        CodeSymbol("<=", null, "小于等于"),
        CodeSymbol(">=", null, "大于等于"),
        CodeSymbol("&&", null, "逻辑与"),
        CodeSymbol("||", null, "逻辑或"),
        CodeSymbol("->", null, "箭头"),
        CodeSymbol("=>", null, "粗箭头"),
    ),
    // 逻辑 / 位运算
    listOf(
        CodeSymbol("!", null, "非"),
        CodeSymbol("&", null, "与"),
        CodeSymbol("|", null, "或"),
        CodeSymbol("~", null, "取反"),
    ),
    // 分隔符
    listOf(
        CodeSymbol(",", null, "逗号"),
        CodeSymbol(";", null, "分号"),
        CodeSymbol(":", null, "冒号"),
        CodeSymbol(".", null, "点号"),
        CodeSymbol("_", null, "下划线"),
    ),
    // 其它常用符号
    listOf(
        CodeSymbol("#", null, "井号"),
        CodeSymbol("@", null, "at 号"),
        CodeSymbol("$", null, "美元号"),
        CodeSymbol("^", null, "脱字符"),
        CodeSymbol("?", null, "问号"),
        CodeSymbol("\\", null, "反斜杠"),
    ),
)

/**
 * 边界补偿间隔（见 [CodeSymbolToolbar]）。
 *
 * 设标准键宽 B、成对键宽 W、向心位移 δ：字形到盒边的可达距离 = W/2 ∓ δ
 * （串首右移偏右、串尾左移偏左，普通键两侧都是 B/2）。
 * 于是「标准留白 − 实际可达」= 需要补回的间隔：
 * - 同一对之内（串首 +δ 紧接串尾 −δ）：**不补**——那正是要收紧的地方（字形间距 W − 2δ）
 * - 成对键 ↔ 成对键（串尾 −δ 紧接下一串首 +δ）：补 2δ（字形间距回到 B）
 * - 成对键 ↔ 普通键 / 分组竖线：补 δ
 *
 * 数据保证「串首后面一定是它的串尾」，故不会出现 串首↔普通键 这种半截组合；
 * 工具栏最左是撤销 / 取消撤销两枚普通键（照常占标准键宽），不需要额外补偿。
 */
private fun boundaryGap(prevNudge: Dp, nudge: Dp, shift: Dp): Dp = when {
    prevNudge > 0.dp && nudge < 0.dp -> 0.dp
    prevNudge < 0.dp && nudge > 0.dp -> shift * 2
    prevNudge != 0.dp || nudge != 0.dp -> shift
    else -> 0.dp
}

@Composable
internal fun CodeSymbolToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSymbol: (CodeSymbol) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pairShift = EditorToolbarPairShift
    EditorToolbarContainer(modifier) {
        // 符号键横排、可横向滚动（与 markdown 工具栏同一滚动机制）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        ) {
            // 撤销 / 取消撤销：与 markdown 格式工具栏同款按键、同语义
            // （共用 EditorUndoController：粒度按编辑动作、撤销与重做都恢复光标位置）
            EditorToolbarButton(
                icon = Icons.AutoMirrored.Outlined.Undo,
                desc = "撤销",
                enabled = canUndo,
                onClick = onUndo,
            )
            EditorToolbarButton(
                icon = Icons.AutoMirrored.Outlined.Redo,
                desc = "取消撤销",
                enabled = canRedo,
                onClick = onRedo,
            )
            EditorToolbarDivider()

            // 前一项的字形位移（0.dp = 普通键 / 分组竖线）
            var prevNudge: Dp = 0.dp
            CodeSymbolGroups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) {
                    val gap = boundaryGap(prevNudge, 0.dp, pairShift)
                    if (gap > 0.dp) Spacer(Modifier.width(gap))
                    EditorToolbarDivider()
                    prevNudge = 0.dp
                }
                group.forEachIndexed { index, symbol ->
                    // 同种符号（与相邻键同 kind，如括号的开/闭）→ 键宽收窄，成对贴合
                    val sameKindAsPrevious = index > 0 &&
                        symbol.kind != null && symbol.kind == group[index - 1].kind
                    val sameKindAsNext = index < group.lastIndex &&
                        symbol.kind != null && symbol.kind == group[index + 1].kind
                    val inSameKindRun = sameKindAsPrevious || sameKindAsNext
                    // 同种符号串内做向心位移：串首右移、串尾左移（串中的键不动），
                    // 配合键宽收窄让成对符号贴紧；异种符号之间距离用 boundaryGap 补回（守恒）
                    val glyphShift = when {
                        inSameKindRun && !sameKindAsPrevious -> pairShift
                        inSameKindRun && !sameKindAsNext -> -pairShift
                        else -> 0.dp
                    }
                    val gap = boundaryGap(prevNudge, glyphShift, pairShift)
                    if (gap > 0.dp) Spacer(Modifier.width(gap))
                    EditorToolbarButton(
                        text = symbol.text,
                        desc = symbol.desc,
                        monospace = true,
                        compact = inSameKindRun,
                        glyphShift = glyphShift,
                        onClick = { onSymbol(symbol) },
                    )
                    prevNudge = glyphShift
                }
            }
        }
    }
}

/**
 * 插入符号（成对符号自动配对）：
 * - 成对 + 有选区 → 包裹选区，光标落包裹之后
 * - 成对 + 无选区 → 插入一对，光标在中间
 * - 非成对 → 插到光标处（有选区 → 替换选区），光标落符号之后
 */
internal fun insertCodeSymbol(value: TextFieldValue, symbol: CodeSymbol): TextFieldValue {
    val text = value.text
    val start = value.selection.start.coerceIn(0, text.length)
    val end = value.selection.end.coerceIn(0, text.length)
    val close = symbol.close
    return when {
        start != end && close != null -> {
            val selected = text.substring(start, end)
            TextFieldValue(
                text = text.substring(0, start) + symbol.text + selected + close + text.substring(end),
                selection = TextRange(start + symbol.text.length + selected.length + close.length),
            )
        }

        start != end -> TextFieldValue(
            text = text.substring(0, start) + symbol.text + text.substring(end),
            selection = TextRange(start + symbol.text.length),
        )

        close != null -> TextFieldValue(
            text = text.substring(0, start) + symbol.text + close + text.substring(start),
            selection = TextRange(start + symbol.text.length),
        )

        else -> TextFieldValue(
            text = text.substring(0, start) + symbol.text + text.substring(start),
            selection = TextRange(start + symbol.text.length),
        )
    }
}
