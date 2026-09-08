package com.pient.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pient.app.ui.theme.MonoFont

/**
 * 简化 GFM 渲染器（pi-web MarkdownBody 同规格子集）：
 * 标题 / 列表（无序+有序）/ 代码块 / 引用 / 分割线 / 粗体 / 斜体 / 行内代码 / 链接。
 * - 本地文件链接 [label](path) 通过 onFileLink 回调（跳转文件页语义）
 * - 代码块：等宽字体 + 面板底，禁止玻璃（设计红线）
 * - 表格 / Mermaid / 数学：接入运行时后由完整渲染管线替换
 */

private sealed class Block {
    data class Header(val level: Int, val text: String) : Block()
    data class Para(val text: String) : Block()
    data class Code(val lang: String, val code: String) : Block()
    data class Quote(val text: String) : Block()
    data class ListBlock(val items: List<ListItem>) : Block()
    object Hr : Block()
}

private data class ListItem(val text: String, val ordered: Boolean)

private fun parseBlocks(markdown: String): List<Block> {
    val lines = markdown.lines()
    val blocks = mutableListOf<Block>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.startsWith("```") -> {
                val lang = line.removePrefix("```").trim()
                val sb = StringBuilder()
                i++
                while (i < lines.size && !lines[i].startsWith("```")) {
                    sb.appendLine(lines[i]); i++
                }
                blocks += Block.Code(lang, sb.toString().trimEnd('\n'))
                i++
            }
            line.startsWith("### ") -> blocks += Block.Header(3, line.removePrefix("### "))
            line.startsWith("## ") -> blocks += Block.Header(2, line.removePrefix("## "))
            line.startsWith("# ") -> blocks += Block.Header(1, line.removePrefix("# "))
            line.startsWith("> ") || line == ">" ->
                blocks += Block.Quote(line.removePrefix("> ").trim())
            line.matches(Regex("-{3,}")) -> blocks += Block.Hr
            line.startsWith("- ") || line.startsWith("* ") -> {
                val items = mutableListOf<ListItem>()
                while (i < lines.size && (lines[i].startsWith("- ") || lines[i].startsWith("* "))) {
                    items += ListItem(lines[i].substring(2), ordered = false)
                    i++
                }
                blocks += Block.ListBlock(items)
                i--
            }
            line.matches(Regex("\\d+\\.\\s+.*")) -> {
                val items = mutableListOf<ListItem>()
                while (i < lines.size && lines[i].matches(Regex("\\d+\\.\\s+.*"))) {
                    items += ListItem(lines[i].substringAfter(". "), ordered = true)
                    i++
                }
                blocks += Block.ListBlock(items)
                i--
            }
            line.isBlank() -> Unit
            else -> {
                val sb = StringBuilder(line)
                while (i + 1 < lines.size && lines[i + 1].isNotBlank() && !isBlockStart(lines[i + 1])) {
                    i++
                    sb.append('\n').append(lines[i])
                }
                blocks += Block.Para(sb.toString())
            }
        }
        i++
    }
    return blocks
}

private fun isBlockStart(line: String): Boolean =
    line.startsWith("#") || line.startsWith("```") || line.startsWith("> ") ||
        line.startsWith("- ") || line.startsWith("* ") ||
        line.matches(Regex("\\d+\\.\\s+.*")) || line.matches(Regex("-{3,}"))

private class InlineStyles(
    val base: TextStyle,
    val bold: SpanStyle,
    val italic: SpanStyle,
    val code: SpanStyle,
    val link: SpanStyle,
)

/** 行内解析：**粗体**、*斜体*、`行内代码`、[label](path) */
private fun parseInline(text: String, s: InlineStyles): AnnotatedString = buildAnnotatedString {
    val regex = Regex("""(\*\*.+?\*\*)|(\*[^*\n]+?\*)|(`[^`]+?`)|(\[[^\]\n]+\]\([^)\n]+\))""")
    var last = 0
    for (m in regex.findAll(text)) {
        append(text.substring(last, m.range.first))
        when {
            m.groupValues[1].isNotEmpty() -> {
                pushStyle(s.bold)
                append(m.groupValues[1].removeSurrounding("**"))
                pop()
            }
            m.groupValues[2].isNotEmpty() -> {
                pushStyle(s.italic)
                append(m.groupValues[2].removeSurrounding("*"))
                pop()
            }
            m.groupValues[3].isNotEmpty() -> {
                pushStyle(s.code)
                append(m.groupValues[3].removeSurrounding("`"))
                pop()
            }
            m.groupValues[4].isNotEmpty() -> {
                val inner = m.groupValues[4]
                val label = inner.substringAfter('[').substringBefore("](")
                val url = inner.substringAfter("](").removeSuffix(")")
                pushStringAnnotation("link", url)
                pushStyle(s.link)
                append(label)
                pop()
                pop()
            }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@Composable
private fun inlineStyles(): InlineStyles {
    val base = MaterialTheme.typography.bodyMedium
    val prose = base.copy(
        // Hermes --conversation-text-font-size 0.8125rem = 13sp / --dt-line-height 1.5；
        // 字号按全局字号设置等比缩放（基准 14sp）
        fontSize = base.fontSize * (13f / 14f),
        lineHeight = 1.5.em,
    )
    return InlineStyles(
        base = prose,
        bold = SpanStyle(fontWeight = FontWeight.Bold),
        italic = SpanStyle(fontStyle = FontStyle.Italic),
        code = SpanStyle(
            fontFamily = MonoFont,
            fontSize = 13.sp,
            background = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        link = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
}

@Composable
fun MarkdownText(
    markdown: String,
    onFileLink: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    val s = inlineStyles()

    Column(modifier) {
        blocks.forEachIndexed { idx, block ->
            when (block) {
                is Block.Header -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.labelLarge
                    }
                    val annotated = remember(block.text, style) { parseInline(block.text, s) }
                    Text(
                        annotated,
                        color = MaterialTheme.colorScheme.onBackground, // 显式主题色，杜绝深色文字
                        // Hermes 标题间距：margin-block 1rem 0.25rem = 16dp/4dp；首块齐平（first-child flush）
                        modifier = Modifier.padding(top = if (idx == 0) 0.dp else 16.dp, bottom = 4.dp),
                    )
                }
                is Block.Para -> ClickableRichText(
                    block.text, onFileLink,
                    s.base.copy(color = MaterialTheme.colorScheme.onBackground),
                    // Hermes 段落间距：--paragraph-gap 0.7rem ≈ 11dp 上距、0 下距；首块齐平
                    modifier = Modifier.padding(top = if (idx == 0) 0.dp else 11.dp),
                )
                is Block.Code -> CodeBlockView(block.lang, block.code)
                is Block.Quote -> Row(Modifier.padding(vertical = 4.dp)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
                    )
                    ClickableRichText(
                        block.text, onFileLink,
                        s.base.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                is Block.ListBlock -> Column(Modifier.padding(vertical = 4.dp)) {
                    block.items.forEachIndexed { itemIdx, item ->
                        Row {
                            Text(
                                if (item.ordered) "${itemIdx + 1}. " else "• ",
                                style = s.base.copy(color = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.padding(start = 4.dp),
                            )
                            ClickableRichText(
                                item.text, onFileLink,
                                s.base.copy(color = MaterialTheme.colorScheme.onBackground),
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
                is Block.Hr -> HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ClickableRichText(
    text: String,
    onFileLink: ((String) -> Unit)?,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val s = inlineStyles()
    val annotated = remember(text, style) { parseInline(text, s) }
    ClickableText(
        text = annotated,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations("link", offset, offset).firstOrNull()?.let {
                onFileLink?.invoke(it.item)
            }
        },
    )
}

/** 代码块：等宽 + 面板底 + hairline 边框；禁止玻璃 */
@Composable
private fun CodeBlockView(lang: String, code: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        if (lang.isNotEmpty()) {
            Text(
                lang,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            code,
            color = MaterialTheme.colorScheme.onBackground, // 显式主题色
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFont, lineHeight = 20.sp),
        )
    }
}
