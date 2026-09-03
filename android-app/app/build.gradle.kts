plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.proxyvpn.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.proxyvpn.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Адрес backend API из telegram-bot/lib/apiServer.js.
        // 10.0.2.2 — алиас хоста для эмулятора Android; на реальном устройстве
        // укажите настоящий адрес/домен сервера (и используйте HTTPS в проде).
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3000\"")
        // Юзернейм бота без @, для диплинков покупки/входа.
        buildConfigField("String", "BOT_USERNAME", "\"MyVpnBot\"")
        // ВРЕМЕННО true — для проверки интерфейса без бэкенда и без входа через
        // Telegram (см. MockApiService.kt). Верните false для реальной работы.
        buildConfigField("boolean", "MOCK_MODE", "true")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
