package com.alshayeksa.afiati.data

import com.alshayeksa.afiati.tr
import android.content.ContentValues
import java.util.Locale

/** العلامات الحيوية. النبض يقيسه الجهاز بالكاميرا (HeartRateRepository)، والباقي إدخال يدوي. */
enum class VitalType(val key: String, private val titleAr: String, private val titleEn: String, private val unitAr: String, private val unitEn: String) {
    PULSE("pulse", "النبض", "Heart rate", "نبضة/دقيقة", "bpm"),
    PRESSURE("pressure", "ضغط الدم", "Blood pressure", "ملم زئبق", "mmHg"),
    SUGAR("sugar", "سكر الدم", "Blood sugar", "ملغ/دل", "mg/dL"),
    TEMPERATURE("temperature", "درجة الحرارة", "Temperature", "°م", "°C");

    val title get() = tr(titleAr, titleEn)
    val unit get() = tr(unitAr, unitEn)

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

/** متى قيس السكر: يغيّر المدى الطبيعي */
enum class SugarContext(
    val key: String, private val titleAr: String, private val titleEn: String, private val shortAr: String, private val shortEn: String,
    private val hintAr: String, private val hintEn: String,
) {
    FASTING("fasting", "صائم", "Fasting", "صائم", "Fasting", "قبل الأكل، بعد 8 ساعات بلا طعام", "Before eating, after 8 hours without food"),
    AFTER_MEAL("after_meal", "بعد الأكل", "After meal", "بعد الأكل", "After meal", "بعد ساعتين من بداية الأكل", "Two hours after you start eating"),
    RANDOM("random", "أي وقت", "Any time", "أي وقت", "Any time", "في أي وقت من اليوم", "At any time of day");

    val title get() = tr(titleAr, titleEn)
    val short get() = tr(shortAr, shortEn)
    val hint get() = tr(hintAr, hintEn)

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: RANDOM
    }
}

/** حالة القراءة بلغة بسيطة. للمتابعة فقط، وليست تشخيصًا. */
enum class VitalStatus(private val ar: String, private val en: String) {
    VERY_LOW("منخفض جدًا", "Very low"),
    LOW("منخفض", "Low"),
    NORMAL("طبيعي", "Normal"),
    ELEVATED("مرتفع قليلًا", "Slightly high"),
    HIGH("مرتفع", "High"),
    VERY_HIGH("مرتفع جدًا", "Very high");

    val title get() = tr(ar, en)

    /** قراءة تستحق التنبيه بالاتصال بالطبيب أو الإسعاف إن صاحبتها أعراض */
    val urgent get() = this == VERY_LOW || this == VERY_HIGH
}

/** قراءة يدوية: الضغط قيمتان (انقباضي/انبساطي)، والسكر معه وقت القياس */
data class VitalReading(
    val id: String,
    val patientId: String,
    val type: VitalType,
    val value: Double,
    val value2: Double?,
    val context: String?,
    val measuredAt: String,
)

/**
 * المدى الطبيعي للبالغين بحسب الإرشادات الشائعة:
 * الضغط (جمعية القلب الأمريكية)، السكر (جمعية السكري الأمريكية)، الحرارة الفموية، النبض في الراحة.
 */
object VitalRanges {
    fun pressure(systolic: Double, diastolic: Double): VitalStatus = when {
        systolic >= 180 || diastolic >= 120 -> VitalStatus.VERY_HIGH
        systolic >= 140 || diastolic >= 90 -> VitalStatus.HIGH
        systolic < 90 || diastolic < 60 -> VitalStatus.LOW
        systolic >= 120 || diastolic >= 80 -> VitalStatus.ELEVATED
        else -> VitalStatus.NORMAL
    }

    fun sugar(value: Double, context: SugarContext): VitalStatus = when {
        value < 54 -> VitalStatus.VERY_LOW
        value < 70 -> VitalStatus.LOW
        value >= 300 -> VitalStatus.VERY_HIGH
        context == SugarContext.FASTING -> when {
            value < 100 -> VitalStatus.NORMAL
            value < 126 -> VitalStatus.ELEVATED
            else -> VitalStatus.HIGH
        }
        else -> when {
            value < 140 -> VitalStatus.NORMAL
            value < 200 -> VitalStatus.ELEVATED
            else -> VitalStatus.HIGH
        }
    }

    fun temperature(celsius: Double): VitalStatus = when {
        celsius < 35.0 -> VitalStatus.LOW
        celsius < 37.5 -> VitalStatus.NORMAL
        celsius < 38.0 -> VitalStatus.ELEVATED
        celsius < 39.5 -> VitalStatus.HIGH
        else -> VitalStatus.VERY_HIGH
    }

    fun pulse(bpm: Int): VitalStatus = when {
        bpm < 50 -> VitalStatus.LOW
        bpm <= 100 -> VitalStatus.NORMAL
        bpm <= 120 -> VitalStatus.ELEVATED
        else -> VitalStatus.HIGH
    }

    fun of(reading: VitalReading): VitalStatus = when (reading.type) {
        VitalType.PRESSURE -> pressure(reading.value, reading.value2 ?: 0.0)
        VitalType.SUGAR -> sugar(reading.value, SugarContext.of(reading.context))
        VitalType.TEMPERATURE -> temperature(reading.value)
        VitalType.PULSE -> pulse(reading.value.toInt())
    }

    /** سطر المدى الطبيعي تحت اسم العلامة */
    fun normalText(type: VitalType, context: SugarContext = SugarContext.FASTING) = when (type) {
        VitalType.PULSE -> tr("الطبيعي في الراحة: ${ltr("60–100")}", "Normal at rest: ${ltr("60–100")}")
        VitalType.PRESSURE -> tr("الطبيعي: أقل من ${ltr("120/80")}", "Normal: below ${ltr("120/80")}")
        VitalType.SUGAR -> if (context == SugarContext.FASTING) tr("الطبيعي صائمًا: ${ltr("70–99")}", "Normal fasting: ${ltr("70–99")}") else tr("الطبيعي بعد الأكل: أقل من 140", "Normal after meals: below 140")
        VitalType.TEMPERATURE -> tr("الطبيعي: ${ltr("36.1–37.4")}", "Normal: ${ltr("36.1–37.4")}")
    }

    /** يثبّت اتجاه المدى من اليسار إلى اليمين داخل جملة عربية، فلا يظهر «100-60» */
    fun ltr(text: String) = "⁦$text⁩"

    /** نصيحة هادئة لقراءة خارج الطبيعي */
    fun advice(status: VitalStatus): String? = when {
        status.urgent -> tr("قراءة تحتاج انتباهًا. إن شعرت بتعب أو دوخة أو ألم فاتصل بطبيبك أو بالإسعاف 997.", "This reading needs attention. If you feel tired, dizzy or in pain, call your doctor or an ambulance (997).")
        status == VitalStatus.NORMAL -> null
        else -> tr("أعد القياس بعد راحة خمس دقائق، وإن تكررت فاستشر طبيبك.", "Measure again after resting five minutes; if it repeats, consult your doctor.")
    }

    /** الحدود المقبولة للإدخال، لمنع الأخطاء الكتابية */
    val systolicRange = 60.0..260.0
    val diastolicRange = 30.0..160.0
    val sugarRange = 20.0..600.0
    val temperatureRange = 33.0..43.0

    fun display(reading: VitalReading): String = when (reading.type) {
        VitalType.PRESSURE -> ltr("${reading.value.toInt()}/${reading.value2?.toInt() ?: 0}")
        VitalType.TEMPERATURE -> String.format(Locale.US, "%.1f", reading.value)
        else -> reading.value.toInt().toString()
    }
}

/** القراءات اليدوية محفوظة على الجوال فقط */
object VitalsRepository {
    const val TABLE_SQL = """CREATE TABLE IF NOT EXISTS vital_readings_local (
        id TEXT PRIMARY KEY NOT NULL,
        patient_id TEXT NOT NULL,
        type TEXT NOT NULL CHECK (type IN ('pressure', 'sugar', 'temperature')),
        value REAL NOT NULL,
        value2 REAL,
        context TEXT,
        measured_at TEXT NOT NULL
    )"""
    const val INDEX_SQL = "CREATE INDEX IF NOT EXISTS idx_vitals_patient_type ON vital_readings_local(patient_id, type, measured_at)"

    fun save(patientId: String, type: VitalType, value: Double, value2: Double? = null, context: String? = null): VitalReading {
        require(type != VitalType.PULSE) { "النبض يُحفظ من القياس بالكاميرا" }
        val reading = VitalReading(Ids.random(), patientId, type, value, value2, context, Iso.now())
        Db.db.insertOrThrow("vital_readings_local", null, ContentValues().apply {
            put("id", reading.id)
            put("patient_id", patientId)
            put("type", type.key)
            put("value", value)
            if (value2 == null) putNull("value2") else put("value2", value2)
            put("context", context)
            put("measured_at", reading.measuredAt)
        })
        return reading
    }

    fun recent(patientId: String, type: VitalType, limit: Int = 5): List<VitalReading> = Db.db.rawQuery(
        "SELECT id, patient_id, type, value, value2, context, measured_at FROM vital_readings_local WHERE patient_id = ? AND type = ? ORDER BY measured_at DESC LIMIT ?",
        arrayOf(patientId, type.key, limit.toString()),
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val kind = VitalType.of(c.getString(2)) ?: continue
                add(VitalReading(c.getString(0), c.getString(1), kind, c.getDouble(3), if (c.isNull(4)) null else c.getDouble(4), c.getString(5), c.getString(6)))
            }
        }
    }

    fun delete(id: String) {
        Db.db.delete("vital_readings_local", "id = ?", arrayOf(id))
    }
}
