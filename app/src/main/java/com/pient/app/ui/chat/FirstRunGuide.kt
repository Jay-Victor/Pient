package com.pient.app.ui.chat

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pient.app.R
import com.pient.app.data.ChatState
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.theme.PientPanel
import java.io.File

/**
 * 聊天页首次引导（2026-09-08 用户定：移除 mock 后初次进入无项目，2026-09-08 晚迭代为
 * 双步骤清单——两个条件任一未满足即显示引导，已完成步骤打勾提示，两者齐备才进入聊天）：
 * ① 创建项目（新建文件夹；2026-09-14 用户拍板：移除 SAF「选择本地文件夹」）；
 * ② 配置 AI 模型：跳转服务商与模型配置页，「测试连接」成功后标记完成。
 */
@Composable
fun FirstRunGuide(
    chatState: ChatState,
    onConfigureAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var createDialogOpen by remember { mutableStateOf(false) }
    val projectDone = chatState.currentProject != null
    val aiDone = chatState.aiConfigured
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.pient_logo),
                    contentDescription = null,
                    // 与引导页欢迎页同比例：logo 填满圆形底（80/80，2026-09-08 用户：logo 偏小放大）
                    modifier = Modifier.size(80.dp),
                )
            }
            Text(
                "开始使用 Pient",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "完成以下步骤后即可开始对话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            GuideActionCard(
                icon = Icons.Outlined.CreateNewFolder,
                title = "创建项目",
                desc = when {
                    projectDone -> "已绑定项目：${chatState.currentProject}"
                    else -> "新建项目文件夹（应用私有目录 Projects/ 下）"
                },
                done = projectDone,
                onClick = { createDialogOpen = true },
                modifier = Modifier.padding(top = 28.dp),
            )
            GuideActionCard(
                icon = Icons.Outlined.Tune,
                title = "配置 AI 模型",
                desc = if (aiDone) "已通过连接测试" else "接入服务商与模型，测试连接成功后即可对话",
                done = aiDone,
                onClick = onConfigureAi,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
    if (createDialogOpen) {
        CreateProjectDialog(
            chatState = chatState,
            onDismiss = { createDialogOpen = false },
        )
    }
}

/** 引导步骤卡：图标 + 标题/描述 + 完成态 ✓（主色）或右箭头；整卡可点 */
@Composable
private fun GuideActionCard(
    icon: ImageVector,
    title: String,
    desc: String,
    done: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PientPanel(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                icon, null,
                tint = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (done) {
                Icon(
                    Icons.Outlined.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    Icons.Outlined.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * 创建项目弹窗（2026-09-08 新增，聊天页引导入口；与侧边栏新建项目同语义）：
 * 输入名称 → 应用私有目录 Projects/ 下真实创建（可选项目类型模板）
 * 目录选择器绑定现有文件夹（tree URI 持久化授权）。
 */
@Composable
private fun CreateProjectDialog(
    chatState: ChatState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    val confirmEnabled = name.isNotBlank() && !name.contains('/') &&
        chatState.projects.none { it.name == name.trim() }


    Box(Modifier.fillMaxSize()) {
        PientDialog(
            title = "创建项目",
            onDismiss = onDismiss,
            confirmText = "创建",
            showClose = false,
            confirmEnabled = confirmEnabled,
            onConfirm = {
                val dir = File(context.filesDir, "Projects/${name.trim()}")
                if (dir.exists() || dir.mkdirs()) {
                    chatState.addProject(name.trim(), dir.absolutePath)
                    onDismiss()
                } else {
                    Toast.makeText(context, "文件夹创建失败", Toast.LENGTH_SHORT).show()
                }
            },
        ) {
            Column(Modifier.padding(top = 12.dp)) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                )
                Text(
                    "将在应用私有目录 Projects/ 下创建该文件夹",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
