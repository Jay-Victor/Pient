package com.pient.app.data.i18n

/** perm 组文案（由 i18n 流水线生成；zh = 源码原文字面量） */
interface PermStrings {
    val tierTitle: String
    val shizukuService: String
    val goDownload: String
    val goAuthorize: String
    val rootChannel: String
    val unauthorized: String
    val ready: String
    val requestAccess: String
    val viewGuide: String
    val tierStandard: String
    val tierStandardDesc: String
    val recommended: String
    val tierDebug: String
    val tierDebugDesc: String
    val shizukuRequired: String
    val tierRoot: String
    val tierRootDesc: String
    val rootedDeviceRequired: String
    val openStorageSettingsFailed: String
    val openOverlaySettingsFailed: String
    val openBatterySettingsFailed: String
    val openShizukuDownloadFailed: String
    val openShizukuAppFailed: String
    val startShizukuFirst: String
    val rootGranted: String
    val rootNotGranted: String
    val shizukuSetupFirst: String
    val rootDeviceRequired: String
    val basicPermissionsMissing: String
    fun tierSwitched(a0: Any?): String
    fun tierSwitchedWithHint(a0: Any?, a1: Any?): String
    val shizukuGranted: String
    val shizukuDenied: String
    val refreshStatus: String
    val rootNotDetectedHint: String
    val setActiveTier: String
    val tierInUse: String
    val shellSectionTitle: String
    val shellNote1: String
    val shellNote2: String
    val shellNote3: String
    val shellNoteRoot: String
    val wizardTitle: String
    val openShizukuGuideFailed: String
    val openRootGuideFailed: String
    val basicPermissions: String
    val storagePermission: String
    val batteryExemption: String
    val locationPermission: String
    val overlayPermission: String
    val shizukuAppInstalled: String
    val serviceRunning: String
    val goStart: String
    val pientAuthorized: String
    val deviceRooted: String
    val notDetected: String
    val suGranted: String
    val denied: String
    val notVerified: String
    val standardHint: String
    val activeNow: String
    val available: String
    val notSupported: String
    val authorize: String
    val notInstalled: String
    val notRunning: String
    val shizukuNote: String
    val stepInstallShizuku: String
    val stepStartShizuku: String
    val openShizuku: String
    val stepAuthorizeShizuku: String
    val downloadShizuku: String
    val startShizuku: String
    val requestAuthorization: String
    val rootNotDetected: String
    val rootNote: String
    val stepRootDevice: String
    val viewGuideArrow: String
    val stepGrantSu: String
    val requestRootAccess: String
    val completed: String
}

object ZhPerm : PermStrings {
    override val tierTitle: String = "权限档位"
    override val shizukuService: String = "Shizuku 服务"
    override val goDownload: String = "去下载 →"
    override val goAuthorize: String = "去授权 →"
    override val rootChannel: String = "Root 通道"
    override val unauthorized: String = "未授权"
    override val ready: String = "已就绪"
    override val requestAccess: String = "请求授权 →"
    override val viewGuide: String = "查看教程"
    override val tierStandard: String = "标准权限"
    override val tierStandardDesc: String = "使用系统标准权限模型：网络、存储、通知等常规授权，无需安装任何额外工具，覆盖日常 Agent 任务所需能力"
    override val recommended: String = "推荐"
    override val tierDebug: String = "调试权限"
    override val tierDebugDesc: String = "借助 Shizuku 获得 ADB 级调试能力：UI 自动化、应用管理、系统设置读写；无需解锁 Bootloader，设备重启后需重新激活"
    override val shizukuRequired: String = "需安装 Shizuku"
    override val tierRoot: String = "Root 权限"
    override val tierRootDesc: String = "以 Root 身份运行：chroot 容器、系统级文件操作与完整工具链；权限等级最高、能力全部解锁，安全风险需自行评估"
    override val rootedDeviceRequired: String = "⚠ 需设备已 Root"
    override val openStorageSettingsFailed: String = "无法打开存储权限设置"
    override val openOverlaySettingsFailed: String = "无法打开悬浮窗权限设置"
    override val openBatterySettingsFailed: String = "无法打开电池优化设置"
    override val openShizukuDownloadFailed: String = "无法打开 Shizuku 下载页"
    override val openShizukuAppFailed: String = "无法打开 Shizuku 应用"
    override val startShizukuFirst: String = "请先在 Shizuku 应用中启动服务"
    override val rootGranted: String = "已获得 Root 权限"
    override val rootNotGranted: String = "未获得 Root 权限（设备未 Root 或授权被拒绝）"
    override val shizukuSetupFirst: String = "需先完成 Shizuku 安装与授权"
    override val rootDeviceRequired: String = "需设备已 Root 并授予 Pient 权限"
    override val basicPermissionsMissing: String = "基础权限未全部授权"
    override fun tierSwitched(a0: Any?): String = "已切换为「${a0}」"
    override fun tierSwitchedWithHint(a0: Any?, a1: Any?): String = "已切换为「${a0}」 · ${a1}"
    override val shizukuGranted: String = "已获得 Shizuku 授权"
    override val shizukuDenied: String = "Shizuku 授权被拒绝"
    override val refreshStatus: String = "刷新权限状态"
    override val rootNotDetectedHint: String = "设备不支持：未检测到 Root（Magisk / su）"
    override val setActiveTier: String = "设为当前档位"
    override val tierInUse: String = "当前使用中"
    override val shellSectionTitle: String = "Android shell（系统命令通道）"
    override val shellNote1: String = "这是一条**独立通道**：命令由系统直接执行，不经过 Ubuntu、也不经过终端会话；"
    override val shellNote2: String = "每次调用都是新进程（没有会话、不保留 cd/export 状态）。"
    override val shellNote3: String = "AI 侧通过 `android_shell` 工具使用它。"
    override val shellNoteRoot: String = " Root 档下还可以在「环境配置」把 Ubuntu 从 PRoot 升级为 chroot。"
    override val wizardTitle: String = "设置向导"
    override val openShizukuGuideFailed: String = "无法打开 Shizuku 激活教程"
    override val openRootGuideFailed: String = "无法打开 Root 教程"
    override val basicPermissions: String = "基础权限"
    override val storagePermission: String = "存储权限"
    override val batteryExemption: String = "电池优化豁免"
    override val locationPermission: String = "位置权限"
    override val overlayPermission: String = "悬浮窗权限"
    override val shizukuAppInstalled: String = "已安装 Shizuku 应用"
    override val serviceRunning: String = "服务运行中"
    override val goStart: String = "去启动 →"
    override val pientAuthorized: String = "已授权 Pient"
    override val deviceRooted: String = "设备已 Root"
    override val notDetected: String = "未检测到"
    override val suGranted: String = "已授予 Pient su 权限"
    override val denied: String = "已拒绝"
    override val notVerified: String = "未验证"
    override val standardHint: String = "标准权限开箱即用：无需安装任何额外组件，四项基础权限齐备即可使用日常 Agent 能力。"
    override val activeNow: String = "  当前生效"
    override val available: String = "可用 ✓"
    override val notSupported: String = "设备不支持"
    override val authorize: String = "去授权"
    override val notInstalled: String = "未安装"
    override val notRunning: String = "未运行"
    override val shizukuNote: String = "Shizuku 以 ADB 权限运行，无需解锁 Bootloader；设备重启后服务需要重新激活（无线调试配对或一次 ADB 授权），授权本身不会丢失。"
    override val stepInstallShizuku: String = "安装 Shizuku 应用"
    override val stepStartShizuku: String = "启动 Shizuku 服务"
    override val openShizuku: String = "打开 Shizuku →"
    override val stepAuthorizeShizuku: String = "授权 Pient 使用 Shizuku"
    override val downloadShizuku: String = "下载 Shizuku"
    override val startShizuku: String = "启动 Shizuku"
    override val requestAuthorization: String = "请求授权"
    override val rootNotDetected: String = "未检测到 Root"
    override val rootNote: String = "Pient 通过 su 通道获得最高级系统能力（系统级文件操作与特权能力）。首次请求会由 Root 管理器（Magisk / KernelSU / APatch）弹出授权框；未 Root 的设备可继续使用标准 / 调试权限。"
    override val stepRootDevice: String = "设备已获取 Root 权限"
    override val viewGuideArrow: String = "查看教程 →"
    override val stepGrantSu: String = "授予 Pient su 权限"
    override val requestRootAccess: String = "请求 Root 授权"
    override val completed: String = "已完成 ✓"
}

object EnPerm : PermStrings {
    override val tierTitle: String = "Permission tier"
    override val shizukuService: String = "Shizuku service"
    override val goDownload: String = "Download →"
    override val goAuthorize: String = "Authorize →"
    override val rootChannel: String = "Root channel"
    override val unauthorized: String = "Unauthorized"
    override val ready: String = "Ready"
    override val requestAccess: String = "Request access →"
    override val viewGuide: String = "View guide"
    override val tierStandard: String = "Standard"
    override val tierStandardDesc: String = "Uses the standard system permission model: network, storage, notifications and other routine grants, no extra tools to install, covering everyday Agent tasks"
    override val recommended: String = "Recommended"
    override val tierDebug: String = "Debug"
    override val tierDebugDesc: String = "ADB-level debug capabilities via Shizuku: UI automation, app management, system settings read/write; no Bootloader unlock needed, reactivation required after a reboot"
    override val shizukuRequired: String = "Shizuku required"
    override val tierRoot: String = "Root"
    override val tierRootDesc: String = "Runs as Root: chroot containers, system-level file operations and a full toolchain; the highest privilege tier with everything unlocked, assess the security risk yourself"
    override val rootedDeviceRequired: String = "⚠ Rooted device required"
    override val openStorageSettingsFailed: String = "Can't open storage permission settings"
    override val openOverlaySettingsFailed: String = "Can't open overlay permission settings"
    override val openBatterySettingsFailed: String = "Can't open battery optimization settings"
    override val openShizukuDownloadFailed: String = "Can't open the Shizuku download page"
    override val openShizukuAppFailed: String = "Can't open the Shizuku app"
    override val startShizukuFirst: String = "Start the service in the Shizuku app first"
    override val rootGranted: String = "Root access granted"
    override val rootNotGranted: String = "Root access not granted (device not rooted or request denied)"
    override val shizukuSetupFirst: String = "Complete Shizuku installation and authorization first"
    override val rootDeviceRequired: String = "Requires a rooted device with Pient granted access"
    override val basicPermissionsMissing: String = "Not all basic permissions granted"
    override fun tierSwitched(a0: Any?): String = "Switched to \"${a0}\""
    override fun tierSwitchedWithHint(a0: Any?, a1: Any?): String = "Switched to \"${a0}\" · ${a1}"
    override val shizukuGranted: String = "Shizuku access granted"
    override val shizukuDenied: String = "Shizuku access denied"
    override val refreshStatus: String = "Refresh permission status"
    override val rootNotDetectedHint: String = "Device not supported: no Root detected (Magisk / su)"
    override val setActiveTier: String = "Set as active tier"
    override val tierInUse: String = "In use"
    override val shellSectionTitle: String = "Android shell (system command channel)"
    override val shellNote1: String = "This is a **separate channel**: commands are executed by the system directly, neither through Ubuntu nor through a Terminal session;"
    override val shellNote2: String = "Every call is a new process (no Session, and cd/export state is not kept)."
    override val shellNote3: String = "The AI uses it through the `android_shell` tool."
    override val shellNoteRoot: String = " On the Root tier you can also upgrade Ubuntu from PRoot to chroot in Environment."
    override val wizardTitle: String = "Setup wizard"
    override val openShizukuGuideFailed: String = "Can't open the Shizuku activation guide"
    override val openRootGuideFailed: String = "Can't open the Root guide"
    override val basicPermissions: String = "Basic permissions"
    override val storagePermission: String = "Storage permission"
    override val batteryExemption: String = "Battery optimization exemption"
    override val locationPermission: String = "Location permission"
    override val overlayPermission: String = "Overlay permission"
    override val shizukuAppInstalled: String = "Shizuku app installed"
    override val serviceRunning: String = "Service running"
    override val goStart: String = "Start →"
    override val pientAuthorized: String = "Pient authorized"
    override val deviceRooted: String = "Device rooted"
    override val notDetected: String = "Not detected"
    override val suGranted: String = "Pient granted su access"
    override val denied: String = "Denied"
    override val notVerified: String = "Not verified"
    override val standardHint: String = "Standard works out of the box: no extra components to install — with all four basic permissions in place you can use everyday Agent capabilities."
    override val activeNow: String = "  Active now"
    override val available: String = "Available ✓"
    override val notSupported: String = "Not supported"
    override val authorize: String = "Authorize"
    override val notInstalled: String = "Not installed"
    override val notRunning: String = "Not running"
    override val shizukuNote: String = "Shizuku runs with ADB permissions, so no Bootloader unlock is needed; after a reboot the service must be reactivated (wireless debugging pairing or one ADB authorization) — the authorization itself is not lost."
    override val stepInstallShizuku: String = "Install the Shizuku app"
    override val stepStartShizuku: String = "Start the Shizuku service"
    override val openShizuku: String = "Open Shizuku →"
    override val stepAuthorizeShizuku: String = "Authorize Pient to use Shizuku"
    override val downloadShizuku: String = "Download Shizuku"
    override val startShizuku: String = "Start Shizuku"
    override val requestAuthorization: String = "Request access"
    override val rootNotDetected: String = "No Root detected"
    override val rootNote: String = "Pient gains the highest system capabilities through the su channel (system-level file operations and privileged capabilities). The first request triggers an authorization prompt from the Root manager (Magisk / KernelSU / APatch); devices without Root can keep using Standard / Debug permissions."
    override val stepRootDevice: String = "Device is rooted"
    override val viewGuideArrow: String = "View guide →"
    override val stepGrantSu: String = "Grant Pient su access"
    override val requestRootAccess: String = "Request Root access"
    override val completed: String = "Completed ✓"
}
