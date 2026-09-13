package com.pient.app.data

import android.content.Context
import android.util.Log
import com.pient.app.runtime.PiRuntime
import com.pient.app.runtime.PiTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 应用内工具层（2026-09-14）：**直连路径（pi 宿主没跑）时由 App 自己执行 pi 的七工具**。
 *
 * 为什么需要它：宿主路径（pi agent 循环）自带工具；直连路径过去只把消息发给模型、不带任何工具，
 * 于是「配置里说模型有工具、实际上一个都没有」——模型只能把工具调用写成文本（DeepSeek 的
 * `＜|DSML|invoke ...＞` 标记就是这么漏到回答里的）。
 *
 * 与「模型能力」开关（[ProviderConfig.toolCallEnabled]）的关系，口径对齐 Operit：
 * - 开关**开**：请求带 `tools`，模型走服务商 API 的**原生工具调用**（回包 `tool_calls`）；
 * - 开关**关**：不下发 `tools`，工具说明写进系统提示，模型用**文本标记**调用（软件内机制）；
 * - 两条路都落到本文件的同一个执行器 —— **关掉开关不等于没有工具**（Operit 原文：
 *   「关闭时，会使用软件内工具调用机制，也可以完成工具调用」）。
 *
 * 工具 schema 逐项对齐 pi（`packages/coding-agent/src/core/tools/` 下的七工具文件）：名字、参数、截断口径
 * （2000 行 / 50KB）都照抄，避免同一个工具在两套实现下行为分歧。
 * bash 走终端层已有的通道（[PiTerminal.execOnce] → `pient-shell -c`，工作区 = /workspace）。
 */
object AppTools {

    private const val TAG = "PientTools"

    /** 一轮对话里最多几轮工具调用（防止模型来回空转） */
    const val MAX_ROUNDS = 8

    /** 与 pi 的 truncate.ts 同口径 */
    private const val MAX_LINES = 2000
    private const val MAX_BYTES = 50 * 1024
    private const val LS_LIMIT = 500
    private const val FIND_LIMIT = 1000
    private const val GREP_LIMIT = 100

    /** 七工具（顺序 = [ToolPolicy.TOOLS] 的前七个；android_shell 是宿主扩展工具，直连不提供） */
    val NAMES = listOf("read", "write", "edit", "bash", "grep", "find", "ls")

    /** 一次工具调用（协议层与文本标记层共用；文本标记的 id 是合成的） */
    data class Call(val id: String, val name: String, val arguments: String)

    /** 一次工具执行结果 */
    data class Outcome(val output: String, val isError: Boolean, val diff: String? = null)

    // ───────────────────────── 工具定义（下发用） ─────────────────────────

    /** OpenAI 协议：`tools: [{type:"function", function:{name, description, parameters}}]` */
    fun definitions(): JSONArray {
        val arr = JSONArray()
        for (t in TOOL_SPECS) {
            arr.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", t.name)
                            .put("description", t.description)
                            .put("parameters", t.parameters),
                    ),
            )
        }
        return arr
    }

    /** Anthropic Messages 协议：`tools: [{name, description, input_schema}]` */
    fun anthropicDefinitions(): JSONArray {
        val arr = JSONArray()
        for (t in TOOL_SPECS) {
            arr.put(
                JSONObject()
                    .put("name", t.name)
                    .put("description", t.description)
                    .put("input_schema", t.parameters),
            )
        }
        return arr
    }

    private class Spec(val name: String, val description: String, val parameters: JSONObject)

    private fun prop(type: String, desc: String) = JSONObject().put("type", type).put("description", desc)

    private fun obj(vararg props: Pair<String, JSONObject>, required: List<String> = emptyList()): JSONObject {
        val properties = JSONObject()
        for ((k, v) in props) properties.put(k, v)
        return JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray(required))
    }

    /**
     * 展示用 glob 例子 `**` + `/`：**在源码里拆开拼** —— Kotlin 词法会对字符串里紧邻的
     * `*` `/` 做块注释扫描，`'**' + '/x'` 这种写法会在编译期报 `Syntax error: Expecting a
     * top level declaration`（实测，报错位置指向那个 `/`，整段对象体被吃掉）。
     */
    private val GLOB_ANY_DEPTH = "*" + "*" + "/" + "*"

    /** 定义逐项对齐 pi（描述里的截断口径也一并抄来） */
    private val TOOL_SPECS: List<Spec> = listOf(
        Spec(
            "read",
            "Read the contents of a file. Supports text files. Output is truncated to $MAX_LINES lines " +
                "or ${MAX_BYTES / 1024}KB (whichever is hit first). Use offset/limit for large files.",
            obj(
                "path" to prop("string", "Path to the file to read (relative or absolute)"),
                "offset" to prop("number", "Line number to start reading from (1-indexed)"),
                "limit" to prop("number", "Maximum number of lines to read"),
                required = listOf("path"),
            ),
        ),
        Spec(
            "write",
            "Create or overwrite a file with the given content. Creates parent directories as needed. " +
                "Use write only for new files or complete rewrites.",
            obj(
                "path" to prop("string", "Path to the file to write (relative or absolute)"),
                "content" to prop("string", "Content to write to the file"),
                required = listOf("path", "content"),
            ),
        ),
        Spec(
            "edit",
            "Apply targeted replacements to a file. Each oldText must be unique in the original file " +
                "and must not overlap with other edits in the same call.",
            obj(
                "path" to prop("string", "Path to the file to edit (relative or absolute)"),
                "oldText" to prop("string", "Exact text for one targeted replacement (must be unique in the file)"),
                "newText" to prop("string", "Replacement text for this targeted edit"),
                "edits" to JSONObject()
                    .put("type", "array")
                    .put(
                        "description",
                        "One or more targeted replacements (alternative to oldText/newText). " +
                            "Each edit is matched against the original file, not incrementally.",
                    )
                    .put(
                        "items",
                        obj(
                            "oldText" to prop("string", "Exact text to replace"),
                            "newText" to prop("string", "Replacement text"),
                            required = listOf("oldText", "newText"),
                        ),
                    ),
                required = listOf("path"),
            ),
        ),
        Spec(
            "bash",
            "Execute a bash command in the workspace (Ubuntu). Returns stdout and stderr, truncated to " +
                "the last $MAX_LINES lines or ${MAX_BYTES / 1024}KB. Optionally provide a timeout in seconds.",
            obj(
                "command" to prop("string", "Shell command to execute"),
                "timeout" to prop("number", "Timeout in seconds (optional, default 120)"),
                required = listOf("command"),
            ),
        ),
        Spec(
            "grep",
            "Search file contents for a pattern. Returns matching lines with file paths and line numbers. " +
                "Output is truncated to $GREP_LIMIT matches or ${MAX_BYTES / 1024}KB (whichever is hit first).",
            obj(
                "pattern" to prop("string", "Search pattern (regex or literal string)"),
                "path" to prop("string", "Directory or file to search (default: current directory)"),
                "glob" to prop("string", "Filter files by glob pattern, e.g. '*.md' or 'src" + GLOB_ANY_DEPTH + ".kt'"),
                "ignoreCase" to prop("boolean", "Case-insensitive search (default: false)"),
                "literal" to prop("boolean", "Treat pattern as literal string instead of regex (default: false)"),
                "context" to prop("number", "Number of lines to show before and after each match (default: 0)"),
                "limit" to prop("number", "Maximum number of matches to return (default: $GREP_LIMIT)"),
                required = listOf("pattern"),
            ),
        ),
        Spec(
            "find",
            "Search for files by glob pattern. Returns matching file paths relative to the search directory. " +
                "Output is truncated to $FIND_LIMIT results or ${MAX_BYTES / 1024}KB (whichever is hit first).",
            obj(
                "pattern" to prop("string", "Glob pattern to match files, e.g. '*.md' or '" + GLOB_ANY_DEPTH + ".json'"),
                "path" to prop("string", "Directory to search in (default: current directory)"),
                "limit" to prop("number", "Maximum number of results (default: $FIND_LIMIT)"),
                required = listOf("pattern"),
            ),
        ),
        Spec(
            "ls",
            "List directory contents. Returns entries sorted alphabetically, with '/' suffix for directories. " +
                "Includes dotfiles. Output is truncated to $LS_LIMIT entries.",
            obj(
                "path" to prop("string", "Directory to list (default: current directory)"),
                "limit" to prop("number", "Maximum number of entries to return (default: $LS_LIMIT)"),
            ),
        ),
    )

    // ───────────────────────── 执行 ─────────────────────────

    /** 执行一次工具调用（argsJson = 模型给的参数 JSON；解析失败按空对象处理） */
    suspend fun run(context: Context, name: String, argsJson: String): Outcome =
        withContext(Dispatchers.IO) {
            val args = runCatching { JSONObject(argsJson.ifBlank { "{}" }) }.getOrElse { JSONObject() }
            try {
                when (name) {
                    "read" -> read(context, args)
                    "write" -> write(context, args)
                    "edit" -> edit(context, args)
                    "bash" -> bash(context, args)
                    "grep" -> grep(context, args)
                    "find" -> find(context, args)
                    "ls" -> ls(context, args)
                    else -> Outcome("未知工具：$name（可用：${NAMES.joinToString(", ")}）", true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "工具 $name 执行失败：${e.message}")
                Outcome("工具 $name 执行失败：${e.message ?: e::class.java.simpleName}", true)
            }
        }

    /** 可访问根：工作区 + 应用私有目录（附件、SAF 物化副本都在这两处） */
    private fun allowedRoots(context: Context): List<File> = listOfNotNull(
        PiRuntime.workspaceDir(context),
        PiRuntime.root(context),
        context.cacheDir,
        context.getExternalFilesDir(null),
    )

    private fun canonical(f: File): File = runCatching { f.canonicalFile }.getOrElse { f.absoluteFile }

    /** 路径解析：相对路径一律相对工作区；越界（不在可访问根内）直接拒绝并说明 */
    private fun resolve(context: Context, raw: String?): Pair<File?, String?> {
        val p = raw?.trim()?.trim('"').orEmpty()
        if (p.isEmpty()) return null to "缺少 path 参数"
        val ws = PiRuntime.workspaceDir(context)
        val f = if (File(p).isAbsolute) File(p) else File(ws, p)
        val canon = canonical(f)
        val ok = allowedRoots(context).any { root ->
            val r = canonical(root).path
            canon.path == r || canon.path.startsWith(r + File.separator)
        }
        return if (ok) canon to null else null to
            "路径不可访问：$p（只在工作区与应用目录内可用；当前工作区：${ws.absolutePath}）"
    }

    /** 目录树遍历：跳过 `.git`（与 pi 的 .gitignore 口径同为「不给 agent 翻版本库」） */
    private fun walk(root: File, limit: Int): List<File> {
        val out = ArrayList<File>()
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty() && out.size < limit) {
            val dir = stack.removeLast()
            val children = dir.listFiles()?.sortedBy { it.name } ?: continue
            for (c in children) {
                if (out.size >= limit) break
                if (c.isDirectory) {
                    if (c.name == ".git") continue
                    stack.addLast(c)
                } else {
                    out.add(c)
                }
            }
        }
        return out
    }

    private fun relTo(root: File, f: File): String = runCatching {
        root.canonicalFile.toURI().relativize(f.canonicalFile.toURI()).path
    }.getOrElse { f.name }

    private fun isBinary(f: File): Boolean = runCatching {
        f.inputStream().use { ins ->
            val buf = ByteArray(8192)
            val n = ins.read(buf)
            for (i in 0 until maxOf(0, n)) if (buf[i] == 0.toByte()) return@use true
            false
        }
    }.getOrDefault(false)

    private fun truncate(text: String, note: String): String {
        val lines = text.split("\n")
        var out = text
        var truncated = false
        if (lines.size > MAX_LINES) {
            out = lines.take(MAX_LINES).joinToString("\n")
            truncated = true
        }
        if (out.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            out = out.toByteArray(Charsets.UTF_8).copyOf(MAX_BYTES).toString(Charsets.UTF_8)
            truncated = true
        }
        return if (truncated) "$out\n\n[$note：输出已截断（上限 $MAX_LINES 行 / ${MAX_BYTES / 1024}KB）]" else out
    }

    // ── read ──

    private fun read(context: Context, args: JSONObject): Outcome {
        val (f, err) = resolve(context, args.optString("path"))
        if (f == null) return Outcome(err.orEmpty(), true)
        if (!f.exists()) return Outcome("文件不存在：${f.path}", true)
        if (f.isDirectory) return Outcome("这是一个目录，用 ls 列目录：${f.path}", true)
        val ext = f.name.substringAfterLast('.', "").lowercase()
        if (ext in MEDIA_IMAGE_EXTS) {
            return Outcome("（图片文件，${f.length()} 字节：${f.path}）", false)
        }
        if (isBinary(f)) return Outcome("二进制文件，无法按文本读取：${f.path}", true)
        val all = f.readLines()
        val offset = args.optInt("offset", 1).coerceAtLeast(1)
        val limit = args.optInt("limit", 0).takeIf { it > 0 } ?: Int.MAX_VALUE
        val from = (offset - 1).coerceAtMost(all.size)
        val slice = all.subList(from, minOf(all.size, from + limit))
        val numbered = slice.mapIndexed { i, line -> "${from + i + 1}|$line" }.joinToString("\n")
        val head = if (all.size > slice.size || from > 0) {
            "（共 ${all.size} 行，显示第 ${from + 1}~${from + slice.size} 行）\n"
        } else {
            ""
        }
        return Outcome(truncate(head + numbered, "read"), false)
    }

    // ── write ──

    private fun write(context: Context, args: JSONObject): Outcome {
        val (f, err) = resolve(context, args.optString("path"))
        if (f == null) return Outcome(err.orEmpty(), true)
        if (f.isDirectory) return Outcome("目标是目录，不能写入：${f.path}", true)
        val content = args.optString("content")
        f.parentFile?.mkdirs()
        f.writeText(content)
        return Outcome("已写入 ${f.path}（${content.length} 字符）", false)
    }

    // ── edit ──

    private fun edit(context: Context, args: JSONObject): Outcome {
        val (f, err) = resolve(context, args.optString("path"))
        if (f == null) return Outcome(err.orEmpty(), true)
        if (!f.isFile) return Outcome("文件不存在：${f.path}", true)
        val pairs = ArrayList<Pair<String, String>>()
        // 兼容两种形态：pi 的 edits[] 数组；软件内标记模式下展平的 oldText/newText
        val edits = args.optJSONArray("edits")
        if (edits != null) {
            for (i in 0 until edits.length()) {
                val o = edits.optJSONObject(i) ?: continue
                val old = o.optString("oldText")
                if (old.isEmpty()) continue
                pairs.add(old to o.optString("newText"))
            }
        } else {
            val old = args.optString("oldText")
            if (old.isNotEmpty()) pairs.add(old to args.optString("newText"))
        }
        if (pairs.isEmpty()) return Outcome("缺少 oldText/newText（或 edits 数组）参数", true)

        val original = f.readText()
        var text = original
        var applied = 0
        val diffs = StringBuilder()
        for ((old, new) in pairs) {
            val first = text.indexOf(old)
            if (first < 0) return Outcome("未找到要替换的文本（原文件已保持不变）：\n${old.take(200)}", true)
            if (text.indexOf(old, first + 1) >= 0) {
                return Outcome("要替换的文本在文件中不唯一，请给出更长的上下文（原文件已保持不变）：\n${old.take(200)}", true)
            }
            val lineNo = text.take(first).count { it == '\n' } + 1
            diffs.append(fragmentDiff(relTo(PiRuntime.workspaceDir(context), f), lineNo, old, new))
            text = text.substring(0, first) + new + text.substring(first + old.length)
            applied++
        }
        f.writeText(text)
        return Outcome(
            "已编辑 ${f.path}：$applied 处替换（原文件 ${original.length} 字符 → ${text.length} 字符）",
            false,
            diffs.toString().trim().ifEmpty { null },
        )
    }

    /** 片段级 unified diff（工具卡「文件编辑」用的就是这段文本；与宿主路径 pi 的 details.diff 同形态） */
    private fun fragmentDiff(path: String, lineNo: Int, old: String, new: String): String {
        val oldLines = old.split("\n")
        val newLines = new.split("\n")
        val sb = StringBuilder()
        sb.append("--- a/").append(path).append('\n')
        sb.append("+++ b/").append(path).append('\n')
        sb.append("@@ -").append(lineNo).append(',').append(oldLines.size)
            .append(" +").append(lineNo).append(',').append(newLines.size).append(" @@\n")
        // 相同前缀/后缀直接作为上下文，中间差异整块 -/+
        var head = 0
        while (head < oldLines.size && head < newLines.size && oldLines[head] == newLines[head]) head++
        var tail = 0
        while (
            tail < oldLines.size - head && tail < newLines.size - head &&
            oldLines[oldLines.size - 1 - tail] == newLines[newLines.size - 1 - tail]
        ) tail++
        for (i in 0 until head) sb.append(' ').append(oldLines[i]).append('\n')
        for (i in head until oldLines.size - tail) sb.append('-').append(oldLines[i]).append('\n')
        for (i in head until newLines.size - tail) sb.append('+').append(newLines[i]).append('\n')
        for (i in oldLines.size - tail until oldLines.size) sb.append(' ').append(oldLines[i]).append('\n')
        return sb.toString()
    }

    // ── bash ──

    private fun bash(context: Context, args: JSONObject): Outcome {
        val cmd = args.optString("command").trim()
        if (cmd.isEmpty()) return Outcome("缺少 command 参数", true)
        if (!PiRuntime.shellPath(context).isFile) {
            return Outcome("bash 不可用：终端层未随包就绪（缺少 pient-shell）", true)
        }
        if (!PiRuntime.rootfsReady(context)) {
            return Outcome(
                "bash 不可用：Ubuntu 终端环境尚未就绪（rootfs 未解包）——" +
                    "请到「终端 → 环境配置」点「一键配置/安装所选」后重试",
                true,
            )
        }
        val timeoutS = args.optDouble("timeout", 0.0).takeIf { it > 0 }?.toLong() ?: 120L
        val out = PiTerminal.execOnce(context, cmd, (timeoutS * 1000).coerceIn(5_000, 600_000))
        val body = out.trim().ifEmpty { "（命令无输出）" }
        return Outcome(truncate(body, "bash"), false)
    }

    // ── ls ──

    private fun ls(context: Context, args: JSONObject): Outcome {
        val raw = args.optString("path").takeIf { it.isNotBlank() } ?: "."
        val (dir, err) = resolve(context, raw)
        if (dir == null) return Outcome(err.orEmpty(), true)
        if (!dir.isDirectory) return Outcome("不是目录：${dir.path}", true)
        val limit = args.optInt("limit", LS_LIMIT).takeIf { it > 0 } ?: LS_LIMIT
        val entries = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
        val shown = entries.take(limit)
        val lines = shown.map { if (it.isDirectory) it.name + "/" else it.name }
        val head = if (entries.size > shown.size) "（共 ${entries.size} 项，显示前 ${shown.size} 项）\n" else ""
        return Outcome(truncate(head + lines.joinToString("\n"), "ls"), false)
    }

    // ── find ──

    private fun find(context: Context, args: JSONObject): Outcome {
        val pattern = args.optString("pattern").trim()
        if (pattern.isEmpty()) return Outcome("缺少 pattern 参数", true)
        val raw = args.optString("path").takeIf { it.isNotBlank() } ?: "."
        val (dir, err) = resolve(context, raw)
        if (dir == null) return Outcome(err.orEmpty(), true)
        if (!dir.isDirectory) return Outcome("不是目录：${dir.path}", true)
        val limit = args.optInt("limit", FIND_LIMIT).takeIf { it > 0 } ?: FIND_LIMIT
        val re = globRegex(pattern)
        val matches = walk(dir, 20_000).asSequence()
            .map { it to relTo(dir, it) }
            .filter { (f, rel) -> re.matches(rel) || (!pattern.contains('/') && re.matches(f.name)) }
            .take(limit)
            .toList()
        if (matches.isEmpty()) return Outcome("没有匹配 $pattern 的文件（搜索目录：${dir.path}）", false)
        return Outcome(truncate(matches.joinToString("\n") { it.second }, "find"), false)
    }

    /** glob → 正则：`**` = 任意层、`*` = 单层通配、`?` = 单字符 */
    private fun globRegex(glob: String): Regex {
        val sb = StringBuilder()
        var i = 0
        val g = if (glob.startsWith("**/")) glob.removePrefix("**/") else glob
        val anyDepth = glob.startsWith("**/")
        while (i < g.length) {
            val c = g[i]
            when {
                c == '*' && i + 1 < g.length && g[i + 1] == '*' -> {
                    sb.append(".*"); i++
                }
                c == '*' -> sb.append("[^/]*")
                c == '?' -> sb.append("[^/]")
                c in "\\.[]{}()+^$|" -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
            i++
        }
        val body = sb.toString()
        return if (anyDepth) Regex("(?:.*/)?$body") else Regex(body)
    }

    // ── grep ──

    private fun grep(context: Context, args: JSONObject): Outcome {
        val pattern = args.optString("pattern")
        if (pattern.isEmpty()) return Outcome("缺少 pattern 参数", true)
        val raw = args.optString("path").takeIf { it.isNotBlank() } ?: "."
        val (target, err) = resolve(context, raw)
        if (target == null) return Outcome(err.orEmpty(), true)
        val literal = args.optBoolean("literal", false)
        val ignoreCase = args.optBoolean("ignoreCase", false)
        val contextLines = args.optInt("context", 0).coerceIn(0, 10)
        val limit = args.optInt("limit", GREP_LIMIT).takeIf { it > 0 } ?: GREP_LIMIT
        val glob = args.optString("glob").takeIf { it.isNotBlank() }
        val globRe = glob?.let { globRegex(it) }

        val root = if (target.isDirectory) target else target.parentFile ?: target
        val candidates = if (target.isFile) listOf(target) else walk(target, 20_000)
        val re = try {
            Regex(
                if (literal) Regex.escape(pattern) else pattern,
                if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet(),
            )
        } catch (e: Exception) {
            return Outcome("正则表达式无效：${e.message}", true)
        }

        val sb = StringBuilder()
        var hits = 0
        var files = 0
        for (f in candidates) {
            if (hits >= limit) break
            if (f.length() > 2 * 1024 * 1024L) continue
            if (globRe != null && !(globRe.matches(relTo(root, f)) || globRe.matches(f.name))) continue
            if (isBinary(f)) continue
            val lines = runCatching { f.readLines() }.getOrNull() ?: continue
            val rel = relTo(root, f)
            var matchedInFile = false
            for ((idx, line) in lines.withIndex()) {
                if (!re.containsMatchIn(line)) continue
                if (!matchedInFile) { matchedInFile = true; files++ }
                val from = (idx - contextLines).coerceAtLeast(0)
                val to = (idx + contextLines).coerceAtMost(lines.size - 1)
                for (i in from..to) {
                    val sep = if (i == idx) ":" else "-"
                    sb.append(rel).append(sep).append(i + 1).append(sep).append(lines[i].take(500)).append('\n')
                }
                hits++
                if (hits >= limit) break
            }
        }
        if (hits == 0) return Outcome("没有匹配：$pattern（目录：${root.path}）", false)
        val head = "（$files 个文件命中 $hits 行）\n"
        return Outcome(truncate(head + sb.toString().trimEnd(), "grep"), false)
    }

    /**
     * 流式显示用：把「可能正在到来的工具标记」之前的内容交出去 —— **标记本身一个字都不显示**。
     *
     * 为什么需要（2026-09-14 用户报「工具调用直接输出在回复里」）：DSML 标记是**逐片流进来**的，
     * 整轮结束才做 [extractTextCalls] 会晚一步 —— 流式期间气泡里会先把 `<||DSML||invoke …>` 打出来
     * （用户看到的就是「回复里直接输出命令」）。这里在最早一个标记起始处截断：
     * 标记之前的正文照常流式显示，标记之后一律不显示，等这一轮结束由 [extractTextCalls] 定性
     * （真调用 → 落工具卡；空标记 → 正文什么都不剩）。
     */
    fun safeStreamText(text: String): String {
        val idx = firstMarkerIndex(text)
        return if (idx >= 0) text.substring(0, idx).trimEnd() else text
    }

    /** 最早出现的工具标记起始位置（-1 = 没有）；覆盖 DSML 三变体与契约写法 */
    private fun firstMarkerIndex(text: String): Int {
        val candidates = listOf("<|DSML", "<｜DSML", "<||DSML", "<tool_call", "<invoke")
        var best = -1
        for (c in candidates) {
            val i = text.indexOf(c, ignoreCase = true)
            if (i >= 0 && (best < 0 || i < best)) best = i
        }
        return best
    }

    /** 正文里是否含工具标记（宿主路径的最终文本也要过这道：漏出来的标记不许当正文渲染） */
    fun hasToolMarkup(text: String): Boolean = firstMarkerIndex(text) >= 0

    // ───────────────────────── 系统提示词 ─────────────────────────

    /**
     * 直连路径的系统提示词（宿主路径的提示词由 pi 自己构建，与此无关）。
     *
     * @param workspace 工作区绝对路径（相对路径的解析基准、bash 的 cwd）
     * @param nativeTools true = 工具经服务商 API 原生下发（开关开）；false = 用下方标记契约调用（开关关）
     */
    fun systemPrompt(workspace: String, nativeTools: Boolean): String {
        val sb = StringBuilder()
        sb.append("You are Pient's on-device agent, running inside the Pient Android app. ")
        sb.append("The app itself calls the model API (direct-connection mode), and the app executes your tool calls.\n\n")
        sb.append("Environment:\n")
        sb.append("- OS: Android (app sandbox). Shell commands run inside the bundled Ubuntu workspace.\n")
        sb.append("- Working directory (workspace): ").append(workspace).append('\n')
        sb.append("- Relative paths are resolved against the workspace; bash starts there.\n")
        sb.append("- Files outside the workspace and the app's own directory are not reachable in this mode.\n\n")
        if (nativeTools) {
            sb.append("Tools are declared through the provider's native tool-calling API — call them the normal way. ")
            sb.append("read/write/edit/ls/find/grep run in the app; bash runs in the Ubuntu workspace.\n\n")
        } else {
            sb.append("Tools are NOT declared through the API. To use a tool, output one or more blocks in exactly ")
            sb.append("this form (the app runs them and sends the results back as a user message):\n\n")
            sb.append("<tool_call>\n")
            sb.append("<invoke name=\"read\"><parameter name=\"path\">notes.md</parameter></invoke>\n")
            sb.append("</tool_call>\n\n")
            sb.append("Available tools:\n")
            sb.append("- read: path, offset?, limit? — read a text file\n")
            sb.append("- write: path, content — create or overwrite a file\n")
            sb.append("- edit: path, oldText, newText — exact single replacement (oldText must be unique)\n")
            sb.append("- bash: command, timeout? — run a bash command in the workspace (Ubuntu)\n")
            sb.append("- grep: pattern, path?, glob?, ignoreCase?, literal?, context?, limit? — search file contents\n")
            sb.append("- find: pattern, path?, limit? — find files by glob pattern\n")
            sb.append("- ls: path?, limit? — list a directory\n\n")
            sb.append("After the tool results come back, continue the task; when you are done, answer normally ")
            sb.append("without any tool markup.\n\n")
        }
        sb.append("Guidelines:\n")
        sb.append("- Work autonomously: break the task into steps, use the tools, verify the result before reporting.\n")
        sb.append("- Never fabricate file contents, command output, or results you did not actually produce.\n")
        sb.append("- Match the user's language in your replies.")
        return sb.toString()
    }

    // ───────────────────────── 文本形态的工具调用（软件内机制 / DSML 兜底） ─────────────────────────

    private val DSML_OPEN = Regex("<[|｜]{1,2}DSML[|｜]{1,2}")
    private val DSML_CLOSE = Regex("</[|｜]{1,2}DSML[|｜]{1,2}")
    private val INVOKE = Regex("<invoke\\s+name\\s*=\\s*\"([^\"]+)\"\\s*>(.*?)</invoke>", RegexOption.DOT_MATCHES_ALL)
    private val PARAM = Regex("<parameter\\s+name\\s*=\\s*\"([^\"]+)\"[^>]*>(.*?)</parameter>", RegexOption.DOT_MATCHES_ALL)
    private val ARGUMENTS = Regex("<arguments\\s*>(.*?)</arguments>", RegexOption.DOT_MATCHES_ALL)
    private val WRAPPERS = listOf("<tool_calls>", "</tool_calls>", "<tool_call>", "</tool_call>")

    /**
     * 从助手正文里抽出**文本形态的工具调用**，并把标记从正文里剔除。
     *
     * 为什么需要兜底：DeepSeek 系模型即使声明了工具，也可能把调用写成 DSML 标记漏进正文
     * （上游已知问题：`＜|DSML|tool_calls＞` 的开始标记缺失/写错时解析器漏掉，标记当正文返回），
     * 用户看到的就是「回答里直接输出命令」。这里把它认回来执行，正文只留模型真正说的话。
     *
     * 认三种写法：① DSML（全角 `｜DSML｜`、半角 `|DSML|`、`||DSML||` 变体都吃）；② 本契约的
     * `<tool_call><invoke …>`；③ 裸 `<invoke …>`。
     *
     * @return (清理后的正文, 抽出的调用列表)
     */
    fun extractTextCalls(text: String): Pair<String, List<Call>> {
        if (text.isBlank()) return text to emptyList()
        val hasHint = text.contains("DSML", ignoreCase = true) ||
            text.contains("<invoke", ignoreCase = true) ||
            text.contains("<tool_call", ignoreCase = true)
        if (!hasHint) return text to emptyList()

        // ① 归一化 DSML 标记：`<|DSML|invoke>` → `<invoke>`、`</|DSML|parameter>` → `</parameter>`
        var norm = DSML_CLOSE.replace(text, "</")
        norm = DSML_OPEN.replace(norm, "<")

        val calls = ArrayList<Call>()
        val spans = ArrayList<IntRange>()
        var n = 0
        for (m in INVOKE.findAll(norm)) {
            val name = m.groupValues[1].trim()
            val body = m.groupValues[2]
            val args = JSONObject()
            var usedArguments = false
            ARGUMENTS.find(body)?.let { am ->
                runCatching { JSONObject(am.groupValues[1].trim()) }
                    .onSuccess { parsed -> for (k in parsed.keys()) args.put(k, parsed.get(k)); usedArguments = true }
            }
            if (!usedArguments) {
                for (p in PARAM.findAll(body)) {
                    args.put(p.groupValues[1].trim(), p.groupValues[2].trim())
                }
            }
            if (name.isNotEmpty()) {
                calls.add(Call("call_text_${n++}", name, args.toString()))
                spans.add(m.range)
            }
        }
        var cleaned = norm
        for (r in spans.sortedByDescending { it.first }) {
            cleaned = cleaned.removeRange(r)
        }
        for (w in WRAPPERS) cleaned = cleaned.replace(w, "")
        cleaned = cleaned.trim()
        // 标记被清空后只剩代码围栏的行（模型常把标记包在 ``` 里）也一并收掉
        if (cleaned.lines().all { it.isBlank() || it.trim().startsWith("```") }) cleaned = ""
        return cleaned to calls
    }
}
