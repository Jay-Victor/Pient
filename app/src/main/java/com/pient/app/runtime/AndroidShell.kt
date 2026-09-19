package com.pient.app.runtime

import com.pient.app.data.i18n.L
import android.content.Context
import android.os.ParcelFileDescriptor
import com.pient.app.data.PientLog
import com.pient.app.data.PermissionTier
import com.pient.app.data.RootGateway
import com.pient.app.data.SettingsStore
import com.pient.app.data.ShizukuGateway
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * 一个可以长连的 shell 进程（终端页会话用它）—— 屏蔽「本地 Process」与「Shizuku 远端进程」的差别。
 *
 * 本地：包装脚本（PRoot / su+chroot / su+sh）→ `java.lang.Process`；
 * 远端：Shizuku `IShizukuService.newProcess` → `IRemoteProcess`（stdin/stdout 都是 ParcelFileDescriptor）。
 */
interface ShellProcess {
    /** 进程 stdin（写命令） */
    val stdin: OutputStream

    /** 进程 stdout（读输出；已与 stderr 合流由实现决定） */
    val stdout: InputStream

    /** 出错提示里显示的身份（`Ubuntu(PRoot)` / `Shizuku` / `Root`） */
    val label: String

    fun destroy()

    /** 等待退出并取退出码（阻塞） */
    fun waitFor(): Int

    fun alive(): Boolean
}

/** 本地进程适配（PRoot / su / chroot 都走它） */
class LocalShellProcess(private val p: Process, override val label: String) : ShellProcess {
    private val merged = p.inputStream      // 调用方启动时 redirectErrorStream(true)
    override val stdin: OutputStream get() = p.outputStream
    override val stdout: InputStream get() = merged
    override fun destroy() = runCatching { p.destroy() }.let { }
    override fun waitFor(): Int = runCatching { p.waitFor() }.getOrDefault(-1)
    override fun alive(): Boolean = runCatching { p.isAlive }.getOrDefault(false)
}

/**
 * Shizuku 远端进程适配。
 *
 * `IRemoteProcess` 的流是 ParcelFileDescriptor：`getInputStream()` = 子进程 stdout、
 * `getErrorStream()` = stderr、`getOutputStream()` = 子进程 **stdin**（这就是能当长连 shell 用的原因）。
 * 我们把 stderr 合流进 stdout（终端会话只需要一条输出）。
 */
class RemoteShellProcess(
    private val proc: IRemoteProcess,
    override val label: String,
) : ShellProcess {

    private val pfdIn: ParcelFileDescriptor? = runCatching { proc.inputStream }.getOrNull()
    private val pfdErr: ParcelFileDescriptor? = runCatching { proc.errorStream }.getOrNull()
    private val pfdOut: ParcelFileDescriptor? = runCatching { proc.outputStream }.getOrNull()

    override val stdin: OutputStream = FileOutputStreamCompat(pfdOut)

    override val stdout: InputStream = object : InputStream() {
        private val main = pfdIn?.let { FileInputStream(it.fileDescriptor) }
        private val err = pfdErr?.let { FileInputStream(it.fileDescriptor) }
        private val buf = ByteArray(1)

        /** 先读 stdout；它到 EOF 后再读 stderr（简单串行，长连场景够用） */
        override fun read(): Int {
            main?.let { m ->
                val n = m.read()
                if (n >= 0) return n
            }
            err?.let { e -> return e.read() }
            return -1
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            main?.let { m ->
                val n = m.read(b, off, len)
                if (n > 0) return n
            }
            err?.let { e -> return e.read(b, off, len) }
            return -1
        }

        override fun available(): Int = buf.size
    }

    override fun destroy() {
        runCatching { proc.destroy() }
        runCatching { pfdIn?.close() }
        runCatching { pfdErr?.close() }
        runCatching { pfdOut?.close() }
    }

    override fun waitFor(): Int = runCatching { proc.waitFor() }.getOrDefault(-1)

    override fun alive(): Boolean = runCatching { proc.alive() }.getOrDefault(false)
}

/** 把 ParcelFileDescriptor 包成 OutputStream（写子进程 stdin） */
private class FileOutputStreamCompat(private val pfd: ParcelFileDescriptor?) : OutputStream() {
    private val inner = pfd?.let { android.os.ParcelFileDescriptor.AutoCloseOutputStream(it) }

    override fun write(b: Int) {
        inner?.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        inner?.write(b, off, len)
    }

    override fun flush() {
        inner?.flush()
    }

    override fun close() {
        runCatching { inner?.close() }
    }
}

/**
 * **Android shell 通道**：
 * 让 AI（pi 的 `android_shell` 工具）与终端页能在 **Android 系统里**执行命令 ——
 * 这是 Ubuntu(PRoot) 做不到的部分：`pm`/`am`/`cmd`/`dumpsys` 等系统命令、装应用、改系统设置、
 * 读别的 app 私有数据（`/data/data`，需要 Root）、操作硬件。root 档下还能把 Ubuntu 从
 * PRoot 升级成 chroot（见 `pient-shell.sh` 与 `pient-root-wrapper.sh`）。
 *
 * 两条通道（档位决定，与「执行环境」是两条轴）：
 *  - **Shizuku**（调试权限）：`IShizukuService.newProcess` 以 **shell（ADB）身份**跑命令，
 *    这个 binder 只在 Java 侧可达 —— 所以工具侧走 [ExecBridge] 回桥（guest → 127.0.0.1 → App）。
 *  - **Root**（Root 权限）：`su -c`，uid 0。
 *
 * 标准档一律不可用（返回 NONE + 人话说明），这是产品口径：没有特权通道时 AI 不该以为能碰系统。
 */
object AndroidShell {
    private const val TAG = "PientAndroidShell"

    enum class Backend(val id: String) {
        NONE("none"),
        SHIZUKU("shizuku"),
        ROOT("root");

        /** 显示名（计算属性：枚举构造参数只求值一次，写 `L.…` 会冻结成首帧语言） */
        val label: String get() = when (this) {
            NONE -> L.runtime.shellBackendNone
            SHIZUKU -> L.runtime.shellBackendShizuku
            ROOT -> "Root（su）"
        }
    }

    data class Result(
        val backend: Backend,
        val exit: Int,
        val stdout: String,
        val stderr: String,
        val timeout: Boolean = false,
        val note: String = "",
    ) {
        val ok: Boolean get() = note.isEmpty() && !timeout && exit == 0
    }

    // ───────────────────────── 能力判定 ─────────────────────────

    /** Shizuku 服务接口（未运行 / 未授权 / 服务端版本不兼容时返回 null） */
    fun shizukuService(): IShizukuService? = runCatching {
        if (!ShizukuGateway.running()) return null
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        val binder = Shizuku.getBinder() ?: return null
        if (!binder.isBinderAlive) return null
        IShizukuService.Stub.asInterface(binder)
    }.getOrElse {
        PientLog.w(TAG, "取 Shizuku 服务失败：${it.message}")
        null
    }

    /** su 是否可用（Root 管理器的轻量检测；真执行时再看结果） */
    fun rootAvailable(context: Context): Boolean = RootGateway.granted || RootGateway.deviceRooted(context)

    /** 当前档位下的通道（**按档位门控**：标准档即使装了 Shizuku 也不走） */
    fun backend(context: Context): Backend = when (SettingsStore.permissionTier) {
        PermissionTier.DEBUGGER -> if (shizukuService() != null) Backend.SHIZUKU else Backend.NONE
        PermissionTier.ROOT -> if (rootAvailable(context)) Backend.ROOT else Backend.NONE
        PermissionTier.STANDARD -> Backend.NONE
    }

    fun available(context: Context): Boolean = backend(context) != Backend.NONE

    /** 一句人话：为什么不可用 / 现在走哪条通道（UI 与工具的错误文案唯一出处） */
    fun statusText(context: Context): String = when (SettingsStore.permissionTier) {
        PermissionTier.STANDARD ->
            L.runtime.shellNeedsPrivilege +
                L.runtime.shellSwitchTierHint
        PermissionTier.DEBUGGER -> when {
            !ShizukuGateway.installed(context) -> L.runtime.shellNoShizukuApp
            !ShizukuGateway.running() -> L.runtime.shellShizukuNotRunning
            !ShizukuGateway.authorized() -> L.runtime.shellShizukuUnauthorized
            else -> L.runtime.shellReadyShizuku
        }
        PermissionTier.ROOT -> if (rootAvailable(context)) {
            L.runtime.shellReadyRoot
        } else {
            L.runtime.shellNoSu
        }
    }

    // ───────────────────────── 执行 ─────────────────────────

    /**
     * 执行一条 Android 命令并返回结果 —— **即发即走**：
     * 每次调用都是**独立的一次执行**，没有会话、不保留 `cd` / `export` 之类的状态；
     * 命令里需要先切目录/设变量就在同一条命令里写（`cd /sdcard && ls`）。
     * 命令走 `sh -c`，所以 `;` `&&` 管道重定向都合法。
     */
    fun execBlocking(context: Context, command: String, timeoutMs: Long = 30_000): Result {
        val b = backend(context)
        if (b == Backend.NONE) return Result(b, -1, "", "", note = statusText(context))
        return when (b) {
            Backend.ROOT -> {
                val p = runCatching {
                    ProcessBuilder("su", "-c", command)
                        .redirectErrorStream(false)
                        .start()
                }.getOrElse { return Result(b, -1, "", "", note = L.runtime.suStartFailed(it.message)) }
                drain(LocalShellProcess(p, "Root"), timeoutMs, b)
            }
            Backend.SHIZUKU -> {
                val svc = shizukuService()
                    ?: return Result(b, -1, "", "", note = statusText(context))
                val proc = runCatching { svc.newProcess(arrayOf("sh", "-c", command), null, null) }
                    .getOrNull()
                    ?: return Result(b, -1, "", "", note = L.runtime.shizukuSpawnFailed)
                drain(RemoteShellProcess(proc, "Shizuku"), timeoutMs, b)
            }
            Backend.NONE -> Result(b, -1, "", "", note = statusText(context))
        }
    }

    /** 读干一个进程（超时则 destroy 并如实标注） */
    private fun drain(sp: ShellProcess, timeoutMs: Long, b: Backend): Result {
        val out = StringBuilder()
        val err = StringBuilder()
        val t = Thread {
            runCatching { sp.stdout.bufferedReader().forEachLine { out.append(it).append('\n') } }
        }
        t.isDaemon = true
        t.start()
        val waiter = Thread { runCatching { sp.waitFor() } }
        waiter.isDaemon = true
        waiter.start()
        waiter.join(timeoutMs)
        val timeout = waiter.isAlive
        if (timeout) sp.destroy()
        t.join(1200)
        val exit = if (timeout) -1 else runCatching { sp.waitFor() }.getOrDefault(-1)
        return Result(b, exit, out.toString().trimEnd(), err.toString().trimEnd(), timeout)
    }

    /** 给命令里的路径加单引号（只用于我们拼的固定片段，不用于用户命令） */
}
