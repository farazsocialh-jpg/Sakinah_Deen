package com.sakinah.deen.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.auth.AuthRepository
import com.sakinah.deen.auth.AuthUser
import com.sakinah.deen.auth.GoogleAccount
import com.sakinah.deen.auth.GoogleSignInCancelled
import com.sakinah.deen.auth.GoogleSignInManager
import com.sakinah.deen.auth.GoogleSignInNotConfigured
import com.sakinah.deen.profile.UserProfile
import com.sakinah.deen.profile.UserProfileRepository
import com.sakinah.deen.ui.rememberInject
import kotlinx.coroutines.launch

/**
 * Sign in, account creation and Google Sign-In.
 *
 * Validation is inline per field; the top banner is reserved for repository/provider
 * failures. Google only returns identity fields, so demographic data is collected by the
 * profile-completion step that runs after [onDone].
 */
@Composable
fun AuthScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val repo = rememberInject<AuthRepository>()
    val profileRepo = rememberInject<UserProfileRepository>()
    val context = LocalContext.current
    val googleManager = remember(context) { GoogleSignInManager(context.applicationContext) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    var isSignUp by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isGoogleSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var googleNotice by remember { mutableStateOf<String?>(null) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }

    val busy = isSubmitting || isGoogleSubmitting

    fun clearErrors() {
        nameError = null
        emailError = null
        passwordError = null
        confirmError = null
        errorMessage = null
    }

    fun validateForm(): Boolean {
        clearErrors()
        var valid = true
        if (isSignUp && displayName.trim().isEmpty()) {
            nameError = "Please enter your display name."
            valid = false
        }
        val cleanEmail = email.trim()
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".") || cleanEmail.contains(" ")) {
            emailError = "Please enter a valid email address."
            valid = false
        }
        if (isSignUp) {
            if (password.length < 6) {
                passwordError = "Use at least 6 characters."
                valid = false
            }
            if (confirmPassword != password) {
                confirmError = "Passwords do not match."
                valid = false
            }
        } else if (password.isBlank()) {
            passwordError = "Please enter your password."
            valid = false
        }
        return valid
    }

    fun dismissKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    /** Writes the signed-in identity into the profile store before the completion step runs. */
    suspend fun prepareProfile(
        user: AuthUser,
        provider: String,
        google: GoogleAccount? = null,
        createdName: String? = null
    ) {
        profileRepo.loadForUser(user.id)
        val current = profileRepo.profile.value ?: UserProfile()
        profileRepo.update(
            current.copy(
                uniqueId = google?.uniqueId ?: current.uniqueId.ifBlank { user.id },
                authProvider = provider,
                displayName = current.displayName.ifBlank {
                    createdName?.takeIf { it.isNotBlank() } ?: user.displayName
                },
                givenName = current.givenName.ifBlank { google?.givenName.orEmpty() },
                familyName = current.familyName.ifBlank { google?.familyName.orEmpty() },
                email = current.email.ifBlank { user.email },
                photoUrl = current.photoUrl.ifBlank { google?.photoUrl.orEmpty() },
                avatarUrl = current.avatarUrl ?: google?.photoUrl?.takeIf { it.isNotBlank() },
            )
        )
    }

    fun submitCredentials() {
        if (busy) return
        if (!validateForm()) return
        dismissKeyboard()
        val cleanEmail = email.trim()
        val cleanName = displayName.trim()
        scope.launch {
            isSubmitting = true
            errorMessage = null
            try {
                val result = if (isSignUp) {
                    repo.signUp(cleanEmail, password, cleanName)
                } else {
                    repo.signIn(cleanEmail, password)
                }
                result.onSuccess { user ->
                    prepareProfile(
                        user = user,
                        provider = "local",
                        createdName = if (isSignUp) cleanName else null
                    )
                    onDone()
                }.onFailure { err ->
                    errorMessage = err.message ?: "Authentication failed. Please check your details."
                }
            } finally {
                isSubmitting = false
            }
        }
    }

    fun startGoogleSignIn() {
        if (busy) return
        errorMessage = null
        googleNotice = null
        if (!googleManager.isConfigured) {
            googleNotice = GoogleSignInNotConfigured().message
            return
        }
        val activity = context.findActivity()
        if (activity == null) {
            errorMessage = "Google Sign-In needs the app window. Please reopen the app and try again."
            return
        }
        scope.launch {
            isGoogleSubmitting = true
            try {
                val googleResult = googleManager.signIn(activity)
                val account = googleResult.getOrElse { err ->
                    // A cancelled sheet is a normal user action, not an error to display.
                    if (err !is GoogleSignInCancelled) {
                        errorMessage = err.message ?: "Google Sign-In failed. Please try again."
                    }
                    return@launch
                }
                repo.signInWithGoogle(
                    uniqueId = account.uniqueId,
                    email = account.email,
                    displayName = account.displayName,
                    givenName = account.givenName,
                    familyName = account.familyName,
                    photoUrl = account.photoUrl
                ).onSuccess { user ->
                    prepareProfile(user = user, provider = "google", google = account)
                    onDone()
                }.onFailure { err ->
                    errorMessage = err.message ?: "Could not complete Google Sign-In."
                }
            } finally {
                isGoogleSubmitting = false
            }
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
                text = if (isSignUp) "Create Account" else "Sign In",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (isSignUp) {
                    "Your account is stored securely on this device."
                } else {
                    "Sign in to access your saved profile and preferences."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            errorMessage?.let { err ->
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

            googleNotice?.let { notice ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = notice,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // 1. Google first: it is the fastest path and needs no typing.
            OutlinedButton(
                onClick = { startGoogleSignIn() },
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isGoogleSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Signing in…")
                } else {
                    GoogleMark()
                    Spacer(Modifier.width(10.dp))
                    Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "or",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            // 2. Display name comes first so the form reads top-down for a new account.
            if (isSignUp) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it; nameError = null; errorMessage = null },
                    label = { Text("Display name") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .autofillName()
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; emailError = null; errorMessage = null },
                label = { Text("Email") },
                singleLine = true,
                isError = emailError != null,
                supportingText = emailError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .autofillEmail()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; passwordError = null; confirmError = null; errorMessage = null },
                label = { Text("Password") },
                singleLine = true,
                isError = passwordError != null,
                supportingText = {
                    val hint = passwordError
                        ?: if (isSignUp) "At least 6 characters" else null
                    if (hint != null) Text(hint)
                },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (isSignUp) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = if (isSignUp) {
                    KeyboardActions(
                        onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }
                    )
                } else {
                    KeyboardActions(onDone = { submitCredentials() })
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .autofillPassword(isSignUp)
            )

            if (isSignUp) {
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; confirmError = null; errorMessage = null },
                    label = { Text("Confirm password") },
                    singleLine = true,
                    isError = confirmError != null,
                    supportingText = confirmError?.let { { Text(it) } },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitCredentials() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .autofillPassword(isSignUp = true)
                )
            }

            Button(
                onClick = { submitCredentials() },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isSignUp) "Create Account" else "Sign In")
                }
            }

            TextButton(
                onClick = {
                    isSignUp = !isSignUp
                    clearErrors()
                    googleNotice = null
                    confirmPassword = ""
                },
                enabled = !busy
            ) {
                Text(if (isSignUp) "Already have an account? Sign in" else "Create account")
            }

            // There is no "Forgot password?" action: local accounts cannot receive email, so
            // offering one would be misleading.

            TextButton(
                onClick = {
                    if (busy) return@TextButton
                    scope.launch {
                        isSubmitting = true
                        try {
                            repo.continueAsGuest()
                            // Guests must not inherit the previous account's profile.
                            profileRepo.clear()
                            onDone()
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            errorMessage = e.message ?: "Could not continue as guest."
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                enabled = !busy
            ) {
                Text("Continue as Guest")
            }
        }
    }
}

/** Google's wordmark is not part of Material Icons, so draw its "G" mark. */
@Composable
private fun GoogleMark() {
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "G",
            color = Color(0xFF4285F4),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
