package com.pient.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
 */
object DownloadsOut {

    /** 下载目录下的子目录名（会话导出与日志导出共用） */
    const val REL_DIR = "Pient"

    /**
     * 写文件；返回**落点描述**（`下载/Pient/x.txt`，API 26~28 为绝对路径）供 Toast 展示；
     * 失败返回 null（调用方给「导出失败」提示）。调用方放 IO 线程。
     */
    fun writeText(context: Context, fileName: String, mime: String, content: String): String? = runCatching {
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
            L.project.exportLocation(REL_DIR, fileName)
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return@runCatching null
            val f = File(dir, fileName)
            f.writeText(content)
            PientLog.i("PientExport", "已导出到 ${f.absolutePath}")
            f.absolutePath
        }
    }.onFailure { PientLog.w("PientExport", "导出失败：${it.message}") }.getOrNull()
}
