package com.pient.app.data

import com.pient.app.tools.ToolCall
import org.json.JSONObject

/**
 * **工具标记解析（协议层）** —— 模型把工具调用写成正文文本时的认领与剔除。
 *
 * 为什么需要兜底（2026-09-14 用户报「工具调用直接输出在回复里」）：DeepSeek 系模型即使声明了
 * 工具，也可能把调用写成 DSML 标记漏进正文（上游已知问题：开始标记缺失/写错时解析器漏掉，
 * 标记当正文返回）。这里把它认回来执行，正文只留模型真正说的话。
 *
 * 认三种写法：① DSML（全角 `｜DSML｜`、半角 `|DSML|`、`||DSML||` 变体都吃）；
 * ② 软件内契约的 `<tool_call><invoke …>`；③ 裸 `<invoke …>`。
 *
 * 属于**协议层**而不是工具层（2026-09-14 拆开）：它不认识任何工具怎么执行，
 * 只负责「模型说的话」与「模型想调的工具」分开。
 */
object ToolMarkup {

    private val DSML_OPEN = Regex("<[|｜]{1,2}DSML[|｜]{1,2}")
    private val DSML_CLOSE = Regex("</[|｜]{1,2}DSML[|｜]{1,2}")
    private val INVOKE = Regex("<invoke\\s+name\\s*=\\s*\"([^\"]+)\"\\s*>(.*?)</invoke>", RegexOption.DOT_MATCHES_ALL)
    private val PARAM = Regex("<parameter\\s+name\\s*=\\s*\"([^\"]+)\"[^>]*>(.*?)</parameter>", RegexOption.DOT_MATCHES_ALL)
    private val ARGUMENTS = Regex("<arguments\\s*>(.*?)</arguments>", RegexOption.DOT_MATCHES_ALL)
    private val WRAPPERS = listOf("<tool_calls>", "</tool_calls>", "<tool_call>", "</tool_call>")

    /**
     * 流式显示用：把「可能正在到来的工具标记」之前的内容交出去 —— **标记本身一个字都不显示**。
     *
     * 为什么需要：DSML 标记是**逐片流进来**的，整轮结束才做 [extractTextCalls] 会晚一步 ——
     * 流式期间气泡里会先把 `<||DSML||invoke …>` 打出来（用户看到的就是「回复里直接输出命令」）。
     * 这里在最早一个标记起始处截断：标记之前的正文照常流式显示，标记之后一律不显示，
     * 等这一轮结束由 [extractTextCalls] 定性（真调用 → 落工具卡；空标记 → 正文什么都不剩）。
     */
    fun safeStreamText(text: String): String {
        val idx = firstMarkerIndex(text)
        return if (idx >= 0) text.substring(0, idx).trimEnd() else text
    }

    /** 正文里是否含工具标记（宿主路径的最终文本也要过这道：漏出来的标记不许当正文渲染） */
    fun hasToolMarkup(text: String): Boolean = firstMarkerIndex(text) >= 0

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

    /**
     * 从助手正文里抽出**文本形态的工具调用**，并把标记从正文里剔除。
     * @return (清理后的正文, 抽出的调用列表)
     */
    fun extractTextCalls(text: String): Pair<String, List<ToolCall>> {
        if (text.isBlank()) return text to emptyList()
        val hasHint = text.contains("DSML", ignoreCase = true) ||
            text.contains("<invoke", ignoreCase = true) ||
            text.contains("<tool_call", ignoreCase = true)
        if (!hasHint) return text to emptyList()

        // ① 归一化 DSML 标记：`<|DSML|invoke>` → `<invoke>`、`</|DSML|parameter>` → `</parameter>`
        var norm = DSML_CLOSE.replace(text, "</")
        norm = DSML_OPEN.replace(norm, "<")

        val calls = ArrayList<ToolCall>()
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
                calls.add(ToolCall("call_text_${n++}", name, args.toString()))
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
