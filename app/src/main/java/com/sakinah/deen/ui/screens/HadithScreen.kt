package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.sakinah.deen.data.model.HadithModel
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.MintGreen
import com.sakinah.deen.ui.theme.SahihGreen

@Composable
fun HadithScreen(
    initialBookKey: String = "all",
    initialSearchQuery: String = "",
    modifier: Modifier = Modifier
) {
    val books = remember {
        listOf(
            Triple("bukhari", "Sahih al-Bukhari", "7,563 Hadiths"),
            Triple("muslim", "Sahih Muslim", "7,500 Hadiths"),
            Triple("abudawud", "Sunan Abi Dawud", "5,274 Hadiths"),
            Triple("tirmidhi", "Jami‘ at-Tirmidhi", "3,956 Hadiths"),
            Triple("nasai", "Sunan an-Nasa'i", "5,760 Hadiths"),
            Triple("ibnmajah", "Sunan Ibn Majah", "4,341 Hadiths"),
            Triple("muwatta", "Muwatta Malik", "1,858 Hadiths"),
            Triple("ahmad", "Musnad Ahmad", "27,647 Hadiths"),
            Triple("darimi", "Sunan al-Darimi", "3,500 Hadiths")
        )
    }

    var selectedBookKey by remember(initialBookKey) { mutableStateOf(initialBookKey) }
    var searchQuery by remember(initialSearchQuery) { mutableStateOf(initialSearchQuery) }
    var filterGrade by remember { mutableStateOf("all") }

    val hadiths = remember {
        listOf(
            HadithModel(
                id = "bukhari-1",
                bookKey = "bukhari",
                bookTitle = "Sahih al-Bukhari",
                bookTotalCount = 7563,
                hadithNumber = 1,
                chapterTitle = "Book of Revelation (Bad' al-Wahy)",
                grade = "SAHIH",
                gradingScholar = "Muttafaq ‘Alayh",
                arabicText = "إِنَّمَا الأَعْمَالُ بِالنِّيَّاتِ، وَإِنَّمَا لِكُلِّ امْرِئٍ مَا نَوَى، فَمَنْ كَانَتْ هِجْرَتُهُ إِلَى دُنْيَا يُصِيبُهَا أَوْ إِلَى امْرَأَةٍ يَنْكِحُهَا فَهِجْرَتُهُ إِلَى مَا هَاجَرَ إِلَيْهِ.",
                sanad = "Humaidi narrated from Sufyan, from Yahya ibn Sa'id al-Ansari, from Umar ibn al-Khattab (r.a):",
                matnTranslation = "Actions are judged by intentions, and every person will get what they intended. So whoever emigrated for worldly gain or to marry, his emigration is for that to which he emigrated.",
                bookmarked = true
            ),
            HadithModel(
                id = "bukhari-5063",
                bookKey = "bukhari",
                bookTitle = "Sahih al-Bukhari",
                bookTotalCount = 7563,
                hadithNumber = 5063,
                chapterTitle = "Book of Virtues of the Quran",
                grade = "SAHIH",
                gradingScholar = "Imam al-Bukhari",
                arabicText = "خَيْرُكُمْ مَنْ تَعَلَّمَ الْقُرْآنَ وَعَلَّمَهُ.",
                sanad = "Hajjaj narrated from Shu'bah, from Uthman ibn Affan (r.a):",
                matnTranslation = "The best among you are those who learn the Quran and teach it to others.",
                bookmarked = false
            ),
            HadithModel(
                id = "muslim-140",
                bookKey = "muslim",
                bookTitle = "Sahih Muslim",
                bookTotalCount = 7500,
                hadithNumber = 140,
                chapterTitle = "Book of Faith (Kitab al-Iman)",
                grade = "SAHIH",
                gradingScholar = "Imam Muslim",
                arabicText = "بُنِيَ الإِسْلاَمُ عَلَى خَمْسٍ: شَهَادَةِ أَنْ لاَ إِلَهَ إِلاَّ اللَّهُ وَأَنَّ مُحَمَّدًا عَبْدُهُ وَرَسُولُهُ، وَإِقَامِ الصَّلاَةِ، وَإِيتَاءِ الزَّكَاةِ، وَحَجِّ الْبَيْتِ، وَصَوْمِ رَمَضَانَ.",
                sanad = "Abdullah ibn Umar (r.a) narrated from the Messenger of Allah (ﷺ):",
                matnTranslation = "Islam is founded upon five pillars: testifying there is no deity worthy of worship except Allah and Muhammad is His servant and messenger, establishing prayer, giving Zakat, Hajj, and fasting Ramadan.",
                bookmarked = true
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Text(
            text = "Kutub al-Tis‘ah Encyclopedia",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = EmeraldDeep
        )

        // 1. Canonical Books Horizontal Carousel
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (selectedBookKey == "all") EmeraldDeep else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (selectedBookKey == "all") EmeraldDeep else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.clickable { selectedBookKey = "all" }
                ) {
                    Text(
                        text = "All Books",
                        color = if (selectedBookKey == "all") Color.White else EmeraldDeep,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
            items(books) { (key, title, count) ->
                val isSelected = selectedBookKey == key
                Card(
                    modifier = Modifier.clickable { selectedBookKey = key },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MintGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, if (isSelected) MintGreen else MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldDeep)
                        Text(text = count, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        }

        // 2. Full-Text Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search Hadith by Arabic or English meaning...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            shape = RoundedCornerShape(18.dp),
            singleLine = true
        )

        // 3. Hadith Display Cards
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(hadiths) { hadith ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Top Row: Book Name, Chapter, Hadith Number & Grading Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${hadith.bookTitle} #${hadith.hadithNumber}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = EmeraldDeep
                                )
                                Text(
                                    text = hadith.chapterTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            // Authenticity Badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SahihGreen.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${hadith.grade} · ${hadith.gradingScholar}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SahihGreen
                                )
                            }
                        }

                        // Sanad
                        Text(
                            text = "Sanad: ${hadith.sanad}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        // Arabic Tashkeel Text
                        Text(
                            text = hadith.arabicText,
                            fontFamily = FontFamily.Serif,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldDeep,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Matn Translation
                        Text(
                            text = "\"${hadith.matnTranslation}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Action Bar: Bookmark, Copy, Share
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = { /* Copy */ }) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
                            }
                            IconButton(onClick = { /* Bookmark */ }) {
                                Icon(
                                    imageVector = if (hadith.bookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Save",
                                    tint = EmeraldDeep
                                )
                            }
                            IconButton(onClick = { /* Share */ }) {
                                Icon(Icons.Outlined.Share, contentDescription = "Share")
                            }
                        }
                    }
                }
            }
        }
    }
}
