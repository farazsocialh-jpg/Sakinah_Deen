import Foundation
import SwiftUI
import Shared

/// SwiftUI-facing state for accounts, profile, and settings.
@MainActor
final class AccountStore: ObservableObject {

    enum Mode { case loading, guest, authenticated, failed(String) }

    @Published private(set) var mode: Mode = .loading
    @Published private(set) var displayName: String = ""
    @Published private(set) var email: String = ""
    @Published var defaultCity: String = ""
    @Published var madhhab: String = "SHAFI"
    @Published var journeyTrack: String = "SEEKER"
    @Published var errorMessage: String?

    private let bridge = AccountBridge()

    var cacheSizeLabel: String { bridge.cacheSizeLabel() }

    var isSignedIn: Bool { if case .authenticated = mode { return true }; return false }
    var isGuest: Bool { if case .guest = mode { return true }; return false }

    func start() {
        bridge.observeAuth { [weak self] state in
            Task { @MainActor in self?.apply(state) }
        }
    }

    private func apply(_ state: String) {
        if state == "authenticated" {
            mode = .authenticated
            displayName = bridge.currentUser?.displayName ?? ""
            email = bridge.currentUser?.email ?? ""
        } else if state == "guest" {
            mode = .guest
            displayName = ""
            email = ""
        } else if state.hasPrefix("error:") {
            let message = String(state.dropFirst("error:".count))
            errorMessage = message
            mode = .failed(message)
        }
    }

    func signIn(email: String, password: String) {
        Task { await finish(bridge.signIn(email: email, password: password)) }
    }

    func createAccount(email: String, password: String, displayName: String) {
        Task { await finish(bridge.signUp(email: email, password: password, displayName: displayName)) }
    }

    func resetPassword(email: String) {
        Task { await finish(bridge.resetPassword(email: email)) }
    }

    func signInWithGoogle() {
        Task { await finish(bridge.signInWithGoogle(idToken: "google-placeholder")) }
    }

    func signInWithApple() {
        Task { await finish(bridge.signInWithApple(identityToken: "apple-placeholder")) }
    }

    func continueAsGuest() {
        Task { await finish(bridge.continueAsGuest()) }
    }

    func signOut() {
        Task {
            await bridge.signOut()
            mode = .guest
        }
    }

    func deleteAccount() {
        Task {
            _ = await bridge.deleteAccount()
            mode = .guest
        }
    }

    func saveProfile() {
        Task {
            await finish(
                bridge.updateProfile(
                    displayName: displayName,
                    defaultCity: defaultCity,
                    madhhab: madhhab,
                    journeyTrack: journeyTrack
                )
            )
        }
    }

    func savePrayer(calculationMethod: String, asrMethod: String, highLatitudeRule: String, hijriOffset: Int32) {
        Task {
            await bridge.updatePrayerSettings(
                calculationMethod: calculationMethod,
                asrMethod: asrMethod,
                highLatitudeRule: highLatitudeRule,
                hijriOffset: hijriOffset
            )
        }
    }

    func saveAdhan(
        choice: String,
        carBluetoothAutoPlay: Bool,
        fajr: Bool,
        dhuhr: Bool,
        asr: Bool,
        maghrib: Bool,
        isha: Bool
    ) {
        Task {
            await bridge.updateAdhanSettings(
                adhanChoice: choice,
                carBluetoothAutoPlay: carBluetoothAutoPlay,
                fajrEnabled: fajr,
                dhuhrEnabled: dhuhr,
                asrEnabled: asr,
                maghribEnabled: maghrib,
                ishaEnabled: isha
            )
        }
    }

    func saveAppearance(theme: String, arabicFontSize: Int32, language: String) {
        Task {
            await bridge.updateAppearanceSettings(
                theme: theme,
                arabicFontSize: arabicFontSize,
                language: language
            )
        }
    }

    func clearCache() {
        Task { await bridge.clearCache() }
    }

    private func finish(_ error: String?) async {
        if let error {
            errorMessage = error
        } else {
            errorMessage = nil
        }
    }
}
