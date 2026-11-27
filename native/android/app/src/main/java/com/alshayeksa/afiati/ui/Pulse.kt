package com.alshayeksa.afiati.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.alshayeksa.afiati.data.HeartRateReading
import com.alshayeksa.afiati.data.HeartRateRepository
import com.alshayeksa.afiati.data.Iso
import com.alshayeksa.afiati.pulse.FrameStats
import com.alshayeksa.afiati.pulse.PulseCamera
import com.alshayeksa.afiati.pulse.PulseEstimate
import com.alshayeksa.afiati.pulse.PulseMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** مدة القياس بعد استقرار الإشارة */
private const val MEASURE_SECONDS = 30.0
/** أول ثوانٍ بعد وضع الإصبع: الكاميرا تضبط الإضاءة، فلا تُحسب */
private const val SETTLE_SECONDS = 3.0
/** إن رُفع الإصبع أطول من هذا يبدأ القياس من جديد */
private const val FINGER_GRACE_SECONDS = 0.7
/** أقصى مدة لمحاولة واحدة، ثم يتوقف الفلاش */
private const val GIVE_UP_SECONDS = 90.0
/** المعدل المعتاد أثناء الراحة للبالغين، للتنبيه اللطيف فقط */
private val RESTING_RANGE = 50..110

/** يزيد بعد كل حفظ، فتتحدث بطاقة «آخر قياس» */
object PulseStore {
    var version by mutableIntStateOf(0)
}

private fun readingTime(iso: String): String {
    val instant = runCatching { Iso.parse(iso) }.getOrNull() ?: return ""
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val day = when (date) {
        LocalDate.now() -> "اليوم"
        LocalDate.now().minusDays(1) -> "أمس"
        else -> Format.gregorianDay(date)
    }
    return "$day · ${Format.time(instant)}"
}

private fun bpmWords(bpm: Int) = "$bpm نبضة في الدقيقة"

/** بطاقة النبض في «اليوم»: تفتح القياس، وتعرض آخر قراءة */
@Composable
fun PulseCard(onOpen: () -> Unit) {
    val patientId = AppModel.patient?.id
    val last by produceState<HeartRateReading?>(null, patientId, PulseStore.version) {
        value = patientId?.let { id -> withContext(Dispatchers.IO) { runCatching { HeartRateRepository.recent(id, 1).firstOrNull() }.getOrNull() } }
    }
    Row(
        Modifier.padding(horizontal = 14.dp).padding(top = 6.dp).fillMaxWidth().glass(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClickLabel = "قياس النبض", onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(Brand.glow.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MonitorHeart, null, tint = Brand.glow, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("قياس النبض", style = tajawal(20, FontWeight.Bold))
            Text(
                last?.let { "آخر قياس: ${it.bpm} نبضة · ${readingTime(it.measuredAt)}" } ?: "ضع إصبعك على الكاميرا 30 ثانية",
                style = tajawal(14, color = Brand.secondaryText),
            )
        }
    }
}

private enum class Stage { INTRO, MEASURING, RESULT, FAILED }

private class Failure(val title: String, val body: String, val showTips: Boolean = false, val openSettings: Boolean = false)

/** حالة قياس واحد، تُحدَّث مع كل إطار على الخيط الرئيسي */
private class PulseSession {
    private val times = ArrayList<Double>()
    private val values = ArrayList<Double>()
    private var segmentStart = -1.0
    private var lastFinger = -1.0
    private var firstFrame = -1.0
    private var frames = 0
    private var lastLive = 0.0

    var fingerOn by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var live by mutableStateOf<Int?>(null)
    var wave by mutableStateOf<List<Float>>(emptyList())

    sealed interface Outcome {
        class Done(val estimate: PulseEstimate?, val wave: List<Float>) : Outcome
        data object GaveUp : Outcome
    }

    private fun resetSegment() {
        segmentStart = -1.0
        times.clear(); values.clear()
        progress = 0f; live = null; wave = emptyList()
    }

    fun onFrame(frame: FrameStats): Outcome? {
        if (firstFrame < 0) firstFrame = frame.timeSeconds
        frames++
        val t = frame.timeSeconds
        val on = PulseMath.fingerOn(frame)
        if (on) {
            lastFinger = t
            if (segmentStart < 0) segmentStart = t
        } else if (segmentStart >= 0 && t - lastFinger > FINGER_GRACE_SECONDS) {
            resetSegment()
        }
        fingerOn = segmentStart >= 0
        if (on && segmentStart >= 0 && t - segmentStart >= SETTLE_SECONDS) {
            times += t
            values += frame.luma
        }
        val measured = if (times.size > 1) times.last() - times.first() else 0.0
        progress = (measured / MEASURE_SECONDS).toFloat().coerceIn(0f, 1f)
        if (times.size > 30 && frames % 2 == 0) wave = PulseMath.display(times.toDoubleArray(), values.toDoubleArray())
        // رقم مبدئي بعد 10 ثوانٍ، فقط إن اجتاز فحص الجودة
        if (measured >= 10 && t - lastLive >= 1) {
            lastLive = t
            PulseMath.estimate(times.toDoubleArray(), values.toDoubleArray())?.let { live = it.bpm }
        }
        if (measured >= MEASURE_SECONDS) return Outcome.Done(PulseMath.estimate(times.toDoubleArray(), values.toDoubleArray()), wave)
        if (t - firstFrame > GIVE_UP_SECONDS) return Outcome.GaveUp
        return null
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun tooHot(context: Context): Boolean =
    Build.VERSION.SDK_INT >= 29 &&
        (context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_SEVERE

private fun vibrate(context: Context) {
    runCatching {
        context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

/**
 * قياس النبض بالكاميرا الخلفية والفلاش (PPG). 30 ثانية بعد استقرار الإشارة.
 * إن كانت الإشارة ضعيفة لا يُعرض رقم، ويُطلب إعادة القياس. الفلاش يُطفأ عند الانتهاء أو الخروج أو سخونة الجوال.
 */
@Composable
fun PulseScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var stage by remember { mutableStateOf(Stage.INTRO) }
    var failure by remember { mutableStateOf<Failure?>(null) }
    var result by remember { mutableStateOf<PulseEstimate?>(null) }
    var resultWave by remember { mutableStateOf<List<Float>>(emptyList()) }
    var saving by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)

    fun fail(f: Failure) { failure = f; stage = Stage.FAILED }
    fun begin() {
        if (tooHot(context)) fail(Failure("الجوال ساخن الآن", "انتظر بضع دقائق حتى يبرد، ثم أعد القياس."))
        else stage = Stage.MEASURING
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin()
        else fail(Failure("نحتاج إذن الكاميرا", "القياس يستخدم الكاميرا الخلفية والفلاش فقط، ولا تُحفظ أي صورة. اسمح بالكاميرا من إعدادات الجوال.", openSettings = true))
    }
    val start = {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) begin()
        else permission.launch(Manifest.permission.CAMERA)
    }

    FullScreen {
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Row(Modifier.fillMaxWidth().padding(top = top + 10.dp).padding(start = 20.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("قياس النبض", style = tajawal(26, FontWeight.Bold), modifier = Modifier.weight(1f))
            Text(
                "إغلاق", style = tajawal(18, FontWeight.Medium, Brand.glow),
                modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onClose).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (stage) {
                Stage.INTRO -> Intro(onStart = start)
                Stage.MEASURING -> Measuring(
                    onDone = { estimate, wave ->
                        vibrate(context)
                        if (estimate == null) {
                            fail(Failure("لم تتضح إشارة النبض", "لن نعرض رقمًا غير دقيق. جرّب مرة أخرى بهذه الطريقة:", showTips = true))
                        } else {
                            result = estimate; resultWave = wave; stage = Stage.RESULT
                        }
                    },
                    onFail = ::fail,
                    onStop = { stage = Stage.INTRO },
                )
                Stage.RESULT -> result?.let { estimate ->
                    Result(
                        estimate, resultWave, saving,
                        onSave = {
                            val patientId = AppModel.patient?.id
                            if (patientId != null && !saving) {
                                saving = true
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { HeartRateRepository.save(patientId, estimate.bpm, estimate.quality) } }
                                        .onSuccess { PulseStore.version++; AppModel.toast = "حُفظ القياس"; stage = Stage.INTRO }
                                        .onFailure { AppModel.toast = "تعذّر حفظ القياس" }
                                    saving = false
                                }
                            }
                        },
                        onAgain = start,
                    )
                }
                Stage.FAILED -> failure?.let { f ->
                    FailureView(f, onAgain = start, onSettings = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    })
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), content = content,
    )
}

@Composable
private fun Intro(onStart: () -> Unit) {
    Card {
        FingerIllustration(Modifier.size(170.dp))
        Text("ضع إصبعك على الكاميرا", style = tajawal(26, FontWeight.Bold), textAlign = TextAlign.Center)
        Text("برفق، وأبقِ يدك ثابتة", style = tajawal(18, color = Brand.secondaryText), textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        PrimaryButton("ابدأ القياس", icon = Icons.Filled.Favorite, onClick = onStart)
    }
    Tips()
    History()
    Text(
        "قراءة تقديرية وليست تشخيصًا طبيًا. يتم القياس داخل جوالك، ولا تُحفظ ولا تُرسل أي صورة.",
        style = tajawal(14, color = Brand.secondaryText), textAlign = TextAlign.Center,
    )
}

@Composable
private fun Tips() {
    Card {
        Text("لقياس أدق", style = tajawal(19, FontWeight.Bold), modifier = Modifier.fillMaxWidth())
        listOf(
            "اجلس وارتح دقيقة قبل القياس.",
            "غطِّ الكاميرا الخلفية والفلاش معًا بطرف إصبعك.",
            "لا تضغط بقوة، ولا تحرك يدك أو تتكلم حتى ينتهي.",
        ).forEach { tip ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 10.dp).size(7.dp).clip(CircleShape).background(Brand.glow))
                Spacer(Modifier.width(10.dp))
                Text(tip, style = tajawal(17, color = Brand.secondaryText))
            }
        }
    }
}

@Composable
private fun History() {
    val patientId = AppModel.patient?.id ?: return
    val readings by produceState(emptyList<HeartRateReading>(), patientId, PulseStore.version) {
        value = withContext(Dispatchers.IO) { runCatching { HeartRateRepository.recent(patientId, 5) }.getOrDefault(emptyList()) }
    }
    if (readings.isEmpty()) return
    Card {
        Text("قياساتك السابقة", style = tajawal(19, FontWeight.Bold), modifier = Modifier.fillMaxWidth())
        readings.forEach { reading ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${reading.bpm}", style = tajawal(26, FontWeight.Bold, Brand.glow))
                Spacer(Modifier.width(8.dp))
                Text("نبضة", style = tajawal(16, color = Brand.secondaryText))
                Spacer(Modifier.weight(1f))
                Text(readingTime(reading.measuredAt), style = tajawal(15, color = Brand.secondaryText))
            }
        }
    }
}

@Composable
private fun Measuring(onDone: (PulseEstimate?, List<Float>) -> Unit, onFail: (Failure) -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val session = remember { PulseSession() }
    var finished by remember { mutableStateOf(false) }

    // الشاشة تبقى مضاءة أثناء القياس
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // الكاميرا والفلاش: يُغلقان حين تغادر هذه الحالة، مهما كان السبب
    DisposableEffect(Unit) {
        lateinit var camera: PulseCamera
        camera = PulseCamera(
            context,
            onFrame = { frame ->
                if (!finished) {
                    when (val outcome = session.onFrame(frame)) {
                        is PulseSession.Outcome.Done -> { finished = true; camera.stop(); onDone(outcome.estimate, outcome.wave) }
                        PulseSession.Outcome.GaveUp -> {
                            finished = true; camera.stop()
                            onFail(Failure("لم نستطع إكمال القياس", "لم يبقَ الإصبع على الكاميرا مدة كافية. جرّب مرة أخرى:", showTips = true))
                        }
                        null -> Unit
                    }
                }
            },
            onReady = { hasFlash ->
                if (!hasFlash && !finished) {
                    finished = true; camera.stop()
                    onFail(Failure("جوالك بلا فلاش خلفي", "القياس يحتاج ضوء الفلاش ليمر خلال الإصبع، لذلك لا يعمل على هذا الجوال."))
                }
            },
            onError = { message -> if (!finished) { finished = true; onFail(Failure("تعذّر تشغيل الكاميرا", message)) } },
        )
        camera.start()
        // الخروج من التطبيق أو سخونة الجوال: يتوقف القياس ويُطفأ الفلاش
        val lifecycle = (context.activity() as? ComponentActivity)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !finished) {
                finished = true; camera.stop()
                onFail(Failure("توقف القياس", "خرجت من التطبيق أثناء القياس، فأطفأنا الفلاش. ابدأ من جديد."))
            }
        }
        lifecycle?.addObserver(observer)
        val power = context.getSystemService(PowerManager::class.java)
        val thermal = if (Build.VERSION.SDK_INT >= 29) PowerManager.OnThermalStatusChangedListener { status ->
            if (status >= PowerManager.THERMAL_STATUS_SEVERE && !finished) {
                finished = true; camera.stop()
                onFail(Failure("توقف القياس لأن الجوال ساخن", "أطفأنا الفلاش. انتظر بضع دقائق حتى يبرد، ثم أعد القياس."))
            }
        } else null
        if (Build.VERSION.SDK_INT >= 29 && thermal != null) power?.addThermalStatusListener(context.mainExecutor, thermal)
        onDispose {
            camera.stop()
            lifecycle?.removeObserver(observer)
            if (Build.VERSION.SDK_INT >= 29 && thermal != null) power?.removeThermalStatusListener(thermal)
        }
    }

    val remaining = ((1 - session.progress) * MEASURE_SECONDS).toInt().coerceAtLeast(0)
    Card {
        ProgressHeart(session.progress, session.fingerOn)
        if (!session.fingerOn) {
            Text("ضع إصبعك على الكاميرا", style = tajawal(24, FontWeight.Bold), textAlign = TextAlign.Center)
            Text("غطِّ الكاميرا الخلفية والفلاش برفق بطرف إصبعك", style = tajawal(17, color = Brand.warning), textAlign = TextAlign.Center)
        } else {
            Text(if (session.progress > 0f) "باقي $remaining ثانية" else "جارٍ ضبط الإشارة…", style = tajawal(24, FontWeight.Bold), textAlign = TextAlign.Center)
            Text("ممتاز، أبقِ إصبعك ثابتًا ولا تتكلم", style = tajawal(17, color = Brand.mint), textAlign = TextAlign.Center)
        }
        Wave(session.wave, Modifier.fillMaxWidth().height(110.dp))
        Text(
            session.live?.let { "تقريبًا $it نبضة في الدقيقة" } ?: " ",
            style = tajawal(17, color = Brand.secondaryText), textAlign = TextAlign.Center,
        )
    }
    GlassButton("إيقاف القياس", icon = Icons.Filled.Stop, color = Brand.text) {
        finished = true
        onStop()
    }
}

@Composable
private fun ProgressHeart(progress: Float, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "heart")
    val beat by transition.animateFloat(1f, if (active) 1.12f else 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "beat")
    Box(Modifier.padding(vertical = 6.dp).size(170.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = 12.dp.toPx()
            drawArc(Brand.glow.copy(alpha = 0.15f), 0f, 360f, false, style = Stroke(width))
            drawArc(Brand.glow, -90f, 360f * progress, false, style = Stroke(width, cap = StrokeCap.Round))
        }
        Icon(Icons.Filled.Favorite, null, tint = if (active) Brand.danger else Brand.secondaryText, modifier = Modifier.size(64.dp).scale(beat))
    }
}

/** موجة النبض: خط مضيء، مسطح حتى تصل الإشارة */
@Composable
private fun Wave(points: List<Float>, modifier: Modifier) {
    val color = Brand.glow
    Canvas(modifier) {
        val stroke = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val middle = size.height / 2
        if (points.size < 2) {
            drawLine(color.copy(alpha = 0.4f), Offset(0f, middle), Offset(size.width, middle), 3.dp.toPx(), StrokeCap.Round)
            return@Canvas
        }
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = size.width * i / (points.size - 1)
            val y = middle - p * middle * 0.85f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable
private fun Result(estimate: PulseEstimate, wave: List<Float>, saving: Boolean, onSave: () -> Unit, onAgain: () -> Unit) {
    Card {
        Icon(Icons.Filled.MonitorHeart, null, tint = Brand.glow, modifier = Modifier.size(44.dp))
        Text("نبض القلب", style = tajawal(19, color = Brand.secondaryText))
        Text("${estimate.bpm}", style = tajawal(84, FontWeight.Bold))
        Text("نبضة في الدقيقة", style = tajawal(19, color = Brand.secondaryText))
        Wave(wave, Modifier.fillMaxWidth().height(110.dp))
        if (estimate.bpm !in RESTING_RANGE) {
            Text(
                "القراءة خارج المعتاد أثناء الراحة. اجلس خمس دقائق ثم أعد القياس، وإن تكررت أو شعرت بتعب أو دوخة فاستشر طبيبك.",
                style = tajawal(16, color = Brand.warning), textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(2.dp))
        PrimaryButton(if (saving) "جارٍ الحفظ…" else "حفظ القياس", icon = Icons.Filled.Save, enabled = !saving, onClick = onSave)
        GlassButton("قِس مرة أخرى", icon = Icons.Filled.Refresh, onClick = onAgain)
        Text("قراءة تقديرية وليست تشخيصًا طبيًا", style = tajawal(14, color = Brand.secondaryText), textAlign = TextAlign.Center)
    }
}

@Composable
private fun FailureView(failure: Failure, onAgain: () -> Unit, onSettings: () -> Unit) {
    Card {
        Icon(Icons.Filled.MonitorHeart, null, tint = Brand.warning, modifier = Modifier.size(52.dp))
        Text(failure.title, style = tajawal(24, FontWeight.Bold), textAlign = TextAlign.Center)
        Text(failure.body, style = tajawal(17, color = Brand.secondaryText), textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        if (failure.openSettings) PrimaryButton("فتح الإعدادات", onClick = onSettings)
        else PrimaryButton("أعد القياس", icon = Icons.Filled.Refresh, onClick = onAgain)
    }
    if (failure.showTips) Tips()
}

/** رسم توضيحي: طرف إصبع يغطي الكاميرا والفلاش في ظهر الجوال */
@Composable
private fun FingerIllustration(modifier: Modifier) {
    val outline = Brand.glow
    val skin = Color(0xFFE9B994)
    val skinEdge = Color(0xFFC98F6A)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val phoneLeft = w * 0.2f
        val phoneTop = h * 0.04f
        val phoneSize = Size(w * 0.5f, h * 0.88f)
        drawRoundRect(outline.copy(alpha = 0.12f), Offset(phoneLeft, phoneTop), phoneSize, CornerRadius(w * 0.08f))
        drawRoundRect(outline, Offset(phoneLeft, phoneTop), phoneSize, CornerRadius(w * 0.08f), style = Stroke(3.dp.toPx()))
        // الكاميرا والفلاش أعلى ظهر الجوال
        val lens = Offset(phoneLeft + w * 0.12f, phoneTop + h * 0.12f)
        val flash = Offset(lens.x + w * 0.12f, lens.y)
        // الإصبع من أسفل اليمين حتى يغطي العدسة والفلاش
        val tip = Offset(lens.x + w * 0.07f, lens.y + h * 0.015f)
        val base = Offset(w * 0.98f, h * 0.8f)
        drawLine(skinEdge, tip, base, w * 0.21f, StrokeCap.Round)
        drawLine(skin, tip, base, w * 0.18f, StrokeCap.Round)
        // الظفر على ظهر الإصبع
        val along = Offset(base.x - tip.x, base.y - tip.y).let { it / it.getDistance() }
        drawCircle(Color(0xFFF6D9C4), w * 0.045f, tip + along * (w * 0.09f))
        // العدسة والفلاش يظهران من تحت الإصبع، والدائرة المتقطعة تحدد مكانه
        drawCircle(Color(0xFF0D2233).copy(alpha = 0.55f), w * 0.05f, lens)
        drawCircle(Color(0xFFFFE08A).copy(alpha = 0.8f), w * 0.025f, flash)
        drawCircle(outline, w * 0.13f, Offset((lens.x + flash.x) / 2, lens.y), style = Stroke(2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
    }
}
