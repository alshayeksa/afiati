package com.alshayeksa.afiati.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** ملف المريض المحلي. لا يوجد إلا ملف واحد في الجوال. */
object PatientRepository {
    private fun fromCursor(c: Cursor) = Patient(
        id = c.str("id"),
        authUserId = c.strOrNull("auth_user_id"),
        fullName = c.str("full_name"),
        phone = c.str("phone"),
        email = c.strOrNull("email"),
        createdAt = c.str("created_at"),
        updatedAt = c.str("updated_at"),
    )

    fun get(): Patient? = Db.db.rawQuery("SELECT * FROM patients_local LIMIT 1", null).first(::fromCursor)

    fun create(fullName: String, phone: String, email: String?): Patient {
        get()?.let { return it }
        val now = Iso.now()
        val patient = Patient(Ids.random(), null, fullName.trim(), phone.trim(), email?.trim()?.lowercase()?.ifEmpty { null }, now, now)
        Db.db.insertOrThrow("patients_local", null, ContentValues().apply {
            put("id", patient.id)
            putNull("auth_user_id")
            put("full_name", patient.fullName)
            put("phone", patient.phone)
            put("email", patient.email)
            put("phone_verified", 0)
            put("created_at", now)
            put("updated_at", now)
        })
        return patient
    }

    fun update(fullName: String, phone: String, email: String?): Patient {
        val existing = get() ?: error("لا يوجد ملف شخصي لتحديثه.")
        val updated = existing.copy(
            fullName = fullName.trim(), phone = phone.trim(),
            email = email?.trim()?.lowercase()?.ifEmpty { null }, updatedAt = Iso.now(),
        )
        Db.db.update("patients_local", ContentValues().apply {
            put("full_name", updated.fullName)
            put("phone", updated.phone)
            put("email", updated.email)
            put("updated_at", updated.updatedAt)
        }, "id = ?", arrayOf(updated.id))
        return updated
    }

    /** يحفظ معرّف الجهاز في الخادم. لا يغيّر updated_at حتى لا يُعاد رفع الملف بلا داعٍ. */
    fun setCloudIdentity(deviceId: String) {
        Db.db.execSQL("UPDATE patients_local SET auth_user_id = ? WHERE id = (SELECT id FROM patients_local LIMIT 1)", arrayOf(deviceId))
    }
}

/** قيم المزامنة: رمز الجهاز، ومؤشرات السحب، وآخر مزامنة ناجحة. */
object SyncState {
    const val DEVICE_TOKEN = "device_token"
    const val LAST_SUCCESS = "last_successful_sync"
    const val PROFILE_VERSION = "profile_synced_version"
    const val PULL_MEDICATIONS = "pull_medications_after"
    const val PULL_LOGS = "pull_logs_after"

    fun get(key: String): String? =
        Db.db.rawQuery("SELECT value FROM sync_state WHERE key = ?", arrayOf(key)).first { it.getString(0) }

    fun set(key: String, value: String) {
        Db.db.execSQL(
            "INSERT INTO sync_state (key, value, updated_at) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at",
            arrayOf(key, value, Iso.now()),
        )
    }
}

data class OutboxOperation(
    val operationId: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val payload: String,
)

/** العمليات المحلية التي لم تصل الخادم بعد. لا تُحذف إلا بعد قبول الخادم لها. */
object Outbox {
    /** عملية رفضها الخادم 5 مرات تبقى للفحص لكنها لا تُرسل، فلا تحجز ما بعدها. */
    const val MAX_ATTEMPTS = 5

    fun enqueue(db: SQLiteDatabase, patientId: String, entityType: String, entityId: String, payload: JSONObject) {
        db.insertOrThrow("outbox", null, ContentValues().apply {
            put("operation_id", Ids.random())
            put("patient_id", patientId)
            put("entity_type", entityType)
            put("entity_id", entityId)
            put("operation_type", "upsert")
            put("payload_json", payload.toString())
            put("created_at", Iso.now())
        })
    }

    fun list(limit: Int): List<OutboxOperation> = Db.db.rawQuery(
        "SELECT * FROM outbox WHERE attempts < ? ORDER BY created_at ASC, rowid ASC LIMIT ?",
        arrayOf(MAX_ATTEMPTS.toString(), limit.toString()),
    ).list { OutboxOperation(it.str("operation_id"), it.str("entity_type"), it.str("entity_id"), it.str("operation_type"), it.str("payload_json")) }

    fun remove(operationId: String) {
        Db.db.delete("outbox", "operation_id = ?", arrayOf(operationId))
    }

    fun fail(operationId: String, message: String) {
        Db.db.execSQL("UPDATE outbox SET attempts = attempts + 1, last_error = ? WHERE operation_id = ?", arrayOf(message.take(500), operationId))
    }

    fun pendingCount(): Int =
        Db.db.rawQuery("SELECT COUNT(*) FROM outbox WHERE attempts < ?", arrayOf(MAX_ATTEMPTS.toString())).first { it.getInt(0) } ?: 0
}

/** الأدوية والجرعات. كل تغيير يُكتب مع عملية outbox في المعاملة نفسها. */
object MedicationRepository {
    const val ENTITY_MEDICATION = "medication"
    const val ENTITY_LOG = "medication_log"

    private fun medicationFrom(c: Cursor) = Medication(
        id = c.str("id"),
        patientId = c.str("patient_id"),
        name = c.str("name"),
        doseAmount = c.double("dose_amount"),
        doseUnit = c.str("dose_unit"),
        repeatType = c.str("repeat_type"),
        repeatDays = Json.ints(JSONArray(c.str("repeat_days_json"))),
        frequency = c.int("frequency"),
        times = Json.strings(JSONArray(c.str("times_json"))),
        stockCount = c.intOrNull("stock_count"),
        stockAlert = c.int("stock_alert"),
        isActive = c.int("is_active") == 1,
        notes = c.strOrNull("notes"),
        createdAt = c.str("created_at"),
        updatedAt = c.str("updated_at"),
        deletedAt = c.strOrNull("deleted_at"),
    )

    private fun logFrom(c: Cursor) = DoseLog(
        id = c.str("id"),
        medicationId = c.str("medication_id"),
        patientId = c.str("patient_id"),
        scheduledAt = c.str("scheduled_at"),
        takenAt = c.strOrNull("taken_at"),
        status = c.str("status"),
        createdAt = c.str("created_at"),
        updatedAt = c.str("updated_at"),
        medicationName = c.optStr("medication_name") ?: "",
        doseAmount = c.optDouble("medication_dose_amount") ?: 1.0,
        doseUnit = c.optStr("medication_dose_unit") ?: "",
    )

    private fun values(m: Medication) = ContentValues().apply {
        put("id", m.id)
        put("patient_id", m.patientId)
        put("name", m.name)
        put("dose_amount", m.doseAmount)
        put("dose_unit", m.doseUnit)
        put("repeat_type", m.repeatType)
        put("repeat_days_json", JSONArray().apply { m.repeatDays.forEach { put(it) } }.toString())
        put("frequency", m.frequency)
        put("times_json", JSONArray().apply { m.times.forEach { put(it) } }.toString())
        if (m.stockCount == null) putNull("stock_count") else put("stock_count", m.stockCount)
        put("stock_alert", m.stockAlert)
        put("is_active", if (m.isActive) 1 else 0)
        put("notes", m.notes)
        put("created_at", m.createdAt)
        put("updated_at", m.updatedAt)
        put("deleted_at", m.deletedAt)
    }

    fun list(patientId: String, includeInactive: Boolean = false): List<Medication> = Db.db.rawQuery(
        "SELECT * FROM medications_local WHERE patient_id = ? ${if (includeInactive) "" else "AND is_active = 1"} ORDER BY created_at DESC",
        arrayOf(patientId),
    ).list(::medicationFrom)

    fun find(db: SQLiteDatabase, id: String): Medication? =
        db.rawQuery("SELECT * FROM medications_local WHERE id = ?", arrayOf(id)).first(::medicationFrom)

    fun logsBetween(patientId: String, fromIso: String, toIso: String): List<DoseLog> = Db.db.rawQuery(
        """SELECT logs.*, m.name AS medication_name, m.dose_amount AS medication_dose_amount, m.dose_unit AS medication_dose_unit
           FROM medication_logs_local logs JOIN medications_local m ON m.id = logs.medication_id
           WHERE logs.patient_id = ? AND logs.scheduled_at >= ? AND logs.scheduled_at < ?
           ORDER BY logs.scheduled_at ASC""",
        arrayOf(patientId, fromIso, toIso),
    ).list(::logFrom)

    /** ينشئ جرعات اليوم المجدولة للأدوية النشطة، مرة واحدة لكل جرعة */
    fun ensureLogsForDate(patientId: String, date: LocalDate = LocalDate.now()) {
        val medications = list(patientId)
        Db.tx { db ->
            for (medication in medications) {
                // لا جرعات قبل يوم إضافة الدواء
                val created = runCatching { Iso.parse(medication.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.getOrNull()
                if (created != null && date.isBefore(created)) continue
                if (!Schedule.isScheduledOn(medication.repeatType, medication.repeatDays, date)) continue
                for (time in Schedule.timesFor(medication.repeatType, medication.times)) {
                    val scheduledAt = Schedule.scheduledAt(date, time) ?: continue
                    // جرعة موعدها قبل إضافة الدواء لا تُحسب، فلا تظهر متأخرة أو فائتة
                    if (scheduledAt < medication.createdAt) continue
                    insertPendingLog(db, patientId, medication.id, scheduledAt)
                }
            }
        }
    }

    /** يضيف جرعة منتظرة بمعرّف ثابت إن لم تكن موجودة، ويعيد معرّفها */
    private fun insertPendingLog(db: SQLiteDatabase, patientId: String, medicationId: String, scheduledAt: String): String {
        val now = Iso.now()
        val log = DoseLog(Ids.doseLogId(medicationId, scheduledAt), medicationId, patientId, scheduledAt, null, DoseStatus.PENDING, now, now)
        val row = db.insertWithOnConflict("medication_logs_local", null, ContentValues().apply {
            put("id", log.id)
            put("medication_id", log.medicationId)
            put("patient_id", log.patientId)
            put("scheduled_at", log.scheduledAt)
            putNull("taken_at")
            put("status", log.status)
            put("created_at", now)
            put("updated_at", now)
        }, SQLiteDatabase.CONFLICT_IGNORE)
        if (row != -1L) Outbox.enqueue(db, patientId, ENTITY_LOG, log.id, Json.log(log))
        return db.rawQuery(
            "SELECT id FROM medication_logs_local WHERE medication_id = ? AND scheduled_at = ?", arrayOf(medicationId, scheduledAt),
        ).first { it.getString(0) } ?: log.id
    }

    fun add(patientId: String, draft: MedicationDraft): Medication {
        val now = Iso.now()
        val medication = Medication(
            id = Ids.random(), patientId = patientId, name = draft.name.trim(), doseAmount = draft.doseAmount,
            doseUnit = draft.doseUnit, repeatType = draft.repeatType, repeatDays = draft.repeatDays,
            frequency = draft.frequency, times = draft.times, stockCount = draft.stockCount, stockAlert = 7,
            isActive = true, notes = null, createdAt = now, updatedAt = now, deletedAt = null,
        )
        Db.tx { db ->
            db.insertOrThrow("medications_local", null, values(medication))
            Outbox.enqueue(db, patientId, ENTITY_MEDICATION, medication.id, Json.medication(medication))
        }
        ensureLogsForDate(patientId)
        return medication
    }

    /** دواء مطابق بالاسم والجرعة والمواعيد، للتنبيه قبل الإضافة المكررة */
    fun findDuplicate(patientId: String, draft: MedicationDraft): Medication? {
        fun normalize(value: String) = value.trim().lowercase()
        return list(patientId).firstOrNull {
            normalize(it.name) == normalize(draft.name) && it.doseAmount == draft.doseAmount && it.doseUnit == draft.doseUnit &&
                it.repeatType == draft.repeatType && it.repeatDays.sorted() == draft.repeatDays.sorted() && it.times.sorted() == draft.times.sorted()
        }
    }

    /** يسجّل الجرعة مأخوذة وينقص المخزون حبة واحدة. لا شيء إن كانت مأخوذة من قبل. */
    fun markTaken(patientId: String, logId: String): Boolean = Db.tx { db -> markTakenIn(db, patientId, logId) }

    private fun markTakenIn(db: SQLiteDatabase, patientId: String, logId: String): Boolean {
        val now = Iso.now()
        db.compileStatement("UPDATE medication_logs_local SET status = 'taken', taken_at = ?, updated_at = ? WHERE id = ? AND status != 'taken'").use {
            it.bindString(1, now)
            it.bindString(2, now)
            it.bindString(3, logId)
            if (it.executeUpdateDelete() == 0) return false
        }
        val log = db.rawQuery("SELECT * FROM medication_logs_local WHERE id = ?", arrayOf(logId)).first(::logFrom) ?: return false
        db.execSQL(
            "UPDATE medications_local SET stock_count = CASE WHEN stock_count IS NULL THEN NULL ELSE MAX(stock_count - 1, 0) END, updated_at = ? WHERE id = ?",
            arrayOf(now, log.medicationId),
        )
        Outbox.enqueue(db, patientId, ENTITY_LOG, log.id, Json.log(log))
        find(db, log.medicationId)?.let { Outbox.enqueue(db, patientId, ENTITY_MEDICATION, it.id, Json.medication(it)) }
        return true
    }

    /** تسجيل من الإشعار: ينشئ الجرعة إن لم تكن موجودة ثم يسجّلها مأخوذة */
    fun markTakenFor(patientId: String, medicationId: String, scheduledAt: String): Boolean = Db.tx { db ->
        if (find(db, medicationId)?.isActive != true) return@tx false
        val logId = insertPendingLog(db, patientId, medicationId, scheduledAt)
        markTakenIn(db, patientId, logId)
    }

    /** إيقاف الدواء: يختفي من القوائم والتذكيرات ويبقى في السجل */
    fun deactivate(patientId: String, medicationId: String) = updateMedication(patientId, medicationId) {
        val now = Iso.now()
        it.copy(isActive = false, deletedAt = now, updatedAt = now)
    }

    /** علبة جديدة: يضيف العدد إلى المخزون */
    fun refill(patientId: String, medicationId: String, amount: Int) = updateMedication(patientId, medicationId) {
        it.copy(stockCount = ((it.stockCount ?: 0) + amount).coerceIn(0, 100000), updatedAt = Iso.now())
    }

    private fun updateMedication(patientId: String, medicationId: String, change: (Medication) -> Medication) {
        Db.tx { db ->
            val current = find(db, medicationId) ?: return@tx
            val updated = change(current)
            db.update("medications_local", values(updated), "id = ?", arrayOf(medicationId))
            Outbox.enqueue(db, patientId, ENTITY_MEDICATION, updated.id, Json.medication(updated))
        }
    }

    /** يطبّق صفحة من تغييرات الخادم وفق قواعد التعارض */
    fun applyRemote(medications: List<Medication>, logs: List<DoseLog>) {
        if (medications.isEmpty() && logs.isEmpty()) return
        Db.tx { db ->
            for (remote in medications) {
                val local = find(db, remote.id)
                if (!Conflict.shouldApplyRemote(local?.updatedAt, remote.updatedAt)) continue
                // تحديث لا استبدال: REPLACE يحذف الصف أولًا فيحذف جرعاته معه (ON DELETE CASCADE)
                if (local == null) db.insertOrThrow("medications_local", null, values(remote))
                else db.update("medications_local", values(remote), "id = ?", arrayOf(remote.id))
            }
            for (remote in logs) {
                val local = db.rawQuery("SELECT status, taken_at, updated_at FROM medication_logs_local WHERE id = ?", arrayOf(remote.id))
                    .first { Conflict.DoseState(it.str("status"), it.strOrNull("taken_at"), it.str("updated_at")) }
                val merged = Conflict.mergeDose(local, Conflict.DoseState(remote.status, remote.takenAt, remote.updatedAt))
                if (local != null) {
                    db.update("medication_logs_local", ContentValues().apply {
                        put("status", merged.status)
                        put("taken_at", merged.takenAt)
                        put("updated_at", maxOf(local.updatedAt, merged.updatedAt))
                    }, "id = ?", arrayOf(remote.id))
                    continue
                }
                // جرعة الخادم قد تكون محفوظة محليًا بمعرّف آخر للموعد نفسه: نبقي النسخة المحلية
                if (find(db, remote.medicationId) == null) continue
                db.insertWithOnConflict("medication_logs_local", null, ContentValues().apply {
                    put("id", remote.id)
                    put("medication_id", remote.medicationId)
                    put("patient_id", remote.patientId)
                    put("scheduled_at", remote.scheduledAt)
                    put("taken_at", merged.takenAt)
                    put("status", merged.status)
                    put("created_at", remote.createdAt)
                    put("updated_at", merged.updatedAt)
                }, SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
    }
}
