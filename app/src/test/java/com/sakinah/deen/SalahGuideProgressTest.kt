package com.sakinah.deen

import com.sakinah.deen.data.prefs.SalahGuideProgress
import com.sakinah.deen.data.prefs.SalahGuideProgressStore
import com.sakinah.deen.fakes.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Mark as learned" and favourites have to survive a restart and must never shift onto a
 * different posture when the step lists change, so progress is keyed by stable strings.
 */
class SalahGuideProgressTest {

    private fun store(prefs: FakeSharedPreferences = FakeSharedPreferences()) =
        SalahGuideProgressStore(prefs)

    @Test
    fun learnedStepsToggleAndPersistAcrossRestarts() {
        val prefs = FakeSharedPreferences()
        val store = store(prefs)
        store.toggleLearned("SALAH:Qiyam (Hand Placement & Recitation)")
        store.toggleLearned("WUDU:5. Dua After Wudu")

        assertTrue(store.progress.value.isLearned("SALAH:Qiyam (Hand Placement & Recitation)"))

        val reopened = store(prefs)
        assertEquals(2, reopened.progress.value.learnedStepKeys.size)
        assertTrue(reopened.progress.value.isLearned("WUDU:5. Dua After Wudu"))

        // Toggling again removes it, in this process and after a restart.
        reopened.toggleLearned("WUDU:5. Dua After Wudu")
        assertFalse(reopened.progress.value.isLearned("WUDU:5. Dua After Wudu"))
        assertFalse(store(prefs).progress.value.isLearned("WUDU:5. Dua After Wudu"))
    }

    @Test
    fun favouritesAreTrackedPerStepAndPerAdhkar() {
        val store = store()
        store.toggleFavoriteStep("SALAH:Ruku (90° Bowing)")
        store.toggleFavoriteAdhkar("ayat_al_kursi")

        assertTrue(store.progress.value.isFavoriteStep("SALAH:Ruku (90° Bowing)"))
        assertTrue(store.progress.value.isFavoriteAdhkar("ayat_al_kursi"))
        assertFalse(store.progress.value.isFavoriteAdhkar("tasbih_fatimah"))

        store.toggleFavoriteAdhkar("ayat_al_kursi")
        assertFalse(store.progress.value.isFavoriteAdhkar("ayat_al_kursi"))
    }

    @Test
    fun percentLearnedIsClampedAndIgnoresUnknownStoredKeys() {
        val progress = SalahGuideProgress(
            learnedStepKeys = setOf("A", "B", "REMOVED_STEP")
        )
        val keys = listOf("A", "B", "C", "D")

        assertEquals(2, progress.learnedCount(keys))
        assertEquals(50, progress.percentLearned(keys))
        assertEquals(0, progress.percentLearned(emptyList()))
        assertEquals(100, SalahGuideProgress(setOf("A")).percentLearned(listOf("A")))
    }

    @Test
    fun practiceReminderOptInPersists() {
        val prefs = FakeSharedPreferences()
        val store = store(prefs)
        assertFalse(store.progress.value.practiceReminderEnabled)

        store.setPracticeReminder(true)
        assertTrue(store.progress.value.practiceReminderEnabled)
        assertTrue(store(prefs).progress.value.practiceReminderEnabled)

        store.setPracticeReminder(false)
        assertFalse(store(prefs).progress.value.practiceReminderEnabled)
    }

    @Test
    fun clearingLearnedKeepsFavourites() {
        val store = store()
        store.toggleLearned("SALAH:Taslim (Concluding the Prayer)")
        store.toggleFavoriteStep("SALAH:Taslim (Concluding the Prayer)")

        store.clearLearned()

        assertTrue(store.progress.value.learnedStepKeys.isEmpty())
        assertTrue(store.progress.value.isFavoriteStep("SALAH:Taslim (Concluding the Prayer)"))
    }
}
