package com.pient.app.ui.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * 环境配置页（**空白占位**，2026-09-14 用户拍板：页面内容整体移除）。
 *
 * 历史：这里曾是「执行环境（PRoot / chroot 二选一）+ 环境内软件（apt 镜像源 + 组件勾选）」的
 * 管理页；终端执行链路（Ubuntu rootfs / PRoot / 真 bash / apt 安装）整体移除后页面内容不再有意义。
 * 现只保留顶栏（返回 + 标题），内容区留空；入口（终端页 → 环境配置）与返回行为保持不变。
 */
@Composable
fun TerminalSetupScreen(nav: NavController) {
    Column(Modifier.fillMaxSize()) {
        // ── 顶栏：返回 + 标题 ──
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
                "环境配置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        // ── 内容区：空白占位 ──
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}
