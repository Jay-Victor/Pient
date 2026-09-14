package com.pient.app.tools

import org.json.JSONArray
import org.json.JSONObject

/**
 * 一个工具的**声明**（下发用）：名字 / 标签 / 描述 / 参数 schema / 所属层 / 来源。
 *
 * 为什么单独抽出来：旧实现里这份声明只存在于 `AppTools.TOOL_SPECS`（Kotlin 抄 pi），
 * 而宿主路径用的是 pi 自己的 TS 声明 —— 同一个工具两处描述，必然漂移。现在**声明只有一处**
 * （各层的 `specs()`），下发（OpenAI / Anthropic 两形态）与工具页展示都从这里取。
 *
 * @param source `native` = 内置原语；`pkg:<包名>` = 工具包提供的工具（M2 起）
 */
data class ToolSpec(
    val name: String,
    val label: String,
    val description: String,
    val parameters: JSONObject,
    val layer: ToolLayer,
    val source: String = NATIVE,
) {
    companion object {
        const val NATIVE = "native"

        /** 参数属性（`{type, description}`） */
        fun prop(type: String, desc: String): JSONObject =
            JSONObject().put("type", type).put("description", desc)

        /** 对象 schema（`{type:object, properties, required}`） */
        fun obj(vararg props: Pair<String, JSONObject>, required: List<String> = emptyList()): JSONObject {
            val properties = JSONObject()
            for ((k, v) in props) properties.put(k, v)
            return JSONObject()
                .put("type", "object")
                .put("properties", properties)
                .put("required", JSONArray(required))
        }
    }
}

/**
 * 展示用 glob 例子 `**` + `/`：**在源码里拆开拼** —— Kotlin 词法会对字符串里紧邻的
 * `*` `/` 做块注释扫描，`"**" + "/x"` 这种写法会在编译期报 `Syntax error: Expecting a
 * top level declaration`（实测，报错位置指向那个 `/`，整段对象体被吃掉）。
 */
val GLOB_ANY_DEPTH: String = "*" + "*" + "/" + "*"
