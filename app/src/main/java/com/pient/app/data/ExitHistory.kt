package com.pient.app.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.pient.app.data.i18n.L
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「上次是怎么退出的」（2026-09-17）。
 *
 * 为什么需要它：我们自己的崩溃 handler 只能记「Java 未捕获异常」这一种死法，而用户实际遇到的
 * 「应用怎么没了 / 卡死了 / 后台回来啥都没了」多数是另外几种 —— **ANR**、**被系统回收内存杀掉（LMK）**、
 * **用户强停**、原生崩溃。Android 11（API 30）起系统为每个应用保留一小段进程退出记录
 * （`ActivityManager.getHistoricalProcessExitReasons`，环形缓冲、读自己的无需任何权限），
 * ANR 那次还能取到 trace（`ApplicationExitInfo.getTraceInputStream`）。
 *
 * 口径（照 Android 官方）：
 * - `REASON_CRASH`(4) / `REASON_CRASH_NATIVE`(5) / `REASON_ANR`(6) / `REASON_LOW_MEMORY`(3) /
 *   `REASON_SIGNALED`(2) / `REASON_EXIT_SELF`(1) / `REASON_USER_REQUESTED`(10) …
 * - 环形缓冲里最近一条**不属于当前进程**的记录 = 「上次退出」；当前进程还没退出，不会出现在里面。
 * - API < 30 无此能力（[supported] 返回 false，页面如实写明）。
 */
object ExitHistory {

    private const val MAX_RECORDS = 8

    /** 一条退出记录（给页面与导出共用） */
    data class LastExit(
        val reason: Int,
        val label: String,
        val timeMs: Long,
        val detail: String,
        val hasAnrTrace: Boolean,
        /** 出问题的那几种（崩溃 / ANR / 被系统杀）—— 页面据此选图标与措辞 */
        val abnormal: Boolean,
    )

    /** 本机是否支持（Android 11 起） */
    fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** 最近一次进程退出；不支持 / 没有记录返回 null。调用方放 IO 线程（要读系统记录）。 */
    fun last(context: Context): LastExit? {
        if (!supported()) return null
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
            val infos = am.getHistoricalProcessExitReasons(context.packageName, 0, MAX_RECORDS)
            val info = infos.maxByOrNull { it.timestamp } ?: return null
            val reason = info.reason
            LastExit(
                reason = reason,
                label = labelOf(reason),
                timeMs = info.timestamp,
                detail = detailOf(info),
                hasAnrTrace = runCatching { info.traceInputStream != null }.getOrDefault(false),
                abnormal = reason in setOf(
                    android.app.ApplicationExitInfo.REASON_CRASH,
                    android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,
                    android.app.ApplicationExitInfo.REASON_ANR,
                    android.app.ApplicationExitInfo.REASON_LOW_MEMORY,
                    android.app.ApplicationExitInfo.REASON_SIGNALED,
                ),
            )
        }.getOrElse {
            PientLog.w("PientLog", "读退出记录失败：${it.message}")
            null
        }
    }

    private fun labelOf(reason: Int): String = when (reason) {
        android.app.ApplicationExitInfo.REASON_CRASH -> L.settings.logExitCrash
        android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> L.settings.logExitCrashNative
        android.app.ApplicationExitInfo.REASON_ANR -> L.settings.logExitAnr
        android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> L.settings.logExitLowMemory
        android.app.ApplicationExitInfo.REASON_SIGNALED -> L.settings.logExitSignaled
        android.app.ApplicationExitInfo.REASON_EXIT_SELF -> L.settings.logExitSelf
        android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> L.settings.logExitUser
        else -> L.settings.logExitOther
    }

    private fun detailOf(info: android.app.ApplicationExitInfo): String {
        val sb = StringBuilder()
        sb.append("reason=").append(info.reason)
        info.description?.takeIf { it.isNotBlank() }?.let { sb.append(" description=").append(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sb.append(" importance=").append(info.importance)
            sb.append(" status=").append(info.status)
        }
        info.pss?.let { if (it > 0) sb.append(" pss=").append(it) }
        info.rss?.let { if (it > 0) sb.append(" rss=").append(it) }
        return sb.toString()
    }

    /**
     * 导出用文本段（排障产物，英文键）：退出原因原文 + 时间 + 详情 + ANR 轨迹（截断）。
     * 无记录 / 不支持时也返回一行说明 —— 导出文件里「为什么没有这段」同样是有用信息。
     */
    fun exportSection(context: Context): String {
        if (!supported()) return "unsupported: Android 11 (API 30) or newer keeps these records"
        val last = last(context) ?: return "no record"
        val sb = StringBuilder()
        sb.append("reason: ").append(last.label).append(" (").append(last.reason).append(")\n")
        sb.append("time: ").append(formatTime(last.timeMs)).append('\n')
        sb.append(last.detail).append('\n')
        val trace = readAnrTrace(context)
        if (trace.isNullOrBlank()) {
            sb.append("anr_trace: -")
        } else {
            val lines = trace.lines()
            val capped = lines.take(MAX_TRACE_LINES)
            sb.append("anr_trace: ").append(lines.size).append(" lines")
            if (lines.size > MAX_TRACE_LINES) sb.append("（只保留前 $MAX_TRACE_LINES 行）")
            sb.append('\n').append(capped.joinToString("\n"))
        }
        return sb.toString()
    }

    private const val MAX_TRACE_LINES = 400

    /** ANR 轨迹（只有那几种退出带 trace；读不到返回 null） */
    private fun readAnrTrace(context: Context): String? {
        if (!supported()) return null
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
            val info = am.getHistoricalProcessExitReasons(context.packageName, 0, MAX_RECORDS)
                .maxByOrNull { it.timestamp } ?: return null
            val stream: InputStream = info.traceInputStream ?: return null
            stream.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))
}
