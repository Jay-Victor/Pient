package com.pient.app

import android.content.Context

/**
 * **应用上下文的唯一持有者**（2026-09-14）。
 *
 * 为什么需要它：宿主冻结前，内核各处都从 pi 宿主的 context 持有者（`PiHost.appContextOrNull`）
 * 取应用上下文；宿主不再启动后那里恒为 null —— 实测后果是直连路径**不下发任何工具**
 * （设备侧 mock 日志 `tools=0[]`），工具执行也报「应用上下文缺失」。
 *
 * 内核（对话循环 / 工具调度 / 会话存储 / 技能与插件）一律从这里取上下文；
 * 冻结的宿主代码保留自己的持有者，两边不再互相依赖。
 */
object AppCtx {

    @Volatile
    private var ctx: Context? = null

    fun set(context: Context) {
        ctx = context.applicationContext
    }

    fun get(): Context? = ctx
}
