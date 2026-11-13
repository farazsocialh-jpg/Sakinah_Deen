package com.sakinah.deen.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.sakinah.deen.R
import com.sakinah.deen.auth.AuthRepository
import com.sakinah.deen.auth.AuthState
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.ui.screens.AboutScreen
import com.sakinah.deen.ui.screens.AuthScreen
import com.sakinah.deen.ui.screens.DataSourcesScreen
import com.sakinah.deen.ui.screens.HelpScreen
import com.sakinah.deen.ui.screens.PrivacyScreen
import com.sakinah.deen.ui.screens.ProfileScreen
import com.sakinah.deen.ui.screens.SettingsScreen
import com.sakinah.deen.ui.screens.AiChatScreen
import kotlinx.coroutines.launch
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
    AiScholar(R.string.nav_scholar),
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
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by rememberSaveable { mutableStateOf(SakinahScreen.DailySalah) }
    var targetSurahNumber by rememberSaveable { mutableStateOf(1) }
    var targetAyahNumber by rememberSaveable { mutableStateOf(1) }
    var targetHadithBookKey by rememberSaveable { mutableStateOf("all") }
    var targetHadithSearchQuery by rememberSaveable { mutableStateOf("") }

    val salahLabel = stringResource(R.string.nav_salah)
    val quranLabel = stringResource(R.string.nav_quran)
    val hadithLabel = stringResource(R.string.nav_hadith)
    val safarLabel = stringResource(R.string.nav_travel)
    val zakatLabel = stringResource(R.string.nav_wealth)
    val scholarLabel = stringResource(R.string.nav_scholar)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                val account = rememberInject<AuthRepository>()
                val authState by account.authState.collectAsStateWithLifecycle()
                val signedInUser = (authState as? AuthState.Authenticated)?.user
                Text(signedInUser?.displayName ?: "Sakinah", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                if (signedInUser != null) {
                    Text(signedInUser.email, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                } else {
                    Text("Guest mode — sign in to sync", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                }
                NavigationDrawerItem(
                    label = { Text(safarLabel) },
                    selected = currentScreen == SakinahScreen.Travel,
                    icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = SakinahScreen.Travel
                    }
                )
                NavigationDrawerItem(
                    label = { Text(zakatLabel) },
                    selected = currentScreen == SakinahScreen.Wealth,
                    icon = { Icon(Icons.Default.MonetizationOn, contentDescription = null) },
                    onClick = {
                        scope.launch { drawerState.close() }
                        currentScreen = SakinahScreen.Wealth
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                NavigationDrawerItem(label = { Text(if (signedInUser != null) "Profile & Account" else "Sign In / Create Account") }, selected = false, onClick = { scope.launch { drawerState.close() }; currentScreen = if (signedInUser != null) SakinahScreen.Profile else SakinahScreen.Auth })
                NavigationDrawerItem(label = { Text("App Settings") }, selected = false, onClick = { scope.launch { drawerState.close() }; currentScreen = SakinahScreen.Settings })
                NavigationDrawerItem(label = { Text("Islamic Data Sources") }, selected = false, onClick = { scope.launch { drawerState.close() }; currentScreen = SakinahScreen.DataSources })
                NavigationDrawerItem(label = { Text("Help & Feedback") }, selected = false, onClick = { scope.launch { drawerState.close() }; currentScreen = SakinahScreen.Help })
                NavigationDrawerItem(label = { Text("Privacy & Terms") }, selected = false, onClick = { scope.launch { drawerState.close() }; currentScreen = SakinahScreen.Privacy })
                NavigationDrawerItem(label = { Text("About Sakinah") }, selected = false, onClick = { scope.launch { drawerState.close() }; currentScreen = SakinahScreen.About })
                if (signedInUser != null) {
                    NavigationDrawerItem(
                        label = { Text("Sign Out") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close(); account.signOut() }
                            currentScreen = SakinahScreen.DailySalah
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Sakinah") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            },
            bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == SakinahScreen.DailySalah,
                    onClick = { if (currentScreen != SakinahScreen.DailySalah) currentScreen = SakinahScreen.DailySalah },
                    icon = { Icon(Icons.Default.Mosque, contentDescription = salahLabel) },
                    label = { Text(salahLabel) }
                )
                NavigationBarItem(
                    selected = currentScreen == SakinahScreen.Quran,
                    onClick = { if (currentScreen != SakinahScreen.Quran) currentScreen = SakinahScreen.Quran },
                    icon = { Icon(Icons.Default.Book, contentDescription = quranLabel) },
                    label = { Text(quranLabel) }
                )
                NavigationBarItem(
                    selected = currentScreen == SakinahScreen.Hadith,
                    onClick = { if (currentScreen != SakinahScreen.Hadith) currentScreen = SakinahScreen.Hadith },
                    icon = { Icon(Icons.Default.LibraryBooks, contentDescription = hadithLabel) },
                    label = { Text(hadithLabel) }
                )
                NavigationBarItem(
                    selected = currentScreen == SakinahScreen.AiScholar,
                    onClick = { if (currentScreen != SakinahScreen.AiScholar) currentScreen = SakinahScreen.AiScholar },
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
            SakinahScreen.Auth -> {
                AuthScreen(onDone = { currentScreen = SakinahScreen.DailySalah }, modifier = Modifier.padding(innerPadding))
            }
            SakinahScreen.Profile -> {
                ProfileScreen(onBack = { currentScreen = SakinahScreen.DailySalah }, modifier = Modifier.padding(innerPadding))
            }
            SakinahScreen.Settings -> {
                SettingsScreen(onBack = { currentScreen = SakinahScreen.DailySalah }, modifier = Modifier.padding(innerPadding))
            }
            SakinahScreen.DataSources -> {
                DataSourcesScreen(onBack = { currentScreen = SakinahScreen.DailySalah }, modifier = Modifier.padding(innerPadding))
            }
            SakinahScreen.Help -> {
                HelpScreen(onBack = { currentScreen = SakinahScreen.DailySalah }, modifier = Modifier.padding(innerPadding))
            }
            SakinahScreen.Privacy -> {
                PrivacyScreen(onBack = { currentScreen = SakinahScreen.DailySalah }, modifier = Modifier.padding(innerPadding))
            }
            SakinahScreen.About -> {
                AboutScreen(onBack = { currentScreen = SakinahScreen.DailySalah }, modifier = Modifier.padding(innerPadding))
            }
        }
    }
    }
}
