import Foundation

typealias JSON = [String: Any]

/// تحويل الصفوف إلى JSON وبالعكس بنفس أسماء حقول الخادم
enum Json {
    static func medication(_ m: Medication) -> JSON {
        [
            "id": m.id,
            "patient_id": m.patientId,
            "name": m.name,
            "dose_amount": m.doseAmount,
            "dose_unit": m.doseUnit,
            "repeat_type": m.repeatType,
            "repeat_days": m.repeatDays,
            "frequency": m.frequency,
            "times": m.times,
            "stock_count": m.stockCount.map { $0 as Any } ?? NSNull(),
            "stock_alert": m.stockAlert,
            "is_active": m.isActive,
            "notes": m.notes.map { $0 as Any } ?? NSNull(),
            "created_at": m.createdAt,
            "updated_at": m.updatedAt,
            "deleted_at": m.deletedAt.map { $0 as Any } ?? NSNull(),
        ]
    }

    static func log(_ l: DoseLog) -> JSON {
        [
            "id": l.id,
            "medication_id": l.medicationId,
            "patient_id": l.patientId,
            "scheduled_at": l.scheduledAt,
            "taken_at": l.takenAt.map { $0 as Any } ?? NSNull(),
            "status": l.status,
            "created_at": l.createdAt,
            "updated_at": l.updatedAt,
        ]
    }

    static func parseMedication(_ o: JSON) -> Medication? {
        guard let id = string(o["id"]), let patientId = string(o["patient_id"]), let name = string(o["name"]) else { return nil }
        return Medication(
            id: id, patientId: patientId, name: name,
            doseAmount: double(o["dose_amount"]) ?? 1,
            doseUnit: string(o["dose_unit"]) ?? "",
            repeatType: string(o["repeat_type"]) ?? RepeatType.daily,
            repeatDays: ints(o["repeat_days"]),
            frequency: int(o["frequency"]) ?? 1,
            times: strings(o["times"]),
            stockCount: int(o["stock_count"]),
            stockAlert: int(o["stock_alert"]) ?? 7,
            isActive: bool(o["is_active"]) ?? true,
            notes: string(o["notes"]),
            createdAt: string(o["created_at"]) ?? Iso.now(),
            updatedAt: string(o["updated_at"]) ?? Iso.now(),
            deletedAt: string(o["deleted_at"])
        )
    }

    static func parseLog(_ o: JSON) -> DoseLog? {
        guard let id = string(o["id"]), let medicationId = string(o["medication_id"]), let patientId = string(o["patient_id"]),
              let scheduledAt = string(o["scheduled_at"]) else { return nil }
        return DoseLog(
            id: id, medicationId: medicationId, patientId: patientId, scheduledAt: scheduledAt,
            takenAt: string(o["taken_at"]), status: string(o["status"]) ?? DoseStatus.pending,
            createdAt: string(o["created_at"]) ?? Iso.now(), updatedAt: string(o["updated_at"]) ?? Iso.now()
        )
    }

    // قراءة القيم بأمان: NSNull وأي نوع غير متوقع تصير nil

    static func string(_ value: Any?) -> String? {
        if value is NSNull { return nil }
        return value as? String
    }

    static func int(_ value: Any?) -> Int? {
        if value is NSNull { return nil }
        if let number = value as? NSNumber { return number.intValue }
        if let text = value as? String { return Int(text) }
        return nil
    }

    static func double(_ value: Any?) -> Double? {
        if value is NSNull { return nil }
        if let number = value as? NSNumber { return number.doubleValue }
        if let text = value as? String { return Double(text) }
        return nil
    }

    static func bool(_ value: Any?) -> Bool? {
        if value is NSNull { return nil }
        if let number = value as? NSNumber { return number.boolValue }
        return nil
    }

    static func ints(_ value: Any?) -> [Int] { (value as? [Any])?.compactMap { int($0) } ?? [] }
    static func strings(_ value: Any?) -> [String] { (value as? [Any])?.compactMap { string($0) } ?? [] }
    static func objects(_ value: Any?) -> [JSON] { (value as? [Any])?.compactMap { $0 as? JSON } ?? [] }

    static func encode(_ object: Any) -> String {
        guard JSONSerialization.isValidJSONObject(object), let data = try? JSONSerialization.data(withJSONObject: object) else { return "{}" }
        return String(data: data, encoding: .utf8) ?? "{}"
    }

    static func decode(_ text: String) -> Any? {
        guard let data = text.data(using: .utf8) else { return nil }
        return try? JSONSerialization.jsonObject(with: data)
    }
}
