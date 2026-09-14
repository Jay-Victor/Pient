package com.pient.app.tools.system

import android.content.Context
import android.util.Log
import com.pient.app.data.RootGateway
import com.pient.app.data.ShizukuGateway
import com.pient.app.runtime.ShizukuShellChannel
import com.pient.app.tools.ToolLayer
import com.pient.app.tools.ToolOutcome
import com.pient.app.tools.ToolPart
import com.pient.app.tools.ToolSpec
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * **① 系统命令层** —— Android shell：这是**特权后门**，借 Shizuku / Root 身份直接对 Android
 * 系统下令（`am` / `pm` / `cmd` / `dumpsys` / `getprop` / `settings`）。
 *
 * 三条通道，按能力就高（`su` → Shizuku 用户服务 → 标准应用身份）：
 * 1. `su -c`：uid 0，需设备已 Root；
 * 2. Shizuku 用户服务：uid 2000（ADB 级），无需 Root，模拟器实测可用；
 * 3. 标准：应用身份（u0_aXXX）跑 `/system/bin/sh` —— **永远可用**（对齐 Operit
 *    `StandardShellExecutor`：`isAvailable()` 恒真），能跑基础命令，`am`/`pm` 多数被系统拒绝，
 *    报错原样回给模型（不假装成功）。
 *
 * 为什么这一层必须在 Kotlin 侧：Shizuku 的 `newProcess` 只能在 Java/binder 侧调，
 * shell 链路到不了（实测结论）——所以宿主里的扩展工具也只能经回桥回到这里执行。
 * 与「终端层」的区别：这一层不认识 Ubuntu、不跑 GNU 工具链，它只对 Android 系统下令。
 */
object SystemPart : ToolPart {

    private const val TAG = "PiHost"
    private const val MAX_OUTPUT = 200_000

    override val layer = ToolLayer.SYSTEM

    /** AI 可见工具 = `shell`：Android 系统命令通道（am / pm / cmd / dumpsys / getprop / settings） */
    override fun specs(): List<ToolSpec> = listOf(
        ToolSpec(
            "shell",
            "系统命令（Android）",
            "Run an Android system command (am / pm / cmd / dumpsys / getprop / settings …) through Pient's " +
                "privileged channel (Shizuku ADB-level or root, falling back to the app's own uid). " +
                "Use this for Android system operations; use `terminal` for GNU/Linux work inside Ubuntu.",
            ToolSpec.obj(
                "command" to ToolSpec.prop("string", "Android system command to run, e.g. 'dumpsys battery' or 'pm list packages'"),
                "timeoutMs" to ToolSpec.prop("number", "Timeout in milliseconds (default 120000)"),
                required = listOf("command"),
            ),
            layer,
        ),
    )

    override fun notReady(context: Context): String? = null

    override suspend fun run(context: Context, name: String, args: JSONObject): ToolOutcome {
        val command = args.optString("command").trim()
        if (command.isEmpty()) return ToolOutcome.err("缺少 command 参数")
        val timeoutMs = args.optLong("timeoutMs", 120_000)
        val res = execute(context, command, args.optString("cwd").takeIf { it.isNotBlank() }, timeoutMs)
        val error = res.optString("error").takeIf { it.isNotBlank() }
        val channel = res.optString("channel").takeIf { it.isNotBlank() }
        if (error != null) {
            return ToolOutcome("[${error}] ${res.optString("message")}".trim(), true, channel = channel)
        }
        return ToolOutcome(res.optString("output").ifEmpty { "(无输出)" }, false, channel = channel)
    }

    // ───────────────────────── 通道选择与执行 ─────────────────────────

    /** 执行一条系统命令（按可用通道）；返回响应 JSON（`{channel, code, output}` / `{error, message}`） */
    fun execute(context: Context?, command: String, cwd: String?, timeoutMs: Long): JSONObject {
        // 通道 1：su（Root 档）
        if (hasSu()) return runSu(command, cwd, timeoutMs)

        // 通道 2：Shizuku 用户服务（ADB 级 uid 2000，无需 Root）
        if (context != null && runCatching { ShizukuGateway.authorized() }.getOrDefault(false)) {
            if (ShizukuShellChannel.ensureBound(context)) {
                ShizukuShellChannel.exec(command, cwd, timeoutMs)?.let { return it }
                return JSONObject()
                    .put("error", "shizuku-call-failed")
                    .put("message", ShizukuShellChannel.lastError ?: "用户服务调用失败")
            }
            return JSONObject()
                .put("error", "shizuku-unavailable")
                .put("message", "Shizuku 已授权，但用户服务不可用：" +
                    (ShizukuShellChannel.lastError ?: "未知原因"))
        }

        // 通道 3：标准权限（应用身份跑 /system/bin/sh）
        return runStandard(command, cwd, timeoutMs)
    }

    /** 标准档：应用身份（u0_aXXX）的 /system/bin/sh —— 永远可用，能力受限但真实 */
    private fun runStandard(command: String, cwd: String?, timeoutMs: Long): JSONObject {
        val full = if (cwd.isNullOrBlank()) command else "cd ${shellQuote(cwd)} && $command"
        return runCatching {
            val p = ProcessBuilder("/system/bin/sh", "-c", full).redirectErrorStream(true).start()
            collect(p, timeoutMs, "standard")
        }.getOrElse {
            JSONObject().put("error", "exec-failed").put("channel", "standard")
                .put("message", it.message ?: it.javaClass.simpleName)
        }
    }

    private fun hasSu(): Boolean = SU_PATHS.any { runCatching { File(it).exists() }.getOrDefault(false) } ||
        runCatching {
            ProcessBuilder("sh", "-c", "command -v su").redirectErrorStream(true).start()
                .let { p -> p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0 }
        }.getOrDefault(false)

    private fun runSu(command: String, cwd: String?, timeoutMs: Long): JSONObject {
        val full = if (cwd.isNullOrBlank()) command else "cd ${shellQuote(cwd)} && $command"
        return runCatching {
            val p = ProcessBuilder("su", "-c", full).redirectErrorStream(true).start()
            collect(p, timeoutMs, "su")
        }.getOrElse {
            JSONObject().put("error", "exec-failed").put("channel", "su")
                .put("message", it.message ?: it.javaClass.simpleName)
        }
    }

    /** 起进程 → 收输出 → 组装统一响应（三通道共用，超时硬杀） */
    private fun collect(p: Process, timeoutMs: Long, channel: String): JSONObject {
        val text = StringBuilder()
        val reader = Thread {
            runCatching { p.inputStream.bufferedReader().forEachLine { text.append(it).append('\n') } }
        }
        reader.isDaemon = true
        reader.start()
        if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            runCatching { p.destroy() }
            return JSONObject().put("error", "timeout").put("channel", channel)
                .put("message", "命令超时（${timeoutMs}ms）")
        }
        reader.join(1000)
        val out = text.toString().let { if (it.length > MAX_OUTPUT) it.takeLast(MAX_OUTPUT) else it }
        return JSONObject().put("channel", channel).put("code", p.exitValue()).put("output", out.trimEnd())
    }

    /** 单引号包裹（su -c 走 sh 解析，路径里有空格/中文时要防拆） */
    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private val SU_PATHS = arrayOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/debug_ramdisk/su", "/data/adb/ksu/bin/su", "/data/adb/ap/bin/su",
    )

    // ───────────────────────── 档位状态（系统权限页 / 工具页共用） ─────────────────────────

    /** 当前实际生效的档（最高可用）：su → Shizuku → 标准 */
    fun effectiveTier(context: Context): ShellTier =
        tiers(context).firstOrNull { it.ready && it.tier != ShellTier.STANDARD }?.tier ?: ShellTier.STANDARD

    /** 三档实时状态（唯一实现：系统权限页与工具错误信息都从这里取） */
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

    /** 初始化提示：某档未就绪时的可读原因（诊断与日志用） */
    fun describeUnavailable(context: Context): String? =
        tiers(context).firstOrNull { !it.ready && it.supported }?.let { "${it.tier.title}：${it.detail}" }
}

/**
 * Android shell（**系统命令通道**）的三档边界 —— 2026-09-13 定稿。
 *
 * 术语分清（用户明确要求过）：这**不是** bash 的执行环境（bash 只在 Ubuntu 里跑），而是
 * AI 的系统命令工具（am / pm / dumpsys / getprop / settings 等）走哪条通道：
 *
 *  - [STANDARD] 标准权限：以**应用身份**（u0_aXXX）跑 `/system/bin/sh` —— 永远可用，无需任何授权；
 *  - [ADB]      ADB 级（Shizuku）：由 Shizuku 以 shell（uid 2000）身份托管 Pient 的用户服务执行；
 *  - [ROOT]     Root：`su -c` 通道，uid 0，可动系统分区，仅已 Root 的设备。
 *
 * 对齐 Operit 的 `ShellExecutorFactory`：**STANDARD 永远可用**（`isAvailable() = true`），
 * 而不是「没授权就整档不可用」。
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
