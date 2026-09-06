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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.MockStore
import com.pient.app.data.SkillItem
import com.pient.app.ui.components.PientSegmented
import com.pient.app.ui.theme.MonoFont

/**
 * 技能管理（P7，设计计划第 4 章，pi-web SkillsConfig 参考）：
 * 全局/项目分段（~/.pi/agent/skills/ ↔ .pi/skills/）；列表 = 名称+描述+开关
 * （disable-model-invocation 外科手术式修改）；右下双 FAB：搜索 + 导入。
 */
@Composable
fun SkillsScreen(nav: NavController) {
    var segment by remember { mutableStateOf(0) }
    var importOpen by remember { mutableStateOf(false) }

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
                if (segment == 0) "~/.pi/agent/skills/" else "当前项目 .pi/skills/",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )

            // 技能列表
            val list = if (segment == 0) MockStore.globalSkills else MockStore.projectSkills
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 90.dp,
                ),
            ) {
                items(list.size, key = { i -> list[i].name }) { i ->
                    SkillRow(list[i])
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
            onImported = { name, desc ->
                val item = SkillItem(name, desc, enabled = true, global = segment == 0)
                if (segment == 0) MockStore.globalSkills.add(0, item)
                else MockStore.projectSkills.add(0, item)
                importOpen = false
            },
        )
    }
}

@Composable
private fun SkillRow(item: SkillItem) {
    var enabled by remember(item.name) { mutableStateOf(item.enabled) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFont),
                color = if (enabled) MaterialTheme.colorScheme.onBackground
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
            checked = enabled,
            onCheckedChange = { enabled = it },
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}
