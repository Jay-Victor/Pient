package com.pient.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.ui.components.SectionHeader

/**
 * 权限中心（P8，设计计划第 7 章，v1 交付；本文件为 UI 骨架实现）。
 * 结构：三级权限状态卡（标准/Shizuku/Root，当前档位高亮，可切换）
 * + 工具级授权列表（七工具 × 三档 ALLOW/ASK/FORBID）。
 * 按设计"设置页仅三项入口"，本页 v1 时由设置页接入；首启形态 = P10 引导页。
 */
@Composable
fun PermissionsCenterScreen(nav: NavController) {
    var level by remember { mutableIntStateOf(0) }
    // 七工具默认：read/write/edit/ls ASK，bash/find/grep ALLOW（示例）
    val toolLevels = remember {
        mutableStateMapOf(
            "read" to 1, "write" to 1, "edit" to 1,
            "bash" to 0, "find" to 0, "grep" to 0, "ls" to 1,
        )
    }
    val levels = listOf("ALLOW", "ASK", "FORBID")

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
                "权限中心",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            // ── 三级权限状态卡 ──
            item { SectionHeader("权限档位（标准 / Shizuku / Root）") }
            item {
                val options = listOf(
                    Triple("标准权限", "L0 · 网络/存储/通知/无障碍服务", "当前"),
                    Triple("调试权限 (Shizuku)", "L1 · ADB 级：UI 自动化/应用管理", "未连接"),
                    Triple("Root 权限", "L2 · chroot / 最高级系统操作", "⚠ 未检测"),
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEachIndexed { i, opt ->
                        val sel = i == level
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                    RoundedCornerShape(16.dp),
                                )
                                .border(
                                    if (sel) 1.5.dp else 1.dp,
                                    if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable(onClick = { level = i })
                                .padding(14.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    opt.first,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    opt.second,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Text(
                                opt.third,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box(
                                Modifier
                                    .padding(start = 10.dp)
                                    .size(16.dp)
                                    .border(1.5.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .padding(3.dp),
                            ) {
                                if (sel) {
                                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                }
                            }
                        }
                    }
                }
            }

            // ── 工具级授权列表 ──
            item { SectionHeader("工具级授权（七工具 × ALLOW/ASK/FORBID）") }
            item {
                Text(
                    "Operit tool_permissions 语义：允许/禁止分组例外（从分组移除=恢复跟随全局）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            items(toolLevels.keys.toList(), key = { it }) { tool ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(tool, style = MaterialTheme.typography.labelLarge.copy(fontFamily = com.pient.app.ui.theme.MonoFont), modifier = Modifier.weight(1f))
                    levels.forEachIndexed { i, lvName ->
                        val sel = toolLevels[tool] == i
                        Text(
                            lvName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .clickable(onClick = { toolLevels[tool] = i }),
                        )
                    }
                }
            }
        }
    }
}
