package com.pient.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.DrawerMode
import com.pient.app.data.SettingsStore
import com.pient.app.ui.components.DividerLine
import com.pient.app.ui.components.SectionHeader
import com.pient.app.ui.components.isTabletLayout

/**
 * 行为设置（2026-08-28；2026-09-10 改为三选一）：
 * 手机端 = 侧边栏展出方式三选一 —— 水平滑出（默认，浮层 + 遮罩）/ 3D 透视展开 / 推动展开；
 * 平板端 = 固定压缩滑出（不支持 3D 透视展开与推动展开），故只呈现一种方式（用户决策 2026-09-10）。
 * 切换即时生效并持久化（prefs `drawer_mode`）；平板端不写 prefs，手机端所选方式得以保留。
 */
@Composable
fun BehaviorSettingsScreen(nav: NavController) {
    val isTablet = isTabletLayout()
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
                "行为设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { SectionHeader("侧边栏展出方式") }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                ) {
                    if (isTablet) {
                        // 平板端固定压缩滑出（不支持 3D 透视展开 / 推动展开）：只有一种方式，
                        // 因此整行不可点、也不写 prefs —— 手机端所选展出方式在平板仍被保留。
                        DrawerModeRow(
                            icon = Icons.Outlined.Compress,
                            title = "压缩滑出（平板固定）",
                            desc = "侧边栏展开时聊天页宽度收窄并同步右移，整页始终完整可见。",
                            selected = true,
                            onClick = null,
                        )
                    } else {
                        DrawerModeOptions.forEachIndexed { i, option ->
                            if (i > 0) DividerLine()
                            DrawerModeRow(
                                icon = option.icon,
                                title = option.title,
                                desc = option.desc,
                                selected = SettingsStore.drawerMode == option.mode,
                                onClick = { SettingsStore.drawerMode = option.mode },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 展出方式选项（顺序即卡片内的排列顺序） */
private data class DrawerModeOption(
    val mode: DrawerMode,
    val icon: ImageVector,
    val title: String,
    val desc: String,
)

private val DrawerModeOptions = listOf(
    DrawerModeOption(
        mode = DrawerMode.SLIDE,
        icon = Icons.AutoMirrored.Outlined.ViewSidebar,
        title = "水平滑出（默认）",
        desc = "侧边栏浮在聊天页之上滑出，遮罩同步淡入；主内容保持不动。",
    ),
    DrawerModeOption(
        mode = DrawerMode.PERSPECTIVE,
        icon = Icons.Outlined.ViewInAr,
        title = "3D 透视展开",
        desc = "侧边栏展开时聊天页透视让位：右移、下沉、缩小并绕 Y 轴旋转。",
    ),
    DrawerModeOption(
        mode = DrawerMode.PUSH,
        icon = Icons.AutoMirrored.Outlined.FormatIndentIncrease,
        title = "推动展开",
        desc = "侧边栏展开时，主内容区域被“推”向另一侧，两者同时移动。",
    ),
)

/** 展出方式选项行：图标 + 标题 + 说明 + 选中对勾（整行可点，选中态变色加勾）；onClick = null 时行不可点（平板固定项） */
@Composable
private fun DrawerModeRow(
    icon: ImageVector,
    title: String,
    desc: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Icon(
            icon, null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp).size(18.dp),
            )
        }
    }
}
