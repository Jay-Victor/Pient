package com.pient.app.ui.settings

import android.content.Context
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * **工具页** —— 工具层的唯一可视化入口（2026-09-14）。
 *
 * 一页装两件事，且刻意分成上下两段（2026-09-14 用户拍板合并，原「工具权限」页并入本页）：
 * - **清单**（上面）：层（工具在哪跑）→ 包（谁声明的、开不开）→ 工具（AI 能用什么）；
 * - **策略**（下面）：调用时的授权（允许 / 每次询问 / 禁止），全局默认 + 逐工具例外。
 *
 * 为什么合成一页：两页面对的是**同一份工具清单**，只是看的角度不同（有什么 / 问不问）；
 * 分成两页会让人来回跳（改完包再跑另一页改授权）。段与段之间用分组标题隔开，
 * 各自的数据源仍是单一实现：清单来自工具包、策略来自 [ToolGate]（`pient_gate.json`）。
 *
 * 工具清单随包增减（[ToolRegistry.specs]，按层排序），不再维护手写工具说明表。
 */
@Composable
fun ToolsScreen(nav: NavController) {
    val context = LocalContext.current
    var packages by remember { mutableStateOf(ToolPkgLoader.packages(context)) }
    var expandedPkg by remember { mutableStateOf(setOf<String>()) }

    // ── 授权策略（原「工具权限」页） ──
    var toolDefault by remember { mutableStateOf(ToolGate.ASK) }
    var toolPolicies by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var expandedKey by remember { mutableStateOf<String?>(null) }

    fun reloadPolicies() {
        val (d, m) = ToolGate.snapshot(context)
        toolDefault = d
        toolPolicies = m
    }

    fun applyPolicy(tool: String?, policy: String) {
        if (tool == null) ToolGate.setDefault(context, policy) else ToolGate.setToolPolicy(context, tool, policy)
        reloadPolicies()
        expandedKey = null
        Toast.makeText(context, "已设为「${policyLabel(policy)}」", Toast.LENGTH_SHORT).show()
    }

    /** 关/开一个包（写盘后强制重载，页面与下发清单立即一致） */
    fun togglePkg(pkg: ToolPackageState, on: Boolean) {
        ToolPkgLoader.setEnabled(context, pkg.pkg.name, on)
        packages = ToolPkgLoader.packages(context, force = true)
        Toast.makeText(
            context,
            if (on) "已启用「${pkg.pkg.displayName}」" else "已停用「${pkg.pkg.displayName}」",
            Toast.LENGTH_SHORT,
        ).show()
    }

    LaunchedEffect(Unit) { reloadPolicies() }

    val specs = ToolRegistry.specs(context)
    val layers = ToolLayer.entries.filter { layer -> specs.any { it.layer == layer } }

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
            /* ═══════════ 一、工具清单 ═══════════ */

            // 概览：层 × 包 × 工具
            GroupHeader("工具层", "${layers.size} 层 · ${packages.count { it.enabled }} 个包 · ${specs.size} 个工具")
            Card {
                layers.forEachIndexed { i, layer ->
                    if (i > 0) CardDivider()
                    LayerRow(context, layer, specs)
                }
            }

            // 包清单（声明工具的地方；执行体是四层 Kotlin 实现）
            GroupHeader("工具包", "声明工具的地方；执行体是四层 Kotlin 实现")
            packages.forEach { st ->
                Card {
                    PackageHead(st, expanded = st.pkg.name in expandedPkg, onToggle = { togglePkg(st, it) }) {
                        expandedPkg = if (st.pkg.name in expandedPkg) expandedPkg - st.pkg.name else expandedPkg + st.pkg.name
                    }
                    if (st.pkg.name in expandedPkg) {
                        st.effectiveTools.forEach { t ->
                            CardDivider()
                            ToolRow(t.name, t.label, t.description)
                        }
                        if (st.effectiveTools.isEmpty()) {
                            CardDivider()
                            Hint(
                                "（此包未声明可用工具）",
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 用户包与装载诊断
            GroupHeader("用户包", null)
            Card {
                Hint(
                    "把包文件放到应用私有目录 toolpkg/ 下即可被扫描（与内置包同一格式）。" +
                        "当前版本只支持「声明 + 调度」——包里的 JS 实现体尚未接线，用户包的工具会走内置执行体（同名即接管）。",
                    modifier = Modifier.padding(14.dp),
                )
                CardDivider()
                val dir = remember { java.io.File(context.filesDir, "toolpkg").absolutePath }
                IconHint(Icons.Outlined.Folder, dir)
                CardDivider()
                val warns = ToolPkgLoader.warnings
                if (warns.isEmpty()) {
                    Hint("装载诊断：无（${packages.size} 个包全部解析成功）", modifier = Modifier.padding(14.dp))
                } else {
                    warns.forEach { Hint("装载诊断：$it", warn = true, modifier = Modifier.padding(14.dp)) }
                }
            }

            /* ═══════════ 二、调用策略（授权） ═══════════ */

            GroupHeader(
                "调用策略",
                "AI 调用工具前按这里执行；${specs.size} 个工具，当前默认「${policyLabel(toolDefault)}」",
            )
            Card {
                Hint(
                    "允许 = 直接执行；每次询问 = 弹三选授权；禁止 = 直接拦下并把原因回给模型。" +
                        "授权弹窗里的「始终允许」也写到这里（应用私有目录的 pient_gate.json）。",
                    modifier = Modifier.padding(14.dp),
                )
                CardDivider()
                PolicyRow(
                    title = "默认策略",
                    desc = "未单独设置的工具都按它执行",
                    policy = toolDefault,
                    expanded = expandedKey == DEFAULT_KEY,
                    onClick = { expandedKey = if (expandedKey == DEFAULT_KEY) null else DEFAULT_KEY },
                    onPick = { p -> applyPolicy(null, p) },
                )
            }

            // 逐工具（与上面的清单同一份、同一分层）
            layers.forEach { layer ->
                val layerSpecs = specs.filter { it.layer == layer }
                GroupHeader("${layer.title}层", layer.desc)
                Card {
                    layerSpecs.forEachIndexed { i, spec ->
                        if (i > 0) CardDivider()
                        PolicyRow(
                            title = spec.name,
                            desc = spec.label,
                            policy = toolPolicies[spec.name] ?: toolDefault,
                            expanded = expandedKey == spec.name,
                            onClick = { expandedKey = if (expandedKey == spec.name) null else spec.name },
                            onPick = { p -> applyPolicy(spec.name, p) },
                        )
                    }
                }
            }
        }
    }
}

/* ────────────────────────── 页面局部组件 ────────────────────────── */

private const val DEFAULT_KEY = "__default__"

private fun policyLabel(policy: String): String = when (policy) {
    ToolGate.ALLOW -> "允许"
    ToolGate.FORBID -> "禁止"
    else -> "每次询问"
}

/** 分组标题（卡片外上方；与设置页 SettingsGroup 同款口径） */
@Composable
private fun GroupHeader(title: String, subtitle: String?) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) {
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

/** 说明文字（可选错误色） */
@Composable
private fun Hint(text: String, warn: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}

/** 说明行（等宽 + 文件夹图标，用于真实落点路径） */
@Composable
private fun IconHint(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 层图标：① 系统命令（Shield）② 终端（Terminal）③ 自身工具（Build）④ 扩展（Extension） */
private fun layerIcon(layer: ToolLayer): ImageVector = when (layer) {
    ToolLayer.SYSTEM -> Icons.Outlined.Shield
    ToolLayer.TERMINAL -> Icons.Outlined.Terminal
    ToolLayer.APP -> Icons.Outlined.Build
    ToolLayer.EXTENSION -> Icons.Outlined.Extension
}

/** 一层一行：图标 + 层名 + 层说明 + 该层工具名 + 就绪状态 */
@Composable
private fun LayerRow(context: Context, layer: ToolLayer, specs: List<com.pient.app.tools.ToolSpec>) {
    val part = ToolRegistry.partOf(layer)
    val tools = specs.filter { it.layer == layer }
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

/** 包表头：名称 + 来源徽标 + 副标题 + 启停开关（整行可点 = 展开/收起工具） */
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

/** 策略行：标题 + 说明 + 当前策略 + 展开箭头；展开后三选（允许 / 每次询问 / 禁止，选中打勾） */
@Composable
private fun PolicyRow(
    title: String,
    desc: String,
    policy: String,
    expanded: Boolean,
    onClick: () -> Unit,
    onPick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                if (desc.isNotBlank()) {
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Text(
                policyLabel(policy),
                style = MaterialTheme.typography.labelMedium,
                color = when (policy) {
                    ToolGate.ALLOW -> MaterialTheme.colorScheme.primary
                    ToolGate.FORBID -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp).size(18.dp),
            )
        }
        if (expanded) {
            OptionRow("允许", "直接执行，不再询问", ToolGate.ALLOW, policy, onPick)
            OptionRow("每次询问", "每次调用都弹授权（默认）", ToolGate.ASK, policy, onPick)
            OptionRow("禁止", "直接拦下，并把原因回给模型", ToolGate.FORBID, policy, onPick)
        }
    }
}

/** 策略选项行（缩进一格；选中 = 主色 + 对勾，整行可点） */
@Composable
private fun OptionRow(label: String, desc: String, value: String, current: String, onPick: (String) -> Unit) {
    val selected = value == current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(value) }
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(
                Icons.Outlined.CheckCircle, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
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
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}
