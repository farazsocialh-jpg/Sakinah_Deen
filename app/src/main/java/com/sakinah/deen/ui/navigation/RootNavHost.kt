package com.sakinah.deen.ui.navigation

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
import com.sakinah.deen.ui.screens.AiChatScreen
import com.sakinah.deen.ui.screens.HadithScreen
import com.sakinah.deen.ui.screens.OnboardingScreen
import com.sakinah.deen.ui.screens.QuranScreen
import com.sakinah.deen.ui.screens.SalahScreen
import com.sakinah.deen.ui.screens.TravelScreen
import com.sakinah.deen.ui.screens.VisualSalahGuideScreen
import com.sakinah.deen.ui.screens.WealthScreen

enum class SakinahScreen(val title: String) {
    Onboarding("Journey"),
    DailySalah("Salah"),
    VisualGuide("Guide"),
    Quran("Quran"),
    Hadith("Hadith"),
    Travel("Safar"),
    Wealth("Zakat"),
    AiScholar("Scholar")
}

@Composable
fun RootNavHost() {
    var currentScreen by remember { mutableStateOf(SakinahScreen.DailySalah) }
    var targetSurahNumber by remember { mutableStateOf(1) }
    var targetAyahNumber by remember { mutableStateOf(1) }
    var targetHadithBookKey by remember { mutableStateOf("all") }
    var targetHadithSearchQuery by remember { mutableStateOf("") }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == SakinahScreen.DailySalah,
                    onClick = { currentScreen = SakinahScreen.DailySalah },
                    icon = { Icon(Icons.Default.Mosque, contentDescription = "Salah") },
                    label = { Text("Salah") }
                )
                NavigationBarItem(
                    selected = currentScreen == SakinahScreen.Quran,
                    onClick = { currentScreen = SakinahScreen.Quran },
                    icon = { Icon(Icons.Default.Book, contentDescription = "Quran") },
                    label = { Text("Quran") }
                )
                NavigationBarItem(
                    selected = currentScreen == SakinahScreen.Hadith,
                    onClick = { currentScreen = SakinahScreen.Hadith },
                    icon = { Icon(Icons.Default.LibraryBooks, contentDescription = "Hadith") },
                    label = { Text("Hadith") }
                )
                NavigationBarItem(
                    selected = currentScreen == SakinahScreen.Travel,
                    onClick = { currentScreen = SakinahScreen.Travel },
                    icon = { Icon(Icons.Default.Explore, contentDescription = "Safar") },
                    label = { Text("Safar") }
                )
                NavigationBarItem(
                    selected = currentScreen == SakinahScreen.Wealth,
                    onClick = { currentScreen = SakinahScreen.Wealth },
                    icon = { Icon(Icons.Default.MonetizationOn, contentDescription = "Zakat") },
                    label = { Text("Zakat") }
                )
                NavigationBarItem(
                    selected = currentScreen == SakinahScreen.AiScholar,
                    onClick = { currentScreen = SakinahScreen.AiScholar },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Scholar") },
                    label = { Text("Scholar") }
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
                SalahScreen(
                    onOpenGuide = { currentScreen = SakinahScreen.VisualGuide },
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
