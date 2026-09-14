package com.pient.app.tools.terminal

import android.content.Context
import com.pient.app.data.RootGateway
import com.pient.app.runtime.PiRuntime
import java.io.File

/**
 * **执行环境（终端层的落点）** —— AI 的 `bash` 工具（以及输入栏 `!` 命令）最终是
 * `spawn(shellPath, ["-c", 命令])`（pi `core/tools/bash.ts:95`），shellPath 指向哪一个 shell，
 * 命令就在哪一个环境里跑。Pient 提供两种落点（**都是 Ubuntu**）：
 *
 *  - [UBUNTU]        Ubuntu 24.04 rootfs（PRoot）：GNU bash + coreutils + apt，无需 Root；
 *  - [UBUNTU_CHROOT] 同一个 rootfs，但以 `su` + chroot 运行：真 uid 0、零模拟开销，需设备已 Root。
 *
 * **Android shell 不在这里**：它不是 bash 的落点（`spawn` 到不了 Shizuku，只有 root 的 su 才行），
 * 而是系统命令层的通道 —— 见 [com.pient.app.tools.system.SystemPart] / `ShellTier`。
 *
 * 选择结果落在 `SettingsStore.execEnv`，由 `PiRuntime.prepareTerminal` 写成运行时根目录下的
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

/** 目录大小（环境页展示用；读不到返回 0） */
fun dirSizeOf(dir: File): Long = runCatching {
    dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)
