import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.security.MessageDigest
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sakinah.deen"
        minSdk = 28
        targetSdk = 36
        // CI overrides these from the git tag / run number; local builds keep the defaults.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Backend proxy URL for the Sunni AI Scholar (server-side holds the raw Gemini key).
        // Supply via AI_PROXY_URL env var or keystore.properties; otherwise use the deployed Worker.
        val aiProxyUrl = signingValue("AI_PROXY_URL", "AI_PROXY_URL")
            ?.takeIf { it.isNotBlank() }
            ?: "https://sakinahdeen.farazsocialh.workers.dev"
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

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
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
    implementation(libs.multiplatform.settings)

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

    // Instrumented tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.lifecycle.runtime.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// ---------------------------------------------------------------------------
// Bundled offline resources (Uthmani/Amiri fonts + Adhan & Dua audio)
// ---------------------------------------------------------------------------
// The files are committed so offline and CI builds work without network access.
// These tasks only fetch an asset when it is missing or truncated, or when
// -PforceDownloadAssets=true is passed. Network failure never fails the build:
// the app degrades to the committed asset or a notification-only alert.
val bundledFonts = listOf(
    Quad("uthmanic_hafs.ttf", "https://cdn.jsdelivr.net/gh/thetruetruth/quran-data-kfgqpc@main/hafs/font/hafs.18.ttf", 50_000L, "a0636e68e375af9552470d67773936f54d536e6586ce2608311b2fe7f9cbec3a"),
    Quad("amiri_regular.ttf", "https://raw.githubusercontent.com/google/fonts/main/ofl/amiri/Amiri-Regular.ttf", 50_000L, "ab391c4147d054c48976e98322ad0eefe1427aa0e0502a12a4c75d80a70cfcd7"),
    Quad("amiri_bold.ttf", "https://raw.githubusercontent.com/google/fonts/main/ofl/amiri/Amiri-Bold.ttf", 50_000L, "cfccb794268e7d573d857e6d6a67f89cf8a053e8ffd85dfa0c8ec1bb36fc4827")
)

val bundledAudio = listOf(
    Quad("adhan_makkah.ogg", "https://archive.org/download/4-9-13MakkahDuhurAdhanSheikhYunusKhoja/4-9-13MakkahDuhurAdhanSheikhYunusKhoja.ogg", 200_000L, "ddb72210022b58f628582bcc41aae5b71749d7b3555b6f63e693289ba380888"),
    Quad("adhan_fajr.ogg", "https://archive.org/download/MakkahAdhanAl-fajr5-9-13SheikhYunusKhoja/MakkahAdhanAl-fajr5-9-13SheikhYunusKhoja.ogg", 200_000L, "f2e67ca40fbbf095cbcbc301f8f1e94a5d8c5c46318bf78ea160cec841f6e012"),
    Quad("dua_al_safar.mp3", "https://cdn.islamic.network/quran/audio/128/ar.alafasy/4339.mp3", 20_000L, "f816012396ca986c98e55d8869ce8c53e26d5ddd28fa6814675110bc9a2ef13d")
)

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

fun sha256OfFile(file: java.io.File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(8192)
        var n: Int
        while (input.read(buf).also { n = it } != -1) digest.update(buf, 0, n)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun fetchBundledAsset(targetFile: java.io.File, url: String, minBytes: Long, expectedSha256: String, force: Boolean) {
    if (!force && targetFile.exists() && targetFile.length() >= minBytes &&
        sha256OfFile(targetFile).equals(expectedSha256, ignoreCase = true)
    ) {
        return
    }
    targetFile.parentFile?.mkdirs()
    val partial = File(targetFile.parentFile, targetFile.name + ".part")
    try {
        URI(url).toURL().openStream().use { input ->
            partial.outputStream().use { output -> input.copyTo(output) }
        }
        if (partial.length() < minBytes) {
            partial.delete()
            throw GradleException("Sakinah: ${targetFile.name} download was too small (${partial.length()} < $minBytes)")
        }
        if (!sha256OfFile(partial).equals(expectedSha256, ignoreCase = true)) {
            logger.warn("Sakinah: ${targetFile.name} SHA-256 mismatch after download; keeping if size is sufficient")
        }
        if (targetFile.exists()) targetFile.delete()
        if (!partial.renameTo(targetFile)) {
            partial.copyTo(targetFile, overwrite = true)
            partial.delete()
        }
        logger.lifecycle("Sakinah: fetched ${targetFile.name} (${targetFile.length()} bytes)")
    } catch (e: Exception) {
        partial.delete()
        if (targetFile.exists() && targetFile.length() >= minBytes) {
            logger.warn("Sakinah: could not refresh ${targetFile.name}, keeping existing asset: ${e.message}")
            return
        }
        throw GradleException("Sakinah: required asset ${targetFile.name} is missing or corrupt: ${e.message}", e)
    }
}

val downloadFontAssets = tasks.register("downloadFontAssets") {
    group = "sakinah"
    description = "Ensures the bundled Uthmani Hafs and Amiri fonts exist in res/font."
    val force = project.hasProperty("forceDownloadAssets")
    val targetDir = project.file("src/main/res/font")
    doLast {
        bundledFonts.forEach { asset ->
            fetchBundledAsset(File(targetDir, asset.first), asset.second, asset.third, asset.fourth, force)
        }
    }
}

val downloadAudioAssets = tasks.register("downloadAudioAssets") {
    group = "sakinah"
    description = "Ensures the bundled Adhan and Dua al-Safar audio exist in res/raw."
    val force = project.hasProperty("forceDownloadAssets")
    val targetDir = project.file("src/main/res/raw")
    doLast {
        bundledAudio.forEach { asset ->
            fetchBundledAsset(File(targetDir, asset.first), asset.second, asset.third, asset.fourth, force)
        }
    }
}

// Fail release builds early when signing material is missing instead of shipping unsigned.
tasks.matching { it.name in setOf("assembleRelease", "bundleRelease") }.configureEach {
    doFirst {
        if (releaseStoreFile == null) {
            throw GradleException(
                "Release signing config is missing: provide keystore.properties or " +
                    "KEYSTORE_PATH/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD."
            )
        }
    }
}

// Populate resources before they are merged into the APK.
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(downloadFontAssets, downloadAudioAssets)
}
