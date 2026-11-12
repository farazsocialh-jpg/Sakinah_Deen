import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// Release credentials are never hardcoded: keystore.properties (git-ignored) or environment variables.
val keystoreProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(propertyKey: String, environmentKey: String): String? =
    keystoreProperties.getProperty(propertyKey) ?: System.getenv(environmentKey)

val releaseStoreFile = signingValue("storeFile", "KEYSTORE_PATH")?.let { configuredPath ->
    rootProject.file(configuredPath).takeIf { it.exists() }
}

android {
    namespace = "com.sakinah.deen"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sakinah.deen"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Free Google AI Studio key. Supply via GEMINI_API_KEY env var or keystore.properties.
        // Never commit a real key: the value is embedded in the APK and is extractable.
        val geminiApiKey = signingValue("GEMINI_API_KEY", "GEMINI_API_KEY").orEmpty()
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "GEMINI_MODEL", "\"gemini-2.5-flash\"")
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = releaseStoreFile
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Only sign when a keystore is actually configured; otherwise assembleRelease
            // produces an unsigned artifact instead of silently shipping a debug-style key.
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
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
}

dependencies {
    // Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)

    // Room Database & SQLCipher Encryption
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Media3 / ExoPlayer Adhan Playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    // Astronomy & Prayer Calculation
    implementation(libs.adhan.kotlin)

    // Koin Dependency Injection
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Ktor Client & Kotlinx Serialization
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
}
