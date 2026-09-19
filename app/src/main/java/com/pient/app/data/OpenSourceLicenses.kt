package com.pient.app.data

/**
 * 「开源许可声明」页的数据表：随应用一同分发的第三方组件。
 *
 * 用途：`ui/settings/LicensesScreen.kt` 按 [Section] 分组渲染成卡片，每行点开 [Entry.url]。
 * 单列一张表的理由：许可是「分发方必须随附」的事实，加依赖时只改这一处。
 * 组件名与许可名保持上游写法（英文，SPDX 口径）；只有「多许可/数据」这类要跟界面语言走，
 * 用 [LicenseLabel] 的另外两个取值表达，渲染时映射到语言包（见 LicensesScreen）。
 */
object OpenSourceLicenses {

    /** 页面上的一张卡片 */
    enum class Section { RUNTIME, LIBRARY, FONT, DATA }

    /** 一行的「许可」列 */
    sealed interface LicenseLabel {
        /** SPDX 口径的字面名（`MIT` / `GPL-2.0` / `BSD-3-Clause` …）——不随界面语言变化 */
        data class Spdx(val name: String) : LicenseLabel

        /** 内含多种许可、逐项列在各自目录里的合集（发行镜像这类） */
        data object Various : LicenseLabel

        /** 数据集而非代码（随包的数据表） */
        data object OpenData : LicenseLabel
    }

    data class Entry(
        val name: String,
        val license: LicenseLabel,
        val url: String,
        val section: Section,
    )

    /**
     * 随 APK 分发的部分：这些二进制与归档会进到用户设备上，发行时按各自许可随附声明与来源。
     * `libtalloc` / `libandroid-shmem` 是 PRoot 的依赖库，随包以 `libpient_*.so` 形态分发。
     */
    private val runtime = listOf(
        Entry("@earendil-works/pi-coding-agent", LicenseLabel.Spdx("MIT"), "https://github.com/earendil-works/pi", Section.RUNTIME),
        Entry("PRoot", LicenseLabel.Spdx("GPL-2.0"), "https://github.com/termux/proot", Section.RUNTIME),
        Entry("libtalloc", LicenseLabel.Spdx("GPL-3.0"), "https://talloc.samba.org/talloc/doc/html/index.html", Section.RUNTIME),
        Entry("libandroid-shmem", LicenseLabel.Spdx("BSD-3-Clause"), "https://github.com/termux/libandroid-shmem", Section.RUNTIME),
        Entry("Ubuntu 24.04 LTS base", LicenseLabel.Various, "https://ubuntu.com/", Section.RUNTIME),
    )

    /** 构建期依赖：随 APK 一起编译分发 */
    private val libraries = listOf(
        Entry("AndroidX · Jetpack Compose", LicenseLabel.Spdx("Apache-2.0"), "https://developer.android.com/jetpack/androidx", Section.LIBRARY),
        Entry("Kotlin · kotlinx.coroutines", LicenseLabel.Spdx("Apache-2.0"), "https://kotlinlang.org/", Section.LIBRARY),
        Entry("OkHttp", LicenseLabel.Spdx("Apache-2.0"), "https://square.github.io/okhttp/", Section.LIBRARY),
        Entry("AndroidX Media3 / ExoPlayer", LicenseLabel.Spdx("Apache-2.0"), "https://github.com/androidx/media", Section.LIBRARY),
        Entry("Apache POI", LicenseLabel.Spdx("Apache-2.0"), "https://poi.apache.org/", Section.LIBRARY),
        Entry("Shizuku", LicenseLabel.Spdx("MIT"), "https://github.com/RikkaApps/Shizuku", Section.LIBRARY),
        Entry("Android-Image-Cropper", LicenseLabel.Spdx("Apache-2.0"), "https://github.com/CanHub/Android-Image-Cropper", Section.LIBRARY),
        Entry("JLaTeXMath-Android", LicenseLabel.Spdx("GPL-2.0-or-later (Classpath exception)"), "https://github.com/noties/jlatexmath-android", Section.LIBRARY),
        Entry("Backdrop (AndroidLiquidGlass)", LicenseLabel.Spdx("Apache-2.0"), "https://github.com/Kyant0/AndroidLiquidGlass", Section.LIBRARY),
        Entry("Liquid", LicenseLabel.Spdx("Apache-2.0"), "https://github.com/FletchMcKee/liquid", Section.LIBRARY),
    )

    /** 随包字体（两门都是 SIL Open Font License 1.1） */
    private val fonts = listOf(
        Entry("LXGW WenKai", LicenseLabel.Spdx("OFL-1.1"), "https://github.com/lxgw/LxgwWenKai", Section.FONT),
        Entry("JetBrains Mono", LicenseLabel.Spdx("OFL-1.1"), "https://github.com/JetBrains/JetBrainsMono", Section.FONT),
    )

    /** 随包数据表的来源：品牌图标（服务商徽标）与模型定价 */
    private val data = listOf(
        Entry("simple-icons", LicenseLabel.Spdx("CC0-1.0"), "https://github.com/simple-icons/simple-icons", Section.DATA),
        Entry("models.dev", LicenseLabel.OpenData, "https://models.dev", Section.DATA),
    )

    /** 全表（按 [Section] 的声明顺序分组渲染） */
    val entries: List<Entry> = runtime + libraries + fonts + data

    /** GPL-3.0 全文随包（assets/licenses/），供许可页末尾一行打开展示 */
    const val GPL3_ASSET = "licenses/GPL-3.0.txt"
}
