package com.sakinah.deen.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.sakinah.deen.R
import com.sakinah.deen.ui.screens.AiChatScreen
import com.sakinah.deen.ui.screens.HadithScreen
import com.sakinah.deen.ui.screens.OnboardingScreen
import com.sakinah.deen.ui.screens.QuranScreen
import com.sakinah.deen.ui.screens.SalahScreen
import com.sakinah.deen.ui.screens.TravelScreen
import com.sakinah.deen.ui.screens.VisualSalahGuideScreen
import com.sakinah.deen.ui.screens.WealthScreen
import com.sakinah.deen.ui.viewmodel.SalahViewModel
import org.koin.androidx.compose.koinViewModel

enum class SakinahScreen(@StringRes val titleRes: Int) {
    Onboarding(R.string.app_name),
    DailySalah(R.string.nav_salah),
    VisualGuide(R.string.nav_guide),
    Quran(R.string.nav_quran),
    Hadith(R.string.nav_hadith),
    Travel(R.string.nav_travel),
    Wealth(R.string.nav_wealth),
    AiScholar(R.string.nav_scholar)
}

@Composable
fun RootNavHost() {
    var currentScreen by remember { mutableStateOf(SakinahScreen.DailySalah) }
    var targetSurahNumber by remember { mutableStateOf(1) }
    var targetAyahNumber by remember { mutableStateOf(1) }
    var targetHadithBookKey by remember { mutableStateOf("all") }
    var targetHadithSearchQuery by remember { mutableStateOf("") }

    val salahLabel = stringResource(R.string.nav_salah)
    val quranLabel = stringResource(R.string.nav_quran)
    val hadithLabel = stringResource(R.string.nav_hadith)
    val safarLabel = stringResource(R.string.nav_travel)
    val zakatLabel = stringResource(R.string.nav_wealth)
    val scholarLabel = stringResource(R.string.nav_scholar)

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == SakinahScreen.DailySalah,
                    onClick = { currentScreen = SakinahScreen.DailySalah },
                    icon = { Icon(Icons.Default.Mosque, contentDescription = salahLabel) },
                    label = { Text(salahLabel) }
                )
                NavigationBarItem(
                    selected = currentScreen == SakinahScreen.Quran,
                    onClick = { currentScreen = SakinahScreen.Quran },
                    icon = { Icon(Icons.Default.Book, contentDescription = quranLabel) },
                    label = { Text(quranLabel) }
                )
                NavigationBarItem(
                    selected = currentScreen == SakinahScreen.Hadith,
                    onClick = { currentScreen = SakinahScreen.Hadith },
                    icon = { Icon(Icons.Default.LibraryBooks, contentDescription = hadithLabel) },
                    label = { Text(hadithLabel) }
                )
                NavigationBarItem(
                    selected = currentScreen == SakinahScreen.Travel,
                    onClick = { currentScreen = SakinahScreen.Travel },
                    icon = { Icon(Icons.Default.Explore, contentDescription = safarLabel) },
                    label = { Text(safarLabel) }
                )
                NavigationBarItem(
                    selected = currentScreen == SakinahScreen.Wealth,
                    onClick = { currentScreen = SakinahScreen.Wealth },
                    icon = { Icon(Icons.Default.MonetizationOn, contentDescription = zakatLabel) },
                    label = { Text(zakatLabel) }
                )
                NavigationBarItem(
                    selected = currentScreen == SakinahScreen.AiScholar,
                    onClick = { currentScreen = SakinahScreen.AiScholar },
                    icon = { Icon(Icons.Default.Chat, contentDescription = scholarLabel) },
                    label = { Text(scholarLabel) }
                )
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            SakinahScreen.Onboarding -> {
                OnboardingScreen(
                    onContinue = { currentScreen = SakinahScreen.DailySalah },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            SakinahScreen.DailySalah -> {
                val salahVm = runCatching { koinViewModel<SalahViewModel>() }.getOrNull()
                SalahScreen(
                    onOpenGuide = { currentScreen = SakinahScreen.VisualGuide },
                    viewModel = salahVm,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            SakinahScreen.VisualGuide -> {
                VisualSalahGuideScreen(
                    onBack = { currentScreen = SakinahScreen.DailySalah },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            SakinahScreen.Quran -> {
                QuranScreen(
                    initialSurahNumber = targetSurahNumber,
                    initialAyahNumber = targetAyahNumber,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            SakinahScreen.Hadith -> {
                HadithScreen(
                    initialBookKey = targetHadithBookKey,
                    initialSearchQuery = targetHadithSearchQuery,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            SakinahScreen.Travel -> {
                TravelScreen(
                    modifier = Modifier.padding(innerPadding)
                )
            }
            SakinahScreen.Wealth -> {
                WealthScreen(
                    modifier = Modifier.padding(innerPadding)
                )
            }
            SakinahScreen.AiScholar -> {
                AiChatScreen(
                    onNavigateToQuran = {
                        targetSurahNumber = 1
                        targetAyahNumber = 1
                        currentScreen = SakinahScreen.Quran
                    },
                    onNavigateToHadith = {
                        targetHadithBookKey = "all"
                        targetHadithSearchQuery = ""
                        currentScreen = SakinahScreen.Hadith
                    },
                    onNavigateToQuranDetail = { surah, ayah ->
                        targetSurahNumber = surah
                        targetAyahNumber = ayah
                        currentScreen = SakinahScreen.Quran
                    },
                    onNavigateToHadithDetail = { bookKey, number ->
                        targetHadithBookKey = bookKey
                        targetHadithSearchQuery = number.toString()
                        currentScreen = SakinahScreen.Hadith
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
