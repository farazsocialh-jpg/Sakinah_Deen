package com.sakinah.deen.auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthStateTest {
    @Test
    fun guestByDefault() = runTest {
        val repo = InMemoryAuthRepository()
        assertEquals(AuthState.Guest, repo.authState.first())
    }

    @Test
    fun signUpTransitionsToAuthenticated() = runTest {
        val repo = InMemoryAuthRepository()
        val result = repo.signUp("a@b.com", "password", "Alice")
        assertTrue(result.isSuccess)
        val state = repo.authState.first()
        assertTrue(state is AuthState.Authenticated)
        assertEquals("Alice", (state as AuthState.Authenticated).user.displayName)
    }

    @Test
    fun signOutTransitionsToGuest() = runTest {
        val repo = InMemoryAuthRepository()
        repo.signUp("a@b.com", "password", "Alice")
        repo.signOut()
        assertEquals(AuthState.Guest, repo.authState.first())
    }

    @Test
    fun deleteAccountReturnsToGuest() = runTest {
        val repo = InMemoryAuthRepository()
        repo.signUp("a@b.com", "password", "Alice")
        val result = repo.deleteAccount()
        assertTrue(result.isSuccess)
        assertEquals(AuthState.Guest, repo.authState.first())
    }
}