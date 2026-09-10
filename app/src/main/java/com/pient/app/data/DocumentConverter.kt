package com.pient.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.InputStream
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory

/**
 * 老格式与表格文档 → 预览 HTML（口径逐项对齐 Operit `DocumentConversionUtil`）：
 * - `.doc`（Word 97-2003 二进制）：`HWPFDocument` + `WordExtractor` 取全文 → 逐段 `<p>`（Operit convertToHtml 的 doc 分支）
 * - `.xls` / `.xlsx`：`WorkbookFactory` + `DataFormatter`（含公式求值）→ 多表切换的 HTML（Operit convertSpreadsheetToHtml）
 *
 * 依赖 Apache POI（Operit 同款 5.2.3 三件套）。POI 的 OOXML 路径（docx/xlsx）需要 StAX，
 * Pient 的 docx 预览另走 `DocxConverter`（自解析，零依赖）；此处仅 .xls / .xlsx 走 POI。
 */
object DocumentConverter {

    private const val HTML_HEAD =
        "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>%s</title><style>" +
            "body { font-family: Arial, sans-serif; margin: 40px; }" +
            "</style></head><body>\n"

    private val SPREADSHEET_STYLE = """
        body { font-family: sans-serif; margin: 16px; color: #1f2937; background: #f8fafc; }
        h1 { margin: 0 0 16px; font-size: 20px; }
        .sheet-tabs { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
        .sheet-tab { border: 1px solid #cbd5e1; background: white; border-radius: 999px; padding: 6px 12px; cursor: pointer; }
        .sheet-tab.active { background: #111827; color: white; border-color: #111827; }
        .sheet-panel { display: none; overflow: auto; background: white; border: 1px solid #e2e8f0; border-radius: 12px; padding: 12px; }
        .sheet-panel.active { display: block; }
        table { border-collapse: collapse; min-width: 100%; }
        th, td { border: 1px solid #dbe4ee; padding: 8px 10px; text-align: left; vertical-align: top; white-space: pre-wrap; }
        th { background: #eef2ff; position: sticky; top: 0; }
        .empty { color: #94a3b8; padding: 24px 0; }
    """.trimIndent()

    private const val SPREADSHEET_SCRIPT = """
        <script>
        function showSheet(index) {
          document.querySelectorAll('.sheet-panel').forEach((panel, panelIndex) => {
            panel.classList.toggle('active', panelIndex === index);
          });
          document.querySelectorAll('.sheet-tab').forEach((tab, tabIndex) => {
            tab.classList.toggle('active', tabIndex === index);
          });
        }
        </script>
    """

    /** .doc → HTML（Operit doc 分支：全文按行拆段，空行丢弃） */
    fun docToHtml(context: Context, node: FileNode): String? {
        val text = try {
            openStream(context, node)?.use { input ->
                HWPFDocument(input).use { doc -> WordExtractor(doc).text }
            }
        } catch (e: Exception) {
            null
        } ?: return null

        val body = buildString {
            text.split("\n").forEach { para ->
                if (para.isNotBlank()) append("<p>").append(escape(para)).append("</p>\n")
            }
        }
        if (body.isBlank()) return null
        return HTML_HEAD.format(escape(node.name.substringBeforeLast('.'))) + body + "</body></html>"
    }

    /** .xls / .xlsx → HTML（Operit convertSpreadsheetToHtml：多表 tab 切换 + 列表头 + DataFormatter 取值） */
    fun spreadsheetToHtml(context: Context, node: FileNode): String? {
        return try {
            openStream(context, node)?.use { input ->
                WorkbookFactory.create(input).use { workbook ->
                    val formatter = DataFormatter()
                    val evaluator = workbook.creationHelper.createFormulaEvaluator()
                    val sheetCount = workbook.numberOfSheets
                    val html = StringBuilder()
                        .append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">")
                        .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                        .append("<title>").append(escape(node.name.substringBeforeLast('.'))).append("</title><style>")
                        .append(SPREADSHEET_STYLE)
                        .append("</style></head><body>")
                        .append("<h1>").append(escape(node.name)).append("</h1>")

                    if (sheetCount > 1) {
                        html.append("<div class=\"sheet-tabs\">")
                        for (i in 0 until sheetCount) {
                            html.append("<button class=\"sheet-tab").append(if (i == 0) " active" else "")
                                .append("\" onclick=\"showSheet(").append(i).append(")\">")
                                .append(escape(workbook.getSheetAt(i).sheetName))
                                .append("</button>")
                        }
                        html.append("</div>")
                    }

                    for (sheetIndex in 0 until sheetCount) {
                        val sheet = workbook.getSheetAt(sheetIndex)
                        val maxColumns = sheet.maxOfOrNull { row -> row.lastCellNum.toInt().coerceAtLeast(0) } ?: 0
                        html.append("<section class=\"sheet-panel")
                            .append(if (sheetIndex == 0) " active" else "")
                            .append("\" data-sheet-index=\"").append(sheetIndex).append("\">")
                            .append("<h2>").append(escape(sheet.sheetName)).append("</h2>")

                        if (sheet.physicalNumberOfRows == 0 || maxColumns == 0) {
                            html.append("<div class=\"empty\">Empty sheet</div></section>")
                            continue
                        }

                        html.append("<table><thead><tr>")
                        for (column in 0 until maxColumns) {
                            html.append("<th>").append(columnName(column)).append("</th>")
                        }
                        html.append("</tr></thead><tbody>")

                        sheet.forEach { row ->
                            html.append("<tr>")
                            for (column in 0 until maxColumns) {
                                val value = row.getCell(column)?.let { formatter.formatCellValue(it, evaluator) }.orEmpty()
                                html.append("<td>").append(escape(value).replace("\n", "<br>")).append("</td>")
                            }
                            html.append("</tr>")
                        }
                        html.append("</tbody></table></section>")
                    }

                    if (sheetCount > 1) html.append(SPREADSHEET_SCRIPT)
                    html.append("</body></html>")
                    html.toString()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun openStream(context: Context, node: FileNode): InputStream? {
        val src = node.source ?: return null
        return if (src.startsWith("content://")) {
            context.contentResolver.openInputStream(android.net.Uri.parse(src))
        } else {
            File(src).takeIf { it.exists() }?.inputStream()
        }
    }

    // ── PDF（Operit WorkspacePdfPreview 同款：PdfRenderer 逐页位图） ──

    /** PDF 页数；无法打开 / 空文档返回 0 */
    fun pdfPageCount(context: Context, node: FileNode): Int = try {
        openPfd(context, node)?.use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
        } ?: 0
    } catch (e: Exception) {
        0
    }

    /** 渲染第 pageIndex 页（2 倍分辨率、白底、RENDER_MODE_FOR_DISPLAY），失败 null */
    fun renderPdfPage(context: Context, node: FileNode, pageIndex: Int): Bitmap? = try {
        openPfd(context, node)?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) return@use null
                renderer.openPage(pageIndex).use { page ->
                    val scale = 2
                    val bitmap = Bitmap.createBitmap(
                        page.width * scale,
                        page.height * scale,
                        Bitmap.Config.ARGB_8888,
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun openPfd(context: Context, node: FileNode): ParcelFileDescriptor? {
        val src = node.source ?: return null
        return if (src.startsWith("content://")) {
            context.contentResolver.openFileDescriptor(android.net.Uri.parse(src), "r")
        } else {
            val f = File(src)
            if (f.exists()) ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY) else null
        }
    }

    /** 列名 A、B…Z、AA…（Operit columnName 同款） */
    private fun columnName(index: Int): String {
        var current = index
        val result = StringBuilder()
        do {
            result.insert(0, ('A'.code + (current % 26)).toChar())
            current = current / 26 - 1
        } while (current >= 0)
        return result.toString()
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
