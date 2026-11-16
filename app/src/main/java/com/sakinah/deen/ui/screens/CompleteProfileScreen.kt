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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
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
import com.sakinah.deen.ui.components.ProfileAvatar
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.ui.theme.PrayerGuideGender
import com.sakinah.deen.util.ReverseGeocoder
import kotlinx.coroutines.launch

/**
 * Post-sign-in profile completion.
 *
 * Google Sign-In supplies identity only, so gender, date of birth, city and country are
 * collected here. Nothing is mandatory to keep using the app: "Skip for now" always works.
 */
@Composable
fun CompleteProfileScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onSkip: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val authRepo = rememberInject<AuthRepository>()
    val profileRepo = rememberInject<UserProfileRepository>()
    val appearance = rememberInject<AppearancePreferences>()
    val prefs = rememberInject<SakinahPreferences>()

    val authState by authRepo.authState.collectAsStateWithLifecycle()
    val profile by profileRepo.profile.collectAsStateWithLifecycle()
    val appearanceConfig by appearance.config.collectAsStateWithLifecycle()
    val signedInUser = (authState as? AuthState.Authenticated)?.user

    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var seeded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var geocodeStatus by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    // Seed once from the profile so later repository emissions do not clobber typing.
    LaunchedEffect(profile, signedInUser?.id) {
        if (seeded) return@LaunchedEffect
        val current = profile ?: return@LaunchedEffect
        displayName = current.displayName.ifBlank { signedInUser?.displayName.orEmpty() }
        email = current.email.ifBlank { signedInUser?.email.orEmpty() }
        gender = current.gender.ifBlank {
            when (appearanceConfig.prayerGuideGender) {
                PrayerGuideGender.MALE -> "MALE"
                PrayerGuideGender.FEMALE -> "FEMALE"
            }
        }
        dateOfBirth = current.dateOfBirth
        city = current.city.ifBlank { current.defaultCity }
        country = current.country
        seeded = true
    }

    // City/country cannot come from Google; derive them from the saved prayer location once.
    LaunchedEffect(seeded, city, country) {
        if (!seeded) return@LaunchedEffect
        if (city.isNotBlank() && country.isNotBlank()) return@LaunchedEffect
        val latitude = prefs.locationLatitude ?: return@LaunchedEffect
        val longitude = prefs.locationLongitude ?: return@LaunchedEffect
        geocodeStatus = "Looking up your saved location…"
        val resolved = ReverseGeocoder.lookup(context, latitude, longitude)
        if (resolved == null) {
            geocodeStatus = "Could not detect your city. Please enter it manually."
        } else {
            if (city.isBlank() && resolved.city.isNotBlank()) city = resolved.city
            if (country.isBlank() && resolved.country.isNotBlank()) country = resolved.country
            geocodeStatus = null
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
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
                text = "Complete your profile",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "A little about you tailors prayer times, the Salah guide and reminders. " +
                    "You can change any of this later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(
                    photoUrl = profile?.pictureUrl()?.takeIf { it.isNotBlank() },
                    displayName = displayName
                )
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
                    val provider = profile?.authProvider.orEmpty()
                    if (provider == "google") {
                        Text(
                            text = "Signed in with Google",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            error?.let { message ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it; error = null },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .autofillName()
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .autofillEmail()
            )

            GenderSelector(gender = gender, onGenderChange = { gender = it })

            DateOfBirthField(dateOfBirth = dateOfBirth, onDateChange = { dateOfBirth = it })

            OutlinedTextField(
                value = city,
                onValueChange = { city = it; error = null },
                label = { Text("City") },
                singleLine = true,
                supportingText = geocodeStatus?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = country,
                onValueChange = { country = it; error = null },
                label = { Text("Country") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (isSaving) return@Button
                    val cleanName = displayName.trim()
                    if (cleanName.isEmpty()) {
                        error = "Please enter a display name."
                        return@Button
                    }
                    if (gender.isBlank()) {
                        error = "Please select your gender."
                        return@Button
                    }
                    if (dateOfBirth.isBlank()) {
                        error = "Please set your date of birth."
                        return@Button
                    }
                    if (city.isBlank() || country.isBlank()) {
                        error = "Please enter your city and country."
                        return@Button
                    }
                    scope.launch {
                        isSaving = true
                        error = null
                        try {
                            val updated = (profile ?: UserProfile()).copy(
                                displayName = cleanName,
                                email = email.trim(),
                                gender = gender,
                                dateOfBirth = dateOfBirth,
                                city = city.trim(),
                                country = country.trim(),
                                defaultCity = city.trim()
                            )
                            profileRepo.update(updated)
                            appearance.update {
                                it.copy(
                                    userName = cleanName,
                                    prayerGuideGender = if (gender == "MALE") {
                                        PrayerGuideGender.MALE
                                    } else {
                                        PrayerGuideGender.FEMALE
                                    }
                                )
                            }
                            if (signedInUser != null && !signedInUser.isGuest) {
                                authRepo.updateDisplayName(cleanName)
                            }
                            onDone()
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            error = e.message ?: "Could not save your profile."
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
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save & Continue")
                }
            }

            TextButton(
                onClick = { (onSkip ?: onDone)() },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for now")
            }
        }
    }
}
