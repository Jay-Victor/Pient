package com.pient.app.data

import android.content.Context
import java.io.File

/**
 * **媒体直发部件（请求层）** —— 按「模型能力 → 模型支持识图」把本条用户消息里的**图片**转成
 * 随请求发出的内容部件：
 * - 开关**开** → 图片本体转内容部件（pi RPC `prompt.images` 的 ImageContent）；
 * - 开关**关**（或超上限 / 读取失败）→ **不拦消息**，只追加一行说明（关 = 占位文案）；
 *   没被直发的附件仍以「名称 · 路径」留在正文里。
 *
 * **只有图片**：pi 的用户消息内容类型只有 text / image
 * （`ImageContent`，见 pi `docs/rpc.md`）—— 音频 / 视频在 pi 通道里发不出去，
 * 音频 / 视频附件按**普通文件**处理（正文里仍列「名称 · 路径」，
 * AI 可以用自己的工具去读）。
 *
 * 独立成层：它管的是**请求体怎么拼**，与工具执行无关。
 */
object MediaInline {

    /** base64 让请求体膨胀约 1/3，直发上限（超出则不直发，正文里给一行说明） */
    private const val INLINE_IMAGE_MAX = 6L * 1024 * 1024

    /** 未直发时的占位文案（与「读回」侧共用 [ContextPolicy] 那一份） */
    private val OMIT_IMAGE = ContextPolicy.OMIT_IMAGE

    /**
     * 直发结果：
     * - [parts]：随请求发出的内容部件；
     * - [inlinedIndexes]：已直发的附件下标（正文里不再重复列「名称 · 路径」）；
     * - [notes]：未直发时拼进正文的说明行（能力关 → 占位文案；超上限 / 读取失败 → 各自说明）。
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
        modelId: String,
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
            // 只有图片进请求（音频 / 视频 / 其它文件不进请求，也不给「未直发」提示 ——
            // 它们本来就是以「名称 · 路径」进正文的普通附件）
            if (ext !in MEDIA_IMAGE_EXTS) return@forEachIndexed
            // 逐模型：这个模型自己开没开识图（没有逐模型条目时落回卡面默认值）
            if (!cfg.settingOf(modelId).image) {
                notes.add(OMIT_IMAGE)
                return@forEachIndexed
            }
            if (file.length() > INLINE_IMAGE_MAX) {
                notes.add("[附件未直发] ${a.name}（${file.length() / 1024 / 1024}MB 超过直发上限 ${INLINE_IMAGE_MAX / 1024 / 1024}MB）")
                return@forEachIndexed
            }
            val b64 = runCatching {
                android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
            }.getOrNull()
            if (b64 == null) {
                notes.add("[附件未直发] ${a.name}（读取失败）")
                return@forEachIndexed
            }
            parts.add(WirePart("image", mimeOf(file.name), b64))
            inlined.add(index)
        }
        return InlineResult(parts, inlined, notes)
    }
}
