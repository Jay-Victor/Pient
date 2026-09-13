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
 * 命令就在哪一个环境里跑。Pient 提供三种落点：
 *
 *  - [ANDROID]       系统自带 shell（toybox/mksh）：`am` / `pm` / `dumpsys` / `getprop` 这类
 *                    Android 系统命令直接可用，不依赖 rootfs，装了 App 就能跑；
 *  - [UBUNTU]        Ubuntu 24.04 rootfs（PRoot）：GNU bash + coreutils + apt，无需 Root；
 *  - [UBUNTU_CHROOT] 同一个 rootfs，但以 `su` + chroot 运行：真 uid 0、零模拟开销，需设备已 Root。
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
    ANDROID(
        id = "android",
        title = "Android shell",
        desc = "系统命令直通：am / pm / dumpsys / getprop / settings 等，经 Shizuku（ADB 级）或 Root 执行；解锁后 AI 的 android_shell 工具与 bash 都可用系统命令",
        badge = "需 Shizuku / Root",
    ),
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
enum class EnvAction { NONE, UNPACK_ROOTFS, REQUEST_ROOT, GRANT_PRIVILEGE }

/** 环境就绪状态：ready + 一句面向用户的说明（未就绪时说明缺什么）+ 该给什么初始化入口 */
data class EnvStatus(val ready: Boolean, val detail: String, val action: EnvAction = EnvAction.NONE)

/**
 * 三种执行环境的就绪判定（**唯一实现**：环境配置页与终端页提示都从这里取）。
 * 纯文件系统判定，不起进程（探针另有其处，见环境配置页的「试跑」）。
 */
object ExecEnvs {

    fun statusOf(context: Context, env: ExecEnv): EnvStatus = when (env) {
        ExecEnv.ANDROID -> androidStatus(context)
        ExecEnv.UBUNTU -> ubuntuStatus(context, chroot = false)
        ExecEnv.UBUNTU_CHROOT -> ubuntuStatus(context, chroot = true)
    }

    private fun androidStatus(context: Context): EnvStatus {
        val rooted = RootGateway.deviceRooted(context)
        val shizukuAuthorized = ShizukuGateway.authorized()
        val shizukuInstalled = ShizukuGateway.installed(context)
        return when {
            rooted -> EnvStatus(true, "设备已 Root：bash 与系统命令都以 root 身份执行（su 通道）")
            shizukuAuthorized -> EnvStatus(
                false,
                "Shizuku 已授权：系统命令可用（AI 的 android_shell 工具，uid 2000，已实测）；作为 bash 落点需要 Root",
            )
            shizukuInstalled -> EnvStatus(
                false,
                "已安装 Shizuku，但服务未运行 / 未授权",
                EnvAction.GRANT_PRIVILEGE,
            )
            else -> EnvStatus(
                false,
                "标准权限下不可用：需 Shizuku（调试权限）或 Root 授权后才能执行 Android 系统命令",
                EnvAction.GRANT_PRIVILEGE,
            )
        }
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
            chroot && !su -> EnvStatus(
                false,
                "rootfs 已就绪，但设备上没有可用的 su（未 Root / 未授权）",
                EnvAction.REQUEST_ROOT,
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
 * 可勾选安装的常用组件（全部走 apt，装进 Ubuntu 环境里）。
 * [probe] 是**检测命令名**（`command -v <probe>` 判定是否已装；多数与包名相同）。
 */
data class UbuntuComponent(
    val id: String,
    val name: String,
    val pkg: String,
    val probe: String,
    val desc: String,
)

val UBUNTU_COMPONENTS = listOf(
    UbuntuComponent("ca", "CA 证书", "ca-certificates", "update-ca-certificates", "https 源 / 下载校验的基础（minbase 未带）"),
    UbuntuComponent("git", "Git", "git", "git", "版本控制，AI 拉取/提交代码用"),
    UbuntuComponent("curl", "curl", "curl", "curl", "HTTP 请求与下载"),
    UbuntuComponent("wget", "wget", "wget", "wget", "下载工具（脚本常用）"),
    UbuntuComponent("unzip", "unzip", "unzip", "unzip", "解压 zip 归档"),
    UbuntuComponent("python3", "Python 3", "python3", "python3", "脚本运行时（AI 跑 .py 用）"),
    UbuntuComponent("pip3", "pip", "python3-pip", "pip3", "Python 包管理"),
    UbuntuComponent("nodejs", "Node.js", "nodejs", "node", "JS 运行时（AI 跑前端/脚本用）"),
    UbuntuComponent("npm", "npm", "npm", "npm", "Node 包管理"),
    UbuntuComponent("build", "编译工具链", "build-essential", "gcc", "gcc / make 等（编译原生代码）"),
    UbuntuComponent("vim", "vim", "vim", "vim", "终端编辑器"),
    UbuntuComponent("tmux", "tmux", "tmux", "tmux", "终端复用"),
    UbuntuComponent("ssh", "openssh-client", "openssh-client", "ssh", "SSH 客户端（git over ssh）"),
)
