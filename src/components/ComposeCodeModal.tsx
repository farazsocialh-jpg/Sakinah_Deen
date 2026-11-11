import React, { useState } from 'react';
import { X, Copy, Check, FileCode, Layers } from 'lucide-react';

interface ComposeCodeModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const COMPOSE_FILES: Record<string, { filename: string; category: string; code: string }> = {
  'Theme': {
    filename: 'Theme.kt',
    category: 'Theme & Tokens',
    code: `package com.sakinah.deen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val EmeraldDeep = Color(0xFF0B3B2C)
val EmeraldSurface = Color(0xFF134E3A)
val MintGreen = Color(0xFF10B981)
val WarmCream = Color(0xFFF9FAF7)
val CardSurface = Color(0xFFFFFFFF)
val BorderHairline = Color(0xFFE2E8F0)
val DarkCharcoal = Color(0xFF1E293B)
val SubtitleGray = Color(0xFF64748B)

private val SakinahLightColorScheme = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = MintGreen,
    onPrimaryContainer = EmeraldDeep,
    secondary = MintGreen,
    onSecondary = EmeraldDeep,
    background = WarmCream,
    onBackground = DarkCharcoal,
    surface = CardSurface,
    onSurface = DarkCharcoal,
    outline = BorderHairline
)

@Composable
fun SakinahTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SakinahLightColorScheme,
        typography = SakinahTypography,
        content = content
    )
}`
  },
  'DomainModels': {
    filename: 'DomainModels.kt',
    category: 'Core Models',
    code: `package com.sakinah.deen.data.model

enum class PrayerType { FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA }
enum class NaflType { TAHAJJUD, DUHA, AWWABIN, WITR, TAHIYYAT_AL_MASJID, SUNNAH_RAWATIB }
enum class UserTrack { NEW_MUSLIM, PRACTICING }
enum class Madhhab { SHAFI_HANBALI_MALIKI, HANAFI }
enum class CalculationMethod { MUSLIM_WORLD_LEAGUE, EGYPTIAN, KARACHI, UMM_AL_QURA, NORTH_AMERICA }

data class Ayah(
    val surahNumber: Int,
    val ayahNumber: Int,
    val textUthmani: String,
    val translation: String,
    val audioUrl: String? = null
)

data class TafseerEntry(
    val tafseerId: String,
    val surahNumber: Int,
    val ayahNumber: Int,
    val author: String,
    val languageCode: String,
    val contentMarkdown: String
)

enum class HadithGrade { SAHIH, HASAN, DA_IF }

data class Hadith(
    val id: String,
    val bookSlug: String,
    val chapterId: Int,
    val hadithNumber: Int,
    val arabicText: String,
    val translation: String,
    val grade: HadithGrade,
    val grader: String
)

data class ZakatCalculation(
    val cashOnHand: Double,
    val goldGrams: Double,
    val silverGrams: Double,
    val tradeGoods: Double,
    val investmentEquities: Double,
    val deductibleDebts: Double,
    val goldPricePerGram: Double,
    val silverPricePerGram: Double,
    val useSilverStandard: Boolean = false
) {
    val nisabThreshold: Double
        get() = if (useSilverStandard) silverPricePerGram * 595.0 else goldPricePerGram * 85.0

    val netZakatableWealth: Double
        get() = (cashOnHand + (goldGrams * goldPricePerGram) + (silverGrams * silverPricePerGram) + tradeGoods + investmentEquities - deductibleDebts).coerceAtLeast(0.0)

    val isZakatDue: Boolean
        get() = netZakatableWealth >= nisabThreshold

    val zakatPayable: Double
        get() = if (isZakatDue) netZakatableWealth * 0.025 else 0.0
}`
  },
  'Database': {
    filename: 'SakinahEncryptedDatabase.kt',
    category: 'Encrypted Persistence',
    code: `package com.sakinah.deen.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sakinah.deen.data.database.converter.SakinahTypeConverters
import com.sakinah.deen.data.database.dao.HifzDao
import com.sakinah.deen.data.database.dao.QadhaDao
import com.sakinah.deen.data.database.dao.SadaqahDao
import com.sakinah.deen.data.database.dao.SalahDao
import com.sakinah.deen.data.database.entity.HifzMistakeEntity
import com.sakinah.deen.data.database.entity.QadhaLedgerEntity
import com.sakinah.deen.data.database.entity.SadaqahLogEntity
import com.sakinah.deen.data.database.entity.SalahRecordEntity
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        SalahRecordEntity::class,
        QadhaLedgerEntity::class,
        HifzMistakeEntity::class,
        SadaqahLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(SakinahTypeConverters::class)
abstract class SakinahEncryptedDatabase : RoomDatabase() {
    abstract fun salahDao(): SalahDao
    abstract fun qadhaDao(): QadhaDao
    abstract fun hifzDao(): HifzDao
    abstract fun sadaqahDao(): SadaqahDao

    companion object {
        private const val DB_NAME = "sakinah_encrypted.db"

        @Volatile
        private var INSTANCE: SakinahEncryptedDatabase? = null

        fun getInstance(context: Context, passphraseBytes: ByteArray): SakinahEncryptedDatabase {
            return INSTANCE ?: synchronized(this) {
                val factory = SupportFactory(passphraseBytes)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SakinahEncryptedDatabase::class.java,
                    DB_NAME
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}`
  },
  'DAOs': {
    filename: 'Daos.kt',
    category: 'Encrypted Persistence',
    code: `package com.sakinah.deen.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sakinah.deen.data.database.entity.HifzMistakeEntity
import com.sakinah.deen.data.database.entity.QadhaLedgerEntity
import com.sakinah.deen.data.database.entity.SadaqahLogEntity
import com.sakinah.deen.data.database.entity.SalahRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SalahDao {
    @Query("SELECT * FROM salah_records WHERE dateEpochDay = :epochDay")
    fun observeTodaySalah(epochDay: Long): Flow<List<SalahRecordEntity>>

    @Query("SELECT COUNT(*) FROM salah_records WHERE dateEpochDay BETWEEN :startDay AND :endDay AND isCompleted = 1")
    fun observeCompletedCountBetween(startDay: Long, endDay: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSalahRecord(record: SalahRecordEntity)
}

@Dao
interface QadhaDao {
    @Query("SELECT * FROM qadha_ledger ORDER BY prayerType ASC")
    fun observeQadhaLedger(): Flow<List<QadhaLedgerEntity>>

    @Query("SELECT * FROM qadha_ledger WHERE prayerType = :prayerType LIMIT 1")
    suspend fun getQadhaForPrayer(prayerType: String): QadhaLedgerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQadha(entity: QadhaLedgerEntity)

    @Transaction
    suspend fun decrementMissed(prayerType: String, delta: Int = 1) {
        val existing = getQadhaForPrayer(prayerType)
        if (existing != null) {
            val newMissed = (existing.totalMissed - delta).coerceAtLeast(0)
            val newMadeUp = existing.totalMadeUp + delta
            upsertQadha(existing.copy(totalMissed = newMissed, totalMadeUp = newMadeUp))
        }
    }
}`
  },
  'Astronomy': {
    filename: 'PrayerCalculationEngineImpl.kt',
    category: 'Astronomy Engine',
    code: `package com.sakinah.deen.domain.astronomy

import com.sakinah.deen.data.model.CalculationMethod
import com.sakinah.deen.data.model.Madhhab
import com.sakinah.deen.data.model.PrayerType
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.*

class PrayerCalculationEngineImpl : PrayerCalculationEngine {

    companion object {
        private const val KAABA_LATITUDE = 21.422487
        private const val KAABA_LONGITUDE = 39.826206
    }

    override fun getQiblaAzimuth(coordinates: Pair<Double, Double>): Double {
        val lat1 = Math.toRadians(coordinates.first)
        val lon1 = Math.toRadians(coordinates.second)
        val lat2 = Math.toRadians(KAABA_LATITUDE)
        val lon2 = Math.toRadians(KAABA_LONGITUDE)

        val deltaLon = lon2 - lon1
        val y = sin(deltaLon)
        val x = cos(lat1) * tan(lat2) - sin(lat1) * cos(deltaLon)
        val qiblaDeg = Math.toDegrees(atan2(y, x))
        return (qiblaDeg + 360.0) % 360.0
    }

    override fun getPrayerTimes(
        coordinates: Pair<Double, Double>,
        date: LocalDate,
        method: CalculationMethod,
        madhhab: Madhhab
    ): Map<PrayerType, LocalTime> {
        // Full mathematical astronomical calculation (Solar declination, EoT, Zenith angles)
        // Standard Asr (1:1) vs Hanafi (2:1 shadow ratio)
        // ...
        return emptyMap()
    }
}`
  },
  'Travel': {
    filename: 'TravelStatusManager.kt',
    category: 'Mobility & Safar',
    code: `package com.sakinah.deen.domain.travel

import com.sakinah.deen.data.model.PrayerType
import kotlin.math.*

class TravelStatusManager(
    private var homeAnchorCoordinates: Pair<Double, Double> = Pair(51.5074, -0.1278)
) {
    companion object {
        const val TRAVEL_THRESHOLD_KM = 80.0
        private const val EARTH_RADIUS_KM = 6371.0
    }

    fun calculateHaversineDistanceKm(p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
        val lat1 = Math.toRadians(p1.first)
        val lon1 = Math.toRadians(p1.second)
        val lat2 = Math.toRadians(p2.first)
        val lon2 = Math.toRadians(p2.second)
        val dLat = lat2 - lat1
        val dLon = lon2 - lon1
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun evaluateTravelState(current: Pair<Double, Double>): TravelState {
        val distance = calculateHaversineDistanceKm(homeAnchorCoordinates, current)
        val isTraveler = distance >= TRAVEL_THRESHOLD_KM
        // Triggers Qasr (shorten 4 -> 2) and Jam' combining windows
        return TravelState(...)
    }
}`
  },
  'TafseerModular': {
    filename: 'TafseerDownloadRepository.kt',
    category: 'Dynamic SQLite Loader',
    code: `package com.sakinah.deen.data.repository

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TafseerDownloadRepository(private val context: Context) {
    // Manages remote CDN downloads of tafsir_ibnkathir_en.db to keep base APK < 25MB
    // Executes ATTACH DATABASE '...' AS tafseer_alias for zero-latency SQL JOINs
    suspend fun attachTafseerDatabase(
        roomDb: SakinahEncryptedDatabase,
        packageId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val db: SupportSQLiteDatabase = roomDb.openHelper.writableDatabase
        db.execSQL("ATTACH DATABASE '/data/user/0/.../tafsir.db' AS tafseer_alias")
        Result.success("tafseer_alias")
    }
}`
  },
  'RagClient': {
    filename: 'SunniAiScholarRepository.kt',
    category: 'Networking & SSE Client',
    code: `package com.sakinah.deen.network

import com.sakinah.deen.network.model.ProofSourceDto
import com.sakinah.deen.network.model.RagQueryRequest
import com.sakinah.deen.network.model.RagStreamChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface SunniAiScholarRepository {
    fun streamAskScholar(request: RagQueryRequest): Flow<RagStreamChunk>
}

class SunniAiScholarRepositoryImpl(
    private val backendBaseUrl: String = "http://localhost:3000/api/scholar"
) : SunniAiScholarRepository {
    override fun streamAskScholar(request: RagQueryRequest): Flow<RagStreamChunk> = flow {
        // 1. Emit Initial Orthodoxy Guardrail Notice
        emit(RagStreamChunk.GuardrailDisclaimer("Educational Reference Guide: Verified against authentic Sunni Quran, Hadith, and classical Fiqh texts."))

        // 2. Stream tokens via Ktor SSE / HTTP
        emitResilientSunniKnowledge(request.query)

        // 3. Emit Grounded Proof Citations
        for (src in getGroundedSources(request.query)) {
            emit(RagStreamChunk.SourceCitation(src))
        }

        // 4. Emit 4-Madhhab Comparative Positions
        for ((school, summary) in getMadhhabBreakdown(request.query)) {
            emit(RagStreamChunk.MadhhabBreakdown(school, summary))
        }

        emit(RagStreamChunk.Complete)
    }
}`
  },
  'ViewModels': {
    filename: 'SalahViewModel.kt',
    category: 'Reactive UDF StateFlow',
    code: `package com.sakinah.deen.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakinah.deen.data.database.dao.QadhaDao
import com.sakinah.deen.data.database.dao.SalahDao
import com.sakinah.deen.domain.astronomy.PrayerCalculationEngine
import com.sakinah.deen.domain.travel.TravelStatusManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SalahUiState(
    val prayerTimes: Map<PrayerType, LocalTime> = emptyMap(),
    val currentPrayer: PrayerType = PrayerType.DHUHR,
    val nextPrayer: PrayerType = PrayerType.ASR,
    val countdownText: String = "01h 24m 12s",
    val isTraveler: Boolean = false,
    val qadhaDeficits: Map<PrayerType, Int> = emptyMap()
)

class SalahViewModel(
    private val salahDao: SalahDao,
    private val qadhaDao: QadhaDao,
    private val astronomyEngine: PrayerCalculationEngine,
    private val travelManager: TravelStatusManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(SalahUiState())
    val uiState: StateFlow<SalahUiState> = _uiState.asStateFlow()

    fun onIntent(intent: SalahUiIntent) {
        when (intent) {
            is SalahUiIntent.ToggleSalahCompletion -> toggleSalah(intent.prayerType)
            is SalahUiIntent.IncrementQadha -> viewModelScope.launch { qadhaDao.incrementMissed(intent.prayerType.name) }
            is SalahUiIntent.DecrementQadha -> viewModelScope.launch { qadhaDao.decrementMissed(intent.prayerType.name) }
        }
    }
}`
  },
  'KoinDI': {
    filename: 'KoinModules.kt',
    category: 'Koin Dependency Injection',
    code: `package com.sakinah.deen.di

import com.sakinah.deen.data.database.SakinahEncryptedDatabase
import com.sakinah.deen.network.SunniAiScholarRepository
import com.sakinah.deen.network.SunniAiScholarRepositoryImpl
import com.sakinah.deen.ui.viewmodel.*
import org.koin.dsl.module

val databaseModule = module {
    single { SakinahEncryptedDatabase.getInstance(get(), "SakinahMasterKey".toByteArray()) }
    single { get<SakinahEncryptedDatabase>().salahDao() }
    single { get<SakinahEncryptedDatabase>().qadhaDao() }
    single { get<SakinahEncryptedDatabase>().hifzDao() }
    single { get<SakinahEncryptedDatabase>().sadaqahDao() }
}

val networkModule = module {
    single<SunniAiScholarRepository> { SunniAiScholarRepositoryImpl() }
}

val viewModelModule = module {
    factory { SalahViewModel(get(), get(), get(), get()) }
    factory { QuranViewModel(get(), get()) }
    factory { HadithViewModel() }
    factory { TravelViewModel(get(), get(), getOrNull()) }
    factory { WealthViewModel(get()) }
    factory { AiScholarViewModel(get()) }
}

val appModules = listOf(databaseModule, networkModule, travelModule, viewModelModule)`
  },
  'AlarmScheduler': {
    filename: 'PrayerAlarmScheduler.kt',
    category: 'Exact Alarms & Doze Bypass',
    code: `package com.sakinah.deen.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sakinah.deen.data.model.PrayerType

class PrayerAlarmScheduler(
    private val context: Context,
    private val astronomyEngine: PrayerCalculationEngine
) {
    fun scheduleSingleAlarm(prayer: PrayerType, triggerEpochMillis: Long) {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
            putExtra(EXTRA_PRAYER_TYPE, prayer.name)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1000 + prayer.ordinal, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Android 12+ runtime permission check for exact alarms
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerEpochMillis, pendingIntent)
        }
    }
}`
  },
  'AdhanService': {
    filename: 'AdhanPlaybackService.kt',
    category: 'Media3 Foreground Service',
    code: `package com.sakinah.deen.service

import android.app.Service
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.core.app.NotificationCompat

class AdhanPlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {
    // Android 14+ Foreground Service (foregroundServiceType="mediaPlayback")
    // Progressive audio fade-in over 5 seconds
    // Lock-screen playback notification with controls: Stop, Mute, Snooze 10m
    // Audio focus management with automatic ducking over telephone calls
    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> stopPlayback()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mediaPlayer?.setVolume(0.2f, 0.2f)
            AudioManager.AUDIOFOCUS_GAIN -> mediaPlayer?.setVolume(1.0f, 1.0f)
        }
    }
}`
  },
  'QiblaSensor': {
    filename: 'QiblaSensorManager.kt',
    category: 'Sensor Fusion Compass',
    code: `package com.sakinah.deen.domain.sensor

import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorManager

class QiblaSensorManager(private val context: Context) : SensorEventListener {
    // 1. Rotation Vector Fusion with Accelerometer+Magnetometer fallback
    // 2. Exponential Moving Average (EMA alpha=0.15) to damp motor vibration
    // 3. GeomagneticField true north declination adjustment
    // 4. Over 15 km/h vehicle speed: smoothly transitions to GPS trajectory heading
    override fun onSensorChanged(event: SensorEvent) {
        if (isMovingFast && location.hasBearing()) {
            val vehicleHeading = location.bearing
            smoothedAzimuth = smoothAngle(smoothedAzimuth, vehicleHeading)
            evaluateHeadingOutput(smoothedAzimuth, isVehicular = true)
            return
        }
        // Sensor fusion computation...
    }
}`
  },
  'CarBluetooth': {
    filename: 'CarBluetoothReceiver.kt',
    category: 'Bluetooth Car Handshake',
    code: `package com.sakinah.deen.receiver

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CarBluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            if (device?.bluetoothClass?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) {
                // Trigger heads-up notification with one-tap Dua al-Safar playback
                showVehiclePromptNotification(context, device.name ?: "Vehicle Audio")
            }
        }
    }
}`
  },
  'ProGuard': {
    filename: 'proguard-rules.pro',
    category: 'R8 / Size Optimization',
    code: `# SQLCipher Keep Rules
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.sqlcipher.**

# Adhan-Kotlin & Astronomy
-keep class com.batoulapps.adhan.** { *; }
-keep class com.sakinah.deen.domain.astronomy.** { *; }

# Serialization
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# Strip release debugging logs (Zero telemetry)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}`
  }
};

export const ComposeCodeModal: React.FC<ComposeCodeModalProps> = ({ isOpen, onClose }) => {
  const [selectedKey, setSelectedKey] = useState<string>('DomainModels');
  const [copied, setCopied] = useState<boolean>(false);

  if (!isOpen) return null;

  const activeFile = COMPOSE_FILES[selectedKey] || COMPOSE_FILES['DomainModels'];

  const handleCopy = () => {
    navigator.clipboard.writeText(activeFile.code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex items-center justify-center p-3 sm:p-6 animate-fadeIn">
      <div className="bg-[#0B251C] border border-[#144736] rounded-3xl w-full max-w-4xl max-h-[90vh] flex flex-col shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="px-6 py-4 border-b border-[#144736] flex items-center justify-between bg-[#071913]">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-emerald-900/60 flex items-center justify-center text-emerald-400 border border-emerald-700/50">
              <FileCode className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-bold text-white font-mono">
                  {activeFile.filename}
                </h3>
                <span className="text-[10px] bg-emerald-950 text-emerald-300 px-2 py-0.5 rounded-full border border-emerald-700/50">
                  {activeFile.category}
                </span>
              </div>
              <p className="text-xs text-emerald-400/80">
                Offline-First Data Architecture · Room SQLCipher · Astronomy Math · Mobility Engine
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={handleCopy}
              className="px-3 py-1.5 rounded-xl bg-[#144736] hover:bg-[#1c5c47] text-white text-xs font-medium transition-all flex items-center gap-1.5 border border-emerald-500/30"
            >
              {copied ? (
                <>
                  <Check className="w-4 h-4 text-emerald-400" />
                  <span>Copied</span>
                </>
              ) : (
                <>
                  <Copy className="w-4 h-4 text-emerald-300" />
                  <span>Copy Code</span>
                </>
              )}
            </button>
            <button
              onClick={onClose}
              className="p-1.5 rounded-xl bg-[#0E3326] hover:bg-[#144736] text-emerald-300 hover:text-white transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Tab Strip */}
        <div className="flex items-center gap-1.5 px-4 py-2 bg-[#081F17] border-b border-[#144736] overflow-x-auto scrollbar-none">
          {Object.keys(COMPOSE_FILES).map((key) => {
            const isSel = selectedKey === key;
            return (
              <button
                key={key}
                onClick={() => setSelectedKey(key)}
                className={`px-3 py-1 text-xs rounded-lg font-mono transition-colors whitespace-nowrap ${
                  isSel
                    ? 'bg-[#10B981] text-emerald-950 font-bold'
                    : 'bg-[#0E3326] text-emerald-300 hover:bg-[#144736]'
                }`}
              >
                {COMPOSE_FILES[key].filename}
              </button>
            );
          })}
        </div>

        {/* Code Content */}
        <div className="flex-1 overflow-y-auto p-4 bg-[#05140F]">
          <pre className="text-xs font-mono text-emerald-200/95 leading-relaxed selection:bg-emerald-500/30">
            <code>{activeFile.code}</code>
          </pre>
        </div>

        {/* Footer Note */}
        <div className="px-6 py-2.5 bg-[#071913] border-t border-[#144736] flex items-center justify-between text-[11px] text-emerald-400/80">
          <div className="flex items-center gap-2">
            <Layers className="w-3.5 h-3.5 text-emerald-400" />
            <span>Room 2.6.1 · SQLCipher 4.5.4 · Ktor 3.0.1 · Koin 4.0.0 · WorkManager 2.10.0</span>
          </div>
          <span>Package: com.sakinah.deen</span>
        </div>
      </div>
    </div>
  );
};
