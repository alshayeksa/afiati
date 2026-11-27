import Foundation

/// قياس نبض محفوظ على الجوال فقط (لا يُرفع للخادم)
struct HeartRateReading: Identifiable, Equatable {
    let id: String
    let patientId: String
    let bpm: Int
    let measuredAt: String
    let quality: Double
}

enum HeartRateRepository {
    @discardableResult
    static func save(_ patientId: String, bpm: Int, quality: Double) throws -> HeartRateReading {
        let reading = HeartRateReading(id: Ids.random(), patientId: patientId, bpm: bpm, measuredAt: Iso.now(), quality: quality)
        try Database.shared.run("INSERT INTO heart_rate_local (id, patient_id, bpm, measured_at, quality) VALUES (?, ?, ?, ?, ?)",
                                [reading.id, patientId, bpm, reading.measuredAt, quality])
        return reading
    }

    static func recent(_ patientId: String, limit: Int = 5) -> [HeartRateReading] {
        let rows = (try? Database.shared.query(
            "SELECT id, patient_id, bpm, measured_at, quality FROM heart_rate_local WHERE patient_id = ? ORDER BY measured_at DESC LIMIT ?",
            [patientId, limit]
        )) ?? []
        return rows.map { HeartRateReading(id: $0.str("id"), patientId: $0.str("patient_id"), bpm: $0.int("bpm"), measuredAt: $0.str("measured_at"), quality: $0.double("quality")) }
    }
}

/// العلامات الحيوية. النبض يقيسه الجهاز بالكاميرا، والباقي إدخال يدوي.
enum VitalType: String, CaseIterable, Identifiable {
    case pulse, pressure, sugar, temperature

    var id: String { rawValue }

    var title: String {
        switch self {
        case .pulse: return tr("النبض", "Heart rate")
        case .pressure: return tr("ضغط الدم", "Blood pressure")
        case .sugar: return tr("سكر الدم", "Blood sugar")
        case .temperature: return tr("درجة الحرارة", "Temperature")
        }
    }

    var unit: String {
        switch self {
        case .pulse: return tr("نبضة/دقيقة", "bpm")
        case .pressure: return tr("ملم زئبق", "mmHg")
        case .sugar: return tr("ملغ/دل", "mg/dL")
        case .temperature: return tr("°م", "°C")
        }
    }
}

/// متى قيس السكر: يغيّر المدى الطبيعي
enum SugarContext: String, CaseIterable, Identifiable {
    case fasting
    case afterMeal = "after_meal"
    case random

    var id: String { rawValue }

    var title: String {
        switch self {
        case .fasting: return tr("صائم", "Fasting")
        case .afterMeal: return tr("بعد الأكل", "After meal")
        case .random: return tr("أي وقت", "Any time")
        }
    }

    var short: String { title }

    var hint: String {
        switch self {
        case .fasting: return tr("قبل الأكل، بعد 8 ساعات بلا طعام", "Before eating, after 8 hours without food")
        case .afterMeal: return tr("بعد ساعتين من بداية الأكل", "Two hours after you start eating")
        case .random: return tr("في أي وقت من اليوم", "At any time of day")
        }
    }

    static func of(_ key: String?) -> SugarContext { SugarContext(rawValue: key ?? "") ?? .random }
}

/// حالة القراءة بلغة بسيطة. للمتابعة فقط، وليست تشخيصًا.
enum VitalStatus {
    case veryLow, low, normal, elevated, high, veryHigh

    var title: String {
        switch self {
        case .veryLow: return tr("منخفض جدًا", "Very low")
        case .low: return tr("منخفض", "Low")
        case .normal: return tr("طبيعي", "Normal")
        case .elevated: return tr("مرتفع قليلًا", "Slightly high")
        case .high: return tr("مرتفع", "High")
        case .veryHigh: return tr("مرتفع جدًا", "Very high")
        }
    }

    /// قراءة تستحق التنبيه بالاتصال بالطبيب أو الإسعاف إن صاحبتها أعراض
    var urgent: Bool { self == .veryLow || self == .veryHigh }
}

/// قراءة يدوية: الضغط قيمتان (انقباضي/انبساطي)، والسكر معه وقت القياس
struct VitalReading: Identifiable, Equatable {
    let id: String
    let patientId: String
    let type: VitalType
    let value: Double
    let value2: Double?
    let context: String?
    let measuredAt: String
}

/**
 المدى الطبيعي للبالغين بحسب الإرشادات الشائعة:
 الضغط (جمعية القلب الأمريكية)، السكر (جمعية السكري الأمريكية)، الحرارة الفموية، النبض في الراحة.
 */
enum VitalRanges {
    static func pressure(_ systolic: Double, _ diastolic: Double) -> VitalStatus {
        if systolic >= 180 || diastolic >= 120 { return .veryHigh }
        if systolic >= 140 || diastolic >= 90 { return .high }
        if systolic < 90 || diastolic < 60 { return .low }
        if systolic >= 120 || diastolic >= 80 { return .elevated }
        return .normal
    }

    static func sugar(_ value: Double, _ context: SugarContext) -> VitalStatus {
        if value < 54 { return .veryLow }
        if value < 70 { return .low }
        if value >= 300 { return .veryHigh }
        if context == .fasting {
            if value < 100 { return .normal }
            if value < 126 { return .elevated }
            return .high
        }
        if value < 140 { return .normal }
        if value < 200 { return .elevated }
        return .high
    }

    static func temperature(_ celsius: Double) -> VitalStatus {
        if celsius < 35.0 { return .low }
        if celsius < 37.5 { return .normal }
        if celsius < 38.0 { return .elevated }
        if celsius < 39.5 { return .high }
        return .veryHigh
    }

    static func pulse(_ bpm: Int) -> VitalStatus {
        if bpm < 50 { return .low }
        if bpm <= 100 { return .normal }
        if bpm <= 120 { return .elevated }
        return .high
    }

    static func of(_ reading: VitalReading) -> VitalStatus {
        switch reading.type {
        case .pressure: return pressure(reading.value, reading.value2 ?? 0)
        case .sugar: return sugar(reading.value, SugarContext.of(reading.context))
        case .temperature: return temperature(reading.value)
        case .pulse: return pulse(Int(reading.value))
        }
    }

    /// يثبّت اتجاه المدى من اليسار إلى اليمين داخل جملة عربية، فلا يظهر «100-60»
    static func ltr(_ text: String) -> String { "\u{2066}\(text)\u{2069}" }

    /// سطر المدى الطبيعي تحت اسم العلامة
    static func normalText(_ type: VitalType, _ context: SugarContext = .fasting) -> String {
        switch type {
        case .pulse: return tr("الطبيعي في الراحة: \(ltr("60–100"))", "Normal at rest: \(ltr("60–100"))")
        case .pressure: return tr("الطبيعي: أقل من \(ltr("120/80"))", "Normal: below \(ltr("120/80"))")
        case .sugar:
            return context == .fasting
                ? tr("الطبيعي صائمًا: \(ltr("70–99"))", "Normal fasting: \(ltr("70–99"))")
                : tr("الطبيعي بعد الأكل: أقل من 140", "Normal after meals: below 140")
        case .temperature: return tr("الطبيعي: \(ltr("36.1–37.4"))", "Normal: \(ltr("36.1–37.4"))")
        }
    }

    /// نصيحة هادئة لقراءة خارج الطبيعي
    static func advice(_ status: VitalStatus) -> String? {
        if status.urgent {
            return tr("قراءة تحتاج انتباهًا. إن شعرت بتعب أو دوخة أو ألم فاتصل بطبيبك أو بالإسعاف 997.",
                      "This reading needs attention. If you feel tired, dizzy or in pain, call your doctor or an ambulance (997).")
        }
        if status == .normal { return nil }
        return tr("أعد القياس بعد راحة خمس دقائق، وإن تكررت فاستشر طبيبك.", "Measure again after resting five minutes; if it repeats, consult your doctor.")
    }

    /// الحدود المقبولة للإدخال، لمنع الأخطاء الكتابية
    static let systolicRange: ClosedRange<Double> = 60...260
    static let diastolicRange: ClosedRange<Double> = 30...160
    static let sugarRange: ClosedRange<Double> = 20...600
    static let temperatureRange: ClosedRange<Double> = 33...43

    static func display(_ reading: VitalReading) -> String {
        switch reading.type {
        case .pressure: return ltr("\(Int(reading.value))/\(Int(reading.value2 ?? 0))")
        case .temperature: return String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), reading.value)
        default: return "\(Int(reading.value))"
        }
    }
}

/// القراءات اليدوية محفوظة على الجوال فقط
enum VitalsRepository {
    @discardableResult
    static func save(_ patientId: String, _ type: VitalType, value: Double, value2: Double? = nil, context: String? = nil) throws -> VitalReading {
        precondition(type != .pulse, "النبض يُحفظ من القياس بالكاميرا")
        let reading = VitalReading(id: Ids.random(), patientId: patientId, type: type, value: value, value2: value2, context: context, measuredAt: Iso.now())
        try Database.shared.run(
            "INSERT INTO vital_readings_local (id, patient_id, type, value, value2, context, measured_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
            [reading.id, patientId, type.rawValue, value, value2, context, reading.measuredAt]
        )
        return reading
    }

    static func recent(_ patientId: String, _ type: VitalType, limit: Int = 5) -> [VitalReading] {
        let rows = (try? Database.shared.query(
            "SELECT id, patient_id, type, value, value2, context, measured_at FROM vital_readings_local WHERE patient_id = ? AND type = ? ORDER BY measured_at DESC LIMIT ?",
            [patientId, type.rawValue, limit]
        )) ?? []
        return rows.compactMap { row in
            guard let kind = VitalType(rawValue: row.str("type")) else { return nil }
            return VitalReading(id: row.str("id"), patientId: row.str("patient_id"), type: kind, value: row.double("value"),
                                value2: row.doubleOrNil("value2"), context: row.strOrNil("context"), measuredAt: row.str("measured_at"))
        }
    }

    static func delete(_ id: String) {
        _ = try? Database.shared.run("DELETE FROM vital_readings_local WHERE id = ?", [id])
    }
}
