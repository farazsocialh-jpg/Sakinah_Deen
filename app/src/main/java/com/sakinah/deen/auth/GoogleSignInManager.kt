package com.sakinah.deen.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.sakinah.deen.BuildConfig

/** Identity fields Google actually returns (no gender/DOB/location). */
data class GoogleAccount(
    val uniqueId: String,
    val email: String,
    val displayName: String,
    val givenName: String?,
    val familyName: String?,
    val photoUrl: String?,
    val idToken: String,
)

/** The user dismissed the Google account sheet. Never worth an error banner. */
class GoogleSignInCancelled : Exception("Google Sign-In was cancelled")

/** The build has no GOOGLE_WEB_CLIENT_ID, so Credential Manager cannot be invoked. */
class GoogleSignInNotConfigured : Exception(
    "Google Sign-In is not set up for this build. Add GOOGLE_WEB_CLIENT_ID to " +
        ".env or local.properties (or the environment) and rebuild."
)

/**
 * Sign in with Google through Android Credential Manager and a Google ID token.
 *
 * Pure Play-services credentials: no Firebase project, no billing. The Web OAuth client id is
 * passed as `serverClientId` and the Android OAuth client is matched by package + signing
 * SHA-1, so the token is bound to this app.
 */
class GoogleSignInManager(private val context: Context) {

    private val credentialManager: CredentialManager by lazy { CredentialManager.create(context) }

    val webClientId: String
        get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()

    val isConfigured: Boolean
        get() = webClientId.isNotEmpty()

    /**
     * Shows the "Sign in with Google" sheet. [activity] is required because the credential
     * UI is an Activity-scoped bottom sheet.
     */
    suspend fun signIn(activity: Activity): Result<GoogleAccount> {
        if (!isConfigured) return Result.failure(GoogleSignInNotConfigured())

        return try {
            val option = GetSignInWithGoogleOption.Builder(webClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val response = credentialManager.getCredential(activity, request)
            val credential = response.credential

            if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return Result.failure(
                    IllegalStateException("Google returned an unexpected credential. Please try again.")
                )
            }

            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            Result.success(
                GoogleAccount(
                    uniqueId = googleCredential.uniqueId,
                    email = googleCredential.id,
                    displayName = googleCredential.displayName.orEmpty(),
                    givenName = googleCredential.givenName,
                    familyName = googleCredential.familyName,
                    photoUrl = googleCredential.profilePictureUri?.toString(),
                    idToken = googleCredential.idToken,
                )
            )
        } catch (e: GetCredentialCancellationException) {
            Result.failure(GoogleSignInCancelled())
        } catch (e: NoCredentialException) {
            Result.failure(
                IllegalStateException(
                    "No Google account is available on this device. Add one in Android " +
                        "Settings → Google, then try again."
                )
            )
        } catch (e: GoogleIdTokenParsingException) {
            Result.failure(
                IllegalStateException("Google returned an unreadable sign-in response. Please try again.")
            )
        } catch (e: GetCredentialException) {
            Result.failure(
                IllegalStateException(
                    "Google Sign-In could not complete. Check your internet connection and try again."
                )
            )
        }
    }

    /**
     * Clears the provider's cached credential state after a local sign-out so the next launch
     * does not silently restore the previous Google session.
     */
    suspend fun clearCredentialState() {
        runCatching {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
    }
}
