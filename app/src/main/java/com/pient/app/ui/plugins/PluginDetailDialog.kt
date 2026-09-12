package com.pient.app.ui.plugins

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pient.app.data.PluginItem
import com.pient.app.data.PluginResource
import com.pient.app.data.PluginResourceKind
import com.pient.app.data.PluginStatus
import com.pient.app.ui.components.MarkdownText
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.PientPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** pi-web PluginsConfig statusColor 的 amber（installed 状态） */
private val StatusAmber = Color(0xFFF59E0B)

/**
 * 插件详情弹窗（2026-09-08，参考技能详情弹窗 SkillDetailDialog + pi-web PackageDetail）：
 * ① 插件名称
 * ② 描述（package.json description；无描述整块隐藏）
 * ③ 「查看README.md」按键（无 README 时禁用）→ 点击展开/收起 Markdown 渲染的预览窗口
 * ④ 状态（pi-web 四态：已加载 primary / 已安装 amber / 已禁用 dim / 缺失 error；包禁用时显示「已禁用」）
 * ⑤ 版本（pi-web versionSummary：已安装 x · 已配置 y；均无显示「未知」）
 * ⑥ 资源（pi-web resourceSummary：N扩展 · N技能 · N提示词 · N主题；无资源显示「没有资源」）
 * ⑦ 来源（安装源 spec）
 * ⑧ 安装路径（pi 落盘规则：npm → npm/node_modules/<name>/；git/https → git/<host>/<path>/）
 * ⑨ 已解析资源（pi-web ResourceList 分组清单 + 逐项启停开关，对应 pi config 语义）
 * ⑩ 底部「更新」（mock 进度）「删除」+「关闭」。
 * 无右上角 ×（带关闭按钮的弹窗按全局原则不显示 ×）；点 scrim 空白处同样关闭。
 */
@Composable
fun PluginDetailDialog(
    item: PluginItem,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: () -> Unit,
) {
    var showReadme by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    var resources by remember(item.name) { mutableStateOf(item.resources) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // 「检查更新 → 更新」状态机（2026-09-10 用户要求）：默认「检查更新」；检查中按钮文字换成旋转圆弧；
    // 有更新 → 按钮变「更新」；无更新 → 文案不变 + Toast「已是最新版本 x」
    var checking by remember(item.name) { mutableStateOf(false) }
    var updateAvailable by remember(item.name) { mutableStateOf(false) }
    val busy = checking || updating   // 任一进行中：三个按键全部禁用，避免并发
    // 卡片最大高度 = 屏幕高 70%（与技能详情弹窗同款，2026-09-10）：资源清单/README 展开后
    // 内容会高于屏幕，超出的部分由内容区滚动承接（原先无上限，卡片会顶到屏幕上下缘）
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        PientPanel(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .heightIn(max = maxCardHeight)
                .padding(horizontal = 24.dp)
                .clickable(
                    onClick = {},
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ),
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(20.dp)) {
                // ① 插件名称
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFont),
                    color = MaterialTheme.colorScheme.onBackground,
                )

                // 中间内容（描述 / README / 状态 / 版本 / 资源 / 来源路径 / 已解析资源）——
                // 唯一可滚动区：卡片到达最大高度时只滚这里，名称与底部按键保持固定
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // ② 描述（无描述不显示）
                    if (item.desc.isNotBlank()) {
                        Text(
                            "描述",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            item.desc,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4,
                            ),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    // ③ 查看README.md 按键（无文件时禁用）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clickable(enabled = item.readmeMd != null) { showReadme = !showReadme },
                    ) {
                        Text(
                            "查看README.md",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (item.readmeMd != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Icon(
                            if (showReadme) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                            "展开/收起",
                            tint = if (item.readmeMd != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    // README.md 预览窗口（Markdown 渲染；点击「查看README.md」后出现）
                    if (showReadme && item.readmeMd != null) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth()
                                .height(240.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                    RoundedCornerShape(10.dp),
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(10.dp),
                                ),
                        ) {
                            MarkdownText(
                                markdown = item.readmeMd,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }

                    // ④ 状态（pi-web statusColor；包禁用时显示「已禁用」）
                    Text(
                        "状态",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        statusText(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor(item),
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    // ⑤ 版本（pi-web versionSummary：已安装 x · 已配置 y；均无显示「未知」）
                    Text(
                        "版本",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        versionSummary(item),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFont),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    // ⑥ 资源摘要（pi-web resourceSummary：N扩展 · N技能 · N提示词 · N主题）
                    Text(
                        "资源",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        resourceSummary(resources),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    // ⑦ 来源（安装源 spec）
                    Text(
                        "来源",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        item.source,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFont),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    // ⑧ 安装路径
                    Text(
                        "路径",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        pluginPath(item),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFont),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    // ⑨ 已解析资源（pi-web ResourceList 分组清单 + 逐项启停）
                    Text(
                        "已解析资源",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    if (resources.isEmpty()) {
                        Text(
                            if (!item.enabled) "包已禁用。" else "没有已解析资源",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        // pi-web ResourceList 分组顺序：扩展 → 技能 → 提示词 → 主题（仅非空组）
                        PluginResourceKind.entries.forEach { kind ->
                            val indexed = resources.mapIndexed { i, r -> i to r }
                                .filter { it.second.kind == kind }
                            if (indexed.isNotEmpty()) {
                                Text(
                                    kindLabel(kind),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                )
                                indexed.forEach { (i, r) ->
                                    ResourceRow(
                                        resource = r,
                                        enabled = r.enabled,
                                        onToggle = {
                                            resources = resources.toMutableList().also { list ->
                                                list[i] = list[i].copy(enabled = !list[i].enabled)
                                            }
                                        },
                                        modifier = Modifier.padding(vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // ⑩ 检查更新 / 更新 · 删除 · 关闭
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 三联等宽按钮行：每枚仅 ~97dp（窄屏更窄），须用小于组件默认 16dp 的内容内边距，
                    // 否则「检查更新」这类 4 字标签会被 Ellipsis 截断
                    PientButton(
                        text = if (updateAvailable) "更新" else "检查更新",
                        enabled = !busy,
                        loading = busy,   // 检查中 / 更新中：文字位置都换成旋转圆弧
                        primary = false,
                        contentPadding = 8,
                        onClick = {
                            if (updateAvailable) {
                                updating = true
                                scope.launch {
                                    delay(1100) // 原型更新进度（pi update <pkg> mock）
                                    onUpdate()
                                    updating = false
                                    updateAvailable = false // 更新完成 → 回到「检查更新」
                                }
                            } else {
                                checking = true
                                scope.launch {
                                    delay(1100) // 原型查询远端版本（npm view / git ls-remote mock）
                                    val latest = item.latestVersion
                                    val hasUpdate = latest != null && latest != item.version
                                    checking = false
                                    updateAvailable = hasUpdate
                                    if (!hasUpdate) {
                                        Toast.makeText(
                                            context,
                                            "已是最新版本" + (item.version?.let { " $it" } ?: ""),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    PientButton("删除", enabled = !busy, onClick = onDelete, contentPadding = 8, modifier = Modifier.weight(1f))
                    PientButton(
                        "关闭",
                        onClick = onDismiss,
                        primary = false,
                        contentPadding = 8,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** pi-web ResourceList 行：名称（等宽）+ 相对路径（等宽弱化）+ 启停开关 */
@Composable
private fun ResourceRow(
    resource: PluginResource,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                resource.name,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFont),
                color = if (enabled) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                resource.relativePath,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        // 注意：不能用 Modifier.size() 压缩 Switch——内部轨道仍按默认 52dp 绘制并居中
        // 溢出（见技能/插件卡片同款注释）
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

/** 状态文案（enabled=false 显示「已禁用」，与 pi-web 语义一致） */
private fun statusText(item: PluginItem): String = when {
    !item.enabled -> "已禁用"
    item.status == PluginStatus.LOADED -> "已加载"
    item.status == PluginStatus.INSTALLED -> "已安装"
    item.status == PluginStatus.DISABLED -> "已禁用"
    else -> "缺失"
}

/** 状态颜色（pi-web statusColor：loaded=accent、installed=amber、disabled=dim、missing=red） */
@Composable
private fun statusColor(item: PluginItem): Color {
    if (!item.enabled || item.status == PluginStatus.DISABLED) {
        return MaterialTheme.colorScheme.onSurfaceVariant
    }
    return when (item.status) {
        PluginStatus.LOADED -> MaterialTheme.colorScheme.primary
        PluginStatus.INSTALLED -> StatusAmber
        PluginStatus.MISSING -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** 版本摘要（pi-web versionSummary：已安装 x · 已配置 y；均无 → 未知） */
private fun versionSummary(item: PluginItem): String {
    val parts = buildList {
        item.version?.let { add("已安装 $it") }
        item.configuredVersion?.let { add("已配置 $it") }
    }
    return if (parts.isEmpty()) "未知" else parts.joinToString(" · ")
}

/** 资源摘要（pi-web resourceSummary：N扩展 · N技能 · N提示词 · N主题；空 → 没有资源） */
private fun resourceSummary(resources: List<PluginResource>): String {
    val parts = buildList {
        val ext = resources.count { it.kind == PluginResourceKind.EXTENSION }
        val skill = resources.count { it.kind == PluginResourceKind.SKILL }
        val prompt = resources.count { it.kind == PluginResourceKind.PROMPT }
        val theme = resources.count { it.kind == PluginResourceKind.THEME }
        if (ext > 0) add("${ext}扩展")
        if (skill > 0) add("${skill}技能")
        if (prompt > 0) add("${prompt}提示词")
        if (theme > 0) add("${theme}主题")
    }
    return if (parts.isEmpty()) "没有资源" else parts.joinToString(" · ")
}

/** 资源类型分组标题（pi-web i18n：extensions/skills/prompts/themes） */
private fun kindLabel(kind: PluginResourceKind): String = when (kind) {
    PluginResourceKind.EXTENSION -> "扩展"
    PluginResourceKind.SKILL -> "技能"
    PluginResourceKind.PROMPT -> "提示词"
    PluginResourceKind.THEME -> "主题"
}

/**
 * 插件安装路径（pi-0.85.1 package-manager 落盘规则）：
 * - npm: 全局 ~/.pi/agent/npm/node_modules/<name>/；项目 .pi/npm/node_modules/<name>/
 * - git:/https:// 全局 ~/.pi/agent/git/<host>/<path>/；项目 .pi/git/<host>/<path>/
 * - 本地路径：原样保留
 */
private fun pluginPath(item: PluginItem): String {
    val base = if (item.global) "~/.pi/agent" else ".pi"
    val s = item.source.trim()
    return when {
        s.startsWith("npm:") -> "$base/npm/node_modules/${npmName(s.removePrefix("npm:"))}/"
        s.startsWith("git:") -> {
            val rest = s.removePrefix("git:").replaceFirst("ssh://", "")
            // git@host:path 保留 @；末尾 @ref 版本引用剥掉
            val path = if (rest.startsWith("git@")) rest else rest.substringBefore('@')
            "$base/git/$path/"
        }
        s.startsWith("https://") || s.startsWith("http://") -> {
            val path = s.substringAfter("://").trimEnd('/')
            "$base/git/$path/"
        }
        else -> s
    }
}

/** npm spec 取包名：@scope/name@ver → @scope/name；name@ver → name */
private fun npmName(spec: String): String =
    if (spec.startsWith("@")) {
        val parts = spec.split('/')
        if (parts.size >= 2) parts[0] + "/" + parts[1].substringBefore('@') else spec
    } else spec.substringBefore('@')
