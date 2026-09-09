package com.pient.app.ui.files

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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.InsertDriveFile
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pient.app.data.ChatState
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
    var treeOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            FileTabBar(chatState)
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
    }
}

// ───────────────────────────── 标签栏 ─────────────────────────────

@Composable
private fun FileTabBar(chatState: ChatState) {
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
                    Icon(
                        Icons.Outlined.Close, "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(14.dp)
                            .clickable(onClick = { chatState.closeTab(i) }),
                    )
                }
            }
        }
        // markdown 激活时：标签栏最右出现编辑/渲染切换键
        if (active != null && active.ext == "md") {
            Icon(
                if (chatState.mdEditMode) Icons.Outlined.Visibility else Icons.Outlined.Edit,
                if (chatState.mdEditMode) "渲染模式" else "编辑模式",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(18.dp)
                    .clickable(onClick = { chatState.mdEditMode = !chatState.mdEditMode }),
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
