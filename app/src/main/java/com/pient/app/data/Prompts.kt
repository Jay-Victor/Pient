package com.pient.app.data

/**
 * **系统提示词（提示层）** —— 对话请求里下发的 system prompt。
 *
 * 2026-09-14 用户拍板：工具能力整体移除（不再有 read / write / bash 等工具，技能装配也一并移除），
 * 提示词随之收敛为一段简短的助手身份说明。「系统提示词」面板显示的仍是这里构造的真实内容
 * （由发送路径写入 `ChatState.systemPrompt`，见 ChatState.runChat）。
 */
object Prompts {

    fun systemPrompt(): String = buildString {
        append("You are Pient, an AI assistant running inside the Pient Android app. ")
        append("The app calls the model API directly on the device and shows your replies in a chat UI.\n\n")
        append("Guidelines:\n")
        append("- Reply directly and helpfully; answers are rendered as Markdown, so structure longer replies with it.\n")
        append("- Never claim to have executed commands, accessed files, or produced results you did not actually produce.\n")
        append("- Match the user's language in your replies.")
    }
}
