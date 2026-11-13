package com.sakinah.deen.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import com.sakinah.deen.profile.UserProfile
import com.sakinah.deen.profile.UserProfileRepository
import com.sakinah.deen.ui.rememberInject
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val authRepo = rememberInject<AuthRepository>()
    val profileRepo = rememberInject<UserProfileRepository>()

    val authState by authRepo.authState.collectAsStateWithLifecycle()
    val profile by profileRepo.profile.collectAsStateWithLifecycle()
    val signedInUser = (authState as? AuthState.Authenticated)?.user

    val initialName = profile?.displayName?.takeIf { it.isNotBlank() }
        ?: signedInUser?.displayName.orEmpty()
    val accountEmail = profile?.email?.takeIf { it.isNotBlank() }
        ?: signedInUser?.email.orEmpty()

    var displayName by remember(profile, signedInUser?.id) { mutableStateOf(initialName) }
    var defaultCity by remember(profile) { mutableStateOf(profile?.defaultCity.orEmpty()) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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

                if (accountEmail.isNotBlank()) {
                    Text(
                        text = "Account Email: $accountEmail",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = defaultCity,
                    onValueChange = { defaultCity = it },
                    label = { Text("Default city") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val cleanName = displayName.trim()
                        if (cleanName.isEmpty()) {
                            validationError = "Display name cannot be empty."
                            return@Button
                        }
                        val cleanCity = defaultCity.trim()
                        scope.launch {
                            val updatedProfile = (profile ?: UserProfile()).copy(
                                displayName = cleanName,
                                defaultCity = cleanCity,
                                email = accountEmail
                            )
                            profileRepo.update(updatedProfile)
                            if (signedInUser != null && !signedInUser.isGuest) {
                                authRepo.updateDisplayName(cleanName)
                            }
                            snackbarHostState.showSnackbar("Profile saved.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Changes")
                }

                OutlinedButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Account")
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

                TextButton(onClick = onBack) { Text("Back") }
            }
        }
    }
}