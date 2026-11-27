package com.alshayeksa.afiati.sync

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.alshayeksa.afiati.MainActivity
import com.alshayeksa.afiati.R
import com.alshayeksa.afiati.data.SyncState
import com.alshayeksa.afiati.reminders.Reminders
import com.alshayeksa.afiati.ui.AppModel
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
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
            else -> Reminders.FAMILY_CHANNEL
        }
        val id = (data["type"].orEmpty() + title + body + System.currentTimeMillis() / 60_000).hashCode()
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN, if (data["type"] == "invite_request") MainActivity.OPEN_FAMILY else MainActivity.OPEN_NOTIFICATIONS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pill)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
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
            if (data["type"]?.startsWith("invite") == true) AppModel.refreshFamilyIfRunning()
        }
    }
}
