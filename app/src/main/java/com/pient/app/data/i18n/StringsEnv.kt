package com.pient.app.data.i18n

/** env 组文案（由 i18n 流水线生成；zh = 源码原文字面量） */
interface EnvStrings {
    val mirrorTsinghuaTuna: String
    val prootDesc: String
    val recommended: String
    val chrootDesc: String
    val rootRequired: String
    val mirrorUbuntuOfficial: String
    val mirrorAliyun: String
    val mirrorUstc: String
    val mirrorNetEase: String
    val groupPiSearch: String
    val groupSsh: String
    val groupBase: String
    val groupNodeDesc: String
    val groupPiSearchDesc: String
    val groupPythonDesc: String
    val groupBaseDesc: String
    val groupGoDesc: String
    val groupJavaDesc: String
    val groupRustDesc: String
    val groupSshDesc: String
    val caName: String
    val caDesc: String
    val curlDesc: String
    val wgetDesc: String
    val gitDesc: String
    val unzipDesc: String
    val jqDesc: String
    val treeDesc: String
    val python3Desc: String
    val pip3Desc: String
    val pythonAliasName: String
    val pythonAliasDesc: String
    val venvName: String
    val venvDesc: String
    val uvName: String
    val uvDesc: String
    val nodejsDesc: String
    val pnpmDesc: String
    val tscDesc: String
    val buildName: String
    val buildDesc: String
    val cmakeDesc: String
    val ripgrepDesc: String
    val fdDesc: String
    val vimDesc: String
    val tmuxDesc: String
    val htopDesc: String
    val javaDesc: String
    val goDesc: String
    val rustDesc: String
    val sshDesc: String
    val sshpassDesc: String
    val rsyncDesc: String
    val opensshServerName: String
    val opensshServerDesc: String
    val piAgentDesc: String
}

object ZhEnv : EnvStrings {
    override val mirrorTsinghuaTuna: String = "清华 TUNA"
    override val prootDesc: String = "GNU 用户空间：bash + coreutils + apt，可 apt 装任意软件；无需 Root，命令经 PRoot 运行"
    override val recommended: String = "推荐"
    override val chrootDesc: String = "同一个 rootfs，但以 su + chroot 运行：真 uid 0、零模拟开销，可改系统、绑特权端口；需设备已 Root"
    override val rootRequired: String = "需 Root"
    override val mirrorUbuntuOfficial: String = "Ubuntu 官方"
    override val mirrorAliyun: String = "阿里云"
    override val mirrorUstc: String = "中科大 USTC"
    override val mirrorNetEase: String = "网易 163"
    override val groupPiSearch: String = "pi 搜索依赖"
    override val groupSsh: String = "SSH 与远程"
    override val groupBase: String = "基础与开发"
    override val groupNodeDesc: String = "Node 运行时与包管理（pi 本体已随 Pient 预置，这里只需要 Node）"
    override val groupPiSearchDesc: String = "pi 的 grep / find 工具靠它们执行；不装则 pi 首次调用时自己联网下载"
    override val groupPythonDesc: String = "只在你让 AI 写 .py / 跑脚本时才需要（Pient 自身与 pi 都不依赖 Python）"
    override val groupBaseDesc: String = "下载、解压、版本控制、编译，以及终端里的日常工具"
    override val groupGoDesc: String = "Go 工具链（按项目需要）"
    override val groupJavaDesc: String = "Java 运行时 / 编译（JDK，按项目需要）"
    override val groupRustDesc: String = "Rust 工具链（官方 rustup 安装，体积大）"
    override val groupSshDesc: String = "SSH 客户端、免密脚本与文件同步"
    override val caName: String = "CA 证书"
    override val caDesc: String = "https 源 / 下载校验的基础（minbase 未带）"
    override val curlDesc: String = "HTTP 请求与下载"
    override val wgetDesc: String = "下载工具（脚本常用）"
    override val gitDesc: String = "版本控制，AI 拉取/提交代码用"
    override val unzipDesc: String = "解压 zip 归档"
    override val jqDesc: String = "命令行 JSON 处理（脚本里读接口结果）"
    override val treeDesc: String = "目录树（一眼看项目结构）"
    override val python3Desc: String = "脚本运行时（AI 跑 .py 用）"
    override val pip3Desc: String = "Python 包管理"
    override val pythonAliasName: String = "python 别名"
    override val pythonAliasDesc: String = "让 `python` 指向 python3（脚本兼容）"
    override val venvName: String = "venv 虚拟环境"
    override val venvDesc: String = "`python3 -m venv` 可用"
    override val uvName: String = "uv（Python 包管理器）"
    override val uvDesc: String = "pip 的快速替代：`uv pip install` / `uv venv`"
    override val nodejsDesc: String = "JS 运行时；NodeSource 官方源装 Node 24（apt 里的 nodejs 只有 18）"
    override val pnpmDesc: String = "Node 包管理器（快、省盘）"
    override val tscDesc: String = "tsc 编译器（AI 写 TS 时用）"
    override val buildName: String = "编译工具链"
    override val buildDesc: String = "gcc / make 等（编译原生代码）"
    override val cmakeDesc: String = "构建系统（C/C++ 项目）"
    override val ripgrepDesc: String = "快速全文搜索（bash 里替代 grep -r）"
    override val fdDesc: String = "快速找文件（Ubuntu 里命令名是 fdfind）"
    override val vimDesc: String = "终端编辑器"
    override val tmuxDesc: String = "终端复用（长任务挂后台）"
    override val htopDesc: String = "进程与资源查看"
    override val javaDesc: String = "Java 运行时 / 编译（jdk）"
    override val goDesc: String = "Go 工具链"
    override val rustDesc: String = "rustc / cargo（官方 rustup 安装，体积较大）"
    override val sshDesc: String = "SSH 客户端（git over ssh）"
    override val sshpassDesc: String = "非交互 SSH（密码登录脚本）"
    override val rsyncDesc: String = "增量同步（本地 / 远端）"
    override val opensshServerName: String = "OpenSSH 服务器"
    override val opensshServerDesc: String = "反向上隧道挂载本地文件系统用"
    override val piAgentDesc: String = "随 Pient 预置；这一项只把 pi 更新到 npm 官方最新"
}

object EnEnv : EnvStrings {
    override val mirrorTsinghuaTuna: String = "Tsinghua TUNA"
    override val prootDesc: String = "GNU userland: bash + coreutils + apt, so you can install anything with apt; no Root needed, commands run through PRoot"
    override val recommended: String = "Recommended"
    override val chrootDesc: String = "Same rootfs, but run as su + chroot: real uid 0, zero emulation overhead, can modify the system and bind privileged ports; requires a rooted device"
    override val rootRequired: String = "Root required"
    override val mirrorUbuntuOfficial: String = "Ubuntu official"
    override val mirrorAliyun: String = "Alibaba Cloud"
    override val mirrorUstc: String = "USTC"
    override val mirrorNetEase: String = "NetEase 163"
    override val groupPiSearch: String = "pi search dependencies"
    override val groupSsh: String = "SSH & remote"
    override val groupBase: String = "Basics & development"
    override val groupNodeDesc: String = "Node runtime and package manager (pi itself ships with Pient, so only Node is needed here)"
    override val groupPiSearchDesc: String = "pi's grep / find tools run on them; if not installed, pi downloads them itself on first use"
    override val groupPythonDesc: String = "Only needed when you have the AI write .py / run scripts (neither Pient itself nor pi depends on Python)"
    override val groupBaseDesc: String = "Downloads, extraction, version control, compilation, and everyday tools in the Terminal"
    override val groupGoDesc: String = "Go toolchain (as needed per project)"
    override val groupJavaDesc: String = "Java runtime / compilation (JDK, as needed per project)"
    override val groupRustDesc: String = "Rust toolchain (installed via the official rustup, large download)"
    override val groupSshDesc: String = "SSH client, passwordless scripts, and file sync"
    override val caName: String = "CA certificates"
    override val caDesc: String = "Basis for https sources / download verification (not in minbase)"
    override val curlDesc: String = "HTTP requests and downloads"
    override val wgetDesc: String = "Download tool (common in scripts)"
    override val gitDesc: String = "Version control, for the AI to pull/commit code"
    override val unzipDesc: String = "Extract zip archives"
    override val jqDesc: String = "Command-line JSON processing (reading API results in scripts)"
    override val treeDesc: String = "Directory tree (see the project structure at a glance)"
    override val python3Desc: String = "Script runtime (for the AI to run .py)"
    override val pip3Desc: String = "Python package management"
    override val pythonAliasName: String = "python alias"
    override val pythonAliasDesc: String = "Point `python` at python3 (script compatibility)"
    override val venvName: String = "venv virtual environment"
    override val venvDesc: String = "`python3 -m venv` available"
    override val uvName: String = "uv (Python package manager)"
    override val uvDesc: String = "Fast pip replacement: `uv pip install` / `uv venv`"
    override val nodejsDesc: String = "JS runtime; Node 24 comes from the official NodeSource repo (the nodejs in apt is only 18)"
    override val pnpmDesc: String = "Node package manager (fast, disk-efficient)"
    override val tscDesc: String = "tsc compiler (for when the AI writes TS)"
    override val buildName: String = "Build toolchain"
    override val buildDesc: String = "gcc / make etc. (compiling native code)"
    override val cmakeDesc: String = "Build system (C/C++ projects)"
    override val ripgrepDesc: String = "Fast full-text search (replaces grep -r in bash)"
    override val fdDesc: String = "Fast file finding (the command is fdfind on Ubuntu)"
    override val vimDesc: String = "Terminal editor"
    override val tmuxDesc: String = "Terminal multiplexing (keep long tasks in the background)"
    override val htopDesc: String = "Process and resource monitoring"
    override val javaDesc: String = "Java runtime / compilation (jdk)"
    override val goDesc: String = "Go toolchain"
    override val rustDesc: String = "rustc / cargo (installed via the official rustup, fairly large)"
    override val sshDesc: String = "SSH client (git over ssh)"
    override val sshpassDesc: String = "Non-interactive SSH (password login scripts)"
    override val rsyncDesc: String = "Incremental sync (local / remote)"
    override val opensshServerName: String = "OpenSSH server"
    override val opensshServerDesc: String = "For mounting the local file system over a reverse tunnel"
    override val piAgentDesc: String = "Ships with Pient; this item only updates pi to the latest official npm release"
}
