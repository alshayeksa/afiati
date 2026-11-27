import Foundation

/**
 تحية اليوم في الشاشة الرئيسية: من جدول greetings في الخادم (تُضاف له تحيات جديدة متى شئت).
 القائمة محفوظة في الجوال فتعمل بلا إنترنت، وتتحدث مرة كل 12 ساعة.
 كل يوم تحية مختلفة عشوائيًا، ولا تتكرر حتى تمر كل التحيات.
 */
enum Greetings {
    private static let listKey = "greetings.list"
    private static let englishKey = "greetings.list_en"
    private static let fetchedKey = "greetings.fetched_at"
    private static let refreshSeconds: TimeInterval = 12 * 60 * 60

    /// نسخة مرفقة بالتطبيق لأول تشغيل بلا إنترنت (نفس بداية الجدول)
    static let bundled = [
        "نهارك سعيد وعافيتك تدوم.", "دمت بصحة وعافية.", "أسعد الله يومك.", "يومك صحة وطمأنينة.", "صحتك أغلى ما تملك.",
        "عافاك الله وأدام ابتسامتك.", "كل يوم بداية جميلة.", "جعل الله أيامك عافية.", "ابتسم، فالحياة جميلة.", "صحتك تستحق اهتمامك.",
        "ربي يحفظك ويسعدك.", "يوم جميل بإذن الله.", "عافيتك تسعد من يحبك.", "كن بخير دائمًا.", "أدام الله عليك الصحة.",
        "يوم مليء بالخير والبركة.", "اعتنِ بنفسك، فأنت غالٍ.", "عسى أيامك كلها عافية.", "صحتك اليوم، سعادتك غدًا.", "حفظك الله لمن يحبك.",
        "ألبسك الله ثوب العافية.", "يومك سعادة وراحة بال.", "متعك الله بالصحة والعافية.", "العافية أجمل النعم.", "حفظك الله من كل مكروه.",
        "أدام الله عليك نعمة العافية.", "أيامك جميلة بإذن الله.", "أسعد الله قلبك.", "عافيتك تهمنا.", "طاب يومك ودامت عافيتك.",
    ]

    /// النسخة الإنجليزية، بنفس ترتيب الجدول
    static let bundledEnglish = [
        "Have a lovely day, and stay well.", "Wishing you health and wellness.", "May your day be happy.",
        "A day of health and peace of mind.", "Your health is your greatest treasure.",
        "May you stay well and keep smiling.", "Every day is a beautiful beginning.", "May all your days be healthy.",
        "Smile, life is beautiful.", "Your health deserves your care.",
        "May God keep you happy and safe.", "A beautiful day, God willing.", "Your wellness brings joy to those who love you.",
        "Stay well, always.", "May God keep you healthy.",
        "A day full of goodness and blessings.", "Take care of yourself, you are precious.", "May all your days be well.",
        "Your health today is your happiness tomorrow.", "May God protect you for those who love you.",
        "May God dress you in good health.", "A day of happiness and peace of mind.", "May God grant you health and wellness.",
        "Wellness is the finest blessing.", "May God keep you safe from all harm.",
        "May God keep you in good health.", "Your days are beautiful, God willing.", "May God fill your heart with joy.",
        "Your wellness matters to us.", "Have a good day, and stay well.",
    ]

    static func saved() -> (arabic: [String], english: [String]) {
        let defaults = UserDefaults.standard
        let arabic = (defaults.stringArray(forKey: listKey) ?? []).filter { !$0.isEmpty }
        let english = (defaults.stringArray(forKey: englishKey) ?? []).filter { !$0.isEmpty }
        return (arabic.isEmpty ? bundled : arabic, english.isEmpty ? bundledEnglish : english)
    }

    /// يجلب الجدول من الخادم إن مر وقت كافٍ. الأخطاء تُتجاهل وتبقى القائمة المحفوظة.
    static func refresh(force: Bool = false) async -> (arabic: [String], english: [String])? {
        let defaults = UserDefaults.standard
        let last = defaults.double(forKey: fetchedKey)
        if !force && Date().timeIntervalSince1970 - last < refreshSeconds { return nil }
        guard let response = try? await Api.request("/v1/greetings", auth: false) else { return nil }
        let rows = Json.objects(response["greetings"])
        let arabic = rows.compactMap { Json.string($0["text"])?.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        // التحيات التي لم تُكتب لها ترجمة لا تظهر في الواجهة الإنجليزية
        let english = rows.compactMap { Json.string($0["text_en"])?.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        guard !arabic.isEmpty else { return nil }
        defaults.set(arabic, forKey: listKey)
        if !english.isEmpty { defaults.set(english, forKey: englishKey) }
        defaults.set(Date().timeIntervalSince1970, forKey: fetchedKey)
        return (arabic, english.isEmpty ? saved().english : english)
    }

    /**
     ترتيب عشوائي ثابت لكل دورة، والدورة بطول القائمة: كل تحية تظهر مرة واحدة قبل أن تتكرر،
     ونفس اليوم يعطي نفس التحية طوال اليوم.
     */
    static func pick(_ items: [String], epochDay: Int) -> String {
        guard !items.isEmpty else { return "" }
        let size = items.count
        let cycle = Int((Double(epochDay) / Double(size)).rounded(.down))
        let position = ((epochDay % size) + size) % size
        var generator = SeededGenerator(seed: UInt64(bitPattern: Int64(cycle * 31 + size)))
        return items.shuffled(using: &generator)[position]
    }
}

/// مولّد أرقام عشوائية ثابت البذرة (SplitMix64)
struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64) { state = seed }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}
