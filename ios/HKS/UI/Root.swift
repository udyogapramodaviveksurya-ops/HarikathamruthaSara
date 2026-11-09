import SwiftUI

enum SettingsPage: Hashable { case look, language, notify, goal, chant, reading, check, source }

enum Screen: Hashable {
    case library(forRecite: Bool), verses(Int, forRecite: Bool), learn, recite(Int, Int), quiz, letters, progress, settings, credits, sub(SettingsPage), account
    var top: TopDest? {
        switch self {
        case .library(let r): r ? nil : .listen
        case .quiz: .games; case .letters: .letters; case .progress: .progress
        default: nil
        }
    }
}

enum TopDest: CaseIterable { case home, listen, games, letters, progress
    var screen: Screen? { switch self { case .home: nil; case .listen: .library(forRecite: false); case .games: .quiz; case .letters: .letters; case .progress: .progress } }
}

@MainActor @Observable
final class Nav {
    var path: [Screen] = []
    var current: TopDest? { path.isEmpty ? .home : path.last?.top }
    func go(_ s: Screen) { path.append(s) }
    func back() { if !path.isEmpty { path.removeLast() } }
    /// Jump to a top-level destination: it sits right above Home, so Back always leads home.
    func top(_ d: TopDest) {
        var t = Transaction(); t.disablesAnimations = true
        withTransaction(t) { path = d.screen.map { [$0] } ?? [] }
    }
}

struct RootView: View {
    @Environment(HkApp.self) private var app
    @Environment(\.colorScheme) private var system
    @State private var nav = Nav()
    @State private var tour = TourController()

    var body: some View {
        let mode = app.settings.value.darkMode
        GeometryReader { g in
            themed(g.size.width)
        }
        .preferredColorScheme(mode == .system ? nil : mode == .dark ? .dark : .light)
    }

    private func themed(_ width: CGFloat) -> some View {
        let cs = system == .dark ? CS.dark : CS.light
        return ZStack {
            cs.background.ignoresSafeArea()
            if app.settings.value.lang == nil { LanguagePicker() }
            else if let lib = app.library { frame(lib, wide: width >= 600, cs: cs) }
            else { ProgressView() }
            TourOverlay(c: tour)
            if let r = app.progress.rewards.first { RewardDialog(reward: r) { app.progress.rewards.removeFirst() }.id(r.id) }
        }
        .tint(cs.primary)
        .environment(\.cs, cs)
        .environment(\.wide, width >= 600)
        .environment(\.veryWide, width >= 840)
        .environment(\.tour, tour)
        .environment(nav)
    }

    @ViewBuilder private func frame(_ lib: [Sandhi], wide: Bool, cs: CS) -> some View {
        let stack = NavigationStack(path: $nav.path) {
            HomeScreen(lib: lib).navigationDestination(for: Screen.self) { ScreenHost(screen: $0, lib: lib) }
        }
        if wide {
            HStack(spacing: 0) {
                if nav.current != nil { NavRail().transition(.move(edge: .leading)) }
                stack
            }
        } else {
            VStack(spacing: 0) {
                stack
                if nav.current != nil { NavBar().transition(.move(edge: .bottom)) }
            }
        }
    }
}

private struct ScreenHost: View {
    let screen: Screen
    let lib: [Sandhi]
    private func sandhi(_ id: Int) -> Sandhi { lib.first { $0.id == id } ?? lib[0] }
    var body: some View {
        switch screen {
        case .library(let r): LibraryScreen(lib: lib, forRecite: r)
        case .verses(let id, let r): VersesScreen(sandhi: sandhi(id), forRecite: r)
        case .learn: LearnScreen()
        case .recite(let id, let v): ReciteScreen(sandhi: sandhi(id), verseIdx: v)
        case .quiz: QuizScreen(lib: lib)
        case .letters: LettersScreen()
        case .progress: ProgressScreen()
        case .settings: SettingsScreen()
        case .credits: CreditsScreen()
        case .sub(let p): SettingsPageScreen(page: p)
        case .account: AccountScreen()
        }
    }
}

private struct DestItem: View {
    let d: TopDest
    @Environment(HkApp.self) private var app
    @Environment(Nav.self) private var nav
    @Environment(\.cs) private var cs
    private var tone: Tone {
        switch d { case .home: cs.tones.marigold; case .listen: cs.tones.lotus; case .games: cs.tones.peacock; case .letters: cs.tones.sky; case .progress: cs.tones.leaf }
    }
    private var label: String {
        switch d { case .home: app.S.navHome; case .listen: app.S.navListen; case .games: app.S.navGames; case .letters: app.S.navLetters; case .progress: app.S.navProgress }
    }
    var body: some View {
        let sel = nav.current == d
        Button { nav.top(d) } label: {
            VStack(spacing: 3) {
                Group {
                    switch d {
                    case .home: Image(systemName: "house.fill")
                    case .listen: Image(systemName: "headphones")
                    case .games: Image(systemName: "dice.fill")
                    case .letters: Text("ಅ").font(.hk(20, .bold))
                    case .progress: Image(systemName: "trophy.fill")
                    }
                }
                .font(.system(size: 19)).foregroundStyle(sel ? tone.on : cs.onSurfaceVariant)
                .frame(width: 62, height: 32).background(sel ? tone.container : .clear, in: Capsule())
                Text(label).font(.hk(11, sel ? .bold : .medium)).lineLimit(1).minimumScaleFactor(0.65).foregroundStyle(cs.onSurface)
            }.frame(maxWidth: .infinity, minHeight: 56).contentShape(Rectangle())
        }.buttonStyle(.plain).accessibilityLabel(label).accessibilityAddTraits(sel ? .isSelected : [])
    }
}

private struct NavBar: View {
    @Environment(\.cs) private var cs
    var body: some View {
        HStack(spacing: 0) { ForEach(TopDest.allCases, id: \.self) { DestItem(d: $0) } }
            .padding(.horizontal, 6).padding(.top, 8).padding(.bottom, 2)
            .background(cs.container.ignoresSafeArea(edges: .bottom))
            .tourTarget(TT.nav)
    }
}

private struct NavRail: View {
    @Environment(\.cs) private var cs
    var body: some View {
        VStack(spacing: 8) {
            BrandLogo(size: 48).padding(.top, 12).padding(.bottom, 12)
            ForEach(TopDest.allCases, id: \.self) { DestItem(d: $0) }
            Spacer()
        }
        .frame(width: 88).frame(maxHeight: .infinity)
        .background(cs.container.ignoresSafeArea())
        .tourTarget(TT.nav)
    }
}

/// First launch: everything else in the app follows this choice.
struct LanguagePicker: View {
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                BrandLogo(size: 180).padding(.top, 16)
                Text(HK.appName).font(.hk(26, .bold)).foregroundStyle(cs.primary).multilineTextAlignment(.center)
                Text("ಹರಿಕಥಾಮೃತಸಾರ").font(.hk(22)).foregroundStyle(cs.secondary)
                Text("Choose your language · மொழி · ಭಾಷೆ · भाषा · భాష · भाषा (संस्कृतम्)").font(.hk(16, .semibold)).multilineTextAlignment(.center).foregroundStyle(cs.onSurface).padding(.top, 8)
                ForEach(Array(Lang.allCases.enumerated()), id: \.1) { i, l in
                    let tone = cs.tones[i]
                    BouncyCard(color: tone.container, fg: tone.on, radius: 28, action: { app.settings.update { $0.lang = l } }) {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(l.native).font(.hk(24, .bold))
                                Text(l.english + " · " + Scripts.render("ಹರಿಕಥಾಮೃತಸಾರ", l.script)).font(.hk(14)).foregroundStyle(tone.on.opacity(0.8))
                            }
                            Spacer()
                            Text("›").font(.hk(30, .bold)).foregroundStyle(tone.accent)
                        }.padding(18)
                    }
                }
            }.padding(24).readable(560)
        }
    }
}
