package com.pient.app.ui.settings

import com.pient.app.data.i18n.L
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pient.app.data.ApkDownloader
import com.pient.app.data.DownloadState
import com.pient.app.data.UpdateCheckState
import com.pient.app.data.UpdateInfo
import com.pient.app.ui.components.ArcSpinner
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.theme.PientPanel
import java.text.DecimalFormat

/** 「更新内容」卡折叠前最多显示的条数（与更新日志页同一口径） */
private const val COLLAPSED_MAX_CHANGES = 3

/**
 * 检查更新弹窗：形态对齐参考实现 Mdcito 的 `UpdateDialog` ——
 * 标题「检查更新」+ 右上关闭键，正文按状态分支，**动作按钮放在正文里**（没有底部按钮行）。
 *
 * 四种检查状态（检测中 / 已是最新 / 发现新版本 / 检查失败）与四种下载状态
 * （空闲 / 下载中·已暂停 / 下载完成 / 下载出错）都在这里渲染；实际动作由 [com.pient.app.data.UpdateCenter] 执行。
 *
 * 检查状态分支写成 if-else（不是 when 的多分支逗号写法）：状态在检测中/已是最新间切换时保持槽位稳定，
 * 旋转圆弧不会被销毁重建。
 */
@Composable
fun UpdateDialog(
    state: UpdateCheckState,
    downloadState: DownloadState,
    installPermissionNeeded: Boolean,
    appVersion: String,
    onRetry: () -> Unit,
    onDownload: (String?) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (String) -> Unit,
    onOpenInstallSettings: () -> Unit,
    onOpenDownloadPage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        PientPanel(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(horizontal = 24.dp)
                .clickable(
                    onClick = {},
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                // ── 标题 + 关闭 ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        L.common.checkUpdate,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.W700,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(onClick = onDismiss),
                    ) {
                        Icon(
                            Icons.Outlined.Close, L.common.close,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (state is UpdateCheckState.Checking) {
                    CheckingBody()
                } else {
                    when (state) {
                        UpdateCheckState.Latest -> UpToDateBody(appVersion)
                        UpdateCheckState.Failed -> FailedBody(onRetry)
                        is UpdateCheckState.Available -> AvailableBody(
                            info = state.info,
                            appVersion = appVersion,
                            downloadState = downloadState,
                            installPermissionNeeded = installPermissionNeeded,
                            onDownload = onDownload,
                            onPause = onPause,
                            onResume = onResume,
                            onCancelDownload = onCancelDownload,
                            onInstall = onInstall,
                            onOpenInstallSettings = onOpenInstallSettings,
                            onOpenDownloadPage = onOpenDownloadPage,
                            onDismiss = onDismiss,
                        )
                        UpdateCheckState.Checking, UpdateCheckState.Idle -> Unit
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** 检测中：居中转圈 + 说明文字 */
@Composable
private fun CheckingBody() {
    Spacer(Modifier.height(16.dp))
    ArcSpinner(size = 48.dp, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(16.dp))
    Text(
        L.settings.checkingUpdate,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
}

/** 已是最新：主色对勾 + 标题（带版本号）+ 说明 */
@Composable
private fun UpToDateBody(appVersion: String) {
    Spacer(Modifier.height(8.dp))
    Icon(
        Icons.Outlined.CheckCircle, null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text(
        L.settings.upToDateLabel(appVersion),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.W600,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        L.settings.upToDateDesc,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
}

/** 检查失败：error 色感叹号 + 标题 + 原因 + 整宽「重试」 */
@Composable
private fun FailedBody(onRetry: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Icon(
        Icons.Outlined.ErrorOutline, null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(48.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text(
        L.settings.updateCheckFailedTitle,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.W600,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        L.settings.updateCheckFailed,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    PientButton(
        L.chat.retry,
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
}

/** 发现新版本：图标 + 标题 + 版本对比 + 安装包信息行 +「更新内容」卡 + 下载区 */
@Composable
private fun AvailableBody(
    info: UpdateInfo,
    appVersion: String,
    downloadState: DownloadState,
    installPermissionNeeded: Boolean,
    onDownload: (String?) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (String) -> Unit,
    onOpenInstallSettings: () -> Unit,
    onOpenDownloadPage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val canExpand = info.notes.size > COLLAPSED_MAX_CHANGES

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Outlined.Update, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                L.settings.newVersionFound,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W700,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                L.settings.newVersionDetail(info.versionName, appVersion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // 安装包信息行：大小（主色容器小标签）+ 发布日期
    if (info.apkSize > 0 || info.releaseDate.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (info.apkSize > 0) {
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        formatFileSize(info.apkSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (info.releaseDate.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    info.releaseDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (info.notes.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    L.settings.updateNotes,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                val shown = if (expanded || !canExpand) info.notes else info.notes.take(COLLAPSED_MAX_CHANGES)
                shown.forEach { note ->
                    Row(modifier = Modifier.padding(vertical = 1.5.dp)) {
                        Text(
                            "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(12.dp),
                        )
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (canExpand) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            if (expanded) L.common.collapse else L.common.expand,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    DownloadSection(
        info = info,
        downloadState = downloadState,
        installPermissionNeeded = installPermissionNeeded,
        onDownload = onDownload,
        onPause = onPause,
        onResume = onResume,
        onCancelDownload = onCancelDownload,
        onInstall = onInstall,
        onOpenInstallSettings = onOpenInstallSettings,
        onDismiss = onDismiss,
    )

    // 网页侧入口（始终可见，不受下载状态影响）
    if (info.downloadPageUrl.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        PientButton(
            L.perm.goDownload,
            onClick = { onOpenDownloadPage(info.downloadPageUrl) },
            primary = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(16.dp))
}

/**
 * 下载区（照 Mdcito 的 `DownloadSection`）：空闲给「下载更新」+ 镜像按键；下载中给进度条与暂停/取消；
 * 暂停显示「已暂停」；完成给「立即安装 / 稍后安装」；出错给原因与「重新下载」。
 * 没给「安装未知应用」权限时，下面再叠一张安装权限卡。
 */
@Composable
private fun DownloadSection(
    info: UpdateInfo,
    downloadState: DownloadState,
    installPermissionNeeded: Boolean,
    onDownload: (String?) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (String) -> Unit,
    onOpenInstallSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (downloadState) {
        is DownloadState.Idle -> {
            if (info.apkUrl.isNotBlank()) {
                PientButton(
                    L.settings.updateDownload,
                    onClick = { onDownload(null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (info.mirrors.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        L.settings.updateMirrorLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        info.mirrors.forEach { mirror ->
                            PientButton(
                                mirror.name,
                                onClick = { onDownload(mirror.url) },
                                primary = false,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        is DownloadState.Downloading -> DownloadProgressBody(
            progress = downloadState.progress,
            downloadedBytes = downloadState.downloadedBytes,
            totalBytes = downloadState.totalBytes,
            speedBytesPerSec = downloadState.speedBytesPerSec,
            remainingTimeSecs = downloadState.remainingTimeSecs,
            isPaused = false,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancelDownload,
        )

        is DownloadState.Paused -> DownloadProgressBody(
            progress = downloadState.progress,
            downloadedBytes = downloadState.downloadedBytes,
            totalBytes = downloadState.totalBytes,
            speedBytesPerSec = 0L,
            remainingTimeSecs = 0L,
            isPaused = true,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancelDownload,
        )

        is DownloadState.Completed -> {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Outlined.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    L.settings.updateDownloadComplete,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(12.dp))
            PientButton(
                L.settings.updateInstallNow,
                onClick = { onInstall(downloadState.filePath) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            PientButton(
                L.settings.updateInstallLater,
                onClick = onDismiss,
                primary = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is DownloadState.Error -> {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Outlined.ErrorOutline, null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    downloadState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            PientButton(
                L.settings.updateRetryDownload,
                onClick = { onDownload(null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (installPermissionNeeded) {
        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    L.settings.updateInstallPermissionTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    L.settings.updateInstallPermissionDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                PientButton(
                    L.settings.updateInstallPermissionGo,
                    onClick = onOpenInstallSettings,
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 进度条 + 百分比 + 已下载/总量 + 速度 + 剩余时间 +「暂停/继续」与「取消」 */
@Composable
private fun DownloadProgressBody(
    progress: Float,
    downloadedBytes: Long,
    totalBytes: Long,
    speedBytesPerSec: Long,
    remainingTimeSecs: Long,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "${(progress * 100).toInt()}%",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.W700,
        color = MaterialTheme.colorScheme.primary,
    )
    if (isPaused) {
        Spacer(Modifier.height(4.dp))
        Text(
            L.settings.updatePaused,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.W600,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
    Spacer(Modifier.height(4.dp))
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "${formatFileSize(downloadedBytes)}/${formatFileSize(totalBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (speedBytesPerSec > 0) {
            Text(
                "${formatFileSize(speedBytesPerSec)}/s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (remainingTimeSecs > 0 && speedBytesPerSec > 0) {
        Text(
            L.settings.updateRemaining(formatRemainingTime(remainingTimeSecs)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        PientButton(
            if (isPaused) L.settings.updateResume else L.settings.updatePause,
            onClick = if (isPaused) onResume else onPause,
            primary = false,
            modifier = Modifier.weight(1f),
        )
        PientButton(
            L.common.cancel,
            onClick = onCancel,
            primary = false,
            modifier = Modifier.weight(1f),
        )
    }
}

private val fileSizeFormat = DecimalFormat("#.##")

/** 文件大小（B / KB / MB / GB，与 Mdcito 同一套进位） */
private fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${fileSizeFormat.format(bytes.toDouble() / 1024)} KB"
    bytes < 1024L * 1024 * 1024 -> "${fileSizeFormat.format(bytes.toDouble() / (1024 * 1024))} MB"
    else -> "${fileSizeFormat.format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
}

/** 剩余时间（1m20s / 12s / 1h05m） */
private fun formatRemainingTime(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m${seconds % 60}s"
    else -> "${seconds / 3600}h${(seconds % 3600) / 60}m"
}
