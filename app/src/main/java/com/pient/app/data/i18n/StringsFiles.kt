package com.pient.app.data.i18n

/** files 组文案（由 i18n 流水线生成；zh = 源码原文字面量） */
interface FilesStrings {
    fun created(a0: Any?, a1: Any?): String
    fun deleteConfirm(a0: Any?, a1: Any?): String
    val deleteDirWarn: String
    val redo: String
    fun openFailed(a0: Any?): String
    val typeUnspecified: String
    val symParen: String
    val symBracket: String
    val symBrace: String
    val symAngle: String
    val symDoubleQuote: String
    val symSingleQuote: String
    val symBacktick: String
    val symPlus: String
    val symMinus: String
    val symMultiply: String
    val symDivide: String
    val symPercent: String
    val symEquals: String
    val symEqualTo: String
    val symNotEqualTo: String
    val symLessOrEqual: String
    val symGreaterOrEqual: String
    val symLogicalAnd: String
    val symLogicalOr: String
    val symArrow: String
    val symFatArrow: String
    val symLogicalNot: String
    val symBitwiseAnd: String
    val symBitwiseOr: String
    val symBitwiseNot: String
    val symComma: String
    val symSemicolon: String
    val symColon: String
    val symDot: String
    val symUnderscore: String
    val symHash: String
    val symAt: String
    val symDollar: String
    val symCaret: String
    val symQuestion: String
    val symBackslash: String
    val tooLargeHint: String
    val binaryHint: String
    val playUnavailable: String
    val imageLoadFailed: String
    val imagePlaceholder: String
    val sortByName: String
    val sortBySize: String
    val sortByTime: String
    fun importedCount(a0: Any?, a1: Any?): String
    val importFolderDone: String
    val importFailed: String
    val exportDone: String
    val exportFailed: String
    val noProject: String
    val sort: String
    val importFile: String
    val importFolder: String
    val exportSelected: String
    val exportProject: String
    val collapseAll: String
    val noSearchResults: String
    val treeLoading: String
    val treeNoProject: String
    val treeTruncated: String
    fun selectedCount(a0: Any?): String
    val createFailed: String
    val mentionInsert: String
    fun mentionInserted(a0: Any?): String
    val renamed: String
    fun deleted(a0: Any?): String
    val typeText: String
    val typePng: String
    val typeJpg: String
    val typeMp4: String
    val typeMp3: String
    val typeZip: String
    val typeApk: String
    val folder: String
    val fileType: String
    fun nameHintAutoExt(a0: Any?): String
    val nameHint: String
    val folderNameHint: String
    fun saved(a0: Any?): String
    fun saveFailedFor(a0: Any?): String
    val previewTitle: String
    val emptyHint: String
    val fileTree: String
    val saveChangesTitle: String
    fun savedOnClose(a0: Any?): String
    fun saveFailedOnClose(a0: Any?): String
    val dontSave: String
    fun unsavedMessage(a0: Any?): String
    val unsaved: String
    val renderMode: String
    val editMode: String
    fun headingLevel(a0: Any?): String
    val italic: String
    val bold: String
    val boldItalic: String
    val strikethrough: String
    val horizontalRule: String
    val bulletList: String
    val numberedList: String
    val taskList: String
    val inlineCode: String
    val codeBlock: String
    val quote: String
    val link: String
    val table: String
    val searchHint: String
    val matchCase: String
    val wholeWord: String
    val regex: String
    fun matchCount(a0: Any?): String
    val replaceHint: String
    val replace: String
    val noMatches: String
    val checked: String
    val placeholderText: String
    val placeholderLinkText: String
    val placeholderCode: String
    val placeholderImageAlt: String
    val placeholderImageUrl: String
    val replaceNothingSelected: String
    fun tableColumn(a0: Any?): String
}

object ZhFiles : FilesStrings {
    override fun created(a0: Any?, a1: Any?): String = "已创建${a0} ${a1}"
    override fun deleteConfirm(a0: Any?, a1: Any?): String = "确定要删除「${a0}」吗？${a1}此操作不可撤销。"
    override val deleteDirWarn: String = "其下所有内容将一并删除。"
    override val redo: String = "取消撤销"
    override fun openFailed(a0: Any?): String = "无法打开文件: ${a0}"
    override val typeUnspecified: String = "不指定（手动输入后缀）"
    override val symParen: String = "圆括号"
    override val symBracket: String = "方括号"
    override val symBrace: String = "花括号"
    override val symAngle: String = "尖括号"
    override val symDoubleQuote: String = "双引号"
    override val symSingleQuote: String = "单引号"
    override val symBacktick: String = "反引号"
    override val symPlus: String = "加号"
    override val symMinus: String = "减号"
    override val symMultiply: String = "乘号"
    override val symDivide: String = "除号"
    override val symPercent: String = "百分号"
    override val symEquals: String = "等号"
    override val symEqualTo: String = "等于"
    override val symNotEqualTo: String = "不等于"
    override val symLessOrEqual: String = "小于等于"
    override val symGreaterOrEqual: String = "大于等于"
    override val symLogicalAnd: String = "逻辑与"
    override val symLogicalOr: String = "逻辑或"
    override val symArrow: String = "箭头"
    override val symFatArrow: String = "粗箭头"
    override val symLogicalNot: String = "非"
    override val symBitwiseAnd: String = "与"
    override val symBitwiseOr: String = "或"
    override val symBitwiseNot: String = "取反"
    override val symComma: String = "逗号"
    override val symSemicolon: String = "分号"
    override val symColon: String = "冒号"
    override val symDot: String = "点号"
    override val symUnderscore: String = "下划线"
    override val symHash: String = "井号"
    override val symAt: String = "at 号"
    override val symDollar: String = "美元号"
    override val symCaret: String = "脱字符"
    override val symQuestion: String = "问号"
    override val symBackslash: String = "反斜杠"
    override val tooLargeHint: String = "文件超过 2MB，暂不支持预览"
    override val binaryHint: String = "二进制文件，暂不支持预览"
    override val playUnavailable: String = "无法播放：文件位置不可用"
    override val imageLoadFailed: String = "图片加载失败或格式不支持"
    override val imagePlaceholder: String = "图片预览占位"
    override val sortByName: String = "按名称"
    override val sortBySize: String = "按大小"
    override val sortByTime: String = "按修改时间"
    override fun importedCount(a0: Any?, a1: Any?): String = "已导入 ${a0}/${a1} 个文件"
    override val importFolderDone: String = "已导入文件夹"
    override val importFailed: String = "导入失败"
    override val exportDone: String = "已导出到所选目录"
    override val exportFailed: String = "导出失败"
    override val noProject: String = "未绑定项目"
    override val sort: String = "排序"
    override val importFile: String = "导入文件"
    override val importFolder: String = "导入文件夹"
    override val exportSelected: String = "批量导出"
    override val exportProject: String = "导出项目"
    override val collapseAll: String = "折叠全部"
    override val noSearchResults: String = "未找到匹配文件"
    override val treeLoading: String = "正在读取文件树…"
    override val treeNoProject: String = "绑定项目后显示文件树"
    override val treeTruncated: String = "文件过多，仅显示部分内容"
    override fun selectedCount(a0: Any?): String = "已选 ${a0} 项"
    override val createFailed: String = "创建失败（名称无效或目录不可写）"
    override val mentionInsert: String = "@ 提及插入输入框"
    override fun mentionInserted(a0: Any?): String = "@${a0} 已插入输入框"
    override val renamed: String = "已重命名"
    override fun deleted(a0: Any?): String = "已删除 ${a0}"
    override val typeText: String = "文本文件 .txt"
    override val typePng: String = "图片 .png"
    override val typeJpg: String = "图片 .jpg"
    override val typeMp4: String = "视频 .mp4"
    override val typeMp3: String = "音频 .mp3"
    override val typeZip: String = "压缩包 .zip"
    override val typeApk: String = "安装包 .apk"
    override val folder: String = "文件夹"
    override val fileType: String = "文件类型"
    override fun nameHintAutoExt(a0: Any?): String = "输入文件名（自动补 ${a0}）"
    override val nameHint: String = "输入文件名（含后缀）"
    override val folderNameHint: String = "输入文件夹名称"
    override fun saved(a0: Any?): String = "已保存 ${a0}"
    override fun saveFailedFor(a0: Any?): String = "保存失败：${a0}"
    override val previewTitle: String = "文件内容预览区"
    override val emptyHint: String = "点右下角文件夹图标打开项目文件树\n文本/代码：高亮 + 等宽 · Markdown：GFM 渲染 · 图片：预览"
    override val fileTree: String = "文件树"
    override val saveChangesTitle: String = "保存更改？"
    override fun savedOnClose(a0: Any?): String = "已保存 ${a0}"
    override fun saveFailedOnClose(a0: Any?): String = "保存失败：${a0}"
    override val dontSave: String = "不保存"
    override fun unsavedMessage(a0: Any?): String = "文件 ${a0} 已被修改，是否保存更改？"
    override val unsaved: String = "未保存"
    override val renderMode: String = "渲染模式"
    override val editMode: String = "编辑模式"
    override fun headingLevel(a0: Any?): String = "${a0} 级标题"
    override val italic: String = "斜体"
    override val bold: String = "粗体"
    override val boldItalic: String = "粗斜体"
    override val strikethrough: String = "删除线"
    override val horizontalRule: String = "分割线"
    override val bulletList: String = "无序列表"
    override val numberedList: String = "有序列表"
    override val taskList: String = "任务列表"
    override val inlineCode: String = "行内代码"
    override val codeBlock: String = "代码块"
    override val quote: String = "引用"
    override val link: String = "链接"
    override val table: String = "表格"
    override val searchHint: String = "搜索…"
    override val matchCase: String = "区分大小写"
    override val wholeWord: String = "全词匹配"
    override val regex: String = "正则表达式"
    override fun matchCount(a0: Any?): String = "${a0} 个匹配"
    override val replaceHint: String = "替换为…"
    override val replace: String = "替换"
    override val noMatches: String = "未找到匹配结果"
    override val checked: String = "已选中"
    override val placeholderText: String = "文本"
    override val placeholderLinkText: String = "链接文本"
    override val placeholderCode: String = "代码"
    override val placeholderImageAlt: String = "图片描述"
    override val placeholderImageUrl: String = "图片路径"
    override val replaceNothingSelected: String = "请先勾选要替换的结果"
    override fun tableColumn(a0: Any?): String = "列 ${a0}"
}

object EnFiles : FilesStrings {
    override fun created(a0: Any?, a1: Any?): String = "Created ${a0} ${a1}"
    override fun deleteConfirm(a0: Any?, a1: Any?): String = "Delete “${a0}”? ${a1}This cannot be undone."
    override val deleteDirWarn: String = "Everything inside will be deleted too."
    override val redo: String = "Redo"
    override fun openFailed(a0: Any?): String = "Cannot open file: ${a0}"
    override val typeUnspecified: String = "Unspecified (type the extension manually)"
    override val symParen: String = "Parentheses"
    override val symBracket: String = "Square brackets"
    override val symBrace: String = "Curly braces"
    override val symAngle: String = "Angle brackets"
    override val symDoubleQuote: String = "Double quote"
    override val symSingleQuote: String = "Single quote"
    override val symBacktick: String = "Backtick"
    override val symPlus: String = "Plus"
    override val symMinus: String = "Minus"
    override val symMultiply: String = "Multiply"
    override val symDivide: String = "Divide"
    override val symPercent: String = "Percent"
    override val symEquals: String = "Equals sign"
    override val symEqualTo: String = "Equal to"
    override val symNotEqualTo: String = "Not equal to"
    override val symLessOrEqual: String = "Less than or equal to"
    override val symGreaterOrEqual: String = "Greater than or equal to"
    override val symLogicalAnd: String = "Logical AND"
    override val symLogicalOr: String = "Logical OR"
    override val symArrow: String = "Arrow"
    override val symFatArrow: String = "Fat arrow"
    override val symLogicalNot: String = "Logical NOT"
    override val symBitwiseAnd: String = "Bitwise AND"
    override val symBitwiseOr: String = "Bitwise OR"
    override val symBitwiseNot: String = "Bitwise NOT"
    override val symComma: String = "Comma"
    override val symSemicolon: String = "Semicolon"
    override val symColon: String = "Colon"
    override val symDot: String = "Dot"
    override val symUnderscore: String = "Underscore"
    override val symHash: String = "Hash"
    override val symAt: String = "At sign"
    override val symDollar: String = "Dollar sign"
    override val symCaret: String = "Caret"
    override val symQuestion: String = "Question mark"
    override val symBackslash: String = "Backslash"
    override val tooLargeHint: String = "Files over 2 MB can't be previewed yet"
    override val binaryHint: String = "Binary files can't be previewed yet"
    override val playUnavailable: String = "Cannot play: file location unavailable"
    override val imageLoadFailed: String = "Image failed to load or the format is unsupported"
    override val imagePlaceholder: String = "Image preview placeholder"
    override val sortByName: String = "By name"
    override val sortBySize: String = "By size"
    override val sortByTime: String = "By modified time"
    override fun importedCount(a0: Any?, a1: Any?): String = "Imported ${a0}/${a1} files"
    override val importFolderDone: String = "Folder imported"
    override val importFailed: String = "Import failed"
    override val exportDone: String = "Exported to the selected folder"
    override val exportFailed: String = "Export failed"
    override val noProject: String = "No project bound"
    override val sort: String = "Sort"
    override val importFile: String = "Import file"
    override val importFolder: String = "Import folder"
    override val exportSelected: String = "Export selected"
    override val exportProject: String = "Export project"
    override val collapseAll: String = "Collapse all"
    override val noSearchResults: String = "No matching files"
    override val treeLoading: String = "Loading file tree…"
    override val treeNoProject: String = "Bind a project to see the file tree"
    override val treeTruncated: String = "Too many files — showing partial content"
    override fun selectedCount(a0: Any?): String = "${a0} selected"
    override val createFailed: String = "Create failed (invalid name or folder not writable)"
    override val mentionInsert: String = "@-mention into the input bar"
    override fun mentionInserted(a0: Any?): String = "@${a0} inserted into the input bar"
    override val renamed: String = "Renamed"
    override fun deleted(a0: Any?): String = "Deleted ${a0}"
    override val typeText: String = "Text file .txt"
    override val typePng: String = "Image .png"
    override val typeJpg: String = "Image .jpg"
    override val typeMp4: String = "Video .mp4"
    override val typeMp3: String = "Audio .mp3"
    override val typeZip: String = "Archive .zip"
    override val typeApk: String = "Installer .apk"
    override val folder: String = "Folder"
    override val fileType: String = "File type"
    override fun nameHintAutoExt(a0: Any?): String = "File name (auto-append ${a0})"
    override val nameHint: String = "File name (with extension)"
    override val folderNameHint: String = "Folder name"
    override fun saved(a0: Any?): String = "Saved ${a0}"
    override fun saveFailedFor(a0: Any?): String = "Save failed: ${a0}"
    override val previewTitle: String = "File preview"
    override val emptyHint: String = "Tap the folder icon at the bottom right to open the project file tree\nText/code: highlighting + monospace · Markdown: GFM rendering · Images: preview"
    override val fileTree: String = "File tree"
    override val saveChangesTitle: String = "Save changes?"
    override fun savedOnClose(a0: Any?): String = "Saved ${a0}"
    override fun saveFailedOnClose(a0: Any?): String = "Save failed: ${a0}"
    override val dontSave: String = "Don't save"
    override fun unsavedMessage(a0: Any?): String = "File ${a0} has been modified. Save changes?"
    override val unsaved: String = "Unsaved"
    override val renderMode: String = "Render mode"
    override val editMode: String = "Edit mode"
    override fun headingLevel(a0: Any?): String = "Level ${a0} heading"
    override val italic: String = "Italic"
    override val bold: String = "Bold"
    override val boldItalic: String = "Bold italic"
    override val strikethrough: String = "Strikethrough"
    override val horizontalRule: String = "Horizontal rule"
    override val bulletList: String = "Bulleted list"
    override val numberedList: String = "Numbered list"
    override val taskList: String = "Task list"
    override val inlineCode: String = "Inline code"
    override val codeBlock: String = "Code block"
    override val quote: String = "Quote"
    override val link: String = "Link"
    override val table: String = "Table"
    override val searchHint: String = "Search…"
    override val matchCase: String = "Match case"
    override val wholeWord: String = "Whole word"
    override val regex: String = "Regex"
    override fun matchCount(a0: Any?): String = "${a0} matches"
    override val replaceHint: String = "Replace with…"
    override val replace: String = "Replace"
    override val noMatches: String = "No matches found"
    override val checked: String = "Selected"
    override val placeholderText: String = "Text"
    override val placeholderLinkText: String = "Link text"
    override val placeholderCode: String = "Code"
    override val placeholderImageAlt: String = "Image description"
    override val placeholderImageUrl: String = "Image path"
    override val replaceNothingSelected: String = "Select the results to replace first"
    override fun tableColumn(a0: Any?): String = "Column ${a0}"
}
