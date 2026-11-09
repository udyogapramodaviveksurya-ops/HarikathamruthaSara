import SwiftUI

private enum QType: CaseIterable { case fill, next, hear; var emoji: String { switch self { case .fill: "🧩"; case .next: "➡️"; case .hear: "👂" } } }

private struct Question: Identifiable {
    let id = UUID()
    let type: QType, prompt: String, verse: Verse, options: [String], answer: Int
    var hearLine: Int?
    var answerLine = 0
    var answerWord: Int?
}

private enum QuizMaker {
    static func pool(_ s: Sandhi, _ p: ProgressSnapshot) -> [Verse] {
        let padyas = s.verses.filter { !$0.isRefrain }
        let practised = padyas.filter { p.stat($0.key).listens > 0 }
        return practised.count >= 3 ? practised : Array(padyas.prefix(6))
    }
    static func make(_ s: Sandhi, _ pool: [Verse], canHear: Bool) -> Question {
        func uniq(_ a: [String]) -> [String] { var seen = Set<String>(); return a.filter { seen.insert($0).inserted } }
        let allLines = uniq(s.verses.flatMap(\.lines).map(\.text))
        let allWords = uniq(s.verses.flatMap { $0.words.map(\.text) }.filter { $0.length >= 3 })
        for _ in 0..<20 {
            let v = pool.randomElement()!
            switch (canHear ? QType.allCases : [.fill, .next]).randomElement()! {
            case .fill:
                let li = Int.random(in: 0..<v.lines.count), line = v.lines[li]
                let cands = line.words.indices.filter { line.words[$0].length >= 3 }
                guard line.words.count >= 2, let k = cands.randomElement() else { continue }
                let ans = line.words[k]
                let wrong = Array(allWords.filter { $0 != ans && abs($0.length - ans.length) <= 3 }.shuffled().prefix(3))
                guard wrong.count == 3 else { continue }
                var prompt = ""
                for (i, w) in line.words.enumerated() { prompt += i == k ? "______" : w; prompt += line.sepAfter(i) }
                let opts = (wrong + [ans]).shuffled()
                let g = v.words.first { $0.line == li && $0.index == k }!.global
                return Question(type: .fill, prompt: prompt, verse: v, options: opts, answer: opts.firstIndex(of: ans)!, answerLine: li, answerWord: g)
            case .next:
                guard v.lines.count >= 2 else { continue }
                let k = Int.random(in: 0..<v.lines.count - 1), ans = v.lines[k + 1].text
                let wrong = Array(allLines.filter { $0 != ans && $0 != v.lines[k].text }.shuffled().prefix(3))
                guard wrong.count == 3 else { continue }
                let opts = (wrong + [ans]).shuffled()
                return Question(type: .next, prompt: v.lines[k].text, verse: v, options: opts, answer: opts.firstIndex(of: ans)!, answerLine: k + 1)
            case .hear:
                let li = Int.random(in: 0..<v.lines.count), ans = v.lines[li].text
                let wrong = Array(allLines.filter { $0 != ans }.shuffled().prefix(3))
                guard wrong.count == 3 else { continue }
                let opts = (wrong + [ans]).shuffled()
                return Question(type: .hear, prompt: "", verse: v, options: opts, answer: opts.firstIndex(of: ans)!, hearLine: li, answerLine: li)
            }
        }
        let v = pool[0]
        return Question(type: .next, prompt: v.firstLine, verse: v, options: [v.lines.count > 1 ? v.lines[1].text : v.firstLine], answer: 0, answerLine: min(1, v.lines.count - 1))
    }
}

struct QuizScreen: View {
    let lib: [Sandhi]
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    @State private var sandhiId: Int?
    @State private var qNo = 0
    @State private var correct = 0
    @State private var run = 0
    @State private var picked: Int?
    @State private var q: Question?
    private let total = 10

    private var sandhi: Sandhi { lib.first { $0.id == (sandhiId ?? app.progress.state.lastSandhi) } ?? lib[0] }

    private func newQuestion() {
        let s = sandhi
        let qq = QuizMaker.make(s, QuizMaker.pool(s, app.progress.state), canHear: app.vag.isDownloaded(s.id))
        q = qq
        app.player.pause()
        if let l = qq.hearLine { app.player.sayLine(s.id, qq.verse, l) }
    }

    var body: some View {
        let S = app.S, t = cs.tones, tour = Tours.quiz(app.T)
        TourScroll {
            VStack(alignment: .leading, spacing: 12) {
                if qNo >= total {
                    VStack(spacing: 4) {
                        Text(correct >= 8 ? "🏆" : correct >= 5 ? "🌟" : "🌱").font(.hk(72))
                        Text(S.correctOfTotal(correct, total)).font(.hk(28, .bold))
                        Stars(n: correct >= 9 ? 3 : correct >= 7 ? 2 : correct >= 4 ? 1 : 0, size: 30)
                        Text("+" + S.leaves(correct * 3)).foregroundStyle(cs.primary)
                        FilledButton(title: S.playAgain) { qNo = 0; correct = 0; picked = nil; newQuestion() }.padding(.top, 16)
                    }.padding(24).frame(maxWidth: .infinity).foregroundStyle(t.marigold.on).card(t.marigold.container, radius: 36)
                } else if let q {
                    HStack {
                        Text(S.questionOf(qNo + 1, total)).font(.hk(16, .semibold)).foregroundStyle(cs.onSurface).frame(maxWidth: .infinity, alignment: .leading)
                        if run >= 2 { Pill(text: S.inARow(run)) }
                        Pill(text: "✔ \(correct)", bg: t.leaf.container, fg: t.leaf.on)
                    }
                    HBar(value: Double(qNo) / Double(total), height: 10, color: t.peacock.accent, track: t.peacock.container)
                    questionCard(q).tourTarget(TT.quizQ)
                    VStack(spacing: 12) { ForEach(q.options.indices, id: \.self) { option(q, $0) } }.tourTarget(TT.quizAnswers)
                    if let picked {
                        let ok = picked == q.answer
                        Text(ok ? S.correctPlus : S.wrongGreen).font(.hk(18, .bold)).foregroundStyle(ok ? HK.good : HK.bad).frame(maxWidth: .infinity)
                        HStack(spacing: 10) {
                            if app.vag.isDownloaded(sandhi.id) {
                                OutlineButton(title: "🔊 " + S.hearAnswer, fill: true) {
                                    if let w = q.answerWord { app.player.sayWord(sandhi.id, q.verse, w) } else { app.player.sayLine(sandhi.id, q.verse, q.answerLine) }
                                }
                            }
                            FilledButton(title: S.next + " ▶", fill: true) { self.picked = nil; qNo += 1; if qNo < total { newQuestion() } }
                        }
                    }
                }
            }.padding(16).readable(680)
        }
        .bar(S.gamesTitle, "\(sandhi.id). \(app.mine(sandhi.title))", top: true) {
            TourHelp(tour: tour)
            Menu {
                ForEach(lib) { s in Button("\(s.id). \(app.mine(s.title))") { sandhiId = s.id; qNo = 0; correct = 0; run = 0; picked = nil; newQuestion() } }
            } label: { Text(short(S.changeSandhi)).font(.hk(14, .semibold)).lineLimit(1) }.tourTarget(TT.quizSandhi)
        }
        .onAppear { if q == nil { newQuestion() } }
        .onDisappear { app.player.pause() }
        .modifier(AutoTour(tour: tour))
    }

    private func questionCard(_ q: Question) -> some View {
        let S = app.S
        return VStack(alignment: .leading, spacing: 2) {
            Text("\(q.type.emoji)  " + (q.type == .fill ? S.qFill : q.type == .next ? S.qNext : S.qHear)).font(.hk(16, .bold))
            Text(S.verseLabel(q.verse)).font(.hk(12)).padding(.bottom, 8)
            if q.type == .hear {
                FilledButton(title: "🔊  " + S.hear, color: cs.secondaryContainer, fg: cs.onSecondaryContainer) { if let l = q.hearLine { app.player.sayLine(sandhi.id, q.verse, l) } }
                Text(S.pickWhatHeard).font(.hk(13)).padding(.top, 6)
            } else {
                Text(app.mine(q.prompt)).font(.hk(21))
                if app.script != .kannada { Text(q.prompt).font(.hk(16)).foregroundStyle(cs.secondary) }
            }
        }.padding(18).frame(maxWidth: .infinity, alignment: .leading).foregroundStyle(cs.onPrimaryContainer).card(cs.primaryContainer)
    }

    private func option(_ q: Question, _ i: Int) -> some View {
        let tone = cs.tones[i]
        let border: Color = picked == nil ? tone.accent.opacity(0.5) : i == q.answer ? HK.good : i == picked ? HK.bad : cs.outline.opacity(0.3)
        let bg: Color = picked == nil ? cs.low : i == q.answer ? HK.goodBg : i == picked ? HK.badBg : cs.background
        return BouncyCard(color: bg, enabled: picked == nil, border: (border, 2), action: {
            picked = i
            let ok = i == q.answer
            if ok { correct += 1; run += 1 } else { run = 0 }
            app.progress.recordQuiz(ok)
        }) {
            HStack(spacing: 12) {
                Text(String(Array("ABCD")[min(i, 3)])).font(.hk(16, .black)).foregroundStyle(tone.on).frame(width: 36, height: 36).background(tone.container, in: Circle())
                VStack(alignment: .leading) {
                    Text(app.mine(q.options[i])).font(.hk(19)).multilineTextAlignment(.leading)
                    if app.script != .kannada { Text(q.options[i]).font(.hk(14)).foregroundStyle(cs.secondary).multilineTextAlignment(.leading) }
                }.frame(maxWidth: .infinity, alignment: .leading)
            }.padding(14)
        }
        .scaleEffect(picked != nil && i == q.answer ? 1.02 : 1).animation(.spring(response: 0.3, dampingFraction: 0.3), value: picked)
    }
}

let VOWELS = ["ಅ", "ಆ", "ಇ", "ಈ", "ಉ", "ಊ", "ಋ", "ಎ", "ಏ", "ಐ", "ಒ", "ಓ", "ಔ", "ಅಂ", "ಅಃ"]
let CONSONANTS = ["ಕ", "ಖ", "ಗ", "ಘ", "ಙ", "ಚ", "ಛ", "ಜ", "ಝ", "ಞ", "ಟ", "ಠ", "ಡ", "ಢ", "ಣ", "ತ", "ಥ", "ದ", "ಧ", "ನ", "ಪ", "ಫ", "ಬ", "ಭ", "ಮ", "ಯ", "ರ", "ಲ", "ವ", "ಶ", "ಷ", "ಸ", "ಹ", "ಳ", "ಕ್ಷ", "ಜ್ಞ"]
let SIGNS = ["", "ಾ", "ಿ", "ೀ", "ು", "ೂ", "ೃ", "ೆ", "ೇ", "ೈ", "ೊ", "ೋ", "ೌ", "ಂ", "ಃ", "್"]
let GAME_SYLLABLES = ["ಕಾ", "ಗಿ", "ದು", "ಭೂ", "ಮೆ", "ನೇ", "ಹೊ", "ಶ್ರೀ", "ಕೃ", "ತ್ತ", "ಮಂ", "ವೈ", "ಧ್ಯ", "ಪ್ರ", "ಲ್ಲ", "ಳು", "ಜ್ಞಾ", "ಷ್ಣು", "ರ್ಮ"]
let PRACTICE_WORDS = ["ಅಮ್ಮ", "ಅಪ್ಪ", "ಮನೆ", "ನೀರು", "ಹಾಲು", "ಅನ್ನ", "ಊಟ", "ಉಪ್ಪು", "ಮರ", "ಹೂವು", "ಹಣ್ಣು", "ಎಲೆ", "ಕಣ್ಣು", "ಕಿವಿ", "ಬಾಯಿ", "ಕೈ", "ಕಾಲು", "ಮಗು", "ಆನೆ", "ಹಸು", "ಬೆಕ್ಕು", "ನಾಯಿ", "ಇಲಿ", "ಮೀನು", "ಗಿಳಿ", "ಕೋಳಿ", "ಮಳೆ", "ಒಲೆ", "ಓದು", "ಏಳು", "ಶಾಲೆ", "ಪುಸ್ತಕ", "ಸೂರ್ಯ", "ಚಂದ್ರ", "ದೀಪ", "ಗಂಗೆ", "ತುಳಸಿ", "ಘಂಟೆ", "ಫಲ", "ಗುರು", "ದೇವರು", "ಹರಿ", "ರಾಮ", "ಕೃಷ್ಣ", "ಲಕ್ಷ್ಮಿ", "ಹನುಮ", "ವಿಠಲ", "ಭಕ್ತಿ", "ಧರ್ಮ", "ಜ್ಞಾನ"]
private let SAY_LETTERS = VOWELS + CONSONANTS + GAME_SYLLABLES

struct LettersScreen: View {
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    @State private var tab = 0
    var body: some View {
        let S = app.S, tour = Tours.letters(app.T)
        let tabs = ["ಸ್ವರ " + S.tabVowels, "ವ್ಯಂಜನ " + S.tabConsonants, "ಕಾಗುಣಿತ " + S.tabSigns, S.tabSay, S.tabGame]
        VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 2) {
                    ForEach(tabs.indices, id: \.self) { i in
                        Button { tab = i } label: {
                            VStack(spacing: 6) {
                                Text(tabs[i]).font(.hk(12, tab == i ? .bold : .regular)).multilineTextAlignment(.center).foregroundStyle(tab == i ? cs.primary : cs.onSurfaceVariant)
                                Capsule().fill(tab == i ? cs.primary : .clear).frame(height: 3)
                            }.padding(.horizontal, 12).padding(.top, 8).frame(minHeight: 44)
                        }.buttonStyle(.plain)
                    }
                }.padding(.horizontal, 8)
            }.tourTarget(TT.lettersTabs)
            Divider()
            switch tab {
            case 0: LetterGrid(letters: VOWELS, header: S.vowelsHeader, target: true)
            case 1: LetterGrid(letters: CONSONANTS, header: S.consonantsHeader, target: false)
            case 2: KagunitaTab()
            case 3: SayItTab()
            default: LetterGame()
            }
        }
        .bar(S.lettersTitle, S.lettersSub, top: true) { TourHelp(tour: tour) }
        .modifier(AutoTour(tour: tour))
        .onDisappear { app.player.pause() }
    }
}

private struct LetterCell: View {
    let kn: String, ta: String
    let tone: Tone
    let action: () -> Void
    var body: some View {
        BouncyCard(color: tone.container, fg: tone.on, action: action) {
            VStack(spacing: 0) {
                Text(kn).font(.hk(34, .bold)).lineLimit(1).minimumScaleFactor(0.6)
                Text(ta).font(.hk(18)).opacity(0.75).lineLimit(1).minimumScaleFactor(0.6)
            }.padding(.vertical, 10).frame(maxWidth: .infinity)
        }
    }
}

private struct LetterGrid: View {
    let letters: [String], header: String
    let target: Bool
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text(header).font(.hk(13)).foregroundStyle(cs.onSurfaceVariant)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 76), spacing: 8)], spacing: 8) {
                    ForEach(Array(letters.enumerated()), id: \.1) { i, l in
                        LetterCell(kn: l, ta: app.mine(l), tone: cs.tones[i]) { app.player.sayLetter(l) }.modifier(FirstTarget(on: target && i == 0, id: TT.lettersFirst))
                    }
                }
            }.padding(12)
        }
    }
}

private struct KagunitaTab: View {
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    @State private var base = "ಕ"
    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 4) {
                Text(app.S.letterLabel).font(.hk(15, .semibold)).foregroundStyle(cs.onSurface)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach(CONSONANTS, id: \.self) { c in
                            Button { base = c } label: { Text(c).font(.hk(22, c == base ? .bold : .regular)).foregroundStyle(c == base ? cs.primary : cs.onSurface).frame(minWidth: 44, minHeight: 44) }
                        }
                    }
                }
            }.padding(8)
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    Text(app.S.signsHeader).font(.hk(13)).foregroundStyle(cs.onSurfaceVariant)
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 76), spacing: 8)], spacing: 8) {
                        ForEach(Array(SIGNS.enumerated()), id: \.0) { i, sign in
                            let c = base + sign
                            LetterCell(kn: c, ta: app.mine(c), tone: cs.tones[i]) { app.player.sayLetter(c) }
                        }
                    }
                }.padding(12)
            }
        }
    }
}

private struct LetterGame: View {
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    @State private var round: (String, [String])?
    @State private var score = 0
    @State private var run = 0
    @State private var picked: Int?
    private let pool = VOWELS + CONSONANTS + GAME_SYLLABLES

    private func next() {
        let t = pool.randomElement()!, sc = app.script
        let wrong = pool.filter { $0 != t && Scripts.render($0, sc) != Scripts.render(t, sc) }.shuffled().prefix(3)
        round = (t, (Array(wrong) + [t]).shuffled()); picked = nil
    }

    var body: some View {
        let S = app.S
        ScrollView {
            VStack(spacing: 12) {
                HStack {
                    Text(S.score(score)).font(.hk(16, .bold)).foregroundStyle(cs.onSurface).frame(maxWidth: .infinity, alignment: .leading)
                    if run >= 3 { Pill(text: "🔥 \(run)") }
                    Pill(text: S.forBadge(app.progress.state.lettersRight))
                }
                Text(S.whichMatches).font(.hk(16)).foregroundStyle(cs.onSurface)
                if let (target, options) = round {
                    Button { app.player.sayLetter(target) } label: {
                        Text(target).font(.hk(64, .bold)).padding(.horizontal, 48).padding(.vertical, 20).foregroundStyle(cs.onPrimaryContainer).card(cs.primaryContainer, radius: 36)
                    }.buttonStyle(Bouncy())
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                        ForEach(options.indices, id: \.self) { i in
                            let opt = options[i], tone = cs.tones[i]
                            let c: Color = picked == nil ? tone.accent.opacity(0.6) : opt == target ? HK.good : i == picked ? HK.bad : cs.outline.opacity(0.3)
                            BouncyCard(color: tone.container, fg: tone.on, enabled: picked == nil, border: (c, 2), action: {
                                picked = i
                                let ok = opt == target
                                if ok { score += 1; run += 1 } else { run = 0 }
                                app.progress.recordLetter(ok)
                                app.player.sayLetter(target)
                            }) { Text(app.mine(opt)).font(.hk(30)).lineLimit(1).minimumScaleFactor(0.6).padding(18).frame(maxWidth: .infinity) }
                        }
                    }
                    if let picked {
                        Text(options[picked] == target ? S.rightMark : S.itIs(app.mine(target))).font(.hk(20, .bold)).foregroundStyle(cs.onSurface)
                        FilledButton(title: S.next + " ▶") { next() }
                    }
                }
            }.padding(16).readable(560)
        }.onAppear { if round == nil { next() } }
    }
}

private enum SayRefs {
    static let shared: SayCheck.Refs? = (try? Data(contentsOf: Paths.asset("saycheck/refs.bin"))).flatMap { try? SayCheck.load($0) }
}

/// Read Kannada → 🎤 Say it: hear a letter or word, say it, and the ear tells whether it was right — offline.
struct SayItTab: View {
    @Environment(HkApp.self) private var app
    @Environment(\.cs) private var cs
    @Environment(\.scenePhase) private var phase
    @State private var listener: Listener?
    @State private var refs: SayCheck.Refs?
    @State private var hasMic = Listener.micGranted
    @State private var words = false
    @State private var index = 0
    @State private var verdict: SayCheck.Verdict?
    @State private var judging = false
    @State private var right = 0
    @State private var tried = 0
    @State private var handsFree = false
    @State private var handsJob: Task<Void, Never>?
    @State private var playingClip = false
    @State private var stoppedForSilence = false

    private var items: [String] { words ? PRACTICE_WORDS : SAY_LETTERS }
    private var item: String { items[((index % items.count) + items.count) % items.count] }
    private func spelled(_ t: String) -> String { PRACTICE_WORDS.contains(t) ? Scripts.renderWord(t, app.script, app.settings.value.tamilSuperscripts) : app.mine(t) }
    private func both(_ t: String) -> String { let s = spelled(t); return s == t || s.isEmpty ? t : "\(t) (\(s))" }

    private func hear(_ t: String) { listener?.cancel(); words ? app.player.sayPracticeWord(t) : app.player.sayLetter(t) }
    private func go(_ d: Int) { listener?.cancel(); verdict = nil; index = ((index + d) % items.count + items.count) % items.count }

    private func judge(_ target: String, _ isWord: Bool, _ r: SayCheck.Refs) async -> SayCheck.Verdict {
        let lp = listener?.lastLogProbs, err = listener?.state.error, st = app.settings.value.strictness
        judging = true
        defer { judging = false }
        return await Task.detached(priority: .userInitiated) {
            if err != nil || lp == nil { return SayCheck.Verdict.notHeard }
            return isWord ? SayCheck.judgeWord(lp, target, words: PRACTICE_WORDS, refs: r) : SayCheck.judgeLetter(lp, target, pool: SAY_LETTERS, refs: r, st)
        }.value
    }

    private func score(_ v: SayCheck.Verdict, _ isWord: Bool) {
        guard v != .notHeard else { return }
        tried += 1
        let ok = v == .ok
        if ok { right += 1 }
        if !isWord { app.progress.recordLetter(ok) }
        ok ? Chime.good() : Chime.bad()
    }

    private func needMic() -> Bool {
        if hasMic { return false }
        Task { hasMic = await Listener.micAllowed(); if !hasMic { Notifier.openSystemSettings() } }
        return true
    }

    private func say() {
        guard !needMic(), let listener else { return }
        app.player.pause(); app.player.clip.pause()
        verdict = nil
        let target = item, isWord = words
        listener.onSegmentDone = {
            guard let r = refs else { return }
            Task {
                let v = await judge(target, isWord, r)
                guard target == item else { return }
                verdict = v
                score(v, isWord)
            }
        }
        listener.start(continuous: false, maxSeconds: 8, quickEnd: true)
    }

    private func startHandsFree() {
        guard !needMic(), let r = refs, let listener else { return }
        listener.cancel(); stoppedForSilence = false; verdict = nil; handsFree = true
        UIApplication.shared.isIdleTimerDisabled = true
        let isWord = words
        handsJob = Task {
            var silent = 0
            defer {
                playingClip = false; judging = false; listener.cancel(); app.player.clip.pause(); handsFree = false
                UIApplication.shared.isIdleTimerDisabled = false
            }
            do {
                while true {
                    let target = item
                    for n in 0..<2 {
                        if verdict == .ok && n > 0 { continue }
                        verdict = nil
                        app.player.pause()
                        playingClip = true
                        _ = try await app.player.clip.playAsset(isWord ? wordAsset(target) : letterAsset(target))
                        playingClip = false
                        try await Task.sleep(for: .milliseconds(350))
                        var done = false
                        listener.onSegmentDone = { done = true }
                        listener.start(continuous: false, maxSeconds: 5, quickEnd: true)
                        while !done { try await Task.sleep(for: .milliseconds(40)) }
                        let v = await judge(target, isWord, r)
                        verdict = v
                        score(v, isWord)
                        silent = v == .notHeard ? silent + 1 : 0
                        if silent >= 3 { stoppedForSilence = true; return }
                        try await Task.sleep(for: .milliseconds(900))
                    }
                    verdict = nil
                    index = ((items.firstIndex(of: target) ?? 0) + 1) % items.count
                }
            } catch {}
        }
    }
    private func stopHandsFree() { handsJob?.cancel(); handsJob = nil }

    var body: some View {
        let S = app.S
        ScrollView {
            if let listener { content(S, listener) }
        }
        .task {
            if listener == nil { let l = Listener(ear: app.ear); l.strs = app.S; listener = l }
            AudioRoute.useVoice()
            let r = await Task.detached { SayRefs.shared }.value
            refs = r
        }
        .onDisappear { stopHandsFree(); listener?.cancel(); AudioRoute.usePlayback() }
        .onChange(of: phase) { _, p in if p == .background { stopHandsFree() } }
    }

    private func content(_ S: Strs, _ listener: Listener) -> some View {
        let ls = listener.state, earReady = app.ear.state == .ready
        let busy = ls.listening || ls.analyzing || judging
        return VStack(spacing: 12) {
            if !earReady { EarBanner() }
            HStack(spacing: 8) {
                Chip(text: S.sayLetters, selected: !words, enabled: !handsFree) { if words { words = false; index = 0; verdict = nil; listener.cancel() } }
                Chip(text: S.sayWords, selected: words, enabled: !handsFree) { if !words { words = true; index = 0; verdict = nil; listener.cancel() } }
                Spacer()
            }
            if tried > 0 { Pill(text: S.sayStreak(right, tried)) }
            Text(handsFree ? S.sayHandsFreeHow : S.sayHow).font(.hk(15)).foregroundStyle(cs.onSurfaceVariant).multilineTextAlignment(.center)
            Button { if !handsFree { hear(item) } } label: {
                VStack(spacing: 2) {
                    Text(item).font(.hk(words ? 52 : 72, .bold)).lineLimit(1).minimumScaleFactor(0.6)
                    Text(spelled(item)).font(.hk(22)).opacity(0.75)
                    Text("🔊").font(.hk(22))
                }.padding(.horizontal, 40).padding(.vertical, 18).foregroundStyle(cs.onPrimaryContainer).card(cs.primaryContainer, radius: 36)
            }.buttonStyle(Bouncy())
            if handsFree {
                FilledButton(title: S.sayHandsFreeStop, color: HK.bad, fg: .white, height: 96, fill: true, size: 26, action: stopHandsFree)
            } else {
                Button { if ls.listening { listener.stop() } else if !busy { say() } } label: {
                    Text(ls.listening ? "⏹" : "🎤").font(.hk(40)).frame(width: 96, height: 96).background(ls.listening ? HK.bad : cs.primary, in: Circle()).opacity(earReady && refs != nil ? 1 : 0.4)
                }.buttonStyle(Bouncy()).disabled(!(earReady && refs != nil && !ls.analyzing && !judging)).accessibilityLabel(S.reciteCheck)
            }
            if ls.listening {
                Text(S.sayListening).font(.hk(16, .semibold)).foregroundStyle(cs.onSurface)
                HBar(value: Double(ls.level), height: 6, color: cs.primary, track: cs.outlineVariant).frame(width: 220)
            } else if ls.analyzing || judging { Text(S.checking).foregroundStyle(cs.onSurface) }
            else if handsFree && playingClip { Text(S.sayHandsFreeHear).font(.hk(16, .semibold)).foregroundStyle(cs.onSurface) }
            if let v = verdict { verdictCard(S, v, ls) }
            if !handsFree {
                HStack(spacing: 12) {
                    OutlineButton(title: "◀") { go(-1) }
                    FilledButton(title: S.next + " ▶") { go(1) }
                }.padding(.top, 4)
                FilledButton(title: S.sayHandsFreeStart, height: 64, fill: true, size: 20, enabled: earReady && refs != nil && !busy, action: startHandsFree)
                if stoppedForSilence { Text(S.sayHandsFreeStopped).font(.hk(15)).foregroundStyle(cs.onSurface).multilineTextAlignment(.center) }
            }
        }.padding(16).readable(560)
    }

    private func verdictCard(_ S: Strs, _ v: SayCheck.Verdict, _ ls: ListenState) -> some View {
        let bg: Color = { switch v { case .ok: HK.good.opacity(0.15); case .mistake: cs.tertiaryContainer; default: cs.surfaceVariant } }()
        let text: String = {
            switch v {
            case .ok: S.sayOk
            case .mistake(let said, let k): S.sayMistake(both(said), k == .length)
            case .wrong(let h): words && h != nil ? S.sayOtherWord(both(h!)) : S.sayNotSure
            case .notHeard: ls.error ?? S.didntHear
            }
        }()
        return VStack(alignment: .leading, spacing: 8) {
            Text(text).font(.hk(17, .semibold)).foregroundStyle(cs.onSurface)
            if case .mistake(let said, _) = v, !handsFree {
                Flow {
                    FilledButton(title: "🔊 " + both(item)) { hear(item) }
                    OutlineButton(title: "🔊 " + both(said)) { listener?.cancel(); app.player.sayLetter(said) }
                }
            }
            if case .wrong = v, !words, !handsFree { Text(S.sayLetterNote).font(.hk(13)).foregroundStyle(cs.onSurfaceVariant) }
        }.padding(14).frame(maxWidth: .infinity, alignment: .leading).card(bg, radius: 18)
    }
}
