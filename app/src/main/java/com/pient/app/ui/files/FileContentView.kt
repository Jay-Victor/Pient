package com.pient.app.ui.files

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.pient.app.data.ChatState
import com.pient.app.data.FileNode
import com.pient.app.data.ProjectFiles
import com.pient.app.ui.components.MarkdownText
import com.pient.app.ui.theme.MonoFont
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 文件内容预览区（设计计划 3.5；2026-09-10 按类型分流 + 编辑，参照 Operit 工作区）：
 * - 视频/音频：ExoPlayer 播放（video：黑底 + FIT 播放器，音频：居中播放器条）
 * - 图片：真实 bitmap，可双指缩放/拖动/双击复位（Operit WorkspaceImagePreview 同款）
 * - Markdown：渲染模式（GFM）/ 编辑模式（可直接编辑）
 * - txt：等宽纯文本，**无行号**，可编辑
 * - 其他文本/代码：**行号槽随类型自动显示**（Operit CanvasCodeEditorView 规格），可编辑
 * - 文档/压缩包/安装包：二进制，走「暂不支持预览」提示（无法按文本编辑）
 */
@Composable
fun FileContentView(chatState: ChatState, node: FileNode) {
    val context = LocalContext.current
    val isMd = node.ext == "md"
    val isImage = node.ext in PREVIEW_IMAGE_EXTS
    val isVideo = node.ext in PREVIEW_VIDEO_EXTS
    val isAudio = node.ext in PREVIEW_AUDIO_EXTS
    val isMedia = isVideo || isAudio
    val key = chatState.fileKey(node)

    // ── 真实文件读取（source 节点；mock 节点直接用 node.content）──
    var textContent by remember(node.source) { mutableStateOf(node.content) }
    var tooLarge by remember(node.source) { mutableStateOf(false) }
    var unreadable by remember(node.source) { mutableStateOf(false) }
    var bitmap by remember(node.source) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(node.source) {
        if (node.source == null || node.isDir) {
            node.content?.let { chatState.seedDraft(node, it) }
            return@LaunchedEffect
        }
        if (isImage) {
            bitmap = ProjectFiles.readBitmap(context, node)
            return@LaunchedEffect
        }
        if (isMedia) return@LaunchedEffect            // 媒体直接交给播放器，不做文本读取
        val t = ProjectFiles.readText(context, node)
        if (t == null) {
            if (node.size > 2L * 1024 * 1024) tooLarge = true else unreadable = true
        } else {
            textContent = t
            chatState.seedDraft(node, t)              // 读取完成播种编辑缓冲（已有改动不覆盖）
        }
    }

    when {
        // 视频 / 音频播放（ExoPlayer）
        isMedia && node.source != null -> MediaPreview(node, isVideo)
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
        // 文本/代码：可编辑；行号槽仅非纯文本显示（txt 无行号）
        else -> EditableTextView(
            text = chatState.fileDrafts[key] ?: textContent ?: "",
            onValueChange = { chatState.editDraft(node, it) },
            showLineNumbers = node.ext !in PLAIN_TEXT_EXTS,
        )
    }
}

// ───────────────────────────── 视频 / 音频播放 ─────────────────────────────

/**
 * 媒体播放（Operit WorkspaceManager 的音视频分支同构）：
 * - 视频：黑底 + 播放器 fillMaxWidth + heightIn(180dp..420dp) + RESIZE_MODE_FIT
 * - 音频：居中播放器条（fillMaxWidth），无黑底
 * - autoPlay = false（由用户点播放键起播），离开组合即释放播放器
 */
@Composable
private fun MediaPreview(node: FileNode, isVideo: Boolean) {
    val context = LocalContext.current
    val uri = remember(node.source) { ProjectFiles.mediaUri(node) }
    if (uri == null) {
        PaddedHint("无法播放：文件位置不可用")
        return
    }
    val player = remember(uri) { ExoPlayer.Builder(context).build() }
    LaunchedEffect(uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = false
    }
    DisposableEffect(player) {
        onDispose {
            runCatching {
                player.stop()
                player.clearMediaItems()
                player.release()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (isVideo) Modifier.background(Color.Black) else Modifier)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    if (isVideo) resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view -> if (view.player !== player) view.player = player },
            modifier = if (isVideo) {
                Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp)
            } else {
                Modifier.fillMaxWidth()
            },
        )
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

// ───────────────────────────── 文本 / 代码（可编辑） ─────────────────────────────

/** 代码文本排版（等宽；行号槽内字号 = 该字号 × 0.82，见 EditableTextView） */
private val CodeFontSize = 12.sp
private val CodeLineHeight = 20.sp

/** 真实解码预览的图片扩展名（Operit workspaceMimeTypeForPath 的 image 分支口径） */
private val PREVIEW_IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif")

/** 视频扩展名（Operit workspaceMimeTypeForPath 的 video 分支） */
private val PREVIEW_VIDEO_EXTS = setOf("webm", "mp4", "m4v", "mov", "mkv", "avi", "3gp")

/** 音频扩展名（Operit workspaceMimeTypeForPath 的 audio 分支） */
private val PREVIEW_AUDIO_EXTS = setOf("mp3", "wav", "m4a", "aac", "ogg", "opus", "flac")

/** 无行号的纯文本（用户 2026-09-10 定：txt 侧边不加行号） */
private val PLAIN_TEXT_EXTS = setOf("txt", "text")

/**
 * 文本编辑区（所有非媒体/非图片文本文件的唯一入口）：等宽正文 + 可选行号槽，输入即改缓冲。
 * 行号槽逐项对齐 Operit CanvasCodeEditorView（drawEditor / gutterWidth / drawLineNumber）：
 * - 槽底色 = surfaceContainer（Operit gutterBackground：相对代码底微偏离）、铺满视口高度、无分隔线
 * - 槽宽 = max(行号文字宽 + 前后内边距, 24dp)；内边距按位数取 1→5dp / 2→6dp / 3→7dp / 其余 8dp
 * - 行号字号 = 正文字号 × 0.82、右对齐（右缘距槽右缘 = 尾内边距）、颜色 onSurfaceVariant
 * - 正文左缘紧贴槽右缘（间距 = 槽尾内边距）、上下留白 10dp（无行号时 8dp）、正文右侧留白 12dp
 * - 行号按「逻辑行」编号：折行的续行不给号，行号与首行同基线（取文本排版的行基线定位）
 */
@Composable
private fun EditableTextView(
    text: String,
    onValueChange: (String) -> Unit,
    showLineNumbers: Boolean,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val codeStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = MonoFont,
        fontSize = CodeFontSize,
        lineHeight = CodeLineHeight,
        color = MaterialTheme.colorScheme.onBackground,
    )
    val numberStyle = codeStyle.copy(
        fontSize = CodeFontSize * 0.82f,
        textAlign = TextAlign.End,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val scroll = rememberScrollState()

    // 行号槽几何（位数 → 内边距；槽宽下限 24dp）
    val logicalLines = remember(text) { text.count { it == '\n' } + 1 }
    val digits = logicalLines.toString().length.coerceAtLeast(1)
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
    val leadingPx = with(density) { gutterPadding.toPx() }
    val numberBoxPx = with(density) { (gutterWidth - gutterPadding * 2).toPx().roundToInt() }

    Box(Modifier.fillMaxSize()) {
        // 行号槽底色（铺满视口的色块；行号绘制在其上的滚动内容层）
        if (showLineNumbers) {
            Box(Modifier.fillMaxHeight().width(gutterWidth).background(MaterialTheme.colorScheme.surfaceContainer))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .imePadding()
                .padding(vertical = if (showLineNumbers) 10.dp else 8.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                val layout = textLayout
                if (showLineNumbers && layout != null) {
                    Canvas(Modifier.matchParentSize()) {
                        drawEditorLineNumbers(
                            layout = layout,
                            text = text,
                            measurer = measurer,
                            numberStyle = numberStyle,
                            leadingPx = leadingPx,
                            numberBoxPx = numberBoxPx,
                        )
                    }
                }
                BasicTextField(
                    value = text,
                    onValueChange = onValueChange,
                    textStyle = codeStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                    onTextLayout = { textLayout = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (showLineNumbers) gutterWidth else 14.dp,
                            end = if (showLineNumbers) 12.dp else 14.dp,
                        ),
                )
            }
        }
    }
}

/**
 * 行号绘制：遍历文本排版的视觉行，仅逻辑行首（行首前一字符为换行 / 首行）给号；
 * 行号右对齐于槽内（布局宽 = 槽宽 − 前后内边距），基线对齐该行正文（Operit drawLineNumber 同款定位）。
 */
private fun DrawScope.drawEditorLineNumbers(
    layout: TextLayoutResult,
    text: String,
    measurer: TextMeasurer,
    numberStyle: TextStyle,
    leadingPx: Float,
    numberBoxPx: Int,
) {
    var logical = 0
    for (i in 0 until layout.lineCount) {
        val start = layout.getLineStart(i)
        if (i > 0 && (start <= 0 || text.getOrNull(start - 1) != '\n')) continue  // 折行续行不给号
        logical++
        val numberLayout = measurer.measure(
            text = AnnotatedString(logical.toString()),
            style = numberStyle,
            constraints = Constraints(minWidth = numberBoxPx, maxWidth = numberBoxPx),
        )
        drawText(
            textLayoutResult = numberLayout,
            topLeft = Offset(
                x = leadingPx,
                y = layout.getLineBaseline(i) - numberLayout.getLineBaseline(0),
            ),
        )
    }
}

// ───────────────────────────── 占位 / 容器 ─────────────────────────────

/** 带页面留白的滚动容器（Markdown 渲染共用） */
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
