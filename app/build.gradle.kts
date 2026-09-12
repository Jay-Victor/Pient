plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pient.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pient.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs {
            // pi 宿主运行时（内嵌 Node）以「原生库」形式随包分发：Android 10 起
            // 应用不得 execve 自己私有目录里的文件（SELinux: untrusted_app × app_data_file
            // 只给 execute 不给 execute_no_trans，实测 avc 拒绝），但 /data/app 下的
            // apk_data_file 允许 execve —— 因此二进制必须放进 native lib 目录。
            // useLegacyPackaging=true 才会在安装时解压到 /data/app/.../lib/<abi>/，
            // 否则只做 APK 内 mmap、磁盘上没有可执行文件。
            useLegacyPackaging = true
            keepDebugSymbols += setOf("**/libpient_*.so") // 这些不是真 .so，禁止 strip
        }
    }
}

// ===== pi 宿主运行时二进制（工具层）=====
// 源：runtime/cache/usr/bin（由 runtime/scripts/fetch_runtime.py 拉取，见 runtime/README.md）
// 目标：build/pientJniLibs/<abi>/libpient_{node,rg,fd}.so → 作为 jniLibs 源打进 APK
val pientRuntimeAbi = (findProperty("pientRuntimeAbi") as String?) ?: "x86_64"
val pientJniAbi = when (pientRuntimeAbi) {
    "x86_64" -> "x86_64"
    "aarch64", "arm64-v8a" -> "arm64-v8a"
    else -> pientRuntimeAbi
}
val pientRuntimeCacheDir = rootProject.layout.projectDirectory
    .dir("runtime/cache/usr/bin")
    .asFile
val pientRuntimeLibDir = rootProject.layout.projectDirectory
    .dir("runtime/cache/usr/lib")
    .asFile
// 终端层（Ubuntu rootfs + PRoot）缓存，见 runtime/scripts/fetch_rootfs.py
val pientRootfsCacheDir = rootProject.layout.projectDirectory
    .dir("runtime/cache/rootfs-$pientRuntimeAbi")
    .asFile
val pientRootfsLibDir = File(pientRootfsCacheDir, "usr/lib")

// ABI 策略（2026-09-12 拍板，与 Operit 同口径）：**单 ABI 出包**——运行时 260MB，
// fat APK 会翻倍；ABI 切分（AAB）只在走 Play 分发时才有意义。默认 x86_64 供模拟器开发，
// 真机/release 必须显式 -PpientRuntimeAbi=arm64-v8a（漏了直接构建失败，别出无声的错包）。
if (findProperty("pientRuntimeAbi") == null &&
    gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
) {
    throw GradleException("release 构建必须显式指定 -PpientRuntimeAbi=arm64-v8a（默认 x86_64 只用于模拟器开发）")
}

/**
 * 运行时库的 jniLib 名：jniLibs 只收 lib*.so，而依赖带 SONAME 后缀（libicuuc.so.78 / libz.so.1）。
 * 原名整段转义（不截断 `.so` 之后的部分）——`libicudata.so.78` 与 `libicudata.so.78.3` 同时存在，
 * 截断命名会撞车（后者覆盖前者，映射表里两条指向同一个文件）。
 */
fun pientJniLibName(original: String): String =
    "libpient_" + original.replace(Regex("[^A-Za-z0-9_]"), "_") + ".so"

// jniLibs 源目录约定是「根目录 + <abi> 子目录」，所以复制到 pientJniLibs/<abi>/
val pientJniRoot = layout.buildDirectory.dir("pientJniLibs")

val syncPientRuntime = tasks.register<Copy>("syncPientRuntimeBinaries") {
    description = "把 pi 宿主运行时二进制以 lib*.so 形式放入 jniLibs 目录"
    onlyIf { pientRuntimeCacheDir.isDirectory }
    from(pientRuntimeCacheDir) {
        include("node", "rg", "fd")
        rename { name -> "libpient_$name.so" }
    }
    into(pientJniRoot.map { it.dir(pientJniAbi) })
}

/**
 * 终端层：PRoot 主程序与它的 ELF loader 也必须以 native lib 形态随包——
 * 两者都是「被 execve 的可执行文件」，放私有目录会被 SELinux 拒（同 Node 的坑）。
 * rootfs 里成千上万个 GNU 程序不进 APK：它们由 loader 以 mmap 方式加载（只要求 execute 权限，
 * SELinux 给），所以 rootfs 可以留在私有目录、首启解包。
 */
val syncPientTerminalBinaries = tasks.register<Copy>("syncPientTerminalBinaries") {
    description = "把 PRoot 与 loader 以 lib*.so 形式放入 jniLibs 目录"
    onlyIf { File(pientRootfsCacheDir, "bin/proot").isFile }
    from(File(pientRootfsCacheDir, "bin/proot")) { rename { "libpient_proot.so" } }
    from(File(pientRootfsCacheDir, "libexec/proot/loader")) { rename { "libpient_proot_loader.so" } }
    // pi 的 bash 工具要一个「shellPath」：shebang 脚本在私有目录同样不能 exec（实测 EACCES），
    // 所以包装脚本也走 native lib 目录，构建期从 runtime/terminal/pient-shell.sh 复制过来。
    from(File(rootProject.projectDir, "runtime/terminal/pient-shell.sh")) {
        rename { "libpient_shell.so" }
    }
    into(pientJniRoot.map { it.dir(pientJniAbi) })
}

/**
 * 终端层 rootfs 归档随包（首启解包用）：Ubuntu base 是 ~28MB 的 tar.gz，放 assets。
 * 归档本身不入库（runtime/cache 已 gitignore），构建期从缓存复制过来 → 装完 APK 即可自解包，
 * 不再依赖开发机手动铺（此前 files/pient-rt/rootfs 是用 run-as 手工解的）。
 */
val syncPientRootfsArchive = tasks.register<Copy>("syncPientRootfsArchive") {
    description = "把 Ubuntu base rootfs 归档放进 assets（首启解包）"
    onlyIf {
        pientRootfsCacheDir.listFiles()?.any { it.name.startsWith("ubuntu-base-") && it.name.endsWith(".tar.gz") } == true
    }
    from(pientRootfsCacheDir) {
        include("ubuntu-base-*.tar.gz")
        // 后缀不能是 .gz：aapt 会把 assets 里的 *.gz **自动解压并去掉后缀**（实测 30MB 的
        // tar.gz 变成 84MB 的 assets/pient-rootfs.tar，白胖 50MB）。改成 .tgz 就不触发。
        rename { "pient-rootfs.tgz" }
    }
    into(layout.buildDirectory.dir("generated/pientAssets"))
}

// 打包形态 = 混合（2026-09-12 拍板）：**随包带 node + 核心库**（可 execve 的二进制必须在
// native lib 目录，被加载的依赖也一并随包 → 离线可起宿主），**pi npm 包按需下载**（可独立升级）。
val syncPientRuntimeLibs = tasks.register<Copy>("syncPientRuntimeLibs") {
    description = "把 Node 动态依赖以 libpient_*.so 形式放入 jniLibs 目录"
    onlyIf { pientRuntimeLibDir.isDirectory }
    from(pientRuntimeLibDir) {
        include("*.so", "*.so.*")
        rename { name -> pientJniLibName(name) }
    }
    // 终端层的依赖库（PRoot 的 libtalloc / libandroid-shmem）同走这条路
    from(pientRootfsLibDir) {
        include("*.so", "*.so.*")
        rename { name -> pientJniLibName(name) }
    }
    into(pientJniRoot.map { it.dir(pientJniAbi) })
}

// SONAME → jniLib 名映射表（随 APK assets 分发，运行时按它建软链，见 PiRuntime.ensureLinks）
val pientRuntimeLibsManifest =
    layout.buildDirectory.file("generated/pientAssets/pient_runtime_libs.txt")

val writePientRuntimeLibsManifest = tasks.register("writePientRuntimeLibsManifest") {
    description = "生成 SONAME → jniLib 名映射表"
    outputs.file(pientRuntimeLibsManifest)
    doLast {
        val f = pientRuntimeLibsManifest.get().asFile
        f.parentFile.mkdirs()
        val libFiles = listOf(pientRuntimeLibDir, pientRootfsLibDir)
            .flatMap { dir -> (dir.listFiles() ?: emptyArray()).toList() }
        val lines = if (libFiles.isNotEmpty()) {
            libFiles
                .filter { it.isFile && (it.name.endsWith(".so") || it.name.contains(".so.")) }
                .sortedBy { it.name }
                .map { "${it.name} ${pientJniLibName(it.name)}" }
        } else emptyList()
        f.writeText(lines.joinToString("\n") + "\n")
        println("pient 运行时库映射表：${lines.size} 项 → ${f.absolutePath}")
    }
}

android.sourceSets.getByName("main").jniLibs.srcDir(pientJniRoot)
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/pientAssets"))
tasks.named("preBuild") {
    dependsOn(syncPientRuntime, syncPientRuntimeLibs, syncPientTerminalBinaries,
        syncPientRootfsArchive, writePientRuntimeLibsManifest)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.01.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.media3:media3-exoplayer:1.7.1")
    implementation("androidx.media3:media3-ui:1.7.1")
    implementation("androidx.media3:media3-effect:1.7.1")
    implementation("com.vanniktech:android-image-cropper:4.5.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // 权限体系（2026-09-12，开发计划 6.1 三级权限）：Shizuku 官方 SDK（ADB 级调试通道，与 Operit 同版本）
    //   api      — 状态/授权 API（Shizuku.pingBinder / checkSelfPermission / requestPermission）
    //   provider — binder 接收入口（AndroidManifest 里需声明 rikka.shizuku.ShizukuProvider，见 manifest）
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // 数学公式渲染（LaTeX → 位图，Operit 同款依赖：jlatexmath 的 Android 移植，字体资源随 AAR 打包）
    implementation("ru.noties:jlatexmath-android:0.2.0")
    // 输入框材质（磨砂玻璃 / 液态玻璃，2026-09-12，与 Mdcito 同款依赖）：
    //   com.kyant.backdrop  — 背景采样 + 高斯模糊 + 边缘高光/投影（磨砂玻璃，enableLens 时含透镜折射）
    //   io.github.fletchmckee.liquid — 水玻璃流体折射/色散（液态玻璃）
    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation("io.github.fletchmckee.liquid:liquid:1.1.1")
    // 文档预览（Operit 同款：.doc = poi-scratchpad HWPF、.xls/.xlsx = poi/poi-ooxml WorkbookFactory）
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("org.apache.poi:poi-scratchpad:5.2.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
