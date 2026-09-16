package com.pient.app.runtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pient.app.MainActivity
import com.pient.app.PientRuntime
import com.pient.app.R
import com.pient.app.data.SettingsStore
import com.pient.app.data.i18n.L

/**
 * **消息通知**（2026-09-16，行为设置；照 Operit 的「回复通知」口径实现）：
 * AI 回复完成、且**应用不在前台**（[PientRuntime.appVisible] 为假）时发一条系统通知。
 * 应用在前台一律静默 —— 用户正看着屏幕，不用再提醒（Operit 同口径：`getCurrentActivity() != null` 就返回）。
 *
 * 三个开关（[SettingsStore]）：`replyNotify`（总开关）/ `replyNotifySound` / `replyNotifyVibrate`，
 * 默认 = 开 / 关 / 关。
 *
 * 为什么是**按「提示音 × 震动」四档各建一个通知渠道**（Operit 的 `buildReplyNotificationChannelId` 同款）：
 * Android 8 起声音与震动是**渠道属性**，渠道一旦建好，应用就改不动了（用户在系统设置里仍可再改）；
 * 所以「可切换的声音/震动组合」只能各自占一个渠道 id。渠道名与档位一一对应，
 * 用户在系统通知设置里看到的就是当前生效的那一档。
 *
 * 通知 id 走 [NOTIF_ID]（前台服务 = 1001 / 保活中断说明 = 1002，三者互不覆盖）。
 */
object ReplyNotify {

    private const val TAG = "PientReplyNotify"

    /** 消息通知的 id（前台服务 1001、保活中断说明 1002 之外另起一号） */
    const val NOTIF_ID = 1003

    /** 渠道 id 前缀（后缀按档位拼，见 [channelId]） */
    private const val CHANNEL_PREFIX = "pient_reply"

    /** 震动节奏（Operit `REPLY_VIBRATION_PATTERN` 逐值对齐：0ms 起振、250ms 振、150ms 停、250ms 振） */
    private val VIBRATION_PATTERN = longArrayOf(0L, 250L, 150L, 250L)

    /**
     * AI 回复完成时调用（[com.pient.app.data.ChatState] 的两处成功出口：发送 / 重新生成）。
     *
     * @param sessionTitle 通知标题 = 当前会话名（空则回落品牌名 Pient）
     * @param replyText 回复正文（通知里取预览，展开看全文）
     */
    fun notifyReply(context: Context?, sessionTitle: String?, replyText: String) {
        val ctx = context?.applicationContext ?: return
        if (!SettingsStore.replyNotify) return
        // 前台静默（用户拍板，对齐 Operit）：用户正看着 Pient 时不再提醒
        if (PientRuntime.appVisible) return
        val body = replyText.trim()
        if (body.isEmpty()) return
        runCatching { post(ctx, sessionTitle?.takeIf { it.isNotBlank() }, body) }
            .onFailure { Log.w(TAG, "发送消息通知失败：${it.message}") }
    }

    private fun post(ctx: Context, sessionTitle: String?, body: String) {
        val channelId = ensureChannel(ctx, SettingsStore.replyNotifySound, SettingsStore.replyNotifyVibrate)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val notif = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(R.drawable.pient_logo)
            .setContentTitle(sessionTitle ?: "Pient")
            // 折叠态取前 100 字（Operit 同口径），展开看全文
            .setContentText(body.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(
                PendingIntent.getActivity(
                    ctx,
                    0,
                    open,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID, notif)
        Log.i(TAG, "消息通知已发送（$channelId）：${body.take(40)}")
    }

    /** 渠道 id = 前缀 + 档位后缀（Operit 的 `_sound_vibration / _sound / _vibration / _silent` 同款） */
    private fun channelId(sound: Boolean, vibrate: Boolean): String = when {
        sound && vibrate -> "${CHANNEL_PREFIX}_sound_vibration"
        sound -> "${CHANNEL_PREFIX}_sound"
        vibrate -> "${CHANNEL_PREFIX}_vibration"
        else -> "${CHANNEL_PREFIX}_silent"
    }

    /** 渠道显示名：四档各一条文案（系统设置里看到的就是当前生效那档） */
    private fun channelName(sound: Boolean, vibrate: Boolean): String = when {
        sound && vibrate -> L.runtime.replyChannelBoth
        sound -> L.runtime.replyChannelSound
        vibrate -> L.runtime.replyChannelVibration
        else -> L.runtime.replyChannelSilent
    }

    /**
     * 幂等建渠道（同 id 重复调用无副作用）：`IMPORTANCE_HIGH` + 按开关设声音 / 震动。
     * 声音用**系统默认通知音**（Operit 口径：不内置音源，尊重用户在系统里的选择）。
     */
    private fun ensureChannel(ctx: Context, sound: Boolean, vibrate: Boolean): String {
        val id = channelId(sound, vibrate)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return id
        runCatching {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(id) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        id,
                        channelName(sound, vibrate),
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = L.runtime.replyChannelDesc
                        setSound(
                            if (sound) Settings.System.DEFAULT_NOTIFICATION_URI else null,
                            if (sound) {
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                            } else {
                                null
                            },
                        )
                        // 顺序要紧（2026-09-16 实测）：setVibrationPattern 会把渠道的「震动」标志一并置真，
                        // 所以先设 pattern、再用 enableVibration 收尾 —— 关闭震动的档（静默 / 纯响铃）在
                        // 系统设置里才会显示「振动 关」，而不是「振动 开、但没效果」
                        vibrationPattern = if (vibrate) VIBRATION_PATTERN else null
                        enableVibration(vibrate)
                    },
                )
            }
        }.onFailure { Log.w(TAG, "创建消息通知渠道失败：${it.message}") }
        return id
    }
}
