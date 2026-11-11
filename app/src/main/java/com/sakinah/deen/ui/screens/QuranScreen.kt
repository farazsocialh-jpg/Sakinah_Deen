package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.data.model.AyahModel
import com.sakinah.deen.data.model.SurahModel
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.MintGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranScreen(
    initialSurahNumber: Int = 1,
    initialAyahNumber: Int = 1,
    modifier: Modifier = Modifier
) {
    val surahs = remember {
        listOf(
            SurahModel(
                number = 1,
                nameEnglish = "Al-Fatihah",
                nameArabic = "الفاتحة",
                englishMeaning = "The Opening",
                revelationType = "Makki",
                ayahCount = 7,
                juzNumber = 1,
                ayahs = listOf(
                    AyahModel(
                        numberInSurah = 1,
                        arabicText = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                        transliteration = "Bismillāhir-Raḥmānir-Raḥīm",
                        translation = "In the name of Allah, the Entirely Merciful, the Especially Merciful.",
                        tafseerIbnKathir = "The Basmalah is an opening of sanctification and blessing before every noble action. The scholars of Ahl al-Sunnah agree it is recited to seek divine aid.",
                        tafseerTabari = "Tabari reports from Ibn Abbas that Allah is the possessor of divine majesty over all creation, Ar-Rahman possesses boundless mercy, and Ar-Rahim confers specific mercy upon believers.",
                        tafseerQurtubi = "Qurtubi discusses the virtue of commencing all recitation with the Basmalah.",
                        tafseerSadi = "Begin every action seeking grace through all of Allah's Beautiful Names.",
                        tafseerMaarifulQuran = "Linguistic clarity on how divine mercy preceded wrath in creation.",
                        hadithProofQuote = "The Prophet (ﷺ) said: Any significant deed that does not begin with Bismillah is clipped of blessing."
                    ),
                    AyahModel(
                        numberInSurah = 2,
                        arabicText = "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
                        transliteration = "Al-ḥamdu lillāhi Rabbil-‘ālamīn",
                        translation = "All praise is due to Allah, Lord of all the worlds.",
                        tafseerIbnKathir = "Al-Hamd is comprehensive gratitude with love and veneration belonging inherently to Allah alone.",
                        tafseerTabari = "Rabb signifies the Master who owns, nurtures, and governs all created realms.",
                        tafseerQurtubi = "Gratitude is the head of all worship.",
                        tafseerSadi = "Praise belongs to Him for His perfection of attributes and torrential blessings.",
                        tafseerMaarifulQuran = "Recognition of mercy naturally compels the heart to overflow in praise.",
                        hadithProofQuote = "Hadith Qudsi: I have divided prayer between Myself and My servant into two halves."
                    ),
                    AyahModel(
                        numberInSurah = 3,
                        arabicText = "الرَّحْمَٰنِ الرَّحِيمِ",
                        transliteration = "Ar-Raḥmānir-Raḥīm",
                        translation = "The Entirely Merciful, the Especially Merciful.",
                        tafseerIbnKathir = "Balances awe with intimate hope so the servant draws near with longing.",
                        tafseerTabari = "Divine compassion embraces all realms in sustenance.",
                        tafseerQurtubi = "Ar-Rahman is non-transferable to any creation.",
                        tafseerSadi = "All grace reaching creation is the fruit of His Mercy.",
                        tafseerMaarifulQuran = "Reinforces optimism for all believers."
                    ),
                    AyahModel(
                        numberInSurah = 4,
                        arabicText = "مَالِكِ يَوْمِ الدِّينِ",
                        transliteration = "Māliki Yawmid-Dīn",
                        translation = "Sovereign of the Day of Recompense.",
                        tafseerIbnKathir = "On that Day all earthly kings are stripped of power.",
                        tafseerTabari = "Yawm al-Din is the Day of Final Accounting and Retribution.",
                        tafseerQurtubi = "Recited as Maalik (Owner) and Malik (King) in Mutawatir Qira'at.",
                        tafseerSadi = "Contemplating this Ayah awakens Taqwa.",
                        tafseerMaarifulQuran = "Reminds us that justice will rectify all temporal oppressions."
                    ),
                    AyahModel(
                        numberInSurah = 5,
                        arabicText = "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ",
                        transliteration = "Iyyāka na‘budu wa iyyāka nasta‘īn",
                        translation = "It is You alone we worship, and You alone we ask for help.",
                        tafseerIbnKathir = "The pivot of the entire Quran: Pure Tawhid with exclusive pronouns.",
                        tafseerTabari = "We submit our limbs in obedience to You alone.",
                        tafseerQurtubi = "Ibadah cures pride; Isti'anah cures self-reliance.",
                        tafseerSadi = "Worship before aid highlights that pleasing Him is the ultimate aim.",
                        tafseerMaarifulQuran = "The foundation of Ibn al-Qayyim's Madarij al-Salikin."
                    ),
                    AyahModel(
                        numberInSurah = 6,
                        arabicText = "اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ",
                        transliteration = "Ihdinaṣ-ṣirāṭal-mustaqīm",
                        translation = "Guide us to the straight path.",
                        tafseerIbnKathir = "The path of Islam and the authentic Prophetic Sunnah without innovation.",
                        tafseerTabari = "Guidance in both knowing the truth and having the strength to tread it.",
                        tafseerQurtubi = "Righteous servants continuously seek steadfastness.",
                        tafseerSadi = "A path that is wide, illuminated, and clear.",
                        tafseerMaarifulQuran = "The greatest blessing is steadfastness on the prophetic methodology."
                    ),
                    AyahModel(
                        numberInSurah = 7,
                        arabicText = "صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ",
                        transliteration = "Ṣirāṭalladhīna an‘amta ‘alayhim, ghayril-maghḍūbi ‘alayhim walāḍ-ḍāllīn",
                        translation = "The path of those upon whom You have bestowed favor, not of those who have evoked anger or of those who are astray.",
                        tafseerIbnKathir = "The favored ones are the Prophets, the Truthful, the Martyrs, and the Righteous.",
                        tafseerTabari = "Warning against intellectual arrogance and ignorant spiritual zeal.",
                        tafseerQurtubi = "Pronounce Ameen at the conclusion.",
                        tafseerSadi = "The middle path between negligence and extremism.",
                        tafseerMaarifulQuran = "A heartfelt collective prayer for the Ummah.",
                        hadithProofQuote = "Say Ameen when the Imam says Ameen. (Al-Bukhari 780)"
                    )
                )
            )
        )
    }

    var selectedSurah by remember(initialSurahNumber) {
        mutableStateOf(surahs.find { it.number == initialSurahNumber } ?: surahs[0])
    }
    var selectedAyah by remember(initialAyahNumber, selectedSurah) {
        mutableStateOf(selectedSurah.ayahs.find { it.numberInSurah == initialAyahNumber } ?: selectedSurah.ayahs.firstOrNull())
    }
    var isStudyMode by remember { mutableStateOf(true) }
    var isTafseerSheetOpen by remember { mutableStateOf(false) }
    var selectedScholar by remember { mutableStateOf("Ibn Kathir") }
    var hifzOverlayActive by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top Navigation & Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedSurah.number}. ${selectedSurah.nameEnglish}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = EmeraldDeep
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${selectedSurah.nameArabic})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Layout toggle: Mus-haf vs Verse-by-Verse
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(modifier = Modifier.padding(2.dp)) {
                    Button(
                        onClick = { isStudyMode = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStudyMode) EmeraldDeep else Color.Transparent,
                            contentColor = if (isStudyMode) Color.White else EmeraldDeep
                        )
                    ) {
                        Text("Study", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { isStudyMode = false },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isStudyMode) EmeraldDeep else Color.Transparent,
                            contentColor = if (!isStudyMode) Color.White else EmeraldDeep
                        )
                    ) {
                        Text("Mus-haf", fontSize = 11.sp)
                    }
                }
            }
        }

        // Hifz Overlay toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hifz Memorization Marker Canvas",
                fontSize = 12.sp,
                color = EmeraldDeep,
                fontWeight = FontWeight.SemiBold
            )
            Button(
                onClick = { hifzOverlayActive = !hifzOverlayActive },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hifzOverlayActive) MintGreen else Color.LightGray.copy(alpha = 0.3f)
                )
            ) {
                Text(if (hifzOverlayActive) "Active" else "Off", fontSize = 11.sp, color = EmeraldDeep)
            }
        }

        // 2. Reading Canvas (Verse by Verse Study Mode)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(selectedSurah.ayahs) { ayah ->
                val isSelected = selectedAyah?.numberInSurah == ayah.numberInSurah

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedAyah = ayah
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MintGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MintGreen else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MintGreen.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "Ayah ${ayah.numberInSurah}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldDeep
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    selectedAyah = ayah
                                    isTafseerSheetOpen = true
                                }
                            ) {
                                Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tafseer", fontSize = 11.sp)
                            }
                        }

                        // Uthmani Arabic Text
                        Text(
                            text = "${ayah.arabicText} ۝${ayah.numberInSurah}",
                            fontFamily = FontFamily.Serif,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldDeep,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = ayah.transliteration,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Text(
                            text = ayah.translation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    // 3. Expandable Tafseer Bottom Sheet
    if (isTafseerSheetOpen && selectedAyah != null) {
        ModalBottomSheet(
            onDismissRequest = { isTafseerSheetOpen = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sunni Tafseer Studio · Ayah ${selectedAyah!!.numberInSurah}",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = EmeraldDeep
                    )
                    IconButton(onClick = { isTafseerSheetOpen = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Scholar selection tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Ibn Kathir", "Al-Tabari", "Al-Qurtubi", "Al-Sa'di", "Ma'ariful Quran").forEach { scholar ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedScholar == scholar) EmeraldDeep else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (selectedScholar == scholar) EmeraldDeep else MaterialTheme.colorScheme.outline),
                            modifier = Modifier.clickable { selectedScholar = scholar }
                        ) {
                            Text(
                                text = scholar,
                                color = if (selectedScholar == scholar) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Classical Exegesis Content
                Text(
                    text = when (selectedScholar) {
                        "Al-Tabari" -> selectedAyah!!.tafseerTabari
                        "Al-Qurtubi" -> selectedAyah!!.tafseerQurtubi
                        "Al-Sa'di" -> selectedAyah!!.tafseerSadi
                        "Ma'ariful Quran" -> selectedAyah!!.tafseerMaarifulQuran
                        else -> selectedAyah!!.tafseerIbnKathir
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                )

                // Athar / Hadith Proof Quote Box
                if (selectedAyah!!.hadithProofQuote != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MintGreen.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, MintGreen.copy(alpha = 0.3f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Icon(Icons.Default.FormatQuote, contentDescription = null, tint = EmeraldDeep)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedAyah!!.hadithProofQuote!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldDeep
                            )
                        }
                    }
                }
            }
        }
    }
}
