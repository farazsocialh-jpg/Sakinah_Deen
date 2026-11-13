package com.sakinah.deen.auth

import com.sakinah.deen.profile.PersistentUserProfileRepository
import com.sakinah.deen.profile.UserProfile
import com.sakinah.deen.settings.InMemorySettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PersistentAccountTest {

    @Test
    fun signUp_validatesInputsAndPersistsAcrossRestart() = runTest {
        val prefs = InMemorySettings()
        val firstRepo = PersistentAuthRepository(prefs)

        assertTrue(firstRepo.signUp("invalid-email", "secret12", "Amina").isFailure)
        assertTrue(firstRepo.signUp("amina@sakinah.app", "123", "Amina").isFailure)
        assertTrue(firstRepo.signUp("amina@sakinah.app", "secret12", "  ").isFailure)

        val created = firstRepo.signUp("Amina@Sakinah.App", "secret12", "Amina")
        assertTrue(created.isSuccess)
        assertEquals("amina@sakinah.app", created.getOrThrow().email)

        // Simulate app restart with the same backing Settings store
        val restartedRepo = PersistentAuthRepository(prefs)
        val restoredState = restartedRepo.authState.value
        assertTrue(restoredState is AuthState.Authenticated)
        assertEquals("Amina", restoredState.user.displayName)

        // Sign out and verify wrong password rejection vs valid sign in
        restartedRepo.signOut()
        assertEquals(AuthState.Guest, restartedRepo.authState.value)
        assertTrue(restartedRepo.signIn("amina@sakinah.app", "wrongpass").isFailure)

        val signedIn = restartedRepo.signIn("amina@sakinah.app", "secret12")
        assertTrue(signedIn.isSuccess)
        assertEquals("Amina", signedIn.getOrThrow().displayName)
    }

    @Test
    fun updateDisplayName_updatesAuthStateAndPersists() = runTest {
        val prefs = InMemorySettings()
        val repo = PersistentAuthRepository(prefs)
        repo.signUp("bilal@sakinah.app", "password99", "Bilal")

        val updated = repo.updateDisplayName("Bilal ibn Rabah")
        assertTrue(updated.isSuccess)
        assertEquals("Bilal ibn Rabah", updated.getOrThrow().displayName)

        val restarted = PersistentAuthRepository(prefs)
        val state = restarted.authState.value as AuthState.Authenticated
        assertEquals("Bilal ibn Rabah", state.user.displayName)
    }

    @Test
    fun persistentUserProfile_savesAndRestoresAcrossRestart() = runTest {
        val prefs = InMemorySettings()
        val profileRepo = PersistentUserProfileRepository(prefs)

        assertFalse(profileRepo.validate(UserProfile(displayName = "", email = "bad")))
        val profile = UserProfile(
            displayName = "Zayd",
            email = "zayd@sakinah.app",
            defaultCity = "Doha, Qatar"
        )
        assertTrue(profileRepo.validate(profile))
        profileRepo.update(profile)

        val restarted = PersistentUserProfileRepository(prefs)
        val loaded = restarted.profile.value
        assertNotNull(loaded)
        assertEquals("Zayd", loaded.displayName)
        assertEquals("Doha, Qatar", loaded.defaultCity)
    }
}
