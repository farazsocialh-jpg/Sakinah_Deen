package com.sakinah.deen.auth

import com.sakinah.deen.settings.InMemorySettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersistentGoogleAccountTest {

    @Test
    fun googleSignIn_createsPasswordlessAccountAndSurvivesRestart() = runTest {
        val prefs = InMemorySettings()
        val repo = PersistentAuthRepository(prefs)

        // Without any identifier there is nothing to key the account on.
        assertTrue(repo.signInWithGoogle("", "", "", null, null, null).isFailure)

        val created = repo.signInWithGoogle(
            uniqueId = "1188776655",
            email = "Amina@Gmail.com",
            displayName = "Amina Yusuf",
            givenName = "Amina",
            familyName = "Yusuf",
            photoUrl = "https://example.com/amina.png"
        )
        assertTrue(created.isSuccess)
        val user = created.getOrThrow()
        assertEquals("amina@gmail.com", user.email)
        assertEquals("Amina Yusuf", user.displayName)
        assertEquals("https://example.com/amina.png", user.avatarUrl)
        assertTrue(repo.authState.value is AuthState.Authenticated)

        // A Google-only account has no local password, so password sign-in stays rejected.
        repo.signOut()
        assertTrue(repo.signIn("amina@gmail.com", "anything").isFailure)

        // Signing in with Google again reuses the same account and keeps the stored identity.
        val restarted = PersistentAuthRepository(prefs)
        val again = restarted.signInWithGoogle("1188776655", "amina@gmail.com", "", null, null, null)
        assertTrue(again.isSuccess)
        assertEquals("Amina Yusuf", again.getOrThrow().displayName)
    }

    @Test
    fun googleSignIn_withoutEmail_restoresFromIdKey() = runTest {
        val prefs = InMemorySettings()
        val repo = PersistentAuthRepository(prefs)

        assertTrue(repo.signInWithGoogle("g-1", "", "No Email", null, null, null).isSuccess)

        // The session is restored on the next launch even though the account has no email.
        val restarted = PersistentAuthRepository(prefs)
        val state = restarted.authState.value
        assertTrue(state is AuthState.Authenticated)
        assertEquals("No Email", state.user.displayName)
    }

    @Test
    fun linkingGoogleToLocalAccount_keepsPasswordSignInWorking() = runTest {
        val prefs = InMemorySettings()
        val repo = PersistentAuthRepository(prefs)

        assertTrue(repo.signUp("bilal@sakinah.app", "secret12", "Bilal").isSuccess)
        repo.signOut()

        // Same email: the Google profile is linked onto the existing local record.
        val linked = repo.signInWithGoogle(
            uniqueId = "g-77",
            email = "bilal@sakinah.app",
            displayName = "Bilal A.",
            givenName = "Bilal",
            familyName = "A.",
            photoUrl = null
        )
        assertTrue(linked.isSuccess)
        repo.signOut()

        val localSignIn = repo.signIn("bilal@sakinah.app", "secret12")
        assertTrue(localSignIn.isSuccess)
        assertEquals("Bilal A.", localSignIn.getOrThrow().displayName)
    }

    @Test
    fun signOut_runsPlatformCredentialHook() = runTest {
        var hookCalls = 0
        val repo = PersistentAuthRepository(
            prefs = InMemorySettings(),
            onSignOut = { hookCalls++ }
        )

        repo.signInWithGoogle("g-9", "hamza@sakinah.app", "Hamza", null, null, null)
        repo.signOut()

        assertEquals(1, hookCalls)
        assertEquals(AuthState.Guest, repo.authState.value)
    }
}
