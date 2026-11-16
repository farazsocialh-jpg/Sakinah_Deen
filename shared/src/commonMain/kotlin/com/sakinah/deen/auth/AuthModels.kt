package com.sakinah.deen.auth

import kotlinx.coroutines.flow.StateFlow

data class AuthUser(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val isGuest: Boolean = false,
)

sealed class AuthState {
    data object Loading : AuthState()
    data object Guest : AuthState()
    data class Authenticated(val user: AuthUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

interface AuthRepository {
    val authState: StateFlow<AuthState>
    suspend fun signUp(email: String, password: String, displayName: String): Result<AuthUser>
    suspend fun signIn(email: String, password: String): Result<AuthUser>
    suspend fun sendPasswordReset(email: String): Result<Unit>

    /**
     * Signs in (or links) the Google account described by the Credential Manager ID token.
     *
     * Google only supplies identity fields — gender, date of birth, city and country are
     * collected afterwards by the profile-completion step.
     */
    suspend fun signInWithGoogle(
        uniqueId: String,
        email: String,
        displayName: String,
        givenName: String? = null,
        familyName: String? = null,
        photoUrl: String? = null,
    ): Result<AuthUser>

    suspend fun signInWithApple(identityToken: String): Result<AuthUser>
    suspend fun continueAsGuest(): Result<AuthUser>
    suspend fun signOut()
    suspend fun deleteAccount(): Result<Unit>
    suspend fun updateDisplayName(displayName: String): Result<AuthUser> =
        Result.failure(IllegalStateException("Not supported"))
}