package com.pient.app.data

/**
 * 复制消息卡「XML」分段的取数（2026-09-17 用户拍板）。
 *
 * 为什么需要它：复制卡原有「纯文本 / Markdown 源码」两个分段都只给**最终正文**，
 * 思考过程与工具调用过程不在里面。这个分段把目标消息所属**回合的 AI 侧全量**
 * 按发生顺序序列化成一份 XML，便于整段留档 / 转发 / 回灌给别的模型。
 *
 * 口径（2026-09-17 用户拍板）：
 * - 范围 = AI 侧全量：思考 → 正文 → 工具调用（含参数与结果），直到下一条用户消息之前；
 *   长按用户消息时给出该消息自己的 `<user>`（正文 + 引用 + 附件）。
 * - 元信息进属性：`<turn model>`、`<thinking level duration>`、`<tool_call name status duration>`。
 *
 * 取数用 app 侧消息镜像（与界面同源、打开菜单时快照），与复制卡其它分段一致。
 */
fun turnXml(messages: List<Msg>, index: Int): String {
    val target = messages.getOrNull(index) ?: return ""
    val sb = StringBuilder()
    if (target is Msg.User) {
        sb.append("<turn>\n")
        appendUser(sb, target)
        sb.append("</turn>")
        return sb.toString()
    }

    // 回合范围：往前最近一条用户消息之后 → 往后下一条用户消息之前（找不到则取到两端）
    val start = ((index - 1) downTo 0).firstOrNull { messages[it] is Msg.User }?.plus(1) ?: 0
    val end = ((index + 1) until messages.size).firstOrNull { messages[it] is Msg.User }
        ?.minus(1) ?: messages.lastIndex
    val turn = messages.subList(start, end + 1)

    // 模型标签挂在回合末尾那条最终回答上（flushText 落阶段的正文不带模型），
    // 所以从后往前取第一个有模型的助手条目。
    val model = turn.asReversed().firstNotNullOfOrNull { (it as? Msg.Assistant)?.model }
    sb.append("<turn")
    model?.takeIf { it.isNotBlank() }?.let { sb.append(" model=\"").append(xmlAttr(it)).append('"') }
    sb.append(">\n")

    // 工具调用与它的结果在消息流里是相邻两条（ToolCall + ToolResult）→ 结果作为子元素
    // 收进 `</tool_call>` 内；对不上名（并行调用错位）或没有配对的调用时，单独给 `<tool_result>`。
    var openCall: String? = null
    fun closeCall() {
        if (openCall != null) {
            sb.append("  </tool_call>\n")
            openCall = null
        }
    }

    for (m in turn) {
        when (m) {
            is Msg.Thinking -> {
                closeCall()
                sb.append("  <thinking")
                if (m.level.isNotBlank()) sb.append(" level=\"").append(xmlAttr(m.level)).append('"')
                formatDuration(m.durationMs)?.let { sb.append(" duration=\"").append(it).append('"') }
                sb.append('>').append(xmlText(m.text.trim())).append("</thinking>\n")
            }
            is Msg.Assistant -> {
                closeCall()
                val tag = if (m.error) "error" else "text"
                sb.append("  <").append(tag).append('>')
                    .append(xmlText(m.markdown.trim())).append("</").append(tag).append(">\n")
            }
            is Msg.ToolCall -> {
                closeCall()
                sb.append("  <tool_call name=\"").append(xmlAttr(m.name)).append('"')
                    .append(" status=\"").append(m.status.name.lowercase()).append('"')
                formatDuration(m.durationMs)?.let { sb.append(" duration=\"").append(it).append('"') }
                sb.append(">\n")
                if (m.params.isNotBlank()) {
                    sb.append("    <params>").append(xmlText(m.params.trim())).append("</params>\n")
                }
                m.diff?.takeIf { it.isNotBlank() }?.let {
                    sb.append("    <diff>").append(xmlText(it.trim())).append("</diff>\n")
                }
                openCall = m.name
            }
            is Msg.ToolResult -> {
                val body = (m.full ?: m.preview).trim()
                if (openCall != null && (m.toolName.isBlank() || m.toolName == openCall)) {
                    if (body.isNotEmpty()) {
                        sb.append("    <result>").append(xmlText(body)).append("</result>\n")
                    }
                    closeCall()
                } else if (body.isNotEmpty()) {
                    sb.append("  <tool_result name=\"").append(xmlAttr(m.toolName)).append("\">")
                        .append(xmlText(body)).append("</tool_result>\n")
                }
            }
            is Msg.Compaction -> {
                closeCall()
                sb.append("  <compaction before=\"").append(m.tokensBefore)
                    .append("\" saved=\"").append(m.saved).append('"')
                m.reason?.takeIf { it.isNotBlank() }?.let {
                    sb.append(" reason=\"").append(xmlAttr(it)).append('"')
                }
                val summary = m.summary.trim()
                if (summary.isEmpty()) sb.append("/>\n")
                else sb.append('>').append(xmlText(summary)).append("</compaction>\n")
            }
            is Msg.User -> Unit   // 回合范围内不含用户消息（边界已排除）
        }
    }
    closeCall()
    sb.append("</turn>")
    return sb.toString()
}

/** 用户消息侧：`<user>`（引用块 → 正文 → 附件）。 */
private fun appendUser(sb: StringBuilder, m: Msg.User) {
    sb.append("  <user>\n")
    m.quote?.let { q ->
        sb.append("    <quote role=\"").append(xmlAttr(q.role)).append("\">")
            .append(xmlText(q.text.trim())).append("</quote>\n")
    }
    sb.append("    <text>").append(xmlText(m.text.trim())).append("</text>\n")
    m.attachments.forEach { a ->
        sb.append("    <attachment kind=\"").append(a.kind.name.lowercase()).append("\" name=\"")
            .append(xmlAttr(a.name)).append('"')
        a.path?.takeIf { it.isNotBlank() }?.let {
            sb.append(" path=\"").append(xmlAttr(it)).append('"')
        }
        sb.append("/>\n")
    }
    sb.append("  </user>\n")
}

/**
 * XML 文本内容转义：转义 `&` `<` `>`，并**剔除 XML 1.0 不允许的控制字符**
 * （终端/工具输出里常带 ESC 等控制码，原样留着这份 XML 就不是合法文档了）。
 */
private fun xmlText(s: String): String {
    val sb = StringBuilder(s.length + 16)
    for (ch in s) {
        when {
            ch == '&' -> sb.append("&amp;")
            ch == '<' -> sb.append("&lt;")
            ch == '>' -> sb.append("&gt;")
            ch == '\t' || ch == '\n' || ch == '\r' -> sb.append(ch)
            ch.code < 0x20 -> Unit
            ch.code == 0xFFFE || ch.code == 0xFFFF -> Unit
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

/** 属性值转义：文本转义 + 引号；换行折叠成空格（属性值里的换行解析时也会被规范化）。 */
private fun xmlAttr(s: String): String =
    xmlText(s.replace('\r', ' ').replace('\n', ' ')).replace("\"", "&quot;")
