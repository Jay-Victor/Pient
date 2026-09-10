package com.pient.app.data

/**
 * 代码语法着色 —— 移植 Operit 工作区编辑器（`ui/.../workspace/editor`）：
 * - 扫描算法：`EditorSyntaxHighlighter.parseFullText`（逐字符：注释 → 字符串 → 数字 → 标识符 → 运算符）
 * - 语言定义：Operit `language/` 下各 `*Support.kt`（关键字 / 内置类型 / 内置函数 / 内置变量 / 注释标记 / 字符串分隔符）
 *   与 `LanguageDetector`（扩展名 → 语言）
 * - 颜色：本层只产出「种类」，具体色值在 UI 层按 Operit `theme/EditorTheme.kt` 的
 *   LightTheme / DarkTheme 调色板取用（见 ui/files/CodeHighlight.kt）
 */

/** 着色种类（对应 Operit LanguageSupport 的颜色常量；OPERATOR/DEFAULT 都落到正文色，故不单列） */
enum class CodeToken { KEYWORD, TYPE, VARIABLE, FUNCTION, STRING, NUMBER, COMMENT, HEADING, LINK }

/** 一段同色区间 [start, end)（粗体/斜体/删除线 = 标记语言的强调样式，Operit 编辑器不用） */
data class CodeSpan(
    val start: Int,
    val end: Int,
    val token: CodeToken,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
)

/** 语言定义（Operit LanguageSupport + BaseLanguageSupport 默认值） */
class CodeLanguage(
    val name: String,
    val extensions: Set<String>,
    val keywords: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val functions: Set<String> = emptySet(),
    val variables: Set<String> = emptySet(),
    /** 注释起始标记（Operit 规则：`//` 或单字符标记 → 行注释；其余 → 到多行结束标记） */
    val commentStarts: List<String> = listOf("//", "/*"),
    val multiCommentEnd: String? = "*/",
    val stringDelimiters: Set<Char> = setOf('"', '\''),
    val escapeChar: Char = '\\',
    /** 标记语言（Markdown）：走专用扫描器，不用上面的逐字符词法 */
    val markdown: Boolean = false,
)

object CodeLanguages {
    /** 通用回退（Operit BaseLanguageSupport：`//` 与块注释、引号字符串、转义） */
    val generic = CodeLanguage(name = "text", extensions = emptySet())

    /** Kotlin（关键字/类型/函数集逐字来自 Operit KotlinSupport） */
    val kotlin = CodeLanguage(
        name = "kotlin",
        extensions = setOf("kt", "kts"),
        keywords = setOf(
            "package", "as", "typealias", "class", "this", "super", "val", "var", "fun", "for",
            "null", "true", "false", "is", "in", "throw", "return", "break", "continue", "object",
            "if", "else", "while", "do", "try", "when", "interface", "typeof", "by", "catch",
            "constructor", "delegate", "dynamic", "field", "file", "finally", "get", "import",
            "init", "param", "property", "receiver", "set", "setparam", "where", "actual",
            "abstract", "annotation", "companion", "const", "crossinline", "data", "enum",
            "expect", "external", "final", "infix", "inline", "inner", "internal", "lateinit",
            "noinline", "open", "operator", "out", "override", "private", "protected", "public",
            "reified", "sealed", "suspend", "tailrec", "vararg",
        ),
        types = setOf(
            "Any", "Unit", "String", "Int", "Boolean", "Char", "Byte", "Short",
            "Long", "Double", "Float", "Array", "List", "Map", "Set", "Pair",
            "Triple", "Nothing", "Collection", "MutableList", "MutableMap", "MutableSet",
        ),
        functions = setOf(
            "apply", "let", "run", "with", "also", "takeIf", "takeUnless", "repeat",
            "lazy", "lazyOf", "arrayOf", "listOf", "mutableListOf", "setOf", "mutableSetOf",
            "mapOf", "mutableMapOf", "println", "print", "require", "check", "error",
            "TODO", "runCatching", "use",
        ),
    )

    /** JavaScript（逐字来自 Operit JavaScriptSupport） */
    val javascript = CodeLanguage(
        name = "javascript",
        extensions = setOf("js", "mjs", "cjs"),
        keywords = setOf(
            "function", "var", "let", "const", "if", "else", "for", "while", "do", "switch",
            "case", "break", "continue", "return", "try", "catch", "finally", "throw",
            "new", "delete", "typeof", "instanceof", "void", "this", "super", "class",
            "extends", "import", "export", "default", "async", "await", "yield", "static",
            "get", "set", "in", "of", "with", "debugger", "null", "true", "false",
        ),
        types = setOf(
            "Array", "Boolean", "Date", "Error", "Function", "JSON", "Math", "Number",
            "Object", "Promise", "RegExp", "String", "Symbol", "Map", "Set", "WeakMap",
            "WeakSet", "ArrayBuffer", "DataView", "Int8Array", "Uint8Array", "Uint8ClampedArray",
            "Int16Array", "Uint16Array", "Int32Array", "Uint32Array", "Float32Array", "Float64Array",
        ),
        variables = setOf(
            "console", "window", "document", "navigator", "location", "history", "screen",
            "localStorage", "sessionStorage", "performance",
        ),
        functions = setOf(
            "setTimeout", "clearTimeout", "setInterval", "clearInterval",
            "encodeURI", "decodeURI", "encodeURIComponent", "decodeURIComponent",
            "parseInt", "parseFloat", "isNaN", "isFinite", "eval", "alert", "confirm", "prompt",
            "btoa", "atob", "fetch",
        ),
    )

    /** TypeScript（Operit 只注册到 JS；TS 按其 LanguageDetector 的 ts/tsx 补充类型语法） */
    val typescript = CodeLanguage(
        name = "typescript",
        extensions = setOf("ts", "tsx"),
        keywords = javascript.keywords + setOf(
            "interface", "type", "enum", "implements", "declare", "readonly", "namespace",
            "module", "abstract", "public", "private", "protected", "as", "satisfies",
            "keyof", "infer", "is", "override", "unique", "accessor", "using", "asserts",
        ),
        types = javascript.types + setOf(
            "any", "unknown", "never", "void", "string", "number", "boolean", "object",
            "symbol", "bigint", "undefined", "Partial", "Required", "Record", "Pick", "Omit",
            "Readonly", "Exclude", "Extract", "ReturnType", "Awaited",
        ),
        variables = javascript.variables,
        functions = javascript.functions,
    )

    /** Python（Operit 未注册 python 支持、`Detector` 有 py；此处按其算法补 Python 词表） */
    val python = CodeLanguage(
        name = "python",
        extensions = setOf("py", "pyw"),
        keywords = setOf(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
            "continue", "def", "del", "elif", "else", "except", "finally", "for", "from",
            "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass",
            "raise", "return", "try", "while", "with", "yield", "match", "case",
        ),
        types = setOf(
            "int", "float", "complex", "str", "bool", "bytes", "bytearray", "list", "tuple",
            "dict", "set", "frozenset", "object", "type", "Exception", "BaseException",
            "ValueError", "TypeError", "KeyError", "IndexError", "RuntimeError", "OSError",
            "any", "Optional", "List", "Dict", "Tuple", "Set", "Callable", "Iterable",
        ),
        functions = setOf(
            "print", "len", "range", "enumerate", "zip", "map", "filter", "open", "input",
            "isinstance", "issubclass", "getattr", "setattr", "hasattr", "super", "sorted",
            "reversed", "sum", "min", "max", "abs", "round", "repr", "format", "iter", "next",
            "id", "hash", "exit", "vars", "dir",
        ),
        commentStarts = listOf("#"),
        multiCommentEnd = null,
    )

    /** Java（Operit 未注册；`Detector` 有 java） */
    val java = CodeLanguage(
        name = "java",
        extensions = setOf("java"),
        keywords = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof",
            "int", "interface", "long", "native", "new", "package", "private", "protected",
            "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "try", "void", "volatile", "while",
            "var", "record", "sealed", "permits", "yield", "true", "false", "null",
        ),
        types = setOf(
            "String", "Object", "Integer", "Long", "Double", "Float", "Boolean", "Character",
            "Byte", "Short", "Void", "Number", "Math", "System", "List", "Map", "Set",
            "ArrayList", "HashMap", "HashSet", "LinkedList", "Optional", "Stream",
            "Collection", "Iterator", "Exception", "RuntimeException", "Thread", "StringBuilder",
        ),
        functions = setOf("println", "print", "printf", "format", "valueOf", "parseInt", "parseDouble"),
    )

    /** HTML（TAGS/ATTRIBUTES、注释 `<!-- -->` 逐字来自 Operit HtmlSupport） */
    val html = CodeLanguage(
        name = "html",
        extensions = setOf("html", "htm", "xhtml"),
        keywords = setOf(
            "html", "head", "title", "body", "div", "p", "span", "a", "img", "ul", "ol", "li",
            "h1", "h2", "h3", "h4", "h5", "h6", "strong", "em", "br", "hr", "meta", "link",
            "script", "style", "table", "tr", "td", "th", "form", "input", "button", "textarea",
            "select", "option", "label", "header", "footer", "nav", "section", "article", "aside",
            "main", "canvas", "audio", "video", "source", "iframe", "svg", "path", "rect", "circle",
            "line", "polyline", "polygon", "text", "g", "defs", "use", "symbol", "marker", "pattern",
            "clipPath", "mask", "filter", "feGaussianBlur", "feOffset", "feBlend", "feColorMatrix",
        ),
        functions = setOf(
            "id", "class", "style", "href", "src", "alt", "title", "width", "height", "type",
            "value", "name", "placeholder", "disabled", "checked", "selected", "readonly", "required",
            "maxlength", "minlength", "max", "min", "pattern", "autocomplete", "autofocus", "formaction",
            "formmethod", "formnovalidate", "formtarget", "rel", "target", "download", "colspan",
            "rowspan", "headers", "scope", "action", "method", "enctype", "accept", "accept-charset",
            "novalidate", "onsubmit", "onclick", "onchange", "onkeyup", "onkeydown", "onkeypress",
            "onmouseover", "onmouseout", "onmousedown", "onmouseup", "onload", "onerror",
        ),
        commentStarts = listOf("<!--"),
        multiCommentEnd = "-->",
    )

    /** XML（无标签词表：只着色注释/字符串/数字；`<!-- -->`） */
    val xml = CodeLanguage(
        name = "xml",
        extensions = setOf("xml", "svg", "xsd", "xsl"),
        commentStarts = listOf("<!--"),
        multiCommentEnd = "-->",
    )

    /** JSON（无注释；true/false/null 视作关键字） */
    val json = CodeLanguage(
        name = "json",
        extensions = setOf("json"),
        keywords = setOf("true", "false", "null"),
        commentStarts = emptyList(),
        multiCommentEnd = null,
        stringDelimiters = setOf('"'),
    )

    /** CSS（块注释、字符串；Operit Detector 的 css/scss/sass/less） */
    val css = CodeLanguage(
        name = "css",
        extensions = setOf("css", "scss", "sass", "less"),
        commentStarts = listOf("/*"),
        multiCommentEnd = "*/",
    )

    /** 配置文件（YAML / TOML / properties：`#` 行注释） */
    val yaml = CodeLanguage(
        name = "yaml",
        extensions = setOf("yml", "yaml", "toml", "properties", "conf", "cfg", "ini"),
        keywords = setOf("true", "false", "null", "yes", "no", "on", "off"),
        commentStarts = listOf("#"),
        multiCommentEnd = null,
    )

    /** Shell（`#` 行注释） */
    val shell = CodeLanguage(
        name = "shell",
        extensions = setOf("sh", "bash", "zsh"),
        keywords = setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "case",
            "esac", "function", "select", "in", "return", "export", "local", "readonly",
            "declare", "typeset", "unset", "break", "continue", "exit", "trap", "set", "shift",
            "source", "eval", "exec", "echo", "cd", "pwd", "test", "true", "false",
        ),
        commentStarts = listOf("#"),
        multiCommentEnd = null,
    )

    /** Markdown（源码多色：标题/强调/代码/链接/列表/引用 —— 见 scanMarkdown） */
    val markdown = CodeLanguage(
        name = "markdown",
        extensions = setOf("md", "markdown", "mdown", "mkd"),
        markdown = true,
    )

    private val all = listOf(
        kotlin, java, javascript, typescript, python,
        html, xml, json, css, yaml, shell, markdown,
    )

    private val byExtension: Map<String, CodeLanguage> =
        all.flatMap { lang -> lang.extensions.map { it to lang } }.toMap()

    /** 围栏代码块 info 串里的语言名（```kotlin / ```kt / ```js …）→ 语言；未知返回 null */
    fun byName(name: String): CodeLanguage? {
        val key = name.trim().lowercase().substringBefore(' ').substringBefore(',')
        if (key.isEmpty()) return null
        return byExtension[key] ?: all.firstOrNull { it.name == key }
    }

    /** 扩展名 → 语言（无匹配返回 null，调用方决定是否回退 generic） */
    fun forExtension(ext: String): CodeLanguage? = byExtension[ext.lowercase()]

    /** 按 4 空格缩进的制表位（Operit `CanvasCodeEditorView.TAB_SPACES`） */
    const val TAB_SPACES = 4

    /** 超过此长度不做着色（扫描是 O(n)，避免大文件在输入时卡顿；行号与缩进标记不受影响） */
    const val MAX_HIGHLIGHT_CHARS = 50_000
}

/**
 * 逐字符扫描（Operit `EditorSyntaxHighlighter.parseFullText` 同款顺序与归类）：
 * 注释 → 字符串 → 数字 → 标识符（关键字/内置类型/内置变量/内置函数/后跟 `(` → 函数/首字母大写 → 类型/其余 → 变量）。
 * 运算符与空白归正文色，不产出区间。Markdown 走 [scanMarkdown] 专用扫描器。
 */
fun scanCode(text: String, lang: CodeLanguage): List<CodeSpan> =
    if (lang.markdown) scanMarkdown(text) else scanCodeTokens(text, lang)

private fun scanCodeTokens(text: String, lang: CodeLanguage): List<CodeSpan> {
    val spans = ArrayList<CodeSpan>()
    val n = text.length
    var i = 0

    fun add(start: Int, end: Int, token: CodeToken) {
        if (end > start) spans += CodeSpan(start, end, token)
    }

    while (i < n) {
        val c = text[i]

        // ── 注释：`//` 或单字符标记 → 行注释；其余 → 到多行结束标记 ──
        var handled = false
        for (marker in lang.commentStarts) {
            if (!text.startsWith(marker, i)) continue
            val lineComment = marker == "//" || marker.length == 1
            val end = if (lineComment) {
                val nl = text.indexOf('\n', i)
                if (nl == -1) n else nl
            } else {
                val endMarker = lang.multiCommentEnd ?: "*/"
                val found = text.indexOf(endMarker, i + marker.length)
                if (found == -1) n else found + endMarker.length
            }
            add(i, end, CodeToken.COMMENT)
            i = end
            handled = true
            break
        }
        if (handled) continue

        // ── 字符串（含转义跳过）──
        if (c in lang.stringDelimiters) {
            val quote = c
            val start = i
            i++
            while (i < n) {
                if (text[i] == lang.escapeChar && i + 1 < n) {
                    i += 2
                    continue
                }
                if (text[i] == quote) {
                    i++
                    break
                }
                i++
            }
            add(start, i, CodeToken.STRING)
            continue
        }

        // ── 数字（0x / 0b + 科学计数 + L/l/F/f/D/d 后缀）──
        if (c.isDigit()) {
            val start = i
            if (c == '0' && i + 1 < n && (text[i + 1] == 'x' || text[i + 1] == 'X')) {
                i += 2
                while (i < n && (text[i].isDigit() || text[i] in 'a'..'f' || text[i] in 'A'..'F')) i++
            } else if (c == '0' && i + 1 < n && (text[i + 1] == 'b' || text[i + 1] == 'B')) {
                i += 2
                while (i < n && (text[i] == '0' || text[i] == '1')) i++
            } else {
                i = consumeNumber(text, i)
            }
            if (i < n && (text[i] == 'L' || text[i] == 'l' || text[i] == 'F' || text[i] == 'f' ||
                        text[i] == 'D' || text[i] == 'd')
            ) {
                i++
            }
            add(start, i, CodeToken.NUMBER)
            continue
        }

        // ── 标识符 ──
        if (Character.isJavaIdentifierStart(c)) {
            val start = i
            while (i < n && Character.isJavaIdentifierPart(text[i])) i++
            val word = text.substring(start, i)
            var j = i
            while (j < n && (text[j] == ' ' || text[j] == '\t')) j++
            val nextChar = if (j < n) text[j] else ' '
            val token = when {
                lang.keywords.contains(word) -> CodeToken.KEYWORD
                lang.types.contains(word) -> CodeToken.TYPE
                lang.variables.contains(word) -> CodeToken.VARIABLE
                lang.functions.contains(word) -> CodeToken.FUNCTION
                nextChar == '(' -> CodeToken.FUNCTION
                word[0].isUpperCase() -> CodeToken.TYPE
                else -> CodeToken.VARIABLE
            }
            add(start, i, token)
            continue
        }

        // 运算符 / 空白 / 标点 → 正文色，跳过
        i++
    }
    return spans
}

private fun consumeNumber(text: String, startIndex: Int): Int {
    var index = startIndex
    while (index < text.length &&
        (text[index].isDigit() || text[index] == '.' || text[index] == 'e' || text[index] == 'E' ||
            text[index] == '-' || text[index] == '+')
    ) {
        index++
    }
    return index
}

/** 行首缩进的空格数（tab 记 [CodeLanguages.TAB_SPACES] 个）；用于缩进标记 */
fun leadingIndentCells(line: String): Int {
    var cells = 0
    for (ch in line) {
        when (ch) {
            ' ' -> cells++
            '\t' -> cells += CodeLanguages.TAB_SPACES
            else -> return cells
        }
    }
    return cells
}

// ───────────────────────────── Markdown 源码着色 ─────────────────────────────

/**
 * Markdown 源码多色扫描（Pient 增补；Operit 编辑器无 markdown 语言）：
 * 标题 → HEADING(+粗体，含 `#` 标记)；列表/任务标记 → NUMBER；引用标记 `>`、分隔线、围栏标记 → COMMENT；
 * 行内代码与围栏代码体 → STRING（围栏有语言时按该语言着色）；链接文字 → FUNCTION、链接地址 → LINK；
 * 强调标记 → VARIABLE（粗体/斜体内容分别加粗/倾斜，删除线内容 → COMMENT + 删除线）；frontmatter 键 → FUNCTION、值 → STRING。
 */

private val MD_HEADING = Regex("^ {0,3}(#{1,6})(?:\\s.*)?$")
private val MD_HR = Regex("^ {0,3}(?:(?:- *){3,}|(?:\\* *){3,}|(?:_ *){3,})$")
private val MD_QUOTE = Regex("^ {0,3}(?:> ?)+")
private val MD_LIST = Regex("^\\s*(?:[-*+]|\\d{1,9}[.)])(?:\\s+|$)")
private val MD_TASK = Regex("^\\[[ xX]\\](?:\\s+|$)")
private val MD_FENCE = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")
private val MD_FRONT_KEY = Regex("^(\\s*)([A-Za-z0-9_.-]+)(:)(.*)$")

private class MdFence(val marker: Char, val length: Int, val lang: String, val bodyStart: Int)

fun scanMarkdown(text: String): List<CodeSpan> {
    val spans = ArrayList<CodeSpan>()
    val frontEnd = frontmatterEnd(text)
    var offset = 0
    var fence: MdFence? = null

    for (line in text.split("\n")) {
        val lineStart = offset
        val lineEnd = lineStart + line.length
        offset = lineEnd + 1

        if (lineStart < frontEnd) {
            scanFrontmatterLine(line, lineStart, spans)
            continue
        }
        val open = MD_FENCE.find(line)
        val current = fence
        if (current != null) {
            val closing = open != null &&
                open.groupValues[1][0] == current.marker &&
                open.groupValues[1].length >= current.length &&
                open.groupValues[2].isBlank()
            if (closing) {
                scanFenceBody(text, current, lineStart, spans)
                addSpan(spans, lineStart, lineEnd, CodeToken.COMMENT)
                fence = null
            }
            // 围栏内其余行由 scanFenceBody 处理
            continue
        }
        if (open != null) {
            addSpan(spans, lineStart, lineEnd, CodeToken.COMMENT)
            fence = MdFence(
                marker = open.groupValues[1][0],
                length = open.groupValues[1].length,
                lang = open.groupValues[2].trim(),
                bodyStart = offset,
            )
            continue
        }
        scanMdLine(line, lineStart, spans)
    }
    // 未闭合围栏：正文到文末
    fence?.let { scanFenceBody(text, it, text.length, spans) }
    return spans.sortedBy { it.start }
}

/** frontmatter 结束偏移（无 frontmatter 返回 0） */
private fun frontmatterEnd(text: String): Int {
    if (!text.startsWith("---")) return 0
    var offset = 0
    val lines = text.split("\n")
    if (lines.firstOrNull()?.trimEnd() != "---") return 0
    for ((index, line) in lines.withIndex()) {
        if (index == 0) {
            offset = line.length + 1
            continue
        }
        if (line.trimEnd() == "---") return offset + line.length + 1
        offset += line.length + 1
    }
    return 0
}

private fun scanFrontmatterLine(line: String, lineStart: Int, spans: MutableList<CodeSpan>) {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed == "---") {
        addSpan(spans, lineStart, lineStart + line.length, CodeToken.COMMENT)
        return
    }
    if (trimmed.startsWith("#")) {
        addSpan(spans, lineStart, lineStart + line.length, CodeToken.COMMENT)
        return
    }
    val match = MD_FRONT_KEY.find(line)
    if (match == null) {
        addSpan(spans, lineStart, lineStart + line.length, CodeToken.STRING)
        return
    }
    val keyStart = lineStart + match.groupValues[1].length
    val keyEnd = keyStart + match.groupValues[2].length
    addSpan(spans, keyStart, keyEnd, CodeToken.FUNCTION)
    val valueStart = keyEnd + match.groupValues[3].length
    if (match.groupValues[4].isNotBlank()) {
        addSpan(spans, valueStart, lineStart + line.length, CodeToken.STRING)
    }
}

/** 围栏代码体：有语言 → 按该语言着色（偏移平移到全文坐标）；无/未知语言 → 正文色 */
private fun scanFenceBody(text: String, fence: MdFence, end: Int, spans: MutableList<CodeSpan>) {
    if (fence.bodyStart >= end) return
    val language = CodeLanguages.byName(fence.lang) ?: CodeLanguages.markdown.takeIf { fence.lang.startsWith("md") }
    val body = text.substring(fence.bodyStart, minOf(end - 1, text.length).coerceAtLeast(fence.bodyStart))
    if (language == null || language.markdown) {
        addSpan(spans, fence.bodyStart, fence.bodyStart + body.length, CodeToken.STRING)
        return
    }
    for (span in scanCodeTokens(body, language)) {
        spans += CodeSpan(span.start + fence.bodyStart, span.end + fence.bodyStart, span.token, span.bold, span.italic, span.strike)
    }
}

private fun scanMdLine(line: String, lineStart: Int, spans: MutableList<CodeSpan>) {
    if (line.isBlank()) return
    val lineEnd = lineStart + line.length
    if (MD_HR.matches(line)) {
        addSpan(spans, lineStart, lineEnd, CodeToken.COMMENT)
        return
    }
    if (MD_HEADING.matches(line)) {
        addSpan(spans, lineStart, lineEnd, CodeToken.HEADING, bold = true)
        return
    }
    var cursor = 0
    val quote = MD_QUOTE.find(line)
    if (quote != null) {
        addSpan(spans, lineStart, lineStart + quote.value.length, CodeToken.COMMENT)
        cursor = quote.value.length
    }
    val list = MD_LIST.find(line.substring(cursor))?.takeIf { it.range.first == 0 }
    if (list != null) {
        addSpan(spans, lineStart + cursor, lineStart + cursor + list.value.length, CodeToken.NUMBER)
        cursor += list.value.length
        val task = MD_TASK.find(line.substring(cursor))
        if (task != null) {
            addSpan(spans, lineStart + cursor, lineStart + cursor + task.value.trimEnd().length, CodeToken.NUMBER)
            cursor += task.value.trimEnd().length
        }
    }
    scanMdInline(line, lineStart, cursor, line.length, spans, CodeToken.LINK, bold = false, italic = false)
}

/**
 * 行内扫描 —— 必须带 [to] 上界：强调/删除线的内容递归只扫自己那一段，
 * 否则递归会一路扫到行尾并重复产出区间（2026-09-10 实测：整段被重复着色）。
 */
private fun scanMdInline(
    line: String,
    lineStart: Int,
    from: Int,
    to: Int,
    spans: MutableList<CodeSpan>,
    linkToken: CodeToken,
    bold: Boolean,
    italic: Boolean,
) {
    var i = from
    while (i < to) {
        val c = line[i]
        when {
            // 行内代码
            c == '`' -> {
                val run = line.runLength(i, '`')
                val close = line.indexOf("`".repeat(run), i + run)
                if (close < 0 || close + run > to) {
                    i += run
                } else {
                    addSpan(spans, lineStart + i, lineStart + close + run, CodeToken.STRING)
                    i = close + run
                }
            }
            // 强调（1=斜体 / 2=粗体 / 3=粗斜体）
            c == '*' || c == '_' -> {
                val run = line.runLength(i, c)
                val k = minOf(run, 3)
                val marker = c.toString().repeat(k)
                val close = findMarker(line, marker, i + k, to)
                if (close < 0) {
                    i += k
                } else {
                    addSpan(spans, lineStart + i, lineStart + i + k, CodeToken.VARIABLE)
                    scanMdInline(
                        line, lineStart, i + k, close, spans, linkToken,
                        bold || k >= 2, italic || k == 1 || k == 3,
                    )
                    addSpan(spans, lineStart + close, lineStart + close + k, CodeToken.VARIABLE)
                    i = close + k
                }
            }
            // 删除线
            c == '~' && line.startsWith("~~", i) -> {
                val close = line.indexOf("~~", i + 2)
                if (close < 0 || close + 2 > to) {
                    i += 1
                } else {
                    addSpan(spans, lineStart + i, lineStart + i + 2, CodeToken.VARIABLE)
                    addSpan(spans, lineStart + i + 2, lineStart + close, CodeToken.COMMENT, strike = true)
                    addSpan(spans, lineStart + close, lineStart + close + 2, CodeToken.VARIABLE)
                    i = close + 2
                }
            }
            // 链接 / 图片（label → FUNCTION，地址 → LINK）
            (c == '[' || (c == '!' && i + 1 < line.length && line[i + 1] == '[')) -> {
                val labelStart = if (c == '!') i + 2 else i + 1
                val labelEnd = line.indexOf(']', labelStart)
                val target = if (labelEnd in labelStart until to && labelEnd + 1 < line.length &&
                    line[labelEnd + 1] == '('
                ) {
                    line.indexOf(')', labelEnd + 2)
                } else -1
                if (labelEnd < 0 || labelEnd >= to || target < 0 || target > to) {
                    i += 1
                } else {
                    if (labelEnd > labelStart) {
                        addSpan(
                            spans, lineStart + labelStart, lineStart + labelEnd, CodeToken.FUNCTION,
                            bold = bold, italic = italic,
                        )
                    }
                    addSpan(
                        spans, lineStart + labelEnd + 2, lineStart + target, CodeToken.LINK,
                        bold = bold, italic = italic,
                    )
                    i = target + 1
                }
            }
            // 自动链接
            c == 'h' && line.startsWith("http", i) &&
                (i == 0 || !line[i - 1].isLetterOrDigit()) -> {
                var end = i
                while (end < to && !line[end].isWhitespace()) end++
                addSpan(spans, lineStart + i, lineStart + end, linkToken, bold = bold, italic = italic)
                i = end
            }
            else -> i++
        }
    }
}

/** 在 [from, to) 内找下一个 `marker`（不越过上界） */
private fun findMarker(line: String, marker: String, from: Int, to: Int): Int {
    var index = line.indexOf(marker, from)
    while (index >= 0 && index + marker.length <= to) {
        if (index > from) return index
        index = line.indexOf(marker, index + 1)
    }
    return -1
}

private fun String.runLength(index: Int, ch: Char): Int {
    var i = index
    while (i < length && this[i] == ch) i++
    return i - index
}

private fun addSpan(
    spans: MutableList<CodeSpan>,
    start: Int,
    end: Int,
    token: CodeToken,
    bold: Boolean = false,
    italic: Boolean = false,
    strike: Boolean = false,
) {
    if (end > start) spans += CodeSpan(start, end, token, bold, italic, strike)
}
