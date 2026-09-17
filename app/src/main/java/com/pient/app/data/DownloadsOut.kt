package com.pient.app.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.pient.app.data.i18n.L
import java.io.File

/**
 * 「导出到系统下载目录」的唯一实现（2026-09-17 从 [SessionExport] 抽出）。
 *
 * 语义：写系统「下载/Pient/<fileName>」——API 29+ 走 MediaStore（免权限、系统文件管理器可见）；
 * API 26~28 公共下载目录需要 WRITE_EXTERNAL_STORAGE，不引权限 → 落应用自己的外部下载目录
 * （同样免权限，但路径在 Android/data 下）。
 *
 * 抽取原因（用户口径「同一语义一份实现」）：日志导出与会话导出是同一个语义（把一段文本
 * 交给用户能在文件管理器里拿到的位置），两份实现必然漂移（MIME / 目录名 / 失败兜底各写一遍）。
 * 2026-09-17 第二次收口：把 MediaStore 的 uri 一起带出来（[Written]），导出后可直接分享 —— 不再需要
 * 重新在 Downloads 里找文件，也不再为 API 29+ 走 FileProvider。
 */
object DownloadsOut {

    /** 下载目录下的子目录名（会话导出与日志导出共用） */
    const val REL_DIR = "Pient"

    /** 导出结果：展示用落点描述 + 可直接分享的 uri（API 26~28 走 FileProvider 时也在这里） */
    data class Written(val location: String, val uri: Uri?, val mime: String, val fileName: String)

    /**
     * 写文件；失败返回 null（调用方给「导出失败」提示）。调用方放 IO 线程。
     */
    fun writeText(context: Context, fileName: String, mime: String, content: String): Written? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + REL_DIR)
            }
            val uri: Uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching null
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                ?: return@runCatching null
            PientLog.i("PientExport", "已导出到下载/$REL_DIR/$fileName")
            Written(L.project.exportLocation(REL_DIR, fileName), uri, mime, fileName)
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return@runCatching null
            val f = File(dir, fileName)
            f.writeText(content)
            PientLog.i("PientExport", "已导出到 ${f.absolutePath}")
            // API 26~28 没有 MediaStore uri：分享走 FileProvider（manifest 里的 provider + file_paths.xml）
            val uri = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            }.getOrNull()
            Written(f.absolutePath, uri, mime, fileName)
        }
    }.onFailure { PientLog.w("PientExport", "导出失败：${it.message}") }.getOrNull()

    /**
     * 分享已导出的文件（系统分享面板）。uri 缺失（FileProvider 也拿不到）时返回 null ——
     * 调用方据此提示「文件已导出，可到下载目录自取」。
     */
    fun shareIntent(written: Written, chooserTitle: String): Intent? {
        val uri = written.uri ?: return null
        val send = Intent(Intent.ACTION_SEND).apply {
            type = written.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, written.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, chooserTitle)
    }
}
