package com.sakinah.deen.auth

import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Multiplatform persistent [AuthRepository] backed by [Settings] (encrypted
 * SharedPreferences on Android, NSUserDefaults on iOS). Restores the active session on startup
 * and stores only PBKDF2-HMAC-SHA256 hashes over a random per-account salt.
 * Adds rate limiting: lock an account after 5 consecutive failed sign-in attempts for that
 * account, with the streak expiring after five minutes. OAuth identities are stored as stable,
 * non-secret provider subjects — raw identity tokens are never written to disk.
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

    /** One failed-sign-in streak per account; keyed in [Settings] by the account key. */
    @Serializable
    private data class LoginFailures(
        val count: Int = 0,
        val lastFailureAtMs: Long = 0L,
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

    // --- Rate-limiting state (per account, with an expiry) ---
    private val LOCKOUT_RESET_MS = 300_000L // 5 minutes
    private val LOCKOUT_AFTER = 5

    private fun failureKey(accountKey: String): String = "$KEY_LOGIN_FAILURES:$accountKey"

    private fun readFailures(accountKey: String): LoginFailures {
        val raw = prefs.getStringOrNull(failureKey(accountKey)) ?: return LoginFailures()
        return runCatching { json.decodeFromString<LoginFailures>(raw) }.getOrDefault(LoginFailures())
    }

    private fun writeFailures(accountKey: String, failures: LoginFailures) {
        if (failures.count <= 0) {
            prefs.remove(failureKey(accountKey))
        } else {
            prefs.putString(failureKey(accountKey), json.encodeToString(failures))
        }
    }

    /** A streak whose last failure is older than the window is expired and starts over. */
    private fun activeFailures(accountKey: String): LoginFailures {
        val failures = readFailures(accountKey)
        if (failures.count <= 0) return failures
        val elapsed = Clock.System.now().toEpochMilliseconds() - failures.lastFailureAtMs
        return if (elapsed >= LOCKOUT_RESET_MS || elapsed < 0) {
            writeFailures(accountKey, LoginFailures())
            LoginFailures()
        } else {
            failures
        }
    }

    private fun isLockedOut(accountKey: String): Boolean =
        activeFailures(accountKey).count >= LOCKOUT_AFTER

    /** Records one failure and returns true once the account reached the lockout threshold. */
    private fun recordFailure(accountKey: String): Boolean {
        val count = activeFailures(accountKey).count + 1
        writeFailures(
            accountKey,
            LoginFailures(count, Clock.System.now().toEpochMilliseconds()),
        )
        return count >= LOCKOUT_AFTER
    }

    /** Cleared only after a successful password sign-in for this specific account. */
    private fun clearFailures(accountKey: String) = writeFailures(accountKey, LoginFailures())

    private fun lockoutMessage(): String =
        "Account locked due to multiple failed attempts. " +
            "Please try again in ${LOCKOUT_RESET_MS / 60_000} minutes."

    /** Best effort: a failed provider sign-out must never block the local sign-out. */
    private suspend fun clearPlatformCredentialState() {
        runCatching { onSignOut?.invoke() }
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

    /* --- OAuth identity handling ----------------------------------------- */

    /**
     * Stable, non-secret provider id. The `sub` claim of a JWT-shaped credential is preferred;
     * when no subject is present the token is reduced to a one-way fingerprint so no raw
     * identity token is ever persisted. A plain platform unique id is already non-secret and
     * stable, so it is used as-is.
     */
    private fun providerIdentity(provider: String, credential: String): String {
        val trimmed = credential.trim()
        val jwt = looksLikeJwt(trimmed)
        val subject = if (jwt) jwtSubject(trimmed) else null
        val identifier = when {
            !subject.isNullOrBlank() -> subject
            jwt -> PasswordHasher.fingerprint(trimmed)
            else -> trimmed
        }
        return "$provider:$identifier"
    }

    private fun looksLikeJwt(value: String): Boolean {
        val parts = value.split('.')
        return parts.size == 3 && parts.all { part ->
            part.isNotEmpty() &&
                part.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '=' }
        }
    }

    /** Reads just the `sub` claim; signature verification remains the provider SDK's job. */
    private fun jwtSubject(token: String): String? {
        val payload = token.split('.').getOrNull(1) ?: return null
        val decoded = decodeBase64Url(payload) ?: return null
        val primitive = runCatching {
            (json.parseToJsonElement(decoded) as? JsonObject)?.get("sub") as? JsonPrimitive
        }.getOrNull()
        return primitive?.content?.takeIf { it.isNotBlank() }
    }

    private fun decodeBase64Url(value: String): String? {
        val bytes = ArrayList<Byte>(value.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (ch in value) {
            if (ch == '=') break
            val digit = when (ch) {
                in 'A'..'Z' -> ch - 'A'
                in 'a'..'z' -> ch - 'a' + 26
                in '0'..'9' -> ch - '0' + 52
                '-' -> 62
                '_' -> 63
                else -> return null
            }
            buffer = (buffer shl 6) or digit
            bits += 6
            if (bits >= 8) {
                bits -= 8
                bytes.add(((buffer shr bits) and 0xff).toByte())
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    /**
     * Legacy records embedded the raw provider credential in the id (`apple:<token>` or
     * `google:<token>`). On the next sign-in we know both the raw token and the new stable id,
     * so only the matching record is migrated; ids of other accounts are left untouched.
     */
    private fun migrateLegacyIdentity(provider: String, rawCredential: String, stableId: String) {
        val legacyId = "$provider:${rawCredential.trim()}"
        val legacyKeys = accounts.entries
            .filter { (key, account) -> key == legacyId || account.id == legacyId }
            .map { (key, _) -> key }
        if (legacyKeys.isNotEmpty()) {
            legacyKeys.forEach { oldKey ->
                val account = accounts.remove(oldKey) ?: return@forEach
                val newKey = if (oldKey == legacyId) stableId else oldKey
                accounts[newKey] = account.copy(id = stableId)
            }
            persistAccounts()
        }
        current?.let { user ->
            if (user.id == legacyId) {
                val updated = user.copy(id = stableId)
                current = updated
                persistCurrentUser(updated)
            }
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
        // Reject locked accounts before doing the (expensive) KDF work. An expired streak is
        // cleared here, so the account recovers automatically after LOCKOUT_RESET_MS.
        if (isLockedOut(cleanEmail)) {
            return@withLock Result.failure(IllegalStateException(lockoutMessage()))
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
            // Count failures against this account only; no account rewrite is needed.
            if (recordFailure(cleanEmail)) {
                return@withLock Result.failure(IllegalStateException(lockoutMessage()))
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
        // Only a successful password sign-in clears this account's failure streak.
        clearFailures(cleanEmail)
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

        val identity = if (cleanUniqueId.isBlank()) "" else providerIdentity("google", cleanUniqueId)
        // Replace a legacy id that embedded the raw credential before looking the account up.
        if (identity.isNotBlank()) {
            migrateLegacyIdentity("google", cleanUniqueId, identity)
        }

        val accountKey = cleanEmail.ifBlank { identity }
        val existing = accounts[accountKey]
        // Linking Google onto an existing local/Google account must keep that account's id:
        // the stored UserProfile is keyed by it, so a new id would orphan the profile.
        val id = existing?.id?.takeIf { it.isNotBlank() }
            ?: identity.ifBlank { "google:$cleanEmail" }
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
        val cleanToken = identityToken.trim()
        val id = if (cleanToken.isBlank()) "apple:local" else providerIdentity("apple", cleanToken)
        if (cleanToken.isNotBlank()) {
            migrateLegacyIdentity("apple", cleanToken, id)
        }
        val user = AuthUser(
            id = id,
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

    override suspend fun deleteAccount(): Result<Unit> {
        // One non-reentrant Mutex acquisition only: nesting withLock on the same mutex
        // deadlocked forever, so delete never completed.
        val deleted = stateMutex.withLock {
            val c = current ?: return@withLock false
            val accountKey = c.email.lowercase().ifBlank { c.id }
            accounts.remove(accountKey)
            persistAccounts()
            clearFailures(accountKey)
            current = null
            persistCurrentUser(null)
            _authState.value = AuthState.Guest
            true
        }
        if (!deleted) return Result.failure(IllegalStateException("Not signed in"))
        // Run the platform hook outside the lock; best effort only.
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
        private const val KEY_LOGIN_FAILURES = "sakinah_auth_login_failures_v1"
    }
}
