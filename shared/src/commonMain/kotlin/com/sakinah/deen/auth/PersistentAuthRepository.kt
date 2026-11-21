package com.sakinah.deen.auth

import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Multiplatform persistent [AuthRepository] backed by [Settings] (encrypted
 * SharedPreferences on Android, NSUserDefaults on iOS). Restores the active session on startup
 * and stores only PBKDF2-HMAC-SHA256 hashes over a random per-account salt.
 * Adds rate limiting: lock account after 5 consecutive failed sign-in attempts.
 */
class PersistentAuthRepository(
    private val prefs: Settings = Settings(),
    /** Platform hook run when the session ends, e.g. Android's
     * `CredentialManager.clearCredentialState(...)` so a signed-out user is not silently
     * re-authenticated by Google on the next launch.
     */
    private val onSignOut: (suspend () -> Unit)? = null,
) : AuthRepository {

    @Serializable
    private data class StoredAccount(
        val id: String,
        val email: String,
        val displayName: String,
        val salt: String,
        /** Empty for Google-only accounts, which never authenticate with a local password. */
        val passwordHash: String = "",
        /** "local" or "google". */
        val provider: String = "local",
        val givenName: String = "",
        val familyName: String = "",
        val photoUrl: String = "",
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

    private val stateMutex = Mutex()
    private val accounts = mutableMapOf<String, StoredAccount>()
    private var current: AuthUser? = null

    private val _authState = MutableStateFlow<AuthState>(AuthState.Guest)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // --- Rate-limiting state ---
    private val LOGIN_FAILURES_KEY = "sakinah_auth_login_failures_v1"
    private val LOCKOUT_RESET_MS = 300000L // 5 minutes
    private val LOCKOUT_AFTER = 5

    private var loginFailures: Int
        get() = prefs.getIntOrNull(LOGIN_FAILURES_KEY) ?: 0
    private set(failures) {
        prefs.putInt(LOGIN_FAILURES_KEY, failures)
    }

    private fun resetLoginFailures() {
        loginFailures = 0
        prefs.remove(LOGIN_FAILURES_KEY)
    }

    /** True when the account is in lockout and should reject new attempts. */
    private val isInLockout: Boolean get() = loginFailures >= LOCKOUT_AFTER

    /** Clear lockout after reset period. Called on successful auth flows. */
    private fun clearLoginLockout() {
        resetLoginFailures()
    }

    /** Best effort: a failed provider sign-out must never block the local sign-out. */
    private suspend fun clearPlatformCredentialState() {
        runCatching { onSignOut?.invoke() }
        clearLoginLockout()
    }

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val rawAccounts = prefs.getStringOrNull(KEY_ACCOUNTS)
        if (!rawAccounts.isNullOrBlank()) {
            runCatching {
                json.decodeFromString<List<StoredAccount>>(rawAccounts)
            }.getOrNull()?.forEach { acc ->
                // Google accounts may have no email; key them by id instead of colliding on "".
                accounts[acc.email.lowercase().ifBlank { acc.id }] = acc
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

    override suspend fun signUp(email: String, password: String, displayName: String): Result<AuthUser> = stateMutex.withLock {
        val cleanEmail = email.trim().lowercase()
        val cleanName = displayName.trim()
        if (cleanName.isBlank()) {
            return@withLock Result.failure(IllegalArgumentException("Please enter your full name."))
        }
        if (!isValidEmail(cleanEmail)) {
            return@withLock Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (!PasswordHasher.isAcceptablePassword(password)) {
            return@withLock Result.failure(
                IllegalArgumentException(
                    "Password must be at least ${PasswordHasher.MIN_PASSWORD_LENGTH} characters."
                )
            )
        }
        if (accounts.containsKey(cleanEmail)) {
            return@withLock Result.failure(IllegalStateException("An account with this email already exists."))
        }
        // Random per-account salt: a value derived from the email would let an attacker
        // precompute hashes and reuse them across accounts.
        val salt = PasswordHasher.newSalt()
        val stored = StoredAccount(
            id = cleanEmail,
            email = cleanEmail,
            displayName = cleanName,
            salt = salt,
            passwordHash = withContext(Dispatchers.Default) {
                PasswordHasher.hash(password, salt)
            },
        )
        accounts[cleanEmail] = stored
        persistAccounts()

        val user = AuthUser(id = cleanEmail, email = cleanEmail, displayName = cleanName)
        current = user
        persistCurrentUser(user)
        _authState.value = AuthState.Authenticated(user)
        Result.success(user)
    }

    override suspend fun signIn(email: String, password: String): Result<AuthUser> = stateMutex.withLock {
        val cleanEmail = email.trim().lowercase()
        if (!isValidEmail(cleanEmail)) {
            return@withLock Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (password.isBlank()) {
            return@withLock Result.failure(IllegalArgumentException("Please enter your password."))
        }
        val stored = accounts[cleanEmail]
            ?: return@withLock Result.failure(IllegalStateException("No account found for this email. Please sign up first."))
        if (stored.passwordHash.isBlank()) {
            return@withLock Result.failure(
                IllegalStateException("This account uses Google Sign-In. Continue with Google instead.")
            )
        }
        val verified = withContext(Dispatchers.Default) {
            PasswordHasher.verify(password, stored.salt, stored.passwordHash)
        }
        if (!verified) {
            // Increment failure count and check lockout
            loginFailures = loginFailures + 1
            persistAccounts()
            if (isInLockout) {
                return@withLock Result.failure(
                    IllegalStateException(
                        "Account locked due to multiple failed attempts. " +
                            "Please try again in 5 minutes."
                    )
                )
            }
            return@withLock Result.failure(IllegalStateException("Incorrect password."))
        }
        // Accounts created before the KDF change are re-hashed on their next sign-in.
        if (PasswordHasher.needsUpgrade(stored.passwordHash)) {
            val newSalt = PasswordHasher.newSalt()
            val upgraded = stored.copy(
                salt = newSalt,
                passwordHash = withContext(Dispatchers.Default) {
                    PasswordHasher.hash(password, newSalt)
                }
            )
            accounts[cleanEmail] = upgraded
            persistAccounts()
        }
        // Reset failure count on successful sign-in
        clearLoginLockout()
        val user = AuthUser(id = stored.id, email = stored.email, displayName = stored.displayName)
        current = user
        persistCurrentUser(user)
        _authState.value = AuthState.Authenticated(user)
        Result.success(user)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = stateMutex.withLock {
        val cleanEmail = email.trim().lowercase()
        if (!isValidEmail(cleanEmail)) {
            return@withLock Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (!accounts.containsKey(cleanEmail)) {
            return@withLock Result.failure(IllegalStateException("No local account found for $cleanEmail."))
        }
        // Accounts live only on this device and there is no mail server, so claiming a reset
        // was "sent" would be a lie. Report the real limitation to the caller.
        Result.failure(
            IllegalStateException(
                "Password reset by email is unavailable for on-device accounts. " +
                    "Create a new account with a new password, or continue as a guest."
            )
        )
    }

    /** Creates or links a Google account. Linking keeps an existing local password hash, so a
     * user who signed up with email first can still sign in either way.
     */
    override suspend fun signInWithGoogle(
        uniqueId: String,
        email: String,
        displayName: String,
        givenName: String?,
        familyName: String?,
        photoUrl: String?,
    ): Result<AuthUser> = stateMutex.withLock {
        val cleanUniqueId = uniqueId.trim()
        val cleanEmail = email.trim().lowercase()
        if (cleanUniqueId.isBlank() && cleanEmail.isBlank()) {
            return@withLock Result.failure(
                IllegalStateException("Google Sign-In did not return an account id. Please try again.")
            )
        }

        val accountKey = cleanEmail.ifBlank { "google:$cleanUniqueId" }
        val existing = accounts[accountKey]
        // Linking Google onto an existing local/Google account must keep that account's id:
        // the stored UserProfile is keyed by it, so a new id would orphan the profile.
        val id = existing?.id
            ?: if (cleanUniqueId.isNotBlank()) "google:$cleanUniqueId" else "google:$cleanEmail"
        val resolvedName = displayName.trim().ifBlank {
            listOfNotNull(givenName?.trim(), familyName?.trim())
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { existing?.displayName ?: "Google User" }
        }
        val stored = StoredAccount(
            id = id,
            email = cleanEmail,
            displayName = resolvedName,
            salt = existing?.salt ?: "",
            passwordHash = existing?.passwordHash ?: "",
            provider = "google",
            givenName = givenName?.trim().orEmpty(),
            familyName = familyName?.trim().orEmpty(),
            photoUrl = photoUrl?.trim().orEmpty(),
        )
        accounts[accountKey] = stored
        persistAccounts()

        val user = AuthUser(
            id = id,
            email = stored.email,
            displayName = stored.displayName,
            avatarUrl = stored.photoUrl.takeIf { it.isNotBlank() },
        )
        current = user
        persistCurrentUser(user)
        _authState.value = AuthState.Authenticated(user)
        Result.success(user)
    }

    override suspend fun signInWithApple(identityToken: String): Result<AuthUser> = stateMutex.withLock {
        val clean = identityToken.trim().ifBlank { "local" }
        val user = AuthUser(
            id = "apple:$clean",
            email = "",
            displayName = "Apple User",
        )
        current = user
        persistCurrentUser(user)
        _authState.value = AuthState.Authenticated(user)
        Result.success(user)
    }

    override suspend fun continueAsGuest(): Result<AuthUser> = stateMutex.withLock {
        current = null
        persistCurrentUser(null)
        _authState.value = AuthState.Guest
        Result.success(AuthUser(id = "guest", email = "", displayName = "Guest", isGuest = true))
    }

    override suspend fun signOut() {
        stateMutex.withLock {
            current = null
            persistCurrentUser(null)
            _authState.value = AuthState.Guest
        }
        clearPlatformCredentialState()
    }

    override suspend fun deleteAccount(): Result<Unit> = stateMutex.withLock {
        val deleted = stateMutex.withLock {
            val c = current ?: return@withLock false
            accounts.remove(c.email.lowercase().ifBlank { c.id })
            persistAccounts()
            current = null
            persistCurrentUser(null)
            _authState.value = AuthState.Guest
            true
        }
        if (!deleted) return Result.failure(IllegalStateException("Not signed in"))
        clearPlatformCredentialState()
        return Result.success(Unit)
    }

    override suspend fun updateDisplayName(displayName: String): Result<AuthUser> = stateMutex.withLock {
        val c = current ?: return@withLock Result.failure(IllegalStateException("Not signed in"))
        val clean = displayName.trim()
        if (clean.isBlank()) {
            return@withLock Result.failure(IllegalArgumentException("Display name cannot be blank."))
        }
        val updated = c.copy(displayName = clean)
        current = updated
        persistCurrentUser(updated)
        val key = c.email.lowercase().ifBlank { c.id }
        accounts[key]?.let { existing ->
            accounts[key] = existing.copy(displayName = clean)
            persistAccounts()
        }
        _authState.value = AuthState.Authenticated(updated)
        Result.success(updated)
    }

    private fun isValidEmail(email: String): Boolean {
        val at = email.indexOf('@')
        val dot = email.lastIndexOf('.')
        return at > 0 && dot > at + 1 && dot < email.length - 1 && !email.contains(' ')
    }

    companion object {
        private const val KEY_ACCOUNTS = "sakinah_auth_accounts_v1"
        private const val KEY_CURRENT_USER = "sakinah_auth_current_user_v1"
    }
}