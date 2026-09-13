package com.pient.app.runtime

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.os.IBinder
import android.util.Log
import com.pient.app.IPientShellService
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Shizuku「用户服务」客户端（选型 B，2026-09-13）—— 把 [PientShellService] 绑起来，拿到 ADB 级执行器。
 *
 * 流程（照 Shizuku 官方 demo）：
 * ```
 * Shizuku.bindUserService(UserServiceArgs(ComponentName(包名, 服务类名)).daemon(false)
 *     .processNameSuffix("pient_shell").version(versionCode).debuggable(isDebug), connection)
 *   → onServiceConnected(binder) → IPientShellService.Stub.asInterface(binder)
 * ```
 * 注意：绑定的**不是 manifest 组件**，服务类由守护进程反射实例化（见 [PientShellService]）。
 * 需要 Shizuku API ≥ 10（`Shizuku.getVersion()`）；未授权/服务未运行时绑定会失败或超时，
 * 调用方（[PiExecServer]）据此把可读原因回给模型。
 *
 * 生命周期：绑定成功后常驻（守护进程侧 daemon=false，连接断开时守护进程会回收）；
 * 设备重启 / Shizuku 重启 / 应用更新后连接会失效 —— [ensureBound] 每次都会 ping 一遍并按需重绑。
 */
object ShizukuShellChannel {

    private const val TAG = "PiHost"
    private const val MIN_API = 10

    @Volatile
    private var service: IPientShellService? = null

    @Volatile
    var bound: Boolean = false
        private set

    /** 最近一次失败原因（给模型/页面看的人类可读文本） */
    @Volatile
    var lastError: String? = null
        private set

    private var args: Shizuku.UserServiceArgs? = null
    private var connection: ServiceConnection? = null
    private var latch = CountDownLatch(1)

    /** 已绑且通道还活着 */
    private fun alive(): Boolean = runCatching {
        service?.asBinder()?.pingBinder() == true
    }.getOrDefault(false)

    /**
     * 确保绑定可用；返回是否可执行。绑定是异步的，这里最多等 [timeoutMs]。
     * 失败时把原因写进 [lastError]（例如「Shizuku 服务未运行」「需要 Shizuku API ≥ 10」「绑定超时」）。
     */
    @Synchronized
    fun ensureBound(context: Context, timeoutMs: Long = 6000): Boolean {
        if (alive()) return true
        bound = false
        service = null
        lastError = null

        val api = runCatching { Shizuku.getVersion() }.getOrDefault(-1)
        when {
            api < 0 -> {
                lastError = "Shizuku 服务未运行（需先启动 Shizuku / 设备重启后重新激活）"
                return false
            }
            api < MIN_API -> {
                lastError = "需要 Shizuku API ≥ $MIN_API（当前 $api）"
                return false
            }
            runCatching { Shizuku.checkSelfPermission() }.getOrDefault(-1) != 0 -> {
                lastError = "Pient 尚未获得 Shizuku 授权"
                return false
            }
        }

        val appCtx = context.applicationContext
        val debug = (appCtx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        // version：给 Shizuku 用（版本变了会重启用户服务进程）——取本包 versionCode
        @Suppress("DEPRECATION")
        val versionCode = runCatching {
            appCtx.packageManager.getPackageInfo(appCtx.packageName, 0).versionCode
        }.getOrDefault(1)
        val a = args ?: Shizuku.UserServiceArgs(
            ComponentName(appCtx.packageName, PientShellService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("pient_shell")
            .version(versionCode)
            .debuggable(debug)
            .also { args = it }

        val c = connection ?: object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val iface = runCatching { IPientShellService.Stub.asInterface(binder) }.getOrNull()
                if (iface != null && runCatching { binder?.pingBinder() == true }.getOrDefault(false)) {
                    service = iface
                    bound = true
                    lastError = null
                    Log.i(TAG, "Shizuku 用户服务已连接（写段 uid=2000 / ADB 级）")
                } else {
                    lastError = "用户服务返回了无效 binder"
                }
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                bound = false
                Log.i(TAG, "Shizuku 用户服务已断开")
            }
        }.also { connection = it }

        latch = CountDownLatch(1)
        return runCatching {
            Shizuku.bindUserService(a, c)
        }.getOrElse {
            lastError = "绑定用户服务失败：${it.message}"
            return false
        }.let {
            val ok = latch.await(timeoutMs, TimeUnit.MILLISECONDS) && alive()
            if (!ok) lastError = lastError ?: "绑定用户服务超时（${timeoutMs}ms）"
            ok
        }
    }

    /** 执行命令；未就绪返回 null（调用方读 [lastError] 或自行回退其它通道） */
    fun exec(command: String, cwd: String?, timeoutMs: Long): JSONObject? {
        val s = service ?: return null
        return runCatching { JSONObject(s.exec(command, cwd, timeoutMs)) }
            .onFailure { lastError = "用户服务调用失败：${it.message}" }
            .getOrNull()
    }

    /** 主动解绑（验证/诊断用） */
    @Synchronized
    fun unbind() {
        val a = args ?: return
        val c = connection ?: return
        runCatching { Shizuku.unbindUserService(a, c, true) }
        service = null
        bound = false
    }
}
