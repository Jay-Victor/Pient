import java.io.RandomAccessFile
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
            // 终端层随包二进制（proot / loader / shell 包装脚本）以「原生库」形式分发：
            // Android 10 起应用不得 execve 自己私有目录里的文件（SELinux: untrusted_app ×
            // app_data_file 只给 execute、不给 execute_no_trans，实测 avc 拒绝），只有
            // /data/app 下的 apk_data_file 允许 —— 所以这些文件必须进 native lib 目录。
            // useLegacyPackaging=true 才会在安装时解压到 /data/app/.../lib/<abi>/，
            // 否则只做 APK 内 mmap、磁盘上没有可执行文件。
            useLegacyPackaging = true
            keepDebugSymbols += setOf("**/libpient_*.so") // 这些不是真 .so，禁止 strip
        }
    }
}

// ===== 终端层随包（Ubuntu rootfs + PRoot）=====
// 设计基线（2026-09-14 第三次申明）：**Ubuntu 随 APK 内置、首启解包**（用户不需要下载）；
// node / python 等环境不随包，由用户首次进入时在环境配置页/首启弹窗里下载安装。
// 源 = runtime/cache/rootfs-<abi>/（runtime/scripts/fetch_rootfs.py 拉取；cache 不入库）；
// 目标 = jniLibs（可执行文件：proot + loader + shell 包装脚本）+ assets（rootfs 归档）。
val pientRuntimeAbi = (findProperty("pientRuntimeAbi") as String?) ?: "x86_64"
val pientJniAbi = when (pientRuntimeAbi) {
    "x86_64" -> "x86_64"
    "aarch64", "arm64-v8a" -> "arm64-v8a"
    else -> pientRuntimeAbi
}

// ⚠️ **两套 ABI 名必须映射，别直接拿 Android ABI 去拼缓存目录**：fetch_rootfs.py 的 `--abi` 是
// Debian 口径（`aarch64` / `x86_64`），Android 是 `arm64-v8a` / `x86_64`。曾直接拼
// `rootfs-$pientRuntimeAbi` → arm64 构建去找 `runtime/cache/rootfs-arm64-v8a`（不存在）→
// 任务被静默跳过 → **APK 里塞的是上一次 x86_64 构建留下的归档与 proot**（装到 arm64 真机就是坏的）。
val pientRootfsAbi = when (pientRuntimeAbi) {
    "arm64-v8a", "aarch64" -> "aarch64"
    "x86_64" -> "x86_64"
    else -> pientRuntimeAbi
}
val pientRootfsCacheDir = rootProject.layout.projectDirectory
    .dir("runtime/cache/rootfs-$pientRootfsAbi")
    .asFile
val pientRootfsLibDir = File(pientRootfsCacheDir, "usr/lib")

/** 缓存缺失时的统一报错文案（带上要跑的命令，别让人猜） */
fun pientRootfsCacheError(what: String): String =
    "终端层缓存缺少$what：${pientRootfsCacheDir.absolutePath}\n" +
        "  先拉取：python runtime/scripts/fetch_rootfs.py --abi $pientRootfsAbi"

/** 缓存里的 rootfs 归档（ubuntu-base-*.tar.gz） */
fun pientRootfsArchive(): File? = pientRootfsCacheDir.listFiles()
    ?.firstOrNull { it.name.startsWith("ubuntu-base-") && it.name.endsWith(".tar.gz") }

/** ELF 的 e_machine（小端，偏移 18）：0x3E = x86_64，0xB7 = aarch64；读不到返回 null */
fun pientElfMachine(file: File): String? = runCatching {
    RandomAccessFile(file, "r").use { raf ->
        val head = ByteArray(20)
        raf.readFully(head)
        when (head[18].toInt() and 0xff) {
            0x3e -> "x86_64"
            0xb7 -> "aarch64"
            else -> null
        }
    }
}.getOrNull()

/**
 * 构建期 ABI 校验：这批 ELF 必须全部是 [expected]（`x86_64` / `aarch64`）。
 *
 * **连依赖库一起验**：`usr/lib` 里的 SONAME 别名在切 ABI 后可能残留老架构版本，混进包里要到
 * 设备上才会现形（实测报 `CANNOT LINK EXECUTABLE … is for EM_X86_64 instead of EM_AARCH64`）。
 * 宁可构建失败，也不出「能装、跑不起来」的包。
 */
fun pientAssertAbi(files: List<File>, expected: String, hint: String) {
    val wrong = files.filter { it.isFile }
        .map { it to pientElfMachine(it) }
        .filter { (_, m) -> m != null && m != expected }
    if (wrong.isNotEmpty()) {
        throw GradleException(
            "以下二进制的 ABI 与目标（$expected）不符：\n  " +
                wrong.joinToString("\n  ") { (f, m) -> "${f.name}=$m" } + "\n  " + hint,
        )
    }
}

// ABI 策略（与 Operit 同口径）：**单 ABI 出包**——rootfs 约 30MB，fat APK 会翻倍。
// 默认 x86_64 供模拟器开发，真机/release 必须显式 -PpientRuntimeAbi=arm64-v8a（漏了直接失败）。
if (findProperty("pientRuntimeAbi") == null &&
    gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
) {
    throw GradleException("release 构建必须显式指定 -PpientRuntimeAbi=arm64-v8a（默认 x86_64 只用于模拟器开发）")
}

/**
 * 运行时库的 jniLib 名：jniLibs 只收 lib*.so，而依赖带 SONAME 后缀（libtalloc.so.2）。
 * 原名整段转义（不截断 `.so` 之后的部分）——`libicudata.so.78` 与 `libicudata.so.78.3`
 * 同时存在时，截断命名会撞车。
 */
fun pientJniLibName(original: String): String =
    "libpient_" + original.replace(Regex("[^A-Za-z0-9_]"), "_") + ".so"

// jniLibs 源目录约定是「根目录 + <abi> 子目录」，所以复制到 pientJniLibs/<abi>/
val pientJniRoot = layout.buildDirectory.dir("pientJniLibs")

/**
 * 终端层：PRoot 主程序与它的 ELF loader 也必须以 native lib 形态随包——
 * 两者都是「被 execve 的可执行文件」，放私有目录会被 SELinux 拒（同 Node 的坑）。
 * rootfs 里成千上万个 GNU 程序不进 APK：它们由 loader 以 mmap 方式加载（只要求 execute 权限，
 * SELinux 给），所以 rootfs 可以留在私有目录、首启解包。
 */
val syncPientTerminalBinaries = tasks.register<Copy>("syncPientTerminalBinaries") {
    description = "把 PRoot、loader 与 shell 包装脚本以 lib*.so 形式放入 jniLibs 目录"
    // 缺缓存 = 这个包一定是坏的（终端跑不起来），出声失败而不是静默跳过
    doFirst {
        if (!File(pientRootfsCacheDir, "bin/proot").isFile) {
            throw GradleException(pientRootfsCacheError(" PRoot（bin/proot）"))
        }
        pientAssertAbi(
            listOf(File(pientRootfsCacheDir, "bin/proot"), File(pientRootfsCacheDir, "libexec/proot/loader")),
            if (pientRootfsAbi == "aarch64") "aarch64" else "x86_64",
            "先重拉：python runtime/scripts/fetch_rootfs.py --abi $pientRootfsAbi",
        )
    }
    from(File(pientRootfsCacheDir, "bin/proot")) { rename { "libpient_proot.so" } }
    from(File(pientRootfsCacheDir, "libexec/proot/loader")) { rename { "libpient_proot_loader.so" } }
    // shell 包装脚本也走 native lib 目录：私有目录里的 shebang 脚本同样不能 exec（实测 EACCES）
    from(File(rootProject.projectDir, "runtime/terminal/pient-shell.sh")) {
        rename { "libpient_shell.so" }
    }
    into(pientJniRoot.map { it.dir(pientJniAbi) })
}

/**
 * 终端层 rootfs 归档随包（**首启解包**用）：Ubuntu base 约 28MB 的 tar.gz，放 assets。
 * 归档本身不入库（runtime/cache 已 gitignore），构建期从缓存复制过来 → 装完 APK 即可自解包，
 * 不再依赖开发机用 adb 手动铺。
 */
val syncPientRootfsArchive = tasks.register<Copy>("syncPientRootfsArchive") {
    description = "把 Ubuntu base rootfs 归档放进 assets（首启解包）"
    // 缺归档 = 打出来的包首启必定解不出 Ubuntu，出声失败
    doFirst {
        val archive = pientRootfsArchive()
            ?: throw GradleException(pientRootfsCacheError(" rootfs 归档（ubuntu-base-*.tar.gz）"))
        // 目标目录跨 ABI 共用，切 ABI 时必须先删上一次那份，否则会留下一份 ABI 不符的归档
        val stale = layout.buildDirectory.file("generated/pientAssets/pient-rootfs.tgz").get().asFile
        if (stale.isFile && stale.length() != archive.length()) {
            logger.lifecycle("替换 rootfs 归档：${stale.length()} → ${archive.length()} 字节（${archive.name}）")
            stale.delete()
        }
    }
    from(pientRootfsCacheDir) {
        include("ubuntu-base-*.tar.gz")
        // 后缀不能是 .gz：aapt 会把 assets 里的 *.gz **自动解压并去掉后缀**（实测 30MB 的
        // tar.gz 变成 84MB 的 assets/pient-rootfs.tar，白胖 50MB）。改成 .tgz 就不触发。
        rename { "pient-rootfs.tgz" }
    }
    into(layout.buildDirectory.dir("generated/pientAssets"))
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
        val libFiles = (pientRootfsLibDir.listFiles() ?: emptyArray()).toList()
        val lines = libFiles
            .filter { it.isFile && (it.name.endsWith(".so") || it.name.contains(".so.")) }
            .sortedBy { it.name }
            .map { "${it.name} ${pientJniLibName(it.name)}" }
        f.writeText(lines.joinToString("\n") + "\n")
        println("pient 运行时库映射表：${lines.size} 项 → ${f.absolutePath}")
    }
}

/** 终端层的依赖库（PRoot 的 libtalloc / libandroid-shmem）随包 */
val syncPientRuntimeLibs = tasks.register<Copy>("syncPientRuntimeLibs") {
    description = "把 PRoot 的动态依赖以 libpient_*.so 形式放入 jniLibs 目录"
    // 依赖库也逐个验 ABI：切 ABI 后残留的老架构库会混进包 → 设备上 proot 起不来
    doFirst {
        if (!pientRootfsLibDir.isDirectory) {
            throw GradleException(pientRootfsCacheError(" 依赖库目录（usr/lib）"))
        }
        pientAssertAbi(
            (pientRootfsLibDir.listFiles() ?: emptyArray()).filter {
                it.name.endsWith(".so") || it.name.contains(".so.")
            },
            if (pientJniAbi == "arm64-v8a") "aarch64" else "x86_64",
            "usr/lib 里的 SONAME 别名是「已存在就跳过」，切 ABI 时残留的老架构库会混进包 —— " +
                "删掉 runtime/cache/rootfs-*/usr/lib 后重跑 fetch_rootfs.py --abi $pientRootfsAbi",
        )
    }
    from(pientRootfsLibDir) {
        include("*.so", "*.so.*")
        rename { name -> pientJniLibName(name) }
    }
    into(pientJniRoot.map { it.dir(pientJniAbi) })
}

/**
 * pi 官方包**随包预置**（2026-09-14 用户拍板，按 Operit「工具包随 App」的口径）：
 * pi 的工具（read/write/edit/bash/grep/find/ls）就在 pi 包里 —— 所以「Pient 里有没有 pi 的工具」
 * 等价于「assets 里有没有这个归档」。归档顶层无 `package/` 前缀（设备侧只用 toybox tar，
 * 没有 --strip-components，见 runtime/scripts/pack_pi_archive.py）。
 */
val syncPientPiArchive = tasks.register<Exec>("syncPientPiArchive") {
    description = "把 pi 官方 npm 包打成 assets 归档（随 APK 预置，首启解进 Ubuntu 的 npm 全局位置）"
    val outFile = layout.buildDirectory.file("generated/pientAssets/pient-pi.tgz").get().asFile
    outputs.file(outFile)
    commandLine(
        "python",
        rootProject.layout.projectDirectory.file("runtime/scripts/pack_pi_archive.py").asFile.absolutePath,
        "--out", outFile.absolutePath,
    )
}

android.sourceSets.getByName("main").jniLibs.srcDir(pientJniRoot)
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/pientAssets"))
tasks.named("preBuild") {
    dependsOn(
        syncPientTerminalBinaries, syncPientRootfsArchive,
        syncPientRuntimeLibs, writePientRuntimeLibsManifest,
        syncPientPiArchive,
    )
    // 切 ABI 时清掉上一次构建留在 jniLibs 源目录里的另一套 ABI（jniLibs 源是整个
    // build/pientJniLibs，不清就会 fat 出包：实测 arm64 包里混进了 x86_64 的 proot/loader）
    doFirst {
        pientJniRoot.get().asFile.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name != pientJniAbi) {
                logger.lifecycle("清掉旧 ABI 的 jniLibs 目录：${dir.name}（目标 $pientJniAbi）")
                dir.deleteRecursively()
            }
        }
    }
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
    // aidl = `moe.shizuku.server.IShizukuService` / `IRemoteProcess` 的接口定义（2026-09-15 加）：
    // Android shell 通道要在 Java 侧调 `IShizukuService.newProcess(...)` 以 shell 身份起进程 ——
    // 那两个类型就在这个 artifact 里（api 只是 Java 友好封装，不含 newProcess）。
    implementation("dev.rikka.shizuku:aidl:13.1.5")
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
