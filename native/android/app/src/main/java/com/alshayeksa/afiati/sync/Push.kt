package com.alshayeksa.afiati.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.alshayeksa.afiati.MainActivity
import com.alshayeksa.afiati.R
import com.alshayeksa.afiati.data.Db
import com.alshayeksa.afiati.data.PatientRepository
import com.alshayeksa.afiati.data.SyncState
import com.alshayeksa.afiati.reminders.Reminders
import com.alshayeksa.afiati.ui.AppModel
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * إشعارات العائلة الفورية عبر Firebase (FCM): طلب متابعة، موافقة، تذكير من قريب.
 * الخادم يرسل رسالة بيانات، والتطبيق يعرضها في قناته حتى لو كان مغلقًا.
 */
object Push {
    private const val TAG = "AfiatiPush"
    const val SENT_TOKEN = "push_token_sent"

    /** هل في هذا البناء ملف إعداد Firebase؟ بدونه يعمل التطبيق بلا إشعارات فورية. */
    fun available(context: Context): Boolean = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    /** رمز هذا الجهاز من Firebase. يُستدعى من خيط خلفي فقط. */
    fun token(context: Context): String? {
        if (!available(context)) return null
        return try {
            Tasks.await(FirebaseMessaging.getInstance().token, 20, TimeUnit.SECONDS)?.takeIf { it.isNotEmpty() }
        } catch (error: Exception) {
            Log.w(TAG, "device token failed: ${error.message}")
            null
        }
    }

    /** يسجّل الرمز في الخادم إن تغيّر. جزء من المزامنة، بعد تسجيل الجهاز. */
    fun register(context: Context) {
        val token = token(context) ?: return
        if (SyncState.get(SENT_TOKEN) == token) return
        Api.request("/v1/me/push-token", "PUT", JSONObject().put("push_token", token))
        SyncState.set(SENT_TOKEN, token)
        // السجل يذكر الطول فقط، لا الرمز نفسه
        Log.i(TAG, "push token registered (${token.length} chars)")
    }

    fun show(context: Context, data: Map<String, String>) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val title = data["title"] ?: return
        val body = data["body"].orEmpty()
        val channel = when (data["channel"]) {
            Reminders.CHANNEL -> Reminders.CHANNEL
            Reminders.HELP_CHANNEL -> Reminders.HELP_CHANNEL
            else -> Reminders.FAMILY_CHANNEL
        }
        val type = data["type"].orEmpty()
        val helpId = data["help_id"]
        // تنبيهات الطلب الواحد تحل محل بعضها بدل أن تتراكم
        val id = if (helpId != null) helpNotificationId(helpId) else (type + title + body + System.currentTimeMillis() / 60_000).hashCode()
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(
                    MainActivity.EXTRA_OPEN,
                    when {
                        type == "invite_request" -> MainActivity.OPEN_FAMILY
                        type.startsWith("help") -> MainActivity.OPEN_HELP
                        // جرعة فائتة أو دواء قارب على النفاد: يفتح يوم قريبك وزر التذكير
                        (type == "missed_dose" || type == "low_stock") && !data["patient_id"].isNullOrBlank() ->
                            MainActivity.OPEN_PATIENT_PREFIX + data["patient_id"]
                        else -> MainActivity.OPEN_NOTIFICATIONS
                    },
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(if (type == "help_request") Notification.CATEGORY_ALARM else Notification.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
        if (type == "help_request" && helpId != null) {
            // من الإشعار مباشرة: «أنا قادم» يبلغه دون فتح التطبيق، و«اتصال» يفتح شاشة الاتصال برقمه
            builder.setColor(0xFFE5484D.toInt())
            builder.addAction(
                Notification.Action.Builder(
                    null, "أنا قادم",
                    PendingIntent.getBroadcast(
                        context, id + 1,
                        Intent(context, HelpActionReceiver::class.java).setAction(HelpActionReceiver.ACTION_ACK).putExtra(HelpActionReceiver.EXTRA_HELP, helpId)
                            .putExtra(HelpActionReceiver.EXTRA_NAME, data["patient_name"]),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).build(),
            )
            data["phone"]?.takeIf { it.isNotBlank() }?.let { phone ->
                builder.addAction(
                    Notification.Action.Builder(
                        null, "اتصال",
                        PendingIntent.getActivity(
                            context, id + 2, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    ).build(),
                )
            }
        }
        context.getSystemService(NotificationManager::class.java)?.notify(id, builder.build())
    }

    fun helpNotificationId(helpId: String) = ("help|" + helpId).hashCode()
}

/** زر «أنا قادم» في إشعار طلب المساعدة */
class HelpActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val helpId = intent.getStringExtra(EXTRA_HELP) ?: return
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val manager = context.getSystemService(NotificationManager::class.java)
            val message = try {
                Db.init(context)
                val help = FamilyApi.acknowledgeHelp(context, helpId)
                if (help.acknowledgedBy != null && help.acknowledgedBy != PatientRepository.get()?.id) {
                    (help.acknowledgedByName ?: "أحد أفراد العائلة") + " في طريقه إلى " + name
                } else {
                    "أبلغنا " + name + " أنك قادم"
                }
            } catch (error: Exception) {
                "تعذّر الإبلاغ: " + (error.message ?: "افتح عافيتي")
            }
            // نستبدل الإشعار العاجل بتأكيد هادئ
            manager?.notify(
                Push.helpNotificationId(helpId),
                Notification.Builder(context, Reminders.FAMILY_CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_pill)
                    .setContentTitle(message)
                    .setAutoCancel(true)
                    .setContentIntent(
                        PendingIntent.getActivity(
                            context, helpId.hashCode(),
                            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_HELP),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                    .build(),
            )
            Handler(Looper.getMainLooper()).post { AppModel.refreshHelpIfRunning() }
            pending.finish()
        }
    }

    companion object {
        const val ACTION_ACK = "com.alshayeksa.afiati.HELP_ACK"
        const val EXTRA_HELP = "help_id"
        const val EXTRA_NAME = "patient_name"
    }
}

class PushService : FirebaseMessagingService() {
    private val main = Handler(Looper.getMainLooper())

    override fun onNewToken(token: String) {
        // المزامنة القادمة ترسل الرمز الجديد إلى الخادم
        SyncJobService.requestSoon(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        Push.show(applicationContext, data)
        main.post {
            AppModel.refreshNotificationsIfRunning()
            if (data["type"]?.startsWith("help") == true) AppModel.refreshHelpIfRunning()
            if (data["type"]?.startsWith("invite") == true) AppModel.refreshFamilyIfRunning()
        }
    }
}
