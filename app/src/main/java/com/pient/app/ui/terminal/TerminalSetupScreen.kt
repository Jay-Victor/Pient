package com.pient.app.ui.terminal

import com.pient.app.tools.terminal.TerminalSessions
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.PientRuntime
import com.pient.app.tools.terminal.APT_MIRRORS
import com.pient.app.tools.terminal.ComponentGroups
import com.pient.app.tools.terminal.EnvAction
import com.pient.app.tools.terminal.ExecEnv
import com.pient.app.tools.terminal.ExecEnvs
import com.pient.app.tools.terminal.EnvStatus
import com.pient.app.data.Panel
import com.pient.app.data.RootGateway
import com.pient.app.data.SettingsStore
import com.pient.app.tools.terminal.UBUNTU_COMPONENTS
import com.pient.app.tools.terminal.UbuntuComponent
import com.pient.app.runtime.EnvProvision
import com.pient.app.runtime.PiRuntime
import com.pient.app.ui.components.ArcSpinner
import com.pient.app.ui.components.DividerLine
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.SectionHeader
import com.pient.app.ui.theme.PientPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 环境配置页（2026-09-14 重做第二版）。
 *
 * **语义**：这里配置的不是「Ubuntu 内部组件清单」，而是 **AI 的工具命令的执行落点**——
 * 移植进来的 pi 工具里，`bash`（与输入栏 `!` 命令）是唯一经 shell 执行的一个，
 * 它的入口是 pi 的 `shellPath`（settings.json 项，由 `PiConfig.syncShellPath` 指向随包包装脚本）。
 * 包装脚本每次被执行时现读 `<pient-rt>/exec_env`，于是本页的「执行环境」单选即决定
 * AI 的 shell 命令跑在哪：Android shell（toybox，系统命令开箱即用）/ Ubuntu PRoot / Ubuntu chroot。
 *
 * 页面两段：
 * 1. **执行环境**——三选一 + 各自就绪状态 + 初始化入口（Ubuntu 缺 rootfs → 一键解包；chroot 缺 su → 请求 Root 授权）；
 * 2. **环境内软件（Ubuntu）**——apt 镜像源 + **按用途分类**的组件多选（命令行基础 / 语言运行时 /
 *    开发与构建 / 网络与远程），点「安装所选」**跳进终端页**由专用会话「环境配置」实跑
 *    （apt 输出实时滚在终端里；本页只留一条「正在安装 · 去终端」的状态，见 [EnvProvision.installInTerminal]）。
 */
@Composable
fun TerminalSetupScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }

    var statuses by remember { mutableStateOf<Map<ExecEnv, EnvStatus>>(emptyMap()) }
    var detected by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var mirrorUri by remember { mutableStateOf("") }
    var workspacePath by remember { mutableStateOf("") }
    var unpacking by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var showMirrorPicker by remember { mutableStateOf(false) }
    var rootRequesting by remember { mutableStateOf(false) }

    suspend fun refresh() {
        statuses = withContext(Dispatchers.IO) {
            ExecEnv.entries.associateWith { ExecEnvs.statusOf(context, it) }
        }
        detected = withContext(Dispatchers.IO) { EnvProvision.detect(context, UBUNTU_COMPONENTS) }
        mirrorUri = withContext(Dispatchers.IO) { EnvProvision.currentMirrorUri(context) }
        workspacePath = PiRuntime.workspaceDir(context).absolutePath
    }

    LaunchedEffect(Unit) { refresh() }
    // 安装任务结束后重新检测（流程：安装 → 回本页 → 勾选行转「已安装」）
    LaunchedEffect(EnvProvision.running) {
        if (!EnvProvision.running) detected = withContext(Dispatchers.IO) {
            EnvProvision.detect(context, UBUNTU_COMPONENTS)
        }
    }
    // rootfs 自动解包可能由 App 启动时的后台线程触发（不经过本页）→ 解包期间让状态行自己刷新，
    // 用户看得见「正在自动解包 rootfs：n% · …」，不再是「点了/等了都没反应」
    LaunchedEffect(Unit) {
        while (true) {
            if (PiRuntime.isUnpacking()) refresh()
            delay(1000)
        }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    /**
     * 切到终端页并选中承载安装的会话（「安装所选」的落点）：
     * 环境配置页是独立路由 → 先 popBackStack 回聊天页，再把面板换成终端。
     */
    fun openTerminal(session: TerminalSessions.Session) {
        val cs = PientRuntime.chatState
        if (cs != null) {
            val idx = TerminalSessions.sessions.indexOf(session)
            if (idx >= 0) cs.terminalIndex = idx
            cs.activePanel = Panel.TERMINAL
        }
        nav.popBackStack()
    }

    /** 选定执行环境：未就绪的环境不允许选中（先给解锁入口，避免 AI 命令静默失败） */
    fun selectEnv(env: ExecEnv) {
        val st = statuses[env]
        if (st != null && !st.ready) {
            toast("「${env.title}」尚未就绪：${st.detail}")
            return
        }
        SettingsStore.execEnv = env
        PiRuntime.prepareTerminal(context)
        scope.launch { withContext(Dispatchers.IO) { SettingsStore.saveEnvironment(context) } }
        toast("已选「${env.title}」")
        scope.launch { refresh() }
    }

    // Android shell（Shizuku / Root）的授权入口已挪到「设置 → 系统权限」页（三档边界卡）：
    // 终端页只做 Ubuntu（PRoot / chroot）+ 环境内软件，别再往这里塞系统命令通道的东西。

    /** 一键配置：解包随包的 Ubuntu rootfs（进度回调来自 IO 线程 → 转主线程写状态） */
    fun provision() {
        if (unpacking) return
        scope.launch {
            unpacking = true
            progress = "准备解包…"
            val ok = withContext(Dispatchers.IO) {
                PiRuntime.extractRootfs(context) { pct, text ->
                    main.post { progress = "${(pct * 100).toInt()}% · $text" }
                }
            }
            refresh()
            unpacking = false
            if (ok) {
                progress = ""
                toast("Ubuntu rootfs 解包完成")
            } else {
                // 失败必须看得见：把原因留在页面上 + toast 一次（此前失败是静默的，
                // 真机上表现为"点「一键配置」没反应"）
                val msg = progress.substringAfter("· ", progress).ifBlank { "解包失败" }
                toast(msg)
            }
        }
    }

    /** chroot 环境需要 su：真跑一次 su（首次弹 Root 管理器授权框） */
    fun requestRoot() {
        if (rootRequesting) return
        scope.launch {
            rootRequesting = true
            val granted = RootGateway.requestAccess()
            rootRequesting = false
            toast(if (granted) "已获得 Root 权限" else "未获得 Root 权限（设备未 Root 或授权被拒绝）")
            refresh()
        }
    }

    /** 「安装所选」：勾选集减去已装的 → 交给终端会话执行 → 跳到终端页 */
    fun installSelected() {
        val picked = UBUNTU_COMPONENTS.filter {
            it.id in SettingsStore.selectedComponents && detected[it.id] != true
        }
        if (picked.isEmpty()) {
            toast("先勾选要装的组件（已安装的不用再选）")
            return
        }
        val st = statuses[ExecEnv.UBUNTU]
        if (st?.ready != true) {
            // 不再把用户支到「一键配置」的死路上（2026-09-14 真机反馈）：rootfs 缺失时
            // 直接开始自动解包，并跳到终端页看解包进度（终端会话自己会解包）
            if (st?.action == EnvAction.UNPACK_ROOTFS) {
                PiRuntime.ensureRootfsAsync(context)
                toast("rootfs 未就绪：已开始自动解包（约 30MB / 1–2 分钟），完成后回来再点「安装所选」")
                openTerminal(TerminalSessions.sessionNamed(TerminalSessions.CONFIG_SESSION) ?: TerminalSessions.newSession(context))
                scope.launch {
                    delay(1500)
                    refresh()
                }
            } else {
                toast("Ubuntu 环境未就绪：${st?.detail ?: "先解包 rootfs"}")
            }
            return
        }
        val session = EnvProvision.installInTerminal(context, picked)
        openTerminal(session)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ── 顶栏：返回 + 标题 ──
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
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
            ) {
                // ═══════════ 执行环境 ═══════════
                SectionHeader("执行环境", icon = Icons.Outlined.Terminal)
                PientPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "AI 的工具命令（bash 与 ! 命令）在这一个环境里执行；终端页固定是 Ubuntu 环境（装工具链、跑脚本都在那）。切换后下一条命令 / 新会话生效",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                        ExecEnv.entries.forEachIndexed { i, env ->
                            if (i > 0) DividerLine(Modifier.padding(horizontal = 14.dp))
                            EnvRow(
                                env = env,
                                status = statuses[env],
                                selected = SettingsStore.execEnv == env,
                                unpacking = unpacking,
                                progress = progress,
                                rootRequesting = rootRequesting,
                                onSelect = { selectEnv(env) },
                                onProvision = ::provision,
                                onRequestRoot = ::requestRoot,
                                onRecheck = { scope.launch { refresh() } },
                            )
                        }
                    }
                }

                // ═══════════ 环境内软件（Ubuntu）═══════════
                SectionHeader("环境内软件（Ubuntu）", icon = Icons.Outlined.Download)
                PientPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        // 说明 + 刷新状态（检测要跑真命令，手动触发，别每次重组都问一遍）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "装进 Ubuntu 的工具链：勾选后点底部「安装所选」，安装过程在**终端页**里跑（专用会话「环境配置」）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { scope.launch { refresh() } },
                            ) {
                                Icon(
                                    Icons.Outlined.Refresh, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    "刷新",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                        DividerLine(Modifier.padding(horizontal = 14.dp))

                        // apt 镜像源（整行可点 → 选择弹窗）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showMirrorPicker = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("apt 镜像源", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    mirrorUri.ifBlank { "未写入（用 rootfs 默认源）" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                SettingsStore.aptMirror,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        // ── 分类分节：每类一个标题行（含计数与全选）+ 该类组件 ──
                        ComponentGroups.ORDER.forEach { group ->
                            val list = UBUNTU_COMPONENTS.filter { it.group == group }
                            if (list.isEmpty()) return@forEach
                            val installedCount = list.count { detected[it.id] == true }
                            val pickedCount = list.count { it.id in SettingsStore.selectedComponents && detected[it.id] != true }

                            DividerLine(Modifier.padding(horizontal = 14.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        group,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        ComponentGroups.DESCS[group].orEmpty(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    "$installedCount/${list.size} 已装",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // 全选 / 取消全选：整块可点（交互红线：小图标不做点击目标）
                                Text(
                                    if (pickedCount == list.count { detected[it.id] != true }) "取消全选" else "全选",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(start = 10.dp)
                                        .clickable {
                                            val pending = list.filter { detected[it.id] != true }.map { it.id }.toSet()
                                            val allPicked = pending.isNotEmpty() && pending.all { it in SettingsStore.selectedComponents }
                                            SettingsStore.selectedComponents =
                                                if (allPicked) SettingsStore.selectedComponents - pending
                                                else SettingsStore.selectedComponents + pending
                                            scope.launch {
                                                withContext(Dispatchers.IO) { SettingsStore.saveEnvironment(context) }
                                            }
                                        },
                                )
                            }
                            list.forEach { c ->
                                ComponentRow(
                                    component = c,
                                    installed = detected[c.id] == true,
                                    checked = SettingsStore.selectedComponents.contains(c.id),
                                    onToggle = {
                                        SettingsStore.selectedComponents =
                                            if (c.id in SettingsStore.selectedComponents)
                                                SettingsStore.selectedComponents - c.id
                                            else SettingsStore.selectedComponents + c.id
                                        scope.launch {
                                            withContext(Dispatchers.IO) { SettingsStore.saveEnvironment(context) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                Text(
                    "当前工作区：$workspacePath（AI 的 bash cwd 与终端页同一处）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // ── 底部固定操作条（滚动区之外：勾到哪一类都点得到「安装所选」）──
            InstallBar(
                running = EnvProvision.running,
                runningLabel = EnvProvision.step,
                pickedCount = UBUNTU_COMPONENTS.count {
                    it.id in SettingsStore.selectedComponents && detected[it.id] != true
                },
                onInstall = ::installSelected,
                onClear = {
                    SettingsStore.selectedComponents = emptySet()
                    scope.launch { withContext(Dispatchers.IO) { SettingsStore.saveEnvironment(context) } }
                },
                onOpenTerminal = {
                    openTerminal(TerminalSessions.sessionNamed(TerminalSessions.CONFIG_SESSION) ?: TerminalSessions.newSession(context))
                },
            )
        }

        // 镜像源选择弹窗（页面根层，不做在滚动区内——全屏浮层不能在滚动容器里）
        if (showMirrorPicker) {
            PientDialog(
                title = "apt 镜像源",
                onDismiss = { showMirrorPicker = false },
                confirmText = "关闭",
                onConfirm = { showMirrorPicker = false },
                showCancel = false,
            ) {
                Column(Modifier.padding(top = 8.dp)) {
                    APT_MIRRORS.forEach { m ->
                        val selected = SettingsStore.aptMirror == m.name
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    SettingsStore.aptMirror = m.name
                                    val ok = EnvProvision.applyMirror(context, m)
                                    scope.launch {
                                        withContext(Dispatchers.IO) { SettingsStore.saveEnvironment(context) }
                                    }
                                    Toast.makeText(
                                        context,
                                        if (ok) "镜像源已切换为「${m.name}」" else "写入失败（rootfs 未就绪？）",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                m.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(
                                    Icons.Outlined.Check, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 底部操作条：未在安装时给「安装所选（N）/清空」，安装中给「去终端」 */
@Composable
private fun InstallBar(
    running: Boolean,
    runningLabel: String,
    pickedCount: Int,
    onInstall: () -> Unit,
    onClear: () -> Unit,
    onOpenTerminal: () -> Unit,
) {
    PientPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (running) {
                ArcSpinner(size = 14.dp, color = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        "正在安装…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "${runningLabel.ifBlank { "安装中" }}(输出在终端页)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PientButton(
                    text = "去终端",
                    onClick = onOpenTerminal,
                    height = 36,
                    contentPadding = 12,
                )
            } else {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (pickedCount > 0) "已选 $pickedCount 个待安装组件" else "还没勾选组件",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "点「安装所选」后跳到终端页执行",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (pickedCount > 0) {
                    Text(
                        "清空",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clickable(onClick = onClear),
                    )
                }
                PientButton(
                    text = if (pickedCount > 0) "安装所选（$pickedCount）" else "安装所选",
                    onClick = onInstall,
                    enabled = pickedCount > 0,
                    height = 36,
                    contentPadding = 12,
                )
            }
        }
    }
}

/** 单个执行环境的行：图标 + 标题 + 状态 + （未就绪时的初始化入口）；整行可点即选中 */
@Composable
private fun EnvRow(
    env: ExecEnv,
    status: EnvStatus?,
    selected: Boolean,
    unpacking: Boolean,
    progress: String,
    rootRequesting: Boolean,
    onSelect: () -> Unit,
    onProvision: () -> Unit,
    onRequestRoot: () -> Unit,
    onRecheck: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                env.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            Text(
                env.badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selected) {
                Icon(
                    Icons.Outlined.CheckCircle, "当前使用中",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp).size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            env.desc,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                status?.detail ?: "检测中…",
                style = MaterialTheme.typography.labelSmall,
                color = if (status?.ready == true) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 未就绪 → 初始化入口（action 由 ExecEnvs 判定给出，不在页面里猜文案）
        if (status != null && !status.ready) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (status.action) {
                    EnvAction.UNPACK_ROOTFS -> PientButton(
                        text = if (unpacking) progress.ifBlank { "解包中…" } else "一键配置",
                        onClick = onProvision,
                        enabled = !unpacking,
                        loading = unpacking,
                        height = 34,
                        contentPadding = 12,
                    )
                    EnvAction.REQUEST_ROOT -> PientButton(
                        text = "请求 Root 授权",
                        onClick = onRequestRoot,
                        enabled = !rootRequesting,
                        loading = rootRequesting,
                        height = 34,
                        contentPadding = 12,
                    )
                    // 设备不具备该能力（未 Root 设备上的 chroot）：不给可点的操作入口，只如实说明
                    EnvAction.UNSUPPORTED -> PientButton(
                        text = "设备不支持",
                        onClick = {},
                        enabled = false,
                        primary = false,
                        height = 34,
                        contentPadding = 12,
                    )
                    EnvAction.NONE -> PientButton(
                        text = "重新检测",
                        onClick = onRecheck,
                        primary = false,
                        height = 34,
                        contentPadding = 12,
                    )
                }
            }
        }
    }
}

/**
 * 组件行：勾选 + 名称（含「大」标记）/说明 + 安装状态；整行可点。
 * 已安装的行不可勾（勾了也没意义，反而会让「安装所选」装上重复包）。
 */
@Composable
private fun ComponentRow(
    component: UbuntuComponent,
    installed: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val enabled = !installed && !EnvProvision.running
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Checkbox(
            checked = installed || checked,
            enabled = enabled,
            onCheckedChange = { onToggle() },
        )
        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(component.name, style = MaterialTheme.typography.bodyMedium)
                if (component.heavy) {
                    Text(
                        "大",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .background(
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                component.desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (installed) "已安装" else "未安装",
            style = MaterialTheme.typography.labelSmall,
            color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
