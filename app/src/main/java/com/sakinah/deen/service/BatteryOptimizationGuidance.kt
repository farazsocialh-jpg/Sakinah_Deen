package com.sakinah.deen.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * OEM-aware guidance for surviving aggressive battery managers.
 *
 * Samsung/Xiaomi/Oppo/Huawei-style "battery optimisation" and auto-start managers kill
 * background apps, so an exact alarm can be scheduled correctly and still never wake the
 * process. Android offers no public API for those screens, so this object:
 *
 *  1. detects the manufacturer we know about,
 *  2. deep-links to the vendor's own auto-start/battery screen when the device exposes it,
 *  3. falls back to the standard Android battery-optimisation settings page,
 *  4. never throws when a vendor screen is missing (manufacturers rename or remove them).
 */
object BatteryOptimizationGuidance {

    /** Known vendor auto-start / power-manager activities, most specific first. */
    data class OemProfile(
        val label: String,
        val brands: Set<String>,
        val components: List<ComponentName>
    )

    private fun component(packageName: String, className: String) =
        ComponentName(packageName, className)

    private val PROFILES = listOf(
        OemProfile(
            label = "Xiaomi / Redmi / POCO",
            brands = setOf("xiaomi", "redmi", "poco"),
            components = listOf(
                component(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                ),
                component(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
            )
        ),
        OemProfile(
            label = "Huawei / Honor",
            brands = setOf("huawei", "honor"),
            components = listOf(
                component(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                ),
                component(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            )
        ),
        OemProfile(
            label = "Oppo / Realme / OnePlus",
            brands = setOf("oppo", "realme", "oneplus"),
            components = listOf(
                component(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                ),
                component(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                ),
                component(
                    "com.coloros.oppoguardelf",
                    "com.coloros.oppoguardelf.powermgr.powersaver.PowerSaverActivity"
                ),
                component(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                )
            )
        ),
        OemProfile(
            label = "Vivo / iQOO",
            brands = setOf("vivo", "iqoo"),
            components = listOf(
                component(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                ),
                component(
                    "com.iqoo.secure",
                    "com.iqoo.secure.safeguard.PurviewTabActivity"
                )
            )
        ),
        OemProfile(
            label = "Samsung",
            brands = setOf("samsung"),
            components = listOf(
                component("com.samsung.android.lool", "com.samsung.android.lool.activity.BatteryActivity"),
                component("com.samsung.android.sm", "com.samsung.android.sm.ui.powersaving.PowerSavingList")
            )
        ),
        OemProfile(
            label = "Meizu",
            brands = setOf("meizu"),
            components = listOf(
                component("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity")
            )
        ),
        OemProfile(
            label = "Asus",
            brands = setOf("asus"),
            components = listOf(
                component("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")
            )
        )
    )

    /** Manufacturer tokens as reported by the platform, lower-cased. */
    fun deviceBrandTokens(): List<String> = listOf(Build.MANUFACTURER, Build.BRAND)
        .filterNotNull()
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()

    /** The matching OEM profile, or null on stock/AOSP-like devices (Pixel, Nothing, …). */
    fun matchingProfile(): OemProfile? {
        val tokens = deviceBrandTokens()
        return PROFILES.firstOrNull { profile -> profile.brands.any { brand -> brand in tokens } }
    }

    fun isAggressiveOem(): Boolean = matchingProfile() != null

    fun oemLabel(): String? = matchingProfile()?.label

    /** True when the app is still subject to Android's own battery optimisation. */
    fun isBatteryOptimizationActive(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return runCatching { !power.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    /** Standard Android battery-optimisation list; always resolvable. */
    fun systemBatterySettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Per-app settings page, used when no battery screen can be opened. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Vendor auto-start screens this build can actually resolve on this device. */
    fun resolvableVendorIntents(context: Context): List<Intent> {
        val profile = matchingProfile() ?: return emptyList()
        val packageManager = context.packageManager
        return profile.components.mapNotNull { component ->
            val available = runCatching {
                packageManager.getActivityInfo(component, 0)
            }.isSuccess
            if (!available) return@mapNotNull null
            Intent().apply {
                this.component = component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Opens the best available screen: the vendor's auto-start page if it exists, otherwise
     * Android's battery-optimisation list. Returns false only when nothing could be launched.
     */
    fun openBestSettings(context: Context): Boolean {
        val candidates = resolvableVendorIntents(context) + listOf(
            systemBatterySettingsIntent(),
            appDetailsIntent(context)
        )
        for (intent in candidates) {
            val opened = runCatching { context.startActivity(intent) }.isSuccess
            if (opened) return true
        }
        return false
    }

    /** Short, honest explanation of what to change on this device. */
    fun guidanceText(context: Context): String {
        val oem = oemLabel()
        val batteryActive = isBatteryOptimizationActive(context)
        return when {
            oem != null -> "$oem device detected: set Battery usage to \"Unrestricted\" and allow auto-start for Sakinah so Doze and the OEM power manager cannot delay Fajr."
            batteryActive -> "Battery optimisation is on. Allow unrestricted battery use so Doze cannot delay reminders."
            else -> "Battery optimisation is off. Some OEM power managers can still restrict background apps; check your device's battery settings if Fajr is late."
        }
    }
}
