import Foundation

private var db: Database { Database.shared }

/// ملف المريض المحلي. لا يوجد إلا ملف واحد في الجوال.
enum PatientRepository {
    private static func from(_ r: Row) -> Patient {
        Patient(id: r.str("id"), authUserId: r.strOrNil("auth_user_id"), fullName: r.str("full_name"), phone: r.str("phone"),
                email: r.strOrNil("email"), createdAt: r.str("created_at"), updatedAt: r.str("updated_at"), gender: Gender.of(r.strOrNil("gender")))
    }

    static func get() -> Patient? {
        (try? db.query("SELECT * FROM patients_local LIMIT 1"))?.first.map(from)
    }

    private static func cleanEmail(_ email: String?) -> String? {
        guard let value = email?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(), !value.isEmpty else { return nil }
        return value
    }

    @discardableResult
    static func create(fullName: String, phone: String, email: String?, gender: String?) throws -> Patient {
        if let existing = get() { return existing }
        let now = Iso.now()
        let patient = Patient(id: Ids.random(), authUserId: nil, fullName: fullName.trimmingCharacters(in: .whitespacesAndNewlines),
                              phone: phone.trimmingCharacters(in: .whitespacesAndNewlines), email: cleanEmail(email),
                              createdAt: now, updatedAt: now, gender: Gender.of(gender))
        try db.run(
            "INSERT INTO patients_local (id, auth_user_id, full_name, phone, email, phone_verified, created_at, updated_at, gender) VALUES (?, NULL, ?, ?, ?, 0, ?, ?, ?)",
            [patient.id, patient.fullName, patient.phone, patient.email, now, now, patient.gender]
        )
        return patient
    }

    @discardableResult
    static func update(fullName: String, phone: String, email: String?, gender: String?) throws -> Patient {
        guard var patient = get() else { throw DatabaseError(message: tr("لا يوجد ملف شخصي لتحديثه.", "There's no profile to update.")) }
        patient.fullName = fullName.trimmingCharacters(in: .whitespacesAndNewlines)
        patient.phone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        patient.email = cleanEmail(email)
        patient.gender = Gender.of(gender)
        patient.updatedAt = Iso.now()
        try db.run("UPDATE patients_local SET full_name = ?, phone = ?, email = ?, gender = ?, updated_at = ? WHERE id = ?",
                   [patient.fullName, patient.phone, patient.email, patient.gender, patient.updatedAt, patient.id])
        return patient
    }

    /// يحفظ معرّف الجهاز في الخادم. لا يغيّر updated_at حتى لا يُعاد رفع الملف بلا داعٍ.
    static func setCloudIdentity(_ deviceId: String) {
        _ = try? db.run("UPDATE patients_local SET auth_user_id = ? WHERE id = (SELECT id FROM patients_local LIMIT 1)", [deviceId])
    }
}

/// قيم المزامنة: رمز الجهاز، ومؤشرات السحب، وآخر مزامنة ناجحة.
enum SyncState {
    static let deviceToken = "device_token"
    static let lastSuccess = "last_successful_sync"
    static let profileVersion = "profile_synced_version"
    static let pullMedications = "pull_medications_after"
    static let pullLogs = "pull_logs_after"
    static let pushToken = "push_token_sent"

    static func get(_ key: String) -> String? {
        (try? db.query("SELECT value FROM sync_state WHERE key = ?", [key]))?.first?.strOrNil("value")
    }

    static func set(_ key: String, _ value: String) {
        _ = try? db.run(
            "INSERT INTO sync_state (key, value, updated_at) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at",
            [key, value, Iso.now()]
        )
    }
}

struct OutboxOperation {
    let operationId: String
    let entityType: String
    let entityId: String
    let operationType: String
    let payload: String
}

/// العمليات المحلية التي لم تصل الخادم بعد. لا تُحذف إلا بعد قبول الخادم لها.
enum Outbox {
    /// عملية رفضها الخادم 5 مرات تبقى للفحص لكنها لا تُرسل، فلا تحجز ما بعدها.
    static let maxAttempts = 5

    static func enqueue(_ patientId: String, _ entityType: String, _ entityId: String, _ payload: JSON) throws {
        try db.run(
            "INSERT INTO outbox (operation_id, patient_id, entity_type, entity_id, operation_type, payload_json, created_at) VALUES (?, ?, ?, ?, 'upsert', ?, ?)",
            [Ids.random(), patientId, entityType, entityId, Json.encode(payload), Iso.now()]
        )
    }

    static func list(_ limit: Int) -> [OutboxOperation] {
        let rows = (try? db.query("SELECT * FROM outbox WHERE attempts < ? ORDER BY created_at ASC, rowid ASC LIMIT ?", [maxAttempts, limit])) ?? []
        return rows.map {
            OutboxOperation(operationId: $0.str("operation_id"), entityType: $0.str("entity_type"), entityId: $0.str("entity_id"),
                            operationType: $0.str("operation_type"), payload: $0.str("payload_json"))
        }
    }

    static func remove(_ operationId: String) {
        _ = try? db.run("DELETE FROM outbox WHERE operation_id = ?", [operationId])
    }

    static func fail(_ operationId: String, _ message: String) {
        _ = try? db.run("UPDATE outbox SET attempts = attempts + 1, last_error = ? WHERE operation_id = ?", [String(message.prefix(500)), operationId])
    }

    static func pendingCount() -> Int {
        (try? db.query("SELECT COUNT(*) AS n FROM outbox WHERE attempts < ?", [maxAttempts]))?.first?.int("n") ?? 0
    }
}

/// الأدوية والجرعات. كل تغيير يُكتب مع عملية outbox في المعاملة نفسها.
enum MedicationRepository {
    static let entityMedication = "medication"
    static let entityLog = "medication_log"

    private static func medication(_ r: Row) -> Medication {
        Medication(
            id: r.str("id"), patientId: r.str("patient_id"), name: r.str("name"),
            doseAmount: r.double("dose_amount"), doseUnit: r.str("dose_unit"), repeatType: r.str("repeat_type"),
            repeatDays: Json.ints(Json.decode(r.str("repeat_days_json"))), frequency: r.int("frequency"),
            times: Json.strings(Json.decode(r.str("times_json"))), stockCount: r.intOrNil("stock_count"),
            stockAlert: r.int("stock_alert"), isActive: r.int("is_active") == 1, notes: r.strOrNil("notes"),
            createdAt: r.str("created_at"), updatedAt: r.str("updated_at"), deletedAt: r.strOrNil("deleted_at")
        )
    }

    private static func log(_ r: Row) -> DoseLog {
        DoseLog(
            id: r.str("id"), medicationId: r.str("medication_id"), patientId: r.str("patient_id"), scheduledAt: r.str("scheduled_at"),
            takenAt: r.strOrNil("taken_at"), status: r.str("status"), createdAt: r.str("created_at"), updatedAt: r.str("updated_at"),
            medicationName: r.strOrNil("medication_name") ?? "", doseAmount: r.doubleOrNil("medication_dose_amount") ?? 1,
            doseUnit: r.strOrNil("medication_dose_unit") ?? ""
        )
    }

    private static func values(_ m: Medication) -> [Any?] {
        [m.id, m.patientId, m.name, m.doseAmount, m.doseUnit, m.repeatType, Json.encode(m.repeatDays), m.frequency,
         Json.encode(m.times), m.stockCount, m.stockAlert, m.isActive ? 1 : 0, m.notes, m.createdAt, m.updatedAt, m.deletedAt]
    }

    private static let columns = "id, patient_id, name, dose_amount, dose_unit, repeat_type, repeat_days_json, frequency, times_json, stock_count, stock_alert, is_active, notes, created_at, updated_at, deleted_at"

    private static func insert(_ m: Medication) throws {
        try db.run("INSERT INTO medications_local (\(columns)) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", values(m))
    }

    private static func update(_ m: Medication) throws {
        try db.run(
            """
            UPDATE medications_local SET patient_id = ?, name = ?, dose_amount = ?, dose_unit = ?, repeat_type = ?, repeat_days_json = ?,
            frequency = ?, times_json = ?, stock_count = ?, stock_alert = ?, is_active = ?, notes = ?, created_at = ?, updated_at = ?, deleted_at = ?
            WHERE id = ?
            """,
            Array(values(m).dropFirst()) + [m.id]
        )
    }

    static func list(_ patientId: String, includeInactive: Bool = false) -> [Medication] {
        let filter = includeInactive ? "" : "AND is_active = 1"
        let rows = (try? db.query("SELECT * FROM medications_local WHERE patient_id = ? \(filter) ORDER BY created_at DESC", [patientId])) ?? []
        return rows.map(medication)
    }

    static func find(_ id: String) -> Medication? {
        (try? db.query("SELECT * FROM medications_local WHERE id = ?", [id]))?.first.map(medication)
    }

    static func logsBetween(_ patientId: String, _ from: String, _ to: String) -> [DoseLog] {
        let rows = (try? db.query(
            """
            SELECT logs.*, m.name AS medication_name, m.dose_amount AS medication_dose_amount, m.dose_unit AS medication_dose_unit
            FROM medication_logs_local logs JOIN medications_local m ON m.id = logs.medication_id
            WHERE logs.patient_id = ? AND logs.scheduled_at >= ? AND logs.scheduled_at < ? AND logs.status != 'skipped'
              AND NOT (m.is_active = 0 AND logs.status = 'pending' AND logs.scheduled_at > COALESCE(m.deleted_at, ''))
            ORDER BY logs.scheduled_at ASC
            """,
            [patientId, from, to]
        )) ?? []
        return rows.map(log)
    }

    /// هل سُجّلت هذه الجرعة مأخوذة، أو أُلغيت بتعديل المواعيد؟ (فلا يُجدول تذكيرها)
    static func isTaken(_ medicationId: String, _ scheduledAt: String) -> Bool {
        ((try? db.query("SELECT 1 AS x FROM medication_logs_local WHERE medication_id = ? AND scheduled_at = ? AND status IN ('taken', 'skipped')",
                        [medicationId, scheduledAt])) ?? []).isEmpty == false
    }

    /// ينشئ جرعات اليوم المجدولة للأدوية النشطة، مرة واحدة لكل جرعة
    static func ensureLogs(_ patientId: String, for day: Day = Day.today()) {
        let medications = list(patientId)
        _ = try? db.transaction {
            for medication in medications {
                // لا جرعات قبل يوم إضافة الدواء
                if let created = Iso.parse(medication.createdAt), day < Day(created) { continue }
                guard Schedule.isScheduled(medication.repeatType, medication.repeatDays, on: day) else { continue }
                for time in Schedule.timesFor(medication.repeatType, medication.times) {
                    guard let scheduledAt = Schedule.scheduledAt(day, time) else { continue }
                    // جرعة موعدها قبل إضافة الدواء لا تُحسب، فلا تظهر متأخرة أو فائتة
                    if scheduledAt < medication.createdAt { continue }
                    try insertPendingLog(patientId, medication.id, scheduledAt)
                }
            }
        }
    }

    /// يضيف جرعة منتظرة بمعرّف ثابت إن لم تكن موجودة، ويعيد معرّفها
    @discardableResult
    private static func insertPendingLog(_ patientId: String, _ medicationId: String, _ scheduledAt: String) throws -> String {
        let now = Iso.now()
        let entry = DoseLog(id: Ids.doseLogId(medicationId, scheduledAt), medicationId: medicationId, patientId: patientId,
                            scheduledAt: scheduledAt, takenAt: nil, status: DoseStatus.pending, createdAt: now, updatedAt: now)
        let inserted = try db.run(
            "INSERT OR IGNORE INTO medication_logs_local (id, medication_id, patient_id, scheduled_at, taken_at, status, created_at, updated_at) VALUES (?, ?, ?, ?, NULL, ?, ?, ?)",
            [entry.id, medicationId, patientId, scheduledAt, entry.status, now, now]
        )
        if inserted > 0 { try Outbox.enqueue(patientId, entityLog, entry.id, Json.log(entry)) }
        let existing = try db.query("SELECT id FROM medication_logs_local WHERE medication_id = ? AND scheduled_at = ?", [medicationId, scheduledAt])
        return existing.first?.strOrNil("id") ?? entry.id
    }

    @discardableResult
    static func add(_ patientId: String, _ draft: MedicationDraft) throws -> Medication {
        let now = Iso.now()
        let medication = Medication(
            id: Ids.random(), patientId: patientId, name: draft.name.trimmingCharacters(in: .whitespacesAndNewlines),
            doseAmount: draft.doseAmount, doseUnit: draft.doseUnit, repeatType: draft.repeatType, repeatDays: draft.repeatDays,
            frequency: draft.frequency, times: draft.times, stockCount: draft.stockCount, stockAlert: 7, isActive: true,
            notes: nil, createdAt: now, updatedAt: now, deletedAt: nil
        )
        try db.transaction {
            try insert(medication)
            try Outbox.enqueue(patientId, entityMedication, medication.id, Json.medication(medication))
        }
        ensureLogs(patientId)
        return medication
    }

    /// دواء مطابق بالاسم والجرعة والمواعيد، للتنبيه قبل الإضافة المكررة
    static func findDuplicate(_ patientId: String, _ draft: MedicationDraft) -> Medication? {
        func normalize(_ value: String) -> String { value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
        return list(patientId).first {
            normalize($0.name) == normalize(draft.name) && $0.doseAmount == draft.doseAmount && $0.doseUnit == draft.doseUnit &&
                $0.repeatType == draft.repeatType && $0.repeatDays.sorted() == draft.repeatDays.sorted() && $0.times.sorted() == draft.times.sorted()
        }
    }

    /// يسجّل الجرعة مأخوذة وينقص المخزون حبة واحدة. false إن كانت مأخوذة من قبل.
    static func markTaken(_ patientId: String, _ logId: String) -> Bool {
        (try? db.transaction { try markTakenInside(patientId, logId) }) ?? false
    }

    private static func markTakenInside(_ patientId: String, _ logId: String) throws -> Bool {
        let now = Iso.now()
        let changed = try db.run("UPDATE medication_logs_local SET status = 'taken', taken_at = ?, updated_at = ? WHERE id = ? AND status != 'taken'", [now, now, logId])
        guard changed > 0, let row = try db.query("SELECT * FROM medication_logs_local WHERE id = ?", [logId]).first else { return false }
        let entry = log(row)
        try db.run(
            "UPDATE medications_local SET stock_count = CASE WHEN stock_count IS NULL THEN NULL ELSE MAX(stock_count - 1, 0) END, updated_at = ? WHERE id = ?",
            [now, entry.medicationId]
        )
        try Outbox.enqueue(patientId, entityLog, entry.id, Json.log(entry))
        if let medication = find(entry.medicationId) {
            try Outbox.enqueue(patientId, entityMedication, medication.id, Json.medication(medication))
        }
        return true
    }

    /**
     تراجع عن جرعة سُجّلت بالخطأ: تعود «منتظرة» ويعود المخزون حبة.
     الخادم لا يُرجع جرعة مأخوذة إلا بهذه الإشارة الصريحة (undo_taken).
     */
    static func untake(_ patientId: String, _ logId: String) -> Bool {
        (try? db.transaction { () -> Bool in
            let now = Iso.now()
            let changed = try db.run("UPDATE medication_logs_local SET status = 'pending', taken_at = NULL, updated_at = ? WHERE id = ? AND status = 'taken'", [now, logId])
            guard changed > 0, let row = try db.query("SELECT * FROM medication_logs_local WHERE id = ?", [logId]).first else { return false }
            let entry = log(row)
            try db.run(
                "UPDATE medications_local SET stock_count = CASE WHEN stock_count IS NULL THEN NULL ELSE stock_count + 1 END, updated_at = ? WHERE id = ?",
                [now, entry.medicationId]
            )
            var payload = Json.log(entry)
            payload["undo_taken"] = true
            try Outbox.enqueue(patientId, entityLog, entry.id, payload)
            if let medication = find(entry.medicationId) {
                try Outbox.enqueue(patientId, entityMedication, medication.id, Json.medication(medication))
            }
            return true
        }) ?? false
    }

    /**
     تعديل دواء: الاسم والجرعة والمواعيد والمخزون. ما سُجّل منه يبقى في السجل.
     إن تغيّرت المواعيد: جرعات اليوم وما بعده التي لم تؤخذ وليست في الجدول الجديد تُلغى (skipped)،
     ومواعيد اليوم الجديدة التي فات وقتها لا تُحسب، فلا تظهر متأخرة.
     */
    @discardableResult
    static func edit(_ patientId: String, _ medicationId: String, _ draft: MedicationDraft) -> Bool {
        (try? db.transaction { () -> Bool in
            guard let current = find(medicationId) else { return false }
            let now = Iso.now()
            var updated = current
            updated.name = draft.name.trimmingCharacters(in: .whitespacesAndNewlines)
            updated.doseAmount = draft.doseAmount
            updated.doseUnit = draft.doseUnit
            updated.repeatType = draft.repeatType
            updated.repeatDays = draft.repeatDays
            updated.frequency = draft.frequency
            updated.times = draft.times
            updated.stockCount = draft.stockCount
            updated.updatedAt = now
            try update(updated)
            try Outbox.enqueue(patientId, entityMedication, updated.id, Json.medication(updated))
            let scheduleChanged = current.repeatType != updated.repeatType || current.repeatDays.sorted() != updated.repeatDays.sorted() ||
                Schedule.timesFor(current.repeatType, current.times) != Schedule.timesFor(updated.repeatType, updated.times)
            if !scheduleChanged { return true }
            let today = Day.today()
            func planned(_ day: Day) -> [String] {
                guard Schedule.isScheduled(updated.repeatType, updated.repeatDays, on: day) else { return [] }
                return Schedule.timesFor(updated.repeatType, updated.times).compactMap { Schedule.scheduledAt(day, $0) }
            }
            let keep = Set(planned(today) + planned(today.adding(days: 1)))
            let from = Schedule.dayRange(today).0
            let pending = try db.query("SELECT * FROM medication_logs_local WHERE medication_id = ? AND status = 'pending' AND scheduled_at >= ?", [medicationId, from]).map(log)
            for entry in pending where !keep.contains(entry.scheduledAt) {
                try db.run("UPDATE medication_logs_local SET status = 'skipped', updated_at = ? WHERE id = ?", [now, entry.id])
                var skipped = entry
                skipped.status = DoseStatus.skipped
                skipped.updatedAt = now
                try Outbox.enqueue(patientId, entityLog, skipped.id, Json.log(skipped))
            }
            for scheduledAt in planned(today) where scheduledAt <= now {
                let skipped = DoseLog(id: Ids.doseLogId(medicationId, scheduledAt), medicationId: medicationId, patientId: patientId,
                                      scheduledAt: scheduledAt, takenAt: nil, status: DoseStatus.skipped, createdAt: now, updatedAt: now)
                let inserted = try db.run(
                    "INSERT OR IGNORE INTO medication_logs_local (id, medication_id, patient_id, scheduled_at, taken_at, status, created_at, updated_at) VALUES (?, ?, ?, ?, NULL, ?, ?, ?)",
                    [skipped.id, medicationId, patientId, scheduledAt, skipped.status, now, now]
                )
                if inserted > 0 { try Outbox.enqueue(patientId, entityLog, skipped.id, Json.log(skipped)) }
            }
            return true
        }) ?? false
    }

    /// تسجيل من الإشعار: ينشئ الجرعة إن لم تكن موجودة ثم يسجّلها مأخوذة
    static func markTaken(_ patientId: String, medicationId: String, scheduledAt: String) -> Bool {
        (try? db.transaction { () -> Bool in
            guard find(medicationId)?.isActive == true else { return false }
            let logId = try insertPendingLog(patientId, medicationId, scheduledAt)
            return try markTakenInside(patientId, logId)
        }) ?? false
    }

    /// إيقاف الدواء: يختفي من القوائم والتذكيرات ويبقى في السجل
    static func deactivate(_ patientId: String, _ medicationId: String) {
        let now = Iso.now()
        updateMedication(patientId, medicationId) {
            $0.isActive = false
            $0.deletedAt = now
            $0.updatedAt = now
        }
        // جرعاته القادمة التي لم تؤخذ تُلغى، فلا تظهر في «جرعات اليوم» ولا عند العائلة
        _ = try? db.transaction {
            let pending = try db.query("SELECT * FROM medication_logs_local WHERE medication_id = ? AND status = 'pending' AND scheduled_at > ?", [medicationId, now]).map(log)
            for entry in pending {
                try db.run("UPDATE medication_logs_local SET status = 'skipped', updated_at = ? WHERE id = ?", [now, entry.id])
                var skipped = entry
                skipped.status = DoseStatus.skipped
                skipped.updatedAt = now
                try Outbox.enqueue(patientId, entityLog, skipped.id, Json.log(skipped))
            }
        }
    }

    /// علبة جديدة: يضيف العدد إلى المخزون
    static func refill(_ patientId: String, _ medicationId: String, _ amount: Int) {
        updateMedication(patientId, medicationId) {
            $0.stockCount = min(max(($0.stockCount ?? 0) + amount, 0), 100_000)
            $0.updatedAt = Iso.now()
        }
    }

    private static func updateMedication(_ patientId: String, _ medicationId: String, _ change: (inout Medication) -> Void) {
        _ = try? db.transaction {
            guard var medication = find(medicationId) else { return }
            change(&medication)
            try update(medication)
            try Outbox.enqueue(patientId, entityMedication, medication.id, Json.medication(medication))
        }
    }

    /// يطبّق صفحة من تغييرات الخادم وفق قواعد التعارض
    static func applyRemote(_ medications: [Medication], _ logs: [DoseLog]) throws {
        if medications.isEmpty && logs.isEmpty { return }
        try db.transaction {
            for remote in medications {
                let local = find(remote.id)
                guard Conflict.shouldApplyRemote(local?.updatedAt, remote.updatedAt) else { continue }
                // تحديث لا استبدال: REPLACE يحذف الصف أولًا فيحذف جرعاته معه (ON DELETE CASCADE)
                if local == nil { try insert(remote) } else { try update(remote) }
            }
            for remote in logs {
                let localRow = try db.query("SELECT status, taken_at, updated_at FROM medication_logs_local WHERE id = ?", [remote.id]).first
                let local = localRow.map { Conflict.DoseState(status: $0.str("status"), takenAt: $0.strOrNil("taken_at"), updatedAt: $0.str("updated_at")) }
                let merged = Conflict.mergeDose(local, Conflict.DoseState(status: remote.status, takenAt: remote.takenAt, updatedAt: remote.updatedAt))
                if let local = local {
                    try db.run("UPDATE medication_logs_local SET status = ?, taken_at = ?, updated_at = ? WHERE id = ?",
                               [merged.status, merged.takenAt, max(local.updatedAt, merged.updatedAt), remote.id])
                    continue
                }
                // جرعة الخادم قد تكون محفوظة محليًا بمعرّف آخر للموعد نفسه: نبقي النسخة المحلية
                guard find(remote.medicationId) != nil else { continue }
                try db.run(
                    "INSERT OR IGNORE INTO medication_logs_local (id, medication_id, patient_id, scheduled_at, taken_at, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    [remote.id, remote.medicationId, remote.patientId, remote.scheduledAt, merged.takenAt, merged.status, remote.createdAt, merged.updatedAt]
                )
            }
        }
    }
}
