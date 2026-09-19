package com.pient.app.ui.chat

import com.pient.app.data.i18n.L
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
import androidx.compose.material.icons.outlined.Terminal
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
 * 聊天页首次引导（两个条件任一未满足即显示引导，已完成步骤打勾提示，两者齐备才进入聊天）：
 * ① 创建项目（新建文件夹）；
 * ② 配置 AI 模型：跳转服务商与模型配置页，「测试连接」成功后标记完成；
 * ③ 配置 Ubuntu 环境：pi 本体随 Pient 预置，但**跑 pi 的 Node 环境不随包**，
 *    首次要在「环境配置」里装一次（node + rg/fd）；这一项以 **pi 通道就绪** 为判据 ——
 *    因为它正是「环境配好了、pi 真的起得来」的唯一可信信号（与聊天页的就绪条同源）。
 */
@Composable
fun FirstRunGuide(
    chatState: ChatState,
    onConfigureAi: () -> Unit,
    onOpenEnvSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var createDialogOpen by remember { mutableStateOf(false) }
    val projectDone = chatState.currentProject != null
    val aiDone = chatState.aiConfigured
    val envDone = chatState.piReadiness == ChatState.PiReadiness.Ready
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
                    // 与引导页欢迎页同比例：logo 填满圆形底（80/80）
                    modifier = Modifier.size(80.dp),
                )
            }
            Text(
                L.chat.getStarted,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                L.chat.firstRunSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            GuideActionCard(
                icon = Icons.Outlined.CreateNewFolder,
                title = L.chat.createProject,
                desc = when {
                    projectDone -> L.chat.projectBound(chatState.currentProject)
                    else -> L.chat.createProjectDesc
                },
                done = projectDone,
                onClick = { createDialogOpen = true },
                modifier = Modifier.padding(top = 28.dp),
            )
            GuideActionCard(
                icon = Icons.Outlined.Tune,
                title = L.chat.configureAi,
                desc = if (aiDone) L.chat.aiReady else L.chat.configureAiDesc,
                done = aiDone,
                onClick = onConfigureAi,
                modifier = Modifier.padding(top = 10.dp),
            )
            GuideActionCard(
                icon = Icons.Outlined.Terminal,
                title = L.chat.configureEnv,
                // 三态文案：
                // - 已完成 → 打勾 + 一句结论；
                // - **上一步（AI 模型）还没配时不说"检测中/未就绪"**：那时 pi 根本没法起，
                //   探针给的原因会是「没有可用的服务商 / 模型」—— 那是第二步的事，摆在环境这一步会串味；
                // - 其余 → 探针给的真实原因（rootfs 未解 / 缺 Node / 通道起不来…）。
                desc = when {
                    envDone -> L.chat.envReady
                    !aiDone -> L.chat.envInstallNode
                    chatState.piUnreadyReason.isNotBlank() -> chatState.piUnreadyReason
                    else -> L.chat.envInstallNode
                },
                done = envDone,
                onClick = onOpenEnvSetup,
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
 * 创建项目弹窗（聊天页引导入口；与侧边栏新建项目同语义）：
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
            title = L.chat.createProject,
            onDismiss = onDismiss,
            confirmText = L.common.create,
            showClose = false,
            confirmEnabled = confirmEnabled,
            onConfirm = {
                val dir = File(context.filesDir, "Projects/${name.trim()}")
                if (dir.exists() || dir.mkdirs()) {
                    chatState.addProject(name.trim(), dir.absolutePath)
                    onDismiss()
                } else {
                    Toast.makeText(context, L.chat.folderCreateFailed, Toast.LENGTH_SHORT).show()
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
                    L.chat.createProjectFolderHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
