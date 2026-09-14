package com.pient.app.tools.files

import android.content.Context
import com.pient.app.data.DocxConverter
import com.pient.app.data.MEDIA_IMAGE_EXTS
import com.pient.app.runtime.PiRuntime
import com.pient.app.tools.GLOB_ANY_DEPTH
import com.pient.app.tools.ToolLayer
import com.pient.app.tools.ToolOutcome
import com.pient.app.tools.ToolPart
import com.pient.app.tools.ToolSpec
import com.pient.app.tools.Truncate
import org.json.JSONObject
import java.io.File

/**
 * **③ 自身工具层** —— App 的手脚：`read` / `write` / `edit` / `grep` / `find` / `ls`。
 *
 * 边界（用户口径）：**普通文件操作原生直读，根本不走 shell**。所以这一层不碰 PRoot、
 * 不碰 Shizuku、不起进程；路径门控走 [PathGuard]，与附件 / @ 引用同一套可访问根。
 *
 * 与 pi 的对应关系：这六个工具就是 pi 官方 `core/tools` 的七工具（去掉 `bash`——它属于
 * 终端层，见 [com.pient.app.tools.terminal.TerminalPart]）。**截断口径与参数 schema 逐项对齐
 * pi**（2000 行 / 50KB / 各 list 上限），M2 起改为直接跑 pi 官方那份工具代码（工具包运行时），
 * 本文件随之降级为「路径门控 + 原生文件原语」的提供方。
 */
object FilesPart : ToolPart {

    override val layer = ToolLayer.APP

    /** 截断口径与 pi 的 truncate.ts 同源（唯一实现在 [com.pient.app.tools.Truncate]） */
    private const val MAX_LINES = Truncate.MAX_LINES
    private const val MAX_BYTES = Truncate.MAX_BYTES
    private const val LS_LIMIT = 500
    private const val FIND_LIMIT = 1000
    private const val GREP_LIMIT = 100

    override fun specs(): List<ToolSpec> = listOf(
        ToolSpec(
            "read",
            "读取文件",
            "Read the contents of a file. Supports text files. Output is truncated to $MAX_LINES lines " +
                "or ${MAX_BYTES / 1024}KB (whichever is hit first). Use offset/limit for large files.",
            ToolSpec.obj(
                "path" to ToolSpec.prop("string", "Path to the file to read (relative or absolute)"),
                "offset" to ToolSpec.prop("number", "Line number to start reading from (1-indexed)"),
                "limit" to ToolSpec.prop("number", "Maximum number of lines to read"),
                required = listOf("path"),
            ),
            layer,
        ),
        ToolSpec(
            "write",
            "写入文件",
            "Create or overwrite a file with the given content. Creates parent directories as needed. " +
                "Use write only for new files or complete rewrites.",
            ToolSpec.obj(
                "path" to ToolSpec.prop("string", "Path to the file to write (relative or absolute)"),
                "content" to ToolSpec.prop("string", "Content to write to the file"),
                required = listOf("path", "content"),
            ),
            layer,
        ),
        ToolSpec(
            "edit",
            "编辑文件",
            "Apply targeted replacements to a file. Each oldText must be unique in the original file " +
                "and must not overlap with other edits in the same call.",
            ToolSpec.obj(
                "path" to ToolSpec.prop("string", "Path to the file to edit (relative or absolute)"),
                "oldText" to ToolSpec.prop("string", "Exact text for one targeted replacement (must be unique in the file)"),
                "newText" to ToolSpec.prop("string", "Replacement text for this targeted edit"),
                "edits" to JSONObject()
                    .put("type", "array")
                    .put(
                        "description",
                        "One or more targeted replacements (alternative to oldText/newText). " +
                            "Each edit is matched against the original file, not incrementally.",
                    )
                    .put(
                        "items",
                        ToolSpec.obj(
                            "oldText" to ToolSpec.prop("string", "Exact text to replace"),
                            "newText" to ToolSpec.prop("string", "Replacement text"),
                            required = listOf("oldText", "newText"),
                        ),
                    ),
                required = listOf("path"),
            ),
            layer,
        ),
        ToolSpec(
            "grep",
            "内容搜索",
            "Search file contents for a pattern. Returns matching lines with file paths and line numbers. " +
                "Output is truncated to $GREP_LIMIT matches or ${MAX_BYTES / 1024}KB (whichever is hit first).",
            ToolSpec.obj(
                "pattern" to ToolSpec.prop("string", "Search pattern (regex or literal string)"),
                "path" to ToolSpec.prop("string", "Directory or file to search (default: current directory)"),
                "glob" to ToolSpec.prop("string", "Filter files by glob pattern, e.g. '*.md' or 'src" + GLOB_ANY_DEPTH + ".kt'"),
                "ignoreCase" to ToolSpec.prop("boolean", "Case-insensitive search (default: false)"),
                "literal" to ToolSpec.prop("boolean", "Treat pattern as literal string instead of regex (default: false)"),
                "context" to ToolSpec.prop("number", "Number of lines to show before and after each match (default: 0)"),
                "limit" to ToolSpec.prop("number", "Maximum number of matches to return (default: $GREP_LIMIT)"),
                required = listOf("pattern"),
            ),
            layer,
        ),
        ToolSpec(
            "find",
            "按名找文件",
            "Search for files by glob pattern. Returns matching file paths relative to the search directory. " +
                "Output is truncated to $FIND_LIMIT results or ${MAX_BYTES / 1024}KB (whichever is hit first).",
            ToolSpec.obj(
                "pattern" to ToolSpec.prop("string", "Glob pattern to match files, e.g. '*.md' or '" + GLOB_ANY_DEPTH + ".json'"),
                "path" to ToolSpec.prop("string", "Directory to search in (default: current directory)"),
                "limit" to ToolSpec.prop("number", "Maximum number of results (default: $FIND_LIMIT)"),
                required = listOf("pattern"),
            ),
            layer,
        ),
        ToolSpec(
            "ls",
            "列目录",
            "List directory contents. Returns entries sorted alphabetically, with '/' suffix for directories. " +
                "Includes dotfiles. Output is truncated to $LS_LIMIT entries.",
            ToolSpec.obj(
                "path" to ToolSpec.prop("string", "Directory to list (default: current directory)"),
                "limit" to ToolSpec.prop("number", "Maximum number of entries to return (default: $LS_LIMIT)"),
            ),
            layer,
        ),
    )

    override suspend fun run(context: Context, name: String, args: JSONObject): ToolOutcome = when (name) {
        "read" -> read(context, args)
        "write" -> write(context, args)
        "edit" -> edit(context, args)
        "grep" -> grep(context, args)
        "find" -> find(context, args)
        "ls" -> ls(context, args)
        else -> ToolOutcome.err("自身工具层没有这个工具：$name")
    }

    // ───────────────────────── 共用小工具 ─────────────────────────

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

    /** glob → 正则：`**` = 任意层、`*` = 单层通配、`?` = 单字符 */
    private fun globRegex(glob: String): Regex {
        val sb = StringBuilder()
        var i = 0
        val g = if (glob.startsWith("*" + "*/")) glob.removePrefix("*" + "*/") else glob
        val anyDepth = glob.startsWith("*" + "*/")
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

    // ── read ──

    private fun read(context: Context, args: JSONObject): ToolOutcome {
        val (f, err) = PathGuard.resolve(context, args.optString("path"))
        if (f == null) return ToolOutcome.err(err.orEmpty())
        if (!f.exists()) return ToolOutcome.err("文件不存在：${f.path}")
        if (f.isDirectory) return ToolOutcome.err("这是一个目录，用 ls 列目录：${f.path}")
        val ext = f.name.substringAfterLast('.', "").lowercase()
        if (ext in MEDIA_IMAGE_EXTS) {
            return ToolOutcome.ok("（图片文件，${f.length()} 字节：${f.path}）")
        }
        // 文档类附件（docx）：用户「+」上传的文档要让 AI 读得出内容，复用 UI 侧同一个解析器
        if (ext == "docx") {
            val text = DocxConverter.toPlainText(context, f.path)
            if (!text.isNullOrBlank()) {
                return ToolOutcome.ok("（docx 已抽取为文本）\n" + truncate(text, "read"))
            }
        }
        if (isBinary(f)) {
            return ToolOutcome.err("二进制文件，无法按文本读取：${f.path}（docx 可读；doc/xlsx/pdf 等暂不支持）")
        }
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
        return ToolOutcome.ok(truncate(head + numbered, "read"))
    }

    // ── write ──

    private fun write(context: Context, args: JSONObject): ToolOutcome {
        val (f, err) = PathGuard.resolve(context, args.optString("path"))
        if (f == null) return ToolOutcome.err(err.orEmpty())
        if (f.isDirectory) return ToolOutcome.err("目标是目录，不能写入：${f.path}")
        val content = args.optString("content")
        f.parentFile?.mkdirs()
        f.writeText(content)
        return ToolOutcome.ok("已写入 ${f.path}（${content.length} 字符）")
    }

    // ── edit ──

    private fun edit(context: Context, args: JSONObject): ToolOutcome {
        val (f, err) = PathGuard.resolve(context, args.optString("path"))
        if (f == null) return ToolOutcome.err(err.orEmpty())
        if (!f.isFile) return ToolOutcome.err("文件不存在：${f.path}")
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
        if (pairs.isEmpty()) return ToolOutcome.err("缺少 oldText/newText（或 edits 数组）参数")

        val original = f.readText()
        var text = original
        var applied = 0
        val diffs = StringBuilder()
        for ((old, new) in pairs) {
            val first = text.indexOf(old)
            if (first < 0) return ToolOutcome.err("未找到要替换的文本（原文件已保持不变）：\n${old.take(200)}")
            if (text.indexOf(old, first + 1) >= 0) {
                return ToolOutcome.err("要替换的文本在文件中不唯一，请给出更长的上下文（原文件已保持不变）：\n${old.take(200)}")
            }
            val lineNo = text.take(first).count { it == '\n' } + 1
            diffs.append(fragmentDiff(relTo(PiRuntime.workspaceDir(context), f), lineNo, old, new))
            text = text.substring(0, first) + new + text.substring(first + old.length)
            applied++
        }
        f.writeText(text)
        return ToolOutcome(
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

    // ── ls ──

    private fun ls(context: Context, args: JSONObject): ToolOutcome {
        val raw = args.optString("path").takeIf { it.isNotBlank() } ?: "."
        val (dir, err) = PathGuard.resolve(context, raw)
        if (dir == null) return ToolOutcome.err(err.orEmpty())
        if (!dir.isDirectory) return ToolOutcome.err("不是目录：${dir.path}")
        val limit = args.optInt("limit", LS_LIMIT).takeIf { it > 0 } ?: LS_LIMIT
        val entries = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
        val shown = entries.take(limit)
        val lines = shown.map { if (it.isDirectory) it.name + "/" else it.name }
        val head = if (entries.size > shown.size) "（共 ${entries.size} 项，显示前 ${shown.size} 项）\n" else ""
        return ToolOutcome.ok(truncate(head + lines.joinToString("\n"), "ls"))
    }

    // ── find ──

    private fun find(context: Context, args: JSONObject): ToolOutcome {
        val pattern = args.optString("pattern").trim()
        if (pattern.isEmpty()) return ToolOutcome.err("缺少 pattern 参数")
        val raw = args.optString("path").takeIf { it.isNotBlank() } ?: "."
        val (dir, err) = PathGuard.resolve(context, raw)
        if (dir == null) return ToolOutcome.err(err.orEmpty())
        if (!dir.isDirectory) return ToolOutcome.err("不是目录：${dir.path}")
        val limit = args.optInt("limit", FIND_LIMIT).takeIf { it > 0 } ?: FIND_LIMIT
        val re = globRegex(pattern)
        val matches = walk(dir, 20_000).asSequence()
            .map { it to relTo(dir, it) }
            .filter { (f, rel) -> re.matches(rel) || (!pattern.contains('/') && re.matches(f.name)) }
            .take(limit)
            .toList()
        if (matches.isEmpty()) return ToolOutcome.ok("没有匹配 $pattern 的文件（搜索目录：${dir.path}）")
        return ToolOutcome.ok(truncate(matches.joinToString("\n") { it.second }, "find"))
    }

    // ── grep ──

    private fun grep(context: Context, args: JSONObject): ToolOutcome {
        val pattern = args.optString("pattern")
        if (pattern.isEmpty()) return ToolOutcome.err("缺少 pattern 参数")
        val raw = args.optString("path").takeIf { it.isNotBlank() } ?: "."
        val (target, err) = PathGuard.resolve(context, raw)
        if (target == null) return ToolOutcome.err(err.orEmpty())
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
            return ToolOutcome.err("正则表达式无效：${e.message}")
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
        if (hits == 0) return ToolOutcome.ok("没有匹配：$pattern（目录：${root.path}）")
        val head = "（$files 个文件命中 $hits 行）\n"
        return ToolOutcome.ok(truncate(head + sb.toString().trimEnd(), "grep"))
    }
}
