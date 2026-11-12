package com.sakinah.deen.settings

import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsDefaultsTest {
    @Test
    fun defaults() {
        val repo = SettingsRepositoryImpl(com.sakinah.deen.settings.InMemorySettings())
        val s = repo.settings.value
        assertEquals(CalculationMethod.MWL, s.prayer.calculationMethod)
        assertEquals(AsrMethod.SHAFI, s.prayer.asrMethod)
        assertEquals(ThemeMode.SYSTEM, s.appearance.theme)
        assertEquals(AdhanChoice.MAKKAH, s.adhan.adhanChoice)
    }
}