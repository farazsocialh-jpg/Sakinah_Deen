package com.sakinah.deen.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.sakinah.deen.R
import com.sakinah.deen.data.prefs.HadithReadingLayout
import com.sakinah.deen.data.prefs.QuranReadingLayout
import com.sakinah.deen.data.prefs.SalahHomeLayout

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
        title = "Complete Islamic Companion",
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
    @get:StringRes val titleRes: Int,
    @get:StringRes val subtitleRes: Int,
    val lightPreviewPrimary: Color,
    val lightPreviewAccent: Color,
    val darkPreviewPrimary: Color,
    val darkPreviewBg: Color
) {
    EMERALD_SANCTUARY(
        titleRes = R.string.theme_palette_emerald_sanctuary_name,
        subtitleRes = R.string.theme_palette_emerald_sanctuary_subtitle,
        lightPreviewPrimary = EmeraldDeep,
        lightPreviewAccent = MintGreen,
        darkPreviewPrimary = NightMintAccent,
        darkPreviewBg = NightBackground
    ),
    MADINAH_SAND(
        titleRes = R.string.theme_palette_madinah_sand_name,
        subtitleRes = R.string.theme_palette_madinah_sand_subtitle,
        lightPreviewPrimary = Color(0xFF6B4423),
        lightPreviewAccent = Color(0xFFB4823E),
        darkPreviewPrimary = Color(0xFFE0B36B),
        darkPreviewBg = Color(0xFF14100C)
    ),
    ANDALUSIAN_SAPPHIRE(
        titleRes = R.string.theme_palette_andalusian_sapphire_name,
        subtitleRes = R.string.theme_palette_andalusian_sapphire_subtitle,
        lightPreviewPrimary = Color(0xFF1E3A5F),
        lightPreviewAccent = Color(0xFF3B82F6),
        darkPreviewPrimary = Color(0xFF93C5FD),
        darkPreviewBg = Color(0xFF070B14)
    ),
    PURE_MONOCHROME(
        titleRes = R.string.theme_palette_pure_monochrome_name,
        subtitleRes = R.string.theme_palette_pure_monochrome_subtitle,
        lightPreviewPrimary = Color(0xFF000000),
        lightPreviewAccent = Color(0xFF2E2E2E),
        darkPreviewPrimary = Color(0xFFFFFFFF),
        darkPreviewBg = Color(0xFF000000)
    ),
    MADINAH_EMERALD(
        titleRes = R.string.theme_palette_madinah_emerald_name,
        subtitleRes = R.string.theme_palette_madinah_emerald_subtitle,
        lightPreviewPrimary = Color(0xFF0F6B4E),
        lightPreviewAccent = Color(0xFFC89B3C),
        darkPreviewPrimary = Color(0xFF66D6B3),
        darkPreviewBg = Color(0xFF020B08)
    ),
    MIDNIGHT_FAJR(
        titleRes = R.string.theme_palette_midnight_fajr_name,
        subtitleRes = R.string.theme_palette_midnight_fajr_subtitle,
        lightPreviewPrimary = Color(0xFF1E3A5F),
        lightPreviewAccent = Color(0xFF7FA6D6),
        darkPreviewPrimary = Color(0xFF6C97D0),
        darkPreviewBg = Color(0xFF03060A)
    ),
    DESERT_AMBER(
        titleRes = R.string.theme_palette_desert_amber_name,
        subtitleRes = R.string.theme_palette_desert_amber_subtitle,
        lightPreviewPrimary = Color(0xFF9A5B13),
        lightPreviewAccent = Color(0xFFD99B4E),
        darkPreviewPrimary = Color(0xFFD6A266),
        darkPreviewBg = Color(0xFF0F0902)
    ),
    ANDALUSIAN_TEAL(
        titleRes = R.string.theme_palette_andalusian_teal_name,
        subtitleRes = R.string.theme_palette_andalusian_teal_subtitle,
        lightPreviewPrimary = Color(0xFF0E6E73),
        lightPreviewAccent = Color(0xFFD4A359),
        darkPreviewPrimary = Color(0xFF66D1D6),
        darkPreviewBg = Color(0xFF010B0C)
    ),
    OLIVE_GROVE(
        titleRes = R.string.theme_palette_olive_grove_name,
        subtitleRes = R.string.theme_palette_olive_grove_subtitle,
        lightPreviewPrimary = Color(0xFF4A6741),
        lightPreviewAccent = Color(0xFFB59E5F),
        darkPreviewPrimary = Color(0xFF8AC577),
        darkPreviewBg = Color(0xFF070A06)
    ),
    OTTOMAN_TURQUOISE(
        titleRes = R.string.theme_palette_ottoman_turquoise_name,
        subtitleRes = R.string.theme_palette_ottoman_turquoise_subtitle,
        lightPreviewPrimary = Color(0xFF007A87),
        lightPreviewAccent = Color(0xFFE08E6D),
        darkPreviewPrimary = Color(0xFF66CBD6),
        darkPreviewBg = Color(0xFF000C0E)
    ),
    DAMASCUS_ROSE(
        titleRes = R.string.theme_palette_damascus_rose_name,
        subtitleRes = R.string.theme_palette_damascus_rose_subtitle,
        lightPreviewPrimary = Color(0xFF8C3F56),
        lightPreviewAccent = Color(0xFFD19A72),
        darkPreviewPrimary = Color(0xFFC5778E),
        darkPreviewBg = Color(0xFF0E0609)
    ),
    ATLAS_INDIGO(
        titleRes = R.string.theme_palette_atlas_indigo_name,
        subtitleRes = R.string.theme_palette_atlas_indigo_subtitle,
        lightPreviewPrimary = Color(0xFF3B428C),
        lightPreviewAccent = Color(0xFF8C96D9),
        darkPreviewPrimary = Color(0xFF777DC6),
        darkPreviewBg = Color(0xFF06070E)
    ),
    SUHOOR_PLUM(
        titleRes = R.string.theme_palette_suhoor_plum_name,
        subtitleRes = R.string.theme_palette_suhoor_plum_subtitle,
        lightPreviewPrimary = Color(0xFF5E3A6E),
        lightPreviewAccent = Color(0xFFC792B8),
        darkPreviewPrimary = Color(0xFFAD77C5),
        darkPreviewBg = Color(0xFF09060B)
    ),
    KAABA_GOLD(
        titleRes = R.string.theme_palette_kaaba_gold_name,
        subtitleRes = R.string.theme_palette_kaaba_gold_subtitle,
        lightPreviewPrimary = Color(0xFF7A5C1E),
        lightPreviewAccent = Color(0xFFD4AF37),
        darkPreviewPrimary = Color(0xFFD6B266),
        darkPreviewBg = Color(0xFF0C0903)
    ),
    CEDAR_WOOD(
        titleRes = R.string.theme_palette_cedar_wood_name,
        subtitleRes = R.string.theme_palette_cedar_wood_subtitle,
        lightPreviewPrimary = Color(0xFF6B4423),
        lightPreviewAccent = Color(0xFFC48F65),
        darkPreviewPrimary = Color(0xFFCF9A6D),
        darkPreviewBg = Color(0xFF0B0704)
    ),
    ZAMZAM_AQUA(
        titleRes = R.string.theme_palette_zamzam_aqua_name,
        subtitleRes = R.string.theme_palette_zamzam_aqua_subtitle,
        lightPreviewPrimary = Color(0xFF1B7A8C),
        lightPreviewAccent = Color(0xFF6EC4C7),
        darkPreviewPrimary = Color(0xFF66C4D6),
        darkPreviewBg = Color(0xFF030C0E)
    ),
    SLATE_STONE(
        titleRes = R.string.theme_palette_slate_stone_name,
        subtitleRes = R.string.theme_palette_slate_stone_subtitle,
        lightPreviewPrimary = Color(0xFF455A64),
        lightPreviewAccent = Color(0xFF90A4AE),
        darkPreviewPrimary = Color(0xFF77ACC5),
        darkPreviewBg = Color(0xFF07090A)
    ),
    CRIMSON_TERRACOTTA(
        titleRes = R.string.theme_palette_crimson_terracotta_name,
        subtitleRes = R.string.theme_palette_crimson_terracotta_subtitle,
        lightPreviewPrimary = Color(0xFF9E3B33),
        lightPreviewAccent = Color(0xFFD98A6C),
        darkPreviewPrimary = Color(0xFFD0746C),
        darkPreviewBg = Color(0xFF100605)
    ),
}

enum class PrayerGuideGender(val label: String) {
    MALE("Brother (Male)"),
    FEMALE("Sister (Female)")
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
    val prayerGuideGender: PrayerGuideGender = PrayerGuideGender.MALE,
    val salahHomeLayout: SalahHomeLayout = SalahHomeLayout.CLASSIC_COMPACT,
    val quranReadingLayout: QuranReadingLayout = QuranReadingLayout.DIVIDER_HAIRLINE,
    val hadithReadingLayout: HadithReadingLayout = HadithReadingLayout.COMPACT_DROPDOWN,
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

    SakinahPalette.MADINAH_SAND -> if (isDark) {
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

    SakinahPalette.PURE_MONOCHROME -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFFFFFFFF),
            onPrimary = Color(0xFF000000),
            primaryContainer = Color(0xFF1A1A1A),
            onPrimaryContainer = Color(0xFFFFFFFF),
            secondary = Color(0xFFE0E0E0),
            onSecondary = Color(0xFF000000),
            secondaryContainer = Color(0xFF121212),
            onSecondaryContainer = Color(0xFFFFFFFF),
            background = Color(0xFF000000),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF121212),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF1A1A1A),
            onSurfaceVariant = Color(0xFFBDBDBD),
            outline = Color(0xFF2E2E2E)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF000000),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFF0F0F0),
            onPrimaryContainer = Color(0xFF000000),
            secondary = Color(0xFF2E2E2E),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFF2F2F2),
            onSecondaryContainer = Color(0xFF000000),
            background = Color(0xFFFFFFFF),
            onBackground = Color(0xFF000000),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF000000),
            surfaceVariant = Color(0xFFF2F2F2),
            onSurfaceVariant = Color(0xFF4A4A4A),
            outline = Color(0xFFD9D9D9)
        )
    }

    SakinahPalette.ANDALUSIAN_SAPPHIRE -> if (isDark) {
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

    SakinahPalette.MADINAH_EMERALD -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFF66D6B3),
            onPrimary = Color(0xFF020B08),
            primaryContainer = Color(0xFF062D21),
            onPrimaryContainer = Color(0xFFE2EDEA),
            secondary = Color(0xFFD8B976),
            onSecondary = Color(0xFF141006),
            background = Color(0xFF020B08),
            onBackground = Color(0xFFF3F8F6),
            surface = Color(0xFF02110C),
            onSurface = Color(0xFFF3F8F6),
            surfaceVariant = Color(0xFF041E16),
            onSurfaceVariant = Color(0xFFBCD6CD),
            outline = Color(0xFF073023),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0F6B4E),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE2EDEA),
            onPrimaryContainer = Color(0xFF0A4B37),
            secondary = Color(0xFFC89B3C),
            onSecondary = Color(0xFF000000),
            background = Color(0xFFF9FBFB),
            onBackground = Color(0xFF03130E),
            surface = CardSurface,
            onSurface = Color(0xFF03130E),
            surfaceVariant = Color(0xFFEEF5F3),
            onSurfaceVariant = Color(0xFF093E2D),
            outline = Color(0xFFC1D9D1),
        )
    }

    SakinahPalette.MIDNIGHT_FAJR -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFF6C97D0),
            onPrimary = Color(0xFF03060A),
            primaryContainer = Color(0xFF0D1828),
            onPrimaryContainer = Color(0xFFE4E7EC),
            secondary = Color(0xFFA5C1E2),
            onSecondary = Color(0xFF0D1115),
            background = Color(0xFF03060A),
            onBackground = Color(0xFFF4F5F7),
            surface = Color(0xFF05090F),
            onSurface = Color(0xFFF4F5F7),
            surfaceVariant = Color(0xFF08101B),
            onSurfaceVariant = Color(0xFFC0C8D2),
            outline = Color(0xFF0E1A2B),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1E3A5F),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE4E7EC),
            onPrimaryContainer = Color(0xFF152942),
            secondary = Color(0xFF7FA6D6),
            onSecondary = Color(0xFF000000),
            background = Color(0xFFF9FAFB),
            onBackground = Color(0xFF050A11),
            surface = CardSurface,
            onSurface = Color(0xFF050A11),
            surfaceVariant = Color(0xFFEFF1F4),
            onSurfaceVariant = Color(0xFF112237),
            outline = Color(0xFFC4CCD5),
        )
    }

    SakinahPalette.DESERT_AMBER -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFFD6A266),
            onPrimary = Color(0xFF0F0902),
            primaryContainer = Color(0xFF412608),
            onPrimaryContainer = Color(0xFFF3EBE3),
            secondary = Color(0xFFE4B983),
            onSecondary = Color(0xFF161008),
            background = Color(0xFF0F0902),
            onBackground = Color(0xFFFAF7F3),
            surface = Color(0xFF190F03),
            onSurface = Color(0xFFFAF7F3),
            surfaceVariant = Color(0xFF2B1905),
            onSurfaceVariant = Color(0xFFE3D1BD),
            outline = Color(0xFF452909),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF9A5B13),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFF3EBE3),
            onPrimaryContainer = Color(0xFF6C400D),
            secondary = Color(0xFFD99B4E),
            onSecondary = Color(0xFF000000),
            background = Color(0xFFFCFBF9),
            onBackground = Color(0xFF1C1003),
            surface = CardSurface,
            onSurface = Color(0xFF1C1003),
            surfaceVariant = Color(0xFFF8F4EE),
            onSurfaceVariant = Color(0xFF59350B),
            outline = Color(0xFFE5D4C2),
        )
    }

    SakinahPalette.ANDALUSIAN_TEAL -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFF66D1D6),
            onPrimary = Color(0xFF010B0C),
            primaryContainer = Color(0xFF062E30),
            onPrimaryContainer = Color(0xFFE2EEEE),
            secondary = Color(0xFFE1BF8B),
            onSecondary = Color(0xFF151009),
            background = Color(0xFF010B0C),
            onBackground = Color(0xFFF3F8F8),
            surface = Color(0xFF021212),
            onSurface = Color(0xFFF3F8F8),
            surfaceVariant = Color(0xFF041F20),
            onSurfaceVariant = Color(0xFFBCD6D8),
            outline = Color(0xFF063134),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0E6E73),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE2EEEE),
            onPrimaryContainer = Color(0xFF0A4D50),
            secondary = Color(0xFFD4A359),
            onSecondary = Color(0xFF000000),
            background = Color(0xFFF9FBFC),
            onBackground = Color(0xFF031415),
            surface = CardSurface,
            onSurface = Color(0xFF031415),
            surfaceVariant = Color(0xFFEEF5F5),
            onSurfaceVariant = Color(0xFF084043),
            outline = Color(0xFFC0D9DB),
        )
    }

    SakinahPalette.OLIVE_GROVE -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFF8AC577),
            onPrimary = Color(0xFF070A06),
            primaryContainer = Color(0xFF1F2B1B),
            onPrimaryContainer = Color(0xFFE9EDE8),
            secondary = Color(0xFFCBBB8F),
            onSecondary = Color(0xFF12100A),
            background = Color(0xFF070A06),
            onBackground = Color(0xFFF6F7F6),
            surface = Color(0xFF0C100A),
            onSurface = Color(0xFFF6F7F6),
            surfaceVariant = Color(0xFF151D12),
            onSurfaceVariant = Color(0xFFCCD4CA),
            outline = Color(0xFF212E1D),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF4A6741),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE9EDE8),
            onPrimaryContainer = Color(0xFF34482E),
            secondary = Color(0xFFB59E5F),
            onSecondary = Color(0xFF000000),
            background = Color(0xFFFAFBFA),
            onBackground = Color(0xFF0D130C),
            surface = CardSurface,
            onSurface = Color(0xFF0D130C),
            surfaceVariant = Color(0xFFF2F4F2),
            onSurfaceVariant = Color(0xFF2B3C26),
            outline = Color(0xFFD0D7CE),
        )
    }

    SakinahPalette.OTTOMAN_TURQUOISE -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFF66CBD6),
            onPrimary = Color(0xFF000C0E),
            primaryContainer = Color(0xFF003339),
            onPrimaryContainer = Color(0xFFE0EFF1),
            secondary = Color(0xFFE9B099),
            onSecondary = Color(0xFF160E0B),
            background = Color(0xFF000C0E),
            onBackground = Color(0xFFF2F8F9),
            surface = Color(0xFF001416),
            onSurface = Color(0xFFF2F8F9),
            surfaceVariant = Color(0xFF002226),
            onSurfaceVariant = Color(0xFFB8DADD),
            outline = Color(0xFF00373D),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF007A87),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE0EFF1),
            onPrimaryContainer = Color(0xFF00555E),
            secondary = Color(0xFFE08E6D),
            onSecondary = Color(0xFF000000),
            background = Color(0xFFF9FCFC),
            onBackground = Color(0xFF001618),
            surface = CardSurface,
            onSurface = Color(0xFF001618),
            surfaceVariant = Color(0xFFEDF6F7),
            onSurfaceVariant = Color(0xFF00474E),
            outline = Color(0xFFBDDCE0),
        )
    }

    SakinahPalette.DAMASCUS_ROSE -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFFC5778E),
            onPrimary = Color(0xFF0E0609),
            primaryContainer = Color(0xFF3B1A24),
            onPrimaryContainer = Color(0xFFF1E8EB),
            secondary = Color(0xFFDFB89C),
            onSecondary = Color(0xFF150F0B),
            background = Color(0xFF0E0609),
            onBackground = Color(0xFFF9F5F7),
            surface = Color(0xFF160A0E),
            onSurface = Color(0xFFF9F5F7),
            surfaceVariant = Color(0xFF271218),
            onSurfaceVariant = Color(0xFFDFC9D0),
            outline = Color(0xFF3F1C27),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF8C3F56),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFF1E8EB),
            onPrimaryContainer = Color(0xFF622C3C),
            secondary = Color(0xFFD19A72),
            onSecondary = Color(0xFF000000),
            background = Color(0xFFFCFAFB),
            onBackground = Color(0xFF190B0F),
            surface = CardSurface,
            onSurface = Color(0xFF190B0F),
            surfaceVariant = Color(0xFFF7F2F3),
            onSurfaceVariant = Color(0xFF512532),
            outline = Color(0xFFE1CDD3),
        )
    }

    SakinahPalette.ATLAS_INDIGO -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFF777DC6),
            onPrimary = Color(0xFF06070E),
            primaryContainer = Color(0xFF191C3B),
            onPrimaryContainer = Color(0xFFE7E8F1),
            secondary = Color(0xFFAEB6E4),
            onSecondary = Color(0xFF0E0F16),
            background = Color(0xFF06070E),
            onBackground = Color(0xFFF5F6F9),
            surface = Color(0xFF090B16),
            onSurface = Color(0xFFF5F6F9),
            surfaceVariant = Color(0xFF111227),
            onSurfaceVariant = Color(0xFFC8CADF),
            outline = Color(0xFF1B1E3F),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF3B428C),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE7E8F1),
            onPrimaryContainer = Color(0xFF292E62),
            secondary = Color(0xFF8C96D9),
            onSecondary = Color(0xFF000000),
            background = Color(0xFFFAFAFC),
            onBackground = Color(0xFF0B0C19),
            surface = CardSurface,
            onSurface = Color(0xFF0B0C19),
            surfaceVariant = Color(0xFFF1F2F7),
            onSurfaceVariant = Color(0xFF222651),
            outline = Color(0xFFCCCEE1),
        )
    }

    SakinahPalette.SUHOOR_PLUM -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFFAD77C5),
            onPrimary = Color(0xFF09060B),
            primaryContainer = Color(0xFF27182E),
            onPrimaryContainer = Color(0xFFECE7EE),
            secondary = Color(0xFFD8B3CD),
            onSecondary = Color(0xFF140F12),
            background = Color(0xFF09060B),
            onBackground = Color(0xFFF7F5F8),
            surface = Color(0xFF0F0912),
            onSurface = Color(0xFFF7F5F8),
            surfaceVariant = Color(0xFF1A101F),
            onSurfaceVariant = Color(0xFFD2C8D6),
            outline = Color(0xFF2A1A31),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF5E3A6E),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFECE7EE),
            onPrimaryContainer = Color(0xFF42294D),
            secondary = Color(0xFFC792B8),
            onSecondary = Color(0xFF000000),
            background = Color(0xFFFBFAFB),
            onBackground = Color(0xFF110A14),
            surface = CardSurface,
            onSurface = Color(0xFF110A14),
            surfaceVariant = Color(0xFFF4F1F5),
            onSurfaceVariant = Color(0xFF372240),
            outline = Color(0xFFD5CCD9),
        )
    }

    SakinahPalette.KAABA_GOLD -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFFD6B266),
            onPrimary = Color(0xFF0C0903),
            primaryContainer = Color(0xFF33270D),
            onPrimaryContainer = Color(0xFFEFEBE4),
            secondary = Color(0xFFE1C773),
            onSecondary = Color(0xFF151206),
            background = Color(0xFF0C0903),
            onBackground = Color(0xFFF8F7F4),
            surface = Color(0xFF140F05),
            onSurface = Color(0xFFF8F7F4),
            surfaceVariant = Color(0xFF221A08),
            onSurfaceVariant = Color(0xFFDAD1C0),
            outline = Color(0xFF37290E),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF7A5C1E),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEFEBE4),
            onPrimaryContainer = Color(0xFF554015),
            secondary = Color(0xFFD4AF37),
            onSecondary = Color(0xFF000000),
            background = Color(0xFFFCFBF9),
            onBackground = Color(0xFF161105),
            surface = CardSurface,
            onSurface = Color(0xFF161105),
            surfaceVariant = Color(0xFFF6F4EF),
            onSurfaceVariant = Color(0xFF473511),
            outline = Color(0xFFDCD5C4),
        )
    }

    SakinahPalette.CEDAR_WOOD -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFFCF9A6D),
            onPrimary = Color(0xFF0B0704),
            primaryContainer = Color(0xFF2D1D0F),
            onPrimaryContainer = Color(0xFFEDE9E5),
            secondary = Color(0xFFD6B193),
            onSecondary = Color(0xFF140E0A),
            background = Color(0xFF0B0704),
            onBackground = Color(0xFFF8F6F4),
            surface = Color(0xFF110B06),
            onSurface = Color(0xFFF8F6F4),
            surfaceVariant = Color(0xFF1E130A),
            onSurfaceVariant = Color(0xFFD6CBC1),
            outline = Color(0xFF301F10),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6B4423),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEDE9E5),
            onPrimaryContainer = Color(0xFF4B3018),
            secondary = Color(0xFFC48F65),
            onSecondary = Color(0xFF000000),
            background = Color(0xFFFBFAFA),
            onBackground = Color(0xFF130C06),
            surface = CardSurface,
            onSurface = Color(0xFF130C06),
            surfaceVariant = Color(0xFFF5F2F0),
            onSurfaceVariant = Color(0xFF3E2714),
            outline = Color(0xFFD9CEC6),
        )
    }

    SakinahPalette.ZAMZAM_AQUA -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFF66C4D6),
            onPrimary = Color(0xFF030C0E),
            primaryContainer = Color(0xFF0B333B),
            onPrimaryContainer = Color(0xFFE4EFF1),
            secondary = Color(0xFF9AD6D8),
            onSecondary = Color(0xFF0B1414),
            background = Color(0xFF030C0E),
            onBackground = Color(0xFFF4F8F9),
            surface = Color(0xFF041416),
            onSurface = Color(0xFFF4F8F9),
            surfaceVariant = Color(0xFF082227),
            onSurfaceVariant = Color(0xFFBFDADF),
            outline = Color(0xFF0C373F),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1B7A8C),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE4EFF1),
            onPrimaryContainer = Color(0xFF135562),
            secondary = Color(0xFF6EC4C7),
            onSecondary = Color(0xFF000000),
            background = Color(0xFFF9FCFC),
            onBackground = Color(0xFF051619),
            surface = CardSurface,
            onSurface = Color(0xFF051619),
            surfaceVariant = Color(0xFFEFF6F7),
            onSurfaceVariant = Color(0xFF104751),
            outline = Color(0xFFC4DCE1),
        )
    }

    SakinahPalette.SLATE_STONE -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFF77ACC5),
            onPrimary = Color(0xFF07090A),
            primaryContainer = Color(0xFF1D262A),
            onPrimaryContainer = Color(0xFFE9EBEC),
            secondary = Color(0xFFB1BFC6),
            onSecondary = Color(0xFF0E1011),
            background = Color(0xFF07090A),
            onBackground = Color(0xFFF6F7F7),
            surface = Color(0xFF0B0E10),
            onSurface = Color(0xFFF6F7F7),
            surfaceVariant = Color(0xFF13191C),
            onSurfaceVariant = Color(0xFFCBD1D4),
            outline = Color(0xFF1F282D),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF455A64),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE9EBEC),
            onPrimaryContainer = Color(0xFF303F46),
            secondary = Color(0xFF90A4AE),
            onSecondary = Color(0xFF000000),
            background = Color(0xFFFAFBFB),
            onBackground = Color(0xFF0C1012),
            surface = CardSurface,
            onSurface = Color(0xFF0C1012),
            surfaceVariant = Color(0xFFF2F3F4),
            onSurfaceVariant = Color(0xFF28343A),
            outline = Color(0xFFCFD4D7),
        )
    }

    SakinahPalette.CRIMSON_TERRACOTTA -> if (isDark) {
        darkColorScheme(
            primary = Color(0xFFD0746C),
            onPrimary = Color(0xFF100605),
            primaryContainer = Color(0xFF421915),
            onPrimaryContainer = Color(0xFFF3E7E7),
            secondary = Color(0xFFE4AD98),
            onSecondary = Color(0xFF160E0B),
            background = Color(0xFF100605),
            onBackground = Color(0xFFFAF5F5),
            surface = Color(0xFF190908),
            onSurface = Color(0xFFFAF5F5),
            surfaceVariant = Color(0xFF2C110E),
            onSurfaceVariant = Color(0xFFE4C8C6),
            outline = Color(0xFF471B17),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF9E3B33),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFF3E7E7),
            onPrimaryContainer = Color(0xFF6F2924),
            secondary = Color(0xFFD98A6C),
            onSecondary = Color(0xFF000000),
            background = Color(0xFFFDFAFA),
            onBackground = Color(0xFF1C0B09),
            surface = CardSurface,
            onSurface = Color(0xFF1C0B09),
            surfaceVariant = Color(0xFFF8F1F1),
            onSurfaceVariant = Color(0xFF5C221E),
            outline = Color(0xFFE6CCCA),
        )
    }
}
