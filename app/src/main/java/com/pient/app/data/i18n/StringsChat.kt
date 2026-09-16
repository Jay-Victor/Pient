package com.pient.app.data.i18n

/** chat 组文案（由 i18n 流水线生成；zh = 源码原文字面量） */
interface ChatStrings {
    val attach: String
    val inputPlaceholder: String
    val fullscreenInput: String
    val noModelSelected: String
    val systemPrompt: String
    val send: String
    val copyMessage: String
    val sessionDrawer: String
    val createProject: String
    val envInstallNode: String
    val toolRunning: String
    val toolReading: String
    val toolEditing: String
    val imageReadFailed: String
    val cameraSaveFailed: String
    val fileReadFailed: String
    val photos: String
    val takePhoto: String
    val cameraUnavailable: String
    val attachTip: String
    val attachUrlTitle: String
    val attachUrlDesc: String
    val urlIncomplete: String
    val addAttachment: String
    val removeAttachment: String
    val showEarlier: String
    val backToBottom: String
    val messageLocator: String
    fun locatorPosition(a0: Any?, a1: Any?): String
    val searchMessages: String
    val filterMessages: String
    val filterAll: String
    val filterUser: String
    val filterAi: String
    val locatorHint: String
    val noMatchingMessages: String
    val roleUser: String
    val forkFromHere: String
    val quote: String
    val regenerate: String
    val quoteAssistant: String
    val quoteUser: String
    val removeQuote: String
    val plainText: String
    val markdownSource: String
    val messageCopied: String
    val copyPlainText: String
    val copyMarkdownSource: String
    val thinkingLive: String
    val thought: String
    val thoughtBriefly: String
    fun thoughtFor(a0: Any?): String
    val contextCompacted: String
    fun compactTokens(a0: Any?, a1: Any?): String
    val viewSummary: String
    fun previewThinking(a0: Any?): String
    fun previewTool(a0: Any?): String
    fun previewResult(a0: Any?): String
    fun previewCompacted(a0: Any?): String
    val pressAgainToExit: String
    val piUnreadyToast: String
    val compactDone: String
    val sessionCreated: String
    val newSession: String
    val idle: String
    val sessionBranches: String
    val terminal: String
    val piUnready: String
    val piUnreadyHint: String
    val retry: String
    val contextUsage: String
    fun contextUsedPercent(a0: Any?): String
    val contextUsedUnknown: String
    val compactionNoProvider: String
    val autoCompactOff: String
    fun autoCompactAt(a0: Any?): String
    val autoCompactNearLimit: String
    val compacting: String
    val compactContext: String
    val getStarted: String
    val firstRunSubtitle: String
    fun projectBound(a0: Any?): String
    val createProjectDesc: String
    val configureAi: String
    val aiReady: String
    val configureAiDesc: String
    val configureEnv: String
    val envReady: String
    val folderCreateFailed: String
    val createProjectFolderHint: String
    val referenceFile: String
    val noFilesInProject: String
    val noMatchingFiles: String
    val thinkingMode: String
    fun providerReceives(a0: Any?): String
    fun thinkingBudget(a0: Any?): String
    val levelUnsupported: String
    val noModelsAvailable: String
    val manageModels: String
    val thinking: String
    fun modelCount(a0: Any?): String
    val notSelected: String
    val readOnly: String
    val noSystemPrompt: String
    val toolReadingFile: String
    val toolReadFile: String
    val toolEditingFile: String
    val toolEditedFile: String
    val toolPatchingFile: String
    val toolPatchedFile: String
    val toolSearchingFiles: String
    val toolSearchedFiles: String
    val toolFindingFiles: String
    val toolFoundFiles: String
    val toolListingFiles: String
    val toolListedFiles: String
    val toolRunningCommand: String
    val toolRanCommand: String
    fun toolRunningName(a0: Any?): String
    fun toolRanName(a0: Any?): String
    fun lineCountLabel(a0: Any?): String
    val toolPatching: String
    val toolWriting: String
    val toolSearching: String
    val toolFinding: String
    val toolListing: String
    /** 逐工具行标题的过去式动词（grep / find / ls；此前硬编码在 UI 层） */
    val toolSearched: String
    val toolFound: String
    val toolListed: String
    /** 带查询词的标题分句（引号属语言标点）：{0} = 查询词 */
    fun toolSearchingQuery(a0: Any?): String
    fun toolSearchedQuery(a0: Any?): String
    fun matchCount(a0: Any?): String
    fun fileCountLabel(a0: Any?): String
    fun entryCountLabel(a0: Any?): String
    fun rowCountLabel(a0: Any?): String
    fun replacedCount(a0: Any?): String
    val error: String
    val writtenContent: String
    fun truncatedContent(a0: Any?): String
    fun moreItems(a0: Any?): String
    fun moreMatches(a0: Any?): String
    val results: String
    val copyCommand: String
    val copyOutput: String
    val toolPayload: String
    val toolEdited: String
    val toolRead: String
    val nounCommand: String
    val toolRan: String
    val nounTool: String
    val toolUsed: String
    val toolUsing: String
    /** 复数形（计数分句用；无复数概念的语言与单数同值）：已编辑 */
    val toolEditedPlural: String
    /** 复数形（计数分句用；无复数概念的语言与单数同值）：已读取 */
    val toolReadPlural: String
    /** 复数形（计数分句用；无复数概念的语言与单数同值）：已运行 */
    val toolRanPlural: String
    /** 复数形（计数分句用；无复数概念的语言与单数同值）：已使用 */
    val toolUsedPlural: String
    /** 复数形（计数分句用；无复数概念的语言与单数同值）：正在编辑 */
    val toolEditingPlural: String
    /** 复数形（计数分句用；无复数概念的语言与单数同值）：正在读取 */
    val toolReadingPlural: String
    /** 复数形（计数分句用；无复数概念的语言与单数同值）：正在运行 */
    val toolRunningPlural: String
    /** 复数形（计数分句用；无复数概念的语言与单数同值）：正在使用 */
    val toolUsingPlural: String
    /** 计数分句的文件名词（EDIT/EXPLORE 类；需要复数形式的语言写复数） */
    val nounFile: String
    /** 分句连接符（中文「、」不能硬编码进 UI 层：英/西 ', ' 或 ' · '） */
    val runSummaryJoin: String
    /** 单条带目标的分句：{0} = 动词，{1} = 目标（语序归语言） */
    fun runSummaryTarget(a0: Any?, a1: Any?): String
    /** 计数分句：{0} = 动词（单数形），{1} = 条数，{2} = 名词，{3} = 动词（复数形） */
    fun runSummaryClause(a0: Any?, a1: Any?, a2: Any?, a3: Any?): String
    val polish: String
    val polishUndo: String
    val polishing: String
    fun polishFailed(a0: Any?): String
}

object ZhChat : ChatStrings {
    override val attach: String = "附加"
    override val inputPlaceholder: String = "给 Agent 派个任务…"
    override val fullscreenInput: String = "全屏输入"
    override val noModelSelected: String = "未选择模型"
    override val systemPrompt: String = "系统提示词"
    override val send: String = "发送"
    override val copyMessage: String = "复制消息"
    override val sessionDrawer: String = "会话侧栏"
    override val createProject: String = "创建项目"
    override val envInstallNode: String = "到「环境配置」装 Node.js（pi 是 Node 程序；rg/fd 是它的搜索工具）"
    override val toolRunning: String = "正在运行"
    override val toolReading: String = "正在读取"
    override val toolEditing: String = "正在编辑"
    override val imageReadFailed: String = "图片读取失败"
    override val cameraSaveFailed: String = "拍照保存失败"
    override val fileReadFailed: String = "文件读取失败"
    override val photos: String = "照片"
    override val takePhoto: String = "拍照"
    override val cameraUnavailable: String = "无法调起相机"
    override val attachTip: String = "提示：输入 @ 以内联引用文件。"
    override val attachUrlTitle: String = "附加 URL"
    override val attachUrlDesc: String = "Pient 将抓取该页面并作为本回合的上下文。"
    override val urlIncomplete: String = "请包含完整 URL，例如 https://…"
    override val addAttachment: String = "添加附件"
    override val removeAttachment: String = "移除附件"
    override val showEarlier: String = "显示更早的消息"
    override val backToBottom: String = "回到底部"
    override val messageLocator: String = "消息定位"
    override fun locatorPosition(a0: Any?, a1: Any?): String = "当前定位：第${a0}/${a1}条"
    override val searchMessages: String = "搜索消息"
    override val filterMessages: String = "筛选消息"
    override val filterAll: String = "全部消息"
    override val filterUser: String = "用户消息"
    override val filterAi: String = "AI消息"
    override val locatorHint: String = "点击任意一条消息即可快速跳转"
    override val noMatchingMessages: String = "无匹配消息"
    override val roleUser: String = "用户"
    override val forkFromHere: String = "从此处创建新会话"
    override val quote: String = "引用"
    override val regenerate: String = "重新生成"
    override val quoteAssistant: String = "引用 AI 回答"
    override val quoteUser: String = "引用用户消息"
    override val removeQuote: String = "取消引用"
    override val plainText: String = "纯文本"
    override val markdownSource: String = "Markdown 源码"
    override val messageCopied: String = "消息已复制到剪贴板"
    override val copyPlainText: String = "复制纯文本"
    override val copyMarkdownSource: String = "复制 Markdown 源码"
    override val thinkingLive: String = "思考中"
    override val thought: String = "已思考"
    override val thoughtBriefly: String = "思考了片刻"
    override fun thoughtFor(a0: Any?): String = "思考了 ${a0}"
    override val contextCompacted: String = "上下文已压缩"
    override fun compactTokens(a0: Any?, a1: Any?): String = "前 ${a0} · 节省 ${a1}"
    override val viewSummary: String = "查看摘要"
    override fun previewThinking(a0: Any?): String = "思考 · ${a0}"
    override fun previewTool(a0: Any?): String = "工具 · ${a0}"
    override fun previewResult(a0: Any?): String = "结果 · ${a0}"
    override fun previewCompacted(a0: Any?): String = "上下文已压缩 · 节省 ${a0} tokens"
    override val pressAgainToExit: String = "再按一次退出应用"
    override val piUnreadyToast: String = "pi 运行时未就绪：消息未发送（见上方提示条）"
    override val compactDone: String = "已压缩上下文（pi 原生）：聊天页已按压缩后的上下文重建"
    override val sessionCreated: String = "已创建新会话"
    override val newSession: String = "新会话"
    override val idle: String = "空闲"
    override val sessionBranches: String = "会话分支"
    override val terminal: String = "终端"
    override val piUnready: String = "pi 运行时未就绪"
    override val piUnreadyHint: String = "Ubuntu / pi 还没准备好（可在「环境配置」里检测）"
    override val retry: String = "重试"
    override val contextUsage: String = "上下文用量"
    override fun contextUsedPercent(a0: Any?): String = "已用 ${a0}%"
    override val contextUsedUnknown: String = "已用 ?（压缩后还没有新回复）"
    override val compactionNoProvider: String = "上下文压缩：未配置服务商"
    override val autoCompactOff: String = "自动压缩已关闭"
    override fun autoCompactAt(a0: Any?): String = "自动压缩：已用 ≥ ${a0}% 时"
    override val autoCompactNearLimit: String = "自动压缩：接近上下文上限时"
    override val compacting: String = "压缩中…"
    override val compactContext: String = "压缩上下文"
    override val getStarted: String = "开始使用 Pient"
    override val firstRunSubtitle: String = "完成以下步骤后即可开始对话"
    override fun projectBound(a0: Any?): String = "已绑定项目：${a0}"
    override val createProjectDesc: String = "新建项目文件夹（应用私有目录 Projects/ 下）"
    override val configureAi: String = "配置 AI 模型"
    override val aiReady: String = "已通过连接测试"
    override val configureAiDesc: String = "接入服务商与模型，测试连接成功后即可对话"
    override val configureEnv: String = "配置 Ubuntu 环境"
    override val envReady: String = "环境已就绪：Node 与 pi 可用"
    override val folderCreateFailed: String = "文件夹创建失败"
    override val createProjectFolderHint: String = "将在应用私有目录 Projects/ 下创建该文件夹"
    override val referenceFile: String = "引用文件"
    override val noFilesInProject: String = "项目文件夹内暂无文件"
    override val noMatchingFiles: String = "无匹配文件"
    override val thinkingMode: String = "思考模式"
    override fun providerReceives(a0: Any?): String = "服务商实际收到：${a0}"
    override fun thinkingBudget(a0: Any?): String = "思考预算：${a0} tokens"
    override val levelUnsupported: String = "当前服务商不支持档位调节（只支持开 / 关）"
    override val noModelsAvailable: String = "暂无可用模型 · 请在「服务商与模型配置」中添加服务商并填写模型列表"
    override val manageModels: String = "管理模型配置"
    override val thinking: String = "思考"
    override fun modelCount(a0: Any?): String = "${a0}个模型"
    override val notSelected: String = "未选择"
    override val readOnly: String = "只读"
    override val noSystemPrompt: String = "暂无系统提示词"
    override val toolReadingFile: String = "正在读取文件"
    override val toolReadFile: String = "已读取文件"
    override val toolEditingFile: String = "正在编辑文件"
    override val toolEditedFile: String = "已编辑文件"
    override val toolPatchingFile: String = "正在修补文件"
    override val toolPatchedFile: String = "已修补文件"
    override val toolSearchingFiles: String = "正在搜索文件"
    override val toolSearchedFiles: String = "已搜索文件"
    override val toolFindingFiles: String = "正在查找文件"
    override val toolFoundFiles: String = "已查找文件"
    override val toolListingFiles: String = "正在列出文件"
    override val toolListedFiles: String = "已列出文件"
    override val toolRunningCommand: String = "正在运行命令"
    override val toolRanCommand: String = "已运行命令"
    override fun toolRunningName(a0: Any?): String = "正在运行 ${a0}"
    override fun toolRanName(a0: Any?): String = "已运行 ${a0}"
    override fun lineCountLabel(a0: Any?): String = "共 ${a0} 行"
    override val toolPatching: String = "正在修补"
    override val toolWriting: String = "正在写入"
    override val toolSearching: String = "正在搜索"
    override val toolFinding: String = "正在查找"
    override val toolListing: String = "正在列出"
    override val toolSearched: String = "已搜索"
    override val toolFound: String = "已查找"
    override val toolListed: String = "已列出"
    override fun toolSearchingQuery(a0: Any?): String = "正在搜索“${a0}”"
    override fun toolSearchedQuery(a0: Any?): String = "已搜索“${a0}”"
    override fun matchCount(a0: Any?): String = "${a0} 处匹配"
    override fun fileCountLabel(a0: Any?): String = "${a0} 个文件"
    override fun entryCountLabel(a0: Any?): String = "${a0} 项"
    override fun rowCountLabel(a0: Any?): String = "${a0} 行"
    override fun replacedCount(a0: Any?): String = "${a0} 处替换"
    override val error: String = "错误"
    override val writtenContent: String = "写入内容"
    override fun truncatedContent(a0: Any?): String = "${a0}\n…（内容过长，已截断）"
    override fun moreItems(a0: Any?): String = "另有 ${a0} 项…"
    override fun moreMatches(a0: Any?): String = "另有 ${a0} 处…"
    override val results: String = "结果"
    override val copyCommand: String = "复制命令"
    override val copyOutput: String = "复制输出"
    override val toolPayload: String = "工具负载"
    override val toolEdited: String = "已编辑"
    override val toolRead: String = "已读取"
    override val nounCommand: String = "命令"
    override val toolRan: String = "已运行"
    override val nounTool: String = "工具"
    override val toolUsed: String = "已使用"
    override val toolUsing: String = "正在使用"
    override val toolEditedPlural: String = "已编辑"
    override val toolReadPlural: String = "已读取"
    override val toolRanPlural: String = "已运行"
    override val toolUsedPlural: String = "已使用"
    override val toolEditingPlural: String = "正在编辑"
    override val toolReadingPlural: String = "正在读取"
    override val toolRunningPlural: String = "正在运行"
    override val toolUsingPlural: String = "正在使用"
    override val nounFile: String = "文件"
    override val runSummaryJoin: String = "、"
    override fun runSummaryTarget(a0: Any?, a1: Any?): String = "${a0} ${a1}"
    override fun runSummaryClause(a0: Any?, a1: Any?, a2: Any?, a3: Any?): String = "${a0} ${a1} 个${a2}"
    override val polish: String = "润色提示词"
    override val polishUndo: String = "撤销润色"
    override val polishing: String = "正在润色提示词"
    override fun polishFailed(a0: Any?): String = "润色失败：${a0}"
}

object EnChat : ChatStrings {
    override val attach: String = "Attach"
    override val inputPlaceholder: String = "Give the Agent a task…"
    override val fullscreenInput: String = "Fullscreen input"
    override val noModelSelected: String = "No model selected"
    override val systemPrompt: String = "System prompt"
    override val send: String = "Send"
    override val copyMessage: String = "Copy message"
    override val sessionDrawer: String = "Session drawer"
    override val createProject: String = "Create project"
    override val envInstallNode: String = "Install Node.js in \"Environment\" (pi is a Node program; rg/fd are its search tools)"
    override val toolRunning: String = "Running"
    override val toolReading: String = "Reading"
    override val toolEditing: String = "Editing"
    override val imageReadFailed: String = "Failed to read image"
    override val cameraSaveFailed: String = "Failed to save photo"
    override val fileReadFailed: String = "Failed to read file"
    override val photos: String = "Photos"
    override val takePhoto: String = "Take photo"
    override val cameraUnavailable: String = "Can't open camera"
    override val attachTip: String = "Tip: type @ to reference a file inline."
    override val attachUrlTitle: String = "Attach URL"
    override val attachUrlDesc: String = "Pient will fetch the page and use it as context for this turn."
    override val urlIncomplete: String = "Include a full URL, e.g. https://…"
    override val addAttachment: String = "Add attachment"
    override val removeAttachment: String = "Remove attachment"
    override val showEarlier: String = "Show earlier messages"
    override val backToBottom: String = "Back to bottom"
    override val messageLocator: String = "Message locator"
    override fun locatorPosition(a0: Any?, a1: Any?): String = "Current: ${a0}/${a1}"
    override val searchMessages: String = "Search messages"
    override val filterMessages: String = "Filter messages"
    override val filterAll: String = "All messages"
    override val filterUser: String = "User messages"
    override val filterAi: String = "AI messages"
    override val locatorHint: String = "Tap any message to jump to it"
    override val noMatchingMessages: String = "No matching messages"
    override val roleUser: String = "User"
    override val forkFromHere: String = "New session from here"
    override val quote: String = "Quote"
    override val regenerate: String = "Regenerate"
    override val quoteAssistant: String = "Quote AI answer"
    override val quoteUser: String = "Quote user message"
    override val removeQuote: String = "Remove quote"
    override val plainText: String = "Plain text"
    override val markdownSource: String = "Markdown source"
    override val messageCopied: String = "Message copied to clipboard"
    override val copyPlainText: String = "Copy plain text"
    override val copyMarkdownSource: String = "Copy Markdown source"
    override val thinkingLive: String = "Thinking"
    override val thought: String = "Thought"
    override val thoughtBriefly: String = "Thought briefly"
    override fun thoughtFor(a0: Any?): String = "Thought for ${a0}"
    override val contextCompacted: String = "Context compacted"
    override fun compactTokens(a0: Any?, a1: Any?): String = "Before ${a0} · Saved ${a1}"
    override val viewSummary: String = "View summary"
    override fun previewThinking(a0: Any?): String = "Thinking · ${a0}"
    override fun previewTool(a0: Any?): String = "Tool · ${a0}"
    override fun previewResult(a0: Any?): String = "Result · ${a0}"
    override fun previewCompacted(a0: Any?): String = "Context compacted · saved ${a0} tokens"
    override val pressAgainToExit: String = "Press again to exit"
    override val piUnreadyToast: String = "pi runtime not ready: message not sent (see the banner above)"
    override val compactDone: String = "Context compacted (pi-native): the chat page was rebuilt from the compacted context"
    override val sessionCreated: String = "New session created"
    override val newSession: String = "New session"
    override val idle: String = "Idle"
    override val sessionBranches: String = "Session branches"
    override val terminal: String = "Terminal"
    override val piUnready: String = "pi runtime not ready"
    override val piUnreadyHint: String = "Ubuntu / pi isn't ready (you can check it in \"Environment\")"
    override val retry: String = "Retry"
    override val contextUsage: String = "Context usage"
    override fun contextUsedPercent(a0: Any?): String = "Used ${a0}%"
    override val contextUsedUnknown: String = "Used ? (no new reply after compaction)"
    override val compactionNoProvider: String = "Context compaction: no provider configured"
    override val autoCompactOff: String = "Auto-compaction off"
    override fun autoCompactAt(a0: Any?): String = "Auto-compaction when used ≥ ${a0}%"
    override val autoCompactNearLimit: String = "Auto-compaction near the context limit"
    override val compacting: String = "Compacting…"
    override val compactContext: String = "Compact context"
    override val getStarted: String = "Get started with Pient"
    override val firstRunSubtitle: String = "Complete the steps below to start chatting"
    override fun projectBound(a0: Any?): String = "Project bound: ${a0}"
    override val createProjectDesc: String = "Create a project folder (under the app-private Projects/ directory)"
    override val configureAi: String = "Configure AI model"
    override val aiReady: String = "Connection test passed"
    override val configureAiDesc: String = "Add a provider and model; chat once the connection test passes"
    override val configureEnv: String = "Configure Ubuntu environment"
    override val envReady: String = "Environment ready: Node and pi available"
    override val folderCreateFailed: String = "Failed to create folder"
    override val createProjectFolderHint: String = "The folder will be created under the app-private Projects/ directory"
    override val referenceFile: String = "Reference file"
    override val noFilesInProject: String = "No files in the project folder"
    override val noMatchingFiles: String = "No matching files"
    override val thinkingMode: String = "Thinking mode"
    override fun providerReceives(a0: Any?): String = "Provider receives: ${a0}"
    override fun thinkingBudget(a0: Any?): String = "Thinking budget: ${a0} tokens"
    override val levelUnsupported: String = "This provider doesn't support level tuning (on / off only)"
    override val noModelsAvailable: String = "No models available · Add a provider and fill in its model list in \"Providers & models\""
    override val manageModels: String = "Manage models"
    override val thinking: String = "Thinking"
    override fun modelCount(a0: Any?): String = "${a0} models"
    override val notSelected: String = "Not selected"
    override val readOnly: String = "Read-only"
    override val noSystemPrompt: String = "No system prompt"
    override val toolReadingFile: String = "Reading file"
    override val toolReadFile: String = "Read file"
    override val toolEditingFile: String = "Editing file"
    override val toolEditedFile: String = "Edited file"
    override val toolPatchingFile: String = "Patching file"
    override val toolPatchedFile: String = "Patched file"
    override val toolSearchingFiles: String = "Searching files"
    override val toolSearchedFiles: String = "Searched files"
    override val toolFindingFiles: String = "Finding files"
    override val toolFoundFiles: String = "Found files"
    override val toolListingFiles: String = "Listing files"
    override val toolListedFiles: String = "Listed files"
    override val toolRunningCommand: String = "Running command"
    override val toolRanCommand: String = "Ran command"
    override fun toolRunningName(a0: Any?): String = "Running ${a0}"
    override fun toolRanName(a0: Any?): String = "Ran ${a0}"
    override fun lineCountLabel(a0: Any?): String = "${a0} lines"
    override val toolPatching: String = "Patching"
    override val toolWriting: String = "Writing"
    override val toolSearching: String = "Searching"
    override val toolFinding: String = "Finding"
    override val toolListing: String = "Listing"
    override val toolSearched: String = "Searched"
    override val toolFound: String = "Found"
    override val toolListed: String = "Listed"
    override fun toolSearchingQuery(a0: Any?): String = "Searching \"${a0}\""
    override fun toolSearchedQuery(a0: Any?): String = "Searched \"${a0}\""
    override fun matchCount(a0: Any?): String = "${a0} matches"
    override fun fileCountLabel(a0: Any?): String = "${a0} files"
    override fun entryCountLabel(a0: Any?): String = "${a0} items"
    override fun rowCountLabel(a0: Any?): String = "${a0} lines"
    override fun replacedCount(a0: Any?): String = "${a0} replacements"
    override val error: String = "Error"
    override val writtenContent: String = "Written content"
    override fun truncatedContent(a0: Any?): String = "${a0}\n… (content too long, truncated)"
    override fun moreItems(a0: Any?): String = "${a0} more items…"
    override fun moreMatches(a0: Any?): String = "${a0} more matches…"
    override val results: String = "Results"
    override val copyCommand: String = "Copy command"
    override val copyOutput: String = "Copy output"
    override val toolPayload: String = "Tool payload"
    override val toolEdited: String = "Edited"
    override val toolRead: String = "Read"
    override val nounCommand: String = "commands"
    override val toolRan: String = "Ran"
    override val nounTool: String = "tools"
    override val toolUsed: String = "Used"
    override val toolUsing: String = "Using"
    override val toolEditedPlural: String = "Edited"
    override val toolReadPlural: String = "Read"
    override val toolRanPlural: String = "Ran"
    override val toolUsedPlural: String = "Used"
    override val toolEditingPlural: String = "Editing"
    override val toolReadingPlural: String = "Reading"
    override val toolRunningPlural: String = "Running"
    override val toolUsingPlural: String = "Using"
    override val nounFile: String = "files"
    override val runSummaryJoin: String = ", "
    override fun runSummaryTarget(a0: Any?, a1: Any?): String = "${a0} ${a1}"
    override fun runSummaryClause(a0: Any?, a1: Any?, a2: Any?, a3: Any?): String = "${a0} ${a1} ${a2}"
    override val polish: String = "Polish prompt"
    override val polishUndo: String = "Undo polish"
    override val polishing: String = "Polishing prompt"
    override fun polishFailed(a0: Any?): String = "Polish failed: ${a0}"
}
