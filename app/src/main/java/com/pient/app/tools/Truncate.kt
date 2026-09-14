package com.pient.app.tools

/**
 * 工具输出截断（**唯一实现**，口径同 pi 的 `core/tools/truncate.ts`：先按行、再按字节）。
 *
 * 三处都要它：自身工具层（read/ls/find/grep）、终端层（bash 的一整段输出）、
 * 以及 M2 起宿主回桥的批量回传。同一份常量，避免「read 截 2000 行而 bash 截 500 行」这类漂移。
 */
object Truncate {

    const val MAX_LINES = 2000
    const val MAX_BYTES = 50 * 1024

    fun of(text: String, note: String): String {
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
        return if (truncated) {
            "$out\n\n[$note：输出已截断（上限 $MAX_LINES 行 / ${MAX_BYTES / 1024}KB）]"
        } else {
            out
        }
    }
}
