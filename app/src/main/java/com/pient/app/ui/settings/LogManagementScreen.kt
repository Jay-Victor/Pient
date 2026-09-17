package com.pient.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.LogExport
import com.pient.app.data.PientLog
import com.pient.app.data.i18n.L
import com.pient.app.ui.components.DividerLine
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.SectionHeader
import com.pient.app.ui.components.SettingsRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 应用日志管理（2026-09-17；设置 → 数据与权限 → 第 4 行）。
 *
 * 形态（用户拍板 A：**导出为中心**）：状态卡（大小 / 行数 / 时间段）+ 三行操作
 * （导出日志 / 查看最近日志 / 清空日志）+ 页脚口径说明。导出 = 一个 txt 落系统「下载/Pient/」，
 * 内容见 [LogExport]（环境报告 + pi stderr + 应用日志 + 特权档的系统 logcat）。
 *
 * 为什么日志要先落盘才能导出：见 [PientLog] 头注释（纯 logcat 用户拿不到）。
 * 本页所有读数都来自磁盘（[PientLog.stats]），没有一处是界面自己编的。
 */
@Composable
fun LogManagementScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // null = 还在读盘（状态卡显示读取中，而不是先摆一排 0）
    var stats by remember { mutableStateOf<PientLog.Stats?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var clearOpen by remember { mutableStateOf(false) }

    suspend fun refresh() {
        stats = withContext(Dispatchers.IO) { PientLog.stats(context) }
    }

    LaunchedEffect(Unit) { refresh() }

    fun exportLogs() {
        if (exporting) return
        exporting = true
        scope.launch {
            val fresh = withContext(Dispatchers.IO) { PientLog.stats(context) }
            stats = fresh
            if (fresh.lines == 0) {
                exporting = false
                Toast.makeText(context, L.settings.logExportEmpty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            // 系统日志那段（特权通道）是阻塞调用，整段都在 IO 上跑
            val where = withContext(Dispatchers.IO) { LogExport.export(context) }
            stats = withContext(Dispatchers.IO) { PientLog.stats(context) }
            exporting = false
            Toast.makeText(
                context,
                if (where != null) L.settings.logExported(where) else L.settings.logExportFailed,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Icon(
                    Icons.Outlined.ArrowBack, L.common.back,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = { nav.popBackStack() }),
                )
                Text(
                    L.settings.logTitle,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item { SectionHeader(L.settings.logFileLabel) }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                    ) {
                        LogStatRow(stats)
                        DividerLine()
                        SettingsRow(
                            icon = Icons.Outlined.FileDownload,
                            title = L.settings.logExport,
                            subtitle = if (exporting) L.settings.logExporting else L.settings.logExportSubtitle,
                            onClick = { exportLogs() },
                        )
                        DividerLine()
                        SettingsRow(
                            icon = Icons.Outlined.Visibility,
                            title = L.settings.logView,
                            subtitle = L.settings.logViewSubtitle,
                            onClick = { nav.navigate("log_viewer") },
                        )
                        DividerLine()
                        SettingsRow(
                            icon = Icons.Outlined.DeleteSweep,
                            title = L.settings.logClear,
                            subtitle = L.settings.logClearSubtitle,
                            onClick = { clearOpen = true },
                        )
                    }
                }
                item {
                    Text(
                        L.settings.logNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                    )
                }
            }
        }

        if (clearOpen) {
            PientDialog(
                title = L.settings.logClearTitle,
                onDismiss = { clearOpen = false },
                confirmText = L.common.confirm,
                showClose = false,
                onConfirm = {
                    clearOpen = false
                    scope.launch {
                        withContext(Dispatchers.IO) { PientLog.clear(context) }
                        refresh()
                        Toast.makeText(context, L.settings.logCleared, Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Text(
                    L.settings.logClearBody,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

/**
 * 日志状态行（不可点）：文件名 + 大小·行数 + 覆盖时间段 + 位置说明。
 * 全部来自 [PientLog.stats] 的真实读数；还没读到（null）时只显示文件名与位置说明。
 */
@Composable
private fun LogStatRow(stats: PientLog.Stats?) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.ReceiptLong, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                "pient.log",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val text = statusText(stats)
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                L.settings.logLocationNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 状态卡第二行：`大小 · 行数` +（有日志时）换行跟一条时间段 */
private fun statusText(stats: PientLog.Stats?): String {
    if (stats == null) return L.common.reading
    if (stats.lines == 0) return L.settings.logEmptyStat
    val size = formatBytes(stats.bytes)
    val head = L.settings.logStatLine(size, stats.lines)
    val range = rangeLine(stats)
    return if (range.isEmpty()) head else "$head\n$range"
}

/** 时间段：同一天只显示后半段的时钟，跨天（跨轮转）显示完整日期时间 */
private fun rangeLine(stats: PientLog.Stats): String {
    if (stats.oldest.isBlank()) return ""
    val from = stats.oldest.take(19)
    val to = if (stats.newest.take(10) == stats.oldest.take(10)) {
        stats.newest.substring(11, 19)
    } else {
        stats.newest.take(19)
    }
    return L.settings.logRangeLine(from, to)
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
