package com.pient.app.runtime

import com.pient.app.data.i18n.L
import android.content.Context
import android.net.Uri
import com.pient.app.data.PientLog
import java.io.File
import java.util.zip.ZipInputStream

/**
 * pi 技能（Skills）—— 与 pi 的**文件约定**同源，不引入任何自有格式。
 *
 * pi 侧规则取自 `Refences/pi-0.85.1/packages/coding-agent/docs/skills.md` 与 `src/core/skills.ts`：
 *
 * - **加载位置**：全局 `~/.pi/agent/skills/`、`~/.agents/skills/`；项目（仅在项目被信任后）
 *   `<cwd>/.pi/skills/`、`<cwd>/.agents/skills/`，以及包内 `skills/`、settings 里的 `skills` 数组、`--skill <path>`；
 * - **发现规则**：`~/.pi/agent/skills/`、`.pi/skills/` 下**根级 `.md` 文件**（frontmatter 里有非空 description）算独立技能；
 *   所有位置里**含 `SKILL.md` 的目录**递归发现；`~/.agents/skills/`、`.agents/skills/` 里根级 `.md` 忽略、
 *   **分组目录里的嵌套 `.md`**（带 frontmatter）算技能；
 * - **跳过**：名字以 `.` 开头的条目（`skills.ts`：`if (entry.name.startsWith(".")) continue;`）与 `node_modules`；
 * - 技能会注册成 `/skill:<name>` 命令（`settings.json` 的 `enableSkillCommands` 开关）。
 *
 * 页面上的「启用/停用」就是**利用上面这条跳过规则**：停用 = 把技能挪进同级的 `.disabled/`，
 * 文件一个不删、pi 立刻不再加载；启用 = 挪回来。不发明 pi 没有的开关，也不改 pi 的代码。
 */
object PiSkills {
    private const val TAG = "PiSkills"

    /** 停用区目录名（以 `.` 开头 → pi 必然跳过） */
    private const val DISABLED_DIR = ".disabled"

    /** 一个磁盘上的真实技能 */
    data class Local(
        val file: File,             // 技能入口：SKILL.md（目录型）或根级 .md（单文件型）
        val root: File,             // 它属于哪个 skills 根（用来算相对路径 / 停用区）
        val relPath: String,        // 相对 root 的路径（如 `my-skill/SKILL.md` 或 `single.md`）
        val name: String,
        val desc: String,
        val global: Boolean,
        val enabled: Boolean,
        val problem: String? = null,   // frontmatter 不合法 / 缺 description（pi 也会跳过）
    ) {
        /** 技能目录（单文件型技能的目录 = 它所在目录） */
        val dir: File? get() = file.parentFile

        /** 详情页要展示的 SKILL.md（真读盘） */
        fun content(): String = runCatching { file.readText() }.getOrElse { "" }
    }

    /** 扫全部本地技能（启用 + 停用都返回，停用的标 enabled=false） */
    fun list(context: Context): List<Local> {
        val out = mutableListOf<Local>()
        for ((root, global) in roots(context)) {
            if (!root.isDirectory) continue
            scanRoot(root, root, global, out, insideDisabled = false)
            val disabled = File(root, DISABLED_DIR)
            if (disabled.isDirectory) scanRoot(disabled, root, global, out, insideDisabled = true)
        }
        PientLog.i(TAG, "技能扫描：${out.count { it.enabled }} 个启用 / ${out.count { !it.enabled }} 个停用")
        return out.sortedWith(compareByDescending<Local> { it.enabled }.thenBy { it.name })
    }

    fun readSkillMd(item: Local): String = item.content()

    /** 技能目录的 ASCII 树（详情页「文件树」；最多 [maxDepth] 层、[maxEntries] 条） */
    fun fileTree(item: Local, maxDepth: Int = 3, maxEntries: Int = 60): String {
        val base = item.file.parentFile ?: return ""
        val sb = StringBuilder()
        var count = 0
        fun walk(dir: File, prefix: String, depth: Int) {
            if (depth > maxDepth || count >= maxEntries) return
            val kids = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: return
            kids.forEachIndexed { i, f ->
                if (count >= maxEntries) return
                val lastOne = i == kids.lastIndex
                sb.append(prefix).append(if (lastOne) "└─ " else "├─ ").append(f.name)
                if (f.isDirectory) sb.append('/')
                sb.append('\n')
                count++
                if (f.isDirectory) walk(f, prefix + if (lastOne) "   " else "│  ", depth + 1)
            }
        }
        sb.append(base.name).append("/\n")
        walk(base, "", 1)
        if (count >= maxEntries) sb.append(L.runtime.treeTruncated)
        return sb.toString()
    }

    /**
     * 启用/停用：把**整个技能条目**在 `<root>/<entry>` 与 `<root>/.disabled/<entry>` 之间挪动。
     *
     * 条目 = 目录型技能的那个目录（`<name>/`，不是里面的 SKILL.md —— 只挪 SKILL.md 会在原地
     * 留一个空目录，实测踩过）；单文件型技能就是那个 `.md`。文件一个不删。
     */
    fun setEnabled(item: Local, enabled: Boolean): Boolean {
        // 注意：relPath 是相对**外层 skills 根**的，停用态的技能在它前面还带着 `.disabled/`
        // —— 早先这里把两者又拼了一次，日志里出现过 `.disabled/.disabled/xxx`（实测踩过）。
        val relRaw = entryRelOf(item)                        // 如 `my-skill` 或 `.disabled/my-skill`
        val relPlain = relRaw.removePrefix("$DISABLED_DIR/") // 如 `my-skill`
        val from = File(item.root, if (enabled) relRaw else relPlain)
        val target = File(item.root, if (enabled) relPlain else "$DISABLED_DIR/$relPlain")
        if (!from.exists()) {
            PientLog.w(TAG, "技能开关：源不存在 ${from.absolutePath}")
            return false
        }
        return runCatching {
            target.parentFile?.mkdirs()
            val moved = from.renameTo(target)
            if (!moved) {
                // 目标是已存在的目录（历史遗留的空壳）时 rename 会失败 → 退化为「合并复制 + 删源」
                if (from.isDirectory) from.copyRecursively(target, overwrite = true)
                else from.copyTo(target, overwrite = true)
                from.deleteRecursively()
            }
            PientLog.i(TAG, "技能${if (enabled) "启用" else "停用"}：${item.name} → ${target.absolutePath}（rename=$moved）")
            true
        }.getOrElse { PientLog.w(TAG, "技能开关失败：${it.message}"); false }
    }

    /** 技能条目相对外层 skills 根的路径：目录型 = 那个目录（relPath 去掉尾部 SKILL.md）；单文件型 = 那个 .md */
    internal fun entryRelOf(item: Local): String {
        val rel = item.relPath.replace('\\', '/')
        return if (item.file.name == "SKILL.md") rel.substringBeforeLast('/', rel) else rel
    }

    /** 删除技能：目录型删整个目录，单文件型删那个文件（含停用区里的那份） */
    fun delete(item: Local): Boolean = runCatching {
        val ok = when {
            item.file.name == "SKILL.md" -> item.file.parentFile?.deleteRecursively() ?: false
            else -> item.file.delete()
        }
        PientLog.i(TAG, "技能删除：${item.name} ok=$ok")
        ok
    }.getOrElse { false }

    /**
     * 导入技能：把内容写成 `<root>/<slug>/SKILL.md`。
     * 校验与 pi 一致：必须有 frontmatter、且 description 非空（否则 pi 不认）。
     * 返回 null 成功，否则是错误文本。
     */
    fun import(context: Context, displayName: String, content: String, global: Boolean): String? {
        val fm = parseFrontmatter(content)
        if (fm == null) return L.runtime.frontmatterMissing
        if (fm.second.isBlank()) return L.runtime.frontmatterDescRequired
        val slug = slugOf(fm.first.ifBlank { displayName })
        if (slug.isBlank()) return L.runtime.invalidSkillName
        val root = if (global) globalRoot(context) else projectRoot(context)
        val dir = File(root, slug)
        if (dir.exists()) return L.runtime.skillExistsSlug(slug)
        return runCatching {
            dir.mkdirs()
            File(dir, "SKILL.md").writeText(content)
            PientLog.i(TAG, "技能导入：$slug → ${dir.absolutePath}")
            null
        }.getOrElse { it.message ?: L.runtime.writeFailed }
    }

    /**
     * **ZIP 导入**（2026-09-16 真实化，取代原型 mock：原来点选择只写死假文件名、导入只用
     * 假描述写一个 SKILL.md，既不打开文件选择器也不解压）。
     *
     * 流程：SAF 选中的 .zip → 逐条读出 → 定位 SKILL.md（允许整体套一层目录）→
     * 校验 frontmatter（name/description，与 pi 同规则）→ 只写 SKILL.md 所在目录下的文件
     * 到 `<skills>/<slug>/`（顶层 __MACOSX、目录项、`..` 一律跳过）。
     * @return (错误描述, 技能名)；错误为 null 表示成功
     */
    fun importZip(context: Context, uri: Uri, global: Boolean): Pair<String?, String> = runCatching {
        val entries = linkedMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name.replace('\\', '/').trimStart('/')
                    if (e.isDirectory || name.isEmpty() ||
                        name.contains("..") || name.startsWith("__MACOSX")
                    ) {
                        zip.closeEntry()
                        continue
                    }
                    entries[name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
        } ?: return L.runtime.cannotOpenFile to ""
        if (entries.isEmpty()) return L.runtime.zipNoFiles to ""

        val skillPath = entries.keys.firstOrNull {
            it.equals("SKILL.md", ignoreCase = true) || it.endsWith("/SKILL.md", ignoreCase = true)
        } ?: return L.runtime.zipNoSkillMd to ""
        val prefix = skillPath.substring(0, skillPath.length - "SKILL.md".length)
        val md = entries[skillPath]?.toString(Charsets.UTF_8) ?: return L.runtime.skillMdReadFailed to ""
        val fm = parseFrontmatter(md)
            ?: return L.runtime.skillMdFrontmatterMissing to ""
        if (fm.second.isBlank()) return L.runtime.frontmatterDescRequired to ""
        val slug = slugOf(fm.first.ifBlank { prefix.trimEnd('/').substringAfterLast('/') })
        if (slug.isBlank()) return L.runtime.invalidSkillName to ""

        val root = if (global) globalRoot(context) else projectRoot(context)
        val dir = File(root, slug)
        if (dir.exists()) return L.runtime.skillExistsSlug(slug) to slug
        dir.mkdirs()
        var written = 0
        entries.forEach { (path, bytes) ->
            if (!path.startsWith(prefix, ignoreCase = true)) return@forEach
            val rel = path.substring(prefix.length)
            if (rel.isBlank()) return@forEach
            val f = File(dir, rel)
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
            written++
        }
        PientLog.i(TAG, "技能 ZIP 导入：$slug → ${dir.absolutePath}（$written 个文件）")
        null to slug
    }.getOrElse { (it.message ?: L.runtime.unzipFailed) to "" }

    /** 从市场/其他仓库导入一个技能目录（把 [files] 里的相对路径 → 内容写下去） */
    fun importFiles(context: Context, slug: String, files: Map<String, String>, global: Boolean): String? {
        val name = slugOf(slug)
        if (name.isBlank()) return L.runtime.invalidSkillName
        val dir = File(if (global) globalRoot(context) else projectRoot(context), name)
        if (dir.exists()) return L.runtime.skillExistsName(name)
        return runCatching {
            files.forEach { (rel, text) ->
                val f = File(dir, rel)
                f.parentFile?.mkdirs()
                f.writeText(text)
            }
            PientLog.i(TAG, "技能目录导入：$name（${files.size} 个文件）")
            null
        }.getOrElse { it.message ?: L.runtime.writeFailed }
    }

    // ─────────────────────────── 扫描与解析 ───────────────────────────

    private fun roots(context: Context): List<Pair<File, Boolean>> {
        val rootfs = File(PiRuntime.root(context), "rootfs")
        val home = File(rootfs, "root")                 // pi 在 guest 里的 HOME=/root
        val project = PiRuntime.workspaceDir(context)
        return listOf(
            File(home, ".pi/agent/skills") to true,
            File(home, ".agents/skills") to true,
            File(project, ".pi/skills") to false,
            File(project, ".agents/skills") to false,
        )
    }

    /** 递归扫一个 skills 根。`agentRoot` 用来算相对路径（停用区扫描时传外层根） */
    private fun scanRoot(dir: File, root: File, global: Boolean, out: MutableList<Local>, insideDisabled: Boolean) {
        val entries = dir.listFiles()?.sortedBy { it.name } ?: return
        // 先看：本目录是不是一个技能目录（含 SKILL.md）
        val skillMd = entries.firstOrNull { it.name == "SKILL.md" && it.isFile }
        if (skillMd != null) {
            out += toLocal(skillMd, root, global, !insideDisabled)
        } else if (entries.any { it.isFile && it.name.endsWith(".md") && it.name != "SKILL.md" && !it.name.startsWith(".") }) {
            // 根级 / 分组目录里的 .md（.agents 树里就是这个规则）—— 有合法 frontmatter 才算技能
            entries.filter { it.isFile && it.name.endsWith(".md") && !it.name.startsWith(".") && it.name != "SKILL.md" }
                .forEach { md ->
                    val fm = parseFrontmatter(runCatching { md.readText() }.getOrElse { "" })
                    if (fm != null && fm.second.isNotBlank()) out += toLocal(md, root, global, !insideDisabled)
                }
        }
        entries.filter { it.isDirectory && !it.name.startsWith(".") && it.name != "node_modules" }
            .forEach { sub -> scanRoot(sub, root, global, out, insideDisabled) }
    }

    private fun toLocal(file: File, root: File, global: Boolean, enabled: Boolean): Local {
        val text = runCatching { file.readText() }.getOrElse { "" }
        val fm = parseFrontmatter(text)
        val rel = runCatching { file.relativeTo(root).path.replace('\\', '/') }.getOrElse { file.name }
        return Local(
            file = file,
            root = root,
            relPath = rel,
            name = fm?.first?.takeIf { it.isNotBlank() }
                ?: file.parentFile?.name?.takeIf { file.name == "SKILL.md" } ?: file.nameWithoutExtension,
            desc = fm?.second.orEmpty(),
            global = global,
            enabled = enabled,
            problem = when {
                fm == null -> L.runtime.frontmatterMissingShort
                fm.second.isBlank() -> L.runtime.descriptionEmpty
                else -> null
            },
        )
    }

    /**
     * 极简 frontmatter 解析：只认 `---` 包起来的头部里的 `name:` 与 `description:`
     * （单行值；值两端的引号去掉）。返回 `(name, description)`；没有 frontmatter 返回 null。
     */
    internal fun parseFrontmatter(text: String): Pair<String, String>? {
        val lines = text.replace("\r\n", "\n").lines()
        if (lines.firstOrNull()?.trim() != "---") return null
        var name = ""
        var desc = ""
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.trim() == "---") break
            val key = line.substringBefore(':', "").trim().lowercase()
            val value = line.substringAfter(':', "").trim().trim('"', '\'')
            when (key) {
                "name" -> name = value
                "description" -> desc = value
            }
        }
        return name to desc
    }

    /** 目录名安全化（pi 的技能名规则：小写、连字符；这里做保守归一） */
    internal fun slugOf(raw: String): String = raw.trim().lowercase()
        .replace(Regex("[^a-z0-9·_\\-\\u4e00-\\u9fa5]+"), "-")
        .trim('-')

    /** 全局 skills 根（导入用） */
    fun globalRoot(context: Context): File =
        File(File(PiRuntime.root(context), "rootfs/root"), ".pi/agent/skills")

    /** 项目 skills 根（导入用） */
    fun projectRoot(context: Context): File = File(PiRuntime.workspaceDir(context), ".pi/skills")
}
