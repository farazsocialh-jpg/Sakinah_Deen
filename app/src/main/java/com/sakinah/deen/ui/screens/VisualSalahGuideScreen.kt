package com.sakinah.deen.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.data.model.PostureStepModel
import com.sakinah.deen.ui.components.ArabicVerseBlock
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.components.StatusBadge
import com.sakinah.deen.ui.theme.AmiriFontFamily

@Composable
fun VisualSalahGuideScreen(
    onBack: () -> Unit,
    totalRakats: Int = 2,
    prayerTitle: String = "Prayer",
    modifier: Modifier = Modifier
) {
    val safeTotalRakats = totalRakats.coerceIn(1, 4)
    val steps = remember {
        listOf(
            PostureStepModel(
                id = 1,
                name = "Takbirat al-Ihram",
                arabicTitle = "تكبيرة الإحرام",
                postureKey = "takbir",
                postureAngleText = "Standing erect, 0° torso tilt, eyes focused on Sujud spot",
                footPositioning = "Feet shoulder-width apart (~4 fingers to 1 handspan), parallel toward Qibla",
                handPlacement = "Raise both hands up to earlobes, palms open facing Qibla",
                arabicRecitation = "اللَّهُ أَكْبَرُ",
                phoneticTransliteration = "Allāhu Akbar",
                englishTranslation = "Allah is the Greatest",
                sunnahNotes = "Recite at the exact moment of lifting the hands. Enter sacred consecrated state."
            ),
            PostureStepModel(
                id = 2,
                name = "Qiyam & Surah Al-Fatihah",
                arabicTitle = "القيام وقراءة الفاتحة",
                postureKey = "qiyam",
                postureAngleText = "Upright vertical posture, back straight, eyes pinned to prostration ground",
                footPositioning = "Weight balanced evenly on both feet, motionless",
                handPlacement = "Right hand grasping left wrist resting on chest / below navel",
                arabicRecitation = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ۝ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
                phoneticTransliteration = "Bismillāhir-Raḥmānir-Raḥīm. Al-ḥamdu lillāhi Rabbil-‘ālamīn.",
                englishTranslation = "In the name of Allah, Most Gracious. All praise is for Allah—Lord of all worlds.",
                sunnahNotes = "Recite Thana first, then Ta'awwudh, Basmalah, and Al-Fatihah."
            ),
            PostureStepModel(
                id = 3,
                name = "Ruku‘ (The Bowing)",
                arabicTitle = "الركوع",
                postureKey = "ruku",
                postureAngleText = "Torso hinged precisely 90° horizontal to floor; head aligned with spine",
                footPositioning = "Legs straight with slight knee flex if needed, feet anchored",
                handPlacement = "Firmly cup kneecaps with fingers spread out wide like gripping an arch",
                arabicRecitation = "سُبْحَانَ رَبِّيَ الْعَظِيمِ",
                phoneticTransliteration = "Subḥāna Rabbiyal-‘Aẓīm (3 times)",
                englishTranslation = "Glory be to my Lord, the Most Magnificent",
                sunnahNotes = "Stay stationary in Ruku until bones settle (Tum'aninah)."
            ),
            PostureStepModel(
                id = 4,
                name = "Sujud (Prostration)",
                arabicTitle = "السجود",
                postureKey = "sujud",
                postureAngleText = "7 bodily points touching floor: forehead + nose, palms, knees, toes",
                footPositioning = "Toes bent forward gripping floor toward Qibla",
                handPlacement = "Palms flat on mat level with shoulders or ears, elbows lifted off floor",
                arabicRecitation = "سُبْحَانَ رَبِّيَ الْأَعْلَى",
                phoneticTransliteration = "Subḥāna Rabbiyal-A‘lā (3 times)",
                englishTranslation = "Glory be to my Lord, the Most High",
                sunnahNotes = "The closest a servant comes to Allah is in Sujud."
            ),
            PostureStepModel(
                id = 5,
                name = "Tashahhud",
                arabicTitle = "التشهد",
                postureKey = "tashahhud",
                postureAngleText = "Seated calmly. Right foot upright with toes bent; left foot folded underneath",
                footPositioning = "Iftirash sitting",
                handPlacement = "Right index finger pointing toward Qibla during testimony of faith",
                arabicRecitation = "التَّحِيَّاتُ لِلَّهِ وَالصَّلَوَاتُ وَالطَّيِّبَاتُ",
                phoneticTransliteration = "At-taḥiyyātu lillāhi waṣ-ṣalawātu waṭ-ṭayyibāt.",
                englishTranslation = "All greetings, prayers, and pure things belong to Allah.",
                sunnahNotes = "Raise index finger when declaring pure monotheism."
            )
        )
    }

    var currentRakat by remember(safeTotalRakats, prayerTitle) { mutableIntStateOf(1) }
    var currentStepIndex by remember(safeTotalRakats, prayerTitle) { mutableIntStateOf(0) }
    val step = steps[currentStepIndex]
    var loopEnabled by remember { mutableStateOf(false) }
    var isAudioPlaying by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            mediaPlayer = null
        }
    }

    fun rakatHasTashahhud(rakat: Int): Boolean =
        rakat == safeTotalRakats || (safeTotalRakats >= 3 && rakat == 2)

    val lastStepForCurrentRakat = if (rakatHasTashahhud(currentRakat)) 4 else 3
    val firstStepForCurrentRakat = if (currentRakat == 1) 0 else 1
    val isFinalAction = currentRakat == safeTotalRakats && currentStepIndex == lastStepForCurrentRakat

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "$prayerTitle · Rakat $currentRakat of $safeTotalRakats · Step ${step.id} of ${steps.size}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            StatusBadge(
                text = "Qibla Focus",
                accentColor = MaterialTheme.colorScheme.primary
            )
        }

        // 1. Step Indicator Carousel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            steps.forEachIndexed { index, s ->
                val isCurrent = index == currentStepIndex
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clickable { currentStepIndex = index }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "${s.id}",
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 2. Posture Graphic Viewport
        val strokePrimary = MaterialTheme.colorScheme.primary
        val strokeSecondary = MaterialTheme.colorScheme.secondary
        val torsoColor = MaterialTheme.colorScheme.onSurface
        val matNeutral = MaterialTheme.colorScheme.outline

        SakinahCard(contentPadding = 16.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = step.name,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = step.postureAngleText.split(",")[0],
                        fontSize = 11.sp,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val centerX = canvasWidth / 2f
                    val groundY = canvasHeight - 20f

                    // Prayer mat ground line
                    drawLine(
                        color = matNeutral,
                        start = Offset(centerX - 110f, groundY),
                        end = Offset(centerX + 110f, groundY),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = strokeSecondary,
                        start = Offset(centerX - 65f, groundY),
                        end = Offset(centerX + 65f, groundY),
                        strokeWidth = 8f,
                        cap = StrokeCap.Round
                    )

                    when (step.postureKey) {
                        "takbir" -> {
                            drawLine(strokePrimary, Offset(centerX - 12f, groundY), Offset(centerX - 10f, groundY - 60f), 12f, StrokeCap.Round)
                            drawLine(strokePrimary, Offset(centerX + 12f, groundY), Offset(centerX + 10f, groundY - 60f), 12f, StrokeCap.Round)
                            drawRect(torsoColor, Offset(centerX - 18f, groundY - 110f), Size(36f, 50f))
                            drawCircle(strokeSecondary, radius = 16f, center = Offset(centerX, groundY - 125f))
                            drawLine(strokePrimary, Offset(centerX - 18f, groundY - 100f), Offset(centerX - 35f, groundY - 130f), 8f, StrokeCap.Round)
                            drawLine(strokePrimary, Offset(centerX + 18f, groundY - 100f), Offset(centerX + 35f, groundY - 130f), 8f, StrokeCap.Round)
                        }
                        "ruku" -> {
                            drawLine(strokePrimary, Offset(centerX + 30f, groundY), Offset(centerX + 25f, groundY - 70f), 12f, StrokeCap.Round)
                            drawLine(torsoColor, Offset(centerX + 25f, groundY - 70f), Offset(centerX - 40f, groundY - 70f), 16f, StrokeCap.Round)
                            drawCircle(strokeSecondary, radius = 16f, center = Offset(centerX - 56f, groundY - 70f))
                            drawLine(strokePrimary, Offset(centerX - 25f, groundY - 70f), Offset(centerX + 20f, groundY - 35f), 8f, StrokeCap.Round)
                        }
                        "sujud" -> {
                            drawLine(strokePrimary, Offset(centerX + 52f, groundY), Offset(centerX + 12f, groundY), 10f, StrokeCap.Round)
                            drawLine(strokePrimary, Offset(centerX + 12f, groundY), Offset(centerX - 10f, groundY - 35f), 12f, StrokeCap.Round)
                            drawLine(torsoColor, Offset(centerX - 10f, groundY - 35f), Offset(centerX - 45f, groundY - 10f), 14f, StrokeCap.Round)
                            drawCircle(strokeSecondary, radius = 14f, center = Offset(centerX - 60f, groundY - 10f))
                        }
                        "tashahhud" -> {
                            // Seated Iftirash figure facing Qibla (left)
                            drawLine(strokePrimary, Offset(centerX + 42f, groundY), Offset(centerX - 28f, groundY), 12f, StrokeCap.Round)
                            drawLine(strokePrimary, Offset(centerX + 42f, groundY), Offset(centerX + 42f, groundY - 26f), 10f, StrokeCap.Round)
                            // Upright seated torso
                            drawLine(torsoColor, Offset(centerX + 18f, groundY - 4f), Offset(centerX + 18f, groundY - 68f), 24f, StrokeCap.Round)
                            // Head
                            drawCircle(strokeSecondary, radius = 15f, center = Offset(centerX + 16f, groundY - 88f))
                            // Arm resting on thigh with pointed right index finger
                            drawLine(strokePrimary, Offset(centerX + 14f, groundY - 52f), Offset(centerX - 14f, groundY - 26f), 8f, StrokeCap.Round)
                            drawLine(strokeSecondary, Offset(centerX - 14f, groundY - 26f), Offset(centerX - 32f, groundY - 34f), 6f, StrokeCap.Round)
                        }
                        else -> {
                            drawLine(strokePrimary, Offset(centerX - 12f, groundY), Offset(centerX - 10f, groundY - 60f), 12f, StrokeCap.Round)
                            drawLine(strokePrimary, Offset(centerX + 12f, groundY), Offset(centerX + 10f, groundY - 60f), 12f, StrokeCap.Round)
                            drawRect(torsoColor, Offset(centerX - 18f, groundY - 110f), Size(36f, 50f))
                            drawCircle(strokeSecondary, radius = 16f, center = Offset(centerX, groundY - 125f))
                            drawLine(strokePrimary, Offset(centerX - 18f, groundY - 95f), Offset(centerX + 18f, groundY - 95f), 10f, StrokeCap.Round)
                        }
                    }
                }

                Text(
                    text = "Placement: ${step.handPlacement}. ${step.footPositioning}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 3. Recitation & Meaning Card
        SakinahCard(contentPadding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = step.arabicTitle,
                    fontFamily = AmiriFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ArabicVerseBlock(
                        arabicText = step.arabicRecitation,
                        fontSizeSp = 24,
                        color = MaterialTheme.colorScheme.onSurface,
                        isQuran = false,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Text(
                    text = "Phonetic: \"${step.phoneticTransliteration}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Meaning: \"${step.englishTranslation}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = step.sunnahNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (isAudioPlaying) {
                                runCatching {
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                }
                                mediaPlayer = null
                                isAudioPlaying = false
                            } else {
                                val resId = context.resources.getIdentifier("adhan_fajr", "raw", context.packageName)
                                if (resId != 0) {
                                    runCatching {
                                        mediaPlayer?.release()
                                        mediaPlayer = MediaPlayer.create(context, resId)?.apply {
                                            isLooping = loopEnabled
                                            setOnCompletionListener { isAudioPlaying = false }
                                            start()
                                        }
                                        isAudioPlaying = mediaPlayer != null
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isAudioPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isAudioPlaying) "Stop Audio" else "Pronounce Audio")
                    }
                    IconButton(onClick = {
                        loopEnabled = !loopEnabled
                        mediaPlayer?.isLooping = loopEnabled
                    }) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Loop",
                            tint = if (loopEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 4. Navigation Footer across all Rakats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val canGoBack = currentRakat > 1 || currentStepIndex > 0
            OutlinedButton(
                onClick = {
                    if (currentStepIndex > firstStepForCurrentRakat) {
                        currentStepIndex--
                    } else if (currentRakat > 1) {
                        currentRakat--
                        currentStepIndex = if (rakatHasTashahhud(currentRakat)) 4 else 3
                    }
                },
                enabled = canGoBack,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = null)
                Text("Previous")
            }

            Button(
                onClick = {
                    if (currentStepIndex < lastStepForCurrentRakat) {
                        currentStepIndex++
                    } else if (currentRakat < safeTotalRakats) {
                        currentRakat++
                        currentStepIndex = 1 // Subsequent rakats begin with Qiyam & Al-Fatihah
                    } else {
                        onBack()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = if (isFinalAction) "Complete" else "Next Step",
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
