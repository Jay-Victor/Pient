package com.pient.app.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.pient.app.data.BuiltinFontKind
import com.pient.app.data.BuiltinFontOption
import com.pient.app.data.BuiltinFonts
import com.pient.app.data.FontSource
import com.pient.app.data.SettingsStore
import java.io.File

/**
 * 字体设置全局解析（字体设置标签与 PientTheme 共用）。
 * 当前设置 → FontFamily：内置 → 系统字体族/系统字体文件/资源/assets；
 * 自定义 → filesDir/fonts 导入文件；任何一层解析失败逐级回退，最终默认字体。
 */
fun resolveFontFamily(context: Context): FontFamily = when (SettingsStore.fontSource) {
    FontSource.BUILTIN ->
        BuiltinFonts.firstOrNull { it.name == SettingsStore.builtinFontName }
            ?.let { builtinFamily(context, it) }
            ?: FontFamily.Default
    FontSource.CUSTOM ->
        SettingsStore.customFontPath?.let { name ->
            runCatching { Typeface.createFromFile(File(context.filesDir, "fonts/$name")) }
                .getOrNull()?.let { FontFamily(it) }
        } ?: FontFamily.Default
}

/** 内置字体选项 → FontFamily（解析失败回退家族名，再失败返回 null 由调用方兜底） */
private fun builtinFamily(context: Context, option: BuiltinFontOption): FontFamily? {
    val fallback = runCatching { Typeface.create(option.familyName, Typeface.NORMAL) }.getOrNull()
    return when (option.kind) {
        BuiltinFontKind.SYSTEM_FAMILY -> fallback?.let { FontFamily(it) }
        BuiltinFontKind.SYSTEM_FILE ->
            runCatching { Typeface.createFromFile(option.filePath) }.getOrNull()
                ?.let { FontFamily(it) } ?: fallback?.let { FontFamily(it) }
        BuiltinFontKind.RES ->
            runCatching { FontFamily(Font(option.resId)) }.getOrNull() ?: fallback?.let { FontFamily(it) }
        BuiltinFontKind.ASSET ->
            runCatching { Typeface.createFromAsset(context.assets, option.assetPath) }.getOrNull()
                ?.let { FontFamily(it) } ?: fallback?.let { FontFamily(it) }
    }
}
