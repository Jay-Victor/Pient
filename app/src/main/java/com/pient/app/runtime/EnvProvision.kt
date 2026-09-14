package com.pient.app.runtime

import com.pient.app.tools.terminal.TerminalSessions
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pient.app.tools.terminal.AptMirror
import com.pient.app.tools.terminal.UbuntuComponent
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 环境内软件（Ubuntu）的**真实**配置：apt 镜像源写入 + 组件检测 + 组件安装。
 *
 * 三条硬口径：
 * 1. 这些命令**永远在 Ubuntu 环境里跑**，与「AI 工具当前用的执行环境」无关；
 * 2. **安装过程显示在终端页**（2026-09-14 用户口径）：点「安装所选」→ 跳到终端页，
 *    脚本在专用会话「环境配置」里跑，apt 输出实时滚 —— 本页不再自建日志区；
 * 3. 检测（`command -v` / `dpkg -s`）仍是短命令直查，不进终端（否则每次进页面都刷一屏）。
 */
object EnvProvision {

    private const val TAG = "PiHost"

    /** 有安装任务在跑（页面据此把「安装所选」换成「去终端」） */
    var running by mutableStateOf(false)
        private set

    /** 当前步骤说明（「安装 3 个组件」这类） */
    var step by mutableStateOf("")
        private set

    /** 上一次安装的退出码（null = 本轮还没跑完） */
    var lastExitCode by mutableStateOf<Int?>(null)
        private set

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
            // 自定义检测命令优先（装了但版本不对 / 没有可执行文件的情形）
            val check = c.detectCmd ?: "command -v ${c.probe}"
            "if $check >/dev/null 2>&1; then echo ${c.id}=1; else echo ${c.id}=0; fi"
        }
        val out = query(context, script, timeoutMs = 20_000)
        return components.associate { c ->
            c.id to Regex("(?m)^${Regex.escape(c.id)}=1\\s*$").containsMatchIn(out)
        }
    }

    // ─────────────────────────── 组件安装（在终端页的会话里跑） ───────────────────────────

    /**
     * 安装脚本：**一条命令一个元素**，执行时用 `&&` 串行（任一步失败即停，不会带着半成品继续）。
     *
     * 顺序与自愈口径是实测结论，别改：
     * ① 非交互（`DEBIAN_FRONTEND`）；② `dpkg --configure -a` 自愈上一次被中断的半配置状态
     * （App 被杀 / 用户取消后 apt 会一律 `E: dpkg was interrupted`）；③ 刷新索引；
     * ④ apt 单包一次装完；⑤ 自定义命令（NodeSource / npm 全局 / rustup）按清单顺序跟在后面。
     */
    fun commandsFor(components: List<UbuntuComponent>): List<String> {
        val aptComponents = components.filter { it.installCmd == null }
        val custom = components.filter { it.installCmd != null }
        val cmds = ArrayList<String>()
        cmds += "export DEBIAN_FRONTEND=noninteractive"
        cmds += "dpkg --configure -a >/dev/null 2>&1 || true"
        cmds += "apt-get update"
        if (aptComponents.isNotEmpty()) {
            cmds += "apt-get install -y --no-install-recommends " +
                aptComponents.joinToString(" ") { it.pkg }
        }
        // 自定义安装的顺序即清单顺序：node 在 pnpm/typescript 之前、pip 在 uv 之前
        custom.forEach { cmds += it.installCmd!! }
        return cmds
    }

    /** 终端里那行「这是干什么」的标题（命令行的样子，读起来像用户在终端里敲的） */
    fun labelFor(components: List<UbuntuComponent>): String =
        "环境配置 · 安装 ${components.size} 个组件（${components.joinToString(" · ") { it.name }}）"

    /**
     * 把选中组件交给**终端页的专用会话**去装：会话不存在就新建（`环境配置`），
     * 输出实时滚在终端里；本页只留一条「正在安装 · 去终端」的状态。
     *
     * @return 承载安装的会话（调用方据此切到终端页并选中这个会话）
     */
    fun installInTerminal(context: Context, components: List<UbuntuComponent>): TerminalSessions.Session {
        val session = TerminalSessions.sessionNamed(TerminalSessions.CONFIG_SESSION)
            ?: TerminalSessions.newSession(context, TerminalSessions.CONFIG_SESSION)
        if (components.isEmpty() || running) return session
        running = true
        step = "安装 ${components.size} 个组件"
        lastExitCode = null
        TerminalSessions.runScript(session, labelFor(components), commandsFor(components)) { code ->
            running = false
            step = ""
            lastExitCode = code
            Log.i(TAG, "环境配置脚本结束，退出码 $code")
        }
        return session
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
}
