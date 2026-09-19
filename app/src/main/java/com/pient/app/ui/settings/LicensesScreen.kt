package com.pient.app.ui.settings

import com.pient.app.data.i18n.L
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Dataset
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pient.app.data.OpenSourceLicenses
import com.pient.app.ui.components.ArcSpinner
import com.pient.app.ui.components.DividerLine
import com.pient.app.ui.components.SectionHeader
import com.pient.app.ui.components.SettingsRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 开源许可声明页：按「随应用分发 / 应用依赖 / 字体 / 数据来源」四组列出第三方组件，
 * 每行给组件名 + 上游地址 + 许可名，整行可点（打开上游页面）；末尾一行进 GPL-3.0 全文页。
 *
 * 数据（组件、许可、地址）在 [OpenSourceLicenses]，这里只管渲染与分组标题的取词；
 * 许可名不翻译（SPDX 口径），只有「多许可合集 / 数据集」两条通用表述走语言包。
 */
@Composable
fun LicensesScreen(nav: NavController) {
    val groups = listOf(
        SectionGroup(OpenSourceLicenses.Section.RUNTIME, Icons.Outlined.Memory, L.settings.licensesSectionRuntime),
        SectionGroup(OpenSourceLicenses.Section.LIBRARY, Icons.Outlined.Extension, L.settings.licensesSectionLibraries),
        SectionGroup(OpenSourceLicenses.Section.FONT, Icons.Outlined.TextFields, L.settings.licensesSectionFonts),
        SectionGroup(OpenSourceLicenses.Section.DATA, Icons.Outlined.Dataset, L.settings.licensesSectionData),
    )
    Column(Modifier.fillMaxSize()) {
        LicensesTopBar(L.settings.openSourceLicenses) { nav.popBackStack() }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        ) {
            item { LicensesIntroCard() }
            groups.forEach { group ->
                item { SectionHeader(group.title, icon = group.icon) }
                item {
                    LicenseCard(OpenSourceLicenses.entries.filter { it.section == group.section })
                }
            }
            item {
                Column(Modifier.padding(top = 12.dp)) {
                    SettingsRow(
                        icon = Icons.Outlined.Gavel,
                        title = L.settings.licensesFullText,
                        subtitle = "GNU General Public License v3.0",
                        onClick = { nav.navigate("license_text") },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                    )
                }
            }
        }
    }
}

/** 分组标题与卡片内容的绑定（标题按当前语言取词） */
private class SectionGroup(
    val section: OpenSourceLicenses.Section,
    val icon: ImageVector,
    val title: String,
)

/** 顶部说明：本项目的许可 + 这一页管什么 */
@Composable
private fun LicensesIntroCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(
            L.settings.licensesIntro,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
    }
}

/** 一组组件（与设置页卡片同款：surfaceContainerLow 底 + 16dp 圆角 + 描边） */
@Composable
private fun LicenseCard(entries: List<OpenSourceLicenses.Entry>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) DividerLine()
            LicenseRow(entry)
        }
    }
}

/** 一行：组件名 + 上游地址（左，占满剩余宽度）；右端许可名；整行点开上游页面 */
@Composable
private fun LicenseRow(entry: OpenSourceLicenses.Entry) {
    val uriHandler = LocalUriHandler.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { runCatching { uriHandler.openUri(entry.url) } }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.url.removePrefix("https://"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            licenseLabel(entry.license),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 128.dp)
                .padding(start = 12.dp),
        )
    }
}

/** 许可列的显示文案：SPDX 名照原样，两个通用取值走语言包 */
@Composable
private fun licenseLabel(label: OpenSourceLicenses.LicenseLabel): String = when (label) {
    is OpenSourceLicenses.LicenseLabel.Spdx -> label.name
    OpenSourceLicenses.LicenseLabel.Various -> L.settings.licensesVarious
    OpenSourceLicenses.LicenseLabel.OpenData -> L.settings.licensesOpenData
}

/**
 * GPL-3.0 全文页：正文从随包的 `assets/licenses/GPL-3.0.txt` 读（离线可读，不依赖网络），
 * 文本较长（约 35KB）所以纯滚动展示、不做选择与搜索。
 */
@Composable
fun LicenseTextScreen(nav: NavController) {
    val context = LocalContext.current
    var body by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        body = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(OpenSourceLicenses.GPL3_ASSET).bufferedReader().use { it.readText() }
            }.getOrNull()
        }
    }

    Column(Modifier.fillMaxSize()) {
        LicensesTopBar(L.settings.licensesFullText) { nav.popBackStack() }
        val text = body
        if (text == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ArcSpinner(size = 32.dp, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

/** 两页共用的顶栏（返回 + 标题） */
@Composable
private fun LicensesTopBar(title: String, onBack: () -> Unit) {
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
                .clickable(onClick = onBack),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
