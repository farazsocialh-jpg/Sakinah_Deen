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
}
