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
