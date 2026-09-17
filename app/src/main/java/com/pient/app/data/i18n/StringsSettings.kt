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
    val messageNotify: String
    val messageNotifyDesc: String
    val messageNotifySound: String
    val messageNotifySoundDesc: String
    val messageNotifyVibrate: String
    val messageNotifyVibrateDesc: String
    val messageNotifyNoPermission: String

    // ── 应用日志管理（2026-09-17）──
    val logTitle: String
    val logSubtitle: String
    val logFileLabel: String
    val logLocationNote: String
    val logEmptyStat: String
    fun logStatLine(a0: Any?, a1: Any?): String
    fun logRangeLine(a0: Any?, a1: Any?): String
    val logExport: String
    val logExportSubtitle: String
    val logExporting: String
    val logView: String
    val logViewSubtitle: String
    val logClear: String
    val logClearSubtitle: String
    val logClearTitle: String
    val logClearBody: String
    val logCleared: String
    fun logExported(a0: Any?): String
    val logExportEmpty: String
    val logExportFailed: String
    val logNote: String
    val logViewerTitle: String
    val logFilterAll: String
    val logFilterWarn: String
    val logFilterError: String
    fun logViewerCount(a0: Any?): String
    val logViewerEmpty: String
    val logDocTitle: String
    val logExportTimeLabel: String
    val logSectionEnv: String
    fun logSectionStderr(a0: Any?): String
    fun logSectionApp(a0: Any?): String
    val logSectionSystem: String
    fun logSystemNeedTier(a0: Any?): String
    val logSystemSelf: String
    val logSystemEmpty: String
    fun logSystemFailed(a0: Any?): String
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
    override val messageNotify: String = "消息通知"
    override val messageNotifyDesc: String = "当 AI 回复时发送系统通知"
    override val messageNotifySound: String = "消息通知提示音"
    override val messageNotifySoundDesc: String = "当消息通知触发时播放消息提示音"
    override val messageNotifyVibrate: String = "消息通知震动"
    override val messageNotifyVibrateDesc: String = "当消息通知触发时进行震动提醒"
    override val messageNotifyNoPermission: String = "未授予通知权限：消息通知不会显示（可在系统设置里开启）"

    // ── 应用日志管理（2026-09-17）──
    override val logTitle: String = "应用日志管理"
    override val logSubtitle: String = "运行记录 · 导出与清理"
    override val logFileLabel: String = "日志文件"
    override val logLocationNote: String = "位置：应用私有目录（导出后可在系统「下载/Pient/」查看）"
    override val logEmptyStat: String = "还没有日志记录"
    override fun logStatLine(a0: Any?, a1: Any?): String = "${a0} · ${a1} 行"
    override fun logRangeLine(a0: Any?, a1: Any?): String = "${a0} — ${a1}"
    override val logExport: String = "导出日志"
    override val logExportSubtitle: String = "生成到系统「下载/Pient/」，可发给开发者分析"
    override val logExporting: String = "正在导出…"
    override val logView: String = "查看最近日志"
    override val logViewSubtitle: String = "只读查看最近 2000 行，可按级别筛选"
    override val logClear: String = "清空日志"
    override val logClearSubtitle: String = "删除已记录的全部运行日志"
    override val logClearTitle: String = "清空日志？"
    override val logClearBody: String = "将删除应用已记录的全部运行日志（不影响会话、项目与配置）。"
    override val logCleared: String = "日志已清空"
    override fun logExported(a0: Any?): String = "日志已导出：${a0}"
    override val logExportEmpty: String = "还没有可导出的日志"
    override val logExportFailed: String = "导出失败，请重试"
    override val logNote: String = "日志记录应用运行过程（含 pi 报错原文与环境信息）。不含 API 密钥与会话内容 —— 会话导出见「项目与会话记录」。"
    override val logViewerTitle: String = "日志"
    override val logFilterAll: String = "全部"
    override val logFilterWarn: String = "警告以上"
    override val logFilterError: String = "错误"
    override fun logViewerCount(a0: Any?): String = "${a0} 行"
    override val logViewerEmpty: String = "没有符合筛选的日志"
    override val logDocTitle: String = "Pient 应用日志"
    override val logExportTimeLabel: String = "导出时间："
    override val logSectionEnv: String = "环境报告"
    override fun logSectionStderr(a0: Any?): String = "pi stderr（最近 ${a0} 行）"
    override fun logSectionApp(a0: Any?): String = "应用日志（${a0} 行）"
    override val logSectionSystem: String = "系统日志（logcat）"
    override fun logSystemNeedTier(a0: Any?): String = "系统日志需要调试 / Root 档；当前档位 ${a0} 没有特权通道"
    override val logSystemSelf: String = "应用自身 logcat 尾部（无特权通道：只有本应用的日志行；系统其它进程的日志需要调试 / Root 档）"
    override val logSystemEmpty: String = "这次抓取没有匹配到日志行"
    override fun logSystemFailed(a0: Any?): String = "系统日志读取失败：${a0}"
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
    override val messageNotify: String = "Message notifications"
    override val messageNotifyDesc: String = "Send a system notification when the AI replies"
    override val messageNotifySound: String = "Notification sound"
    override val messageNotifySoundDesc: String = "Play a sound when a message notification fires"
    override val messageNotifyVibrate: String = "Notification vibration"
    override val messageNotifyVibrateDesc: String = "Vibrate when a message notification fires"
    override val messageNotifyNoPermission: String = "Notification permission not granted: message notifications stay hidden (you can enable it in system settings)"

    // ── 应用日志管理（2026-09-17）──
    override val logTitle: String = "App log management"
    override val logSubtitle: String = "Runtime log · export and cleanup"
    override val logFileLabel: String = "Log file"
    override val logLocationNote: String = "Location: app-private storage (after export it lands in the system “Downloads/Pient/” folder)"
    override val logEmptyStat: String = "No log recorded yet"
    override fun logStatLine(a0: Any?, a1: Any?): String = "${a0} · ${a1} lines"
    override fun logRangeLine(a0: Any?, a1: Any?): String = "${a0} — ${a1}"
    override val logExport: String = "Export log"
    override val logExportSubtitle: String = "Writes to the system “Downloads/Pient/” folder — send it to the developer for analysis"
    override val logExporting: String = "Exporting…"
    override val logView: String = "View recent log"
    override val logViewSubtitle: String = "Read-only, last 2000 lines, filterable by level"
    override val logClear: String = "Clear log"
    override val logClearSubtitle: String = "Delete all recorded runtime log"
    override val logClearTitle: String = "Clear log?"
    override val logClearBody: String = "This deletes all recorded runtime log (sessions, projects and settings are not affected)."
    override val logCleared: String = "Log cleared"
    override fun logExported(a0: Any?): String = "Log exported: ${a0}"
    override val logExportEmpty: String = "Nothing to export yet"
    override val logExportFailed: String = "Export failed, please retry"
    override val logNote: String = "The log records the app's runtime (including pi's raw errors and environment info). It contains no API keys and no session content — export sessions from “Project & session records”."
    override val logViewerTitle: String = "Log"
    override val logFilterAll: String = "All"
    override val logFilterWarn: String = "Warning+"
    override val logFilterError: String = "Error"
    override fun logViewerCount(a0: Any?): String = "${a0} lines"
    override val logViewerEmpty: String = "No log lines match the filter"
    override val logDocTitle: String = "Pient app log"
    override val logExportTimeLabel: String = "Exported at: "
    override val logSectionEnv: String = "Environment report"
    override fun logSectionStderr(a0: Any?): String = "pi stderr (last ${a0} lines)"
    override fun logSectionApp(a0: Any?): String = "App log (${a0} lines)"
    override val logSectionSystem: String = "System log (logcat)"
    override fun logSystemNeedTier(a0: Any?): String = "System log needs the Debugger / Root tier; tier ${a0} has no privileged channel"
    override val logSystemSelf: String = "The app's own logcat tail (no privileged channel: this app's lines only; other processes' logs need the Debugger / Root tier)"
    override val logSystemEmpty: String = "No matching log lines were captured this time"
    override fun logSystemFailed(a0: Any?): String = "Failed to read the system log: ${a0}"
}
