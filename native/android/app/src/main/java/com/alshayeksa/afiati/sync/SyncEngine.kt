package com.alshayeksa.afiati.sync

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.alshayeksa.afiati.data.Db
import com.alshayeksa.afiati.data.Iso
import com.alshayeksa.afiati.data.Json
import com.alshayeksa.afiati.data.MedicationRepository
import com.alshayeksa.afiati.data.Outbox
import com.alshayeksa.afiati.data.Patient
import com.alshayeksa.afiati.data.PatientRepository
import com.alshayeksa.afiati.data.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class SyncStatus { SYNCED, OFFLINE, NOT_CONFIGURED, NO_PATIENT, FAILED }

data class SyncResult(val status: SyncStatus, val pushed: Int = 0, val pulled: Int = 0, val error: String? = null)

/**
 * المزامنة: تسجيل الجهاز مرة واحدة، ثم رفع الملف الشخصي، ثم رفع outbox، ثم سحب التغييرات بمؤشر الخادم.
 * ترتيب وقواعد نسخة Expo نفسها (lib/sync/syncEngine.ts).
 */
object SyncEngine {
    /** لا يتجاوز MAX_PUSH_OPERATIONS في worker/src/sync.ts */
    private const val PUSH_BATCH = 25
    private const val PULL_PAGE = 200
    private val mutex = Mutex()

    /** يُستدعى من أي مكان؛ مزامنة واحدة في كل وقت */
    suspend fun sync(context: Context): SyncResult = withContext(Dispatchers.IO) {
        Db.init(context)
        mutex.withLock { run(context) }
    }

    private fun run(context: Context): SyncResult {
        if (!Api.configured) return SyncResult(SyncStatus.NOT_CONFIGURED)
        val patient = PatientRepository.get() ?: return SyncResult(SyncStatus.NO_PATIENT)
        if (!Network.online(context)) return SyncResult(SyncStatus.OFFLINE)
        return try {
            ensureIdentity(patient)
            pushProfile()
            // رمز الإشعارات لا يوقف المزامنة إن تعذّر، ويُعاد في المرة القادمة
            runCatching { Push.register(context) }
            // جرعات اليوم والغد تصل الخادم قبل موعدها، فيعرف إن فاتت جرعة حتى لو لم يُفتح التطبيق
            runCatching {
                val today = java.time.LocalDate.now()
                MedicationRepository.ensureLogsForDate(patient.id, today)
                MedicationRepository.ensureLogsForDate(patient.id, today.plusDays(1))
            }
            val pushed = pushOutbox()
            val pulled = pull()
            SyncState.set(SyncState.LAST_SUCCESS, Iso.now())
            SyncResult(SyncStatus.SYNCED, pushed, pulled)
        } catch (error: ApiException) {
            SyncResult(if (error.code == "network_error") SyncStatus.OFFLINE else SyncStatus.FAILED, error = error.message)
        } catch (error: Exception) {
            SyncResult(SyncStatus.FAILED, error = error.message)
        }
    }

    /** يسجّل الجوال لدى الخادم مرة واحدة ويحفظ رمزه. يُستدعى أيضًا قبل ميزات العائلة. */
    fun ensureIdentity(patient: Patient? = PatientRepository.get()) {
        val local = patient ?: throw ApiException(0, "no_patient", "لا يوجد ملف شخصي.")
        if (SyncState.get(SyncState.DEVICE_TOKEN) != null) return
        val response = try {
            Api.request(
                "/v1/register", "POST", auth = false,
                body = JSONObject()
                    .put("profile_id", local.id)
                    .put("full_name", local.fullName)
                    .put("phone", local.phone)
                    .put("email", local.email ?: JSONObject.NULL)
                    .put("gender", local.gender ?: JSONObject.NULL)
                    .put("platform", "android"),
            )
        } catch (error: ApiException) {
            if (error.code == "profile_exists") throw ApiException(409, error.code, "هذا الملف مسجل مسبقًا على جهاز آخر.")
            throw error
        }
        SyncState.set(SyncState.DEVICE_TOKEN, response.getString("token"))
        // الخادم استلم هذه النسخة من الملف عند التسجيل
        SyncState.set(SyncState.PROFILE_VERSION, local.updatedAt)
        PatientRepository.setCloudIdentity(response.getString("device_id"))
    }

    private fun pushProfile() {
        val patient = PatientRepository.get() ?: return
        if (SyncState.get(SyncState.PROFILE_VERSION) == patient.updatedAt) return
        Api.request(
            "/v1/me", "PUT",
            JSONObject().put("full_name", patient.fullName).put("phone", patient.phone).put("email", patient.email ?: JSONObject.NULL)
                .put("gender", patient.gender ?: JSONObject.NULL),
        )
        SyncState.set(SyncState.PROFILE_VERSION, patient.updatedAt)
    }

    private fun pushOutbox(): Int {
        var pushed = 0
        // كل دورة تحذف عمليات أو تزيد محاولاتها حتى تُستبعد، فالحلقة تنتهي دائمًا
        while (true) {
            val operations = Outbox.list(PUSH_BATCH)
            if (operations.isEmpty()) return pushed
            val body = JSONObject().put("operations", JSONArray().apply {
                operations.forEach { op ->
                    put(
                        JSONObject()
                            .put("operation_id", op.operationId)
                            .put("entity_type", op.entityType)
                            .put("entity_id", op.entityId)
                            .put("operation_type", op.operationType)
                            .put("payload", JSONObject(op.payload)),
                    )
                }
            })
            // أخطاء الشبكة والخادم تخرج من هنا وتبقى العمليات للمحاولة القادمة
            val results = Json.objects(Api.request("/v1/sync/push", "POST", body).optJSONArray("results"))
            for (result in results) {
                val id = result.getString("operation_id")
                if (result.optString("status") == "applied") {
                    Outbox.remove(id)
                    pushed++
                } else {
                    Outbox.fail(id, result.optString("error", "rejected"))
                }
            }
            if (operations.size < PUSH_BATCH) return pushed
        }
    }

    private fun pull(): Int {
        var medicationsAfter = SyncState.get(SyncState.PULL_MEDICATIONS)?.toLongOrNull() ?: 0L
        var logsAfter = SyncState.get(SyncState.PULL_LOGS)?.toLongOrNull() ?: 0L
        var pulled = 0
        while (true) {
            val page = Api.request("/v1/sync/pull?medications_after=$medicationsAfter&logs_after=$logsAfter&limit=$PULL_PAGE")
            val medications = Json.objects(page.optJSONArray("medications")).map(Json::parseMedication)
            val logs = Json.objects(page.optJSONArray("logs")).map(Json::parseLog)
            MedicationRepository.applyRemote(medications, logs)
            // المؤشر يُحفظ بعد حفظ الصفحة محليًا فقط
            val cursor = page.getJSONObject("cursor")
            medicationsAfter = cursor.getLong("medications_after")
            logsAfter = cursor.getLong("logs_after")
            SyncState.set(SyncState.PULL_MEDICATIONS, medicationsAfter.toString())
            SyncState.set(SyncState.PULL_LOGS, logsAfter.toString())
            pulled += medications.size + logs.size
            if (!page.optBoolean("has_more")) return pulled
        }
    }
}

/**
 * مزامنة في الخلفية عبر JobScheduler: دورية كل 15 دقيقة، وفورية عند وجود تغييرات.
 * النظام يؤجل المهمة حتى يتوفر الإنترنت، فتُرفع العمليات المعلقة عند عودة الشبكة تلقائيًا.
 */
class SyncJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            val result = SyncEngine.sync(applicationContext)
            jobFinished(params, result.status == SyncStatus.FAILED || result.status == SyncStatus.OFFLINE)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val PERIODIC_ID = 101
        private const val SOON_ID = 102

        fun schedulePeriodic(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(PERIODIC_ID) != null) return
            scheduler.schedule(
                JobInfo.Builder(PERIODIC_ID, ComponentName(context, SyncJobService::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(15 * 60 * 1000L)
                    .setPersisted(true)
                    .build(),
            )
        }

        /** مزامنة قريبة متى توفر الإنترنت */
        fun requestSoon(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            scheduler.schedule(
                JobInfo.Builder(SOON_ID, ComponentName(context, SyncJobService::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                    .setPersisted(true)
                    .build(),
            )
        }
    }
}
