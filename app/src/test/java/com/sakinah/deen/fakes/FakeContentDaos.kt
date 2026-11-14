package com.sakinah.deen.fakes

import com.sakinah.deen.data.database.dao.AdhkarDao
import com.sakinah.deen.data.database.dao.HadithDao
import com.sakinah.deen.data.database.dao.QuranDao
import com.sakinah.deen.data.database.dao.TafsirDao
import com.sakinah.deen.data.database.entity.AdhkarEntity
import com.sakinah.deen.data.database.entity.HadithEntryEntity
import com.sakinah.deen.data.database.entity.QuranAyahEntity
import com.sakinah.deen.data.database.entity.QuranTranslationEntity
import com.sakinah.deen.data.database.entity.SurahMetadataEntity
import com.sakinah.deen.data.database.entity.TafsirEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * In-memory stand-ins for the Room DAOs, enough to prove the offline seeding path
 * without SQLCipher/Robolectric: the seeding logic only ever inserts and counts.
 */
class FakeQuranDao : QuranDao {
    val ayahs = linkedMapOf<Pair<Int, Int>, QuranAyahEntity>()
    val translations = linkedMapOf<Triple<Int, Int, Int>, QuranTranslationEntity>()
    val surahs = linkedMapOf<Int, SurahMetadataEntity>()
    var ayahWrites = 0
    var translationWrites = 0

    override fun observeSurah(surahNumber: Int): Flow<List<QuranAyahEntity>> = flow {
        emit(
            ayahs.values.filter { it.surahNumber == surahNumber }.sortedBy { it.ayahNumber }
        )
    }

    override fun observeAllAyahs(): Flow<List<QuranAyahEntity>> = flow {
        emit(
            ayahs.values.sortedWith(compareBy({ it.surahNumber }, { it.ayahNumber }))
        )
    }

    override fun observeTranslations(surahNumber: Int, translationId: Int): Flow<List<QuranTranslationEntity>> = flow {
        emit(
            translations.values
                .filter { it.surahNumber == surahNumber && it.translationId == translationId }
                .sortedBy { it.ayahNumber }
        )
    }

    override suspend fun upsertTranslations(translations: List<QuranTranslationEntity>) {
        this.translations += translations.associateBy { Triple(it.surahNumber, it.ayahNumber, it.translationId) }
        translationWrites += translations.size
    }

    override suspend fun deleteTranslations(surahNumber: Int, translationId: Int) {
        translations.keys.removeAll { it.first == surahNumber && it.third == translationId }
    }

    override fun searchAyahs(query: String, limit: Int): Flow<List<QuranAyahEntity>> = flow {
        emit(
            ayahs.values
                .filter { it.textUthmani.contains(query, true) || it.translation.orEmpty().contains(query, true) }
                .sortedWith(compareBy({ it.surahNumber }, { it.ayahNumber }))
                .take(limit)
        )
    }

    override suspend fun getAyah(surahNumber: Int, ayahNumber: Int): QuranAyahEntity? =
        ayahs[surahNumber to ayahNumber]

    override suspend fun countAyahs(surahNumber: Int): Int =
        ayahs.values.count { it.surahNumber == surahNumber }

    override suspend fun countSurahs(): Int = surahs.size

    override fun observeSurahs(): Flow<List<SurahMetadataEntity>> =
        flow { emit(surahs.values.sortedBy { it.surahNumber }) }

    override suspend fun upsertAyahs(ayahs: List<QuranAyahEntity>) {
        this.ayahs += ayahs.associateBy { it.surahNumber to it.ayahNumber }
        ayahWrites += ayahs.size
    }

    override suspend fun upsertSurahs(surahs: List<SurahMetadataEntity>) {
        this.surahs += surahs.associateBy { it.surahNumber }
    }

    override suspend fun deleteSurah(surahNumber: Int) {
        ayahs.keys.removeAll { it.first == surahNumber }
        surahs.remove(surahNumber)
    }
}

class FakeHadithDao : HadithDao {
    val entries = linkedMapOf<Triple<String, String, Int>, HadithEntryEntity>()
    var writes = 0

    override fun observeHadiths(bookKey: String, languageCode: String, limit: Int, offset: Int): Flow<List<HadithEntryEntity>> =
        flow {
            emit(
                entries.values.filter { it.bookKey == bookKey && it.languageCode == languageCode }
                    .sortedBy { it.hadithNumber }
                    .drop(offset)
                    .take(limit)
            )
        }

    override fun observeHadithsBySection(bookKey: String, languageCode: String, sectionNumber: Int, limit: Int, offset: Int): Flow<List<HadithEntryEntity>> =
        flow {
            emit(
                entries.values.filter { it.bookKey == bookKey && it.languageCode == languageCode && it.sectionNumber == sectionNumber }
                    .sortedBy { it.hadithNumber }
                    .drop(offset)
                    .take(limit)
            )
        }

    override fun observeAllHadiths(limit: Int, offset: Int): Flow<List<HadithEntryEntity>> = flow {
        emit(
            entries.values.sortedWith(compareBy({ it.bookKey }, { it.languageCode }, { it.hadithNumber })).drop(offset).take(limit)
        )
    }

    override fun observeAllHadithsByLanguage(
        languageCode: String,
        limit: Int,
        offset: Int
    ): Flow<List<HadithEntryEntity>> = flow {
        emit(
            entries.values
                .filter { it.languageCode == languageCode }
                .sortedWith(compareBy({ it.bookKey }, { it.hadithNumber }))
                .drop(offset)
                .take(limit)
        )
    }

    override fun searchHadiths(query: String, limit: Int): Flow<List<HadithEntryEntity>> = flow {
        emit(
            entries.values
                .filter {
                    it.englishText.contains(query, true) ||
                        it.arabicText.contains(query, true) ||
                        it.bookTitle.contains(query, true)
                }
                .take(limit)
        )
    }

    override suspend fun getHadith(bookKey: String, languageCode: String, hadithNumber: Int): HadithEntryEntity? =
        entries[Triple(bookKey, languageCode, hadithNumber)]

    override suspend fun countForBook(bookKey: String, languageCode: String): Int =
        entries.keys.count { it.first == bookKey && it.second == languageCode }

    override suspend fun upsertAll(entries: List<HadithEntryEntity>) {
        this.entries += entries.associateBy { Triple(it.bookKey, it.languageCode, it.hadithNumber) }
        writes += entries.size
    }

    override suspend fun clearBook(bookKey: String, languageCode: String) {
        entries.keys.removeAll { it.first == bookKey && it.second == languageCode }
    }
}

class FakeAdhkarDao : AdhkarDao {
    val entries = linkedMapOf<String, AdhkarEntity>()
    var writes = 0

    override fun observeCategory(categoryKey: String): Flow<List<AdhkarEntity>> = flow {
        emit(
            entries.values.filter { it.categoryKey == categoryKey }.sortedBy { it.orderIndex }
        )
    }

    override suspend fun count(): Int = entries.size

    override suspend fun upsertAll(entries: List<AdhkarEntity>) {
        this.entries += entries.associateBy { it.id }
        writes += entries.size
    }
}

class FakeTafsirDao : TafsirDao {
    val entries = mutableListOf<TafsirEntryEntity>()

    override suspend fun getTafsir(
        sourceKey: String,
        surahNumber: Int,
        ayahNumber: Int
    ): TafsirEntryEntity? = entries.firstOrNull {
        it.sourceKey == sourceKey && it.surahNumber == surahNumber && it.ayahNumber == ayahNumber
    }

    override suspend fun countForSurah(sourceKey: String, surahNumber: Int): Int = entries.count {
        it.sourceKey == sourceKey && it.surahNumber == surahNumber
    }

    override suspend fun countForSource(sourceKey: String): Int = entries.count {
        it.sourceKey == sourceKey
    }

    override suspend fun upsertAll(entries: List<TafsirEntryEntity>) {
        this.entries += entries
    }

    override suspend fun deleteAll() {
        entries.clear()
    }
}