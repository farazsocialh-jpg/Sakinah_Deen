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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
    Profile(R.string.app_name),
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
fun RootNavHost() {
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
            appearancePrefs.setPreview(appearanceConfig)
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
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!autoHideBars) return Offset.Zero
                if (available.y < -10f) {
                    bottomBarHidden = true
                } else if (available.y > 10f) {
                    bottomBarHidden = false
                }
                return Offset.Zero
            }
        }
    }

    val revealOnTapModifier = if (autoHideBars && bottomBarHidden) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                if (up != null) {
                    val dx = up.position.x - down.position.x
                    val dy = up.position.y - down.position.y
                    if (dx * dx + dy * dy < 400f) {
                        bottomBarHidden = false
                        scrollBehavior.state.heightOffset = 0f
                    }
                }
            }
        }
    } else {
        Modifier
    }

    val salahLabel = stringResource(R.string.nav_salah)
    val quranLabel = stringResource(R.string.nav_quran)
    val hadithLabel = stringResource(R.string.nav_hadith)
    val scholarLabel = stringResource(R.string.nav_scholar)
    val toolsLabel = stringResource(R.string.nav_tools)

    val topBarTitle = when (currentScreen) {
        SakinahScreen.DailySalah -> "Sakinah"
        SakinahScreen.Quran -> quranLabel
        SakinahScreen.Hadith -> hadithLabel
        SakinahScreen.AiScholar -> "Sunni AI Scholar"
        SakinahScreen.ToolsHub -> toolsLabel
        SakinahScreen.VisualGuide -> "Visual Salah Guide"
        SakinahScreen.Travel -> "Safar & Qibla"
        SakinahScreen.Wealth -> "Zakat & Sadaqah"
        SakinahScreen.Auth -> "Account"
        SakinahScreen.Profile -> "Profile & Account"
        SakinahScreen.Settings -> "Settings"
        SakinahScreen.DataSources -> "Islamic Data Sources"
        SakinahScreen.Help -> "Help & Feedback"
        SakinahScreen.Privacy -> "Privacy & Terms"
        SakinahScreen.About -> "About Sakinah"
        SakinahScreen.NotificationSettings -> "Azaan & Notifications"
        SakinahScreen.Onboarding -> "Welcome to Sakinah"
    }

    val scaffoldScrollModifier = if (autoHideBars) {
        Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .nestedScroll(bottomBarScrollConnection)
            .then(revealOnTapModifier)
    } else {
        Modifier
    }

    Scaffold(
        modifier = scaffoldScrollModifier,
        topBar = {
            if (currentScreen != SakinahScreen.Onboarding) {
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
                    scrollBehavior = if (autoHideBars) scrollBehavior else null
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
                        contentPadding = innerPadding
                    )
                }
                SakinahScreen.Auth -> {
                    AuthScreen(onDone = { navigateBack() })
                }
                SakinahScreen.Profile -> {
                    ProfileScreen(onBack = { navigateBack() })
                }
                SakinahScreen.Settings -> {
                    SettingsScreen(
                        onBack = { navigateBack() },
                        onRerunOnboarding = { rerunOnboarding = true },
                        onOpenNotificationSettings = {
                            navigateTo(SakinahScreen.NotificationSettings)
                        }
                    )
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
                    NotificationSettingsScreen(onBack = { navigateBack() })
                }
            }
        }
    }
}
