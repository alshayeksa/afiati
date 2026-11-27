package com.alshayeksa.afiati.sync

import android.content.Context
import com.alshayeksa.afiati.data.DoseLog
import com.alshayeksa.afiati.data.Gender
import com.alshayeksa.afiati.data.Iso
import com.alshayeksa.afiati.data.Json
import com.alshayeksa.afiati.data.nullableString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant

object Privacy {
    const val FULL = "full"
    const val STATUS_ONLY = "status_only"
    const val ALERTS_ONLY = "alerts_only"

    fun title(level: String) = when (level) {
        FULL -> "كل شيء"
        STATUS_ONLY -> "أخذت أم لا فقط"
        else -> "تنبيهات التأخير فقط"
    }

    fun body(level: String) = when (level) {
        FULL -> "يرى أسماء الأدوية والجرعات ومواعيدها"
        STATUS_ONLY -> "يرى هل أخذت جرعاتك، بدون أسماء الأدوية"
        else -> "لا يرى التفاصيل، ويصله تنبيه عند فوات جرعة"
    }
}

data class FamilyLink(val id: String, val patientId: String, val caregiverId: String, val privacyLevel: String, val status: String, val name: String)

data class FamilyOverview(val followers: List<FamilyLink>, val following: List<FamilyLink>, val pending: List<FamilyLink>)

data class PatientMedication(val id: String, val name: String, val doseAmount: Double, val doseUnit: String)

data class PatientDay(val patientName: String?, val privacyLevel: String, val medications: List<PatientMedication>, val logs: List<DoseLog>)

data class NotificationItem(val id: String, val type: String, val title: String, val body: String, val read: Boolean, val createdAt: String)

data class Invitation(val code: String, val expiresAt: String)

data class JoinResult(val status: String, val patientName: String)

class FamilyException(message: String) : Exception(message)

/** طلب مساعدة: من الكبير لعائلته */
data class HelpRequest(
    val id: String,
    val patientId: String,
    val patientName: String,
    /** رقم الكبير، يصل لعائلته فقط ليتصلوا به */
    val patientPhone: String?,
    val patientGender: String?,
    val status: String,
    val message: String?,
    val acknowledgedBy: String?,
    val acknowledgedByName: String?,
    val acknowledgedByGender: String?,
    /** للعائلة فقط: موقع الكبير ورابطه على خرائط Google */
    val latitude: Double?,
    val longitude: Double?,
    val accuracy: Double?,
    val mapUrl: String?,
    /** للكبير: هل وصل موقعه لعائلته */
    val locationShared: Boolean,
    val createdAt: String,
) {
    val answered get() = status == "acknowledged"
}

data class HelpSent(val help: HelpRequest, val notified: Int)

data class ActiveHelp(val mine: HelpRequest?, val family: List<HelpRequest>)

/** ميزات العائلة على الخادم. تحتاج إنترنت، وكل خطأ يصل برسالة عربية واضحة. */
object FamilyApi {
    const val OFFLINE_MESSAGE = "خاصية العائلة تحتاج اتصالًا بالإنترنت. اتصل بالشبكة لمتابعة ذويك."

    private val messages = mapOf(
        "not_configured" to "خدمة العائلة غير مُعدّة بعد.",
        "network_error" to "تعذر الاتصال بخدمة العائلة. تحقق من الإنترنت وحاول مرة أخرى.",
        "invalid_code" to "الرمز غير صحيح أو منتهي الصلاحية.",
        "self_follow" to "هذا رمزك أنت. أرسله لأحد أفراد عائلتك ليتابعك.",
        "forbidden" to "لا تملك صلاحية متابعة هذا الشخص.",
        "request_not_found" to "هذا الطلب لم يعد بانتظار الموافقة.",
        "profile_exists" to "هذا الملف مسجل مسبقًا على جهاز آخر.",
        "help_not_found" to "طلب المساعدة لم يعد موجودًا.",
    )

    private suspend fun call(
        context: Context, path: String, method: String = "GET", body: JSONObject? = null, rateLimitMessage: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        if (!Network.online(context)) throw FamilyException(OFFLINE_MESSAGE)
        try {
            SyncEngine.ensureIdentity()
            Api.request(path, method, body)
        } catch (error: ApiException) {
            if (error.status == 429 && rateLimitMessage != null) throw FamilyException(rateLimitMessage)
            throw FamilyException(messages[error.code] ?: "تعذر إكمال الطلب. حاول مرة أخرى.")
        }
    }

    private fun link(o: JSONObject) = FamilyLink(
        id = o.getString("id"),
        patientId = o.getString("patient_id"),
        caregiverId = o.getString("caregiver_id"),
        privacyLevel = o.optString("privacy_level", Privacy.FULL),
        status = o.optString("status"),
        name = o.optJSONObject("profiles")?.nullableString("full_name") ?: "فرد من العائلة",
    )

    suspend fun overview(context: Context): FamilyOverview {
        val o = call(context, "/v1/family")
        return FamilyOverview(
            Json.objects(o.optJSONArray("followers")).map(::link),
            Json.objects(o.optJSONArray("following")).map(::link),
            Json.objects(o.optJSONArray("pending")).map(::link),
        )
    }

    suspend fun createInvitation(context: Context): Invitation {
        val o = call(context, "/v1/family/invitations", "POST")
        return Invitation(o.getString("code"), o.getString("expires_at"))
    }

    suspend fun join(context: Context, code: String): JoinResult {
        val o = call(context, "/v1/family/join", "POST", JSONObject().put("code", code), "محاولات كثيرة. انتظر ساعة ثم حاول مرة أخرى.")
        return JoinResult(o.optString("status", "pending"), o.optString("patient_name"))
    }

    suspend fun respond(context: Context, caregiverId: String, approve: Boolean, privacyLevel: String) {
        call(
            context, "/v1/family/respond", "POST",
            JSONObject().put("caregiver_id", caregiverId).put("action", if (approve) "approve" else "reject").put("privacy_level", privacyLevel),
        )
    }

    suspend fun patientDay(context: Context, patientId: String, from: Instant, to: Instant): PatientDay {
        val query = "from=${URLEncoder.encode(Iso.format(from), "UTF-8")}&to=${URLEncoder.encode(Iso.format(to), "UTF-8")}"
        val o = call(context, "/v1/family/patients/${URLEncoder.encode(patientId, "UTF-8")}/today?$query")
        val medications = Json.objects(o.optJSONArray("medications")).map {
            PatientMedication(it.getString("id"), it.optString("name"), it.optDouble("dose_amount", 1.0), it.optString("dose_unit"))
        }
        val byId = medications.associateBy { it.id }
        val logs = Json.objects(o.optJSONArray("logs")).map(Json::parseLog).map { log ->
            val medication = byId[log.medicationId]
            log.copy(medicationName = medication?.name ?: "", doseAmount = medication?.doseAmount ?: 1.0, doseUnit = medication?.doseUnit ?: "")
        }
        return PatientDay(o.nullableString("patient_name"), o.optString("privacy_level", Privacy.FULL), medications, logs)
    }

    suspend fun remind(context: Context, patientId: String) {
        call(
            context, "/v1/family/patients/${URLEncoder.encode(patientId, "UTF-8")}/remind", "POST",
            rateLimitMessage = "أرسلت تذكيرًا قبل قليل. يمكنك الإرسال مرة أخرى بعد 10 دقائق.",
        )
    }

    private fun help(o: JSONObject) = HelpRequest(
        id = o.getString("id"),
        patientId = o.getString("patient_id"),
        patientName = o.optString("patient_name"),
        patientPhone = o.nullableString("patient_phone"),
        patientGender = Gender.of(o.nullableString("patient_gender")),
        status = o.optString("status", "open"),
        message = o.nullableString("message"),
        acknowledgedBy = o.nullableString("acknowledged_by"),
        acknowledgedByName = o.nullableString("acknowledged_by_name"),
        acknowledgedByGender = Gender.of(o.nullableString("acknowledged_by_gender")),
        latitude = if (o.isNull("latitude")) null else o.optDouble("latitude").takeIf { !it.isNaN() },
        longitude = if (o.isNull("longitude")) null else o.optDouble("longitude").takeIf { !it.isNaN() },
        accuracy = if (o.isNull("location_accuracy")) null else o.optDouble("location_accuracy").takeIf { !it.isNaN() },
        mapUrl = o.nullableString("map_url"),
        locationShared = o.optBoolean("location_shared", false),
        createdAt = o.optString("created_at"),
    )

    private fun JSONObject.putPoint(point: HelpPoint?): JSONObject {
        if (point == null) return this
        put("latitude", point.latitude).put("longitude", point.longitude)
        point.accuracy?.let { put("accuracy", it.toDouble()) }
        return this
    }

    suspend fun requestHelp(context: Context, message: String? = null, point: HelpPoint? = null): HelpSent {
        val o = call(
            context, "/v1/help", "POST", JSONObject().put("message", message ?: JSONObject.NULL).putPoint(point),
            "أرسلت طلبات مساعدة كثيرة. اتصل بعائلتك أو بالإسعاف مباشرة.",
        )
        return HelpSent(help(o.getJSONObject("help")), o.optInt("notified"))
    }

    suspend fun acknowledgeHelp(context: Context, id: String): HelpRequest =
        help(call(context, "/v1/help/$id/acknowledge", "POST").getJSONObject("help"))

    /** الموقع إن وصل بعد إرسال الطلب */
    suspend fun sendHelpLocation(context: Context, id: String, point: HelpPoint): HelpRequest =
        help(call(context, "/v1/help/$id/location", "POST", JSONObject().putPoint(point)).getJSONObject("help"))

    suspend fun resolveHelp(context: Context, id: String): HelpRequest =
        help(call(context, "/v1/help/$id/resolve", "POST").getJSONObject("help"))

    suspend fun activeHelp(context: Context): ActiveHelp {
        val o = call(context, "/v1/help/active")
        return ActiveHelp(o.optJSONObject("mine")?.let(::help), Json.objects(o.optJSONArray("family")).map(::help))
    }

    suspend fun notifications(context: Context): List<NotificationItem> =
        Json.objects(call(context, "/v1/notifications").optJSONArray("notifications")).map {
            NotificationItem(it.getString("id"), it.optString("type"), it.optString("title"), it.optString("body"), it.optBoolean("read"), it.optString("created_at"))
        }

    suspend fun markAllRead(context: Context) {
        call(context, "/v1/notifications/read-all", "POST")
    }
}
