plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    kotlin("kapt")
}

android {
    namespace = "com.limette.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.limette.tv"
        minSdk = 21
        targetSdk = 34
        // Limette TV 起始版本（与 LomenTV 包名不同，视为全新应用）
        versionCode = 10023
        versionName = "1.0.0"

        // TV-specific configurations
        vectorDrawables.useSupportLibrary = true
    }

    // 原仓库的 release keystore 未随源码提供；如需正式签名，请在此自行配置 signingConfigs。
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
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        // 与根工程的 Kotlin 1.9.20 匹配（1.5.5 ↔ 1.9.20）
        kotlinCompilerExtensionVersion = "1.5.5"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        disable += listOf("UnsafeOptInUsageError")
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Core library desugaring for Java 8+ APIs on older Android versions
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // TV-specific libraries
    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    implementation("androidx.tv:tv-material:1.0.0-alpha10")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Activity + setContent
    implementation("androidx.activity:activity-compose:1.8.2")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // 局域网 HTTP：WebDAV / 飞牛影视接口 / Coil 图片加载（飞牛 API 见 FeiNiuApi.kt，纯 OkHttp + Gson + Authx 签名）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-gif:2.5.0")

    // Media3 / ExoPlayer：本地文件、局域网 HTTP 流，以及飞牛影视返回的 HLS(m3u8) 播放链接
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-datasource:1.2.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.2.1")
    implementation("androidx.media3:media3-database:1.2.1")

    // Jellyfin FFmpeg decoder for MP2/AC3/EAC3/DTS audio support
    // 由 DefaultRenderersFactory(EXTENSION_RENDERER_MODE_PREFER) 反射加载，无需显式 import
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.2.1+1")

    // JSON（媒体源配置持久化）
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 中文标题排序 / 首字母检索
    implementation("com.belerweb:pinyin4j:2.5.1")

    // 本地 NAS：SMB2/3 客户端 + 仅监听 127.0.0.1 的 HTTP 流代理
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.hierynomus:smbj:0.11.5")
    implementation("org.slf4j:slf4j-api:1.7.36")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt {
    correctErrorTypes = true
}
