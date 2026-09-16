package com.pient.app

import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pient.app.data.SettingsStore
import com.pient.app.data.ThemeMode
import com.pient.app.runtime.PiKeepAlive
import com.pient.app.runtime.PiKeepAliveService
import com.pient.app.runtime.PiRuntime
import com.pient.app.ui.theme.preloadBackgroundImage

class MainActivity : ComponentActivity() {

    /** 前后台可见性（2026-09-16）：「保活被系统停掉」这类说明在前台走 Toast、后台走通知 */
    override fun onStart() {
        super.onStart()
        PientRuntime.appVisible = true
    }

    override fun onStop() {
        PientRuntime.appVisible = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyPanelIntent(intent)
    }

    /**
     * 前台服务通知点开回终端页（2026-09-16）：通知带 `pient_panel=terminal` 进来。
     * 这里只把请求**存进 PientRuntime.pendingPanel**（这时 ChatState 还没建），
     * 由 PientApp 在就绪后消费一次；extra 同步移除，避免旋屏/重建时反复跳终端页。
     */
    private fun applyPanelIntent(intent: Intent?) {
        val panel = intent?.getStringExtra(PiKeepAliveService.EXTRA_PANEL) ?: return
        intent.removeExtra(PiKeepAliveService.EXTRA_PANEL)
        PientRuntime.pendingPanel = panel
        android.util.Log.i("PientMain", "收到面板请求：$panel")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyPanelIntent(intent)

        // 恢复持久化主题（必须在 setContent 前，否则首帧用默认暗色渲染再闪切）
        SettingsStore.load(this)
        // 后台常驻通知（行为设置，2026-09-16）：开着就在每次启动时把常驻档重新挂上
        //（进程被杀 / 换包后常驻通知要自己回来；关着则什么都不做，维持按需前台化）。
        if (SettingsStore.residentNotification) {
            PiKeepAlive.setResident(this, true)
        }
        // 终端环境（Ubuntu 24.04 rootfs，随包）**首启解包**：后台线程铺到 files/pient-rt/rootfs，
        // 不阻塞首帧；rootfs 已就绪时这是空操作（判据含 ELF 架构，换包/换架构会自动重解）。
        PiRuntime.ensureRootfsAsync(this)
        // 预置的 pi 包解进 Ubuntu（rootfs 未就绪时立即返回，由解包完成后的链式调用接手）
        PiRuntime.ensurePiAsync(this)
        // Android shell 回桥（2026-09-15，要求 5）：guest 里的 pi 工具要执行 Android 命令时，
        // 经 loopback 回到应用执行（Shizuku 的 binder 只在 Java 侧可达）。起在应用启动，
        // 端点文件每次请求都会刷新（档位/授权变化后无需重启 pi）。
        com.pient.app.runtime.ExecBridge.ensureStarted(this)
        // 背景图片预解码（与 Compose 启动重叠）：聊天页首帧即可同步命中缓存，
        // 不再出现「先空背景、等 IO 解码完才出图」的一瞬（2026-09-12）
        preloadBackgroundImage(this)

        // 首帧窗口底色跟随持久化主题：亮色用户冷启动不再先闪暗色底
        val dark = when (SettingsStore.themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM ->
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
        window.setBackgroundDrawable(
            ColorDrawable(if (dark) 0xFF0D1117.toInt() else 0xFFFFFFFF.toInt()),
        )

        setContent {
            PientApp()
        }
    }
}
