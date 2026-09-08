package com.pient.app.ui.files

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.data.ChatState
import com.pient.app.data.FileNode
import com.pient.app.data.ProjectFiles
import com.pient.app.ui.components.MarkdownText
import com.pient.app.ui.theme.MonoFont

/**
 * 文件内容预览区（设计计划 3.5）：
 * - 文本/代码：等宽 + 行号可选（长按选择复制）
 * - Markdown：渲染模式（GFM）/ 编辑模式（首期只读 + 复制）
 * - 图片：真实 bitmap 预览（source 节点）；mock 节点沿用占位
 * - 2026-09-02 真实化：source 非空节点从真实文件系统读取内容
 */
@Composable
fun FileContentView(chatState: ChatState, node: FileNode) {
    val context = LocalContext.current
    val isMd = node.ext == "md"
    val isImage = node.ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

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

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
        // 行号开关（代码 / md 编辑模式）
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(
                "行号：${if (chatState.lineNumbers) "开" else "关"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = { chatState.lineNumbers = !chatState.lineNumbers })
                    .padding(4.dp),
            )
        }

        when {
            // 真实图片预览
            isImage && node.source != null -> {
                val bmp = bitmap
                when {
                    bmp != null -> Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = node.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    )
                    else -> PreviewHint("图片加载失败或格式不支持")
                }
            }
            tooLarge -> PreviewHint("文件超过 2MB，暂不支持预览")
            unreadable -> PreviewHint("二进制文件，暂不支持预览")
            node.imageHint != null -> ImagePlaceholder(node)
            isMd && !chatState.mdEditMode -> MarkdownText(
                textContent ?: "",
                onFileLink = { path -> onLocalFileLink(chatState, path) },
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            )
            else -> CodeOrEditView(textContent, chatState.lineNumbers)
        }
    }
}

/** 提示占位（居中） */
@Composable
private fun PreviewHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

@Composable
private fun CodeOrEditView(content: String?, lineNumbers: Boolean) {
    val lines = (content ?: "").lines()
    SelectionContainer(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Column {
            lines.forEachIndexed { i, line ->
                Row {
                    if (lineNumbers) {
                        Text(
                            (i + 1).toString().padStart(4) + " │",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = MonoFont,
                                fontSize = 12.sp,
                                lineHeight = 20.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    Text(
                        line.ifEmpty { " " },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = MonoFont,
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
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
