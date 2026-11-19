package com.sakinah.deen.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryAuthRepository : AuthRepository {

    private val stateMutex = Mutex()
    private val _authState = MutableStateFlow<AuthState>(AuthState.Guest)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private data class StoredUser(val email: String, val password: String, val displayName: String)
    private val users = mutableMapOf<String, StoredUser>()
    private var current: AuthUser? = null

    override suspend fun signUp(email: String, password: String, displayName: String): Result<AuthUser> =
        stateMutex.withLock {
            if (users.containsKey(email)) return@withLock Result.failure(IllegalStateException("Account already exists"))
            if (email.isBlank() || password.length < 6) return@withLock Result.failure(IllegalArgumentException("Invalid credentials"))
            users[email] = StoredUser(email, password, displayName)
            val user = AuthUser(id = email, email = email, displayName = displayName)
            current = user
            _authState.value = AuthState.Authenticated(user)
            Result.success(user)
        }

    override suspend fun signIn(email: String, password: String): Result<AuthUser> =
        stateMutex.withLock {
            val stored = users[email] ?: return@withLock Result.failure(IllegalStateException("Unknown account"))
            if (stored.password != password) return@withLock Result.failure(IllegalStateException("Invalid password"))
            val user = AuthUser(id = email, email = email, displayName = stored.displayName)
            current = user
            _authState.value = AuthState.Authenticated(user)
            Result.success(user)
        }

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        stateMutex.withLock {
            if (!users.containsKey(email)) return@withLock Result.failure(IllegalStateException("Unknown account"))
            Result.success(Unit)
        }

    override suspend fun signInWithGoogle(
        uniqueId: String,
        email: String,
        displayName: String,
        givenName: String?,
        familyName: String?,
        photoUrl: String?
    ): Result<AuthUser> = stateMutex.withLock {
        val cleanId = uniqueId.trim().ifBlank { email.trim() }
        if (cleanId.isBlank()) {
            return@withLock Result.failure(IllegalStateException("Google Sign-In returned no account id"))
        }
        val user = AuthUser(
            id = "google:$cleanId",
            email = email.trim(),
            displayName = displayName.trim().ifBlank { "Google User" },
            avatarUrl = photoUrl?.takeIf { it.isNotBlank() }
        )
        current = user
        _authState.value = AuthState.Authenticated(user)
        Result.success(user)
    }

    override suspend fun signInWithApple(identityToken: String): Result<AuthUser> = stateMutex.withLock {
        val user = AuthUser(id = "apple:$identityToken", email = "", displayName = "Apple User")
        current = user
        _authState.value = AuthState.Authenticated(user)
        Result.success(user)
    }

    override suspend fun continueAsGuest(): Result<AuthUser> = stateMutex.withLock {
        val user = AuthUser(id = "guest", email = "", displayName = "Guest", isGuest = true)
        current = user
        _authState.value = AuthState.Guest
        Result.success(user)
    }

    override suspend fun signOut() {
        stateMutex.withLock {
            current = null
            _authState.value = AuthState.Guest
        }
    }

    override suspend fun deleteAccount(): Result<Unit> = stateMutex.withLock {
        val c = current ?: return@withLock Result.failure(IllegalStateException("Not signed in"))
        users.remove(c.email)
        current = null
        _authState.value = AuthState.Guest
        Result.success(Unit)
    }

    override suspend fun updateDisplayName(displayName: String): Result<AuthUser> = stateMutex.withLock {
        val c = current ?: return@withLock Result.failure(IllegalStateException("Not signed in"))
        val clean = displayName.trim()
        if (clean.isBlank()) return@withLock Result.failure(IllegalArgumentException("Display name cannot be blank"))
        val updated = c.copy(displayName = clean)
        current = updated
        users[c.email]?.let { existing -> users[c.email] = existing.copy(displayName = clean) }
        _authState.value = AuthState.Authenticated(updated)
        Result.success(updated)
    }
}