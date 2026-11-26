package com.sakinah.deen

import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.ceil

/**
 * Real glyph-shaping/rasterization check for the Uthmani reading face.
 *
 * Robolectric's NATIVE runtime is unavailable on linux-aarch64 CI hosts, so this uses
 * the JDK's FreeType-backed AWT TextLayout. It renders the actual reader phrase and a
 * control containing explicit U+25CC dotted circles; if the font auto-inserts dotted
 * circles for U+06DF the two renders are nearly identical. Amiri Quran shapes the mark
 * onto its base, so the control visibly adds two circles and much more ink.
 */
class UthmaniFontRenderingTest {

    companion object {
        private const val CLEAN = "أُو۟لَٰٓئِكَ وَءَامِنُوا۟"
        private const val WITH_EXPLICIT_DOTTED_CIRCLES = "أُو◌۟لَٰٓئِكَ وَءَامِنُو◌ا۟"

        @JvmStatic
        @BeforeClass
        fun headless() {
            System.setProperty("java.awt.headless", "true")
        }
    }

    @Test
    fun uthmaniMarksShapeWithoutAutoInsertedDottedCircles() {
        val font = quranFont()
        val context = FontRenderContext(null, true, true)

        val clean = TextLayout(CLEAN, font, context)
        val control = TextLayout(WITH_EXPLICIT_DOTTED_CIRCLES, font, context)

        val cleanInk = inkPixels(clean.bounds) { graphics -> clean.draw(graphics, 0f, 0f) }
        val controlInk = inkPixels(control.bounds) { graphics -> control.draw(graphics, 0f, 0f) }

        assertTrue(
            "Control has $controlInk ink vs clean $cleanInk; font appears to insert U+25CC",
            controlInk - cleanInk > 40
        )
        assertTrue(
            "Explicit dotted circles should widen the run",
            control.bounds.width - clean.bounds.width > 30.0
        )
    }

    @Test
    fun u06dfGlyphRendersAsASmallMark() {
        val font = quranFont()
        val context = FontRenderContext(null, true, true)
        val glyph = font.createGlyphVector(context, "۟")
        val markInk = inkPixels(glyph.visualBounds) { graphics ->
            graphics.drawGlyphVector(glyph, 0f, 0f)
        }

        // The KFGQPC v18 dotted-circle composite produced ~10x this ink at 48px.
        assertTrue("U+06DF rendered $markInk pixels; expected a compact mark", markInk in 1..120)
    }

    private fun quranFont(): Font {
        val file = listOf(
            File("src/main/res/font/amiri_quran_regular.ttf"),
            File("app/src/main/res/font/amiri_quran_regular.ttf")
        ).firstOrNull(File::isFile)
            ?: error("Missing bundled Amiri Quran font")
        return Font.createFont(Font.TRUETYPE_FONT, file).deriveFont(48f)
    }

    private fun inkPixels(bounds: Rectangle2D, draw: (java.awt.Graphics2D) -> Unit): Int {
        val margin = 16
        val width = ceil(bounds.width).toInt() + margin * 2
        val height = ceil(bounds.height).toInt() + margin * 2
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, width, height)
        graphics.color = Color.BLACK
        graphics.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        )
        graphics.translate(margin - bounds.minX.toInt(), margin - bounds.minY.toInt())
        draw(graphics)
        graphics.dispose()

        var ink = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((image.getRGB(x, y) and 0xFFFFFF) < 0x999999) ink++
            }
        }
        return ink
    }
}
