package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.CompassCalibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.data.model.PostureStepModel
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.EmeraldSurface
import com.sakinah.deen.ui.theme.MintGreen

@Composable
fun VisualSalahGuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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

    var currentStepIndex by remember { mutableIntStateOf(0) }
    val step = steps[currentStepIndex]
    var loopEnabled by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Rakat 1 of 2 · Step ${step.id} of ${steps.size}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = EmeraldDeep
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MintGreen.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "Qibla Focus",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    color = EmeraldDeep,
                    fontWeight = FontWeight.Bold
                )
            }
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
                    color = if (isCurrent) EmeraldDeep else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isCurrent) EmeraldDeep else MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = "${s.id}",
                            color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 2. Posture Graphic Viewport (Pure Compose Canvas Drawing)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Orientation: Facing Qibla with calm focus",
                        fontSize = 11.sp,
                        color = EmeraldDeep,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = step.postureAngleText.split(",")[0],
                        fontSize = 11.sp,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Pure Vector Canvas Drawing of the Islamic Posture Figure
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
                        color = Color.LightGray,
                        start = Offset(centerX - 100f, groundY),
                        end = Offset(centerX + 100f, groundY),
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFF10B981),
                        start = Offset(centerX - 60f, groundY),
                        end = Offset(centerX + 60f, groundY),
                        strokeWidth = 8f,
                        cap = StrokeCap.Round
                    )

                    when (step.postureKey) {
                        "takbir" -> {
                            // Upright standing with hands raised by ears
                            drawLine(Color(0xFF0B3B2C), Offset(centerX - 12f, groundY), Offset(centerX - 10f, groundY - 60f), 12f, StrokeCap.Round)
                            drawLine(Color(0xFF0B3B2C), Offset(centerX + 12f, groundY), Offset(centerX + 10f, groundY - 60f), 12f, StrokeCap.Round)
                            drawRect(Color(0xFF134E3A), Offset(centerX - 18f, groundY - 110f), Size(36f, 50f))
                            drawCircle(Color(0xFF10B981), radius = 16f, center = Offset(centerX, groundY - 125f))
                            // Arms raised
                            drawLine(Color(0xFF0B3B2C), Offset(centerX - 18f, groundY - 100f), Offset(centerX - 35f, groundY - 130f), 8f, StrokeCap.Round)
                            drawLine(Color(0xFF0B3B2C), Offset(centerX + 18f, groundY - 100f), Offset(centerX + 35f, groundY - 130f), 8f, StrokeCap.Round)
                        }
                        "ruku" -> {
                            // 90 degree horizontal back
                            drawLine(Color(0xFF0B3B2C), Offset(centerX + 30f, groundY), Offset(centerX + 25f, groundY - 70f), 12f, StrokeCap.Round)
                            drawLine(Color(0xFF134E3A), Offset(centerX + 25f, groundY - 70f), Offset(centerX - 40f, groundY - 70f), 16f, StrokeCap.Round)
                            drawCircle(Color(0xFF10B981), radius = 16f, center = Offset(centerX - 56f, groundY - 70f))
                            // Arm bracing knee
                            drawLine(Color(0xFF0B3B2C), Offset(centerX - 25f, groundY - 70f), Offset(centerX + 20f, groundY - 35f), 8f, StrokeCap.Round)
                        }
                        "sujud" -> {
                            // 7 points prostration on mat
                            drawLine(Color(0xFF0B3B2C), Offset(centerX + 50f, groundY), Offset(centerX + 10f, groundY), 10f, StrokeCap.Round)
                            drawLine(Color(0xFF0B3B2C), Offset(centerX + 10f, groundY), Offset(centerX - 10f, groundY - 35f), 12f, StrokeCap.Round)
                            drawLine(Color(0xFF134E3A), Offset(centerX - 10f, groundY - 35f), Offset(centerX - 45f, groundY - 10f), 14f, StrokeCap.Round)
                            drawCircle(Color(0xFF10B981), radius = 14f, center = Offset(centerX - 60f, groundY - 10f))
                        }
                        else -> {
                            // Standing upright (Qiyam)
                            drawLine(Color(0xFF0B3B2C), Offset(centerX - 12f, groundY), Offset(centerX - 10f, groundY - 60f), 12f, StrokeCap.Round)
                            drawLine(Color(0xFF0B3B2C), Offset(centerX + 12f, groundY), Offset(centerX + 10f, groundY - 60f), 12f, StrokeCap.Round)
                            drawRect(Color(0xFF134E3A), Offset(centerX - 18f, groundY - 110f), Size(36f, 50f))
                            drawCircle(Color(0xFF10B981), radius = 16f, center = Offset(centerX, groundY - 125f))
                            // Arms folded
                            drawLine(Color(0xFF0B3B2C), Offset(centerX - 18f, groundY - 95f), Offset(centerX + 18f, groundY - 95f), 10f, StrokeCap.Round)
                        }
                    }
                }

                Text(
                    text = "Placement: ${step.handPlacement}. ${step.footPositioning}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        // 3. Recitation & Meaning Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = step.arabicTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = EmeraldDeep
                )

                // Large Arabic Text
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MintGreen.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = step.arabicRecitation,
                        fontFamily = FontFamily.Serif,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldDeep,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Text(
                    text = "Phonetic: \"${step.phoneticTransliteration}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = EmeraldDeep
                )
                Text(
                    text = "Meaning: \"${step.englishTranslation}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )

                // Audio Trigger Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(modifier = Modifier.weight(1f), onClick = {
                        val resId = context.resources.getIdentifier("adhan_fajr", "raw", context.packageName)
                        if (resId != 0) {
                            val mp = android.media.MediaPlayer.create(context, resId)
                            mp?.isLooping = loopEnabled
                            mp?.start()
                        }
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pronounce Audio")
                    }
                    IconButton(onClick = { loopEnabled = !loopEnabled }) {
                        Icon(Icons.Default.Repeat, contentDescription = "Loop", tint = if (loopEnabled) EmeraldDeep else Color.Gray)
                    }
                }
            }
        }

        // 4. Navigation Footer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { if (currentStepIndex > 0) currentStepIndex-- },
                enabled = currentStepIndex > 0,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = null)
                Text("Previous")
            }

            Button(
                onClick = {
                    if (currentStepIndex < steps.size - 1) {
                        currentStepIndex++
                    } else {
                        onBack()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDeep)
            ) {
                Text(if (currentStepIndex == steps.size - 1) "Complete" else "Next Step")
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
    }
}
