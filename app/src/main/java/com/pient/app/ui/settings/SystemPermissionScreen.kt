package com.pient.app.ui.settings

import com.pient.app.data.i18n.L
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.pient.app.data.PermissionTier
import com.pient.app.data.RootGateway
import com.pient.app.data.SettingsStore
import com.pient.app.data.ShizukuGateway
import com.pient.app.runtime.AndroidShell
import com.pient.app.data.SystemPermissions
import com.pient.app.ui.components.ArcSpinner
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.components.PientSegmented
import com.pient.app.ui.components.SectionHeader
import com.pient.app.ui.components.StatusBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * 系统权限设置页（三级权限体系）
 *
 * 结构（视觉走 Pient 卡片令牌）：
 *  ① 权限档位卡：档位分段选择（标准 / 调试 / Root，可预览）→ 档位说明 → 「设为当前档位」/「当前使用中」
 *     → 该档位对应的权限清单（基础权限四项 + 档位专属项，逐项实时状态、点击直达授权）；
 *  ② 设置向导（当前档位未就绪时出现）：Shizuku 三步 / Root 两步的渐进引导。
 *
 * 全部状态都是**真实检测**（无 mock）：基础权限四项读系统授权状态（与首启引导页共用
 * [SystemPermissions] 一份实现）；Shizuku 走官方 SDK（installed / running / authorized 三态）；
 * Root 走 su 通道检测。连接/授权入口一律跳系统页或官方应用，不在本页伪造结果。
 */
@Composable
fun SystemPermissionScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── 实时状态（页面显示的一切都从这里来：进入页面 / ON_RESUME / 刷新键 / 换档位，一律走 readStatus） ──
    var status by remember { mutableStateOf(SystemPermissions.status(context)) }
    var shizukuInstalled by remember { mutableStateOf(ShizukuGateway.installed(context)) }
    var shizukuRunning by remember { mutableStateOf(ShizukuGateway.running()) }
    var shizukuAuthorized by remember { mutableStateOf(ShizukuGateway.authorized()) }
    var deviceRooted by remember { mutableStateOf(RootGateway.deviceRooted(context)) }
    var rootGranted by remember { mutableStateOf(RootGateway.granted) }
    var rootProbed by remember { mutableStateOf(RootGateway.probed) }
    var refreshing by remember { mutableStateOf(false) }
    var rootRequesting by remember { mutableStateOf(false) }
    // Android shell 卡状态：读进 state（不在组合期现查 binder / 包列表），随刷新键与换档位更新
    var shellReady by remember { mutableStateOf(AndroidShell.available(context)) }
    var shellText by remember { mutableStateOf(AndroidShell.statusText(context)) }

    // 展示中的档位（可预览，与「当前生效档位」分离）
    var displayed by remember { mutableStateOf(SettingsStore.permissionTier) }
    val activeTier = SettingsStore.permissionTier

    // 「该档位能不能设为当前档位」的唯一判据（与首启引导页共用 SystemPermissions.TierState）
    val tierState = SystemPermissions.TierState(
        status = status,
        shizukuInstalled = shizukuInstalled,
        shizukuRunning = shizukuRunning,
        shizukuAuthorized = shizukuAuthorized,
        deviceRooted = deviceRooted,
        rootGranted = rootGranted,
    )

    /**
     * 读取全部真实状态（binder / 文件系统查询放 IO 线程）——
     * 进入页面 / ON_RESUME / 刷新键 / 换档位**都走这一条**，页面不留任何「只查一次」的死状态。
     */
    suspend fun readStatus() = withContext(Dispatchers.IO) {
        // su 授权读不到、只能真跑一次 su：用户已申请过才复检（未申请过就不主动弹 Root 授权框）
        if (RootGateway.probed) RootGateway.requestAccess()
        val st = SystemPermissions.tierState(context)
        status = st.status
        shizukuInstalled = st.shizukuInstalled
        shizukuRunning = st.shizukuRunning
        shizukuAuthorized = st.shizukuAuthorized
        deviceRooted = st.deviceRooted
        rootGranted = st.rootGranted
        rootProbed = RootGateway.probed
        // Android shell 卡同样要真刷新（不在组合期现查，否则刷新键动不了它）
        shellReady = AndroidShell.available(context)
        shellText = AndroidShell.statusText(context)
    }

    fun refresh() {
        scope.launch {
            refreshing = true
            val t0 = System.nanoTime()
            readStatus()
            // 可见反馈：真读一遍通常只要几毫秒，圆弧得转够时长才看得出「点到了」（交互红线②：状态变化必须有可见反馈）
            val dtMs = (System.nanoTime() - t0) / 1_000_000
            if (dtMs < REFRESH_FEEDBACK_MS) delay(REFRESH_FEEDBACK_MS - dtMs)
            refreshing = false
        }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    // 运行时权限回调（Android 10 及以下的存储权限 / 位置权限）：回来无条件重检
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { scope.launch { readStatus() } }

    // ── 授权入口 ──
    fun grantStorage() {
        if (SystemPermissions.needsRuntimeStorage) {
            runtimeLauncher.launch(SystemPermissions.runtimeStoragePermissions)
        } else if (!SystemPermissions.openStorageSettings(context)) {
            toast(L.perm.openStorageSettingsFailed)
        }
    }

    fun grantOverlay() {
        if (!SystemPermissions.openOverlaySettings(context)) toast(L.perm.openOverlaySettingsFailed)
    }

    fun grantBattery() {
        if (!SystemPermissions.openBatterySettings(context)) toast(L.perm.openBatterySettingsFailed)
    }

    fun grantLocation() {
        runtimeLauncher.launch(SystemPermissions.runtimeLocationPermissions)
    }

    /** Shizuku：按当前进度发对应动作（未装→下载页 / 未运行→打开应用 / 未授权→请求授权） */
    fun grantShizuku() {
        when {
            !shizukuInstalled -> if (!ShizukuGateway.openUrl(context, ShizukuGateway.DOWNLOAD_URL)) {
                toast(L.perm.openShizukuDownloadFailed)
            }
            !shizukuRunning -> if (!ShizukuGateway.openApp(context)) {
                toast(L.perm.openShizukuAppFailed)
            }
            !shizukuAuthorized -> if (!ShizukuGateway.requestPermission()) {
                toast(L.perm.startShizukuFirst)
            }
        }
    }

    /** Root：真执行一次 su（首次触发 Root 管理器授权框） */
    fun requestRoot() {
        scope.launch {
            rootRequesting = true
            val granted = RootGateway.requestAccess()
            rootGranted = granted
            rootProbed = true
            if (granted) deviceRooted = true
            rootRequesting = false
            toast(if (granted) L.perm.rootGranted else L.perm.rootNotGranted)
        }
    }

    /** 设为当前档位 —— **门控：该档位权限清单全部配齐才允许**（按钮已不可点，这里是第二道闸） */
    fun setActiveTier(tier: PermissionTier) {
        if (!tierState.ready(tier)) return
        SettingsStore.permissionTier = tier
        // 通道按档位门控：换档后 Android shell 卡（与向导）要跟着重读
        scope.launch { readStatus() }
        toast(L.perm.tierSwitched(tier.title))
    }

    // 进入页面即读一次；从系统设置/授权弹窗返回（ON_RESUME）自动重检
    LaunchedEffect(Unit) { readStatus() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scope.launch { readStatus() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Shizuku 授权结果回调（SDK 静态监听）
    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            shizukuAuthorized = granted
            toast(if (granted) L.perm.shizukuGranted else L.perm.shizukuDenied)
        }
        ShizukuGateway.addPermissionResultListener(listener)
        onDispose { ShizukuGateway.removePermissionResultListener(listener) }
    }

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
                Icons.Outlined.ArrowBack, L.common.back,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = { nav.popBackStack() }),
            )
            Text(
                L.settings.systemPermissions,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // ═══════════ 权限档位卡 ═══════════
            SectionHeader(L.perm.tierTitle, icon = Icons.Outlined.AdminPanelSettings)
            PermissionCardBox {
                Column(Modifier.padding(14.dp)) {
                    // 卡头：图标 + 标题 + 刷新（刷新中换成旋转圆弧）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (displayed == PermissionTier.ROOT) Icons.Outlined.Lock else Icons.Outlined.Shield,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            L.perm.tierTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(enabled = !refreshing, onClick = { refresh() }),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (refreshing) {
                                ArcSpinner(size = 16.dp)
                            } else {
                                Icon(
                                    Icons.Outlined.Refresh, L.perm.refreshStatus,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    CardDivider()
                    Spacer(Modifier.height(12.dp))

                    // 档位选择（三档，可预览）——**设备不具备的能力不给选**：
                    // Root 档在未 Root 设备上变暗且点不动
                    val tierSupported = PermissionTier.values().map { t ->
                        when (t) {
                            // 标准档永远可用；调试档只依赖可安装的 Shizuku（未装时给向导，不算不支持）
                            PermissionTier.STANDARD, PermissionTier.DEBUGGER -> true
                            PermissionTier.ROOT -> deviceRooted
                        }
                    }
                    PientSegmented(
                        labels = PermissionTier.values().map { it.title },
                        selected = displayed.ordinal,
                        onSelect = { displayed = PermissionTier.values()[it] },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = tierSupported,
                    )

                    Spacer(Modifier.height(10.dp))
                    AnimatedContent(
                        targetState = displayed,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "tierDesc",
                    ) { tier ->
                        Text(
                            tier.desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // 设为当前档位 / 当前使用中
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (!tierSupported[displayed.ordinal]) {
                            // 设备不支持该档：不给「设为当前档位」，只如实说明
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Info, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    L.perm.rootNotDetectedHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        } else if (displayed != activeTier) {
                            // 门控：只有该档位权限清单**全部配齐**才允许设为当前档位
                            val ready = tierState.ready(displayed)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PientButton(
                                    L.perm.setActiveTier,
                                    onClick = { setActiveTier(displayed) },
                                    enabled = ready,
                                    modifier = Modifier.widthIn(max = 200.dp),
                                    height = 38,   // 与同页「设置向导」按钮同高
                                )
                                // 未配齐：按钮变暗不可点 + 一行说明缺什么（复用各档位既有提示文案）
                                tierState.missingHint(displayed)?.let { hint ->
                                    Text(
                                        hint,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                                    )
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.CheckCircle, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    L.perm.tierInUse,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    CardDivider()
                    Spacer(Modifier.height(6.dp))

                    // 该档位对应的权限清单
                    AnimatedContent(
                        targetState = displayed,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "tierPerms",
                    ) { tier ->
                        TierPermissionList(
                            tier = tier,
                            status = status,
                            shizukuInstalled = shizukuInstalled,
                            shizukuRunning = shizukuRunning,
                            shizukuAuthorized = shizukuAuthorized,
                            deviceRooted = deviceRooted,
                            rootGranted = rootGranted,
                            rootProbed = rootProbed,
                            onStorage = { grantStorage() },
                            onBattery = { grantBattery() },
                            onLocation = { grantLocation() },
                            onOverlay = { grantOverlay() },
                            onShizuku = { grantShizuku() },
                            onRoot = { requestRoot() },
                        )
                    }
                }
            }

            // ═══════════ Android shell（系统命令通道，三档边界） ═══════════
            // 系统命令通道属于"权限能力"，只在权限页管；bash 的执行落点与这里无关（只有 Ubuntu 两档）。
            //
            // 这里是**真状态**：通道 = 应用在 Java 侧用 Shizuku / su 直接把命令
            // 扔给 Android 系统执行，即发即走、没有会话；AI 的工具 `android_shell` 与它同源。
            SectionHeader(L.perm.shellSectionTitle, icon = Icons.Outlined.Terminal)
            PermissionCardBox {
                Column(Modifier.padding(14.dp)) {
                    // shellReady / shellText 由 readStatus 读入 state（刷新键、换档位都会更新；不在组合期现查 binder）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (shellReady) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                            null,
                            tint = if (shellReady) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            shellText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (shellReady) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp).weight(1f),
                        )
                    }
                    Text(
                        L.perm.shellNote1 +
                            L.perm.shellNote2 +
                            L.perm.shellNote3 +
                            if (shellReady && displayed == PermissionTier.ROOT) {
                                L.perm.shellNoteRoot
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            // ═══════════ 设置向导（档位未就绪时） ═══════════
            val wizardTier = when {
                tierState.ready(displayed) -> null
                displayed == PermissionTier.STANDARD -> null      // 标准档无外部组件，缺的只是上面的四项授权
                else -> displayed
            }
            if (wizardTier != null) {
                SectionHeader(L.perm.wizardTitle, icon = Icons.Outlined.Build)
                PermissionCardBox {
                    Column(Modifier.padding(14.dp)) {
                        when (wizardTier) {
                            PermissionTier.DEBUGGER -> ShizukuWizard(
                                installed = shizukuInstalled,
                                running = shizukuRunning,
                                authorized = shizukuAuthorized,
                                onAction = { grantShizuku() },
                                onGuide = {
                                    if (!ShizukuGateway.openUrl(context, ShizukuGateway.GUIDE_URL)) {
                                        toast(L.perm.openShizukuGuideFailed)
                                    }
                                },
                            )
                            else -> RootWizard(
                                rooted = deviceRooted,
                                granted = rootGranted,
                                requesting = rootRequesting,
                                onRequest = { requestRoot() },
                                onGuide = {
                                    if (!ShizukuGateway.openUrl(context, ROOT_GUIDE_URL)) {
                                        toast(L.perm.openRootGuideFailed)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────── 权限清单 ───────────────────────────

@Composable
private fun TierPermissionList(
    tier: PermissionTier,
    status: SystemPermissions.Status,
    shizukuInstalled: Boolean,
    shizukuRunning: Boolean,
    shizukuAuthorized: Boolean,
    deviceRooted: Boolean,
    rootGranted: Boolean,
    rootProbed: Boolean,
    onStorage: () -> Unit,
    onBattery: () -> Unit,
    onLocation: () -> Unit,
    onOverlay: () -> Unit,
    onShizuku: () -> Unit,
    onRoot: () -> Unit,
) {
    Column {
        GroupLabel(L.perm.basicPermissions, "${status.readyCount}/${SystemPermissions.TOTAL}")
        PermissionStatusRow(L.perm.storagePermission, status.storage, onClick = onStorage)
        PermissionStatusRow(L.perm.batteryExemption, status.battery, onClick = onBattery)
        PermissionStatusRow(L.perm.locationPermission, status.location, onClick = onLocation)
        PermissionStatusRow(L.perm.overlayPermission, status.overlay, onClick = onOverlay)

        when (tier) {
            PermissionTier.DEBUGGER -> {
                Spacer(Modifier.height(8.dp))
                GroupLabel(L.perm.shizukuService)
                PermissionStatusRow(L.perm.shizukuAppInstalled, shizukuInstalled, pendingText = L.perm.goDownload, onClick = onShizuku)
                PermissionStatusRow(L.perm.serviceRunning, shizukuRunning, pendingText = L.perm.goStart, onClick = onShizuku)
                PermissionStatusRow(L.perm.pientAuthorized, shizukuAuthorized, pendingText = L.perm.goAuthorize, onClick = onShizuku)
            }
            PermissionTier.ROOT -> {
                Spacer(Modifier.height(8.dp))
                GroupLabel(L.perm.rootChannel)
                PermissionStatusRow(
                    L.perm.deviceRooted,
                    deviceRooted,
                    pendingText = L.perm.notDetected,
                    onClick = null,   // 设备是否 Root 由设备决定，本页只能如实显示
                )
                PermissionStatusRow(
                    L.perm.suGranted,
                    rootGranted,
                    pendingText = if (rootProbed) L.perm.denied else L.perm.notVerified,
                    onClick = onRoot,
                )
            }
            PermissionTier.STANDARD -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    L.perm.standardHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 权限状态行（标题 + 右侧状态；未授权时整行可点直达授权页 —— 交互红线：整块可点） */
@Composable
private fun PermissionStatusRow(
    title: String,
    granted: Boolean,
    pendingText: String = L.perm.goAuthorize,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 9.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (granted) {
            Text(
                L.common.granted,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pendingText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    " ⚠",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ─────────────────────────── 设置向导 ───────────────────────────

private const val ROOT_GUIDE_URL = "https://github.com/topjohnwu/Magisk"

/** 刷新键的最短可见时长（ms）：真读一遍通常只要几毫秒，圆弧得转够时长才看得出「点到了」 */
private const val REFRESH_FEEDBACK_MS = 420L

@Composable
private fun ShizukuWizard(
    installed: Boolean,
    running: Boolean,
    authorized: Boolean,
    onAction: () -> Unit,
    onGuide: () -> Unit,
) {
    WizardHeader(
        icon = Icons.Outlined.Build,
        title = L.perm.shizukuService,
        badge = when {
            !installed -> L.perm.notInstalled to MaterialTheme.colorScheme.error
            !running -> L.perm.notRunning to MaterialTheme.colorScheme.error
            !authorized -> L.perm.unauthorized to MaterialTheme.colorScheme.error
            else -> L.perm.ready to MaterialTheme.colorScheme.primary
        },
    )
    Text(
        L.perm.shizukuNote,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
    Spacer(Modifier.height(10.dp))
    WizardStepRow(1, L.perm.stepInstallShizuku, installed, L.perm.goDownload) { onAction() }
    WizardStepRow(2, L.perm.stepStartShizuku, running, L.perm.openShizuku) { onAction() }
    WizardStepRow(3, L.perm.stepAuthorizeShizuku, authorized, L.perm.requestAccess) { onAction() }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PientButton(
            when {
                !installed -> L.perm.downloadShizuku
                !running -> L.perm.startShizuku
                else -> L.perm.requestAuthorization
            },
            onClick = onAction,
            modifier = Modifier.weight(1f),
            height = 38,
        )
        PientButton(L.perm.viewGuide, onClick = onGuide, primary = false, modifier = Modifier.weight(1f), height = 38)
    }
}

@Composable
private fun RootWizard(
    rooted: Boolean,
    granted: Boolean,
    requesting: Boolean,
    onRequest: () -> Unit,
    onGuide: () -> Unit,
) {
    WizardHeader(
        icon = Icons.Outlined.Lock,
        title = L.perm.rootChannel,
        badge = when {
            !rooted -> L.perm.rootNotDetected to MaterialTheme.colorScheme.error
            !granted -> L.perm.unauthorized to MaterialTheme.colorScheme.error
            else -> L.perm.ready to MaterialTheme.colorScheme.primary
        },
    )
    Text(
        L.perm.rootNote,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
    Spacer(Modifier.height(10.dp))
    WizardStepRow(1, L.perm.stepRootDevice, rooted, L.perm.viewGuideArrow) { onGuide() }
    WizardStepRow(2, L.perm.stepGrantSu, granted, L.perm.requestAccess) { onRequest() }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PientButton(
            L.perm.requestRootAccess,
            onClick = onRequest,
            modifier = Modifier.weight(1f),
            loading = requesting,
            height = 38,
        )
        PientButton(L.perm.viewGuide, onClick = onGuide, primary = false, modifier = Modifier.weight(1f), height = 38)
    }
}

@Composable
private fun WizardHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, badge: Pair<String, androidx.compose.ui.graphics.Color>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        StatusBadge(badge.first, badge.second)
    }
}

/** 向导步骤行：序号圆点 + 标题 + （未完成时）右侧动作文字；已完成 = 主色 ✓ */
@Composable
private fun WizardStepRow(
    index: Int,
    title: String,
    done: Boolean,
    actionText: String,
    onAction: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (!done) it.clickable(onClick = onAction) else it }
            .padding(vertical = 7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    if (done) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    CircleShape,
                )
                .border(
                    1.dp,
                    if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    Icons.Outlined.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                )
            } else {
                Text(
                    "$index",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        if (done) {
            Text(
                L.perm.completed,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                actionText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ─────────────────────────── 卡片容器 ───────────────────────────

/** 分组卡片容器（与设置页 SettingsGroup 同款：surfaceContainerLow + 16dp 圆角 + 描边） */
@Composable
private fun PermissionCardBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
        content()
    }
}

/** 卡内分隔线（内容已有 14dp 内边距，此处不再缩进） */
@Composable
private fun CardDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

/** 卡内小分组标题 */
@Composable
private fun GroupLabel(text: String, trailing: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
