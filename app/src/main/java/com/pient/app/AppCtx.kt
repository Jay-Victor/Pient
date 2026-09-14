package com.pient.app

import android.content.Context

/**
 * **应用上下文的唯一持有者**（2026-09-14）。
 *
 * 为什么需要它：内核各处（对话循环、上下文压缩、附件直发……）都需要一个应用上下文，
 * 而又不适合被业务流程直接持有 Activity / Application —— 这里放进程级单例，
 * 由 [PientApp] 在首屏数据就绪后注入。
 *
 * 内核（对话 / 压缩 / 会话存储 / 文件树）一律从这里取上下文。
 */
object AppCtx {

    @Volatile
    private var ctx: Context? = null

    fun set(context: Context) {
        ctx = context.applicationContext
    }

    fun get(): Context? = ctx
}
