package com.alshayeksa.afiati.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alshayeksa.afiati.sync.HelpRequest
import kotlinx.coroutines.delay

/** رقم الإسعاف في السعودية */
const val AMBULANCE = "997"
private const val COUNTDOWN_SECONDS = 5

private val Red = Color(0xFFE5484D)
private val DeepRed = Color(0xFF9E1C22)

/** يفتح شاشة الاتصال بالرقم. لا يتصل وحده، فلا حاجة لإذن الاتصال ولا خطر من ضغطة خاطئة. */
fun dial(context: Context, phone: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** زر المساعدة في الشاشة الرئيسية: أحمر واضح، أو حالة الطلب الجاري */
@Composable
fun HelpButton(onOpen: () -> Unit) {
    val active = AppModel.myHelp
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.padding(horizontal = 14.dp).padding(top = 12.dp).fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Red.copy(alpha = if (active != null) 0.32f else 0.16f), Red.copy(alpha = 0.06f))))
            .border(1.5.dp, Red.copy(alpha = 0.6f), shape)
            .clickable(role = Role.Button, onClickLabel = "طلب مساعدة من العائلة", onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).shadow(12.dp, CircleShape, ambientColor = Red, spotColor = Red).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFFF6B6F), DeepRed))),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Sos, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            when {
                active == null -> {
                    Text("أحتاج مساعدة", style = tajawal(21, FontWeight.Bold))
                    Text("اضغط هنا ليصل تنبيه عاجل لعائلتك", style = tajawal(15, color = Brand.secondaryText))
                }
                active.answered -> {
                    Text("${active.acknowledgedByName ?: "أحد أفراد عائلتك"} قادم إليك", style = tajawal(20, FontWeight.Bold))
                    Text("اضغط إذا صرت بخير", style = tajawal(15, color = Brand.secondaryText))
                }
                else -> {
                    Text("طلب المساعدة أُرسل", style = tajawal(20, FontWeight.Bold))
                    Text("ننتظر رد عائلتك · اضغط للتفاصيل", style = tajawal(15, color = Brand.secondaryText))
                }
            }
        }
    }
}

private enum class Phase { COUNTDOWN, SENDING, SENT, FAILED }

/**
 * شاشة طلب المساعدة للكبير: عدّ تنازلي 5 ثوانٍ يمكن إلغاؤه (للضغط الخاطئ)، ثم يصل التنبيه للعائلة.
 * أزرار الاتصال بالإسعاف وبرقم الطوارئ ظاهرة دائمًا، وتعمل بلا إنترنت.
 */
@Composable
fun HelpScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val existing = AppModel.myHelp
    var phase by remember { mutableStateOf(if (existing != null) Phase.SENT else Phase.COUNTDOWN) }
    var seconds by remember { mutableIntStateOf(COUNTDOWN_SECONDS) }
    var error by remember { mutableStateOf<String?>(null) }
    var notified by remember { mutableIntStateOf(-1) }
    var attempt by remember { mutableIntStateOf(0) }
    var resolving by remember { mutableStateOf(false) }
    val help = AppModel.myHelp
    BackHandler(onBack = onClose)

    LaunchedEffect(phase, attempt) {
        when (phase) {
            Phase.COUNTDOWN -> {
                while (seconds > 0) {
                    delay(1000)
                    seconds--
                }
                phase = Phase.SENDING
            }
            Phase.SENDING -> {
                AppModel.sendHelp()
                    .onSuccess { notified = it.notified; phase = Phase.SENT }
                    .onFailure { error = it.message; phase = Phase.FAILED }
            }
            Phase.SENT -> while (true) {
                // حتى يصل إشعار «قادم إليك»، أو إن تأخر
                delay(15_000)
                AppModel.refreshHelp()
            }
            Phase.FAILED -> Unit
        }
    }
    // أُغلق الطلب من جهاز آخر أو بعد «أنا بخير»
    LaunchedEffect(help, phase) { if (phase == Phase.SENT && help == null && existing != null) onClose() }

    FullScreen {
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = top + 24.dp).padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (phase) {
                Phase.COUNTDOWN -> {
                    Text("سنرسل تنبيهًا عاجلًا لعائلتك", style = tajawal(26, FontWeight.Bold), textAlign = TextAlign.Center)
                    Text("خلال ثوانٍ. إن ضغطت بالخطأ اضغط «إلغاء».", style = tajawal(18, color = Brand.secondaryText), textAlign = TextAlign.Center)
                    Countdown(seconds)
                    CallButton("أرسل الآن", Icons.Filled.Sos, Red) { seconds = 0; phase = Phase.SENDING }
                    GlassButton("إلغاء، أنا بخير", color = Brand.text) { onClose() }
                }
                Phase.SENDING -> {
                    Pulse(Icons.Filled.Sos)
                    Text("جارٍ إرسال التنبيه…", style = tajawal(26, FontWeight.Bold))
                }
                Phase.SENT -> {
                    val answered = help?.answered == true
                    Pulse(if (answered) Icons.Filled.DirectionsRun else Icons.Filled.Check, calm = answered)
                    Text(
                        if (answered) "${help?.acknowledgedByName ?: "أحد أفراد عائلتك"} قادم إليك" else "وصل التنبيه إلى عائلتك",
                        style = tajawal(28, FontWeight.Bold), textAlign = TextAlign.Center,
                    )
                    Text(
                        when {
                            answered -> "ابق في مكانك واطمئن. إن ساء حالك فاتصل بالإسعاف."
                            notified == 0 -> "لا يتابعك أحد من عائلتك بعد، فلم يصل التنبيه لأحد. اتصل بالإسعاف أو برقم الطوارئ."
                            else -> "ننتظر ردّهم. سنعيد تنبيههم كل دقيقتين حتى يرد أحدهم."
                        },
                        style = tajawal(18, color = if (notified == 0 && !answered) Brand.warning else Brand.secondaryText), textAlign = TextAlign.Center,
                    )
                }
                Phase.FAILED -> {
                    Pulse(Icons.Filled.WifiOff)
                    Text("تعذّر إرسال التنبيه", style = tajawal(28, FontWeight.Bold))
                    Text(error ?: "تحقق من الإنترنت.", style = tajawal(18, color = Brand.warning), textAlign = TextAlign.Center)
                    Text("اتصل مباشرة بالأزرار أدناه، فهي تعمل بدون إنترنت.", style = tajawal(17, color = Brand.secondaryText), textAlign = TextAlign.Center)
                    GlassButton("حاول مرة أخرى") { error = null; phase = Phase.SENDING; attempt++ }
                }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (AppModel.emergencyPhone.isNotBlank()) {
                CallButton("اتصال بـ ${AppModel.emergencyName.ifBlank { AppModel.emergencyPhone }}", Icons.Filled.Call, Brand.mint) { dial(context, AppModel.emergencyPhone) }
            }
            CallButton("اتصال بالإسعاف $AMBULANCE", Icons.Filled.LocalHospital, Red) { dial(context, AMBULANCE) }
            if (phase == Phase.SENT && help != null) {
                GlassButton(if (resolving) "جارٍ الإرسال…" else "أنا بخير الآن", icon = Icons.Filled.Check, enabled = !resolving) {
                    resolving = true
                    AppModel.resolveMyHelp { message ->
                        resolving = false
                        if (message == null) onClose() else AppModel.toast = message
                    }
                }
            } else if (phase == Phase.SENT || phase == Phase.FAILED) {
                GlassButton("إغلاق", color = Brand.text, onClick = onClose)
            }
        }
    }
}

@Composable
private fun CallButton(text: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().shadow(10.dp, shape, ambientColor = color, spotColor = color).clip(shape)
            .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.75f))))
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 17.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = tajawal(19, FontWeight.Bold, Color.White))
    }
}

@Composable
private fun Countdown(seconds: Int) {
    val progress = seconds / COUNTDOWN_SECONDS.toFloat()
    Box(Modifier.padding(vertical = 18.dp).size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = 14.dp.toPx()
            drawArc(Red.copy(alpha = 0.15f), 0f, 360f, false, style = Stroke(width))
            drawArc(Red, -90f, 360f * progress, false, style = Stroke(width, cap = StrokeCap.Round))
        }
        Text("$seconds", style = tajawal(84, FontWeight.Bold, Red))
    }
}

/** دائرة نابضة بأيقونة الحالة */
@Composable
private fun Pulse(icon: ImageVector, calm: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(1f, if (calm) 1.04f else 1.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale")
    val color = if (calm) Brand.mint else Red
    Box(Modifier.padding(vertical = 24.dp).size(190.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(190.dp).scale(scale).background(color.copy(alpha = 0.14f), CircleShape))
        Box(
            Modifier.size(128.dp).shadow(24.dp, CircleShape, ambientColor = color, spotColor = color).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.6f)))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(64.dp)) }
    }
}

/** بطاقة عاجلة لفرد العائلة: قريبه يحتاج مساعدة، مع اتصال و«أنا قادم» */
@Composable
fun FamilyHelpCard(help: HelpRequest) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(26.dp)
    val transition = rememberInfiniteTransition(label = "alert")
    val glow by transition.animateFloat(0.45f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "glow")
    val answered = help.answered
    Column(
        Modifier.padding(horizontal = 14.dp).padding(top = 12.dp).fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(Red.copy(alpha = 0.28f), Red.copy(alpha = 0.10f))))
            .border(2.dp, Red.copy(alpha = if (answered) 0.5f else glow), shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Sos, null, tint = Red, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${help.patientName} يحتاج مساعدة", style = tajawal(22, FontWeight.Bold))
                Text(Format.relative(help.createdAt), style = tajawal(15, color = Brand.secondaryText))
            }
        }
        help.message?.let { Text("«$it»", style = tajawal(18)) }
        if (answered) {
            val me = help.acknowledgedBy != null && help.acknowledgedBy == AppModel.patient?.id
            Text(if (me) "أنت في طريقك إليه" else "${help.acknowledgedByName ?: "أحد أفراد العائلة"} في طريقه إليه", style = tajawal(17, FontWeight.Medium, Brand.glow))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            help.patientPhone?.takeIf { it.isNotBlank() }?.let { phone ->
                Box(Modifier.weight(1f)) { CallButton("اتصال", Icons.Filled.Call, Brand.mint) { dial(context, phone) } }
            }
            if (!answered) Box(Modifier.weight(1f)) { CallButton("أنا قادم", Icons.Filled.DirectionsRun, Red) { AppModel.acknowledgeHelp(help) } }
        }
    }
}
