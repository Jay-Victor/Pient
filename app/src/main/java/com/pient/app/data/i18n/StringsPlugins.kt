package com.pient.app.data.i18n

/** plugins 组文案（由 i18n 流水线生成；zh = 源码原文字面量） */
interface PluginsStrings {
    val disabled: String
    val description: String
    val viewReadme: String
    val expandCollapse: String
    val status: String
    val version: String
    val resources: String
    val source: String
    val resolvedResources: String
    val packageDisabled: String
    val noResolvedResources: String
    val upToDate: String
    val loaded: String
    val missing: String
    fun installedVersion(a0: Any?): String
    fun configuredVersion(a0: Any?): String
    fun resourceExtensions(a0: Any?): String
    fun resourceSkills(a0: Any?): String
    fun resourcePrompts(a0: Any?): String
    fun resourceThemes(a0: Any?): String
    val noResources: String
    val extension: String
    val prompt: String
    val theme: String
    val title: String
    val projectSettingsFile: String
    fun loadFailed(a0: Any?): String
    val loadingPackages: String
    val emptyGlobal: String
    val emptyProject: String
    fun installProgress(a0: Any?, a1: Any?): String
    val installPlugin: String
    fun installStarted(a0: Any?, a1: Any?): String
    fun removeStarted(a0: Any?): String
    fun updateStarted(a0: Any?): String
    fun sourceLabel(a0: Any?): String
    val addPlugin: String
    val installSupport: String
    val marketplaceNote: String
    val installGlobalNote: String
    val installProjectNote: String
    fun installVisibleInTerminal(a0: Any?): String
    val installing: String
}

object ZhPlugins : PluginsStrings {
    override val disabled: String = "已禁用"
    override val description: String = "描述"
    override val viewReadme: String = "查看README.md"
    override val expandCollapse: String = "展开/收起"
    override val status: String = "状态"
    override val version: String = "版本"
    override val resources: String = "资源"
    override val source: String = "来源"
    override val resolvedResources: String = "已解析资源"
    override val packageDisabled: String = "包已禁用。"
    override val noResolvedResources: String = "没有已解析资源"
    override val upToDate: String = "已是最新版本"
    override val loaded: String = "已加载"
    override val missing: String = "缺失"
    override fun installedVersion(a0: Any?): String = "已安装 ${a0}"
    override fun configuredVersion(a0: Any?): String = "已配置 ${a0}"
    override fun resourceExtensions(a0: Any?): String = "${a0}扩展"
    override fun resourceSkills(a0: Any?): String = "${a0}技能"
    override fun resourcePrompts(a0: Any?): String = "${a0}提示词"
    override fun resourceThemes(a0: Any?): String = "${a0}主题"
    override val noResources: String = "没有资源"
    override val extension: String = "扩展"
    override val prompt: String = "提示词"
    override val theme: String = "主题"
    override val title: String = "插件管理"
    override val projectSettingsFile: String = "当前项目 .pi/settings.json（pi install -l）"
    override fun loadFailed(a0: Any?): String = "读取失败：${a0}"
    override val loadingPackages: String = "正在读取 pi 的包列表…"
    override val emptyGlobal: String = "还没有配置任何插件。点右下 + 安装（npm: 包 / git: / 本地路径）。"
    override val emptyProject: String = "当前项目没有插件（pi install -l 装到项目里）。"
    override fun installProgress(a0: Any?, a1: Any?): String = "⏳ ${a0}（终端页「${a1}」会话可看全过程）"
    override val installPlugin: String = "安装插件"
    override fun installStarted(a0: Any?, a1: Any?): String = "已开始安装 ${a0} —— 终端页「${a1}」可看进度"
    override fun removeStarted(a0: Any?): String = "已开始移除 ${a0}"
    override fun updateStarted(a0: Any?): String = "已开始更新 ${a0}"
    override fun sourceLabel(a0: Any?): String = "来源：${a0}"
    override val addPlugin: String = "添加插件"
    override val installSupport: String = "支持：npm: 包 / git: 仓库 / https 链接 / 本地路径（git 类需 Ubuntu 里已装 git）"
    override val marketplaceNote: String = "pi.dev/packages 为官方插件市场"
    override val installGlobalNote: String = "装到全局：写 ~/.pi/agent/settings.json"
    override val installProjectNote: String = "装到项目：写 .pi/settings.json（等价 pi install -l）"
    override fun installVisibleInTerminal(a0: Any?): String = "安装过程在终端页「${a0}」会话里可见"
    override val installing: String = "安装中…"
}

object EnPlugins : PluginsStrings {
    override val disabled: String = "Disabled"
    override val description: String = "Description"
    override val viewReadme: String = "View README.md"
    override val expandCollapse: String = "Expand/collapse"
    override val status: String = "Status"
    override val version: String = "Version"
    override val resources: String = "Resources"
    override val source: String = "Source"
    override val resolvedResources: String = "Resolved resources"
    override val packageDisabled: String = "Package disabled."
    override val noResolvedResources: String = "No resolved resources"
    override val upToDate: String = "Up to date"
    override val loaded: String = "Loaded"
    override val missing: String = "Missing"
    override fun installedVersion(a0: Any?): String = "Installed ${a0}"
    override fun configuredVersion(a0: Any?): String = "Configured ${a0}"
    override fun resourceExtensions(a0: Any?): String = "${a0} extensions"
    override fun resourceSkills(a0: Any?): String = "${a0} skills"
    override fun resourcePrompts(a0: Any?): String = "${a0} prompts"
    override fun resourceThemes(a0: Any?): String = "${a0} themes"
    override val noResources: String = "No resources"
    override val extension: String = "Extension"
    override val prompt: String = "Prompt"
    override val theme: String = "Theme"
    override val title: String = "Plugins"
    override val projectSettingsFile: String = "Current project: .pi/settings.json (pi install -l)"
    override fun loadFailed(a0: Any?): String = "Failed to load: ${a0}"
    override val loadingPackages: String = "Reading pi's package list…"
    override val emptyGlobal: String = "No plugins configured yet. Tap + at the bottom right to install (npm: package / git: / local path)."
    override val emptyProject: String = "No plugins in the current project (install into the project with pi install -l)."
    override fun installProgress(a0: Any?, a1: Any?): String = "⏳ ${a0} (see the whole process in the Terminal page \"${a1}\" session)"
    override val installPlugin: String = "Install plugin"
    override fun installStarted(a0: Any?, a1: Any?): String = "Installation of ${a0} started — see progress in the Terminal page \"${a1}\""
    override fun removeStarted(a0: Any?): String = "Removal of ${a0} started"
    override fun updateStarted(a0: Any?): String = "Update of ${a0} started"
    override fun sourceLabel(a0: Any?): String = "Source: ${a0}"
    override val addPlugin: String = "Add plugin"
    override val installSupport: String = "Supported: npm: package / git: repo / https link / local path (git-based sources need git installed in Ubuntu)"
    override val marketplaceNote: String = "pi.dev/packages is the official plugin marketplace"
    override val installGlobalNote: String = "Global install: writes to ~/.pi/agent/settings.json"
    override val installProjectNote: String = "Project install: writes to .pi/settings.json (equivalent to pi install -l)"
    override fun installVisibleInTerminal(a0: Any?): String = "The installation process is visible in the Terminal page \"${a0}\" session"
    override val installing: String = "Installing…"
}
