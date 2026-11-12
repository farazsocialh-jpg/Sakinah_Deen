package com.sakinah.deen.fakes

import com.sakinah.deen.data.repository.NisabRateRepository
import com.sakinah.deen.data.repository.NisabRates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Mirrors the real repository contract: [refresh] never throws. When [shouldFail] is
 * set the last cached rate is kept, which is what the offline path relies on.
 */
class FakeNisabRateRepository(
    initial: NisabRates = NisabRates(88.40f, 1.15f, 1_700_000_000_000L),
    private val fresh: NisabRates = NisabRates(90.0f, 1.20f, 1_760_000_000_000L)
) : NisabRateRepository {

    private val _rates = MutableStateFlow(initial)
    override val rates: StateFlow<NisabRates> = _rates
    var refreshCalled = false
    var shouldFail = false

    override suspend fun refresh() {
        refreshCalled = true
        if (shouldFail) return
        _rates.value = fresh
    }

    fun setRates(rates: NisabRates) {
        _rates.value = rates
    }
}