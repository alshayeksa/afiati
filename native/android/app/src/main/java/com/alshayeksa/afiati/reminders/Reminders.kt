package com.alshayeksa.afiati.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.alshayeksa.afiati.MainActivity
import com.alshayeksa.afiati.R
import com.alshayeksa.afiati.data.Db
import com.alshayeksa.afiati.data.MedicationRepository
import com.alshayeksa.afiati.data.PatientRepository
import com.alshayeksa.afiati.data.Schedule
import com.alshayeksa.afiati.sync.SyncEngine
import com.alshayeksa.afiati.sync.SyncJobService
import com.alshayeksa.afiati.sync.SyncStatus
import com.alshayeksa.afiati.ui.AppModel
import com.alshayeksa.afiati.ui.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * تذكيرات الأدوية المحلية: منبّه لكل وقت دواء، يعمل بلا إنترنت وبعد إعادة تشغيل الجوال.
 * الإشعار فيه زر «أخذتها» يسجّل الجرعة مباشرة، وزر «ذكّرني بعد 10 دقائق».
 */
object Reminders {
    const val CHANNEL = "medication-reminders"
    const val FAMILY_CHANNEL = "family"
    const val HELP_CHANNEL = "help"
    private const val PREFS = "reminders"
    private const val KEY_CODES = "codes"
    const val SNOOZE_MINUTES = 10L

    const val ACTION_FIRE = "com.alshayeksa.afiati.REMINDER_FIRE"
    const val ACTION_TAKE = "com.alshayeksa.afiati.REMINDER_TAKE"
    const val ACTION_SNOOZE = "com.alshayeksa.afiati.REMINDER_SNOOZE"
    const val EXTRA_MEDICATION = "medication_id"
    const val EXTRA_TIME = "time"
    const val EXTRA_SCHEDULED_AT = "scheduled_at"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "مواعيد الأدوية", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "تذكير بموعد كل دواء"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
        }
        val family = NotificationChannel(FAMILY_CHANNEL, "تنبيهات العائلة", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "طلبات المتابعة والتذكيرات من أفراد العائلة"
            enableVibration(true)
        }
        // طلبات المساعدة: صوت المنبّه واهتزاز طويل، ويتخطى «عدم الإزعاج» إن سمح المستخدم بذلك
        val help = NotificationChannel(HELP_CHANNEL, "طلبات المساعدة العاجلة", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "حين يضغط قريبك زر المساعدة، وحين يرد أحد على طلبك"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 300, 800, 300, 800)
            setBypassDnd(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannels(listOf(channel, family, help))
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    private fun code(medicationId: String, time: String) = "$medicationId|$time".hashCode()

    /** يعيد جدولة كل التذكيرات من قاعدة البيانات. يُستدعى بعد أي تغيير في الأدوية وعند تشغيل الجوال. */
    fun reschedule(context: Context) {
        Db.init(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        val patient = PatientRepository.get()
        val medications = if (patient == null) emptyList() else MedicationRepository.list(patient.id)
        val now = Instant.now()
        val scheduled = mutableSetOf<Int>()
        for (medication in medications) {
            for (time in Schedule.timesFor(medication.repeatType, medication.times)) {
                val next = Schedule.nextOccurrence(medication.repeatType, medication.repeatDays, time, now) ?: continue
                val requestCode = code(medication.id, time)
                set(context, alarms, next.toEpochMilli(), fireIntent(context, requestCode, medication.id, time, null))
                scheduled += requestCode
            }
        }
        for (stale in old - scheduled) {
            PendingIntent.getBroadcast(
                context, stale, Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let { alarms.cancel(it); it.cancel() }
        }
        prefs.edit().putStringSet(KEY_CODES, scheduled.map { it.toString() }.toSet()).apply()
    }

    private fun fireIntent(context: Context, requestCode: Int, medicationId: String, time: String, scheduledAt: String?): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)
                .putExtra(EXTRA_MEDICATION, medicationId).putExtra(EXTRA_TIME, time).putExtra(EXTRA_SCHEDULED_AT, scheduledAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun set(context: Context, alarms: AlarmManager, atMillis: Long, intent: PendingIntent) {
        if (canScheduleExact(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        } else {
            // بلا إذن المنبّه الدقيق: نافذة 10 دقائق، والنظام يختار اللحظة
            alarms.setWindow(AlarmManager.RTC_WAKEUP, atMillis, 10 * 60 * 1000L, intent)
        }
    }

    fun snooze(context: Context, medicationId: String, time: String, scheduledAt: String) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val requestCode = code(medicationId, time) xor 0x5A5A5A5A
        set(context, alarms, System.currentTimeMillis() + SNOOZE_MINUTES * 60 * 1000, fireIntent(context, requestCode, medicationId, time, scheduledAt))
    }

    fun notificationId(medicationId: String, scheduledAt: String) = "$medicationId|$scheduledAt".hashCode()

    /**
     * بعد كل جرعة أو علبة جديدة: إن بلغ الدواء حد التنبيه يظهر إشعار مرة واحدة،
     * ولا يتكرر حتى يُضاف مخزون فوق الحد ثم ينخفض من جديد. يعمل بلا إنترنت.
     */
    fun checkStock(context: Context, patientId: String, medicationId: String) {
        Db.init(context)
        val medication = MedicationRepository.list(patientId).firstOrNull { it.id == medicationId } ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "stock_alerted_$medicationId"
        if (!medication.lowStock) {
            prefs.edit().remove(key).apply()
            context.getSystemService(NotificationManager::class.java)?.cancel(stockNotificationId(medicationId))
            return
        }
        if (prefs.getBoolean(key, false)) return
        prefs.edit().putBoolean(key, true).apply()
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val count = medication.stockCount ?: 0
        val open = PendingIntent.getActivity(
            context, stockNotificationId(medicationId),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (count <= 0) "نفد ${medication.name}" else "${medication.name} قارب على النفاد"
        val body = if (count <= 0) "اطلب علبة جديدة، ثم اضغط «اشتريت علبة» في عافيتي." else "باقي $count. اطلب علبة جديدة قريبًا، ثم اضغط «اشتريت علبة» في عافيتي."
        context.getSystemService(NotificationManager::class.java)?.notify(
            stockNotificationId(medicationId),
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_pill)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun stockNotificationId(medicationId: String) = "stock|$medicationId".hashCode()

    /** إشعار موعد الدواء */
    fun show(context: Context, medicationId: String, time: String, scheduledAt: String) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        Db.init(context)
        val patient = PatientRepository.get() ?: return
        val medication = MedicationRepository.list(patient.id).firstOrNull { it.id == medicationId } ?: return
        val id = notificationId(medicationId, scheduledAt)
        fun action(action: String, code: Int) = PendingIntent.getBroadcast(
            context, code,
            Intent(context, ReminderReceiver::class.java).setAction(action)
                .putExtra(EXTRA_MEDICATION, medicationId).putExtra(EXTRA_TIME, time).putExtra(EXTRA_SCHEDULED_AT, scheduledAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle("حان موعد ${medication.name}")
            .setContentText("${Format.dose(medication.doseAmount, medication.doseUnit)} · ${Format.time(time)}")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(null, "أخذتها ✓", action(ACTION_TAKE, id + 1)).build())
            .addAction(Notification.Action.Builder(null, "ذكّرني بعد 10 دقائق", action(ACTION_SNOOZE, id + 2)).build())
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getStringExtra(Reminders.EXTRA_MEDICATION) ?: return
        val time = intent.getStringExtra(Reminders.EXTRA_TIME) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    Reminders.ACTION_FIRE -> {
                        // موعد اليوم لهذا الوقت، أو موعد التأجيل المحفوظ
                        val scheduledAt = intent.getStringExtra(Reminders.EXTRA_SCHEDULED_AT)
                            ?: Schedule.scheduledAt(java.time.LocalDate.now(), time)
                        if (scheduledAt != null && !alreadyTaken(context, medicationId, scheduledAt)) {
                            Reminders.show(context, medicationId, time, scheduledAt)
                        }
                        Reminders.reschedule(context)
                    }
                    Reminders.ACTION_TAKE -> {
                        val scheduledAt = intent.getStringExtra(Reminders.EXTRA_SCHEDULED_AT) ?: return@launch
                        Db.init(context)
                        val patient = PatientRepository.get()
                        val changed = patient != null && MedicationRepository.markTakenFor(patient.id, medicationId, scheduledAt)
                        context.getSystemService(NotificationManager::class.java)?.cancel(Reminders.notificationId(medicationId, scheduledAt))
                        AppModel.refreshIfRunning()
                        if (changed && patient != null) {
                            Reminders.checkStock(context, patient.id, medicationId)
                            // ترفع الآن إن أمكن، فلا يصل العائلة تنبيه جرعة فائتة وهي مأخوذة
                            val synced = withTimeoutOrNull(8_000) { SyncEngine.sync(context) }
                            if (synced?.status != SyncStatus.SYNCED) SyncJobService.requestSoon(context)
                        }
                    }
                    Reminders.ACTION_SNOOZE -> {
                        val scheduledAt = intent.getStringExtra(Reminders.EXTRA_SCHEDULED_AT) ?: return@launch
                        context.getSystemService(NotificationManager::class.java)?.cancel(Reminders.notificationId(medicationId, scheduledAt))
                        Reminders.snooze(context, medicationId, time, scheduledAt)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun alreadyTaken(context: Context, medicationId: String, scheduledAt: String): Boolean {
        Db.init(context)
        return Db.db.rawQuery(
            "SELECT 1 FROM medication_logs_local WHERE medication_id = ? AND scheduled_at = ? AND status = 'taken'",
            arrayOf(medicationId, scheduledAt),
        ).use { it.moveToFirst() }
    }
}

/** بعد تشغيل الجوال أو تحديث التطبيق أو تغيير الوقت: تُعاد جدولة التذكيرات */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Reminders.reschedule(context)
                SyncJobService.schedulePeriodic(context)
            } finally {
                pending.finish()
            }
        }
    }
}
