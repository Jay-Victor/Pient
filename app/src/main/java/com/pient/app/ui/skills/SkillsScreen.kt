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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.SkillItem
import com.pient.app.runtime.PiSkills
import com.pient.app.ui.components.PientSegmented
import com.pient.app.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 技能管理（**真数据层**，2026-09-15 接回 pi）：
 *
 * - 列表 = **扫盘**（[PiSkills.list]）：全局 `~/.pi/agent/skills/`+`~/.agents/skills/`、
 *   项目 `<工作区>/.pi/skills/`+`.agents/skills/`，按 pi 的发现规则认技能（含 SKILL.md 的目录、
 *   根级带 frontmatter 的 .md），frontmatter 里读 name/description；
 * - 开关 = **真停用/启用**：把技能在 `<root>/<name>` 与 `<root>/.disabled/<name>` 之间挪动
 *   （pi 的扫描器跳过 `.` 开头的条目，所以停用后 pi 立刻不再加载，文件一个不删）；
 * - 删除 = 真删文件；导入 = 真写 `SKILL.md`（校验 frontmatter 与 description，与 pi 一致）。
 */
@Composable
fun SkillsScreen(nav: NavController) {
    var segment by remember { mutableStateOf(0) }
    var importOpen by remember { mutableStateOf(false) }
    var detailFor by remember { mutableStateOf<SkillItem?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── 真数据：磁盘上的技能（全局 + 项目；启停都是落盘操作） ──
    var skills by remember { mutableStateOf<List<PiSkills.Local>>(emptyList()) }
    var loadedOnce by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    fun reload() {
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { PiSkills.list(context) } }
            skills = result.getOrElse {
                scanError = it.message ?: "扫描失败"; emptyList()
            }
            scanError = null
            loadedOnce = true
        }
    }
    LaunchedEffect(Unit) { reload() }
    val byPath = remember(skills) { skills.associateBy { it.file.absolutePath } }
    val rows = remember(skills, segment) {
        skills.filter { it.global == (segment == 0) }.map { it.toItem() }
    }

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

            // 技能列表（扫盘结果；开关落盘后重扫）
            val list = rows
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 90.dp,
                ),
            ) {
                items(list.size, key = { i -> list[i].path ?: list[i].name }) { i ->
                    val row = list[i]
                    SkillRow(
                        row,
                        onClick = { detailFor = row },
                        onToggle = { on ->
                            val local = row.path?.let { byPath[it] }
                            if (local == null) {
                                toast(context, "找不到技能文件")
                            } else {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { PiSkills.setEnabled(local, on) }
                                    toast(context, if (ok) (if (on) "已启用 ${local.name}" else "已停用 ${local.name}") else "操作失败")
                                    reload()
                                }
                            }
                        },
                    )
                }
                if (list.isEmpty()) {
                    item {
                        Text(
                            when {
                                scanError != null -> "扫描失败：$scanError"
                                !loadedOnce -> "正在扫描技能目录…"
                                segment == 0 -> "还没有全局技能。点右下「导入」新建，或用「搜索」查看技能市场。"
                                else -> "当前项目没有技能（.pi/skills 或 .agents/skills 里放 SKILL.md 即可）。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
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
                // 真写盘：<skills>/<slug>/SKILL.md（frontmatter 校验与 pi 一致）
                val body = md?.takeIf { it.isNotBlank() } ?: "# $name\n\n$desc"
                val skillMd = "---\nname: $name\ndescription: $desc\n---\n\n$body"
                scope.launch {
                    val err = withContext(Dispatchers.IO) {
                        PiSkills.import(context, name, skillMd, global = segment == 0)
                    }
                    toast(context, err ?: "已导入技能 $name")
                    if (err == null) {
                        importOpen = false
                        reload()
                    }
                }
            },
            // ZIP 页签（2026-09-16 真实化）：SAF 选的 zip → 解压 + 校验 + 整目录落盘
            onImportZip = { uri ->
                scope.launch {
                    val (err, slug) = withContext(Dispatchers.IO) {
                        PiSkills.importZip(context, uri, global = segment == 0)
                    }
                    toast(context, err ?: "已导入技能 $slug")
                    if (err == null) {
                        importOpen = false
                        reload()
                    }
                }
            },
        )
    }

    // 技能详情弹窗（2026-09-06：点技能卡片弹出；删除 = 从列表移除技能及全部文件）
    detailFor?.let { item ->
        SkillDetailDialog(
            item = item,
            onDismiss = { detailFor = null },
            onDelete = {
                val local = item.path?.let { byPath[it] }
                scope.launch {
                    val ok = local != null && withContext(Dispatchers.IO) { PiSkills.delete(local) }
                    toast(context, if (ok) "已删除技能 ${item.name}" else "删除失败")
                    detailFor = null
                    reload()
                }
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

/** 磁盘上的技能 → 列表/详情用的展示模型（skillMd 与文件树都是现读的真内容） */
private fun PiSkills.Local.toItem(): SkillItem = SkillItem(
    name = name,
    desc = problem?.let { "⚠ $it" } ?: desc,
    enabled = enabled,
    global = global,
    skillMd = content(),
    fileTree = PiSkills.fileTree(this),
    path = file.absolutePath,
)
