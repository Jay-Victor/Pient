package com.pient.app.data.i18n

/** settings 组文案（由 i18n 流水线生成；zh = 源码原文字面量） */
interface SettingsStrings {
    val about: String
    val updateLog: String
    val updateSubtitle: String
    val updateLogSubtitle: String
    val projectInfo: String
    val contact: String
    val developer: String
    val contactInfo: String
    val copyright: String
    val openSourceLicenses: String
    val openSourceNote: String
    val copyrightOwner: String
    val copyrightText: String
    val noChangelog: String
    val copyEmail: String
    val languageTitle: String
    val languageSubtitle: String
    val followSystem: String
    val personalization: String
    val theme: String
    val themeSubtitle: String
    val behavior: String
    val behaviorSubtitle: String
    val groupModels: String
    val modelConfig: String
    val modelConfigSubtitle: String
    val groupData: String
    val systemPermissions: String
    val systemPermissionsSubtitle: String
    val projectManagement: String
    val projectManagementSubtitle: String
    val usage: String
    val usageSubtitle: String
    val groupAbout: String
    val aboutSubtitle: String
    fun versionLabel(a0: Any?): String
    fun upToDateLabel(a0: Any?): String
    val languageApplied: String
    val backgroundKeepAlive: String
    val residentNotification: String
    val residentNotificationDesc: String
    val residentNotificationNoPermission: String
}

object ZhSettings : SettingsStrings {
    override val about: String = "关于"
    override val updateLog: String = "更新日志"
    override val updateSubtitle: String = "检测是否有新版本可用"
    override val updateLogSubtitle: String = "查看历史版本的更新内容"
    override val projectInfo: String = "项目信息"
    override val contact: String = "联系"
    override val developer: String = "开发者"
    override val contactInfo: String = "联系方式"
    override val copyright: String = "版权"
    override val openSourceLicenses: String = "开源许可声明"
    override val openSourceNote: String = "本项目以开源许可发布"
    override val copyrightOwner: String = "版权所有"
    override val copyrightText: String = "© 2026 Pient 保留所有权利"
    override val noChangelog: String = "暂无更新日志"
    override val copyEmail: String = "复制邮箱"
    override val languageTitle: String = "语言设置"
    override val languageSubtitle: String = "界面语言"
    override val followSystem: String = "跟随系统"
    override val personalization: String = "个性化"
    override val theme: String = "主题与外观"
    override val themeSubtitle: String = "深色 · 亮色 · 跟随系统"
    override val behavior: String = "行为设置"
    override val behaviorSubtitle: String = "侧边栏展出方式"
    override val groupModels: String = "AI模型配置"
    override val modelConfig: String = "服务商与模型配置"
    override val modelConfigSubtitle: String = "服务商 · 模型 · 密钥"
    override val groupData: String = "数据与权限"
    override val systemPermissions: String = "系统权限设置"
    override val systemPermissionsSubtitle: String = "标准 · 调试 · Root"
    override val projectManagement: String = "项目管理设置"
    override val projectManagementSubtitle: String = "项目与会话记录"
    override val usage: String = "模型用量信息"
    override val usageSubtitle: String = "Token 与成本统计"
    override val groupAbout: String = "关于Pient"
    override val aboutSubtitle: String = "版本与产品信息"
    override fun versionLabel(a0: Any?): String = "版本 ${a0}"
    override fun upToDateLabel(a0: Any?): String = "当前已是最新版本 ${a0}"
    override val languageApplied: String = "切换后即时生效（无需重启）"
    override val backgroundKeepAlive: String = "后台保活"
    override val residentNotification: String = "后台常驻通知"
    override val residentNotificationDesc: String = "打开后通知栏常驻一条 Pient 通知：进程不被系统清理，AI 回合与终端会话退到后台、熄屏后也能继续跑"
    override val residentNotificationNoPermission: String = "未授予通知权限：常驻通知不会显示（可在系统设置里开启）"
}

object EnSettings : SettingsStrings {
    override val about: String = "About"
    override val updateLog: String = "Changelog"
    override val updateSubtitle: String = "Check for a new version"
    override val updateLogSubtitle: String = "See what changed in past versions"
    override val projectInfo: String = "Project info"
    override val contact: String = "Contact"
    override val developer: String = "Developer"
    override val contactInfo: String = "Contact"
    override val copyright: String = "Copyright"
    override val openSourceLicenses: String = "Open-source licenses"
    override val openSourceNote: String = "Released under an open-source license"
    override val copyrightOwner: String = "Copyright holder"
    override val copyrightText: String = "© 2026 Pient. All rights reserved."
    override val noChangelog: String = "No changelog yet"
    override val copyEmail: String = "Copy email"
    override val languageTitle: String = "Language"
    override val languageSubtitle: String = "Interface language"
    override val followSystem: String = "Follow system"
    override val personalization: String = "Personalization"
    override val theme: String = "Theme & appearance"
    override val themeSubtitle: String = "Dark · Light · Follow system"
    override val behavior: String = "Behavior"
    override val behaviorSubtitle: String = "Sidebar reveal mode"
    override val groupModels: String = "AI models"
    override val modelConfig: String = "Providers & models"
    override val modelConfigSubtitle: String = "Providers · models · API keys"
    override val groupData: String = "Data & permissions"
    override val systemPermissions: String = "System permissions"
    override val systemPermissionsSubtitle: String = "Standard · Debug · Root"
    override val projectManagement: String = "Project management"
    override val projectManagementSubtitle: String = "Projects & sessions"
    override val usage: String = "Model usage"
    override val usageSubtitle: String = "Token & cost stats"
    override val groupAbout: String = "About Pient"
    override val aboutSubtitle: String = "Version & product info"
    override fun versionLabel(a0: Any?): String = "Version ${a0}"
    override fun upToDateLabel(a0: Any?): String = "Up to date (${a0})"
    override val languageApplied: String = "Applies immediately — no restart needed"
    override val backgroundKeepAlive: String = "Background keep-alive"
    override val residentNotification: String = "Resident notification"
    override val residentNotificationDesc: String = "Pins a Pient notification in the shade so the system does not clean up the process: AI turns and terminal sessions keep running in the background or with the screen off"
    override val residentNotificationNoPermission: String = "Notification permission not granted: the resident notification stays hidden (you can enable it in system settings)"
}
