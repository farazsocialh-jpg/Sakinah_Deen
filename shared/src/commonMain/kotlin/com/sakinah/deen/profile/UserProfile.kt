package com.sakinah.deen.profile

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class Madhhab { SHAFI, HANAFI, MALIKI, HANBALI }

@Serializable
enum class JourneyTrack { SEEKER, STUDENT, SCHOLAR }

/**
 * The user's account and demographic profile.
 *
 * `city`, `country`, `gender` and `dateOfBirth` cannot come from Google Sign-In (the ID token
 * only carries identity fields), so they are collected in the post-sign-in completion step.
 * All new fields have defaults, which keeps profiles written by older app versions readable.
 */
@Serializable
data class UserProfile(
    val displayName: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val defaultCity: String = "",
    val madhhab: Madhhab = Madhhab.SHAFI,
    val journeyTrack: JourneyTrack = JourneyTrack.SEEKER,
    val qadhaLogs: List<String> = emptyList(),
    val bookmarks: List<String> = emptyList(),
    /** Stable provider account id (Google `uniqueId`, or the local account id). */
    val uniqueId: String = "",
    /** "google", "local" or "guest". */
    val authProvider: String = "local",
    val givenName: String = "",
    val familyName: String = "",
    /** Google profile picture URL; empty when the user signed up locally. */
    val photoUrl: String = "",
    /** "MALE", "FEMALE" or "" when not collected yet. */
    val gender: String = "",
    /** ISO-8601 calendar date ("YYYY-MM-DD"); empty when not collected yet. */
    val dateOfBirth: String = "",
    val city: String = "",
    val country: String = "",
) {

    /** Parsed [dateOfBirth], or null when it is empty or not a real ISO date. */
    fun birthDate(): LocalDate? {
        if (dateOfBirth.isBlank()) return null
        return runCatching { LocalDate.parse(dateOfBirth.trim()) }
            .getOrNull()
            ?.takeIf { it.year in 1900..2100 }
    }

    /**
     * Age in whole years derived from [dateOfBirth]. Computed on demand so it never goes stale.
     * Returns null when the birthday is missing, unparseable, or in the future.
     */
    fun ageYears(
        currentDate: LocalDate = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
    ): Int? {
        val birth = birthDate() ?: return null
        if (birth > currentDate) return null
        var years = currentDate.year - birth.year
        val hadBirthdayThisYear = currentDate.monthNumber > birth.monthNumber ||
            (currentDate.monthNumber == birth.monthNumber && currentDate.dayOfMonth >= birth.dayOfMonth)
        if (!hadBirthdayThisYear) years -= 1
        return years.coerceAtLeast(0)
    }

    /** Everything the post-sign-in completion step needs before the profile is "done". */
    fun isProfileComplete(): Boolean =
        displayName.isNotBlank() &&
            gender.isNotBlank() &&
            birthDate() != null &&
            city.isNotBlank() &&
            country.isNotBlank()

    /** Best available picture URL (Google photo, then the legacy avatar field). */
    fun pictureUrl(): String = photoUrl.ifBlank { avatarUrl.orEmpty() }
}

interface UserProfileRepository {
    val profile: StateFlow<UserProfile?>
    suspend fun loadForUser(userId: String)
    suspend fun update(profile: UserProfile)

    /** Drops the active profile (used by "Continue as Guest" and account deletion). */
    suspend fun clear()

    /**
     * Ends the active session without deleting anything: the stored profile (gender, date of
     * birth, city, country) stays on disk and is restored the next time this account signs in.
     */
    suspend fun clearActiveSession()

    suspend fun syncCloud()
    fun validate(profile: UserProfile): Boolean
}

class InMemoryUserProfileRepository : UserProfileRepository {
    private val _profile = MutableStateFlow<UserProfile?>(null)
    override val profile: StateFlow<UserProfile?> = _profile

    override suspend fun loadForUser(userId: String) {
        _profile.value = UserProfile(displayName = userId)
    }

    override suspend fun update(profile: UserProfile) {
        _profile.value = profile
    }

    override suspend fun clear() {
        _profile.value = null
    }

    override suspend fun clearActiveSession() {
        _profile.value = null
    }

    override suspend fun syncCloud() { /* no-op offline */ }

    override fun validate(profile: UserProfile): Boolean =
        profile.displayName.isNotBlank() && profile.email.contains("@")
}

/**
 * Persistent [UserProfileRepository] backed by [Settings]. Stores per-user profiles keyed by
 * the active account id and restores the active profile automatically on app restart.
 *
 * Updates are written to the active account key (set by [loadForUser]) rather than to the
 * profile's email, so a Google account — which may have no email — still round-trips.
 */
class PersistentUserProfileRepository(
    private val prefs: Settings = Settings(),
) : UserProfileRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val stateMutex = Mutex()
    private val _profile = MutableStateFlow<UserProfile?>(null)
    override val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private var activeKey: String = prefs.getStringOrNull(KEY_ACTIVE_USER) ?: DEFAULT_KEY

    init {
        val storedKey = prefs.getStringOrNull(KEY_ACTIVE_USER)
        if (!storedKey.isNullOrBlank()) {
            activeKey = storedKey
            _profile.value = readProfile(storedKey)
        }
    }

    override suspend fun loadForUser(userId: String) {
        stateMutex.withLock {
            activeKey = normalize(userId)
            prefs.putString(KEY_ACTIVE_USER, activeKey)
            _profile.value = readProfile(activeKey) ?: UserProfile(
                uniqueId = userId.trim(),
                email = if (activeKey.contains("@")) activeKey else ""
            )
        }
    }

    override suspend fun update(profile: UserProfile) {
        stateMutex.withLock {
            prefs.putString(KEY_ACTIVE_USER, activeKey)
            prefs.putString(profileKey(activeKey), json.encodeToString(profile))
            _profile.value = profile
        }
    }

    override suspend fun clear() {
        stateMutex.withLock {
            prefs.remove(profileKey(activeKey))
            prefs.remove(KEY_ACTIVE_USER)
            activeKey = DEFAULT_KEY
            _profile.value = null
        }
    }

    override suspend fun clearActiveSession() {
        // Forget which account is active but keep its stored profile: signing out must never
        // wipe gender, date of birth, city or country. Only deleteAccount() does that.
        stateMutex.withLock {
            prefs.remove(KEY_ACTIVE_USER)
            activeKey = DEFAULT_KEY
            _profile.value = null
        }
    }

    override suspend fun syncCloud() { /* no-op offline */ }

    override fun validate(profile: UserProfile): Boolean =
        profile.displayName.trim().isNotBlank() &&
            (profile.email.isBlank() || (profile.email.contains("@") && profile.email.contains(".")))

    private fun readProfile(userKey: String): UserProfile? {
        val raw = prefs.getStringOrNull(profileKey(userKey)) ?: return null
        return runCatching { json.decodeFromString<UserProfile>(raw) }.getOrNull()
    }

    private fun profileKey(userKey: String): String = "$KEY_PREFIX$userKey"

    private fun normalize(userId: String): String =
        userId.trim().lowercase().ifBlank { DEFAULT_KEY }

    companion object {
        private const val KEY_ACTIVE_USER = "sakinah_profile_active_user"
        private const val KEY_PREFIX = "sakinah_profile_v1_"
        private const val DEFAULT_KEY = "default"
    }
}
