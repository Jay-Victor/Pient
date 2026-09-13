package com.pient.app.data

/**
 * 上下文管理策略（2026-09-13 实现：**参考 Operit 1.12.1 的总结式上下文管理**）。
 *
 * 对齐关系（Pient ↔ Operit 源码位置）：
 * - **触发判定** ← `AIMessageManager.shouldGenerateSummary`：token 占比 ≥ `summaryTokenThreshold`
 *   **或** 自上次总结后的用户消息数 ≥ `summaryMessageCountThreshold` → [shouldSummarize]；
 * - **触发时机** ← `MessageCoordinationDelegate.maybeSummarize*`：**一轮回答结束后**判定
 *   （下一轮请求的上下文才从新摘要起）→ 调用点在 ChatState 的回复落库之后；
 * - **上下文切片** ← `AIMessageManager.getMemoryFromMessages`：请求历史 = 取「最后一条摘要
 *   （**含**它）」之后的全部消息 → [sliceFromLastCompaction]（取代原先的 `takeLast(40)`）；
 * - **历史媒体裁剪** ← `limitImageLinksInChatHistory` / `limitMediaLinksInChatHistory`：
 *   只有最近 N 个用户回合保留图片/音视频，更早的在请求文本里替换为「已省略」占位 → [attachmentWindows]；
 * - **摘要生成** ← `ConversationService.generateSummaryFromPromptTurns`：system =
 *   `FunctionalPrompts.SUMMARY_PROMPT`（+ 自定义总结规则），最后追加一条 user「请按照要求总结对话内容」
 *   → [SUMMARY_PROMPT] / [summaryUserMessage] / [buildSummarySystemPrompt]（Operit 原文照搬）；
 * - **执行通道**：宿主在跑时走 **pi 原生** `compact` 命令（`customInstructions` = Operit 的
 *   `summaryCustomRules`）；宿主不可用时（直连路径）才由 App 用当前模型自己生成摘要 —— 与 Operit 同路径。
 *
 * **摘要文本的格式（2026-09-13 拍板）**：宿主路径用 **pi 自己的结构化 checkpoint 格式**
 * （Goal / Constraints / Progress / Key Decisions / Next Steps / Critical Context），
 * **不**把 Operit 的 `SUMMARY_PROMPT` 强加给它 —— 理由是 Pient 是 Agent 工作台而非对话/角色扮演产品：
 * ① pi 的 checkpoint 格式就是为「另一模型接着干活」写的，续跑语义最贴合（Operit 的
 * 【核心任务状态】/【互动情节与设定】里有一半段落是给剧情型会话用的，Pient 用不上）；
 * ② 宿主路径再叠一层自定义 instructions 属于「协议外改写原生机制」，违反项目红线①；
 * ③ 用户想要别的格式时，`summaryCustomRules`（自定义总结规则）就是出口 —— 直接粘 Operit 的
 * 提示词进去即可。直连路径没有 pi，才用 Operit 的提示词（[SUMMARY_PROMPT]）。
 *
 * 默认值全部逐值对齐 Operit：
 * `ModelConfigDefaults.DEFAULT_SUMMARY_TOKEN_THRESHOLD = 0.70f`、
 * `DEFAULT_ENABLE_SUMMARY = true`、`DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT = true`、
 * `DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD = 16`、
 * `ApiPreferences.DEFAULT_MAX_IMAGE_HISTORY_USER_TURNS = 2`、`DEFAULT_MAX_MEDIA_HISTORY_USER_TURNS = 1`。
 */
object ContextPolicy {

    // ───────────────────────── 默认值（Operit 同值） ─────────────────────────

    const val DEFAULT_SUMMARY_TOKEN_THRESHOLD = 0.70f
    const val DEFAULT_ENABLE_SUMMARY = true
    const val DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT = true
    const val DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD = 16
    const val DEFAULT_MAX_IMAGE_HISTORY_TURNS = 2
    const val DEFAULT_MAX_MEDIA_HISTORY_TURNS = 1

    /** 媒体被裁掉后写进请求文本的占位（Operit 对应 R.string.ai_message_image/media_omitted 文案） */
    const val OMITTED_IMAGE_HINT = "（历史图片已省略）"
    const val OMITTED_MEDIA_HINT = "（历史音视频已省略）"

    /** 触发原因（Operit 的布尔返回值 → 这里带上原因，日志/验证要看得出是哪条阈值命中） */
    enum class SummaryTrigger { BY_TOKENS, BY_MESSAGE_COUNT }

    // ───────────────────────── 触发判定 ─────────────────────────

    /**
     * 是否该生成总结（Operit `shouldGenerateSummary` 逐条移植）：
     * ① token 阈值：`currentTokens / maxTokens ≥ summaryTokenThreshold`（maxTokens > 0 时才算）；
     * ② 消息数阈值：**自上次总结之后**的用户消息数 ≥ `summaryMessageCountThreshold`
     *    （Operit 此处取 `subList(lastSummaryIndex + 1, …)` —— 与请求切片不同，**不含**摘要本身）。
     */
    fun shouldSummarize(
        messages: List<Msg>,
        currentTokens: Int,
        maxTokens: Int,
        cfg: ProviderConfig,
    ): SummaryTrigger? {
        if (!cfg.summaryEnabled) return null
        if (maxTokens > 0) {
            val ratio = currentTokens.toDouble() / maxTokens.toDouble()
            if (ratio >= cfg.summaryTokenThresholdValue.toDouble()) return SummaryTrigger.BY_TOKENS
        }
        if (cfg.summaryByMessageCount) {
            val since = messagesSinceLastCompaction(messages)
            if (since.count { it is Msg.User } >= cfg.summaryMessageCountValue) {
                return SummaryTrigger.BY_MESSAGE_COUNT
            }
        }
        return null
    }

    // ───────────────────────── 上下文切片 ─────────────────────────

    /** 请求上下文切片：最后一条压缩摘要（**含**）之后的全部条目（Operit getMemoryFromMessages 同款） */
    fun sliceFromLastCompaction(messages: List<Msg>): List<Msg> {
        val last = messages.indexOfLast { it is Msg.Compaction }
        return if (last < 0) messages else messages.subList(last, messages.size)
    }

    /** 上次总结**之后**的条目（不含摘要本身；消息数阈值判定用，与 Operit 的 +1 切片一致） */
    fun messagesSinceLastCompaction(messages: List<Msg>): List<Msg> {
        val last = messages.indexOfLast { it is Msg.Compaction }
        return if (last < 0) messages else messages.subList(last + 1, messages.size)
    }

    // ───────────────────────── 历史媒体裁剪 ─────────────────────────

    /**
     * 每个用户回合的附件可见性（Operit 的 `limitImageLinksInChatHistory` 逐条移植）。
     *
     * Operit 的口径：设 limit = 保留的用户回合数，`keepFromTurn = 总回合数 − limit`；
     * 第 i 个用户回合（从 0 起）满足 `limit > 0 && i >= keepFromTurn` 时保留媒体链接，
     * 否则把该条消息里的图片/媒体链接**从内容里剥掉**、替换成「已省略」文案。
     *
     * Pient 的附件不在正文里（是 chip + 路径），因此这里返回「每个下标该不该保留」，
     * 由调用方在拼请求文本时决定「列出附件路径」还是「写占位文案」。
     *
     * @return 与 [messages] 等长的可见性表（非用户消息恒为 (true, true)，不参与裁剪）
     */
    fun attachmentWindows(
        messages: List<Msg>,
        imageTurns: Int,
        mediaTurns: Int,
    ): List<Pair<Boolean, Boolean>> {
        val imgLimit = imageTurns.coerceAtLeast(0)
        val avLimit = mediaTurns.coerceAtLeast(0)
        val totalUserTurns = messages.count { it is Msg.User }
        val imgFrom = (totalUserTurns - imgLimit).coerceAtLeast(0)
        val avFrom = (totalUserTurns - avLimit).coerceAtLeast(0)
        var turn = -1
        return messages.map { m ->
            if (m !is Msg.User) return@map true to true
            turn += 1
            ((imgLimit > 0 && turn >= imgFrom) to (avLimit > 0 && turn >= avFrom))
        }
    }

    /**
     * 用户消息的请求文本：正文 + 附件清单（**Operit 的媒体链接等价物** —— Pient 的附件以路径形式
     * 进请求，pi 的 read 工具能据此打开文件），历史回合的媒体按 [windows] 替换为占位文案。
     */
    fun promptTextFor(msg: Msg.User, window: Pair<Boolean, Boolean>): String {
        if (msg.attachments.isEmpty()) return msg.text
        val (keepImages, keepMedia) = window
        val lines = msg.attachments.map { a ->
            val ext = extOf(a.path ?: a.name)
            when {
                ext in MEDIA_IMAGE_EXTS -> if (keepImages) attachmentLine(a) else OMITTED_IMAGE_HINT
                ext in MEDIA_AV_EXTS -> if (keepMedia) attachmentLine(a) else OMITTED_MEDIA_HINT
                else -> attachmentLine(a)
            }
        }
        return msg.text + "\n\n" + lines.joinToString("\n")
    }

    private fun attachmentLine(a: Attachment): String =
        "[附件] ${a.name}" + (a.path?.let { " · $it" } ?: "")

    // ───────────────────────── 估算（Operit 的 window 估算等价物） ─────────────────────────

    /**
     * 粗略 token 估算：1 token ≈ 2 字符（中英混排折中）——沿用 Pient 既有口径
     * （ChatState.trimToContextBudget；Operit 侧由 `estimateRequestWindowFromMemory` 走服务商侧估算）。
     */
    fun estimateTokens(text: String): Int = (text.length + 1) / 2

    /** 一段历史的估算 token 总量 */
    fun estimateTokens(history: List<Pair<String, String>>): Int =
        history.sumOf { estimateTokens(it.second) }

    // ───────────────────────── 摘要提示词（Operit FunctionalPrompts 原文） ─────────────────────────

    /** Operit `FunctionalPrompts.SUMMARY_PROMPT`（简体中文原文照搬，未做任何改写） */
    const val SUMMARY_PROMPT = """
你是负责生成对话摘要的AI助手。你的任务是根据"上一次的摘要"（如果提供）和"最近的对话内容"，生成一份全新的、独立的、全面的摘要。这份新摘要将完全取代之前的摘要，成为后续对话的唯一历史参考。

**必须严格遵循以下固定格式输出，不得更改格式结构：**

==========对话摘要==========

【核心任务状态】
[先交代用户最新需求的内容与情境类型（真实执行/角色扮演/故事/假设等），再说明当前所处步骤、已完成的动作、正在处理的事项以及下一步。]
[明确任务状态（已完成/进行中/等待中），列出未完成的依赖或所需信息；如在等待用户输入，说明原因与所需材料。]
[显式覆盖信息搜集、任务执行、代码编写或其他关键环节的状态，哪怕某环节尚未启动也要说明原因。]
[最后补充最近一次任务的进度拆解：哪些已完成、哪些进行中、哪些待处理。]

【互动情节与设定】
[如存在虚构或场景设定，概述名称、角色身份、背景约束及其来源，避免把剧情当成现实。]
[用1-2段概括近期关键互动：谁提出了什么、目的为何、采用何种表达方式、对任务或剧情的影响，以及仍需确认的事项。]
[若用户给出剧本/业务/策略等非技术内容，提炼要点并说明它们如何指导后续输出。]

【对话历程与概要】
[用不少于3段描述整体演进，每段包含“行动+目的+结果”，可涵盖技术、业务、剧情或策略等不同主题，需特别点名信息搜集、任务执行、代码编写等阶段的衔接；如涉及具体代码，可引用关键片段以辅助说明。]
[突出转折、已解决的问题和形成的共识，引用必要的路径、命令、场景节点或原话，确保读者能看懂上下文和因果关系。]

【关键信息与上下文】
- [信息点1：用户需求、限制、背景或引用的文件/接口/角色等，说明其具体内容及作用。]
- [信息点2：技术或剧本结构中的关键元素（函数、配置、日志、人物动机等）及其意义。]
- [信息点3：问题或创意的探索路径、验证结果与当前状态。]
- [信息点4：影响后续决策的因素，如优先级、情绪基调、角色约束、外部依赖、时间节点。]
- [信息点5+：补充其他必要细节，覆盖现实与虚构信息。每条至少两句：先述事实，再讲影响或后续计划。]

============================

**格式要求：**
1. 必须使用上述固定格式，包括分隔线、标题标识符【】、列表符号等，不得更改。
2. 标题"对话摘要"必须放在第一行，前后用等号分隔。
3. 每个部分必须使用【】标识符作为标题，标题后换行。
4. "核心任务状态"、"互动情节与设定"、"对话历程与概要"使用段落形式；方括号只为示例，实际输出不需保留.
5. "关键信息与上下文"使用列表格式，每个信息点以"- "开头.
6. 结尾使用等号分隔线.

**内容要求：**
1. 语言风格：专业、清晰、客观.
2. 内容长度：不要限制字数，根据对话内容的复杂程度和重要性，自行决定合适的长度。可以写得详细一些，确保重要信息不丢失。宁可内容多一点，也不要因为过度精简导致关键信息丢失或失真。每个部分都要具备充分篇幅，绝不能以一句话敷衍.
3. 信息完整性：优先保证信息的完整性和准确性，技术与非技术内容都需提供必要证据或引用.
4. 内容还原：摘要既要说明“过程如何推进”，也要写清“实际产出/讨论内容是什么”，必要时引用结果文本、结论、代码片段或参数，确保在没有原始对话的情况下依然能完全还原信息本身.
5. 目标：生成的摘要必须是自包含的。即使AI完全忘记了之前的对话，仅凭这份摘要也能够准确理解历史背景、当前状态、具体进度和下一步行动.
6. 时序重点：请先聚焦于最新一段对话（约占输入的最后30%），明确最新指令、问题和进展，再回顾更早的内容。若新消息与旧内容冲突或更新，应以最新对话为准，并解释差异.
"""

    /** Operit `FunctionalPrompts.summaryUserMessage`（摘要请求的最后一条用户消息） */
    const val SUMMARY_USER_MESSAGE = "请按照要求总结对话内容"

    /**
     * 摘要 system prompt（Operit `buildSummarySystemPrompt` + 自定义总结规则的拼接口径）：
     * 有上一次摘要时把旧摘要并入，要求「融合生成全新的、更完整的摘要」；自定义规则追加在末尾。
     */
    fun buildSummarySystemPrompt(previousSummary: String?, customRules: String?): String {
        var prompt = SUMMARY_PROMPT.trimIndent()
        if (!previousSummary.isNullOrBlank()) {
            prompt += """

                上一次的摘要（用于继承上下文）：
                ${previousSummary.trim()}
                请将以上摘要中的关键信息，与本次新的对话内容相融合，生成一份全新的、更完整的摘要。
            """.trimIndent()
        }
        if (!customRules.isNullOrBlank()) prompt += "\n\n${customRules.trim()}"
        return prompt
    }

    /**
     * 摘要结果的后处理（Operit `summarizeMemory` 口径）：去空白，再包一层「」引用；
     * `autoContinue` 时在尾部追加续写提示（Operit R.string.ai_message_continue_task_if_complete）。
     * Pient 侧 autoContinue 恒为 false（发送路径由用户触发），保留参数以便后续对齐。
     */
    fun formatSummary(raw: String, autoContinue: Boolean = false): String {
        val trimmed = raw.trim()
        val quoted = if (trimmed.startsWith("「") && trimmed.endsWith("」")) trimmed else "「$trimmed」"
        return if (!autoContinue) quoted else "$quoted\n\n如果任务已完成，请继续下一步；否则继续当前任务。"
    }
}
