import { readFileSync, writeFileSync } from "node:fs";

const EDITS = [
  // ---------------------------------------------------------------- SunniMethodMapper
  {
    path: "app/src/main/java/com/sakinah/deen/data/prayer/SunniMethodMapper.kt",
    mode: "block",
    old: `    fun localCalculationMethod(authority: SunniAuthority): CalculationMethod = when (authority) {
        SunniAuthority.UMM_AL_QURA -> CalculationMethod.UMM_AL_QURA
        SunniAuthority.QATAR -> CalculationMethod.QATAR
        SunniAuthority.DUBAI -> CalculationMethod.DUBAI
        SunniAuthority.KARACHI -> CalculationMethod.KARACHI
        SunniAuthority.EGYPTIAN -> CalculationMethod.EGYPTIAN
        SunniAuthority.ISNA -> CalculationMethod.NORTH_AMERICA
        SunniAuthority.MUSLIM_WORLD_LEAGUE -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        SunniAuthority.KUWAIT -> CalculationMethod.KUWAIT
        SunniAuthority.GULF_REGION -> CalculationMethod.GULF_REGION
        SunniAuthority.DIYANET -> CalculationMethod.DIYANET
        SunniAuthority.JAKIM -> CalculationMethod.JAKIM
        SunniAuthority.MUIS -> CalculationMethod.MUIS
        SunniAuthority.KEMENAG -> CalculationMethod.KEMENAG
        SunniAuthority.MOONSIGHTING_COMMITTEE -> CalculationMethod.MOONSIGHTING_COMMITTEE
        SunniAuthority.UOIF -> CalculationMethod.UOIF
        SunniAuthority.SAMR -> CalculationMethod.SAMR
        SunniAuthority.JORDAN -> CalculationMethod.JORDAN
        SunniAuthority.TUNISIA -> CalculationMethod.TUNISIA
        SunniAuthority.TEHRAN -> CalculationMethod.TEHRAN
    }`,
    new: `    fun localCalculationMethod(authority: SunniAuthority): CalculationMethod =
        overridesByAuthority[authority] ?: CalculationMethod.MUSLIM_WORLD_LEAGUE

    /**
     * Reverse of [fromOverride], built from it rather than restated, so the two directions can
     * never drift apart when a calculation method is added.
     */
    private val overridesByAuthority: Map<SunniAuthority, CalculationMethod> by lazy {
        CalculationMethod.entries.associateBy(::fromOverride)
    }`,
  },

  // ---------------------------------------------------------------- AlAdhanApiClient
  {
    path: "app/src/main/java/com/sakinah/deen/data/prayer/AlAdhanApiClient.kt",
    mode: "block",
    old: `        private fun keyOf(type: PrayerType): String = when (type) {
            PrayerType.FAJR -> "Fajr"
            PrayerType.SUNRISE -> "Sunrise"
            PrayerType.DHUHR -> "Dhuhr"
            PrayerType.ASR -> "Asr"
            PrayerType.MAGHRIB -> "Maghrib"
            PrayerType.ISHA -> "Isha"
        }`,
    new: ``,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/data/prayer/AlAdhanApiClient.kt",
    mode: "sub",
    old: "timings[keyOf(type)]",
    new: "timings[type.englishName]",
  },

  // ---------------------------------------------------------------- SalahViewModel
  {
    path: "app/src/main/java/com/sakinah/deen/ui/viewmodel/SalahViewModel.kt",
    mode: "sub",
    old: "prayerDisplayName(PrayerType.DHUHR)",
    new: "PrayerType.DHUHR.englishName",
    all: true,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/viewmodel/SalahViewModel.kt",
    mode: "sub",
    old: "prayerDisplayName(countdown.currentPrayer)",
    new: "countdown.currentPrayer.englishName",
    all: true,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/viewmodel/SalahViewModel.kt",
    mode: "sub",
    old: "prayerDisplayName(countdown.nextPrayer)",
    new: "countdown.nextPrayer.englishName",
    all: true,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/viewmodel/SalahViewModel.kt",
    mode: "sub",
    old: "prayerDisplayName(it)",
    new: "it.englishName",
    all: true,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/viewmodel/SalahViewModel.kt",
    mode: "sub",
    old: "prayerArabicName(countdown.currentPrayer)",
    new: "countdown.currentPrayer.arabicName",
    all: true,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/viewmodel/SalahViewModel.kt",
    mode: "block",
    old: `    private fun prayerDisplayName(type: PrayerType): String = when (type) {
        PrayerType.FAJR -> "Fajr"
        PrayerType.SUNRISE -> "Sunrise"
        PrayerType.DHUHR -> "Dhuhr"
        PrayerType.ASR -> "Asr"
        PrayerType.MAGHRIB -> "Maghrib"
        PrayerType.ISHA -> "Isha"
    }`,
    new: ``,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/viewmodel/SalahViewModel.kt",
    mode: "block",
    old: `    private fun prayerArabicName(type: PrayerType): String = when (type) {
        PrayerType.FAJR -> "الفجر"
        PrayerType.SUNRISE -> "الشروق"
        PrayerType.DHUHR -> "الظهر"
        PrayerType.ASR -> "العصر"
        PrayerType.MAGHRIB -> "المغرب"
        PrayerType.ISHA -> "العشاء"
    }`,
    new: ``,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/viewmodel/SalahViewModel.kt",
    mode: "sub",
    old: "import com.sakinah.deen.data.model.PrayerType",
    new: `import com.sakinah.deen.data.model.PrayerType
import com.sakinah.deen.data.prayer.arabicName
import com.sakinah.deen.data.prayer.englishName`,
  },

  // ---------------------------------------------------------------- IslamicDataRepository
  {
    path: "app/src/main/java/com/sakinah/deen/network/IslamicDataRepository.kt",
    mode: "block",
    old: `    private fun cacheCatalog(catalog: List<QuranTranslationResource>) {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences("sakinah_quran_translations", android.content.Context.MODE_PRIVATE)
        prefs.edit {
            putString(
                "catalog_json",
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(QuranTranslationResource.serializer()),
                    catalog
                )
            )
        }
    }

    private fun loadCachedCatalog(): List<QuranTranslationResource> {
        val context = appContext ?: return emptyList()
        val prefs = context.getSharedPreferences("sakinah_quran_translations", android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString("catalog_json", null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(QuranTranslationResource.serializer()),
                raw
            )
        }.getOrDefault(emptyList())
    }`,
    new: `    private fun cacheTranslationCatalog(catalog: List<QuranTranslationResource>) =
        cacheCatalog(QURAN_CATALOG_PREFS, catalog, ListSerializer(QuranTranslationResource.serializer()))

    private fun loadCachedTranslationCatalog(): List<QuranTranslationResource> =
        loadCachedCatalog(QURAN_CATALOG_PREFS, ListSerializer(QuranTranslationResource.serializer()))

    private fun cacheHadithCatalog(catalog: List<HadithBookCatalog>) =
        cacheCatalog(HADITH_CATALOG_PREFS, catalog, ListSerializer(HadithBookCatalog.serializer()))

    private fun loadCachedHadithCatalog(): List<HadithBookCatalog> =
        loadCachedCatalog(HADITH_CATALOG_PREFS, ListSerializer(HadithBookCatalog.serializer()))

    /** Both catalogs are small JSON lists, each cached in its own preferences file. */
    private fun <T> cacheCatalog(
        prefsName: String,
        catalog: List<T>,
        serializer: KSerializer<List<T>>
    ) {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
        prefs.edit { putString(CATALOG_KEY, json.encodeToString(serializer, catalog)) }
    }

    private fun <T> loadCachedCatalog(prefsName: String, serializer: KSerializer<List<T>>): List<T> {
        val context = appContext ?: return emptyList()
        val prefs = context.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString(CATALOG_KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }`,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/network/IslamicDataRepository.kt",
    mode: "block",
    old: `    private fun cacheHadithCatalog(catalog: List<HadithBookCatalog>) {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences("sakinah_hadith_catalog", android.content.Context.MODE_PRIVATE)
        prefs.edit {
            putString(
                "catalog_json",
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(HadithBookCatalog.serializer()),
                    catalog
                )
            )
        }
    }

    private fun loadCachedHadithCatalog(): List<HadithBookCatalog> {
        val context = appContext ?: return emptyList()
        val prefs = context.getSharedPreferences("sakinah_hadith_catalog", android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString("catalog_json", null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(HadithBookCatalog.serializer()),
                raw
            )
        }.getOrDefault(emptyList())
    }`,
    new: ``,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/network/IslamicDataRepository.kt",
    mode: "sub",
    old: "cacheCatalog(parsed)",
    new: "cacheTranslationCatalog(parsed)",
  },
  {
    path: "app/src/main/java/com/sakinah/deen/network/IslamicDataRepository.kt",
    mode: "sub",
    old: "return loadCachedCatalog()",
    new: "return loadCachedTranslationCatalog()",
  },
  {
    path: "app/src/main/java/com/sakinah/deen/network/IslamicDataRepository.kt",
    mode: "sub",
    old: "        private const val TAFSIR_UPSERT_CHUNK = 50",
    new: `        private const val TAFSIR_UPSERT_CHUNK = 50

        /** Preferences files and key for the two catalogs cached for offline use. */
        private const val QURAN_CATALOG_PREFS = "sakinah_quran_translations"
        private const val HADITH_CATALOG_PREFS = "sakinah_hadith_catalog"
        private const val CATALOG_KEY = "catalog_json"`,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/network/IslamicDataRepository.kt",
    mode: "sub",
    old: "import kotlinx.serialization.decodeFromString",
    new: `import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString`,
  },

  // ---------------------------------------------------------------- ProfileScreen
  {
    path: "app/src/main/java/com/sakinah/deen/ui/screens/ProfileScreen.kt",
    mode: "block",
    old: `                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(photoUrl = avatarUrl, displayName = displayName.ifBlank { email })
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = displayName.ifBlank { stringResource(R.string.profile_your_account) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (email.isNotBlank()) {
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = providerLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }`,
    new: `                ProfileAccountHeader(
                    photoUrl = avatarUrl,
                    name = displayName.ifBlank { stringResource(R.string.profile_your_account) },
                    email = email,
                    subtitle = providerLabel
                )`,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/screens/ProfileScreen.kt",
    mode: "block",
    old: `                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it; validationError = null },
                    label = { Text(stringResource(R.string.profile_display_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .autofillName()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; validationError = null },
                    label = { Text(stringResource(R.string.profile_email)) },
                    singleLine = true,
                    readOnly = signedInUser?.isGuest == false,
                    supportingText = if (signedInUser?.isGuest == false) {
                        { Text(stringResource(R.string.profile_email_tied)) }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .autofillEmail()
                )`,
    new: `                ProfileNameEmailFields(
                    displayName = displayName,
                    onDisplayNameChange = { displayName = it; validationError = null },
                    nameLabel = stringResource(R.string.profile_display_name),
                    email = email,
                    onEmailChange = { email = it; validationError = null },
                    emailLabel = stringResource(R.string.profile_email),
                    emailReadOnly = signedInUser?.isGuest == false,
                    emailSupportingText = if (signedInUser?.isGuest == false) {
                        stringResource(R.string.profile_email_tied)
                    } else {
                        null
                    }
                )`,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/screens/ProfileScreen.kt",
    mode: "sub",
    old: "import com.sakinah.deen.ui.components.DateOfBirthField",
    new: `import com.sakinah.deen.ui.components.DateOfBirthField
import com.sakinah.deen.ui.components.ProfileAccountHeader
import com.sakinah.deen.ui.components.ProfileNameEmailFields`,
  },

  // ---------------------------------------------------------------- CompleteProfileScreen
  {
    path: "app/src/main/java/com/sakinah/deen/ui/screens/CompleteProfileScreen.kt",
    mode: "block",
    old: `            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(
                    photoUrl = profile?.pictureUrl()?.takeIf { it.isNotBlank() },
                    displayName = displayName
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = displayName.ifBlank { stringResource(R.string.complete_profile_your_account) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (email.isNotBlank()) {
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val provider = profile?.authProvider.orEmpty()
                    if (provider == "google") {
                        Text(
                            text = stringResource(R.string.complete_profile_signed_in_google),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }`,
    new: `            ProfileAccountHeader(
                photoUrl = profile?.pictureUrl()?.takeIf { it.isNotBlank() },
                name = displayName.ifBlank { stringResource(R.string.complete_profile_your_account) },
                email = email,
                subtitle = if (profile?.authProvider == "google") {
                    stringResource(R.string.complete_profile_signed_in_google)
                } else {
                    null
                }
            )`,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/screens/CompleteProfileScreen.kt",
    mode: "block",
    old: `            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it; error = null },
                label = { Text(stringResource(R.string.complete_profile_display_name)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .autofillName()
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; error = null },
                label = { Text(stringResource(R.string.complete_profile_email)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .autofillEmail()
            )`,
    new: `            ProfileNameEmailFields(
                displayName = displayName,
                onDisplayNameChange = { displayName = it; error = null },
                nameLabel = stringResource(R.string.complete_profile_display_name),
                email = email,
                onEmailChange = { email = it; error = null },
                emailLabel = stringResource(R.string.complete_profile_email)
            )`,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/screens/CompleteProfileScreen.kt",
    mode: "sub",
    old: "import com.sakinah.deen.ui.components.DateOfBirthField",
    new: `import com.sakinah.deen.ui.components.DateOfBirthField
import com.sakinah.deen.ui.components.ProfileAccountHeader
import com.sakinah.deen.ui.components.ProfileNameEmailFields`,
  },

  // ---------------------------------------------------------------- SettingsScreen
  {
    path: "app/src/main/java/com/sakinah/deen/ui/screens/SettingsScreen.kt",
    mode: "block",
    old: `    var gpsHomeRequested by remember { mutableStateOf(false) }`,
    new: ``,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/screens/SettingsScreen.kt",
    mode: "block",
    old: `    fun saveCurrentGpsAsHome() {
        scope.launch {
            val resolved = locationProvider.getFreshOrCachedLocation()
            val label = listOf(resolved.cityName, resolved.countryName)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { "Saved Location" }
            appPrefs.saveHomeAnchor(resolved.latitude, resolved.longitude, label)
            hasHomeLocation = appPrefs.homeCoordinates != null
            homeCityName = appPrefs.homeCityName
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (isGranted && gpsHomeRequested) saveCurrentGpsAsHome()
        gpsHomeRequested = false
    }`,
    new: `    val saveGpsHome = rememberGpsHomeSaver(
        locationProvider = locationProvider,
        preferences = appPrefs,
        fallbackLabel = "Saved Location"
    ) {
        hasHomeLocation = appPrefs.homeCoordinates != null
        homeCityName = appPrefs.homeCityName
    }`,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/screens/SettingsScreen.kt",
    mode: "block",
    old: `                                        onClick = {
                                            gpsHomeRequested = true
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        },`,
    new: `                                        onClick = saveGpsHome,`,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/screens/SettingsScreen.kt",
    mode: "sub",
    old: `import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
`,
    new: ``,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/screens/SettingsScreen.kt",
    mode: "sub",
    old: "import androidx.lifecycle.compose.collectAsStateWithLifecycle",
    new: `import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.ui.location.rememberGpsHomeSaver`,
  },

  // ---------------------------------------------------------------- TravelScreen
  {
    path: "app/src/main/java/com/sakinah/deen/ui/screens/TravelScreen.kt",
    mode: "block",
    old: `    var gpsHomeRequested by remember { mutableStateOf(false) }`,
    new: ``,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/screens/TravelScreen.kt",
    mode: "block",
    old: `    fun saveCurrentGpsAsHome() {
        scope.launch {
            val resolved = locationProvider.getFreshOrCachedLocation()
            val label = listOf(resolved.cityName, resolved.countryName)
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { context.getString(R.string.travel_saved_location) }
            appPrefs.saveHomeAnchor(resolved.latitude, resolved.longitude, label)
            homeCoordinates = appPrefs.homeCoordinates
            homeCity = appPrefs.homeCityName
            // A home set from the current position means zero distance and no active concession.
            distanceKm = 0f
            isSafar = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (isGranted && gpsHomeRequested) saveCurrentGpsAsHome()
        gpsHomeRequested = false
    }`,
    new: `    val saveGpsHome = rememberGpsHomeSaver(
        locationProvider = locationProvider,
        preferences = appPrefs,
        fallbackLabel = context.getString(R.string.travel_saved_location)
    ) {
        homeCoordinates = appPrefs.homeCoordinates
        homeCity = appPrefs.homeCityName
        // A home set from the current position means zero distance and no active concession.
        distanceKm = 0f
        isSafar = false
    }`,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/screens/TravelScreen.kt",
    mode: "block",
    old: `                        onClick = {
                            gpsHomeRequested = true
                            showHomeOptions = false
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },`,
    new: `                        onClick = {
                            showHomeOptions = false
                            saveGpsHome()
                        },`,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/screens/TravelScreen.kt",
    mode: "sub",
    old: `import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
`,
    new: ``,
  },
  {
    path: "app/src/main/java/com/sakinah/deen/ui/screens/TravelScreen.kt",
    mode: "sub",
    old: "import androidx.lifecycle.compose.collectAsStateWithLifecycle",
    new: `import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.ui.location.rememberGpsHomeSaver`,
  },
];

const normalize = (p) => {
  const raw = readFileSync(p, "utf8");
  const crlf = raw.includes("\r\n");
  return { crlf, text: raw.replace(/\r\n/g, "\n") };
};

const byFile = new Map();
for (const e of EDITS) {
  if (!byFile.has(e.path)) byFile.set(e.path, []);
  byFile.get(e.path).push(e);
}

let failures = 0;
for (const [path, edits] of byFile) {
  const { crlf, text } = normalize(path);
  let lines = text.split("\n");
  const report = [];
  for (const e of edits) {
    if (e.mode === "sub") {
      const occurrences = lines.join("\n").split(e.old).length - 1;
      if (occurrences === 0) {
        report.push(`MISS(sub) ${JSON.stringify(e.old.slice(0, 60))}`);
        failures++;
        continue;
      }
      if (occurrences > 1 && !e.all) {
        report.push(`AMBIGUOUS(sub) ${occurrences}x ${JSON.stringify(e.old.slice(0, 60))}`);
        failures++;
        continue;
      }
      lines = lines.join("\n").split(e.old).join(e.new).split("\n");
      report.push(`ok(sub ${occurrences}x)`);
      continue;
    }
    // block: match line sequences ignoring indentation
    const oldLines = e.old.split("\n").map((l) => l.trim());
    const newLines = e.new === "" ? [] : e.new.split("\n");
    const found = [];
    for (let i = 0; i + oldLines.length <= lines.length; i++) {
      let match = true;
      for (let j = 0; j < oldLines.length; j++) {
        if (lines[i + j].trim() !== oldLines[j]) { match = false; break; }
      }
      if (match) found.push(i);
    }
    if (found.length !== 1) {
      report.push(`MISS(block ${found.length} hits) ${JSON.stringify(oldLines[0]?.slice(0, 60))}`);
      failures++;
      continue;
    }
    lines.splice(found[0], oldLines.length, ...newLines);
    report.push(`ok(block ${oldLines.length} lines)`);
  }
  const out = lines.join("\n");
  writeFileSync(path, crlf ? out.replace(/\n/g, "\r\n") : out, "utf8");
  console.log(`\n${path}`);
  for (const r of report) console.log("  " + r);
}
console.log(`\nfailures: ${failures}`);
