package com.sakinah.deen.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.sakinah.deen.R
import com.sakinah.deen.ui.screens.AboutScreen
import com.sakinah.deen.ui.screens.AiChatScreen
import com.sakinah.deen.ui.screens.AuthScreen
import com.sakinah.deen.ui.screens.DataSourcesScreen
import com.sakinah.deen.ui.screens.HadithScreen
import com.sakinah.deen.ui.screens.HelpScreen
import com.sakinah.deen.ui.screens.OnboardingScreen
import com.sakinah.deen.ui.screens.PrivacyScreen
import com.sakinah.deen.ui.screens.ProfileScreen
import com.sakinah.deen.ui.screens.QuranScreen
import com.sakinah.deen.ui.screens.SalahScreen
import com.sakinah.deen.ui.screens.SettingsScreen
import com.sakinah.deen.ui.screens.ToolsHubScreen
import com.sakinah.deen.ui.screens.TravelScreen
import com.sakinah.deen.ui.screens.VisualSalahGuideScreen
import com.sakinah.deen.ui.screens.WealthScreen
import com.sakinah.deen.ui.viewmodel.AiScholarViewModel
import com.sakinah.deen.ui.viewmodel.HadithViewModel
import com.sakinah.deen.ui.viewmodel.QuranViewModel
import com.sakinah.deen.ui.viewmodel.SalahViewModel
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
    About(R.string.app_name)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootNavHost() {
    var currentScreen by rememberSaveable { mutableStateOf(SakinahScreen.DailySalah) }
    val backStack = remember { mutableStateListOf<SakinahScreen>() }

    var targetSurahNumber by rememberSaveable { mutableIntStateOf(1) }
    var targetAyahNumber by rememberSaveable { mutableIntStateOf(1) }
    var quranDeepLinkNonce by rememberSaveable { mutableIntStateOf(0) }

    var targetHadithBookKey by rememberSaveable { mutableStateOf("all") }
    var targetHadithNumber by rememberSaveable { mutableIntStateOf(0) }
    var targetHadithSearchQuery by rememberSaveable { mutableStateOf("") }
    var hadithDeepLinkNonce by rememberSaveable { mutableIntStateOf(0) }

    var guidePrayerTitle by rememberSaveable { mutableStateOf("Prayer") }
    var guideTotalRakats by rememberSaveable { mutableIntStateOf(2) }

    // Hoist primary ViewModels so tab switches retain loaded schedules, timelines, and chat streams.
    val koinStarted = remember { GlobalContext.getOrNull() != null }
    val salahVm: SalahViewModel? = if (koinStarted) koinViewModel() else null
    val quranVm: QuranViewModel? = if (koinStarted) koinViewModel() else null
    val hadithVm: HadithViewModel? = if (koinStarted) koinViewModel() else null
    val aiScholarVm: AiScholarViewModel? = if (koinStarted) koinViewModel() else null

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
        } else if (currentScreen != SakinahScreen.DailySalah) {
            currentScreen = SakinahScreen.DailySalah
        }
    }

    BackHandler(enabled = backStack.isNotEmpty() || currentScreen != SakinahScreen.DailySalah) {
        navigateBack()
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
        SakinahScreen.Onboarding -> "Welcome to Sakinah"
    }

    Scaffold(
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
                    }
                )
            }
        },
        bottomBar = {
            if (currentScreen in topLevelTabs) {
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
            modifier = Modifier.padding(innerPadding)
        ) { screen ->
            when (screen) {
                SakinahScreen.Onboarding -> {
                    OnboardingScreen(
                        onContinue = {
                            backStack.clear()
                            currentScreen = SakinahScreen.DailySalah
                        }
                    )
                }
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
                        viewModel = quranVm
                    )
                }
                SakinahScreen.Hadith -> {
                    HadithScreen(
                        initialBookKey = targetHadithBookKey,
                        initialHadithNumber = targetHadithNumber,
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
                        onNavigateToHadithDetail = { bookKey, number ->
                            targetHadithBookKey = bookKey
                            targetHadithNumber = number
                            targetHadithSearchQuery = ""
                            hadithDeepLinkNonce++
                            navigateTo(SakinahScreen.Hadith)
                        },
                        viewModel = aiScholarVm
                    )
                }
                SakinahScreen.Auth -> {
                    AuthScreen(onDone = { navigateBack() })
                }
                SakinahScreen.Profile -> {
                    ProfileScreen(onBack = { navigateBack() })
                }
                SakinahScreen.Settings -> {
                    SettingsScreen(onBack = { navigateBack() })
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
            }
        }
    }
}
