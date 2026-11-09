import Foundation

/// How strictly a recitation is checked. Numbers are the calibrated Android/server values.
enum Strictness: String, CaseIterable, Sendable {
    case gentle = "GENTLE", normal = "NORMAL", strict = "STRICT"
    var wordPass: Float { switch self { case .gentle: 0.6; case .normal: 0.75; case .strict: 0.85 } }
    var passMark: Int { switch self { case .gentle: 60; case .normal: 75; case .strict: 85 } }
    var judge: Set<Pronunciation.Kind> { switch self { case .gentle: []; case .normal: [.length]; case .strict: [.length, .consonant] } }
    var flagBelow: Float { switch self { case .gentle: 0; case .normal: -3; case .strict: -1 } }
}

private func sc(_ s: String) -> Unicode.Scalar { s.unicodeScalars.first! }
private func pairs(_ s: String) -> [(Unicode.Scalar, Unicode.Scalar)] {
    s.split(separator: " ").map { let u = Array($0.unicodeScalars); return (u[0], u[1]) }
}

/// Compares what was heard with the verse letter by letter, so differences in where words are split don't matter.
enum Matcher {
    struct Result {
        var wordScores: [Float]
        var reached: Int
        var overall: Int
        var sounds: [Int: [Pronunciation.Issue]] = [:]
        func isSaid(_ i: Int, _ s: Strictness) -> Bool { (i < wordScores.count ? wordScores[i] : 0) >= s.wordPass }
        func isCorrect(_ i: Int, _ s: Strictness) -> Bool { isSaid(i, s) && sounds[i] == nil }
    }

    static func withSounds(_ r: Result, _ target: [String], _ sounds: [Int: [Pronunciation.Issue]], _ s: Strictness) -> Result {
        var w = r; w.sounds = sounds
        let len = target.map { normalize($0, true).count }
        let total = len.reduce(0, +)
        guard total > 0 else { return w }
        let good = target.indices.reduce(0) { $0 + (w.isCorrect($1, s) ? len[$1] : 0) }
        w.overall = good * 100 / total
        return w
    }

    private static let lenientMap: [Unicode.Scalar: Unicode.Scalar] = {
        var d: [Unicode.Scalar: Unicode.Scalar] = [:]
        for (a, b) in pairs("ಖಕ ಘಗ ಛಚ ಝಜ ಠಟ ಢಡ ಥತ ಧದ ಫಪ ಭಬ") { d[a] = b }
        for (a, b) in pairs("ಆಅ ಈಇ ಊಉ ಏಎ ಓಒ ೀಿ ೂು ೇೆ ೋೊ ಣನ ಳಲ ಶಸ ಷಸ ೠಋ ೄೃ ಙಂ ಞಂ") { d[a] = b }
        return d
    }()
    private static func isKannada(_ c: Unicode.Scalar) -> Bool { c.value >= 0x0C80 && c.value <= 0x0CFF }
    private static let nasals = Set("ಙಞಣನಮ".unicodeScalars)
    private static let virama = sc("್"), anusvara = sc("ಂ"), nukta = sc("಼"), d0 = sc("೦").value, d9 = sc("೯").value

    static func normalize(_ text: String, _ lenient: Bool) -> Scalars {
        let s = text.scalars
        var out = Scalars(); var i = 0
        while i < s.count {
            var c = s[i]
            if !isKannada(c) || c == nukta || (c.value >= d0 && c.value <= d9) { i += 1; continue }
            if lenient {
                if nasals.contains(c) && i + 1 < s.count && s[i + 1] == virama { out.append(anusvara); i += 2; continue }
                c = lenientMap[c] ?? c
                if c == virama && i + 1 < s.count && !isKannada(s[i + 1]) { i += 1; continue }
            }
            out.append(c); i += 1
        }
        return out
    }

    static func match(_ target: [String], _ heard: String, lenient: Bool = true) -> Result {
        var t = Scalars(), owner: [Int] = [], wordLen = [Int](repeating: 0, count: target.count)
        for (wi, w) in target.enumerated() {
            let n = normalize(w, lenient)
            wordLen[wi] = n.count
            for c in n { t.append(c); owner.append(wi) }
        }
        let h = normalize(heard, lenient)
        let n = t.count, m = h.count
        if n == 0 { return Result(wordScores: [], reached: -1, overall: 0) }
        if m == 0 { return Result(wordScores: [Float](repeating: 0, count: target.count), reached: -1, overall: 0) }
        var dp = [[Int]](repeating: [Int](repeating: 0, count: m + 1), count: n + 1)
        for i in 0...n { dp[i][0] = i }
        for j in 0...m { dp[0][j] = j }
        for i in 1...n { for j in 1...m {
            let cost = t[i - 1] == h[j - 1] ? 0 : 1
            dp[i][j] = min(dp[i - 1][j - 1] + cost, dp[i - 1][j] + 1, dp[i][j - 1] + 1)
        } }
        var endI = n, best = dp[n][m]
        for i in 0...n where dp[i][m] < best { best = dp[i][m]; endI = i }
        var matched = [Bool](repeating: false, count: n)
        var i = endI, j = m
        while i > 0 && j > 0 {
            let cost = t[i - 1] == h[j - 1] ? 0 : 1
            if dp[i][j] == dp[i - 1][j - 1] + cost { if cost == 0 { matched[i - 1] = true }; i -= 1; j -= 1 }
            else if dp[i][j] == dp[i - 1][j] + 1 { i -= 1 }
            else { j -= 1 }
        }
        var got = [Int](repeating: 0, count: target.count)
        for k in 0..<n where matched[k] { got[owner[k]] += 1 }
        let scores = target.indices.map { wordLen[$0] == 0 ? Float(1) : Float(got[$0]) / Float(wordLen[$0]) }
        return Result(wordScores: scores, reached: endI == 0 ? -1 : owner[endI - 1], overall: matched.filter { $0 }.count * 100 / n)
    }
}

/// Pronunciation scoring from the ear's CTC output; the server's tools/ear/gop.py is the reference.
enum Pronunciation {
    enum Kind: Sendable { case length, consonant }
    struct Mark { let index: Int; let letter: Unicode.Scalar; let margin: Float; let kind: Kind; let best: Unicode.Scalar? }
    struct Issue: Sendable { let word: Int; let kind: Kind; let margin: Float; let said: String }

    static let vocab: [String] = ["<s>", "<pad>", "</s>", "<unk>", "|"] +
        "ಂ ಃ ಅ ಆ ಇ ಈ ಉ ಊ ಋ ಎ ಏ ಐ ಒ ಓ ಔ ಕ ಖ ಗ ಘ ಚ ಛ ಜ ಝ ಞ ಟ ಠ ಡ ಢ ಣ ತ ಥ ದ ಧ ನ ಪ ಫ ಬ ಭ ಮ ಯ ರ ಲ ಳ ವ ಶ ಷ ಸ ಹ ಾ ಿ ೀ ು ೂ ೃ ೆ ೇ ೈ ೊ ೋ ೌ ್".split(separator: " ").map(String.init)
    private static let ids: [Unicode.Scalar: Int] = {
        var d: [Unicode.Scalar: Int] = [:]
        for (i, v) in vocab.enumerated() where v.unicodeScalars.count == 1 { d[v.unicodeScalars.first!] = i }
        return d
    }()
    static let blank = 1
    private static let space = 4
    private static let alt: [Unicode.Scalar: [Unicode.Scalar: Kind]] = {
        var d: [Unicode.Scalar: [Unicode.Scalar: Kind]] = [:]
        for (a, b) in pairs("ಆಅ ಈಇ ಊಉ ಏಎ ಓಒ ೀಿ ೂು ೇೆ ೋೊ") { d[a, default: [:]][b] = .length; d[b, default: [:]][a] = .length }
        for (a, b) in pairs("ಖಕ ಘಗ ಛಚ ಝಜ ಠಟ ಢಡ ಥತ ಧದ ಫಪ ಭಬ ಣನ ಳಲ ಶಸ ಷಸ ಶಷ") { d[a, default: [:]][b] = .consonant; d[b, default: [:]][a] = .consonant }
        return d
    }()

    static func clean(_ text: String) -> Scalars {
        var o = Scalars()
        for c in text.unicodeScalars {
            if c == " " { if let l = o.last, l != " " { o.append(c) } }
            else if ids[c] != nil { o.append(c) }
        }
        while o.last == " " { o.removeLast() }
        return o
    }
    private static func encode(_ s: Scalars) -> [Int] { s.compactMap { $0 == " " ? space : ids[$0] } }

    static func greedy(_ lp: [[Float]]) -> String {
        var out = ""; var prev = -1
        for f in lp {
            var best = 0
            for k in f.indices where f[k] > f[best] { best = k }
            if best != prev && best != blank && best > 3 { out += best == space ? " " : vocab[best] }
            prev = best
        }
        return out.split(separator: " ", omittingEmptySubsequences: true).joined(separator: " ")
    }

    private static func logAdd(_ a: Double, _ b: Double) -> Double {
        if a == -.infinity { return b }
        if b == -.infinity { return a }
        return a > b ? a + log1p(exp(b - a)) : b + log1p(exp(a - b))
    }

    static func logLik(_ lp: [[Float]], _ ids: [Int]) -> Double {
        guard !lp.isEmpty else { return -.infinity }
        let s = 2 * ids.count + 1
        let ext = (0..<s).map { $0 % 2 == 0 ? blank : ids[$0 / 2] }
        var a = [Double](repeating: -.infinity, count: s)
        a[0] = Double(lp[0][blank])
        if s > 1 { a[1] = Double(lp[0][ext[1]]) }
        var n = [Double](repeating: 0, count: s)
        for ti in 1..<max(lp.count, 1) {
            let f = lp[ti]
            for j in 0..<s {
                var v = a[j]
                if j >= 1 { v = logAdd(v, a[j - 1]) }
                if j >= 2 && ext[j] != blank && ext[j] != ext[j - 2] { v = logAdd(v, a[j - 2]) }
                n[j] = v + Double(f[ext[j]])
            }
            swap(&a, &n)
        }
        return s > 1 ? logAdd(a[s - 1], a[s - 2]) : a[s - 1]
    }

    static func margins(_ lp: [[Float]], _ text: Scalars) -> [Mark] {
        let base = logLik(lp, encode(text))
        var out: [Mark] = []
        let aa = sc("ಾ")
        for (i, c) in text.enumerated() {
            // nil key = the sign dropped
            var alts: [(Unicode.Scalar?, Kind)] = (alt[c] ?? [:]).map { ($0.key, $0.value) }
            if c == aa { alts.append((nil, .length)) }
            let before: Unicode.Scalar? = i > 0 ? text[i - 1] : nil
            let after: Unicode.Scalar? = i + 1 < text.count ? text[i + 1] : nil
            if c == before || c == after { continue }
            alts.removeAll { $0.0 != nil && ($0.0 == before || $0.0 == after) }
            if alts.isEmpty { continue }
            var bestLl = -Double.infinity; var bestAlt: Unicode.Scalar?; var kind = Kind.length
            for (b, k) in alts {
                var swapped = Scalars(text[..<i]); if let b { swapped.append(b) }; swapped.append(contentsOf: text[(i + 1)...])
                let ll = logLik(lp, encode(swapped))
                if ll > bestLl { bestLl = ll; bestAlt = b; kind = k }
            }
            out.append(Mark(index: i, letter: c, margin: Float(base - bestLl), kind: kind, best: bestAlt))
        }
        return out
    }

    static func judge(_ lp: [[Float]], _ words: [String], _ joins: [Bool], reached: Int, present: (Int) -> Bool, _ st: Strictness) -> [Int: [Issue]] {
        if st.judge.isEmpty || reached < 0 || lp.isEmpty { return [:] }
        var owner: [Int] = [], text = Scalars(), starts = [Int](repeating: 0, count: words.count)
        for w in 0...min(reached, words.count - 1) {
            starts[w] = text.count
            for c in clean(words[w]) where c != " " { text.append(c); owner.append(w) }
            if (w < joins.count ? joins[w] : true) && w < reached { text.append(" "); owner.append(-1) }
        }
        var out: [Int: [Issue]] = [:]
        for m in margins(lp, text) {
            let w = owner[m.index]
            if w < 0 || !st.judge.contains(m.kind) || m.margin >= st.flagBelow || !present(w) { continue }
            let unit = clean(words[w]).filter { $0 != " " }
            let at = m.index - starts[w]
            var said = Scalars(unit[..<at]); if let b = m.best { said.append(b) }; said.append(contentsOf: unit[(at + 1)...])
            out[w, default: []].append(Issue(word: w, kind: m.kind, margin: m.margin, said: String(scalars: said)))
        }
        return out
    }
}

/// Read Kannada → "Say it": DTW of her attempt against reference fingerprints (Bundled/saycheck/refs.bin).
enum SayCheck {
    private static let clip: Float = 15, blank = 1
    static let kLetter = 7, slack: Double = 0.1

    enum Verdict: Equatable { case ok, mistake(String, Pronunciation.Kind), wrong(String?), notHeard }

    final class Print: @unchecked Sendable {
        let lp: [[Float]]; let p: [[Float]]
        init(_ lp: [[Float]]) { self.lp = lp; p = lp.map { $0.map { Float(exp(Double($0))) } } }
    }
    final class Refs: @unchecked Sendable {
        let ear: String; let prints: [String: [Print]]
        init(ear: String, prints: [String: [Print]]) { self.ear = ear; self.prints = prints }
        func has(_ t: String) -> Bool { prints[t] != nil }
    }

    static func load(_ d: Data) throws -> Refs {
        let b = [UInt8](d); var p = 0
        func u16() -> Int { defer { p += 2 }; return Int(b[p]) | Int(b[p + 1]) << 8 }
        func str() -> String { let n = u16(); defer { p += n }; return String(decoding: b[p..<p + n], as: UTF8.self) }
        guard b.count > 10, String(decoding: b[0..<4], as: UTF8.self) == "SAY1" else { throw TextError.empty }
        p = 4
        let n = Int(b[4]) | Int(b[5]) << 8 | Int(b[6]) << 16 | Int(b[7]) << 24
        p = 8
        let ear = str()
        var out: [String: [Print]] = [:]
        for _ in 0..<n {
            let t = str()
            p += 1 // voice index
            let frames = u16()
            var lp: [[Float]] = []; lp.reserveCapacity(frames)
            for _ in 0..<frames { lp.append((0..<66).map { -Float(b[p + $0]) * clip / 255 }); p += 66 }
            out[t, default: []].append(Print(lp))
        }
        return Refs(ear: ear, prints: out)
    }

    static func fingerprint(_ lp: [[Float]]) -> Print {
        let keep = lp.filter { exp(Double($0[blank])) < 0.95 }
        let x = keep.count >= 3 ? keep : lp
        return Print(x.map { $0.map { max($0, -clip) } })
    }

    static func dtw(_ a: Print, _ b: Print) -> Double {
        let n = a.lp.count, m = b.lp.count
        if n == 0 || m == 0 { return .infinity }
        var prev = [Double](repeating: .infinity, count: m + 1); prev[0] = 0
        var cur = [Double](repeating: 0, count: m + 1)
        for i in 1...n {
            cur[0] = .infinity
            let al = a.lp[i - 1], ap = a.p[i - 1]
            for j in 1...m {
                let bl = b.lp[j - 1], bp = b.p[j - 1]
                var c: Float = 0
                for k in al.indices { c += (ap[k] - bp[k]) * (al[k] - bl[k]) }
                cur[j] = 0.5 * Double(c) + min(prev[j], cur[j - 1], prev[j - 1])
            }
            swap(&prev, &cur)
        }
        return prev[m] / Double(n + m) + 0.5 * abs(log(Double(n) / Double(m)))
    }

    private static func dist(_ her: Print, _ t: String, _ r: Refs) -> Double { r.prints[t]?.map { dtw(her, $0) }.min() ?? .infinity }

    private static let swaps: [Unicode.Scalar: [(Unicode.Scalar, Pronunciation.Kind)]] = {
        var d: [Unicode.Scalar: [(Unicode.Scalar, Pronunciation.Kind)]] = [:]
        for (a, b) in pairs("ಅಆ ಇಈ ಉಊ ಎಏ ಒಓ ಐಏ ಔಓ ಿೀ ುೂ ೆೇ ೊೋ ೈೇ ೌೋ") { d[a, default: []].append((b, .length)); d[b, default: []].append((a, .length)) }
        for (a, b) in pairs("ಕಖ ಗಘ ಚಛ ಜಝ ಟಠ ಡಢ ತಥ ದಧ ಪಫ ಬಭ ಟತ ಠಥ ಡದ ಢಧ ಣನ ಳಲ ಶಸ ಷಸ ಶಷ ಙನ ಞನ ವಬ") { d[a, default: []].append((b, .consonant)); d[b, default: []].append((a, .consonant)) }
        return d
    }()

    static func mistakes(_ t: String) -> [(String, Pronunciation.Kind)] {
        let u = t.scalars
        var out: [(String, Pronunciation.Kind)] = []
        func add(_ s: String, _ k: Pronunciation.Kind) { if !out.contains(where: { $0.0.unicodeScalars.elementsEqual(s.unicodeScalars) && $0.1 == k }) { out.append((s, k)) } }
        for (i, c) in u.enumerated() {
            for (b, k) in swaps[c] ?? [] { var v = u; v[i] = b; add(String(scalars: v), k) }
        }
        let aa = sc("ಾ"), ka = sc("ಕ").value, ha = sc("ಹ").value
        if let l = u.last, l.value >= ka, l.value <= ha { add(t + "ಾ", .length) }
        if u.last == aa { add(String(scalars: u.dropLast()), .length) }
        return out
    }

    static func judgeLetter(_ lp: [[Float]]?, _ target: String, pool: [String], refs: Refs, _ st: Strictness) -> Verdict {
        guard let lp, !lp.isEmpty else { return .notHeard }
        let her = fingerprint(lp)
        let ms = mistakes(target)
        var d: [String: Double] = [:]
        for c in pool + ms.map(\.0) + [target] where refs.has(c) && d[c] == nil { d[c] = dist(her, c, refs) }
        guard let dt = d[target] else { return .ok }
        let closer = d.filter { $0.key != target && $0.value < dt }
        if closer.count >= kLetter { return .wrong(closer.min { $0.value < $1.value }?.key) }
        for (m, k) in ms { if let dm = d[m], st.judge.contains(k), dm * (1 + slack) < dt { return .mistake(m, k) } }
        return .ok
    }

    static func judgeWord(_ lp: [[Float]]?, _ target: String, words: [String], refs: Refs) -> Verdict {
        guard let lp, !lp.isEmpty else { return .notHeard }
        let her = fingerprint(lp)
        var d: [String: Double] = [:]
        for w in words where refs.has(w) { d[w] = dist(her, w, refs) }
        guard let dt = d[target], let best = d.min(by: { $0.value < $1.value }) else { return .ok }
        return best.key == target || best.value >= dt ? .ok : .wrong(best.key)
    }
}
