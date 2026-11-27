package com.alshayeksa.afiati.data

/** المريض صاحب الجوال. المعرّف يُنشأ محليًا ولا يتغير بعد المزامنة. */
data class Patient(
    val id: String,
    val authUserId: String?,
    val fullName: String,
    val phone: String,
    val email: String?,
    val createdAt: String,
    val updatedAt: String,
)

object RepeatType {
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
    val all = listOf(DAILY, WEEKLY, MONTHLY)
}

object DoseStatus {
    const val PENDING = "pending"
    const val TAKEN = "taken"
    const val MISSED = "missed"
    const val SKIPPED = "skipped"
}

data class Medication(
    val id: String,
    val patientId: String,
    val name: String,
    val doseAmount: Double,
    val doseUnit: String,
    val repeatType: String,
    /** أسبوعي: 1 الأحد … 7 السبت. شهري: يوم الشهر 1 … 31. */
    val repeatDays: List<Int>,
    val frequency: Int,
    /** HH:mm بنظام 24 ساعة */
    val times: List<String>,
    val stockCount: Int?,
    val stockAlert: Int,
    val isActive: Boolean,
    val notes: String?,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
) {
    val lowStock: Boolean get() = stockCount != null && stockCount <= stockAlert
}

/** ما يدخله المستخدم في معالج الإضافة */
data class MedicationDraft(
    val name: String,
    val doseAmount: Double,
    val doseUnit: String,
    val repeatType: String,
    val repeatDays: List<Int>,
    val frequency: Int,
    val times: List<String>,
    val stockCount: Int?,
)

data class DoseLog(
    val id: String,
    val medicationId: String,
    val patientId: String,
    val scheduledAt: String,
    val takenAt: String?,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val medicationName: String = "",
    val doseAmount: Double = 1.0,
    val doseUnit: String = "",
)
