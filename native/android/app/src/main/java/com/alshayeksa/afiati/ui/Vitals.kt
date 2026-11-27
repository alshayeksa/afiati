package com.alshayeksa.afiati.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.alshayeksa.afiati.data.HeartRateRepository
import com.alshayeksa.afiati.data.SugarContext
import com.alshayeksa.afiati.data.VitalRanges
import com.alshayeksa.afiati.data.VitalReading
import com.alshayeksa.afiati.data.VitalStatus
import com.alshayeksa.afiati.data.VitalType
import com.alshayeksa.afiati.data.VitalsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** يزيد بعد كل حفظ أو حذف، فتتحدث البطاقات */
object VitalsStore {
    var version by mutableIntStateOf(0)
}

/** لون الحالة: الطبيعي بلون التطبيق، القريب من الحد برتقالي، والبعيد أحمر */
fun statusColor(status: VitalStatus): Color = when (status) {
    VitalStatus.NORMAL -> Brand.mint
    VitalStatus.ELEVATED, VitalStatus.LOW -> Brand.warning
    else -> Brand.danger
}

private fun icon(type: VitalType): ImageVector = when (type) {
    VitalType.PULSE -> Icons.Filled.MonitorHeart
    VitalType.PRESSURE -> Icons.Filled.Favorite
    VitalType.SUGAR -> Icons.Filled.Bloodtype
    VitalType.TEMPERATURE -> Icons.Filled.Thermostat
}

private fun tint(type: VitalType): Color = when (type) {
    VitalType.PULSE -> Color(0xFFFF6B8A)
    VitalType.PRESSURE -> Color(0xFFE5484D)
    VitalType.SUGAR -> Color(0xFFB28CFF)
    VitalType.TEMPERATURE -> Color(0xFFFFA62B)
}

/** آخر قراءة لكل علامة، كما تُعرض في البطاقة */
private data class Latest(val value: String, val status: VitalStatus, val time: String, val note: String?)

/** الرابط الصغير يسار التحية */
@Composable
fun VitalsLink(onOpen: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClickLabel = "العلامات الحيوية", onClick = onOpen)
            .glass(RoundedCornerShape(18.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.MonitorHeart, null, tint = Brand.glow, modifier = Modifier.size(24.dp))
        Text("العلامات", style = tajawal(12, FontWeight.Medium, Brand.secondaryText))
        Text("الحيوية", style = tajawal(12, FontWeight.Medium, Brand.secondaryText))
    }
}

/** العلامات الحيوية: النبض بالكاميرا، والضغط والسكر والحرارة إدخال يدوي */
@Composable
fun VitalsScreen(onClose: () -> Unit, onPulse: () -> Unit) {
    var entry by remember { mutableStateOf<VitalType?>(null) }
    BackHandler(enabled = entry == null, onBack = onClose)
    val patientId = AppModel.patient?.id
    val latest by produceState(emptyMap<VitalType, Latest>(), patientId, VitalsStore.version, PulseStore.version) {
        value = if (patientId == null) emptyMap() else withContext(Dispatchers.IO) {
            buildMap {
                runCatching { HeartRateRepository.recent(patientId, 1).firstOrNull() }.getOrNull()?.let {
                    put(VitalType.PULSE, Latest("${it.bpm}", VitalRanges.pulse(it.bpm), readingTime(it.measuredAt), "بالكاميرا"))
                }
                for (type in listOf(VitalType.PRESSURE, VitalType.SUGAR, VitalType.TEMPERATURE)) {
                    runCatching { VitalsRepository.recent(patientId, type, 1).firstOrNull() }.getOrNull()?.let {
                        put(type, Latest(VitalRanges.display(it), VitalRanges.of(it), readingTime(it.measuredAt),
                            if (type == VitalType.SUGAR) SugarContext.of(it.context).title else null))
                    }
                }
            }
        }
    }

    FullScreen {
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Row(Modifier.fillMaxWidth().padding(top = top + 10.dp).padding(start = 20.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("العلامات الحيوية", style = tajawal(26, FontWeight.Bold), modifier = Modifier.weight(1f))
            Text(
                "إغلاق", style = tajawal(18, FontWeight.Medium, Brand.glow),
                modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onClose).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("اضغط على أي علامة لتسجّل قراءة جديدة أو ترى قراءاتك السابقة.", style = tajawal(15, color = Brand.secondaryText), modifier = Modifier.padding(horizontal = 6.dp))
            listOf(VitalType.PULSE to VitalType.PRESSURE, VitalType.SUGAR to VitalType.TEMPERATURE).forEach { (a, b) ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VitalCard(a, latest[a], Modifier.weight(1f).fillMaxHeight()) { if (a == VitalType.PULSE) onPulse() else entry = a }
                    VitalCard(b, latest[b], Modifier.weight(1f).fillMaxHeight()) { if (b == VitalType.PULSE) onPulse() else entry = b }
                }
            }
            Text(
                "للمتابعة فقط، وليست تشخيصًا طبيًا. القراءات محفوظة على جوالك.",
                style = tajawal(14, color = Brand.secondaryText), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
    entry?.let { type -> VitalEntrySheet(type, onDismiss = { entry = null }) }
}

@Composable
private fun VitalCard(type: VitalType, latest: Latest?, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.glass(RoundedCornerShape(24.dp)).clickable(role = Role.Button, onClickLabel = type.title, onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tint(type).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon(type), null, tint = tint(type), modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(type.title, style = tajawal(17, FontWeight.Bold), modifier = Modifier.weight(1f))
        }
        if (latest == null) {
            Text("—", style = tajawal(30, FontWeight.Bold, Brand.secondaryText))
            Text(if (type == VitalType.PULSE) "اضغط للقياس بالكاميرا" else "اضغط لتسجيل قراءة", style = tajawal(14, color = Brand.glow))
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(latest.value, style = tajawal(30, FontWeight.Bold))
                Spacer(Modifier.width(4.dp))
                Text(type.unit, style = tajawal(13, color = Brand.secondaryText), modifier = Modifier.padding(bottom = 6.dp))
            }
            StatusChip(latest.status)
            Text(listOfNotNull(latest.note, latest.time).joinToString(" · "), style = tajawal(13, color = Brand.secondaryText))
        }
        Spacer(Modifier.weight(1f))
        Text(VitalRanges.normalText(type), style = tajawal(12, color = Brand.secondaryText))
    }
}

@Composable
fun StatusChip(status: VitalStatus) {
    val color = statusColor(status)
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(status.title, style = tajawal(13, FontWeight.Bold, color))
    }
}

/** تسجيل قراءة يدوية بخانات كبيرة، وتحتها آخر القراءات */
@Composable
private fun VitalEntrySheet(type: VitalType, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val patientId = AppModel.patient?.id
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var sugarContext by remember { mutableStateOf(SugarContext.FASTING) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<VitalReading?>(null) }
    val history by produceState(emptyList<VitalReading>(), patientId, type, VitalsStore.version) {
        value = if (patientId == null) emptyList() else withContext(Dispatchers.IO) { runCatching { VitalsRepository.recent(patientId, type, 5) }.getOrDefault(emptyList()) }
    }
    fun number(text: String) = text.trim().replace('٫', '.').replace(',', '.').let { digits ->
        // الأرقام العربية الهندية إلى لاتينية
        digits.map { c -> if (c in '٠'..'٩') '0' + (c - '٠') else c }.joinToString("").toDoubleOrNull()
    }
    val numbers = KeyboardOptions(keyboardType = if (type == VitalType.TEMPERATURE) KeyboardType.Decimal else KeyboardType.Number)

    AfiatiSheet(type.title, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (type) {
                VitalType.PRESSURE -> {
                    Text("اكتب الرقمين كما يظهران في جهاز الضغط", style = tajawal(16, color = Brand.secondaryText))
                    // من اليسار إلى اليمين كما يُكتب الضغط وكما يظهر في الجهاز: 120 / 80
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("الانقباضي (الأعلى)", style = tajawal(15, color = Brand.secondaryText))
                                GlassField(first, { first = it; error = null }, "120", style = tajawal(26, FontWeight.Bold), centered = true, keyboard = numbers, ltr = true)
                            }
                            Text("/", style = tajawal(30, FontWeight.Bold, Brand.secondaryText), modifier = Modifier.padding(top = 26.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("الانبساطي (الأدنى)", style = tajawal(15, color = Brand.secondaryText))
                                GlassField(second, { second = it; error = null }, "80", style = tajawal(26, FontWeight.Bold), centered = true, keyboard = numbers, ltr = true)
                            }
                        }
                    }
                }
                VitalType.SUGAR -> {
                    FieldLabel("متى قست السكر؟")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SugarContext.entries.forEach { context ->
                            Chip(context.short, sugarContext == context, Modifier.weight(1f)) { sugarContext = context }
                        }
                    }
                    Text(sugarContext.hint, style = tajawal(14, color = Brand.secondaryText))
                    FieldLabel("القراءة (${type.unit})")
                    GlassField(first, { first = it; error = null }, "100", style = tajawal(26, FontWeight.Bold), centered = true, keyboard = numbers, ltr = true)
                }
                else -> {
                    FieldLabel("القراءة (${type.unit})")
                    GlassField(first, { first = it; error = null }, "36.8", style = tajawal(26, FontWeight.Bold), centered = true, keyboard = numbers, ltr = true)
                }
            }
            Text(VitalRanges.normalText(type, sugarContext), style = tajawal(14, color = Brand.secondaryText))
            error?.let { Text(it, style = tajawal(16, FontWeight.Medium, Brand.danger)) }
            PrimaryButton("حفظ القراءة", Modifier.padding(top = 4.dp), icon = Icons.Filled.Save, enabled = first.isNotBlank() && (type != VitalType.PRESSURE || second.isNotBlank())) {
                val a = number(first)
                val b = number(second)
                error = when (type) {
                    VitalType.PRESSURE -> when {
                        a == null || b == null -> "اكتب الرقمين بالأرقام فقط."
                        a !in VitalRanges.systolicRange || b !in VitalRanges.diastolicRange -> "تأكد من الرقمين؛ يبدو أن فيهما خطأ كتابيًا."
                        a <= b -> "الرقم الأعلى (الانقباضي) يجب أن يكون أكبر من الأدنى."
                        else -> null
                    }
                    VitalType.SUGAR -> if (a == null || a !in VitalRanges.sugarRange) "اكتب قراءة السكر كما في الجهاز (بين 20 و600)." else null
                    else -> if (a == null || a !in VitalRanges.temperatureRange) "اكتب درجة الحرارة بالمئوية، مثل 36.8" else null
                }
                if (error == null && patientId != null) {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                VitalsRepository.save(patientId, type, a!!, if (type == VitalType.PRESSURE) b else null, if (type == VitalType.SUGAR) sugarContext.key else null)
                            }
                        }.onSuccess { saved ->
                            VitalsStore.version++
                            val status = VitalRanges.of(saved)
                            AppModel.toast = "حُفظت القراءة · ${status.title}"
                            first = ""; second = ""
                        }.onFailure { error = "تعذّر حفظ القراءة." }
                    }
                }
            }

            if (history.isNotEmpty()) {
                FieldLabel("آخر القراءات", Modifier.padding(top = 10.dp))
                history.forEach { reading ->
                    val status = VitalRanges.of(reading)
                    Row(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp))
                            .clickable(onClickLabel = "حذف القراءة") { deleting = reading }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(VitalRanges.display(reading), style = tajawal(22, FontWeight.Bold))
                                Spacer(Modifier.width(4.dp))
                                Text(type.unit, style = tajawal(12, color = Brand.secondaryText), modifier = Modifier.padding(bottom = 4.dp))
                            }
                            Text(
                                listOfNotNull(if (type == VitalType.SUGAR) SugarContext.of(reading.context).title else null, readingTime(reading.measuredAt)).joinToString(" · "),
                                style = tajawal(13, color = Brand.secondaryText),
                            )
                        }
                        StatusChip(status)
                    }
                    VitalRanges.advice(status)?.takeIf { reading == history.first() }?.let {
                        Text(it, style = tajawal(14, color = statusColor(status)))
                    }
                }
                Text("اضغط على قراءة لحذفها إن سُجّلت بالخطأ.", style = tajawal(13, color = Brand.secondaryText))
            }
        }
    }

    deleting?.let { reading ->
        AfiatiDialog(
            title = "حذف القراءة؟",
            text = "${type.title}: ${VitalRanges.display(reading)} ${type.unit} · ${readingTime(reading.measuredAt)}",
            confirm = "حذف", destructive = true,
            onConfirm = {
                scope.launch { withContext(Dispatchers.IO) { runCatching { VitalsRepository.delete(reading.id) } }; VitalsStore.version++ }
                deleting = null
            },
            dismiss = "إلغاء", onDismiss = { deleting = null },
        )
    }
}
