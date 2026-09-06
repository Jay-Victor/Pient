package com.pient.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.R
import com.pient.app.ui.components.DividerLine
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.SectionHeader
import com.pient.app.ui.components.SettingsRow
import kotlinx.coroutines.delay

private const val VERSION = "版本 0.1.0"
private const val GITHUB_USER_URL = "https://github.com/Jay-Victor"
private const val GITHUB_REPO_URL = "https://github.com/Jay-Victor/Pient"
private const val GITEE_REPO_URL = "https://gitee.com/Jay-Victor/Pient"
private const val CONTACT_EMAIL = "18261738221@163.com"

private enum class AboutDialog { UPDATE, LOG }

/**
 * 关于页（2026-08-31 重做，Operit AboutScreen 顶部 + 设置页分组卡片）：
 * 顶部 = 圆形 logo（Operit 同款）+ 产品名「Pient」+ 版本号；
 * 下方 = 分组标题（卡片左上方）+ 卡片，四组：
 * 更新（检查更新/更新日志）、项目信息（GitHub/Gitee 仓库地址，暂未提供）、
 * 联系（开发者 Jay-Victor / 联系方式暂未提供）、版权（开源许可声明/版权所有）。
 */
@Composable
fun AboutScreen(nav: NavController) {
    var dialog by remember { mutableStateOf<AboutDialog?>(null) }
    var emailCopied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    fun copyEmail() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("email", CONTACT_EMAIL))
        emailCopied = true
    }
    LaunchedEffect(emailCopied) {
        if (emailCopied) {
            delay(1500)
            emailCopied = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Outlined.ArrowBack, "返回",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = { nav.popBackStack() }),
            )
            Text(
                "关于",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            // ── 顶部：logo + 产品名 + 版本号（Operit 同款） ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.pient_logo),
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Pient",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = VERSION,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            // ── 更新 ──
            AboutGroup("更新", Icons.Outlined.Update) {
                SettingsRow(
                    icon = Icons.Outlined.SystemUpdate,
                    title = "检查更新",
                    subtitle = "检测是否有新版本可用",
                    onClick = { dialog = AboutDialog.UPDATE },
                )
                DividerLine()
                SettingsRow(
                    icon = Icons.Outlined.History,
                    title = "更新日志",
                    subtitle = "查看历史版本的更新内容",
                    onClick = { dialog = AboutDialog.LOG },
                )
            }

            // ── 项目信息 ──
            AboutGroup("项目信息", Icons.Outlined.Info) {
                AboutLinkRow(
                    icon = painterResource(id = R.drawable.brand_github),
                    iconTint = MaterialTheme.colorScheme.onBackground,
                    title = "GitHub",
                    subtitle = GITHUB_REPO_URL,
                    onClick = { openUrl(GITHUB_REPO_URL) },
                )
                DividerLine()
                AboutLinkRow(
                    icon = painterResource(id = R.drawable.brand_gitee),
                    iconTint = MaterialTheme.colorScheme.onBackground,
                    title = "Gitee",
                    subtitle = GITEE_REPO_URL,
                    onClick = { openUrl(GITEE_REPO_URL) },
                )
            }

            // ── 联系 ──
            AboutGroup("联系", Icons.Outlined.Person) {
                AboutLinkRow(
                    icon = rememberVectorPainter(image = Icons.Outlined.Badge),
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "开发者",
                    subtitle = "Jay-Victor",
                    onClick = { openUrl(GITHUB_USER_URL) },
                )
                DividerLine()
                AboutCopyRow(
                    icon = Icons.Outlined.Email,
                    title = "联系方式",
                    subtitle = CONTACT_EMAIL,
                    copied = emailCopied,
                    onCopy = { copyEmail() },
                )
            }

            // ── 版权 ──
            AboutGroup("版权", Icons.Outlined.Copyright) {
                AboutInfoRow(
                    icon = Icons.Outlined.Gavel,
                    title = "开源许可声明",
                    subtitle = "本项目以开源许可发布",
                )
                DividerLine()
                AboutInfoRow(
                    icon = Icons.Outlined.Lock,
                    title = "版权所有",
                    subtitle = "© 2026 Pient 保留所有权利",
                )
            }
        }
    }

    // ── mock 弹窗（原型期演示；接入更新系统后替换） ──
    when (dialog) {
        AboutDialog.UPDATE -> PientDialog(
            title = "检查更新",
            onDismiss = { dialog = null },
            onConfirm = { dialog = null },
            showClose = false,
        ) {
            Text("当前已是最新版本 0.1.0", style = MaterialTheme.typography.bodyMedium)
        }
        AboutDialog.LOG -> PientDialog(
            title = "更新日志",
            onDismiss = { dialog = null },
            onConfirm = { dialog = null },
            showClose = false,
        ) {
            Text("暂无更新日志", style = MaterialTheme.typography.bodyMedium)
        }
        null -> Unit
    }
}

/** 关于页分组：SectionHeader（卡片左上方）+ 卡片容器（与设置页同款样式） */
@Composable
private fun AboutGroup(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    SectionHeader(title, icon = icon)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        content()
    }
}

/** 可点击信息行（跳转外链）：图标+标题+副标题小字+外部链接图标，整行点击 */
@Composable
private fun AboutLinkRow(
    icon: Painter,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Image(
            painter = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(iconTint),
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            Icons.Outlined.OpenInNew, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(18.dp),
        )
    }
}

/** 静态信息行（无点击、无箭头）：左侧图标+标题+副标题小字，右侧灰色值 */
@Composable
private fun AboutInfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

/** 复制行（联系方式）：图标+标题+副标题小字，右侧复制按键（点击复制、短暂切换 ✓） */
@Composable
private fun AboutCopyRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    copied: Boolean,
    onCopy: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 14.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
            if (copied) "已复制" else "复制邮箱",
            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(onClick = onCopy)
                .padding(8.dp)
                .size(20.dp),
        )
    }
}
