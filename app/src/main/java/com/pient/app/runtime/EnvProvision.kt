package com.pient.app.runtime

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pient.app.data.AptMirror
import com.pient.app.data.SettingsStore
import com.pient.app.data.UbuntuComponent
import com.pient.app.data.aptMirrorByName
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 环境内软件（Ubuntu）的**真实**配置：apt 镜像源写入 + 组件检测 + 组件安装。
 *
 * 三条硬口径：
 * 1. 这些命令**永远在 Ubuntu 环境里跑**（`PIENT_EXEC_ENV=ubuntu` 强制），与「当前选的执行环境」无关；
 * 2. **安装过程显示在终端页**（用户 2026-09-14 口径）：点「安装所选」→ 跳到终端页，脚本在专用会话
 *    「环境配置」里跑，apt 输出实时滚 —— 页面不再自建日志区；
 * 3. 检测（`command -v` / `dpkg -s` / 版本号）是短命令直查，不进终端（否则每次进页面刷一屏）。
 */
object EnvProvision {

    private const val TAG = "PientEnv"

    /** pi 在 npm 上的包名（与清单里的安装命令一致） */
    private const val PI_PACKAGE = "@earendil-works/pi-coding-agent"

    /**
     * 查 **pi 的官方最新版**（npm registry：先镜像 `registry.npmmirror.com`，失败退回官方源）。
     *
     * 返回 null = 查不到（没网 / 解析失败）—— 由调用方如实显示「检测失败」，不假装已是最新。
     * 走 App 自己的网络（宿主侧），不需要起 guest。
     */
    suspend fun latestPiVersion(): String? = withContext(Dispatchers.IO) {
        val urls = listOf(
            "https://registry.npmmirror.com/$PI_PACKAGE/latest",
            "https://registry.npmjs.org/$PI_PACKAGE/latest",
        )
        for (u in urls) {
            val body = runCatching {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .build()
                client.newCall(okhttp3.Request.Builder().url(u).build()).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            }.getOrNull()
            val ver = body?.let { Regex("\"version\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
            if (!ver.isNullOrBlank()) return@withContext ver
        }
        null
    }

    /** 版本比较：[a] 是否比 [b] 新（`0.86.0` > `0.85.1`；忽略 `-pre` 后缀与空段） */
    fun isNewer(a: String, b: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.trim().toIntOrNull() ?: 0 }
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrElse(i) { 0 }) - (y.getOrElse(i) { 0 })
            if (d != 0) return d > 0
        }
        return false
    }

    /** 有安装任务在跑（页面据此把「安装所选」换成「去终端查看」） */
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
     *
     * **写进去的是按本机架构选中的那条路径**（[AptMirror.uriFor]）：arm64 走 `…/ubuntu-ports/`，
     * x86_64 走 `…/ubuntu/` —— 写错档位 apt 会整轮 404（见 `AptMirror` 的注释）。
     */
    fun applyMirror(context: Context, mirror: AptMirror): Boolean = runCatching {
        val uri = mirror.uriFor(PiRuntime.hostMachine())
        val dir = File(PiRuntime.rootfsDir(context), "etc/apt/sources.list.d")
        dir.mkdirs()
        File(dir, "ubuntu.sources").writeText(
            """
            |Types: deb
            |URIs: $uri
            |Suites: noble noble-updates noble-backports
            |Components: main universe restricted multiverse
            |Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
            |
            |Types: deb
            |URIs: $uri
            |Suites: noble-security
            |Components: main universe restricted multiverse
            |Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
            |
            """.trimMargin(),
        )
        Log.i(TAG, "apt 镜像源已写入：${mirror.name} → $uri（本机架构 ${PiRuntime.hostMachine()}）")
        true
    }.getOrElse {
        Log.w(TAG, "apt 镜像源写入失败：${it.message}")
        false
    }

    /**
     * **安装前自愈**：把「设置里选中的镜像源」按本机架构重写一遍。
     *
     * 为什么每次安装都写：apt 的 URI 是设备侧持久文件，一旦写错档位（或 roots 换 ABI 后沿用旧值），
     * `apt-get update` 会整轮 404 而用户看不出原因 —— 安装流程自己保证「索引刷新前源是对的」。
     */
    fun ensureSelectedMirror(context: Context): Boolean =
        applyMirror(context, aptMirrorByName(SettingsStore.aptMirror))

    /** 当前 rootfs 里生效的镜像源 URI（读 ubuntu.sources；读不到返回空串） */
    fun currentMirrorUri(context: Context): String = runCatching {
        val f = File(File(PiRuntime.rootfsDir(context), "etc/apt/sources.list.d"), "ubuntu.sources")
        Regex("^URIs:\\s*(\\S+)", RegexOption.MULTILINE).find(f.readText())?.groupValues?.get(1).orEmpty()
    }.getOrDefault("")

    // ─────────────────────────── 组件检测 ───────────────────────────

    /** 检测结果（null = 还没检测 / 正在检测） */
    fun detect(context: Context, components: List<UbuntuComponent>): Map<String, Boolean>? {
        if (!PiRuntime.rootfsReady(context)) return null
        val script = components.joinToString("\n") { c ->
            // 自定义检测命令优先（装了但版本不对 / 没有可执行文件的情形）
            val check = c.detectCmd ?: "command -v ${c.probe}"
            "if $check >/dev/null 2>&1; then echo ${c.id}=1; else echo ${c.id}=0; fi"
        }
        val out = query(context, script, timeoutMs = 25_000)
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
     * 输出实时滚在终端里；页面只留一条「正在安装 · 去终端」的状态。
     *
     * @return 承载安装的会话（调用方据此切到终端页并选中这个会话）
     */
    fun installInTerminal(context: Context, components: List<UbuntuComponent>): TerminalSessions.Session {
        // 会话固定 Ubuntu（不受用户给终端页选的执行环境影响；见 GuestScripts）
        val session = GuestScripts.sessionFor(context, TerminalSessions.CONFIG_SESSION)
        if (components.isEmpty() || running) return session
        // 索引刷新前先把镜像源写对（架构档位错 = apt 整轮 404；见 ensureSelectedMirror）
        ensureSelectedMirror(context)
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

    /** 跑一条命令并取回输出（**强制 Ubuntu 环境**，短任务） */
    private fun query(context: Context, script: String, timeoutMs: Long): String {
        val shell = PiRuntime.shellPath(context)
        if (!shell.isFile) return ""
        return runCatching {
            val p = ProcessBuilder(shell.absolutePath, "-c", script)
                .redirectErrorStream(true)
                .also { pb ->
                    pb.environment().putAll(PiRuntime.environment(context))
                    pb.environment()["PIENT_EXEC_ENV"] = "ubuntu"   // 与当前执行环境选择解耦
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
}
