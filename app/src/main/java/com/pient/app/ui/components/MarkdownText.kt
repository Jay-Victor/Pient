package com.pient.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pient.app.data.CodeLanguages
import com.pient.app.data.FileNode
import com.pient.app.data.MdBlock
import com.pient.app.data.MdFrontmatter
import com.pient.app.data.MdSpan
import com.pient.app.data.ProjectFiles
import com.pient.app.data.parseMdInline
import com.pient.app.data.parseMarkdown
import com.pient.app.ui.files.CodeHighlightTransformation
import com.pient.app.ui.files.codePalette
import com.pient.app.ui.theme.LocalPientIsDark
import com.pient.app.ui.theme.MonoFont
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * GFM 渲染器（文件预览 / 对话 / 弹窗共用）—— 规格逐项对齐 pi-web：
 * - 基础排版 = `.markdown-body`（font-size 14px、line-height 1.7、p margin-bottom 12px、
 *   h1~h6 margin 10px 0 5px + weight 600 + line-height 1.35、ul/ol padding-left 22px / margin 5px 0 8px、
 *   li margin 3px 0、marker 色 mix(accent 72%, text-muted) + weight 600、strong mix(text 88%, accent) + 700、
 *   em → text-muted、a → accent + 下划线、blockquote 左边 3px + radius 0/6/6/0 + padding 6/11 + bg subtle、
 *   table 外框 border + radius 7px / th 底 panel / 偶数行 bg subtle / 单元格 padding 6px 10px / 13px、
 *   hr = 1px 渐隐线 margin 12px 0、行内代码 bg subtle + mono + 0.92em）
 * - 文件预览增量 = `.markdown-file-preview`（h1 1.8em / h2 1.4em / h3 1.15em，h4~h6 浏览器默认 1em/0.83em/0.67em，
 *   容器留白 24px 32px 见 FileContentView）
 * - 代码块 = `.markdown-code-block`（外框 border + radius 7px、头部 padding 5px 10px + bg panel + 11px 语言标、
 *   正文 padding 11px 13px + 12.5px/1.62 + 行号 text-dim + 复制键）→ 高亮走 Operit 同款 VS Code 调色板
 * - frontmatter = pi-web FrontmatterCard（标题 / 标签胶囊 / 键值行）
 *
 * 与 pi-web 的已知差距（本层不做）：Mermaid、内联 HTML 块、表格横向滚动（改为列宽自适应换行）、
 * 行内代码的背景圆角与内边距（Compose 的 SpanStyle 不支持逐片段 padding/圆角）、远端图片（无图片加载库）。
 * 数学公式走 jlatexmath 原生渲染（`ui/components/Latex.kt`），不引入 KaTeX/WebView —— 命令覆盖度比 KaTeX
 * 略窄（缺 `\begin{align}` 之类），渲染不出来的公式退化为等宽源码显示。
 */

// ───────────────────────────── 调色板 / 样式 ─────────────────────────────

private class MdStyles(
    val base: TextStyle,
    val filePreview: Boolean,
    /** 对话/弹窗口径的 1~3 级标题（Material 排版；文件预览用 em 倍率另算） */
    val conversationHeadings: List<TextStyle>,
    val text: Color,
    val muted: Color,
    val dim: Color,
    val accent: Color,
    val panel: Color,
    val border: Color,
    val subtle: Color,
    val isDark: Boolean,
) {
    /** 行内代码：bg subtle + mono + 0.92em */
    val codeSpan: SpanStyle get() = SpanStyle(
        fontFamily = MonoFont,
        fontSize = base.fontSize * 0.92f,
        background = subtle,
        color = text,
    )
    val linkSpan: SpanStyle get() = SpanStyle(color = accent, textDecoration = TextDecoration.Underline)
    val boldColor: Color get() = lerp(text, accent, 0.12f)

    /** 块间距（CSS margin 折叠：相邻块取两者较大值） */
    fun gaps(block: MdBlock): Pair<Int, Int> = when (block) {
        is MdBlock.Heading -> if (filePreview) 10 to 5 else 16 to 4
        is MdBlock.Paragraph -> if (filePreview) 12 to 12 else 11 to 0
        is MdBlock.ListBlock -> 5 to 8
        is MdBlock.Quote -> 6 to 6
        is MdBlock.Code -> 6 to 6
        is MdBlock.Table -> 8 to 8
        // `.katex-display { margin: 0.6em 0 }`（正文 13~14sp → 约 8dp）
        is MdBlock.MathBlock -> 8 to 8
        MdBlock.Hr -> 12 to 12
    }

    fun headingStyle(level: Int): TextStyle = if (filePreview) {
        base.copy(
            fontSize = base.fontSize * when (level) {
                1 -> 1.8f; 2 -> 1.4f; 3 -> 1.15f; 4 -> 1f; 5 -> 0.83f; else -> 0.67f
            },
            fontWeight = FontWeight.SemiBold,
            lineHeight = 1.35.em,
        )
    } else {
        conversationHeadings.getOrElse(level - 1) { conversationHeadings.last() }
    }
}

@Composable
private fun rememberMdStyles(filePreview: Boolean, reasoning: Boolean = false): MdStyles {
    val scheme = MaterialTheme.colorScheme
    val isDark = LocalPientIsDark.current
    val base = MaterialTheme.typography.bodyMedium
    val prose = when {
        // 思考/推理正文（Hermes `text-xs leading-snug text-muted-foreground/85`）：
        // 12px + 1.375 行高——比回答正文（13px/1.5）更小更紧凑，读起来是「过程」不是「回答」
        reasoning -> base.copy(fontSize = base.fontSize * (12f / 14f), lineHeight = 1.375.em)
        // `.markdown-body`：14px / line-height 1.7（字号随全局字体设置缩放）
        filePreview -> base.copy(lineHeight = 1.7.em)
        else -> base.copy(fontSize = base.fontSize * (13f / 14f), lineHeight = 1.5.em)
    }
    return MdStyles(
        base = prose,
        filePreview = filePreview,
        conversationHeadings = listOf(
            MaterialTheme.typography.titleLarge,
            MaterialTheme.typography.titleMedium,
            MaterialTheme.typography.labelLarge,
            MaterialTheme.typography.labelLarge,
            MaterialTheme.typography.labelLarge,
            MaterialTheme.typography.labelLarge,
        ),
        text = if (reasoning) scheme.onSurfaceVariant.copy(alpha = 0.85f) else scheme.onBackground,
        muted = scheme.onSurfaceVariant,
        dim = scheme.onSurfaceVariant.copy(alpha = 0.7f),
        accent = scheme.primary,
        panel = scheme.surfaceContainer,
        border = scheme.outlineVariant,
        subtle = scheme.onBackground.copy(alpha = if (isDark) 0.04f else 0.03f),
        isDark = isDark,
    )
}

// ───────────────────────────── 入口 ─────────────────────────────

@Composable
fun MarkdownText(
    markdown: String,
    onFileLink: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** 文件预览口径（`.markdown-file-preview` 标题字号 + 段落间距）；对话/弹窗沿用既有 Material 排版 */
    filePreview: Boolean = false,
    /** 本地图片解析（相对路径 → 文件树节点）；null 时图片只显示 alt 文本 */
    imageResolver: ((String) -> FileNode?)? = null,
    /** 思考/推理正文口径（Hermes：12px、行高 1.375、muted 85%）；思考折叠块专用 */
    reasoning: Boolean = false,
) {
    val s = rememberMdStyles(filePreview, reasoning)
    val doc = remember(markdown) { parseMarkdown(markdown) }

    Column(modifier) {
        doc.frontmatter?.let { FrontmatterCard(it, s) }
        MarkdownBlockList(doc.blocks, s, onFileLink, imageResolver, topLevel = true)
    }
}

@Composable
private fun MarkdownBlockList(
    blocks: List<MdBlock>,
    s: MdStyles,
    onFileLink: ((String) -> Unit)?,
    imageResolver: ((String) -> FileNode?)?,
    topLevel: Boolean = false,
) {
    var prevBottom = 0
    blocks.forEachIndexed { index, block ->
        val (top, bottom) = s.gaps(block)
        val gap = if (index == 0) 0 else maxOf(prevBottom, top)
        MarkdownBlock(block, s, onFileLink, imageResolver, gap, topLevel)
        prevBottom = bottom
    }
}

@Composable
private fun MarkdownBlock(
    block: MdBlock,
    s: MdStyles,
    onFileLink: ((String) -> Unit)?,
    imageResolver: ((String) -> FileNode?)?,
    topGap: Int,
    topLevel: Boolean,
) {
    val topPad = Modifier.padding(top = topGap.dp)
    when (block) {
        is MdBlock.Heading -> {
            val style = s.headingStyle(block.level).copy(color = s.text)
            val spans = remember(block.text) { parseMdInline(block.text) }
            InlineText(spans, style, s, onFileLink, imageResolver, topPad)
        }
        is MdBlock.Paragraph -> {
            val spans = remember(block.text) { parseMdInline(block.text) }
            InlineText(spans, s.base.copy(color = s.text), s, onFileLink, imageResolver, topPad)
        }
        is MdBlock.Code -> MarkdownCodeBlock(block.lang, block.code, s, topPad)
        is MdBlock.Quote -> QuoteBlock(block.blocks, s, onFileLink, imageResolver, topPad)
        is MdBlock.ListBlock -> ListBlock(block, s, onFileLink, imageResolver, topPad)
        is MdBlock.Table -> TableBlock(block, s, topPad)
        is MdBlock.MathBlock -> LatexBlock(block.latex, s, topPad)
        MdBlock.Hr -> Box(topPad.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(listOf(Color.Transparent, s.border, Color.Transparent)),
                    ),
            )
        }
    }
}

// ───────────────────────────── 行内文本 ─────────────────────────────

@Composable
private fun InlineText(
    spans: List<MdSpan>,
    style: TextStyle,
    s: MdStyles,
    onFileLink: ((String) -> Unit)?,
    imageResolver: ((String) -> FileNode?)?,
    modifier: Modifier = Modifier,
) {
    if (spans.isEmpty()) return
    // 图片是块级元素（pi-web `.markdown-body img { display: block }`）→ 逐段拆成「文本行 + 图片行」
    var buffer = ArrayList<MdSpan>()
    val groups = ArrayList<Pair<Boolean, List<MdSpan>>>()   // true = 图片组
    spans.forEach { span ->
        if (span.image != null) {
            if (buffer.isNotEmpty()) groups += false to buffer
            buffer = ArrayList()
            groups += true to listOf(span)
        } else {
            buffer.add(span)
        }
    }
    if (buffer.isNotEmpty()) groups += false to buffer

    if (groups.size == 1 && !groups[0].first) {
        RichText(groups[0].second, style, s, onFileLink, modifier)
        return
    }
    Column(modifier) {
        groups.forEach { (isImage, group) ->
            if (isImage) MarkdownImage(group[0].image!!, group[0].text, imageResolver, s)
            else RichText(group, style, s, onFileLink)
        }
    }
}

@Composable
private fun RichText(
    spans: List<MdSpan>,
    style: TextStyle,
    s: MdStyles,
    onFileLink: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // MdStyles 每次组合都是新实例（无 equals）→ remember 的键用其中真正影响渲染的字段
    val md = remember(spans, style, s.text, s.accent, s.muted, s.subtle, s.boldColor, density) {
        buildMdAnnotated(spans, s, mathTextSizePx(style, s, density), s.text.toArgb(), density)
    }
    val hasLink = remember(md) { md.text.getStringAnnotations("link", 0, md.text.length).isNotEmpty() }
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = md.text,
        inlineContent = md.inlineContent,
        style = style,
        onTextLayout = { layout.value = it },
        modifier = modifier.pointerInput(md, onFileLink, hasLink) {
            // 无链接的段落不接管点击（滚动/长按照旧交给父级）；点击命中链接区间时回调（ClickableText 同语义）
            if (onFileLink == null || !hasLink) return@pointerInput
            detectTapGestures { position ->
                val offset = layout.value?.getOffsetForPosition(position) ?: return@detectTapGestures
                md.text.getStringAnnotations("link", offset, offset).firstOrNull()?.let { onFileLink.invoke(it.item) }
            }
        },
    )
}

/** 构建后的行内文本：AnnotatedString + 内联公式内容表 + 占位符区间（占位符同时供表格测量使用） */
private class MdAnnotated(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
    val placeholders: List<AnnotatedString.Range<Placeholder>>,
)

/** 公式字号 = 所在文本字号 ×1.05（pi-web `.markdown-body .katex { font-size: 1.05em }`） */
private fun mathTextSizePx(style: TextStyle, s: MdStyles, density: Density): Float {
    val size = if (style.fontSize.isSp) style.fontSize else s.base.fontSize
    return with(density) { size.toPx() } * 1.05f
}

/**
 * 行内片段 → AnnotatedString（数学公式以「内联内容」形式嵌入文本流，随文字一起换行，
 * 垂直方向按行内文字中心对齐 —— Operit `LatexDrawableSpan` 同口径：公式位图不贴基线，
 * 否则分式/积分/下标会被整体顶高）。
 * 渲染不出来的公式退化为等宽源码（Operit 同款兜底），不影响其它行内样式。
 */
private fun buildMdAnnotated(
    spans: List<MdSpan>,
    s: MdStyles,
    mathTextSizePx: Float,
    mathColorArgb: Int,
    density: Density,
): MdAnnotated {
    val inline = LinkedHashMap<String, InlineTextContent>()
    val placeholders = ArrayList<AnnotatedString.Range<Placeholder>>()
    val rendered = HashMap<String, LatexImage?>()
    var mathIndex = 0
    val text = buildAnnotatedString {
        for (span in spans) {
            if (span.math) {
                val image = rendered.getOrPut(span.text) {
                    LatexRenderer.image(span.text, mathTextSizePx, mathColorArgb)
                }
                if (image == null) {
                    withStyle(s.codeSpan) { append(span.text) }
                    continue
                }
                mathIndex++
                val id = "math#$mathIndex"
                val placeholder = Placeholder(
                    width = with(density) { image.widthPx.toSp() },
                    height = with(density) { image.heightPx.toSp() },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                )
                val start = length
                appendInlineContent(id, span.text)
                val end = length
                inline[id] = InlineTextContent(placeholder) { LatexInline(image) }
                placeholders += AnnotatedString.Range(placeholder, start, end)
                continue
            }
            if (span.lineBreak) {
                append("\n")
                continue
            }
            if (span.image != null) continue
            val style = when {
                span.code -> s.codeSpan
                span.link != null -> SpanStyle(
                    color = s.accent,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                )
                span.bold -> SpanStyle(
                    color = s.boldColor,
                    fontWeight = FontWeight.Bold,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    textDecoration = if (span.strike) TextDecoration.LineThrough else null,
                )
                span.italic -> SpanStyle(
                    color = s.muted,
                    fontStyle = FontStyle.Italic,
                    textDecoration = if (span.strike) TextDecoration.LineThrough else null,
                )
                span.strike -> SpanStyle(color = s.muted, textDecoration = TextDecoration.LineThrough)
                else -> SpanStyle(color = s.text)
            }
            if (span.link != null) pushStringAnnotation("link", span.link)
            withStyle(style) { append(span.text) }
            if (span.link != null) pop()
        }
    }
    return MdAnnotated(text, inline, placeholders)
}

/** 行内公式：位图按 1:1 落在占位槽内（槽尺寸 = 位图尺寸） */
@Composable
private fun LatexInline(image: LatexImage) {
    val density = LocalDensity.current
    Image(
        bitmap = image.bitmap,
        contentDescription = null,
        modifier = Modifier.size(
            with(density) { image.widthPx.toDp() },
            with(density) { image.heightPx.toDp() },
        ),
    )
}

/**
 * 块级公式（`$$…$$` / `\[…\]`）：居中显示、超宽横向滚动（Operit BLOCK_LATEX 口径），
 * 字号 = 正文 ×1.05、上下留白见 [MdStyles.gaps]（pi-web `.katex-display { margin: .6em 0 }`）。
 */
@Composable
private fun LatexBlock(latex: String, s: MdStyles, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val sizePx = with(density) { s.base.fontSize.toPx() * 1.05f }
    val colorArgb = s.text.toArgb()
    val image = remember(latex, sizePx, colorArgb) { LatexRenderer.image(latex, sizePx, colorArgb) }
    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // 视口宽兜底：公式窄于视口时居中、宽于视口时横向滚动（内层盒宽 = max(视口, 公式)）
        val minWidth = if (constraints.hasBoundedWidth) maxWidth else 0.dp
        Box(Modifier.horizontalScroll(scrollState)) {
            Box(Modifier.widthIn(min = minWidth), contentAlignment = Alignment.Center) {
                if (image == null) {
                    Text(
                        text = latex,
                        style = s.base.copy(fontFamily = MonoFont, fontSize = s.base.fontSize * 0.92f),
                        color = s.muted,
                    )
                } else {
                    Image(
                        bitmap = image.bitmap,
                        contentDescription = latex,
                        modifier = Modifier.size(
                            with(density) { image.widthPx.toDp() },
                            with(density) { image.heightPx.toDp() },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownImage(
    src: String,
    alt: String,
    imageResolver: ((String) -> FileNode?)?,
    s: MdStyles,
) {
    val context = LocalContext.current
    val node = remember(src, imageResolver) { runCatching { imageResolver?.invoke(src) }.getOrNull() }
    val bitmap by produceState<Bitmap?>(initialValue = null, node?.source) {
        value = if (node == null) null
        else withContext(Dispatchers.IO) { ProjectFiles.readBitmap(context, node) }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = alt,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.FillWidth,
            )
        } else {
            Text(
                text = "🖼 ${alt.ifEmpty { src }}",
                style = s.base.copy(fontSize = s.base.fontSize * 0.92f),
                color = s.dim,
            )
        }
    }
}

// ───────────────────────────── 代码块 ─────────────────────────────

@Composable
private fun MarkdownCodeBlock(lang: String, code: String, s: MdStyles, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isDark = LocalPientIsDark.current
    val palette = remember(isDark) { codePalette(isDark) }
    val language = remember(lang) {
        val key = lang.trim().lowercase().substringBefore(',')
        CodeLanguages.byName(key) ?: CodeLanguages.generic
    }
    val highlighted = remember(code, language, palette) {
        CodeHighlightTransformation(language, palette).filter(AnnotatedString(code)).text
    }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    val codeStyle = s.base.copy(
        fontFamily = MonoFont,
        fontSize = 12.5.sp,
        lineHeight = 1.62.em,
        color = s.text,
    )
    val lineCount = remember(code) { code.count { it == '\n' } + 1 }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, s.border, RoundedCornerShape(7.dp)),
    ) {
        // 头部：语言标 + 复制（`.markdown-code-header` / `.markdown-code-lang` / `.markdown-code-action`）
        Row(
            Modifier
                .fillMaxWidth()
                .background(s.panel)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = lang.trim().ifEmpty { "text" },
                style = TextStyle(fontFamily = MonoFont, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = s.muted,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, s.border, RoundedCornerShape(5.dp))
                    .clickable {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("code", code))
                        copied = true
                    }
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    text = if (copied) "已复制" else "复制",
                    style = TextStyle(fontSize = 11.sp, lineHeight = 1.25.em),
                    color = s.muted,
                )
            }
        }
        // 正文：行号（text-dim）+ 高亮代码；不折行 → 横向滚动（`white-space: pre` + overflow-x auto）
        Row(
            Modifier
                .fillMaxWidth()
                .background(lerp(MaterialTheme.colorScheme.background, s.panel, 0.08f))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 13.dp, vertical = 11.dp),
        ) {
            val numbers = remember(lineCount) { (1..lineCount).joinToString("\n") }
            Text(text = numbers, style = codeStyle.copy(color = s.dim, textAlign = TextAlign.End))
            Spacer(Modifier.width(10.dp))
            Text(text = highlighted, style = codeStyle)
        }
    }
}

// ───────────────────────────── 列表 ─────────────────────────────

@Composable
private fun ListBlock(
    block: MdBlock.ListBlock,
    s: MdStyles,
    onFileLink: ((String) -> Unit)?,
    imageResolver: ((String) -> FileNode?)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(start = 22.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                val markerWidth = 18.dp
                if (item.task != null) {
                    Box(Modifier.width(markerWidth).padding(top = 3.dp)) { TaskCheckbox(item.checked, s) }
                } else {
                    Text(
                        text = if (block.ordered) "${block.start + index}." else "•",
                        style = s.base.copy(fontWeight = FontWeight.SemiBold),
                        color = lerp(s.accent, s.muted, 0.28f),
                        modifier = Modifier.width(markerWidth),
                    )
                }
                Column(Modifier.fillMaxWidth()) {
                    val spans = remember(item.text) { parseMdInline(item.text) }
                    InlineText(spans, s.base.copy(color = s.text), s, onFileLink, imageResolver)
                    if (item.children.isNotEmpty()) {
                        MarkdownBlockList(item.children, s, onFileLink, imageResolver)
                    }
                }
            }
        }
    }
}

/** 任务项复选框（`.task-list-item input[type=checkbox]`：14px 方框、radius 4px、勾选时 accent 描边/淡底/勾） */
@Composable
private fun TaskCheckbox(checked: Boolean, s: MdStyles) {
    Box(
        Modifier
            .size(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked) lerp(MaterialTheme.colorScheme.background, s.accent, 0.10f) else MaterialTheme.colorScheme.background)
            .border(
                1.dp,
                if (checked) lerp(s.border, s.accent, 0.55f) else s.border,
                RoundedCornerShape(4.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Canvas(Modifier.size(8.dp)) {
                val stroke = 1.5.dp.toPx()
                drawLine(
                    color = s.accent,
                    start = Offset(size.width * 0.05f, size.height * 0.55f),
                    end = Offset(size.width * 0.4f, size.height * 0.9f),
                    strokeWidth = stroke,
                )
                drawLine(
                    color = s.accent,
                    start = Offset(size.width * 0.4f, size.height * 0.9f),
                    end = Offset(size.width * 1.0f, size.height * 0.1f),
                    strokeWidth = stroke,
                )
            }
        }
    }
}

// ───────────────────────────── 引用 ─────────────────────────────

@Composable
private fun QuoteBlock(
    blocks: List<MdBlock>,
    s: MdStyles,
    onFileLink: ((String) -> Unit)?,
    imageResolver: ((String) -> FileNode?)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(lerp(s.border, s.muted, 0.25f)),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(s.subtle, RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                .padding(horizontal = 11.dp, vertical = 6.dp),
        ) {
            QuoteAwareBlocks(blocks, s, onFileLink, imageResolver)
        }
    }
}

@Composable
private fun QuoteAwareBlocks(
    blocks: List<MdBlock>,
    s: MdStyles,
    onFileLink: ((String) -> Unit)?,
    imageResolver: ((String) -> FileNode?)?,
) {
    var prevBottom = 0
    blocks.forEachIndexed { index, block ->
        val (top, bottom) = s.gaps(block)
        // 引用内段落取 text-muted（`.markdown-body blockquote { color: var(--text-muted) }`）
        val tinted = when (block) {
            is MdBlock.Paragraph -> {
                val spans = remember(block.text) { parseMdInline(block.text) }
                InlineText(
                    spans = spans,
                    style = s.base.copy(color = s.muted),
                    s = s,
                    onFileLink = onFileLink,
                    imageResolver = imageResolver,
                    modifier = Modifier.padding(top = (if (index == 0) 0 else maxOf(prevBottom, top)).dp),
                )
                true
            }
            else -> false
        }
        if (!tinted) {
            MarkdownBlock(block, s, onFileLink, imageResolver, if (index == 0) 0 else maxOf(prevBottom, top), topLevel = false)
        }
        prevBottom = bottom
    }
}

// ───────────────────────────── 表格 ─────────────────────────────

@Composable
private fun TableBlock(block: MdBlock.Table, s: MdStyles, modifier: Modifier = Modifier) {
    val columns = maxOf(block.head.size, block.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cellPadding = with(density) { (TableCellHorizontalPadding * 2).toPx() }
        // 列宽（px）：逐格量出内容宽度 → 每列取最小/最大内容宽度 → 按可用宽度分配
        val widths = remember(block, s, constraints.maxWidth, constraints.hasBoundedWidth) {
            if (!constraints.hasBoundedWidth) {
                // 宽度无界（如横向滚动容器内）拿不到可用宽度，退回按字符数估权重
                estimatedColumnWeights(block, columns)
            } else {
                val minW = FloatArray(columns)
                val maxW = FloatArray(columns)
                fun accumulate(cells: List<String>, header: Boolean) {
                    val style = tableCellStyle(s, header)
                    for (col in 0 until columns) {
                        val raw = cells.getOrNull(col) ?: continue
                        val cell = buildMdAnnotated(
                            spans = parseMdInline(raw),
                            s = s,
                            mathTextSizePx = mathTextSizePx(style, s, density),
                            mathColorArgb = s.text.toArgb(),
                            density = density,
                        )
                        val (min, max) = cellContentWidths(measurer, cell.text, style, cell.placeholders)
                        if (min > minW[col]) minW[col] = min
                        if (max > maxW[col]) maxW[col] = max
                    }
                }
                accumulate(block.head, header = true)
                block.rows.forEach { accumulate(it, header = false) }
                distributeColumnWidths(minW.toList(), maxW.toList(), constraints.maxWidth.toFloat(), cellPadding)
            }
        }
        TableGrid(block, widths, s)
    }
}

@Composable
private fun TableGrid(block: MdBlock.Table, widths: List<Float>, s: MdStyles) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, s.border, RoundedCornerShape(7.dp))
            // 列分隔竖线：行间横线由各行的 Box 画，列间竖线没有对应的布局元素（Row 高度由内容决定，
            // 竖条用 fillMaxHeight 需要 IntrinsicSize 测量），改为在表格容器上按列宽等比绘制整表贯穿的竖线。
            .drawWithContent {
                drawContent()
                val total = widths.sum()
                if (total <= 0f) return@drawWithContent
                // 像素对齐：竖线落点取整，宽度取整，避免 1dp 在非整数倍密度下被抗锯齿糊成 2px 灰带
                val stroke = 1.dp.toPx().roundToInt().toFloat()
                var acc = 0f
                for (i in 0 until widths.size - 1) {
                    acc += widths[i]
                    val left = (size.width * acc / total - stroke / 2f).roundToInt().toFloat()
                    drawRect(
                        color = s.border,
                        topLeft = Offset(left, 0f),
                        size = Size(stroke, size.height),
                    )
                }
            },
    ) {
        TableRow(block.head, widths, s, header = true)
        block.rows.forEachIndexed { index, row ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(s.border))
            TableRow(row, widths, s, header = false, zebra = index % 2 == 1)
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    widths: List<Float>,
    s: MdStyles,
    header: Boolean,
    zebra: Boolean = false,
) {
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    header -> s.panel
                    zebra -> s.subtle
                    else -> Color.Transparent
                },
            ),
        // 单元格文字在行内垂直居中（行高由最高的单元格决定，单行单元格贴顶会显得参差）
        verticalAlignment = Alignment.CenterVertically,
    ) {
        widths.forEachIndexed { index, weight ->
            val cellText = cells.getOrNull(index) ?: ""
            val style = tableCellStyle(s, header)
            val md = remember(cellText, style, s.text, s.accent, s.muted, s.subtle, s.boldColor, density) {
                buildMdAnnotated(
                    spans = parseMdInline(cellText),
                    s = s,
                    mathTextSizePx = mathTextSizePx(style, s, density),
                    mathColorArgb = s.text.toArgb(),
                    density = density,
                )
            }
            Text(
                text = md.text,
                inlineContent = md.inlineContent,
                style = style,
                modifier = Modifier.weight(weight).padding(horizontal = TableCellHorizontalPadding, vertical = 6.dp),
            )
        }
    }
}

// ── 列宽计算：最小/最大内容宽度（MCW / max-content）→ 按可用宽度分配 ──

/** 单元格左右内边距（列宽分配时要先从可用宽度里扣除） */
private val TableCellHorizontalPadding = 10.dp

/** 单元格文字样式（表头/正文两态）：水平居中为用户口径，表头行与左右两列同样居中 */
private fun tableCellStyle(s: MdStyles, header: Boolean): TextStyle = s.base.copy(
    fontSize = 13.sp,
    lineHeight = 1.55.em,
    fontWeight = if (header) FontWeight.W600 else FontWeight.Normal,
    color = if (header) lerp(s.text, s.muted, 0.12f) else s.text,
    textAlign = TextAlign.Center,
)

/** 一个单元格的 (最小内容宽度, 最大内容宽度)，单位 px；[placeholders] = 行内公式占位槽（测量时按位图实际宽度计入） */
private fun cellContentWidths(
    m: TextMeasurer,
    text: AnnotatedString,
    style: TextStyle,
    placeholders: List<AnnotatedString.Range<Placeholder>>,
): Pair<Float, Float> {
    val max = m.measure(text, style, placeholders = placeholders, constraints = Constraints()).size.width.toFloat()
    var min = 0f
    // MCW = 最宽的「不可断行单元」：CJK 逐字可断（单字即一单元），空白处可断，其余拉丁/数字/标点连成一个单元
    var units = unbreakableUnits(text.text)
    if (units.size > MaxMeasuredUnits) {
        // 长文本只量最长的若干单元，避免逐词测量拖慢首帧（按字符数取候选）
        units = units.sortedByDescending { it.second - it.first }.take(MaxMeasuredUnits)
    }
    for ((start, end) in units) {
        val w = m.measure(
            text = text.subSequence(start, end),
            style = style,
            placeholders = placeholdersIn(placeholders, start, end),
            constraints = Constraints(),
        ).size.width.toFloat()
        if (w > min) min = w
    }
    return min to max
}

/** 子串测量用：取出落在 [start, end) 内的公式占位槽并平移到子串坐标系 */
private fun placeholdersIn(
    placeholders: List<AnnotatedString.Range<Placeholder>>,
    start: Int,
    end: Int,
): List<AnnotatedString.Range<Placeholder>> = placeholders.mapNotNull { range ->
    val s = maxOf(range.start, start)
    val e = minOf(range.end, end)
    if (s >= e) null else AnnotatedString.Range(range.item, s - start, e - start)
}

private const val MaxMeasuredUnits = 12

/** 文本里各「不可断行单元」的 [start, end) 区间 */
private fun unbreakableUnits(text: String): List<Pair<Int, Int>> {
    val units = ArrayList<Pair<Int, Int>>()
    var runStart = -1
    var i = 0
    while (i < text.length) {
        val cp = Character.codePointAt(text, i)
        val size = Character.charCount(cp)
        when {
            isCjk(cp) -> {
                if (runStart >= 0) {
                    units += runStart to i
                    runStart = -1
                }
                units += i to i + size
            }
            Character.isWhitespace(cp) -> {
                if (runStart >= 0) {
                    units += runStart to i
                    runStart = -1
                }
            }
            runStart < 0 -> runStart = i
        }
        i += size
    }
    if (runStart >= 0) units += runStart to text.length
    return units
}

/** CJK 及全角标点：两侧都是断行机会（Kinsoku 的简化判断） */
private fun isCjk(cp: Int): Boolean =
    cp in 0x2E80..0x303F || cp in 0x3040..0x30FF || cp in 0x3400..0x4DBF ||
        cp in 0x4E00..0x9FFF || cp in 0xF900..0xFAFF || cp in 0xFE30..0xFE4F ||
        cp in 0xFF00..0xFFEF || cp in 0x20000..0x2FA1F

/**
 * 列宽分配（CSS auto table layout 的简化版；pi-web 那边由浏览器表格布局完成）：
 * ① Σ最大内容宽度 ≤ 可用文字区 → 每列都拿到自己的最大内容宽度，余量按最大内容宽度比例摊开铺满；
 * ② Σ最小内容宽度 > 可用文字区 → 表里存在超长不可断单元（长 URL / 长标识符），物理上放不下，
 *    改按「最大内容宽度」做 progressive filling：需求小的列先按需满足，剩下的列均分余量；
 * ③ 中间态 → 先给每列 MCW 保底，余量按「最大内容宽度」从小到大依次补足，补不满的列按最大内容宽度比例分摊。
 * 传入/传出均为 px；单元格内边距先扣除，分配的是文字区宽度。
 * 注：②里 MCW 必然被突破（例：57 字符的 token 宽 942px 而文字区只有 833px），此时交给列内词内折行。
 * 关键是别用「按增长潜力比例」一刀切 —— 那样短列（如 2 字的表头，只需 68px）会被饿到 40px 而换行。
 */
private fun distributeColumnWidths(
    minW: List<Float>,
    maxW: List<Float>,
    available: Float,
    cellPadding: Float,
): List<Float> {
    val n = minW.size
    if (n == 0) return emptyList()
    val textSpace = (available - n * cellPadding).coerceAtLeast(0f)
    val text = FloatArray(n)
    val maxSum = maxW.sum()
    when {
        maxSum <= textSpace -> {
            for (i in 0 until n) text[i] = if (maxSum > 0f) maxW[i] * textSpace / maxSum else textSpace / n
        }
        minW.sum() > textSpace -> {
            progressiveFill(text, maxW.toFloatArray(), textSpace)
        }
        else -> {
            for (i in 0 until n) text[i] = minW[i]
            var free = textSpace - minW.sum()
            val pending = ArrayList<Int>()
            for (i in (0 until n).sortedBy { maxW[it] }) {
                val need = maxW[i] - text[i]
                if (need <= free) {
                    text[i] = maxW[i]
                    free -= need
                } else {
                    pending += i
                }
            }
            if (free > 0f) {
                val targets = if (pending.isEmpty()) (0 until n).toList() else pending
                var pool = 0f
                for (i in targets) pool += maxW[i]
                if (pool > 0f) for (i in targets) text[i] += free * maxW[i] / pool
            }
        }
    }
    return List(n) { text[it] + cellPadding }
}

/** 经典 progressive filling（最大最小公平分配）：需求小的列先按需满足，需求装不下的列均分余量 */
private fun progressiveFill(out: FloatArray, demand: FloatArray, budget: Float) {
    var remaining = budget
    val active = ArrayList(out.indices.sortedBy { demand[it] })
    while (active.isNotEmpty()) {
        val share = remaining / active.size
        val fit = active.filter { demand[it] <= share }
        if (fit.isEmpty()) {
            for (i in active) out[i] = share
            return
        }
        for (i in fit) {
            out[i] = demand[i]
            remaining -= demand[i]
        }
        active.removeAll(fit)
    }
}

/** 宽度无界时的兜底权重：按各列最长文本的字符数估（旧口径） */
private fun estimatedColumnWeights(block: MdBlock.Table, columns: Int): List<Float> =
    List(columns) { col ->
        val maxLen = (listOf(block.head.getOrNull(col)) + block.rows.map { it.getOrNull(col) })
            .filterNotNull().maxOfOrNull { it.length } ?: 1
        maxLen.coerceIn(3, 40).toFloat()
    }

// ───────────────────────────── frontmatter 卡片 ─────────────────────────────

@Composable
private fun FrontmatterCard(front: MdFrontmatter, s: MdStyles) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .background(s.panel, RoundedCornerShape(8.dp))
            .border(1.dp, s.border, RoundedCornerShape(8.dp))
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
    ) {
        front.title?.let {
            Text(it, style = s.base.copy(fontSize = s.base.fontSize * 1.3f, fontWeight = FontWeight.SemiBold, lineHeight = 1.35.em), color = s.text)
        }
        if (front.tags.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                front.tags.forEach { tag ->
                    Text(
                        text = tag,
                        style = TextStyle(fontSize = 12.sp, lineHeight = 1.6.em),
                        color = s.muted,
                        modifier = Modifier
                            .background(s.subtle, RoundedCornerShape(999.dp))
                            .border(1.dp, s.border, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp),
                    )
                }
            }
        }
        if (front.rows.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                front.rows.forEach { (key, value) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            text = key,
                            style = TextStyle(fontFamily = MonoFont, fontSize = 13.sp * 0.92f, lineHeight = 1.55.em),
                            color = s.muted,
                            modifier = Modifier.padding(end = 14.dp),
                        )
                        Text(
                            text = value,
                            style = TextStyle(fontSize = 13.sp, lineHeight = 1.55.em),
                            color = s.text,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
