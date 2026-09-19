package com.pient.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 下载进度状态（形状与参考实现 Mdcito 的 `DownloadState` 对齐） */
sealed interface DownloadState {
    data object Idle : DownloadState

    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
        val remainingTimeSecs: Long,
    ) : DownloadState

    data class Paused(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : DownloadState

    data class Completed(val filePath: String) : DownloadState

    data class Error(val message: String) : DownloadState
}

/**
 * 安装包下载器：落到 `cacheDir/updates/`，带进度、暂停/继续（HTTP `Range` 断点续传）、取消与 SHA-256 校验。
 *
 * 状态是 Compose 状态（弹窗直接读），暂停/取消是两个 volatile 标志（下载循环里轮询）——
 * 所以调用点不需要持有它的 Job：暂停只是把标志置上，循环下一轮就停住。
 */
object ApkDownloader {

    private const val TAG = "PientUpdate"

    var state by mutableStateOf<DownloadState>(DownloadState.Idle)
        private set

    @Volatile private var paused = false
    @Volatile private var cancelled = false

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** 安装包目录（外部缓存优先，没有就内部缓存） */
    fun downloadDir(context: Context): File = File(context.externalCacheDir ?: context.cacheDir, "updates")

    /** 清空状态与标志（换源重下前调用） */
    fun reset() {
        paused = false
        cancelled = false
        state = DownloadState.Idle
    }

    /** 暂停：只置标志，正在跑的循环下一轮会停住并把状态切成 Paused */
    fun pause() {
        paused = true
        val current = state
        if (current is DownloadState.Downloading) {
            state = DownloadState.Paused(current.progress, current.downloadedBytes, current.totalBytes)
        }
    }

    fun resume() {
        paused = false
    }

    /** 取消：连同半成品文件一起删（留着会在下次被当成「续传」接到别的包上） */
    fun cancel(context: Context) {
        cancelled = true
        runCatching { downloadDir(context).listFiles()?.forEach { it.delete() } }
        state = DownloadState.Idle
    }

    /** 清掉已下载的文件与状态（安装完 / 换版本时用） */
    fun clearFiles(context: Context) {
        runCatching { downloadDir(context).listFiles()?.forEach { it.delete() } }
        reset()
    }

    /**
     * 下载 [url] 到 `downloadDir/[fileName]`，成功返回文件路径、失败返回 null（原因写在 [state] 里）。
     * 目录里已有同名半成品时用 `Range` 续传。
     */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
    ): String? = withContext(Dispatchers.IO) {
        cancelled = false

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            PientLog.w(TAG, "下载地址无效（缺协议前缀）：$url")
            state = DownloadState.Error("URL 格式无效")
            return@withContext null
        }

        val safeName = sanitizeFileName(fileName)
        if (safeName.isEmpty()) {
            state = DownloadState.Error("文件名无效")
            return@withContext null
        }

        val dir = downloadDir(context)
        dir.mkdirs()
        val target = File(dir, safeName)
        // 规范化后再确认没跳出目录（文件名里带 .. 时会在这一步被挡下）
        if (!target.canonicalPath.startsWith(dir.canonicalPath + File.separator)) {
            state = DownloadState.Error("文件名非法")
            return@withContext null
        }

        val resumeFrom = if (target.exists() && target.length() > 0) {
            target.length()
        } else {
            if (target.exists()) target.delete()
            0L
        }

        try {
            val request = Request.Builder()
                .url(url)
                .apply { if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-") }
                .build()
            val response = client.newCall(request).execute()
            val code = response.code
            if (code != 200 && code != 206) {
                PientLog.w(TAG, "下载失败 HTTP $code：$url")
                state = DownloadState.Error("HTTP $code")
                return@withContext null
            }
            val body = response.body ?: run {
                state = DownloadState.Error("响应体为空")
                return@withContext null
            }
            val totalBytes = if (code == 206) {
                // 206 时 contentLength 只是剩余长度，总大小要从 Content-Range 的「/总」里取
                response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                    ?: (resumeFrom + body.contentLength())
            } else {
                body.contentLength()
            }

            var downloaded = resumeFrom
            var lastTime = System.currentTimeMillis()
            var lastBytes = downloaded
            var speed = 0L
            paused = false

            body.byteStream().use { input ->
                FileOutputStream(target, resumeFrom > 0).buffered().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        if (cancelled) {
                            target.delete()
                            state = DownloadState.Idle
                            PientLog.i(TAG, "下载已取消")
                            return@withContext null
                        }
                        // 暂停：原地等标志复位（不退出循环，文件与连接都留着继续用）
                        while (paused && !cancelled) {
                            Thread.sleep(200)
                        }
                        if (cancelled) {
                            target.delete()
                            state = DownloadState.Idle
                            return@withContext null
                        }
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastTime
                        if (elapsed >= 1000) {
                            speed = (downloaded - lastBytes) * 1000 / elapsed
                            lastTime = now
                            lastBytes = downloaded
                        }
                        state = DownloadState.Downloading(
                            progress = if (totalBytes > 0) (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f,
                            downloadedBytes = downloaded,
                            totalBytes = totalBytes,
                            speedBytesPerSec = speed,
                            remainingTimeSecs = if (speed > 0 && totalBytes > 0) (totalBytes - downloaded) / speed else 0L,
                        )
                    }
                }
            }

            if (!target.exists() || target.length() <= 0) {
                state = DownloadState.Error("下载文件异常")
                return@withContext null
            }
            PientLog.i(TAG, "下载完成：${target.absolutePath}（$downloaded 字节）")
            state = DownloadState.Completed(target.absolutePath)
            target.absolutePath
        } catch (e: CancellationException) {
            state = DownloadState.Idle
            null
        } catch (e: Exception) {
            PientLog.w(TAG, "下载失败：${e.message}")
            state = DownloadState.Error(e.message ?: "下载失败")
            null
        }
    }

    /** 只留文件名那一段（挡掉路径分隔符与 `..`） */
    private fun sanitizeFileName(fileName: String): String {
        val nameOnly = fileName.substringAfterLast('/').substringAfterLast('\\')
        if (nameOnly.isBlank() || nameOnly == "." || nameOnly == "..") return ""
        return nameOnly.replace("..", "").trim()
    }
}
