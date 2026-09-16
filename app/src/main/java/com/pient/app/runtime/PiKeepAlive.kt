package com.pient.app.runtime

import com.pient.app.data.i18n.L
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pient.app.MainActivity
import com.pient.app.R
import java.util.concurrent.ConcurrentHashMap

/**
 * **前台服务保活**（2026-09-16，开发计划 M5）。
 *
 * 为什么需要：pi 是**应用子进程**（App↔pi 走 stdin/stdout 管道），终端会话同样是应用子进程。
 * 应用退到后台 / 熄屏后，Android 会把这套进程一起清掉 —— 进行中的 AI 回合、终端里跑的
 * apt / npm 都会消失（用户看到的是「切走一会儿回来就断了」）。前台服务（带一条常驻通知）
 * 是 Android 上唯一被允许的保活手段。
 *
 * 口径：**只在有活干的时候前台化** —— 一轮对话在跑、或终端里有一段脚本在跑时挂起，
 * 干完就停（不留常驻通知）。可能同时有多个来源（对话 + 终端脚本），所以按 key 引用计数。
 *
 * 与「权限」无关：这是应用自身的能力，标准档同样生效（不需要 Shizuku / Root）。
 */
object PiKeepAlive {
    private const val TAG = "PientKeepAlive"

    /** 终端会话保活的 key 前缀（`term:<会话id>`）；通知点开时要带回终端页 */
    private const val TERM_KEY_PREFIX = "term:"

    /** 通知携带的「打开哪个面板」值（MainActivity 消费，见 PiKeepAliveService.EXTRA_PANEL） */
    const val PANEL_TERMINAL = "terminal"

    /** 正在干活的来源：key → 通知副标题（对话 = "chat"，终端脚本 = "script:<会话名>"） */
    private val active = ConcurrentHashMap<String, String>()

    @Volatile
    private var running = false

    /** 某个来源开始干活：第一个来源把前台服务拉起来（通知文案取它） */
    fun acquire(context: Context?, key: String, text: String) {
        val ctx = context?.applicationContext ?: return
        active[key] = text
        if (running) return
        running = true
        runCatching {
            val intent = Intent(ctx, PiKeepAliveService::class.java)
                .putExtra(PiKeepAliveService.EXTRA_TEXT, text)
                // 通知点开回哪个页（2026-09-16）：终端会话 → 终端页；聊天回合 → 打开应用即可
                .putExtra(
                    PiKeepAliveService.EXTRA_PANEL,
                    if (key.startsWith(TERM_KEY_PREFIX)) PANEL_TERMINAL else "",
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
            else ctx.startService(intent)
            Log.i(TAG, "前台保活已开启（$key：$text）")
        }.onFailure {
            running = false
            Log.w(TAG, "前台服务启动失败：${it.message}")
        }
    }

    /** 某个来源干完了：没有来源了就停服务（不留常驻通知） */
    fun release(context: Context?, key: String) {
        val ctx = context?.applicationContext ?: return
        active.remove(key)
        if (active.isEmpty() && running) {
            running = false
            runCatching {
                // **不能用 stopService()**（2026-09-16 真机崩溃实测）：
                // acquire 的 `startForegroundService()` 还挂着、服务还没跑进 onStartCommand 时被 stopService 撤单，
                // Android 判定「startForegroundService() 没有随后调用 startForeground()」→
                // `RemoteServiceException: ForegroundServiceDidNotStartInTimeException` **直接杀掉应用**。
                // 触发面很小但很真实：**一轮在 ~100ms 内就结束**（被 pi 拒绝 / 立刻中止 / 生成失败）——
                // markRunning(true)→(false) 挨得太近。改成给服务送一条「停」指令，让它自己走完
                // `startForeground()` 契约再自停（服务未起时也只是一次极短的建-停，不违反契约）。
                ctx.startService(
                    Intent(ctx, PiKeepAliveService::class.java).setAction(PiKeepAliveService.ACTION_STOP),
                )
                Log.i(TAG, "前台保活已关闭（没有在跑的活了）")
            }
        }
    }

    /** 当前是否在前台保活（诊断/日志用） */
    fun isRunning(): Boolean = running
}

/**
 * 前台服务本体：只负责挂一条通知把进程留在前台。
 * `START_NOT_STICKY`：被系统杀掉后**不要**自动重启 —— pi 子进程已经不在了，重启一个空壳没有意义，
 * 下一次用户操作会按需重新拉起（`ensureRootfsAsync` / 通道启动都在）。
 */
class PiKeepAliveService : Service() {

    companion object {
        const val EXTRA_TEXT = "text"

        /**
         * 「只做收尾，不保活」的指令（[PiKeepAlive.release] 用）。
         *
         * 为什么不是 stopService()：`startForegroundService()` 之后必须由服务自己调用
         * `startForeground()`，否则系统抛 `ForegroundServiceDidNotStartInTimeException` 杀进程
         * （2026-09-16 真机崩溃实测：一轮在 ~100ms 内结束就会撞上）。走一条普通 startService 送
         * 「停」指令 = 服务一定先拿到 onStartCommand 走完契约，再自己停。
         */
        const val ACTION_STOP = "com.pient.app.action.KEEPALIVE_STOP"

        /** 「点通知回哪个面板」的 extra（值 = [PiKeepAlive.PANEL_TERMINAL]） */
        const val EXTRA_PANEL = "pient_panel"
        const val CHANNEL_ID = "pient_runtime"
        const val NOTIF_ID = 1001
        private const val TAG = "PientKeepAlive"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: L.common.running
        // 点通知回到对应页面（2026-09-16）：终端会话 → 终端页；其余 → 只是把应用调到前台
        val panel = intent?.getStringExtra(EXTRA_PANEL).orEmpty()
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (panel.isNotEmpty()) putExtra(EXTRA_PANEL, panel) }
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.pient_logo)
            .setContentTitle("Pient")
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    open,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        }.onFailure {
            // 通知权限被拒 / 类型不允许：如实记一条，别让保活静默失效
            Log.w(TAG, "startForeground 失败（保活未生效）：${it.message}")
        }
        // 「停」指令：**先走完 startForeground 契约再自停**（见 [ACTION_STOP] 的注释 ——
        // 直接 stopService 撤单会让系统抛 ForegroundServiceDidNotStartInTimeException 杀掉应用）
        if (intent?.action == ACTION_STOP) {
            runCatching { @Suppress("DEPRECATION") stopForeground(true) }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /** 通知渠道（幂等；IMPORTANCE_LOW = 不响不震，只在通知栏占一行） */
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, L.runtime.keepAliveChannelName, NotificationManager.IMPORTANCE_LOW).apply {
                        description = L.runtime.keepAliveChannelDesc
                        setShowBadge(false)
                    },
                )
            }
        }.onFailure { Log.w(TAG, "创建通知渠道失败：${it.message}") }
    }
}
