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
    // ── 开源许可声明页（第三方组件清单 + GPL-3.0 全文）──
    val licensesIntro: String
    val licensesSectionRuntime: String
    val licensesSectionLibraries: String
    val licensesSectionFonts: String
    val licensesSectionData: String
    val licensesFullText: String
    val licensesVarious: String
    val licensesOpenData: String
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
    // ── 更新检查（关于页：检查更新 / 更新日志）──
    val checkingUpdate: String
    val loadingChangelog: String
    val newVersionFound: String
    fun newVersionDetail(a0: Any?, a1: Any?): String
    val updateNotes: String
    val updateCheckFailed: String
    val updateCheckFailedTitle: String
    val upToDateDesc: String
    // ── 更新日志页（版本卡片列表）──
    val changelogLatest: String
    val changelogLoadFailed: String
    val changelogViewRelease: String
    // ── 应用内更新（检查更新弹窗的下载区 + 关于页两行设置）──
    val updateAutoCheck: String
    val updateAutoCheckDesc: String
    val updateSourceAuto: String
    val updateSourceAutoDesc: String
    val updateSourceGitee: String
    val updateSourceGithub: String
    val updateDownload: String
    val updateDownloadComplete: String
    val updateInstallNow: String
    val updateInstallLater: String
    val updateRetryDownload: String
    val updatePause: String
    val updatePaused: String
    val updateResume: String
    val updateMirrorLabel: String
    val updateInstallPermissionTitle: String
    val updateInstallPermissionDesc: String
    val updateInstallPermissionGo: String
    fun updateRemaining(a0: Any?): String
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

    // ── 应用日志管理 ──
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
    fun logSectionAppScoped(a0: Any?, a1: Any?): String
    val logSectionSystem: String
    fun logSystemNeedTier(a0: Any?): String
    val logSystemSelf: String
    val logSystemEmpty: String
    fun logSystemFailed(a0: Any?): String

    // ── 应用日志管理：导出范围 / 分享 / 诊断摘要 / 上次运行 / 查看器筛选 ──
    val logScopeLabel: String
    val logScopeAll: String
    val logScopeWarn: String
    val logScopeRecent: String
    val logScopeTitle: String
    val logSectionExit: String
    val logCopyDiag: String
    val logCopyDiagSubtitle: String
    val logExportDoneTitle: String
    val logShare: String
    val logSearchHint: String
    val logFilterLevelLabel: String
    val logFilterTagLabel: String
    val logFilterAllTags: String
    val logFilterReset: String
    val logExitTitle: String
    val logExitUnsupported: String
    val logExitNone: String
    fun logExitReason(a0: Any?): String
    fun logExitTimeAndNote(a0: Any?, a1: Any?): String
    val logExitHasTrace: String
    val logExitCrash: String
    val logExitCrashNative: String
    val logExitAnr: String
    val logExitLowMemory: String
    val logExitSignaled: String
    val logExitSelf: String
    val logExitUser: String
    val logExitOther: String
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
    override val openSourceNote: String = "以 GPL-3.0 许可发布"
    override val copyrightOwner: String = "版权所有"
    override val copyrightText: String = "© 2026 Pient · GPL-3.0"
    override val licensesIntro: String = "Pient 依据 GNU GPL-3.0 发布。下列第三方组件随应用一同分发，各自遵循其许可；点任意一行可打开上游页面。"
    override val licensesSectionRuntime: String = "随应用分发"
    override val licensesSectionLibraries: String = "应用依赖"
    override val licensesSectionFonts: String = "字体"
    override val licensesSectionData: String = "数据来源"
    override val licensesFullText: String = "GPL-3.0 全文"
    override val licensesVarious: String = "各组件各自许可"
    override val licensesOpenData: String = "开放数据"
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
    override val checkingUpdate: String = "正在检查更新…"
    override val loadingChangelog: String = "正在获取更新日志…"
    override val newVersionFound: String = "发现新版本"
    override fun newVersionDetail(a0: Any?, a1: Any?): String = "最新版本 ${a0} · 当前 ${a1}"
    override val updateNotes: String = "更新内容"
    override val updateCheckFailed: String = "连不上更新服务器"
    override val updateCheckFailedTitle: String = "检查更新失败"
    override val upToDateDesc: String = "你正在使用最新版本的 Pient"
    override val changelogLatest: String = "最新"
    override val changelogLoadFailed: String = "加载更新日志失败"
    override val changelogViewRelease: String = "查看发布"
    override val updateAutoCheck: String = "自动检查更新"
    override val updateAutoCheckDesc: String = "应用启动时自动检查新版本"
    override val updateSourceAuto: String = "自动"
    override val updateSourceAutoDesc: String = "优先 Gitee，失败时切换 GitHub"
    override val updateSourceGitee: String = "仅 Gitee"
    override val updateSourceGithub: String = "仅 GitHub"
    override val updateDownload: String = "下载更新"
    override val updateDownloadComplete: String = "下载完成"
    override val updateInstallNow: String = "立即安装"
    override val updateInstallLater: String = "稍后安装"
    override val updateRetryDownload: String = "重新下载"
    override val updatePause: String = "暂停"
    override val updatePaused: String = "已暂停"
    override val updateResume: String = "继续"
    override val updateMirrorLabel: String = "镜像下载源"
    override val updateInstallPermissionTitle: String = "安装权限"
    override val updateInstallPermissionDesc: String = "需要允许安装未知来源应用才能安装更新"
    override val updateInstallPermissionGo: String = "前往设置"
    override fun updateRemaining(a0: Any?): String = "剩余约 ${a0}"
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

    // ── 应用日志管理 ──
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
    override val logViewSubtitle: String = "只读查看最近 2000 行，可按级别 / tag / 关键字筛选"
    override val logClear: String = "清空日志"
    override val logClearSubtitle: String = "删除已记录的全部运行日志"
    override val logClearTitle: String = "清空日志？"
    override val logClearBody: String = "将删除应用已记录的全部运行日志（不影响会话、项目与配置）。"
    override val logCleared: String = "日志已清空"
    override fun logExported(a0: Any?): String = "日志已导出：${a0}"
    override val logExportEmpty: String = "还没有可导出的日志"
    override val logExportFailed: String = "导出失败，请重试"
    override val logNote: String = "日志记录应用运行过程（含 pi 报错原文与环境信息），不含 API 密钥、不记录会话正文（导出前还会对疑似密钥的字符串加掩码）；「上次运行」来自系统退出记录。会话内容请用「项目与会话记录 → 导出会话」。"
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
    override fun logSectionAppScoped(a0: Any?, a1: Any?): String = "应用日志（${a0} · ${a1} 行）"
    override val logSectionSystem: String = "系统日志（logcat）"
    override fun logSystemNeedTier(a0: Any?): String = "系统日志需要调试 / Root 档；当前档位 ${a0} 没有特权通道"
    override val logSystemSelf: String = "应用自身 logcat 尾部（无特权通道：只有本应用的日志行；系统其它进程的日志需要调试 / Root 档）"
    override val logSystemEmpty: String = "这次抓取没有匹配到日志行"
    override fun logSystemFailed(a0: Any?): String = "系统日志读取失败：${a0}"

    override val logScopeLabel: String = "范围："
    override val logScopeAll: String = "全部"
    override val logScopeWarn: String = "仅警告以上"
    override val logScopeRecent: String = "最近 30 分钟"
    override val logScopeTitle: String = "导出范围"
    override val logSectionExit: String = "上次退出（系统记录）"
    override val logCopyDiag: String = "复制诊断摘要"
    override val logCopyDiagSubtitle: String = "把版本与环境信息复制到剪贴板（不含日志正文）"
    override val logExportDoneTitle: String = "日志已导出"
    override val logShare: String = "分享"
    override val logSearchHint: String = "搜索日志…"
    override val logFilterLevelLabel: String = "级别"
    override val logFilterTagLabel: String = "tag"
    override val logFilterAllTags: String = "全部 tag"
    override val logFilterReset: String = "重置"
    override val logExitTitle: String = "上次运行"
    override val logExitUnsupported: String = "本机不支持（Android 11 起系统才记录这项）"
    override val logExitNone: String = "未检测到异常退出"
    override fun logExitReason(a0: Any?): String = "上次退出：${a0}"
    override fun logExitTimeAndNote(a0: Any?, a1: Any?): String = "${a0} · 系统记录${a1}"
    override val logExitHasTrace: String = " · 含 ANR 轨迹（随导出带走）"
    override val logExitCrash: String = "崩溃（未捕获异常）"
    override val logExitCrashNative: String = "原生崩溃"
    override val logExitAnr: String = "无响应（ANR）"
    override val logExitLowMemory: String = "被系统回收内存杀掉"
    override val logExitSignaled: String = "被系统信号终止"
    override val logExitSelf: String = "应用主动退出"
    override val logExitUser: String = "被用户强制停止"
    override val logExitOther: String = "其它原因"
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
    override val openSourceNote: String = "Released under GPL-3.0"
    override val copyrightOwner: String = "Copyright holder"
    override val copyrightText: String = "© 2026 Pient · GPL-3.0"
    override val licensesIntro: String = "Pient is released under GNU GPL-3.0. The following third-party components are distributed with the app, each under its own license; tap a row to open its upstream page."
    override val licensesSectionRuntime: String = "Bundled with the app"
    override val licensesSectionLibraries: String = "App dependencies"
    override val licensesSectionFonts: String = "Fonts"
    override val licensesSectionData: String = "Data sources"
    override val licensesFullText: String = "Full text of GPL-3.0"
    override val licensesVarious: String = "Various component licenses"
    override val licensesOpenData: String = "Open data"
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
    override val checkingUpdate: String = "Checking for updates…"
    override val loadingChangelog: String = "Loading changelog…"
    override val newVersionFound: String = "New version available"
    override fun newVersionDetail(a0: Any?, a1: Any?): String = "Latest ${a0} · Current ${a1}"
    override val updateNotes: String = "Release notes"
    override val updateCheckFailed: String = "Cannot reach the update server"
    override val updateCheckFailedTitle: String = "Check failed"
    override val upToDateDesc: String = "You're using the latest version of Pient"
    override val changelogLatest: String = "Latest"
    override val changelogLoadFailed: String = "Failed to load the changelog"
    override val changelogViewRelease: String = "View release"
    override val updateAutoCheck: String = "Check automatically"
    override val updateAutoCheckDesc: String = "Check for a new version on app start"
    override val updateSourceAuto: String = "Automatic"
    override val updateSourceAutoDesc: String = "Gitee first, fall back to GitHub"
    override val updateSourceGitee: String = "Gitee only"
    override val updateSourceGithub: String = "GitHub only"
    override val updateDownload: String = "Download update"
    override val updateDownloadComplete: String = "Download complete"
    override val updateInstallNow: String = "Install now"
    override val updateInstallLater: String = "Install later"
    override val updateRetryDownload: String = "Download again"
    override val updatePause: String = "Pause"
    override val updatePaused: String = "Paused"
    override val updateResume: String = "Resume"
    override val updateMirrorLabel: String = "Mirror sources"
    override val updateInstallPermissionTitle: String = "Install permission"
    override val updateInstallPermissionDesc: String = "Allow installing apps from unknown sources to install updates"
    override val updateInstallPermissionGo: String = "Open settings"
    override fun updateRemaining(a0: Any?): String = "About ${a0} left"
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

    // ── 应用日志管理 ──
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
    override val logViewSubtitle: String = "Read-only, last 2000 lines, filterable by level / tag / keyword"
    override val logClear: String = "Clear log"
    override val logClearSubtitle: String = "Delete all recorded runtime log"
    override val logClearTitle: String = "Clear log?"
    override val logClearBody: String = "This deletes all recorded runtime log (sessions, projects and settings are not affected)."
    override val logCleared: String = "Log cleared"
    override fun logExported(a0: Any?): String = "Log exported: ${a0}"
    override val logExportEmpty: String = "Nothing to export yet"
    override val logExportFailed: String = "Export failed, please retry"
    override val logNote: String = "The log records the app's runtime (including pi's raw errors and environment info). It contains no API keys and no session text (suspected key-like strings are masked before export); “Last run” comes from the system's exit record. Use “Project & session records → Export sessions” for session content."
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
    override fun logSectionAppScoped(a0: Any?, a1: Any?): String = "App log (${a0} · ${a1} lines)"
    override val logSectionSystem: String = "System log (logcat)"
    override fun logSystemNeedTier(a0: Any?): String = "System log needs the Debugger / Root tier; tier ${a0} has no privileged channel"
    override val logSystemSelf: String = "The app's own logcat tail (no privileged channel: this app's lines only; other processes' logs need the Debugger / Root tier)"
    override val logSystemEmpty: String = "No matching log lines were captured this time"
    override fun logSystemFailed(a0: Any?): String = "Failed to read the system log: ${a0}"

    override val logScopeLabel: String = "Scope: "
    override val logScopeAll: String = "Everything"
    override val logScopeWarn: String = "Warnings and errors only"
    override val logScopeRecent: String = "Last 30 minutes"
    override val logScopeTitle: String = "Export scope"
    override val logSectionExit: String = "Last exit (system record)"
    override val logCopyDiag: String = "Copy diagnostic summary"
    override val logCopyDiagSubtitle: String = "Copy version and environment info to the clipboard (no log body)"
    override val logExportDoneTitle: String = "Log exported"
    override val logShare: String = "Share"
    override val logSearchHint: String = "Search log…"
    override val logFilterLevelLabel: String = "Level"
    override val logFilterTagLabel: String = "tag"
    override val logFilterAllTags: String = "All tags"
    override val logFilterReset: String = "Reset"
    override val logExitTitle: String = "Last run"
    override val logExitUnsupported: String = "Not available here (the system records this from Android 11 on)"
    override val logExitNone: String = "No abnormal exit detected"
    override fun logExitReason(a0: Any?): String = "Last exit: ${a0}"
    override fun logExitTimeAndNote(a0: Any?, a1: Any?): String = "${a0} · system record${a1}"
    override val logExitHasTrace: String = " · includes the ANR trace (travels with the export)"
    override val logExitCrash: String = "Crash (uncaught exception)"
    override val logExitCrashNative: String = "Native crash"
    override val logExitAnr: String = "Not responding (ANR)"
    override val logExitLowMemory: String = "Killed by the system to reclaim memory"
    override val logExitSignaled: String = "Terminated by a system signal"
    override val logExitSelf: String = "App exited on its own"
    override val logExitUser: String = "Force-stopped by the user"
    override val logExitOther: String = "Other reason"
}
