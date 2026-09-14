package com.pient.app.tools.terminal

/**
 * **环境内软件（终端层的数据面）** —— 装进 Ubuntu 里的东西：apt 镜像源与常用组件清单。
 *
 * 纯数据 + 两条命令生成（见 `runtime/EnvProvision.kt`）：这一层不执行安装，只声明「装什么、
 * 怎么检测、用什么命令装」；安装动作走终端层的会话（用户可见）。
 *
 * 从 `data/ExecEnvs.kt` 拆出（2026-09-14）：那份文件同时装着「执行环境落点」「系统命令档位」
 * 「环境内软件」三件事，边界糊在一起。
 */

// ─────────────────────────────────────────────────────────────
// 环境内软件（Ubuntu）：apt 镜像源 + 常用组件
// ─────────────────────────────────────────────────────────────

/**
 * apt 镜像源（改写 rootfs 里的 `/etc/apt/sources.list.d/ubuntu.sources`，deb822 格式）。
 *
 * **一律用 http**：ubuntu-base rootfs 里没有 `ca-certificates`（实测：切到 https 镜像后
 * `Certificate verification failed: The certificate is NOT trusted` + `Unable to locate package`，
 * 索引一条都拉不下来）。想用 https 源，先在「环境内软件」里勾装 CA 证书。
 */
data class AptMirror(val name: String, val uri: String)

val APT_MIRRORS = listOf(
    AptMirror("Ubuntu 官方", "http://archive.ubuntu.com/ubuntu/"),
    AptMirror("清华 TUNA", "http://mirrors.tuna.tsinghua.edu.cn/ubuntu/"),
    AptMirror("阿里云", "http://mirrors.aliyun.com/ubuntu/"),
    AptMirror("中科大 USTC", "http://mirrors.ustc.edu.cn/ubuntu/"),
    AptMirror("网易 163", "http://mirrors.163.com/ubuntu/"),
)

/**
 * 环境内软件的分类（Pient 口径：**按「装它来干什么」分**，不按语言/仓库分区）。
 * 顺序即页面分节的顺序（不依赖 groupBy 的返回顺序）。
 */
object ComponentGroups {
    const val BASE = "命令行基础"
    const val RUNTIME = "语言运行时"
    const val TOOLING = "开发与构建"
    const val NETWORK = "网络与远程"

    val ORDER = listOf(BASE, RUNTIME, TOOLING, NETWORK)

    val DESCS = mapOf(
        BASE to "下载、解压、版本控制 —— 装完立刻能用的最小集",
        RUNTIME to "AI 写代码要跑的那些运行时与包管理",
        TOOLING to "编译、搜索、编辑、长任务挂后台",
        NETWORK to "SSH 与文件传输",
    )
}

/**
 * 可勾选安装的组件（装进 Ubuntu 环境里）。
 *
 * - [probe]：默认检测用的**命令名**（`command -v <probe>`）；
 * - [detectCmd]：自定义检测命令（装了但版本不对、或没有可执行文件时用，如 Node 要求 v24+、
 *   openssh-server 只有守护进程没有命令）；
 * - [installCmd]：自定义安装命令（不走 apt 单包，如 NodeSource / npm 全局包 / rustup）；
 * - [group]：见 [ComponentGroups]；
 * - [heavy]：体积/耗时明显更大的项（页面上给一枚「大」标记，让用户知道要等）。
 *
 * **清单顺序即安装顺序**（自定义命令按此逐条执行）：node 必须在 pnpm/typescript 之前、
 * pip 必须在 uv 之前 —— 别随手重排。
 */
data class UbuntuComponent(
    val id: String,
    val name: String,
    val pkg: String,
    val probe: String,
    val desc: String,
    val group: String = ComponentGroups.BASE,
    val detectCmd: String? = null,
    val installCmd: String? = null,
    val heavy: Boolean = false,
)

val UBUNTU_COMPONENTS = listOf(
    // ── 命令行基础 ──
    UbuntuComponent("ca", "CA 证书", "ca-certificates", "update-ca-certificates", "https 源 / 下载校验的基础（minbase 未带）", ComponentGroups.BASE),
    UbuntuComponent("curl", "curl", "curl", "curl", "HTTP 请求与下载", ComponentGroups.BASE),
    UbuntuComponent("wget", "wget", "wget", "wget", "下载工具（脚本常用）", ComponentGroups.BASE),
    UbuntuComponent("git", "Git", "git", "git", "版本控制，AI 拉取/提交代码用", ComponentGroups.BASE),
    UbuntuComponent("unzip", "unzip", "unzip", "unzip", "解压 zip 归档", ComponentGroups.BASE),
    UbuntuComponent("jq", "jq", "jq", "jq", "命令行 JSON 处理（脚本里读接口结果）", ComponentGroups.BASE),
    UbuntuComponent("tree", "tree", "tree", "tree", "目录树（一眼看项目结构）", ComponentGroups.BASE),

    // ── 语言运行时 ──
    UbuntuComponent("python3", "Python 3", "python3", "python3", "脚本运行时（AI 跑 .py 用）", ComponentGroups.RUNTIME),
    UbuntuComponent("pip3", "pip", "python3-pip", "pip3", "Python 包管理", ComponentGroups.RUNTIME),
    UbuntuComponent("python-is-python3", "python 别名", "python-is-python3", "python", "让 `python` 指向 python3（脚本兼容）", ComponentGroups.RUNTIME),
    UbuntuComponent("python3-venv", "venv 虚拟环境", "python3-venv", "python3", "`python3 -m venv` 可用", ComponentGroups.RUNTIME, detectCmd = "dpkg -s python3-venv"),
    UbuntuComponent(
        "uv", "uv（Python 包管理器）", "python3-pip", "uv",
        "pip 的快速替代：`uv pip install` / `uv venv`",
        ComponentGroups.RUNTIME,
        installCmd = "apt-get install -y --no-install-recommends python3-pip && python3 -m pip install --break-system-packages -q uv",
    ),

    // ── 语言运行时：Node.js（走 NodeSource 装 Node 24，发行版 apt 里只有 18） ──
    UbuntuComponent(
        "nodejs", "Node.js 24（NodeSource）", "nodejs", "node",
        "JS 运行时；NodeSource 官方源装 Node 24（apt 里的 nodejs 只有 18）",
        ComponentGroups.RUNTIME,
        detectCmd = "node -v 2>/dev/null | grep -q '^v2[4-9]\\.'",
        installCmd = "apt-get install -y --no-install-recommends ca-certificates curl gnupg && " +
            "curl -fsSL https://deb.nodesource.com/setup_24.x | bash - && " +
            "apt-get install -y nodejs",
    ),
    UbuntuComponent(
        "pnpm", "pnpm", "pnpm", "pnpm", "Node 包管理器（快、省盘）", ComponentGroups.RUNTIME,
        detectCmd = "command -v pnpm",
        installCmd = "npm install -g pnpm",
    ),
    UbuntuComponent(
        "typescript", "TypeScript", "typescript", "tsc", "tsc 编译器（AI 写 TS 时用）", ComponentGroups.RUNTIME,
        detectCmd = "command -v tsc",
        installCmd = "npm install -g typescript",
    ),

    // ── 开发与构建 ──
    UbuntuComponent("build", "编译工具链", "build-essential", "gcc", "gcc / make 等（编译原生代码）", ComponentGroups.TOOLING, heavy = true),
    UbuntuComponent("cmake", "CMake", "cmake", "cmake", "构建系统（C/C++ 项目）", ComponentGroups.TOOLING),
    UbuntuComponent("ripgrep", "ripgrep", "ripgrep", "rg", "快速全文搜索（bash 里替代 grep -r）", ComponentGroups.TOOLING),
    UbuntuComponent("fd", "fd", "fd-find", "fdfind", "快速找文件（Ubuntu 里命令名是 fdfind）", ComponentGroups.TOOLING),
    UbuntuComponent("vim", "vim", "vim", "vim", "终端编辑器", ComponentGroups.TOOLING),
    UbuntuComponent("tmux", "tmux", "tmux", "tmux", "终端复用（长任务挂后台）", ComponentGroups.TOOLING),
    UbuntuComponent("htop", "htop", "htop", "htop", "进程与资源查看", ComponentGroups.TOOLING),

    // ── 语言运行时：JVM ──
    UbuntuComponent("java", "OpenJDK 17", "openjdk-17-jdk", "java", "Java 运行时 / 编译（jdk）", ComponentGroups.RUNTIME, heavy = true),
    // 注：apt 的 gradle 是 4.4（2017 年）对现代工程基本不可用 → 不进清单；要 Gradle 用工程自带 wrapper。

    // ── 语言运行时：Go / Rust ──
    UbuntuComponent("go", "Go", "golang-go", "go", "Go 工具链", ComponentGroups.RUNTIME, heavy = true),
    UbuntuComponent(
        "rust", "Rust（rustup）", "rustc", "rustc", "rustc / cargo（官方 rustup 安装，体积较大）", ComponentGroups.RUNTIME,
        detectCmd = "command -v rustc",
        installCmd = "curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --profile minimal",
        heavy = true,
    ),

    // ── 网络与远程 ──
    UbuntuComponent("ssh", "openssh-client", "openssh-client", "ssh", "SSH 客户端（git over ssh）", ComponentGroups.NETWORK),
    UbuntuComponent("sshpass", "sshpass", "sshpass", "sshpass", "非交互 SSH（密码登录脚本）", ComponentGroups.NETWORK),
    UbuntuComponent("rsync", "rsync", "rsync", "rsync", "增量同步（本地 / 远端）", ComponentGroups.NETWORK),
    UbuntuComponent(
        "openssh-server", "OpenSSH 服务器", "openssh-server", "sshd", "反向上隧道挂载本地文件系统用", ComponentGroups.NETWORK,
        detectCmd = "dpkg -s openssh-server",
    ),
)

