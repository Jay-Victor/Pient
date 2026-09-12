package com.pient.app.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pient.app.MainActivity
import com.pient.app.PientRuntime
import com.pient.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * pi 宿主的**前台服务**容器（开发计划 §6.4「后台保活」第一项）。
 *
 * 职责：拉起宿主（Node 子进程，与 UI 同进程）+ 常驻通知「Agent 运行中」+ [START_STICKY]
 * （被系统回收后自动重建）。通知带「停止」动作 = 用户的手动降级开关。
 *
 * 类型取 `dataSync`：宿主的形态是「按需读写文件/网络的长任务」，是唯一不需要额外用户动作
 * 即可长期持有的前台服务类型。
 *
 * 通知权限（Android 13+）：没授权时服务照常运行、只是通知不显示——不阻断保活；
 * 启动入口（PientApp）会顺手请求一次。
 */
class PiHostService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("启动中…"))
        // 宿主状态变化 → 刷新通知副标题（模型 / 思考档位），让常驻通知有信息量
        scope.launch {
            PiHost.lastState.collect { st: JSONObject? ->
                val model = st?.optJSONObject("model")?.optString("id").orEmpty()
                val thinking = st?.optString("thinkingLevel").orEmpty()
                val detail = listOfNotNull(
                    model.ifBlank { null }?.let { "模型 $it" },
                    thinking.ifBlank { null }?.let { "思考 $it" },
                ).joinToString(" · ")
                notify(buildNotification(detail.ifBlank { "等待模型配置" }))
            }
        }
        Log.i(TAG, "前台服务已启动（通知 #$NOTIFICATION_ID）")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "用户从通知里停止宿主")
            // 复位标记：用户停的是**这一次**的后台运行，App 回到前台时该重新起来
            // （否则下次拉起服务会跳过 ensureStarted，通知照旧显示「Agent 运行中」而宿主根本没跑）
            PientRuntime.hostStarted = false
            PiHost.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        // 拉宿主要等**数据读盘完成**（模型接线要用 AiConfigStore 里的服务商/模型，同旧实现口径：
        // 早于 ready 启动会写出不完整的 models.json，日志表现为 `宿主启动 … model=unknown`）。
        // PientApp 在 ready 后会再调一次 start()，这里是那一次的落点；
        // START_STICKY 重建（intent == null）或用户停掉后再回前台，也会走到这里重新拉起。
        if (PientRuntime.dataLoaded && !PientRuntime.hostStarted) {
            PientRuntime.hostStarted = true
            val chat = PientRuntime.chatState
            val selected = chat?.selectedModel
            PiHost.ensureStarted(
                applicationContext,
                selected?.provider,
                selected?.name,
                // 思考开关 → 宿主档位：关闭时显式 `off`（pi 默认 medium，不显式关会给默认思考的模型照发推理）
                if (chat?.thinkingEnabled == true) chat.thinkingLevel.piValue else "off",
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Agent 运行中", NotificationManager.IMPORTANCE_LOW).apply {
                description = "pi 宿主（工具层底座）的运行状态"
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(detail: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, PiHostService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pient)
            .setContentTitle("Agent 运行中")
            .setContentText(detail)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .addAction(0, "停止", stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun notify(n: Notification) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, n)
    }

    companion object {
        private const val TAG = "PiHost"
        private const val CHANNEL_ID = "pient_agent"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.pient.app.action.STOP_HOST"

        /** 拉起前台服务（幂等：已在跑时只是重投 onStartCommand） */
        fun start(context: Context) {
            val intent = Intent(context, PiHostService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "前台服务启动失败：${it.message}") }
        }
    }
}
