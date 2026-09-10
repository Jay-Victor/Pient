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
 * 口径与 Operit `DocumentConversionUtil.convertToHtml` 的 docx 分支一致：
 * 逐段取文本、转义 `<`/`>`、每段包 `<p>`、空段落丢弃、同一套 HTML 外壳与 CSS（Arial / margin 40px）；
 * 粗体 / 斜体按运行（run）的 `w:rPr` 判定（Operit 是按「段内任一 run 有格式则整段加粗」，此处按 run 粒度，
 * 视觉更接近原文，外壳与段结构不变）。
 *
 * 与 Operit 的差异只在取字节的方式：Operit 走 Apache POI（`XWPFWordExtractor`，Android 上需 StAX/awt 兼容层），
 * 这里直接解 docx 的 `word/document.xml`（zip + XmlPullParser），零第三方依赖。
 * 同 Operit：只取正文顶层段落，表格内容不取（POI 的 `getParagraphs()` 不含表格）。
 */
object DocxConverter {

    private const val DOCUMENT_PART = "word/document.xml"

    private const val HTML_HEAD =
        "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>%s</title><style>" +
            "body { font-family: Arial, sans-serif; margin: 40px; }" +
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

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.localName()) {
                    // 表格：Operit 口径不取（POI getParagraphs 不含表格单元格），整段跳过
                    "tbl" -> skipElement(parser)
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
                    "p" -> runs?.let { out.add(paragraphHtml(it)) }
                        .also { runs = null }
                    "body" -> return out
                }
            }
            event = parser.next()
        }
        return out
    }

    /** 段 HTML：空段落丢弃（Operit 同款），run 级 粗体/斜体 */
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

    private fun skipElement(parser: XmlPullParser) {
        val target = parser.depth
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.END_TAG && parser.depth == target) return
            event = parser.next()
        }
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
