import Foundation

/// All text handling works on Unicode scalars, never Swift Characters: Kannada letters and their signs must be counted
/// one by one exactly as the Android (UTF-16) code does, or scoring and transliteration would drift from it.
typealias Scalars = [Unicode.Scalar]
extension String {
    var scalars: Scalars { Array(unicodeScalars) }
    /// Kotlin's String.length for this app's BMP-only text.
    var length: Int { utf16.count }
    init(scalars s: some Sequence<Unicode.Scalar>) {
        var v = String.UnicodeScalarView(); v.append(contentsOf: s); self.init(v)
    }
}

struct WordRef: Hashable, Sendable { let line: Int; let index: Int; let global: Int; let text: String }

/// One line as printed in the Tadipatri script. `seps` per word: ' ' space · '-' gana break · '_' gana break + space ·
/// '|' "--" silent pause · '.' line end. `wordEnds` per word: '1' a real word ends after it.
struct Line: Hashable, Sendable {
    let words: [String]
    let sentenceEnd: Bool
    let seps: Scalars
    let wordEnds: Scalars

    func sepAfter(_ i: Int) -> String {
        switch i < seps.count ? seps[i] : " " {
        case "-": "-"
        case "_": "- "
        case "|": " -- "
        case ".": ""
        default: i == words.count - 1 ? "" : " "
        }
    }
    var text: String {
        var s = ""
        for (i, w) in words.enumerated() { s += w; s += sepAfter(i) }
        return s
    }
}

final class Verse: Identifiable, Hashable, @unchecked Sendable {
    let sandhiId: Int
    /// 0 = the refrain (ಪಲ್ಲವಿ).
    let number: Int
    let lines: [Line]
    let words: [WordRef]
    let key: String
    var id: String { key }
    init(sandhiId: Int, number: Int, lines: [Line]) {
        self.sandhiId = sandhiId; self.number = number; self.lines = lines
        key = "\(sandhiId):\(number)"
        var g = 0; var out: [WordRef] = []
        for (li, l) in lines.enumerated() { for (wi, w) in l.words.enumerated() { out.append(WordRef(line: li, index: wi, global: g, text: w)); g += 1 } }
        words = out
    }
    var isRefrain: Bool { number == 0 }
    var firstLine: String { lines.first?.text ?? "" }
    static func == (a: Verse, b: Verse) -> Bool { a.key == b.key }
    func hash(into h: inout Hasher) { h.combine(key) }
}

final class Sandhi: Identifiable, Hashable, @unchecked Sendable {
    let id: Int; let title: String; let alt: String; let verses: [Verse]
    init(id: Int, title: String, alt: String, verses: [Verse]) { self.id = id; self.title = title; self.alt = alt; self.verses = verses }
    var padyaCount: Int { verses.filter { !$0.isRefrain }.count }
    static func == (a: Sandhi, b: Sandhi) -> Bool { a.id == b.id }
    func hash(into h: inout Hasher) { h.combine(id) }
}

enum Paths {
    static let support: URL = {
        let u = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        return u
    }()
    /// Re-downloadable content must stay out of iCloud backups (App Review guideline 2.23).
    static func dir(_ name: String) -> URL {
        var u = support.appendingPathComponent(name, isDirectory: true)
        try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
        var v = URLResourceValues(); v.isExcludedFromBackup = true
        try? u.setResourceValues(v)
        return u
    }
    static let bundled = Bundle.main.url(forResource: "Bundled", withExtension: nil)!
    static func asset(_ rel: String) -> URL { bundled.appendingPathComponent(rel) }
}

enum TextError: Error { case empty }

enum Library {
    static var localText: URL { Paths.dir("text").appendingPathComponent("hks.json") }

    static func load() -> [Sandhi] {
        if let d = try? Data(contentsOf: localText), let l = try? parse(d) { return l }
        return (try? parse(Data(contentsOf: Paths.asset("hks.json")))) ?? []
    }

    /// Parses (and so validates) the text JSON.
    static func parse(_ data: Data) throws -> [Sandhi] {
        guard let arr = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { throw TextError.empty }
        let list: [Sandhi] = arr.compactMap { o in
            guard let id = o["id"] as? Int else { return nil }
            var verses: [Verse] = []
            if let intro = o["intro"] as? [[String: Any]], !intro.isEmpty { verses.append(Verse(sandhiId: id, number: 0, lines: lines(intro))) }
            for v in (o["verses"] as? [[String: Any]]) ?? [] {
                if let n = v["n"] as? Int { verses.append(Verse(sandhiId: id, number: n, lines: lines((v["lines"] as? [[String: Any]]) ?? []))) }
            }
            return Sandhi(id: id, title: o["title"] as? String ?? "", alt: o["alt"] as? String ?? "", verses: verses)
        }
        guard !list.isEmpty, list.allSatisfy({ !$0.verses.isEmpty }) else { throw TextError.empty }
        return list
    }

    private static func lines(_ a: [[String: Any]]) -> [Line] {
        a.map { l in
            let words = (l["t"] as? String ?? "").split(whereSeparator: { $0 == " " || $0 == "\t" || $0 == "\n" }).map(String.init)
            let s = (l["s"] as? String ?? "").scalars, w = (l["w"] as? String ?? "").scalars
            return Line(words: words, sentenceEnd: l["e"] as? Bool ?? false,
                        seps: s.count == words.count ? s : Scalars(repeating: " ", count: words.count),
                        wordEnds: w.count == words.count ? w : Scalars(repeating: "1", count: words.count))
        }
    }
}

enum Script: String, CaseIterable, Sendable { case latin = "LATIN", tamil = "TAMIL", devanagari = "DEVANAGARI", telugu = "TELUGU", kannada = "KANNADA" }

private func sc(_ s: String) -> Unicode.Scalar { s.unicodeScalars.first! }
private func table(_ pairs: [(String, String)]) -> [Unicode.Scalar: String] {
    Dictionary(uniqueKeysWithValues: pairs.map { (sc($0.0), $0.1) })
}

/// Kannada → Tamil script, in the style of Tamil-printed stotra books: ಖ=க², ಗ=க³, ಘ=க⁴ … and Grantha letters.
enum Translit {
    private static let vowels = table([("ಅ","அ"),("ಆ","ஆ"),("ಇ","இ"),("ಈ","ஈ"),("ಉ","உ"),("ಊ","ஊ"),("ಋ","ரு"),("ೠ","ரூ"),("ಎ","எ"),("ಏ","ஏ"),("ಐ","ஐ"),("ಒ","ஒ"),("ಓ","ஓ"),("ಔ","ஔ")])
    private static let signs = table([("ಾ","ா"),("ಿ","ி"),("ೀ","ீ"),("ು","ு"),("ೂ","ூ"),("ೆ","ெ"),("ೇ","ே"),("ೈ","ை"),("ೊ","ொ"),("ೋ","ோ"),("ೌ","ௌ"),("್","்")])
    private static let vocalicR = table([("ೃ","ரு"),("ೄ","ரூ")])
    private struct C { let ta: String; let sup: Int; let nasal: String }
    private static let cons: [Unicode.Scalar: C] = {
        var d: [Unicode.Scalar: C] = [:]
        func g(_ chars: String, _ ta: String, _ nasal: String, _ sups: [Int]) { for (i, ch) in chars.unicodeScalars.enumerated() { d[ch] = C(ta: ta, sup: sups[i], nasal: nasal) } }
        func p(_ c: String, _ ta: String, _ nasal: String) { d[sc(c)] = C(ta: ta, sup: 1, nasal: nasal) }
        g("ಕಖಗಘ", "க", "ங", [1,2,3,4]); p("ಙ", "ங", "ங")
        g("ಚಛ", "ச", "ஞ", [1,2]); g("ಜಝ", "ஜ", "ஞ", [1,2]); p("ಞ", "ஞ", "ஞ")
        g("ಟಠಡಢ", "ட", "ண", [1,2,3,4]); p("ಣ", "ண", "ண")
        g("ತಥದಧ", "த", "ந", [1,2,3,4]); p("ನ", "ந", "ந")
        g("ಪಫಬಭ", "ப", "ம", [1,2,3,4]); p("ಮ", "ம", "ம")
        for (k, ta) in [("ಯ","ய"),("ರ","ர"),("ಱ","ற"),("ಲ","ல"),("ವ","வ"),("ಶ","ஶ"),("ಷ","ஷ"),("ಸ","ஸ"),("ಹ","ஹ"),("ಳ","ள"),("ೞ","ழ")] { p(k, ta, "ம") }
        return d
    }()
    private static let supChars = ["", "", "²", "³", "⁴"]
    private static let NUKTA = sc("಼"), ZWJ: Unicode.Scalar = "\u{200D}", ZWNJ: Unicode.Scalar = "\u{200C}"
    private static let zero = sc("೦").value, nine = sc("೯").value

    static func toTamil(_ text: String, superscripts: Bool = true) -> String {
        let kn = text.scalars
        var sb = ""; sb.reserveCapacity(kn.count + 8)
        var i = 0; let n = kn.count
        func skip(_ k: Int) -> Int { var j = k; while j < n, kn[j] == NUKTA || kn[j] == ZWJ || kn[j] == ZWNJ { j += 1 }; return j }
        while i < n {
            let ch = kn[i]
            if let c = cons[ch] {
                let sup = superscripts ? supChars[c.sup] : ""
                sb += c.ta
                let j = skip(i + 1)
                if j < n, let vr = vocalicR[kn[j]] { sb += "்" + sup + vr; i = j + 1 }
                else if j < n, let s = signs[kn[j]] { sb += s + sup; i = j + 1 }
                else { sb += sup; i = j }
            } else if let v = vowels[ch] { sb += v; i += 1 }
            else if ch == sc("ಂ") {
                let j = skip(i + 1)
                sb += (j < n ? cons[kn[j]]?.nasal : nil) ?? "ம"; sb += "்"; i += 1
            }
            else if ch == sc("ಃ") { sb += "ஃ"; i += 1 }
            else if ch.value >= zero, ch.value <= nine { sb += String(ch.value - zero); i += 1 }
            else if ch == NUKTA || ch == ZWJ || ch == ZWNJ { i += 1 }
            else if ch == sc("ಽ") { sb += "'"; i += 1 }
            else { sb.unicodeScalars.append(ch); i += 1 }
        }
        return sb
    }
}

enum Scripts {
    static func render(_ kn: String, _ script: Script, _ tamilSuperscripts: Bool = true) -> String {
        switch script {
        case .kannada: kn
        case .tamil: Translit.toTamil(kn, superscripts: tamilSuperscripts)
        case .latin: toLatin(kn)
        case .devanagari: shift(kn, -0x380, devaFix)
        case .telugu: shift(kn, -0x80, [:])
        }
    }

    private static let longEnding: Set<String> = ["ಅಮ್ಮ", "ಅಪ್ಪ"]

    /// A whole everyday word (Say it) spelled the way a reader of that script expects. Verses keep `render`.
    static func renderWord(_ kn: String, _ script: Script, _ tamilSuperscripts: Bool = true) -> String {
        let text = render(kn, script, tamilSuperscripts)
        switch script {
        case .tamil: return tamilNa(text) + (longEnding.contains(kn) ? "ா" : "")
        case .devanagari: return text + (longEnding.contains(kn) ? "ा" : "")
        default: return text
        }
    }

    /// Tamil writes ந only at the start of a word or before த; everywhere else the same sound is ன.
    private static func tamilNa(_ word: String) -> String {
        let w = word.scalars
        return String(scalars: w.enumerated().map { i, ch in
            if ch == "ந", i > 0, !(i + 2 < w.count && w[i + 1] == "்" && w[i + 2] == "த") { return "ன" }
            return ch
        })
    }

    private static let devaFix = table([("ೞ","ळ"),("ಱ","ऱ"),("ೱ",""),("ೲ","")])

    private static func shift(_ kn: String, _ offset: Int, _ fix: [Unicode.Scalar: String]) -> String {
        var sb = String.UnicodeScalarView()
        for ch in kn.unicodeScalars {
            if let f = fix[ch] { sb.append(contentsOf: f.unicodeScalars) }
            else if ch == sc("಼") { continue }
            else if ch.value >= 0x0C80, ch.value <= 0x0CFF, let m = Unicode.Scalar(UInt32(Int(ch.value) + offset)) { sb.append(m) }
            else { sb.append(ch) }
        }
        return String(sb)
    }

    private static let lv = table([("ಅ","a"),("ಆ","ā"),("ಇ","i"),("ಈ","ī"),("ಉ","u"),("ಊ","ū"),("ಋ","ṛ"),("ೠ","ṝ"),("ಎ","e"),("ಏ","ē"),("ಐ","ai"),("ಒ","o"),("ಓ","ō"),("ಔ","au")])
    private static let ls = table([("ಾ","ā"),("ಿ","i"),("ೀ","ī"),("ು","u"),("ೂ","ū"),("ೃ","ṛ"),("ೄ","ṝ"),("ೆ","e"),("ೇ","ē"),("ೈ","ai"),("ೊ","o"),("ೋ","ō"),("ೌ","au")])
    private static let lc = table([("ಕ","k"),("ಖ","kh"),("ಗ","g"),("ಘ","gh"),("ಙ","ṅ"),("ಚ","c"),("ಛ","ch"),("ಜ","j"),("ಝ","jh"),("ಞ","ñ"),("ಟ","ṭ"),("ಠ","ṭh"),("ಡ","ḍ"),("ಢ","ḍh"),("ಣ","ṇ"),("ತ","t"),("ಥ","th"),("ದ","d"),("ಧ","dh"),("ನ","n"),("ಪ","p"),("ಫ","ph"),("ಬ","b"),("ಭ","bh"),("ಮ","m"),("ಯ","y"),("ರ","r"),("ಱ","ṟ"),("ಲ","l"),("ವ","v"),("ಶ","ś"),("ಷ","ṣ"),("ಸ","s"),("ಹ","h"),("ಳ","ḷ"),("ೞ","ḻ")])

    /// ISO 15919-style romanization: ಹರಿಕಥಾಮೃತಸಾರ → harikathāmṛtasāra
    static func toLatin(_ text: String) -> String {
        let kn = text.scalars
        var sb = ""; var i = 0
        let nukta = sc("಼"), virama = sc("್"), zero = sc("೦").value, nine = sc("೯").value
        while i < kn.count {
            let ch = kn[i]
            if let c = lc[ch] {
                sb += c
                var j = i + 1
                if j < kn.count, kn[j] == nukta { j += 1 }
                let nx: Unicode.Scalar? = j < kn.count ? kn[j] : nil
                if nx == virama { i = j + 1 }
                else if let nx, let s = ls[nx] { sb += s; i = j + 1 }
                else { sb += "a"; i = j }
                continue
            }
            if let v = lv[ch] { sb += v }
            else if ch == sc("ಂ") { sb += "ṁ" }
            else if ch == sc("ಃ") { sb += "ḥ" }
            else if ch.value >= zero, ch.value <= nine { sb += String(ch.value - zero) }
            else if ch == "\u{200C}" || ch == "\u{200D}" || ch == nukta { }
            else { sb.unicodeScalars.append(ch) }
            i += 1
        }
        return sb
    }
}
