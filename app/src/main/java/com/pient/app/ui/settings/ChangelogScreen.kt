package com.pient.app.ui.settings

import com.pient.app.data.i18n.L
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.AppUpdate
import com.pient.app.data.ChangelogEntry
import com.pient.app.ui.components.ArcSpinner

/** 折叠时最多显示的变更条数（超出才出现「展开 / 收起」） */
private const val COLLAPSED_MAX_CHANGES = 3

/** 更新日志页状态：加载中 / 清单还没写任何版本 / 取到条目 / 取不到（没网或清单不在） */
private sealed interface ChangelogUiState {
    data object Loading : ChangelogUiState
    data object Empty : ChangelogUiState
    data class Loaded(val entries: List<ChangelogEntry>) : ChangelogUiState
    data object Failed : ChangelogUiState
}

/**
 * 更新日志页：整页展示远端版本清单里的各版本记录（版本卡片列表）——
 * 一行 = 版本号 + 「最新」徽标 + 发布日期，下方是版本标题与逐条变更（超过三条折叠），
 * 卡片底部按需给「查看发布」（打开该版本的发布页）。
 *
 * 数据源 = 关于页「检查更新」读的同一份数据（GitHub / Gitee 的 Releases API，照参考实现 Mdcito）；
 * 进页面即拉取，
 * 顶栏右侧键随时重拉（加载中转圈 / 失败与成功都是刷新键，失败态用主色以便看出该点它）。
 */
@Composable
fun ChangelogScreen(nav: NavController) {
    var state by remember { mutableStateOf<ChangelogUiState>(ChangelogUiState.Loading) }
    var refreshSeq by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshSeq) {
        state = ChangelogUiState.Loading
        val entries = AppUpdate.fetchReleases()
        state = when {
            entries == null -> ChangelogUiState.Failed
            entries.isEmpty() -> ChangelogUiState.Empty
            else -> ChangelogUiState.Loaded(entries)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ── 顶栏：返回 + 标题 + 重拉键（加载中显示旋转圆弧） ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack, L.common.back,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = { nav.popBackStack() }),
            )
            Text(
                L.settings.updateLog,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            if (state is ChangelogUiState.Loading) {
                ArcSpinner(size = 16.dp, color = MaterialTheme.colorScheme.primary)
            } else {
                Icon(
                    Icons.Outlined.Refresh, L.common.refresh,
                    tint = if (state is ChangelogUiState.Failed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { refreshSeq += 1 },
                )
            }
        }

        when (val s = state) {
            ChangelogUiState.Loading -> CenteredNote(L.settings.loadingChangelog, spinner = true)
            ChangelogUiState.Empty -> CenteredNote(L.settings.noChangelog)
            ChangelogUiState.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(
                    L.settings.changelogLoadFailed,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { refreshSeq += 1 }) {
                    Text(L.chat.retry)
                }
            }
            is ChangelogUiState.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(s.entries, key = { it.versionName }) { entry ->
                    VersionCard(entry)
                }
            }
        }
    }
}

/** 居中提示（加载中带旋转圆弧） */
@Composable
private fun CenteredNote(text: String, spinner: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (spinner) {
                ArcSpinner(size = 32.dp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 一个版本的卡片：最新版用主色容器底，其余与设置页卡片同款 */
@Composable
private fun VersionCard(entry: ChangelogEntry) {
    val shape = RoundedCornerShape(16.dp)
    if (entry.isLatest) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp,
        ) {
            VersionCardContent(entry)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        ) {
            VersionCardContent(entry)
        }
    }
}

@Composable
private fun VersionCardContent(entry: ChangelogEntry) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val canExpand = entry.changes.size > COLLAPSED_MAX_CHANGES
    // 最新版卡片是主色容器底：强调色用 primary，正文用 onSurfaceVariant
    val strongColor = if (entry.isLatest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
    val subtleColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // 第一行：版本号 + 「最新」徽标 + 发布日期
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "v${entry.versionName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W700,
                color = strongColor,
            )
            if (entry.isLatest) {
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary) {
                    Text(
                        L.settings.changelogLatest,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.W600,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(entry.releaseDate, style = MaterialTheme.typography.bodySmall, color = subtleColor)
        }

        Spacer(Modifier.height(6.dp))

        // 版本标题
        Text(
            entry.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.W600,
            color = strongColor,
        )

        // 变更条目（超过三条折叠）
        if (entry.changes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.animateContentSize()) {
                val shown = if (expanded || !canExpand) entry.changes else entry.changes.take(COLLAPSED_MAX_CHANGES)
                shown.forEach { change ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.5.dp),
                    ) {
                        Text(
                            "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (entry.isLatest) strongColor else subtleColor,
                            modifier = Modifier.width(12.dp),
                        )
                        Text(
                            change,
                            style = MaterialTheme.typography.bodySmall,
                            color = subtleColor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (canExpand) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(top = 4.dp),
                    ) {
                        Text(
                            if (expanded) L.common.collapse else L.common.expand,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // 该版本有发布页时的入口
        if (entry.releaseUrl.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                color = if (entry.isLatest) {
                    MaterialTheme.colorScheme.outlineVariant
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                },
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = {
                        runCatching { uriHandler.openUri(entry.releaseUrl) }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew, null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        L.settings.changelogViewRelease,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
