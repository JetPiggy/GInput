import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/** local.properties 不進版控：放 Sentry 的 DSN，也可以把網址改指測試用的伺服器。 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

fun localProperty(name: String, default: String = ""): String =
    localProperties.getProperty(name)?.takeIf(String::isNotBlank) ?: default

/** keystore.properties 和 signing/ 不進版控；格式見 keystore.properties.example。 */
val releaseSigning = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use(::load)
}
val hasReleaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !releaseSigning.getProperty(it).isNullOrBlank() }

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.jetpiggy.ginput"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jetpiggy.ginput"
        minSdk = 28
        targetSdk = 36
        versionCode = 7
        versionName = "0.10.0"

        // 當機回報（ADR 0001）：沒有 DSN 就不啟用 Sentry。
        buildConfigField("String", "SENTRY_DSN", localProperty("SENTRY_DSN").asBuildConfigString())
        // 使用統計的 Worker（backend/usage-stats）。
        buildConfigField(
            "String",
            "USAGE_STATS_URL",
            localProperty("USAGE_STATS_URL", "https://ginput-stats.jetpiggy.com/v1/daily").asBuildConfigString(),
        )
        // 雲端翻譯和改寫的 Worker（backend/ai）。
        buildConfigField(
            "String",
            "CLOUD_TEXT_URL",
            localProperty("CLOUD_TEXT_URL", "https://ginput-ai.jetpiggy.com/v1/text").asBuildConfigString(),
        )
        // GInput-updates 的 GitHub Pages：下載頁、私隱政策和 latest.json。
        buildConfigField(
            "String",
            "SITE_URL",
            localProperty("SITE_URL", "https://ginput.jetpiggy.com/").asBuildConfigString(),
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 沒有 keystore.properties 的話，release 版不簽署，不能安裝。
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")

    implementation(composeBom)

    implementation(project(":engine"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // 當機回報（MIT）；連帶 epitaph（MIT）。
    implementation("io.sentry:sentry-android-core:8.57.0")
    testImplementation("junit:junit:4.13.2")
    // 只供單元測試解析 JSON（Android 內置 org.json，JVM 測試沒有）。
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
