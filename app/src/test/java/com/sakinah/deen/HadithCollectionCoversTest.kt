package com.sakinah.deen

import com.sakinah.deen.data.model.HadithCollectionCovers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HadithCollectionCoversTest {

    private val supportedKeys = listOf(
        "bukhari",
        "muslim",
        "abudawud",
        "tirmidhi",
        "nasai",
        "ibnmajah",
        "malik",
        "nawawi"
    )

    @Test
    fun everySupportedCollectionMapsToACompleteCover() {
        supportedKeys.forEach { key ->
            val cover = HadithCollectionCovers.forBook(key)
            assertEquals(key, cover.bookKey)
            assertTrue("English title missing for $key", cover.englishTitle.isNotBlank())
            assertTrue("Arabic title missing for $key", cover.arabicTitle.isNotBlank())
            assertTrue("Compiler missing for $key", cover.compiler.isNotBlank())
            assertEquals("Cover color must be opaque for $key", 1f, cover.coverColor.alpha)
            assertEquals("Spine color must be opaque for $key", 1f, cover.spineColor.alpha)
            assertNotEquals(
                "Cover and spine should differ for $key",
                cover.coverColor.value,
                cover.spineColor.value
            )
        }
    }

    @Test
    fun eachCollectionUsesADistinctCoverPalette() {
        val primaryColors = supportedKeys.map { HadithCollectionCovers.forBook(it).coverColor.value }
        assertEquals(primaryColors.size, primaryColors.distinct().size)
    }
}
