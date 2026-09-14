package com.pient.app.ui.terminal

import com.pient.app.data.UbuntuComponent
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
import android.widget.Toast
import com.pient.app.data.ComponentGroups
import com.pient.app.data.SettingsStore
import com.pient.app.data.UBUNTU_COMPONENTS
import com.pient.app.ui.components.PientDialog

/**
 * 首启「环境安装」弹窗（UI 壳，2026-09-14：安装链路已移除，只保留界面与勾选交互）。
 *
 * 定位（用户 2026-09-13 拍板）：**首启进终端页弹一次、可跳过**。
 * 与「环境配置 → 环境内软件」是同一份清单（[UBUNTU_COMPONENTS]）、同一个分类
 * （[ComponentGroups]）；点「安装所选」当前只同步勾选状态并关闭（不会真正安装）。
 */
@Composable
fun EnvSetupDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    // 默认勾选：必备基础（认证/下载/版本控制）+ Python + Node
    var selected by remember { mutableStateOf(DEFAULT_SETUP) }
    val toInstall = UBUNTU_COMPONENTS.filter { it.id in selected }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    PientDialog(
        title = "环境安装",
        onDismiss = onDone,
        confirmText = if (toInstall.isEmpty()) "完成" else "安装所选（${toInstall.size}）",
        confirmEnabled = true,
        onConfirm = {
            if (toInstall.isEmpty()) {
                onDone()
            } else {
                // 勾选集同步给「环境内软件」段（同一份状态，两处一致；安装本身已停用）
                SettingsStore.selectedComponents = selected
                toast("安装功能已停用（当前版本仅保留界面）")
                onDone()
            }
        },
        extraActionText = "跳过",
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
                "这些工具链原用于 Ubuntu 环境（当前版本已移除安装链路，仅保留勾选界面）。",
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (c.id in selected) selected - c.id else selected + c.id
                            },
                    ) {
                        Checkbox(
                            checked = c.id in selected,
                            onCheckedChange = {
                                selected = if (c.id in selected) selected - c.id else selected + c.id
                            },
                        )
                        Column(Modifier.padding(vertical = 2.dp)) {
                            Text(
                                c.name,
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
        }
    }
}

/** 首启默认勾选：必备基础 + Python + Node（用户点名"必须的环境"） */
private val DEFAULT_SETUP = setOf("ca", "curl", "git", "python3", "nodejs")
