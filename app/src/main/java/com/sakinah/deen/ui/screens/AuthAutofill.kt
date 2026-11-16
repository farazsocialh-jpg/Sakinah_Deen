package com.sakinah.deen.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics

/**
 * Autofill hints for the auth form so password managers and Google's credential provider can
 * fill (and save) these fields. Keeping the mapping here avoids repeating it at each call site.
 */
internal fun Modifier.autofillName(): Modifier =
    semantics { contentType = ContentType.PersonFullName }

internal fun Modifier.autofillEmail(): Modifier =
    semantics { contentType = ContentType.EmailAddress }

internal fun Modifier.autofillPassword(isSignUp: Boolean): Modifier =
    semantics { contentType = if (isSignUp) ContentType.NewPassword else ContentType.Password }
