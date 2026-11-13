package com.sakinah.deen.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class UserObjective(
    val title: String,
    val subtitle: String,
    val recommendedStartTab: String
) {
    PRAYER_COMPASS(
        title = "Daily Salah & Qibla",
        subtitle = "Local prayer timetable, Qibla compass, and Qadha tracking right when you open the app.",
        recommendedStartTab = "DailySalah"
    ),
    QURAN_STUDY(
        title = "Quran Study & Memorization",
        subtitle = "Open directly to the Uthmanic Mus-haf, translations, Tafsir studio, and Hifz tools.",
        recommendedStartTab = "Quran"
    ),
    HADITH_SCHOLAR(
        title = "Hadith & Sunnah Research",
        subtitle = "Start in the 9 canonical Hadith collections with authenticity grades and topic search.",
        recommendedStartTab = "Hadith"
    ),
    ALL_IN_ONE(
        title = "Complete Sunni Companion",
        subtitle = "Balanced daily home for Salah, Quran, Hadith, Scholar Q&A, Zakat, and Safar tools.",
        recommendedStartTab = "DailySalah"
    )
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class SakinahPalette(
    val label: String,
    val subtitle: String,
    val lightPreviewPrimary: Color,
    val lightPreviewAccent: Color,
    val darkPreviewPrimary: Color,
    val darkPreviewBg: Color
) {
    EMERALD_SANCTUARY(
        label = "Emerald Sanctuary",
        subtitle = "Warm cream & deep botanical green",
        lightPreviewPrimary = EmeraldDeep,
        lightPreviewAccent = MintGreen,
        darkPreviewPrimary = NightMintAccent,
        darkPreviewBg = NightBackground
    ),
    MECCAN_SAND(
        label = "Meccan Sand",
        subtitle = "Limestone parchment & bronze gold",
        lightPreviewPrimary = Color(0xFF6B4423),
        lightPreviewAccent = Color(0xFFB4823E),
        darkPreviewPrimary = Color(0xFFE0B36B),
        darkPreviewBg = Color(0xFF14100C)
    ),
    MADINAH_TEAL(
        label = "Madinah Teal",
        subtitle = "Alabaster & serene Nabawi teal",
        lightPreviewPrimary = Color(0xFF0E4D5C),
        lightPreviewAccent = Color(0xFF14919B),
        darkPreviewPrimary = Color(0xFF4FD1C5),
        darkPreviewBg = Color(0xFF07161A)
    ),
    MIDNIGHT_TAHAJJUD(
        label = "Midnight Crescent",
        subtitle = "Deep sapphire & silver starlight",
        lightPreviewPrimary = Color(0xFF1E3A5F),
        lightPreviewAccent = Color(0xFF3B82F6),
        darkPreviewPrimary = Color(0xFF93C5FD),
        darkPreviewBg = Color(0xFF070B14)
    )
}

enum class FontScalePreset(val label: String, val multiplier: Float) {
    COMPACT("Compact", 0.90f),
    STANDARD("Standard", 1.00f),
    COMFORTABLE("Comfort", 1.12f),
    LARGE("Large", 1.24f)
}

data class UserAppearanceConfig(
    val userName: String = "",
    val experienceLevel: String = "Practicing Muslim",
    val objective: UserObjective = UserObjective.ALL_IN_ONE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val palette: SakinahPalette = SakinahPalette.EMERALD_SANCTUARY,
    val fontScale: FontScalePreset = FontScalePreset.STANDARD,
    val focusModeHideDistractions: Boolean = false,
    val autoHideBarsOnScroll: Boolean = true,
    val onboardingCompleted: Boolean = false
)

fun buildSakinahColorScheme(palette: SakinahPalette, isDark: Boolean): ColorScheme = when (palette) {
    SakinahPalette.EMERALD_SANCTUARY -> if (isDark) {
        darkColorScheme(
            primary = NightMintAccent,
            onPrimary = NightBackground,
            primaryContainer = NightMintSubtle,
            onPrimaryContainer = NightMintAccent,
            secondary = MintGreen,
            onSecondary = NightBackground,
            secondaryContainer = NightSurfaceElevated,
            onSecondaryContainer = NightTextPrimary,
            background = NightBackground,
            onBackground = NightTextPrimary,
            surface = NightSurface,
            onSurface = NightTextPrimary,
            surfaceVariant = NightSurfaceElevated,
            onSurfaceVariant = NightTextMuted,
            outline = NightBorderHairline
        )
    } else {
        lightColorScheme(
            primary = EmeraldDeep,
            onPrimary = Color.White,
            primaryContainer = MintSubtle,
            onPrimaryContainer = EmeraldDeep,
            secondary = MintGreen,
            onSecondary = EmeraldDeep,
            secondaryContainer = MintSubtle,
            onSecondaryContainer = EmeraldDeep,
            background = WarmCream,
            onBackground = DarkCharcoal,
            surface = CardSurface,
            onSurface = DarkCharcoal,
            surfaceVariant = Color(0xFFF1F5F3),
            onSurfaceVariant = SubtitleGray,
            outline = BorderHairline
        )
    }

    SakinahPalette.MECCAN_SAND -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFFE0B36B),
            onPrimary = Color(0xFF21160A),
            primaryContainer = Color(0xFF3A2B18),
            onPrimaryContainer = Color(0xFFF5DFB8),
            secondary = Color(0xFFC89B54),
            onSecondary = Color(0xFF21160A),
            secondaryContainer = Color(0xFF2B231A),
            onSecondaryContainer = Color(0xFFF5EFE6),
            background = Color(0xFF14100C),
            onBackground = Color(0xFFF5EFE6),
            surface = Color(0xFF1E1812),
            onSurface = Color(0xFFF5EFE6),
            surfaceVariant = Color(0xFF2B231A),
            onSurfaceVariant = Color(0xFFB5A897),
            outline = Color(0xFF3D3226)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6B4423),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF3E5D0),
            onPrimaryContainer = Color(0xFF4A2E14),
            secondary = Color(0xFFB4823E),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF3E5D0),
            onSecondaryContainer = Color(0xFF4A2E14),
            background = Color(0xFFFAF6EE),
            onBackground = Color(0xFF231B12),
            surface = CardSurface,
            onSurface = Color(0xFF231B12),
            surfaceVariant = Color(0xFFF3ECE0),
            onSurfaceVariant = Color(0xFF6A5D4D),
            outline = Color(0xFFE4DACB)
        )
    }

    SakinahPalette.MADINAH_TEAL -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFF4FD1C5),
            onPrimary = Color(0xFF062024),
            primaryContainer = Color(0xFF12373D),
            onPrimaryContainer = Color(0xFFB2F5EA),
            secondary = Color(0xFF2CB1BC),
            onSecondary = Color(0xFF062024),
            secondaryContainer = Color(0xFF16323A),
            onSecondaryContainer = Color(0xFFECF8FA),
            background = Color(0xFF07161A),
            onBackground = Color(0xFFECF8FA),
            surface = Color(0xFF0E2329),
            onSurface = Color(0xFFECF8FA),
            surfaceVariant = Color(0xFF16323A),
            onSurfaceVariant = Color(0xFF93B3BA),
            outline = Color(0xFF1E444E)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0E4D5C),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFCFF2F6),
            onPrimaryContainer = Color(0xFF0A3945),
            secondary = Color(0xFF14919B),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFCFF2F6),
            onSecondaryContainer = Color(0xFF0A3945),
            background = Color(0xFFF5FAFA),
            onBackground = Color(0xFF132428),
            surface = CardSurface,
            onSurface = Color(0xFF132428),
            surfaceVariant = Color(0xFFE9F3F4),
            onSurfaceVariant = Color(0xFF536A6F),
            outline = Color(0xFFD5E5E7)
        )
    }

    SakinahPalette.MIDNIGHT_TAHAJJUD -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFF93C5FD),
            onPrimary = Color(0xFF091426),
            primaryContainer = Color(0xFF17253D),
            onPrimaryContainer = Color(0xFFDBEAFE),
            secondary = Color(0xFF60A5FA),
            onSecondary = Color(0xFF091426),
            secondaryContainer = Color(0xFF18243E),
            onSecondaryContainer = Color(0xFFF1F5F9),
            background = Color(0xFF070B14),
            onBackground = Color(0xFFF1F5F9),
            surface = Color(0xFF0F172A),
            onSurface = Color(0xFFF1F5F9),
            surfaceVariant = Color(0xFF18243E),
            onSurfaceVariant = Color(0xFF94A3B8),
            outline = Color(0xFF243352)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1E3A5F),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDBEAFE),
            onPrimaryContainer = Color(0xFF172C49),
            secondary = Color(0xFF3B82F6),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFDBEAFE),
            onSecondaryContainer = Color(0xFF172C49),
            background = Color(0xFFF6F8FC),
            onBackground = Color(0xFF111827),
            surface = CardSurface,
            onSurface = Color(0xFF111827),
            surfaceVariant = Color(0xFFEDF1F7),
            onSurfaceVariant = Color(0xFF5B6475),
            outline = Color(0xFFDCE3EE)
        )
    }
}
