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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pient.app.data.CodeLanguages
import com.pient.app.data.FileNode
import com.pient.app.data.MdAlign
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
 * 与 pi-web 的已知差距（本层不做）：Mermaid、数学公式（KaTeX）、内联 HTML 块、表格横向滚动（改为列宽自适应换行）、
 * 行内代码的背景圆角与内边距（Compose 的 SpanStyle 不支持逐片段 padding/圆角）、远端图片（无图片加载库）。
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
private fun rememberMdStyles(filePreview: Boolean): MdStyles {
    val scheme = MaterialTheme.colorScheme
    val isDark = LocalPientIsDark.current
    val base = MaterialTheme.typography.bodyMedium
    val prose = if (filePreview) {
        // `.markdown-body`：14px / line-height 1.7（字号随全局字体设置缩放）
        base.copy(lineHeight = 1.7.em)
    } else {
        base.copy(fontSize = base.fontSize * (13f / 14f), lineHeight = 1.5.em)
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
        text = scheme.onBackground,
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
) {
    val s = rememberMdStyles(filePreview)
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
    val annotated = remember(spans, style, s.text, s.accent, s.muted, s.subtle, s.boldColor) {
        buildAnnotated(spans, s)
    }
    ClickableText(
        text = annotated,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations("link", offset, offset).firstOrNull()?.let { onFileLink?.invoke(it.item) }
        },
    )
}

private fun buildAnnotated(spans: List<MdSpan>, s: MdStyles): AnnotatedString = buildAnnotatedString {
    for (span in spans) {
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
    // 列宽权重按各列最长内容估算（pi-web 用 min-width:100% + 横向滚动；移动端改为自适应换行）
    val weights = remember(block) {
        List(columns) { col ->
            val maxLen = (listOf(block.head.getOrNull(col)) + block.rows.map { it.getOrNull(col) })
                .filterNotNull().maxOfOrNull { it.length } ?: 1
            maxLen.coerceIn(3, 40).toFloat()
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, s.border, RoundedCornerShape(7.dp)),
    ) {
        TableRow(block.head, block.align, weights, s, header = true)
        block.rows.forEachIndexed { index, row ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(s.border))
            TableRow(row, block.align, weights, s, header = false, zebra = index % 2 == 1)
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    aligns: List<MdAlign>,
    weights: List<Float>,
    s: MdStyles,
    header: Boolean,
    zebra: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                when {
                    header -> s.panel
                    zebra -> s.subtle
                    else -> Color.Transparent
                },
            ),
    ) {
        weights.forEachIndexed { index, weight ->
            val cellText = cells.getOrNull(index) ?: ""
            val align = aligns.getOrNull(index) ?: MdAlign.LEFT
            val style = s.base.copy(
                fontSize = 13.sp,
                lineHeight = 1.55.em,
                fontWeight = if (header) FontWeight.W600 else FontWeight.Normal,
                color = if (header) lerp(s.text, s.muted, 0.12f) else s.text,
                textAlign = when (align) {
                    MdAlign.CENTER -> TextAlign.Center
                    MdAlign.RIGHT -> TextAlign.End
                    MdAlign.LEFT -> TextAlign.Start
                },
            )
            val spans = remember(cellText) { parseMdInline(cellText) }
            val annotated = remember(spans) { buildAnnotated(spans, s) }
            Text(
                text = annotated,
                style = style,
                modifier = Modifier.weight(weight).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
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
