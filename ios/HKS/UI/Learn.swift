import SwiftUI

private enum Picker: Identifiable { case unit, repeats, after, speed, more; var id: Self { self } }

struct LearnScreen: View {
    @Environment(HkApp.self) private var app
    @Environment(Nav.self) private var nav
    @Environment(\.cs) private var cs
    @Environment(\.dismiss) private var dismiss
    @State private var chores = false
    @State private var peekWord = -1
    @State private var picker: Picker?
    @State private var reporting = false

    var body: some View {
        let player = app.player, s = app.settings.value, S = app.S
        if let sandhi = player.sandhi, let verse = player.verse {
            let tour = Tours.learn(app.T)
            let step = player.step
            let focusLine = player.currentWord >= 0 && player.currentWord < verse.words.count ? verse.words[player.currentWord].line : step.map { verse.words[$0.from].line } ?? -1
            VStack(spacing: 0) {
                navigator(sandhi, verse)
                ScrollViewReader { proxy in
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            VagBanner(sandhiId: sandhi.id)
                            if player.finishedSandhi {
                                VStack(spacing: 6) {
                                    Text("\(S.sandhiDone): \(app.mine(sandhi.title))").font(.hk(16, .bold)).multilineTextAlignment(.center)
                                    Offering()
                                }.padding(18).frame(maxWidth: .infinity).foregroundStyle(cs.onTertiaryContainer).card(cs.tertiaryContainer, radius: 22)
                            }
                            if chores { choresView(verse) } else { verseCard(sandhi, verse, step, s) }
                        }.padding(.horizontal, 16).padding(.vertical, 8).readable(820)
                    }
                    .onChange(of: focusLine) { _, l in if l >= 0 { withAnimation { proxy.scrollTo("line\(l)", anchor: .center) } } }
                }
                PlayerDock(onPlay: onPlay, onRecite: { player.pause(); nav.go(.recite(sandhi.id, player.verseIdx)) }, onPick: { picker = $0 })
            }
            .bar("\(sandhi.id). \(app.mine(sandhi.title))", app.script != .kannada ? sandhi.title : nil) {
                TourHelp(tour: tour)
                Button { picker = .more } label: { Image(systemName: "slider.horizontal.3") }.accessibilityLabel(S.options).tourTarget(TT.learnMore)
            }
            .modifier(AutoTour(tour: tour))
            .onDisappear { player.stopAndClose(); UIApplication.shared.isIdleTimerDisabled = false }
            .onAppear { AudioRoute.usePlayback() }
            .onChange(of: player.playing, initial: true) { _, p in UIApplication.shared.isIdleTimerDisabled = p && app.settings.value.keepScreenOn }
            .onChange(of: s.unit) { player.unitChanged() }
            .onChange(of: verse.key) { peekWord = -1 }
            .sheet(item: $picker) { pk in
                PickerSheet(pk: pk, chores: $chores, onClose: { picker = nil }, onReport: { picker = nil; player.pause(); reporting = true })
                    .presentationDetents(pk == .more ? [.large] : [.medium, .large]).presentationDragIndicator(.visible)
                    .environment(\.cs, cs).presentationBackground(cs.low)
            }
            .sheet(isPresented: $reporting) {
                ReportSheet(sandhi: sandhi.id, padya: verse.number, line: focusLine >= 0 ? focusLine + 1 : nil).environment(\.cs, cs).presentationDetents([.medium, .large])
            }
        } else {
            Color.clear.onAppear { nav.back() }
        }
    }

    private func onPlay() {
        if !app.player.playing { Task { await Notifier.requestPermission() } }
        app.player.toggle()
    }

    private func navigator(_ sandhi: Sandhi, _ verse: Verse) -> some View {
        let p = app.player, S = app.S
        return HStack {
            RoundIcon(system: "chevron.left", size: 48, icon: 22, label: S.back, enabled: p.verseIdx > 0) { p.prevVerse() }
            VStack(spacing: 0) {
                Text(S.verseLabel(verse)).font(.hk(20, .bold)).foregroundStyle(cs.onSurface)
                HStack(spacing: 8) {
                    Text(S.verseOf(p.verseIdx + 1, sandhi.verses.count)).font(.hk(13)).foregroundStyle(cs.onSurfaceVariant)
                    Stars(n: app.progress.state.stat(verse.key).stars, size: 14)
                }
            }.frame(maxWidth: .infinity)
            RoundIcon(system: "chevron.right", size: 48, icon: 22, label: S.next, enabled: p.verseIdx < sandhi.verses.count - 1) { p.nextVerse() }
        }.padding(.horizontal, 8).padding(.vertical, 2).tourTarget(TT.learnNav)
    }

    @ViewBuilder private func verseCard(_ sandhi: Sandhi, _ verse: Verse, _ step: Step?, _ s: Settings) -> some View {
        let p = app.player, S = app.S
        VerseView(verse: verse, fontScale: s.fontScale, look: { g in g == p.currentWord ? .current : step?.contains(g) == true ? .inStep : .normal }, hideMine: s.peekTamil, onWordTap: { g in peekWord = g; p.tapWord(g) })
            .padding(18).frame(maxWidth: .infinity, alignment: .leading).card(cs.low)
            .overlay(RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(cs.tones[sandhi.id - 1].container, lineWidth: 2))
            .tourTarget(TT.learnVerse)
        if verse.words.indices.contains(peekWord) {
            let w = verse.words[peekWord]
            HStack {
                VStack(alignment: .leading) {
                    Text(w.text).font(.hk(22, .bold))
                    if app.script != .kannada { Text(app.mine(w.text)).font(.hk(18)).foregroundStyle(cs.secondary) }
                }.frame(maxWidth: .infinity, alignment: .leading)
                Button { p.sayWord(sandhi.id, verse, w.global) } label: { Image(systemName: "speaker.wave.2.fill").frame(width: 44, height: 44) }.accessibilityLabel(S.hear)
                Button { peekWord = -1 } label: { Image(systemName: "xmark").frame(width: 44, height: 44) }.accessibilityLabel(S.cancel)
            }.padding(.horizontal, 14).padding(.vertical, 6).foregroundStyle(cs.onSecondaryContainer).card(cs.secondaryContainer, radius: 16)
        }
        if app.vag.isDownloaded(sandhi.id) { Text(p.usesVag(sandhi.id, verse) ? S.vagCredit : S.vagNotReady).font(.hk(13)).foregroundStyle(cs.onSurfaceVariant) }
        Text(S.dashTip).font(.hk(12)).foregroundStyle(cs.onSurfaceVariant)
        Text(S.tapTip).font(.hk(12)).foregroundStyle(cs.onSurfaceVariant)
    }

    private func choresView(_ verse: Verse) -> some View {
        let p = app.player, S = app.S, step = p.step, s = app.settings.value
        let lines = step.map { st in Set(verse.words.filter { st.contains($0.global) }.map(\.line)) } ?? Set(verse.lines.indices)
        let turn = p.phase == .yourTurn
        return VStack(spacing: 16) {
            Text(!p.playing ? S.paused : turn ? S.yourTurnSay : S.listenDots).font(.hk(26, .bold)).multilineTextAlignment(.center).padding(20).frame(maxWidth: .infinity)
                .foregroundStyle(turn ? cs.onTertiaryContainer : cs.onPrimaryContainer).card(turn ? cs.tertiaryContainer : cs.primaryContainer, radius: 24).animation(.easeInOut, value: turn)
            VerseView(verse: verse, fontScale: s.fontScale * 1.45, look: { g in g == p.currentWord ? .current : step?.contains(g) == true ? .inStep : .normal }, onlyLines: lines)
        }
    }
}

private func gapName(_ S: Strs, _ g: GapMode, _ sec: Int) -> String { switch g { case .none: S.noPause; case .fixed: S.pauseSec(sec); case .echo: S.myTurn } }

private struct PlayerDock: View {
    let onPlay: () -> Void, onRecite: () -> Void, onPick: (Picker) -> Void
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    var body: some View {
        let p = app.player, s = app.settings.value, S = app.S, t = cs.tones, total = s.repeatCount
        VStack(spacing: 6) {
            HStack(spacing: 6) {
                Text([p.step.map { short(tagged(S, $0)) }, "🔁 \(total == 0 ? p.repeatNo : min(p.repeatNo, total))/\(total == 0 ? "∞" : String(total))"].compactMap { $0 }.joined(separator: "  ·  "))
                    .font(.hk(15, .semibold)).foregroundStyle(cs.onSurfaceVariant).lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
                if p.phase == .yourTurn { Pill(text: S.yourTurn, bg: t.leaf.container, fg: t.leaf.on) }
                else if p.playing { Pill(text: S.listenNow, bg: t.lotus.container, fg: t.lotus.on) }
                if let at = p.sleepAt { Pill(text: "😴 \(max(Int(at.timeIntervalSinceNow / 60) + 1, 1))", bg: t.berry.container, fg: t.berry.on) }
            }.padding(.horizontal, 20)
            HStack {
                Spacer()
                RoundIcon(system: "backward.fill", label: S.back) { p.prevStep() }
                Spacer()
                RoundIcon(system: p.playing ? "pause.fill" : "play.fill", size: 80, icon: 40, bg: cs.primary, fg: cs.onPrimary, label: p.playing ? S.pause : S.play, action: onPlay).tourTarget(TT.learnPlay)
                Spacer()
                RoundIcon(system: "forward.fill", label: S.next) { p.nextStep() }
                Spacer()
                Button(action: onRecite) {
                    VStack(spacing: 2) {
                        Image(systemName: "mic.fill").font(.system(size: 24)).foregroundStyle(t.marigold.on).frame(width: 48, height: 48).background(t.marigold.container, in: Circle())
                        Text(short(S.reciteCheck)).font(.hk(12)).foregroundStyle(cs.onSurface).lineLimit(2).multilineTextAlignment(.center)
                    }.frame(width: 84)
                }.buttonStyle(Bouncy()).tourTarget(TT.learnRecite)
            }
            Flow(center: true) {
                chip("📖", short(S.unitName(s.unit)), t.lotus) { onPick(.unit) }
                chip("🔁", s.repeatCount == 0 ? "∞" : "\(s.repeatCount)×", t.marigold) { onPick(.repeats) }.tourTarget(TT.learnRepeat)
                chip("🗣️", short(gapName(S, s.gapMode, s.gapSeconds)), t.leaf) { onPick(.after) }
                chip("⏩", x2(s.speed).replacingOccurrences(of: ".00×", with: "×"), t.sky) { onPick(.speed) }
            }.padding(.horizontal, 16).tourTarget(TT.learnChips)
        }
        .padding(.top, 12).padding(.bottom, 8).frame(maxWidth: .infinity)
        .background(cs.container, in: UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32, style: .continuous))
        .background(alignment: .bottom) { cs.container.frame(height: 40).ignoresSafeArea(edges: .bottom) }
    }
    private func chip(_ e: String, _ v: String, _ tone: Tone, _ f: @escaping () -> Void) -> some View {
        Button(action: f) {
            HStack(spacing: 6) {
                Text(e).font(.hk(16)); Text(v).font(.hk(15, .semibold)).lineLimit(1)
                Text("▾").font(.hk(12)).opacity(0.7)
            }.padding(.horizontal, 14).frame(minHeight: 48).foregroundStyle(tone.on).background(tone.container, in: Capsule())
        }.buttonStyle(Bouncy())
    }
}

private struct ChoiceCard: View {
    let title: String
    var sub: String?
    let selected: Bool
    let tone: Tone
    let action: () -> Void
    @Environment(\.cs) private var cs
    var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading) {
                    Text(title).font(.hk(17, .bold))
                    if let sub { Text(sub).font(.hk(13)).opacity(0.85).multilineTextAlignment(.leading) }
                }.frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: selected ? "largecircle.fill.circle" : "circle").font(.system(size: 22)).foregroundStyle(selected ? tone.accent : cs.outline)
            }.padding(.horizontal, 16).padding(.vertical, 12).foregroundStyle(selected ? tone.on : cs.onSurface).card(selected ? tone.container : cs.low, radius: 20)
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(selected ? tone.accent : cs.outlineVariant, lineWidth: 2))
        }.buttonStyle(Bouncy())
    }
}

private struct PickerSheet: View {
    let pk: Picker
    @Binding var chores: Bool
    let onClose: () -> Void, onReport: () -> Void
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs

    private func upd(_ f: (inout Settings) -> Void) { app.settings.update(f) }
    private func label(_ t: String) -> some View { Text(t).font(.hk(15, .bold)).foregroundStyle(cs.secondary).padding(.top, 10).padding(.bottom, 2) }

    var body: some View {
        let S = app.S
        let head: (String, String) = {
            switch pk { case .unit: ("📖", S.sheetUnit); case .repeats: ("🔁", S.sheetRepeat); case .after: ("🗣️", S.sheetAfter); case .speed: ("⏩", S.sheetSpeed); case .more: ("⚙️", S.options) }
        }()
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text("\(head.0)  \(head.1)").font(.hk(22, .bold)).foregroundStyle(cs.onSurface).padding(.top, 20)
                switch pk {
                case .unit: unit
                case .repeats: repeats
                case .after: after
                case .speed: speed
                case .more: more
                }
                FilledButton(title: "✓ " + S.ok, height: 52, fill: true, size: 17, action: onClose).padding(.top, 8)
            }.padding(.horizontal, 20).padding(.bottom, 16).readable(640)
        }
    }

    @ViewBuilder private var unit: some View {
        let S = app.S, s = app.settings.value, t = cs.tones
        Text("✅ " + S.unitRecommended).font(.hk(13, .semibold)).foregroundStyle(HK.good)
        ForEach([StudyUnit.sentence, .verse], id: \.self) { u in ChoiceCard(title: S.unitName(u), sub: S.unitSub(u), selected: s.unit == u, tone: t.lotus) { upd { $0.unit = u } } }
        Text(S.unitTraining).font(.hk(13, .semibold)).foregroundStyle(cs.onSurfaceVariant).padding(.top, 6)
        ForEach([StudyUnit.line, .buildup], id: \.self) { u in ChoiceCard(title: S.unitName(u), sub: S.unitSub(u), selected: s.unit == u, tone: t.sky) { upd { $0.unit = u } } }
    }

    @ViewBuilder private var repeats: some View {
        let s = app.settings.value, S = app.S
        HStack {
            RoundIcon(system: "minus", label: "−") { upd { $0.repeatCount = $0.repeatCount <= 1 ? 1 : $0.repeatCount - 1 } }
            Text(s.repeatCount == 0 ? "∞" : "\(s.repeatCount)×").font(.system(size: 40, weight: .black)).foregroundStyle(cs.tones.marigold.accent).frame(width: 110)
            RoundIcon(system: "plus", label: "+") { upd { $0.repeatCount = $0.repeatCount == 0 ? 1 : min($0.repeatCount + 1, 108) } }
        }.frame(maxWidth: .infinity)
        Flow {
            ForEach([1, 3, 5, 11, 21, 108], id: \.self) { n in Chip(text: "\(n)×", selected: s.repeatCount == n) { upd { $0.repeatCount = n } } }
            Chip(text: S.untilIStop, selected: s.repeatCount == 0) { upd { $0.repeatCount = 0 } }
        }
    }

    @ViewBuilder private var after: some View {
        let s = app.settings.value, S = app.S, t = cs.tones.leaf
        ChoiceCard(title: S.myTurn, sub: S.myTurnSub, selected: s.gapMode == .echo, tone: t) { upd { $0.gapMode = .echo } }
        ChoiceCard(title: S.pauseSec(s.gapSeconds), sub: S.fixedPauseSub, selected: s.gapMode == .fixed, tone: t) { upd { $0.gapMode = .fixed } }
        if s.gapMode == .fixed {
            Slider(value: Binding(get: { Double(s.gapSeconds) }, set: { v in upd { $0.gapSeconds = Int(v) } }), in: 1...15, step: 1)
        }
        ChoiceCard(title: S.noPause, sub: S.noPauseSub, selected: s.gapMode == .none, tone: t) { upd { $0.gapMode = .none } }
    }

    @ViewBuilder private var speed: some View {
        let s = app.settings.value
        Text(x2(s.speed)).font(.system(size: 40, weight: .black)).foregroundStyle(cs.tones.sky.accent).frame(maxWidth: .infinity)
        Slider(value: Binding(get: { s.speed }, set: { v in upd { $0.speed = v } }), in: 0.5...1.5)
        Flow {
            ForEach([0.75, 0.9, 1.0, 1.1, 1.25], id: \.self) { v in Chip(text: x2(v), selected: abs(s.speed - v) < 0.01) { upd { $0.speed = v } } }
        }
    }

    @ViewBuilder private var more: some View {
        let s = app.settings.value, S = app.S
        if app.script != .kannada {
            label("🔤 " + S.showVerses)
            let me = Scripts.render("ಹರಿಕಥಾ", app.script, s.tamilSuperscripts)
            Flow {
                ForEach([(ScriptMode.both, "ಹರಿಕಥಾ + \(me)"), (.mine, me), (.kannada, "ಹರಿಕಥಾ")], id: \.0) { m, t in Chip(text: t, selected: s.scriptMode == m) { upd { $0.scriptMode = m } } }
            }
        }
        label("🔤 " + S.alsoShowIn)
        AlsoScriptsPicker()
        label(S.sleepTimer)
        Flow {
            ForEach([0, 10, 15, 30, 45, 60], id: \.self) { m in Chip(text: m == 0 ? S.off : S.minutesN(m), selected: false) { app.player.setSleepTimer(m); onClose() } }
        }
        Divider().padding(.top, 6)
        SwitchRow(title: S.bigText, sub: S.bigTextSub, on: $chores)
        SwitchRow(title: S.autoNext, sub: nil, on: Binding(get: { s.autoAdvance }, set: { v in upd { $0.autoAdvance = v } }))
        Divider()
        Button(action: onReport) { Text(S.reportProblem).font(.hk(16)) }.frame(minHeight: 44)
    }
}

struct SwitchRow: View {
    let title: String
    let sub: String?
    @Binding var on: Bool
    @Environment(\.cs) private var cs
    var body: some View {
        Toggle(isOn: $on) {
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.hk(17, .semibold)).foregroundStyle(cs.onSurface)
                if let sub { Text(sub).font(.hk(13)).foregroundStyle(cs.onSurfaceVariant) }
            }
        }.padding(.vertical, 8).tint(cs.primary)
    }
}

struct ReportSheet: View {
    let sandhi: Int, padya: Int, line: Int?
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    @Environment(\.dismiss) private var dismiss
    @State private var kind = "audio"
    @State private var note = ""
    @State private var result: Bool?
    @State private var sending = false
    var body: some View {
        let S = app.S
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text(S.reportWhat).font(.hk(20, .bold)).foregroundStyle(cs.onSurface)
                Text((padya > 0 ? S.padya(padya) : S.refrain) + (line.map { " · " + S.stepLine($0) } ?? "")).font(.hk(13)).foregroundStyle(cs.onSurfaceVariant)
                Flow {
                    Chip(text: S.reportAudio, selected: kind == "audio") { kind = "audio" }
                    Chip(text: S.reportText, selected: kind == "text") { kind = "text" }
                }
                TextField(S.reportNoteHint, text: $note, axis: .vertical).lineLimit(2...5).textFieldStyle(.roundedBorder)
                if let result { Text(result ? S.reportSent : S.reportFailed).font(.hk(13)).foregroundStyle(result ? HK.good : HK.bad) }
                HStack {
                    if result != true { OutlineButton(title: S.cancel) { dismiss() } }
                    Spacer()
                    if result == true { FilledButton(title: S.ok) { dismiss() } }
                    else {
                        FilledButton(title: S.send, enabled: !sending) {
                            sending = true
                            Task { result = await app.source.report(sandhi: sandhi, padya: padya, line: line, kind: kind, note: note); sending = false }
                        }
                    }
                }
            }.padding(20)
        }.presentationBackground(cs.low)
    }
}
