package com.sakinah.deen.profile

import com.sakinah.deen.settings.InMemorySettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Signing out must end the session without deleting the stored profile. */
class ProfileSessionTest {

    @Test
    fun clearActiveSession_keepsStoredProfileForNextSignIn() = runTest {
        val prefs = InMemorySettings()
        val repo = PersistentUserProfileRepository(prefs)

        repo.loadForUser("user-1")
        repo.update(
            UserProfile(
                displayName = "Amina",
                email = "amina@sakinah.app",
                uniqueId = "user-1",
                gender = "FEMALE",
                dateOfBirth = "2000-06-15",
                city = "Doha",
                country = "Qatar"
            )
        )

        repo.clearActiveSession()
        assertNull(repo.profile.value, "the session should be empty after signing out")

        // Signing back in as the same account restores every collected field.
        repo.loadForUser("user-1")
        val restored = repo.profile.value
        assertEquals("Amina", restored?.displayName)
        assertEquals("FEMALE", restored?.gender)
        assertEquals("2000-06-15", restored?.dateOfBirth)
        assertEquals("Doha", restored?.city)
        assertEquals("Qatar", restored?.country)
    }

    @Test
    fun clear_deletesProfileForAccountDeletion() = runTest {
        val prefs = InMemorySettings()
        val repo = PersistentUserProfileRepository(prefs)

        repo.loadForUser("user-2")
        repo.update(UserProfile(displayName = "Bilal", uniqueId = "user-2", city = "Cairo"))

        repo.clear()
        repo.loadForUser("user-2")

        assertEquals("", repo.profile.value?.city.orEmpty())
    }
}
