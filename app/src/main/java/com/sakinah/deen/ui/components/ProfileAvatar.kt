package com.sakinah.deen.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Circular profile picture with an initials fallback.
 *
 * The Google avatar URL is fetched with a plain HTTP request on the IO dispatcher, so the app
 * needs no image-loading dependency; any failure (offline, no network permission, bad URL)
 * simply leaves the initials avatar in place.
 */
@Composable
fun ProfileAvatar(
    photoUrl: String?,
    displayName: String,
    size: Dp = 72.dp,
    modifier: Modifier = Modifier
) {
    val image by produceState<ImageBitmap?>(initialValue = null, photoUrl) {
        val url = photoUrl?.takeIf { it.isNotBlank() } ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    instanceFollowRedirects = true
                }
                try {
                    connection.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
    }

    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initialsOf(displayName),
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value / 3).sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/** Up to two initials from a display name, or a person glyph stand-in when it is blank. */
internal fun initialsOf(displayName: String): String {
    val words = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return "?"
    val first = words.first().first().uppercaseChar()
    val second = words.getOrNull(1)?.first()?.uppercaseChar()
    return if (second == null) first.toString() else "$first$second"
}
