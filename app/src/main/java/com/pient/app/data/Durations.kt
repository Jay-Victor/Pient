package com.pient.app.data

/**
 * 停顿时长文案（Hermes `formatDurationSeconds`：<1s 用 ms、<10s 一位小数、<60s 秒、否则 分+秒）。
 *
 * 放在 data 层是因为有两个消费面：工具行 meta（`ui/chat/ToolRows.kt`）与复制卡 XML 分段的
 * `duration` 属性（`data/MessageXml.kt`）——同一语义只留一份实现。
 */
fun formatDuration(ms: Long?): String? {
    if (ms == null || ms < 0) return null
    val s = ms / 1000.0
    return when {
        s < 1 -> "${maxOf(1L, ms)}ms"
        s < 10 -> String.format("%.1fs", s)
        s < 60 -> "${s.toInt()}s"
        else -> "${(s / 60).toInt()}m${(s % 60).toInt()}s"
    }
}
