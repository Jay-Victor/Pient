package com.pient.app.data

/**
 * 文件类型家族判定（口径对齐 Operit `WorkspaceFilePreviewSupport`）：
 * Operit 用 `name.endsWith(".html", ignoreCase = true)` / `name.endsWith(".md", ignoreCase = true)`
 * 这类忽略大小写的后缀判断；Pient 统一在 `FileNode.ext` 里小写化（见 Models.kt），
 * 这里只写小写后缀，避免「.HTML / .Markdown 掉进编辑器分支」这类偏差。
 */

/** markdown 家族（Operit isMarkdown：.md / .markdown） */
val MARKDOWN_EXTS = setOf("md", "markdown")

/** HTML 家族（Operit isHtml：.html / .htm） */
val HTML_EXTS = setOf("html", "htm")

/** 带「渲染/源码」切换键的扩展名（markdown 与 html 同款交互） */
val SOURCE_TOGGLE_EXTS = MARKDOWN_EXTS + HTML_EXTS

// ── 媒体家族（**唯一出处**：文件预览分流 FileContentView 与上下文裁剪 ContextPolicy 共用；
//    2026-09-13 从 FileContentView 的 PREVIEW_* 上移到这里 —— 上下文层（data）要用同一份判定，
//    不能各写一套扩展名清单，否则「预览认得的图片、上下文裁剪不认得」必然漂移）──

/** 图片家族（Operit workspaceMimeTypeForPath 的 image 分支口径） */
val MEDIA_IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif")

/** 视频家族（Operit workspaceMimeTypeForPath 的 video 分支） */
val MEDIA_VIDEO_EXTS = setOf("webm", "mp4", "m4v", "mov", "mkv", "avi", "3gp")

/** 音频家族（Operit workspaceMimeTypeForPath 的 audio 分支） */
val MEDIA_AUDIO_EXTS = setOf("mp3", "wav", "m4a", "aac", "ogg", "opus", "flac")

/** 音视频家族（上下文裁剪里的「媒体」= Operit 的 media 链接口径：video + audio） */
val MEDIA_AV_EXTS = MEDIA_VIDEO_EXTS + MEDIA_AUDIO_EXTS

/** 文件名的扩展名（小写；无扩展名 = 空串）——与 FileNode.ext 同口径 */
fun extOf(nameOrPath: String): String =
    nameOrPath.substringAfterLast('/', nameOrPath).substringAfterLast('.', "").lowercase()
