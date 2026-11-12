import SwiftUI

struct SettingsView: View {
    @ObservedObject var store: AccountStore

    @State private var method = "MWL"
    @State private var asr = "SHAFI"
    @State private var highLatitude = "MIDDLE_OF_NIGHT"
    @State private var hijriOffset: Int32 = 0

    @State private var adhanChoice = "MAKKAH"
    @State private var carBluetooth = false
    @State private var fajr = true
    @State private var dhuhr = true
    @State private var asrAlert = true
    @State private var maghrib = true
    @State private var isha = true

    @State private var theme = "SYSTEM"
    @State private var fontSize: Int32 = 24
    @State private var language = "en"

    private let methods = ["MWL", "ISNA", "UMM_AL_QURA", "EGYPTIAN", "KARACHI", "TEHRAN", "MAKKAH"]
    private let adhanChoices = ["FAJR", "MAKKAH", "VIBRATE", "SILENT"]
    private let themes = ["LIGHT", "DARK", "SYSTEM"]
    private let languages = ["en", "ar", "ur", "fr", "id"]

    var body: some View {
        Form {
            Section("Prayer & Qibla") {
                Picker("Calculation method", selection: $method) {
                    ForEach(methods, id: \.self) { Text($0).tag($0) }
                }
                Picker("Asr juristic method", selection: $asr) {
                    Text("Shafi'i").tag("SHAFI")
                    Text("Hanafi").tag("HANAFI")
                }
                Picker("High latitude rule", selection: $highLatitude) {
                    Text("Middle of night").tag("MIDDLE_OF_NIGHT")
                    Text("Seventh of night").tag("SEVENTH_OF_NIGHT")
                    Text("Twilight angle").tag("TWILIGHT_ANGLE")
                }
                Stepper("Hijri offset: \(hijriOffset)", value: $hijriOffset, in: -2...2)
                Button("Save prayer settings") {
                    store.savePrayer(calculationMethod: method, asrMethod: asr,
                                     highLatitudeRule: highLatitude, hijriOffset: hijriOffset)
                }
            }

            Section("Adhan & Notifications") {
                Toggle("Fajr", isOn: $fajr)
                Toggle("Dhuhr", isOn: $dhuhr)
                Toggle("Asr", isOn: $asrAlert)
                Toggle("Maghrib", isOn: $maghrib)
                Toggle("Isha", isOn: $isha)
                Picker("Adhan audio", selection: $adhanChoice) {
                    ForEach(adhanChoices, id: \.self) { Text($0.capitalized).tag($0) }
                }
                Toggle("Car Bluetooth Dua al-Safar auto-play", isOn: $carBluetooth)
                Button("Save adhan settings") {
                    store.saveAdhan(choice: adhanChoice, carBluetoothAutoPlay: carBluetooth,
                                    fajr: fajr, dhuhr: dhuhr, asr: asrAlert,
                                    maghrib: maghrib, isha: isha)
                }
            }

            Section("Appearance & Reading") {
                Picker("Theme", selection: $theme) {
                    ForEach(themes, id: \.self) { Text($0.capitalized).tag($0) }
                }
                Stepper("Arabic font size: \(fontSize)", value: $fontSize, in: 16...40)
                Picker("Translation", selection: $language) {
                    ForEach(languages, id: \.self) { Text($0.uppercased()).tag($0) }
                }
                Button("Save appearance") {
                    store.saveAppearance(theme: theme, arabicFontSize: fontSize, language: language)
                }
            }

            Section("Offline Storage") {
                Text("Cache size: \(store.cacheSizeLabel)")
                Button("Clear cache", role: .destructive) { store.clearCache() }
            }
        }
        .frame(maxWidth: 600)
        .navigationTitle("App Settings")
    }
}
