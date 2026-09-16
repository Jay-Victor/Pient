package com.pient.app.runtime

import com.pient.app.data.i18n.L
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * **在 Ubuntu（guest）里跑一条一次性命令**并取回 `(退出码, 输出)`。
 *
 * 与终端页的会话进程是同一套环境（同一个 rootfs、同一个 HOME=/root、同一个 /workspace），
 * 但**强制 Ubuntu（PRoot）** —— 与用户当前的「执行环境」选择解耦（见 [PiRuntime.guestEnv]）。
 * 应用内部的控制类命令（`pi list` / `npx skills …` / 探针）都走这里：
 * 它们要的是「pi 与 node 在的那棵 rootfs」，不是用户给终端页选的那个落点。
 *
 * 输出截断保护：命令输出超过 [maxChars] 时只保留尾部（报错通常在最末几行）。
 */
object GuestExec {
    private const val TAG = "PientGuestExec"

    suspend fun run(
        context: Context,
        command: String,
        timeoutMs: Long = 60_000,
        maxChars: Int = 200_000,
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val shell = PiRuntime.shellPath(context)
        if (!shell.isFile) return@withContext -1 to L.runtime.launcherMissing
        runCatching {
            val proc = ProcessBuilder(shell.absolutePath, "-c", command)
                .redirectErrorStream(true)
                .also { it.environment().putAll(PiRuntime.guestEnv(context)) }
                .start()
            val out = StringBuilder()
            val reader = Thread {
                runCatching { proc.inputStream.bufferedReader().forEachLine { out.append(it).append('\n') } }
            }
            reader.isDaemon = true
            reader.start()
            val finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                runCatching { proc.destroy() }
                return@runCatching -1 to (tail(out, maxChars) + L.runtime.timeoutSuffix(timeoutMs / 1000))
            }
            reader.join(1200)
            proc.exitValue() to tail(out, maxChars)
        }.getOrElse {
            Log.w(TAG, "guest 命令执行失败：${it.message}")
            -1 to (it.message ?: L.runtime.commandFailed)
        }
    }

    private fun tail(sb: StringBuilder, maxChars: Int): String {
        val text = sb.toString()
        return if (text.length <= maxChars) text else L.runtime.outputHeadOmitted + text.takeLast(maxChars)
    }

    /** 去掉 ANSI 颜色转义（pi 的 CLI 在有 TTY 时会上色；我们已经是 NO_COLOR，这里再兜一层） */
    fun stripAnsi(s: String): String = s.replace(Regex("\u001B\\[[0-9;]*[A-Za-z]"), "")
}
