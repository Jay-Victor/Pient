package com.pient.app.data

import android.content.Context
import java.io.File

/**
 * **媒体直发部件（请求层）** —— 按「模型能力」的三个媒体开关把本条用户消息的附件转成
 * 随请求发出的内容部件（照 Operit `OpenAIProvider.buildContentField` 的口径）：
 * - 该类型开关**开** → 文件本体转内容部件（图片 `image_url` / 音频 `input_audio` / 视频 `video_url`）；
 * - 该类型开关**关**（或超上限 / 读取失败）→ **不拦消息**，只追加一行说明（关 = Operit 原文占位文案）；
 *   没被直发的附件仍以「名称 · 路径」留在正文里。
 *
 * 独立成层（2026-09-14）：它管的是**请求体怎么拼**，与工具执行无关 ——
 * 早期挤在单文件实现里，让一个文件同时背着请求层与提示词层的职责。
 */
object MediaInline {

    /** base64 让请求体膨胀约 1/3，各类直发上限（超出则不直发，正文里给一行说明） */
    private const val INLINE_IMAGE_MAX = 6L * 1024 * 1024
    private const val INLINE_AUDIO_MAX = 12L * 1024 * 1024
    private const val INLINE_VIDEO_MAX = 24L * 1024 * 1024

    /** 未直发时的占位文案（Operit strings.xml 原文：`openai_image_omitted` / `openai_audio_video_omitted`） */
    private const val OMIT_IMAGE = "图片内容已省略，当前模型不支持图片处理"
    private const val OMIT_MEDIA = "音视频内容已省略，当前模型不支持音视频处理"

    /**
     * 直发结果：
     * - [parts]：随请求发出的内容部件；
     * - [inlinedIndexes]：已直发的附件下标（正文里不再重复列「名称 · 路径」，对齐 Operit 的「移除链接」）；
     * - [notes]：未直发时拼进正文的说明行（能力关 → Operit 原文占位；超上限 / 读取失败 → 各自说明）。
     */
    data class InlineResult(
        val parts: List<WirePart>,
        val inlinedIndexes: Set<Int>,
        val notes: List<String>,
    )

    fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "heic" -> "image/heic"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "3gp" -> "video/3gpp"
        else -> "application/octet-stream"
    }

    fun parts(
        context: Context,
        attachments: List<Attachment>,
        cfg: ProviderConfig,
    ): InlineResult {
        if (attachments.isEmpty()) return InlineResult(emptyList(), emptySet(), emptyList())
        val parts = ArrayList<WirePart>()
        val inlined = LinkedHashSet<Int>()
        val notes = ArrayList<String>()
        attachments.forEachIndexed { index, a ->
            val path = a.path ?: return@forEachIndexed
            val file = File(path)
            if (!file.isFile) return@forEachIndexed
            val ext = file.name.substringAfterLast('.', "").lowercase()
            val (type, max) = when {
                ext in MEDIA_IMAGE_EXTS -> "image" to INLINE_IMAGE_MAX
                ext in MEDIA_VIDEO_EXTS -> "video" to INLINE_VIDEO_MAX
                ext in MEDIA_AUDIO_EXTS -> "audio" to INLINE_AUDIO_MAX
                else -> return@forEachIndexed
            }
            val enabled = when (type) {
                "image" -> cfg.imageDirectEnabled
                "audio" -> cfg.audioDirectEnabled
                else -> cfg.videoDirectEnabled
            }
            if (!enabled) {
                notes.add(if (type == "image") OMIT_IMAGE else OMIT_MEDIA)
                return@forEachIndexed
            }
            if (file.length() > max) {
                notes.add("[附件未直发] ${a.name}（${file.length() / 1024 / 1024}MB 超过直发上限 ${max / 1024 / 1024}MB）")
                return@forEachIndexed
            }
            val b64 = runCatching {
                android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
            }.getOrNull()
            if (b64 == null) {
                notes.add("[附件未直发] ${a.name}（读取失败）")
                return@forEachIndexed
            }
            parts.add(WirePart(type, mimeOf(file.name), b64))
            inlined.add(index)
        }
        return InlineResult(parts, inlined, notes)
    }
}
