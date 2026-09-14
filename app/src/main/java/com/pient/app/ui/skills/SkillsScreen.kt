package com.pient.app.ui.skills

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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import com.pient.app.data.SkillItem
import com.pient.app.data.SkillStore
import com.pient.app.runtime.PiHostService
import com.pient.app.ui.components.HostRestartHint
import com.pient.app.ui.components.PientSegmented
import com.pient.app.ui.theme.MonoFont

/**
 * 技能管理（P7，设计计划第 4 章，pi-web SkillsConfig 参考）：
 * 全局/项目分段（~/.pi/agent/skills/ ↔ .pi/skills/）；列表 = 名称+描述+开关
 * （disable-model-invocation 外科手术式修改）；右下双 FAB：搜索 + 导入。
 * 2026-09-06：点击技能卡片弹出详情弹窗（SKILL.md 内容/路径/删除+关闭）。
 */
@Composable
fun SkillsScreen(nav: NavController) {
    var segment by remember { mutableStateOf(0) }
    var importOpen by remember { mutableStateOf(false) }
    var detailFor by remember { mutableStateOf<SkillItem?>(null) }
    val context = LocalContext.current
    // 真实数据：进页面即扫盘（全局 = ~/.pi/agent/skills 等；项目 = 工作区 .pi/skills）
    LaunchedEffect(Unit) { SkillStore.refresh(context) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 页头
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
                    "技能管理",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            // 分段控制器
            PientSegmented(
                labels = listOf("全局", "项目"),
                selected = segment,
                onSelect = { segment = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Text(
                if (segment == 0) "~/.pi/agent/skills/ · ~/.agents/skills/" else "当前项目 .pi/skills/ · .agents/skills/",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )

            // 改了技能/装完新技能 → pi 要重启才会重新装配（提示 + 一键重启）
            if (SkillStore.needsHostRestart) {
                HostRestartHint("技能改动需重启宿主后才被 pi 加载") {
                    PiHostService.restart(context)
                    SkillStore.markHostRestarted()
                }
            }

            // 技能列表（真实扫描结果）
            val list = if (segment == 0) SkillStore.global else SkillStore.project
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 90.dp,
                ),
            ) {
                items(list.size, key = { i -> list[i].name }) { i ->
                    SkillRow(
                        list[i],
                        onClick = { detailFor = list[i] },
                        onToggle = { on -> SkillStore.setEnabled(context, list[i], on) },
                    )
                }
                if (list.isEmpty() && !SkillStore.loading) {
                    item {
                        Text(
                            if (segment == 0)
                                "未发现全局技能。点右下「导入」新建，或用「搜索」从 skills.sh 安装。"
                            else
                                "当前项目没有技能。项目技能放在工作区的 .pi/skills/（pi 侧需项目被信任才加载）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                SkillStore.lastError?.let { err ->
                    item {
                        Text(
                            "诊断：$err",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        // 右下双 FAB（纵向叠放：搜索 + 导入）
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable(onClick = { nav.navigate("skill_search") }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Search, "搜索技能", tint = MaterialTheme.colorScheme.onPrimary)
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable(onClick = { importOpen = true }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Download, "导入技能", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }

    // 导入弹窗
    if (importOpen) {
        ImportSkillDialog(
            global = segment == 0,
            onDismiss = { importOpen = false },
            onImported = { name, desc, md ->
                // 真实落盘：写 <全局|项目> 技能目录下的 <name>/SKILL.md，然后重扫
                val ok = SkillStore.createSkill(context, name, desc, md, globalScope = segment == 0)
                toast(context, if (ok) "已导入技能 $name" else "导入失败：写入技能目录失败")
                importOpen = false
            },
        )
    }

    // 技能详情弹窗（2026-09-06：点技能卡片弹出；删除 = 从列表移除技能及全部文件）
    detailFor?.let { item ->
        SkillDetailDialog(
            item = item,
            onDismiss = { detailFor = null },
            onDelete = {
                val ok = SkillStore.delete(context, item)
                toast(context, if (ok) "已删除技能 ${item.name}" else "删除失败：技能目录不可写")
                detailFor = null
            },
        )
    }
}

@Composable
private fun SkillRow(item: SkillItem, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onClick),
        ) {
            Text(
                item.name,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFont),
                color = if (item.enabled) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                item.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // 开关状态文字"开/关"已移除（用户反馈纯多余，2026-08-27）
        // 注意：不能用 Modifier.size() 压缩 Switch——内部轨道仍按默认 52dp 绘制并居中
        // 溢出，会向左侵入内容文字造成视觉重叠（实测溢出 ~10dp）
        Switch(
            checked = item.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

private fun toast(context: android.content.Context, msg: String) {
    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
}
