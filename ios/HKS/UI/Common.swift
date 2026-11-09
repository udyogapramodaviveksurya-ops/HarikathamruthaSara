import SwiftUI

struct Bouncy: ButtonStyle {
    func makeBody(configuration c: Configuration) -> some View {
        c.label.scaleEffect(c.isPressed ? 0.95 : 1).animation(.spring(response: 0.3, dampingFraction: 0.5), value: c.isPressed)
    }
}

/// A card that squishes a little when pressed: the app's main tappable surface.
struct BouncyCard<Content: View>: View {
    var color: Color?
    var fg: Color?
    var radius: CGFloat = 20
    var enabled = true
    var border: (Color, CGFloat)?
    let action: () -> Void
    @ViewBuilder let content: () -> Content
    @Environment(\.cs) private var cs
    var body: some View {
        Button(action: action) {
            content().frame(maxWidth: .infinity, alignment: .leading).foregroundStyle(fg ?? cs.onSurface)
                .background(color ?? cs.high, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
                .overlay { if let border { RoundedRectangle(cornerRadius: radius, style: .continuous).strokeBorder(border.0, lineWidth: border.1) } }
                .contentShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
        }.buttonStyle(Bouncy()).disabled(!enabled)
    }
}

struct FilledButton: View {
    let title: String
    var color: Color?
    var fg: Color?
    var height: CGFloat = 48
    var fill = false
    var size: CGFloat = 16
    var enabled = true
    let action: () -> Void
    @Environment(\.cs) private var cs
    var body: some View {
        Button(action: action) {
            Text(title).font(.hk(size, .semibold)).multilineTextAlignment(.center).padding(.horizontal, 20).padding(.vertical, 6)
                .frame(maxWidth: fill ? .infinity : nil, minHeight: height).foregroundStyle(fg ?? cs.onPrimary)
                .background(enabled ? (color ?? cs.primary) : cs.outlineVariant, in: Capsule())
                .contentShape(Capsule())
        }.buttonStyle(Bouncy()).disabled(!enabled)
    }
}

struct OutlineButton: View {
    let title: String
    var fill = false
    var height: CGFloat = 44
    var enabled = true
    let action: () -> Void
    @Environment(\.cs) private var cs
    var body: some View {
        Button(action: action) {
            Text(title).font(.hk(15, .semibold)).multilineTextAlignment(.center).padding(.horizontal, 18).padding(.vertical, 6)
                .frame(maxWidth: fill ? .infinity : nil, minHeight: height).foregroundStyle(enabled ? cs.primary : cs.outline)
                .overlay(Capsule().strokeBorder(cs.outline, lineWidth: 1)).contentShape(Capsule())
        }.buttonStyle(Bouncy()).disabled(!enabled)
    }
}

struct RoundIcon: View {
    let system: String
    var size: CGFloat = 56
    var icon: CGFloat = 26
    var bg: Color?
    var fg: Color?
    var label: String
    var enabled = true
    let action: () -> Void
    @Environment(\.cs) private var cs
    var body: some View {
        Button(action: action) {
            Image(systemName: system).font(.system(size: icon, weight: .semibold)).foregroundStyle(fg ?? cs.onSecondaryContainer)
                .frame(width: size, height: size).background(bg ?? cs.secondaryContainer, in: Circle()).opacity(enabled ? 1 : 0.4)
        }.buttonStyle(Bouncy()).disabled(!enabled).accessibilityLabel(label)
    }
}

struct Pill: View {
    let text: String
    var bg: Color?
    var fg: Color?
    @Environment(\.cs) private var cs
    var body: some View {
        Text(text).font(.hk(13, .semibold)).lineLimit(1).padding(.horizontal, 10).padding(.vertical, 4)
            .foregroundStyle(fg ?? cs.onSecondaryContainer).background(bg ?? cs.secondaryContainer, in: Capsule())
    }
}

struct Stars: View {
    let n: Int
    var size: CGFloat = 16
    var body: some View {
        Text(String(repeating: "★", count: n) + String(repeating: "☆", count: max(3 - n, 0))).font(.hk(size)).foregroundStyle(HK.gold)
    }
}

struct BrandLogo: View {
    let size: CGFloat
    var body: some View {
        Image("BrandLogo").resizable().scaledToFit().frame(width: size, height: size).clipShape(RoundedRectangle(cornerRadius: size * 0.16, style: .continuous)).accessibilityHidden(true)
    }
}

struct HBar: View {
    let value: Double
    var height: CGFloat = 8
    let color: Color
    let track: Color
    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .leading) {
                Capsule().fill(track)
                Capsule().fill(color).frame(width: g.size.width * min(max(value, 0), 1))
            }
        }.frame(height: height)
    }
}

struct SectionTitle: View {
    let text: String
    let tone: Tone
    @Environment(\.cs) private var cs
    var body: some View {
        HStack(spacing: 10) {
            Capsule().fill(tone.accent).frame(width: 6, height: 22)
            Text(text).font(.hk(17, .bold)).foregroundStyle(cs.onSurface)
        }.padding(.top, 12).padding(.bottom, 4)
    }
}

struct BigTile: View {
    let emoji: String, title: String, sub: String
    let tone: Tone
    let action: () -> Void
    var body: some View {
        BouncyCard(color: tone.container, fg: tone.on, radius: 28, action: action) {
            VStack(alignment: .leading, spacing: 0) {
                Text(emoji).font(.hk(30)).frame(width: 56, height: 56).background(tone.accent.opacity(0.28), in: Circle())
                Spacer().frame(height: 10)
                Text(title).font(.hk(17, .bold)).multilineTextAlignment(.leading)
                Text(sub).font(.hk(13)).foregroundStyle(tone.on.opacity(0.78)).multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }.padding(16).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(alignment: .topTrailing) { Circle().fill(tone.accent.opacity(0.18)).frame(width: 110, height: 110).offset(x: 40, y: -40) }
            .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        }
    }
}

struct ProgressRing: View {
    let fraction: Double, label: String, sub: String
    let tone: Tone, end: Color
    var body: some View {
        ZStack {
            Circle().stroke(tone.accent.opacity(0.18), style: .init(lineWidth: 10))
            Circle().trim(from: 0, to: min(max(fraction, 0), 1)).stroke(AngularGradient(colors: [tone.accent, end, tone.accent], center: .center), style: .init(lineWidth: 10, lineCap: .round)).rotationEffect(.degrees(-90))
            VStack(spacing: 0) {
                Text(label).font(.hk(18, .bold))
                Text(sub).font(.hk(12)).opacity(0.8).lineLimit(1).minimumScaleFactor(0.6)
            }.padding(10)
        }.padding(5)
    }
}

/// Wraps its children like words on a line.
struct Flow: Layout {
    var spacing: CGFloat = 8
    var lineSpacing: CGFloat = 8
    var center = false

    private func rows(_ w: CGFloat, _ subs: Subviews) -> [(items: [(Int, CGSize)], width: CGFloat, height: CGFloat)] {
        var out: [(items: [(Int, CGSize)], width: CGFloat, height: CGFloat)] = [([], 0, 0)]
        for (i, s) in subs.enumerated() {
            let z = s.sizeThatFits(.init(width: w, height: nil))
            let adv = (out[out.count - 1].items.isEmpty ? 0 : spacing) + z.width
            if out[out.count - 1].width + adv > w + 0.5 && !out[out.count - 1].items.isEmpty { out.append(([], 0, 0)) }
            let k = out.count - 1
            out[k].width += (out[k].items.isEmpty ? 0 : spacing) + z.width
            out[k].height = max(out[k].height, z.height)
            out[k].items.append((i, z))
        }
        return out
    }
    func sizeThatFits(proposal p: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let w = p.width ?? .greatestFiniteMagnitude
        let r = rows(w, subviews)
        let h = r.reduce(0) { $0 + $1.height } + CGFloat(max(r.count - 1, 0)) * lineSpacing
        return CGSize(width: p.width ?? (r.map(\.width).max() ?? 0), height: h)
    }
    func placeSubviews(in b: CGRect, proposal p: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = b.minY
        for row in rows(b.width, subviews) {
            var x = center ? b.minX + (b.width - row.width) / 2 : b.minX
            for (i, z) in row.items {
                subviews[i].place(at: .init(x: x, y: y + (row.height - z.height) / 2), proposal: .init(z))
                x += z.width + spacing
            }
            y += row.height + lineSpacing
        }
    }
}

struct TileGrid<T, Cell: View>: View {
    let items: [T]
    let columns: Int
    var gap: CGFloat = 12
    @ViewBuilder let cell: (T) -> Cell
    var body: some View {
        VStack(spacing: gap) {
            ForEach(0..<(items.count + columns - 1) / columns, id: \.self) { r in
                HStack(alignment: .top, spacing: gap) {
                    ForEach(0..<columns, id: \.self) { c in
                        let i = r * columns + c
                        if i < items.count { cell(items[i]).frame(maxWidth: .infinity, maxHeight: .infinity) } else { Color.clear.frame(maxWidth: .infinity) }
                    }
                }.fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

extension View {
    func readable(_ max: CGFloat = 720) -> some View { frame(maxWidth: max).frame(maxWidth: .infinity) }
    func card(_ color: Color, radius: CGFloat = 28) -> some View { background(color, in: RoundedRectangle(cornerRadius: radius, style: .continuous)) }
}

let alsoScripts: [(Script, String)] = [(.devanagari, "देवनागरी"), (.tamil, "தமிழ்"), (.telugu, "తెలుగు"), (.latin, "Latin"), (.kannada, "ಕನ್ನಡ")]

struct Chip: View {
    let text: String
    let selected: Bool
    var enabled = true
    let action: () -> Void
    @Environment(\.cs) private var cs
    var body: some View {
        Button(action: action) {
            Text((selected ? "✓ " : "") + text).font(.hk(15, selected ? .semibold : .regular)).padding(.horizontal, 14).padding(.vertical, 10)
                .frame(minHeight: 44)
                .foregroundStyle(selected ? cs.onSecondaryContainer : cs.onSurfaceVariant).background(selected ? cs.secondaryContainer : .clear, in: RoundedRectangle(cornerRadius: 12))
                .overlay { if !selected { RoundedRectangle(cornerRadius: 12).strokeBorder(cs.outlineVariant) } }
                .contentShape(Rectangle()).opacity(enabled ? 1 : 0.5)
        }.buttonStyle(.plain).disabled(!enabled)
    }
}

struct AlsoScriptsPicker: View {
    @Environment(HkApp.self) private var app
    var body: some View {
        Flow {
            ForEach(alsoScripts.filter { $0.0 != app.script && $0.0 != .kannada }, id: \.0) { sc, name in
                Chip(text: name, selected: app.settings.value.alsoScripts.contains(sc)) {
                    app.settings.update { if $0.alsoScripts.contains(sc) { $0.alsoScripts.remove(sc) } else { $0.alsoScripts.insert(sc) } }
                }
            }
        }
    }
}

enum WordLook { case normal, inStep, current, good, bad, sound, next, hidden }

/// One verse in Kannada, the reader's script and any extra scripts. Each word is its own tappable piece.
struct VerseView: View {
    let verse: Verse
    var fontScale: Double
    let look: (Int) -> WordLook
    var hideMine = false
    var onlyLines: Set<Int>?
    var onWordTap: ((Int) -> Void)?
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs

    var body: some View {
        let st = app.settings.value, reader = app.script, mode = st.scriptMode
        let showKn = mode != .mine || reader == .kannada
        let showMine = reader != .kannada && mode != .kannada && !hideMine
        let mineOnly = showMine && !showKn
        let extras: [Script] = (mode == .kannada || hideMine) ? [] : alsoScripts.map(\.0).filter { st.alsoScripts.contains($0) && $0 != .kannada && !($0 == reader && (showMine || reader == .kannada)) }
        VStack(alignment: .leading, spacing: 10) {
            ForEach(verse.lines.indices, id: \.self) { li in
                if onlyLines?.contains(li) ?? true {
                    VStack(alignment: .leading, spacing: 2) {
                        if showKn { lineRow(li, .kannada, 23, cs.onSurface) }
                        if showMine { lineRow(li, reader, mineOnly ? 22 : 17, mineOnly ? cs.onSurface : cs.secondary) }
                        ForEach(extras, id: \.self) { lineRow(li, $0, 16, cs.tertiary) }
                    }.id("line\(li)")
                }
            }
        }
    }

    private func lineRow(_ li: Int, _ sc: Script, _ size: CGFloat, _ base: Color) -> some View {
        let line = verse.lines[li], refs = verse.words.filter { $0.line == li }
        let sup = app.settings.value.tamilSuperscripts
        let scaled = size * fontScale
        return Flow(spacing: 0, lineSpacing: scaled * 0.28) {
            ForEach(refs, id: \.global) { r in
                let l = look(r.global), w = Scripts.render(r.text, sc, sup)
                HStack(spacing: 0) {
                    wordText(w, l, base, scaled)
                    let sep = line.sepAfter(r.index)
                    if !sep.isEmpty { Text(sep.replacingOccurrences(of: " ", with: "\u{00A0}")).font(.hk(scaled)).foregroundStyle(cs.outline) }
                }
                .contentShape(Rectangle())
                .onTapGesture { onWordTap?(r.global) }
                .accessibilityAddTraits(onWordTap == nil ? [] : .isButton)
            }
        }
    }

    @ViewBuilder private func wordText(_ w: String, _ l: WordLook, _ base: Color, _ size: CGFloat) -> some View {
        let (bg, fg, weight): (Color?, Color, Font.Weight) = {
            switch l {
            case .normal: (nil, base, .regular)
            case .inStep: (cs.tertiaryContainer, cs.onTertiaryContainer, .regular)
            case .current: (cs.primary, cs.onPrimary, .bold)
            case .good: (HK.goodBg, HK.good, .semibold)
            case .bad: (HK.badBg, HK.bad, .semibold)
            case .sound: (HK.soundBg, HK.sound, .semibold)
            case .next: (cs.tertiaryContainer, cs.onTertiaryContainer, .bold)
            case .hidden: (nil, cs.outline, .regular)
            }
        }()
        let text = l == .hidden ? String(repeating: "•", count: min(max(w.length / 2, 2), 6)) : w
        Text(text).font(.hk(size, weight)).foregroundStyle(fg).fixedSize().padding(.horizontal, 1)
            .background { if let bg { RoundedRectangle(cornerRadius: 5).fill(bg) } }
    }
}

struct Offering: View {
    var size: CGFloat = 22
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    var body: some View {
        VStack(spacing: 2) {
            Text("🙏").font(.hk(size + 6))
            Text(app.S.krishnarpanam).font(.hk(size, .bold)).foregroundStyle(cs.primary)
            if app.script != .kannada { Text(app.mine(app.S.krishnarpanam)).font(.hk(size - 6)).foregroundStyle(cs.secondary) }
        }.frame(maxWidth: .infinity)
    }
}

struct Confetti: View {
    @State private var start = Date()
    private let parts: [(x: Double, speed: Double, color: Color, rot: Double)] = {
        let c = [0xFFC93C, 0xFF6B2C, 0xE0457B, 0x0E9A97, 0x3D6FE0, 0x8752D6, 0x3E9C48].map { Color(hex: UInt32($0)) }
        return (0..<70).map { (.random(in: 0...1), .random(in: 0.4...1), c[$0 % c.count], .random(in: 0...360)) }
    }()
    var body: some View {
        TimelineView(.animation) { tl in
            Canvas { ctx, size in
                let t = min(tl.date.timeIntervalSince(start) / 2.6, 1)
                guard t < 1 else { return }
                for (i, p) in parts.enumerated() {
                    let angle = Double(i * 37 % 360) * .pi / 180
                    let x = size.width * p.x + sin(angle + t * 8) * 30
                    let y = -40 + t * p.speed * size.height * 1.3
                    var c = ctx
                    c.translateBy(x: x, y: y); c.rotate(by: .degrees(p.rot + t * 720 * p.speed))
                    c.fill(Path(CGRect(x: 0, y: 0, width: 9, height: 6)), with: .color(p.color.opacity(1 - t + 0.2)))
                }
            }
        }.allowsHitTesting(false)
    }
}

struct RewardDialog: View {
    let reward: Reward
    let onDismiss: () -> Void
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    @State private var pop = 0.3
    var body: some View {
        let S = app.S
        let c: (String, String, String) = {
            switch reward {
            case .badge(let b): (b.emoji, S.newBadge, "\(S.badgeTitle(b.id))\n\(S.badgeHint(b.id))")
            case .levelUp(let l): (l.emoji, S.levelUp, S.nowLevel(S.levelNames[LEVELS.firstIndex(of: l)!]))
            case .goalMet(let m): ("🎯", S.goalDone, S.goalDoneBody(m) + "\n\n" + S.krishnarpanam + " 🙏")
            }
        }()
        ZStack {
            Color.black.opacity(0.45).ignoresSafeArea().onTapGesture(perform: onDismiss)
            VStack(spacing: 12) {
                Text(c.0).font(.hk(68)).scaleEffect(pop).frame(width: 120, height: 120).background(cs.tones.marigold.container, in: Circle())
                Text(c.1).font(.hk(24, .bold)).foregroundStyle(cs.primary).multilineTextAlignment(.center)
                Text(c.2).multilineTextAlignment(.center).foregroundStyle(cs.onSurface)
                FilledButton(title: S.ok + " 🙏", action: onDismiss)
            }.padding(28).frame(width: 316).card(cs.high, radius: 36)
            Confetti().ignoresSafeArea()
        }.onAppear { withAnimation(.spring(response: 0.5, dampingFraction: 0.35)) { pop = 1 } }
    }
}

func tagged(_ S: Strs, _ st: Step) -> String {
    switch st.kind {
    case .word: S.stepWord(st.n); case .line: S.stepLine(st.n); case .part: S.stepPart(st.n); case .whole: S.stepWhole; case .lines: S.stepLines(st.n)
    }
}
extension Strs {
    func verseLabel(_ v: Verse) -> String { v.isRefrain ? refrain : padya(v.number) }
    func levelName(_ l: Level) -> String { levelNames[LEVELS.firstIndex(of: l)!] }
    func unitName(_ u: StudyUnit) -> String { switch u { case .line: unitLine; case .sentence: unitHalf; case .verse: unitVerse; case .buildup: unitBuild } }
    func unitSub(_ u: StudyUnit) -> String { switch u { case .line: unitLineSub; case .sentence: unitHalfSub; case .verse: unitVerseSub; case .buildup: unitBuildSub } }
}

/// The two-line inline title used by every screen except Home.
struct BarModifier<Actions: View>: ViewModifier {
    let title: String
    var subtitle: String?
    let top: Bool
    @ViewBuilder let actions: () -> Actions
    @Environment(\.cs) private var cs
    func body(content: Content) -> some View {
        content.background(cs.background.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarBackButtonHidden(top)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    VStack(spacing: 0) {
                        Text(short(title)).font(.hk(17, .bold)).foregroundStyle(cs.onSurface).lineLimit(1).minimumScaleFactor(0.7)
                        if let subtitle { Text(subtitle).font(.hk(12)).foregroundStyle(cs.onSurfaceVariant).lineLimit(1).minimumScaleFactor(0.7) }
                    }
                }
                ToolbarItemGroup(placement: .topBarTrailing) { actions() }
            }
    }
}
extension View {
    func bar<A: View>(_ title: String, _ subtitle: String? = nil, top: Bool = false, @ViewBuilder actions: @escaping () -> A = { EmptyView() }) -> some View {
        modifier(BarModifier(title: title, subtitle: subtitle, top: top, actions: actions))
    }
}
