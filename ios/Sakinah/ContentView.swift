import SwiftUI

struct ContentView: View {
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            DailySalahView()
                .tabItem {
                    Label("Salah", systemImage: "clock.fill")
                }
                .tag(0)

            QuranView()
                .tabItem {
                    Label("Quran", systemImage: "book.fill")
                }
                .tag(1)

            HadithView()
                .tabItem {
                    Label("Hadith", systemImage: "books.vertical.fill")
                }
                .tag(2)

            TravelView()
                .tabItem {
                    Label("Safar", systemImage: "location.north.circle.fill")
                }
                .tag(3)

            WealthView()
                .tabItem {
                    Label("Zakat", systemImage: "dollarsign.circle.fill")
                }
                .tag(4)

            AiScholarView()
                .tabItem {
                    Label("Scholar", systemImage: "message.fill")
                }
                .tag(5)
        }
        .tint(Color.emeraldDeep)
    }
}

struct DailySalahView: View {
    var body: some View {
        NavigationView {
            ZStack {
                Color.warmCream.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("14 Jumada al-Ula 1448 AH")
                            .font(.headline)
                            .foregroundColor(.emeraldDeep)
                        
                        // Hero Active Prayer Card
                        VStack(alignment: .leading, spacing: 8) {
                            Text("CURRENT / NEXT PRAYER")
                                .font(.caption.bold())
                                .foregroundColor(.mintGreen)
                            Text("ASR · العصر")
                                .font(.system(size: 28, weight: .bold, design: .serif))
                                .foregroundColor(.white)
                            Text("03:52 PM · Standard Calculation")
                                .font(.subheadline)
                                .foregroundColor(.white.opacity(0.8))
                        }
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(
                            LinearGradient(colors: [.emeraldDeep, .emeraldSurface], startPoint: .topLeading, endPoint: .bottomTrailing)
                        )
                        .cornerRadius(20)
                    }
                    .padding()
                }
            }
            .navigationTitle("Sakinah")
        }
    }
}

struct QuranView: View {
    var body: some View {
        NavigationView {
            Text("Quran & Multi-Tafseer Studio")
                .navigationTitle("Quran")
        }
    }
}

struct HadithView: View {
    var body: some View {
        NavigationView {
            Text("Nine Canonical Hadith Books")
                .navigationTitle("Hadith")
        }
    }
}

struct TravelView: View {
    var body: some View {
        NavigationView {
            Text("Safar & In-Vehicle Road Qibla")
                .navigationTitle("Safar")
        }
    }
}

struct WealthView: View {
    var body: some View {
        NavigationView {
            Text("Māl, Zakat & Sadaqah Hub")
                .navigationTitle("Zakat")
        }
    }
}

struct AiScholarView: View {
    var body: some View {
        NavigationView {
            Text("Sunni Scholar Research Assistant")
                .navigationTitle("Scholar")
        }
    }
}
