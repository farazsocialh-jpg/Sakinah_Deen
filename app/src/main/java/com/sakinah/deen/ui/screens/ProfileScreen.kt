package com.sakinah.deen.ui.screens

import com.sakinah.deen.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import android.net.Uri
import com.sakinah.deen.data.backup.EncryptedBackupManager
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.ui.theme.PrayerGuideGender
import kotlinx.coroutines.launch

/**
 * Profile & account: shows the Google avatar and every collected field, and lets the user edit
 * them at any time. Email is the account identity for local accounts, so it stays read-only.
 */
@Composable
fun ProfileScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
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

    var backupPassphrase by remember { mutableStateOf("") }
    var showExportPassphrase by remember { mutableStateOf(false) }
    var showImportPassphrase by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            pendingExportUri = uri
            backupPassphrase = ""
            showExportPassphrase = true
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            backupPassphrase = ""
            showImportPassphrase = true
        }
    }

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
        "google" -> stringResource(R.string.profile_google_account)
        "guest" -> stringResource(R.string.profile_guest_session)
        else -> stringResource(R.string.profile_local_account)
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
                    text = stringResource(R.string.profile_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                ProfileAccountHeader(
                    photoUrl = avatarUrl,
                    name = displayName.ifBlank { stringResource(R.string.profile_your_account) },
                    avatarName = displayName.ifBlank { email },
                    email = email,
                    subtitle = providerLabel
                )

                if (profile?.isProfileComplete() != true) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.profile_complete_prompt),
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

                ProfileNameEmailFields(
                    displayName = displayName,
                    onDisplayNameChange = { displayName = it; validationError = null },
                    nameLabel = stringResource(R.string.profile_display_name),
                    email = email,
                    onEmailChange = { email = it; validationError = null },
                    emailLabel = stringResource(R.string.profile_email),
                    emailReadOnly = signedInUser?.isGuest == false,
                    emailSupportingText = if (signedInUser?.isGuest == false) {
                        stringResource(R.string.profile_email_tied)
                    } else {
                        null
                    }
                )

                GenderSelector(gender = gender, onGenderChange = { gender = it })

                DateOfBirthField(dateOfBirth = dateOfBirth, onDateChange = { dateOfBirth = it })

                if (age != null) {
                    Text(
                        text = stringResource(R.string.profile_age, age),
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
                        label = { Text(stringResource(R.string.profile_home_city)) },
                        singleLine = true,
                        supportingText = { Text(stringResource(R.string.profile_home_city_shared)) },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showHomeCityPicker = true }) {                     Text(stringResource(R.string.profile_change)) }
                }
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it; validationError = null },
                    label = { Text(stringResource(R.string.profile_country)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (isSaving) return@Button
                        val cleanName = displayName.trim()
                        if (cleanName.isEmpty()) {
                            validationError = context.getString(R.string.profile_display_name_empty)
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
                                snackbarHostState.showSnackbar(context.getString(R.string.profile_saved))
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                validationError = e.message ?: context.getString(R.string.profile_save_failed)
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
                    Text(if (isSaving) stringResource(R.string.profile_saving) else stringResource(R.string.profile_save_changes))
                }

                backupMessage?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = { exportLauncher.launch("sakinah-backup.skbak") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.profile_export_backup))
                }

                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.profile_import_backup))
                }

                // Every account lifecycle action lives here, not in the tools hub.
                OutlinedButton(
                    onClick = { showSignOut = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.profile_sign_out))
                }

                OutlinedButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.profile_delete_account))
                }

                if (showSignOut) {
                    AlertDialog(
                        onDismissRequest = { showSignOut = false },
                        title = { Text(stringResource(R.string.profile_sign_out_title)) },
                        text = {
                            Text(stringResource(R.string.profile_sign_out_body))
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showSignOut = false
                                scope.launch {
                                    // Only drop the session once the repository confirms the
                                    // sign-out; a failed sign-out must not lose the profile.
                                    // clearActiveSession() keeps the stored profile intact.
                                    runCatching { authRepo.signOut() }
                                        .onSuccess {
                                            profileRepo.clearActiveSession()
                                            onBack()
                                        }
                                        .onFailure { err ->
                                            if (err is kotlinx.coroutines.CancellationException) throw err
                                            validationError = err.message ?: context.getString(R.string.profile_sign_out_failed)
                                        }
                                }
                            }) { Text(stringResource(R.string.profile_sign_out)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSignOut = false }) { Text(stringResource(R.string.profile_cancel)) }
                        }
                    )
                }

                if (showDelete) {
                    AlertDialog(
                        onDismissRequest = { showDelete = false },
                        title = { Text(stringResource(R.string.profile_delete_account_title)) },
                        text = { Text(stringResource(R.string.profile_delete_account_body)) },
                        confirmButton = {
                            TextButton(onClick = {
                                scope.launch {
                                    authRepo.deleteAccount()
                                    profileRepo.clear()
                                    showDelete = false
                                    onBack()
                                }
                            }) { Text(stringResource(R.string.profile_delete)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.profile_cancel)) }
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

    if (showExportPassphrase) {
        AlertDialog(
            onDismissRequest = { showExportPassphrase = false },
            title = { Text(stringResource(R.string.profile_export_backup)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.profile_backup_passphrase_prompt_export))
                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text(stringResource(R.string.profile_backup_passphrase)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingExportUri
                    val pass = backupPassphrase
                    if (uri != null && pass.isNotBlank()) {
                        showExportPassphrase = false
                        scope.launch {
                            EncryptedBackupManager.export(
                                context = context,
                                uri = uri,
                                passphrase = pass
                            ).onSuccess {
                                backupMessage = context.getString(R.string.profile_backup_exported)
                            }.onFailure { e ->
                                backupMessage = context.getString(R.string.profile_backup_export_failed, e.message ?: "")
                            }
                        }
                    }
                }) { Text(stringResource(R.string.profile_backup_export_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportPassphrase = false }) { Text(stringResource(R.string.profile_cancel)) }
            }
        )
    }

    if (showImportPassphrase) {
        AlertDialog(
            onDismissRequest = { showImportPassphrase = false },
            title = { Text(stringResource(R.string.profile_import_backup)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.profile_backup_passphrase_prompt_import))
                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text(stringResource(R.string.profile_backup_passphrase)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImportUri
                    val pass = backupPassphrase
                    if (uri != null && pass.isNotBlank()) {
                        showImportPassphrase = false
                        scope.launch {
                            EncryptedBackupManager.import(
                                context = context,
                                uri = uri,
                                passphrase = pass
                            ).onSuccess {
                                backupMessage = context.getString(R.string.profile_backup_imported)
                            }.onFailure { e ->
                                backupMessage = context.getString(R.string.profile_backup_import_failed, e.message ?: "")
                            }
                        }
                    }
                }) { Text(stringResource(R.string.profile_backup_import_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportPassphrase = false }) { Text(stringResource(R.string.profile_cancel)) }
            }
        )
    }
}
