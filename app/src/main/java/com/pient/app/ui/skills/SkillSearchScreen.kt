package com.pient.app.ui.skills

import com.pient.app.data.i18n.L
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.runtime.PiSkills
import com.pient.app.runtime.PiSkillsMarket
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.components.PientSegmented
import com.pient.app.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 技能搜索页（**真数据层**）：
 *
 * - **市场结果** = skills.sh（`GET /api/search`，失败回落
 *   `npx skills find`），见 [PiSkillsMarket]；
 * - **安装** = `npx skills add <包> -y --agent pi [-g]` —— `--agent pi` 让 skills CLI 按
 *   pi 的目录约定落地（项目作用域 `.agents/skills`、全局 `~/.agents/skills`），
 *   安装过程在终端页「技能市场」会话里可见（有报错就去那里看原文）；
 * - **已安装（本地匹配）** = 直接扫盘 pi 的技能目录（[PiSkills]：`~/.pi/agent/skills`、
 *   `~/.agents/skills`、项目 `.pi/skills`、`.agents/skills`）。
 *
 * 页面结构：搜索行 + 分组列表。
 */
@Composable
fun SkillSearchScreen(nav: NavController) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf<String?>(null) }     // 正在安装的包标识
    val scope = rememberCoroutineScope()

    var marketResults by remember { mutableStateOf<List<PiSkillsMarket.Hit>>(emptyList()) }
    var note by remember { mutableStateOf<String?>(null) }

    /** 安装落点：true = 全局（`-g`，~/.agents/skills）/ false = 项目（当前项目的 .agents/skills） */
    var global by remember { mutableStateOf(true) }

    /** 本地已安装技能（真扫盘；安装完会重扫） */
    var localSkills by remember { mutableStateOf<List<PiSkills.Local>>(emptyList()) }
    fun rescanLocal() {
        scope.launch { localSkills = withContext(Dispatchers.IO) { PiSkills.list(context) } }
    }
    LaunchedEffect(Unit) { rescanLocal() }

    // 市场搜索（真网络；失败时把原因如实写在 note 里）
    LaunchedEffect(searched) {
        if (searched.isBlank()) {
            marketResults = emptyList()
            note = null
            return@LaunchedEffect
        }
        searching = true
        val (hits, err) = PiSkillsMarket.search(context, searched)
        marketResults = hits
        note = err
        searching = false
    }

    val localResults = localSkills.filter {
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
                Icons.Outlined.ArrowBack, L.common.back,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = { nav.popBackStack() }),
            )
            Text(
                L.skills.searchTitle,
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
                    .height(44.dp) // 与右侧 L.common.search 按钮（height 44）等高
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
                                L.skills.searchPlaceholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        inner()
                    },
                )
            }
            // 搜索按钮：与输入框同高 44dp；宽度固定 72dp 以对齐输入框行
            //（PientButton 自身已带左右 20dp 内边距）
            PientButton(
                if (searching) L.skills.searching else L.common.search,
                onClick = { searched = query },
                height = 44,
                enabled = !searching,
                modifier = Modifier.padding(start = 10.dp).width(72.dp),
            )
        }

        // 安装落点选择（与插件页同一套组件；pi 的两处技能目录）
        PientSegmented(
            labels = listOf(L.skills.installGlobal, L.skills.installProject),
            selected = if (global) 0 else 1,
            onSelect = { global = it == 0 },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Text(
            if (global) L.skills.globalAgentsPath else L.skills.projectAgentsPath,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (searched.isBlank()) {
                item {
                    Text(
                        L.skills.marketHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (searching) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text(
                            L.skills.searchingMarket,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
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
                    Text(L.skills.marketResults, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                items(marketResults, key = { it.pkg }) { hit ->
                    MarketSkillRow(
                        hit = hit,
                        installing = installing == hit.pkg,
                        onInstall = {
                            installing = hit.pkg
                            note = L.skills.installStarted(hit.pkg, PiSkillsMarket.SESSION)
                            PiSkillsMarket.install(context, hit.pkg, global) { code ->
                                installing = null
                                note = if (code == 0) {
                                    L.skills.installedNote(hit.name, if (global) L.common.global else L.common.project)
                                } else {
                                    L.skills.installFailed(code, PiSkillsMarket.SESSION)
                                }
                                rescanLocal()
                            }
                        },
                    )
                }
            }
            if (localResults.isNotEmpty()) {
                item {
                    Text(L.skills.installedLocal, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                items(localResults, key = { "l-${it.global}-${it.name}-${it.relPath}" }) { skill ->
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
                        if (!skill.enabled) {
                            Text(
                                L.skills.disabledBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                        Icon(
                            Icons.Outlined.Check, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            L.common.installed,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (searched.isNotBlank() && !searching && marketResults.isEmpty() && localResults.isEmpty()) {
                item {
                    Text(
                        L.skills.noResults(searched),
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
    hit: PiSkillsMarket.Hit,
    installing: Boolean,
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
            Text(hit.label, style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFont))
            Text(
                hit.pkg,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFont),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (hit.installs.isNotBlank()) {
                Text(
                    hit.installs,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (installing) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    .clickable(onClick = onInstall)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(L.common.install, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
