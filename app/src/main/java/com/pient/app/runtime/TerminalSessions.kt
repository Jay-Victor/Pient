package com.pient.app.runtime

import com.pient.app.data.i18n.L
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.pient.app.data.PientLog
import androidx.compose.runtime.mutableStateListOf
import com.pient.app.data.ExecEnv
import com.pient.app.data.MockTerminal
import com.pient.app.data.SettingsStore
import com.pient.app.data.TerminalLine
import com.pient.app.data.TerminalLineKind
import java.util.concurrent.TimeUnit

/**
 * **终端会话引擎** —— 一个会话 = 一个常驻子进程：
 * `<native lib>/libpient_shell.so`（随包分发的包装脚本）→ PRoot（或 su+chroot）→ rootfs 里的 GNU bash，
 * stdin/stdout 走管道。终端页（用户）与「环境配置」的安装脚本共用这一批进程。
 *
 * 因为是**同一个 shell 进程**，`cd` / `export` 在会话内保持；命令逐行写 stdin，输出逐行回流到界面。
 *
 * 执行环境（`ubuntu` / `ubuntu-chroot`）由包装脚本**每次被执行时现读** `files/pient-rt/exec_env`
 * 决定 —— 改完下一条命令/新会话即生效，已在跑的会话进程不换环境。
 *
 * **Android shell 不在这条链上**：它是**另一条独立通道** ——
 * Shizuku / Root 把命令直接扔给 Android 系统执行，不经过 terminal、不经过 Ubuntu，
 * 且**即发即走、没有会话**（`runtime/AndroidShell.kt` + `runtime/ExecBridge.kt`）。
 *
 * 已知边界（无 PTY，如实说明）：交互式程序（vim / top / apt 的 TUI）与行编辑、Tab 补全不可用；
 * Ctrl+C 也发不出 SIGINT —— [interrupt] 的实现是「结束并重建该会话进程」（cwd 回到 ~）。要真终端需上 PTY。
 */
object TerminalSessions {
    private const val TAG = "PiTerminal"
    private const val MAX_LINES = 2000
    private const val INIT_CMD = """cd ~; . /etc/os-release; echo "${'$'}PRETTY_NAME · ${'$'}(uname -sr)""""

    /** 「环境配置」页的专用会话名（计算属性：object 里的 val 只求值一次，写 `L.…` 会冻结成首帧语言） */
    val CONFIG_SESSION: String get() = L.common.environmentConfig

    /** 脚本结束哨兵：只有 `echo` 出来的这一行会被拦下（不显示），用来判定安装结束与退出码 */
    private const val SENTINEL = "__PIENT_DONE__"

    class Session(val id: Int, val name: String, /**
         * 执行环境覆盖（`PIENT_EXEC_ENV`）：null = 跟随用户在「环境配置」页选的那个。
         * 应用**内部**的会话（环境配置安装、pi 包管理、技能市场）固定 `"ubuntu"` ——
         * 它们要的是「node 与 pi 在的那棵 rootfs」，跟着用户把终端页切到 Android shell 会整体失效。
         */
        val execEnvOverride: String? = null,
        /**
         * AI 执行镜像会话：**不绑进程**，只用来收 pi 工具事件的镜像行。
         * 它不参与前台保活（保活在 [start] 里挂），也不影响用户自己的会话。
         */
        val aiMirror: Boolean = false) {
        val lines = mutableStateListOf<TerminalLine>()
        @Volatile var process: ShellProcess? = null
        @Volatile var alive: Boolean = false

        /** 跨 read 的半行缓冲（管道 read 会把行截断） */
        val pending = StringBuilder()

        /** 脚本模式：结束哨兵到达时的回调（参数 = 退出码）；null = 当前没有脚本在跑 */
        var scriptCallback: ((Int) -> Unit)? = null
    }

    val sessions = mutableStateListOf<Session>()
    private val main = Handler(Looper.getMainLooper())
    private var counter = 0
    private var appContext: Context? = null

    /** 首次进入终端页时建首个会话（幂等） */
    fun ensure(context: Context) {
        appContext = context.applicationContext
        synchronized(this) { if (sessions.isEmpty()) newSession(context) }
    }

    /** 按名字找会话（「环境配置」复用同一个，不重复建） */
    fun sessionNamed(name: String): Session? = sessions.firstOrNull { it.name == name }

    /** AI 执行镜像会话名（终端页里那一栏） */
    val AI_MIRROR_NAME: String get() = L.runtime.aiMirrorSession

    /**
     * 把 pi 的**工具执行**镜像进终端页。
     *
     * 为什么需要：pi 的 `bash` 工具是**它自己 spawn 的一次性进程**（`spawn(shellPath, ["-c", cmd])`），
     * 与终端页这些常驻会话没有任何关系 —— 所以 AI 在 Ubuntu 里干活时，终端页里什么都看不到。
     *
     * 形态：一个**只读镜像会话**（`aiMirror = true`，不绑进程、不占前台保活），
     * 工具每跑一步就往里追一行；用户自己的命令仍在自己的会话里。
     */
    @Synchronized
    fun mirror(context: Context, line: String) {
        if (line.isBlank()) return
        appContext = context.applicationContext
        val s = sessions.firstOrNull { it.aiMirror } ?: Session(
            id = counter + 1,
            name = AI_MIRROR_NAME,
            aiMirror = true,
        ).also {
            counter += 1
            it.lines += TerminalLine(
                L.runtime.aiMirrorBanner,
                TerminalLineKind.COMMAND,
            )
            sessions += it
        }
        append(s, TerminalLine(line, TerminalLineKind.COMMAND))
    }

    /** 新建会话：横幅 + 一条真实的环境自检命令（首屏输出即证明连到了目标环境） */
    @Synchronized
    fun newSession(context: Context, name: String? = null, execEnvOverride: String? = null): Session {
        appContext = context.applicationContext
        counter += 1
        val s = Session(counter, name ?: L.runtime.sessionName(counter), execEnvOverride)
        s.lines.addAll(MockTerminal.banner)
        sessions += s
        start(context, s)
        write(s, initCommand(s))
        return s
    }

    /** 首屏自检命令（**terminal 恒为 Ubuntu**：Android shell 是独立通道，不在这里跑） */
    private fun initCommand(session: Session): String = INIT_CMD

    /**
     * 在会话里跑一段脚本（「环境配置 → 安装所选」走这条）：
     * 命令用 `&&` 串成一条（**任一步失败即停**），末尾发一条哨兵行判定结束与退出码；
     * 输出照常流进终端（[onFinish] 在主线程回调）。
     */
    fun runScript(session: Session, label: String, commands: List<String>, onFinish: (Int) -> Unit) {
        if (commands.isEmpty()) {
            onFinish(0)
            return
        }
        session.scriptCallback = onFinish
        append(session, TerminalLine("~ \$$label", TerminalLineKind.COMMAND))
        val body = commands.joinToString(" && ") { "{ $it; }" } + "\necho $SENTINEL\$?"
        write(session, body)
    }

    /**
     * 前台保活的 key（一个会话一个）。
     *
     * **会话活着就挂着** —— 无 PTY 时应用无从得知「用户手打的那条命令」
     * 何时结束（只有 app 自己发起的脚本能靠哨兵拿到退出码），而系统清进程不区分命令来源。
     * 代价 = 终端页有会话时通知栏会有一条常驻通知（IMPORTANCE_LOW，不响不震）。
     */
    private fun keepAliveKey(session: Session): String = "term:${session.id}"

    /** 关闭会话（结束进程并移除） */
    @Synchronized
    fun close(session: Session) {
        runCatching { session.process?.destroy() }
        session.process = null
        session.alive = false
        sessions.remove(session)
        // 会话没了 → 释放它占的保活（pump 那条路径会因为 process 已置空而跳过，所以这里必须显式放）
        appContext?.let { PiKeepAlive.release(it, keepAliveKey(session)) }
    }

    /** 中断当前命令：无 PTY 发不了 SIGINT → 结束进程重建（会话与已输出内容保留） */
    fun interrupt(context: Context, session: Session) {
        append(session, TerminalLine("^C", TerminalLineKind.COMMAND))
        synchronized(this) {
            runCatching { session.process?.destroy() }
            start(context, session)
        }
    }

    /** 写一行命令（进程已退出时自动重建后再写） */
    fun write(session: Session, command: String) {
        if (!session.alive || session.process == null) {
            val ctx = appContext ?: return
            append(session, TerminalLine(L.runtime.sessionProcessRestarting, TerminalLineKind.OUTPUT))
            synchronized(this) { start(ctx, session) }
        }
        val p = session.process ?: return
        runCatching {
            p.stdin.write((command + "\n").toByteArray(Charsets.UTF_8))
            p.stdin.flush()
        }.onFailure { append(session, TerminalLine(L.runtime.writeFailedDetail(it.message), TerminalLineKind.OUTPUT)) }
    }

    /**
     * 跑一条命令并取回输出（环境页自检用，不建会话）。
     * 超时硬杀——探针命令卡住时不能把调用方一起拖住。
     */
    fun execOnce(context: Context, command: String, timeoutMs: Long = 8000): String {
        val shell = PiRuntime.shellPath(context)
        if (!shell.isFile) return ""
        return runCatching {
            val p = ProcessBuilder(shell.absolutePath, "-c", command)
                .redirectErrorStream(true)
                // 探针类调用固定 Ubuntu（不受 exec_env 选择影响；见 PiRuntime.guestEnv）
                .also { it.environment().putAll(PiRuntime.guestEnv(context)) }
                .start()
            val text = StringBuilder()
            val reader = Thread {
                runCatching { p.inputStream.bufferedReader().forEachLine { text.append(it).append('\n') } }
            }
            reader.isDaemon = true
            reader.start()
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                runCatching { p.destroy() }
                return@runCatching L.runtime.probeTimeout
            }
            reader.join(1000)
            text.toString().trim()
        }.getOrDefault("")
    }

    // ─────────────────────────── 进程与输出 ───────────────────────────

    @Synchronized
    private fun start(context: Context, session: Session) {
        PiRuntime.prepareTerminal(context)   // DNS / root 启动器 / 执行环境，会话启动时对齐
        val effectiveEnv = session.execEnvOverride ?: SettingsStore.execEnv.id

        // 终端页 = **proot Ubuntu**（装工具链、跑脚本、AI 的 bash 工具都在这；chroot 是它的 Root 形态）。
        // 注：**Android shell 不走这里** —— 它是另一条独立通道（Shizuku / Root 直接把命令扔给系统执行、
        // 即发即走、没有会话），实现在 runtime/AndroidShell.kt + ExecBridge.kt。
        if (!PiRuntime.rootfsReady(context)) {
            // **不需要用户手动点解包** —— 首次起会话自动解包并把进度回显到终端。
            if (PiRuntime.isUnpacking()) {
                append(session, TerminalLine(L.runtime.rootfsUnpackingTask, TerminalLineKind.OUTPUT))
                return
            }
            if (!PiRuntime.rootfsArchiveAvailable(context)) {
                append(
                    session,
                    TerminalLine(
                        L.runtime.apkNoRootfsArchive,
                        TerminalLineKind.OUTPUT,
                    ),
                )
                return
            }
            append(session, TerminalLine(L.runtime.rootfsAutoUnpacking, TerminalLineKind.OUTPUT))
            Thread {
                val ok = runCatching {
                    PiRuntime.extractRootfs(context) { pct, text ->
                        append(session, TerminalLine("[${(pct * 100).toInt()}%] $text", TerminalLineKind.OUTPUT))
                    }
                }.getOrDefault(false)
                if (ok) {
                    append(session, TerminalLine(L.runtime.unpackDoneStartSession, TerminalLineKind.OUTPUT))
                    start(context, session)   // 这次 rootfsReady=true，正常往下走
                    write(session, INIT_CMD)
                } else {
                    append(session, TerminalLine(L.runtime.autoUnpackFailed, TerminalLineKind.OUTPUT))
                }
            }.apply {
                isDaemon = true
                name = "pient-rootfs-unpack"
            }.start()
            return
        }
        val shell = PiRuntime.shellPath(context)
        if (!shell.isFile) {
            append(session, TerminalLine(L.runtime.terminalRuntimeMissing(shell.absolutePath), TerminalLineKind.OUTPUT))
            return
        }
        val proc = runCatching {
            ProcessBuilder(shell.absolutePath)
                .redirectErrorStream(true)
                .also { pb ->
                    pb.environment().putAll(PiRuntime.environment(context))
                    // 应用内部会话固定 Ubuntu（见 Session.execEnvOverride）：包装脚本读它优先于 exec_env 文件
                    session.execEnvOverride?.let { pb.environment()["PIENT_EXEC_ENV"] = it }
                }
                .start()
        }.getOrElse {
            append(session, TerminalLine(L.runtime.sessionStartFailed(it.message), TerminalLineKind.OUTPUT))
            return
        }
        // 本地进程统一包成 ShellProcess（与 Shizuku 远端进程同一接口；pump/write 只认它）
        val wrapped = LocalShellProcess(
            proc,
            when (effectiveEnv) {
                ExecEnv.UBUNTU_CHROOT.id -> "Ubuntu(chroot)"
                else -> "Ubuntu(PRoot)"
            },
        )
        session.process = wrapped
        session.alive = true
        session.pending.setLength(0)
        PientLog.i(TAG, "会话${session.id} 启动 shell=${shell.name}")
        // 前台保活：**手打命令也算在跑** —— 无 PTY 时应用不知道用户敲的那条命令
        // 何时结束，只能按「会话活着」挂着（系统清进程是不区分命令来源的）；
        // 释放点 = 关会话（close）/ 进程真退出（pump 末尾，且只认当前这个进程）。
        appContext?.let { PiKeepAlive.acquire(it, keepAliveKey(session), L.runtime.terminalSessionRunning) }
        Thread({ pump(session, wrapped) }, "pient-term-${session.id}")
            .apply { isDaemon = true }
            .start()
    }

    private fun pump(session: Session, proc: ShellProcess) {
        runCatching {
            val buf = ByteArray(4096)
            val input = proc.stdout
            // 块边界可能正好切在一个 UTF-8 多字节字符中间（中文 3 字节），
            // 整块解码会把尾部的半个字符变成 U+FFFD：把不完整的尾巴留到下一块再解。
            var pending = ByteArray(0)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (n == 0) continue
                val bytes = if (pending.isEmpty()) buf.copyOf(n) else pending + buf.copyOf(n)
                val cut = utf8IncompleteSuffix(bytes)
                val text = String(bytes, 0, bytes.size - cut, Charsets.UTF_8)
                pending = if (cut == 0) ByteArray(0) else bytes.copyOfRange(bytes.size - cut, bytes.size)
                if (text.isNotEmpty()) main.post { feed(session, text) }
            }
            if (pending.isNotEmpty()) {
                val text = String(pending, Charsets.UTF_8)
                if (text.isNotEmpty()) main.post { feed(session, text) }
            }
        }.onFailure { PientLog.w(TAG, "读取会话${session.id} 输出失败：${it.message}") }
        val code = runCatching { proc.waitFor() }.getOrDefault(-1)
        session.alive = false
        // 进程真退出了 → 释放保活。**只认「还是当前这个进程」**：中断 = 销毁旧进程 + 立刻重建
        // （interrupt 先 destroy 再 start，start 里换上新的 process），旧 pump 线程收尾时
        // 不能把新会话的保活一起关掉。
        if (session.process === proc) appContext?.let { PiKeepAlive.release(it, keepAliveKey(session)) }
        main.post {
            flushPending(session)
            appendDirect(session, TerminalLine(L.runtime.sessionExited(code), TerminalLineKind.OUTPUT))
        }
        PientLog.i(TAG, "会话${session.id} 退出 code=$code")
    }

    /** 把一段输出按行喂进会话（余下不足一行的部分留在 pending） */
    private fun feed(session: Session, text: String) {
        session.pending.append(text)
        var idx = session.pending.indexOf("\n")
        while (idx >= 0) {
            val line = session.pending.substring(0, idx).trimEnd('\r')
            session.pending.delete(0, idx + 1)
            onOutputLine(session, line)
            idx = session.pending.indexOf("\n")
        }
    }

    private fun flushPending(session: Session) {
        if (session.pending.isNotEmpty()) {
            onOutputLine(session, session.pending.toString().trimEnd('\r'))
            session.pending.setLength(0)
        }
    }

    /**
     * 一行输出的落点：**脚本哨兵行被吃掉**（不显示，用来收尾安装脚本），其余原样进界面。
     * 收尾行由这里补一条「完成/失败」，让终端里读得懂这次安装的结果。
     */
    /**
     * 末尾「不完整的 UTF-8 序列」占几个字节（0 = 这块是完整的）。
     * 判定：从尾部往前走连续字节（0b10xxxxxx），再看它前面那个起始字节声明了几字节。
     */
    private fun utf8IncompleteSuffix(b: ByteArray): Int {
        var i = b.size - 1
        var cont = 0
        while (i >= 0 && cont < 3 && (b[i].toInt() and 0xC0) == 0x80) {
            i--
            cont++
        }
        if (i < 0) return 0                       // 整块都是连续字节（异常输入）：交给 REPLACE 处理
        val c = b[i].toInt() and 0xFF
        val need = when {
            c and 0x80 == 0 -> 1                  // ASCII
            c and 0xE0 == 0xC0 -> 2               // 110xxxxx
            c and 0xF0 == 0xE0 -> 3               // 1110xxxx
            c and 0xF8 == 0xF0 -> 4               // 11110xxx
            else -> 1                             // 非法起始字节：不是我们该留的
        }
        val have = cont + 1
        return if (have < need) have else 0
    }

    private fun onOutputLine(session: Session, line: String) {
        if (line.startsWith(SENTINEL)) {
            val code = line.removePrefix(SENTINEL).trim().toIntOrNull() ?: -1
            val cb = session.scriptCallback
            session.scriptCallback = null
            appendDirect(
                session,
                TerminalLine(
                    if (code == 0) L.runtime.installDone else L.runtime.installFailed(code),
                    TerminalLineKind.OUTPUT,
                ),
            )
            cb?.invoke(code)
            return
        }
        appendDirect(session, TerminalLine(line, TerminalLineKind.OUTPUT))
    }

    private fun append(session: Session, line: TerminalLine) {
        if (Looper.myLooper() == Looper.getMainLooper()) appendDirect(session, line)
        else main.post { appendDirect(session, line) }
    }

    /** 只在主线程调用（Compose 状态） */
    private fun appendDirect(session: Session, line: TerminalLine) {
        session.lines += line
        while (session.lines.size > MAX_LINES) session.lines.removeAt(0)
    }
}
