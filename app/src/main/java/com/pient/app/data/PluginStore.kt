package com.pient.app.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pient.app.runtime.PiRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 插件（pi Packages / Extensions）的真实数据层 —— **2026-09-14 真实化**，
 * 取代 `MockStore.globalPlugins/projectPlugins`。
 *
 * 事实来源（`Refences/pi-0.85.1/…/docs/packages.md` + `src/core/package-manager.ts`）：
 * - 声明：`settings.json` 的 `packages` 数组，元素可为字符串（`"npm:foo@1"` / `"git:host/repo@ref"` /
 *   本地路径）或对象（`{source, extensions[], skills[], prompts[], themes[]}`，数组即过滤，
 *   `[]` = 全不加载 → UI 上的「已禁用」）；
 * - 落点：npm → `~/.pi/agent/npm/node_modules/<包名>`（项目级 `<ws>/.pi/npm/…`）；
 *   git → `~/.pi/agent/git/<host>/<path>`；**本地路径不复制文件**（只写进 settings）；
 * - 资源：包根 `package.json` 的 `pi` 键（`extensions/skills/prompts/themes`，支持 glob）或
 *   约定目录 `extensions/`、`skills/`、`prompts/`、`themes/`；
 * - 安装/卸载/更新：**只有 CLI**（`pi install|remove|update|list`，入口是包里的
 *   `dist/bundle/cli.js` —— 不是 rpc-entry.js，后者会强制进 RPC 模式）；RPC 面没有包管理命令。
 *   Android 上 npm/git 依赖由运行时提供（今天没有 → 失败原因如实回传）。
 */
object PluginStore {

    private const val TAG = "Pient"
    private const val PI_PACKAGE = "@earendil-works/pi-coding-agent"

    val global = mutableStateListOf<PluginItem>()
    val project = mutableStateListOf<PluginItem>()

    var loading by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    /** 最近一次安装/更新的命令输出（页面反馈用） */
    var lastOutput by mutableStateOf<String?>(null)
        private set

    /** 有改动需要重启宿主才生效（pi 启动时装配包资源；同技能页口径） */
    var needsHostRestart by mutableStateOf(false)
        private set

    fun markHostRestarted() {
        needsHostRestart = false
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ───────────────────────── 目录与文件 ─────────────────────────

    private fun globalSettings(context: Context): File =
        File(File(PiRuntime.homeDir(context), ".pi/agent"), "settings.json")

    private fun projectSettings(context: Context): File =
        File(File(PiRuntime.workspaceDir(context), ".pi"), "settings.json")

    private fun npmRoot(context: Context, globalScope: Boolean): File = if (globalScope) {
        File(PiRuntime.agentDir(context), "npm")
    } else {
        File(File(PiRuntime.workspaceDir(context), ".pi"), "npm")
    }

    private fun gitRoot(context: Context, globalScope: Boolean): File = if (globalScope) {
        File(PiRuntime.agentDir(context), "git")
    } else {
        File(File(PiRuntime.workspaceDir(context), ".pi"), "git")
    }

    /** pi CLI 入口（包管理只走它；`rpc-entry.js` 不行——那会强制 RPC 模式） */
    fun cliEntry(context: Context): File =
        File(PiRuntime.appDir(context), "node_modules/$PI_PACKAGE/dist/bundle/cli.js")

    /** 安装目录（列表/详情展示与删除用；npm→npm/node_modules/<name>、git→git/<host>/<path>、本地→原路径） */
    fun installDir(context: Context, item: PluginItem): File = resolveDir(context, item.source, item.global)

    // ───────────────────────── 加载 ─────────────────────────

    fun refresh(context: Context) {
        loading = true
        try {
            val errs = mutableListOf<String>()
            val (g, ge) = readSettings(context, globalSettings(context), globalScope = true)
            val (p, pe) = readSettings(context, projectSettings(context), globalScope = false)
            errs += ge; errs += pe
            global.clear(); global.addAll(g)
            project.clear(); project.addAll(p)
            lastError = errs.takeIf { it.isNotEmpty() }?.joinToString("；")
        } catch (e: Exception) {
            lastError = e.message
            Log.w(TAG, "插件扫描失败：${e.message}")
        } finally {
            loading = false
        }
    }

    private fun readSettings(
        context: Context,
        file: File,
        globalScope: Boolean,
    ): Pair<List<PluginItem>, List<String>> {
        if (!file.isFile) return emptyList<PluginItem>() to emptyList()
        val errs = mutableListOf<String>()
        val out = mutableListOf<PluginItem>()
        val root = runCatching { JSONObject(file.readText()) }.getOrElse {
            return emptyList<PluginItem>() to listOf("${file.name} 解析失败：${it.message}")
        }
        val arr = root.optJSONArray("packages") ?: return emptyList<PluginItem>() to emptyList()
        for (i in 0 until arr.length()) {
            val entry = arr.opt(i)
            val source = when (entry) {
                is String -> entry
                is JSONObject -> entry.optString("source")
                else -> ""
            }
            if (source.isBlank()) continue
            val disabled = isDisabled(entry)
            val dir = resolveDir(context, source, globalScope)
            val pkg = runCatching {
                val f = File(dir, "package.json")
                if (f.isFile) JSONObject(f.readText()) else null
            }.getOrNull()
            val resources = resourcesOf(dir, pkg, entry)
            val missing = pkg == null && !dir.isDirectory
            out += PluginItem(
                name = pkg?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: dir.name.takeIf { it.isNotBlank() }
                    ?: source,
                source = source,
                enabled = !disabled,
                global = globalScope,
                desc = pkg?.optString("description").orEmpty(),
                readmeMd = File(dir, "README.md").takeIf { it.isFile }?.readText(),
                version = pkg?.optString("version")?.takeIf { it.isNotBlank() },
                configuredVersion = pinnedRef(source),
                status = when {
                    disabled -> PluginStatus.DISABLED
                    missing -> PluginStatus.MISSING
                    resources.isNotEmpty() -> PluginStatus.LOADED
                    else -> PluginStatus.INSTALLED
                },
                resources = resources,
            )
        }
        return out to errs
    }

    /** pi 的过滤语义：对象形式里四个数组都为空 = 该包整体不加载（UI 显示「已禁用」） */
    private fun isDisabled(entry: Any?): Boolean {
        val obj = entry as? JSONObject ?: return false
        val keys = listOf("extensions", "skills", "prompts", "themes")
        val present = keys.mapNotNull { k -> obj.optJSONArray(k)?.let { k to it } }
        return present.isNotEmpty() && present.all { (_, a) -> a.length() == 0 }
    }

    /** `npm:@scope/pkg@1.0.0` → 安装根下 node_modules 里的包名；git → clone 路径；本地 → 原路径 */
    private fun resolveDir(context: Context, source: String, globalScope: Boolean): File = when {
        source.startsWith("npm:") -> {
            val spec = source.removePrefix("npm:")
            val name = npmPackageName(spec)
            File(npmRoot(context, globalScope), "node_modules/$name")
        }
        source.startsWith("git:") -> {
            val spec = source.removePrefix("git:").substringBefore('@')
            val clean = spec.removePrefix("https://").removePrefix("http://").trimEnd('/')
            File(gitRoot(context, globalScope), clean.removeSuffix(".git"))
        }
        else -> File(source).let { f ->
            // 本地路径可能是**相对 settings 文件所在目录**的（pi 的 addSourceToSettings 就这么写：
            // 实测装 <ws>/pi-test-pkg 时全局 settings 里存的是 "../../../app/pi-test-pkg"）
            if (f.isAbsolute) f else {
                val base = if (globalScope) globalSettings(context).parentFile else projectSettings(context).parentFile
                File(base, source)
            }
        }
    }

    /** `@scope/pkg@1.0.0` → `@scope/pkg`；`pkg@^1` → `pkg` */
    private fun npmPackageName(spec: String): String {
        val at = spec.lastIndexOf('@')
        return if (at > 0) spec.substring(0, at) else spec
    }

    /** `npm:pkg@1.0.0` / `git:host/repo@v1` 的 pin 部分（pi 的「已配置版本」） */
    private fun pinnedRef(source: String): String? {
        val at = source.lastIndexOf('@')
        return if (at > 0) source.substring(at + 1).takeIf { it.isNotBlank() } else null
    }

    // ───────────────────────── 资源解析 ─────────────────────────

    private fun resourcesOf(dir: File, pkg: JSONObject?, entry: Any?): List<PluginResource> {
        val out = mutableListOf<PluginResource>()
        val manifest = pkg?.optJSONObject("pi")
        val filter = (entry as? JSONObject)
        val kinds = listOf(
            Triple("extensions", PluginResourceKind.EXTENSION, Regex("\\.(ts|js)$")),
            Triple("skills", PluginResourceKind.SKILL, Regex("\\.md$")),
            Triple("prompts", PluginResourceKind.PROMPT, Regex("\\.md$")),
            Triple("themes", PluginResourceKind.THEME, Regex("\\.json$")),
        )
        kinds.forEach { (key, kind, pattern) ->
            // ① manifest 显式声明（支持 glob，这里按前缀/精确匹配的最简形式处理）
            val declared = manifest?.optJSONArray(key)
            val enabledList = filter?.optJSONArray(key)
            fun enabledFor(rel: String): Boolean {
                if (enabledList == null) return true
                return (0 until enabledList.length()).any { i ->
                    val pat = enabledList.optString(i)
                    when {
                        pat == rel -> true
                        pat.endsWith("/*") -> rel.startsWith(pat.removeSuffix("*"))
                        pat.startsWith("!") -> false
                        else -> false
                    }
                }
            }
            if (declared != null && declared.length() > 0) {
                for (i in 0 until declared.length()) {
                    val rel = declared.optString(i)
                    val f = File(dir, rel)
                    if (f.isFile) {
                        out += PluginResource(kind, f.name, rel, enabledFor(rel))
                    }
                }
            } else {
                // ② 约定目录（pi 的自动发现；只扫一层 + skills 递归一层）
                val base = File(dir, key)
                base.listFiles()?.sortedBy { it.name }?.forEach { f ->
                    val match = f.isFile && pattern.containsMatchIn(f.name)
                    if (match) out += PluginResource(kind, f.name, "$key/${f.name}", enabledFor("$key/${f.name}"))
                    if (key == "skills" && f.isDirectory) {
                        File(f, "SKILL.md").takeIf { it.isFile }?.let {
                            out += PluginResource(kind, f.name, "$key/${f.name}/SKILL.md", enabledFor("$key/${f.name}"))
                        }
                    }
                }
            }
        }
        return out
    }

    // ───────────────────────── 操作（写 settings / 跑 CLI） ─────────────────────────

    /**
     * 启用/停用（pi 原生口径）：停用 = 把该条写成对象形式且四个数组都为空；
     * 启用 = 还原成字符串形式（省略过滤 = 全加载）。
     */
    fun setEnabled(context: Context, item: PluginItem, enabled: Boolean): Boolean = runCatching {
        val file = if (item.global) globalSettings(context) else projectSettings(context)
        val root = if (file.isFile) JSONObject(file.readText()) else JSONObject()
        val arr = root.optJSONArray("packages") ?: JSONArray()
        val next = JSONArray()
        for (i in 0 until arr.length()) {
            val entry = arr.opt(i)
            val src = when (entry) {
                is String -> entry
                is JSONObject -> entry.optString("source")
                else -> ""
            }
            if (src != item.source) {
                next.put(entry)
                continue
            }
            next.put(
                if (enabled) {
                    item.source
                } else {
                    JSONObject()
                        .put("source", item.source)
                        .put("extensions", JSONArray())
                        .put("skills", JSONArray())
                        .put("prompts", JSONArray())
                        .put("themes", JSONArray())
                },
            )
        }
        root.put("packages", next)
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
        Log.i(TAG, "插件${if (enabled) "启用" else "停用"}：${item.source}（${file.name}）")
        needsHostRestart = true
        refresh(context)
        true
    }.getOrElse { Log.w(TAG, "插件开关失败：${it.message}"); false }

    /** 移除：先从 settings 删条目，再删安装目录（本地路径包只写 settings、不复制文件，故只删条目） */
    fun remove(context: Context, item: PluginItem): Boolean = runCatching {
        val file = if (item.global) globalSettings(context) else projectSettings(context)
        val root = if (file.isFile) JSONObject(file.readText()) else JSONObject()
        val arr = root.optJSONArray("packages") ?: JSONArray()
        val next = JSONArray()
        for (i in 0 until arr.length()) {
            val entry = arr.opt(i)
            val src = when (entry) {
                is String -> entry
                is JSONObject -> entry.optString("source")
                else -> ""
            }
            if (src != item.source) next.put(entry)
        }
        root.put("packages", next)
        file.writeText(root.toString())
        val dir = resolveDir(context, item.source, item.global)
        val deleted = if (item.source.startsWith("npm:") || item.source.startsWith("git:")) {
            dir.deleteRecursively()
        } else {
            false   // 本地路径：不删用户自己的文件
        }
        Log.i(TAG, "插件移除：${item.source}（settings 已改，目录删除=$deleted）")
        needsHostRestart = true
        refresh(context)
        true
    }.getOrElse { Log.w(TAG, "插件移除失败：${it.message}"); false }

    /** 安装/更新走 pi CLI（官方包管理器）：`node cli.js install <source> [-l]` */
    private suspend fun runCli(context: Context, vararg args: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cli = cliEntry(context)
                if (!cli.isFile) error("找不到 pi CLI（$cli），先把 pi 官方包部署到设备")
                val cmd = mutableListOf(PiRuntime.nodeBinary(context).absolutePath, cli.absolutePath)
                cmd += args
                val pb = ProcessBuilder(cmd)
                    .directory(PiRuntime.workspaceDir(context))
                    .redirectErrorStream(true)
                PiRuntime.environment(context).forEach { (k, v) -> pb.environment()[k] = v }
                val proc = pb.start()
                val text = proc.inputStream.bufferedReader().readText()
                val code = proc.waitFor()
                lastOutput = text.takeLast(2000)
                Log.i(TAG, "pi ${args.joinToString(" ")} → exit=$code：${text.takeLast(300)}")
                if (code != 0) error(text.takeLast(400).ifBlank { "退出码 $code" })
                text
            }
        }

    suspend fun install(context: Context, source: String, globalScope: Boolean): Result<String> =
        runCli(context, "install", source, *(if (globalScope) emptyArray() else arrayOf("-l")))
            .onSuccess {
                needsHostRestart = true
                refresh(context)
            }

    suspend fun update(context: Context, item: PluginItem): Result<String> =
        runCli(context, "update", "--extension", item.source)
            .onSuccess {
                needsHostRestart = true
                refresh(context)
            }

    /**
     * 「检查更新」：只对 npm 源可查（读 registry 的 `dist-tags.latest`）；
     * git 源是 pin 语义（pi 不追新）、本地路径无版本 → 返回 null（UI 提示不可查）。
     */
    suspend fun latestVersion(item: PluginItem): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            if (!item.source.startsWith("npm:")) return@runCatching null
            val name = npmPackageName(item.source.removePrefix("npm:"))
            val body = http.newCall(Request.Builder().url("https://registry.npmjs.org/$name").build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    resp.body?.string().orEmpty()
                }
            JSONObject(body).optJSONObject("dist-tags")?.optString("latest")?.takeIf { it.isNotBlank() }
        }
    }
}
