package com.sakinah.deen

import com.sakinah.deen.util.ArabicNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicNormalizerTest {

    @Test
    fun normalize_stripsTashkeelAndUnifiesAlefVariants() {
        val vocalized = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"
        val plain = "بسم الله الرحمن الرحيم"
        assertEquals(ArabicNormalizer.normalize(plain), ArabicNormalizer.normalize(vocalized))
        assertTrue(ArabicNormalizer.matches(vocalized, "الرحمن"))
        assertTrue(ArabicNormalizer.matches("إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ", "انما الاعمال بالنيات"))
    }

    @Test
    fun normalize_unifiesTaaMarbutaAndAlifMaqsura() {
        assertTrue(ArabicNormalizer.matches("صَلَاةُ الْفَجْرِ", "صلاه الفجر"))
        assertTrue(ArabicNormalizer.matches("عَلَى الصِّرَاطِ", "علي الصراط"))
        assertFalse(ArabicNormalizer.matches("الحمد لله", "صلاة"))
    }

    @Test
    fun toArabicIndicNumerals_formatsPositiveAndZeroIntegers() {
        assertEquals("١", ArabicNormalizer.toArabicIndicNumerals(1))
        assertEquals("١٢", ArabicNormalizer.toArabicIndicNumerals(12))
        assertEquals("٢٨٦", ArabicNormalizer.toArabicIndicNumerals(286))
        assertEquals("٠", ArabicNormalizer.toArabicIndicNumerals(0))
    }
}
