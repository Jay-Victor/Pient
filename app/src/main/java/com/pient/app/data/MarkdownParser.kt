package com.pient.app.data

/**
 * Markdown（GFM 子集）解析 —— 口径对齐 pi-web 文件预览（react-markdown + remark-gfm + remark-frontmatter）：
 *
 * 块级：ATX/Setext 标题（1~6 级）、段落（软换行折叠为空格、硬换行保留）、围栏代码块（含 ``` / ~~~ 与 info 串）、
 * 引用（可嵌套）、列表（有序/无序/嵌套/任务项/loose 空行）、GFM 表格（含对齐行）、分隔线、YAML frontmatter；
 * 行内：粗体、斜体、粗斜体、删除线、行内代码、链接、图片、自动链接、硬换行、反斜杠转义、常见 HTML 实体。
 *
 * 本层是纯 Kotlin（零 Compose 依赖），解析结果由 `ui/components/MarkdownText.kt` 渲染；
 * 未覆盖：内联 HTML 块、引用式链接（`[x][ref]`）、数学公式（KaTeX）与 Mermaid —— 与 pi-web 的差距记在该组件的注释里。
 */

// ───────────────────────────── 数据模型 ─────────────────────────────

/** GFM 表格列对齐（分隔行 `:---` / `:--:` / `---:`） */
enum class MdAlign { LEFT, CENTER, RIGHT }

/** 行内片段（扁平结构：嵌套强调由标志位叠加表达） */
data class MdSpan(
    val text: String = "",
    /** 粗体（`**x**` / `__x__`） */
    val bold: Boolean = false,
    /** 斜体（`*x*` / `_x_`） */
    val italic: Boolean = false,
    /** 删除线（`~~x~~`，GFM） */
    val strike: Boolean = false,
    /** 行内代码（`` `x` ``） */
    val code: Boolean = false,
    /** 链接地址（`[label](url)` / 自动链接）；非空即该片段可点 */
    val link: String? = null,
    /** 图片地址（`![alt](url)`；此时 [text] = alt 文本） */
    val image: String? = null,
    /** 硬换行（行尾两空格或反斜杠）——此片段无文本，仅表示一次换行 */
    val lineBreak: Boolean = false,
)

/** 列表项：首段文本 + 该条目下的其它块（嵌套列表 / 代码块 / 引用等） */
data class MdListItem(
    val text: String,
    val children: List<MdBlock> = emptyList(),
    /** 非空 = 任务项（`- [ ]` / `- [x]`） */
    val task: Boolean? = null,
    val checked: Boolean = false,
)

sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class Code(val lang: String, val code: String) : MdBlock()
    data class Quote(val blocks: List<MdBlock>) : MdBlock()
    data class ListBlock(val ordered: Boolean, val start: Int, val items: List<MdListItem>) : MdBlock()
    data class Table(val head: List<String>, val align: List<MdAlign>, val rows: List<List<String>>) : MdBlock()
    data object Hr : MdBlock()
}

/** YAML frontmatter（渲染为 pi-web FrontmatterCard 同构卡片；无 frontmatter 时为 null） */
data class MdFrontmatter(
    val title: String?,
    val tags: List<String>,
    val rows: List<Pair<String, String>>,
)

data class MarkdownDoc(val frontmatter: MdFrontmatter?, val blocks: List<MdBlock>)

// ───────────────────────────── 入口 ─────────────────────────────

fun parseMarkdown(source: String): MarkdownDoc {
    val text = source.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
    val lines = text.split("\n")
    var start = 0
    var front: MdFrontmatter? = null

    // frontmatter：首行 `---` + 后续某行 `---`（pi-web extractFrontmatter 同款判定）
    if (lines.firstOrNull()?.trimEnd() == "---") {
        val close = (1 until lines.size).firstOrNull { lines[it].trimEnd() == "---" }
        if (close != null) {
            front = parseFrontmatter(lines.subList(1, close))
            start = close + 1
        }
    }
    return MarkdownDoc(front, parseMdBlocks(lines.subList(start, lines.size)))
}

// ───────────────────────────── 块级解析 ─────────────────────────────

fun parseMdBlocks(lines: List<String>): List<MdBlock> = BlockParser(lines).parse()

private class BlockParser(private val lines: List<String>) {
    private var i = 0

    fun parse(): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        while (i < lines.size) {
            val line = lines[i]
            val fence = fenceInfo(line)
            val heading = atxHeading(line)
            when {
                line.isBlank() -> i++
                fence != null -> out += readFence(fence)
                hrLine(line) -> { out += MdBlock.Hr; i++ }
                heading != null -> { out += MdBlock.Heading(heading.first, heading.second); i++ }
                quoteContent(line) != null -> out += MdBlock.Quote(parseMdBlocks(readQuote()))
                listMarker(line) != null && !hrLine(line) -> out += readList()
                tableStart() -> {
                    val table = readTable()
                    if (table != null) out += table else readParagraphAndSetext()?.let { out += it }
                }
                else -> readParagraphAndSetext()?.let { out += it }
            }
        }
        return out
    }

    // ── 段落（含 Setext 标题 `===` / `---`）──
    private fun readParagraphAndSetext(): MdBlock? {
        val buf = ArrayList<String>()
        while (i < lines.size && lines[i].isNotBlank() && !isBlockStartLine(lines[i])) {
            buf += lines[i]
            i++
        }
        if (buf.isEmpty()) {
            // 兜底：本行是块起始却被判为段落（避免死循环）
            if (i < lines.size && lines[i].isNotBlank()) { buf += lines[i]; i++ } else return null
        }
        // Setext：单行段落 + 紧跟的 `===` / `---` 下划线
        if (buf.size == 1 && i < lines.size) {
            val underline = setextUnderline(lines[i])
            if (underline != null) {
                i++
                return MdBlock.Heading(underline, buf[0].trim())
            }
        }
        return MdBlock.Paragraph(buf.joinToString("\n").trim())
    }

    // ── 围栏代码块 ──
    private fun readFence(open: FenceInfo): MdBlock {
        i++
        val body = ArrayList<String>()
        while (i < lines.size) {
            val closing = fenceInfo(lines[i])
            if (closing != null && closing.marker == open.marker && closing.length >= open.length &&
                closing.info.isEmpty()
            ) {
                i++
                break
            }
            body += lines[i]
            i++
        }
        return MdBlock.Code(open.info, body.joinToString("\n"))
    }

    // ── 引用：收集 `>` 行（含懒续行），剥掉标记后递归解析 ──
    private fun readQuote(): List<String> {
        val out = ArrayList<String>()
        while (i < lines.size) {
            val line = lines[i]
            val content = quoteContent(line)
            when {
                content != null -> { out += content; i++ }
                line.isBlank() -> {
                    val next = lines.getOrNull(i + 1)
                    if (next != null && quoteContent(next) != null) { out += ""; i++ } else break
                }
                // 懒续行：非块起始的普通文本行归上一个引用段落
                !isBlockStartLine(line) && out.isNotEmpty() && out.last().isNotBlank() -> { out += line; i++ }
                else -> break
            }
        }
        return out
    }

    // ── 列表 ──
    private fun readList(): MdBlock {
        val first = listMarker(lines[i])!!
        val items = ArrayList<MdListItem>()
        while (i < lines.size) {
            val marker = listMarker(lines[i]) ?: break
            if (marker.indent != first.indent || marker.ordered != first.ordered) break

            var head = marker.content
            var task: Boolean? = null
            var checked = false
            taskMarker(head)?.let { (isTask, isChecked, rest) ->
                task = isTask; checked = isChecked; head = rest
            }

            val body = ArrayList<String>()
            body += head
            i++
            while (i < lines.size) {
                val line = lines[i]
                if (line.isBlank()) {
                    var j = i + 1
                    while (j < lines.size && lines[j].isBlank()) j++
                    val nextLine = lines.getOrNull(j)
                    if (nextLine != null && indentOf(nextLine) >= marker.contentIndent) {
                        body += ""; i++
                        continue
                    }
                    break
                }
                val next = listMarker(line)
                val indent = indentOf(line)
                when {
                    indent >= marker.contentIndent -> { body += dedent(line, marker.contentIndent); i++ }
                    // 懒续行：同级/更浅缩进的普通文本（非块起始）仍属本条目
                    next == null && indent > first.indent && !isBlockStartLine(line) -> {
                        body += line.trimStart(); i++
                    }
                    next == null && indent <= first.indent && !isBlockStartLine(line) &&
                        body.lastOrNull()?.isNotBlank() == true -> { body += line.trimStart(); i++ }
                    else -> break
                }
            }

            val blocks = parseMdBlocks(body).filterNot { it is MdBlock.Paragraph && it.text.isBlank() }
            val para = blocks.firstOrNull() as? MdBlock.Paragraph
            items += MdListItem(
                text = para?.text ?: "",
                children = if (para != null) blocks.drop(1) else blocks,
                task = task,
                checked = checked,
            )
        }
        return MdBlock.ListBlock(ordered = first.ordered, start = first.number, items = items)
    }

    // ── GFM 表格 ──
    private fun tableStart(): Boolean {
        val head = lines.getOrNull(i) ?: return false
        val delim = lines.getOrNull(i + 1) ?: return false
        if (!head.contains('|') || head.isBlank()) return false
        return parseDelimiterRow(delim) != null
    }

    private fun readTable(): MdBlock? {
        val headLine = lines[i]
        val align = parseDelimiterRow(lines[i + 1]) ?: return null
        val head = splitRow(headLine)
        i += 2
        val rows = ArrayList<List<String>>()
        while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|') &&
            !isBlockStartLine(lines[i])
        ) {
            val cells = splitRow(lines[i])
            if (cells.isNotEmpty()) rows += cells
            i++
        }
        return MdBlock.Table(head, align, rows)
    }
}

// ───────────────────────────── 小块判定 ─────────────────────────────

private data class FenceInfo(val marker: Char, val length: Int, val info: String)

private fun fenceInfo(line: String): FenceInfo? {
    val trimmed = line.trimStart(' ')
    if (line.length - trimmed.length > 3) return null
    val marker = trimmed.firstOrNull() ?: return null
    if (marker != '`' && marker != '~') return null
    val run = trimmed.takeWhile { it == marker }.length
    if (run < 3) return null
    val info = trimmed.drop(run).trim()
    // 反引号围栏的 info 串不能含反引号（CommonMark）
    if (marker == '`' && info.contains('`')) return null
    return FenceInfo(marker, run, info.substringBefore(' ').substringBefore('\t'))
}

/** ATX 标题（`#` 后必须跟空格或行尾）→ 级别与文本 */
private fun atxHeading(line: String): Pair<Int, String>? {
    val trimmed = line.trimStart(' ')
    if (line.length - trimmed.length > 3) return null
    val hashes = trimmed.takeWhile { it == '#' }.length
    if (hashes !in 1..6) return null
    val rest = trimmed.drop(hashes)
    if (rest.isNotEmpty() && rest.first() != ' ' && rest.first() != '\t') return null
    // 收尾的 `#` 串不算正文
    val text = rest.trim().trimEnd('#').trimEnd()
    return hashes to text
}

private fun setextUnderline(line: String): Int? {
    val t = line.trim()
    if (t.isEmpty()) return null
    return when {
        t.all { it == '=' } -> 1
        t.all { it == '-' } -> 2
        else -> null
    }
}

private fun hrLine(line: String): Boolean {
    val t = line.trim()
    if (t.length < 3) return false
    val c = t[0]
    if (c != '-' && c != '*' && c != '_') return false
    return t.all { it == c || it == ' ' } && t.count { it == c } >= 3
}

private fun quoteContent(line: String): String? {
    val t = line.trimStart(' ')
    if (line.length - t.length > 3) return null
    if (!t.startsWith(">")) return null
    return t.drop(1).removePrefix(" ")
}

private data class ListMarker(
    val indent: Int,
    val contentIndent: Int,
    val ordered: Boolean,
    val number: Int,
    val content: String,
)

private fun listMarker(line: String): ListMarker? {
    var indent = 0
    var idx = 0
    while (idx < line.length) {
        when (line[idx]) {
            ' ' -> { indent++; idx++ }
            '\t' -> { indent += 4; idx++ }
            else -> break
        }
    }
    if (idx >= line.length) return null
    val markerChar = line[idx]
    val isBullet = markerChar == '-' || markerChar == '*' || markerChar == '+'
    var ordered = false
    var number = 1
    var markerLen = 1
    if (isBullet) {
        // `- ` / `* ` / `+ ` 之后必须是空白（否则是分隔线或普通文本）
        if (idx + 1 >= line.length || (line[idx + 1] != ' ' && line[idx + 1] != '\t')) return null
    } else if (markerChar.isDigit()) {
        var j = idx
        while (j < line.length && line[j].isDigit() && j - idx < 9) j++
        if (j >= line.length || (line[j] != '.' && line[j] != ')')) return null
        if (j + 1 >= line.length || (line[j + 1] != ' ' && line[j + 1] != '\t')) return null
        ordered = true
        number = line.substring(idx, j).toIntOrNull() ?: 1
        markerLen = j - idx + 1
    } else {
        return null
    }
    var gap = 0
    var k = idx + markerLen
    while (k < line.length && (line[k] == ' ' || line[k] == '\t')) {
        gap += if (line[k] == '\t') 4 else 1
        k++
    }
    val content = line.substring(k)
    return ListMarker(
        indent = indent,
        contentIndent = indent + markerLen + gap,
        ordered = ordered,
        number = number,
        content = content,
    )
}

/** 任务项标记 `[ ]` / `[x]` / `[X]` → (是否任务项, 是否勾选, 其余文本) */
private fun taskMarker(text: String): Triple<Boolean, Boolean, String>? {
    if (text.length < 3 || text[0] != '[') return null
    val close = text.indexOf(']')
    if (close != 2) return null
    val inner = text[1]
    if (inner != ' ' && inner != 'x' && inner != 'X') return null
    val rest = text.substring(3)
    if (rest.isNotEmpty() && rest.first() != ' ' && rest.first() != '\t') return null
    return Triple(true, inner == 'x' || inner == 'X', rest.trimStart())
}

private fun parseDelimiterRow(line: String): List<MdAlign>? {
    val cells = splitRow(line)
    if (cells.isEmpty()) return null
    val aligns = ArrayList<MdAlign>()
    for (cell in cells) {
        val c = cell.trim()
        if (c.isEmpty()) return null
        val left = c.startsWith(":")
        val right = c.endsWith(":")
        val dashes = c.trim(':')
        if (dashes.isEmpty() || !dashes.all { it == '-' }) return null
        aligns += when {
            left && right -> MdAlign.CENTER
            right -> MdAlign.RIGHT
            else -> MdAlign.LEFT
        }
    }
    return aligns
}

/** 拆分表格行（保留 `\|` 转义；去掉首尾空单元） */
private fun splitRow(line: String): List<String> {
    val cells = ArrayList<String>()
    val sb = StringBuilder()
    var idx = 0
    val t = line.trim()
    while (idx < t.length) {
        val c = t[idx]
        when {
            c == '\\' && idx + 1 < t.length && t[idx + 1] == '|' -> { sb.append('|'); idx += 2 }
            c == '|' -> { cells += sb.toString().trim(); sb.clear(); idx++ }
            else -> { sb.append(c); idx++ }
        }
    }
    cells += sb.toString().trim()
    if (cells.firstOrNull()?.isEmpty() == true) cells.removeAt(0)
    if (cells.lastOrNull()?.isEmpty() == true) cells.removeAt(cells.lastIndex)
    return cells
}

private fun isBlockStartLine(line: String): Boolean =
    line.isBlank() || fenceInfo(line) != null || atxHeading(line) != null ||
        hrLine(line) || quoteContent(line) != null || listMarker(line) != null

private fun indentOf(line: String): Int {
    var indent = 0
    for (ch in line) {
        when (ch) {
            ' ' -> indent++
            '\t' -> indent += 4
            else -> return indent
        }
    }
    return indent
}

private fun dedent(line: String, columns: Int): String {
    var consumed = 0
    var idx = 0
    while (idx < line.length && consumed < columns) {
        when (line[idx]) {
            ' ' -> { consumed++; idx++ }
            '\t' -> { consumed += 4; idx++ }
            else -> return line.substring(idx)
        }
    }
    return line.substring(idx)
}

// ───────────────────────────── frontmatter ─────────────────────────────

private val TAG_KEYS = listOf("tags", "categories", "keywords", "tag", "category")

private fun parseFrontmatter(lines: List<String>): MdFrontmatter {
    val values = LinkedHashMap<String, String>()
    val tags = ArrayList<String>()
    var currentListKey: String? = null
    for (raw in lines) {
        val line = raw.trimEnd()
        if (line.isBlank() || line.trimStart().startsWith("#")) continue
        val isListItem = line.trimStart().startsWith("- ")
        if (isListItem) {
            val item = unquote(line.trimStart().removePrefix("- ").trim())
            val key = currentListKey
            if (key != null) {
                if (key in TAG_KEYS) tags += item
                else values[key] = listOf(values[key].orEmpty(), item)
                    .filter { it.isNotEmpty() }.joinToString(", ")
            }
            continue
        }
        val colon = line.indexOf(':')
        if (colon <= 0) { currentListKey = null; continue }
        val key = line.substring(0, colon).trim()
        val value = unquote(line.substring(colon + 1).trim())
        currentListKey = key
        if (value.isNotEmpty()) values[key] = value else values.remove(key)
    }
    val title = values.remove("title")?.takeIf { it.isNotBlank() }
    for (key in TAG_KEYS) values.remove(key)
    return MdFrontmatter(title = title, tags = tags, rows = values.entries.map { it.key to it.value })
}

private fun unquote(value: String): String {
    if (value.length >= 2) {
        val first = value.first()
        if ((first == '"' || first == '\'') && value.last() == first) return value.substring(1, value.length - 1)
    }
    return value.removePrefix("[").removeSuffix("]").trim()
}

// ───────────────────────────── 行内解析 ─────────────────────────────

private const val ESCAPABLE = "\\`*_{}[]()#+-.!|~<>\"'"

/** 行内标记解析（`**粗**` / `*斜*` / `` `码` `` / `~~删~~` / `[链接](url)` / `![图](src)` / 自动链接 / 硬换行） */
fun parseMdInline(text: String): List<MdSpan> {
    val out = ArrayList<MdSpan>()
    appendInline(text, MdSpanFlags(), out)
    return out
}

private data class MdSpanFlags(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val link: String? = null,
)

private fun appendInline(src: String, flags: MdSpanFlags, out: MutableList<MdSpan>) {
    val buffer = StringBuilder()
    var i = 0

    fun flush() {
        if (buffer.isEmpty()) return
        out += MdSpan(
            text = buffer.toString(),
            bold = flags.bold,
            italic = flags.italic,
            strike = flags.strike,
            link = flags.link,
        )
        buffer.clear()
    }

    fun appendChar(c: Char) = buffer.append(c)

    while (i < src.length) {
        val c = src[i]
        when {
            // 反斜杠转义
            c == '\\' && i + 1 < src.length && src[i + 1] in ESCAPABLE -> {
                appendChar(src[i + 1]); i += 2
            }
            // 换行：行尾两空格或反斜杠 = 硬换行，否则软换行折叠为空格（HTML 语义，pi-web 同）
            c == '\n' -> {
                var hard = buffer.length >= 2 && buffer.endsWith("  ")
                while (buffer.isNotEmpty() && buffer.last() == ' ') buffer.deleteAt(buffer.length - 1)
                if (!hard && buffer.isNotEmpty() && buffer.last() == '\\') {
                    buffer.deleteAt(buffer.length - 1)
                    hard = true
                }
                flush()
                if (hard) out += MdSpan(lineBreak = true) else appendChar(' ')
                i += 1
            }
            // 行内代码（反引号串长度必须成对）
            c == '`' -> {
                val run = src.runLengthAt(i, '`')
                val close = findBacktickRun(src, i + run, run)
                if (close < 0) { repeat(run) { appendChar('`') }; i += run } else {
                    flush()
                    var content = src.substring(i + run, close)
                    if (content.length >= 2 && content.first() == ' ' && content.last() == ' ' && content.isNotBlank()) {
                        content = content.substring(1, content.length - 1)
                    }
                    out += MdSpan(text = content, code = true, link = flags.link)
                    i = close + run
                }
            }
            // 图片
            c == '!' && i + 1 < src.length && src[i + 1] == '[' -> {
                val close = matchBracket(src, i + 1)
                val target = if (close > 0) linkTarget(src, close + 1) else null
                if (target != null) {
                    flush()
                    out += MdSpan(text = src.substring(i + 2, close), image = target.first)
                    i = target.second
                } else {
                    appendChar(c); i += 1
                }
            }
            // 链接
            c == '[' -> {
                val close = matchBracket(src, i)
                val target = if (close > 0) linkTarget(src, close + 1) else null
                if (target != null) {
                    flush()
                    appendInline(src.substring(i + 1, close), flags.copy(link = target.first), out)
                    i = target.second
                } else {
                    appendChar(c); i += 1
                }
            }
            // 自动链接 <https://…> / 行内 HTML <br>
            c == '<' -> {
                val close = src.indexOf('>', i + 1)
                if (close > 0) {
                    val inner = src.substring(i + 1, close)
                    val lower = inner.lowercase()
                    when {
                        lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:") -> {
                            flush()
                            out += MdSpan(text = inner, link = inner)
                            i = close + 1
                        }
                        lower == "br" || lower == "br/" || lower == "br /" -> {
                            flush(); out += MdSpan(lineBreak = true); i = close + 1
                        }
                        else -> { appendChar(c); i += 1 }
                    }
                } else { appendChar(c); i += 1 }
            }
            // 强调（`*` / `_`，1~3 个标记：1=斜体，2=粗体，3=粗斜体）
            c == '*' || c == '_' -> {
                val run = src.runLengthAt(i, c)
                val k = minOf(run, 3)
                // `_` 不在词内生效（GFM 的 intraword 规则）
                val prev = if (i == 0) ' ' else src[i - 1]
                val intrawordUnderscore = c == '_' && prev.isLetterOrDigit()
                val close = if (intrawordUnderscore) -1 else findEmphasisClose(src, i + k, c, k)
                if (close < 0) {
                    repeat(k) { appendChar(c) }
                    i += k
                } else {
                    val merged = when (k) {
                        3 -> flags.copy(bold = true, italic = true)
                        2 -> flags.copy(bold = true)
                        else -> flags.copy(italic = true)
                    }
                    val sub = ArrayList<MdSpan>()
                    appendInline(src.substring(i + k, close), merged, sub)
                    flush()
                    out += sub
                    i = close + k
                }
            }
            c == '~' && src.startsWith("~~", i) -> {
                val close = src.indexOf("~~", i + 2)
                if (close < 0) { appendChar(c); i += 1 } else {
                    val inner = src.substring(i + 2, close)
                    val sub = ArrayList<MdSpan>()
                    appendInline(inner, flags.copy(strike = true), sub)
                    flush()
                    out += sub
                    i = close + 2
                }
            }
            // 裸 URL 自动链接（GFM autolink literal）
            (c == 'h' || c == 'w') && isWordBoundary(src, i) && bareUrlAt(src, i) != null -> {
                val url = bareUrlAt(src, i)!!
                flush()
                out += MdSpan(text = url, link = if (url.startsWith("www.")) "http://$url" else url)
                i += url.length
            }
            // 常见 HTML 实体
            c == '&' -> {
                val entity = decodeEntity(src, i)
                if (entity != null) { appendChar(entity.first); i = entity.second }
                else { appendChar(c); i += 1 }
            }
            else -> { appendChar(c); i += 1 }
        }
    }
    flush()
}

private fun String.runLengthAt(index: Int, ch: Char): Int {
    var i = index
    while (i < length && this[i] == ch) i++
    return i - index
}

private fun findBacktickRun(src: String, from: Int, run: Int): Int {
    var i = from
    while (i < src.length) {
        if (src[i] == '`') {
            val len = src.runLengthAt(i, '`')
            if (len == run) return i
            i += len
        } else i++
    }
    return -1
}

/** 找配对的强调结束标记（内容不能以空白开头/结尾；k=1 时跳过 `**` 串避免抢占粗体） */
private fun findEmphasisClose(src: String, from: Int, ch: Char, k: Int): Int {
    if (from >= src.length || src[from].isWhitespace()) return -1
    var i = from
    while (i < src.length) {
        when {
            src[i] == '\\' -> i += 2
            src[i] == '\n' -> return -1
            src[i] == ch -> {
                val len = src.runLengthAt(i, ch)
                val usable = if (k == 1 && len >= 2) false else len >= k
                if (usable && i - 1 >= 0 && !src[i - 1].isWhitespace()) return i
                i += len
            }
            else -> i++
        }
    }
    return -1
}

/** `[label` 的配对 `]`（考虑嵌套方括号） */
private fun matchBracket(src: String, open: Int): Int {
    if (open >= src.length || src[open] != '[') return -1
    var depth = 0
    var i = open
    while (i < src.length) {
        when {
            src[i] == '\\' -> i += 2
            src[i] == '[' -> { depth++; i++ }
            src[i] == ']' -> {
                depth--
                if (depth == 0) return i
                i++
            }
            src[i] == '\n' -> return -1
            else -> i++
        }
    }
    return -1
}

/** `(url "title")` → (url, 结束位置)；非链接语法返回 null */
private fun linkTarget(src: String, open: Int): Pair<String, Int>? {
    if (open >= src.length || src[open] != '(') return null
    var depth = 1
    var i = open + 1
    val sb = StringBuilder()
    var quoted = false
    var title = false
    while (i < src.length) {
        val c = src[i]
        when {
            c == '\\' && i + 1 < src.length -> { sb.append(src[i + 1]); i += 2 }
            c == '"' || c == '\'' -> { quoted = !quoted; i++ }
            !quoted && c == '(' -> { depth++; sb.append(c); i++ }
            !quoted && c == ')' -> {
                depth--
                if (depth == 0) {
                    val raw = sb.toString().trim()
                    val url = raw.substringBefore(' ').trim().removePrefix("<").removeSuffix(">")
                    if (url.isEmpty()) return null
                    return url to (i + 1)
                }
                sb.append(c); i++
            }
            !quoted && title && c == ' ' -> {
                // 标题部分（`url "title"`）忽略
                while (i < src.length && src[i] != ')') i++
            }
            !quoted && c == ' ' && sb.isNotEmpty() -> { title = true; i++ }
            c == '\n' -> return null
            else -> { sb.append(c); i++ }
        }
    }
    return null
}

private fun isWordBoundary(src: String, index: Int): Boolean {
    if (index == 0) return true
    val prev = src[index - 1]
    return !prev.isLetterOrDigit()
}

/** 裸 URL（`https://…` / `www.…`）→ 去掉行尾标点后的地址 */
private fun bareUrlAt(src: String, index: Int): String? {
    val rest = src.substring(index)
    val prefix = when {
        rest.startsWith("https://") -> "https://"
        rest.startsWith("http://") -> "http://"
        rest.startsWith("www.") -> "www."
        else -> return null
    }
    var end = index + prefix.length
    while (end < src.length && !src[end].isWhitespace() && src[end] !in "<>\"") end++
    var url = src.substring(index, end)
    while (url.isNotEmpty() && url.last() in ".,;:!?)]}'") url = url.dropLast(1)
    return url.takeIf { it.length > prefix.length }?.takeIf { !it.contains('[') && !it.contains(']') }
}

private fun decodeEntity(src: String, index: Int): Pair<Char, Int>? {
    val semi = src.indexOf(';', index + 1)
    if (semi < 0 || semi - index > 10) return null
    val name = src.substring(index + 1, semi)
    val ch = when (name.lowercase()) {
        "amp" -> '&'
        "lt" -> '<'
        "gt" -> '>'
        "quot" -> '"'
        "apos", "#39" -> '\''
        "nbsp" -> '\u00A0'
        "hellip" -> '…'
        "mdash" -> '—'
        "ndash" -> '–'
        else -> return null
    }
    return ch to (semi + 1)
}

// ──────────────── Markdown → 纯文本（复制消息卡「纯文本」态） ────────────────

/**
 * Markdown → 适合直接粘贴的纯文本。参照 Operit `MarkdownPlainTextRenderer`：
 * **复用同一份 AST**（parseMarkdown / parseMdInline），不另写解析——保证「复制出来的内容」
 * 与屏幕上渲染的内容共享同一套块/行内边界判断。
 *
 * 规则（逐条对齐 Operit）：标题去 `#`；无序列表前缀「• 」、有序列表保留「N. 」、
 * 任务项「[x]/[ ] 」；代码块取源码（有语言标注时按 Operit 加 `----lang-----` 头）；
 * 表格行 `\n` 分隔、单元格 `\t` 分隔；链接「文字 (地址)」、图片取 alt；
 * 分割线丢弃。相邻列表项单换行、其余块间空行，最后折叠多余空行并 trim
 * （模型输出里残留的全角空格行会被折叠，避免多出空行）。
 */
fun markdownToPlainText(source: String): String {
    val blocks = parseMarkdown(source).blocks
    val sb = StringBuilder()
    blocks.forEachIndexed { i, b ->
        if (i > 0) {
            val bothList = b is MdBlock.ListBlock && blocks[i - 1] is MdBlock.ListBlock
            sb.append(if (bothList) "\n" else "\n\n")
        }
        sb.append(plainBlock(b))
    }
    return sb.toString().replace(Regex("\\h*\\n(?:\\h*\\n)+\\h*"), "\n\n").trim()
}

private fun plainBlock(b: MdBlock): String = when (b) {
    is MdBlock.Heading -> plainInline(b.text)
    is MdBlock.Paragraph -> plainInline(b.text)
    is MdBlock.Code -> if (b.lang.isBlank()) b.code else "----${b.lang}-----\n${b.code}"
    is MdBlock.Quote -> b.blocks
        .map { plainBlock(it) }
        .filter { it.isNotEmpty() }
        .joinToString("\n\n")
    is MdBlock.ListBlock -> b.items.mapIndexed { i, item ->
        val marker = when {
            item.task == true -> if (item.checked) "[x] " else "[ ] "
            b.ordered -> "${b.start + i}. "
            else -> "• "
        }
        val children = item.children.map { plainBlock(it) }.filter { it.isNotEmpty() }
        marker + plainInline(item.text) + if (children.isEmpty()) "" else "\n" + children.joinToString("\n")
    }.joinToString("\n")
    is MdBlock.Table -> (listOf(b.head) + b.rows).joinToString("\n") { row ->
        row.joinToString("\t") { plainInline(it) }
    }
    MdBlock.Hr -> ""
}

/** 行内：取纯文本；链接展开为「文字 (地址)」，图片取 alt，硬换行还原为换行 */
private fun plainInline(text: String): String = parseMdInline(text).joinToString("") { s ->
    when {
        s.lineBreak -> "\n"
        s.image != null -> s.text
        s.link != null -> if (s.text.isBlank() || s.text == s.link) s.link else "${s.text} (${s.link})"
        else -> s.text
    }
}
