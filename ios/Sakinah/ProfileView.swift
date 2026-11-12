import SwiftUI

struct ProfileView: View {
    @ObservedObject var store: AccountStore
    @Environment(\.dismiss) private var dismiss

    @State private var confirmDelete = false

    private let madhhabs = ["SHAFI", "HANAFI", "MALIKI", "HANBALI"]
    private let tracks = ["SEEKER", "STUDENT", "SCHOLAR"]

    var body: some View {
        Form {
            Section("Profile") {
                TextField("Display name", text: $store.displayName)
                Text(store.email).foregroundColor(.secondary)
                TextField("Default city", text: $store.defaultCity)
                Picker("Madhhab", selection: $store.madhhab) {
                    ForEach(madhhabs, id: \.self) { Text($0.capitalized).tag($0) }
                }
                Picker("Journey track", selection: $store.journeyTrack) {
                    ForEach(tracks, id: \.self) { Text($0.capitalized).tag($0) }
                }
                Button("Save changes") { store.saveProfile() }
            }

            Section("Account") {
                Button("Sign out") {
                    store.signOut()
                    dismiss()
                }
                Button("Delete account", role: .destructive) { confirmDelete = true }
            }
        }
        .frame(maxWidth: 600)
        .navigationTitle("Profile & Account")
        .alert("Delete account?", isPresented: $confirmDelete) {
            Button("Delete", role: .destructive) {
                store.deleteAccount()
                dismiss()
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This permanently removes your profile and synced data. This cannot be undone.")
        }
    }
}
