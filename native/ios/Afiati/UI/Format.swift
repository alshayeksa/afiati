import Foundation

/// نصوص الأوقات والجرعات والتواريخ بلغة الواجهة، بأرقام واضحة (لاتينية)
enum Format {
    private static let arabicWeekdays = [1: "الأحد", 2: "الاثنين", 3: "الثلاثاء", 4: "الأربعاء", 5: "الخميس", 6: "الجمعة", 7: "السبت"]
    private static let englishWeekdays = [1: "Sunday", 2: "Monday", 3: "Tuesday", 4: "Wednesday", 5: "Thursday", 6: "Friday", 7: "Saturday"]
    static var weekdayNames: [Int: String] { Lang.english ? englishWeekdays : arabicWeekdays }
    /// ترتيب الأسبوع السعودي: يبدأ بالسبت
    static let weekOrder = [7, 1, 2, 3, 4, 5, 6]

    static func time(hour: Int, minute: Int) -> String {
        let h = hour % 12
        let period = hour < 12 ? tr("ص", "AM") : tr("م", "PM")
        return "\(h == 0 ? 12 : h):\(String(format: "%02d", minute)) \(period)"
    }

    static func time(_ value: String) -> String {
        guard let parsed = Schedule.parseTime(value) else { return value }
        return time(hour: parsed.hour, minute: parsed.minute)
    }

    static func time(_ date: Date) -> String {
        let parts = Day.calendar.dateComponents([.hour, .minute], from: date)
        return time(hour: parts.hour ?? 0, minute: parts.minute ?? 0)
    }

    static func isoTime(_ iso: String) -> String { Iso.parse(iso).map { time($0) } ?? "" }

    static func number(_ value: Double) -> String {
        value == value.rounded(.down) ? String(Int(value)) : String(value)
    }

    /// اسم الوحدة للعرض. القيمة المحفوظة تبقى بالعربية («حبة»...) كما يتوقعها الخادم.
    static func unitName(_ unit: String) -> String {
        switch unit {
        case "حبة": return tr("حبة", "tablet")
        case "مل": return tr("مل", "ml")
        case "قطرة": return tr("قطرة", "drop")
        case "بخة": return tr("بخة", "puff")
        case "other": return ""
        default: return unit
        }
    }

    /// «حبة واحدة»، «حبتان»، «3 حبات»، «5 مل» / «1 tablet»، «2 tablets»، «5 ml»
    static func dose(_ amount: Double, _ unit: String) -> String {
        Lang.english ? englishDose(amount, unit) : arabicDose(amount, unit)
    }

    private static func englishDose(_ amount: Double, _ unit: String) -> String {
        if amount == 0.5 && unit == "حبة" { return "half a tablet" }
        let name = unitName(unit)
        if name.isEmpty { return number(amount) }
        // الوحدات المعدودة تُجمع، والمل لا يُجمع
        let plural = unit != "مل" && amount != 1
        return "\(number(amount)) \(name)\(plural ? "s" : "")"
    }

    private static func arabicDose(_ amount: Double, _ unit: String) -> String {
        let whole = amount == amount.rounded(.down)
        let n = Int(amount)
        if amount == 0.5 && unit == "حبة" { return "نصف حبة" }
        func counted(_ one: String, _ two: String, _ few: String, _ many: String) -> String {
            if n == 1 { return one }
            if n == 2 { return two }
            if (3...10).contains(n) { return "\(n) \(few)" }
            return "\(n) \(many)"
        }
        if whole && unit == "حبة" { return counted("حبة واحدة", "حبتان", "حبات", "حبة") }
        if whole && unit == "قطرة" { return counted("قطرة واحدة", "قطرتان", "قطرات", "قطرة") }
        if whole && unit == "بخة" { return counted("بخة واحدة", "بختان", "بخات", "بخة") }
        return "\(number(amount)) \(unit == "other" ? "" : unit)".trimmingCharacters(in: .whitespaces)
    }

    static func times(_ count: Int) -> String {
        if Lang.english {
            switch count {
            case 1: return "once"
            case 2: return "twice"
            default: return "\(count) times"
            }
        }
        switch count {
        case 1: return "مرة واحدة"
        case 2: return "مرتان"
        default: return "\(count) مرات"
        }
    }

    static func repeatTitle(_ type: String) -> String {
        switch type {
        case RepeatType.weekly: return tr("أسبوعيًا", "Weekly")
        case RepeatType.monthly: return tr("شهريًا", "Monthly")
        default: return tr("يوميًا", "Daily")
        }
    }

    /// «يوميًا · 8:00 ص، 8:00 م» أو «الأحد، الخميس · 9:00 م»
    static func schedule(_ type: String, _ days: [Int], _ times: [String]) -> String {
        let comma = tr("، ", ", ")
        let timeText = Schedule.timesFor(type, times).map { time($0) }.joined(separator: comma)
        switch type {
        case RepeatType.weekly:
            return weekOrder.filter { days.contains($0) }.map { weekdayNames[$0] ?? "" }.joined(separator: comma) + " · " + timeText
        case RepeatType.monthly:
            return tr("يوم ", "Day ") + days.sorted().map(String.init).joined(separator: comma) + tr(" من كل شهر · ", " of each month · ") + timeText
        default:
            return tr("يوميًا · ", "Daily · ") + timeText
        }
    }

    private static func formatter(_ pattern: String, calendar: Calendar.Identifier) -> DateFormatter {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: calendar)
        formatter.locale = Lang.english ? Locale(identifier: "en_US") : Locale(identifier: "ar_SA@numbers=latn")
        formatter.timeZone = TimeZone.current
        formatter.dateFormat = pattern
        return formatter
    }

    /// «الخميس 27 نوفمبر» / «Thursday, November 27»
    static func gregorianDay(_ day: Day = Day.today()) -> String {
        formatter(Lang.english ? "EEEE, MMMM d" : "EEEE d MMMM", calendar: .gregorian).string(from: day.start)
    }

    /// «6 جمادى الآخرة 1448 هـ» / «Jumada II 6, 1448 AH»
    static func hijriDay(_ day: Day = Day.today()) -> String {
        if Lang.english { return formatter("MMMM d, y", calendar: .islamicUmmAlQura).string(from: day.start) + " AH" }
        return formatter("d MMMM y", calendar: .islamicUmmAlQura).string(from: day.start) + " هـ"
    }

    static func monthTitle(_ day: Day) -> String {
        formatter("MMMM y", calendar: .gregorian).string(from: day.start)
    }

    static func shortWeekday(_ day: Day) -> String {
        let name = weekdayNames[day.weekday] ?? ""
        if Lang.english { return String(name.prefix(3)) }
        return name.hasPrefix("ال") ? String(name.dropFirst(2)) : name
    }

    /// «قبل 5 دقائق» / «5 minutes ago»
    static func relative(_ iso: String) -> String {
        guard let date = Iso.parse(iso) else { return "" }
        let seconds = max(0, Date().timeIntervalSince(date))
        if seconds < 60 { return tr("الآن", "now") }
        let formatter = RelativeDateTimeFormatter()
        formatter.locale = Lang.english ? Locale(identifier: "en_US") : Locale(identifier: "ar_SA@numbers=latn")
        formatter.unitsStyle = .full
        var parts = DateComponents()
        if seconds < 3600 { parts.minute = -Int(seconds / 60) }
        else if seconds < 86_400 { parts.hour = -Int(seconds / 3600) }
        else if seconds < 7 * 86_400 { parts.day = -Int(seconds / 86_400) }
        else { parts.weekOfMonth = -Int(seconds / (7 * 86_400)) }
        return formatter.localizedString(from: parts)
    }

    /// «اليوم · 8:30 ص»، «أمس · …» أو التاريخ
    static func readingTime(_ iso: String) -> String {
        guard let date = Iso.parse(iso) else { return "" }
        let day = Day(date)
        let label: String
        if day == Day.today() { label = tr("اليوم", "Today") }
        else if day == Day.today().adding(days: -1) { label = tr("أمس", "Yesterday") }
        else { label = gregorianDay(day) }
        return "\(label) · \(time(date))"
    }
}
