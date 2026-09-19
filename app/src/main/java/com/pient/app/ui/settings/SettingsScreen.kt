package com.pient.app.ui.settings

import com.pient.app.data.i18n.L
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.ui.components.DividerLine
import com.pient.app.ui.components.SectionHeader
import com.pient.app.ui.components.SettingsRow

/**
 * 设置页：分组式布局 ——
 * 分组标题（靠左：图标+文字）+ 功能卡片（该分组全部设置项收纳于一张卡片，
 * 样式与设置页卡片一致：surfaceContainerLow + 16dp 圆角 + 描边）；
 * 条目 = 左侧图标+主标题+副标题小字，右侧 ">" 箭头。
 * 分组：个性化（主题与外观/语言设置/行为设置）、AI模型配置（服务商与模型配置）、
 * 数据与权限（系统权限设置/项目记录管理/模型用量信息）、关于Pient（关于）。
 */
@Composable
fun SettingsScreen(nav: NavController) {
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
                L.common.settings,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            // ── 分组 1：个性化（Person 图标） ──
            SettingsGroup(L.settings.personalization, Icons.Outlined.Person) {
                SettingsRow(
                    icon = Icons.Outlined.Palette,
                    title = L.settings.theme,
                    subtitle = L.settings.themeSubtitle,
                    onClick = { nav.navigate("theme_settings") },
                )
                DividerLine()
                SettingsRow(
                    icon = Icons.Outlined.Language,
                    title = L.settings.languageTitle,
                    subtitle = L.settings.languageSubtitle,
                    onClick = { nav.navigate("language_settings") },
                )
                DividerLine()
                SettingsRow(
                    icon = Icons.Outlined.TouchApp,
                    title = L.settings.behavior,
                    subtitle = L.settings.behaviorSubtitle,
                    onClick = { nav.navigate("behavior_settings") },
                )
            }

            // ── 分组 2：AI模型配置（Settings 齿轮图标） ──
            SettingsGroup(L.settings.groupModels, Icons.Outlined.Settings) {
                SettingsRow(
                    icon = Icons.Outlined.SmartToy,
                    title = L.settings.modelConfig,
                    subtitle = L.settings.modelConfigSubtitle,
                    onClick = { nav.navigate("model_config") },
                )
            }

            // ── 分组 3：数据与权限（四行均为真实页面） ──
            SettingsGroup(L.settings.groupData, Icons.Outlined.Security) {
                SettingsRow(
                    icon = Icons.Outlined.AdminPanelSettings,
                    title = L.settings.systemPermissions,
                    subtitle = L.settings.systemPermissionsSubtitle,
                    onClick = { nav.navigate("system_permissions") },
                )
                DividerLine()
                SettingsRow(
                    icon = Icons.Outlined.History,
                    title = L.settings.projectManagement,
                    subtitle = L.settings.projectManagementSubtitle,
                    onClick = { nav.navigate("project_management") },
                )
                DividerLine()
                SettingsRow(
                    icon = Icons.Outlined.BarChart,
                    title = L.settings.usage,
                    subtitle = L.settings.usageSubtitle,
                    onClick = { nav.navigate("usage") },
                )
                DividerLine()
                // 应用日志管理：运行日志的导出 / 查看 / 清空
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = L.settings.logTitle,
                    subtitle = L.settings.logSubtitle,
                    onClick = { nav.navigate("app_logs") },
                )
            }

            // ── 分组 4：关于Pient（标题 Description 文档图标，与行内 Info 区分） ──
            SettingsGroup(L.settings.groupAbout, Icons.Outlined.Description) {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = L.settings.about,
                    subtitle = L.settings.aboutSubtitle,
                    onClick = { nav.navigate("about") },
                )
            }
        }
    }
}

/** 设置分组：分组标题（图标+文字，靠左）+ 功能卡片容器（收纳该组全部条目） */
@Composable
private fun SettingsGroup(
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
