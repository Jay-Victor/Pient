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
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 技能（pi Skills）的真实数据层 —— **2026-09-14 真实化**，取代 `MockStore.globalSkills/projectSkills`。
 *
 * 落点与发现规则全部照 pi 原生（`Refences/pi-0.85.1/packages/coding-agent/docs/skills.md`）：
 * - 全局：`~/.pi/agent/skills/`（source=user）、`~/.agents/skills/`（只认嵌套 `.md`，忽略根级）；
 * - 项目：`<cwd>/.pi/skills/`、祖先目录 `.agents/skills`（**项目未被信任时不加载**，pi 侧门禁）；
 * - 一个技能 = 含 `SKILL.md`（YAML frontmatter：`name` 必填、`description` 必填、可选
 *   `disable-model-invocation`）的目录；也允许目录根下带 frontmatter 的 `.md` 直接当技能；
 * - 停用：pi 自己的做法是在 settings 的 `skills` 数组里写 `-<绝对路径>`（重启用 `+<绝对路径>`），
 *   不是改技能文件本身（pi-web 走 frontmatter 手术，属于它的自定义；这里用 pi 原生口径）。
 *
 * 市场（技能页第二个分段）：pi-web 的 `/api/skills/search|install` 背后是 `npx skills`
 * ——Android 没有 npx，改走 skills.sh 的公开 HTTP 接口（2026-09-14 实测）：
 * - 搜索：`GET https://skills.sh/api/search?q=<关键词>` → `{skills:[{id,skillId,name,installs,source}]}`
 * - 取包：`GET https://skills.sh/api/download/{owner}/{repo}/{slug}` → `{files:[{path,contents}],hash}`
 *   （`files` 里含 `SKILL.md` 与脚本/参考资料，逐文件写入即完成安装，无需 npx/npm/git）
 */
object SkillStore {

    private const val TAG = "Pient"
    const val SKILLS_API = "https://skills.sh"

    /** 全局技能（`~/.pi/agent/skills` + `~/.agents/skills`） */
    val global = mutableStateListOf<SkillItem>()

    /** 项目技能（`<workspace>/.pi/skills`） */
    val project = mutableStateListOf<SkillItem>()

    var loading by mutableStateOf(false)
        private set

    /** 上次加载的错误/诊断（页面显示用；null = 正常） */
    var lastError by mutableStateOf<String?>(null)
        private set

    /**
     * 有改动需要**重启宿主**才生效（pi 在启动时装配技能，RPC 没有 reload 命令 ——
     * 桌面端的 `/reload` 只在交互模式存在）。页面据此显示提示 + 重启键。
     */
    var needsHostRestart by mutableStateOf(false)
        private set

    fun markHostRestarted() {
        needsHostRestart = false
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // ───────────────────────── 目录 ─────────────────────────

    /** 全局技能目录（宿主 HOME 下，与 pi/pi-web 同构） */
    fun globalDirs(context: Context): List<File> = listOf(
        File(PiRuntime.agentDir(context), "skills"),                  // ~/.pi/agent/skills
        File(File(PiRuntime.homeDir(context), ".agents"), "skills"),  // ~/.agents/skills
    )

    /** 项目技能目录（工作区 = 当前项目目录，见 PiRuntime.workspaceDir） */
    fun projectDirs(context: Context): List<File> {
        val ws = PiRuntime.workspaceDir(context)
        return listOf(
            File(File(ws, ".pi"), "skills"),
            File(File(ws, ".agents"), "skills"),
        )
    }

    /** 该条目的技能目录（详情页「路径」与删除用） */
    fun dirOf(context: Context, item: SkillItem): File? {
        val roots = if (item.global) globalDirs(context) else projectDirs(context)
        return roots.map { File(it, item.name) }.firstOrNull { it.isDirectory }
    }

    // ───────────────────────── 加载 ─────────────────────────

    fun refresh(context: Context) {
        loading = true
        try {
            val (g, gErr) = scan(context, globalDirs(context))
            val (p, pErr) = scan(context, projectDirs(context))
            global.clear(); global.addAll(g)
            project.clear(); project.addAll(p)
            lastError = (gErr + pErr).takeIf { it.isNotEmpty() }?.joinToString("；")
        } catch (e: Exception) {
            lastError = e.message
            Log.w(TAG, "技能扫描失败：${e.message}")
        } finally {
            loading = false
        }
    }

    private fun scan(context: Context, roots: List<File>): Pair<List<SkillItem>, List<String>> {
        val out = mutableListOf<SkillItem>()
        val seen = mutableSetOf<String>()
        val errs = mutableListOf<String>()
        val disabled = disabledPaths(context)
        roots.filter { it.isDirectory }.forEach { root ->
            val isAgentsDir = root.parentFile?.name == ".agents"
            root.listFiles()?.sortedBy { it.name }?.forEach { entry ->
                val md = when {
                    entry.isDirectory -> File(entry, "SKILL.md").takeIf { it.isFile }
                    // `.pi/skills` 根下带 frontmatter 的 .md 也算技能；`.agents/skills` 只认嵌套
                    entry.isFile && entry.name.endsWith(".md") && !isAgentsDir -> entry
                    else -> null
                } ?: return@forEach
                runCatching {
                    val text = md.readText()
                    val fm = frontmatter(text)
                    val name = fm["name"]?.trim().orEmpty()
                        .ifBlank { if (entry.isDirectory) entry.name else entry.nameWithoutExtension }
                    val desc = fm["description"]?.trim().orEmpty()
                    if (desc.isBlank()) {
                        errs += "$name：缺 description（pi 不会加载它）"
                        return@runCatching
                    }
                    // pi 的同名冲突口径 = 先发现的胜出（skills.ts diagnostics: collision）；
                    // 顺带避免 LazyColumn 的 key 重复崩溃
                    if (!seen.add(name)) {
                        errs += "$name：重名，已忽略后发现的（${entry.absolutePath}）"
                        return@runCatching
                    }
                    out += SkillItem(
                        name = name,
                        desc = desc,
                        enabled = !disabled.contains(md.absolutePath) && !disabled.contains(entry.absolutePath),
                        global = roots == globalDirs(context),
                        skillMd = text,
                        fileTree = asciiTree(entry),
                    )
                }.onFailure { errs += "${entry.name}：${it.message}" }
            }
        }
        return out to errs
    }

    /** settings 里被 `-path` 停用的条目（pi 原生停用口径） */
    private fun disabledPaths(context: Context): Set<String> {
        val arr = settingsSkills(context)
        return (0 until arr.length()).mapNotNull { i ->
            val s = arr.optString(i)
            if (s.startsWith("-")) s.removePrefix("-") else null
        }.toSet()
    }

    private fun settingsFile(context: Context): File =
        File(File(PiRuntime.homeDir(context), ".pi/agent"), "settings.json")

    private fun settingsSkills(context: Context): org.json.JSONArray = runCatching {
        val f = settingsFile(context)
        if (f.isFile) JSONObject(f.readText()).optJSONArray("skills") ?: org.json.JSONArray() else org.json.JSONArray()
    }.getOrDefault(org.json.JSONArray())

    // ───────────────────────── 操作 ─────────────────────────

    /**
     * 启用/停用（pi 原生：settings.skills 里的 `-<绝对路径>` / `+<绝对路径>`）。
     * 技能目录被停用的判定用 `SKILL.md` 的绝对路径（pi 的资源 id 就是文件路径）。
     */
    fun setEnabled(context: Context, item: SkillItem, enabled: Boolean): Boolean = runCatching {
        val md = dirOf(context, item)?.let { File(it, "SKILL.md").takeIf { f -> f.isFile } }
            ?: globalDirs(context).plus(projectDirs(context))
                .map { File(it, "${item.name}.md") }.firstOrNull { it.isFile }
            ?: return false
        val f = settingsFile(context)
        f.parentFile?.mkdirs()
        val root = if (f.isFile) JSONObject(f.readText()) else JSONObject()
        val arr = root.optJSONArray("skills") ?: org.json.JSONArray()
        val path = md.absolutePath
        val kept = (0 until arr.length()).map { arr.optString(it) }
            .filterNot { it == "-$path" || it == "+$path" || it == path }
        val next = org.json.JSONArray()
        kept.forEach { next.put(it) }
        next.put(if (enabled) "+$path" else "-$path")
        root.put("skills", next)
        f.writeText(root.toString())
        Log.i(TAG, "技能${if (enabled) "启用" else "停用"}：$path")
        needsHostRestart = true
        refresh(context)
        true
    }.getOrElse { Log.w(TAG, "技能开关失败：${it.message}"); false }

    /** 删除技能目录（真实删除；市场装的与手放的都在技能目录下） */
    fun delete(context: Context, item: SkillItem): Boolean = runCatching {
        val dir = dirOf(context, item) ?: return false
        val ok = dir.deleteRecursively()
        Log.i(TAG, "技能删除：${dir.absolutePath} ok=$ok")
        needsHostRestart = true
        refresh(context)
        ok
    }.getOrElse { Log.w(TAG, "技能删除失败：${it.message}"); false }

    /**
     * 新建/导入技能：在 `<全局|项目>/.pi/skills/<name>/SKILL.md` 落盘（pi 的默认发现目录之一）。
     * 全局写 `~/.pi/agent/skills/<name>`（pi 原生全局技能目录，最稳的落点）。
     */
    fun createSkill(
        context: Context,
        name: String,
        desc: String,
        markdown: String?,
        globalScope: Boolean,
    ): Boolean = runCatching {
        val root = if (globalScope) {
            File(PiRuntime.agentDir(context), "skills")
        } else {
            File(File(PiRuntime.workspaceDir(context), ".pi"), "skills")
        }
        val dir = File(root, name)
        if (!dir.exists() && !dir.mkdirs()) error("无法创建 ${dir.absolutePath}")
        val body = markdown?.takeIf { it.isNotBlank() } ?: run {
            val fmDesc = desc.replace("\n", " ").trim().ifBlank { "（未填写描述）" }
            "---\nname: $name\ndescription: $fmDesc\n---\n\n# $name\n\n$fmDesc\n"
        }
        File(dir, "SKILL.md").writeText(body)
        Log.i(TAG, "技能已创建：${dir.absolutePath}")
        needsHostRestart = true
        refresh(context)
        true
    }.getOrElse { Log.w(TAG, "技能创建失败：${it.message}"); false }

    // ───────────────────────── 市场（skills.sh 公开接口） ─────────────────────────
    /** 搜索技能市场（pi-web `/api/skills/search` 的等价物，去掉它的 npx 回退） */
    suspend fun searchMarket(query: String, limit: Int = 30): List<SkillItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            val url = "$SKILLS_API/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=$limit"
            val body = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
            val arr = JSONObject(body).optJSONArray("skills") ?: org.json.JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                SkillItem(
                    name = o.optString("name").ifBlank { o.optString("skillId") },
                    desc = "来源 ${o.optString("source")} · ${o.optInt("installs")} 次安装",
                    enabled = false,
                    marketId = o.optString("id"),
                    installs = o.optInt("installs"),
                )
            }
        }.getOrElse {
            Log.w(TAG, "技能市场搜索失败：${it.message}")
            lastError = "技能市场搜索失败：${it.message}"
            emptyList()
        }
    }

    /**
     * 安装市场技能：`GET /api/download/{owner}/{repo}/{slug}` → 逐文件写进
     * `<全局|项目>/.agents/skills/<名字>/`，并把返回的 `hash` 记进 skills 的 lock 文件
     * （与 `npx skills` 的 `.skill-lock.json` 同构，便于日后比对更新）。
     */
    suspend fun install(context: Context, item: SkillItem, globalScope: Boolean): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val id = item.marketId?.takeIf { it.isNotBlank() }
                    ?: error("缺少市场 id（${item.name}）")
                val body = http.newCall(Request.Builder().url("$SKILLS_API/api/download/$id").build())
                    .execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        resp.body?.string().orEmpty()
                    }
                val root = JSONObject(body)
                val files = root.optJSONArray("files") ?: error("返回里没有 files")
                val home = PiRuntime.homeDir(context)
                val dir = if (globalScope) {
                    File(File(home, ".agents"), "skills/${item.name}")
                } else {
                    File(File(PiRuntime.workspaceDir(context), ".agents"), "skills/${item.name}")
                }
                if (!dir.exists() && !dir.mkdirs()) error("无法创建 ${dir.absolutePath}")
                var written = 0
                for (i in 0 until files.length()) {
                    val f = files.optJSONObject(i) ?: continue
                    val rel = f.optString("path")
                    if (rel.isBlank()) continue
                    val target = File(dir, rel)
                    target.parentFile?.mkdirs()
                    target.writeText(f.optString("contents"))
                    written++
                }
                if (written == 0) error("安装包为空")
                writeLock(context, item, root.optString("hash"), globalScope)
                Log.i(TAG, "技能已安装：${item.name}（$written 个文件 → ${dir.absolutePath}）")
                needsHostRestart = true
                refresh(context)
                "已安装 ${item.name}（$written 个文件）"
            }
        }

    /** 写 skills lock（`~/.agents/.skill-lock.json` / `<ws>/.agents/.skill-lock.json`） */
    private fun writeLock(context: Context, item: SkillItem, hash: String, globalScope: Boolean) {
        runCatching {
            val home = PiRuntime.homeDir(context)
            val f = if (globalScope) {
                File(File(home, ".agents"), ".skill-lock.json")
            } else {
                File(File(PiRuntime.workspaceDir(context), ".agents"), ".skill-lock.json")
            }
            f.parentFile?.mkdirs()
            val root = if (f.isFile) JSONObject(f.readText()) else JSONObject()
            val skills = root.optJSONObject("skills") ?: JSONObject()
            skills.put(
                item.name,
                JSONObject()
                    .put("source", item.marketId.orEmpty())
                    .put("skillFolderHash", hash)
                    .put("installedAt", System.currentTimeMillis()),
            )
            root.put("skills", skills)
            f.writeText(root.toString())
        }.onFailure { Log.w(TAG, "写 skills lock 失败：${it.message}") }
    }

    // ───────────────────────── SKILL.md 解析与小工具 ─────────────────────────

    /** YAML frontmatter（只取顶层 `key: value`，够用且不引依赖；缺 frontmatter 返回空表） */
    fun frontmatter(text: String): Map<String, String> {
        val lines = text.lineSequence().toList()
        if (lines.firstOrNull()?.trim() != "---") return emptyMap()
        val out = linkedMapOf<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.trim() == "---") break
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim().trim('"', '\'')
            if (key.isNotEmpty()) out[key] = value
        }
        return out
    }

    /** 目录 → ASCII 树（技能详情页的「目录」区块；深度上限，避免大仓库卡住） */
    fun asciiTree(root: File, maxDepth: Int = 3, maxEntries: Int = 60): String {
        val sb = StringBuilder("${root.name}/")
        var budget = maxEntries

        fun walk(dir: File, prefix: String, depth: Int) {
            if (depth > maxDepth || budget <= 0) return
            val children = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: return
            children.forEachIndexed { idx, child ->
                if (budget-- <= 0) return@forEachIndexed
                val last = idx == children.lastIndex
                sb.append("\n").append(prefix).append(if (last) "└── " else "├── ").append(child.name)
                if (child.isDirectory) walk(child, prefix + if (last) "    " else "│   ", depth + 1)
            }
        }
        walk(root, "", 1)
        if (budget <= 0) sb.append("\n…（仅显示前 $maxEntries 项）")
        return sb.toString()
    }
}
