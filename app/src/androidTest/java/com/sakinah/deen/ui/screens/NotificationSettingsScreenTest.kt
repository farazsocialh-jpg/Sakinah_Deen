package com.sakinah.deen.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sakinah.deen.data.model.AzaanSettings
import com.sakinah.deen.data.model.Muezzin
import com.sakinah.deen.data.prefs.SakinahPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for the notification screen's single-preview rule.
 *
 * The muezzin picker must never let two recordings play at once, must stop on a second tap, and
 * must release the player when the screen leaves composition or the app is paused/stopped.
 * A recording [AzaanPreviewController] is injected so the behaviour can be observed without
 * decoding audio on the test device.
 */
@RunWith(AndroidJUnit4::class)
class NotificationSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val lifecycleOwner = TestLifecycleOwner(Lifecycle.State.RESUMED)
    private val showScreen: MutableState<Boolean> = mutableStateOf(true)
    private lateinit var preview: RecordingPreviewPlayer

    @Before
    fun setUp() {
        // The per-prayer chips are disabled while Azaan is off; the tests need them tappable.
        val preferences = SakinahPreferences(context)
        preferences.azaanSettings = AzaanSettings(enabled = true)
        preview = RecordingPreviewPlayer()
        showScreen.value = true
    }

    @Test
    fun tappingAVoicePreviewsItAndATapOnTheActiveVoiceStopsIt() {
        showScreen()

        previewChip(0).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(Muezzin.entries[0]), preview.played)
        composeRule.onAllNodesWithText("Stop").assertCountEquals(1)
        val stopsBeforeSecondTap = preview.stopCalls

        composeRule.onNodeWithText("Stop").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertTrue(
            "the active preview must stop on a second tap",
            preview.stopCalls > stopsBeforeSecondTap
        )
        assertEquals("no second recording may start", 1, preview.played.size)
        composeRule.onAllNodesWithText("Stop").assertCountEquals(0)
    }

    @Test
    fun startingASecondPreviewStopsTheFirstOne() {
        showScreen()

        previewChip(0).performClick()
        composeRule.waitForIdle()
        previewChip(0).performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(Muezzin.entries[0], Muezzin.entries[1]),
            preview.played
        )
        assertEquals(
            listOf("play:${Muezzin.entries[0].id}", "stop", "play:${Muezzin.entries[1].id}"),
            preview.events
        )
        composeRule.onAllNodesWithText("Stop").assertCountEquals(1)
    }

    @Test
    fun pausingTheAppStopsAndClearsThePreview() {
        showScreen()
        previewChip(0).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Stop").assertCountEquals(1)

        val stopsBeforePause = preview.stopCalls
        composeRule.runOnUiThread { lifecycleOwner.currentState = Lifecycle.State.CREATED }
        assertTrue(
            "ON_PAUSE must stop the preview",
            preview.stopCalls > stopsBeforePause
        )

        composeRule.runOnUiThread { lifecycleOwner.currentState = Lifecycle.State.RESUMED }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Stop").assertCountEquals(0)
    }

    @Test
    fun leavingTheScreenReleasesThePlayer() {
        showScreen()
        previewChip(0).performClick()
        composeRule.waitForIdle()

        composeRule.runOnUiThread { showScreen.value = false }
        composeRule.waitForIdle()

        assertEquals("the shared player must be released on dispose", 1, preview.releaseCalls)
    }

    private fun showScreen() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                if (showScreen.value) {
                    NotificationSettingsScreen(
                        onBack = {},
                        previewPlayerFactory = { preview }
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun previewChip(index: Int) =
        composeRule.onAllNodesWithText("Preview")[index].performScrollTo()

    private class RecordingPreviewPlayer : AzaanPreviewController {
        override var onFinished: (() -> Unit)? = null
        val played = mutableListOf<Muezzin>()
        val events = mutableListOf<String>()
        var stopCalls = 0
        var releaseCalls = 0

        override fun play(muezzin: Muezzin): Boolean {
            played += muezzin
            events += "play:${muezzin.id}"
            return true
        }

        override fun stop() {
            stopCalls++
            if (events.none { it == "stop" } || events.last() != "stop") events += "stop"
        }

        override fun release() {
            releaseCalls++
            events += "release"
        }
    }
}
