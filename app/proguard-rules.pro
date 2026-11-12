# ==============================================================================
# Sakinah Production R8 / ProGuard Configuration
# Package: com.sakinah.deen
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. Android Room & SQLCipher Database Encryption
# ------------------------------------------------------------------------------
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.sqlcipher.**

# Keep Sakinah Room Entities, DAOs, and Type Converters
-keep class com.sakinah.deen.data.database.entity.** { *; }
-keep class com.sakinah.deen.data.database.dao.** { *; }
-keep class com.sakinah.deen.data.database.converter.** { *; }
-keep class com.sakinah.deen.data.database.SakinahEncryptedDatabase { *; }

# ------------------------------------------------------------------------------
# 2. Adhan-Kotlin & Astronomical Mathematical Ephemeris
# ------------------------------------------------------------------------------
-keep class com.batoulapps.adhan.** { *; }
-dontwarn com.batoulapps.adhan.**

-keep class com.sakinah.deen.domain.astronomy.** { *; }

# ------------------------------------------------------------------------------
# 3. Kotlinx Serialization & Ktor Streaming Client
# ------------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**

-keepclassmembers class * {
    *** Companion;
}

-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

-keepclassmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}

-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    <fields>;
    <methods>;
}

-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep RAG DTOs for reflection-free JSON decoding
-keep class com.sakinah.deen.network.model.** { *; }

# ------------------------------------------------------------------------------
# 4. Koin Dependency Injection
# ------------------------------------------------------------------------------
-keep class org.koin.** { *; }
-dontwarn org.koin.**
-keep class com.sakinah.deen.di.** { *; }

# ------------------------------------------------------------------------------
# 5. Jetpack Compose & Material 3 Optimization
# ------------------------------------------------------------------------------
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.ui.** { *; }

# ------------------------------------------------------------------------------
# 6. Release Logging Stripping (Zero telemetry / Zero debug leaks)
# ------------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ------------------------------------------------------------------------------
# 7. Media3 / ExoPlayer Adhan Playback
# ------------------------------------------------------------------------------
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Sakinah background components resolved by name from the manifest
-keep class com.sakinah.deen.service.** { *; }
-keep class com.sakinah.deen.receiver.** { *; }
-keep class com.sakinah.deen.work.** { *; }

# ------------------------------------------------------------------------------
# 8. Room generated implementations & SQLCipher (legacy net.sqlcipher package)
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.SharedSQLiteStatement { *; }
-keep class * extends androidx.room.EntityInsertionAdapter { *; }
-keep class * extends androidx.room.EntityDeletionOrUpdateAdapter { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# ------------------------------------------------------------------------------
# 9. kotlinx.serialization internal factories & Koin core metadata
# ------------------------------------------------------------------------------
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**
-keep class org.koin.core.** { *; }
-keep class org.koin.android.** { *; }
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, InnerClasses, Signature, EnclosingMethod

# ------------------------------------------------------------------------------
# 10. WorkManager workers declared in the manifest / by reflection
# ------------------------------------------------------------------------------
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
