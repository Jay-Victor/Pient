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
