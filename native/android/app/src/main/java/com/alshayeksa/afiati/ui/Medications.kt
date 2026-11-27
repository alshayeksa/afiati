package com.alshayeksa.afiati.ui

import com.alshayeksa.afiati.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alshayeksa.afiati.data.Medication

@Composable
fun MedicationsScreen(header: @Composable () -> Unit, onAdd: () -> Unit, onOpen: (Medication) -> Unit) {
    val medications = AppModel.medications
    var refilling by remember { mutableStateOf<Medication?>(null) }
    var stopping by remember { mutableStateOf<Medication?>(null) }

    TabList(header) {
        item(key = "title") {
            BigTitle(
                tr("أدويتي", "My medicines"),
                when (medications.size) {
                    0 -> tr("لا توجد أدوية بعد", "No medicines yet")
                    1 -> tr("دواء واحد نشط · اضغط مطوّلًا لخيارات أكثر", "One active medicine · long-press for more options")
                    2 -> tr("دواءان نشطان · اضغط مطوّلًا لخيارات أكثر", "Two active medicines · long-press for more options")
                    in 3..10 -> tr("${medications.size} أدوية نشطة · اضغط مطوّلًا لخيارات أكثر", "${medications.size} active medicines · long-press for more options")
                    else -> tr("${medications.size} دواءً نشطًا · اضغط مطوّلًا لخيارات أكثر", "${medications.size} active medicines · long-press for more options")
                },
            )
        }
        if (medications.isEmpty()) {
            item(key = "empty") {
                EmptyCard(tr("قائمتك فارغة", "Your list is empty"), tr("أضف أدويتك مرة واحدة، وعافيتي يتولى التذكير والمتابعة.", "Add your medicines once, and Afiati takes care of reminders and follow-up."), tr("إضافة دواء", "Add medicine"), onAdd)
            }
        } else {
            items(medications, key = { it.id }) { medication ->
                SwipeRow(
                    actions = listOf(
                        RowAction(tr("مخزون", "Stock"), tr("أضفت علبة جديدة", "I added a new box"), Icons.Filled.AddShoppingCart, Color(0xFF2F7FBF)) { refilling = medication },
                        RowAction(tr("إيقاف", "Stop"), tr("إيقاف الدواء", "Stop medicine"), Icons.Filled.StopCircle, Brand.danger) { stopping = medication },
                    ),
                    clickLabel = tr("تفاصيل ${medication.name}", "Details of ${medication.name}"),
                    onClick = { onOpen(medication) },
                ) { MedicationCard(medication) }
            }
        }
    }

    refilling?.let { RefillDialog(it, onDismiss = { refilling = null }) }
    stopping?.let { StopDialog(it, onDismiss = { stopping = null }) }
}

@Composable
fun MedicationCard(medication: Medication) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Filled.Medication, Brand.avatarColor(medication.id), size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(medication.name, style = tajawal(20, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Format.dose(medication.doseAmount, medication.doseUnit)} · ${Format.schedule(medication.repeatType, medication.repeatDays, medication.times)}",
                style = tajawal(15, color = Brand.secondaryText), maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            medication.stockCount?.let { stock ->
                Text(
                    if (medication.lowStock) tr("المخزون: $stock · قارب على النفاد", "Stock: $stock · running low") else tr("المخزون: $stock", "Stock: $stock"),
                    style = tajawal(15, FontWeight.Medium, if (medication.lowStock) Brand.warning else Brand.glow),
                )
            }
        }
    }
}

/** تفاصيل الدواء: الجدول والمخزون، وإضافة علبة، وإيقاف الدواء */
@Composable
fun MedicationSheet(medication: Medication, onDismiss: () -> Unit) {
    // أحدث نسخة من الدواء بعد أي تعديل
    val current = AppModel.medications.firstOrNull { it.id == medication.id } ?: medication
    var refilling by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }

    AfiatiSheet(null, onDismiss) {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBadge(Icons.Filled.Medication, Brand.avatarColor(current.id), size = 84.dp)
            Text(current.name, style = tajawal(30, FontWeight.Bold))
            Text(Format.dose(current.doseAmount, current.doseUnit), style = tajawal(19, color = Brand.secondaryText))
        }
        Column(
            Modifier.padding(top = 18.dp).fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoRow(tr("التكرار", "Repeats"), "${Format.repeatTitle(current.repeatType)} · ${Format.times(if (current.repeatType == "daily") current.times.size else current.repeatDays.size)}")
            InfoRow(tr("المواعيد", "Times"), Format.schedule(current.repeatType, current.repeatDays, current.times))
            InfoRow(tr("المخزون", "Stock"), current.stockCount?.let { if (current.lowStock) tr("$it · قارب على النفاد", "$it · running low") else "$it" } ?: tr("لا يُتابع", "Not tracked"))
        }
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(tr("أضفت علبة جديدة", "I added a new box"), icon = Icons.Filled.AddShoppingCart) { refilling = true }
            GlassButton(tr("إيقاف هذا الدواء", "Stop this medicine"), icon = Icons.Filled.StopCircle, color = Brand.danger) { stopping = true }
        }
    }
    if (refilling) RefillDialog(current, onDismiss = { refilling = false })
    if (stopping) StopDialog(current, onDismiss = { stopping = false }, onStopped = onDismiss)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = tajawal(15, color = Brand.secondaryText))
        Text(value, style = tajawal(18, FontWeight.Medium))
    }
}

@Composable
fun RefillDialog(medication: Medication, onDismiss: () -> Unit) {
    var amount by remember { mutableIntStateOf(30) }
    AfiatiDialog(
        title = tr("كم أضفت إلى ${medication.name}؟", "How many did you add to ${medication.name}?"),
        confirm = tr("إضافة", "Add"), onConfirm = { AppModel.refill(medication, amount); onDismiss() },
        dismiss = tr("إلغاء", "Cancel"), onDismiss = onDismiss,
    ) {
        Stepper(
            "$amount", onMinus = { amount = (amount - 1).coerceAtLeast(1) }, onPlus = { amount = (amount + 1).coerceAtMost(1000) },
            minusEnabled = amount > 1, caption = medication.doseUnit.takeIf { it != "other" },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 20, 30, 60).forEach { preset ->
                Chip("$preset", amount == preset, Modifier.weight(1f)) { amount = preset }
            }
        }
    }
}

@Composable
private fun StopDialog(medication: Medication, onDismiss: () -> Unit, onStopped: () -> Unit = {}) {
    AfiatiDialog(
        title = tr("إيقاف ${medication.name}؟", "Stop ${medication.name}?"),
        text = tr("لن تصلك تذكيراته بعد الآن، ويبقى ما سجّلته منه في السجل. إذا احتجته لاحقًا أضفه من جديد.", "You won't get its reminders anymore, and what you logged stays in your history. If you need it later, add it again."),
        confirm = tr("إيقاف", "Stop"), destructive = true,
        onConfirm = { AppModel.stopMedication(medication); onDismiss(); onStopped() },
        dismiss = tr("إلغاء", "Cancel"), onDismiss = onDismiss,
    )
}
