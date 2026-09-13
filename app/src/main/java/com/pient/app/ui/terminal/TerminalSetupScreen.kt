package com.pient.app.ui.terminal

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
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
import com.pient.app.data.APT_MIRRORS
import com.pient.app.data.EnvAction
import com.pient.app.data.ExecEnv
import com.pient.app.data.ExecEnvs
import com.pient.app.data.EnvStatus
import com.pient.app.data.RootGateway
import com.pient.app.data.SettingsStore
import com.pient.app.data.ShizukuGateway
import com.pient.app.data.UBUNTU_COMPONENTS
import com.pient.app.runtime.EnvProvision
import com.pient.app.runtime.PiRuntime
import com.pient.app.ui.components.ArcSpinner
import com.pient.app.ui.components.DividerLine
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.SectionHeader
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.PientPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 环境配置页（2026-09-14 重做）。
 *
 * **语义**：这里配置的不是「Ubuntu 内部组件清单」，而是 **AI 的工具命令的执行落点**——
 * 移植进来的 pi 工具里，`bash`（与输入栏 `!` 命令）是唯一经 shell 执行的一个，
 * 它的入口是 pi 的 `shellPath`（settings.json 项，由 `PiConfig.syncShellPath` 指向随包包装脚本）。
 * 包装脚本每次被执行时现读 `<pient-rt>/exec_env`，于是本页的「执行环境」单选即决定
 * AI 的 shell 命令跑在哪：Android shell（toybox，系统命令开箱即用）/ Ubuntu PRoot / Ubuntu chroot。
 *
 * 页面两段：
 * 1. **执行环境**——三选一 + 各自就绪状态 + 初始化入口（Ubuntu 缺 rootfs → 一键解包；chroot 缺 su → 请求 Root 授权）；
 * 2. **环境内软件（Ubuntu）**——apt 镜像源 + 常用组件勾选安装，命令真在 Ubuntu 里跑、输出流式回显。
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
    // 安装任务结束后刷新组件状态（流程：安装 → 重新检测 → 勾选行转「已安装」）
    LaunchedEffect(EnvProvision.running) {
        if (!EnvProvision.running) detected = withContext(Dispatchers.IO) {
            EnvProvision.detect(context, UBUNTU_COMPONENTS)
        }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

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

    /** 解锁 Android shell（Shizuku / Root）：按设备现状给下一步 */
    fun grantPrivilege() {
        when {
            ShizukuGateway.authorized() -> toast("Shizuku 已授权；ADB 级执行通道接入中")
            ShizukuGateway.installed(context) -> {
                // 已装未授权 / 服务未运行：先请求授权，失败则拉起 Shizuku 应用
                if (!ShizukuGateway.requestPermission() && !ShizukuGateway.openApp(context)) {
                    toast("无法打开 Shizuku 应用")
                }
            }
            RootGateway.deviceRooted(context) -> scope.launch {
                rootRequesting = true
                val granted = RootGateway.requestAccess()
                rootRequesting = false
                toast(if (granted) "已获得 Root 权限" else "未获得 Root 权限（设备未 Root 或授权被拒绝）")
                refresh()
            }
            else -> if (!ShizukuGateway.openUrl(context, ShizukuGateway.DOWNLOAD_URL)) {
                toast("需安装 Shizuku 或设备已 Root")
            } else {
                toast("需安装 Shizuku 或设备已 Root")
            }
        }
    }

    /** 一键配置：解包随包的 Ubuntu rootfs（进度回调来自 IO 线程 → 转主线程写状态） */
    fun provision() {
        if (unpacking) return
        scope.launch {
            unpacking = true
            progress = "准备解包…"
            withContext(Dispatchers.IO) {
                PiRuntime.extractRootfs(context) { pct, text ->
                    main.post { progress = "${(pct * 100).toInt()}% · $text" }
                }
            }
            refresh()
            unpacking = false
            progress = ""
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
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
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
                                onGrantPrivilege = ::grantPrivilege,
                                onRecheck = { scope.launch { refresh() } },
                            )
                        }
                    }
                }

                // ═══════════ 环境内软件（Ubuntu）═══════════
                SectionHeader("环境内软件（Ubuntu）", icon = Icons.Outlined.Download)
                PientPanel(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "在这里给 Ubuntu 装工具链（Node / Python / Git …）：装完 AI 的 bash 工具与终端页立刻能用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
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
                        DividerLine(Modifier.padding(horizontal = 14.dp))

                        // 组件勾选
                        UBUNTU_COMPONENTS.forEach { c ->
                            ComponentRow(
                                name = c.name,
                                desc = c.desc,
                                installed = detected[c.id] == true,
                                checked = c.id in SettingsStore.selectedComponents,
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

                        // 动作：刷新状态 / 安装所选
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            PientButton(
                                text = "刷新状态",
                                onClick = { scope.launch { refresh() } },
                                primary = false,
                                enabled = !EnvProvision.running,
                                modifier = Modifier.weight(1f),
                            )
                            PientButton(
                                text = "安装所选（${SettingsStore.selectedComponents.size}）",
                                onClick = {
                                    val picked = UBUNTU_COMPONENTS.filter { it.id in SettingsStore.selectedComponents }
                                    if (picked.isEmpty()) {
                                        toast("先勾选组件")
                                    } else if (statuses[ExecEnv.UBUNTU]?.ready != true) {
                                        toast("Ubuntu rootfs 未就绪：先「一键配置」解包")
                                    } else {
                                        EnvProvision.install(context, picked)
                                    }
                                },
                                enabled = !EnvProvision.running,
                                loading = EnvProvision.running,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // 运行 / 日志区（有输出或有任务在跑时才出现）
                        if (EnvProvision.running || EnvProvision.log.isNotEmpty()) {
                            DividerLine(Modifier.padding(horizontal = 14.dp))
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (EnvProvision.running) {
                                        ArcSpinner(size = 14.dp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(
                                        EnvProvision.step.ifBlank { "执行日志" },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (!EnvProvision.running) {
                                        Text(
                                            "清空",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { EnvProvision.clearLog() },
                                        )
                                    } else {
                                        Text(
                                            "取消",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.clickable { EnvProvision.cancel() },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    EnvProvision.log.takeLast(8).joinToString("\n"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = MonoFont,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp)
                                        .verticalScroll(rememberScrollState()),
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
    onGrantPrivilege: () -> Unit,
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
                    EnvAction.GRANT_PRIVILEGE -> PientButton(
                        text = "用 Shizuku / Root 解锁",
                        onClick = onGrantPrivilege,
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

/** 组件行：勾选 + 名称/说明 + 安装状态（整行可点） */
@Composable
private fun ComponentRow(
    name: String,
    desc: String,
    installed: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(
                desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (installed) "已安装" else "未安装",
            style = MaterialTheme.typography.labelSmall,
            color = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
