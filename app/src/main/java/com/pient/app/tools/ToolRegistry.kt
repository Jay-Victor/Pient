package com.pient.app.tools

import com.pient.app.tools.extensions.ExtensionsPart
import com.pient.app.tools.files.FilesPart
import com.pient.app.tools.system.SystemPart
import com.pient.app.tools.terminal.TerminalPart
import org.json.JSONArray
import org.json.JSONObject

/**
 * **工具注册表（唯一工具清单）** —— 四层提供的工具在这里合并成一份，下发（服务商请求）
 * 与展示（工具页 / 授权列表）都只认这一份。
 *
 * 为什么需要它：旧实现里「有哪些工具」散在三处 —— `AppTools.NAMES`（直连下发）、
 * `ToolPolicy.TOOLS`（授权列表）、`assets/pient-system.ts` + pi 自己的声明（宿主下发）。
 * 三处名单各自演化，用户看到的工具与实际能跑的工具因此对不上。
 */
object ToolRegistry {

    /** 四层执行体（顺序 = 工具页的分节顺序） */
    val parts: List<ToolPart> = listOf(SystemPart, TerminalPart, FilesPart, ExtensionsPart)

    /** 宿主侧扩展工具名：pi 进程里的扩展注册的工具（M3 起改由调度器统一暴露） */
    val HOST_EXTENSION_TOOLS = listOf("android_shell")

    private val index: Map<String, ToolSpec> by lazy { specs().associateBy { it.name } }

    /** 全部工具声明（顺序：系统命令 → 终端 → 自身工具 → 扩展） */
    fun specs(): List<ToolSpec> = parts.flatMap { it.specs() }

    /** 全部工具名（下发顺序） */
    fun names(): List<String> = specs().map { it.name }

    fun specOf(name: String): ToolSpec? = index[name]

    /** 授权列表要覆盖的工具名（含宿主侧扩展工具） */
    fun gateNames(): List<String> = names() + HOST_EXTENSION_TOOLS

    fun layerOf(name: String): ToolLayer? = index[name]?.layer

    /** 某层的执行体 */
    fun partOf(layer: ToolLayer): ToolPart = parts.first { it.layer == layer }

    /** OpenAI 协议形态：`tools: [{type:"function", function:{name, description, parameters}}]` */
    fun definitions(): JSONArray {
        val arr = JSONArray()
        for (t in specs()) {
            arr.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", t.name)
                            .put("description", t.description)
                            .put("parameters", t.parameters),
                    ),
            )
        }
        return arr
    }

    /** Anthropic Messages 协议形态：`tools: [{name, description, input_schema}]` */
    fun anthropicDefinitions(): JSONArray {
        val arr = JSONArray()
        for (t in specs()) {
            arr.put(
                JSONObject()
                    .put("name", t.name)
                    .put("description", t.description)
                    .put("input_schema", t.parameters),
            )
        }
        return arr
    }
}
