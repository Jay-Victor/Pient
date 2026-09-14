package com.pient.app.data

import androidx.compose.runtime.mutableStateListOf

/**
 * 终端页的**界面态会话**（占位，2026-09-14 用户拍板）。
 *
 * 现状：终端执行链路（Ubuntu rootfs / PRoot / 真 bash 子进程）已整体移除，终端页只保留
 * UI 设计与交互 —— 输入的命令**只回显到屏幕上、再补一行占位提示**，不连接任何真实进程；
 * 会话只存内存（重启即空）。对外形态与原 `tools/terminal/TerminalSessions` 保持一致
 * （ensure / newSession / close / submit / clear），页面代码得以保持原样。
 */
object TerminalShell {

    class Session(val name: String) {
        val lines = mutableStateListOf<TerminalLine>()
    }

    val sessions = mutableStateListOf<Session>()

    private var created = 0

    /** 首个会话在首次进入终端页时建立（原 TerminalSessions.ensure 同语义） */
    fun ensure(): Session {
        if (sessions.isEmpty()) newSession()
        return sessions.first()
    }

    /** 新建会话：品牌横幅 + 一行「演示模式」说明（新会话与首会话同款） */
    fun newSession(): Session {
        created += 1
        val s = Session("bash-$created")
        s.lines.addAll(MockTerminal.banner)
        s.lines += TerminalLine("（终端执行已停用：当前版本只保留界面演示，命令不会真正运行）", TerminalLineKind.OUTPUT)
        sessions += s
        return s
    }

    fun close(session: Session) {
        sessions.remove(session)
    }

    /** 提交一条命令：回显命令行 + 一行占位输出（不连接任何真实进程） */
    fun submit(session: Session, cmd: String) {
        if (cmd.isBlank()) return
        session.lines += TerminalLine("~ \$ $cmd", TerminalLineKind.COMMAND)
        session.lines += TerminalLine("终端执行已停用", TerminalLineKind.OUTPUT)
    }

    /** 清屏（纯界面动作，保留） */
    fun clear(session: Session) {
        session.lines.clear()
    }

    /** Ctrl+C 中断：只回一行占位提示（没有真实进程可中断） */
    fun interrupt(session: Session) {
        session.lines += TerminalLine("中断已停用（没有正在运行的命令）", TerminalLineKind.OUTPUT)
    }
}
