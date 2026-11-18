package com.sakinah.deen.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.data.model.HadithCollectionCover

/**
 * Draws a vertical 3:4 hardcover book cover with a spine crease, gold-foil frame,
 * and the collection's Arabic title. No external images are used, so it renders offline.
 */
@Composable
fun HadithBookCover(
    cover: HadithCollectionCover,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(cover.coverColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Subtle leather-like vertical gradient.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(cover.spineColor, cover.coverColor, cover.spineColor.copy(alpha = 0.85f))
                )
            )
            // Spine crease on the start edge.
            drawRect(
                color = Color.Black.copy(alpha = 0.28f),
                topLeft = Offset(w * 0.10f, 0f),
                size = androidx.compose.ui.geometry.Size(w * 0.035f, h)
            )
            drawLine(
                color = cover.foilColor.copy(alpha = 0.35f),
                start = Offset(w * 0.135f, 0f),
                end = Offset(w * 0.135f, h),
                strokeWidth = 1.2f
            )
            // Gold-foil geometric border frame.
            val inset = w * 0.17f
            drawRect(
                color = cover.foilColor.copy(alpha = 0.85f),
                topLeft = Offset(inset, h * 0.055f),
                size = androidx.compose.ui.geometry.Size(w - inset * 1.45f, h * 0.89f),
                style = Stroke(width = 1.4f)
            )
            drawRect(
                color = cover.foilColor.copy(alpha = 0.45f),
                topLeft = Offset(inset + 3f, h * 0.055f + 3f),
                size = androidx.compose.ui.geometry.Size(w - inset * 1.45f - 6f, h * 0.89f - 6f),
                style = Stroke(width = 0.8f)
            )
            // Small arabesque corner ticks.
            val tick = w * 0.10f
            listOf(
                Offset(inset, h * 0.055f),
                Offset(w - inset * 1.45f, h * 0.055f),
                Offset(inset, h * 0.945f),
                Offset(w - inset * 1.45f, h * 0.945f)
            ).forEach { corner ->
                drawLine(
                    color = cover.foilColor,
                    start = corner,
                    end = Offset(corner.x + tick, corner.y),
                    strokeWidth = 1.2f
                )
                drawLine(
                    color = cover.foilColor,
                    start = corner,
                    end = Offset(corner.x, corner.y + tick),
                    strokeWidth = 1.2f
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = cover.arabicTitle,
                color = cover.foilColor,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3
            )
            Text(
                text = "✦",
                color = cover.foilColor.copy(alpha = 0.9f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
