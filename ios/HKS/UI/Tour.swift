import SwiftUI

struct TourStep { let target: String?; let title: String; let body: String; var tap = false }
struct Tour { let id: String; let steps: [TourStep] }

enum TT {
    static let homeContinue = "home_continue", homeToday = "home_today", homeStreak = "home_streak", homeTiles = "home_tiles", homeSettings = "home_settings", nav = "nav_bar"
    static let learnVerse = "learn_verse", learnNav = "learn_nav", learnPlay = "learn_play", learnChips = "learn_chips", learnRepeat = "learn_repeat", learnRecite = "learn_recite", learnMore = "learn_more"
    static let reciteVerse = "recite_verse", reciteHear = "recite_hear", reciteMic = "recite_mic", reciteHands = "recite_hands"
    static let libFirst = "lib_first", versesFirst = "verses_first", versesAll = "verses_all"
    static let quizQ = "quiz_q", quizSandhi = "quiz_sandhi", quizAnswers = "quiz_answers"
    static let lettersTabs = "letters_tabs", lettersFirst = "letters_first"
    static let progStats = "prog_stats", progChart = "prog_chart", progBadges = "prog_badges"
}

@MainActor enum Tours {
    private static func s(_ t: String?, _ a: String, _ b: String, tap: Bool = false) -> TourStep { TourStep(target: t, title: a, body: b, tap: tap) }
    static func home(_ T: TourText) -> Tour { Tour(id: "home", steps: [s(nil, T.h1t, T.h1b), s(TT.homeContinue, T.h2t, T.h2b), s(TT.homeToday, T.h3t, T.h3b), s(TT.homeStreak, T.h4t, T.h4b), s(TT.homeTiles, T.h5t, T.h5b), s(TT.nav, T.h6t, T.h6b), s(TT.homeSettings, T.h7t, T.h7b), s(TT.homeContinue, T.h8t, T.h8b, tap: true)]) }
    static func learn(_ T: TourText) -> Tour { Tour(id: "learn", steps: [s(TT.learnVerse, T.l1t, T.l1b), s(TT.learnNav, T.l2t, T.l2b), s(TT.learnPlay, T.l3t, T.l3b, tap: true), s(TT.learnChips, T.l4t, T.l4b), s(TT.learnRepeat, T.l5t, T.l5b, tap: true), s(TT.learnRecite, T.l6t, T.l6b), s(TT.learnMore, T.l7t, T.l7b)]) }
    static func recite(_ T: TourText) -> Tour { Tour(id: "recite", steps: [s(TT.reciteVerse, T.r1t, T.r1b), s(TT.reciteHear, T.r2t, T.r2b), s(TT.reciteMic, T.r3t, T.r3b), s(TT.reciteHands, T.r4t, T.r4b)]) }
    static func library(_ T: TourText) -> Tour { Tour(id: "library", steps: [s(TT.libFirst, T.b1t, T.b1b, tap: true)]) }
    static func verses(_ T: TourText) -> Tour { Tour(id: "verses", steps: [s(TT.versesAll, T.v2t, T.v2b), s(TT.versesFirst, T.v1t, T.v1b, tap: true)]) }
    static func quiz(_ T: TourText) -> Tour { Tour(id: "quiz", steps: [s(TT.quizQ, T.q1t, T.q1b), s(TT.quizSandhi, T.q2t, T.q2b), s(TT.quizAnswers, T.q3t, T.q3b, tap: true)]) }
    static func letters(_ T: TourText) -> Tour { Tour(id: "letters", steps: [s(TT.lettersTabs, T.k1t, T.k1b), s(TT.lettersFirst, T.k2t, T.k2b, tap: true)]) }
    static func progress(_ T: TourText) -> Tour { Tour(id: "progress", steps: [s(TT.progStats, T.p1t, T.p1b), s(TT.progChart, T.p2t, T.p2b), s(TT.progBadges, T.p3t, T.p3b)]) }
}

/// Runs first-run guides: knows where every target is, which tour is showing, and which were already seen.
@MainActor @Observable
final class TourController {
    var active: Tour?
    var index = 0
    var bounds: [String: CGRect] = [:]
    @ObservationIgnored private var latest: [String: CGRect] = [:]
    @ObservationIgnored private let d = UserDefaults.standard
    @ObservationIgnored private var seenSet: Set<String>
    /// UI tests and screenshots launch with this flag so no guide covers the screen.
    @ObservationIgnored private let disabled = ProcessInfo.processInfo.arguments.contains("-noTours")

    init() { seenSet = Set(UserDefaults.standard.stringArray(forKey: "tours.seen") ?? []) }

    var step: TourStep? { active.flatMap { index < $0.steps.count ? $0.steps[index] : nil } }
    func seen(_ id: String) -> Bool { disabled || seenSet.contains(id) }
    func start(_ t: Tour) { guard !t.steps.isEmpty else { return }; index = 0; bounds = latest; active = t }
    func next() { guard let t = active else { return }; if index < t.steps.count - 1 { index += 1 } else { finish() } }
    func prev() { if index > 0 { index -= 1 } }
    func finish() {
        if let t = active { seenSet.insert(t.id); d.set(Array(seenSet), forKey: "tours.seen") }
        active = nil
    }
    func resetAll() { seenSet = []; d.removeObject(forKey: "tours.seen") }
    func report(_ id: String, _ r: CGRect) {
        latest[id] = r
        if active != nil && bounds[id] != r { bounds[id] = r }
    }
    func gone(_ id: String) { latest[id] = nil; bounds[id] = nil }
    func tapped(_ id: String) { if let s = step, s.tap, s.target == id { next() } }
}

extension View {
    func tourTarget(_ id: String) -> some View { modifier(TourTarget(id: id)) }
}

private struct TourTarget: ViewModifier {
    let id: String
    @Environment(\.tour) private var tour
    func body(content: Content) -> some View {
        content
            .id(id)
            .background {
                GeometryReader { g in
                    Color.clear
                        .onChange(of: g.frame(in: .global), initial: true) { _, f in tour?.report(id, f) }
                        .onDisappear { tour?.gone(id) }
                }
            }
            .simultaneousGesture(TapGesture().onEnded { tour?.tapped(id) })
    }
}

/// A scroll view that brings the guide's current target into view.
struct TourScroll<Content: View>: View {
    var showsIndicators = true
    @ViewBuilder let content: () -> Content
    @Environment(\.tour) private var tour
    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(showsIndicators: showsIndicators) { content() }
                .onChange(of: tour?.step?.target) { _, id in
                    guard let id else { return }
                    withAnimation { proxy.scrollTo(id, anchor: .center) }
                }
        }
    }
}

struct AutoTour: ViewModifier {
    let tour: Tour
    @Environment(\.tour) private var c
    func body(content: Content) -> some View {
        content.task(id: tour.id) {
            try? await Task.sleep(for: .milliseconds(700))
            guard !Task.isCancelled, let c, !c.seen(tour.id), c.active == nil else { return }
            c.start(tour)
        }
    }
}

struct TourHelp: View {
    let tour: Tour
    var tint: Color?
    @Environment(HkApp.self) private var app
    @Environment(\.tour) private var c
    @Environment(\.cs) private var cs
    var body: some View {
        Button { c?.start(tour) } label: { Image(systemName: "questionmark.circle").foregroundStyle(tint ?? cs.onSurfaceVariant) }
            .accessibilityLabel(app.T.showGuide)
    }
}

struct TourOverlay: View {
    let c: TourController
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs

    var body: some View {
        if let tour = c.active, let step = c.step {
            GeometryReader { g in
                let origin = g.frame(in: .global).origin
                let raw = step.target.flatMap { c.bounds[$0] }?.offsetBy(dx: -origin.x, dy: -origin.y).insetBy(dx: -8, dy: -8)
                let hole = raw.map { $0.intersection(CGRect(origin: .zero, size: g.size)) }.flatMap { $0.isNull ? nil : $0 }
                ZStack(alignment: .top) {
                    dim(hole)
                    blockers(hole, g.size)
                    card(tour, step).frame(maxWidth: 460).padding(.horizontal, 16)
                        .frame(maxWidth: .infinity)
                        .position(cardPos(hole, g))
                }
            }.ignoresSafeArea()
        }
    }

    private func dim(_ hole: CGRect?) -> some View {
        TimelineView(.animation) { tl in
            Canvas { ctx, size in
                var bg = Path(CGRect(origin: .zero, size: size))
                if let hole { bg.addPath(Path(roundedRect: hole, cornerRadius: 20)) }
                ctx.fill(bg, with: .color(.black.opacity(0.62)), style: FillStyle(eoFill: true))
                if let hole {
                    let p = (sin(tl.date.timeIntervalSinceReferenceDate * 2.85) + 1) / 2
                    let g = 4 + p * 6
                    ctx.stroke(Path(roundedRect: hole.insetBy(dx: -g, dy: -g), cornerRadius: 20 + g), with: .color(cs.tones.marigold.accent.opacity(0.9 - p * 0.5)), lineWidth: 3)
                }
            }
        }.allowsHitTesting(false)
    }

    @ViewBuilder private func blockers(_ hole: CGRect?, _ s: CGSize) -> some View {
        let rects: [CGRect] = hole.map { h in
            [CGRect(x: 0, y: 0, width: s.width, height: h.minY), CGRect(x: 0, y: h.maxY, width: s.width, height: s.height - h.maxY),
             CGRect(x: 0, y: h.minY, width: h.minX, height: h.height), CGRect(x: h.maxX, y: h.minY, width: s.width - h.maxX, height: h.height)]
        } ?? [CGRect(origin: .zero, size: s)]
        ZStack(alignment: .topLeading) {
            Color.clear
            ForEach(rects.indices, id: \.self) { i in
                Color.black.opacity(0.001).frame(width: max(rects[i].width, 0), height: max(rects[i].height, 0)).offset(x: rects[i].minX, y: rects[i].minY)
            }
        }
    }

    private func cardPos(_ hole: CGRect?, _ g: GeometryProxy) -> CGPoint {
        let h = g.size.height, cx = g.size.width / 2, cardH: CGFloat = 250, safeT = g.safeAreaInsets.top + 60, safeB = g.safeAreaInsets.bottom + 40
        guard let hole else { return .init(x: cx, y: h / 2) }
        let room = h - hole.maxY
        if room >= hole.minY, room >= cardH { return .init(x: cx, y: hole.maxY + 16 + cardH / 2) }
        if hole.minY >= cardH { return .init(x: cx, y: hole.minY - 16 - cardH / 2) }
        return .init(x: cx, y: hole.midY > h / 2 ? safeT + cardH / 2 : h - safeB - cardH / 2)
    }

    private func card(_ tour: Tour, _ st: TourStep) -> some View {
        let t = cs.tones, T = app.T, i = c.index, n = tour.steps.count
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 4) {
                Text(T.stepOf(i + 1, n)).font(.hk(12, .bold)).padding(.horizontal, 10).padding(.vertical, 3).foregroundStyle(t.marigold.on).background(t.marigold.container, in: Capsule())
                Spacer()
                ForEach(0..<n, id: \.self) { k in Capsule().fill(k <= i ? t.marigold.accent : cs.outlineVariant).frame(width: k == i ? 18 : 7, height: 7) }
            }
            Text(st.title).font(.hk(21, .bold)).foregroundStyle(cs.onSurface)
            Text(st.body).font(.hk(16)).foregroundStyle(cs.onSurfaceVariant).fixedSize(horizontal: false, vertical: true)
            if st.tap { Text(T.tapHint).font(.hk(14, .semibold)).padding(.horizontal, 10).padding(.vertical, 6).foregroundStyle(t.leaf.on).background(t.leaf.container, in: RoundedRectangle(cornerRadius: 12)) }
            HStack(spacing: 8) {
                Button(T.skip) { c.finish() }.font(.hk(15, .semibold))
                Spacer()
                if i > 0 { OutlineButton(title: "‹ " + T.prev) { c.prev() } }
                FilledButton(title: i == n - 1 ? T.done : T.next + " ›", height: 44) { c.next() }
            }
        }.padding(18).card(cs.high).shadow(radius: 12)
    }
}
