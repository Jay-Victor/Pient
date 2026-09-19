package com.pient.app.data

import android.content.Context
import android.util.Xml
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

/**
 * docx（OOXML）→ HTML 预览文本。
 *
 * 逐段取文本、转义 `<`/`>`、每段包 `<p>`、空段落丢弃、统一 HTML 外壳与 CSS（Arial / margin 40px）；
 * 粗体 / 斜体按运行（run）的 `w:rPr` 判定（按 run 粒度比整段加粗更接近原文，外壳与段结构不变）。
 *
 * 取字节的方式：直接解 docx 的 `word/document.xml`（zip + XmlPullParser），零第三方依赖
 * （Apache POI 那套在 Android 上需 StAX/awt 兼容层）。
 * 表格：`w:tbl` → HTML `<table>`（`w:tr`/`w:tc` → `<tr><td>`，单元格内段落取文本），外壳补了表格边框样式。
 */
object DocxConverter {

    private const val DOCUMENT_PART = "word/document.xml"

    private const val HTML_HEAD =
        "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>%s</title><style>" +
            "body { font-family: Arial, sans-serif; margin: 40px; }" +
            // 表格补边框样式保证可读
            "table { border-collapse: collapse; }" +
            "td, th { border: 1px solid #999999; padding: 4px 8px; vertical-align: top; }" +
            "</style></head><body>\n"

    /** 转成可渲染的 HTML；失败 / 非 docx 返回 null */
    fun toHtml(context: Context, node: FileNode): String? {
        val src = node.source ?: return null
        val paragraphs = try {
            openStream(context, src)?.use { readParagraphs(it) } ?: return null
        } catch (e: Exception) {
            return null
        }
        if (paragraphs.isEmpty()) return null
        val title = escape(node.name.substringBeforeLast('.'))
        return HTML_HEAD.format(title) + paragraphs.joinToString("") + "</body></html>"
    }

    /**
     * docx → 纯文本。**给 agent 的 read 工具用**：用户用「+」上传的 docx 附件落在
     * `files/attachments/`，模型需要能读到里面的字（UI 侧的富文本渲染仍走 [toHtml]）。
     * 解析路径与 [toHtml] 完全同一份（`word/document.xml` 的 `<w:p>`），只是把标签剥成纯文本。
     *
     * @return null = 不是 docx / 解析失败（调用方据此回退到「二进制不可读」）
     */
    fun toPlainText(context: Context, path: String): String? {
        val paragraphs = try {
            openStream(context, path)?.use { readParagraphs(it) } ?: return null
        } catch (e: Exception) {
            return null
        }
        if (paragraphs.isEmpty()) return null
        return paragraphs.joinToString("\n") { p ->
            unescapeXml(p.replace(Regex("<[^>]*>"), ""))
        }
    }

    /** HTML 实体还原（`toHtml` 的 escape 的逆运算，够用即可） */
    private fun unescapeXml(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

    private fun openStream(context: Context, src: String): InputStream? =
        if (src.startsWith("content://")) {
            context.contentResolver.openInputStream(android.net.Uri.parse(src))
        } else {
            File(src).takeIf { it.exists() }?.inputStream()
        }

    /** 解 zip 取 word/document.xml，逐 `<w:p>` 产出 `<p>…</p>` */
    private fun readParagraphs(input: InputStream): List<String> {
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == DOCUMENT_PART) return parseParagraphs(zip)
                entry = zip.nextEntry
            }
        }
        return emptyList()
    }

    private fun parseParagraphs(input: InputStream): List<String> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, "UTF-8")

        val out = ArrayList<String>()
        var runs: MutableList<Run>? = null      // 当前段落
        var run: Run? = null                    // 当前 run
        var inRunProps = false
        var inText = false
        var table: StringBuilder? = null   // 当前表格 / 行 / 单元格（w:tbl → <table> 等）
        var row: StringBuilder? = null
        var cell: StringBuilder? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.localName()) {
                    "tbl" -> table = StringBuilder("<table>")
                    "tr" -> row = StringBuilder("<tr>")
                    "tc" -> cell = StringBuilder("<td>")
                    "p" -> runs = ArrayList()
                    "r" -> runs?.let { run = Run().also(it::add) }
                    "rPr" -> inRunProps = true
                    "b" -> if (inRunProps) run?.bold = onOff(parser)
                    "i" -> if (inRunProps) run?.italic = onOff(parser)
                    "tab" -> run?.text?.append('\t')
                    "br", "cr" -> run?.text?.append('\n')
                    "t" -> inText = true
                }

                XmlPullParser.TEXT -> if (inText) run?.text?.append(parser.text)

                XmlPullParser.END_TAG -> when (parser.localName()) {
                    "rPr" -> inRunProps = false
                    "t" -> inText = false
                    "r" -> run = null
                    "p" -> {
                        val html = runs?.let { paragraphHtml(it) }.orEmpty()
                        runs = null
                        when {
                            cell != null -> cell.append(html)          // 单元格内段落 → 该单元格
                            table == null -> out.add(html)              // 正文段落
                        }
                    }
                    "tc" -> {
                        row?.append(cell?.append("</td>"))
                        cell = null
                    }
                    "tr" -> {
                        table?.append(row?.append("</tr>"))
                        row = null
                    }
                    "tbl" -> {
                        out.add(table?.append("</table>")?.toString().orEmpty())
                        table = null
                    }
                    "body" -> return out
                }
            }
            event = parser.next()
        }
        return out
    }

    /** 段 HTML：空段落丢弃，run 级 粗体/斜体 */
    private fun paragraphHtml(runs: List<Run>): String {
        val text = runs.joinToString("") { it.text.toString() }
        if (text.isBlank()) return ""
        return buildString {
            append("<p>")
            runs.forEach { r ->
                if (r.text.isEmpty()) return@forEach
                var html = escape(r.text.toString())
                if (r.bold) html = "<strong>$html</strong>"
                if (r.italic) html = "<em>$html</em>"
                append(html)
            }
            append("</p>\n")
        }
    }

    /** `w:b` / `w:i` 无 val 属性即开启；`w:val` 为 0/false/off 视为关闭 */
    private fun onOff(parser: XmlPullParser): Boolean {
        val v = parser.getAttributeValue(null, "w:val") ?: return true
        return !(v == "0" || v.equals("false", ignoreCase = true) || v.equals("off", ignoreCase = true))
    }

    private fun XmlPullParser.localName(): String = name.substringAfterLast(':')

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private class Run(
        val text: StringBuilder = StringBuilder(),
        var bold: Boolean = false,
        var italic: Boolean = false,
    )
}
