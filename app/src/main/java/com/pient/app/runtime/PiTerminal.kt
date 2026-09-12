package com.pient.app.runtime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.pient.app.data.MockTerminal
import com.pient.app.data.TerminalLine
import com.pient.app.data.TerminalLineKind
import java.util.concurrent.TimeUnit

/**
 * 终端页的真实会话引擎（2026-09-12 起，替代原 `MockTerminal.run()` 的假输出）。
 *
 * 一个会话 = 一个常驻子进程：`<native lib>/libpient_shell.so`（随包分发的包装脚本）→ PRoot →
 * rootfs 里的 GNU bash，stdin/stdout 走管道。因为是**同一个 shell 进程**，`cd` / `export` 在
 * 会话内保持；命令逐行写 stdin，输出逐行回流到 UI。
 *
 * 已知边界（无 PTY，如实说明）：交互式程序（vim / top / apt 的 TUI）与行编辑、Tab 补全不可用；
 * Ctrl+C 也发不出 SIGINT —— [interrupt] 的实现是「结束并重建该会话进程」（cwd 回到 ~）。要真终端需上 PTY。
 */
object PiTerminal {
    private const val TAG = "PiTerminal"
    private const val MAX_LINES = 2000
    private const val INIT_CMD = """cd ~; . /etc/os-release; echo "${'$'}PRETTY_NAME · ${'$'}(uname -sr)""""

    class Session(val id: Int, val name: String) {
        val lines = mutableStateListOf<TerminalLine>()
        @Volatile var process: Process? = null
        @Volatile var alive: Boolean = false
        /** 跨 read 的半行缓冲（管道 read 会把行截断） */
        val pending = StringBuilder()
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

    /** 新建会话：横幅 + 一条真实的环境自检命令（首屏输出即证明连到了 rootfs） */
    @Synchronized
    fun newSession(context: Context): Session {
        appContext = context.applicationContext
        counter += 1
        val s = Session(counter, "会话$counter")
        s.lines.addAll(MockTerminal.banner)
        sessions += s
        start(context, s)
        write(s, INIT_CMD)
        return s
    }

    /** 关闭会话（结束进程并移除） */
    @Synchronized
    fun close(session: Session) {
        runCatching { session.process?.destroy() }
        session.process = null
        session.alive = false
        sessions.remove(session)
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
            append(session, TerminalLine("会话进程已退出，正在重建…", TerminalLineKind.OUTPUT))
            synchronized(this) { start(ctx, session) }
        }
        val p = session.process ?: return
        runCatching {
            p.outputStream.write((command + "\n").toByteArray(Charsets.UTF_8))
            p.outputStream.flush()
        }.onFailure { append(session, TerminalLine("写入失败：${it.message}", TerminalLineKind.OUTPUT)) }
    }

    /**
     * 跑一条命令并取回输出（环境配置页自检用，不建会话）。
     * 超时硬杀——探针命令卡住时不能把调用方一起拖住。
     */
    fun execOnce(context: Context, command: String, timeoutMs: Long = 8000): String {
        val shell = PiRuntime.shellPath(context)
        if (!shell.isFile) return ""
        return runCatching {
            val p = ProcessBuilder(shell.absolutePath, "-c", command)
                .redirectErrorStream(true)
                .also { it.environment().putAll(PiRuntime.environment(context)) }
                .start()
            val text = StringBuilder()
            val reader = Thread {
                runCatching { p.inputStream.bufferedReader().forEachLine { text.append(it).append('\n') } }
            }
            reader.isDaemon = true
            reader.start()
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                runCatching { p.destroy() }
                return@runCatching "(探针超时)"
            }
            reader.join(1000)
            text.toString().trim()
        }.getOrDefault("")
    }

    // ─────────────────────────── 进程与输出 ───────────────────────────

    @Synchronized
    private fun start(context: Context, session: Session) {
        val shell = PiRuntime.shellPath(context)
        if (!shell.isFile) {
            append(session, TerminalLine("终端运行时缺失：${shell.absolutePath}", TerminalLineKind.OUTPUT))
            return
        }
        val proc = runCatching {
            ProcessBuilder(shell.absolutePath)
                .redirectErrorStream(true)
                .also { it.environment().putAll(PiRuntime.environment(context)) }
                .start()
        }.getOrElse {
            append(session, TerminalLine("会话启动失败：${it.message}", TerminalLineKind.OUTPUT))
            return
        }
        session.process = proc
        session.alive = true
        session.pending.setLength(0)
        Log.i(TAG, "会话${session.id} 启动 shell=${shell.name}")
        Thread({ pump(session, proc) }, "pient-term-${session.id}")
            .apply { isDaemon = true }
            .start()
    }

    private fun pump(session: Session, proc: Process) {
        runCatching {
            val buf = ByteArray(4096)
            val input = proc.inputStream
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                if (n == 0) continue
                val text = String(buf, 0, n, Charsets.UTF_8)
                main.post { feed(session, text) }
            }
        }.onFailure { Log.w(TAG, "读取会话${session.id} 输出失败：${it.message}") }
        val code = runCatching { proc.waitFor() }.getOrDefault(-1)
        session.alive = false
        main.post {
            flushPending(session)
            appendDirect(session, TerminalLine("[会话进程已退出，退出码 $code]", TerminalLineKind.OUTPUT))
        }
        Log.i(TAG, "会话${session.id} 退出 code=$code")
    }

    /** 把一段输出按行喂进会话（余下不足一行的部分留在 pending） */
    private fun feed(session: Session, text: String) {
        session.pending.append(text)
        var idx = session.pending.indexOf("\n")
        while (idx >= 0) {
            val line = session.pending.substring(0, idx).trimEnd('\r')
            session.pending.delete(0, idx + 1)
            appendDirect(session, TerminalLine(line, TerminalLineKind.OUTPUT))
            idx = session.pending.indexOf("\n")
        }
    }

    private fun flushPending(session: Session) {
        if (session.pending.isNotEmpty()) {
            appendDirect(session, TerminalLine(session.pending.toString().trimEnd('\r'), TerminalLineKind.OUTPUT))
            session.pending.setLength(0)
        }
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
