package com.pient.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.tools.ToolGate
import com.pient.app.tools.ToolLayer
import com.pient.app.tools.ToolPackageState
import com.pient.app.tools.ToolPkgLoader
import com.pient.app.tools.ToolRegistry

/**
 * **工具页** —— 工具层的唯一可视化入口（2026-09-14）：
 *
 * 页面的组织方式就是架构本身：**层（工具在哪跑）→ 包（谁声明的）→ 工具（AI 能用什么）**。
 * - 层：四层各自的就绪状态（层① 要 Shizuku/Root、层② 要 rootfs、层③/④ 始终就绪）；
 * - 包：每个包的启停（关掉即不再下发给模型）、来源（内置/用户）、真实落点路径；
 * - 工具：名字 + 中文标签 + 描述（就是模型收到的那段），参数 schema 不在页面重复渲染。
 *
 * 授权（ToolGate）不在这里改 —— 那是「系统权限设置」页的职责（单一写入点），这里只给跳转。
 */
@Composable
fun ToolsScreen(nav: NavController) {
    val context = LocalContext.current
    var packages by remember { mutableStateOf(ToolPkgLoader.packages(context)) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    /** 关/开一个包（写盘后强制重载，页面与下发清单立即一致） */
    fun toggle(pkg: ToolPackageState, on: Boolean) {
        ToolPkgLoader.setEnabled(context, pkg.pkg.name, on)
        packages = ToolPkgLoader.packages(context, force = true)
        Toast.makeText(
            context,
            if (on) "已启用「${pkg.pkg.displayName}」" else "已停用「${pkg.pkg.displayName}」",
            Toast.LENGTH_SHORT,
        ).show()
    }

    val enabledTools = packages.filter { it.enabled }.flatMap { it.effectiveTools }
    val layerCount = ToolLayer.entries.count { layer -> enabledTools.any { ToolRegistry.layerOf(context, it.name) == layer } }

    Column(Modifier.fillMaxSize()) {
        // ── 顶栏（与其他设置二级页一致） ──
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
                modifier = Modifier.size(24.dp).clickable(onClick = { nav.popBackStack() }),
            )
            Text("工具", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // ── 概览：层 × 包 × 工具 ──
            GroupHeader("工具层", "$layerCount 层 · ${packages.count { it.enabled }} 个包 · ${enabledTools.size} 个工具")
            Card {
                ToolLayer.entries.forEachIndexed { i, layer ->
                    if (i > 0) CardDivider()
                    LayerRow(context, layer)
                }
            }

            // ── 包清单 ──
            GroupHeader("工具包", "声明工具的地方；执行体是四层 Kotlin 实现")
            packages.forEach { st ->
                Card {
                    PackageHead(st, expanded = st.pkg.name in expanded, onToggle = { toggle(st, it) }) {
                        expanded = if (st.pkg.name in expanded) expanded - st.pkg.name else expanded + st.pkg.name
                    }
                    if (st.pkg.name in expanded) {
                        st.effectiveTools.forEach { t ->
                            CardDivider()
                            ToolRow(t.name, t.label, t.description)
                        }
                        if (st.effectiveTools.isEmpty()) {
                            CardDivider()
                            Text(
                                "（此包未声明可用工具）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── 用户包入口与边界说明 ──
            GroupHeader("用户包", null)
            Card {
                NoteRow(
                    "把包文件放到应用私有目录 toolpkg/ 下即可被扫描（与内置包同一格式）。" +
                        "当前版本只支持「声明 + 调度」——包里的 JS 实现体尚未接线，用户包的工具会走内置执行体（同名即接管）。",
                )
                CardDivider()
                val dir = remember { java.io.File(context.filesDir, "toolpkg").absolutePath }
                NoteRow("落点：$dir", mono = true)
                CardDivider()
                val warns = ToolPkgLoader.warnings
                if (warns.isEmpty()) {
                    NoteRow("装载诊断：无（${packages.size} 个包全部解析成功）")
                } else {
                    warns.forEach { NoteRow("装载诊断：$it", warn = true) }
                }
                CardDivider()
                val defaultPolicy = remember { ToolGate.snapshot(context).first }
                NoteRow(
                    "工具级授权（每个工具问不问用户）在「系统权限设置」里改；本页只负责「有哪些工具」。" +
                        "当前默认策略：${policyLabelText(defaultPolicy)}",
                    onClick = { nav.navigate("system_permissions") },
                )
            }
        }
    }
}

/* ────────────────────────── 页面局部组件 ────────────────────────── */

/** 分组标题（与设置页 SettingsGroup 的标题口径一致：卡片外上方） */
@Composable
private fun GroupHeader(title: String, subtitle: String?) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** 卡片容器（与设置页同款：surfaceContainerLow + 16dp 圆角 + 描边） */
@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) { content() }
}

@Composable
private fun CardDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
}

/** 层图标：① 系统命令（Shield）② 终端（Terminal）③ 自身工具（Build）④ 扩展（Extension） */
private fun layerIcon(layer: ToolLayer): ImageVector = when (layer) {
    ToolLayer.SYSTEM -> Icons.Outlined.Shield
    ToolLayer.TERMINAL -> Icons.Outlined.Terminal
    ToolLayer.APP -> Icons.Outlined.Build
    ToolLayer.EXTENSION -> Icons.Outlined.Extension
}

/** 一层一行：图标 + 层名 + 该层工具名 + 就绪状态 */
@Composable
private fun LayerRow(context: android.content.Context, layer: ToolLayer) {
    val part = ToolRegistry.partOf(layer)
    val tools = ToolRegistry.specs(context).filter { it.layer == layer }
    val notReady = part.notReady(context)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(layerIcon(layer), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(layer.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                layer.desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
            Text(
                if (tools.isEmpty()) "（该层无可用工具）" else tools.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 3.dp),
            )
            Text(
                notReady ?: "就绪",
                style = MaterialTheme.typography.labelSmall,
                color = if (notReady == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** 包表头：名称 + 来源 + 副标题（描述）+ 启停开关（整行可点 = 展开/收起） */
@Composable
private fun PackageHead(
    st: ToolPackageState,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.pkg.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Badge(if (st.pkg.builtIn) "内置" else "用户")
            }
            Text(
                "${st.pkg.category} · ${st.effectiveTools.size} 个工具 · ${if (expanded) "收起" else "展开"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Switch(checked = st.enabled, onCheckedChange = onToggle)
    }
}

/** 工具一行：名字（等宽）+ 中文标签 + 描述（就是下发给模型的那段） */
@Composable
private fun ToolRow(name: String, label: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 说明行（可点则带跳转） */
@Composable
private fun NoteRow(text: String, mono: Boolean = false, warn: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (mono) {
            Icon(
                Icons.Outlined.Folder, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text,
            style = if (mono) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 小徽标（内置/用户） */
@Composable
private fun Badge(text: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 授权档位的中文口径（与 SystemPermissionScreen 一致：ASK 询问 / ALLOW 允许 / DENY 拒绝） */
private fun policyLabelText(policy: String): String = when (policy) {
    "ASK" -> "询问"
    "ALLOW" -> "允许"
    "DENY" -> "拒绝"
    else -> policy
}
