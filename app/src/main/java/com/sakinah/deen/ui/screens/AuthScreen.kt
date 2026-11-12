package com.sakinah.deen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sakinah.deen.auth.AuthRepository
import com.sakinah.deen.auth.AuthState
import com.sakinah.deen.ui.rememberInject
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val repo = rememberInject<AuthRepository>()
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val authProvidersEnabled = false

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
            Text(if (isSignUp) "Create Account" else "Sign In", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
            if (isSignUp) {
                OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
            }
            Button(onClick = {
                scope.launch {
                    if (isSignUp) repo.signUp(email, password, displayName) else repo.signIn(email, password)
                    onDone()
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(if (isSignUp) "Create Account" else "Sign In")
            }
            TextButton(onClick = { isSignUp = !isSignUp }) {
                Text(if (isSignUp) "Already have an account? Sign in" else "Need an account? Create one")
            }
            if (authProvidersEnabled) {
                OutlinedButton(
                    onClick = { /* OAuth/Credential Manager hook: wire when provider configured */ },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Continue with Google") }
                OutlinedButton(
                    onClick = { /* Sign in with Apple hook */ },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Continue with Apple") }
            }
            TextButton(onClick = { scope.launch { repo.continueAsGuest(); onDone() } }) {
                Text("Skip / Continue as Guest")
            }
        }
    }
}