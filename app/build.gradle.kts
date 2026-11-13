import java.io.File
import java.net.URI
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
        // CI overrides these from the git tag / run number; local builds keep the defaults.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Backend proxy URL for the Sunni AI Scholar (server-side holds the raw Gemini key).
        // Supply via AI_PROXY_URL env var or keystore.properties. Empty disables the proxy.
        val aiProxyUrl = signingValue("AI_PROXY_URL", "AI_PROXY_URL").orEmpty()
        buildConfigField("String", "AI_PROXY_URL", "\"$aiProxyUrl\"")

        // Public metal-price endpoint used for the live nisab threshold. Empty disables
        // fetching, in which case the last cached rate is used.
        val nisabRateUrl = signingValue("NISAB_RATE_URL", "NISAB_RATE_URL").orEmpty()
        buildConfigField("String", "NISAB_RATE_URL", "\"$nisabRateUrl\"")
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
    ksp {
        arg("room.schemaLocation", "${projectDir}/schemas")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        // Fonts and audio are already compressed formats; do not store them twice.
        noCompress += listOf("ogg", "mp3", "ttf")
    }
}

dependencies {
    // Core & Lifecycle
    implementation(project(":shared"))
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
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
    implementation(libs.play.services.location)

    // Koin Dependency Injection
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Ktor Client & Kotlinx Serialization
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}

// ---------------------------------------------------------------------------
// Bundled offline resources (Uthmani/Amiri fonts + Adhan & Dua audio)
// ---------------------------------------------------------------------------
// The files are committed so offline and CI builds work without network access.
// These tasks only fetch an asset when it is missing or truncated, or when
// -PforceDownloadAssets=true is passed. Network failure never fails the build:
// the app degrades to the committed asset or a notification-only alert.
val bundledFonts = listOf(
    Triple(
        "uthmanic_hafs.ttf",
        "https://cdn.jsdelivr.net/gh/thetruetruth/quran-data-kfgqpc@main/hafs/font/hafs.18.ttf",
        50_000L
    ),
    Triple(
        "amiri_regular.ttf",
        "https://raw.githubusercontent.com/google/fonts/main/ofl/amiri/Amiri-Regular.ttf",
        50_000L
    ),
    Triple(
        "amiri_bold.ttf",
        "https://raw.githubusercontent.com/google/fonts/main/ofl/amiri/Amiri-Bold.ttf",
        50_000L
    )
)

val bundledAudio = listOf(
    Triple(
        "adhan_makkah.ogg",
        "https://archive.org/download/4-9-13MakkahDuhurAdhanSheikhYunusKhoja/4-9-13MakkahDuhurAdhanSheikhYunusKhoja.ogg",
        200_000L
    ),
    Triple(
        "adhan_fajr.ogg",
        "https://archive.org/download/MakkahAdhanAl-fajr5-9-13SheikhYunusKhoja/MakkahAdhanAl-fajr5-9-13SheikhYunusKhoja.ogg",
        200_000L
    ),
    Triple(
        "dua_al_safar.mp3",
        "https://cdn.islamic.network/quran/audio/128/ar.alafasy/4339.mp3",
        20_000L
    )
)

fun fetchBundledAsset(targetFile: java.io.File, url: String, minBytes: Long, force: Boolean) {
    if (!force && targetFile.exists() && targetFile.length() >= minBytes) return
    targetFile.parentFile?.mkdirs()
    val partial = File(targetFile.parentFile, targetFile.name + ".part")
    try {
        URI(url).toURL().openStream().use { input ->
            partial.outputStream().use { output -> input.copyTo(output) }
        }
        if (partial.length() < minBytes) {
            partial.delete()
            logger.warn("Sakinah: ${targetFile.name} download was too small; keeping existing asset")
            return
        }
        if (targetFile.exists()) targetFile.delete()
        if (!partial.renameTo(targetFile)) {
            partial.copyTo(targetFile, overwrite = true)
            partial.delete()
        }
        logger.lifecycle("Sakinah: fetched ${targetFile.name} (${targetFile.length()} bytes)")
    } catch (e: Exception) {
        partial.delete()
        logger.warn("Sakinah: could not fetch ${targetFile.name}: ${e.message}")
    }
}

val downloadFontAssets = tasks.register("downloadFontAssets") {
    group = "sakinah"
    description = "Ensures the bundled Uthmani Hafs and Amiri fonts exist in res/font."
    val force = project.hasProperty("forceDownloadAssets")
    val targetDir = project.file("src/main/res/font")
    doLast {
        bundledFonts.forEach { (name, url, minBytes) ->
            fetchBundledAsset(File(targetDir, name), url, minBytes, force)
        }
    }
}

val downloadAudioAssets = tasks.register("downloadAudioAssets") {
    group = "sakinah"
    description = "Ensures the bundled Adhan and Dua al-Safar audio exist in res/raw."
    val force = project.hasProperty("forceDownloadAssets")
    val targetDir = project.file("src/main/res/raw")
    doLast {
        bundledAudio.forEach { (name, url, minBytes) ->
            fetchBundledAsset(File(targetDir, name), url, minBytes, force)
        }
    }
}

// Populate resources before they are merged into the APK.
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(downloadFontAssets, downloadAudioAssets)
}
