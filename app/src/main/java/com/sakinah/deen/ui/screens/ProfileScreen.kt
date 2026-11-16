package com.sakinah.deen.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.auth.AuthRepository
import com.sakinah.deen.auth.AuthState
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.profile.UserProfile
import com.sakinah.deen.profile.UserProfileRepository
import com.sakinah.deen.ui.components.DateOfBirthField
import com.sakinah.deen.ui.components.GenderSelector
import com.sakinah.deen.ui.components.HomeLocationCityPickerDialog
import com.sakinah.deen.ui.components.ProfileAvatar
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.ui.theme.PrayerGuideGender
import kotlinx.coroutines.launch

/**
 * Profile & account: shows the Google avatar and every collected field, and lets the user edit
 * them at any time. Email is the account identity for local accounts, so it stays read-only.
 */
@Composable
fun ProfileScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val authRepo = rememberInject<AuthRepository>()
    val profileRepo = rememberInject<UserProfileRepository>()
    val appearance = rememberInject<AppearancePreferences>()
    val appPrefs = rememberInject<SakinahPreferences>()

    val authState by authRepo.authState.collectAsStateWithLifecycle()
    val profile by profileRepo.profile.collectAsStateWithLifecycle()
    val signedInUser = (authState as? AuthState.Authenticated)?.user

    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var seeded by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var showSignOut by remember { mutableStateOf(false) }
    var showHomeCityPicker by remember { mutableStateOf(false) }
    // Set when the user picks a city so saving also updates the shared home anchor.
    var pickedHome by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(profile, signedInUser?.id) {
        if (seeded) return@LaunchedEffect
        val current = profile ?: return@LaunchedEffect
        displayName = current.displayName.ifBlank { signedInUser?.displayName.orEmpty() }
        email = current.email.ifBlank { signedInUser?.email.orEmpty() }
        gender = current.gender
        dateOfBirth = current.dateOfBirth
        city = appPrefs.homeCityName
            ?: current.city.ifBlank { current.defaultCity }
        country = current.country
        seeded = true
    }

    val avatarUrl = profile?.pictureUrl()?.takeIf { it.isNotBlank() }
        ?: signedInUser?.avatarUrl
    val age = remember(dateOfBirth) { UserProfile(dateOfBirth = dateOfBirth).ageYears() }
    val providerLabel = when (profile?.authProvider) {
        "google" -> "Google account"
        "guest" -> "Guest session"
        else -> "Local account"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Profile & Account",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(photoUrl = avatarUrl, displayName = displayName.ifBlank { email })
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = displayName.ifBlank { "Your account" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (email.isNotBlank()) {
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = providerLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (profile?.isProfileComplete() != true) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Add your gender, date of birth, city and country to tailor " +
                                "prayer times and the Salah guide.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                validationError?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it; validationError = null },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .autofillName()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; validationError = null },
                    label = { Text("Email") },
                    singleLine = true,
                    readOnly = signedInUser?.isGuest == false,
                    supportingText = if (signedInUser?.isGuest == false) {
                        { Text("Email is tied to your sign-in method.") }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .autofillEmail()
                )

                GenderSelector(gender = gender, onGenderChange = { gender = it })

                DateOfBirthField(dateOfBirth = dateOfBirth, onDateChange = { dateOfBirth = it })

                if (age != null) {
                    Text(
                        text = "Age: $age",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // One home-location control shared with Prayer & Location settings: picking a
                // city here writes the same saved anchor used for prayer times and travel.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = city,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Home city") },
                        singleLine = true,
                        supportingText = { Text("Shared with Prayer & Location settings") },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showHomeCityPicker = true }) { Text("Change") }
                }
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it; validationError = null },
                    label = { Text("Country") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (isSaving) return@Button
                        val cleanName = displayName.trim()
                        if (cleanName.isEmpty()) {
                            validationError = "Display name cannot be empty."
                            return@Button
                        }
                        val cleanCity = city.trim()
                        val cleanCountry = country.trim()
                        scope.launch {
                            isSaving = true
                            try {
                                val updatedProfile = (profile ?: UserProfile()).copy(
                                    displayName = cleanName,
                                    email = email.trim(),
                                    gender = gender,
                                    dateOfBirth = dateOfBirth,
                                    city = cleanCity,
                                    country = cleanCountry,
                                    defaultCity = cleanCity
                                )
                                profileRepo.update(updatedProfile)
                                pickedHome?.let { (latitude, longitude) ->
                                    appPrefs.saveHomeAnchor(latitude, longitude, cleanCity)
                                }
                                pickedHome = null
                                appearance.update {
                                    it.copy(
                                        userName = cleanName,
                                        prayerGuideGender = when (gender) {
                                            "MALE" -> PrayerGuideGender.MALE
                                            "FEMALE" -> PrayerGuideGender.FEMALE
                                            else -> it.prayerGuideGender
                                        }
                                    )
                                }
                                if (signedInUser != null && !signedInUser.isGuest) {
                                    authRepo.updateDisplayName(cleanName)
                                }
                                snackbarHostState.showSnackbar("Profile saved.")
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                validationError = e.message ?: "Could not save your profile."
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(if (isSaving) "Saving…" else "Save Changes")
                }

                // Every account lifecycle action lives here, not in the tools hub.
                OutlinedButton(
                    onClick = { showSignOut = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign Out")
                }

                OutlinedButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Account")
                }

                if (showSignOut) {
                    AlertDialog(
                        onDismissRequest = { showSignOut = false },
                        title = { Text("Sign out?") },
                        text = {
                            Text("Your saved Quran, Hadith and Salah progress stays on this device.")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showSignOut = false
                                scope.launch {
                                    runCatching { authRepo.signOut() }
                                        .onFailure { err ->
                                            if (err is kotlinx.coroutines.CancellationException) throw err
                                            validationError = err.message ?: "Could not sign out."
                                        }
                                    profileRepo.clear()
                                    onBack()
                                }
                            }) { Text("Sign Out") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSignOut = false }) { Text("Cancel") }
                        }
                    )
                }

                if (showDelete) {
                    AlertDialog(
                        onDismissRequest = { showDelete = false },
                        title = { Text("Delete account?") },
                        text = { Text("This action cannot be undone.") },
                        confirmButton = {
                            TextButton(onClick = {
                                scope.launch {
                                    authRepo.deleteAccount()
                                    profileRepo.clear()
                                    showDelete = false
                                    onBack()
                                }
                            }) { Text("Delete") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDelete = false }) { Text("Cancel") }
                        }
                    )
                }

            }
        }
    }

    if (showHomeCityPicker) {
        HomeLocationCityPickerDialog(
            onDismiss = { showHomeCityPicker = false },
            onCitySelected = { picked ->
                city = picked.name
                pickedHome = picked.latitude to picked.longitude
                showHomeCityPicker = false
            }
        )
    }
}
