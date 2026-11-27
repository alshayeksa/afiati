import SwiftUI

/// لغة الواجهة: عربية (الافتراضية) أو إنجليزية. يختارها المستخدم من زر «ع/E» أو من الإعدادات وتُحفظ.
/// تُقرأ قبل أي شاشة أو إشعار، فتعمل أيضًا في الإشعارات والتطبيق مغلق.
enum Lang {
    private static let key = "language"

    /// القيمة الحالية. يغيّرها AppModel.setLanguage فتُعاد بناء الواجهة كلها.
    static private(set) var english: Bool = UserDefaults.standard.string(forKey: key) == "en"

    static var code: String { english ? "en" : "ar" }
    static var direction: LayoutDirection { english ? .leftToRight : .rightToLeft }
    /// للتواريخ: الشهور وأيام الأسبوع بلغة الواجهة، بأرقام لاتينية واضحة
    static var locale: Locale { english ? Locale(identifier: "en_US") : Locale(identifier: "ar_SA@numbers=latn") }

    static func set(english value: Bool) {
        english = value
        UserDefaults.standard.set(code, forKey: key)
    }
}

/// النص بلغة الواجهة الحالية
func tr(_ ar: String, _ en: String) -> String { Lang.english ? en : ar }
