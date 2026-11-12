package com.sakinah.deen.domain.travel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide broadcast of the saved Home Location.
 *
 * The Salah ViewModel is created once and hoisted by the nav host, so a Home saved
 * from Settings or Travel has to reach it without recreating the screen. Publishing
 * goes through [SafarPolicy.sanitize], so `(0.0, 0.0)` can never become a Home.
 */
object HomeAnchorStore {

    private val _state = MutableStateFlow<Pair<Double, Double>?>(null)
    val state: StateFlow<Pair<Double, Double>?> = _state.asStateFlow()

    fun publish(home: Pair<Double, Double>?) {
        _state.value = SafarPolicy.sanitize(home)
    }
}
