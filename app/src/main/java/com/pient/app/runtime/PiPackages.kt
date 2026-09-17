package com.pient.app.runtime

import com.pient.app.data.i18n.L
import android.content.Context
import com.pient.app.data.PientLog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pient.app.data.PluginItem
import com.pient.app.data.PluginStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * pi 包（页面上的「插件」）管理 —— 页面按钮 = pi 官方命令，一条不多一条不少。
 *
 * 口径取自 pi 源码 `Refences/pi-0.85.1/packages/coding-agent/src/package-manager-cli.ts`：
 *
 * - **列**：`pi list` —— 读「用户设置 + 项目设置」两处，输出分 `User packages:` / `Project packages:` 两段；
 *   每个条目两空格缩进（`source`，可能带 ` (filtered)` 后缀 = 包级过滤），其下四空格缩进是 `installedPath`；
 *   一个都没有时输出 `No packages installed.`。
 * - **装**：`pi install <source>`（默认写 `~/.pi/agent/settings.json`；加 `-l` 写项目 `.pi/settings.json`）；
 * - **删**：`pi remove <source>`（同样支持 `-l`）；**更新**：`pi update <source>` /
 *   `pi update --extensions`（更新全部包 + 对齐 pinned git ref）。
 *
 * pi 就在 guest 的 PATH 上（随 APK 预置在 Ubuntu 里），所以这些命令**都在 Ubuntu 里跑** ——
 * 与桌面 pi 的行为逐字节同源；设置文件也就是 Pient 配置页在写的同一份 `settings.json`
 * （见 [com.pient.app.data.PiAgentFiles]，那里是**合并写**，pi 自己写的 `packages` 不会被踩）。
 */
object PiPackages {
    private const val TAG = "PiPackages"

    /** 装/删/更新在哪个终端会话里跑（用户可切到终端页看全过程、看真报错） */
    /** 专用会话名（计算属性：object 里的 val 只求值一次，写 `L.…` 会冻结成首帧语言） */
    val SESSION: String get() = L.runtime.piPackagesSession

    var running by mutableStateOf(false); private set
    var step by mutableStateOf(""); private set
    var lastExit by mutableStateOf<Int?>(null); private set

    /** 页面读这两张表（[refresh] 填充；全局段 = 用户设置里的包，项目段 = 项目设置里的包） */
    val global = mutableStateListOf<PluginItem>()
    val project = mutableStateListOf<PluginItem>()

    /** 命令跑完后的回调（页面用来重新拉列表；由调用方设置，跑完即清） */
    private var onFinished: (() -> Unit)? = null

    /**
     * 跑一条 `pi` 子命令并取回 `(退出码, 输出)`。
     * cwd 固定 guest 的 `/workspace`（项目设置 `.pi/settings.json` 是按 cwd 找的）；
     * `NO_COLOR=1` 关掉 chalk 颜色，解析时再去一次 ANSI，双保险。
     */
    suspend fun runPi(context: Context, args: String, timeoutMs: Long = 60_000): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val shell = PiRuntime.shellPath(context)
            if (!shell.isFile) return@withContext -1 to L.runtime.ubuntuNotReady
            runCatching {
                val proc = ProcessBuilder(
                    shell.absolutePath, "-c",
                    "cd /workspace 2>/dev/null; export NO_COLOR=1; export FORCE_COLOR=0; pi $args",
                )
                    .redirectErrorStream(true)
                    // pi 自己永远在 Ubuntu 里跑（与用户选的 exec_env 解耦，见 PiRuntime.guestEnv）
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
                    return@runCatching -1 to (out.toString() + L.runtime.timeoutSuffix(timeoutMs / 1000))
                }
                reader.join(1000)
                proc.exitValue() to out.toString()
            }.getOrElse { -1 to (it.message ?: L.runtime.commandFailed) }
        }

    /** `pi list` → 填 [global] / [project]。返回 null 表示成功，否则是给页面看的错误文本。 */
    suspend fun refresh(context: Context): String? {
        val (code, out) = runPi(context, "list", 30_000)
        val text = stripAnsi(out)
        if (code != 0) return text.trim().ifBlank { L.runtime.piListFailed(code) }
        val (g, p) = parseList(text)
        global.clear(); global.addAll(g)
        project.clear(); project.addAll(p)
        PientLog.i(TAG, "pi list：用户 ${g.size} 个 / 项目 ${p.size} 个包")
        return null
    }

    /** 装包。source 原样传给 pi（`npm:@x/y@1.0.0` / `git:…` / 本地路径都合法） */
    fun install(context: Context, source: String, local: Boolean, onDone: (() -> Unit)? = null) {
        val cmd = "pi install $source" + if (local) " -l" else ""
        runInTerminal(context, L.runtime.installSource(source), cmd, onDone)
    }

    /** 删包（pi remove 支持 `-l`；source 要与 settings 里配置的写法一致） */
    fun remove(context: Context, source: String, local: Boolean, onDone: (() -> Unit)? = null) {
        val cmd = "pi remove $source" + if (local) " -l" else ""
        runInTerminal(context, L.runtime.removeSource(source), cmd, onDone)
    }

    /** 更新单个包 */
    fun update(context: Context, source: String, onDone: (() -> Unit)? = null) =
        runInTerminal(context, L.runtime.updateSource(source), "pi update $source", onDone)

    /** 更新全部包（= 官方 `pi update --extensions`，会顺带对齐 pinned git ref） */
    fun updateAll(context: Context, onDone: (() -> Unit)? = null) =
        runInTerminal(context, L.runtime.updateAllPackages, "pi update --extensions", onDone)

    // ─────────────────────────── 内部 ───────────────────────────

    private fun runInTerminal(context: Context, label: String, cmd: String, onDone: (() -> Unit)?) {
        running = true
        step = label
        lastExit = null
        onFinished = onDone
        // 会话固定 Ubuntu（见 GuestScripts / TerminalSessions.Session.execEnvOverride）
        GuestScripts.runInTerminal(context, SESSION, label, cmd) { code ->
            running = false
            step = ""
            lastExit = code
            PientLog.i(TAG, "$label 结束，退出码 $code")
            onFinished?.invoke()
            onFinished = null
        }
    }

    /**
     * 解析 `pi list` 的文本。规则（对应 package-manager-cli.ts 的 list 分支）：
     * 段标题 `User packages:` / `Project packages:` 切换当前段；两空格缩进的非空行 = 一个包
     * （尾部 ` (filtered)` 去掉并标记）；紧随其后的四空格缩进行 = 该包的 installedPath；
     * `No packages installed.` = 空。
     */
    internal fun parseList(text: String): Pair<List<PluginItem>, List<PluginItem>> {
        val user = mutableListOf<PluginItem>()
        val proj = mutableListOf<PluginItem>()
        var scope = ""                       // "" | "user" | "project"
        var last: PluginItem? = null
        var lastIsUser = true
        for (rawLine in text.lines()) {
            val line = rawLine.trimEnd()
            val trimmed = line.trim()
            if (trimmed.isEmpty()) { last = null; continue }
            when {
                trimmed.startsWith("User packages:") -> { scope = "user"; last = null }
                trimmed.startsWith("Project packages:") -> { scope = "project"; last = null }
                trimmed.startsWith("No packages installed.") -> { scope = ""; last = null }
                line.startsWith("    ") || line.startsWith("\t") -> {
                    // 四空格 = 上一条包的 installedPath
                    last?.let { pkg ->
                        val updated = pkg.copy(installedPath = trimmed)
                        replaceIn(if (lastIsUser) user else proj, pkg, updated)
                        last = updated
                    }
                }
                scope.isNotEmpty() -> {
                    val filtered = trimmed.endsWith("(filtered)")
                    val source = trimmed.removeSuffix("(filtered)").trim()
                    if (source.isEmpty()) continue
                    val item = PluginItem(
                        name = nameOf(source),
                        source = source,
                        enabled = true,                     // pi 里「配置了」即生效；包级过滤见 filtered 标记
                        global = scope == "user",
                        desc = if (filtered) L.runtime.packageFiltered else "",
                        version = versionOf(source),
                        configuredVersion = pinnedOf(source),
                        status = PluginStatus.LOADED,
                    )
                    if (scope == "user") user.add(item) else proj.add(item)
                    last = item
                    lastIsUser = scope == "user"
                }
            }
        }
        return user to proj
    }

    private fun replaceIn(list: MutableList<PluginItem>, old: PluginItem, new: PluginItem) {
        val idx = list.indexOfFirst { it.source == old.source }
        if (idx >= 0) list[idx] = new
    }

    /** `npm:@x/y@1.0.0` → `y`；`git:host/u/r@v1` → `r`；本地路径 → 末段 */
    internal fun nameOf(source: String): String {
        val s = source.removePrefix("npm:").removePrefix("git:")
        val tail = s.substringAfterLast('/')
        return tail.substringBefore('@').ifBlank { source }
    }

    /** 配置里 pin 的版本：npm 取 `@1.2.3`，git 取 `@v1`（没 pin 时 null） */
    internal fun pinnedOf(source: String): String? {
        val at = source.lastIndexOf('@')
        if (at <= 0) return null
        val v = source.substring(at + 1)
        return v.takeIf { it.isNotEmpty() && !it.contains('/') && !it.contains(':') }
    }

    /** 已装版本：优先看 installedPath 末段（pi 把包解到 `…/<name>/<version>/`），退而用配置 pin 的版本 */
    internal fun versionOf(source: String): String? = pinnedOf(source)

    internal fun stripAnsi(s: String): String = s.replace(Regex("\u001B\\[[0-9;]*[A-Za-z]"), "")
}
