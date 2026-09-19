package com.pient.app.data

import android.content.Context
import android.os.Build
import com.pient.app.data.i18n.L
import com.pient.app.data.i18n.Languages
import com.pient.app.runtime.AndroidShell
import com.pient.app.runtime.PiRpc
import com.pient.app.runtime.PiRpcState
import com.pient.app.runtime.PiRuntime
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 日志导出（「应用日志管理」的产物组装）。
 *
 * 一个导出文件 = 四段（顺序即阅读顺序）：
 *   ① 头部（导出时间 / Pient 版本）
 *   ② **环境报告** —— 设备、ABI、权限档位、执行环境、rootfs 与 pi 就绪状态、node 在不在、
 *      pi 通道状态、服务商与模型名（**只有名字，没有密钥**）、当前项目；
 *      用 `key: value` 英文键 —— 这是排障产物（给人和 AI 直接读），不进 i18n 流水线。
 *   ③ pi stderr 尾巴（pi 进程的报错原文，最常被读的一段）
 *   ④ 应用日志全量（[PientLog.readAll]，含轮转文件）
 *   ⑤ 系统 logcat（特权档才抓得到，见 [systemLogTail]）
 *
 * **不含**：API 密钥（auth.json 里的凭据一律不出现）、会话正文（那是「导出会话」的事）。
 *
 * 落点 = 系统「下载/Pient/」，写盘走 [DownloadsOut]（与会话导出同一份实现）。
 */
object LogExport {

    private const val TAG = "PientExport"

    /** 导出范围：全部 / 仅警告以上 / 最近 30 分钟 —— 作用于**应用日志段** */
    enum class LogScope(val minutes: Long) {
        ALL(0L),
        WARN(0L),
        RECENT(30L);

        val label: String
            get() = when (this) {
                ALL -> L.settings.logScopeAll
                WARN -> L.settings.logScopeWarn
                RECENT -> L.settings.logScopeRecent
            }
    }

    fun fileName(): String =
        "pient-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

    // ───────────────────────── 组装 ─────────────────────────

    /** 组装导出文本（系统日志由调用方先取好 —— 那段要跑特权通道，是阻塞调用） */
    fun build(context: Context, systemLog: String, scope: LogScope = LogScope.ALL): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("=== ").append(L.settings.logDocTitle).append(" ===\n")
        sb.append(L.settings.logExportTimeLabel).append(stamp).append('\n')
        sb.append(L.settings.logScopeLabel).append(scope.label).append('\n')
        sb.append('\n')

        sb.append(section(L.settings.logSectionEnv)).append('\n')
        sb.append(envReport(context)).append('\n')

        // 上次退出（系统记录，Android 11+）：崩溃 / ANR / 被系统杀 —— 我们自己的崩溃 handler 看不到那几种
        sb.append(section(L.settings.logSectionExit)).append('\n')
        sb.append(ExitHistory.exportSection(context)).append("\n\n")

        val stderr = PiRpc.stderrText().trim()
        sb.append(section(L.settings.logSectionStderr(stderr.count { it == '\n' } + if (stderr.isEmpty()) 0 else 1)))
            .append('\n')
        sb.append(stderr.ifBlank { "-" }).append("\n\n")

        val all = PientLog.readAll(context)
        val scoped = applyScope(PientLog.parseRecords(all.lines()), scope)
        sb.append(section(L.settings.logSectionAppScoped(scope.label, scoped.size))).append('\n')
        sb.append(PientLog.render(scoped).ifBlank { "-" }).append('\n')

        sb.append(section(L.settings.logSectionSystem)).append('\n')
        sb.append(systemLog.ifBlank { "-" }).append('\n')
        return mask(sb.toString())
    }

    /** 范围过滤（应用日志段）：全部 / 仅警告以上 / 最近 30 分钟 */
    private fun applyScope(records: List<PientLog.Record>, scope: LogScope): List<PientLog.Record> = when (scope) {
        LogScope.ALL -> records
        LogScope.WARN -> PientLog.atLeastLevel(records, 'W')
        LogScope.RECENT -> PientLog.since(records, System.currentTimeMillis() - scope.minutes * 60_000L)
    }

    private fun section(title: String) = "──── $title ────"

    // ───────────────────────── 脱敏（导出前的最后一道） ─────────────────────────

    /**
     * 密钥类字符串掩码（defense in depth）：日志里本不该有密钥（auth.json 的凭据从不进日志），
     * 但 pi 的 stderr / 平台日志可能带出别的东西 —— 导出是「要发给外人」的动作，这里再兜一层。
     * 只掩明显的凭据形态，不动普通文本（免得把排障要看的 id 也糊掉）。
     */
    private val MASK_RULES: List<Pair<Regex, String>> = listOf(
        Regex("""\bsk-[A-Za-z0-9_\-]{12,}""") to "sk-***",
        Regex("""\b(?:ghp|gho|ghu|ghs|github_pat)_[A-Za-z0-9_]{16,}""") to "gh_***",
        Regex("""\bAIza[A-Za-z0-9_\-]{20,}""") to "AIza***",
        Regex("""(?i)\b(bearer)\s+[A-Za-z0-9._\-]{12,}""") to "$1 ***",
        Regex("""(?i)\b(api[_-]?key|apikey|access[_-]?token|refresh[_-]?token|password|passwd|secret)\b\s*[=:]\s*["']?[A-Za-z0-9._\-]{8,}["']?""") to "$1=***",
        Regex("""(?i)\b(authorization)\b\s*[=:]\s*["']?[A-Za-z0-9._\-]{12,}["']?""") to "$1=***",
    )

    private fun mask(text: String): String {
        var out = text
        MASK_RULES.forEach { (re, rep) -> out = re.replace(out, rep) }
        return out
    }

    /**
     * 诊断摘要（复制到剪贴板用）：版本 + 设备 + ABI + 档位/通道 + rootfs/pi/node + 通道状态 +
     * 服务商与模型 + 项目 + 上次退出 —— 就是历次排障真正要问的那十几行，不含日志正文。
     */
    fun diagnosticSummary(context: Context): String {
        val sb = StringBuilder()
        sb.append("=== ").append(L.settings.logDocTitle).append(" ===\n")
        sb.append(envReport(context))
        val exit = ExitHistory.last(context)?.let { "${it.label} · ${formatTime(it.timeMs)}" }
            ?: if (ExitHistory.supported()) "-" else "unsupported"
        sb.append("last_exit: ").append(exit).append('\n')
        return sb.toString()
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    /**
     * 环境报告：`key: value` 一行一条。
     *
     * 这些字段就是排障真正要问的那几个问题（ABI 对不对、rootfs 缺不缺 node、
     * 档位/通道是哪条、pi 起没起来、连的哪个模型），所以固定列出来 —— 别在别处再拼第二份。
     */
    fun envReport(context: Context): String {
        val sb = StringBuilder()
        fun line(k: String, v: Any?) {
            sb.append(k).append(": ").append(v).append('\n')
        }

        line("pient_version", PientLog.appVersion(context))
        line("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        line("android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        line("abi", "${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()} (${PiRuntime.hostMachine()})")

        val tier = SettingsStore.permissionTier
        line("tier", "${tier.code} ${tier.name.lowercase(Locale.US)}")
        line("shell_backend", AndroidShell.backend(context).id)
        if (!AndroidShell.available(context)) {
            // 「为什么拿不到系统能力」的原文（档位未配齐时它就是答案）
            line("shell_note", AndroidShell.statusText(context))
        }
        line("exec_env", SettingsStore.execEnv.id)

        val rootfsReady = runCatching { PiRuntime.rootfsReady(context) }.getOrDefault(false)
        line("rootfs", if (rootfsReady) "ready" else "NOT-READY")
        if (!rootfsReady) {
            line("rootfs_issue", runCatching { PiRuntime.rootfsIssue(context) }.getOrDefault("?"))
        }
        val piReady = runCatching { PiRuntime.piReady(context) }.getOrDefault(false)
        line(
            "pi",
            if (piReady) {
                "ready · v${runCatching { PiRuntime.piVersion(context) }.getOrDefault("")}"
            } else {
                "NOT-READY"
            },
        )
        // node 在不在 rootfs 里 —— 真机上「Ubuntu 没装 node → pi 起不来」是最常踩的一坑
        line(
            "node_in_rootfs",
            if (File(runCatching { PiRuntime.rootfsDir(context) }.getOrDefault(File("/")), "usr/bin/node").isFile) {
                "installed"
            } else {
                "missing"
            },
        )
        line("pi_rpc", rpcDesc())

        val cfgs = AiConfigStore.configs
        if (cfgs.isEmpty()) {
            line("providers", "none")
        } else {
            line(
                "providers",
                cfgs.values.joinToString("; ") { c ->
                    "${c.providerId} (${modelCount(c.modelList)} models)"
                },
            )
        }
        val chat = com.pient.app.PientRuntime.chatState
        val model = chat?.selectedModel
        line("current_model", if (model == null) "-" else "${model.provider}/${model.name}")

        val savedLang = SettingsStore.language
        val langId = runCatching { Languages.resolveId(savedLang) }.getOrDefault(savedLang)
        line("language", if (savedLang == Languages.SYSTEM) "$langId (follow system)" else langId)

        val project = chat?.currentProject
        line(
            "project",
            if (project.isNullOrBlank()) "-" else "$project (${chat.sessions[project]?.size ?: 0} sessions)",
        )
        return sb.toString()
    }

    private fun modelCount(modelList: String): Int =
        modelList.split(';', ',', '\n').count { it.isNotBlank() }

    private fun rpcDesc(): String = when (val st = PiRpc.state.value) {
        is PiRpcState.Running -> "running · ${st.provider}/${st.model}"
        is PiRpcState.Failed -> "failed · ${st.message}"
        PiRpcState.Starting -> "starting"
        PiRpcState.Stopped -> if (PiRpc.running()) "process alive (state=stopped)" else "stopped"
    }

    // ───────────────────────── 系统日志 ─────────────────────────

    /**
     * 系统 logcat（最近若干行，只挑与本应用相关的 tag + 崩溃/调度行）。
     *
     * 两条路：
     * - **调试 / Root 档**：走 Android shell 通道（Shizuku `newProcess` / `su -c`），以 shell 身份读
     *   `/dev/socket/logdr`，这就是完整 logcat。
     * - **标准档**：没有特权通道，仍**试一次**应用自己读自己的 logcat（Android 只把 READ_LOGS 发给
     *   系统应用，多数设备会直接拒绝）—— 拒绝也把原文写进导出文件：「为什么没有系统日志」本身
     *   就是排障信息，比留空白强。
     */
    fun systemLogTail(context: Context): String {
        if (AndroidShell.available(context)) {
            val cmd = "logcat -d -v threadtime -t 4000 | grep -E " +
                "'Pient|pient|PiRpc|AndroidRuntime|ActivityManager|lowmemorykiller|libc'"
            val r = AndroidShell.execBlocking(context, cmd, 20_000)
            return when {
                r.ok && r.stdout.isNotBlank() -> r.stdout
                r.ok -> L.settings.logSystemEmpty
                else -> L.settings.logSystemFailed(r.note.ifBlank { "exit=${r.exit}${if (r.timeout) " timeout" else ""}" })
            }
        }
        val tier = SettingsStore.permissionTier
        val self = selfLogcatAttempt()
        // 判据：真读到日志时行首是 logcat threadtime 的 `MM-DD HH:MM:SS.mmm`；
        // 读不到时这里拿到的是那句拒绝原文（如 Permission denied）——两种情形分开写，别把拒绝当日志。
        val looksReal = self.lineSequence().any {
            Regex("^\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}").containsMatchIn(it.trim())
        }
        return when {
            looksReal -> L.settings.logSystemSelf + "\n" + self
            self.isBlank() -> L.settings.logSystemNeedTier(tier.code)
            else -> L.settings.logSystemNeedTier(tier.code) + "\n" + self
        }
    }

    private fun selfLogcatAttempt(): String = runCatching {
        val p = ProcessBuilder("/system/bin/logcat", "-d", "-v", "threadtime", "-t", "200")
            .redirectErrorStream(true)
            .start()
        val watchdog = Thread {
            runCatching {
                Thread.sleep(15_000)
                if (p.isAlive) p.destroyForcibly()
            }
        }
        watchdog.isDaemon = true
        watchdog.start()
        val text = runCatching { p.inputStream.bufferedReader().readText() }.getOrDefault("")
        runCatching { p.waitFor(2, TimeUnit.SECONDS) }
        text.trim()
    }.getOrElse { "logcat 启动失败：${it.message}" }

    // ───────────────────────── 导出 ─────────────────────────

    /**
     * 导出并返回落点（失败 null）。调用方放 IO 线程（系统日志那段是阻塞的）。
     */
    fun export(context: Context, scope: LogScope = LogScope.ALL): DownloadsOut.Written? {
        val text = build(context, systemLogTail(context), scope)
        val where = DownloadsOut.writeText(context, fileName(), "text/plain", text)
        if (where != null) {
            PientLog.i(TAG, "日志已导出：${where.location}（${text.length} 字，范围=${scope.name}）")
        }
        return where
    }
}
