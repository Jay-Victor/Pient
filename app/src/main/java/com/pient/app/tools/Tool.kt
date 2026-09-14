package com.pient.app.tools

import android.content.Context
import org.json.JSONObject

/**
 * 工具的**四层归属**（全项目唯一划分处）。
 *
 * 边界（设计见 `Docx/Pient 工具层设计.md`）：
 * - [SYSTEM]   系统命令层：Shizuku / Root / 标准三通道，直接对 Android 系统下令（am / pm / cmd / dumpsys）；
 * - [TERMINAL] 终端层：Ubuntu 沙盘（PRoot）里的真 GNU bash —— apt / Node / Python 都在这儿；
 * - [APP]      自身工具层：App 的手脚，**原生直读文件**（不走 shell），`edit` 带 diff 细节；
 * - [EXTENSION] 扩展层：技能、插件与工具包（`包名:工具名`）。
 *
 * 一个工具只属于一层；别处不得再对「这个工具算什么」做第二次判断（旧实现里 `ChatState` 与
 * `ToolPolicy` 各有一份清单，正是边界糊掉的根源）。
 */
enum class ToolLayer(val id: String, val title: String, val desc: String) {
    SYSTEM("system", "系统命令", "Shizuku / Root 通道：直接对 Android 系统下令（am / pm / cmd / dumpsys / getprop）"),
    TERMINAL("terminal", "终端", "Ubuntu 沙盘（PRoot）：真 GNU bash，apt / Node / Python 都在这一层"),
    APP("app", "自身工具", "App 的手脚：原生直读文件，不走 shell"),
    EXTENSION("extension", "扩展", "技能、插件与工具包（包名:工具名）"),
}

/**
 * 一次工具调用 —— **协议层（服务商原生 `tool_calls`）与文本标记层（DSML 兜底 / 软件内机制）
 * 共用同一个模型**，这样调度器只需要认识一种入参。
 *
 * @param id 协议给的调用 id；文本标记形态由 [com.pient.app.data.ToolMarkup] 合成 `call_text_N`
 */
data class ToolCall(val id: String, val name: String, val arguments: String)

/**
 * 一次工具执行结果（**四层统一形态**）：宿主路径与直连路径、内置工具与包工具都产出它，
 * 渲染层（`ToolRows`）因此只需要面对一种结果。
 *
 * @param diff     `edit` 的片段级 unified diff（工具卡「文件编辑」用）
 * @param channel  系统命令层实际生效的通道（`su` / `shizuku` / `standard`），供工具卡徽标显示
 */
data class ToolOutcome(
    val output: String,
    val isError: Boolean = false,
    val diff: String? = null,
    val channel: String? = null,
) {
    companion object {
        fun ok(output: String) = ToolOutcome(output)
        fun err(message: String) = ToolOutcome(message, true)
    }
}

/**
 * 一层的执行体（四层各一个实现，见 `tools/system`、`tools/terminal`、`tools/files`、`tools/extensions`）。
 *
 * 层的边界由 [layer] 固定；[specs] 声明这一层提供给 AI 的工具；[notReady] 给「未就绪」一个
 * 明确出口（未就绪就不执行、直接把原因当作工具结果回给模型，与「未就绪不可选」的页面门控同一口径）。
 */
interface ToolPart {
    val layer: ToolLayer

    /** 层是否就绪；返回非空 = 未就绪原因（调度器不执行，直接把它作为错误结果回给模型） */
    fun notReady(context: Context): String? = null

    /** 执行本层的一个工具（调用方 = [ToolDispatcher]，授权门已经过） */
    suspend fun run(context: Context, name: String, args: JSONObject): ToolOutcome
}
