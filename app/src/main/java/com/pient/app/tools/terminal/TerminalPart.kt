package com.pient.app.tools.terminal

import android.content.Context
import com.pient.app.runtime.PiRuntime
import com.pient.app.tools.ToolLayer
import com.pient.app.tools.ToolOutcome
import com.pient.app.tools.ToolPart
import com.pient.app.tools.Truncate
import org.json.JSONObject

/**
 * **② 终端层** —— Ubuntu 沙盘（`PRoot`，Root 设备可换 chroot）。
 *
 * 边界：这一层只干一件事 —— 把命令送进 Ubuntu 里跑、把输出拿回来。它**不认识 Android 系统**
 * （那是 [com.pient.app.tools.system.SystemPart] 的事），也**不认识文件读写**（那是
 * [com.pient.app.tools.files.FilesPart] 的事 —— 普通文件操作原生直读，根本不经过 shell）。
 *
 * 会话引擎在 [TerminalSessions]（终端页与 AI 共用同一批常驻 shell 进程，
 * 「持久会话、AI 可执行、用户可见」就是这一层的形态）。改名与 `terminal` 工具化在 M2
 * （见 `Docx/Pient 工具层设计.md` §8）：M1 保持工具名 `bash` 不变，避免与宿主路径的
 * pi 内置 `bash` 在过渡期出现两个名字。
 */
object TerminalPart : ToolPart {

    override val layer = ToolLayer.TERMINAL

    private const val DEFAULT_TIMEOUT_S = 120L


    /**
     * 未就绪的两种情况分别说明（对应页面上「未就绪不可选」的同一口径）：
     * 包装脚本没随包（`pient-shell` 缺失）与 rootfs 未解包 —— 两者的修法不同，不能混成一句。
     *
     * 顺带把运行时软链对一遍（宿主冻结后，软链维护只有这里与终端页两处）：APK 更新会让
     * `/data/app` 路径变化、旧链悬空，而 `terminal` 工具的执行入口正是 `bin/pient-shell`。
     */
    override fun notReady(context: Context): String? {
        runCatching { PiRuntime.ensureLinks(context) }
        if (!PiRuntime.shellPath(context).isFile) {
            return "terminal 不可用：终端层未随包就绪（缺少 pient-shell）"
        }
        if (!PiRuntime.rootfsReady(context)) {
            return "terminal 不可用：Ubuntu 终端环境尚未就绪（rootfs 未解包）——" +
                "请到「终端 → 环境配置」点「一键配置/安装所选」后重试"
        }
        return null
    }

    override suspend fun run(context: Context, name: String, args: JSONObject): ToolOutcome {
        val cmd = args.optString("command").trim()
        if (cmd.isEmpty()) return ToolOutcome.err("缺少 command 参数")
        val timeoutS = args.optDouble("timeout", 0.0).takeIf { it > 0 }?.toLong() ?: DEFAULT_TIMEOUT_S
        val out = TerminalSessions.execOnce(context, cmd, (timeoutS * 1000).coerceIn(5_000, 600_000))
        return ToolOutcome.ok(Truncate.of(out.trim().ifEmpty { "（命令无输出）" }, "terminal"))
    }
}
