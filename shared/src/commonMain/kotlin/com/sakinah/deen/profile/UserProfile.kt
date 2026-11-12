package com.sakinah.deen.profile

import kotlinx.coroutines.flow.StateFlow

enum class Madhhab { SHAFI, HANAFI, MALIKI, HANBALI }
enum class JourneyTrack { SEEKER, STUDENT, SCHOLAR }

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
    private val _profile = kotlinx.coroutines.flow.MutableStateFlow<UserProfile?>(null)
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