package com.pient.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pient.app.R

/**
 * 字体分工（设计计划 1.1）：
 * - UI 文本 = 系统无衬线（思源黑体系），正文行高 1.5
 * - 代码 / 终端 / 路径 = JetBrains Mono（内置资源）
 * 两者严禁混用。
 */
val MonoFont: FontFamily = FontFamily(Font(R.font.jetbrains_mono))

/**
 * 等宽字体**关闭连字**：JetBrains Mono 自带 liga/calt，会把 `!=` 渲染成 ≠、`->` 渲染成 →、
 * `==` 渲染成长等号、`<=`/`>=` 渲染成 ≤/≥ —— 代码 / 源码编辑区必须原样显示字符本身
 * （用户 2026-09-11：「这又不是 markdown 文件，原样输入就行，不要有无谓的渲染」）。
 * 用法：代码文本样式与工具栏符号键都带上本设置；UI 正文字体不涉及。
 * 语法 = CSS font-feature-settings（Android `Painting.fontFeatureSettings` 同款解析）。
 */
const val MonoNoLigatures: String = "'liga' 0, 'calt' 0"

/** 代码/终端文本样式 */
val MonoTextStyle = TextStyle(
    fontFamily = MonoFont,
    fontSize = 13.sp,
    lineHeight = 20.sp,
)

val PientTypography = Typography(
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp, // 1.5
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp, // 1.5
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp, // 1.5
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
)

/** 按比例缩放字号与行高（字体设置全局字号：factor = 设置值 / 14sp 基准） */
private fun TextStyle.scaled(factor: Float): TextStyle {
    // TextStyle.copy 的 lineHeight 参数为非空 TextUnit（默认取自身），null 时需绕开
    val base = copy(fontSize = fontSize * factor)
    val lh = lineHeight?.times(factor)
    return if (lh != null) base.copy(lineHeight = lh) else base
}

/** 全局缩放排版（PientTypography 定义的八种样式 + Tab 用的 titleSmall；代码/终端 MonoTextStyle 不参与缩放） */
fun Typography.scaled(factor: Float): Typography = copy(
    bodyLarge = bodyLarge.scaled(factor),
    bodyMedium = bodyMedium.scaled(factor),
    bodySmall = bodySmall.scaled(factor),
    labelLarge = labelLarge.scaled(factor),
    labelMedium = labelMedium.scaled(factor),
    labelSmall = labelSmall.scaled(factor),
    titleSmall = titleSmall.scaled(factor),
    titleMedium = titleMedium.scaled(factor),
    titleLarge = titleLarge.scaled(factor),
)

/** 全局字体套用（字体设置字体样式；显式指定 MonoFont 的代码/终端文本不受影响） */
fun Typography.withFontFamily(family: FontFamily): Typography = copy(
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
)
