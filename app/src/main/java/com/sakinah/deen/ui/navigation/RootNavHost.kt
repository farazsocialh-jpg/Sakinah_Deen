package com.sakinah.deen.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.R
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.domain.astronomy.UserLocationProvider
import com.sakinah.deen.service.AzaanScheduling
import com.sakinah.deen.settings.SettingsRepository
import com.sakinah.deen.settings.ThemeMode as SharedThemeMode
import com.sakinah.deen.ui.screens.AboutScreen
import com.sakinah.deen.ui.screens.AiChatScreen
import com.sakinah.deen.ui.screens.AuthScreen
import com.sakinah.deen.ui.screens.CompleteProfileScreen
import com.sakinah.deen.ui.screens.DataSourcesScreen
import com.sakinah.deen.ui.screens.HadithScreen
import com.sakinah.deen.ui.screens.HelpScreen
import com.sakinah.deen.ui.screens.NotificationSettingsScreen
import com.sakinah.deen.ui.screens.OnboardingScreen
import com.sakinah.deen.ui.screens.PersonalizedOnboardingScreen
import com.sakinah.deen.ui.screens.PrivacyScreen
import com.sakinah.deen.ui.screens.ProfileScreen
import com.sakinah.deen.ui.screens.QuranScreen
import com.sakinah.deen.ui.screens.SalahScreen
import com.sakinah.deen.ui.screens.SettingsSection
import com.sakinah.deen.ui.screens.SettingsScreen
import com.sakinah.deen.ui.screens.ToolsHubScreen
import com.sakinah.deen.ui.screens.TravelScreen
import com.sakinah.deen.ui.screens.VisualSalahGuideScreen
import com.sakinah.deen.ui.screens.WealthScreen
import com.sakinah.deen.ui.theme.ThemeMode as UiThemeMode
import com.sakinah.deen.ui.theme.UserObjective
import com.sakinah.deen.ui.viewmodel.AiScholarViewModel
import com.sakinah.deen.ui.viewmodel.HadithViewModel
import com.sakinah.deen.ui.viewmodel.QuranViewModel
import com.sakinah.deen.ui.viewmodel.SalahViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.context.GlobalContext

enum class SakinahScreen(@StringRes val titleRes: Int) {
    Onboarding(R.string.app_name),
    DailySalah(R.string.nav_salah),
    VisualGuide(R.string.nav_guide),
    Quran(R.string.nav_quran),
    Hadith(R.string.nav_hadith),
    Travel(R.string.nav_travel),
    Wealth(R.string.nav_wealth),
    AiScholar(R.string.nav_scholar),
    ToolsHub(R.string.nav_tools),
    Auth(R.string.app_name),
    CompleteProfile(R.string.app_name),
    Profile(R.string.app_name),
    /** Prayer, location and calculation preferences. */
    SettingsPrayer(R.string.app_name),
    /** Theme, palette, typography and reader layouts. */
    SettingsAppearance(R.string.app_name),
    /** Quran translation edition, tafseer packs and cache. */
    SettingsQuran(R.string.app_name),
    /** Legacy alias kept so a restored back stack from an older build still resolves. */
    Settings(R.string.app_name),
    DataSources(R.string.app_name),
    Help(R.string.app_name),
    Privacy(R.string.app_name),
    About(R.string.app_name),
    NotificationSettings(R.string.app_name)
}

private fun startScreenForObjective(objective: UserObjective): SakinahScreen =
    when (objective.recommendedStartTab) {
        "Quran" -> SakinahScreen.Quran
        "Hadith" -> SakinahScreen.Hadith
        else -> SakinahScreen.DailySalah
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RootNavHost(
    /** Screen requested by an incoming Intent extra (e.g. "travel" from the car prompt). */
    navigateToExtra: String? = null,
    onNavigateToExtraHandled: () -> Unit = {}
) {
    val koinStarted = remember { GlobalContext.getOrNull() != null }
    val context = LocalContext.current
    val appearancePrefs = remember(koinStarted, context) {
        if (koinStarted) {
            GlobalContext.get().get<AppearancePreferences>()
        } else {
            AppearancePreferences(context)
        }
    }
    val appearanceConfig by appearancePrefs.config.collectAsStateWithLifecycle()
    val settingsRepo: SettingsRepository? = remember(koinStarted) {
        if (koinStarted) GlobalContext.get().getOrNull<SettingsRepository>() else null
    }
    val appPrefs: SakinahPreferences? = remember(koinStarted, context) {
        if (koinStarted) GlobalContext.get().getOrNull<SakinahPreferences>() else null
    }
    val locationProvider: UserLocationProvider? = remember(koinStarted) {
        if (koinStarted) GlobalContext.get().getOrNull<UserLocationProvider>() else null
    }
    val scope = rememberCoroutineScope()

    var rerunOnboarding by rememberSaveable { mutableStateOf(false) }
    val showOnboarding = !appearanceConfig.onboardingCompleted || rerunOnboarding

    var currentScreen by rememberSaveable {
        mutableStateOf(startScreenForObjective(appearanceConfig.objective))
    }
    val backStackSaver: Saver<MutableList<SakinahScreen>, List<String>> = Saver(
        save = { it.map { screen -> screen.name } },
        restore = { saved ->
            mutableStateListOf<SakinahScreen>().apply {
                addAll(saved.map { name -> SakinahScreen.valueOf(name) })
            }
        }
    )
    val backStack = rememberSaveable(saver = backStackSaver) { mutableStateListOf<SakinahScreen>() }

    var targetSurahNumber by rememberSaveable { mutableIntStateOf(1) }
    var targetAyahNumber by rememberSaveable { mutableIntStateOf(1) }
    var quranDeepLinkNonce by rememberSaveable { mutableIntStateOf(0) }

    var targetHadithBookKey by rememberSaveable { mutableStateOf("all") }
    var targetHadithNumber by rememberSaveable { mutableIntStateOf(0) }
    var targetHadithSectionHint by rememberSaveable { mutableStateOf("") }
    var targetHadithSearchQuery by rememberSaveable { mutableStateOf("") }
    var hadithDeepLinkNonce by rememberSaveable { mutableIntStateOf(0) }

    var guidePrayerTitle by rememberSaveable { mutableStateOf("Prayer") }
    var guideTotalRakats by rememberSaveable { mutableIntStateOf(2) }

    if (showOnboarding) {
        // Cancel/X during a re-run must discard the live preview and restore the last saved
        // config, then return to Settings instead of trapping the user on Step 1.
        fun cancelOnboarding() {
            // Clearing the preview is essential: a non-null preview stays active forever
            // and silently blocks all later theme/layout updates.
            appearancePrefs.setPreview(null)
            rerunOnboarding = false
        }
        BackHandler(enabled = rerunOnboarding) { cancelOnboarding() }
        PersonalizedOnboardingScreen(
            initialConfig = appearanceConfig,
            onConfigPreviewChange = { draft -> appearancePrefs.setPreview(draft) },
            onDismiss = if (rerunOnboarding) {
                { cancelOnboarding() }
            } else {
                null
            },
            onComplete = { completedDraft ->
                val savedConfig = completedDraft.copy(onboardingCompleted = true)
                appearancePrefs.saveConfig(savedConfig)
                settingsRepo?.let { repo ->
                    val mappedTheme = when (savedConfig.themeMode) {
                        UiThemeMode.LIGHT -> SharedThemeMode.LIGHT
                        UiThemeMode.DARK -> SharedThemeMode.DARK
                        UiThemeMode.SYSTEM -> SharedThemeMode.SYSTEM
                    }
                    scope.launch {
                        repo.updateAppearance(repo.settings.value.appearance.copy(theme = mappedTheme))
                    }
                }
                appearancePrefs.setPreview(null)
                val wasRerun = rerunOnboarding
                rerunOnboarding = false
                backStack.clear()
                currentScreen = startScreenForObjective(savedConfig.objective)

                // Preserve the user's existing alarm preference on a re-run; only enable
                // by default for a first-time onboarding completion.
                if (!wasRerun) {
                    appPrefs?.alarmsEnabled = true
                }
                scope.launch {
                    AzaanScheduling.scheduleWithBestLocation(
                        context = context,
                        locationProvider = locationProvider
                    )
                }
            }
        )
        return
    }

    // Hoist primary ViewModels so tab switches retain loaded schedules, timelines, and chat streams.
    // Deferred until after the onboarding gate so they are not instantiated for first-run users.
    val salahVm: SalahViewModel? = if (koinStarted && !showOnboarding) koinViewModel() else null
    val quranVm: QuranViewModel? = if (koinStarted && !showOnboarding) koinViewModel() else null
    val hadithVm: HadithViewModel? = if (koinStarted && !showOnboarding) koinViewModel() else null
    val aiScholarVm: AiScholarViewModel? = if (koinStarted && !showOnboarding) koinViewModel() else null

    val topLevelTabs = remember {
        setOf(
            SakinahScreen.DailySalah,
            SakinahScreen.Quran,
            SakinahScreen.Hadith,
            SakinahScreen.AiScholar,
            SakinahScreen.ToolsHub
        )
    }

    val profileRepo = com.sakinah.deen.ui.rememberInject<com.sakinah.deen.profile.UserProfileRepository>()

    fun navigateTo(destination: SakinahScreen) {
        if (destination == currentScreen) return
        if (destination in topLevelTabs) {
            backStack.clear()
        } else {
            backStack.add(currentScreen)
        }
        currentScreen = destination
    }

    fun navigateBack() {
        if (backStack.isNotEmpty()) {
            currentScreen = backStack.removeAt(backStack.lastIndex)
        } else {
            val start = startScreenForObjective(appearanceConfig.objective)
            if (currentScreen != start) {
                currentScreen = start
            }
        }
    }

    // Notification taps carry a target screen (the car handshake asks for Travel). The extra
    // is consumed once so a recomposition or rotation does not re-navigate.
    LaunchedEffect(navigateToExtra) {
        if (navigateToExtra.isNullOrBlank()) return@LaunchedEffect
        val destination = when (navigateToExtra) {
            com.sakinah.deen.receiver.CarBluetoothReceiver.ACTION_OPEN_TRAVEL -> SakinahScreen.Travel
            else -> null
        }
        if (destination != null) navigateTo(destination)
        onNavigateToExtraHandled()
    }

    val startScreen = startScreenForObjective(appearanceConfig.objective)
    BackHandler(enabled = backStack.isNotEmpty() || currentScreen != startScreen) {
        navigateBack()
    }

    val autoHideBars = appearanceConfig.autoHideBarsOnScroll
    val imeVisible = WindowInsets.isImeVisible
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var bottomBarHidden by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(currentScreen, autoHideBars) {
        bottomBarHidden = false
        scrollBehavior.state.heightOffset = 0f
    }

    val bottomBarScrollConnection = remember(autoHideBars) {
        object : NestedScrollConnection {
            // React only to distance the child list actually consumed. Using onPreScroll
            // hid the bar even when a gesture could not move the list, and the old
            // Initial-pass tap detector intercepted scroll gestures on every screen.
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!autoHideBars) return Offset.Zero
                if (consumed.y < -2f) {
                    bottomBarHidden = true
                } else if (consumed.y > 2f) {
                    bottomBarHidden = false
                }
                return Offset.Zero
            }
        }
    }

    val salahLabel = stringResource(R.string.nav_salah)
    val quranLabel = stringResource(R.string.nav_quran)
    val hadithLabel = stringResource(R.string.nav_hadith)
    val scholarLabel = stringResource(R.string.nav_scholar)
    val toolsLabel = stringResource(R.string.nav_tools)

    val topBarTitle = when (currentScreen) {
        SakinahScreen.DailySalah -> "ImanCore"
        SakinahScreen.Quran -> quranLabel
        SakinahScreen.Hadith -> hadithLabel
        SakinahScreen.AiScholar -> "Islamic AI Scholar"
        SakinahScreen.ToolsHub -> toolsLabel
        SakinahScreen.VisualGuide -> "Visual Salah Guide"
        SakinahScreen.Travel -> "Safar & Qibla"
        SakinahScreen.Wealth -> "Zakat & Sadaqah"
        SakinahScreen.Auth -> "Account"
        SakinahScreen.CompleteProfile -> "Complete your profile"
        SakinahScreen.Profile -> "Profile & Account"
        SakinahScreen.SettingsPrayer -> "Prayer & Location"
        SakinahScreen.SettingsAppearance -> "Appearance & Reading"
        SakinahScreen.SettingsQuran -> "Quran & Offline Packs"
        SakinahScreen.Settings -> "Prayer & Location"
        SakinahScreen.DataSources -> "Islamic Data Sources"
        SakinahScreen.Help -> "Help & Feedback"
        SakinahScreen.Privacy -> "Privacy & Terms"
        SakinahScreen.About -> "About ImanCore"
        SakinahScreen.NotificationSettings -> "Azaan & Notifications"
        SakinahScreen.Onboarding -> "Welcome to ImanCore"
    }

    val showsRootTopBar = currentScreen != SakinahScreen.Onboarding &&
        currentScreen != SakinahScreen.DailySalah &&
        currentScreen != SakinahScreen.Quran &&
        currentScreen != SakinahScreen.Hadith &&
        currentScreen != SakinahScreen.AiScholar &&
        currentScreen != SakinahScreen.VisualGuide &&
        currentScreen != SakinahScreen.DataSources &&
        currentScreen != SakinahScreen.Help &&
        currentScreen != SakinahScreen.Privacy &&
        currentScreen != SakinahScreen.About

    // enterAlwaysScrollBehavior belongs only to screens that actually render the root
    // TopAppBar. Attaching its connection to screens without one lets the app bar offset
    // state participate in gestures and can consume otherwise valid list scroll.
    val scaffoldScrollModifier = when {
        autoHideBars && showsRootTopBar -> Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .nestedScroll(bottomBarScrollConnection)
        autoHideBars -> Modifier.nestedScroll(bottomBarScrollConnection)
        else -> Modifier
    }

    Scaffold(
        modifier = scaffoldScrollModifier,
        topBar = {
            if (showsRootTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            text = topBarTitle,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (currentScreen !in topLevelTabs) {
                            IconButton(onClick = { navigateBack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    scrollBehavior = if (autoHideBars && showsRootTopBar) scrollBehavior else null
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = currentScreen in topLevelTabs && (!autoHideBars || !bottomBarHidden) && !imeVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = currentScreen == SakinahScreen.DailySalah,
                        onClick = { navigateTo(SakinahScreen.DailySalah) },
                        icon = { Icon(Icons.Default.Mosque, contentDescription = salahLabel) },
                        label = { Text(salahLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == SakinahScreen.Quran,
                        onClick = { navigateTo(SakinahScreen.Quran) },
                        icon = { Icon(Icons.Default.Book, contentDescription = quranLabel) },
                        label = { Text(quranLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == SakinahScreen.Hadith,
                        onClick = { navigateTo(SakinahScreen.Hadith) },
                        icon = { Icon(Icons.Default.LibraryBooks, contentDescription = hadithLabel) },
                        label = { Text(hadithLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == SakinahScreen.AiScholar,
                        onClick = { navigateTo(SakinahScreen.AiScholar) },
                        icon = { Icon(Icons.Default.Chat, contentDescription = scholarLabel) },
                        label = { Text(scholarLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                    NavigationBarItem(
                        selected = currentScreen == SakinahScreen.ToolsHub,
                        onClick = { navigateTo(SakinahScreen.ToolsHub) },
                        icon = { Icon(Icons.Default.GridView, contentDescription = toolsLabel) },
                        label = { Text(toolsLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "SakinahScreenTransition",
            modifier = Modifier.padding(
                if (currentScreen == SakinahScreen.AiScholar) PaddingValues(0.dp) else innerPadding
            )
        ) { screen ->
            when (screen) {
                SakinahScreen.Onboarding -> { }
                SakinahScreen.DailySalah -> {
                    SalahScreen(
                        onOpenGuide = {
                            guidePrayerTitle = "Prayer"
                            guideTotalRakats = 2
                            navigateTo(SakinahScreen.VisualGuide)
                        },
                        onOpenPrayerGuide = { prayerName, rakats ->
                            guidePrayerTitle = prayerName
                            guideTotalRakats = rakats.coerceAtLeast(2)
                            navigateTo(SakinahScreen.VisualGuide)
                        },
                        focusMode = appearanceConfig.focusModeHideDistractions,
                        onOpenNotificationSettings = {
                            navigateTo(SakinahScreen.NotificationSettings)
                        },
                        viewModel = salahVm
                    )
                }
                SakinahScreen.VisualGuide -> {
                    VisualSalahGuideScreen(
                        onBack = { navigateBack() },
                        totalRakats = guideTotalRakats,
                        prayerTitle = guidePrayerTitle
                    )
                }
                SakinahScreen.Quran -> {
                    QuranScreen(
                        initialSurahNumber = targetSurahNumber,
                        initialAyahNumber = targetAyahNumber,
                        deepLinkNonce = quranDeepLinkNonce,
                        focusMode = appearanceConfig.focusModeHideDistractions,
                        viewModel = quranVm
                    )
                }
                SakinahScreen.Hadith -> {
                    HadithScreen(
                        initialBookKey = targetHadithBookKey,
                        initialHadithNumber = targetHadithNumber,
                        initialSectionHint = targetHadithSectionHint,
                        initialSearchQuery = targetHadithSearchQuery,
                        deepLinkNonce = hadithDeepLinkNonce,
                        viewModel = hadithVm
                    )
                }
                SakinahScreen.ToolsHub -> {
                    ToolsHubScreen(
                        onNavigate = { target -> navigateTo(target) }
                    )
                }
                SakinahScreen.Travel -> {
                    TravelScreen()
                }
                SakinahScreen.Wealth -> {
                    WealthScreen()
                }
                SakinahScreen.AiScholar -> {
                    AiChatScreen(
                        onNavigateToQuran = {
                            targetSurahNumber = 1
                            targetAyahNumber = 1
                            quranDeepLinkNonce++
                            navigateTo(SakinahScreen.Quran)
                        },
                        onNavigateToHadith = {
                            targetHadithBookKey = "all"
                            targetHadithNumber = 0
                            targetHadithSectionHint = ""
                            targetHadithSearchQuery = ""
                            hadithDeepLinkNonce++
                            navigateTo(SakinahScreen.Hadith)
                        },
                        onNavigateToQuranDetail = { surah, ayah ->
                            targetSurahNumber = surah
                            targetAyahNumber = ayah
                            quranDeepLinkNonce++
                            navigateTo(SakinahScreen.Quran)
                        },
                        onNavigateToHadithDetail = { bookKey, number, sectionHint ->
                            targetHadithBookKey = bookKey
                            targetHadithNumber = number
                            targetHadithSectionHint = sectionHint.orEmpty()
                            targetHadithSearchQuery = ""
                            hadithDeepLinkNonce++
                            navigateTo(SakinahScreen.Hadith)
                        },
                        viewModel = aiScholarVm,
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = if (imeVisible) 0.dp else innerPadding.calculateBottomPadding()
                        )
                    )
                }
                SakinahScreen.Auth -> {
                    AuthScreen(
                        onDone = {
                            // Sign-in only returns identity, so collect the rest once. Guests
                            // have no profile and go straight back.
                            val saved = profileRepo.profile.value
                            if (saved != null && !saved.isProfileComplete()) {
                                navigateTo(SakinahScreen.CompleteProfile)
                            } else {
                                navigateBack()
                            }
                        }
                    )
                }
                SakinahScreen.CompleteProfile -> {
                    CompleteProfileScreen(
                        onDone = {
                            // Leave both the completion step and the auth screen behind.
                            navigateBack()
                            navigateBack()
                        },
                        onSkip = {
                            navigateBack()
                            navigateBack()
                        }
                    )
                }
                SakinahScreen.Profile -> {
                    ProfileScreen(onBack = { navigateBack() })
                }
                SakinahScreen.SettingsPrayer -> {
                    SettingsScreen(section = SettingsSection.PRAYER_LOCATION)
                }
                SakinahScreen.SettingsAppearance -> {
                    SettingsScreen(
                        section = SettingsSection.APPEARANCE_READING,
                        onRerunOnboarding = { rerunOnboarding = true }
                    )
                }
                SakinahScreen.SettingsQuran -> {
                    SettingsScreen(section = SettingsSection.QURAN_OFFLINE)
                }
                // Older builds could persist this route; keep it working as Prayer & Location.
                SakinahScreen.Settings -> {
                    SettingsScreen(section = SettingsSection.PRAYER_LOCATION)
                }
                SakinahScreen.DataSources -> {
                    DataSourcesScreen(onBack = { navigateBack() })
                }
                SakinahScreen.Help -> {
                    HelpScreen(onBack = { navigateBack() })
                }
                SakinahScreen.Privacy -> {
                    PrivacyScreen(onBack = { navigateBack() })
                }
                SakinahScreen.About -> {
                    AboutScreen(onBack = { navigateBack() })
                }
                SakinahScreen.NotificationSettings -> {
                    NotificationSettingsScreen()
                }
            }
        }
    }
}
