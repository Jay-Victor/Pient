package com.pient.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.scilab.forge.jlatexmath.Atom
import org.scilab.forge.jlatexmath.ColorAtom
import org.scilab.forge.jlatexmath.EmptyAtom
import org.scilab.forge.jlatexmath.MacroInfo
import org.scilab.forge.jlatexmath.RowAtom
import org.scilab.forge.jlatexmath.SpaceAtom
import org.scilab.forge.jlatexmath.SymbolAtom
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXParser
import org.scilab.forge.jlatexmath.TypedAtom
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * LaTeX 公式渲染（Android 蓝本 = Operit `ui/common/displays/LatexCache.kt` + `JLatexMathCompatibility.kt`）：
 * jlatexmath-android（`ru.noties:jlatexmath-android:0.2.0`）把公式画成 Drawable → 这里再落到 ARGB 位图，
 * 交给 Compose 按位图显示（Pient 全原生渲染，不走 WebView/KaTeX —— pi-web 的 KaTeX 只在 Web 端可用）。
 *
 * 渲染入口按 (公式, 字号 px, 文字色) 做 LRU 缓存：同一段落里重复出现的公式、以及重组/滚动引起的重复调用
 * 都不会重新解析；失败结果同样入缓存（避免每次重组都重试一段渲染不出来的公式）。
 * 线程安全（@Synchronized）：jlatexmath 的字体表在首次渲染时懒加载，不能并发进入。
 */
internal class LatexImage(val bitmap: ImageBitmap, val widthPx: Int, val heightPx: Int)

internal object LatexRenderer {
    /** 超长公式不渲染（防呆；渲染失败会退化为源码文本显示） */
    private const val MaxFormulaChars = 1000

    /** 位图缓存上限（KB 计，约 4MB —— 与 Operit LatexCache 的 1/8 可用内存同量级） */
    private const val MaxCacheKb = 4096

    private class Entry(val image: LatexImage?)

    private val cache = object : LruCache<String, Entry>(MaxCacheKb) {
        override fun sizeOf(key: String, value: Entry): Int {
            val w = value.image?.widthPx ?: 1
            val h = value.image?.heightPx ?: 1
            return (w * h * 4 / 1024).coerceAtLeast(1)
        }
    }

    /** 渲染公式；返回 null = 该公式无法渲染（调用方退化为显示源码） */
    @Synchronized
    fun image(latex: String, textSizePx: Float, colorArgb: Int): LatexImage? {
        if (latex.isBlank() || latex.length > MaxFormulaChars) return null
        val key = "$textSizePx|$colorArgb|$latex"
        cache.get(key)?.let { return it.image }
        val rendered = runCatching { render(latex, textSizePx, colorArgb) }.getOrNull()
        cache.put(key, Entry(rendered))
        return rendered
    }

    private fun render(latex: String, textSizePx: Float, colorArgb: Int): LatexImage? {
        JLatexMathCompatibility.ensureRegistered()
        val drawable = JLatexMathDrawable.builder(latex)
            .textSize(textSizePx)
            .color(colorArgb)
            .padding(2)
            .background(0)
            .align(JLatexMathDrawable.ALIGN_LEFT)
            .build()
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return LatexImage(bitmap.asImageBitmap(), width, height)
    }
}

// ───────────────────────────── jlatexmath 缺失命令的兼容宏（Operit 同款）─────────────────────────────

/**
 * jlatexmath 相对 KaTeX 缺几条命令，模型输出里却常见 —— 按 Operit 的做法注册兼容宏：
 * `\color{…}{…}` 的单参数多参数两种写法、`\oiint` / `\oiiint` 闭合重积分、以及对应的 Unicode 字符映射。
 * 注册只做一次（命令表是全局静态表，重复注册会抛异常）。
 */
private object JLatexMathCompatibility {
    @Volatile
    private var registered = false

    fun ensureRegistered() {
        if (registered) return
        synchronized(this) {
            if (registered) return
            registerCommandIfMissing("color", "color_macro")
            registerCommandIfMissing("oiint", "oiint_macro")
            registerCommandIfMissing("oiiint", "oiiint_macro")
            registerSymbolFormulaIfMissing('\u222F', "\\oiint")
            registerSymbolFormulaIfMissing('\u2230', "\\oiiint")
            registered = true
        }
    }

    private fun registerCommandIfMissing(commandName: String, methodName: String) {
        if (MacroInfo.Commands.containsKey(commandName)) return
        MacroInfo.Commands[commandName] = MacroInfo(JLatexMathCompatMacros::class.java.name, methodName, 0f)
    }

    private fun registerSymbolFormulaIfMissing(char: Char, formula: String) {
        if (TeXFormula.symbolFormulaMappings[char.code] != null) return
        TeXFormula.symbolFormulaMappings[char.code] = formula
    }
}

@Suppress("UNUSED_PARAMETER")
private class JLatexMathCompatMacros {
    /** `\color{red} abc` / `\color{red}{abc}`：jlatexmath 原生不支持该形态 */
    fun color_macro(parser: TeXParser, args: Array<String>): Atom? {
        val color = ColorAtom.getColor(args[1])
        val remaining = parser.getStringFromCurrentPos()
        val nextContentIndex = remaining.indexOfFirst { !it.isWhitespace() }
        if (nextContentIndex == -1) return null
        return if (remaining[nextContentIndex] == '{') {
            ColorAtom(parser.getArgument(), null, color)
        } else {
            val atom = TeXFormula(remaining).root ?: EmptyAtom()
            runCatching {
                val positionField = TeXParser::class.java.getDeclaredField("pos").apply { isAccessible = true }
                positionField.setInt(parser, parser.getPos() + remaining.length)
            }
            ColorAtom(atom, null, color)
        }
    }

    fun oiint_macro(parser: TeXParser, args: Array<String>): Atom = closedIntegralAtom(extraIntegrals = 1)

    fun oiiint_macro(parser: TeXParser, args: Array<String>): Atom = closedIntegralAtom(extraIntegrals = 2)

    private fun closedIntegralAtom(extraIntegrals: Int): Atom {
        val contour = SymbolAtom.get("oint").clone().apply { type_limits = TeXConstants.SCRIPT_NOLIMITS }
        val open = SymbolAtom.get("int").clone().apply { type_limits = TeXConstants.SCRIPT_NOLIMITS }
        val row = RowAtom(contour)
        repeat(extraIntegrals) {
            row.add(SpaceAtom(TeXConstants.UNIT_MU, -6f, 0f, 0f))
            row.add(open.clone())
        }
        row.lookAtLastAtom = true
        return TypedAtom(TeXConstants.TYPE_BIG_OPERATOR, TeXConstants.TYPE_BIG_OPERATOR, row)
    }
}
