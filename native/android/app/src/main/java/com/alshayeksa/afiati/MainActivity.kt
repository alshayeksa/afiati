package com.alshayeksa.afiati

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.alshayeksa.afiati.data.Db
import com.alshayeksa.afiati.reminders.Reminders
import com.alshayeksa.afiati.sync.SyncJobService
import com.alshayeksa.afiati.ui.AfiatiRoot
import com.alshayeksa.afiati.ui.AppModel
import com.alshayeksa.afiati.ui.Appearance
import com.alshayeksa.afiati.ui.LocalStore

class AfiatiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LocalStore.init(this)
        Db.init(this)
        Appearance.init(this)
        Reminders.createChannel(this)
        SyncJobService.schedulePeriodic(this)
        AppModel.init(this)
    }
}

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        AppModel.onForeground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            // أيقونات شريط الحالة داكنة فوق المظهر الفاتح
            val light = Appearance.light
            LaunchedEffect(light) {
                val style = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            // إذن الإشعارات يُطلب مرة بعد إنشاء الملف، لأن التذكيرات لا تظهر بدونه
            val hasPatient = AppModel.patient != null
            LaunchedEffect(hasPatient) { if (hasPatient) askNotificationsOnce() }
            AfiatiRoot()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** ضغطة على إشعار عائلة: تفتح العائلة أو الإشعارات */
    private fun handle(intent: Intent?) {
        intent?.getStringExtra(EXTRA_OPEN)?.let {
            AppModel.pendingOpen = it
            intent.removeExtra(EXTRA_OPEN)
        }
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_FAMILY = "family"
        const val OPEN_NOTIFICATIONS = "notifications"
    }

    override fun onResume() {
        super.onResume()
        AppModel.onForeground()
    }

    override fun onStop() {
        super.onStop()
        AppModel.onBackground()
    }

    private fun askNotificationsOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = LocalStore.prefs
        if (prefs.getBoolean("askedNotifications", false)) return
        prefs.edit().putBoolean("askedNotifications", true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
