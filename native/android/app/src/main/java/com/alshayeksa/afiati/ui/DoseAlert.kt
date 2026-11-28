package com.alshayeksa.afiati.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alshayeksa.afiati.R
import com.alshayeksa.afiati.data.DoseLog
import com.alshayeksa.afiati.data.DoseStatus
import com.alshayeksa.afiati.data.Iso
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/**
 * تنبيه موعد الدواء داخل التطبيق: بطاقة الجرعة تومض من موعدها حتى يسجّلها، وتتوقف بعد 15 دقيقة.
 * ومعها نغمة تتكرر كل ثوانٍ (تُطفأ من الإعدادات). الإشعار المعتاد يبقى كما هو.
 */
object DoseAlert {
    const val RING_MINUTES = 15L
    private const val SOUND_EVERY_MS = 7_000L
    private const val KEY_SOUND = "alert.sound"

    var soundOn by mutableStateOf(true)
        private set

    fun load() {
        soundOn = LocalStore.prefs.getBoolean(KEY_SOUND, true)
    }

    fun setSound(value: Boolean) {
        soundOn = value
        LocalStore.prefs.edit().putBoolean(KEY_SOUND, value).apply()
    }

    /** الجرعة حان موعدها ولم تُسجَّل، وما زلنا في أول 15 دقيقة */
    fun isRinging(log: DoseLog, now: Instant, taken: Boolean): Boolean {
        if (taken || log.status != DoseStatus.PENDING) return false
        val at = runCatching { Iso.parse(log.scheduledAt) }.getOrNull() ?: return false
        val seconds = Duration.between(at, now).seconds
        return seconds >= 0 && seconds < RING_MINUTES * 60
    }

    private var lastPlayed = 0L

    /** نغمة قصيرة بمستوى صوت الإشعارات (تحترم الوضع الصامت) */
    fun play(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastPlayed < SOUND_EVERY_MS) return
        lastPlayed = now
        runCatching {
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            MediaPlayer.create(context, R.raw.dose_alert, attributes, 0)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        }
    }
}

/** معرّفات الجرعات التي ترنّ الآن. يفحص كل ثانية، ويشغّل النغمة ما دامت جرعة ترنّ والتطبيق مفتوح. */
@Composable
fun rememberRinging(logs: List<DoseLog>): Set<String> {
    val context = LocalContext.current
    val current by rememberUpdatedState(logs)
    var ringing by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Instant.now()
            val next = current.filter { DoseAlert.isRinging(it, now, AppModel.isTaken(it)) }.map { it.id }.toSet()
            if (next != ringing) ringing = next
            if (next.isNotEmpty() && DoseAlert.soundOn) DoseAlert.play(context)
            delay(1000)
        }
    }
    return ringing
}

/** هلال (الهلال الأحمر) لزر الاتصال بالإسعاف */
val CrescentIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Crescent", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(17.36f, 5.63f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 17.36f, 18.37f)
            arcTo(7.2f, 7.2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.36f, 5.63f)
            close()
        }
    }.build()
}
