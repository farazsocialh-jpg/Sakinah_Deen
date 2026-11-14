package com.sakinah.deen.ui.screens

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.R
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.ui.components.ArabicVerseBlock
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.ui.theme.AmiriFontFamily
import com.sakinah.deen.ui.theme.PrayerGuideGender
import java.util.Locale

private enum class GuideTab(val label: String) {
    WUDU("Wudu"),
    SALAH("Salah"),
    AFTER_SALAH("After Salah")
}

private enum class GuidePosture {
    TAKBIR,
    QIYAM,
    RUKU,
    QAWMAH,
    SUJUD,
    TASHAHHUD,
    SALAM
}

private data class GuideStep(
    val title: String,
    val arabicTitle: String = "",
    val arabicText: String = "",
    val transliteration: String = "",
    val translation: String = "",
    val source: String,
    val notes: String,
    val posture: GuidePosture? = null
)

@Composable
fun VisualSalahGuideScreen(
    onBack: () -> Unit,
    totalRakats: Int = 2,
    prayerTitle: String = "Prayer",
    modifier: Modifier = Modifier
) {
    val appearancePreferences = rememberInject<AppearancePreferences>()
    val appearanceConfig by appearancePreferences.effectiveConfig.collectAsStateWithLifecycle()
    val gender = appearanceConfig.prayerGuideGender
    val safeTotalRakats = totalRakats.coerceIn(1, 4)

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = GuideTab.entries[tabIndex]
    var currentRakat by rememberSaveable(prayerTitle, safeTotalRakats) { mutableIntStateOf(1) }
    var currentStepIndex by rememberSaveable { mutableIntStateOf(0) }
    var tasbihCount by rememberSaveable { mutableIntStateOf(0) }

    val steps = when (tab) {
        GuideTab.WUDU -> wuduSteps
        GuideTab.SALAH -> buildSalahSteps(currentRakat, safeTotalRakats)
        GuideTab.AFTER_SALAH -> afterSalahSteps
    }
    val safeStepIndex = currentStepIndex.coerceIn(0, steps.lastIndex)
    val step = steps[safeStepIndex]
    val isLastStepInSequence = tab == GuideTab.SALAH &&
        currentRakat == safeTotalRakats && safeStepIndex == steps.lastIndex
    val primaryActionLabel = when {
        isLastStepInSequence -> "Complete"
        tab == GuideTab.WUDU && safeStepIndex == steps.lastIndex -> "Go to Salah"
        tab == GuideTab.AFTER_SALAH && safeStepIndex == steps.lastIndex -> "Complete"
        else -> "Next Step"
    }

    val context = LocalContext.current
    var ttsReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    LaunchedEffect(ttsReady) {
        if (ttsReady) {
            tts.language = Locale("ar", "SA")
            tts.setSpeechRate(0.85f)
        }
    }

    DisposableEffect(tts) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
            }
        })
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    fun speakArabic(text: String) {
        if (text.isBlank() || !ttsReady) return
        if (isSpeaking) {
            tts.stop()
            isSpeaking = false
        } else {
            tts.stop()
            isSpeaking = true
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "salah-guide-${step.hashCode()}")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Visual Salah Guide",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (tab == GuideTab.SALAH) {
                        "$prayerTitle · Rakat $currentRakat of $safeTotalRakats · Step ${safeStepIndex + 1} of ${steps.size}"
                    } else {
                        "${tab.label} · Step ${safeStepIndex + 1} of ${steps.size}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SegmentedPillBar(
            options = GuideTab.entries,
            selected = tab,
            labelProvider = { it.label },
            onSelect = { selected ->
                tabIndex = selected.ordinal
                currentStepIndex = 0
            }
        )

        SegmentedPillBar(
            options = PrayerGuideGender.entries,
            selected = gender,
            labelProvider = { it.label },
            onSelect = { selected ->
                appearancePreferences.update { it.copy(prayerGuideGender = selected) }
            }
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val landscape = maxWidth > maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (tab == GuideTab.SALAH && step.posture != null) {
                    SakinahCard(contentPadding = 12.dp) {
                        Image(
                            painter = painterResource(postureDrawable(step.posture, gender)),
                            contentDescription = "${step.title} posture for ${gender.label}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (landscape) 150.dp else 210.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                        )
                    }
                }

                if (tab == GuideTab.AFTER_SALAH && step.title == "Tasbih") {
                    TasbihCounter(
                        count = tasbihCount,
                        onIncrement = { tasbihCount = (tasbihCount + 1).coerceAtMost(100) },
                        onReset = { tasbihCount = 0 }
                    )
                }

                GuideStepCard(
                    step = step,
                    isSpeaking = isSpeaking,
                    canPronounce = step.arabicText.isNotBlank(),
                    onPronounce = { speakArabic(step.arabicText) }
                )

                if (tab == GuideTab.WUDU && safeStepIndex == steps.lastIndex) {
                    Text(
                        text = "Next, open the Salah tab to begin the prayer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val canGoBack = tab != GuideTab.WUDU && tab != GuideTab.AFTER_SALAH &&
                (currentRakat > 1 || safeStepIndex > 0) ||
                (tab == GuideTab.WUDU || tab == GuideTab.AFTER_SALAH) && safeStepIndex > 0

            OutlinedButton(
                onClick = {
                    if (safeStepIndex > 0) {
                        currentStepIndex = safeStepIndex - 1
                    } else if (tab == GuideTab.SALAH && currentRakat > 1) {
                        currentRakat--
                        currentStepIndex = buildSalahSteps(currentRakat, safeTotalRakats).lastIndex
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
                    when (tab) {
                        GuideTab.WUDU -> {
                            if (safeStepIndex < steps.lastIndex) currentStepIndex = safeStepIndex + 1
                            else {
                                tabIndex = GuideTab.SALAH.ordinal
                                currentStepIndex = 0
                            }
                        }

                        GuideTab.SALAH -> {
                            if (safeStepIndex < steps.lastIndex) {
                                currentStepIndex = safeStepIndex + 1
                            } else if (currentRakat < safeTotalRakats) {
                                currentRakat++
                                currentStepIndex = 0
                            } else {
                                onBack()
                            }
                        }

                        GuideTab.AFTER_SALAH -> {
                            if (safeStepIndex < steps.lastIndex) currentStepIndex = safeStepIndex + 1
                            else onBack()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = primaryActionLabel,
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

@Composable
private fun GuideStepCard(
    step: GuideStep,
    isSpeaking: Boolean,
    canPronounce: Boolean,
    onPronounce: () -> Unit
) {
    SakinahCard(contentPadding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (step.arabicTitle.isNotBlank()) {
                Text(
                    text = step.arabicTitle,
                    fontFamily = AmiriFontFamily,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (step.arabicText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ArabicVerseBlock(
                        arabicText = step.arabicText,
                        fontSizeSp = 23,
                        color = MaterialTheme.colorScheme.onSurface,
                        isQuran = false,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                if (step.transliteration.isNotBlank()) {
                    Text(
                        text = "Pronunciation: ${step.transliteration}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (step.translation.isNotBlank()) {
                    Text(
                        text = "Meaning: ${step.translation}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = step.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Hadith: ${step.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }

            if (canPronounce) {
                FilledTonalButton(
                    onClick = onPronounce,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSpeaking) "Stop Pronunciation" else "Pronounce Arabic")
                }
            }
        }
    }
}

@Composable
private fun TasbihCounter(
    count: Int,
    onIncrement: () -> Unit,
    onReset: () -> Unit
) {
    val phase = when {
        count < 33 -> "SubhanAllah"
        count < 66 -> "Alhamdulillah"
        count < 99 -> "Allahu Akbar"
        else -> "Completion"
    }
    SakinahCard(contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tasbih Counter · $count / 100",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (count >= 100) {
                            "100th completion — La ilaha illallahu wahdahu la sharika lah..."
                        } else {
                            "Now: $phase"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onReset) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset counter")
                }
            }
            Button(onClick = onIncrement, enabled = count < 100, modifier = Modifier.fillMaxWidth()) {
                Text(if (count < 100) "Tap to Count +1" else "100 Complete")
            }
        }
    }
}

private fun postureDrawable(posture: GuidePosture, gender: PrayerGuideGender): Int = when (gender) {
    PrayerGuideGender.MALE -> when (posture) {
        GuidePosture.TAKBIR -> R.drawable.salah_male_1_takbir
        GuidePosture.QIYAM -> R.drawable.salah_male_2_qiyam
        GuidePosture.RUKU -> R.drawable.salah_male_3_ruku
        GuidePosture.QAWMAH -> R.drawable.salah_male_4_qawmah
        GuidePosture.SUJUD -> R.drawable.salah_male_5_sujud
        GuidePosture.TASHAHHUD -> R.drawable.salah_male_6_tashahhud
        GuidePosture.SALAM -> R.drawable.salah_male_7_salam
    }

    PrayerGuideGender.FEMALE -> when (posture) {
        GuidePosture.TAKBIR -> R.drawable.salah_female_1_takbir
        GuidePosture.QIYAM -> R.drawable.salah_female_2_qiyam
        GuidePosture.RUKU -> R.drawable.salah_female_3_ruku
        GuidePosture.QAWMAH -> R.drawable.salah_female_4_qawmah
        GuidePosture.SUJUD -> R.drawable.salah_female_5_sujud
        GuidePosture.TASHAHHUD -> R.drawable.salah_female_6_tashahhud
        GuidePosture.SALAM -> R.drawable.salah_female_7_salam
    }
}

private val wuduSteps = listOf(
    GuideStep(
        title = "1. Intention & Bismillah",
        arabicText = "بِسْمِ اللَّهِ",
        transliteration = "Bismillah",
        translation = "In the name of Allah.",
        source = "Sahih al-Bukhari 1; Sunan Abi Dawud 101",
        notes = "Make the Niyyah silently in the heart, then say Bismillah before beginning."
    ),
    GuideStep(
        title = "2. Wash Both Hands 3×",
        source = "Sunan Abi Dawud 142",
        notes = "Wash both hands up to the wrists three times. Interlace the fingers and clean between them."
    ),
    GuideStep(
        title = "3. Rinse Mouth & Nose 3×",
        source = "Sahih al-Bukhari 191",
        notes = "Take water with the right hand, rinse the mouth, sniff into the nose, then blow it out with the left hand. Repeat three times."
    ),
    GuideStep(
        title = "4. Wash the Entire Face 3×",
        source = "Sahih al-Bukhari 159",
        notes = "Wash the whole face from forehead to chin and ear to ear, three times."
    ),
    GuideStep(
        title = "5. Wash Both Arms 3×",
        source = "Sahih al-Bukhari 159",
        notes = "Wash the right arm from fingertips through the elbow three times, then the left arm in the same way."
    ),
    GuideStep(
        title = "6. Wipe Head & Ears Once",
        source = "Sahih al-Bukhari 185; Sunan Abi Dawud 135",
        notes = "Wipe the head once from front to back and back to front, then wipe the ears once with the index fingers inside and thumbs behind."
    ),
    GuideStep(
        title = "7. Wash Both Feet 3×",
        source = "Sahih al-Bukhari 159; Sunan Abi Dawud 148",
        notes = "Wash the right foot through the ankle three times, including between the toes, then the left foot."
    ),
    GuideStep(
        title = "8. Dua After Wudu",
        arabicTitle = "دعاء الوضوء",
        arabicText = "أَشْهَدُ أَنْ لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ وَأَشْهَدُ أَنَّ مُحَمَّدًا عَبْدُهُ وَرَسُولُهُ، اللَّهُمَّ اجْعَلْنِي مِنَ التَّوَّابِينَ وَاجْعَلْنِي مِنَ الْمُتَطَهِّرِينَ",
        transliteration = "Ash-hadu an la ilaha illallahu wahdahu la sharika lahu, wa ash-hadu anna Muhammadan 'abduhu wa rasuluh. Allahummaj'alni minat-tawwabina waj'alni minal-mutatahhirin.",
        translation = "I bear witness that none has the right to be worshipped except Allah alone, without any partner, and I bear witness that Muhammad is His slave and Messenger. O Allah, make me among those who turn to You in repentance, and make me among those who purify themselves.",
        source = "Sahih Muslim 234; Jami' at-Tirmidhi 55",
        notes = "Recite after completing the wudu."
    )
)

private fun buildSalahSteps(rakat: Int, totalRakats: Int): List<GuideStep> {
    val isFinalRakah = rakat == totalRakats
    val hasMiddleSitting = rakat == 2 && totalRakats >= 3 ||
        rakat == 3 && totalRakats == 4
    val hasTashahhud = isFinalRakah || hasMiddleSitting
    val steps = mutableListOf<GuideStep>()

    // Takbirat al-Ihram opens the prayer once. Rak'ah 2+ starts already standing in Qiyam.
    if (rakat == 1) {
        steps += GuideStep(
            title = "1. Takbirat al-Ihram",
            arabicTitle = "تكبيرة الإحرام",
            arabicText = "اللَّهُ أَكْبَرُ",
            transliteration = "Allahu Akbar",
            translation = "Allah is the Greatest.",
            source = "Sahih al-Bukhari 735; Sahih Muslim 498",
            notes = "Stand facing the Qiblah, make the Niyyah silently, raise the open palms beside the ears (brothers) or shoulders (sisters), and say Allahu Akbar.",
            posture = GuidePosture.TAKBIR
        )
    }

    val recitation = if (rakat == 1) {
        "سُبْحَانَكَ اللَّهُمَّ وَبِحَمْدِكَ، وَتَبَارَكَ اسْمُكَ، وَتَعَالَى جَدُّكَ، وَلَا إِلَهَ غَيْرُكَ. " +
            "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ۝ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ ۝ الرَّحْمَٰنِ الرَّحِيمِ ۝ مَالِكِ يَوْمِ الدِّينِ ۝ إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ ۝ اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ ۝ صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ"
    } else {
        "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ۝ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ ۝ الرَّحْمَٰنِ الرَّحِيمِ ۝ مَالِكِ يَوْمِ الدِّينِ ۝ إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ ۝ اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ ۝ صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ"
    }

    steps += GuideStep(
        title = if (rakat == 1) {
            "2. Al-Qiyam — Standing & Recitation"
        } else {
            "2. Al-Qiyam — Standing & Al-Fatihah"
        },
        arabicTitle = if (rakat == 1) "القيام والقراءة" else "الْقِيَام",
        arabicText = recitation,
        transliteration = "Subhanaka Allahumma wa bihamdika... Alhamdu lillahi Rabbil-'alamin...",
        translation = "Glory be to You, O Allah, and praise... All praise is for Allah, Lord of all worlds. Guide us to the straight path.",
        source = "Sahih al-Bukhari 740, 756, 776, 780; Sunan Abi Dawud 776",
        notes = if (rakat == 1) {
            "Fold the forearms on the chest with the right hand over the left. Recite the opening supplication, Al-Fatihah with Ameen, and another Surah."
        } else if (rakat <= 2) {
            "Recite Al-Fatihah with Ameen, then another Surah. The opening supplication is recited only in Rakat 1."
        } else {
            "Recite Al-Fatihah with Ameen. No additional Surah is required in this Rakat, though it may be recited."
        },
        posture = GuidePosture.QIYAM
    )

    steps += GuideStep(
        title = "3. Ruku' — Bowing",
        arabicTitle = "الركوع",
        arabicText = "سُبْحَانَ رَبِّيَ الْعَظِيمِ",
        transliteration = "Subhana Rabbiyal-'Adheem (3×)",
        translation = "Glory be to my Lord, the Most Magnificent.",
        source = "Sahih al-Bukhari 828; Sunan Abi Dawud 870",
        notes = "Say Allahu Akbar, bow with a flat back at about 90 degrees, grip the knees, and recite three times with calm stillness."
    )

    steps += GuideStep(
        title = "4. Qawmah — Rising from Ruku'",
        arabicTitle = "القيام بعد الركوع",
        arabicText = "سَمِعَ اللَّهُ لِمَنْ حَمِدَهُ، رَبَّنَا وَلَكَ الْحَمْدُ",
        transliteration = "Sami'Allahu liman hamidah, Rabbana wa lakal-hamd",
        translation = "Allah hears the one who praises Him. Our Lord, to You belongs all praise.",
        source = "Sahih al-Bukhari 735, 796",
        notes = "Rise fully upright and pause calmly before prostrating."
    )

    steps += GuideStep(
        title = "5. Sujud & Jalsah — Two Prostrations",
        arabicTitle = "السجود والجلسة",
        arabicText = "سُبْحَانَ رَبِّيَ الْأَعْلَى. رَبِّ اغْفِرْ لِي، رَبِّ اغْفِرْ لِي",
        transliteration = "Subhana Rabbiyal-A'la (3×). Rabbigh-fir li, Rabbigh-fir li.",
        translation = "Glory be to my Lord, the Most High. My Lord, forgive me; my Lord, forgive me.",
        source = "Sahih al-Bukhari 812, 828; Sahih Muslim 772; Sunan Abi Dawud 874",
        notes = "Prostrate on the seven bones with the forearms lifted, recite three times, sit between the two prostrations, then prostrate again."
    )

    if (hasTashahhud) {
        val tashahhudText = if (isFinalRakah) {
            "التَّحِيَّاتُ لِلَّهِ وَالصَّلَوَاتُ وَالطَّيِّبَاتُ، السَّلَامُ عَلَيْكَ أَيُّهَا النَّبِيُّ وَرَحْمَةُ اللَّهِ وَبَرَكَاتُهُ، السَّلَامُ عَلَيْنَا وَعَلَى عِبَادِ اللَّهِ الصَّالِحِينَ، أَشْهَدُ أَنْ لَا إِلَهَ إِلَّا اللَّهُ وَأَشْهَدُ أَنَّ مُحَمَّدًا عَبْدُهُ وَرَسُولُهُ. " +
                "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ، كَمَا صَلَّيْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ، إِنَّكَ حَمِيدٌ مَجِيدٌ، اللَّهُمَّ بَارِكْ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ، كَمَا بَارَكْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ، إِنَّكَ حَمِيدٌ مَجِيدٌ. " +
                "اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنْ عَذَابِ جَهَنَّمَ، وَمِنْ عَذَابِ الْقَبْرِ، وَمِنْ فِتْنَةِ الْمَحْيَا وَالْمَمَاتِ، وَمِنْ شَرِّ فِتْنَةِ الْمَسِيحِ الدَّجَّالِ"
        } else {
            "التَّحِيَّاتُ لِلَّهِ وَالصَّلَوَاتُ وَالطَّيِّبَاتُ، السَّلَامُ عَلَيْكَ أَيُّهَا النَّبِيُّ وَرَحْمَةُ اللَّهِ وَبَرَكَاتُهُ، السَّلَامُ عَلَيْنَا وَعَلَى عِبَادِ اللَّهِ الصَّالِحِينَ، أَشْهَدُ أَنْ لَا إِلَهَ إِلَّا اللَّهُ وَأَشْهَدُ أَنَّ مُحَمَّدًا عَبْدُهُ وَرَسُولُهُ"
        }
        steps += GuideStep(
            title = if (isFinalRakah) "6. Tashahhud, Darood & Final Dua" else "6. Middle Tashahhud",
            arabicTitle = "التشهد والصلاة الإبراهيمية",
            arabicText = tashahhudText,
            transliteration = if (isFinalRakah) {
                "At-tahiyyatu lillahi was-salawatu wat-tayyibat... Allahumma salli 'ala Muhammad..."
            } else {
                "At-tahiyyatu lillahi was-salawatu wat-tayyibat..."
            },
            translation = if (isFinalRakah) {
                "All greetings, prayers and pure things belong to Allah... O Allah, send blessings upon Muhammad and the family of Muhammad... O Allah, I seek refuge in You from the punishment of Hell, the grave, and the trial of the Dajjal."
            } else {
                "All greetings, prayers and pure things belong to Allah... Peace be upon us and upon the righteous servants of Allah."
            },
            source = "Sahih al-Bukhari 6265, 3370; Sahih Muslim 588",
            notes = if (isFinalRakah) {
                "Sit for the final Tashahhud, recite the Darood, then the final supplication before Tasleem. Raise the right index finger during the testimony."
            } else {
                "This is the middle sitting after Rakat 2. Recite the Tashahhud, then stand for Rakat 3."
            },
            posture = GuidePosture.TASHAHHUD
        )
    }

    if (isFinalRakah) {
        steps += GuideStep(
            title = "7. Salam — Tasleem",
            arabicTitle = "التسليم",
            arabicText = "السَّلَامُ عَلَيْكُمْ وَرَحْمَةُ اللَّهِ. السَّلَامُ عَلَيْكُمْ وَرَحْمَةُ اللَّهِ",
            transliteration = "Assalamu 'alaykum wa rahmatullah (right, then left)",
            translation = "Peace be upon you and the mercy of Allah.",
            source = "Sahih Muslim 582",
            notes = "Turn the head to the right, then to the left, saying the Salam each time."
        )
    }

    return steps.mapIndexed { index, step ->
        if (step.posture == null && step.title.contains("Salam")) {
            step.copy(posture = GuidePosture.SALAM)
        } else step.copy(
            title = "${index + 1}. ${step.title.substringAfter(". ", step.title)}"
        )
    }
}

private val afterSalahSteps = listOf(
    GuideStep(
        title = "1. Takbir Once",
        arabicText = "اللَّهُ أَكْبَرُ",
        transliteration = "Allahu Akbar",
        translation = "Allah is the Greatest.",
        source = "Sahih al-Bukhari 841",
        notes = "After completing the prayer, say Allahu Akbar once."
    ),
    GuideStep(
        title = "2. Istighfar 3× & Salam Dua",
        arabicText = "أَسْتَغْفِرُ اللَّهَ، أَسْتَغْفِرُ اللَّهَ، أَسْتَغْفِرُ اللَّهَ. اللَّهُمَّ أَنْتَ السَّلَامُ وَمِنْكَ السَّلَامُ، تَبَارَكْتَ يَا ذَا الْجَلَالِ وَالْإِكْرَامِ",
        transliteration = "Astaghfirullah (3×). Allahumma Antas-Salam wa minkas-salam, tabarakta ya Dhal-Jalali wal-Ikram.",
        translation = "I seek Allah's forgiveness. O Allah, You are Peace and from You comes peace. Blessed are You, Owner of majesty and honour.",
        source = "Sahih Muslim 591",
        notes = "Seek forgiveness three times, then recite the supplication of peace."
    ),
    GuideStep(
        title = "3. Declaration of Tawhid",
        arabicText = "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، اللَّهُمَّ لَا مَانِعَ لِمَا أَعْطَيْتَ، وَلَا مُعْطِيَ لِمَا مَنَعْتَ، وَلَا يَنْفَعُ ذَا الْجَدِّ مِنْكَ الْجَدُّ",
        transliteration = "La ilaha illallahu wahdahu la sharika lah, lahul-mulku wa lahul-hamdu wa huwa 'ala kulli shay'in qadir. Allahumma la mani'a lima a'tayta, wa la mu'tiya lima mana'ta, wa la yanfa'u dhal-jaddi minkal-jadd.",
        translation = "None has the right to be worshipped except Allah alone... O Allah, no one can withhold what You give, or give what You withhold.",
        source = "Sahih al-Bukhari 844",
        notes = "Recite once after the prayer."
    ),
    GuideStep(
        title = "Tasbih",
        arabicText = "سُبْحَانَ اللَّهِ، الْحَمْدُ لِلَّهِ، اللَّهُ أَكْبَرُ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ",
        transliteration = "SubhanAllah 33×, Alhamdulillah 33×, Allahu Akbar 33×, then the 100th completion.",
        translation = "Glory be to Allah. All praise is for Allah. Allah is the Greatest. The 100th completes the dhikr with Tawhid.",
        source = "Sahih Muslim 597",
        notes = "Count 33 + 33 + 33 using the interactive counter, then complete the hundredth with the Tawhid phrase."
    ),
    GuideStep(
        title = "5. Ayat al-Kursi",
        arabicText = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ، لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ، لَهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ، مَنْ ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلَّا بِإِذْنِهِ، يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ، وَلَا يُحِيطُونَ بِشَيْءٍ مِنْ عِلْمِهِ إِلَّا بِمَا شَاءَ، وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ، وَلَا يَئُودُهُ حِفْظُهُمَا، وَهُوَ الْعَلِيُّ الْعَظِيمُ",
        transliteration = "Allahu la ilaha illa Huwal-Hayyul-Qayyum...",
        translation = "Allah — there is no deity except Him, the Ever-Living, the Sustainer of existence...",
        source = "Surah Al-Baqarah 2:255; an-Nasa'i al-Kubra 9928",
        notes = "Recite Ayat al-Kursi after each obligatory prayer."
    ),
    GuideStep(
        title = "6. The Three Quls",
        arabicText = "قُلْ هُوَ اللَّهُ أَحَدٌ ۝ اللَّهُ الصَّمَدُ ۝ لَمْ يَلِدْ وَلَمْ يُولَدْ ۝ وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ. " +
            "قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ ۝ مِنْ شَرِّ مَا خَلَقَ ۝ وَمِنْ شَرِّ غَاسِقٍ إِذَا وَقَبَ ۝ وَمِنْ شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ ۝ وَمِنْ شَرِّ حَاسِدٍ إِذَا حَسَدَ. " +
            "قُلْ أَعُوذُ بِرَبِّ النَّاسِ ۝ مَلِكِ النَّاسِ ۝ إِلَٰهِ النَّاسِ ۝ مِنْ شَرِّ الْوَسْوَاسِ الْخَنَّاسِ ۝ الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ ۝ مِنْ الْجِنَّةِ وَالنَّاسِ",
        transliteration = "Qul Huwa Allahu Ahad... Qul a'udhu bi-Rabbil-Falaq... Qul a'udhu bi-Rabbin-Nas...",
        translation = "Surah Al-Ikhlas, Al-Falaq, and An-Nas.",
        source = "Abu Dawud 1523",
        notes = "Recite Al-Ikhlas, Al-Falaq, and An-Nas once after Dhuhr, Asr, and Isha; three times after Fajr and Maghrib."
    )
)
