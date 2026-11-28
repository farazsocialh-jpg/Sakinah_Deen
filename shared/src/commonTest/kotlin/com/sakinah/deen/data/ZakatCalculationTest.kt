package com.sakinah.deen.data

import com.sakinah.deen.data.model.ZakatCalculation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZakatCalculationTest {

    @Test
    fun zakatDueWhenAboveNisab() {
        val z = ZakatCalculation(
            cashOnHand = 10000.0,
            goldGrams = 0.0,
            silverGrams = 0.0,
            tradeGoods = 0.0,
            investmentEquities = 0.0,
            deductibleDebts = 0.0,
            goldPricePerGram = 70.0,
            silverPricePerGram = 0.8
        )
        assertTrue(z.isZakatDue)
        assertEquals(250.0, z.zakatPayable, 0.001)
    }

    @Test
    fun noZakatBelowNisab() {
        val z = ZakatCalculation(
            cashOnHand = 100.0,
            goldGrams = 0.0,
            silverGrams = 0.0,
            tradeGoods = 0.0,
            investmentEquities = 0.0,
            deductibleDebts = 0.0,
            goldPricePerGram = 70.0,
            silverPricePerGram = 0.8
        )
        assertFalse(z.isZakatDue)
        assertEquals(0.0, z.zakatPayable, 0.001)
    }

    @Test
    fun silverStandardNisab() {
        val z = ZakatCalculation(
            cashOnHand = 500.0,
            goldGrams = 0.0,
            silverGrams = 0.0,
            tradeGoods = 0.0,
            investmentEquities = 0.0,
            deductibleDebts = 0.0,
            goldPricePerGram = 70.0,
            silverPricePerGram = 0.8,
            useSilverStandard = true
        )
        assertEquals(0.8 * 595.0, z.nisabThreshold, 0.001)
    }

    @Test
    fun debtsReduceZakatableWealth() {
        val z = ZakatCalculation(
            cashOnHand = 10000.0,
            goldGrams = 0.0,
            silverGrams = 0.0,
            tradeGoods = 0.0,
            investmentEquities = 0.0,
            deductibleDebts = 9500.0,
            goldPricePerGram = 70.0,
            silverPricePerGram = 0.8
        )
        assertEquals(500.0, z.netZakatableWealth, 0.001)
    }

    @Test
    fun nisabUsesAuthenticWeights85gGoldAnd595gSilver() {
        // Prices arrive from the app's NisabRates in one currency: gold-api.com quotes USD
        // per troy ounce, so the repository divides by 31.1034768 before passing them here.
        val goldPerGram = 3650.0 / 31.1034768
        val silverPerGram = 45.0 / 31.1034768
        val goldStandard = ZakatCalculation(
            cashOnHand = 0.0,
            goldGrams = 0.0,
            silverGrams = 0.0,
            tradeGoods = 0.0,
            investmentEquities = 0.0,
            deductibleDebts = 0.0,
            goldPricePerGram = goldPerGram,
            silverPricePerGram = silverPerGram
        )
        val silverStandard = goldStandard.copy(useSilverStandard = true)

        assertEquals(goldPerGram * 85.0, goldStandard.nisabThreshold, 0.001)
        assertEquals(silverPerGram * 595.0, silverStandard.nisabThreshold, 0.001)
    }

    @Test
    fun sameEligibilityAfterCurrencyConversion() {
        // ZakatCalculation is currency-agnostic by design: currencyCode/sourceLabel live on
        // the app-side NisabRates. Callers must feed prices already quoted in one currency;
        // converting every amount by the same public FX factor preserves the ruling.
        val usdToQar = 3.64
        val usd = ZakatCalculation(
            cashOnHand = 10000.0,
            goldGrams = 0.0,
            silverGrams = 0.0,
            tradeGoods = 0.0,
            investmentEquities = 0.0,
            deductibleDebts = 0.0,
            goldPricePerGram = 70.0,
            silverPricePerGram = 0.8
        )
        val qar = ZakatCalculation(
            cashOnHand = usd.cashOnHand * usdToQar,
            goldGrams = 0.0,
            silverGrams = 0.0,
            tradeGoods = 0.0,
            investmentEquities = 0.0,
            deductibleDebts = 0.0,
            goldPricePerGram = usd.goldPricePerGram * usdToQar,
            silverPricePerGram = usd.silverPricePerGram * usdToQar
        )

        assertEquals(usd.isZakatDue, qar.isZakatDue)
        assertEquals(usd.zakatPayable * usdToQar, qar.zakatPayable, 0.001)
    }
}
