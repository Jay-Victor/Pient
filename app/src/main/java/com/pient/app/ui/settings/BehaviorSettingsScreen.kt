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
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.DrawerMode
import com.pient.app.data.SettingsStore
import com.pient.app.ui.components.SectionHeader
import com.pient.app.ui.components.SettingsSwitchRow

/**
 * 行为设置（2026-08-28）：侧边栏展出方式 ——
 * 水平滑出（默认）/ 3D 透视（Operit PhoneLayout 同款：仅手机端聊天页 3D 让位；
 * 平板端始终为聊天页宽度压缩 + 侧边栏滑出，与开关无关）。切换即时生效。
 */
@Composable
fun BehaviorSettingsScreen(nav: NavController) {
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
                    SettingsSwitchRow(
                        icon = Icons.Outlined.ViewInAr,
                        title = "3D 透视展开",
                        desc = "手机端聊天页 3D 透视让位",
                        checked = SettingsStore.drawerMode == DrawerMode.PERSPECTIVE,
                        onChecked = { on ->
                            SettingsStore.drawerMode =
                                if (on) DrawerMode.PERSPECTIVE else DrawerMode.SLIDE
                        },
                    )
                    Text(
                        "关闭时为水平滑出（默认）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}
