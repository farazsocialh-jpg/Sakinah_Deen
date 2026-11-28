package com.sakinah.deen.ui

import android.Manifest
import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.room.Room
import com.github.takahirom.roborazzi.captureRoboImage
import com.sakinah.deen.data.database.DatabaseSeeder
import com.sakinah.deen.data.database.OfflineContentSeeder
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.data.prefs.HadithReadingLayout
import com.sakinah.deen.data.prefs.QuranReadingLayout
import com.sakinah.deen.data.prefs.SalahHomeLayout
import com.sakinah.deen.data.quran.QulDataSource
import com.sakinah.deen.data.quran.QulOfflineDatabase
import com.sakinah.deen.di.astronomyModule
import com.sakinah.deen.di.networkModule
import com.sakinah.deen.di.preferencesModule
import com.sakinah.deen.di.prayerTimeModule
import com.sakinah.deen.di.repositoryModule
import com.sakinah.deen.di.travelModule
import com.sakinah.deen.di.viewModelModule
import com.sakinah.deen.ui.screens.AboutScreen
import com.sakinah.deen.ui.screens.AiChatScreen
import com.sakinah.deen.ui.screens.AuthScreen
import com.sakinah.deen.ui.screens.DataSourcesScreen
import com.sakinah.deen.ui.screens.HadithScreen
import com.sakinah.deen.ui.screens.HelpScreen
import com.sakinah.deen.ui.screens.NotificationSettingsScreen
import com.sakinah.deen.ui.screens.PersonalizedOnboardingScreen
import com.sakinah.deen.ui.screens.PrivacyScreen
import com.sakinah.deen.ui.screens.ProfileScreen
import com.sakinah.deen.ui.screens.QuranScreen
import com.sakinah.deen.ui.screens.SalahScreen
import com.sakinah.deen.ui.screens.SettingsScreen
import com.sakinah.deen.ui.screens.SettingsSection
import com.sakinah.deen.ui.screens.ToolsHubScreen
import com.sakinah.deen.ui.screens.TravelScreen
import com.sakinah.deen.ui.screens.VisualSalahGuideScreen
import com.sakinah.deen.ui.screens.WealthScreen
import com.sakinah.deen.ui.theme.FontScalePreset
import com.sakinah.deen.ui.theme.SakinahPalette
import com.sakinah.deen.ui.theme.SakinahTheme
import com.sakinah.deen.ui.theme.ThemeMode
import com.sakinah.deen.ui.theme.UserAppearanceConfig
import com.sakinah.deen.ui.viewmodel.HadithViewModel
import com.sakinah.deen.ui.viewmodel.QuranViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Layer 1 of the UI/UX matrix: renders real screen composables on the JVM and writes one PNG
 * per state, so contrast, clipping, truncation and wrapping bugs are visible in minutes
 * without booting an emulator.
 *
 * Coverage:
 *  - every [SakinahPalette] x Light/Dark: the current 8-step onboarding flow (permissions are
 *    granted, so the "Granted" pills are exercised in every palette), Salah, Quran and Hadith;
 *  - every [FontScalePreset] across the onboarding flow, Salah, Quran and Hadith, plus the
 *    Arabic/translation size extremes on the Quran reader;
 *  - every [SalahHomeLayout], [QuranReadingLayout] and [HadithReadingLayout] in both modes;
 *  - every secondary screen (Scholar, Tools, Travel, Wealth, Visual Guide, all three Settings
 *    sections, Notification Settings, Profile, Auth and the four info screens).
 *
 * The device class is a 360dp-wide phone: the narrowest width the app supports, which is where
 * prayer names, chips and header lines wrap or truncate first.
 *
 * Screens resolve their singletons from Koin exactly as they do in the app. The real
 * preference/repository/view-model graph is used; only the SQLCipher database (unavailable on
 * the JVM) and the keystore-backed account store are replaced with in-memory equivalents.
 *
 * Re-run with: ./gradlew :app:recordRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: SakinahApp.onCreate loads the SQLCipher .so, which does not exist on
// the JVM. The test starts its own Koin graph with an in-memory Room database instead.
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class ExhaustiveUiMatrixSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var application: Application
    // Built outside composition (after Koin starts) so lint's ViewModelConstructorInComposable
    // stays quiet. Rebuilt in setUp() for every test, preserving a fresh instance per test.
    private lateinit var quranViewModel: QuranViewModel
    private lateinit var hadithViewModel: HadithViewModel
    private var scene by mutableStateOf(Scene())

    private class Scene(
        val palette: SakinahPalette = SakinahPalette.EMERALD_SANCTUARY,
        val dark: Boolean = false,
        val fontScale: Float = 1f,
        val content: @Composable () -> Unit = {}
    )

    /** JVM stand-ins for the two Android-only stores. Everything else is the production graph. */
    private val snapshotStorageModule = module {
        single {
            Room.inMemoryDatabaseBuilder(androidContext(), SakinahEncryptedDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }
        single { get<SakinahEncryptedDatabase>().salahDao() }
        single { get<SakinahEncryptedDatabase>().qadhaDao() }
        single { get<SakinahEncryptedDatabase>().hifzDao() }
        single { get<SakinahEncryptedDatabase>().sadaqahDao() }
        single { get<SakinahEncryptedDatabase>().quranDao() }
        single { get<SakinahEncryptedDatabase>().hadithDao() }
        single { get<SakinahEncryptedDatabase>().tafsirDao() }
        single { get<SakinahEncryptedDatabase>().adhkarDao() }
        single { get<SakinahEncryptedDatabase>().prayerTimetableDao() }
        single<QulDataSource> { QulOfflineDatabase(androidContext()) }
        single {
            OfflineContentSeeder(
                readAsset = DatabaseSeeder.assetReader(androidContext()),
                content = { null }
            )
        }
    }

    private val snapshotAccountModule = module {
        single<com.russhwolf.settings.Settings>(qualifier = named("account_settings")) {
            com.russhwolf.settings.SharedPreferencesSettings(
                androidContext().getSharedPreferences(
                    "sakinah_snapshot_account",
                    android.content.Context.MODE_PRIVATE
                )
            )
        }
        single<com.sakinah.deen.auth.AuthRepository> {
            com.sakinah.deen.auth.PersistentAuthRepository(
                prefs = get(named("account_settings")),
                onSignOut = {}
            )
        }
        single<com.sakinah.deen.profile.UserProfileRepository> {
            com.sakinah.deen.profile.PersistentUserProfileRepository(
                prefs = get(named("account_settings"))
            )
        }
        single<com.sakinah.deen.settings.SettingsRepository> {
            com.sakinah.deen.settings.SettingsRepositoryImpl(
                prefs = get(named("account_settings")),
                cacheBytesProvider = { 0L },
                onClearCache = {}
            )
        }
    }

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        // Step 4 (permissions) must be captured in its granted state, where the "Allowed"
        // pills previously disappeared into the background.
        shadowOf(application).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
        stopKoin()
        startKoin {
            androidContext(application)
            modules(
                preferencesModule,
                astronomyModule,
                travelModule,
                networkModule,
                prayerTimeModule,
                repositoryModule,
                viewModelModule
            )
            modules(snapshotStorageModule, snapshotAccountModule)
        }
        quranViewModel = QuranViewModel()
        hadithViewModel = HadithViewModel()
        // Infinite progress animations must not stall the snapshot clock.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val current = scene
            SakinahTheme(
                darkTheme = current.dark,
                palette = current.palette,
                fontScaleMultiplier = current.fontScale
            ) {
                Box(Modifier.fillMaxSize()) { current.content() }
            }
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    // ---------------------------------------------------------------- helpers

    private fun appearance(): AppearancePreferences =
        GlobalContext.get().get<AppearancePreferences>()

    private fun show(
        palette: SakinahPalette,
        dark: Boolean,
        fontScale: Float = 1f,
        content: @Composable () -> Unit
    ) {
        scene = Scene(palette = palette, dark = dark, fontScale = fontScale, content = content)
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
    }

    private fun shot(path: String) {
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$path.png")
    }

    private fun showAndShot(
        path: String,
        palette: SakinahPalette,
        dark: Boolean,
        fontScale: Float = 1f,
        content: @Composable () -> Unit
    ) {
        show(palette, dark, fontScale, content)
        shot(path)
    }

    /**
     * The welcome CTA appears only after the animated logo assembles and the greeting fades in.
     * This suite drives the clock manually, so advance it until a CTA is actually present rather
     * than assuming a fixed animation duration.
     */
    private fun advanceUntilOnboardingCta() {
        repeat(60) {
            val ctaVisible =
                composeRule.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodesWithText("Start Using Sakinah")
                        .fetchSemanticsNodes().isNotEmpty()
            if (ctaVisible) return
            composeRule.mainClock.advanceTimeBy(100)
            composeRule.waitForIdle()
        }
        error("No onboarding CTA appeared. Tree:\n" + composeRule.onRoot().printToString(maxDepth = 12))
    }

    private fun onboarding(
        palette: SakinahPalette,
        dark: Boolean,
        fontScale: Float = 1f,
        shots: ((String) -> Unit)? = null
    ) {
        val prefix = "${palette.name.lowercase()}-${if (dark) "dark" else "light"}"
        // A fresh saveable slot per configuration: otherwise rememberSaveable restores the
        // previous run's step and the wizard re-opens midway with stale state.
        show(palette, dark, fontScale) {
            key("onboarding-$prefix-$fontScale") {
                PersonalizedOnboardingScreen(
                    initialConfig = UserAppearanceConfig(
                        userName = "Faraz",
                        themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
                        palette = palette,
                        fontScale = FontScalePreset.entries
                            .firstOrNull { it.multiplier == fontScale }
                            ?: FontScalePreset.STANDARD
                    ),
                    onConfigPreviewChange = {},
                    onComplete = {}
                )
            }
        }
        advanceUntilOnboardingCta()
        shots?.invoke("onboarding/$prefix-step0-welcome")

        // The current flow has up to eight steps and may skip Edition/Tafsir when there is only
        // one edition or no offline Tafsir pack. Follow the actual CTA instead of hard-coding
        // step names so the snapshots track the app rather than a stale four-step wizard.
        var step = 0
        while (step < 10) {
            if (composeRule.onAllNodesWithText("Start Using Sakinah")
                    .fetchSemanticsNodes().isNotEmpty()
            ) {
                shots?.invoke("onboarding/$prefix-step$step-final")
                return
            }
            check(composeRule.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty()) {
                "No Continue CTA at step $step, fontScale=$fontScale. Tree:\n" +
                    composeRule.onRoot().printToString(maxDepth = 12)
            }
            composeRule.onNodeWithText("Continue").performClick()
            step++
            composeRule.mainClock.advanceTimeBy(600)
            composeRule.waitForIdle()
            advanceUntilOnboardingCta()
            val label = when (step) {
                1 -> "appearance"
                2 -> "goals"
                3 -> "language"
                4 -> "edition"
                5 -> "tafsir"
                6 -> "permissions"
                7 -> "final"
                else -> "step"
            }
            shots?.invoke("onboarding/$prefix-step$step-$label")
        }
        error("Onboarding did not reach its final CTA within 10 steps")
    }

    private val salahScene: @Composable () -> Unit = {
        SalahScreen(onOpenGuide = {}, viewModel = null)
    }
    private val quranScene: @Composable () -> Unit = { QuranScreen(viewModel = quranViewModel) }
    private val hadithScene: @Composable () -> Unit = { HadithScreen(viewModel = hadithViewModel) }

    // ------------------------------------------------------------ matrices

    @Test
    fun paletteAndModeMatrix() {
        SakinahPalette.entries.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val mode = if (dark) "dark" else "light"
                val slug = palette.name.lowercase()
                onboarding(palette, dark) { shot(it) }
                showAndShot("matrix/$slug-$mode-salah", palette, dark, content = salahScene)
                showAndShot("matrix/$slug-$mode-quran", palette, dark, content = quranScene)
                showAndShot("matrix/$slug-$mode-hadith", palette, dark, content = hadithScene)
            }
        }
    }

    @Test
    fun fontScaleMatrix() {
        FontScalePreset.entries.forEach { preset ->
            val scale = preset.multiplier
            onboarding(SakinahPalette.EMERALD_SANCTUARY, false, scale) { path ->
                // Every step is captured at every preset; steps 2 and 3 are where the
                // location, method and reminder cards wrap first.
                shot("fonts/${preset.name.lowercase()}-${path.substringAfterLast('/')}")
            }
            showAndShot("fonts/${preset.name.lowercase()}-salah", SakinahPalette.EMERALD_SANCTUARY, false, scale, salahScene)
            showAndShot("fonts/${preset.name.lowercase()}-quran", SakinahPalette.EMERALD_SANCTUARY, false, scale, quranScene)
            showAndShot("fonts/${preset.name.lowercase()}-hadith", SakinahPalette.EMERALD_SANCTUARY, false, scale, hadithScene)
        }

        // Arabic/translation size extremes on the reader itself.
        appearance().saveConfig(UserAppearanceConfig(arabicFontSize = 36, translationFontSize = 24))
        showAndShot("fonts/quran-arabic36-translation24", SakinahPalette.PURE_MONOCHROME, true, 1f, quranScene)
        appearance().saveConfig(UserAppearanceConfig(arabicFontSize = 18, translationFontSize = 12))
        showAndShot("fonts/quran-arabic18-translation12", SakinahPalette.PURE_MONOCHROME, false, 1f, quranScene)
        appearance().saveConfig(UserAppearanceConfig())
    }

    @Test
    fun salahHomeLayoutMatrix() {
        SalahHomeLayout.entries.forEach { layout ->
            appearance().saveConfig(UserAppearanceConfig(salahHomeLayout = layout))
            listOf(false, true).forEach { dark ->
                showAndShot(
                    "layouts/salah-${layout.name.lowercase()}-${if (dark) "dark" else "light"}",
                    SakinahPalette.EMERALD_SANCTUARY,
                    dark,
                    content = salahScene
                )
            }
        }
        appearance().saveConfig(UserAppearanceConfig())
    }

    @Test
    fun quranReadingLayoutMatrix() {
        QuranReadingLayout.entries.forEach { layout ->
            appearance().saveConfig(UserAppearanceConfig(quranReadingLayout = layout))
            listOf(false, true).forEach { dark ->
                showAndShot(
                    "layouts/quran-${layout.name.lowercase()}-${if (dark) "dark" else "light"}",
                    SakinahPalette.MADINAH_SAND,
                    dark,
                    content = quranScene
                )
            }
        }
        appearance().saveConfig(UserAppearanceConfig())
    }

    @Test
    fun hadithReadingLayoutMatrix() {
        HadithReadingLayout.entries.forEach { layout ->
            appearance().saveConfig(UserAppearanceConfig(hadithReadingLayout = layout))
            listOf(false, true).forEach { dark ->
                showAndShot(
                    "layouts/hadith-${layout.name.lowercase()}-${if (dark) "dark" else "light"}",
                    SakinahPalette.ANDALUSIAN_SAPPHIRE,
                    dark,
                    content = hadithScene
                )
            }
        }
        appearance().saveConfig(UserAppearanceConfig())
    }

    @Test
    fun secondaryScreensMatrix() {
        val screens = listOf<Pair<String, @Composable () -> Unit>>(
            "scholar-ai-chat" to { AiChatScreen() },
            "tools-hub" to { ToolsHubScreen(onNavigate = {}) },
            "travel-safar" to { TravelScreen() },
            "wealth-zakat" to { WealthScreen() },
            "visual-salah-guide" to { VisualSalahGuideScreen(onBack = {}) },
            "settings-prayer-location" to { SettingsScreen(SettingsSection.PRAYER_LOCATION) },
            "settings-appearance-reading" to { SettingsScreen(SettingsSection.APPEARANCE_READING) },
            "settings-quran-offline" to { SettingsScreen(SettingsSection.QURAN_OFFLINE) },
            "notification-settings" to { NotificationSettingsScreen() },
            "profile" to { ProfileScreen(onBack = {}) },
            "auth" to { AuthScreen(onDone = {}) },
            "info-data-sources" to { DataSourcesScreen(onBack = {}) },
            "info-help" to { HelpScreen(onBack = {}) },
            "info-privacy" to { PrivacyScreen(onBack = {}) },
            "info-about" to { AboutScreen(onBack = {}) }
        )
        screens.forEach { (name, screen) ->
            listOf(false, true).forEach { dark ->
                showAndShot(
                    "secondary/$name-${if (dark) "dark" else "light"}",
                    SakinahPalette.EMERALD_SANCTUARY,
                    dark,
                    content = screen
                )
            }
        }
    }
}
