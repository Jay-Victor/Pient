package com.pient.app.ui.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.ChatState
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.theme.PientPanel

/**
 * 环境配置页（设计计划 5.4；Operit SetupScreen 导航形态参考）：
 * 终端页快捷按键栏右端「环境配置」进入。rootfs 就绪检测 → 一键配置 → 完成返回。
 * UI 原型：mock 状态（ChatState.envReady）；接入运行时后由 rootfs 解压/挂载进度驱动。
 */
@Composable
fun TerminalSetupScreen(nav: NavController, chatState: ChatState) {
    var configuring by remember { mutableStateOf(false) }
    val envReady = chatState.envReady
    var step by remember { mutableStateOf(if (envReady) 3 else 0) }

    val items = listOf(
        "Ubuntu 24.04 ARM64 rootfs",
        "bash + coreutils + apt（minbase）",
        "PRoot 挂载（数据目录映射）",
        "组件安装（git / python / build-essential）",
    )

    Column(Modifier.fillMaxSize()) {
        // 顶栏：返回 + 标题
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

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // 配置项卡片
            PientPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    items.forEachIndexed { i, label ->
                        EnvRow(
                            label = label,
                            done = envReady || step > i,
                            configuring = configuring && step <= i,
                        )
                    }
                }
            }

            // 状态说明
            Text(
                if (envReady) "环境就绪 · 进入终端后与 Agent bash 工具共用同一 rootfs"
                else "检测到 rootfs 尚未初始化 · 点击「一键配置」自动完成（原型演示）",
                style = MaterialTheme.typography.labelSmall,
                color = if (envReady) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 一键配置 / 完成
            PientButton(
                text = if (envReady) "完成" else "一键配置",
                onClick = {
                    if (envReady) {
                        nav.popBackStack()
                    } else {
                        configuring = true
                        step = 3
                        chatState.envReady = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
            )
        }
    }
}

@Composable
private fun EnvRow(label: String, done: Boolean, configuring: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (done) Icons.Outlined.Check else Icons.Outlined.Terminal,
            null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (done) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        if (configuring && !done) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}
