package com.sakinah.deen.ui.screens

import com.sakinah.deen.R

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.auth.AuthRepository
import com.sakinah.deen.auth.AuthState
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.data.prefs.SakinahPreferences
import com.sakinah.deen.profile.UserProfile
import com.sakinah.deen.profile.UserProfileRepository
import com.sakinah.deen.ui.components.DateOfBirthField
import com.sakinah.deen.ui.components.ProfileAccountHeader
import com.sakinah.deen.ui.components.ProfileNameEmailFields
import com.sakinah.deen.ui.components.GenderSelector
import com.sakinah.deen.ui.components.HomeLocationCityPickerDialog
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
    var showCityPicker by remember { mutableStateOf(false) }

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
        geocodeStatus = context.getString(R.string.complete_profile_geocode_looking)
        val resolved = ReverseGeocoder.lookup(context, latitude, longitude)
        if (resolved == null) {
            geocodeStatus = context.getString(R.string.complete_profile_geocode_failed)
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
                text = stringResource(R.string.complete_profile_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.complete_profile_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ProfileAccountHeader(
                photoUrl = profile?.pictureUrl()?.takeIf { it.isNotBlank() },
                name = displayName.ifBlank { stringResource(R.string.complete_profile_your_account) },
                email = email,
                subtitle = if (profile?.authProvider == "google") {
                    stringResource(R.string.complete_profile_signed_in_google)
                } else {
                    null
                }
            )

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

            ProfileNameEmailFields(
                displayName = displayName,
                onDisplayNameChange = { displayName = it; error = null },
                nameLabel = stringResource(R.string.complete_profile_display_name),
                email = email,
                onEmailChange = { email = it; error = null },
                emailLabel = stringResource(R.string.complete_profile_email)
            )

            GenderSelector(gender = gender, onGenderChange = { gender = it })

            DateOfBirthField(dateOfBirth = dateOfBirth, onDateChange = { dateOfBirth = it })

            // Same searchable picker as Salah and Settings, so the profile stores the same
            // country code and IANA zone the rest of the app relies on.
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCityPicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.complete_profile_city),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = listOf(city, country).filter { it.isNotBlank() }.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = stringResource(R.string.complete_profile_change),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            geocodeStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    if (isSaving) return@Button
                    val cleanName = displayName.trim()
                    if (cleanName.isEmpty()) {
                        error = context.getString(R.string.complete_profile_name_required)
                        return@Button
                    }
                    if (gender.isBlank()) {
                        error = context.getString(R.string.complete_profile_gender_required)
                        return@Button
                    }
                    if (dateOfBirth.isBlank()) {
                        error = context.getString(R.string.complete_profile_dob_required)
                        return@Button
                    }
                    if (city.isBlank() || country.isBlank()) {
                        error = context.getString(R.string.complete_profile_city_country_required)
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
                            error = e.message ?: context.getString(R.string.complete_profile_save_failed)
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
                    Text(stringResource(R.string.complete_profile_save_continue))
                }
            }

            TextButton(
                onClick = { (onSkip ?: onDone)() },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.complete_profile_skip))
            }
        }
    }

    if (showCityPicker) {
        HomeLocationCityPickerDialog(
            title = stringResource(R.string.complete_profile_choose_city),
            subtitle = stringResource(R.string.complete_profile_choose_city_subtitle),
            onDismiss = { showCityPicker = false },
            onCitySelected = { picked ->
                city = picked.name.substringBefore(',').trim()
                country = picked.name.substringAfter(',', picked.countryCode).trim()
                    .ifBlank { picked.countryCode }
                error = null
                geocodeStatus = null
                showCityPicker = false
            }
        )
    }
}
