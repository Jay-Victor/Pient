package com.pient.app.data.i18n

/** onboarding 组文案（由 i18n 流水线生成；zh = 源码原文字面量） */
interface OnboardingStrings {
    val featureAgentTitle: String
    val featureAgentDesc: String
    val featureEcosystemTitle: String
    val featureEcosystemDesc: String
    val featureLocalTitle: String
    val featureLocalDesc: String
    val featurePermissionTitle: String
    val featurePermissionDesc: String
    val permStorage: String
    val permStorageDesc: String
    val permBattery: String
    val permBatteryDesc: String
    val permLocation: String
    val permLocationDesc: String
    val permOverlay: String
    val permOverlayDesc: String
    val storageSettingsFailed: String
    val overlaySettingsFailed: String
    val batterySettingsFailed: String
    val basicPermissionTitle: String
    val basicPermissionRequired: String
    val checkingPermissions: String
    fun lastCheckSummary(a0: Any?, a1: Any?): String
    val permissionStatusHint: String
    val checking: String
    val checkPermissions: String
    val next: String
    fun nextWithProgress(a0: Any?): String
    val grant: String
    val systemPermissionTitle: String
    val recommended: String
    val enterPient: String
    val startupLoading: String
}

object ZhOnboarding : OnboardingStrings {
    override val featureAgentTitle: String = "AI Agent 工作台"
    override val featureAgentDesc: String = "基于 Pi Agent 的移动端工作台，支持深度任务编排与多步推理，全程本地运行"
    override val featureEcosystemTitle: String = "插件与 Skill 生态"
    override val featureEcosystemDesc: String = "完整兼容 Pi 官方插件体系与 Skill 机制，桌面端生态能力无降级迁移"
    override val featureLocalTitle: String = "本地优先 · 数据自持"
    override val featureLocalDesc: String = "会话与配置全部存储于设备本地，数据不上云、完全自持，隐私可控"
    override val featurePermissionTitle: String = "三级权限体系"
    override val featurePermissionDesc: String = "标准 / 调试 / Root 三级权限按任务动态授权，兼顾能力与安全"
    override val permStorage: String = "存储权限"
    override val permStorageDesc: String = "读取项目文件 / 会话数据"
    override val permBattery: String = "电池优化豁免"
    override val permBatteryDesc: String = "保活 / 后台任务"
    override val permLocation: String = "位置权限"
    override val permLocationDesc: String = "位置相关工具调用"
    override val permOverlay: String = "悬浮窗权限"
    override val permOverlayDesc: String = "悬浮终端 / 快捷面板"
    override val storageSettingsFailed: String = "无法打开存储权限设置"
    override val overlaySettingsFailed: String = "无法打开悬浮窗权限设置"
    override val batterySettingsFailed: String = "无法打开电池优化设置"
    override val basicPermissionTitle: String = "基础权限设置"
    override val basicPermissionRequired: String = "请先完成基础权限授权"
    override val checkingPermissions: String = "正在检查权限状态…"
    override fun lastCheckSummary(a0: Any?, a1: Any?): String = "上次检查：${a0}  ·  ${a1}/4 项已授权"
    override val permissionStatusHint: String = "权限状态实时展示 · 点击下方重检"
    override val checking: String = "正在检查…"
    override val checkPermissions: String = "检查权限状态"
    override val next: String = "下一步"
    override fun nextWithProgress(a0: Any?): String = "下一步（${a0}/4 已就绪）"
    override val grant: String = "去授权 →"
    override val systemPermissionTitle: String = "系统权限选项"
    override val recommended: String = "〔推荐〕"
    override val enterPient: String = "确定，进入 Pient"
    override val startupLoading: String = "启动加载中"
}

object EnOnboarding : OnboardingStrings {
    override val featureAgentTitle: String = "AI Agent workspace"
    override val featureAgentDesc: String = "A mobile workspace built on the Pi Agent, supporting deep task orchestration and multi-step reasoning, all running locally"
    override val featureEcosystemTitle: String = "Plugin & Skill ecosystem"
    override val featureEcosystemDesc: String = "Fully compatible with the official Pi plugin system and Skill mechanism, so desktop ecosystem capabilities carry over without downgrade"
    override val featureLocalTitle: String = "Local-first · you own your data"
    override val featureLocalDesc: String = "Sessions and settings are stored entirely on-device — nothing goes to the cloud, fully self-owned, privacy under your control"
    override val featurePermissionTitle: String = "Three-tier permission system"
    override val featurePermissionDesc: String = "Standard / Debug / Root permissions granted dynamically per task, balancing capability and safety"
    override val permStorage: String = "Storage permission"
    override val permStorageDesc: String = "Read project files / session data"
    override val permBattery: String = "Battery optimization exemption"
    override val permBatteryDesc: String = "Keep-alive / background tasks"
    override val permLocation: String = "Location permission"
    override val permLocationDesc: String = "Location-based tool calls"
    override val permOverlay: String = "Overlay permission"
    override val permOverlayDesc: String = "Floating terminal / quick panel"
    override val storageSettingsFailed: String = "Cannot open storage permission settings"
    override val overlaySettingsFailed: String = "Cannot open overlay permission settings"
    override val batterySettingsFailed: String = "Cannot open battery optimization settings"
    override val basicPermissionTitle: String = "Basic permissions"
    override val basicPermissionRequired: String = "Please grant the basic permissions first"
    override val checkingPermissions: String = "Checking permission status…"
    override fun lastCheckSummary(a0: Any?, a1: Any?): String = "Last checked: ${a0}  ·  ${a1}/4 authorized"
    override val permissionStatusHint: String = "Permission status is live · tap below to re-check"
    override val checking: String = "Checking…"
    override val checkPermissions: String = "Check permission status"
    override val next: String = "Next"
    override fun nextWithProgress(a0: Any?): String = "Next (${a0}/4 ready)"
    override val grant: String = "Grant →"
    override val systemPermissionTitle: String = "System permission options"
    override val recommended: String = "Recommended"
    override val enterPient: String = "OK, enter Pient"
    override val startupLoading: String = "Starting up"
}
