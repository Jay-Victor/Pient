package com.pient.app.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.pient.app.data.Attachment
import com.pient.app.data.AttachmentKind
import com.pient.app.data.ChatState
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.theme.PientPanel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "+" 附件菜单（2026-08-28 重设计，参照 Hermes 桌面端 composer context-menu；
 * 2026-09-09 真实化）：
 * - 位置与模型选择器浮层一致：贴屏幕右侧（右距屏 6dp）、底部锚定到输入栏上缘、
 *   同宽 268.8dp、16dp 圆角 PientPanel，点外关闭（无 scrim）。
 * - 行式菜单（Hermes DropdownMenuItem 规格）：顶部小标签「附加」→
 *   照片 / 拍照 / 文件 / 文件夹 / URL（图标 16dp + 标题 13sp，行高 34dp）→
 *   分隔线 → 底部提示「提示：输入 @ 以内联引用文件。」（Hermes tipPre+tipPost）。
 * - 2026-09-09：照片 = 系统照片选择器（多选，PickMultipleVisualMedia）；拍照 = 相机
 *   （FileProvider 暂存后落盘）；文件 = 系统文件选择器；文件夹 = SAF 目录选择器；
 *   选中的照片/文件复制到应用私有目录 filesDir/attachments/ 持久保存（Attachment.path）。
 */
@Composable
fun AttachmentSheet(
    chatState: ChatState,
    onClose: () -> Unit,
    onOpenUrlDialog: () -> Unit = {}, // URL 项 → 打开 URL 输入弹窗（Hermes url-dialog 同款）
    bottomOffset: Dp = 8.dp, // 弹窗底部到屏幕底的距离（与模型选择器同口径）
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // ── 照片（相册多选；Photo Picker 无需存储权限） ──
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult // 取消
        var added = 0
        uris.forEach { uri ->
            val copied = copyToAttachments(context, uri)
            if (copied != null) {
                chatState.attachments += Attachment(copied.first, AttachmentKind.IMAGE, copied.second)
                added++
            }
        }
        if (added == 0) Toast.makeText(context, "图片读取失败", Toast.LENGTH_SHORT).show()
        onClose()
    }

    // ── 拍照（相机写入 FileProvider 缓存文件 → 复制到附件目录） ──
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok: Boolean ->
        val src = cameraFile
        cameraFile = null
        if (!ok || src == null) return@rememberLauncherForActivityResult
        val name = "IMG_${timestamp()}.jpg"
        val dest = copyFileToAttachments(context, src, name)
        if (dest != null) {
            chatState.attachments += Attachment(name, AttachmentKind.IMAGE, dest.absolutePath)
        } else {
            Toast.makeText(context, "拍照保存失败", Toast.LENGTH_SHORT).show()
        }
        src.delete()
        onClose()
    }

    // ── 文件（系统文件选择器，单个） ──
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult // 取消
        val copied = copyToAttachments(context, uri)
        if (copied != null) {
            chatState.attachments += Attachment(copied.first, AttachmentKind.FILE, copied.second)
        } else {
            Toast.makeText(context, "文件读取失败", Toast.LENGTH_SHORT).show()
        }
        onClose()
    }

    // ── 文件夹（SAF 目录选择器；不复制，记录 tree URI 供后续引用） ──
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult // 取消
        val doc = DocumentFile.fromTreeUri(context, uri)
        val name = doc?.name ?: uri.lastPathSegment ?: "文件夹"
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            // 个别 provider 不支持持久化授权，按本次会话临时授权继续
        }
        chatState.attachments += Attachment(name, AttachmentKind.FOLDER, uri.toString())
        onClose()
    }

    PientPanel(
        modifier = modifier
            .padding(end = 6.dp, bottom = bottomOffset)
            .width(268.8.dp), // 与模型选择器浮层同宽（336dp 的 4/5）
        shape = RoundedCornerShape(16.dp),
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        // 顶部小标签（Hermes attachLabel：小号弱化）
        Text(
            "附加",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(Modifier.padding(top = 6.dp)) {
            AttachMenuItem(Icons.Outlined.Image, "照片") {
                photoPicker.launch(PickVisualMediaRequest())
            }
            AttachMenuItem(Icons.Outlined.PhotoCamera, "拍照") {
                val dir = File(context.cacheDir, "camera")
                dir.mkdirs()
                val f = File(dir, "photo_${System.currentTimeMillis()}.jpg")
                cameraFile = f
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                try {
                    cameraLauncher.launch(uri)
                } catch (e: Exception) {
                    cameraFile = null
                    Toast.makeText(context, "无法调起相机", Toast.LENGTH_SHORT).show()
                }
            }
            AttachMenuItem(Icons.Outlined.AttachFile, "文件") {
                filePicker.launch(arrayOf("*/*"))
            }
            AttachMenuItem(Icons.Outlined.Folder, "文件夹") {
                folderPicker.launch(null)
            }
            AttachMenuItem(Icons.Outlined.Link, "URL") {
                onOpenUrlDialog()
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        // 底部提示（Hermes tipPre + @ + tipPost）
        Text(
            "提示：输入 @ 以内联引用文件。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    }
}

// ───────────────────────── 附件落盘工具 ─────────────────────────

/** 附件目录（应用私有 filesDir/attachments/；照片/拍照/文件复制到这里持久保存） */
private fun attachmentsDir(context: Context): File =
    File(context.filesDir, "attachments").apply { mkdirs() }

private fun timestamp(): String =
    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

/**
 * 把 content Uri 复制到附件目录：返回 (显示名, 绝对路径)；失败 null。
 * 显示名优先取提供方的 DISPLAY_NAME，缺失回退 "file_<时间戳>.<扩展名>"。
 */
private fun copyToAttachments(context: Context, uri: Uri): Pair<String, String>? {
    val displayName = queryDisplayName(context, uri)
    val safeName = displayName?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "file_${timestamp()}"
    val f = File(attachmentsDir(context), safeName)
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            f.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        safeName to f.absolutePath
    } catch (e: Exception) {
        null
    }
}

/** 把本地缓存文件复制到附件目录（拍照流程） */
private fun copyFileToAttachments(context: Context, src: File, name: String): File? {
    val dest = File(attachmentsDir(context), name)
    return try {
        src.copyTo(dest, overwrite = true)
    } catch (e: Exception) {
        null
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
    }
} catch (e: Exception) {
    null
}

/**
 * 菜单行（Hermes DropdownMenuItem 规格，与模型选择器 config-row 同款）：
 * min-height 34dp · 图标 16dp（弱化色）+ 标题 13sp · 水平 padding 8dp。
 */
@Composable
private fun AttachMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * URL 输入弹窗（2026-08-28 新增，参照 Hermes 桌面端 url-dialog.tsx）：
 * 标题「附加 URL」；描述「Pient 将抓取该页面并作为本回合的上下文。」；
 * 输入框（placeholder https://example.com/post、URL 键盘）；输入非空且非
 * http(s):// 开头时提示「请包含完整 URL，例如 https://…」；
 * 确认键「附加」仅合法 URL 可用（Hermes looksLikeUrl 同规则）。
 */
@Composable
fun UrlDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val trimmed = text.trim()
    val looksLikeUrl = trimmed.startsWith("http://") || trimmed.startsWith("https://")

    PientDialog(
        title = "附加 URL",
        onDismiss = onDismiss,
        confirmText = "附加",
        confirmEnabled = looksLikeUrl,
        onConfirm = { onConfirm(trimmed) },
    ) {
        Column {
            Text(
                "Pient 将抓取该页面并作为本回合的上下文。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("https://example.com/post") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            if (trimmed.isNotEmpty() && !looksLikeUrl) {
                Text(
                    "请包含完整 URL，例如 https://…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
