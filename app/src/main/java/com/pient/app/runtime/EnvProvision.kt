package com.pient.app.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pient.app.data.AptMirror
import com.pient.app.data.UbuntuComponent
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 环境内软件（Ubuntu）的**真实**配置：apt 镜像源写入 + 组件检测 + 组件安装。
 *
 * 两条硬口径：
 * 1. 这些命令**永远在 Ubuntu 环境里跑**，与「AI 工具当前用的执行环境」无关——所以进程带上
 *    `PIENT_EXEC_ENV=ubuntu` 覆盖包装脚本的路由（选着 Android shell 时也能装组件）；
 * 2. 输出**流式回显**到页面（apt 装包动辄几十秒到几分钟，没有反馈就是「点了没反应」）。
 */
object EnvProvision {

    private const val TAG = "PiHost"
    private const val MAX_LOG = 400

    /** 页面日志（最新在尾；跨页面进出保留，清空走 [clearLog]） */
    val log = mutableStateListOf<String>()

    /** 是否有安装任务在跑（页面据此禁用按钮 + 显示进度） */
    var running by mutableStateOf(false)
        private set

    /** 当前步骤说明（「更新索引…」这类） */
    var step by mutableStateOf("")
        private set

    private var process: Process? = null
    private val main = Handler(Looper.getMainLooper())

    fun clearLog() {
        log.clear()
        step = ""
    }

    // ─────────────────────────── 镜像源 ───────────────────────────

    /**
     * 把 apt 镜像源写进 rootfs：`/etc/apt/sources.list.d/ubuntu.sources`（**deb822 格式**——
     * Ubuntu 24.04 起 sources.list 只剩一句「已迁移」的注释，真正生效的是这个文件）。
     * 两块都指向同一个镜像（国内镜像同时承载 noble-security）。
     */
    fun applyMirror(context: Context, mirror: AptMirror): Boolean = runCatching {
        val dir = File(PiRuntime.rootfsDir(context), "etc/apt/sources.list.d")
        dir.mkdirs()
        File(dir, "ubuntu.sources").writeText(
            """
            |Types: deb
            |URIs: ${mirror.uri}
            |Suites: noble noble-updates noble-backports
            |Components: main universe restricted multiverse
            |Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
            |
            |Types: deb
            |URIs: ${mirror.uri}
            |Suites: noble-security
            |Components: main universe restricted multiverse
            |Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
            |
            """.trimMargin(),
        )
        Log.i(TAG, "apt 镜像源已写入：${mirror.name} → ${mirror.uri}")
        true
    }.getOrElse {
        Log.w(TAG, "apt 镜像源写入失败：${it.message}")
        false
    }

    /** 当前 rootfs 里生效的镜像源 URI（读 ubuntu.sources；读不到返回空串） */
    fun currentMirrorUri(context: Context): String = runCatching {
        val f = File(File(PiRuntime.rootfsDir(context), "etc/apt/sources.list.d"), "ubuntu.sources")
        Regex("^URIs:\\s*(\\S+)", RegexOption.MULTILINE).find(f.readText())?.groupValues?.get(1).orEmpty()
    }.getOrDefault("")

    // ─────────────────────────── 组件检测 ───────────────────────────

    /**
     * 逐个组件的安装状态：**一条命令问完**（每次 `command -v` 一个进程太贵）。
     * Ubuntu 未就绪时全部按未安装返回。
     */
    fun detect(context: Context, components: List<UbuntuComponent>): Map<String, Boolean> {
        if (!PiRuntime.rootfsReady(context)) return components.associate { it.id to false }
        val script = components.joinToString("\n") { c ->
            "if command -v ${c.probe} >/dev/null 2>&1; then echo ${c.id}=1; else echo ${c.id}=0; fi"
        }
        val out = query(context, script, timeoutMs = 20_000)
        return components.associate { c ->
            c.id to Regex("(?m)^${Regex.escape(c.id)}=1\\s*$").containsMatchIn(out)
        }
    }

    // ─────────────────────────── 组件安装 ───────────────────────────

    /** 安装选中组件（先自愈 dpkg 半配置状态，再 `apt-get update` 刷新索引，最后一次性安装） */
    fun install(context: Context, components: List<UbuntuComponent>) {
        if (running || components.isEmpty()) return
        val pkgs = components.joinToString(" ") { it.pkg }
        val script = buildString {
            appendLine("export DEBIAN_FRONTEND=noninteractive")
            // 上一次安装被中断（App 被杀 / 用户取消）会留下 dpkg 半配置状态，
            // 之后 apt 一律 `E: dpkg was interrupted`（实测踩过）→ 先自愈再装。
            appendLine("dpkg --configure -a >/dev/null 2>&1 || true")
            appendLine("apt-get update")
            appendLine("apt-get install -y --no-install-recommends $pkgs")
        }
        run(context, script, "安装 ${components.size} 个组件")
    }

    /** 只刷新索引（换镜像源后用）；同样先自愈 dpkg，否则索引刷新也会被半配置状态挡住 */
    fun updateIndex(context: Context) {
        if (running) return
        run(
            context,
            "export DEBIAN_FRONTEND=noninteractive\ndpkg --configure -a >/dev/null 2>&1 || true\napt-get update",
            "刷新软件索引",
        )
    }

    fun cancel() {
        runCatching { process?.destroy() }
    }

    private fun run(context: Context, script: String, label: String) {
        val shell = PiRuntime.shellPath(context)
        if (!shell.isFile) {
            appendLog("终端运行时缺失：${shell.absolutePath}")
            return
        }
        step = "$label：准备中…"
        running = true
        val proc = runCatching {
            ProcessBuilder(shell.absolutePath, "-c", script)
                .redirectErrorStream(true)
                .also { pb ->
                    pb.environment().putAll(PiRuntime.environment(context))
                    // 强制 Ubuntu 环境：安装/检测与「AI 工具当前用哪个环境」无关
                    pb.environment()["PIENT_EXEC_ENV"] = "ubuntu"
                }
                .start()
        }.getOrElse {
            appendLog("启动失败：${it.message}")
            running = false
            step = ""
            return
        }
        process = proc
        appendLog("$ $label")
        Thread({
            val sb = StringBuilder()
            runCatching {
                val buf = ByteArray(4096)
                val input = proc.inputStream
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n == 0) continue
                    sb.append(String(buf, 0, n, Charsets.UTF_8))
                    // apt 的进度用 \r 刷新：两种换行都要切
                    while (true) {
                        val i = indexOfAny(sb, '\n', '\r') ?: break
                        val line = sb.substring(0, i)
                        sb.delete(0, i + 1)
                        if (line.isNotBlank()) main.post { appendLog(line) }
                    }
                }
            }.onFailure { Log.w(TAG, "读取安装输出失败：${it.message}") }
            if (sb.isNotEmpty()) main.post { appendLog(sb.toString()) }
            val code = runCatching { proc.waitFor() }.getOrDefault(-1)
            main.post {
                appendLog(if (code == 0) "[完成] 退出码 0" else "[失败] 退出码 $code")
                running = false
                step = ""
                process = null
            }
        }, "pient-env-provision").apply { isDaemon = true }.start()
    }

    // ─────────────────────────── 内部工具 ───────────────────────────

    /** 跑一条命令并取回输出（Ubuntu 环境，短任务） */
    private fun query(context: Context, script: String, timeoutMs: Long): String {
        val shell = PiRuntime.shellPath(context)
        if (!shell.isFile) return ""
        return runCatching {
            val p = ProcessBuilder(shell.absolutePath, "-c", script)
                .redirectErrorStream(true)
                .also { pb ->
                    pb.environment().putAll(PiRuntime.environment(context))
                    pb.environment()["PIENT_EXEC_ENV"] = "ubuntu"
                }
                .start()
            val text = StringBuilder()
            val reader = Thread {
                runCatching { p.inputStream.bufferedReader().forEachLine { text.append(it).append('\n') } }
            }
            reader.isDaemon = true
            reader.start()
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                runCatching { p.destroy() }
                return@runCatching ""
            }
            reader.join(1000)
            text.toString()
        }.getOrDefault("")
    }

    private fun indexOfAny(sb: StringBuilder, vararg chars: Char): Int? {
        var best: Int? = null
        chars.forEach { c ->
            val i = sb.indexOf(c.toString())
            if (i >= 0 && (best == null || i < best!!)) best = i
        }
        return best
    }

    private fun appendLog(line: String) {
        log += line
        while (log.size > MAX_LOG) log.removeAt(0)
    }
}
