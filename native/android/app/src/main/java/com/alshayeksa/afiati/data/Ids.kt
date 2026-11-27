package com.alshayeksa.afiati.data

import java.security.MessageDigest

/** معرّفات ثابتة: نفس الجرعة لها نفس المعرّف على كل جهاز، مثل lib/sync/ids.ts في نسخة Expo. */
object Ids {
    /** أول 128 بت من بصمة hex بصيغة UUID (الإصدار 8، RFC 9562) */
    fun uuidFromHex(hex: String): String {
        require(hex.length >= 32 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) { "needs at least 32 hex characters" }
        val h = hex.substring(0, 32).lowercase()
        val variant = ((h[16].digitToInt(16) and 0x3) or 0x8).toString(16)
        return "${h.substring(0, 8)}-${h.substring(8, 12)}-8${h.substring(13, 16)}-$variant${h.substring(17, 20)}-${h.substring(20, 32)}"
    }

    fun doseLogKey(medicationId: String, scheduledAt: String) = "$medicationId|$scheduledAt"

    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { String.format(java.util.Locale.US, "%02x", it) }

    fun doseLogId(medicationId: String, scheduledAt: String) = uuidFromHex(sha256Hex(doseLogKey(medicationId, scheduledAt)))

    fun random(): String = java.util.UUID.randomUUID().toString()
}
