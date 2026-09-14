package com.pient.app

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pient.app.data.SettingsStore
import com.pient.app.data.ThemeMode
import com.pient.app.ui.theme.preloadBackgroundImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 恢复持久化主题（必须在 setContent 前，否则首帧用默认暗色渲染再闪切）
        SettingsStore.load(this)
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
