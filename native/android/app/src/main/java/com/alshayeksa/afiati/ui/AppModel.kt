package com.alshayeksa.afiati.ui

import com.alshayeksa.afiati.Lang
import com.alshayeksa.afiati.tr
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alshayeksa.afiati.data.Db
import com.alshayeksa.afiati.data.DoseLog
import com.alshayeksa.afiati.data.DoseStatus
import com.alshayeksa.afiati.data.Iso
import com.alshayeksa.afiati.data.Medication
import com.alshayeksa.afiati.data.MedicationDraft
import com.alshayeksa.afiati.data.MedicationRepository
import com.alshayeksa.afiati.data.Outbox
import com.alshayeksa.afiati.data.Patient
import com.alshayeksa.afiati.data.PatientRepository
import com.alshayeksa.afiati.data.Schedule
import com.alshayeksa.afiati.data.SyncState
import com.alshayeksa.afiati.data.Gender
import com.alshayeksa.afiati.reminders.Reminders
import com.alshayeksa.afiati.sync.HelpPoint
import com.alshayeksa.afiati.sync.FamilyApi
import com.alshayeksa.afiati.sync.FamilyOverview
import com.alshayeksa.afiati.sync.HelpRequest
import com.alshayeksa.afiati.sync.HelpSent
import com.alshayeksa.afiati.sync.NotificationItem
import com.alshayeksa.afiati.sync.SyncEngine
import com.alshayeksa.afiati.sync.SyncJobService
import com.alshayeksa.afiati.sync.SyncStatus
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** حالة المزامنة كما تظهر في الواجهة */
data class SyncUi(val status: SyncStatus? = null, val pending: Int = 0, val lastSync: String? = null, val error: String? = null, val running: Boolean = false)

/**
 * حالة التطبيق كلها في مكان واحد: الملف والأدوية والجرعات والعائلة والمزامنة.
 * القراءة والكتابة على قاعدة البيانات في خيط خلفي، والواجهة تتحدث تلقائيًا لأن القيم حالات Compose.
 */
object AppModel {
    /** نافذة التراجع بعد تسجيل جرعة */
    const val UNDO_SECONDS = 5L

    private lateinit var app: Context
    /** خطأ غير متوقع لا يوقف التطبيق: يُسجَّل ويظهر للمستخدم رسالة هادئة، والبيانات المحفوظة لا تتأثر */
    private val errors = CoroutineExceptionHandler { _, error ->
        Log.e("Afiati", "unexpected error", error)
        toast = tr("حدث خطأ غير متوقع. حاول مرة أخرى.", "Something unexpected happened. Try again.")
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + errors)
    @Volatile private var running = false

    var loaded by mutableStateOf(false); private set
    var patient by mutableStateOf<Patient?>(null); private set
    var medications by mutableStateOf<List<Medication>>(emptyList()); private set
    var todayLogs by mutableStateOf<List<DoseLog>>(emptyList()); private set
    /** آخر 30 يومًا، للسجل والإحصاءات */
    var historyLogs by mutableStateOf<List<DoseLog>>(emptyList()); private set
    var today by mutableStateOf(LocalDate.now()); private set

    /** جرعة سُجّلت للتو وما زال يمكن التراجع عنها */
    var pendingTake by mutableStateOf<DoseLog?>(null); private set
    private var takeJob: Job? = null

    var sync by mutableStateOf(SyncUi()); private set

    var family by mutableStateOf<FamilyOverview?>(null); private set
    var familyError by mutableStateOf<String?>(null); private set
    var familyLoading by mutableStateOf(false); private set
    var notifications by mutableStateOf<List<NotificationItem>>(emptyList()); private set
    val unreadCount get() = notifications.count { !it.read }

    /** يزيد كلما عاد التطبيق للواجهة، لإعادة قراءة الأذونات */
    var resumeTick by mutableStateOf(0); private set

    // زر المساعدة

    /** طلبي النشط إن وُجد */
    var myHelp by mutableStateOf<HelpRequest?>(null); private set
    /** طلبات مساعدة نشطة ممن أتابعهم */
    var familyHelp by mutableStateOf<List<HelpRequest>>(emptyList()); private set

    /** رقم للطوارئ يُحفظ على الجوال فقط، للاتصال المباشر ولو بلا إنترنت */
    var emergencyName by mutableStateOf(""); private set
    var emergencyPhone by mutableStateOf(""); private set

    fun loadEmergencyContact() {
        emergencyName = LocalStore.prefs.getString("emergency.name", "").orEmpty()
        emergencyPhone = LocalStore.prefs.getString("emergency.phone", "").orEmpty()
    }

    /** يغيّر لغة الواجهة: تُعاد تسمية قنوات الإشعارات، ويعرف الخادم اللغة في المزامنة القادمة لرسائل العائلة */
    fun setLanguage(context: Context, english: Boolean) {
        Lang.set(context, english)
        runCatching { Reminders.createChannel(context) }
        if (::app.isInitialized && patient != null) {
            scope.launch {
                runCatching { Reminders.reschedule(app) }
                syncNow()
            }
        }
    }

    fun saveEmergencyContact(name: String, phone: String) {
        emergencyName = name.trim()
        emergencyPhone = phone.trim()
        LocalStore.prefs.edit().putString("emergency.name", emergencyName).putString("emergency.phone", emergencyPhone).apply()
    }

    fun refreshHelp() {
        if (!::app.isInitialized || patient == null) return
        scope.launch {
            familyCall { FamilyApi.activeHelp(it) }.onSuccess {
                myHelp = it.mine
                familyHelp = it.family
            }
        }
    }

    fun refreshHelpIfRunning() {
        if (running) refreshHelp()
    }

    suspend fun sendHelp(reason: String? = null, point: HelpPoint? = null): Result<HelpSent> =
        familyCall { FamilyApi.requestHelp(it, reason, point) }.onSuccess { myHelp = it.help }

    suspend fun sendHelpLocation(id: String, point: HelpPoint): Result<HelpRequest> =
        familyCall { FamilyApi.sendHelpLocation(it, id, point) }.onSuccess { updated -> if (myHelp?.id == updated.id) myHelp = updated }

    fun resolveMyHelp(onDone: (String?) -> Unit = {}) {
        val help = myHelp ?: return onDone(null)
        scope.launch {
            familyCall { FamilyApi.resolveHelp(it, help.id) }
                .onSuccess { myHelp = null; toast = tr("طمأنّا عائلتك أنك بخير", "We told your family you're okay"); onDone(null) }
                .onFailure { onDone(it.message) }
        }
    }

    fun acknowledgeHelp(help: HelpRequest) {
        scope.launch {
            familyCall { FamilyApi.acknowledgeHelp(it, help.id) }
                .onSuccess { updated ->
                    familyHelp = familyHelp.map { if (it.id == updated.id) updated else it }
                    toast = if (updated.acknowledgedBy == patient?.id) tr("أبلغنا ${help.patientName} أنك قادم", "We told ${help.patientName} you're coming")
                    else tr("${updated.acknowledgedByName ?: "أحد أفراد العائلة"} ${Gender.pick(updated.acknowledgedByGender, "في طريقه", "في طريقها")} ${Gender.pick(help.patientGender, "إليه", "إليها")}", "${updated.acknowledgedByName ?: "A family member"} is on the way")
                }
                .onFailure { toast = it.message }
        }
    }

    /** فتح شاشة بعينها من إشعار: "family" أو "notifications" */
    var pendingOpen by mutableStateOf<String?>(null)

    /** يزيد عند فتح العائلة من إشعار طلب متابعة: تُغلق نوافذها ويظهر الطلب */
    var familyFocus by mutableStateOf(0)

    fun refreshNotificationsIfRunning() {
        if (::app.isInitialized && running && patient != null) refreshNotifications(quiet = true)
    }

    fun refreshFamilyIfRunning() {
        if (::app.isInitialized && running && patient != null) refreshFamily()
    }

    /** رسالة قصيرة تظهر أسفل الشاشة */
    var toast by mutableStateOf<String?>(null)

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        Db.init(app)
        loadEmergencyContact()
        Greetings.load()
        scope.launch { Greetings.refresh() }
        scope.launch {
            reload()
            loaded = true
            if (patient != null) afterChange(sync = true)
        }
    }

    /** يقرأ كل شيء من قاعدة البيانات */
    suspend fun reload() {
        val snapshot = withContext(Dispatchers.IO) {
            val patient = PatientRepository.get()
            val date = LocalDate.now()
            if (patient == null) return@withContext Snapshot(null, date, emptyList(), emptyList(), emptyList(), Outbox.pendingCount(), SyncState.get(SyncState.LAST_SUCCESS))
            // اليوم والأيام الستة السابقة: إن لم يُفتح التطبيق فيها تظهر جرعاتها فائتة في السجل
            for (offset in 6L downTo 0L) MedicationRepository.ensureLogsForDate(patient.id, date.minusDays(offset))
            val zone = ZoneId.systemDefault()
            val (from, to) = Schedule.dayRange(date, zone)
            val historyFrom = Iso.format(date.minusDays(29).atStartOfDay(zone).toInstant())
            Snapshot(
                patient, date,
                MedicationRepository.list(patient.id),
                MedicationRepository.logsBetween(patient.id, from, to),
                MedicationRepository.logsBetween(patient.id, historyFrom, to),
                Outbox.pendingCount(), SyncState.get(SyncState.LAST_SUCCESS),
            )
        }
        patient = snapshot.patient
        today = snapshot.date
        medications = snapshot.medications
        todayLogs = snapshot.todayLogs
        historyLogs = snapshot.historyLogs
        sync = sync.copy(pending = snapshot.pending, lastSync = snapshot.lastSync)
    }

    private data class Snapshot(
        val patient: Patient?, val date: LocalDate, val medications: List<Medication>, val todayLogs: List<DoseLog>,
        val historyLogs: List<DoseLog>, val pending: Int, val lastSync: String?,
    )

    /** بعد أي تغيير محلي: تحديث التذكيرات ورفع التغييرات */
    private fun afterChange(sync: Boolean, reminders: Boolean = true) {
        scope.launch {
            if (reminders) withContext(Dispatchers.IO) { runCatching { Reminders.reschedule(app) } }
            if (sync) syncNow()
        }
    }

    fun onForeground() {
        running = true
        resumeTick++
        if (!::app.isInitialized) return
        // إيقاف الخدمة أو تحديث إلزامي من صفحة التحكم
        scope.launch { com.alshayeksa.afiati.sync.AppControl.refresh() }
        // تحيات جديدة أُضيفت للجدول تظهر دون إعادة تشغيل التطبيق
        scope.launch { Greetings.refresh() }
        scope.launch {
            reload()
            if (patient != null) {
                afterChange(sync = true)
                refreshNotifications(quiet = true)
                refreshHelp()
                // طلب متابعة أو موافقة وصل والتطبيق في الخلفية
                if (family != null) refreshFamily()
            }
        }
    }

    fun onBackground() {
        running = false
        commitPendingTake()
    }

    /** منتصف الليل والتطبيق مفتوح: جرعات اليوم الجديد */
    fun checkDay() {
        if (::app.isInitialized && LocalDate.now() != today) scope.launch { reload(); afterChange(sync = true) }
    }

    /** إشعار «أخذتها» من خارج التطبيق: نحدّث الشاشة إن كانت مفتوحة */
    fun refreshIfRunning() {
        if (!::app.isInitialized || !running) return
        scope.launch { reload() }
    }

    fun syncNow() {
        if (sync.running) return
        sync = sync.copy(running = true)
        scope.launch {
            val result = SyncEngine.sync(app)
            sync = sync.copy(status = result.status, error = result.error, running = false)
            // رفض الخادم (خدمة متوقفة أو نسخة قديمة): نقرأ الحالة فتظهر الشاشة المناسبة
            if (result.status == SyncStatus.FAILED) com.alshayeksa.afiati.sync.AppControl.refresh()
            if (result.status != SyncStatus.SYNCED && result.status != SyncStatus.NO_PATIENT) SyncJobService.requestSoon(app)
            reload()
        }
    }

    // الملف الشخصي

    fun createPatient(name: String, phone: String, email: String?, gender: String?) {
        scope.launch {
            withContext(Dispatchers.IO) { PatientRepository.create(name, phone, email, gender) }
            reload()
            SyncJobService.schedulePeriodic(app)
            afterChange(sync = true)
        }
    }

    fun updatePatient(name: String, phone: String, email: String?, gender: String?) {
        scope.launch {
            withContext(Dispatchers.IO) { PatientRepository.update(name, phone, email, gender) }
            reload()
            afterChange(sync = true, reminders = false)
            toast = tr("حُفظت بياناتك", "Your details were saved")
        }
    }

    // الأدوية

    suspend fun findDuplicate(draft: MedicationDraft): Medication? {
        val id = patient?.id ?: return null
        return withContext(Dispatchers.IO) { MedicationRepository.findDuplicate(id, draft) }
    }

    fun addMedication(draft: MedicationDraft) {
        val id = patient?.id ?: return
        scope.launch {
            withContext(Dispatchers.IO) { MedicationRepository.add(id, draft) }
            reload()
            afterChange(sync = true)
            toast = tr("أُضيف ${draft.name} وستصلك تذكيراته", "${draft.name} added; you'll get its reminders")
        }
    }

    fun editMedication(medication: Medication, draft: MedicationDraft) {
        val id = patient?.id ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                MedicationRepository.edit(id, medication.id, draft)
                Reminders.checkStock(app, id, medication.id)
            }
            reload()
            afterChange(sync = true)
            toast = tr("حُفظت تعديلات ${draft.name}", "${draft.name} changes saved")
        }
    }

    /**
     * حذف الحساب: من الخادم أولًا (كل البيانات وروابط العائلة)، ثم من الجوال، فيعود التطبيق لشاشة البداية.
     * onDone يستلم رسالة الخطأ، أو null عند النجاح.
     */
    fun deleteAccount(onDone: (String?) -> Unit) {
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                val result = runCatching { com.alshayeksa.afiati.sync.Api.request("/v1/me", method = "DELETE") }
                val failure = result.exceptionOrNull() as? com.alshayeksa.afiati.sync.ApiException
                when {
                    result.isSuccess -> null
                    // لم يُسجَّل في الخادم بعد، أو حُذف من قبل: يكفي الحذف من الجوال
                    failure?.status == 401 -> null
                    failure?.status == 0 -> tr("تحتاج اتصالًا بالإنترنت لحذف حسابك. اتصل ثم حاول مرة أخرى.", "You need an internet connection to delete your account. Connect and try again.")
                    else -> failure?.message ?: tr("تعذر حذف الحساب. حاول مرة أخرى.", "Couldn't delete the account. Try again.")
                }
            }
            if (error != null) return@launch onDone(error)
            withContext(Dispatchers.IO) {
                Db.tx { db ->
                    listOf("medication_logs_local", "medications_local", "outbox", "heart_rate_local", "vital_readings_local", "sync_state", "patients_local")
                        .forEach { table -> runCatching { db.delete(table, null, null) } }
                }
                runCatching { Reminders.reschedule(app) }
                runCatching { androidx.core.app.NotificationManagerCompat.from(app).cancelAll() }
            }
            LocalStore.prefs.edit().remove("emergency.name").remove("emergency.phone").apply()
            emergencyName = ""
            emergencyPhone = ""
            reload()
            onDone(null)
        }
    }

    fun stopMedication(medication: Medication) {
        val id = patient?.id ?: return
        scope.launch {
            withContext(Dispatchers.IO) { MedicationRepository.deactivate(id, medication.id) }
            reload()
            afterChange(sync = true)
            toast = tr("أُوقف ${medication.name}", "${medication.name} stopped")
        }
    }

    fun refill(medication: Medication, amount: Int) {
        val id = patient?.id ?: return
        if (amount <= 0) return
        scope.launch {
            withContext(Dispatchers.IO) {
                MedicationRepository.refill(id, medication.id, amount)
                Reminders.checkStock(app, id, medication.id)
            }
            reload()
            afterChange(sync = true, reminders = false)
            toast = tr("أُضيف $amount إلى مخزون ${medication.name}", "Added $amount to ${medication.name} stock")
        }
    }

    // الجرعات

    fun isTaken(log: DoseLog) = log.status == DoseStatus.TAKEN || pendingTake?.id == log.id

    /** يسجّل الجرعة بعد ثوانٍ قليلة، ويمكن التراجع خلالها */
    fun takeDose(log: DoseLog) {
        if (log.status == DoseStatus.TAKEN) return
        commitPendingTake()
        pendingTake = log
        takeJob = scope.launch {
            delay(UNDO_SECONDS * 1000)
            commitPendingTake()
        }
    }

    fun undoTake() {
        takeJob?.cancel()
        takeJob = null
        pendingTake = null
    }

    private fun commitPendingTake() {
        val log = pendingTake ?: return
        val id = patient?.id ?: return
        takeJob?.cancel()
        takeJob = null
        pendingTake = null
        scope.launch {
            withContext(Dispatchers.IO) {
                if (MedicationRepository.markTaken(id, log.id)) Reminders.checkStock(app, id, log.medicationId)
            }
            app.getSystemService(android.app.NotificationManager::class.java)?.cancel(Reminders.notificationId(log.medicationId, log.scheduledAt))
            reload()
            afterChange(sync = true, reminders = false)
        }
    }

    /** «لم آخذها»: تراجع عن جرعة سُجّلت بالخطأ، في أي وقت */
    fun untakeDose(log: DoseLog) {
        if (pendingTake?.id == log.id) return undoTake()
        val id = patient?.id ?: return
        scope.launch {
            val changed = withContext(Dispatchers.IO) {
                MedicationRepository.untake(id, log.id).also { if (it) Reminders.checkStock(app, id, log.medicationId) }
            }
            reload()
            if (changed) {
                afterChange(sync = true)
                toast = tr("أُلغي تسجيل جرعة ${log.medicationName}", "${log.medicationName} dose unmarked")
            }
        }
    }

    /** هل الجرعة بعيدة عن موعدها (أكثر من ساعة قبله)؟ نسأل قبل تسجيلها */
    fun isEarly(log: DoseLog, now: Instant = Instant.now()): Boolean =
        runCatching { Iso.parse(log.scheduledAt).isAfter(now.plusSeconds(3600)) }.getOrDefault(false)

    // العائلة

    fun refreshFamily() {
        if (familyLoading) return
        familyLoading = true
        scope.launch {
            try {
                family = FamilyApi.overview(app)
                familyError = null
            } catch (error: Exception) {
                familyError = error.message ?: tr("تعذر تحميل العائلة.", "Couldn't load family.")
            } finally {
                familyLoading = false
            }
        }
    }

    fun refreshNotifications(quiet: Boolean = false) {
        scope.launch {
            try {
                notifications = FamilyApi.notifications(app)
            } catch (error: Exception) {
                if (!quiet) toast = error.message
            }
        }
    }

    fun markAllRead() {
        scope.launch {
            try {
                FamilyApi.markAllRead(app)
                notifications = notifications.map { it.copy(read = true) }
            } catch (error: Exception) {
                toast = error.message
            }
        }
    }

    /** ينفّذ طلب عائلة ويعيد رسالة الخطأ إن فشل */
    suspend fun <T> familyCall(block: suspend (Context) -> T): Result<T> =
        try {
            Result.success(block(app))
        } catch (error: Exception) {
            Result.failure(error)
        }

    fun respond(caregiverId: String, approve: Boolean, level: String, onDone: (String?) -> Unit) {
        scope.launch {
            val result = familyCall { FamilyApi.respond(it, caregiverId, approve, level) }
            onDone(result.exceptionOrNull()?.message)
            if (result.isSuccess) refreshFamily()
        }
    }
}
