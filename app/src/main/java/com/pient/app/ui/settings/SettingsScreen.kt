package com.pient.app.ui.settings

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
 * 设置页（UI 重设计 2026-08-28）：分组式布局 ——
 * 分组标题（靠左：图标+文字）+ 功能卡片（该分组全部设置项收纳于一张卡片，
 * 样式与旧版设置页卡片一致：surfaceContainerLow + 16dp 圆角 + 描边）；
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
                Icons.Outlined.ArrowBack, "返回",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = { nav.popBackStack() }),
            )
            Text(
                "设置",
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
            // ── 分组 1：个性化（Operit 同款 Person 图标） ──
            SettingsGroup("个性化", Icons.Outlined.Person) {
                SettingsRow(
                    icon = Icons.Outlined.Palette,
                    title = "主题与外观",
                    subtitle = "深色 · 亮色 · 跟随系统",
                    onClick = { nav.navigate("theme_settings") },
                )
                DividerLine()
                SettingsRow(
                    icon = Icons.Outlined.Language,
                    title = "语言设置",
                    subtitle = "界面语言",
                    onClick = { nav.navigate("language_settings") },
                )
                DividerLine()
                SettingsRow(
                    icon = Icons.Outlined.TouchApp,
                    title = "行为设置",
                    subtitle = "侧边栏展出方式",
                    onClick = { nav.navigate("behavior_settings") },
                )
            }

            // ── 分组 2：AI模型配置（Operit 同款 Settings 齿轮图标） ──
            SettingsGroup("AI模型配置", Icons.Outlined.Settings) {
                SettingsRow(
                    icon = Icons.Outlined.SmartToy,
                    title = "服务商与模型配置",
                    subtitle = "服务商 · 模型 · 密钥",
                    onClick = { nav.navigate("model_config") },
                )
            }

            // ── 分组 3：数据与权限（2026-09-01 新增；三行均为原型占位，后续迭代接入） ──
            SettingsGroup("数据与权限", Icons.Outlined.Security) {
                SettingsRow(
                    icon = Icons.Outlined.AdminPanelSettings,
                    title = "系统权限设置",
                    subtitle = "标准 · 调试 · Root",
                    onClick = { nav.navigate("system_permissions") },
                )
                DividerLine()
                SettingsRow(
                    icon = Icons.Outlined.History,
                    title = "项目管理设置",
                    subtitle = "项目与会话记录",
                    onClick = { nav.navigate("project_management") },
                )
                DividerLine()
                SettingsRow(
                    icon = Icons.Outlined.BarChart,
                    title = "模型用量信息",
                    subtitle = "Token 与成本统计",
                    onClick = { nav.navigate("usage") },
                )
            }

            // ── 分组 4：关于Pient（标题 Description 文档图标，与行内 Info 区分） ──
            SettingsGroup("关于Pient", Icons.Outlined.Description) {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = "关于",
                    subtitle = "版本与产品信息",
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
