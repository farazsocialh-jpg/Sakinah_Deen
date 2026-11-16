package com.sakinah.deen.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryAuthRepository : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Guest)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private data class StoredUser(val email: String, val password: String, val displayName: String)
    private val users = mutableMapOf<String, StoredUser>()
    private var current: AuthUser? = null

    override suspend fun signUp(email: String, password: String, displayName: String): Result<AuthUser> {
        if (users.containsKey(email)) return Result.failure(IllegalStateException("Account already exists"))
        if (email.isBlank() || password.length < 6) return Result.failure(IllegalArgumentException("Invalid credentials"))
        users[email] = StoredUser(email, password, displayName)
        val user = AuthUser(id = email, email = email, displayName = displayName)
        current = user
        _authState.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    override suspend fun signIn(email: String, password: String): Result<AuthUser> {
        val stored = users[email] ?: return Result.failure(IllegalStateException("Unknown account"))
        if (stored.password != password) return Result.failure(IllegalStateException("Invalid password"))
        val user = AuthUser(id = email, email = email, displayName = stored.displayName)
        current = user
        _authState.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        if (!users.containsKey(email)) return Result.failure(IllegalStateException("Unknown account"))
        return Result.success(Unit)
    }

    override suspend fun signInWithGoogle(
        uniqueId: String,
        email: String,
        displayName: String,
        givenName: String?,
        familyName: String?,
        photoUrl: String?
    ): Result<AuthUser> {
        val cleanId = uniqueId.trim().ifBlank { email.trim() }
        if (cleanId.isBlank()) {
            return Result.failure(IllegalStateException("Google Sign-In returned no account id"))
        }
        val user = AuthUser(
            id = "google:$cleanId",
            email = email.trim(),
            displayName = displayName.trim().ifBlank { "Google User" },
            avatarUrl = photoUrl?.takeIf { it.isNotBlank() }
        )
        current = user
        _authState.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    override suspend fun signInWithApple(identityToken: String): Result<AuthUser> {
        val user = AuthUser(id = "apple:$identityToken", email = "", displayName = "Apple User")
        current = user
        _authState.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    override suspend fun continueAsGuest(): Result<AuthUser> {
        val user = AuthUser(id = "guest", email = "", displayName = "Guest", isGuest = true)
        current = user
        _authState.value = AuthState.Guest
        return Result.success(user)
    }

    override suspend fun signOut() {
        current = null
        _authState.value = AuthState.Guest
    }

    override suspend fun deleteAccount(): Result<Unit> {
        val c = current ?: return Result.failure(IllegalStateException("Not signed in"))
        users.remove(c.email)
        current = null
        _authState.value = AuthState.Guest
        return Result.success(Unit)
    }

    override suspend fun updateDisplayName(displayName: String): Result<AuthUser> {
        val c = current ?: return Result.failure(IllegalStateException("Not signed in"))
        val clean = displayName.trim()
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Display name cannot be blank"))
        val updated = c.copy(displayName = clean)
        current = updated
        users[c.email]?.let { existing -> users[c.email] = existing.copy(displayName = clean) }
        _authState.value = AuthState.Authenticated(updated)
        return Result.success(updated)
    }
}