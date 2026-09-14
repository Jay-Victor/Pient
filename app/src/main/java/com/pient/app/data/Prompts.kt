package com.pient.app.data

import com.pient.app.tools.ToolRegistry
import com.pient.app.tools.ToolSpec
import org.json.JSONObject

/**
 * **系统提示词（提示层）** —— 直连路径的提示词（宿主路径的提示词由 pi 自己构建，与此无关）。
 *
 * 与工具层的关系（2026-09-14 拆开）：提示词里列出的工具**必须来自 [ToolRegistry]**，
 * 不再手写一份清单 —— 旧实现把工具说明抄在 `AppTools.systemPrompt` 里，与下发/执行处的
 * 清单各写一遍，一旦加工具就漏（用户报过的「回答里直接输出命令」正是「提示词说有的工具、
 * 请求里没带」这类不一致的下游后果之一）。
 */
object Prompts {

    /**
     * @param workspace 工作区绝对路径（相对路径的解析基准、bash 的 cwd）
     * @param nativeTools true = 工具经服务商 API 原生下发（开关开）；false = 用下方标记契约调用（开关关）
     */
    fun systemPrompt(workspace: String, nativeTools: Boolean): String {
        val sb = StringBuilder()
        sb.append("You are Pient's on-device agent, running inside the Pient Android app. ")
        sb.append("The app itself calls the model API (direct-connection mode), and the app executes your tool calls.\n\n")
        sb.append("Environment:\n")
        sb.append("- OS: Android (app sandbox). Shell commands run inside the bundled Ubuntu workspace.\n")
        sb.append("- Working directory (workspace): ").append(workspace).append('\n')
        sb.append("- Relative paths are resolved against the workspace; bash starts there.\n")
        sb.append("- Files outside the workspace and the app's own directory are not reachable in this mode.\n\n")
        if (nativeTools) {
            sb.append("Tools are declared through the provider's native tool-calling API — call them the normal way. ")
            sb.append("read/write/edit/ls/find/grep run in the app; bash runs in the Ubuntu workspace.\n\n")
        } else {
            sb.append("Tools are NOT declared through the API. To use a tool, output one or more blocks in exactly ")
            sb.append("this form (the app runs them and sends the results back as a user message):\n\n")
            sb.append("<tool_call>\n")
            sb.append("<invoke name=\"read\"><parameter name=\"path\">notes.md</parameter></invoke>\n")
            sb.append("</tool_call>\n\n")
            sb.append("Available tools:\n")
            for (t in ToolRegistry.specs()) {
                sb.append("- ").append(t.name)
                val params = paramList(t)
                if (params.isNotEmpty()) sb.append(": ").append(params)
                sb.append(" — ").append(oneLine(t))
                sb.append('\n')
            }
            sb.append('\n')
            sb.append("After the tool results come back, continue the task; when you are done, answer normally ")
            sb.append("without any tool markup.\n\n")
        }
        sb.append("Guidelines:\n")
        sb.append("- Work autonomously: break the task into steps, use the tools, verify the result before reporting.\n")
        sb.append("- Never fabricate file contents, command output, or results you did not actually produce.\n")
        sb.append("- Match the user's language in your replies.")
        return sb.toString()
    }

    /** 参数清单（`path, offset?, limit?`）——必填不加后缀、可选加 `?`，从 schema 现推、不另写一份 */
    private fun paramList(spec: ToolSpec): String {
        val props = spec.parameters.optJSONObject("properties") ?: JSONObject()
        val required = spec.parameters.optJSONArray("required")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        }.orEmpty()
        return props.keys().asSequence().toList()
            .joinToString(", ") { if (it in required) it else "$it?" }
    }

    /** 一句话描述（取到第一个句号为止，别把整段截断口径塞进提示词） */
    private fun oneLine(spec: ToolSpec): String {
        val d = spec.description
        val cut = d.indexOf(". ")
        return if (cut > 0) d.substring(0, cut + 1) else d
    }
}
