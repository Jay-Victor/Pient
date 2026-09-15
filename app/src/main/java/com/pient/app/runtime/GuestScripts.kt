package com.pient.app.runtime

import android.content.Context

/**
 * 应用**内部脚本**跑在终端页会话里的统一入口。
 *
 * 为什么需要它：pi 包管理、技能市场、环境配置的安装动作都要「用户看得见地」跑在终端里
 * （口径：输出实时滚在终端页，页面只留一条状态），而它们的落点**固定是 Ubuntu** ——
 * 见 `TerminalSessions.Session.execEnvOverride`：这些脚本要的是「node 与 pi 在那棵 rootfs」，
 * 不能跟着用户给终端页选的执行环境（chroot / 其它）漂移。
 */
object GuestScripts {

    /** 取（或新建）命名会话；**固定 Ubuntu**，不跟随 `exec_env` 选择 */
    fun sessionFor(context: Context, name: String): TerminalSessions.Session =
        TerminalSessions.sessionNamed(name)
            ?: TerminalSessions.newSession(context, name, execEnvOverride = "ubuntu")

    /**
     * 在命名会话里跑一条命令：输出实时进终端页，结束（哨兵行）后回调退出码。
     * @return 承载本次执行的会话（调用方据此切到终端页并选中它）
     */
    fun runInTerminal(
        context: Context,
        sessionName: String,
        label: String,
        cmd: String,
        onDone: ((Int) -> Unit)? = null,
    ): TerminalSessions.Session {
        val session = sessionFor(context, sessionName)
        TerminalSessions.runScript(session, label, listOf(cmd)) { code -> onDone?.invoke(code) }
        return session
    }
}
