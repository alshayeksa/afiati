package com.alshayeksa.afiati.data

import org.json.JSONArray
import org.json.JSONObject

/** تحويل الصفوف إلى JSON وبالعكس بنفس أسماء حقول الخادم */
object Json {
    fun medication(m: Medication): JSONObject = JSONObject()
        .put("id", m.id)
        .put("patient_id", m.patientId)
        .put("name", m.name)
        .put("dose_amount", m.doseAmount)
        .put("dose_unit", m.doseUnit)
        .put("repeat_type", m.repeatType)
        .put("repeat_days", JSONArray().apply { m.repeatDays.forEach { put(it) } })
        .put("frequency", m.frequency)
        .put("times", JSONArray().apply { m.times.forEach { put(it) } })
        .put("stock_count", m.stockCount ?: JSONObject.NULL)
        .put("stock_alert", m.stockAlert)
        .put("is_active", m.isActive)
        .put("notes", m.notes ?: JSONObject.NULL)
        .put("created_at", m.createdAt)
        .put("updated_at", m.updatedAt)
        .put("deleted_at", m.deletedAt ?: JSONObject.NULL)

    fun log(l: DoseLog): JSONObject = JSONObject()
        .put("id", l.id)
        .put("medication_id", l.medicationId)
        .put("patient_id", l.patientId)
        .put("scheduled_at", l.scheduledAt)
        .put("taken_at", l.takenAt ?: JSONObject.NULL)
        .put("status", l.status)
        .put("created_at", l.createdAt)
        .put("updated_at", l.updatedAt)

    fun parseMedication(o: JSONObject): Medication = Medication(
        id = o.getString("id"),
        patientId = o.getString("patient_id"),
        name = o.getString("name"),
        doseAmount = o.getDouble("dose_amount"),
        doseUnit = o.getString("dose_unit"),
        repeatType = o.optString("repeat_type", RepeatType.DAILY),
        repeatDays = ints(o.optJSONArray("repeat_days")),
        frequency = o.getInt("frequency"),
        times = strings(o.optJSONArray("times")),
        stockCount = if (!o.has("stock_count") || o.isNull("stock_count")) null else o.getInt("stock_count"),
        stockAlert = o.optInt("stock_alert", 7),
        isActive = o.optBoolean("is_active", true),
        notes = o.nullableString("notes"),
        createdAt = o.nullableString("created_at") ?: Iso.now(),
        updatedAt = o.nullableString("updated_at") ?: Iso.now(),
        deletedAt = o.nullableString("deleted_at"),
    )

    fun parseLog(o: JSONObject): DoseLog = DoseLog(
        id = o.getString("id"),
        medicationId = o.getString("medication_id"),
        patientId = o.getString("patient_id"),
        scheduledAt = o.getString("scheduled_at"),
        takenAt = o.nullableString("taken_at"),
        status = o.getString("status"),
        createdAt = o.nullableString("created_at") ?: Iso.now(),
        updatedAt = o.nullableString("updated_at") ?: Iso.now(),
    )

    fun ints(array: JSONArray?): List<Int> = if (array == null) emptyList() else List(array.length()) { array.getInt(it) }
    fun strings(array: JSONArray?): List<String> = if (array == null) emptyList() else List(array.length()) { array.getString(it) }
    fun objects(array: JSONArray?): List<JSONObject> = if (array == null) emptyList() else List(array.length()) { array.getJSONObject(it) }
}

fun JSONObject.nullableString(name: String): String? = if (!has(name) || isNull(name)) null else getString(name)
