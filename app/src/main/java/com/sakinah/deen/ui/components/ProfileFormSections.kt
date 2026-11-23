package com.sakinah.deen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sakinah.deen.ui.screens.autofillEmail
import com.sakinah.deen.ui.screens.autofillName

/**
 * Avatar, name, email and account-type line shared by the profile and post-sign-in screens.
 * The callers supply already-localized text so each keeps its own wording.
 */
@Composable
fun ProfileAccountHeader(
    photoUrl: String?,
    name: String,
    email: String,
    subtitle: String?,
    avatarName: String = name,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatar(photoUrl = photoUrl, displayName = avatarName)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = name,
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
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Display name + email pair. Validation, read-only rules and supporting text stay with the
 * caller because the onboarding and profile screens enforce different ones.
 */
@Composable
fun ProfileNameEmailFields(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    nameLabel: String,
    email: String,
    onEmailChange: (String) -> Unit,
    emailLabel: String,
    modifier: Modifier = Modifier,
    emailReadOnly: Boolean = false,
    emailSupportingText: String? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            label = { Text(nameLabel) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .autofillName()
        )

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text(emailLabel) },
            singleLine = true,
            readOnly = emailReadOnly,
            supportingText = emailSupportingText?.let { text ->
                { Text(text) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .autofillEmail()
        )
    }
}
