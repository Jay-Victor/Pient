package com.pient.app.ui.terminal

import com.pient.app.data.i18n.OptionLabels
import com.pient.app.data.i18n.L
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import com.pient.app.ui.theme.DarkWarn
import com.pient.app.ui.theme.LightWarn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.APT_MIRRORS
import com.pient.app.data.ChatState
import com.pient.app.data.ComponentGroups
import com.pient.app.data.PI_AGENT_UPDATE
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 环境配置页。
 *
 * 页面三段（自上而下）：
 * 1. **执行环境** —— Pient 特有：命令跑在哪个环境里（Ubuntu PRoot / Ubuntu chroot）。未就绪不可选
 *    （chroot 需要设备已 Root）；改完写 `exec_env`，下一条命令即生效。
 * 2. **apt 镜像源** —— 写进 rootfs 的 deb822 `ubuntu.sources`（国内镜像同时承载 noble-security）；
 *    **路径按本机架构选**：arm64 走 `…/ubuntu-ports/`、x86_64 走 `…/ubuntu/`（写错档位 apt 整轮 404）。
 * 3. **环境内软件** —— **分类卡**（标题 + 「n/m 已装」 + 全选 + 展开箭头），展开后逐包
 *    一行（勾选框 + 名称 + 已安装绿标 + 描述），右下角底部「安装所选」。
 *
 * 三条交互口径（Pient 红线）：
 * - 已安装的包**勾上且不可取消**（不会重复装）；
 * - 分类行/包行**整行可点**（不是只有勾选框）；
 * - 点「安装所选」→ **跳到终端页**，脚本在专用会话「环境配置」里跑，输出实时可见（本页不留日志区）。
 */
@Composable
fun TerminalSetupScreen(nav: NavController, chatState: ChatState) {
    val context = LocalContext.current

    // 组件状态：null = 检测中
    var status by remember { mutableStateOf<Map<String, Boolean>?>(null) }
    var detectSeq by remember { mutableIntStateOf(0) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }      // 待安装（已装的从不进来）
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    // pi 版本（读解出来的 package.json，不启 guest）；进页面/重新检测时重读
    var piVersion by remember { mutableStateOf(PiRuntime.piVersion(context)) }

    // 更新检查状态机：**默认是「检测更新」，只有真查到新版本才变成「更新」**
    var piUpdate by remember { mutableStateOf<PiUpdateState>(PiUpdateState.Idle) }
    val scope = rememberCoroutineScope()


    /** rootfs 就绪与否（决定整页可用性：未解包时所有检测都无意义） */
    var rootfsReady by remember { mutableStateOf(PiRuntime.rootfsReady(context)) }
    /** 本机 ELF 架构：决定 apt 镜像走主档还是 ports 路径（组合期只读一次） */
    val hostMachine = remember { PiRuntime.hostMachine() }
    /** 未就绪的具体原因（在 refresh 里算：rootfsIssue 要读 ELF 头，不能放在组合期） */
    var rootfsIssueText by remember { mutableStateOf("") }
    val rooted = remember { RootGateway.deviceRooted(context) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    // 进页面即检测；点刷新重跑（检测在 IO 线程，30 个组件的探测 ≈ 一条脚本一次往返）
    LaunchedEffect(detectSeq) {
        rootfsReady = PiRuntime.rootfsReady(context)
        rootfsIssueText = if (rootfsReady) "" else PiRuntime.rootfsIssue(context)
        status = null
        val result = withContext(Dispatchers.IO) { EnvProvision.detect(context, UBUNTU_COMPONENTS) }
        status = result
        piVersion = PiRuntime.piVersion(context)
        // 已装的项从待安装集合里剔掉（可能刚在终端里装完）
        result?.filterValues { it }?.keys?.forEach { selected.remove(it) }
    }
    // 默认把第一组展开（不然整页像空的）
    LaunchedEffect(Unit) { expanded[ComponentGroups.ORDER.first()] = true }

    /**
     * 环境就绪判定（terminal 的两个形态都要 rootfs；chroot 还要求设备已 Root）。
     * 注：**Android shell 不在这张列表里** —— 它是独立通道（见 `AndroidShell`），
     * 与 terminal 执行环境是两条轴，入口在「系统权限设置」页（权限档位那一侧）。
     */
    fun envReady(env: ExecEnv): Boolean = when (env) {
        ExecEnv.UBUNTU -> rootfsReady
        ExecEnv.UBUNTU_CHROOT -> rootfsReady && rooted
    }

    /** 未就绪时右侧显示的原因 */
    fun envBlockedNote(env: ExecEnv): String = when (env) {
        ExecEnv.UBUNTU_CHROOT -> L.terminal.needsRoot
        ExecEnv.UBUNTU -> L.terminal.notReady
    }

    val installable = status?.let { m -> UBUNTU_COMPONENTS.filter { selected[it.id] == true && m[it.id] != true } }
        ?: emptyList()

    /** 查 npm 官方最新版并和本机版本比：有新版本 → 按钮变「更新到 vX.Y.Z」 */
    fun checkPiUpdate() {
        piUpdate = PiUpdateState.Checking
        scope.launch {
            val latest = EnvProvision.latestPiVersion()
            val current = PiRuntime.piVersion(context)
            piVersion = current
            piUpdate = when {
                latest.isNullOrBlank() -> PiUpdateState.Failed(L.terminal.checkFailedNpm)
                current.isBlank() -> PiUpdateState.Failed(L.terminal.checkFailedPiVersion)
                EnvProvision.isNewer(latest, current) -> PiUpdateState.Available(current, latest)
                else -> PiUpdateState.Latest(current)
            }
        }
    }

    /** 更新 pi agent（走与安装组件同一条终端会话路径，输出在终端页可见） */
    fun updatePi() {
        val session = EnvProvision.installInTerminal(context, listOf(PI_AGENT_UPDATE))
        chatState.activePanel = Panel.TERMINAL
        chatState.terminalIndex = TerminalSessions.sessions.indexOf(session).coerceAtLeast(0)
        nav.popBackStack()
    }

    fun install() {
        if (installable.isEmpty()) {
            toast(L.terminal.pickComponentsFirst)
            return
        }
        val session = EnvProvision.installInTerminal(context, installable)
        // 跳到终端页并选中「环境配置」会话（安装过程要在终端里看得见）
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
                Icons.Outlined.ArrowBack, L.common.back,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = { nav.popBackStack() }),
            )
            Text(
                L.common.environmentConfig,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            if (status == null) {
                ArcSpinner(Modifier.padding(end = 14.dp), size = 16.dp)
            } else {
                Icon(
                    Icons.Outlined.Refresh, L.terminal.recheck,
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
                            when {
                                PiRuntime.isUnpacking() -> L.terminal.unpacking(PiRuntime.unpackNote())
                                rootfsIssueText.isNotBlank() -> L.terminal.notReadyReason(rootfsIssueText)
                                else -> L.terminal.notReadyHint
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // ── 0.5 Pient 运行时（pi 本体：随包预置，这里只做更新 —— 不是"待安装组件"） ──
            item { GroupTitle(L.terminal.groupRuntimeTitle, L.terminal.groupRuntimeNote) }
            item {
                SetupCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("pi agent", style = MaterialTheme.typography.bodyMedium)
                                if (piVersion.isNotEmpty()) Badge("v$piVersion")
                                Badge(L.terminal.bundled)
                            }
                            val (stateLine, warn) = when (val s = piUpdate) {
                                PiUpdateState.Idle ->
                                    (if (!rootfsReady) L.terminal.piNotReadyUnpack
                                     else if (piVersion.isEmpty()) L.terminal.piNotUnpacked
                                     else L.terminal.piReadyHint) to false
                                PiUpdateState.Checking -> L.terminal.checkingNpm to false
                                is PiUpdateState.Latest -> L.terminal.piUpToDate(s.version) to false
                                is PiUpdateState.Available ->
                                    L.terminal.piUpdateAvailable(s.latest, s.current) to true
                                is PiUpdateState.Failed -> s.reason to true
                            }
                            Text(
                                stateLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (warn) {
                                    if (isSystemInDarkTheme()) DarkWarn else LightWarn
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        PientButton(
                            text = when (val s = piUpdate) {
                                PiUpdateState.Checking -> L.terminal.checking
                                is PiUpdateState.Available -> L.terminal.updateTo(s.latest)
                                else -> L.terminal.checkUpdate
                            },
                            enabled = rootfsReady && piVersion.isNotEmpty() && !EnvProvision.running &&
                                piUpdate != PiUpdateState.Checking,
                            height = 36,
                            onClick = {
                                if (piUpdate is PiUpdateState.Available) {
                                    piUpdate = PiUpdateState.Idle
                                    updatePi()
                                } else {
                                    checkPiUpdate()
                                }
                            },
                        )
                    }
                }
            }

            // ── 1. 执行环境 ──
            item { GroupTitle(L.terminal.execEnvTitle, L.terminal.execEnvNote) }
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
                                    toast(L.terminal.envSwitched(env.title))
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
                                Icon(Icons.Outlined.Check, L.terminal.selected, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            } else if (!ready) {
                                Text(
                                    envBlockedNote(env),
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
            item {
                GroupTitle(
                    L.terminal.aptMirror,
                    L.terminal.aptMirrorNote(hostMachine),
                )
            }
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
                                        toast(L.terminal.mirrorNotReady)
                                        return@clickable
                                    }
                                    if (EnvProvision.applyMirror(context, mirror)) {
                                        SettingsStore.aptMirror = mirror.name
                                        toast(L.terminal.mirrorApplied(OptionLabels.aptMirror(mirror.name)))
                                    } else {
                                        toast(L.terminal.mirrorFailed)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(OptionLabels.aptMirror(mirror.name), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    mirror.uriFor(hostMachine),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = MonoFont,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            if (chosen) {
                                Icon(Icons.Outlined.Check, L.terminal.selected, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // ── 3. 环境内软件（分类卡 + 全选 + 展开） ──
            item {
                GroupTitle(
                    L.terminal.packagesTitle,
                    L.terminal.packagesNote,
                )
            }
            items(ComponentGroups.ORDER) { group ->
                val list = UBUNTU_COMPONENTS.filter { it.group == group }
                if (list.isEmpty()) return@items
                CategoryCard(
                    title = OptionLabels.envGroup(group),
                    desc = ComponentGroups.descOf(group),
                    requiredGroup = group in ComponentGroups.REQUIRED_GROUPS,
                    list = list,
                    status = status,
                    expanded = expanded[group] == true,
                    onToggleExpand = { expanded[group] = expanded[group] != true },
                    selected = selected,
                )
            }
        }

        // ── 底部：状态 + 安装所选 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            val installedCount = status?.count { it.value } ?: 0
            Text(
                if (EnvProvision.running) L.terminal.installingStep(EnvProvision.step)
                else L.terminal.installedSummary(installedCount, UBUNTU_COMPONENTS.size, installable.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 「Pient 必须」还差什么（一眼看到，不用逐组展开找）
            val missingRequired = status?.let { m ->
                UBUNTU_COMPONENTS.filter { it.required && m[it.id] != true }
            }.orEmpty()
            if (missingRequired.isNotEmpty() && !EnvProvision.running) {
                Text(
                    L.terminal.mustMissing(missingRequired.size, missingRequired.joinToString("、") { it.name }),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSystemInDarkTheme()) DarkWarn else LightWarn,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    L.terminal.installVisibleInTerminal,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!EnvProvision.running) {
                    Text(
                        L.terminal.selectRequired,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 14.dp)
                            .clickable {
                                UBUNTU_COMPONENTS.filter { it.required }.forEach {
                                    if (status?.get(it.id) != true) selected[it.id] = true
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
                PientButton(
                    text = if (EnvProvision.running) L.terminal.viewInTerminal else L.terminal.installSelectedCount(installable.size),
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

/**
 * 徽标（小圆角标签）：分类徽标（「Pient 必须」/「pi 工具依赖」）与行内标记（必须 / 大）共用。
 * [strong] = 醒目的黄（Pient 必须项那类），否则用主色（普通提示）。
 */
@Composable
private fun Badge(text: String, strong: Boolean = false) {
    val color = if (strong) {
        if (isSystemInDarkTheme()) DarkWarn else LightWarn
    } else {
        MaterialTheme.colorScheme.primary
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .padding(start = 6.dp)
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/**
 * 分类卡：标题 + 「n/m 已装」 + 全选 + 展开箭头；展开后逐包一行。
 * 整行可点 = 展开/收起（交互红线：可点区域必须整块可点）。
 */
@Composable
private fun CategoryCard(
    title: String,
    desc: String,
    requiredGroup: Boolean,
    list: List<UbuntuComponent>,
    status: Map<String, Boolean>?,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    selected: MutableMap<String, Boolean>,
) {
    val installed = list.count { status?.get(it.id) == true }
    val uninstalled = list.filter { status?.get(it.id) != true }
    /**
     * 分类级勾选状态：**「已装」与「已勾选」一起算**——
     * 只要求「有未装项」且它们全被勾的话，**整类都装完之后反而显示未勾**。
     * 三态：全齐（装好的 + 已勾的 = 全部）→ 勾上；一个都没有 → 空；其它 → 半选。
     */
    val picked = uninstalled.count { selected[it.id] == true }
    val triState = when {
        status == null || list.isEmpty() -> ToggleableState.Off
        installed + picked >= list.size -> ToggleableState.On
        picked == 0 && installed == 0 -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }

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
                // 「（Pient 必须）」：写在分类标题下的橙色小字（不另做徽标）
                if (requiredGroup) {
                    Text(
                        L.terminal.pientRequired,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSystemInDarkTheme()) DarkWarn else LightWarn,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                Text(
                    L.terminal.installedOf(installed, list.size) + if (desc.isNotEmpty()) " · $desc" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 同样套 48dp 槽位：onClick = null（"已齐"/检测中）时 Material3 不套最小触控尺寸包裹，
                // 裸放会缩成 20dp 并改变横向位置
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    TriStateCheckbox(
                            state = triState,
                    // 全部装好 = 没有可勾的：onClick 传 null（不可点），但 **enabled 保持 true** ——
                    // disabled 的复选框在无障碍树里等于"不显示"，用户看到的就是"框里没勾"
                        onClick = if (status == null || uninstalled.isEmpty()) {
                            null
                        } else {
                            { uninstalled.forEach { selected[it.id] = picked < uninstalled.size } }
                        },
                        enabled = status != null,
                    )
                    }
                Text(
                    if (uninstalled.isEmpty() && list.isNotEmpty()) L.terminal.allSet else L.common.selectAll,
                    style = MaterialTheme.typography.labelSmall,
                )
                }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                if (expanded) L.common.collapse else L.common.expand,
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

/** 单个组件行：勾选 + 名称（+已安装/大）+ 描述；已安装不可取消 */
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
        // 一律套 48dp 槽位：onCheckedChange = null 时 Material3 **不会**再套最小触控尺寸包裹，
        // 复选框会缩成 20dp 贴到行左边（相对已装行左移 32px），
        // 就是用户看到的「勾选的和没勾的方框不对齐」。用固定槽位让两种状态同宽同位。
        if (checking) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { ArcSpinner(size = 16.dp) }
            } else {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Checkbox(
                    checked = installed || selected,
                // 已装 = 只读：用 **onCheckedChange = null**（不是 enabled = false）——
                // enabled=false 会被 Material3 画成**灰色**；
                // 传 null 时控件不可点但按正常配色渲染（蓝色实心勾），与分类级那个框一致。
                    onCheckedChange = if (installed) null else { { onToggle() } },
                        enabled = !checking,
                )
                }
            }
        Column(Modifier.padding(start = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(component.name, style = MaterialTheme.typography.bodyMedium)
                if (installed) {
                    Text(
                        L.common.installed,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    }
                if (component.required) Badge(L.terminal.requiredBadge, strong = true)
                if (component.heavy) Badge(L.terminal.heavyBadge)
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

/** 「Pient 运行时」卡片的更新检查状态（默认 Idle = 显示「检测更新」） */
private sealed interface PiUpdateState {
    /** 还没检测过（按钮 = 检测更新） */
    data object Idle : PiUpdateState

    data object Checking : PiUpdateState

    /** 已经是最新（[version] = 本机版本） */
    data class Latest(val version: String) : PiUpdateState

    /** 查到新版本（按钮 = 更新到 vX.Y.Z） */
    data class Available(val current: String, val latest: String) : PiUpdateState

    data class Failed(val reason: String) : PiUpdateState
    }
