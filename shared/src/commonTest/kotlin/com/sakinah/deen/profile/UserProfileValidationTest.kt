package com.sakinah.deen.profile

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserProfileValidationTest {
    @Test
    fun validProfile() {
        val repo = InMemoryUserProfileRepository()
        val p = UserProfile(displayName = "Alice", email = "a@b.com")
        assertTrue(repo.validate(p))
    }

    @Test
    fun invalidProfile() {
        val repo = InMemoryUserProfileRepository()
        val p = UserProfile(displayName = "", email = "not-email")
        assertFalse(repo.validate(p))
    }
}