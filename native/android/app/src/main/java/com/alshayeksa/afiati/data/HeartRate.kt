package com.alshayeksa.afiati.data

import android.content.ContentValues

/** قياس نبض محفوظ على الجوال فقط (لا يُرفع للخادم) */
data class HeartRateReading(val id: String, val patientId: String, val bpm: Int, val measuredAt: String, val quality: Double)

object HeartRateRepository {
    const val TABLE_SQL = """CREATE TABLE IF NOT EXISTS heart_rate_local (
        id TEXT PRIMARY KEY NOT NULL,
        patient_id TEXT NOT NULL,
        bpm INTEGER NOT NULL CHECK (bpm BETWEEN 30 AND 250),
        measured_at TEXT NOT NULL,
        quality REAL NOT NULL
    )"""
    const val INDEX_SQL = "CREATE INDEX IF NOT EXISTS idx_heart_rate_patient ON heart_rate_local(patient_id, measured_at)"

    fun save(patientId: String, bpm: Int, quality: Double): HeartRateReading {
        val reading = HeartRateReading(Ids.random(), patientId, bpm, Iso.now(), quality)
        Db.db.insertOrThrow("heart_rate_local", null, ContentValues().apply {
            put("id", reading.id)
            put("patient_id", reading.patientId)
            put("bpm", reading.bpm)
            put("measured_at", reading.measuredAt)
            put("quality", reading.quality)
        })
        return reading
    }

    fun recent(patientId: String, limit: Int = 5): List<HeartRateReading> = Db.db.rawQuery(
        "SELECT id, patient_id, bpm, measured_at, quality FROM heart_rate_local WHERE patient_id = ? ORDER BY measured_at DESC LIMIT ?",
        arrayOf(patientId, limit.toString()),
    ).use { c ->
        buildList { while (c.moveToNext()) add(HeartRateReading(c.getString(0), c.getString(1), c.getInt(2), c.getString(3), c.getDouble(4))) }
    }

    fun delete(id: String) {
        Db.db.delete("heart_rate_local", "id = ?", arrayOf(id))
    }
}
