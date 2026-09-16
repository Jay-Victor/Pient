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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.pient.app.MainActivity
import com.pient.app.PientRuntime
import com.pient.app.R
import java.util.concurrent.ConcurrentHashMap

/**
 * 通知卡片展开区的**明细行**（2026-09-16 用户要求：模型 / 思考等级 / 运行会话数 / 工具调用数）。
 * 每一项都允许缺省（null / 0 = 不知道或没有），渲染时缺项不显示那一行。
 * 数据源注册见 [PiKeepAlive.detailProvider]。
 */
data class KeepAliveDetail(
    /** 当前模型名（没配模型 = null） */
    val model: String? = null,
    /** 思考等级显示名（关闭思考时由提供方给 "off"） */
    val thinking: String? = null,
    /** 终端会话数（不含「AI 执行」镜像会话） */
    val sessions: Int = 0,
    /** 本轮工具调用数 */
    val toolCalls: Int = 0,
)

/**
 * **前台服务保活**（2026-09-16，开发计划 M5）。
 *
 * 为什么需要：pi 是**应用子进程**（App↔pi 走 stdin/stdout 管道），终端会话同样是应用子进程。
 * 应用退到后台 / 熄屏后，Android 会把这套进程一起清掉 —— 进行中的 AI 回合、终端里跑的
 * apt / npm 都会消失（用户看到的是「切走一会儿回来就断了」）。前台服务（带一条常驻通知）
 * 是 Android 上唯一被允许的保活手段。
 *
 * 口径：**两种档位** ——
 * ① 按需（默认）：只在有活干的时候前台化 —— 一轮对话在跑、或终端里有活在跑时挂起，干完就停；
 * ② 常驻（行为设置「后台常驻通知」打开，见 [setResident]）：一直挂着，通知栏常驻一条，
 *    进程始终不退后台（服务类型走 `specialUse`，不受 dataSync 的时限约束）。
 * 可能同时有多个来源（对话 + 终端脚本 + 常驻），所以按 key 引用计数，通知文案取当前最“有信息量”的那条。
 *
 * 与「权限」无关：这是应用自身的能力，标准档同样生效（不需要 Shizuku / Root）。
 */
object PiKeepAlive {
    private const val TAG = "PientKeepAlive"

    /** 聊天回合的 key（[com.pient.app.data.ChatState.markRunning]） */
    private const val CHAT_KEY = "chat"

    /** 应用发起的终端脚本的 key 前缀（`script:<会话名>`，[GuestScripts.runInTerminal]） */
    private const val SCRIPT_KEY_PREFIX = "script:"

    /** 终端会话的 key 前缀（`term:<会话id>`）；通知点开时要带回终端页 */
    private const val TERM_KEY_PREFIX = "term:"

    /** **常驻档**的 key（行为设置「后台常驻通知」；见 [setResident]） */
    const val KEY_RESIDENT = "resident"

    /** 通知携带的「打开哪个面板」值（MainActivity 消费，见 [PiKeepAliveService.EXTRA_PANEL]） */
    const val PANEL_TERMINAL = "terminal"

    /** 正在干活的来源：key → 通知副标题（对话 = "chat"，终端脚本 = "script:<会话名>"） */
    private val active = ConcurrentHashMap<String, String>()

    @Volatile
    private var running = false

    /** 上次下发给通知的（文案 + 是否常驻档）：用来跳过无变化的刷新 */
    @Volatile
    private var sentText: String? = null

    @Volatile
    private var sentResident = false

    /**
     * 通知卡片的**明细数据源**（2026-09-16 用户要求：模型 / 思考等级 / 运行会话数 / 工具调用数）：
     * 由 `ChatState` 构造时注册 —— runtime 层不反向依赖 data 层，谁有数据谁提供，缺项不显示那一行。
     */
    var detailProvider: (() -> KeepAliveDetail?)? = null

    /** 上次下发的（明细文本 + 计时起点）：明细变化 / 活动换了一条时刷新 */
    @Volatile
    private var sentBig: String? = null

    @Volatile
    private var sentWhen = 0L

    /** 某个来源开始干活：第一个来源把前台服务拉起来（通知文案取它）；已在跑就只刷新文案 */
    fun acquire(context: Context?, key: String, text: String) {
        val ctx = context?.applicationContext ?: return
        active[key] = text
        if (running) {
            refresh(ctx, key)
            return
        }
        running = true
        runCatching {
            push(ctx, startNew = true)
            Log.i(TAG, "前台保活已开启（$key：${currentText()}）")
        }.onFailure {
            running = false
            sentText = null
            Log.w(TAG, "前台服务启动失败：${it.message}")
        }
    }

    /** 某个来源干完了：没有来源了就停服务（不留常驻通知）；还有别的来源就刷新文案 */
    fun release(context: Context?, key: String) {
        val ctx = context?.applicationContext ?: return
        active.remove(key)
        if (active.isNotEmpty()) {
            if (running) refresh(ctx, key)
            return
        }
        if (!running) return
        running = false
        sentText = null
        sentBig = null
        sentWhen = 0L
        sentResident = false
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

    /**
     * **常驻档**（行为设置「后台常驻通知」）：打开时挂一个不会自己释放的来源 ——
     * 前台服务一直留着、通知栏常驻一条，进程不退后台（pi 子进程与终端会话也跟着活下来）。
     * 服务类型走 `specialUse`（见 [PiKeepAliveService.foregroundType]），
     * 不受 `dataSync` 的「后台累计 6 小时 / 24 小时」上限约束。
     */
    fun setResident(context: Context?, resident: Boolean) {
        if (resident) {
            acquire(context, KEY_RESIDENT, L.runtime.residentRunning)
        } else {
            release(context, KEY_RESIDENT)
        }
    }

    /** 当前是否在前台保活（诊断/日志用） */
    fun isRunning(): Boolean = running

    /**
     * **保活被系统停掉时如实告知**（2026-09-16）：不假装还在保活。
     * 应用在前台 → Toast（用户正看着屏幕）；已退后台 → 发一条可点开的说明通知。
     */
    fun notifyInterrupted(context: Context?) {
        val ctx = context?.applicationContext ?: return
        if (PientRuntime.appVisible) {
            runCatching {
                Toast.makeText(ctx, L.runtime.keepAliveInterruptedText, Toast.LENGTH_LONG).show()
            }.onFailure { Log.w(TAG, "提示保活中断失败：${it.message}") }
            return
        }
        runCatching {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ensureChannel(ctx)
            nm.notify(
                PiKeepAliveService.NOTIF_INTERRUPTED,
                NotificationCompat.Builder(ctx, PiKeepAliveService.CHANNEL_ID)
                    .setSmallIcon(R.drawable.pient_logo)
                    .setContentTitle(L.runtime.keepAliveInterruptedTitle)
                    .setContentText(L.runtime.keepAliveInterruptedText)
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(L.runtime.keepAliveInterruptedText),
                    )
                    .setContentIntent(
                        PendingIntent.getActivity(
                            ctx,
                            0,
                            Intent(ctx, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                        ),
                    )
                    .setAutoCancel(true)
                    .build(),
            )
        }.onFailure { Log.w(TAG, "发送保活中断说明失败：${it.message}") }
    }

    /** 通知渠道（幂等；IMPORTANCE_LOW = 不响不震，只在通知栏占一行）。服务与「保活中断说明」共用一份 */
    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(PiKeepAliveService.CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        PiKeepAliveService.CHANNEL_ID,
                        L.runtime.keepAliveChannelName,
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = L.runtime.keepAliveChannelDesc
                        setShowBadge(false)
                    },
                )
            }
        }.onFailure { Log.w(TAG, "创建通知渠道失败：${it.message}") }
    }

    /** 当前该进通知栏的文案：聊天回合 → 终端脚本 → 终端会话 → 其余（常驻） */
    private fun currentText(): String =
        active[CHAT_KEY]
            ?: active.entries.firstOrNull { it.key.startsWith(SCRIPT_KEY_PREFIX) }?.value
            ?: active.entries.firstOrNull { it.key.startsWith(TERM_KEY_PREFIX) }?.value
            ?: active.values.firstOrNull()
            ?: L.common.running

    /** 点通知回哪：聊天回合 → 只把应用调到前台；否则终端会话在跑 → 终端页 */
    private fun currentPanel(): String =
        if (active.containsKey(CHAT_KEY)) ""
        else if (active.keys.any { it.startsWith(TERM_KEY_PREFIX) }) PANEL_TERMINAL
        else ""

    /** 组合变化（某个来源进出 / 常驻档切换 / 明细或会话数变了）后把通知刷新成最新内容 */
    private fun refresh(ctx: Context, key: String) {
        if (currentText() == sentText &&
            active.containsKey(KEY_RESIDENT) == sentResident &&
            currentBig() == sentBig
        ) return
        runCatching {
            push(ctx, startNew = false)
            Log.i(TAG, "前台保活通知已刷新（$key：${currentText()}）")
        }.onFailure { Log.w(TAG, "刷新保活通知失败：${it.message}") }
    }

    /**
     * 明细变了（本轮工具调用数 +1、终端会话开了/关了…）：服务在跑就顺手刷一次通知，
     * 卡片上的数字跟得上，不用等下一次来源进出。
     */
    fun detailChanged(context: Context?) {
        val ctx = context?.applicationContext ?: return
        if (running) refresh(ctx, "detail")
    }

    /**
     * 展开区（BigTextStyle）内容：第一行重复活动文案，其后是明细行
     * （`模型：X · 思考：Y` / `终端会话：N · 工具调用：M`）。没有明细就只有活动那一行。
     */
    private fun currentBig(): String {
        val lines = mutableListOf(currentText())
        val d = runCatching { detailProvider?.invoke() }.getOrNull()
        if (d != null) {
            if (!d.model.isNullOrBlank()) {
                lines += L.runtime.keepAliveBigModelThinking(d.model, d.thinking ?: "off")
            }
            if (d.sessions > 0 || d.toolCalls > 0) {
                lines += L.runtime.keepAliveBigRuntime(d.sessions, d.toolCalls)
            }
        }
        return lines.joinToString("\n")
    }

    /**
     * 下发（或刷新）前台服务。
     *
     * `startNew = true` 用 `startForegroundService()`（服务没在跑时唯一的启动姿势）；
     * `false` 用普通 `startService()` 送一次「刷新」，服务已在前台时这条一定被允许
     * （应用有前台服务 = 系统眼里的前台应用），且不会撞上「startForegroundService 没随后
     * startForeground」的杀进程契约。
     *
     * **计时**（2026-09-16 用户要求「Pient 右侧的时间」）：`EXTRA_WHEN` = 当前活动开始的时刻，
     * 通知卡片上由系统渲染成相对时间（「1 分钟前」）；活动文案不变（只是明细刷新）就不动它，
     * 计时因此是「这条活动跑了多久」，不会被明细刷新重置。
     */
    private fun push(ctx: Context, startNew: Boolean) {
        val text = currentText()
        val resident = active.containsKey(KEY_RESIDENT)
        val big = currentBig()
        val whenMs = if (text != sentText || sentWhen == 0L) System.currentTimeMillis() else sentWhen
        val intent = Intent(ctx, PiKeepAliveService::class.java)
            .putExtra(PiKeepAliveService.EXTRA_TEXT, text)
            .putExtra(PiKeepAliveService.EXTRA_BIG, big)
            .putExtra(PiKeepAliveService.EXTRA_WHEN, whenMs)
            // 常驻档标记：服务据此选前台服务类型（specialUse / dataSync）
            .putExtra(PiKeepAliveService.EXTRA_RESIDENT, resident)
            // 通知点开回哪个页（2026-09-16）：终端会话 → 终端页；聊天回合 → 打开应用即可
            .putExtra(PiKeepAliveService.EXTRA_PANEL, currentPanel())
        if (!startNew) {
            ctx.startService(intent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
        sentText = text
        sentBig = big
        sentWhen = whenMs
        sentResident = resident
    }
}

/**
 * 前台服务本体：只负责挂一条通知把进程留在前台。
 * `START_NOT_STICKY`：被系统杀掉后**不要**自动重启 —— pi 子进程已经不在了，重启一个空壳没有意义，
 * 下一次用户操作 / 下次打开应用（常驻档在 MainActivity onCreate 重挂）会按需重新拉起。
 */
class PiKeepAliveService : Service() {

    companion object {
        const val EXTRA_TEXT = "text"

        /** 展开区多行内容（BigTextStyle；活动文案 + 模型/思考/会话数/工具数） */
        const val EXTRA_BIG = "big"

        /** 计时起点（毫秒时间戳；通知卡片上渲染成相对时间） */
        const val EXTRA_WHEN = "when"

        /** 是否处于**常驻档**（[PiKeepAlive] 按「有没有常驻来源」下发）：决定前台服务类型 */
        const val EXTRA_RESIDENT = "resident"

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

        /** 「保活被系统停掉」的说明通知（见 [PiKeepAlive.notifyInterrupted]；与前台通知分号，互不覆盖） */
        const val NOTIF_INTERRUPTED = 1002
        private const val TAG = "PientKeepAlive"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: L.common.running
        val big = intent?.getStringExtra(EXTRA_BIG).orEmpty()
        val whenMs = intent?.getLongExtra(EXTRA_WHEN, 0L) ?: 0L
        val resident = intent?.getBooleanExtra(EXTRA_RESIDENT, false) ?: false
        // 点通知回到对应页面（2026-09-16）：终端会话 → 终端页；其余 → 只是把应用调到前台
        val panel = intent?.getStringExtra(EXTRA_PANEL).orEmpty()
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { if (panel.isNotEmpty()) putExtra(EXTRA_PANEL, panel) }
        PiKeepAlive.ensureChannel(this)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.pient_logo)
            .setContentTitle("Pient")
            .setContentText(text)
            // 计时（2026-09-16 用户要求）：标题右侧的相对时间 = 这条活动开始了多久（系统自己走字）
            .setShowWhen(true)
            .setWhen(if (whenMs > 0L) whenMs else System.currentTimeMillis())
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    open,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        // 展开区明细（模型/思考/会话数/工具数）；没有明细就只有活动那一行
        if (big.isNotBlank()) builder.setStyle(NotificationCompat.BigTextStyle().bigText(big))
        val notification = builder.build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, foregroundType(resident))
            } else {
                startForeground(NOTIF_ID, notification)
            }
        }.onFailure {
            // 通知权限被拒 / 类型不允许：如实记一条，别让保活静默失效
            Log.w(TAG, "startForeground 失败（保活未生效）：${it.message}")
        }
        // 「停」指令：**先走完 startForeground 契约再自停**（见 [ACTION_STOP] 的注释 ——
        // 直接 stopService 撤单会让系统抛 ForegroundServiceDidNotStartInTimeException 杀掉应用）。
        // 用 stopSelf(startId) 而不是 stopSelf()：这条「停」之后如果又来了新的启动请求（release 与
        // 下一次 acquire 挨得很近），不能被这条旧指令顺手停掉。
        if (intent?.action == ACTION_STOP) {
            runCatching { @Suppress("DEPRECATION") stopForeground(true) }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /**
     * 前台服务类型：**API 34+ 一律 `specialUse`**（2026-09-16 真机实测后的收口）。
     *
     * 为什么不用 `dataSync`（按需档原来用它）：Android 15 起 dataSync 前台服务在后台
     * **累计 6 小时 / 24 小时**就被系统收尾，且**到点那一下在真机上必崩** —— 即使实现了
     * `onTimeout` 并当场 `stopForeground` + `stopSelf()`，系统仍抛
     * `RemoteServiceException$ForegroundServiceDidNotStopInTimeException` 杀掉应用
     * （两次实测：旧写法 `stopSelf(startId)` 一次、改无条件 `stopSelf()` 后又一次，都是回调后
     * 十几毫秒就被杀）。官方文档给的第一条建议就是「改用替代 API 而不是 dataSync」——我们这个
     * 用途（保住用户自己要跑的 pi 回合 / 终端会话）本来就属于「不匹配现有类型」的长期运行场景，
     * 与 Operit 的 `AIForegroundService`（`dataSync|microphone|specialUse`）同一口径。
     * `specialUse` 没有时限，于是那条崩溃路径从根上消失；[onTimeout] 只作兜底留着。
     */
    private fun foregroundType(resident: Boolean): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            // API 29~33：类型只是申报，取 dataSync（这两档没有 specialUse）
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }

    /**
     * `dataSync` 到点回调（Android 15+，见 [foregroundType]）：系统只给几秒钟自停，
     * 不自停就抛 `RemoteServiceException` **把应用崩掉** —— 长终端会话挂在后台会真撞上
     * （保活按「会话活着」挂）。这里如实收尾：停掉前台服务（保活到此为止），
     * 不做假的续命，用户下次操作 / 下次打开应用会重新挂上。
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "前台服务到点（type=$fgsType，系统时限）：按系统要求自停")
        runCatching { @Suppress("DEPRECATION") stopForeground(true) }
        // **必须无条件 stopSelf()**（2026-09-16 真机崩溃实测）：这里不能用 stopSelf(startId) ——
        // 服务被多次启动过（acquire / 刷新 / 换 key）时这个 startId 不是最后一次启动请求，
        // stopSelf(startId) 是**空操作**，服务停不下来，系统随即抛
        // `RemoteServiceException$ForegroundServiceDidNotStopInTimeException` **崩掉应用**
        // （修复前实测：onTimeout 日志与 1002 说明通知都出来了，17ms 后 FATAL EXCEPTION 杀进程）。
        stopSelf()
        // 如实告知「保活到此为止」（前台 = Toast，后台 = 通知；见 PiKeepAlive.notifyInterrupted）
        PiKeepAlive.notifyInterrupted(this)
    }
}
