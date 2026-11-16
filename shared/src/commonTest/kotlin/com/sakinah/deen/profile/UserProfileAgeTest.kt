package com.sakinah.deen.profile

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserProfileAgeTest {

    @Test
    fun ageYears_isDerivedFromDateOfBirthAndHandlesBirthdayBoundary() {
        val profile = UserProfile(displayName = "Amina", dateOfBirth = "2000-06-15")

        assertEquals(24, profile.ageYears(LocalDate(2025, 6, 14)))
        assertEquals(25, profile.ageYears(LocalDate(2025, 6, 15)))
        assertEquals(25, profile.ageYears(LocalDate(2025, 12, 31)))
        assertEquals(0, profile.ageYears(LocalDate(2000, 6, 15)))
    }

    @Test
    fun ageYears_isNullWhenDateIsMissingInvalidOrInTheFuture() {
        assertNull(UserProfile(dateOfBirth = "").ageYears(LocalDate(2025, 1, 1)))
        assertNull(UserProfile(dateOfBirth = "not-a-date").ageYears(LocalDate(2025, 1, 1)))
        assertNull(UserProfile(dateOfBirth = "2030-01-01").ageYears(LocalDate(2025, 1, 1)))
    }

    @Test
    fun isProfileComplete_requiresEveryCollectedField() {
        val complete = UserProfile(
            displayName = "Amina",
            gender = "FEMALE",
            dateOfBirth = "2000-06-15",
            city = "Doha",
            country = "Qatar"
        )
        assertTrue(complete.isProfileComplete())

        assertFalse(complete.copy(displayName = "  ").isProfileComplete())
        assertFalse(complete.copy(gender = "").isProfileComplete())
        assertFalse(complete.copy(dateOfBirth = "").isProfileComplete())
        assertFalse(complete.copy(city = "").isProfileComplete())
        assertFalse(complete.copy(country = "").isProfileComplete())
    }

    @Test
    fun pictureUrl_prefersGooglePhotoAndFallsBackToLegacyAvatar() {
        assertEquals(
            "https://example.com/google.png",
            UserProfile(photoUrl = "https://example.com/google.png", avatarUrl = "legacy").pictureUrl()
        )
        assertEquals("legacy", UserProfile(avatarUrl = "legacy").pictureUrl())
        assertEquals("", UserProfile().pictureUrl())
    }
}
