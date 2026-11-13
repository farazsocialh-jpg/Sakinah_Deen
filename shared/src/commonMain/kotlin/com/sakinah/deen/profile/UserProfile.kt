package com.sakinah.deen.profile

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class Madhhab { SHAFI, HANAFI, MALIKI, HANBALI }

@Serializable
enum class JourneyTrack { SEEKER, STUDENT, SCHOLAR }

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
)

interface UserProfileRepository {
    val profile: StateFlow<UserProfile?>
    suspend fun loadForUser(userId: String)
    suspend fun update(profile: UserProfile)
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

    override suspend fun syncCloud() { /* no-op offline */ }

    override fun validate(profile: UserProfile): Boolean =
        profile.displayName.isNotBlank() && profile.email.contains("@")
}

/**
 * Persistent [UserProfileRepository] backed by [Settings]. Stores per-user profiles keyed by
 * userId/email and restores the active profile automatically on app restart.
 */
class PersistentUserProfileRepository(
    private val prefs: Settings = Settings(),
) : UserProfileRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _profile = MutableStateFlow<UserProfile?>(null)
    override val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    init {
        val activeKey = prefs.getStringOrNull(KEY_ACTIVE_USER)
        if (!activeKey.isNullOrBlank()) {
            _profile.value = readProfile(activeKey)
        }
    }

    override suspend fun loadForUser(userId: String) {
        val cleanId = userId.trim().lowercase().ifBlank { "default" }
        prefs.putString(KEY_ACTIVE_USER, cleanId)
        val existing = readProfile(cleanId)
        if (existing != null) {
            _profile.value = existing
        } else {
            val initialEmail = if (cleanId.contains("@")) cleanId else ""
            val created = UserProfile(
                displayName = "",
                email = initialEmail,
            )
            _profile.value = created
        }
    }

    override suspend fun update(profile: UserProfile) {
        val key = profile.email.trim().lowercase().ifBlank {
            prefs.getStringOrNull(KEY_ACTIVE_USER) ?: "default"
        }
        prefs.putString(KEY_ACTIVE_USER, key)
        prefs.putString("$KEY_PREFIX$key", json.encodeToString(profile))
        _profile.value = profile
    }

    override suspend fun syncCloud() { /* no-op offline */ }

    override fun validate(profile: UserProfile): Boolean =
        profile.displayName.trim().isNotBlank() &&
            (profile.email.isBlank() || (profile.email.contains("@") && profile.email.contains(".")))

    private fun readProfile(userKey: String): UserProfile? {
        val raw = prefs.getStringOrNull("$KEY_PREFIX$userKey") ?: return null
        return runCatching { json.decodeFromString<UserProfile>(raw) }.getOrNull()
    }

    companion object {
        private const val KEY_ACTIVE_USER = "sakinah_profile_active_user"
        private const val KEY_PREFIX = "sakinah_profile_v1_"
    }
}