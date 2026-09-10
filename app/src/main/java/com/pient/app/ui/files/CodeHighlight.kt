package com.pient.app.ui.files

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pient.app.data.CodeLanguages
import com.pient.app.data.CodeLanguage
import com.pient.app.data.CodeToken
import com.pient.app.data.scanCode

/**
 * 代码着色 + 缩进标记（移植 Operit 工作区编辑器）
 * - 调色板 = Operit `theme/EditorTheme.kt` 的 LightTheme / DarkTheme
 * - 着色区间 = `data/CodeSyntax.kt scanCode`（同 Operit EditorSyntaxHighlighter）
 * - 缩进标记 = Operit `CanvasCodeEditorView.drawIndentGuides`：每 4 空格一个标记，
 *   x = 文本左缘 + 级别×4×字宽 − 0.5×字宽，纵向自 lineTop+2dp 到 lineBottom−2dp，1dp 描边，
 *   颜色 = blend(背景, 槽边框, 0.68)（Pient 槽无边框 → 取主题 outlineVariant）
 */

/** 语法元素调色板（值取自 Operit EditorTheme；textColor 由主题 onBackground 提供，不在此列） */
data class CodePalette(
    val keyword: Color,
    val type: Color,
    val function: Color,
    val variable: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    /** Operit gutterBorderColor（缩进标记混色用） */
    val gutterBorder: Color,
    /** Markdown 标题（Pient 增补：取品牌 warn 色，Operit 无 markdown 语言） */
    val heading: Color,
    /** Markdown 链接地址（Pient 增补：取对话分类青，与正文/标记色区分） */
    val link: Color,
) {
    fun colorOf(token: CodeToken): Color = when (token) {
        CodeToken.KEYWORD -> keyword
        CodeToken.TYPE -> type
        CodeToken.FUNCTION -> function
        CodeToken.VARIABLE -> variable
        CodeToken.STRING -> string
        CodeToken.NUMBER -> number
        CodeToken.COMMENT -> comment
        CodeToken.HEADING -> heading
        CodeToken.LINK -> link
    }
}

/** Operit `EditorTheme.LightTheme`（VS Code Light+ 色系） */
val CodePaletteLight = CodePalette(
    keyword = Color(0xFF0000FF),
    type = Color(0xFF267F99),
    function = Color(0xFF795E26),
    variable = Color(0xFF001080),
    string = Color(0xFFA31515),
    number = Color(0xFF098658),
    comment = Color(0xFF008000),
    gutterBorder = Color(0xFFE5E5E5),
    heading = Color(0xFF9A6700),   // LightWarn
    link = Color(0xFF1B7C83),      // LightCategoryConversation
)

/** Operit `EditorTheme.DarkTheme`（VS Code Dark+ 色系） */
val CodePaletteDark = CodePalette(
    keyword = Color(0xFF6CB6FF),
    type = Color(0xFF4EC9B0),
    function = Color(0xFFDCDCAA),
    variable = Color(0xFF9CDCFE),
    string = Color(0xFFCE9178),
    number = Color(0xFFB5CEA8),
    comment = Color(0xFF6A9955),
    gutterBorder = Color(0xFF3A3D41),
    heading = Color(0xFFD29922),   // DarkWarn
    link = Color(0xFF39C5CF),      // DarkCategoryConversation
)

fun codePalette(isDark: Boolean): CodePalette = if (isDark) CodePaletteDark else CodePaletteLight

/**
 * 语法着色 VisualTransformation（编辑态可用：只改外观、offset 不变）。
 * 扫描是 O(n)，用「文本不变则复用」的单条缓存兜住布局/重组期的重复调用。
 */
class CodeHighlightTransformation(
    private val language: CodeLanguage,
    private val palette: CodePalette,
) : VisualTransformation {
    private var cachedText: String? = null
    private var cached: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val plain = text.text
        cached?.let { if (plain == cachedText) return it }
        val styled = if (plain.isEmpty() || plain.length > CodeLanguages.MAX_HIGHLIGHT_CHARS) {
            AnnotatedString(plain)
        } else {
            val spans = scanCode(plain, language)
            if (spans.isEmpty()) {
                AnnotatedString(plain)
            } else {
                buildAnnotatedString {
                    append(plain)
                    for (span in spans) {
                        addStyle(
                            SpanStyle(
                                color = palette.colorOf(span.token),
                                fontWeight = if (span.bold) FontWeight.Bold else null,
                                fontStyle = if (span.italic) FontStyle.Italic else null,
                                textDecoration = if (span.strike) TextDecoration.LineThrough else null,
                            ),
                            span.start,
                            span.end,
                        )
                    }
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity).also {
            cachedText = plain
            cached = it
        }
    }
}

/**
 * 行首缩进标记（每 [CodeLanguages.TAB_SPACES] 个空格一条竖线）。
 * 只在逻辑行首（非折行续行）绘制，纵向落在该视觉行内、上下各留 2dp。
 */
fun DrawScope.drawIndentGuides(
    layout: TextLayoutResult,
    text: String,
    textLeftPx: Float,
    charWidthPx: Float,
    color: Color,
) {
    if (charWidthPx <= 0f) return
    val step = CodeLanguages.TAB_SPACES
    val strokeWidth = 1.dp.toPx()
    val inset = 2.dp.toPx()
    val canvasWidth = size.width

    for (line in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(line)
        if (line > 0 && (lineStart <= 0 || text.getOrNull(lineStart - 1) != '\n')) continue  // 折行续行不画

        var cells = 0
        var offset = lineStart
        while (offset < text.length) {
            when (text[offset]) {
                ' ' -> cells++
                '\t' -> cells += step
                else -> break
            }
            offset++
        }
        val levels = cells / step
        if (levels == 0) continue

        val top = layout.getLineTop(line) + inset
        val bottom = layout.getLineBottom(line) - inset
        if (bottom <= top) continue

        for (level in 1..levels) {
            val x = textLeftPx + level * step * charWidthPx - charWidthPx * 0.5f
            if (x < textLeftPx || x > canvasWidth) continue
            drawLine(
                color = color,
                start = Offset(x, top),
                end = Offset(x, bottom),
                strokeWidth = strokeWidth,
            )
        }
    }
}

/** 等宽字体下一个字符的步进宽度（用同一 TextStyle 量 `xx` 与 `x` 之差，避免空格被裁掉） */
fun monoCharWidthPx(measurer: TextMeasurer, style: TextStyle): Float {
    val one = measurer.measure(AnnotatedString("x"), style).size.width
    val two = measurer.measure(AnnotatedString("xx"), style).size.width
    return (two - one).toFloat()
}
