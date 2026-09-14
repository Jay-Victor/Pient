package com.pient.app.tools

import android.content.Context
import com.pient.app.tools.extensions.ExtensionsPart
import com.pient.app.tools.files.FilesPart
import com.pient.app.tools.system.SystemPart
import com.pient.app.tools.terminal.TerminalPart
import org.json.JSONArray
import org.json.JSONObject

/**
 * **工具注册表（唯一工具清单）** —— 声明来自**工具包**（[ToolPkgLoader]，内置包在 `assets/toolpkg/`），
 * 执行体是四层 Kotlin 实现（[ToolPart]）。下发、授权、展示都只认这一份。
 *
 * 为什么拆成"声明在包、执行在层"（2026-09-14，用户口径「包只做声明 + 调度」）：
 * - 加一个工具 = 改一份声明文件，不用动 Kotlin；
 * - 用户包与将来的 JS 包走同一条路（内置包只是 `source = assets` 的包）；
 * - 调度器按**包声明的 layer** 找到执行体，包与实现各自演化不互相牵制。
 *
 * 工具顺序 = **按层**（系统命令 → 终端 → 自身工具 → 扩展），层内按包名排序 —— 与历史下发顺序一致，
 * 免得模型看到的工具次序每次都变。
 */
object ToolRegistry {

    /** 四层执行体（层的顺序即工具顺序；每层只负责执行，不再声明工具） */
    val parts: List<ToolPart> = listOf(SystemPart, TerminalPart, FilesPart, ExtensionsPart)

    /** 宿主侧扩展工具名（宿主已冻结：留空，原 `android_shell` 已并入系统命令层） */
    val HOST_EXTENSION_TOOLS: List<String> = emptyList()

    private fun partOfLayer(layer: ToolLayer): ToolPart = parts.first { it.layer == layer }

    /** 某层的执行体（调度器用） */
    fun partOf(layer: ToolLayer): ToolPart = partOfLayer(layer)

    /** 全部**已启用**工具声明（由包产出；按层 → 包名排序） */
    fun specs(context: Context): List<ToolSpec> {
        val out = ArrayList<ToolSpec>()
        val packages = ToolPkgLoader.enabled(context).sortedBy { it.pkg.name }
        for (layer in ToolLayer.entries) {
            packages.filter { it.pkg.layer == layer }.forEach { st ->
                st.effectiveTools.forEach { t ->
                    out += ToolSpec(
                        name = t.name,
                        label = t.label,
                        description = t.description,
                        parameters = t.parameters,
                        layer = layer,
                        source = "pkg:${st.pkg.name}",
                    )
                }
            }
        }
        return out
    }

    /** 全部已启用工具名（下发顺序） */
    fun names(context: Context): List<String> = specs(context).map { it.name }

    fun specOf(context: Context, name: String): ToolSpec? = specs(context).firstOrNull { it.name == name }

    /** 授权列表要覆盖的工具名（含宿主侧扩展工具；宿主冻结后即工具名本身） */
    fun gateNames(context: Context): List<String> = names(context) + HOST_EXTENSION_TOOLS

    fun layerOf(context: Context, name: String): ToolLayer? = specOf(context, name)?.layer

    /** OpenAI 协议形态：`tools: [{type:"function", function:{name, description, parameters}}]` */
    fun definitions(context: Context): JSONArray {
        val arr = JSONArray()
        for (t in specs(context)) {
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
    fun anthropicDefinitions(context: Context): JSONArray {
        val arr = JSONArray()
        for (t in specs(context)) {
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
