import SwiftUI

struct HomeScreen: View {
    let lib: [Sandhi]
    @Environment(HkApp.self) private var app
    @Environment(Nav.self) private var nav
    @Environment(\.cs) private var cs
    @Environment(\.veryWide) private var veryWide

    private func plant(_ s: Int) -> String { s >= 30 ? "🌳" : s >= 14 ? "🪴" : s >= 7 ? "🌿" : s >= 3 ? "☘️" : s >= 1 ? "🌱" : "💧" }

    var body: some View {
        let S = app.S, p = app.progress.state, t = cs.tones
        let tour = Tours.home(app.T)
        TourScroll {
            VStack(spacing: 14) {
                if veryWide {
                    HStack(alignment: .center, spacing: 14) { header(tour).layoutPriority(1.6); streak; todayCard }.fixedSize(horizontal: false, vertical: true)
                } else { header(tour) }
                UpdatesBanner()
                VagBanner(sandhiId: min(max(p.lastSandhi, 1), lib.count))
                if !veryWide { HStack(spacing: 12) { streak; todayCard }.fixedSize(horizontal: false, vertical: true) }
                continueCard
                TileGrid(items: tiles(S, p, t), columns: 2 + (app.isWideLayout ? 1 : 0)) { BigTile(emoji: $0.0, title: $0.1, sub: $0.2, tone: $0.3, action: $0.4) }.tourTarget(TT.homeTiles)
                verseOfDay
                Button { nav.go(.credits) } label: { Text("📖 Tadipatri Gurukula · 🎶 Vāgdhenu  ·  " + S.credits).font(.hk(13)) }
                Offering(size: 18)
            }.padding(16).readable(1100)
        }
        .background(cs.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .modifier(AutoTour(tour: tour))
    }

    private func tiles(_ S: Strs, _ p: ProgressSnapshot, _ t: Tones) -> [(String, String, String, Tone, () -> Void)] {
        [("🎧", S.listenLearn, S.listenLearnSub, t.lotus, { nav.top(.listen) }),
         ("🎤", S.reciteCheck, S.reciteCheckSub, t.marigold, { nav.go(.library(forRecite: true)) }),
         ("🎲", S.games, S.gamesSub, t.peacock, { nav.top(.games) }),
         ("ಅ", S.readKannada, S.readKannadaSub, t.sky, { nav.top(.letters) }),
         ("🏅", S.myProgress, S.progressSub(p.badges.count, p.mastered), t.leaf, { nav.top(.progress) }),
         ("⚙️", S.settings, S.settingsSub, t.berry, { nav.go(.settings) })]
    }

    private var streak: some View {
        let p = app.progress.state, S = app.S, tone = cs.tones.marigold
        return VStack(alignment: .leading, spacing: 2) {
            Text(plant(p.streak)).font(.hk(40))
            Text(S.streakDays(p.streak)).font(.hk(18, .bold))
            Text(S.bestDays(p.bestStreak)).font(.hk(12)).opacity(0.75)
            Text(S.streakTip(p.streak)).font(.hk(12)).opacity(0.75)
            Spacer(minLength: 0)
        }.padding(16).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading).foregroundStyle(tone.on).card(tone.container).tourTarget(TT.homeStreak)
    }

    private var todayCard: some View {
        let p = app.progress.state, S = app.S, goal = app.settings.value.dailyGoalMinutes, tone = cs.tones.peacock, m = p.todaySeconds / 60
        return VStack(spacing: 2) {
            ProgressRing(fraction: Double(m) / Double(goal), label: "\(m)/\(goal)", sub: S.minutes, tone: tone, end: cs.tones.leaf.accent).frame(width: 96, height: 96)
            Text(S.todayRepeats(p.todayRepeats)).font(.hk(12)).opacity(0.75).multilineTextAlignment(.center)
        }.padding(16).frame(maxWidth: .infinity, maxHeight: .infinity).foregroundStyle(tone.on).card(tone.container).tourTarget(TT.homeToday)
    }

    private func open(_ s: Sandhi, _ v: Verse, play: Bool = false) {
        app.player.open(s, s.verses.firstIndex(of: v) ?? 0)
        if play { app.player.play() }
        nav.go(.learn)
    }

    private var continueCard: some View {
        let p = app.progress.state, S = app.S
        let sandhi = lib.first { $0.id == p.lastSandhi } ?? lib[0]
        let verse = sandhi.verses.indices.contains(p.lastVerse) ? sandhi.verses[p.lastVerse] : sandhi.verses[0]
        return BouncyCard(color: cs.primaryContainer, fg: cs.onPrimaryContainer, radius: 28, action: { open(sandhi, verse) }) {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(S.continueWord) · \(sandhi.id). \(app.mine(sandhi.title))").font(.hk(16, .bold))
                    Text("\(S.verseLabel(verse)): \(app.mine(verse.firstLine))").lineLimit(1)
                    if app.script != .kannada { Text(verse.firstLine).font(.hk(14)).lineLimit(1).opacity(0.75) }
                }.frame(maxWidth: .infinity, alignment: .leading)
                RoundIcon(system: "play.fill", size: 64, icon: 30, bg: cs.primary, fg: cs.onPrimary, label: S.play) { open(sandhi, verse, play: true) }
            }.padding(16)
        }.tourTarget(TT.homeContinue)
    }

    private var verseOfDay: some View {
        let S = app.S, st = app.settings.value
        let all = lib.flatMap { s in s.verses.filter { !$0.isRefrain }.map { (s, $0) } }
        let (vs, vd) = all[today() % all.count]
        return BouncyCard(color: cs.low, radius: 28, border: (cs.tones.lotus.container, 2), action: { open(vs, vd) }) {
            VStack(alignment: .leading, spacing: 2) {
                Text("🌸 \(S.verseOfDay) · \(app.mine(vs.title)) \(vd.number)").font(.hk(16, .bold)).foregroundStyle(cs.secondary).padding(.bottom, 6)
                ForEach(vd.lines.prefix(3), id: \.self) { l in
                    Text(app.mine(l.text)).font(.hk(17))
                    if app.script != .kannada { Text(l.text).font(.hk(14)).foregroundStyle(cs.onSurfaceVariant) }
                    ForEach(alsoScripts.map(\.0).filter { st.alsoScripts.contains($0) && $0 != app.script && $0 != .kannada }, id: \.self) {
                        Text(Scripts.render(l.text, $0, st.tamilSuperscripts)).font(.hk(14)).foregroundStyle(cs.tertiary)
                    }
                }
                Text("…").foregroundStyle(cs.onSurfaceVariant)
            }.padding(16)
        }
    }

    private func header(_ tour: Tour) -> some View {
        let S = app.S, p = app.progress.state, lvl = p.level
        let hour = Calendar.current.component(.hour, from: Date())
        let greeting = (4...11).contains(hour) ? S.goodMorning : (12...16).contains(hour) ? S.namaskara : S.goodEvening
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 14) {
                BrandLogo(size: 72)
                VStack(alignment: .leading, spacing: 0) {
                    Text("\(greeting) 🙏").font(.hk(15)).foregroundStyle(.white.opacity(0.9))
                    Text(HK.appName.replacingOccurrences(of: "thaSaa", with: "tha\u{200B}Saa")).font(.hk(24, .black)).foregroundStyle(HK.brandGold).minimumScaleFactor(0.7)
                    Text("ಹರಿಕಥಾಮೃತಸಾರ").font(.hk(15)).foregroundStyle(.white.opacity(0.85))
                }.frame(maxWidth: .infinity, alignment: .leading)
                VStack(spacing: 8) {
                    ProfileButton()
                    Button { nav.go(.settings) } label: { Image(systemName: "gearshape.fill").font(.system(size: 22)).foregroundStyle(.white).frame(width: 44, height: 40) }.accessibilityLabel(S.settings).tourTarget(TT.homeSettings)
                    TourHelp(tour: tour, tint: .white).font(.system(size: 22)).frame(width: 44, height: 36)
                }
            }
            HStack(spacing: 12) {
                Text(lvl.emoji).font(.hk(32)).frame(width: 56, height: 56).background(.white.opacity(0.16), in: Circle())
                VStack(alignment: .leading, spacing: 2) {
                    Text(S.levelName(lvl)).font(.hk(17, .bold))
                    Text(S.leaves(p.points)).font(.hk(14))
                    if let next = p.nextLevel {
                        HBar(value: Double(p.points - lvl.minPoints) / Double(next.minPoints - lvl.minPoints), height: 10, color: HK.brandGold, track: .white.opacity(0.22)).padding(.top, 4)
                        Text(S.moreFor(next.minPoints - p.points, next.emoji + " " + S.levelName(next))).font(.hk(12)).opacity(0.85)
                    }
                }.foregroundStyle(.white)
            }
        }
        .padding(18)
        .background {
            ZStack {
                LinearGradient(colors: HK.header, startPoint: .topLeading, endPoint: .bottomTrailing)
                GeometryReader { g in
                    Circle().fill(HK.brandGold.opacity(0.16)).frame(width: g.size.height * 1.1, height: g.size.height * 1.1).position(x: g.size.width * 0.95, y: g.size.height * 0.05)
                    Circle().fill(.white.opacity(0.07)).frame(width: g.size.height * 0.7, height: g.size.height * 0.7).position(x: g.size.width * 0.1, y: g.size.height * 1.05)
                }
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 36, style: .continuous))
    }
}

extension HkApp { var isWideLayout: Bool { UIDevice.current.userInterfaceIdiom == .pad } }

private struct ProfileButton: View {
    @Environment(HkApp.self) private var app
    @Environment(Nav.self) private var nav
    var body: some View {
        let person = app.account.user.flatMap { $0.anonymous ? nil : $0 }
        Button { nav.go(.account) } label: {
            if let url = person?.photo {
                AsyncImage(url: url) { $0.resizable().scaledToFill() } placeholder: { Avatar(label: person?.label, size: 40) }.frame(width: 40, height: 40).clipShape(Circle())
            } else if let person { Avatar(label: person.label, size: 40) }
            else { Image(systemName: "person.crop.circle.fill").font(.system(size: 36)).foregroundStyle(.white) }
        }.accessibilityLabel(app.A.account)
    }
}

struct Avatar: View {
    let label: String?
    let size: CGFloat
    @Environment(\.cs) private var cs
    var body: some View {
        let h = label?.unicodeScalars.reduce(0) { $0 + Int($1.value) } ?? 0
        Text(label?.trimmingCharacters(in: .whitespaces).first.map { String($0).uppercased() } ?? "🙏").font(.hk(size * 0.42, .black)).foregroundStyle(.white)
            .frame(width: size, height: size).background(cs.tones[h & 0xff].accent, in: Circle())
    }
}

struct UpdatesBanner: View {
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    var body: some View {
        let st = app.source.state, S = app.S
        if st.hasUpdates || st.updating {
            VStack(alignment: .leading, spacing: 4) {
                Text(S.updatesTitle).font(.hk(16, .bold))
                Text(S.updatesBody(st.textUpdate, st.packUpdates)).font(.hk(13))
                if st.updating { ProgressView().padding(.top, 6); Text(S.updating).font(.hk(12)) }
                else { FilledButton(title: "⬇ " + S.updateNow) { app.source.updateAll() }.padding(.top, 6) }
            }.padding(14).frame(maxWidth: .infinity, alignment: .leading).foregroundStyle(cs.onSecondaryContainer).card(cs.secondaryContainer, radius: 16)
        }
    }
}

struct VagBanner: View {
    let sandhiId: Int
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    var body: some View {
        let _ = app.vag.version
        let S = app.S
        if !app.vag.isDownloaded(sandhiId) {
            VStack(alignment: .leading, spacing: 4) {
                Text(S.vagBannerTitle).font(.hk(16, .bold))
                Text(S.vagBannerBody).font(.hk(13)).padding(.bottom, 4)
                switch app.vag.downloads[sandhiId] {
                case .running(let f):
                    HBar(value: f, height: 6, color: cs.primary, track: cs.outlineVariant)
                    Text(S.downloading(Int(f * 100))).font(.hk(12))
                case .failed(let m):
                    Text(m == VagLibrary.outdated ? S.vagPackOutdated : m == VagLibrary.notOnServer ? S.vagNotOnServer : S.vagNotReady + "\n" + m).font(.hk(13)).foregroundStyle(HK.bad)
                    FilledButton(title: S.tryAgain) { app.vag.download(sandhiId) }
                case nil:
                    FilledButton(title: "⬇ " + S.download) { Task { await Notifier.requestPermission() }; app.vag.download(sandhiId) }
                }
            }.padding(14).frame(maxWidth: .infinity, alignment: .leading).foregroundStyle(cs.onTertiaryContainer).card(cs.tertiaryContainer, radius: 16)
        }
    }
}

struct EarBanner: View {
    var showReady = false
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    var body: some View {
        let S = app.S
        if app.ear.state == .ready {
            if showReady {
                HStack {
                    Text(S.earReady(app.ear.sizeMb)).font(.hk(14)).frame(maxWidth: .infinity, alignment: .leading)
                    OutlineButton(title: S.delete) { app.ear.delete() }
                }
            }
        } else {
            VStack(alignment: .leading, spacing: 4) {
                Text(S.earTitle).font(.hk(16, .bold))
                Text(S.earBody(122)).font(.hk(13)).padding(.bottom, 4)
                switch app.ear.state {
                case .downloading(let f):
                    HBar(value: f, height: 6, color: cs.primary, track: cs.outlineVariant)
                    Text(S.downloading(Int(f * 100))).font(.hk(12))
                case .failed(let m):
                    Text(m).font(.hk(13)).foregroundStyle(HK.bad)
                    FilledButton(title: S.tryAgain) { app.ear.download() }
                default:
                    FilledButton(title: "⬇ " + S.download) { app.ear.download() }
                }
            }.padding(14).frame(maxWidth: .infinity, alignment: .leading).foregroundStyle(cs.onTertiaryContainer).card(cs.tertiaryContainer, radius: 16)
        }
    }
}

struct LibraryScreen: View {
    let lib: [Sandhi]
    let forRecite: Bool
    @Environment(HkApp.self) private var app
    @Environment(Nav.self) private var nav
    @Environment(\.cs) private var cs
    var body: some View {
        let S = app.S, p = app.progress.state, tour = Tours.library(app.T)
        ScrollViewReader { _ in
            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 330), spacing: 10)], spacing: 10) {
                    if forRecite, let ls = lib.first(where: { $0.id == p.lastSandhi }), ls.verses.indices.contains(p.lastVerse) {
                        BouncyCard(color: cs.primaryContainer, fg: cs.onPrimaryContainer, action: { nav.go(.recite(ls.id, p.lastVerse)) }) {
                            HStack(spacing: 12) {
                                Image(systemName: "mic.fill").font(.system(size: 28)).foregroundStyle(cs.primary)
                                VStack(alignment: .leading) {
                                    Text(S.continueWord).font(.hk(16, .bold))
                                    Text("\(ls.id). \(app.mine(ls.title)) · \(S.verseLabel(ls.verses[p.lastVerse]))").font(.hk(14)).lineLimit(1)
                                }.frame(maxWidth: .infinity, alignment: .leading)
                                Text("›").font(.hk(28)).foregroundStyle(cs.primary)
                            }.padding(14)
                        }
                    }
                    ForEach(lib) { s in row(s, p).modifier(FirstTarget(on: s.id == lib[0].id, id: TT.libFirst)) }
                }.padding(16)
            }
        }
        .bar(forRecite ? S.reciteCheck : S.sandhis, forRecite ? S.chooseSandhi : app.mine("ಹರಿಕಥಾಮೃತಸಾರ") + " · " + S.padyaCount(lib.map(\.padyaCount).reduce(0, +)), top: !forRecite) { TourHelp(tour: tour) }
        .modifier(AutoTour(tour: tour))
    }

    private func row(_ s: Sandhi, _ p: ProgressSnapshot) -> some View {
        let S = app.S, padyas = s.verses.filter { !$0.isRefrain }, stars = padyas.map { p.stat($0.key).stars }.reduce(0, +), tone = cs.tones[s.id - 1], current = s.id == p.lastSandhi
        return BouncyCard(color: cs.low, border: current ? (tone.accent, 2) : nil, action: { nav.go(.verses(s.id, forRecite: forRecite)) }) {
            HStack(spacing: 12) {
                Text(s.id == 33 ? "🙏" : "\(s.id)").font(.hk(18, .black)).foregroundStyle(current ? .white : tone.on).frame(width: 48, height: 48).background(current ? tone.accent : tone.container, in: RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 1) {
                    Text(app.mine(s.title)).font(.hk(18, .bold))
                    if app.script != .kannada { Text(s.title).font(.hk(14)).foregroundStyle(cs.secondary) }
                    if !s.alt.isEmpty { Text(app.mine(s.alt)).font(.hk(12)).foregroundStyle(cs.onSurfaceVariant).lineLimit(1) }
                    HBar(value: Double(stars) / Double(max(padyas.count * 3, 1)), height: 6, color: tone.accent, track: tone.container).padding(.top, 6)
                }.frame(maxWidth: .infinity, alignment: .leading)
                VStack(alignment: .trailing, spacing: 1) {
                    Text("\(padyas.count)").font(.hk(16, .bold))
                    Text(S.padyasWord).font(.hk(12)).foregroundStyle(cs.onSurfaceVariant)
                    Text("★ \(stars)").font(.hk(13)).foregroundStyle(HK.gold)
                }
            }.padding(14)
        }
    }
}

struct FirstTarget: ViewModifier {
    let on: Bool, id: String
    func body(content: Content) -> some View { if on { content.tourTarget(id) } else { content } }
}

struct VersesScreen: View {
    let sandhi: Sandhi
    let forRecite: Bool
    @Environment(HkApp.self) private var app
    @Environment(Nav.self) private var nav
    @Environment(\.cs) private var cs
    var body: some View {
        let S = app.S, p = app.progress.state, tour = Tours.verses(app.T), tone = cs.tones[sandhi.id - 1]
        ZStack(alignment: .bottomTrailing) {
            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 340), spacing: 8)], spacing: 8) {
                    ForEach(Array(sandhi.verses.enumerated()), id: \.1.key) { idx, v in row(idx, v, p, tone).modifier(FirstTarget(on: idx == 0, id: TT.versesFirst)) }
                }.padding(16)
                Offering(size: 18).padding(.bottom, 88)
            }
            if !forRecite {
                Button { app.player.open(sandhi, 0); app.player.play(); nav.go(.learn) } label: {
                    Label(S.playWholeSandhi, systemImage: "play.fill").font(.hk(16, .semibold)).padding(.horizontal, 20).frame(minHeight: 56).foregroundStyle(cs.onPrimaryContainer).background(cs.primaryContainer, in: RoundedRectangle(cornerRadius: 18)).shadow(radius: 6)
                }.buttonStyle(Bouncy()).padding(16).tourTarget(TT.versesAll)
            }
        }
        .bar("\(sandhi.id). \(app.mine(sandhi.title))", forRecite ? S.reciteCheck + " · " + S.choosePadya : app.script != .kannada ? sandhi.title : nil) {
            if !forRecite {
                TourHelp(tour: tour)
                Button { nav.go(.recite(sandhi.id, 0)) } label: { Image(systemName: "mic.fill") }.accessibilityLabel(S.reciteCheck)
            }
        }
        .modifier(ConditionalTour(on: !forRecite, tour: tour))
    }

    private func row(_ idx: Int, _ v: Verse, _ p: ProgressSnapshot, _ tone: Tone) -> some View {
        let S = app.S, st = p.stat(v.key)
        let pick = { if forRecite { nav.go(.recite(sandhi.id, idx)) } else { app.player.open(sandhi, idx); nav.go(.learn) } }
        return BouncyCard(color: cs.low, action: pick) {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Pill(text: S.verseLabel(v), bg: tone.container, fg: tone.on)
                        Stars(n: st.stars)
                        if st.listens > 0 { Text("🎧\(st.listens)").font(.hk(12)).foregroundStyle(cs.onSurfaceVariant) }
                        if st.bestScore > 0 { Text("🎤\(st.bestScore)%").font(.hk(12)).foregroundStyle(cs.onSurfaceVariant) }
                    }
                    Text(app.mine(v.firstLine)).font(.hk(18)).lineLimit(1)
                    if app.script != .kannada { Text(v.firstLine).font(.hk(14)).foregroundStyle(cs.secondary).lineLimit(1) }
                }.frame(maxWidth: .infinity, alignment: .leading)
                RoundIcon(system: forRecite ? "mic.fill" : "play.fill", size: 48, icon: 20, label: forRecite ? S.reciteCheck : S.play) {
                    if forRecite { nav.go(.recite(sandhi.id, idx)) } else { app.player.open(sandhi, idx); app.player.play(); nav.go(.learn) }
                }
            }.padding(14)
        }
    }
}

struct ConditionalTour: ViewModifier {
    let on: Bool
    let tour: Tour
    func body(content: Content) -> some View { if on { content.modifier(AutoTour(tour: tour)) } else { content } }
}

struct ProgressScreen: View {
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    @Environment(\.wide) private var wide
    var body: some View {
        let S = app.S, p = app.progress.state, t = cs.tones, tour = Tours.progress(app.T), goal = app.settings.value.dailyGoalMinutes
        let stats: [(String, Int, String)] = [("🌿", p.points, S.statLeaves), ("🔥", p.streak, S.statStreak), ("⭐", p.mastered, S.statMastered), ("🏆", p.bestStreak, S.statBest), ("🎧", p.verses.values.map(\.listens).reduce(0, +), S.statHeard), ("🎤", p.verses.values.map(\.recites).reduce(0, +), S.statRecited)]
        let d0 = today(), days = (0..<14).reversed().map { (p.history[d0 - $0] ?? 0) / 60 }, maxV = max(goal, days.max() ?? 0, 1)
        TourScroll {
            VStack(alignment: .leading, spacing: 12) {
                TileGrid(items: Array(stats.enumerated()), columns: wide ? 6 : 3, gap: 10) { i, s in
                    let tone = t[i + 3]
                    VStack(spacing: 1) {
                        Text(s.0).font(.hk(24)); Text("\(s.1)").font(.hk(22, .bold))
                        Text(short(s.2)).font(.hk(12)).opacity(0.8).multilineTextAlignment(.center)
                        Spacer(minLength: 0)
                    }.padding(12).frame(maxWidth: .infinity, maxHeight: .infinity).foregroundStyle(tone.on).card(tone.container, radius: 20)
                }.tourTarget(TT.progStats)
                SectionTitle(text: S.last14, tone: t.leaf)
                VStack(alignment: .leading, spacing: 8) {
                    Canvas { ctx, size in
                        let w = size.width / CGFloat(days.count), gy = size.height * (1 - CGFloat(goal) / CGFloat(maxV))
                        ctx.stroke(Path { $0.move(to: .init(x: 0, y: gy)); $0.addLine(to: .init(x: size.width, y: gy)) }, with: .color(cs.outline), lineWidth: 1)
                        for (i, v) in days.enumerated() {
                            let h = max(size.height * CGFloat(v) / CGFloat(maxV), 2)
                            ctx.fill(Path(roundedRect: CGRect(x: CGFloat(i) * w + w * 0.18, y: size.height - h, width: w * 0.64, height: h), cornerRadius: 3), with: .color(v >= goal ? t.leaf.accent : t.peacock.accent.opacity(0.55)))
                        }
                    }.frame(height: 120).accessibilityHidden(true)
                    Text(S.goalLine(goal)).font(.hk(12)).foregroundStyle(cs.onSurfaceVariant)
                }.padding(14).frame(maxWidth: .infinity).card(cs.low).tourTarget(TT.progChart)
                SectionTitle(text: S.badgesCount(p.badges.count, BADGES.count), tone: t.marigold)
                TileGrid(items: Array(BADGES.enumerated()), columns: wide ? 6 : 3, gap: 10) { i, b in
                    let got = p.badges.contains(b.id)
                    VStack(spacing: 1) {
                        Text(got ? b.emoji : "🔒").font(.hk(30))
                        Text(S.badgeTitle(b.id)).font(.hk(13, .bold)).multilineTextAlignment(.center)
                        Text(S.badgeHint(b.id)).font(.hk(12)).multilineTextAlignment(.center)
                        Spacer(minLength: 0)
                    }.padding(10).frame(maxWidth: .infinity, maxHeight: .infinity).foregroundStyle(got ? t[i].on : cs.onSurfaceVariant).card(got ? t[i].container : cs.surfaceVariant, radius: 20).opacity(got ? 1 : 0.5)
                }.tourTarget(TT.progBadges)
                SectionTitle(text: S.levels, tone: t.berry)
                ForEach(LEVELS, id: \.minPoints) { l in
                    HStack(spacing: 10) {
                        Text(l.emoji).font(.hk(26))
                        Text(S.levelName(l)).fontWeight(l == p.level ? .bold : .regular).frame(maxWidth: .infinity, alignment: .leading)
                        Text("\(l.minPoints) 🌿").foregroundStyle(cs.onSurfaceVariant)
                    }.foregroundStyle(cs.onSurface).opacity(p.points >= l.minPoints ? 1 : 0.5)
                }
                SectionTitle(text: S.howToEarn, tone: t.sky)
                Text(S.howToEarnBody).font(.hk(14)).foregroundStyle(cs.onSurface)
            }.padding(16).readable(960)
        }
        .bar(S.progressTitle, top: true) { TourHelp(tour: tour) }
        .modifier(AutoTour(tour: tour))
    }
}

struct CreditsScreen: View {
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    @Environment(\.openURL) private var open
    private let kn = "ಅಯೋಧ್ಯಾ ಮಥುರಾ ಮಾಯಾ ಕಾಶೀ ಕಾಂಚೀ ಅವಂತಿಕಾ\u{00A0}।\nಪುರೀ ದ್ವಾರಾವತೀ ಚೈವ ಸಪ್ತೈತಾ ಮೋಕ್ಷದಾಯಿಕಾಃ\u{00A0}॥"
    private let deva = "अयोध्या मथुरा माया काशी काञ्ची अवन्तिका\u{00A0}।\nपुरी द्वारावती चैव सप्तैता मोक्षदायिकाः\u{00A0}॥"

    private func card(_ title: String, _ name: String, _ body: String? = nil, _ tone: Tone, links: [(String, String)] = []) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.hk(16, .bold))
            if let body { Text(name).font(.hk(19, .bold)); Text(body).font(.hk(14)) } else { Text(name).font(.hk(15)) }
            ForEach(links, id: \.0) { l, u in OutlineButton(title: l + " ↗") { open(URL(string: u)!) } }
        }.padding(16).frame(maxWidth: .infinity, alignment: .leading).foregroundStyle(tone.on).card(tone.container)
    }

    var body: some View {
        let S = app.S, t = cs.tones
        ScrollView {
            VStack(spacing: 12) {
                BrandLogo(size: 120)
                card("🙏 " + S.creditPranams, S.creditPranamsBody, nil, t.marigold)
                VStack(spacing: 10) {
                    Text("🪷 " + S.creditShloka).font(.hk(16, .bold)).foregroundStyle(cs.secondary)
                    Text(deva).font(.hk(20, .semibold)).multilineTextAlignment(.center).foregroundStyle(cs.onSurface)
                    Text(kn).font(.hk(18)).multilineTextAlignment(.center).foregroundStyle(cs.onSurfaceVariant)
                    if app.script != .kannada && app.script != .devanagari { Text(Scripts.render(kn, app.script)).font(.hk(16)).multilineTextAlignment(.center).foregroundStyle(cs.tertiary) }
                }.padding(20).frame(maxWidth: .infinity).card(cs.low).overlay(RoundedRectangle(cornerRadius: 28).strokeBorder(t.lotus.accent.opacity(0.6), lineWidth: 2))
                card("📜 " + S.creditWork, S.creditWorkBody, nil, t.marigold)
                card("📖 " + S.creditText, "Tadipatri Gurukula\nShri Kesava Rao Tadipatri", S.creditTextBody, t.lotus, links: [(S.openWebsite, "https://sites.google.com/site/madhwaprameyaqa/hks-script-audio"), ("🔇 Silent pause (dash)", "https://soundcloud.com/shriharivayu-gurugalu/silent-pause-usage-of-dash-in-hks-script")])
                card("🎶 " + S.creditVag, "Vāgdhenu · Dr. Prathosh Acharya (Prathosh A P)\nProfessor, Indian Institute of Science (IISc), Bengaluru\nCreator of Vāgdhenu", S.creditVagBody, t.peacock, links: [("prathosh.in/vagdhenu", "https://prathosh.in/vagdhenu/")])
                card("🎧 " + S.creditEar, "Vakyansh · EkStep Foundation\nwav2vec2 Kannada (MIT License)", S.creditEarBody, t.sky, links: [("Vakyansh", "https://github.com/Open-Speech-EkStep/vakyansh-models")])
                card("❤️ " + S.creditMadeBy, "S. Vivek Surya  &  Janaki", S.creditMadeByRole, t.berry)
                Text(S.personalUse).font(.hk(12)).foregroundStyle(cs.onSurfaceVariant).multilineTextAlignment(.center)
                OutlineButton(title: app.I.privacyPolicy + " ↗") { open(HK.privacyURL) }
                Offering().padding(.bottom, 24)
            }.padding(16).readable()
        }.bar(S.creditsTitle)
    }
}
