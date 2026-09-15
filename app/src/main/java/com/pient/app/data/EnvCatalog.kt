package com.pient.app.data

/**
 * **环境配置相关的静态数据**（2026-09-14 用户拍板：终端执行链路整体移除后，
 * 环境配置页已改为空白占位页 —— 原「执行环境二选一 / 镜像源 / 组件勾选」页面不再渲染本文件的清单）。
 *
 * 现存用途只剩两处：
 * - [ExecEnv] —— 设置项 `exec_env` 的界面语义（`SettingsStore` 仍持有该字段，仅作界面状态）；
 * - [AptMirror] / [APT_MIRRORS]、[ComponentGroups] / [UBUNTU_COMPONENTS] —— 首启「环境安装」弹窗
 *   （`EnvSetupDialog`）仍在用（UI 壳，不真正安装）。
 */

/**
 * 执行环境（**terminal 自己的落点**）—— 终端页会话与工具命令跑在哪一个环境里。
 *
 * **这里只有 proot Ubuntu 的两个形态**：同一个 rootfs（它有**自己的 root 用户、自己的文件系统、
 * 自己的包管理器**），区别只在「怎么进去」——PRoot（应用 uid，无需 Root）与 su + chroot（真 uid 0）。
 *
 * **Android shell 不是这里的一个取值**（2026-09-15 用户口径）：它是**另一条完全独立的通道** ——
 * Shizuku / Root 把命令**直接扔给 Android 系统**执行，不经过 terminal、不经过 Ubuntu，
 * 而且**即发即走、没有会话**（见 `runtime/AndroidShell.kt` 与 `runtime/ExecBridge.kt`）。
 * 两条轴别混：本枚举管「terminal 落在哪」，权限档位管「AI 能不能用 Android shell 通道」。
 */
enum class ExecEnv(
    val id: String,          // prefs 存储值 + exec_env 文件内容
    val title: String,
    val desc: String,
    val badge: String,       // 卡片右侧徽标（推荐 / 需 Root）
) {
    UBUNTU(
        id = "ubuntu",
        title = "Ubuntu 24.04（PRoot）",
        desc = "GNU 用户空间：bash + coreutils + apt，可 apt 装任意软件；无需 Root，命令经 PRoot 运行",
        badge = "推荐",
    ),
    UBUNTU_CHROOT(
        id = "ubuntu-chroot",
        title = "Ubuntu 24.04（chroot）",
        desc = "同一个 rootfs，但以 su + chroot 运行：真 uid 0、零模拟开销，可改系统、绑特权端口；需设备已 Root",
        badge = "需 Root",
    );

    companion object {
        fun fromId(id: String?): ExecEnv =
            entries.firstOrNull { it.id == id } ?: UBUNTU
    }
}

// ─────────────────────────────────────────────────────────────
// 环境内软件（Ubuntu）：apt 镜像源 + 常用组件（纯数据，首启弹窗展示用）
// ─────────────────────────────────────────────────────────────

/** apt 镜像源（展示名 + 源地址） */
data class AptMirror(val name: String, val uri: String)

/**
 * 默认 apt 镜像源（2026-09-14 实测结论）：**国内网络下 archive.ubuntu.com 拉包会
 * `E: Failed to fetch` → apt 退出码 100**，而清华 TUNA 同一步骤通过。
 * 所以新增/首启的默认值取 TUNA（用户可在页面里改；已选过的不受影响）。
 */
const val DEFAULT_APT_MIRROR = "清华 TUNA"

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
    // 口径照 Operit 的安装分类（**按运行时 / 语言分**，不按"用途"分）：
    // 一个运行时一类，类里的东西只出现在这一类里（不会再出现"Node 在一处、Node 生态在另一处"）。
    // 差异只有一处：**Pient 自己必须的两类排在最前，并在标题下写橙色「（Pient 必须）」**——
    // 这正是 Operit 标记「(Operit 必须)」的位置与写法。

    /** pi 是 Node 程序：不装 Node 就用不了 pi（pi 本体已随 Pient 预置） */
    const val NODE = "Node.js"

    /** pi 的 grep / find 两个工具的执行引擎（Rust 写的 rg / fd） */
    const val PI_SEARCH = "pi 搜索依赖"

    const val PYTHON = "Python"
    const val GO = "Go"
    const val JAVA = "Java"
    const val RUST = "Rust"
    const val SSH = "SSH 与远程"
    const val BASE = "基础与开发"

    /** 展示顺序（= 安装顺序：node 必须排在含 npm 安装命令的项之前） */
    val ORDER = listOf(NODE, PI_SEARCH, PYTHON, BASE, GO, JAVA, RUST, SSH)

    val DESCS = mapOf(
        NODE to "Node 运行时与包管理（pi 本体已随 Pient 预置，这里只需要 Node）",
        PI_SEARCH to "pi 的 grep / find 工具靠它们执行；不装则 pi 首次调用时自己联网下载",
        PYTHON to "只在你让 AI 写 .py / 跑脚本时才需要（Pient 自身与 pi 都不依赖 Python）",
        BASE to "下载、解压、版本控制、编译，以及终端里的日常工具",
        GO to "Go 工具链（按项目需要）",
        JAVA to "Java 运行时 / 编译（JDK，按项目需要）",
        RUST to "Rust 工具链（官方 rustup 安装，体积大）",
        SSH to "SSH 客户端、免密脚本与文件同步",
    )

    /** 分类级「（Pient 必须）」：照 Operit 在分类标题下写橙色小字 */
    val REQUIRED_GROUPS = setOf(NODE, PI_SEARCH)
}



/**
 * 一个「环境内软件」组件（照 Operit `PackageItem` 的字段口径）。
 *
 * - [id]：稳定标识（勾选集合、检测结果都以它为主键）；
 * - [name]：页面显示名；
 * - [pkg]：apt 包名（有 [installCmd] 的自定义安装项填什么都会被忽略）；
 * - [probe]：检测用的命令名（`command -v <probe>`）；
 * - [detectCmd]：自定义检测命令（装了但版本不对、或没有可执行文件的服务如 openssh-server 用 `dpkg -s`）；
 * - [installCmd]：自定义安装命令（不走 apt 单包，如 NodeSource / npm 全局包 / rustup）；
 * - [group]：页面分类（见 [ComponentGroups]）；
 * - [required]：**「Pient 必须」**（2026-09-14 重新设计加）：不装它 Pient 的核心能力就是残的
 *   （目前只有 nodejs 与 pi —— node 是 pi 的运行前提，pi 本体虽已预置但同样必备）；
 * - [heavy]：体积/耗时明显更大的项（页面上给一枚「大」标记）。
 */
data class UbuntuComponent(
    val id: String,
    val name: String,
    val pkg: String,
    val probe: String,
    val desc: String,
    val group: String = ComponentGroups.BASE,
    val required: Boolean = false,
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
    UbuntuComponent("python3", "Python 3", "python3", "python3", "脚本运行时（AI 跑 .py 用）", ComponentGroups.PYTHON),
    UbuntuComponent("pip3", "pip", "python3-pip", "pip3", "Python 包管理", ComponentGroups.PYTHON),
    UbuntuComponent("python-is-python3", "python 别名", "python-is-python3", "python", "让 `python` 指向 python3（脚本兼容）", ComponentGroups.PYTHON),
    UbuntuComponent("python3-venv", "venv 虚拟环境", "python3-venv", "python3", "`python3 -m venv` 可用", ComponentGroups.PYTHON, detectCmd = "dpkg -s python3-venv"),
    UbuntuComponent(
        "uv", "uv（Python 包管理器）", "python3-pip", "uv",
        "pip 的快速替代：`uv pip install` / `uv venv`",
        ComponentGroups.PYTHON,
        installCmd = "apt-get install -y --no-install-recommends python3-pip && python3 -m pip install --break-system-packages -q uv",
    ),

    // ── 语言运行时：Node.js（走 NodeSource 装 Node 24，发行版 apt 里只有 18） ──
    UbuntuComponent(
        "nodejs", "Node.js 24（NodeSource）", "nodejs", "node",
        "JS 运行时；NodeSource 官方源装 Node 24（apt 里的 nodejs 只有 18）",
        ComponentGroups.NODE,
        required = true,
        // 判定要**node + npm 都在**：实测踩过「node 在、npm 缺位」的残状态（更新 pi 时只报 command not found）——
        // 只判 node 会让页面显示「已安装」而禁选，用户就没有修复入口了
        detectCmd = "node -v 2>/dev/null | grep -q '^v2[4-9]\\.' && command -v npm >/dev/null 2>&1",
        installCmd = "apt-get install -y --no-install-recommends ca-certificates curl gnupg && " +
            "curl -fsSL https://deb.nodesource.com/setup_24.x | bash - && " +
            // node 在、npm 缺（文件被删过/装残了）时 apt 会说 already the newest version 而不修 —— 实测踩过，
            // 所以这种残状态必须显式 --reinstall（否则用户点了「安装」却什么都没发生）
            "if command -v node >/dev/null 2>&1 && ! command -v npm >/dev/null 2>&1; then " +
            "echo '[Node.js] 检测到 node 在、npm 缺失，重装 nodejs 修复'; apt-get install -y --reinstall nodejs; " +
            "else apt-get install -y nodejs; fi",
    ),
    UbuntuComponent(
        "pnpm", "pnpm", "pnpm", "pnpm", "Node 包管理器（快、省盘）", ComponentGroups.NODE,
        detectCmd = "command -v pnpm",
        installCmd = "npm install -g pnpm",
    ),
    UbuntuComponent(
        "typescript", "TypeScript", "typescript", "tsc", "tsc 编译器（AI 写 TS 时用）", ComponentGroups.NODE,
        detectCmd = "command -v tsc",
        installCmd = "npm install -g typescript",
    ),
    // pi 本体：**已随 Pient 预置**（解在 npm 全局位置，装完 node 就能用）；这一项是「更新到官方最新」

    // ── 开发与构建 ──
    UbuntuComponent("build", "编译工具链", "build-essential", "gcc", "gcc / make 等（编译原生代码）", ComponentGroups.BASE, heavy = true),
    UbuntuComponent("cmake", "CMake", "cmake", "cmake", "构建系统（C/C++ 项目）", ComponentGroups.BASE),
    UbuntuComponent("ripgrep", "ripgrep", "ripgrep", "rg", "快速全文搜索（bash 里替代 grep -r）", ComponentGroups.PI_SEARCH, required = true),
    UbuntuComponent("fd", "fd", "fd-find", "fdfind", "快速找文件（Ubuntu 里命令名是 fdfind）", ComponentGroups.PI_SEARCH, required = true),
    UbuntuComponent("vim", "vim", "vim", "vim", "终端编辑器", ComponentGroups.BASE),
    UbuntuComponent("tmux", "tmux", "tmux", "tmux", "终端复用（长任务挂后台）", ComponentGroups.BASE),
    UbuntuComponent("htop", "htop", "htop", "htop", "进程与资源查看", ComponentGroups.BASE),

    // ── 语言运行时：JVM ──
    UbuntuComponent("java", "OpenJDK 17", "openjdk-17-jdk", "java", "Java 运行时 / 编译（jdk）", ComponentGroups.JAVA, heavy = true),
    // 注：apt 的 gradle 是 4.4（2017 年）对现代工程基本不可用 → 不进清单；要 Gradle 用工程自带 wrapper。

    // ── 语言运行时：Go / Rust ──
    UbuntuComponent("go", "Go", "golang-go", "go", "Go 工具链", ComponentGroups.GO, heavy = true),
    UbuntuComponent(
        "rust", "Rust（rustup）", "rustc", "rustc", "rustc / cargo（官方 rustup 安装，体积较大）", ComponentGroups.RUST,
        detectCmd = "command -v rustc",
        installCmd = "curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --profile minimal",
        heavy = true,
    ),

    // ── 网络与远程 ──
    UbuntuComponent("ssh", "openssh-client", "openssh-client", "ssh", "SSH 客户端（git over ssh）", ComponentGroups.SSH),
    UbuntuComponent("sshpass", "sshpass", "sshpass", "sshpass", "非交互 SSH（密码登录脚本）", ComponentGroups.SSH),
    UbuntuComponent("rsync", "rsync", "rsync", "rsync", "增量同步（本地 / 远端）", ComponentGroups.SSH),
    UbuntuComponent(
        "openssh-server", "OpenSSH 服务器", "openssh-server", "sshd", "反向上隧道挂载本地文件系统用", ComponentGroups.SSH,
        detectCmd = "dpkg -s openssh-server",
    ),
)

/**
 * **pi agent 本体**：不是一个"待安装的组件"，而是 **Pient 的运行时**——
 * 它随 APK 预置在 rootfs 的 npm 全局目录里（`usr/lib/node_modules/@earendil-works/pi-coding-agent`
 * + `/usr/bin/pi`），**不需要用户勾选安装**；这个常量只供「Pient 运行时」状态卡上的
 * **「更新到最新版」**用（执行 `npm install -g` 覆盖同一条路径，与预置布局同构）。
 * 因此它不在 [UBUNTU_COMPONENTS] 里，也不参与「已装 n/30」的计数。
 */
val PI_AGENT_UPDATE = UbuntuComponent(
    "pi", "pi agent", "pi", "pi",
    "随 Pient 预置；这一项只把 pi 更新到 npm 官方最新",
    ComponentGroups.NODE,
    // 先自检 npm：pi 本体不需要 npm（工具都在包里），但「更新」这一步是 npm 在干活 ——
    // 实测踩过：guest 里 npm 缺位时只报 `npm: command not found`（退出码 127），看不出该干什么。
    installCmd = "if ! command -v npm >/dev/null 2>&1; then " +
        "echo '[pi 更新] 找不到 npm —— 请先在本页勾选「Node.js 24」重装 Node（npm 随 Node 一起来）'; exit 3; fi; " +
        "npm install -g --ignore-scripts @earendil-works/pi-coding-agent",
)
