package com.pient.app.data

import android.content.Context
import android.os.Build
import com.pient.app.runtime.PiRuntime
import java.io.File

/**
 * 执行环境 —— **AI 的工具（移植进来的 pi agent 工具）命令的执行落点**。
 *
 * 为什么会有「环境」这一层：pi 的 `bash` 工具（以及输入栏 `!` 命令）最终是
 * `spawn(shellPath, ["-c", 命令])`（pi `core/tools/bash.ts:95`），shellPath 指向哪一个 shell，
 * 命令就在哪一个环境里跑。Pient 提供两种落点（都是 Ubuntu，**Android shell 不在这里**）：
 *
 *  - [UBUNTU]        Ubuntu 24.04 rootfs（PRoot）：GNU bash + coreutils + apt，无需 Root；
 *  - [UBUNTU_CHROOT] 同一个 rootfs，但以 `su` + chroot 运行：真 uid 0、零模拟开销，需设备已 Root。
 *
 * **Android shell（am / pm / dumpsys 等系统命令）是另一回事**：它不是 bash 的可选落点
 * （`spawn` 到不了 Shizuku，只有 root 的 su 才行），而是 AI 的 `android_shell` 工具走哪条
 * **系统命令通道**——三档边界见 [ShellTier] / [AndroidShell]，管理入口在「设置 → 系统权限」。
 *
 * 选择结果落在 `SettingsStore.execEnv`，由 [PiRuntime.prepareTerminal] 写成运行时根目录下的
 * `exec_env` 文件；随包分发的包装脚本（pi 的 shellPath）**每次被执行时现读它**——改完下一个
 * 命令/新会话即生效（已在跑的会话进程不换环境）。
 */
enum class ExecEnv(
    val id: String,          // 写进 exec_env 文件与 prefs 的字面量
    val title: String,
    val desc: String,
    val badge: String,       // 卡片右侧徽标（推荐 / 需 Root / 开箱即用）
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

/** 未就绪时的初始化入口（页面据此给按钮；不靠文案猜测） */
enum class EnvAction {
    NONE,
    UNPACK_ROOTFS,
    REQUEST_ROOT,

    /** 设备不具备该能力（如未 Root 设备上的 chroot）：页面显示为不可用、不给操作入口 */
    UNSUPPORTED,
}

/** 环境就绪状态：ready + 一句面向用户的说明（未就绪时说明缺什么）+ 该给什么初始化入口 */
data class EnvStatus(val ready: Boolean, val detail: String, val action: EnvAction = EnvAction.NONE)

/**
 * Android shell（**系统命令通道**）的三档边界 —— 2026-09-13 定稿。
 *
 * 术语分清（用户明确要求过）：这**不是** bash 的执行环境（bash 只在 Ubuntu 里跑），而是
 * AI 的 `android_shell` 工具（am / pm / dumpsys / getprop / settings 等系统命令）走哪条通道：
 *
 *  - [STANDARD] 标准权限：以**应用身份**（u0_aXXX）跑 `/system/bin/sh` —— 永远可用，无需任何授权；
 *    能跑 shell/toybox 的基础命令，`am` / `pm` / `dumpsys` 这类多数会被系统拒绝（报错原样回给模型）；
 *  - [ADB]      ADB 级（Shizuku）：由 Shizuku 以 shell（uid 2000）身份托管 Pient 的用户服务执行，
 *    无需 Root，绝大多数系统命令可用（模拟器已实测 uid=2000）；
 *  - [ROOT]     Root：`su -c` 通道，uid 0，可动系统分区，仅已 Root 的设备。
 *
 * 对齐 Operit 的 `ShellExecutorFactory`（ROOT / ADMIN / DEBUGGER / ACCESSIBILITY / STANDARD）里
 * 的一条关键语义：**STANDARD 永远可用**（`isAvailable() = true`、`hasPermission() = granted()`），
 * 而不是"没授权就整档不可用"。
 */
enum class ShellTier(val id: String, val title: String, val desc: String) {
    STANDARD(
        "standard",
        "标准权限（普通用户）",
        "以应用身份（u0_aXXX）跑 /system/bin/sh：无需任何授权。实测 getprop / pm list packages 可用，dumpsys 这类被拒" +
            "（`Can't find service: battery`），报错原样回给模型",
    ),
    ADB(
        "adb",
        "ADB 级（Shizuku）",
        "由 Shizuku 以 shell（uid 2000）身份托管 Pient 的用户服务执行系统命令；无需 Root，绝大多数系统命令可用（模拟器实测 uid=2000）",
    ),
    ROOT(
        "root",
        "Root（su）",
        "su -c 通道，uid 0；可改系统分区、绑特权端口；仅已 Root 的设备",
    ),
}

/** 某档当前是否可用 + 状态说明 */
data class ShellTierStatus(
    val tier: ShellTier,
    val ready: Boolean,
    val detail: String,
    /** 设备是否**具备**该档能力：false = 设备不支持（未 Root 的设备上的 Root 档），页面不给操作入口 */
    val supported: Boolean = true,
)

/**
 * 三档 Android shell 的实时状态（**唯一实现**：系统权限页与工具错误信息都从这里取）。
 * 通道优先级与 `PiExecServer` 一致：su → Shizuku 用户服务 → 标准（应用身份）。
 */
object AndroidShell {
    fun tiers(context: Context): List<ShellTierStatus> {
        val rooted = RootGateway.deviceRooted(context)
        val shizukuInstalled = ShizukuGateway.installed(context)
        val shizukuAuthorized = ShizukuGateway.authorized()
        return listOf(
            ShellTierStatus(
                ShellTier.STANDARD,
                true,
                "可用（无需授权）：应用身份 u0_a229 这类；实测 getprop / pm list packages 可用，dumpsys 被拒（报错原样回传）",
            ),
            ShellTierStatus(
                ShellTier.ADB,
                shizukuAuthorized,
                when {
                    shizukuAuthorized -> "可用：Shizuku 已授权，命令以 uid 2000（shell / ADB 级）执行"
                    shizukuInstalled -> "需授权：已安装 Shizuku，但服务未运行 / 未授权"
                    else -> "未安装 Shizuku：装好并激活后授权即可（无需 Root）"
                },
            ),
            ShellTierStatus(
                ShellTier.ROOT,
                rooted,
                if (rooted) "可用：设备已 Root，命令以 uid 0 执行" else "设备不支持：未检测到 su / Root 管理器（模拟器、未 Root 真机）",
                supported = rooted,
            ),
        )
    }

    /** 当前实际生效的档（最高可用）：su → Shizuku → 标准 */
    fun effectiveTier(context: Context): ShellTier =
        tiers(context).firstOrNull { it.ready && it.tier != ShellTier.STANDARD }?.tier ?: ShellTier.STANDARD
}

/**
 * 两种执行环境的就绪判定（**唯一实现**：环境配置页与终端页提示都从这里取）。
 * 纯文件系统判定，不起进程（探针另有其处，见环境配置页的「试跑」）。
 */
object ExecEnvs {

    fun statusOf(context: Context, env: ExecEnv): EnvStatus = when (env) {
        ExecEnv.UBUNTU -> ubuntuStatus(context, chroot = false)
        ExecEnv.UBUNTU_CHROOT -> ubuntuStatus(context, chroot = true)
    }

    private fun ubuntuStatus(context: Context, chroot: Boolean): EnvStatus {
        val missing = PiRuntime.ubuntuChecks(context).filterNot { it.second }.map { it.first }
        val su = RootGateway.deviceRooted(context)
        val rootfsMissing = PiRuntime.ubuntuChecks(context).none { it.first.contains("rootfs") && it.second }
        return when {
            rootfsMissing && PiRuntime.isUnpacking() -> EnvStatus(
                false,
                "正在自动解包 rootfs：${PiRuntime.unpackNote().ifBlank { "准备中…" }}",
            )
            rootfsMissing && !PiRuntime.rootfsArchiveAvailable(context) -> EnvStatus(
                false,
                "此 APK 未内置 rootfs 归档：构建时没跑 fetch_rootfs.py --abi 本机 ABI（arm64 真机要 --abi aarch64），" +
                    "syncPientRootfsArchive 被跳过 —— 换用含归档的包，或按文档重新打包",
            )
            rootfsMissing -> EnvStatus(
                false,
                "缺少 Ubuntu rootfs（随包归档未解包，约 30MB / 1–2 分钟）",
                EnvAction.UNPACK_ROOTFS,
            )
            missing.isNotEmpty() -> EnvStatus(false, "缺少：" + missing.joinToString("、"))
            // chroot 档要 su。区分两种「拿不到 su」：
            //   设备本就未 Root（无 su 二进制 / 无 Root 管理器）→ 设备不支持，不给请求入口（不让用户选）；
            //   设备有 su 但本应用未授权 → 给「请求 Root 授权」入口。
            chroot && !su -> EnvStatus(
                false,
                "设备未 Root：chroot 档不可用（用默认的 PRoot 即可，功能一致、只是有模拟开销）",
                EnvAction.UNSUPPORTED,
            )
            chroot -> EnvStatus(true, "rootfs 已就绪 · 将以 su + chroot 运行（首次命令会请求 Root 授权）")
            else -> EnvStatus(true, "rootfs 已就绪 · 经 PRoot 运行（应用 uid）")
        }
    }
}

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
 * 可勾选安装的常用组件（装进 Ubuntu 环境里）。
 *
 * - [probe]：默认检测用的**命令名**（`command -v <probe>`）；
 * - [detectCmd]：自定义检测命令（装了但版本不对、或没有可执行文件时用，如 Node 要求 v24+、
 *   openssh-server 只有守护进程没有命令）；
 * - [installCmd]：自定义安装命令（不走 apt 单包，如 NodeSource / npm 全局包 / rustup）；
 * - [group]：UI 分组（对齐 Operit 的 SetupScreen 分类）。
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
    val group: String = "基础",
    val detectCmd: String? = null,
    val installCmd: String? = null,
)

val UBUNTU_COMPONENTS = listOf(
    // ── 基础 ──
    UbuntuComponent("ca", "CA 证书", "ca-certificates", "update-ca-certificates", "https 源 / 下载校验的基础（minbase 未带）", "基础"),
    UbuntuComponent("curl", "curl", "curl", "curl", "HTTP 请求与下载", "基础"),
    UbuntuComponent("wget", "wget", "wget", "wget", "下载工具（脚本常用）", "基础"),
    UbuntuComponent("git", "Git", "git", "git", "版本控制，AI 拉取/提交代码用", "基础"),
    UbuntuComponent("unzip", "unzip", "unzip", "unzip", "解压 zip 归档", "基础"),

    // ── Python ──
    UbuntuComponent("python3", "Python 3", "python3", "python3", "脚本运行时（AI 跑 .py 用）", "Python"),
    UbuntuComponent("pip3", "pip", "python3-pip", "pip3", "Python 包管理", "Python"),
    UbuntuComponent("python-is-python3", "python 别名", "python-is-python3", "python", "让 `python` 指向 python3（脚本兼容）", "Python"),
    UbuntuComponent("python3-venv", "venv 虚拟环境", "python3-venv", "python3", "`python3 -m venv` 可用", "Python", detectCmd = "dpkg -s python3-venv"),
    UbuntuComponent(
        "uv", "uv（Python 包管理器）", "python3-pip", "uv",
        "pip 的快速替代：`uv pip install` / `uv venv`",
        "Python",
        installCmd = "apt-get install -y --no-install-recommends python3-pip && python3 -m pip install --break-system-packages -q uv",
    ),

    // ── Node.js（对齐 Operit：走 NodeSource 装 Node 24，发行版只有 18） ──
    UbuntuComponent(
        "nodejs", "Node.js 24（NodeSource）", "nodejs", "node",
        "JS 运行时；NodeSource 官方源装 Node 24（apt 里的 nodejs 只有 18）",
        "Node.js",
        detectCmd = "node -v 2>/dev/null | grep -q '^v2[4-9]\\.'",
        installCmd = "apt-get install -y --no-install-recommends ca-certificates curl gnupg && " +
            "curl -fsSL https://deb.nodesource.com/setup_24.x | bash - && " +
            "apt-get install -y nodejs",
    ),
    UbuntuComponent(
        "pnpm", "pnpm", "pnpm", "pnpm", "Node 包管理器（快、省盘）", "Node.js",
        detectCmd = "command -v pnpm",
        installCmd = "npm install -g pnpm",
    ),
    UbuntuComponent(
        "typescript", "TypeScript", "typescript", "tsc", "tsc 编译器（AI 写 TS 时用）", "Node.js",
        detectCmd = "command -v tsc",
        installCmd = "npm install -g typescript",
    ),

    // ── 编译 / 系统 ──
    UbuntuComponent("build", "编译工具链", "build-essential", "gcc", "gcc / make 等（编译原生代码）", "编译 / 系统"),
    UbuntuComponent("vim", "vim", "vim", "vim", "终端编辑器", "编译 / 系统"),
    UbuntuComponent("tmux", "tmux", "tmux", "tmux", "终端复用", "编译 / 系统"),

    // ── Java（Operit 同款） ──
    UbuntuComponent("java", "OpenJDK 17", "openjdk-17-jdk", "java", "Java 运行时 / 编译（jdk）", "Java"),
    UbuntuComponent("gradle", "Gradle", "gradle", "gradle", "JVM 构建工具", "Java"),

    // ── Go / Rust（Operit 同款） ──
    UbuntuComponent("go", "Go", "golang-go", "go", "Go 工具链", "Go / Rust"),
    UbuntuComponent(
        "rust", "Rust（rustup）", "rustc", "rustc", "rustc / cargo（官方 rustup 安装，体积较大）", "Go / Rust",
        detectCmd = "command -v rustc",
        installCmd = "curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --profile minimal",
    ),

    // ── SSH ──
    UbuntuComponent("ssh", "openssh-client", "openssh-client", "ssh", "SSH 客户端（git over ssh）", "SSH"),
    UbuntuComponent("sshpass", "sshpass", "sshpass", "sshpass", "非交互 SSH（密码登录脚本）", "SSH"),
    UbuntuComponent(
        "openssh-server", "OpenSSH 服务器", "openssh-server", "sshd", "反向上隧道挂载本地文件系统用", "SSH",
        detectCmd = "dpkg -s openssh-server",
    ),
)

