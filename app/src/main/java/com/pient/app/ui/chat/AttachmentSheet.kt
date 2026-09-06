package com.pient.app.ui.chat

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.data.Attachment
import com.pient.app.data.AttachmentKind
import com.pient.app.data.ChatState
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.theme.PientPanel

/**
 * "+" 附件菜单（2026-08-28 重设计，参照 Hermes 桌面端 composer context-menu）：
 * - 位置与模型选择器浮层一致：贴屏幕右侧（右距屏 6dp）、底部锚定到输入栏上缘、
 *   同宽 268.8dp、16dp 圆角 PientPanel，点外关闭（无 scrim）。
 * - 行式菜单（Hermes DropdownMenuItem 规格）：顶部小标签「附加」→
 *   照片 / 拍照 / 文件 / 文件夹 / URL（图标 16dp + 标题 13sp，行高 34dp）→
 *   分隔线 → 底部提示「提示：输入 @ 以内联引用文件。」（Hermes tipPre+tipPost）。
 * - 原 Operit 宫格三件套（上传图片/上传文件/拍照）与拖拽手柄已替换。
 * UI 原型：各选项以 mock 附件 chip 上屏（照片/拍照=IMAGE、文件=FILE、
 * 文件夹=FOLDER、URL=URL）；真实系统选择器/相机/URL 对话框 v1 接入。
 */
@Composable
fun AttachmentSheet(
    chatState: ChatState,
    onClose: () -> Unit,
    onOpenUrlDialog: () -> Unit = {}, // URL 项 → 打开 URL 输入弹窗（Hermes url-dialog 同款）
    bottomOffset: Dp = 8.dp, // 弹窗底部到屏幕底的距离（与模型选择器同口径）
    modifier: Modifier = Modifier,
) {
    var counter by remember { mutableIntStateOf(0) }

    fun mockAttach(name: String, kind: AttachmentKind) {
        counter++
        chatState.attachments += Attachment(name, kind)
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
                mockAttach("photo_$counter.jpg", AttachmentKind.IMAGE)
            }
            AttachMenuItem(Icons.Outlined.PhotoCamera, "拍照") {
                mockAttach("IMG_$counter.jpg", AttachmentKind.IMAGE)
            }
            AttachMenuItem(Icons.Outlined.AttachFile, "文件") {
                mockAttach("logcat_$counter.txt", AttachmentKind.FILE)
            }
            AttachMenuItem(Icons.Outlined.Folder, "文件夹") {
                mockAttach("project_src_$counter/", AttachmentKind.FOLDER)
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
