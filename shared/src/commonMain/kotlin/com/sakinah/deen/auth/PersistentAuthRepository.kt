package com.sakinah.deen.auth

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Multiplatform persistent [AuthRepository] backed by [Settings] (SharedPreferences on Android,
 * NSUserDefaults on iOS). Restores the active session on startup and hashes passwords with
 * per-account salts before persistence.
 */
class PersistentAuthRepository(
    private val prefs: Settings = Settings(),
) : AuthRepository {

    @Serializable
    private data class StoredAccount(
        val id: String,
        val email: String,
        val displayName: String,
        val salt: String,
        val passwordHash: String,
    )

    @Serializable
    private data class PersistedUser(
        val id: String,
        val email: String,
        val displayName: String,
        val avatarUrl: String? = null,
        val isGuest: Boolean = false,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val accounts = mutableMapOf<String, StoredAccount>()
    private var current: AuthUser? = null

    private val _authState = MutableStateFlow<AuthState>(AuthState.Guest)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val rawAccounts = prefs.getStringOrNull(KEY_ACCOUNTS)
        if (!rawAccounts.isNullOrBlank()) {
            runCatching {
                json.decodeFromString<List<StoredAccount>>(rawAccounts)
            }.getOrNull()?.forEach { acc ->
                accounts[acc.email.lowercase()] = acc
            }
        }
        val rawUser = prefs.getStringOrNull(KEY_CURRENT_USER)
        val restored = if (!rawUser.isNullOrBlank()) {
            runCatching { json.decodeFromString<PersistedUser>(rawUser) }.getOrNull()
        } else {
            null
        }
        if (restored != null && !restored.isGuest) {
            val user = AuthUser(
                id = restored.id,
                email = restored.email,
                displayName = restored.displayName,
                avatarUrl = restored.avatarUrl,
                isGuest = false,
            )
            current = user
            _authState.value = AuthState.Authenticated(user)
        } else {
            current = null
            _authState.value = AuthState.Guest
        }
    }

    private fun persistAccounts() {
        prefs.putString(KEY_ACCOUNTS, json.encodeToString(accounts.values.toList()))
    }

    private fun persistCurrentUser(user: AuthUser?) {
        if (user == null || user.isGuest) {
            prefs.remove(KEY_CURRENT_USER)
        } else {
            val payload = PersistedUser(
                id = user.id,
                email = user.email,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl,
                isGuest = false,
            )
            prefs.putString(KEY_CURRENT_USER, json.encodeToString(payload))
        }
    }

    override suspend fun signUp(email: String, password: String, displayName: String): Result<AuthUser> {
        val cleanEmail = email.trim().lowercase()
        val cleanName = displayName.trim()
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your full name."))
        }
        if (!isValidEmail(cleanEmail)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }
        if (accounts.containsKey(cleanEmail)) {
            return Result.failure(IllegalStateException("An account with this email already exists."))
        }
        val salt = "${cleanEmail.hashCode()}-${cleanName.length}-sakinah"
        val stored = StoredAccount(
            id = cleanEmail,
            email = cleanEmail,
            displayName = cleanName,
            salt = salt,
            passwordHash = hashPassword(salt, password),
        )
        accounts[cleanEmail] = stored
        persistAccounts()

        val user = AuthUser(id = cleanEmail, email = cleanEmail, displayName = cleanName)
        current = user
        persistCurrentUser(user)
        _authState.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    override suspend fun signIn(email: String, password: String): Result<AuthUser> {
        val cleanEmail = email.trim().lowercase()
        if (!isValidEmail(cleanEmail)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter your password."))
        }
        val stored = accounts[cleanEmail]
            ?: return Result.failure(IllegalStateException("No account found for this email. Please sign up first."))
        if (stored.passwordHash != hashPassword(stored.salt, password)) {
            return Result.failure(IllegalStateException("Incorrect password."))
        }
        val user = AuthUser(id = stored.id, email = stored.email, displayName = stored.displayName)
        current = user
        persistCurrentUser(user)
        _authState.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        val cleanEmail = email.trim().lowercase()
        if (!isValidEmail(cleanEmail)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (!accounts.containsKey(cleanEmail)) {
            return Result.failure(IllegalStateException("No local account found for $cleanEmail."))
        }
        return Result.success(Unit)
    }

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> {
        val clean = idToken.trim().ifBlank { "local" }
        val user = AuthUser(
            id = "google:$clean",
            email = "",
            displayName = "Google User",
        )
        current = user
        persistCurrentUser(user)
        _authState.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    override suspend fun signInWithApple(identityToken: String): Result<AuthUser> {
        val clean = identityToken.trim().ifBlank { "local" }
        val user = AuthUser(
            id = "apple:$clean",
            email = "",
            displayName = "Apple User",
        )
        current = user
        persistCurrentUser(user)
        _authState.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    override suspend fun continueAsGuest(): Result<AuthUser> {
        current = null
        persistCurrentUser(null)
        _authState.value = AuthState.Guest
        return Result.success(AuthUser(id = "guest", email = "", displayName = "Guest", isGuest = true))
    }

    override suspend fun signOut() {
        current = null
        persistCurrentUser(null)
        _authState.value = AuthState.Guest
    }

    override suspend fun deleteAccount(): Result<Unit> {
        val c = current ?: return Result.failure(IllegalStateException("Not signed in"))
        accounts.remove(c.email.lowercase())
        persistAccounts()
        current = null
        persistCurrentUser(null)
        _authState.value = AuthState.Guest
        return Result.success(Unit)
    }

    override suspend fun updateDisplayName(displayName: String): Result<AuthUser> {
        val c = current ?: return Result.failure(IllegalStateException("Not signed in"))
        val clean = displayName.trim()
        if (clean.isBlank()) {
            return Result.failure(IllegalArgumentException("Display name cannot be blank."))
        }
        val updated = c.copy(displayName = clean)
        current = updated
        persistCurrentUser(updated)
        val key = c.email.lowercase()
        accounts[key]?.let { existing ->
            accounts[key] = existing.copy(displayName = clean)
            persistAccounts()
        }
        _authState.value = AuthState.Authenticated(updated)
        return Result.success(updated)
    }

    private fun isValidEmail(email: String): Boolean {
        val at = email.indexOf('@')
        val dot = email.lastIndexOf('.')
        return at > 0 && dot > at + 1 && dot < email.length - 1 && !email.contains(' ')
    }

    private fun hashPassword(salt: String, password: String): String {
        val input = "$salt:$password:sakinah_v1"
        var h1 = -3750763034362895579L
        var h2 = 0x517cc1b727220a95L
        for (ch in input) {
            val code = ch.code.toLong()
            h1 = (h1 xor code) * 1099511628211L
            h2 = ((h2 shl 7) or (h2 ushr 57)) xor (code * 0x9e3779b97f4a7c15UL.toLong())
        }
        return "${h1.toULong().toString(16)}:${h2.toULong().toString(16)}"
    }

    companion object {
        private const val KEY_ACCOUNTS = "sakinah_auth_accounts_v1"
        private const val KEY_CURRENT_USER = "sakinah_auth_current_user_v1"
    }
}
