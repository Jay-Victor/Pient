package com.pient.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.SettingsStore
import com.pient.app.data.i18n.L
import com.pient.app.data.i18n.Languages

/**
 * 语言设置（设计计划 6.3：Operit `LanguageSettingsScreen` 参考）：
 * 「跟随系统」置顶 + 语言列表（当前项 ✓）；**切换即时生效、无需重启**。
 *
 * 列表本身由 [Languages] 注册表驱动（新增语言只改语言包注册处），
 * 文案取自当前语言包 —— 切完这一页立刻换语言。
 */
@Composable
fun LanguageSettingsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val options = Languages.options()

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
                L.settings.languageTitle,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        LazyColumn {
            items(options, key = { it.id }) { opt ->
                val sel = SettingsStore.language == opt.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = {
                            SettingsStore.language = opt.id
                            SettingsStore.saveLanguage(ctx)
                        })
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Text(
                        opt.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    if (sel) {
                        Icon(
                            Icons.Outlined.Check, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Text(
            L.settings.languageApplied,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}
