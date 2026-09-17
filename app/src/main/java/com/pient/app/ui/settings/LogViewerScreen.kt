package com.pient.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.PientLog
import com.pient.app.data.i18n.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 查看页一次读多少行（够看最近一段；全量在导出文件里） */
private const val VIEW_LINES = 2000

/**
 * 日志查看（2026-09-17，「应用日志管理 → 查看最近日志」）：只读看最近 [VIEW_LINES] 行，
 * 按级别筛选（全部 / 警告以上 / 错误）。**不做实时刷新、不做搜索** —— 查看是顺手，
 * 要完整内容走导出（用户拍板 A 的口径）。
 *
 * 一条日志 = 一条记录（多行堆栈算同一条，续行跟着首行走，筛选时整条一起进出）——
 * 切段规则与文件格式同口径，见 [PientLog] 头注释。
 */
@Composable
fun LogViewerScreen(nav: NavController) {
    val context = LocalContext.current
    var records by remember { mutableStateOf<List<LogRecord>?>(null) }
    var filter by remember { mutableIntStateOf(FILTER_ALL) }

    LaunchedEffect(Unit) {
        records = withContext(Dispatchers.IO) {
            parseRecords(PientLog.readTail(context, VIEW_LINES))
        }
    }

    val shown = remember(records, filter) {
        when (filter) {
            FILTER_WARN -> records.orEmpty().filter { it.level == 'W' || it.level == 'E' }
            FILTER_ERROR -> records.orEmpty().filter { it.level == 'E' }
            else -> records.orEmpty()
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
                Icons.Outlined.ArrowBack, L.common.back,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = { nav.popBackStack() }),
            )
            Text(
                L.settings.logViewerTitle,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
            if (records != null) {
                Text(
                    L.settings.logViewerCount(shown.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            FilterPill(L.settings.logFilterAll, filter == FILTER_ALL) { filter = FILTER_ALL }
            FilterPill(L.settings.logFilterWarn, filter == FILTER_WARN) { filter = FILTER_WARN }
            FilterPill(L.settings.logFilterError, filter == FILTER_ERROR) { filter = FILTER_ERROR }
        }

        if (shown.isNotEmpty()) {
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                itemsIndexed(shown) { idx, rec ->
                    Text(
                        rec.lines.joinToString("\n"),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (rec.level == 'E') {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 3.dp),
                    )
                }
            }
        } else {
            Text(
                L.settings.logViewerEmpty,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

private const val FILTER_ALL = 0
private const val FILTER_WARN = 1
private const val FILTER_ERROR = 2

/** 筛选胶囊（选中 = primary 0.10 底 + 0.5 描边 + primary 文字，与文件页标签同口径） */
@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Column(
        modifier = Modifier
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 一条日志记录：`level` = 首行的级别字母（续行/无前缀行为 null） */
internal data class LogRecord(val level: Char?, val lines: List<String>)

/** 按 `时间戳 级别/tag: 正文` 切段；续行（堆栈）并入上一条 */
internal fun parseRecords(raw: List<String>): List<LogRecord> {
    val out = ArrayList<LogRecord>()
    var cur: MutableList<String>? = null
    var level: Char? = null
    raw.forEach { line ->
        val lv = levelOf(line)
        if (lv != null) {
            cur?.let { out.add(LogRecord(level, it)) }
            cur = mutableListOf(line)
            level = lv
        } else if (cur == null) {
            out.add(LogRecord(null, listOf(line)))
        } else {
            cur!!.add(line)
        }
    }
    cur?.let { out.add(LogRecord(level, it)) }
    return out
}

/** `2026-09-17 20:45:12.123 I/PientChat: 正文` → 'I'；不是行首前缀返回 null */
internal fun levelOf(line: String): Char? {
    if (line.length < 26) return null
    if (line[4] != '-' || line[7] != '-' || line[10] != ' ' || line[13] != ':' ||
        line[16] != ':' || line[19] != '.' || line[23] != ' ' || line[25] != '/'
    ) {
        return null
    }
    val lv = line[24]
    return if (lv in "VDIWEA") lv else null
}
