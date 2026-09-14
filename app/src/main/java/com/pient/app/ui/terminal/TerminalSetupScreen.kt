package com.pient.app.ui.terminal

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.APT_MIRRORS
import com.pient.app.data.ChatState
import com.pient.app.data.ComponentGroups
import com.pient.app.data.ExecEnv
import com.pient.app.data.Panel
import com.pient.app.data.RootGateway
import com.pient.app.data.SettingsStore
import com.pient.app.data.UBUNTU_COMPONENTS
import com.pient.app.data.UbuntuComponent
import com.pient.app.runtime.EnvProvision
import com.pient.app.runtime.PiRuntime
import com.pient.app.runtime.TerminalSessions
import com.pient.app.ui.components.ArcSpinner
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 环境配置页（**2026-09-14 按 Operit `SetupScreen` 的结构重做**）。
 *
 * 页面三段（自上而下）：
 * 1. **执行环境** —— Pient 特有：命令跑在哪个环境里（Ubuntu PRoot / Ubuntu chroot）。未就绪不可选
 *    （chroot 需要设备已 Root）；改完写 `exec_env`，下一条命令即生效。
 * 2. **apt 镜像源** —— 写进 rootfs 的 deb822 `ubuntu.sources`（国内镜像同时承载 noble-security）。
 * 3. **环境内软件** —— 照 Operit：**分类卡**（标题 + 「n/m 已装」 + 全选 + 展开箭头），展开后逐包
 *    一行（勾选框 + 名称 + 已安装绿标 + 描述），右下角底部「安装所选」。
 *
 * 三条交互口径（Operit 同款 + Pient 红线）：
 * - 已安装的包**勾上且不可取消**（不会重复装）；
 * - 分类行/包行**整行可点**（不是只有勾选框）；
 * - 点「安装所选」→ **跳到终端页**，脚本在专用会话「环境配置」里跑，输出实时可见（本页不留日志区）。
 */
@Composable
fun TerminalSetupScreen(nav: NavController, chatState: ChatState) {
    val context = LocalContext.current

    // 组件状态：null = 检测中（Operit 的 InstallStatus.CHECKING 同义）
    var status by remember { mutableStateOf<Map<String, Boolean>?>(null) }
    var detectSeq by remember { mutableIntStateOf(0) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }      // 待安装（已装的从不进来）
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    // rootfs 就绪与否（决定整页可用性：未解包时所有检测都无意义）
    var rootfsReady by remember { mutableStateOf(PiRuntime.rootfsReady(context)) }
    val rooted = remember { RootGateway.deviceRooted(context) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    // 进页面即检测；点刷新重跑（检测在 IO 线程，30 个组件的探测 ≈ 一条脚本一次往返）
    LaunchedEffect(detectSeq) {
        rootfsReady = PiRuntime.rootfsReady(context)
        status = null
        val result = withContext(Dispatchers.IO) { EnvProvision.detect(context, UBUNTU_COMPONENTS) }
        status = result
        // 已装的项从待安装集合里剔掉（可能刚在终端里装完）
        result?.filterValues { it }?.keys?.forEach { selected.remove(it) }
    }
    // 默认把第一组展开（不然整页像空的）
    LaunchedEffect(Unit) { expanded[ComponentGroups.ORDER.first()] = true }

    /** 环境就绪判定：Ubuntu 两类都要 rootfs 就绪；chroot 还要求设备已 Root */
    fun envReady(env: ExecEnv): Boolean = when (env) {
        ExecEnv.UBUNTU -> rootfsReady
        ExecEnv.UBUNTU_CHROOT -> rootfsReady && rooted
    }

    val installable = status?.let { m -> UBUNTU_COMPONENTS.filter { selected[it.id] == true && m[it.id] != true } }
        ?: emptyList()

    fun install() {
        if (installable.isEmpty()) {
            toast("先勾选要安装的组件")
            return
        }
        val session = EnvProvision.installInTerminal(context, installable)
        // 跳到终端页并选中「环境配置」会话（用户口径：安装过程要在终端里看得见）
        chatState.activePanel = Panel.TERMINAL
        chatState.terminalIndex = TerminalSessions.sessions.indexOf(session).coerceAtLeast(0)
        nav.popBackStack()
    }

    Column(Modifier.fillMaxSize()) {
        // ── 顶栏：返回 + 标题 + 刷新 ──
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
                "环境配置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            if (status == null) {
                ArcSpinner(Modifier.padding(end = 14.dp), size = 16.dp)
            } else {
                Icon(
                    Icons.Outlined.Refresh, "重新检测",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(24.dp)
                        .clickable { detectSeq += 1 },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 0. Ubuntu 未就绪提示（首启解包 / 包内没带归档） ──
            if (!rootfsReady) {
                item {
                    SetupCard {
                        Text(
                            if (PiRuntime.isUnpacking()) "Ubuntu 正在后台解包：${PiRuntime.unpackNote()}"
                            else "此安装包未内置 Ubuntu 环境（构建时未打包 rootfs）—— 无法解包，环境内软件不可用。",
                            style = MaterialTheme.typography.labelMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // ── 1. 执行环境 ──
            item { GroupTitle("执行环境", "终端里的命令跑在哪个环境（与「权限档位」是两条轴）") }
            item {
                SetupCard {
                    ExecEnv.entries.forEachIndexed { i, env ->
                        if (i > 0) DividerThin()
                        val ready = envReady(env)
                        val chosen = SettingsStore.execEnv == env
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = ready) {
                                    SettingsStore.execEnv = env
                                    PiRuntime.prepareTerminal(context)   // 立刻写 exec_env，下一条命令生效
                                    toast(if (env == ExecEnv.UBUNTU_CHROOT) "已切到 Ubuntu（chroot）" else "已切到 Ubuntu（PRoot）")
                                }
                                .alpha(if (ready) 1f else 0.45f)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(env.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Badge(env.badge)
                                }
                                Text(
                                    env.desc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            if (chosen) {
                                Icon(Icons.Outlined.Check, "已选", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            } else if (!ready) {
                                Text(
                                    if (env == ExecEnv.UBUNTU_CHROOT) "需 Root" else "未就绪",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── 2. apt 镜像源 ──
            item { GroupTitle("apt 镜像源", "写进 Ubuntu 的 /etc/apt/sources.list.d/ubuntu.sources") }
            item {
                SetupCard {
                    APT_MIRRORS.forEachIndexed { i, mirror ->
                        if (i > 0) DividerThin()
                        val chosen = SettingsStore.aptMirror == mirror.name
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!rootfsReady) {
                                        toast("Ubuntu 未就绪，暂不能写镜像源")
                                        return@clickable
                                    }
                                    if (EnvProvision.applyMirror(context, mirror)) {
                                        SettingsStore.aptMirror = mirror.name
                                        toast("镜像源已应用：${mirror.name}")
                                    } else {
                                        toast("镜像源写入失败")
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(mirror.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    mirror.uri,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = MonoFont,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            if (chosen) {
                                Icon(Icons.Outlined.Check, "已选", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // ── 3. 环境内软件（照 Operit：分类卡 + 全选 + 展开） ──
            item { GroupTitle("环境内软件", "首次进入时按需下载安装；已装好的会标出来，不会重复装") }
            items(ComponentGroups.ORDER) { group ->
                val list = UBUNTU_COMPONENTS.filter { it.group == group }
                if (list.isEmpty()) return@items
                CategoryCard(
                    title = group,
                    desc = ComponentGroups.DESCS[group].orEmpty(),
                    list = list,
                    status = status,
                    expanded = expanded[group] == true,
                    onToggleExpand = { expanded[group] = expanded[group] != true },
                    selected = selected,
                )
            }
        }

        // ── 底部：状态 + 安装所选（Operit 的底部按钮行） ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            val installedCount = status?.count { it.value } ?: 0
            Text(
                if (EnvProvision.running) "正在安装：${EnvProvision.step}"
                else "已装 $installedCount/${UBUNTU_COMPONENTS.size} · 已选 ${installable.size} 项",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    "安装过程在终端页可见",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                PientButton(
                    text = if (EnvProvision.running) "去终端查看" else "安装所选（${installable.size}）",
                    enabled = EnvProvision.running || installable.isNotEmpty(),
                    height = 38,
                    onClick = {
                        if (EnvProvision.running) {
                            val s = TerminalSessions.sessionNamed(TerminalSessions.CONFIG_SESSION)
                            chatState.activePanel = Panel.TERMINAL
                            chatState.terminalIndex = TerminalSessions.sessions.indexOf(s).coerceAtLeast(0)
                            nav.popBackStack()
                        } else {
                            install()
                        }
                    },
                )
            }
        }
    }
}

/** 分组标题（段标题 + 一句说明），与设置页 SectionHeader 同款口径 */
@Composable
private fun GroupTitle(title: String, desc: String) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            desc,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 页面内的分组卡（设置页 SettingsGroup 同款：surfaceContainerLow + 16dp + 描边） */
@Composable
private fun SetupCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        content()
    }
}

@Composable
private fun DividerThin() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

/** 徽标（推荐 / 需 Root）：小圆角标签 */
@Composable
private fun Badge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 6.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/**
 * 分类卡（照 Operit `CategoryCard` 的结构）：标题 + 「n/m 已装」 + 全选 + 展开箭头；展开后逐包一行。
 * 整行可点 = 展开/收起（交互红线：可点区域必须整块可点）。
 */
@Composable
private fun CategoryCard(
    title: String,
    desc: String,
    list: List<UbuntuComponent>,
    status: Map<String, Boolean>?,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    selected: MutableMap<String, Boolean>,
) {
    val installed = list.count { status?.get(it.id) == true }
    val uninstalled = list.filter { status?.get(it.id) != true }
    val allSelected = uninstalled.isNotEmpty() && uninstalled.all { selected[it.id] == true }

    SetupCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "$installed/${list.size} 已装" + if (desc.isNotEmpty()) " · $desc" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { on -> uninstalled.forEach { selected[it.id] = on } },
                    enabled = status != null && uninstalled.isNotEmpty(),
                )
                Text("全选", style = MaterialTheme.typography.labelSmall)
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp),
            )
        }
        if (expanded) {
            list.forEachIndexed { i, c ->
                if (i > 0) DividerThin()
                ComponentRow(
                    component = c,
                    checking = status == null,
                    installed = status?.get(c.id) == true,
                    selected = selected[c.id] == true,
                    onToggle = { selected[c.id] = selected[c.id] != true },
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** 单个组件行（照 Operit `PackageItem`）：勾选 + 名称（+已安装/大）+ 描述；已安装不可取消 */
@Composable
private fun ComponentRow(
    component: UbuntuComponent,
    checking: Boolean,
    installed: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !installed && !checking, onClick = onToggle)
            .padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
    ) {
        if (checking) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { ArcSpinner(size = 16.dp) }
        } else {
            Checkbox(
                checked = installed || selected,
                onCheckedChange = { onToggle() },
                enabled = !installed,
            )
        }
        Column(Modifier.padding(start = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(component.name, style = MaterialTheme.typography.bodyMedium)
                if (installed) {
                    Text(
                        "已安装",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                if (component.heavy) Badge("大")
            }
            Text(
                component.desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
