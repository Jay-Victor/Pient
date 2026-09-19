package com.pient.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.DownloadsOut
import com.pient.app.data.ExitHistory
import com.pient.app.data.LogExport
import com.pient.app.data.PientLog
import com.pient.app.data.i18n.L
import com.pient.app.ui.components.DividerLine
import com.pient.app.ui.components.PientChoiceRow
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.SectionHeader
import com.pient.app.ui.components.SettingsRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用日志管理（设置 → 数据与权限 → 第 4 行）。
 *
 * 形态（**导出为中心**）：状态卡（大小 / 行数 / 时间段）+ 四行操作
 * （导出日志 / 查看最近日志 / 复制诊断摘要 / 清空日志）+「上次运行」卡 + 页脚口径说明。
 *
 * 交互口径：
 * - 导出前选范围（全部 / 仅警告以上 / 最近 30 分钟），导出后弹「完成 / 分享」；
 * - 「上次运行」用系统退出记录（[ExitHistory]）回答「应用怎么没了 / 卡死了」；
 * - 「复制诊断摘要」把十几行环境信息丢进剪贴板（贴 IM 用，比发文件轻）；
 * - 日志里不记用户正文（那一行只记条数 + 长度）。
 *
 * 本页所有读数都来自磁盘或系统 API，没有一处是界面自己编的。
 */
@Composable
fun LogManagementScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // null = 还在读盘（状态卡显示读取中，而不是先摆一排 0）
    var stats by remember { mutableStateOf<PientLog.Stats?>(null) }
    var lastExit by remember { mutableStateOf<ExitHistory.LastExit?>(null) }
    var exitSupported by remember { mutableStateOf(true) }
    var exporting by remember { mutableStateOf(false) }
    var clearOpen by remember { mutableStateOf(false) }
    // 导出范围弹窗（0 = 全部；1 = 仅警告以上；2 = 最近 30 分钟）
    var scopeOpen by remember { mutableStateOf(false) }
    var scopeChoice by remember { mutableIntStateOf(0) }
    // 导出完成弹窗（带「分享」次按钮）
    var written by remember { mutableStateOf<DownloadsOut.Written?>(null) }

    suspend fun refresh() {
        stats = withContext(Dispatchers.IO) { PientLog.stats(context) }
        lastExit = withContext(Dispatchers.IO) { ExitHistory.last(context) }
        exitSupported = ExitHistory.supported()
    }

    LaunchedEffect(Unit) { refresh() }

    fun doExport(exportScope: LogExport.LogScope) {
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
            val result = withContext(Dispatchers.IO) { LogExport.export(context, exportScope) }
            stats = withContext(Dispatchers.IO) { PientLog.stats(context) }
            exporting = false
            if (result != null) written = result
            else Toast.makeText(context, L.settings.logExportFailed, Toast.LENGTH_LONG).show()
        }
    }

    fun copySummary() {
        scope.launch {
            val text = withContext(Dispatchers.IO) { LogExport.diagnosticSummary(context) }
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("pient-diagnostic", text))
            Toast.makeText(context, L.common.copied, Toast.LENGTH_SHORT).show()
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
                            onClick = {
                                if (!exporting) {
                                    scopeChoice = 0
                                    scopeOpen = true
                                }
                            },
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
                            icon = Icons.Outlined.ContentCopy,
                            title = L.settings.logCopyDiag,
                            subtitle = L.settings.logCopyDiagSubtitle,
                            onClick = { copySummary() },
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

                // ── 上次运行（系统退出记录，Android 11+）──
                item { SectionHeader(L.settings.logExitTitle) }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                    ) {
                        ExitInfoRow(lastExit, exitSupported)
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

        // ── 导出范围弹窗 ──
        if (scopeOpen) {
            PientDialog(
                title = L.settings.logScopeTitle,
                onDismiss = { scopeOpen = false },
                confirmText = L.settings.logExport,
                showClose = false,
                onConfirm = {
                    scopeOpen = false
                    doExport(
                        when (scopeChoice) {
                            1 -> LogExport.LogScope.WARN
                            2 -> LogExport.LogScope.RECENT
                            else -> LogExport.LogScope.ALL
                        },
                    )
                },
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    PientChoiceRow(
                        icon = Icons.Outlined.FileDownload,
                        label = L.settings.logScopeAll,
                        selected = scopeChoice == 0,
                        onClick = { scopeChoice = 0 },
                    )
                    PientChoiceRow(
                        icon = Icons.Outlined.ErrorOutline,
                        label = L.settings.logScopeWarn,
                        selected = scopeChoice == 1,
                        onClick = { scopeChoice = 1 },
                    )
                    PientChoiceRow(
                        icon = Icons.Outlined.Info,
                        label = L.settings.logScopeRecent,
                        selected = scopeChoice == 2,
                        onClick = { scopeChoice = 2 },
                    )
                }
            }
        }

        // ── 导出完成弹窗（完成 / 分享）──
        written?.let { w ->
            PientDialog(
                title = L.settings.logExportDoneTitle,
                onDismiss = { written = null },
                confirmText = L.common.done,
                showClose = false,
                extraActionText = L.settings.logShare,
                onExtraAction = {
                    val intent = DownloadsOut.shareIntent(w, L.settings.logShare)
                    if (intent != null) {
                        runCatching { context.startActivity(intent) }
                        written = null
                    } else {
                        // 拿不到可分享的 uri（极罕见）：如实告诉用户文件在哪
                        Toast.makeText(context, L.settings.logExported(w.location), Toast.LENGTH_LONG).show()
                        written = null
                    }
                },
                onConfirm = { written = null },
            ) {
                Text(
                    w.location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
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
            Text(
                statusText(stats),
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

/**
 * 「上次运行」行：三种态都说实话 —— 系统不支持（Android 11 以下）/ 没有异常记录 / 具体原因 + 时间。
 * 出问题的那几种（崩溃 / ANR / 被系统杀）用 ErrorOutline 图标，其余用 Info。
 */
@Composable
private fun ExitInfoRow(last: ExitHistory.LastExit?, supported: Boolean) {
    val bad = last?.abnormal == true
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            if (bad) Icons.Outlined.ErrorOutline else Icons.Outlined.Info, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                when {
                    !supported -> L.settings.logExitTitle
                    last != null -> L.settings.logExitReason(last.label)
                    else -> L.settings.logExitTitle
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                when {
                    !supported -> L.settings.logExitUnsupported
                    last != null -> L.settings.logExitTimeAndNote(
                        formatTime(last.timeMs),
                        if (last.hasAnrTrace) L.settings.logExitHasTrace else "",
                    )
                    else -> L.settings.logExitNone
                },
                style = MaterialTheme.typography.bodySmall,
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

private fun formatTime(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))
