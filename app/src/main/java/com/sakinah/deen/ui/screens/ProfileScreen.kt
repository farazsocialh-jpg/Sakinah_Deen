package com.sakinah.deen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sakinah.deen.auth.AuthRepository
import com.sakinah.deen.profile.UserProfile
import com.sakinah.deen.profile.UserProfileRepository
import com.sakinah.deen.ui.rememberInject
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val authRepo = rememberInject<AuthRepository>()
    val profileRepo = rememberInject<UserProfileRepository>()
    val profile by profileRepo.profile.collectAsState()
    var displayName by remember(profile) { mutableStateOf(profile?.displayName ?: "") }
    var defaultCity by remember(profile) { mutableStateOf(profile?.defaultCity ?: "") }
    val scope = rememberCoroutineScope()
    var showDelete by remember { mutableStateOf(false) }

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
            Text("Profile & Account", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = defaultCity, onValueChange = { defaultCity = it }, label = { Text("Default city") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                scope.launch { profileRepo.update(UserProfile(displayName = displayName, defaultCity = defaultCity, email = profile?.email ?: "")) }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            OutlinedButton(onClick = { showDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete Account") }
            if (showDelete) {
                AlertDialog(
                    onDismissRequest = { showDelete = false },
                    title = { Text("Delete account?") },
                    text = { Text("This action cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            scope.launch { authRepo.deleteAccount(); showDelete = false; onBack() }
                        }) { Text("Delete") }
                    },
                    dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
                )
            }
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}