package com.pient.app.ui.files

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pient.app.data.ChatState
import com.pient.app.data.FileNode
import com.pient.app.data.SOURCE_TOGGLE_EXTS
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.theme.PientPanel
import com.pient.app.ui.theme.MonoFont

/**
 * 文件页面（P3，设计计划 3.5）：
 * 标签栏（36dp 高；标签宽度随名称自适应、上限 180dp 超长省略，pi-web TabBar 规格 maxWidth:180；
 * 2026-09-03 改：原 weight fill=true 恒撑满上限 = 视觉固定宽，改 fill=false 自适应）；markdown 激活时最右出现
 * 编辑/渲染切换键）+ 文件内容预览区 + 右下 FAB 弹出右侧文件树。
 * 安全边界：可浏览位置走允许根白名单（isPathWithinRoots 单一实现，v1 接入）。
 */
@Composable
fun FilesPanel(chatState: ChatState) {
    val context = LocalContext.current
    var treeOpen by remember { mutableStateOf(false) }

    // 保存当前文件（成败都给 Toast 反馈：浮层外的页面态文字看不见时仍可感知）
    fun save(node: FileNode) {
        val ok = chatState.saveFile(context, node)
        Toast.makeText(
            context,
            if (ok) "已保存 ${node.name}" else "保存失败：${node.name}",
            Toast.LENGTH_SHORT,
        ).show()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            FileTabBar(chatState, onSave = { node -> save(node) })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val tabs = chatState.openTabs
                if (tabs.isEmpty() || chatState.activeTabIndex !in tabs.indices) {
                    // 空态引导
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                    ) {
                        Text("文件内容预览区", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "点右下角文件夹图标打开项目文件树\n文本/代码：高亮 + 等宽 · Markdown：GFM 渲染 · 图片：预览",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else {
                    FileContentView(chatState, tabs[chatState.activeTabIndex])
                }
            }
        }

        // 右下 FAB → 右侧文件树
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(52.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .clickable(onClick = { treeOpen = true }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.FolderOpen, "文件树",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp),
            )
        }

        // 右侧文件树浮层（同窗口）：遮罩淡入 + 面板从右滑入，两层独立动画同 tween(300ms)
        // 逐帧同步（聊天页侧栏同款拆层：原同盒 slide 时遮罩随盒从右推出，动画前半程
        // 左侧屏幕无遮罩）。★ scrim 必须全屏铺底——面板左侧圆角弧裁掉的三角区露出的
        // 是 scrim 压暗后的底层内容；若 scrim 只铺到面板左缘（旧 Row 并排结构），
        // 圆角旁会有未压暗的「亮缝」（2026-09-02 用户反馈）。
        AnimatedVisibility(
            visible = treeOpen,
            enter = fadeIn(tween(durationMillis = 300)),
            exit = fadeOut(tween(durationMillis = 300)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim)
                    .clickable(onClick = { treeOpen = false }),
            )
        }
        AnimatedVisibility(
            visible = treeOpen,
            // 从右水平滑入/滑出（聊天页侧栏水平滑出模式的镜像转场）
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 300),
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 300),
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                FileTreePanel(
                    chatState,
                    onClose = { treeOpen = false },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }

        // 关闭未保存文件的确认弹窗（页根浮层：scrim 覆盖标签栏 + 预览区；
        // 文案/按钮结构对齐 Operit：[取消][不保存] 保存）
        val closingIndex = chatState.closingTabIndex
        val closingNode = closingIndex?.let { chatState.openTabs.getOrNull(it) }
        if (closingNode != null && closingIndex != null) {
            PientDialog(
                title = "保存更改？",
                onDismiss = { chatState.closingTabIndex = null },
                confirmText = "保存",
                onConfirm = {
                    val ok = chatState.saveFile(context, closingNode)
                    Toast.makeText(
                        context,
                        if (ok) "已保存 ${closingNode.name}" else "保存失败：${closingNode.name}",
                        Toast.LENGTH_SHORT,
                    ).show()
                    chatState.closingTabIndex = null
                    if (ok) chatState.closeTab(closingIndex)   // 保存失败不关标签，避免改动丢失
                },
                showClose = false,   // 全局原则：带取消按钮的确认弹窗不显示右上角 ×
                extraActionText = "不保存",
                onExtraAction = {
                    chatState.closingTabIndex = null
                    chatState.closeTab(closingIndex)
                },
            ) {
                Text(
                    "文件 ${closingNode.name} 已被修改，是否保存更改？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

// ───────────────────────────── 标签栏 ─────────────────────────────

@Composable
private fun FileTabBar(chatState: ChatState, onSave: (FileNode) -> Unit) {
    val active = chatState.openTabs.getOrNull(chatState.activeTabIndex)
    PientPanel(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        shape = RoundedCornerShape(0.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
        ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            chatState.openTabs.forEachIndexed { i, node ->
                val sel = i == chatState.activeTabIndex
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .widthIn(max = 180.dp)
                        .height(36.dp)
                        .background(
                            if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            RoundedCornerShape(9.dp),
                        )
                        .border(
                            1.dp,
                            if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(9.dp),
                        )
                        .clickable(onClick = { chatState.activeTabIndex = i })
                        .padding(horizontal = 8.dp),
                ) {
                    Icon(
                        fileIcon(node.ext), null,
                        tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        node.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // weight(1f, fill=false)：名称取内容宽（标签随名称长短自适应），
                        // 超长时被 180dp 上限钳制收缩省略，行尾 × 关闭键恒可见
                        // （2026-09-03：原 weight 默认 fill=true 会把标签恒撑满 180dp = 视觉上固定宽度）
                        modifier = Modifier.weight(1f, fill = false).padding(start = 4.dp),
                    )
                    // 行尾键：未保存时由 × 变实心圆点（Operit VSCodeTab 同款「一键两位图」），
                    // 点击仍是关闭——未保存会先弹确认（requestCloseTab）
                    val unsaved = chatState.isUnsaved(node)
                    val tailTint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(14.dp)
                            .clickable(onClick = { chatState.requestCloseTab(i) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (unsaved) {
                            Icon(
                                Icons.Filled.FiberManualRecord, "未保存",
                                tint = tailTint.copy(alpha = 0.9f),
                                modifier = Modifier.size(8.dp),
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Close, "关闭",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
        // 未保存改动的当前文件 → 保存键（Operit 工具栏同款：仅存在未保存改动时出现）
        if (active != null && chatState.isUnsaved(active)) {
            Icon(
                Icons.Outlined.Save, "保存",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(18.dp)
                    .clickable(onClick = { onSave(active) }),
            )
        }
        // markdown / html 激活时：标签栏最右出现「渲染/源码」切换键
        if (active != null && active.ext in SOURCE_TOGGLE_EXTS) {
            Icon(
                if (chatState.sourceEditMode) Icons.Outlined.Visibility else Icons.Outlined.Edit,
                if (chatState.sourceEditMode) "渲染模式" else "编辑模式",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(18.dp)
                    .clickable(onClick = { chatState.sourceEditMode = !chatState.sourceEditMode }),
            )
        }
        }
    }
}

/** 按扩展名区分文件图标（细线 Outlined 风格） */
fun fileIcon(ext: String): ImageVector = when (ext) {
    "md", "txt" -> Icons.Outlined.InsertDriveFile
    "kt", "kts", "java", "xml", "gradle", "py", "js", "ts" -> Icons.Outlined.InsertDriveFile
    "png", "jpg", "jpeg", "webp", "gif" -> Icons.Outlined.InsertDriveFile
    else -> Icons.Outlined.InsertDriveFile
}
