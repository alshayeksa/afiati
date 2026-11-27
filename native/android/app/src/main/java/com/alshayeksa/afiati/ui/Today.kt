package com.alshayeksa.afiati.ui

import com.alshayeksa.afiati.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
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
private enum class Period(private val ar: String, private val en: String, val icon: ImageVector) {
    MORNING("الصباح", "Morning", Icons.Outlined.WbSunny),
    NOON("الظهر", "Noon", Icons.Outlined.LightMode),
    EVENING("المساء", "Evening", Icons.Outlined.WbTwilight),
    NIGHT("الليل", "Night", Icons.Outlined.DarkMode);

    val title get() = tr(ar, en)
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
fun TodayScreen(header: @Composable () -> Unit, onAdd: () -> Unit, onOpenMedication: (Medication) -> Unit, onHelp: () -> Unit, onVitals: () -> Unit) {
    val now = rememberNow()
    val logs = AppModel.todayLogs
    val medications = AppModel.medications
    var confirmEarly by remember { mutableStateOf<DoseLog?>(null) }
    val take: (DoseLog) -> Unit = { log -> if (AppModel.isEarly(log, now)) confirmEarly = log else AppModel.takeDose(log) }

    TabList(header) {
        item(key = "greeting") { Greeting(onVitals) }
        // موجة النبض: هوية عافيتي، مدمجة تحت التحية
        item(key = "pulse-wave") { PulseWave(Modifier.fillMaxWidth().padding(top = 2.dp).height(84.dp)) }
        // زر المساعدة في صفحة «العائلة» حتى لا يُضغط بالخطأ؛ هنا يظهر فقط أثناء طلب جارٍ
        if (AppModel.myHelp != null) item(key = "help-active") { HelpCard(onHelp) }

        if (medications.isEmpty() && logs.isEmpty()) {
            item(key = "empty") {
                EmptyCard(
                    tr("أضف دواءك الأول", "Add your first medicine"),
                    tr("اكتب اسم الدواء ومواعيده، وسنذكّرك بكل جرعة في وقتها حتى بدون إنترنت.", "Type the medicine name and times, and we'll remind you of every dose on time, even offline."),
                    tr("إضافة دواء", "Add medicine"), onAdd,
                )
            }
            return@TabList
        }

        item(key = "health") { ReminderHealthCard() }

        // جرعات اليوم أولًا: هي ما يحتاج الكبير أن يسجّله
        item(key = "doses-title") { SectionTitle(tr("جرعات اليوم", "Today's doses"), Modifier.padding(top = 0.dp)) }
        if (logs.isEmpty()) {
            item(key = "no-doses") {
                EmptyCard(tr("لا جرعات اليوم", "No doses today"), tr("أدويتك الأسبوعية أو الشهرية ليس لها موعد اليوم.", "Your weekly or monthly medicines aren't scheduled today."))
            }
        }
        logs.groupBy(::period).toSortedMap().forEach { (period, group) ->
            item(key = "period-${period.name}") {
                Row(Modifier.padding(horizontal = 22.dp).padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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

        val low = medications.filter { it.lowStock }
        items(low, key = { "low-" + it.id }) { medication -> LowStockCard(medication) { onOpenMedication(medication) } }

        // التزام اليوم في الآخر
        item(key = "progress") { ProgressCard(logs, now) }
    }

    confirmEarly?.let { log ->
        AfiatiDialog(
            title = tr("موعدها لم يحن بعد", "It's not time yet"),
            text = tr("جرعة ${log.medicationName} موعدها ${Format.isoTime(log.scheduledAt)}. هل أخذتها فعلًا الآن؟", "${log.medicationName} is due at ${Format.isoTime(log.scheduledAt)}. Did you really take it now?"),
            confirm = tr("نعم، أخذتها", "Yes, I took it"), onConfirm = { AppModel.takeDose(log); confirmEarly = null },
            dismiss = tr("لا", "No"), onDismiss = { confirmEarly = null },
        )
    }
}

@Composable
private fun Greeting(onVitals: () -> Unit) {
    val sync = AppModel.sync
    val status: Pair<Color, String>? = when {
        sync.running -> null
        sync.status == SyncStatus.FAILED -> Brand.danger to tr("محفوظ على جوالك · تعذّرت المزامنة", "Saved on your phone · sync failed")
        sync.status == SyncStatus.OFFLINE && sync.pending > 0 -> Brand.warning to tr("محفوظ على جوالك · يُرفع عند عودة الإنترنت", "Saved on your phone · uploads when you're back online")
        sync.status == SyncStatus.OFFLINE -> Brand.warning to tr("بدون إنترنت · كل شيء يعمل", "Offline · everything still works")
        else -> null
    }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp).padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // الاسم في الشريط العلوي، فهنا تحية اليوم من جدول التحيات
            Text(Greetings.forDay(AppModel.today).trimEnd('.', '،', ' '), style = tajawal(25, FontWeight.Bold, Brand.glow))
            Text("${Format.gregorianDay(AppModel.today)} · ${Format.hijriDay(AppModel.today)}", style = tajawal(15, color = Brand.secondaryText))
            // في الحالة العادية لا داعي لسطر «محفوظ»؛ يظهر فقط حين يحتاج انتباهًا
            status?.let { (color, text) -> StatusLine(color, text, Modifier.padding(top = 4.dp)) }
        }
        Spacer(Modifier.width(10.dp))
        // رابط صغير للعلامات الحيوية، يسار التحية
        VitalsLink(onVitals)
    }
}

@Composable
private fun ProgressCard(logs: List<DoseLog>, now: Instant) {
    val total = logs.size
    val done = logs.count { AppModel.isTaken(it) }
    val late = logs.count { doseState(it, now, AppModel.isTaken(it)).let { s -> s == DoseState.LATE || s == DoseState.MISSED } }
    val message = when {
        total == 0 -> tr("لا توجد جرعات اليوم", "No doses today")
        done == total -> tr("ممتاز! أكملت جرعات اليوم", "Excellent! You've completed today's doses")
        late > 0 -> if (late == 1) tr("جرعة واحدة متأخرة", "One dose is late") else tr("$late جرعات متأخرة", "$late doses are late")
        done == 0 -> tr("يومك يبدأ الآن", "Your day starts now")
        else -> tr("أحسنت، استمر", "Well done, keep going")
    }
    if (total == 0) return
    val progress = done.toFloat() / total
    Column(
        Modifier.padding(horizontal = 14.dp).padding(top = 6.dp, bottom = 14.dp).fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = tajawal(18, FontWeight.Bold), modifier = Modifier.weight(1f))
            Text(tr("أخذت $done من $total", "Took $done of $total"), style = tajawal(16, FontWeight.Medium, if (late > 0) Brand.warning else Brand.glow))
        }
        // شريط التقدم: يمتلئ من اليمين مع اتجاه القراءة
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Brand.text.copy(alpha = 0.08f))) {
            Box(
                Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(Brand.glow, Brand.mint))),
            )
        }
    }
}

private fun inTime(minutes: Long): String = when {
    minutes < 60 -> tr("بعد ${minutes.coerceAtLeast(1)} دقيقة", "in ${minutes.coerceAtLeast(1)} min")
    minutes < 120 -> tr("بعد ساعة", "in an hour")
    minutes < 180 -> tr("بعد ساعتين", "in 2 hours")
    minutes < 660 -> tr("بعد ${minutes / 60} ساعات", "in ${minutes / 60} hours")
    else -> tr("بعد ${minutes / 60} ساعة", "in ${minutes / 60} hours")
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
            Text(tr("الإشعارات متوقفة", "Notifications are off"), style = tajawal(18, FontWeight.Bold))
            Text(tr("لن تصلك تذكيرات الأدوية. اضغط هنا لتفعيلها.", "You won't get medicine reminders. Tap here to turn them on."), style = tajawal(15, color = Brand.secondaryText))
        }
    }
}

@Composable
private fun LowStockCard(medication: Medication, onOpen: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(22.dp)).background(Brand.warning.copy(alpha = 0.12f))
            .border(1.dp, Brand.warning.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClickLabel = tr("إضافة مخزون", "Add stock"), onClick = onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Inventory2, null, tint = Brand.warning, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("${medication.name} قارب على النفاد", "${medication.name} is running low"), style = tajawal(18, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tr("باقي ${medication.stockCount ?: 0} · اضغط إذا اشتريت علبة جديدة", "${medication.stockCount ?: 0} left · tap if you bought a new box"), style = tajawal(15, color = Brand.secondaryText))
        }
    }
}

/** بطاقة جرعة: الدواء والوقت والحالة، وزر تسجيل كبير */
@Composable
fun DoseRow(log: DoseLog, state: DoseState, onTake: (() -> Unit)?, onClick: () -> Unit) {
    val (stateText, stateColor) = when (state) {
        DoseState.TAKEN -> (log.takenAt?.let { tr("أُخذت ${Format.isoTime(it)}", "Taken ${Format.isoTime(it)}") } ?: tr("أُخذت", "Taken")) to Brand.glow
        DoseState.DUE -> tr("موعدها الآن", "Due now") to Brand.glow
        DoseState.LATE -> tr("متأخرة", "Late") to Brand.warning
        DoseState.MISSED -> tr("فاتت", "Missed") to Brand.danger
        DoseState.UPCOMING -> runCatching { Duration.between(Instant.now(), Iso.parse(log.scheduledAt)).toMinutes() }.getOrNull()
            ?.takeIf { it in 0..(12 * 60) }?.let(::inTime).orEmpty().ifEmpty { tr("لم يحن موعدها", "Not yet due") } to Brand.secondaryText
    }
    val taken = state == DoseState.TAKEN
    Row(
        Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp).fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), if (state == DoseState.DUE) 0.5f else 0.22f)
            .clickable(onClickLabel = tr("تفاصيل ${log.medicationName}", "Details of ${log.medicationName}"), onClick = onClick)
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
            if (taken) {
                Text(Format.dose(log.doseAmount, log.doseUnit), style = tajawal(15, color = Brand.secondaryText), maxLines = 1, overflow = TextOverflow.Ellipsis)
                // تهنئة صغيرة مميزة داخل مربع الدواء نفسه
                Text(
                    tr("أحسنت · أخذتها", "Well done · taken") + (log.takenAt?.let { " " + Format.isoTime(it) } ?: ""),
                    style = tajawal(13, FontWeight.Bold, Brand.mint), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text("${Format.dose(log.doseAmount, log.doseUnit)} · $stateText", style = tajawal(15, color = stateColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
                .background(Brush.verticalGradient(listOf(Brand.glow, Brand.mint))).semantics { contentDescription = tr("أُخذت", "Taken") },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Check, null, tint = Brand.ink, modifier = Modifier.size(30.dp)) }
    } else if (onTake != null) {
        Box(
            Modifier.size(54.dp).glass(CircleShape, 0.45f).clickable(role = Role.Button, onClickLabel = tr("أخذت $name", "I took $name"), onClick = onTake)
                .semantics { contentDescription = tr("تسجيل أخذ $name", "Mark $name as taken") },
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
