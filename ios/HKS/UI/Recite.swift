import SwiftUI

private let reciteUnits: [StudyUnit] = [.sentence, .verse, .line]

@MainActor @Observable
final class ReciteSession {
    let app: HkApp
    let sandhi: Sandhi
    let listener: Listener
    var verseIdx: Int
    var unit = StudyUnit.sentence
    var partIdx = 0
    var hideText = false
    var handsFree = false
    var hintFirst = true
    var result: Matcher.Result?
    var finalResult = false
    var message: String?
    var speaking = false
    @ObservationIgnored private var handsFreeJob: Task<Void, Never>?
    @ObservationIgnored private var hearJob: Task<Void, Never>?

    init(app: HkApp, sandhi: Sandhi, verseIdx: Int) {
        self.app = app; self.sandhi = sandhi; self.verseIdx = min(max(verseIdx, 0), sandhi.verses.count - 1)
        listener = Listener(ear: app.ear); listener.strs = app.S
    }

    var S: Strs { app.S }
    var verse: Verse { sandhi.verses[verseIdx] }
    var parts: [Step] { Planner.plan(verse, unit) }
    var part: Step { let p = parts; return p[min(max(partIdx, 0), p.count - 1)] }
    private var strictness: Strictness { app.settings.value.strictness }

    func live(_ text: String) {
        if finalResult { return }
        result = text.trimmingCharacters(in: .whitespaces).isEmpty ? nil : Matcher.match(part.words, text)
    }

    private func score(_ text: String, _ lp: [[Float]]?) async -> Matcher.Result {
        let s = strictness, p = part, v = verse
        return await Task.detached(priority: .userInitiated) {
            let r = Matcher.match(p.words, text)
            let joins: [Bool] = (p.from..<p.to).map { g in
                let w = v.words[g], e = v.lines[w.line].wordEnds
                return (w.index < e.count ? e[w.index] : "1") == "1"
            }
            let sounds = lp.map { Pronunciation.judge($0, p.words, joins, reached: r.reached, present: { r.isSaid($0, s) }, s) } ?? [:]
            return Matcher.withSounds(r, p.words, sounds, s)
        }.value
    }

    func rescore() {
        let text = listener.state.text
        guard !text.isEmpty, result != nil, finalResult else { return }
        Task { let r = await score(text, listener.lastLogProbs); result = r; message = S.feedback(r.overall, strictness.passMark) }
    }

    func stopHearing() { hearJob?.cancel(); hearJob = nil; app.player.clip.pause() }
    func reset() { stopHearing(); listener.cancel(); result = nil; finalResult = false; message = nil }
    func setPart(_ i: Int) { reset(); partIdx = min(max(i, 0), parts.count - 1) }

    func nextPart() {
        stopHearing()
        if partIdx < parts.count - 1 { setPart(partIdx + 1) }
        else if verseIdx < sandhi.verses.count - 1 { reset(); verseIdx += 1; partIdx = 0 }
    }
    func prevPart() {
        stopHearing()
        if partIdx > 0 { setPart(partIdx - 1) }
        else if verseIdx > 0 { reset(); verseIdx -= 1; partIdx = parts.count - 1 }
    }

    private var maxSeconds: Int { max(Int(Double(part.words.map { Matcher.normalize($0, true).count }.reduce(0, +)) * 0.16 * 3 + 4), 10) }

    func startManual() {
        app.player.pause(); reset()
        message = S.listeningRecite
        listener.onSegmentDone = { [weak self] in Task { _ = await self?.finish(speakBack: true) } }
        listener.start(continuous: unit != .line, maxSeconds: maxSeconds)
    }
    func stopManual() { listener.stop() }

    @discardableResult
    func finish(speakBack: Bool) async -> Int {
        let text = listener.state.text
        let r = await score(text, listener.lastLogProbs)
        result = r; finalResult = true
        if text.isEmpty { message = listener.state.error ?? S.didntHear; return 0 }
        let full = unit == .verse
        app.progress.recordRecite(verse.key, score: r.overall, fullVerse: full)
        if full { app.progress.checkSandhiBadge(sandhi) }
        message = S.feedback(r.overall, strictness.passMark)
        if speakBack && app.settings.value.speakCorrections { Task { try? await speakFeedback(r) } }
        return r.overall
    }

    func speakFeedback(_ r: Matcher.Result) async throws {
        speaking = true
        defer { speaking = false }
        r.overall >= strictness.passMark ? Chime.good() : Chime.bad()
        try await Task.sleep(for: .milliseconds(450))
        let p = part, v = verse, st = strictness
        let missed = p.words.indices.filter { !r.isCorrect($0, st) }
        if !missed.isEmpty && r.overall < 95 {
            try await Task.sleep(for: .milliseconds(250))
            for i in missed.prefix(5) {
                let g = p.from + i
                _ = try await app.player.sayAny(sandhi.id, v, Step([v.words[g].text], g, .word, g + 1))
                try await Task.sleep(for: .milliseconds(250))
            }
        }
    }

    func hear() {
        app.player.pause(); listener.cancel(); stopHearing()
        let p = part, v = verse
        hearJob = Task { speaking = true; defer { speaking = false }; _ = try? await app.player.sayAny(sandhi.id, v, p) }
    }
    func hearWord(_ g: Int) { listener.cancel(); app.player.sayWord(sandhi.id, verse, g) }

    private func waitForSegment() async throws {
        var done = false
        listener.onSegmentDone = { done = true }
        listener.start(continuous: false, maxSeconds: maxSeconds)
        while !done { try await Task.sleep(for: .milliseconds(40)) }
    }

    func startHandsFree() {
        app.player.pause()
        handsFree = true
        handsFreeJob?.cancel()
        UIApplication.shared.isIdleTimerDisabled = true
        handsFreeJob = Task {
            do {
                outer: while true {
                    var tries = 0
                    while true {
                        reset()
                        if hintFirst || tries > 0 {
                            message = S.listenDots
                            speaking = true
                            _ = try await app.player.sayAny(sandhi.id, verse, part)
                            speaking = false
                            try await Task.sleep(for: .milliseconds(300))
                        }
                        Chime.beep()
                        try await Task.sleep(for: .milliseconds(250))
                        message = S.reciteNow
                        try await waitForSegment()
                        let sc = await finish(speakBack: false)
                        if sc == 0 && listener.state.text.isEmpty { tries += 1; if tries >= 2 { break } else { continue } }
                        try await speakFeedback(result!)
                        tries += 1
                        if sc >= strictness.passMark || tries >= 3 { break }
                        try await Task.sleep(for: .milliseconds(400))
                    }
                    try await Task.sleep(for: .milliseconds(600))
                    if partIdx == parts.count - 1 && verseIdx == sandhi.verses.count - 1 { break outer }
                    nextPart()
                }
                handsFree = false
                UIApplication.shared.isIdleTimerDisabled = false
                message = S.sandhiComplete
            } catch {}
        }
    }

    func stopHandsFree() {
        handsFreeJob?.cancel(); handsFreeJob = nil
        handsFree = false; speaking = false
        UIApplication.shared.isIdleTimerDisabled = false
        app.player.clip.pause(); listener.cancel()
        message = nil
    }

    func dispose() { stopHandsFree(); listener.cancel(); AudioRoute.usePlayback() }
}

struct MicBanner: View {
    let onAllow: () -> Void
    let text: String, action: String
    @Environment(\.cs) private var cs
    var body: some View {
        HStack {
            Text(text).font(.hk(14)).frame(maxWidth: .infinity, alignment: .leading)
            FilledButton(title: action, action: onAllow)
        }.padding(12).foregroundStyle(cs.onSecondaryContainer).card(cs.secondaryContainer, radius: 20)
    }
}

struct ReciteScreen: View {
    let sandhi: Sandhi
    let verseIdx: Int
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    @Environment(\.scenePhase) private var phase
    @State private var session: ReciteSession?
    @State private var hasMic = Listener.micGranted
    @State private var denied = false
    @State private var options = false

    var body: some View {
        Group {
            if let session { content(session) } else { Color.clear }
        }
        .task {
            if session == nil { session = ReciteSession(app: app, sandhi: sandhi, verseIdx: verseIdx) }
            app.player.pause()
            AudioRoute.useVoice()
            if !hasMic { await askMic() }
        }
        .onDisappear { session?.dispose() }
        .onChange(of: phase) { _, p in if p == .background { session?.stopHandsFree() } }
    }

    private func askMic() async {
        hasMic = await Listener.micAllowed()
        denied = !hasMic
    }
    private func micOrAsk(_ s: ReciteSession, _ f: () -> Void) {
        if hasMic { f() } else if denied { Notifier.openSystemSettings() } else { Task { await askMic() } }
    }

    private func content(_ s: ReciteSession) -> some View {
        let S = app.S, verse = s.verse, part = s.part, r = s.result, st = app.settings.value.strictness, ls = s.listener.state, tour = Tours.recite(app.T)
        let hide = s.hideText, fin = s.finalResult
        let look: (Int) -> WordLook = { g in
            guard part.contains(g) else { return hide ? .hidden : .normal }
            let i = g - part.from
            guard let r else { return hide ? .hidden : .inStep }
            if fin && r.isSaid(i, st) && r.sounds[i] != nil { return .sound }
            if r.isCorrect(i, st) && (fin || i <= r.reached) { return .good }
            if fin || i < r.reached { return hide && !fin ? .hidden : .bad }
            if i == r.reached + 1 && !hide { return .next }
            return hide ? .hidden : .inStep
        }
        return VStack(spacing: 0) {
            TourScroll {
                VStack(spacing: 12) {
                    EarBanner()
                    if !hasMic { MicBanner(onAllow: { micOrAsk(s) {} }, text: S.micNeeded, action: denied ? app.I.openSettings : S.allow) }
                    if !{ let _ = app.vag.version; return app.player.usesVag(sandhi.id, verse) }() { VagBanner(sandhiId: sandhi.id) }
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text(short(tagged(S, part))).font(.hk(14, .semibold)).foregroundStyle(cs.onSurfaceVariant)
                            Spacer()
                            Stars(n: app.progress.state.stat(verse.key).stars)
                        }
                        VerseView(verse: verse, fontScale: app.settings.value.fontScale, look: look, onWordTap: { g in if part.contains(g) { s.hearWord(g) } })
                    }.padding(18).frame(maxWidth: .infinity, alignment: .leading).card(cs.low, radius: 22).tourTarget(TT.reciteVerse)
                    if let r, fin { resultCard(s, r, part, st) }
                    if let m = ls.analyzing ? S.checking : s.message { Text(m).font(.hk(17, .semibold)).multilineTextAlignment(.center).foregroundStyle(cs.onSurface).frame(maxWidth: .infinity) }
                    if !ls.text.isEmpty {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(S.whatIHeard).font(.hk(12)).foregroundStyle(cs.onSurfaceVariant)
                            Text(ls.text).font(.hk(16)).foregroundStyle(cs.onSurface)
                            Text(app.mine(ls.text)).font(.hk(14)).foregroundStyle(cs.secondary)
                        }.padding(12).frame(maxWidth: .infinity, alignment: .leading).card(cs.surfaceVariant, radius: 14)
                    }
                    if let e = ls.error, s.message != e { Text(e).font(.hk(14)).foregroundStyle(HK.bad) }
                }.padding(16).readable(820)
            }
            controls(s, ls)
        }
        .bar(S.reciteTitle, "\(app.mine(sandhi.title)) · \(S.verseLabel(verse))") {
            TourHelp(tour: tour)
            Button { options = true } label: { Image(systemName: "slider.horizontal.3") }.disabled(s.handsFree).accessibilityLabel(S.options)
        }
        .modifier(AutoTour(tour: tour))
        .onChange(of: ls.text) { _, t in s.live(t) }
        .onChange(of: st) { s.rescore() }
        .sheet(isPresented: $options) { ReciteOptions(s: s).environment(\.cs, cs).presentationDetents([.medium, .large]).presentationDragIndicator(.visible).presentationBackground(cs.low) }
    }

    private func wordButton(_ s: ReciteSession, _ i: Int, _ part: Step, said: String?) -> some View {
        let w = part.words[i], S = app.S
        return Button { s.hearWord(part.from + i) } label: {
            VStack(spacing: 1) {
                Text(w).font(.hk(16, .bold)).foregroundStyle(said != nil ? HK.sound : cs.onSurface)
                if app.script != .kannada { Text(app.mine(w)).font(.hk(12)).foregroundStyle(cs.onSurfaceVariant) }
                if let said { Text(S.notLike(said)).font(.hk(12)).foregroundStyle(cs.onSurfaceVariant) }
            }.padding(.horizontal, 12).padding(.vertical, 6).overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(cs.outline))
        }.buttonStyle(Bouncy())
    }

    private func resultCard(_ s: ReciteSession, _ r: Matcher.Result, _ part: Step, _ st: Strictness) -> some View {
        let S = app.S, sc = r.overall, pass = sc >= st.passMark
        let fix = part.words.indices.filter { r.isSaid($0, st) && r.sounds[$0] != nil }
        let missed = part.words.indices.filter { !r.isSaid($0, st) }
        return VStack(spacing: 4) {
            Text("\(sc)%").font(.hk(44, .bold)).foregroundStyle(pass ? HK.good : cs.secondary)
            Stars(n: sc >= max(90, st.passMark) ? 3 : pass ? 2 : sc >= 40 ? 1 : 0, size: 26)
            if !fix.isEmpty {
                Text(S.soundsToFix).font(.hk(13)).foregroundStyle(cs.onSurface).padding(.top, 8)
                Flow(spacing: 6, center: true) { ForEach(fix.prefix(6), id: \.self) { i in wordButton(s, i, part, said: r.sounds[i]!.min { $0.margin < $1.margin }!.said) } }
            }
            if !missed.isEmpty {
                Text(S.practiseWords).font(.hk(13)).foregroundStyle(cs.onSurface).padding(.top, 8)
                Flow(spacing: 6, center: true) { ForEach(missed.prefix(4), id: \.self) { i in wordButton(s, i, part, said: nil) } }
            }
        }.padding(16).frame(maxWidth: .infinity).card(pass ? HK.goodBg : cs.secondaryContainer, radius: 22)
    }

    private func labeled(_ icon: String, _ label: String, _ f: @escaping () -> Void) -> some View {
        Button(action: f) {
            VStack(spacing: 4) {
                Image(systemName: icon).font(.system(size: 24, weight: .semibold)).foregroundStyle(cs.onSecondaryContainer).frame(width: 56, height: 56).background(cs.secondaryContainer, in: Circle())
                Text(label).font(.hk(12)).foregroundStyle(cs.onSurface).lineLimit(2).multilineTextAlignment(.center)
            }.frame(maxWidth: .infinity)
        }.buttonStyle(Bouncy())
    }

    private func controls(_ s: ReciteSession, _ ls: ListenState) -> some View {
        let S = app.S
        return VStack(spacing: 8) {
            if s.handsFree {
                Text(S.handsFreeOn).font(.hk(13)).multilineTextAlignment(.center).foregroundStyle(cs.onSurface)
                HStack(alignment: .bottom, spacing: 4) {
                    ForEach(0..<12, id: \.self) { i in
                        RoundedRectangle(cornerRadius: 3).fill(ls.listening && i < Int(ls.level * 12) + 1 ? cs.primary : cs.surfaceVariant).frame(width: 8, height: 8 + CGFloat(i) * 1.5)
                    }
                }.frame(height: 28)
                FilledButton(title: "⏹ " + S.stopHandsFree, height: 56, fill: true, size: 17) { s.stopHandsFree() }
            } else {
                HStack {
                    labeled("chevron.left", short(S.back)) { s.prevPart() }
                    labeled("speaker.wave.2.fill", short(S.hear)) { s.hear() }.tourTarget(TT.reciteHear)
                    RoundIcon(system: ls.listening ? "stop.fill" : "mic.fill", size: 76, icon: 36, bg: ls.listening ? HK.bad : cs.primary, fg: cs.onPrimary, label: ls.listening ? S.stop : S.reciteCheck) {
                        if ls.listening { s.stopManual() } else { micOrAsk(s) { s.startManual() } }
                    }
                    .scaleEffect(ls.listening ? 1 + CGFloat(ls.level) * 0.25 : 1).animation(.easeOut(duration: 0.1), value: ls.level)
                    .frame(maxWidth: .infinity).tourTarget(TT.reciteMic)
                    labeled("chevron.right", short(S.next)) { s.nextPart() }
                }
                FilledButton(title: short(S.handsFree), color: cs.secondaryContainer, fg: cs.onSecondaryContainer, height: 56, fill: true, size: 17) { micOrAsk(s) { s.startHandsFree() } }.tourTarget(TT.reciteHands)
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 12).frame(maxWidth: .infinity)
        .background(cs.container, in: UnevenRoundedRectangle(topLeadingRadius: 24, topTrailingRadius: 24, style: .continuous))
        .background(alignment: .bottom) { cs.container.frame(height: 40).ignoresSafeArea(edges: .bottom) }
    }
}

private struct ReciteOptions: View {
    @Bindable var s: ReciteSession
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    var body: some View {
        let S = app.S, st = app.settings.value.strictness
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text(S.reciteHowMuch).font(.hk(17, .bold)).foregroundStyle(cs.onSurface).padding(.top, 20)
                Flow {
                    ForEach(reciteUnits, id: \.self) { u in
                        Chip(text: short(u == .line ? S.unitLine : u == .sentence ? S.unitHalf : S.unitVerse), selected: s.unit == u) { s.reset(); s.unit = u; s.partIdx = 0 }
                    }
                }
                Text(short(S.strictness)).font(.hk(17, .bold)).foregroundStyle(cs.onSurface).padding(.top, 8)
                Flow {
                    ForEach(Strictness.allCases, id: \.self) { l in Chip(text: short(S.strictName(l)), selected: st == l) { app.settings.update { $0.strictness = l } } }
                }
                Text(S.strictSub(st)).font(.hk(12)).foregroundStyle(cs.onSurfaceVariant)
                SwitchRow(title: short(S.memoryTest), sub: S.memoryTestSub, on: $s.hideText)
                SwitchRow(title: short(S.hearFirst), sub: nil, on: $s.hintFirst)
                Text(S.reciteTip).font(.hk(12)).foregroundStyle(cs.onSurfaceVariant)
            }.padding(.horizontal, 24).padding(.bottom, 24)
        }
    }
}
