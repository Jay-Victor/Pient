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
enum class CodeToken { KEYWORD, TYPE, VARIABLE, FUNCTION, STRING, NUMBER, COMMENT }

/** 一段同色区间 [start, end) */
data class CodeSpan(val start: Int, val end: Int, val token: CodeToken)

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

    private val all = listOf(
        kotlin, java, javascript, typescript, python,
        html, xml, json, css, yaml, shell,
    )

    private val byExtension: Map<String, CodeLanguage> =
        all.flatMap { lang -> lang.extensions.map { it to lang } }.toMap()

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
 * 运算符与空白归正文色，不产出区间。
 */
fun scanCode(text: String, lang: CodeLanguage): List<CodeSpan> {
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
