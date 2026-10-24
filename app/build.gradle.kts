import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.ncmusic.lite"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ncmusic.lite"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "1.1.16"

        // 发行包只保留真机 ABI（去掉模拟器用的 x86 / x86_64 原生库）
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        // 只保留应用支持的语言（系统卡片 / AndroidX 自带的多语言字符串会一起裁掉）
        @Suppress("UNCHECKED_CAST")
        (resourceConfigurations as MutableSet<String>)
            .addAll(listOf("zh", "zh-rTW", "zh-rHK", "zh-rMO", "en", "ja", "ko", "es", "fr", "de", "ru"))
    }

    signingConfigs {
        create("release") {
            // 个人使用：复用本机调试签名，保证能直接覆盖安装之前的 APK（登录态、设置都保留）。
            // 换新签名会导致必须卸载重装。
            storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    androidResources {
        // 资源裁剪开关由 defaultConfig.resourceConfigurations 控制
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin/**",
                // 注意：不要排除 okhttp3/internal/publicsuffix/publicsuffixes.gz。
                // OkHttp 解析响应 Set-Cookie 时会调用 PublicSuffixDatabase（发送私信的响应带 Set-Cookie），
                // 缺少该资源会抛 IllegalStateException 并导致 App 闪退。
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // 功能模块
    implementation(project(":feature:home"))
    implementation(project(":feature:mine"))
    implementation(project(":feature:login"))
    implementation(project(":feature:playlist"))
    implementation(project(":feature:player"))
    implementation(project(":feature:comment"))
    implementation(project(":feature:mv"))
    implementation(project(":feature:people"))
    implementation(project(":feature:settings"))

    // 核心模块
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:player"))
    implementation(project(":designsystem"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
