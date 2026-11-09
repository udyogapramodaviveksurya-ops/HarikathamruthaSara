import SwiftUI

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
    }
}

struct Tone { let container: Color; let on: Color; let accent: Color }

struct Tones {
    let all: [Tone]
    var marigold: Tone { all[0] }; var lotus: Tone { all[1] }; var peacock: Tone { all[2] }
    var leaf: Tone { all[3] }; var sky: Tone { all[4] }; var berry: Tone { all[5] }
    subscript(i: Int) -> Tone { all[((i % all.count) + all.count) % all.count] }
}

/// The "Festival" colours: saffron, lotus pink and peacock teal on warm cream.
struct CS {
    let primary, onPrimary, primaryContainer, onPrimaryContainer, secondary, secondaryContainer, onSecondaryContainer: Color
    let tertiary, tertiaryContainer, onTertiaryContainer, error, errorContainer, onErrorContainer: Color
    let background, onSurface, surfaceVariant, onSurfaceVariant: Color
    let lowest, low, container, high: Color
    let outline, outlineVariant: Color
    let tones: Tones

    static let light = CS(
        primary: .init(hex: 0xC43E00), onPrimary: .white, primaryContainer: .init(hex: 0xFFDBC9), onPrimaryContainer: .init(hex: 0x3A0F00),
        secondary: .init(hex: 0xAD1B63), secondaryContainer: .init(hex: 0xFFD8E6), onSecondaryContainer: .init(hex: 0x3E0020),
        tertiary: .init(hex: 0x00696E), tertiaryContainer: .init(hex: 0xA6EEF2), onTertiaryContainer: .init(hex: 0x002022),
        error: .init(hex: 0xBA1A1A), errorContainer: .init(hex: 0xFFDAD6), onErrorContainer: .init(hex: 0x410002),
        background: .init(hex: 0xFFF8F1), onSurface: .init(hex: 0x231A14), surfaceVariant: .init(hex: 0xF5DED3), onSurfaceVariant: .init(hex: 0x53433B),
        lowest: .white, low: .init(hex: 0xFFF1E7), container: .init(hex: 0xFCEADE), high: .init(hex: 0xF7E4D7),
        outline: .init(hex: 0x86736A), outlineVariant: .init(hex: 0xD8C2B7),
        tones: Tones(all: [
            Tone(container: .init(hex: 0xFFE08A), on: .init(hex: 0x3D2E00), accent: .init(hex: 0xE5A000)),
            Tone(container: .init(hex: 0xFFD6E7), on: .init(hex: 0x4A0028), accent: .init(hex: 0xE0457B)),
            Tone(container: .init(hex: 0xB8F1EC), on: .init(hex: 0x00332F), accent: .init(hex: 0x0E9A97)),
            Tone(container: .init(hex: 0xD3F2BE), on: .init(hex: 0x0E3300), accent: .init(hex: 0x3E9C48)),
            Tone(container: .init(hex: 0xD7E3FF), on: .init(hex: 0x001B3F), accent: .init(hex: 0x3D6FE0)),
            Tone(container: .init(hex: 0xEBDDFF), on: .init(hex: 0x25005A), accent: .init(hex: 0x8752D6)),
        ]))

    static let dark = CS(
        primary: .init(hex: 0xFFB693), onPrimary: .init(hex: 0x5A1B00), primaryContainer: .init(hex: 0x8A2D00), onPrimaryContainer: .init(hex: 0xFFDBC9),
        secondary: .init(hex: 0xFFB0CD), secondaryContainer: .init(hex: 0x86064A), onSecondaryContainer: .init(hex: 0xFFD8E6),
        tertiary: .init(hex: 0x8AD2D6), tertiaryContainer: .init(hex: 0x004F53), onTertiaryContainer: .init(hex: 0xA6EEF2),
        error: .init(hex: 0xFFB4AB), errorContainer: .init(hex: 0x93000A), onErrorContainer: .init(hex: 0xFFDAD6),
        background: .init(hex: 0x1A110D), onSurface: .init(hex: 0xF1DFD6), surfaceVariant: .init(hex: 0x53433B), onSurfaceVariant: .init(hex: 0xD8C2B7),
        lowest: .init(hex: 0x140C08), low: .init(hex: 0x231914), container: .init(hex: 0x271D18), high: .init(hex: 0x322722),
        outline: .init(hex: 0xA08D83), outlineVariant: .init(hex: 0x53433B),
        tones: Tones(all: [
            Tone(container: .init(hex: 0x5A4300), on: .init(hex: 0xFFE08A), accent: .init(hex: 0xFFC93C)),
            Tone(container: .init(hex: 0x6E1440), on: .init(hex: 0xFFD6E7), accent: .init(hex: 0xFF8AB3)),
            Tone(container: .init(hex: 0x00504B), on: .init(hex: 0xB8F1EC), accent: .init(hex: 0x4FD8D2)),
            Tone(container: .init(hex: 0x1E5011), on: .init(hex: 0xD3F2BE), accent: .init(hex: 0x8AD86E)),
            Tone(container: .init(hex: 0x1E3F80), on: .init(hex: 0xD7E3FF), accent: .init(hex: 0x8FB3FF)),
            Tone(container: .init(hex: 0x4F2B8A), on: .init(hex: 0xEBDDFF), accent: .init(hex: 0xC6A6FF)),
        ]))
}

enum HK {
    static let good = Color(hex: 0x2E7D32), goodBg = Color(hex: 0x2E7D32, alpha: 0.2)
    static let bad = Color(hex: 0xC62828), badBg = Color(hex: 0xC62828, alpha: 0.2)
    static let sound = Color(hex: 0xB45309), soundBg = Color(hex: 0xE8890C, alpha: 0.2)
    static let gold = Color(hex: 0xE0A100), brandGold = Color(hex: 0xE8B33A)
    static let header = [Color(hex: 0x162856), Color(hex: 0x3A2A8F), Color(hex: 0x9A2A72)]
    static let appName = "HarikathamruthaSaaraa Abhyaasaa"
    static let privacyURL = URL(string: "https://harikathamrutha-sara.web.app/privacy.html")!
}

extension EnvironmentValues {
    @Entry var cs: CS = .light
    @Entry var wide: Bool = false
    @Entry var veryWide: Bool = false
    @Entry var tour: TourController? = nil
}

extension Font {
    static func hk(_ size: CGFloat, _ w: Font.Weight = .regular) -> Font { .system(size: size, weight: w) }
}

func short(_ x: String) -> String { x.components(separatedBy: " · ")[0] }
func plain(_ x: String) -> String {
    String(short(x).drop { !$0.isLetter && !$0.isNumber }).trimmingCharacters(in: .whitespaces)
}
func pad2(_ n: Int) -> String { String(format: "%02d", n) }
func x2(_ v: Double) -> String { String(format: "%.2f×", v) }
