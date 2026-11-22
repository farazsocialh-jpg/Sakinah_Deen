package com.sakinah.deen.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Learning progress for the Visual Salah Guide and the Hisn al-Muslim adhkar library.
 *
 * Step keys are stable strings (`"SALAH:Qiyam (Hand Placement & Recitation)"`) rather than
 * list indices, so adding a step or switching rakat never moves someone's "learned" tick
 * onto a different posture. Adhkar are keyed by their seed id, which the bundled edition
 * keeps stable.
 */
data class SalahGuideProgress(
    val learnedStepKeys: Set<String> = emptySet(),
    val favoriteStepKeys: Set<String> = emptySet(),
    val favoriteAdhkarIds: Set<String> = emptySet(),
    val practiceReminderEnabled: Boolean = false
) {
    fun isLearned(stepKey: String): Boolean = stepKey in learnedStepKeys

    fun isFavoriteStep(stepKey: String): Boolean = stepKey in favoriteStepKeys

    fun isFavoriteAdhkar(adhkarId: String): Boolean = adhkarId in favoriteAdhkarIds

    /** Learned steps that belong to [stepKeys], clamped to the keys that exist right now. */
    fun learnedCount(stepKeys: Collection<String>): Int = stepKeys.count { it in learnedStepKeys }

    /** 0..100 for a tab's step list; 0 when the tab has no steps at all. */
    fun percentLearned(stepKeys: Collection<String>): Int {
        if (stepKeys.isEmpty()) return 0
        return (learnedCount(stepKeys) * 100) / stepKeys.size
    }
}

/**
 * Persists the above in a small dedicated preference file. SharedPreferences (not Room)
 * because the values are a few short strings that also have to be read by the daily
 * reminder worker, which may run with no database open.
 */
class SalahGuideProgressStore internal constructor(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    companion object {
        const val PREFS_NAME = "sakinah_salah_guide_progress"

        private const val KEY_LEARNED_STEPS = "learned_step_keys"
        private const val KEY_FAVORITE_STEPS = "favorite_step_keys"
        private const val KEY_FAVORITE_ADHKAR = "favorite_adhkar_ids"
        private const val KEY_PRACTICE_REMINDER = "practice_reminder_enabled"
    }

    private val _progress = MutableStateFlow(load())
    val progress: StateFlow<SalahGuideProgress> = _progress.asStateFlow()

    fun toggleLearned(stepKey: String) = update { current ->
        current.copy(
            learnedStepKeys = if (stepKey in current.learnedStepKeys) {
                current.learnedStepKeys - stepKey
            } else {
                current.learnedStepKeys + stepKey
            }
        )
    }

    fun toggleFavoriteStep(stepKey: String) = update { current ->
        current.copy(
            favoriteStepKeys = if (stepKey in current.favoriteStepKeys) {
                current.favoriteStepKeys - stepKey
            } else {
                current.favoriteStepKeys + stepKey
            }
        )
    }

    fun toggleFavoriteAdhkar(adhkarId: String) = update { current ->
        current.copy(
            favoriteAdhkarIds = if (adhkarId in current.favoriteAdhkarIds) {
                current.favoriteAdhkarIds - adhkarId
            } else {
                current.favoriteAdhkarIds + adhkarId
            }
        )
    }

    fun setPracticeReminder(enabled: Boolean) = update { it.copy(practiceReminderEnabled = enabled) }

    fun clearLearned() = update { it.copy(learnedStepKeys = emptySet()) }

    private fun update(transform: (SalahGuideProgress) -> SalahGuideProgress) {
        val next = transform(_progress.value)
        if (next == _progress.value) return
        _progress.value = next
        prefs.edit {
            putStringSet(KEY_LEARNED_STEPS, next.learnedStepKeys)
            putStringSet(KEY_FAVORITE_STEPS, next.favoriteStepKeys)
            putStringSet(KEY_FAVORITE_ADHKAR, next.favoriteAdhkarIds)
            putBoolean(KEY_PRACTICE_REMINDER, next.practiceReminderEnabled)
        }
    }

    private fun load(): SalahGuideProgress = SalahGuideProgress(
        learnedStepKeys = readSet(KEY_LEARNED_STEPS),
        favoriteStepKeys = readSet(KEY_FAVORITE_STEPS),
        favoriteAdhkarIds = readSet(KEY_FAVORITE_ADHKAR),
        practiceReminderEnabled = prefs.getBoolean(KEY_PRACTICE_REMINDER, false)
    )

    /** Defensive copy: a caller mutating the returned set must not corrupt stored prefs. */
    private fun readSet(key: String): Set<String> =
        prefs.getStringSet(key, null)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
}
