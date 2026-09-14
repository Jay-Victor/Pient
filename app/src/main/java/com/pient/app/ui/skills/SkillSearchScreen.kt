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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.MockStore
import com.pient.app.data.SkillItem
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.theme.MonoFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 技能搜索页（**UI 壳**，2026-09-14：市场请求 / 安装链路已移除）：
 * 结果分两组：市场结果（占位数据，原 skills.sh 形态）+ 本地已安装匹配（[MockStore]）。
 * 「安装」只把条目标记为已安装（内存态），不写盘、不联网。
 */
@Composable
fun SkillSearchScreen(nav: NavController) {
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf("") }
    var installing by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 市场结果：占位数据按关键词过滤（原 skills.sh 请求已移除）
    var marketResults by remember { mutableStateOf<List<SkillItem>>(emptyList()) }
    var note by remember { mutableStateOf<String?>(null) }
    // 本页内「已安装」集合（把市场条目标记为已安装；内存态，重启即重置）
    var installedNames by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(searched) {
        if (searched.isBlank()) {
            marketResults = emptyList()
            note = null
            return@LaunchedEffect
        }
        val r = MockStore.marketSkills.filter { it.name.contains(searched, ignoreCase = true) }
        marketResults = r
        note = if (r.isEmpty()) "没有匹配的技能（占位数据里没有）" else null
    }

    val localResults = (MockStore.globalSkills + MockStore.projectSkills).filter {
        searched.isNotBlank() && it.name.contains(searched, ignoreCase = true)
    }

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
                "搜索技能",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        // 搜索框 + 按钮（回车即搜）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp) // 与右侧"搜索"按钮（height 44）等高（2026-08-27 对齐）
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp),
            ) {
                Icon(
                    Icons.Outlined.Search, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { searched = query },
                    ),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                "输入技能名或关键词…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        inner()
                    },
                )
            }
            // 搜索按钮：与输入框同高 44dp；宽度固定 72dp 以对齐输入框行
            //（PientButton 自身已带左右 20dp 内边距，2026-09-12 起）
            PientButton(
                "搜索",
                onClick = { searched = query },
                height = 44,
                modifier = Modifier.padding(start = 10.dp).width(72.dp),
            )
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (searched.isBlank()) {
                item {
                    Text(
                        "输入关键词后搜索技能市场（skills.sh）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            note?.let { text ->
                item {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (marketResults.isNotEmpty()) {
                item {
                    Text("市场结果（skills.sh）：", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                items(marketResults, key = { it.marketId ?: it.name }) { skill ->
                    MarketSkillRow(
                        skill = skill,
                        installing = installing == skill.name,
                        installed = skill.name in installedNames,
                        onInstall = {
                            installing = skill.name
                            scope.launch {
                                // 占位安装：模拟一次进度，然后标记「已安装」（不写盘、不联网）
                                delay(700)
                                installedNames = installedNames + skill.name
                                installing = null
                                note = "已安装「${skill.name}」（占位数据，仅界面演示）"
                            }
                        },
                    )
                }
            }
            if (localResults.isNotEmpty()) {
                item {
                    Text("已安装（本地匹配）：", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                items(localResults, key = { "l-${it.global}-${it.name}" }) { skill ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Text(
                            skill.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFont),
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Outlined.Check, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "已安装",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (searched.isNotBlank() && marketResults.isEmpty() && localResults.isEmpty()) {
                item {
                    Text(
                        "没有找到与「$searched」相关的技能",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketSkillRow(
    skill: SkillItem,
    installing: Boolean,
    installed: Boolean,
    onInstall: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(skill.name, style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFont))
            Text(
                skill.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        when {
            installing -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            installed -> {
                Icon(
                    Icons.Outlined.Check, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "已安装",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            else -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    .clickable(onClick = onInstall)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("安装", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
