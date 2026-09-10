package com.pient.app.ui.files

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.data.ChatState
import com.pient.app.data.FileNode
import com.pient.app.data.ProjectFiles
import com.pient.app.ui.components.MarkdownText
import com.pient.app.ui.theme.MonoFont
import kotlin.math.max

/**
 * 文件内容预览区（设计计划 3.5；2026-09-10 按类型分流重做）：
 * - 图片：真实 bitmap，可双指缩放/拖动/双击复位（Operit WorkspaceImagePreview 同款）
 * - Markdown：渲染模式（GFM）/ 编辑模式（代码视图 + 行号）
 * - txt：等宽纯文本，**无行号**
 * - 其他文本/代码文件：**行号槽随类型自动显示**（Operit CanvasCodeEditorView 的行号槽规格；
 *   原「行号：开/关」开关已移除——行号由文件类型决定，不设开关）
 * - 文档/音视频/压缩包/安装包：二进制，走「暂不支持预览」提示（不经代码视图，故无行号）
 */
@Composable
fun FileContentView(chatState: ChatState, node: FileNode) {
    val context = LocalContext.current
    val isMd = node.ext == "md"
    val isImage = node.ext in PREVIEW_IMAGE_EXTS

    // ── 真实文件读取（source 节点；mock 节点直接用 node.content）──
    var textContent by remember(node.source) { mutableStateOf(node.content) }
    var tooLarge by remember(node.source) { mutableStateOf(false) }
    var unreadable by remember(node.source) { mutableStateOf(false) }
    var bitmap by remember(node.source) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(node.source) {
        if (node.source == null || node.isDir) return@LaunchedEffect
        if (isImage) {
            bitmap = ProjectFiles.readBitmap(context, node)
        } else {
            val t = ProjectFiles.readText(context, node)
            if (t == null) {
                if (node.size > 2L * 1024 * 1024) tooLarge = true else unreadable = true
            } else {
                textContent = t
            }
        }
    }

    when {
        // 真实图片预览（可缩放/拖动）
        isImage && node.source != null -> ZoomableImage(bitmap, node.name)
        tooLarge -> PaddedHint("文件超过 2MB，暂不支持预览")
        unreadable -> PaddedHint("二进制文件，暂不支持预览")
        node.imageHint != null -> ImagePlaceholder(node)
        isMd && !chatState.mdEditMode -> PaddedScroll {
            MarkdownText(
                textContent ?: "",
                onFileLink = { path -> onLocalFileLink(chatState, path) },
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            )
        }
        // 纯文本：等宽排版、无行号（用户 2026-09-10 定）
        node.ext in PLAIN_TEXT_EXTS -> PaddedScroll { PlainTextView(textContent) }
        // 其他文本/代码：行号槽
        else -> CodeView(textContent)
    }
}

// ───────────────────────────── 图片预览（缩放/拖动） ─────────────────────────────

/** Operit WorkspaceImagePreview 同款缩放区间与双击倍率 */
private const val IMAGE_MIN_SCALE = 1f
private const val IMAGE_DOUBLE_TAP_SCALE = 2.5f
private const val IMAGE_MAX_SCALE = 5f

/**
 * 图片预览（黑底 + Fit 居中）：双指缩放 1f～5f、拖动平移（按视口夹取边界）、双击在 2.5f/1f 间切换。
 * 数值与判定公式逐项对齐 Operit WorkspaceImagePreview（clampImageOffset / doubleTapOffset）。
 */
@Composable
private fun ZoomableImage(bitmap: Bitmap?, name: String) {
    if (bitmap == null) {
        PaddedHint("图片加载失败或格式不支持")
        return
    }
    var scale by remember(bitmap) { mutableStateOf(IMAGE_MIN_SCALE) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewportSize = it },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                // 双击：已放大 → 复位；未放大 → 以点击处为锚放大到 2.5f
                .pointerInput(bitmap) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            if (scale > IMAGE_MIN_SCALE) {
                                scale = IMAGE_MIN_SCALE
                                offset = Offset.Zero
                            } else {
                                scale = IMAGE_DOUBLE_TAP_SCALE
                                offset = doubleTapOffset(tapOffset, viewportSize, IMAGE_DOUBLE_TAP_SCALE)
                            }
                        },
                    )
                }
                // 双指缩放 + 单指拖动（未放大时偏移恒为 0，平移自动失效）
                .pointerInput(bitmap) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(IMAGE_MIN_SCALE, IMAGE_MAX_SCALE)
                        scale = nextScale
                        offset = clampImageOffset(
                            rawOffset = if (nextScale <= IMAGE_MIN_SCALE) Offset.Zero else offset + pan,
                            viewportSize = viewportSize,
                            scale = nextScale,
                        )
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

/** 平移边界夹取：放大后允许的位移 = 视口尺寸 ×（scale − 1）/ 2 */
private fun clampImageOffset(rawOffset: Offset, viewportSize: IntSize, scale: Float): Offset {
    if (scale <= IMAGE_MIN_SCALE || viewportSize == IntSize.Zero) return Offset.Zero
    val maxX = viewportSize.width * (scale - 1f) / 2f
    val maxY = viewportSize.height * (scale - 1f) / 2f
    return Offset(
        x = rawOffset.x.coerceIn(-maxX, maxX),
        y = rawOffset.y.coerceIn(-maxY, maxY),
    )
}

/** 双击锚定：让点击处在放大后仍位于原屏幕位置 */
private fun doubleTapOffset(tapOffset: Offset, viewportSize: IntSize, scale: Float): Offset {
    if (viewportSize == IntSize.Zero) return Offset.Zero
    return clampImageOffset(
        rawOffset = Offset(
            x = (viewportSize.width / 2f - tapOffset.x) * (scale - 1f),
            y = (viewportSize.height / 2f - tapOffset.y) * (scale - 1f),
        ),
        viewportSize = viewportSize,
        scale = scale,
    )
}

// ───────────────────────────── 文本 / 代码预览 ─────────────────────────────

/** 代码文本排版（等宽；行号槽内字号 = 该字号 × 0.82，见 CodeView） */
private val CodeFontSize = 12.sp
private val CodeLineHeight = 20.sp

/** 真实解码预览的图片扩展名 */
private val PREVIEW_IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

/** 无行号的纯文本（用户 2026-09-10 定：txt 侧边不加行号） */
private val PLAIN_TEXT_EXTS = setOf("txt", "text")

/**
 * 代码/结构化文本预览：左侧行号槽 + 等宽正文。
 * 行号槽逐项对齐 Operit CanvasCodeEditorView（drawEditor / gutterWidth / drawLineNumber）：
 * - 槽底色 = surfaceContainer（Operit gutterBackground：相对代码底微偏离）、铺满视口高度、无分隔线
 * - 槽宽 = max(行号文字宽 + 前后内边距, 24dp)；内边距按位数取 1→5dp / 2→6dp / 3→7dp / 其余 8dp
 * - 行号字号 = 正文字号 × 0.82、右对齐、颜色 onSurfaceVariant；正文左缘紧贴槽右缘（间距 = 槽尾内边距）
 * - 上下留白 10dp、正文右侧留白 12dp（Operit verticalPadding/horizontalPadding）
 * 长行折行时槽随该行高度延伸，行号对齐该行首行（槽底由底色块铺满，不出现断口）。
 */
@Composable
private fun CodeView(content: String?) {
    val lines = (content ?: "").lines()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val codeStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = MonoFont,
        fontSize = CodeFontSize,
        lineHeight = CodeLineHeight,
    )
    val numberStyle = codeStyle.copy(fontSize = CodeFontSize * 0.82f)

    // 位数（Operit EditorDocument.lineDigits = max(1, 行数位数)）
    val digits = lines.size.toString().length.coerceAtLeast(1)
    val gutterPadding = when (digits) {
        1 -> 5.dp
        2 -> 6.dp
        3 -> 7.dp
        else -> 8.dp
    }
    val numberWidth = with(density) {
        measurer.measure(AnnotatedString("9".repeat(digits)), numberStyle).size.width.toDp()
    }
    val gutterWidth = maxOf(numberWidth + gutterPadding * 2, 24.dp)

    Box(Modifier.fillMaxSize()) {
        // 行号槽（铺满视口的底色块；正文层之上再叠行号）
        Box(
            Modifier
                .fillMaxHeight()
                .width(gutterWidth)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
        SelectionContainer {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
            ) {
                lines.forEachIndexed { i, line ->
                    Row(Modifier.fillMaxWidth()) {
                        // alignByBaseline：行号与正文首行同基线（Operit drawLineNumber 用正文 paint 的
                        // baseline 定位；字号不同时不显式对齐会偏高 ~2dp）
                        Text(
                            (i + 1).toString(),
                            style = numberStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            modifier = Modifier
                                .width(gutterWidth)
                                .padding(horizontal = gutterPadding)
                                .alignByBaseline(),
                        )
                        Text(
                            line.ifEmpty { " " },
                            style = codeStyle,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                                .alignByBaseline(),
                        )
                    }
                }
            }
        }
    }
}

/** 纯文本预览（无行号；长按可选择复制） */
@Composable
private fun PlainTextView(content: String?) {
    SelectionContainer(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            content ?: "",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = MonoFont,
                fontSize = CodeFontSize,
                lineHeight = CodeLineHeight,
            ),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

// ───────────────────────────── 占位 / 容器 ─────────────────────────────

/** 带页面留白的滚动容器（Markdown 渲染 / 纯文本共用） */
@Composable
private fun PaddedScroll(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) { content() }
}

/** 提示占位（居中 + 页面留白） */
@Composable
private fun PaddedHint(text: String) {
    Box(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 本地文件链接 → 在当前项目文件树中定位并打开（2026-09-02：真实树；无树直接忽略） */
private fun onLocalFileLink(chatState: ChatState, path: String) {
    val name = path.substringAfterLast('/')
    val tree = chatState.fileTreeRoot ?: return
    val found = findNode(tree, name)
    if (found != null) {
        chatState.openFile(found)
    }
}

private fun findNode(node: FileNode, name: String): FileNode? {
    if (node.name == name) return node
    for (c in node.children) {
        findNode(c, name)?.let { return it }
    }
    return null
}

/** mock 图片占位（保留旧原型演示路径） */
@Composable
private fun ImagePlaceholder(node: FileNode) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            node.imageHint ?: "图片预览占位",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
