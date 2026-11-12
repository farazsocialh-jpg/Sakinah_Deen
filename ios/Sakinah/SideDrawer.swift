import SwiftUI

enum DrawerRoute: Identifiable {
    case auth, profile, settings, sources, help, privacy, about

    var id: String { String(describing: self) }
}

/// Slide-over side navigation shown from the root view's menu button.
struct SideDrawer: View {
    @ObservedObject var store: AccountStore
    @Binding var isOpen: Bool
    @State private var route: DrawerRoute?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
                .padding(.horizontal, 20)
                .padding(.top, 56)
                .padding(.bottom, 16)

            Divider().background(Color.emeraldSurface)

            ScrollView {
                VStack(alignment: .leading, spacing: 4) {
                    drawerItem("Profile & Account", systemImage: "person.crop.circle") {
                        open(store.isSignedIn ? .profile : .auth)
                    }
                    drawerItem("App Settings", systemImage: "gearshape") { open(.settings) }
                    drawerItem("Islamic Data Sources", systemImage: "books.vertical") { open(.sources) }
                    drawerItem("Help, Feedback & Bug Report", systemImage: "questionmark.circle") { open(.help) }
                    drawerItem("Privacy & Terms", systemImage: "hand.raised") { open(.privacy) }
                    drawerItem("About Sakinah", systemImage: "info.circle") { open(.about) }

                    if store.isSignedIn {
                        Divider().padding(.vertical, 8)
                        drawerItem("Sign Out", systemImage: "rectangle.portrait.and.arrow.right") {
                            store.signOut()
                            close()
                        }
                    }
                }
                .padding(.top, 8)
            }
        }
        .frame(width: min(UIScreen.main.bounds.width * 0.82, 320))
        .background(Color.warmCream.ignoresSafeArea())
        .sheet(item: $route) { route in
            NavigationStack {
                Group {
                    switch route {
                    case .auth: AuthView(store: store)
                    case .profile: ProfileView(store: store)
                    case .settings: SettingsView(store: store)
                    case .sources: InfoView(title: "Islamic Data Sources & Attribution",
                                            body: "Quran text: Tanzil / King Fahd Complex. Tafsir: Ibn Kathir, As-Sa'di, Al-Jalalayn. Hadith: nine canonical collections. Audio: Makkah adhan recordings.")
                    case .help: InfoView(title: "Help, Feedback & Bug Report",
                                         body: "Send feedback to support@sakinah.app and include your app version and device model.")
                    case .privacy: InfoView(title: "Privacy Policy & Terms",
                                            body: "Guest data stays on device. Signed-in profiles sync only what you choose to share.")
                    case .about: InfoView(title: "About Sakinah",
                                          body: "Version 1.0.0 (1)\nOpen-source licenses: see NOTICE in the repository.")
                    }
                }
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close") { route = nil }
                    }
                }
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            if store.isSignedIn {
                Circle()
                    .fill(Color.emeraldSurface)
                    .frame(width: 56, height: 56)
                    .overlay(Text(String(store.displayName.prefix(1))).font(.title2.bold()).foregroundColor(.white))
                Text(store.displayName.isEmpty ? "Sakinah member" : store.displayName)
                    .font(.headline)
                    .foregroundColor(.emeraldDeep)
                Text(store.email)
                    .font(.caption)
                    .foregroundColor(.darkCharcoal.opacity(0.7))
                Text("\(store.defaultCity.isEmpty ? "No city set" : store.defaultCity) · \(store.madhhab.capitalized)")
                    .font(.caption2.bold())
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Color.mintGreen.opacity(0.18))
                    .clipShape(Capsule())
            } else {
                Text("Assalamu alaikum")
                    .font(.headline)
                    .foregroundColor(.emeraldDeep)
                Text("Sign in to sync your journey, or continue offline as a guest.")
                    .font(.caption)
                    .foregroundColor(.darkCharcoal.opacity(0.7))
                Button {
                    open(.auth)
                } label: {
                    Text("Sign In / Create Account")
                        .font(.footnote.bold())
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                }
                .background(Color.emeraldDeep)
                .foregroundColor(.white)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
    }

    private func drawerItem(_ title: String, systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .frame(width: 22)
                    .foregroundColor(.emeraldDeep)
                Text(title)
                    .font(.subheadline)
                    .foregroundColor(.darkCharcoal)
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func open(_ destination: DrawerRoute) {
        route = destination
        close()
    }

    private func close() {
        withAnimation(.easeInOut(duration: 0.22)) { isOpen = false }
    }
}

struct InfoView: View {
    let title: String
    let body: String

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(title).font(.title2.bold()).foregroundColor(.emeraldDeep)
                Text(body).font(.body).foregroundColor(.darkCharcoal)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
        .background(Color.warmCream.ignoresSafeArea())
    }
}
