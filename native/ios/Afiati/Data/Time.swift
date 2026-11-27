import Foundation

/// الوقت بصيغة Date#toISOString() بالضبط (ملّي ثانية وZ)، لأن الخادم يرفض غيرها ويرتّب بها نصيًا.
enum Iso {
    private static let formatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter
    }()

    private static let plain: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    static func format(_ date: Date) -> String { formatter.string(from: date) }
    static func now() -> String { format(Date()) }
    static func parse(_ value: String) -> Date? { formatter.date(from: value) ?? plain.date(from: value) }
}

/// يوم محلي (بلا وقت)، مثل LocalDate في أندرويد. بالتقويم الميلادي وبمنطقة الجوال.
struct Day: Hashable, Comparable {
    let year: Int
    let month: Int
    let day: Int

    static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone.current
        return calendar
    }

    init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    init(_ date: Date) {
        let parts = Day.calendar.dateComponents([.year, .month, .day], from: date)
        self.init(year: parts.year ?? 1970, month: parts.month ?? 1, day: parts.day ?? 1)
    }

    static func today() -> Day { Day(Date()) }

    /// بداية اليوم بمنطقة الجوال
    var start: Date { Day.calendar.date(from: DateComponents(year: year, month: month, day: day)) ?? Date() }

    func adding(days: Int) -> Day { Day(Day.calendar.date(byAdding: .day, value: days, to: start) ?? start) }

    /// ترقيم أيام الأسبوع في التطبيق: 1 الأحد … 7 السبت (مثل تقويم Apple)
    var weekday: Int { Day.calendar.component(.weekday, from: start) }

    /// عدد الأيام منذ 1970-01-01، بحساب التاريخ فقط (مثل LocalDate.toEpochDay)
    var epochDay: Int {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let date = utc.date(from: DateComponents(year: year, month: month, day: day)) ?? Date(timeIntervalSince1970: 0)
        return Int((date.timeIntervalSince1970 / 86_400).rounded(.down))
    }

    static func < (lhs: Day, rhs: Day) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }
}

/// مواعيد الدواء: أي الأيام، وأي الأوقات، والموعد القادم. منطق خالص يُختبر.
enum Schedule {
    static func isScheduled(_ repeatType: String, _ repeatDays: [Int], on day: Day) -> Bool {
        switch repeatType {
        case RepeatType.weekly: return repeatDays.contains(day.weekday)
        case RepeatType.monthly: return repeatDays.contains(day.day)
        default: return true
        }
    }

    /// اليومي له حتى 4 أوقات، والأسبوعي والشهري وقت واحد في الأيام المختارة
    static func timesFor(_ repeatType: String, _ times: [String]) -> [String] {
        repeatType == RepeatType.daily ? times : Array(times.prefix(1))
    }

    static func parseTime(_ time: String) -> (hour: Int, minute: Int)? {
        let parts = time.split(separator: ":")
        guard parts.count == 2, let hour = Int(parts[0]), let minute = Int(parts[1]),
              (0..<24).contains(hour), (0..<60).contains(minute) else { return nil }
        return (hour, minute)
    }

    static func date(_ day: Day, _ time: String) -> Date? {
        guard let parsed = parseTime(time) else { return nil }
        return Day.calendar.date(from: DateComponents(year: day.year, month: day.month, day: day.day, hour: parsed.hour, minute: parsed.minute))
    }

    /// الموعد بصيغة ISO. يوم 31 في شهر من 30 يومًا لا يُحسب (لا يظهر في isScheduled أصلًا).
    static func scheduledAt(_ day: Day, _ time: String) -> String? {
        date(day, time).map(Iso.format)
    }

    /// أول موعد لهذا الوقت بعد اللحظة المعطاة، خلال 62 يومًا (يكفي لأي يوم في الشهر)
    static func nextOccurrence(_ repeatType: String, _ repeatDays: [Int], _ time: String, after: Date) -> Date? {
        let start = Day(after)
        for offset in 0...62 {
            let day = start.adding(days: offset)
            guard isScheduled(repeatType, repeatDays, on: day), let at = date(day, time) else { continue }
            if at > after { return at }
        }
        return nil
    }

    static func dayRange(_ day: Day) -> (String, String) {
        (Iso.format(day.start), Iso.format(day.adding(days: 1).start))
    }
}

/// قواعد التعارض نفسها في الخادم وأندرويد: الأحدث يفوز، والجرعة المأخوذة لا تعود.
enum Conflict {
    static func shouldApplyRemote(_ localUpdatedAt: String?, _ remoteUpdatedAt: String?) -> Bool {
        guard let local = localUpdatedAt else { return true }
        guard let remote = remoteUpdatedAt, let remoteDate = Iso.parse(remote), let localDate = Iso.parse(local) else { return false }
        return remoteDate >= localDate
    }

    struct DoseState: Equatable {
        var status: String
        var takenAt: String?
        var updatedAt: String
    }

    static func mergeDose(_ local: DoseState?, _ remote: DoseState) -> DoseState {
        if let local = local, local.status == DoseStatus.taken { return local }
        if remote.status == DoseStatus.taken { return remote }
        return shouldApplyRemote(local?.updatedAt, remote.updatedAt) ? remote : (local ?? remote)
    }
}
