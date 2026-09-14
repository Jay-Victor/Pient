package com.pient.app.tools.extensions

import android.content.Context
import com.pient.app.tools.ToolLayer
import com.pient.app.tools.ToolOutcome
import com.pient.app.tools.ToolPart
import com.pient.app.tools.ToolSpec
import org.json.JSONObject

/**
 * **④ 扩展层** —— Pient 里装的插件与技能的调用面。
 *
 * 边界：这一层不实现工具本身，它负责「**有哪些扩展、它们提供什么工具、怎么被调用**」：
 * - 技能（pi 原生 `SKILL.md` 目录）：作为提示注入，不是工具；本层提供清单与读取入口；
 * - 插件（pi `packages` 里的扩展）：注册在 pi 宿主的进程里，工具名 `包名:工具名`；
 * - 工具包（M2 起）：`assets/toolpkg` 与 `filesDir/toolpkg` 下的包，由 [com.pient.app.tools.ToolPackage]
 *   声明、调度器统一注册。
 *
 * M1 只落边界与占位：`specs()` 为空（现在宿主路径的扩展工具由 pi 自己注册，直连路径没有扩展面），
 * `use_package`（列包 / 激活 / 返回工具清单）与技能清单在 M2 接上。
 */
object ExtensionsPart : ToolPart {

    override val layer = ToolLayer.EXTENSION

    /** AI 可见工具：M2 起提供 `use_package`（照 Operit 的三兼容入口：工具包 / 技能 / 插件） */
    override fun specs(): List<ToolSpec> = emptyList()

    override suspend fun run(context: Context, name: String, args: JSONObject): ToolOutcome =
        ToolOutcome.err("扩展层暂无可执行工具：$name（工具包支持在 M2 落地）")
}
