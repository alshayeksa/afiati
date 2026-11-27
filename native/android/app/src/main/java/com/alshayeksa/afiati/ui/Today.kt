package com.alshayeksa.afiati.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alshayeksa.afiati.data.DoseLog
import com.alshayeksa.afiati.data.DoseStatus
import com.alshayeksa.afiati.data.Iso
import com.alshayeksa.afiati.data.Medication
import com.alshayeksa.afiati.sync.SyncStatus
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** حالة الجرعة كما يراها المستخدم */
enum class DoseState { TAKEN, DUE, LATE, MISSED, UPCOMING }

fun doseState(log: DoseLog, now: Instant, takenOverride: Boolean = false): DoseState {
    if (takenOverride || log.status == DoseStatus.TAKEN) return DoseState.TAKEN
    if (log.status == DoseStatus.MISSED) return DoseState.MISSED
    val at = runCatching { Iso.parse(log.scheduledAt) }.getOrNull() ?: return DoseState.UPCOMING
    val minutes = Duration.between(at, now).toMinutes()
    return when {
        minutes < -30 -> DoseState.UPCOMING
        minutes <= 60 -> DoseState.DUE
        else -> DoseState.LATE
    }
}

/** فترات اليوم لتجميع الجرعات */
private enum class Period(val title: String, val icon: ImageVector) {
    MORNING("الصباح", Icons.Outlined.WbSunny),
    NOON("الظهر", Icons.Outlined.LightMode),
    EVENING("المساء", Icons.Outlined.WbTwilight),
    NIGHT("الليل", Icons.Outlined.DarkMode),
}

private fun period(log: DoseLog): Period {
    val hour = runCatching { Iso.parse(log.scheduledAt).atZone(ZoneId.systemDefault()).hour }.getOrDefault(8)
    return when (hour) {
        in 4..11 -> Period.MORNING
        in 12..16 -> Period.NOON
        in 17..20 -> Period.EVENING
        else -> Period.NIGHT
    }
}

/** الوقت الحالي، يتحدث كل نصف دقيقة لتتغير الحالات «موعدك الآن» و«متأخرة» وحدها */
@Composable
fun rememberNow(): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Instant.now()
        }
    }
    return now
}

@Composable
fun TodayScreen(header: @Composable () -> Unit, onAdd: () -> Unit, onOpenMedication: (Medication) -> Unit, onHelp: () -> Unit, onPulse: () -> Unit) {
    val now = rememberNow()
    val logs = AppModel.todayLogs
    val medications = AppModel.medications
    var confirmEarly by remember { mutableStateOf<DoseLog?>(null) }
    val take: (DoseLog) -> Unit = { log -> if (AppModel.isEarly(log, now)) confirmEarly = log else AppModel.takeDose(log) }

    TabList(header) {
        item(key = "greeting") { Greeting() }
        item(key = "help") { HelpButton(onHelp) }
        item(key = "pulse") { PulseCard(onPulse) }
        item(key = "wave") { WaveRibbon(Modifier.fillMaxWidth().padding(top = 2.dp).height(120.dp)) }

        if (medications.isEmpty() && logs.isEmpty()) {
            item(key = "empty") {
                EmptyCard(
                    "أضف دواءك الأول",
                    "اكتب اسم الدواء ومواعيده، وسنذكّرك بكل جرعة في وقتها حتى بدون إنترنت.",
                    "إضافة دواء", onAdd,
                )
            }
            return@TabList
        }

        item(key = "health") { ReminderHealthCard() }
        item(key = "progress") { ProgressCard(logs, now) }
        item(key = "focus") { FocusCard(logs, now, take) }

        val low = medications.filter { it.lowStock }
        items(low, key = { "low-" + it.id }) { medication -> LowStockCard(medication) { onOpenMedication(medication) } }

        if (logs.isNotEmpty()) {
            item(key = "doses-title") { SectionTitle("جرعات اليوم") }
            logs.groupBy(::period).toSortedMap().forEach { (period, group) ->
                item(key = "period-${period.name}") {
                    Row(Modifier.padding(horizontal = 22.dp).padding(top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(period.icon, null, tint = Brand.glow, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(period.title, style = tajawal(17, FontWeight.Bold, Brand.secondaryText))
                    }
                }
                items(group, key = { "dose-" + it.id }) { log ->
                    DoseRow(log, doseState(log, now, AppModel.isTaken(log)), onTake = { take(log) }) {
                        medications.firstOrNull { it.id == log.medicationId }?.let(onOpenMedication)
                    }
                }
            }
        }
    }

    confirmEarly?.let { log ->
        AfiatiDialog(
            title = "موعدها لم يحن بعد",
            text = "جرعة ${log.medicationName} موعدها ${Format.isoTime(log.scheduledAt)}. هل أخذتها فعلًا الآن؟",
            confirm = "نعم، أخذتها", onConfirm = { AppModel.takeDose(log); confirmEarly = null },
            dismiss = "لا", onDismiss = { confirmEarly = null },
        )
    }
}

@Composable
private fun Greeting() {
    val sync = AppModel.sync
    val (color, text) = when {
        sync.running -> Brand.warning to "جاري المزامنة…"
        sync.status == SyncStatus.FAILED -> Brand.danger to "محفوظ على جوالك · تعذّرت المزامنة"
        sync.status == SyncStatus.OFFLINE && sync.pending > 0 -> Brand.warning to "محفوظ على جوالك · يُرفع عند عودة الإنترنت"
        sync.status == SyncStatus.OFFLINE -> Brand.warning to "بدون إنترنت · كل شيء يعمل"
        else -> Brand.glow to "كل بياناتك محفوظة"
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp)) {
        // الاسم في الشريط العلوي، فهنا تحية اليوم من جدول التحيات
        Text(Greetings.forDay(AppModel.today), style = tajawal(30, FontWeight.Bold, Brand.glow))
        Text("${Format.gregorianDay(AppModel.today)} · ${Format.hijriDay(AppModel.today)}", style = tajawal(16, color = Brand.secondaryText))
        StatusLine(color, text, Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ProgressCard(logs: List<DoseLog>, now: Instant) {
    val total = logs.size
    val done = logs.count { AppModel.isTaken(it) }
    val late = logs.count { doseState(it, now, AppModel.isTaken(it)).let { s -> s == DoseState.LATE || s == DoseState.MISSED } }
    val message = when {
        total == 0 -> "لا توجد جرعات اليوم"
        done == total -> "ممتاز! أكملت جرعات اليوم"
        late > 0 -> if (late == 1) "جرعة واحدة متأخرة" else "$late جرعات متأخرة"
        done == 0 -> "يومك يبدأ الآن"
        else -> "أحسنت، استمر"
    }
    Row(
        Modifier.padding(horizontal = 14.dp).fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("التزامك اليوم", style = tajawal(17, color = Brand.secondaryText))
            Text(message, style = tajawal(23, FontWeight.Bold))
            Text(if (total == 0) "" else "أخذت $done من $total", style = tajawal(17, color = if (late > 0) Brand.warning else Brand.glow))
        }
        Spacer(Modifier.width(12.dp))
        ProgressRing(if (total == 0) 0f else done.toFloat() / total, 108.dp) {
            Text(if (total == 0) "—" else "${done * 100 / total}٪", style = tajawal(26, FontWeight.Bold))
        }
    }
}

/** البطاقة الأهم: الجرعة الحالية بزر كبير، أو القادمة، أو تهنئة */
@Composable
private fun FocusCard(logs: List<DoseLog>, now: Instant, onTake: (DoseLog) -> Unit) {
    val states = logs.map { it to doseState(it, now, AppModel.isTaken(it)) }
    val current = states.firstOrNull { it.second == DoseState.LATE || it.second == DoseState.DUE }
    val next = states.firstOrNull { it.second == DoseState.UPCOMING }
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.padding(horizontal = 14.dp).padding(top = 12.dp, bottom = 14.dp).fillMaxWidth()
            .glass(shape, if (current != null) 0.55f else 0.22f).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            current != null -> {
                val (log, state) = current
                val late = state == DoseState.LATE
                Text(
                    if (late) "متأخرة · كان موعدها ${Format.isoTime(log.scheduledAt)}" else "موعدك الآن · ${Format.isoTime(log.scheduledAt)}",
                    style = tajawal(17, FontWeight.Medium, if (late) Brand.warning else Brand.glow),
                )
                Text(log.medicationName, style = tajawal(30, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(Format.dose(log.doseAmount, log.doseUnit), style = tajawal(19, color = Brand.secondaryText))
                PrimaryButton("أخذت الدواء", Modifier.padding(top = 10.dp), icon = Icons.Filled.Check) { onTake(log) }
            }
            next != null -> {
                val log = next.first
                val minutes = runCatching { Duration.between(now, Iso.parse(log.scheduledAt)).toMinutes() }.getOrDefault(0)
                Text("الجرعة القادمة", style = tajawal(17, FontWeight.Medium, Brand.glow))
                Text(log.medicationName, style = tajawal(28, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${Format.dose(log.doseAmount, log.doseUnit)} · الساعة ${Format.isoTime(log.scheduledAt)} · ${inTime(minutes)}", style = tajawal(18, color = Brand.secondaryText))
            }
            logs.isNotEmpty() -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(56.dp).shadow(12.dp, CircleShape, ambientColor = Brand.glow, spotColor = Brand.glow).clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Brand.glow, Brand.mint))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Check, null, tint = Brand.ink, modifier = Modifier.size(32.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("أحسنت!", style = tajawal(26, FontWeight.Bold))
                        Text("أخذت كل أدوية اليوم. نراك غدًا بإذن الله.", style = tajawal(17, color = Brand.secondaryText))
                    }
                }
            }
            else -> {
                Text("لا جرعات اليوم", style = tajawal(24, FontWeight.Bold))
                Text("أدويتك الأسبوعية أو الشهرية ليس لها موعد اليوم.", style = tajawal(17, color = Brand.secondaryText))
            }
        }
    }
}

private fun inTime(minutes: Long): String = when {
    minutes < 60 -> "بعد ${minutes.coerceAtLeast(1)} دقيقة"
    minutes < 120 -> "بعد ساعة"
    minutes < 180 -> "بعد ساعتين"
    minutes < 660 -> "بعد ${minutes / 60} ساعات"
    else -> "بعد ${minutes / 60} ساعة"
}

/** تنبيه إذا كانت الإشعارات متوقفة، لأن التذكيرات لن تظهر بدونها */
@Composable
private fun ReminderHealthCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tick = AppModel.resumeTick
    val allowed = remember(tick) {
        android.os.Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    if (allowed) return
    Row(
        Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(22.dp)).background(Brand.danger.copy(alpha = 0.12f))
            .border(1.dp, Brand.danger.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .clickable(role = Role.Button) {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.NotificationsOff, null, tint = Brand.danger, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("الإشعارات متوقفة", style = tajawal(18, FontWeight.Bold))
            Text("لن تصلك تذكيرات الأدوية. اضغط هنا لتفعيلها.", style = tajawal(15, color = Brand.secondaryText))
        }
    }
}

@Composable
private fun LowStockCard(medication: Medication, onOpen: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(22.dp)).background(Brand.warning.copy(alpha = 0.12f))
            .border(1.dp, Brand.warning.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClickLabel = "إضافة مخزون", onClick = onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Inventory2, null, tint = Brand.warning, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${medication.name} قارب على النفاد", style = tajawal(18, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("باقي ${medication.stockCount ?: 0} · اضغط إذا اشتريت علبة جديدة", style = tajawal(15, color = Brand.secondaryText))
        }
    }
}

/** بطاقة جرعة: الدواء والوقت والحالة، وزر تسجيل كبير */
@Composable
fun DoseRow(log: DoseLog, state: DoseState, onTake: (() -> Unit)?, onClick: () -> Unit) {
    val (stateText, stateColor) = when (state) {
        DoseState.TAKEN -> (log.takenAt?.let { "أُخذت ${Format.isoTime(it)}" } ?: "أُخذت") to Brand.glow
        DoseState.DUE -> "موعدها الآن" to Brand.glow
        DoseState.LATE -> "متأخرة" to Brand.warning
        DoseState.MISSED -> "فاتت" to Brand.danger
        DoseState.UPCOMING -> "لم يحن موعدها" to Brand.secondaryText
    }
    val taken = state == DoseState.TAKEN
    Row(
        Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp).fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), if (state == DoseState.DUE) 0.5f else 0.22f)
            .clickable(onClickLabel = "تفاصيل ${log.medicationName}", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
            Text(Format.isoTime(log.scheduledAt).substringBefore(' '), style = tajawal(21, FontWeight.Bold, if (taken) Brand.secondaryText else Brand.text))
            Text(Format.isoTime(log.scheduledAt).substringAfter(' '), style = tajawal(14, color = Brand.secondaryText))
        }
        Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(40.dp).background(Brand.text.copy(alpha = 0.12f)))
        Column(Modifier.weight(1f)) {
            Text(log.medicationName, style = tajawal(19, FontWeight.Bold, if (taken) Brand.secondaryText else Brand.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${Format.dose(log.doseAmount, log.doseUnit)} · $stateText", style = tajawal(15, color = stateColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        CheckButton(taken, log.medicationName, onTake)
    }
}

@Composable
private fun CheckButton(taken: Boolean, name: String, onTake: (() -> Unit)?) {
    if (taken) {
        Box(
            Modifier.size(54.dp).shadow(10.dp, CircleShape, ambientColor = Brand.glow, spotColor = Brand.glow).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Brand.glow, Brand.mint))).semantics { contentDescription = "أُخذت" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Check, null, tint = Brand.ink, modifier = Modifier.size(30.dp)) }
    } else if (onTake != null) {
        Box(
            Modifier.size(54.dp).glass(CircleShape, 0.45f).clickable(role = Role.Button, onClickLabel = "أخذت $name", onClick = onTake)
                .semantics { contentDescription = "تسجيل أخذ $name" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Check, null, tint = Brand.glow, modifier = Modifier.size(28.dp)) }
    } else {
        Box(Modifier.size(54.dp).glass(CircleShape, 0.12f), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Medication, null, tint = Brand.secondaryText, modifier = Modifier.size(24.dp))
        }
    }
}

/** لون نص الحالة لاستخدامه خارج هذه الشاشة */
fun stateColor(state: DoseState): Color = when (state) {
    DoseState.TAKEN, DoseState.DUE -> Brand.glow
    DoseState.LATE -> Brand.warning
    DoseState.MISSED -> Brand.danger
    DoseState.UPCOMING -> Brand.secondaryText
}
