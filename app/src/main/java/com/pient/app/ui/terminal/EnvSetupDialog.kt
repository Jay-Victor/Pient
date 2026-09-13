package com.pient.app.ui.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pient.app.PientRuntime
import com.pient.app.data.ComponentGroups
import com.pient.app.data.SettingsStore
import com.pient.app.data.UBUNTU_COMPONENTS
import com.pient.app.runtime.EnvProvision
import com.pient.app.runtime.PiTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pient.app.ui.components.PientDialog

/**
 * 首启「环境安装」。
 *
 * 定位（用户 2026-09-13 拍板）：**首启进终端页弹一次、可跳过**；Ubuntu 本身随包就绪
 * （自动解包 PRoot rootfs），但工具链（Node / Python / Git …）不随包分发，需要时在这里装。
 *
 * 与「环境配置 → 环境内软件」是同一份清单（[UBUNTU_COMPONENTS]）、同一个分类
 * （[ComponentGroups]）与同一条安装通道（[EnvProvision.installInTerminal]）：点「安装所选」后
 * 关闭本弹窗、切到专用会话「环境配置」，安装输出在**终端里**滚（不在弹窗里贴日志）。
 */
@Composable
fun EnvSetupDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    // 默认勾选：必备基础（认证/下载/版本控制）+ Python + Node —— Node 是"必须的环境"里
    // 用户点名的那个；其余按需。
    var selected by remember { mutableStateOf(DEFAULT_SETUP) }
    var detected by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val running = EnvProvision.running

    LaunchedEffect(Unit) {
        detected = withContext(Dispatchers.IO) { EnvProvision.detect(context, UBUNTU_COMPONENTS) }
    }
    // 安装结束（或中途刷新）后重新检测：已装的行变成「已安装」
    LaunchedEffect(running) {
        if (!running) detected = withContext(Dispatchers.IO) { EnvProvision.detect(context, UBUNTU_COMPONENTS) }
    }

    val toInstall = UBUNTU_COMPONENTS.filter { it.id in selected && detected[it.id] != true }

    PientDialog(
        title = "环境安装",
        onDismiss = onDone,
        confirmText = when {
            running -> "安装中…"
            toInstall.isEmpty() -> "完成"
            else -> "安装所选（${toInstall.size}）"
        },
        confirmEnabled = !running,
        onConfirm = {
            if (toInstall.isEmpty()) {
                onDone()
            } else {
                // 勾选集同步给「环境内软件」段（同一份状态，两处一致）
                SettingsStore.selectedComponents = selected
                val session = EnvProvision.installInTerminal(context, toInstall)
                val idx = PiTerminal.sessions.indexOf(session)
                if (idx >= 0) PientRuntime.chatState?.terminalIndex = idx
                onDone()
            }
        },
        extraActionText = if (running) null else "跳过",
        onExtraAction = onDone,
        showCancel = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 6.dp),
        ) {
            Text(
                "Ubuntu 环境已就绪。下面这些工具链不随包分发，勾选后装（之后也能在「环境配置 → 环境内软件」里装）；安装过程在终端页里跑。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            ComponentGroups.ORDER.forEach { group ->
                val list = UBUNTU_COMPONENTS.filter { it.group == group }
                if (list.isEmpty()) return@forEach
                Text(
                    group,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
                list.forEach { c ->
                    val installed = detected[c.id] == true
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !installed && !running) {
                                selected = if (c.id in selected) selected - c.id else selected + c.id
                            },
                    ) {
                        Checkbox(
                            checked = installed || c.id in selected,
                            enabled = !installed && !running,
                            onCheckedChange = {
                                selected = if (c.id in selected) selected - c.id else selected + c.id
                            },
                        )
                        Column(Modifier.padding(vertical = 2.dp)) {
                            Text(
                                if (installed) "${c.name} · 已安装" else c.name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                c.desc,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (running) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${EnvProvision.step.ifBlank { "安装中" }}——输出在终端页",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** 首启默认勾选：必备基础 + Python + Node（用户点名"必须的环境"） */
private val DEFAULT_SETUP = setOf("ca", "curl", "git", "python3", "nodejs")
