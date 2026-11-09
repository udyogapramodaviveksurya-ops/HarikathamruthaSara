import Foundation

/// App languages. Each has its own reading script for the verses.
enum Lang: String, CaseIterable, Identifiable, Sendable {
    case en = "EN", ta = "TA", kn = "KN", hi = "HI", te = "TE", sa = "SA"
    var id: String { rawValue }
    var native: String {
        switch self {
        case .en: "English"; case .ta: "தமிழ்"; case .kn: "ಕನ್ನಡ"; case .hi: "हिन्दी"; case .te: "తెలుగు"; case .sa: "संस्कृतम्"
        }
    }
    var english: String {
        switch self {
        case .en: "English"; case .ta: "Tamil"; case .kn: "Kannada"; case .hi: "Hindi"; case .te: "Telugu"; case .sa: "Sanskrit"
        }
    }
    var script: Script {
        switch self {
        case .en: .latin; case .ta: .tamil; case .kn: .kannada; case .hi, .sa: .devanagari; case .te: .telugu
        }
    }
}

func i18nPick(_ l: Lang, _ en: String, _ ta: String, _ kn: String, _ hi: String, _ te: String, _ sa: String) -> String {
    switch l { case .en: en; case .ta: ta; case .kn: kn; case .hi: hi; case .te: te; case .sa: sa }
}

/// Short labels: the chosen language followed by English, e.g. "இயக்கு · Play". A leading emoji stays in front of both.
func i18nBoth(_ l: Lang, _ en: String, _ native: String) -> String {
    if l == .en { return en }
    let isLetter: (Unicode.Scalar) -> Bool = {
        switch $0.properties.generalCategory {
        case .uppercaseLetter, .lowercaseLetter, .titlecaseLetter, .modifierLetter, .otherLetter: true
        default: false
        }
    }
    let prefix = String(String.UnicodeScalarView(en.unicodeScalars.prefix { !isLetter($0) }))
    let bare = !prefix.isEmpty && native.hasPrefix(prefix) ? String(en.dropFirst(prefix.count)) : en
    return "\(native) · \(bare)"
}

/// A few messages only the iOS app needs (Android has no equivalent screen state).
struct IosText {
    let lang: Lang
    private func t(_ en: String, _ ta: String, _ kn: String, _ hi: String, _ te: String, _ sa: String) -> String { i18nPick(lang, en, ta, kn, hi, te, sa) }
    var signInAgainToDelete: String {
        t("For your safety, please sign out, sign in again, and then delete the account.",
          "பாதுகாப்புக்காக, வெளியேறி மீண்டும் உள்நுழைந்து, பிறகு கணக்கை நீக்குங்கள்.",
          "ಸುರಕ್ಷತೆಗಾಗಿ, ಸೈನ್ ಔಟ್ ಮಾಡಿ, ಮತ್ತೆ ಸೈನ್ ಇನ್ ಮಾಡಿ, ನಂತರ ಖಾತೆಯನ್ನು ಅಳಿಸಿ.",
          "सुरक्षा के लिए, साइन आउट करके फिर साइन इन करें, उसके बाद खाता हटाएँ।",
          "భద్రత కోసం, సైన్ అవుట్ చేసి మళ్ళీ సైన్ ఇన్ చేసి, ఆ తర్వాత ఖాతాను తొలగించండి.",
          "सुरक्षार्थं निर्गत्य पुनः प्रविश्य ततः सदस्यताम् अपनय।")
    }
    var privacyPolicy: String {
        t("Privacy policy", "தனியுரிமைக் கொள்கை", "ಗೌಪ್ಯತಾ ನೀತಿ", "निजता नीति", "గోప్యతా విధానం", "गोपनीयतानीतिः")
    }
    var openSettings: String {
        t("Open Settings", "அமைப்புகளைத் திற", "ಸೆಟ್ಟಿಂಗ್ಸ್ ತೆರೆಯಿರಿ", "सेटिंग्स खोलें", "సెట్టింగ్‌లు తెరవండి", "विन्यासान् उद्घाटय")
    }
}
