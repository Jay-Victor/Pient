package com.pient.app.runtime

import android.system.Os
import android.util.Log
import com.pient.app.IPientShellService
import com.pient.app.data.ToolPolicy
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Shizuku「用户服务」实现（选型 B，2026-09-13）—— **系统命令的 ADB 级执行器**。
 *
 * 运行位置（关键，别按普通 Service 理解）：**本类不是 manifest 组件**，它由 **Shizuku 守护进程
 * 反射实例化、跑在守护进程里**，进程 uid = `shell`(2000)、SELinux 域 = `shell`——
 * 也就是说这里 `Runtime.exec("am …")` 拿到的就是 ADB 级权限（无需 Root）。
 * 依据：Shizuku 官方 demo `demo/src/main/java/rikka/shizuku/demo/service/UserService.java`
 * （`extends IUserService.Stub`、无参构造、`destroy()` = System.exit(0)）。
 *
 * 客户端绑定见 [ShizukuShellChannel]；本类只负责「执行命令 + 回 JSON」。
 * 输出与 App 侧回桥（[PiExecServer]）约定的字段一致：`{channel, uid, code, output}` / `{error, message}`。
 */
class PientShellService : IPientShellService.Stub() {

    /** 实例化即打点（守护进程反射构造；顺带把身份打进日志便于取证） */
    init {
        Log.i(TAG, "用户服务已实例化：pid=${Os.getpid()} uid=${Os.getuid()}（2000 = shell / ADB 级）")
    }

    /** Shizuku 保留事务号：请求销毁（官方 demo 同款，直接退进程） */
    override fun destroy() {
        Log.i(TAG, "收到 destroy，退出用户服务进程")
        exitProcess(0)
    }

    override fun exec(command: String?, cwd: String?, timeoutMs: Long): String {
        val cmd = command?.trim().orEmpty()
        if (cmd.isEmpty()) return err("bad-request", "缺少 command")

        val workdir = cwd?.takeIf { it.isNotBlank() }
        val limit = if (timeoutMs in 1..600_000) timeoutMs else 120_000
        return runCatching {
            val pb = ProcessBuilder("/system/bin/sh", "-c", cmd).redirectErrorStream(true)
            if (workdir != null) pb.directory(java.io.File(workdir))
            val p = pb.start()
            val text = StringBuilder()
            val reader = Thread {
                runCatching { p.inputStream.bufferedReader().forEachLine { text.append(it).append('\n') } }
            }
            reader.isDaemon = true
            reader.start()
            if (!p.waitFor(limit, TimeUnit.MILLISECONDS)) {
                runCatching { p.destroy() }
                return@runCatching err("timeout", "命令超时（${limit}ms）")
            }
            reader.join(1000)
            val out = text.toString().let { if (it.length > 200_000) it.takeLast(200_000) else it }
            JSONObject()
                .put("channel", "shizuku")
                .put("uid", Os.getuid())
                .put("code", p.exitValue())
                .put("output", out.trimEnd())
                .toString()
        }.getOrElse { err("exec-failed", it.message ?: it.javaClass.simpleName) }
    }

    private fun err(code: String, message: String): String =
        JSONObject().put("error", code).put("message", message).toString()

    private companion object {
        const val TAG = "PiHost"
    }
}
