package com.sakinah.deen.ui.screens

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.R
import com.sakinah.deen.data.database.dao.AdhkarDao
import com.sakinah.deen.data.database.entity.AdhkarEntity
import com.sakinah.deen.data.prefs.AppearancePreferences
import com.sakinah.deen.data.prefs.SalahGuideProgress
import com.sakinah.deen.data.prefs.SalahGuideProgressStore
import com.sakinah.deen.profile.UserProfileRepository
import com.sakinah.deen.ui.components.ArabicVerseBlock
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.ui.rememberInject
import com.sakinah.deen.ui.theme.AmiriFontFamily
import com.sakinah.deen.ui.theme.PrayerGuideGender
import kotlinx.coroutines.launch
import java.util.Locale

private enum class GuideTab(@param:StringRes val labelRes: Int) {
    WUDU(R.string.guide_tab_wudu),
    SALAH(R.string.guide_tab_salah),
    AFTER_SALAH(R.string.guide_tab_after_salah),
    ADHKAR(R.string.guide_tab_adhkar)
}

private data class PostureVariation(
    val label: String,
    val physicalInstruction: String,
    val hadithSource: String
)

private data class GuideStep(
    val title: String,
    val shortLabel: String,
    val arabicTitle: String = "",
    val variations: List<PostureVariation> = emptyList(),
    val defaultVariationIndex: Int = 0,
    val physicalInstruction: String = "",
    val arabicDhikr: String = "",
    val transliteration: String = "",
    val englishMeaning: String = "",
    val recitationCountOrNote: String = "",
    val quranReference: String = "",
    val hadithReference: String = "",
    /** Sahih / Hasan grading of the cited narration, shown beside the reference. */
    val grading: String = "",
    /** Prescribed repetitions; 1 when the step is performed once. */
    val repeatCount: Int = 1,
    val hasTasbihCounter: Boolean = false
) {
    /**
     * Stable identity for learned/favorite state: the posture name without its step number,
     * so the same Qiyam ticked in rakat 1 is still ticked in rakat 3.
     */
    val stepKey: String get() = title.substringAfter(". ").trim()
}

private fun stepsForTab(tab: GuideTab, rakat: Int, totalRakats: Int): List<GuideStep> = when (tab) {
    GuideTab.WUDU -> wuduSteps
    GuideTab.SALAH -> buildSalahSteps(rakat, totalRakats)
    GuideTab.AFTER_SALAH -> afterSalahSteps
    GuideTab.ADHKAR -> emptyList()
}

@Composable
fun VisualSalahGuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    totalRakats: Int = 2,
    prayerTitle: String = "Prayer"
) {
    val safeTotalRakats = totalRakats.coerceIn(1, 4)

    val appearancePrefs = rememberInject<AppearancePreferences>()
    val profileRepo = rememberInject<UserProfileRepository>()
    val progressStore = rememberInject<SalahGuideProgressStore>()
    val adhkarDao = rememberInject<AdhkarDao>()
    val appearanceConfig by appearancePrefs.config.collectAsStateWithLifecycle()
    val guideProgress by progressStore.progress.collectAsStateWithLifecycle()
    // The bundled Hisn al-Muslim library, seeded on first launch; small enough to read whole.
    val adhkarEntries by remember { adhkarDao.observeAll() }
        .collectAsStateWithLifecycle(emptyList<AdhkarEntity>())
    val scope = rememberCoroutineScope()

    var tabIndex by rememberSaveable { mutableIntStateOf(GuideTab.SALAH.ordinal) }
    val tab = GuideTab.entries[tabIndex.coerceIn(0, GuideTab.entries.lastIndex)]
    var currentRakat by rememberSaveable(prayerTitle, safeTotalRakats) { mutableIntStateOf(1) }
    var currentStepIndex by rememberSaveable { mutableIntStateOf(0) }
    var tasbihCount by rememberSaveable { mutableIntStateOf(0) }

    val steps = remember(tab, currentRakat, safeTotalRakats) {
        stepsForTab(tab, currentRakat, safeTotalRakats)
    }
    val safeStepIndex = if (steps.isEmpty()) 0 else currentStepIndex.coerceIn(0, steps.lastIndex)
    // The Adhkar tab has no posture steps; the card below is not rendered there.
    val step = steps.getOrNull(safeStepIndex) ?: afterSalahSteps.first()
    val allStepKeys = remember(safeTotalRakats) {
        GuideTab.entries
            .filter { it != GuideTab.ADHKAR }
            .associateWith { guideTab ->
                stepsForTab(guideTab, 1, safeTotalRakats).map { it.stepKey }.distinct()
            }
    }
    val stepKey = "${tab.name}:${step.stepKey}"
    val learnedSteps = allStepKeys.values.sumOf { guideProgress.learnedCount(it) }
    val totalSteps = allStepKeys.values.sumOf { it.size }
    val favoriteCount = guideProgress.favoriteStepKeys.size + guideProgress.favoriteAdhkarIds.size
    var selectedVariationIndex by remember(tab, currentRakat, safeStepIndex) {
        mutableIntStateOf(step.defaultVariationIndex.coerceIn(0, (step.variations.size - 1).coerceAtLeast(0)))
    }
    val activeVariation = step.variations.getOrNull(selectedVariationIndex) ?: step.variations.firstOrNull()

    val isLastSalahStep = tab == GuideTab.SALAH &&
        currentRakat == safeTotalRakats &&
        safeStepIndex == steps.lastIndex
    val primaryActionLabel = when {
        tab == GuideTab.WUDU && safeStepIndex == steps.lastIndex -> stringResource(R.string.guide_go_to_salah)
        isLastSalahStep -> stringResource(R.string.guide_go_to_after_salah)
        tab == GuideTab.AFTER_SALAH && safeStepIndex == steps.lastIndex -> stringResource(R.string.guide_complete)
        else -> stringResource(R.string.guide_next_step)
    }

    val scrollState = rememberScrollState()
    val filmstripState = rememberLazyListState()

    LaunchedEffect(tab, currentRakat, safeStepIndex) {
        scrollState.scrollTo(0)
        // The filmstrip is not composed on the Adhkar tab; scrolling it would be a no-op at
        // best and an index error at worst.
        if (tab != GuideTab.ADHKAR) filmstripState.animateScrollToItem(safeStepIndex)
    }

    val context = LocalContext.current
    var ttsInitialized by remember { mutableStateOf(false) }
    var arabicTtsSupported by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }

    val tts = remember(context) {
        TextToSpeech(context) { status ->
            ttsInitialized = status == TextToSpeech.SUCCESS
        }
    }

    LaunchedEffect(ttsInitialized) {
        if (ttsInitialized) {
            val arabicLocale = Locale.Builder().setLanguage("ar").setRegion("SA").build()
            val result = tts.setLanguage(arabicLocale)
            arabicTtsSupported = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            if (arabicTtsSupported) {
                tts.setSpeechRate(0.85f)
            }
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
        if (text.isBlank() || !arabicTtsSupported) return
        if (isSpeaking) {
            tts.stop()
            isSpeaking = false
        } else {
            tts.stop()
            isSpeaking = true
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "salah-guide-${tab.name}-$currentRakat-$safeStepIndex")
        }
    }

    val tabLabels = GuideTab.entries.map { guideTab ->
        val label = stringResource(guideTab.labelRes)
        val keys = allStepKeys[guideTab]
        if (keys.isNullOrEmpty()) {
            label
        } else {
            stringResource(
                R.string.guide_progress_tab,
                label,
                guideProgress.percentLearned(keys)
            )
        }
    }
    val activeTabLabel = stringResource(tab.labelRes)

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Single Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.guide_back_cd)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (tab == GuideTab.SALAH && prayerTitle != "Prayer") {
                        "$prayerTitle · ${stringResource(R.string.guide_title)}"
                    } else {
                        stringResource(R.string.guide_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (tab == GuideTab.ADHKAR) {
                        pluralStringResource(
                            R.plurals.guide_adhkar_subtitle,
                            adhkarEntries.size,
                            adhkarEntries.size,
                            guideProgress.favoriteAdhkarIds.size
                        )
                    } else if (tab == GuideTab.SALAH) {
                        stringResource(
                            R.string.guide_subtitle_salah,
                            prayerTitle,
                            currentRakat,
                            safeTotalRakats,
                            safeStepIndex + 1,
                            steps.size
                        )
                    } else {
                        stringResource(
                            R.string.guide_subtitle_other,
                            activeTabLabel,
                            safeStepIndex + 1,
                            steps.size
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 1b. Guide style (male/female postures) can be switched here so users do not have
        // to hunt through Settings; it stays in sync with the profile gender.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Guide style",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PrayerGuideGender.entries.forEach { gender ->
                FilterChip(
                    selected = appearanceConfig.prayerGuideGender == gender,
                    onClick = {
                        appearancePrefs.update { it.copy(prayerGuideGender = gender) }
                        val stored = profileRepo.profile.value
                        if (stored != null) {
                            val value = if (gender == PrayerGuideGender.MALE) "MALE" else "FEMALE"
                            scope.launch { profileRepo.update(stored.copy(gender = value)) }
                        }
                    },
                    label = {
                        Text(
                            text = if (gender == PrayerGuideGender.MALE) "Male" else "Female",
                            fontSize = 11.sp
                        )
                    }
                )
            }
        }

        // 2. Compact Wudu | Salah | After Salah Tab Bar
        SegmentedPillBar(
            options = tabLabels,
            selectedIndex = tab.ordinal,
            onSelect = { idx ->
                tts.stop()
                isSpeaking = false
                tabIndex = idx
                currentStepIndex = 0
            }
        )

        // 2b. Persistent learning progress + optional practice reminder (#83, #86).
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(
                        R.plurals.guide_progress_summary,
                        totalSteps,
                        learnedSteps,
                        totalSteps
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = pluralStringResource(R.plurals.guide_favorites_summary, favoriteCount, favoriteCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.guide_practice_reminder),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(
                checked = guideProgress.practiceReminderEnabled,
                onCheckedChange = { progressStore.setPracticeReminder(it) }
            )
        }

        // 3. Layout 2: Horizontal Posture Filmstrip (60.dp thumbnails)
        if (tab != GuideTab.ADHKAR) LazyRow(
            state = filmstripState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            itemsIndexed(steps) { index, item ->
                val isActive = index == safeStepIndex

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = BorderStroke(
                        width = if (isActive) 2.dp else 1.dp,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        }
                    ),
                    modifier = Modifier
                        .size(width = 68.dp, height = 72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            tts.stop()
                            isSpeaking = false
                            currentStepIndex = index
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.shortLabel,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Black.copy(alpha = 0.65f)
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.guide_step_badge, index + 1),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        Color.White
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Scrollable Focused Step Card (Portrait & Landscape Safe)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (tab == GuideTab.ADHKAR) {
                    AdhkarLibrarySection(
                        entries = adhkarEntries,
                        progress = guideProgress,
                        onToggleFavorite = progressStore::toggleFavoriteAdhkar
                    )
                } else {
                    FocusedGuideStepCard(
                        step = step,
                        activeVariation = activeVariation,
                        selectedVariationIndex = selectedVariationIndex,
                        onSelectVariation = { selectedVariationIndex = it },
                        isSpeaking = isSpeaking,
                        arabicTtsSupported = arabicTtsSupported,
                        onPronounce = { speakArabic(step.arabicDhikr) },
                        isLearned = guideProgress.isLearned(stepKey),
                        onToggleLearned = { progressStore.toggleLearned(stepKey) },
                        isFavorite = guideProgress.isFavoriteStep(stepKey),
                        onToggleFavorite = { progressStore.toggleFavoriteStep(stepKey) }
                    )
                }

                if (tab != GuideTab.ADHKAR) {
                    Text(
                        text = stringResource(R.string.guide_review_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (tab != GuideTab.ADHKAR && step.hasTasbihCounter) {
                    TasbihCounter(
                        count = tasbihCount,
                        onIncrement = { tasbihCount = (tasbihCount + 1).coerceAtMost(100) },
                        onReset = { tasbihCount = 0 }
                    )
                }

                if (tab == GuideTab.WUDU && steps.isNotEmpty() && safeStepIndex == steps.lastIndex) {
                    Text(
                        text = stringResource(R.string.guide_wudu_next_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 5. Pinned Bottom Controls (the Adhkar library is browsed, not stepped through)
        if (tab != GuideTab.ADHKAR) Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val canGoBack = when (tab) {
                GuideTab.SALAH -> currentRakat > 1 || safeStepIndex > 0
                GuideTab.WUDU, GuideTab.AFTER_SALAH -> safeStepIndex > 0
                GuideTab.ADHKAR -> false
            }

            OutlinedButton(
                onClick = {
                    tts.stop()
                    isSpeaking = false
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
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                Text(stringResource(R.string.guide_previous))
            }

            Button(
                onClick = {
                    tts.stop()
                    isSpeaking = false
                    when (tab) {
                        GuideTab.WUDU -> {
                            if (safeStepIndex < steps.lastIndex) {
                                currentStepIndex = safeStepIndex + 1
                            } else {
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
                                tabIndex = GuideTab.AFTER_SALAH.ordinal
                                currentStepIndex = 0
                            }
                        }

                        GuideTab.AFTER_SALAH -> {
                            if (safeStepIndex < steps.lastIndex) {
                                currentStepIndex = safeStepIndex + 1
                            } else {
                                onBack()
                            }
                        }

                        // The Adhkar tab has no step navigation (the bar is not shown there).
                        GuideTab.ADHKAR -> Unit
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = primaryActionLabel,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusedGuideStepCard(
    step: GuideStep,
    activeVariation: PostureVariation?,
    selectedVariationIndex: Int,
    onSelectVariation: (Int) -> Unit,
    isSpeaking: Boolean,
    arabicTtsSupported: Boolean,
    onPronounce: () -> Unit,
    isLearned: Boolean,
    onToggleLearned: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    SakinahCard(contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (step.arabicTitle.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = step.arabicTitle,
                        fontFamily = AmiriFontFamily,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.guide_unfavorite_cd else R.string.guide_favorite_cd
                        ),
                        tint = if (isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Persisted practice state: tick a posture once it is learned, and keep favourites
            // so a returning learner can jump straight back to the parts they struggle with.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = isLearned,
                    onClick = onToggleLearned,
                    leadingIcon = if (isLearned) {
                        {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        null
                    },
                    label = {
                        Text(
                            text = stringResource(
                                if (isLearned) R.string.guide_learned else R.string.guide_mark_learned
                            ),
                            fontSize = 11.sp
                        )
                    }
                )
                if (step.grading.isNotBlank()) {
                    ReferencePill(
                        text = stringResource(R.string.guide_adhkar_grading, step.grading),
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
                if (step.repeatCount > 1) {
                    ReferencePill(
                        text = stringResource(R.string.guide_adhkar_repeat, step.repeatCount),
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (activeVariation != null && step.variations.size > 1) {
                Text(
                    text = stringResource(R.string.guide_variation_label),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    step.variations.forEachIndexed { idx, variation ->
                        FilterChip(
                            selected = idx == selectedVariationIndex,
                            onClick = { onSelectVariation(idx) },
                            label = {
                                Text(
                                    text = variation.label,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }


            // Physical instruction block
            val effectivePhysical = buildString {
                if (activeVariation != null && activeVariation.physicalInstruction.isNotBlank()) {
                    append(activeVariation.physicalInstruction)
                }
                if (step.physicalInstruction.isNotBlank()) {
                    if (isNotEmpty() && !activeVariation?.physicalInstruction.equals(step.physicalInstruction)) {
                        append("\n")
                        append(step.physicalInstruction)
                    } else if (isEmpty()) {
                        append(step.physicalInstruction)
                    }
                }
            }
            if (effectivePhysical.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.guide_section_physical),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = effectivePhysical,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (activeVariation != null && activeVariation.hadithSource.isNotBlank()) {
                        Text(
                            text = activeVariation.hadithSource,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Recitation block
            if (step.arabicDhikr.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.guide_section_recite),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (step.recitationCountOrNote.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = step.recitationCountOrNote,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            ArabicVerseBlock(
                                arabicText = step.arabicDhikr,
                                fontSizeSp = 22,
                                color = MaterialTheme.colorScheme.onSurface,
                                isQuran = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = 14.dp,
                                        end = 14.dp,
                                        top = 14.dp,
                                        bottom = 14.dp
                                    )
                            )
                            TtsSpeakerButton(
                                isSpeaking = isSpeaking,
                                enabled = arabicTtsSupported,
                                onClick = onPronounce,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            )
                        }
                    }

                    if (!arabicTtsSupported) {
                        Text(
                            text = stringResource(R.string.guide_tts_unavailable),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // A device voice helps with pronunciation, nothing more: Tajweed needs a
                    // teacher, and no TTS output is a recitation to imitate blindly.
                    Text(
                        text = stringResource(R.string.guide_tts_aid_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (step.transliteration.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.guide_pronunciation_prefix, step.transliteration),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (step.englishMeaning.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.guide_meaning_prefix, step.englishMeaning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Quran & Hadith Reference Pills
            if (step.quranReference.isNotBlank() || step.hadithReference.isNotBlank()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (step.quranReference.isNotBlank()) {
                        ReferencePill(
                            text = stringResource(R.string.guide_quran_ref, step.quranReference),
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (step.hadithReference.isNotBlank()) {
                        ReferencePill(
                            text = stringResource(R.string.guide_hadith_ref, step.hadithReference),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * The bundled Hisn al-Muslim library (#80, #81, #84, #85).
 *
 * Every card carries its own provenance: the Arabic wording, a transliteration for readers
 * who do not know the script, the translation, the book and number of the narration, its
 * grading, the prescribed number of repetitions, and any difference between narrations.
 * Favourites persist through [SalahGuideProgressStore].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdhkarLibrarySection(
    entries: List<AdhkarEntity>,
    progress: SalahGuideProgress,
    onToggleFavorite: (String) -> Unit
) {
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val categories = remember(entries) {
        entries.map { it.categoryKey to it.categoryTitle }.distinct()
    }
    val visible = entries.filter { selectedCategory == null || it.categoryKey == selectedCategory }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (entries.isEmpty()) {
            SakinahCard(contentPadding = 14.dp) {
                Text(
                    text = stringResource(R.string.guide_adhkar_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "all") {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = {
                        Text(stringResource(R.string.guide_adhkar_all), fontSize = 11.sp)
                    }
                )
            }
            items(categories.size) { index ->
                val (key, title) = categories[index]
                FilterChip(
                    selected = selectedCategory == key,
                    onClick = { selectedCategory = key },
                    label = {
                        Text(title, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                )
            }
        }

        visible.forEach { entry ->
            val isFavorite = progress.isFavoriteAdhkar(entry.id)
            SakinahCard(contentPadding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        ReferencePill(
                            text = stringResource(R.string.guide_adhkar_repeat, entry.repeatCount),
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        IconButton(onClick = { onToggleFavorite(entry.id) }) {
                            Icon(
                                imageVector = if (isFavorite) {
                                    Icons.Default.Favorite
                                } else {
                                    Icons.Default.FavoriteBorder
                                },
                                contentDescription = stringResource(
                                    if (isFavorite) {
                                        R.string.guide_unfavorite_cd
                                    } else {
                                        R.string.guide_favorite_cd
                                    }
                                ),
                                tint = if (isFavorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = entry.arabic,
                        fontFamily = AmiriFontFamily,
                        fontSize = 20.sp,
                        lineHeight = 34.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (entry.transliteration.isNotBlank()) {
                        Text(
                            text = entry.transliteration,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (entry.translation.isNotBlank()) {
                        Text(
                            text = entry.translation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReferencePill(
                            text = stringResource(
                                R.string.guide_adhkar_source_prefix,
                                entry.reference
                            ),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (entry.grading.isNotBlank()) {
                            ReferencePill(
                                text = stringResource(R.string.guide_adhkar_grading, entry.grading),
                                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (entry.variantNote.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.guide_adhkar_variant, entry.variantNote),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.guide_review_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TtsSpeakerButton(
    isSpeaking: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description = if (isSpeaking) {
        stringResource(R.string.guide_stop_speaking)
    } else {
        stringResource(R.string.guide_speak_arabic)
    }
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Icon(
            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = description,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ReferencePill(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TasbihCounter(
    count: Int,
    onIncrement: () -> Unit,
    onReset: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val phase = when {
        count < 33 -> stringResource(R.string.guide_tasbih_subhanallah)
        count < 66 -> stringResource(R.string.guide_tasbih_alhamdulillah)
        else -> stringResource(R.string.guide_tasbih_allahuakbar)
    }
    SakinahCard(contentPadding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.guide_tasbih_title, count),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (count >= 100) {
                            stringResource(R.string.guide_tasbih_done_hint)
                        } else {
                            stringResource(R.string.guide_tasbih_now, phase)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onReset()
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.guide_tasbih_reset_cd)
                    )
                }
            }
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onIncrement()
                },
                enabled = count < 100,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (count < 100) {
                        stringResource(R.string.guide_tasbih_tap)
                    } else {
                        stringResource(R.string.guide_tasbih_complete)
                    }
                )
            }
        }
    }
}

private val wuduSteps = listOf(
    GuideStep(
        title = "1. Intention & Bismillah",
        shortLabel = "Bismillah",
        arabicTitle = "النية والتسمية",
        physicalInstruction = "Stand or sit at the tap and make silent Niyyah in the heart before beginning.",
        arabicDhikr = "بِسْمِ اللَّهِ",
        transliteration = "Bismillah",
        englishMeaning = "In the name of Allah.",
        recitationCountOrNote = "1× before washing",
        quranReference = "Quran 5:6",
        hadithReference = "Sunan Abi Dawud 101; Sahih al-Bukhari 1"
    ),
    GuideStep(
        title = "2. Hands, Mouth & Nose (3× each)",
        shortLabel = "Hands & Nose",
        physicalInstruction = "Wash both hands up to the wrists 3×, interlacing the fingers; rinse the mouth (madmadah) and sniff water into the nose and blow it out with the left hand (istinshaq) 3×.",
        quranReference = "Quran 5:6",
        hadithReference = "Sahih al-Bukhari 159, 191; Sahih Muslim 226"
    ),
    GuideStep(
        title = "3. Face & Arms to Elbows (3× each)",
        shortLabel = "Face & Arms",
        physicalInstruction = "Wash the full face from forehead to chin and ear to ear 3×; wash the right arm then the left arm from the fingertips up to and including the elbows 3×.",
        quranReference = "Quran 5:6",
        hadithReference = "Sahih al-Bukhari 159"
    ),
    GuideStep(
        title = "4. Wipe Head, Ears & Wash Feet (to Ankles 3×)",
        shortLabel = "Head & Feet",
        physicalInstruction = "Wipe wet hands over the head front-to-back and back-to-front once and wipe the ears once; wash the right foot then the left foot up to and including the ankles 3×, cleaning between the toes.",
        quranReference = "Quran 5:6",
        hadithReference = "Sahih al-Bukhari 185; Sahih Muslim 226; Sunan Abi Dawud 135, 148"
    ),
    GuideStep(
        title = "5. Dua After Wudu",
        shortLabel = "Wudu Dua",
        arabicTitle = "دعاء بعد الوضوء",
        physicalInstruction = "Recite upon completing wudu.",
        arabicDhikr = "أَشْهَدُ أَنْ لَا إِلَٰهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ وَأَشْهَدُ أَنَّ مُحَمَّدًا عَبْدُهُ وَرَسُولُهُ",
        transliteration = "Ash-hadu alla ilaha illallahu wahdahu la sharika lah, wa ash-hadu anna Muhammadan 'abduhu wa rasuluh.",
        englishMeaning = "I bear witness that there is no deity worthy of worship except Allah alone without partner, and Muhammad is His servant and Messenger.",
        recitationCountOrNote = "1× after wudu",
        hadithReference = "Sahih Muslim 234",
        grading = "Sahih",
        repeatCount = 1
    )
)

private fun buildSalahSteps(rakat: Int, totalRakats: Int): List<GuideStep> {
    val isFinalRakah = rakat == totalRakats
    val hasMiddleSitting = (rakat == 2 && totalRakats >= 3) || (rakat == 3 && totalRakats == 4)
    val hasTashahhud = isFinalRakah || hasMiddleSitting
    val steps = mutableListOf<GuideStep>()

    if (rakat == 1) {
        steps += GuideStep(
            title = "Standing (Qiyam) & Intention",
            shortLabel = "Niyyah",
            arabicTitle = "القيام والنية",
            variations = listOf(
                PostureVariation(
                    label = "Qiyam",
                    physicalInstruction = "Face the Qiblah, feet naturally shoulder-width apart with toes pointing forward, eyes open looking at the Sujud spot. Make Niyyah silently in the heart (no verbal Niyyah).",
                    hadithSource = "Sahih al-Bukhari 1 (intentions), 741"
                )
            ),
            quranReference = "Quran 2:144 (facing Masjid al-Haram)",
            hadithReference = "Sahih al-Bukhari 1, 741"
        )

        steps += GuideStep(
            title = "Takbirat al-Ihram (Opening Takbir)",
            shortLabel = "Takbir",
            arabicTitle = "تكبيرة الإحرام",
            variations = listOf(
                PostureVariation(
                    label = "Raise Hands",
                    physicalInstruction = "Raise both hands with palms open facing Qiblah—palms level with shoulders and fingertips level with earlobes (do NOT touch earlobes with thumbs).",
                    hadithSource = "Sahih al-Bukhari 735; Sahih Muslim 390, 391, 401"
                )
            ),
            arabicDhikr = "اللَّهُ أَكْبَرُ",
            transliteration = "Allahu Akbar",
            englishMeaning = "Allah is the Greatest.",
            recitationCountOrNote = "1× opening",
            quranReference = "Quran 74:3, 29:45",
            hadithReference = "Sahih al-Bukhari 735; Sahih Muslim 390, 391, 401"
        )

        steps += GuideStep(
            title = "Opening Supplication (Dua al-Istiftah) & Ta'awwudh",
            shortLabel = "Istiftah",
            arabicTitle = "دعاء الاستفتاح والتعوذ",
            variations = listOf(
                PostureVariation(
                    label = "Fold Hands",
                    physicalInstruction = "Fold hands on chest; recite the opening supplication, then seek refuge and begin recitation.",
                    hadithSource = "Sahih Muslim 399, 200?"
                )
            ),
            arabicDhikr = "سُبْحَانَكَ اللَّهُمَّ وَبِحَمْدِكَ، وَتَبَارَكَ اسْمُكَ، وَتَعَالَى جَدُّكَ، وَلَا إِلَٰهَ غَيْرُكَ.\nأَعُوذُ بِاللَّهِ مِنَ الشَّيْطَانِ الرَّجِيمِ",
            transliteration = "SubhanakAllahumma wa bihamdika, wa tabarakasmuka, wa ta'ala jadduka, wa la ilaha ghayruk. A'udhu billahi minash-shaytanir-rajim.",
            englishMeaning = "Glory and praise be to You, O Allah; blessed is Your name, exalted is Your majesty, and there is no deity besides You. I seek refuge in Allah from the accursed Satan.",
            recitationCountOrNote = "1× opening",
            quranReference = "Quran 16:98",
            hadithReference = "Sahih Muslim 399; Sahih al-Bukhari 744"
        )
    }

    val qiyamDhikr = when (rakat) {
        1 -> "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ۝ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ ۝ الرَّحْمَٰنِ الرَّحِيمِ ۝ مَالِكِ يَوْمِ الدِّينِ ۝ إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ ۝ اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ ۝ صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ ۝ آمِينَ.\n" +
            "قُلْ هُوَ اللَّهُ أَحَدٌ ۝ اللَّهُ الصَّمَدُ ۝ لَمْ يَلِدْ وَلَمْ يُولَدْ ۝ وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ"
        2 -> "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ۝ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ ۝ الرَّحْمَٰنِ الرَّحِيمِ ۝ مَالِكِ يَوْمِ الدِّينِ ۝ إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ ۝ اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ ۝ صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ ۝ آمِينَ.\n" +
            "قُلْ أَعُوذُ بِرَبِّ النَّاسِ ۝ مَلِكِ النَّاسِ ۝ إِلَٰهِ النَّاسِ..."
        else -> "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ ۝ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ ۝ الرَّحْمَٰنِ الرَّحِيمِ ۝ مَالِكِ يَوْمِ الدِّينِ ۝ إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ ۝ اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ ۝ صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ ۝ آمِينَ"
    }

    steps += GuideStep(
        title = "Qiyam (Hand Placement & Recitation)",
        shortLabel = "Qiyam",
        arabicTitle = "القيام والقراءة",
        variations = listOf(
            PostureVariation(
                label = "Hand on Forearm",
                physicalInstruction = "Place right palm over the back of left hand, wrist, and forearm on center of chest.",
                hadithSource = "Sahih al-Bukhari 740"
            ),
            PostureVariation(
                label = "Hand on Wrist (Wada')",
                physicalInstruction = "Rest right palm directly over left hand/wrist on chest.",
                hadithSource = "Sahih Muslim 401"
            ),
            PostureVariation(
                label = "Grasp Wrist (Qabz)",
                physicalInstruction = "Clasp left wrist with right hand on chest.",
                hadithSource = "Sunan Abi Dawud 759"
            )
        ),
        arabicDhikr = qiyamDhikr,
        transliteration = if (rakat == 1) {
            "Al-Fatihah + Ameen + short Surah."
        } else if (rakat <= 2) {
            "Surah Al-Fatihah + Ameen + short Surah or Ayat."
        } else {
            "Surah Al-Fatihah + Ameen."
        },
        englishMeaning = if (rakat == 1) {
            "Recite Surah Al-Fatihah (a pillar of every rakat), say Ameen, then recite another Surah."
        } else if (rakat <= 2) {
            "Recite Surah Al-Fatihah (a pillar of every rakat), say Ameen, then recite another Surah."
        } else {
            "Recite Surah Al-Fatihah and say Ameen."
        },
        recitationCountOrNote = when {
            rakat == 1 -> "Thana + Fatihah + Surah"
            rakat <= 2 -> "Fatihah + Surah"
            else -> "Fatihah"
        },
        quranReference = "Quran 16:98, 1:1–7, 73:20",
        hadithReference = "Sunan Abi Dawud 776; Tirmidhi 243; Bukhari 756, 776, 780; Muslim 394"
    )

    steps += GuideStep(
        title = "Ruku (90° Bowing)",
        shortLabel = "Ruku",
        arabicTitle = "الركوع",
        variations = listOf(
            PostureVariation(
                label = "90° Bow",
                physicalInstruction = "Raise hands (Raf' al-Yadayn) saying \"Allahu Akbar\" while bowing. Keep back completely flat and horizontal (90°), head level with spine, arms straight, palms gripping knees with fingers spread apart.",
                hadithSource = "Sahih al-Bukhari 735, 828; Sahih Muslim 498, 772; Sunan Abi Dawud 870"
            )
        ),
        arabicDhikr = "سُبْحَانَ رَبِّيَ الْعَظِيمِ",
        transliteration = "Subhana Rabbiyal-'Azim",
        englishMeaning = "Glory be to my Lord, the Almighty.",
        recitationCountOrNote = "3× in Ruku",
        quranReference = "Quran 22:77, 2:43",
        hadithReference = "Sahih al-Bukhari 735, 828; Sahih Muslim 498, 772; Abi Dawud 870"
    )

    steps += GuideStep(
        title = "I'tidal (Rising from Ruku)",
        shortLabel = "I'tidal",
        arabicTitle = "الاعتدال",
        variations = listOf(
            PostureVariation(
                label = "Stand Upright",
                physicalInstruction = "Raise hands (Raf' al-Yadayn) while rising upright until every bone settles (tuma'ninah); place hands back on chest or at sides.",
                hadithSource = "Sahih al-Bukhari 735, 792, 799; Sahih Muslim 476"
            )
        ),
        arabicDhikr = "سَمِعَ اللَّهُ لِمَنْ حَمِدَهُ، رَبَّنَا وَلَكَ الْحَمْدُ حَمْدًا كَثِيرًا طَيِّبًا مُبَارَكًا فِيهِ",
        transliteration = "Sami'Allahu liman hamidah (while rising), then Rabbana wa lakal-hamd, hamdan kathiran tayyiban mubarakan fih.",
        englishMeaning = "Allah hears whoever praises Him. Our Lord, to You belongs all praise—abundant, pure, and blessed.",
        recitationCountOrNote = "1× rising & standing",
        quranReference = "Quran 17:111",
        hadithReference = "Sahih al-Bukhari 735, 792, 799; Sahih Muslim 476"
    )

    steps += GuideStep(
        title = "Sujud (Prostration on 7 Bones)",
        shortLabel = "Sujud",
        arabicTitle = "السجود",
        variations = listOf(
            PostureVariation(
                label = "Hands First",
                physicalInstruction = "Place hands on ground before knees while descending.",
                hadithSource = "Sunan Abi Dawud 840"
            ),
            PostureVariation(
                label = "Knees First",
                physicalInstruction = "Place knees on ground before hands while descending.",
                hadithSource = "Sunan Abi Dawud 838"
            )
        ),
        physicalInstruction = "Prostrate on 7 bones (forehead + nose, both palms, both knees, toes of both feet). Palms beside shoulders/ears with fingers joined pointing toward Qiblah; elbows raised off floor; arms away from sides; stomach clear of thighs; heels together with toes bent forward.",
        arabicDhikr = "سُبْحَانَ رَبِّيَ الْأَعْلَى",
        transliteration = "Subhana Rabbiyal-A'la",
        englishMeaning = "Glory be to my Lord, the Most High.",
        recitationCountOrNote = "3× + personal Dua",
        quranReference = "Quran 96:19, 22:77",
        hadithReference = "Sahih al-Bukhari 812; Sahih Muslim 482, 490, 491, 772"
    )

    steps += GuideStep(
        title = "Jalsa (Sitting Between Two Sujuds)",
        shortLabel = "Jalsa",
        arabicTitle = "الجلسة بين السجدتين",
        variations = listOf(
            PostureVariation(
                label = "Iftirash",
                physicalInstruction = "Sit on flattened left foot, right foot propped upright with toes toward Qiblah. Hands flat on thighs/knees, spine upright, calm stillness.",
                hadithSource = "Sahih al-Bukhari 828"
            ),
            PostureVariation(
                label = "Iq'a (On Heels)",
                physicalInstruction = "Sit back evenly on both upright heels briefly. Hands flat on thighs/knees, spine upright, calm stillness.",
                hadithSource = "Sahih Muslim 536"
            )
        ),
        arabicDhikr = "رَبِّ اغْفِرْ لِي، رَبِّ اغْفِرْ لِي",
        transliteration = "Rabbighfir li, Rabbighfir li",
        englishMeaning = "My Lord, forgive me; My Lord, forgive me.",
        recitationCountOrNote = "Between 2 Sujuds",
        quranReference = "Quran 71:10",
        hadithReference = "Sunan Abi Dawud 874; Sunan Ibn Majah 897; Sahih al-Bukhari 828"
    )

    steps += GuideStep(
        title = "Second Sujud & Jalsat al-Istirahah",
        shortLabel = "2nd Sujud",
        arabicTitle = "السجدة الثانية",
        variations = listOf(
            PostureVariation(
                label = "2nd Prostration",
                physicalInstruction = if (hasTashahhud) {
                    "Repeat Sujud on the 7 bones with calm stillness, then sit for Tashahhud."
                } else {
                    "Repeat Sujud (same 7 bones & dhikr). Before standing for the next rakat, pause briefly seated (Jalsat al-Istirahah), then rise saying \"Allahu Akbar\"."
                },
                hadithSource = "Sahih al-Bukhari 823, 824"
            )
        ),
        arabicDhikr = "سُبْحَانَ رَبِّيَ الْأَعْلَى",
        transliteration = "Subhana Rabbiyal-A'la",
        englishMeaning = "Glory be to my Lord, the Most High.",
        recitationCountOrNote = "3× in 2nd Sujud",
        quranReference = "Quran 96:19",
        hadithReference = "Sahih al-Bukhari 823, 824"
    )

    if (hasTashahhud) {
        val tashahhudDhikr = if (isFinalRakah) {
            "التَّحِيَّاتُ لِلَّهِ وَالصَّلَوَاتُ وَالطَّيِّبَاتُ، السَّلَامُ عَلَيْكَ أَيُّهَا النَّبِيُّ وَرَحْمَةُ اللَّهِ وَبَرَكَاتُهُ، السَّلَامُ عَلَيْنَا وَعَلَى عِبَادِ اللَّهِ الصَّالِحِينَ، أَشْهَدُ أَنْ لَا إِلَٰهَ إِلَّا اللَّهُ وَأَشْهَدُ أَنَّ مُحَمَّدًا عَبْدُهُ وَرَسُولُهُ.\n" +
                "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ كَمَا صَلَّيْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ إِنَّكَ حَمِيدٌ مَجِيدٌ، اللَّهُمَّ بَارِكْ عَلَى مُحَمَّدٍ وَعَلَى آلِ مُحَمَّدٍ كَمَا بَارَكْتَ عَلَى إِبْرَاهِيمَ وَعَلَى آلِ إِبْرَاهِيمَ إِنَّكَ حَمِيدٌ مَجِيدٌ.\n" +
                "اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنْ عَذَابِ جَهَنَّمَ وَمِنْ عَذَابِ الْقَبْرِ وَمِنْ فِتْنَةِ الْمَحْيَا وَالْمَمَاتِ وَمِنْ شَرِّ فِتْنَةِ الْمَسِيحِ الدَّجَّالِ"
        } else {
            "التَّحِيَّاتُ لِلَّهِ وَالصَّلَوَاتُ وَالطَّيِّبَاتُ، السَّلَامُ عَلَيْكَ أَيُّهَا النَّبِيُّ وَرَحْمَةُ اللَّهِ وَبَرَكَاتُهُ، السَّلَامُ عَلَيْنَا وَعَلَى عِبَادِ اللَّهِ الصَّالِحِينَ، أَشْهَدُ أَنْ لَا إِلَٰهَ إِلَّا اللَّهُ وَأَشْهَدُ أَنَّ مُحَمَّدًا عَبْدُهُ وَرَسُولُهُ"
        }

        steps += GuideStep(
            title = if (isFinalRakah) "Tashahhud (Final Sitting)" else "Tashahhud (1st Sitting)",
            shortLabel = "Tashahhud",
            arabicTitle = "التشهد",
            variations = listOf(
                PostureVariation(
                    label = "1st Sit (Iftirash)",
                    physicalInstruction = "Sit on flattened left foot with right foot propped upright toward Qiblah.",
                    hadithSource = "Sahih al-Bukhari 828"
                ),
                PostureVariation(
                    label = "Final Sit (Tawarruk)",
                    physicalInstruction = "Left hip on mat, left foot tucked under right shin, right foot upright.",
                    hadithSource = "Sahih al-Bukhari 828"
                ),
                PostureVariation(
                    label = "Finger Grip: Circle (53)",
                    physicalInstruction = "Thumb & middle finger form a ring, pinky & ring folded, index points & moves.",
                    hadithSource = "Sahih Muslim 580"
                ),
                PostureVariation(
                    label = "Finger Grip: Fist",
                    physicalInstruction = "All other fingers closed in fist, index points toward Qiblah.",
                    hadithSource = "Sahih Muslim 579"
                )
            ),
            defaultVariationIndex = if (isFinalRakah && totalRakats >= 3) 1 else 0,
            arabicDhikr = tashahhudDhikr,
            transliteration = if (isFinalRakah) {
                "At-tahiyyatu lillahi was-salawatu wat-tayyibat... Allahumma salli 'ala Muhammad... Allahumma inni a'udhu bika min 'adhabi jahannam..."
            } else {
                "At-tahiyyatu lillahi was-salawatu wat-tayyibat..."
            },
            englishMeaning = if (isFinalRakah) {
                "At-Tahiyyat, Durood Ibrahim (sending blessings upon Prophet Muhammad and his family), and the Dua of Refuge before Taslim."
            } else {
                "At-Tahiyyat in the middle sitting, then rise saying Allahu Akbar for the next rakat."
            },
            recitationCountOrNote = if (isFinalRakah) "Tahiyyat + Durood + Dua" else "Middle Tashahhud",
            quranReference = if (isFinalRakah) "Quran 33:56" else "",
            hadithReference = if (isFinalRakah) {
                "Sahih al-Bukhari 831, 3370; Sahih Muslim 402, 406, 588"
            } else {
                "Sahih al-Bukhari 828, 831; Sahih Muslim 402"
            }
        )
    }

    if (isFinalRakah) {
        steps += GuideStep(
            title = "Taslim (Concluding the Prayer)",
            shortLabel = "Taslim",
            arabicTitle = "التسليم",
            variations = listOf(
                PostureVariation(
                    label = "Right & Left",
                    physicalInstruction = "Turn head to the right so cheek is visible from behind, then turn head to the left; keep hands still on thighs.",
                    hadithSource = "Sahih Muslim 582; Sunan Abi Dawud 996"
                )
            ),
            arabicDhikr = "السَّلَامُ عَلَيْكُمْ وَرَحْمَةُ اللَّهِ",
            transliteration = "As-salamu 'alaykum wa rahmatullah",
            englishMeaning = "Peace and mercy of Allah be upon you.",
            recitationCountOrNote = "On each side",
            hadithReference = "Sahih Muslim 582; Sunan Abi Dawud 996"
        )
    }

    return steps.mapIndexed { index, item ->
        item.copy(title = "${index + 1}. ${item.title}")
    }
}

private val afterSalahSteps = listOf(
    GuideStep(
        title = "1. Istighfar & Al-Salam",
        shortLabel = "Istighfar",
        arabicTitle = "الاستغفار",
        physicalInstruction = "Recite immediately after concluding the prayer with Taslim.",
        arabicDhikr = "أَسْتَغْفِرُ اللَّهَ، أَسْتَغْفِرُ اللَّهَ، أَسْتَغْفِرُ اللَّهَ. اللَّهُمَّ أَنْتَ السَّلَامُ وَمِنْكَ السَّلَامُ تَبَارَكْتَ يَا ذَا الْجَلَالِ وَالْإِكْرَامِ",
        transliteration = "Astaghfirullah (3×), then Allahumma Antas-Salam wa minkas-salam, tabarakta ya Dhal-Jalali wal-Ikram.",
        englishMeaning = "I seek Allah's forgiveness (3×). O Allah, You are Peace and from You comes peace. Blessed are You, O Possessor of Majesty and Honor.",
        recitationCountOrNote = "3× Istighfar + 1× Dua",
        quranReference = "Quran 4:106",
        hadithReference = "Sahih Muslim 591",
        grading = "Sahih",
        repeatCount = 3
    ),
    GuideStep(
        title = "2. Ayat al-Kursi",
        shortLabel = "Kursi",
        arabicTitle = "آية الكرسي",
        physicalInstruction = "Recite Ayat al-Kursi after each obligatory prayer.",
        arabicDhikr = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَنْ ذَا الَّذِي يَشْفَعُ عِنْدَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ",
        transliteration = "Allahu la ilaha illa Huwal-Hayyul-Qayyum...",
        englishMeaning = "Allah — there is no deity except Him, the Ever-Living, the Sustainer of all existence...",
        recitationCountOrNote = "1× after each prayer",
        quranReference = "Quran 2:255",
        hadithReference = "Sunan al-Kubra lil-Nasa'i 9928",
        grading = "Sahih (al-Albani)",
        repeatCount = 1
    ),
    GuideStep(
        title = "3. Tasbih Fatimah",
        shortLabel = "Tasbih",
        arabicTitle = "التسبيح",
        physicalInstruction = "Count each phrase calmly using your fingers or the interactive counter below.",
        arabicDhikr = "سُبْحَانَ اللَّهِ، الْحَمْدُ لِلَّهِ، اللَّهُ أَكْبَرُ",
        transliteration = "SubhanAllah (33×), Alhamdulillah (33×), Allahu Akbar (34×).",
        englishMeaning = "Glory be to Allah (33×), All praise is for Allah (33×), Allah is the Greatest (34×).",
        recitationCountOrNote = "33× + 33× + 34×",
        quranReference = "Quran 33:41–42",
        hadithReference = "Sahih Muslim 597",
        grading = "Sahih",
        repeatCount = 33,
        hasTasbihCounter = true
    )
)
