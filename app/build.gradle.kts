import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp") version "2.0.20-1.0.25"
}

android {
    namespace = "com.mygithub.lab"
    compileSdk = 34

    val versionFile = rootProject.file("VERSION")
    val appVersionName = if (versionFile.exists()) versionFile.readText().trim() else "0.0.1"
    val versionParts = appVersionName.split(".").mapNotNull { it.toIntOrNull() }
    val major = versionParts.getOrElse(0) { 0 }
    val minor = versionParts.getOrElse(1) { 0 }
    val patch = versionParts.getOrElse(2) { 1 }
    val appVersionCode = major * 10000 + minor * 100 + patch

    defaultConfig {
        applicationId = "com.mygithub.lab"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        create("release") {
            // 签名凭据从 local.properties / 环境变量 读取，绝不硬编码入库
            // 开源使用者在 local.properties 中自行配置：
            //   RELEASE_STORE_FILE=keystore/release.jks
            //   RELEASE_STORE_PASSWORD=your_password
            //   RELEASE_KEY_ALIAS=your_alias
            //   RELEASE_KEY_PASSWORD=your_password
            val localProps = Properties().apply {
                val f = rootProject.file("local.properties")
                if (f.exists()) f.inputStream().use { stream -> load(stream) }
            }
            fun secret(key: String): String? =
                (localProps.getProperty(key) ?: System.getenv(key))?.takeIf { it.isNotBlank() }

            val storeFilePath = secret("RELEASE_STORE_FILE")
            if (storeFilePath != null) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = secret("RELEASE_STORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 仅当 local.properties 配置了签名凭据时才签名，否则产出未签名包（便于社区构建）
            signingConfig = if (signingConfigs.getByName("release").storeFile != null) {
                signingConfigs.getByName("release")
            } else null
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Compose + Material 3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // Security (EncryptedSharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Markdown 渲染（README 真实展示 + 图片渲染）
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.28.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-coil3:0.28.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // CameraX + ML Kit（2FA 扫码导入）
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.compose.material:material") // CameraX 预览需要 Accompanist/AndroidView 支持

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
