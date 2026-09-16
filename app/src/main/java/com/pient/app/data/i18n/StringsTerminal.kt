package com.pient.app.data.i18n

/** terminal 组文案（由 i18n 流水线生成；zh = 源码原文字面量） */
interface TerminalStrings {
    fun mustMissing(a0: Any?, a1: Any?): String
    val closeSession: String
    val newSession: String
    val selected: String
    val keyInterrupt: String
    val keyClear: String
    val extraKeys: String
    fun deleteSessionConfirm(a0: Any?): String
    val noSessions: String
    val emptyHint: String
    val envInstallTitle: String
    fun envInstallConfirm(a0: Any?): String
    val skip: String
    val envInstallNote: String
    val needsRoot: String
    val notReady: String
    val checkFailedNpm: String
    val checkFailedPiVersion: String
    val pickComponentsFirst: String
    val recheck: String
    fun unpacking(a0: Any?): String
    fun notReadyReason(a0: Any?): String
    val notReadyHint: String
    val groupRuntimeTitle: String
    val groupRuntimeNote: String
    val bundled: String
    val piNotReadyUnpack: String
    val piNotUnpacked: String
    val piReadyHint: String
    val checkingNpm: String
    fun piUpToDate(a0: Any?): String
    fun piUpdateAvailable(a0: Any?, a1: Any?): String
    val checking: String
    fun updateTo(a0: Any?): String
    val checkUpdate: String
    val execEnvTitle: String
    val execEnvNote: String
    fun envSwitched(a0: Any?): String
    val aptMirror: String
    fun aptMirrorNote(a0: Any?): String
    val mirrorNotReady: String
    fun mirrorApplied(a0: Any?): String
    val mirrorFailed: String
    val packagesTitle: String
    val packagesNote: String
    fun installingStep(a0: Any?): String
    fun installedSummary(a0: Any?, a1: Any?, a2: Any?): String
    val installVisibleInTerminal: String
    val selectRequired: String
    val viewInTerminal: String
    fun installSelectedCount(a0: Any?): String
    val pientRequired: String
    fun installedOf(a0: Any?, a1: Any?): String
    val allSet: String
    val requiredBadge: String
    val heavyBadge: String
}

object ZhTerminal : TerminalStrings {
    override fun mustMissing(a0: Any?, a1: Any?): String = "Pient 必须项还差 ${a0} 个：${a1} —— 点右侧「勾选必须项」再安装"
    override val closeSession: String = "关闭终端会话"
    override val newSession: String = "新建终端会话"
    override val selected: String = "已选"
    override val keyInterrupt: String = "中断"
    override val keyClear: String = "清屏"
    override val extraKeys: String = "额外按键栏"
    override fun deleteSessionConfirm(a0: Any?): String = "确定要删除会话「${a0}」吗？会话中的数据将丢失。"
    override val noSessions: String = "没有终端会话"
    override val emptyHint: String = "点右上角「+」新建一个终端会话\n会话运行时通知栏会有一条前台通知（保证进程不被系统清掉）"
    override val envInstallTitle: String = "环境安装"
    override fun envInstallConfirm(a0: Any?): String = "安装所选（${a0}）"
    override val skip: String = "跳过"
    override val envInstallNote: String = "这些工具链装进内置的 Ubuntu 环境；安装过程会在终端页实时显示。"
    override val needsRoot: String = "需 Root"
    override val notReady: String = "未就绪"
    override val checkFailedNpm: String = "检测失败：连不上 npm（网络？）"
    override val checkFailedPiVersion: String = "检测失败：读不到本机 pi 版本"
    override val pickComponentsFirst: String = "先勾选要安装的组件"
    override val recheck: String = "重新检测"
    override fun unpacking(a0: Any?): String = "Ubuntu 正在后台解包：${a0}"
    override fun notReadyReason(a0: Any?): String = "Ubuntu 未就绪：${a0}"
    override val notReadyHint: String = "Ubuntu 未就绪：可点右上角「重新检测」，或重启应用触发解包。"
    override val groupRuntimeTitle: String = "Pient 运行时"
    override val groupRuntimeNote: String = "pi 随 Pient 预置，不需要安装；这一块只用来更新它"
    override val bundled: String = "随包预置"
    override val piNotReadyUnpack: String = "Ubuntu 未就绪（解包后自动铺开）"
    override val piNotUnpacked: String = "未解包（重开应用会自动解包）"
    override val piReadyHint: String = "已就绪 · /usr/bin/pi · 点右侧可检测官方最新版"
    override val checkingNpm: String = "正在查 npm 官方最新版…"
    override fun piUpToDate(a0: Any?): String = "已是最新（v${a0}）"
    override fun piUpdateAvailable(a0: Any?, a1: Any?): String = "官方最新 v${a0} · 当前 v${a1} —— 可更新"
    override val checking: String = "检测中…"
    override fun updateTo(a0: Any?): String = "更新到 v${a0}"
    override val checkUpdate: String = "检测更新"
    override val execEnvTitle: String = "执行环境"
    override val execEnvNote: String = "终端里的命令跑在哪个环境（与「权限档位」是两条轴）"
    override fun envSwitched(a0: Any?): String = "已切到 ${a0}"
    override val aptMirror: String = "apt 镜像源"
    override fun aptMirrorNote(a0: Any?): String = "写进 Ubuntu 的 /etc/apt/sources.list.d/ubuntu.sources；下面列出的是本机（${a0}）实际会写入的地址"
    override val mirrorNotReady: String = "Ubuntu 未就绪，暂不能写镜像源"
    override fun mirrorApplied(a0: Any?): String = "镜像源已应用：${a0}"
    override val mirrorFailed: String = "镜像源写入失败"
    override val packagesTitle: String = "环境内软件"
    override val packagesNote: String = "按运行时分类；带「（Pient 必须）」的两类排在最前，已装好的会标出来"
    override fun installingStep(a0: Any?): String = "正在安装：${a0}"
    override fun installedSummary(a0: Any?, a1: Any?, a2: Any?): String = "已装 ${a0}/${a1} · 已选 ${a2} 项"
    override val installVisibleInTerminal: String = "安装过程在终端页可见"
    override val selectRequired: String = "勾选必须项"
    override val viewInTerminal: String = "去终端查看"
    override fun installSelectedCount(a0: Any?): String = "安装所选（${a0}）"
    override val pientRequired: String = "（Pient 必须）"
    override fun installedOf(a0: Any?, a1: Any?): String = "${a0}/${a1} 已装"
    override val allSet: String = "已齐"
    override val requiredBadge: String = "必须"
    override val heavyBadge: String = "大"
}

object EnTerminal : TerminalStrings {
    override fun mustMissing(a0: Any?, a1: Any?): String = "Pient is missing ${a0} required item(s): ${a1} — tap “Select required” on the right, then install"
    override val closeSession: String = "Close terminal session"
    override val newSession: String = "New terminal session"
    override val selected: String = "Selected"
    override val keyInterrupt: String = "Interrupt"
    override val keyClear: String = "Clear"
    override val extraKeys: String = "Extra keys"
    override fun deleteSessionConfirm(a0: Any?): String = "Delete session “${a0}”? All data in this session will be lost."
    override val noSessions: String = "No terminal sessions"
    override val emptyHint: String = "Tap \"+\" in the top-right to create a terminal session\nA foreground notification appears while a session runs (so the system will not kill the process)"
    override val envInstallTitle: String = "Environment setup"
    override fun envInstallConfirm(a0: Any?): String = "Install selected (${a0})"
    override val skip: String = "Skip"
    override val envInstallNote: String = "These toolchains are installed into the built-in Ubuntu environment; progress shows up live on the Terminal page."
    override val needsRoot: String = "Root required"
    override val notReady: String = "Not ready"
    override val checkFailedNpm: String = "Check failed: cannot reach npm (network?)"
    override val checkFailedPiVersion: String = "Check failed: cannot read the local pi version"
    override val pickComponentsFirst: String = "Select components to install first"
    override val recheck: String = "Re-check"
    override fun unpacking(a0: Any?): String = "Ubuntu is unpacking in the background: ${a0}"
    override fun notReadyReason(a0: Any?): String = "Ubuntu not ready: ${a0}"
    override val notReadyHint: String = "Ubuntu not ready: tap \"Re-check\" in the top-right, or restart the app to trigger unpacking."
    override val groupRuntimeTitle: String = "Pient runtime"
    override val groupRuntimeNote: String = "pi ships with Pient and needs no installation; this section only updates it"
    override val bundled: String = "Bundled"
    override val piNotReadyUnpack: String = "Ubuntu not ready (auto-deploys after unpacking)"
    override val piNotUnpacked: String = "Not unpacked (restarting the app unpacks it automatically)"
    override val piReadyHint: String = "Ready · /usr/bin/pi · tap the right side to check for the latest official version"
    override val checkingNpm: String = "Checking npm for the latest official version…"
    override fun piUpToDate(a0: Any?): String = "Up to date (v${a0})"
    override fun piUpdateAvailable(a0: Any?, a1: Any?): String = "Latest v${a0} · current v${a1} — update available"
    override val checking: String = "Checking…"
    override fun updateTo(a0: Any?): String = "Update to v${a0}"
    override val checkUpdate: String = "Check for updates"
    override val execEnvTitle: String = "Execution environment"
    override val execEnvNote: String = "Which environment terminal commands run in (a separate axis from the permission tier)"
    override fun envSwitched(a0: Any?): String = "Switched to ${a0}"
    override val aptMirror: String = "APT mirror"
    override fun aptMirrorNote(a0: Any?): String = "Written into Ubuntu's /etc/apt/sources.list.d/ubuntu.sources; the addresses listed below are what this device (${a0}) actually writes"
    override val mirrorNotReady: String = "Ubuntu is not ready, cannot write the mirror yet"
    override fun mirrorApplied(a0: Any?): String = "Mirror applied: ${a0}"
    override val mirrorFailed: String = "Failed to write the mirror"
    override val packagesTitle: String = "Environment software"
    override val packagesNote: String = "Grouped by runtime; the two groups marked \"(Pient required)\" come first, and installed ones are flagged"
    override fun installingStep(a0: Any?): String = "Installing: ${a0}"
    override fun installedSummary(a0: Any?, a1: Any?, a2: Any?): String = "Installed ${a0}/${a1} · ${a2} selected"
    override val installVisibleInTerminal: String = "Install progress is visible on the Terminal page"
    override val selectRequired: String = "Select required"
    override val viewInTerminal: String = "View in Terminal"
    override fun installSelectedCount(a0: Any?): String = "Install selected (${a0})"
    override val pientRequired: String = "(Pient required)"
    override fun installedOf(a0: Any?, a1: Any?): String = "${a0}/${a1} installed"
    override val allSet: String = "All set"
    override val requiredBadge: String = "Required"
    override val heavyBadge: String = "Large"
}
