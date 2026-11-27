import Foundation

/// المريض صاحب الجوال. المعرّف يُنشأ محليًا ولا يتغير بعد المزامنة.
struct Patient: Equatable {
    var id: String
    var authUserId: String?
    var fullName: String
    var phone: String
    var email: String?
    var createdAt: String
    var updatedAt: String
    /// "male" أو "female"، وnil حتى يختار. تُكتب به رسائل العائلة (يحتاج / تحتاج)
    var gender: String?
}

enum Gender {
    static let male = "male"
    static let female = "female"

    static func of(_ value: String?) -> String? { value == male || value == female ? value : nil }

    /// الصيغة المذكرة أو المؤنثة؛ المذكرة إن لم يُختر
    static func pick(_ gender: String?, _ male: String, _ female: String) -> String { gender == Gender.female ? female : male }
}

enum RepeatType {
    static let daily = "daily"
    static let weekly = "weekly"
    static let monthly = "monthly"
}

enum DoseStatus {
    static let pending = "pending"
    static let taken = "taken"
    static let missed = "missed"
    static let skipped = "skipped"
}

struct Medication: Identifiable, Equatable {
    var id: String
    var patientId: String
    var name: String
    var doseAmount: Double
    var doseUnit: String
    var repeatType: String
    /// أسبوعي: 1 الأحد … 7 السبت. شهري: يوم الشهر 1 … 31.
    var repeatDays: [Int]
    var frequency: Int
    /// HH:mm بنظام 24 ساعة
    var times: [String]
    var stockCount: Int?
    var stockAlert: Int
    var isActive: Bool
    var notes: String?
    var createdAt: String
    var updatedAt: String
    var deletedAt: String?

    var lowStock: Bool {
        guard let stock = stockCount else { return false }
        return stock <= stockAlert
    }
}

/// ما يدخله المستخدم في معالج الإضافة
struct MedicationDraft {
    var name: String
    var doseAmount: Double
    var doseUnit: String
    var repeatType: String
    var repeatDays: [Int]
    var frequency: Int
    var times: [String]
    var stockCount: Int?
}

struct DoseLog: Identifiable, Equatable {
    var id: String
    var medicationId: String
    var patientId: String
    var scheduledAt: String
    var takenAt: String?
    var status: String
    var createdAt: String
    var updatedAt: String
    var medicationName: String = ""
    var doseAmount: Double = 1
    var doseUnit: String = ""
}
