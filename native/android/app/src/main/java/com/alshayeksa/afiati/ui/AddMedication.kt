package com.alshayeksa.afiati.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alshayeksa.afiati.data.MedicationDraft
import com.alshayeksa.afiati.data.RepeatType
import com.alshayeksa.afiati.data.Schedule
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private val quickSuggestions = listOf("دواء السكري", "دواء الضغط", "مسكن", "دواء القلب", "دواء المعدة")
private val categories = listOf(
    "فيتامينات" to listOf("فيتامين د", "فيتامين ب", "فيتامين سي", "كالسيوم", "حديد", "مغنيسيوم"),
    "الحساسية" to listOf("حبوب الحساسية", "بخاخ الأنف", "قطرة الحساسية", "شراب الحساسية"),
    "الكوليسترول" to listOf("دواء الكوليسترول", "منظم الدهون"),
    "العين" to listOf("قطرة العين", "قطرة مرطبة", "قطرة ضغط العين", "مرهم العين"),
    "الربو" to listOf("بخاخ الربو", "بخاخ توسيع الشعب", "بخاخ وقائي"),
    "المفاصل" to listOf("دواء المفاصل", "مسكن المفاصل", "كريم موضعي", "مضاد التهاب"),
)
private val units = listOf("حبة", "مل", "قطرة", "بخة")
private val doseLabels = listOf("الجرعة الأولى", "الجرعة الثانية", "الجرعة الثالثة", "الجرعة الرابعة")
private const val MAX_FREQUENCY = 4
private const val STEPS = 6

/** أوقات مقترحة موزعة على اليوم حسب عدد المرات */
private fun defaultTimes(count: Int): List<String> = when (count) {
    1 -> listOf("08:00")
    2 -> listOf("08:00", "20:00")
    3 -> listOf("08:00", "14:00", "20:00")
    else -> listOf("08:00", "12:00", "16:00", "20:00")
}

/**
 * إضافة دواء في ست خطوات قصيرة، سؤال واحد في كل خطوة:
 * الاسم، الجرعة، التكرار، المواعيد، المخزون، ثم المراجعة.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddMedicationScreen(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableDoubleStateOf(1.0) }
    var unit by remember { mutableStateOf("حبة") }
    var repeatType by remember { mutableStateOf(RepeatType.DAILY) }
    var frequency by remember { mutableIntStateOf(1) }
    val times = remember { mutableStateListOf("08:00") }
    val days = remember { mutableStateListOf<Int>() }
    var trackStock by remember { mutableStateOf(true) }
    var stock by remember { mutableIntStateOf(30) }
    var editingTime by remember { mutableStateOf<Int?>(null) }
    var showMore by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var duplicateOf by remember { mutableStateOf<String?>(null) }
    var confirmClose by remember { mutableStateOf(false) }

    fun selectRepeat(type: String) {
        repeatType = type
        frequency = 1
        times.clear(); times.add("08:00")
        days.clear()
        val today = LocalDate.now()
        when (type) {
            RepeatType.WEEKLY -> days.add(Schedule.weekday(today))
            RepeatType.MONTHLY -> days.add(today.dayOfMonth)
        }
    }

    fun changeFrequency(value: Int) {
        frequency = value.coerceIn(1, MAX_FREQUENCY)
        if (repeatType == RepeatType.DAILY) {
            times.clear(); times.addAll(defaultTimes(frequency))
        } else {
            while (days.size > frequency) days.removeAt(0)
        }
    }

    fun toggleDay(day: Int) {
        if (day in days) days.remove(day)
        else {
            if (days.size >= frequency) days.removeAt(0)
            days.add(day)
        }
    }

    fun draft() = MedicationDraft(
        name = name.trim(), doseAmount = dose, doseUnit = unit, repeatType = repeatType,
        repeatDays = if (repeatType == RepeatType.DAILY) emptyList() else days.sorted(),
        frequency = frequency,
        times = if (repeatType == RepeatType.DAILY) times.take(frequency) else times.take(1),
        stockCount = if (trackStock) stock else null,
    )

    fun save(force: Boolean = false) {
        scope.launch {
            val value = draft()
            if (!force) {
                val duplicate = AppModel.findDuplicate(value)
                if (duplicate != null) {
                    duplicateOf = duplicate.name
                    return@launch
                }
            }
            AppModel.addMedication(value)
            onClose()
        }
    }

    fun next() {
        error = when {
            step == 1 && name.isBlank() -> "اكتب اسم الدواء أولًا، أو اختر من الاقتراحات."
            step == 4 && repeatType != RepeatType.DAILY && days.size != frequency ->
                "اختر ${if (frequency == 1) "يومًا واحدًا" else "$frequency أيام"}."
            else -> null
        }
        if (error != null) return
        if (step < STEPS) step++ else save()
    }

    val dirty = name.isNotBlank() || step > 1
    BackHandler {
        when {
            step > 1 -> step--
            dirty -> confirmClose = true
            else -> onClose()
        }
    }

    FullScreen {
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        // الأعلى: إغلاق، العنوان، رقم الخطوة، وشريط التقدم
        Row(Modifier.fillMaxWidth().padding(top = top + 10.dp, start = 14.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).glass(CircleShape).clickable(role = Role.Button, onClickLabel = "إغلاق") { if (dirty) confirmClose = true else onClose() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, "إغلاق", tint = Brand.text, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("إضافة دواء", style = tajawal(22, FontWeight.Bold))
                Text("الخطوة $step من $STEPS", style = tajawal(15, color = Brand.secondaryText))
            }
        }
        val progress by animateFloatAsState(step / STEPS.toFloat(), label = "step")
        Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(Brand.text.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(progress).height(8.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Brand.mint, Brand.glow))))
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (step) {
                1 -> {
                    StepTitle("ما اسم الدواء؟", "اكتبه كما هو على العلبة، أو اختر وصفًا قريبًا.")
                    GlassField(
                        name, { name = it; error = null }, "اسم الدواء",
                        style = tajawal(22, FontWeight.Medium),
                        keyboard = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    FieldLabel("اقتراحات سريعة")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        quickSuggestions.forEach { item -> Chip(item, name == item) { name = item; error = null } }
                    }
                    GlassButton(
                        if (showMore) "إخفاء التصنيفات" else "تصنيفات أخرى",
                        icon = if (showMore) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    ) { showMore = !showMore; category = null }
                    if (showMore) {
                        val active = categories.firstOrNull { it.first == category }
                        if (active == null) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                categories.forEach { (title, _) -> Chip(title, false) { category = title } }
                            }
                        } else {
                            FieldLabel("اختر النوع · ${active.first}")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                active.second.forEach { item -> Chip(item, name == item) { name = item; error = null } }
                            }
                            GlassButton("رجوع إلى التصنيفات", icon = Icons.AutoMirrored.Filled.ArrowBack) { category = null }
                        }
                    }
                }
                2 -> {
                    StepTitle("كم تأخذ في كل مرة؟", "الكمية في الجرعة الواحدة.")
                    Card {
                        Stepper(
                            Format.number(dose), onMinus = { dose = (dose - 0.5).coerceAtLeast(0.5) }, onPlus = { dose = (dose + 0.5).coerceAtMost(20.0) },
                            minusEnabled = dose > 0.5, plusEnabled = dose < 20.0, caption = Format.dose(dose, unit),
                        )
                    }
                    FieldLabel("الوحدة")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        units.forEach { item -> Chip(item, unit == item, Modifier.weight(1f)) { unit = item } }
                    }
                }
                3 -> {
                    StepTitle("كم مرة تأخذه؟", null)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RepeatOption("يوميًا", Icons.Filled.Today, repeatType == RepeatType.DAILY, Modifier.weight(1f)) { selectRepeat(RepeatType.DAILY) }
                        RepeatOption("أسبوعيًا", Icons.Filled.DateRange, repeatType == RepeatType.WEEKLY, Modifier.weight(1f)) { selectRepeat(RepeatType.WEEKLY) }
                        RepeatOption("شهريًا", Icons.Filled.CalendarMonth, repeatType == RepeatType.MONTHLY, Modifier.weight(1f)) { selectRepeat(RepeatType.MONTHLY) }
                    }
                    Card {
                        Text(
                            when (repeatType) {
                                RepeatType.WEEKLY -> "كم يومًا في الأسبوع؟"
                                RepeatType.MONTHLY -> "كم يومًا في الشهر؟"
                                else -> "كم مرة في اليوم؟"
                            },
                            style = tajawal(19, FontWeight.Bold),
                        )
                        Spacer(Modifier.height(10.dp))
                        Stepper(
                            "$frequency", onMinus = { changeFrequency(frequency - 1) }, onPlus = { changeFrequency(frequency + 1) },
                            minusEnabled = frequency > 1, plusEnabled = frequency < MAX_FREQUENCY, caption = Format.times(frequency),
                        )
                    }
                }
                4 -> when (repeatType) {
                    RepeatType.DAILY -> {
                        StepTitle("متى تأخذه؟", "اضغط على الوقت لتغييره.")
                        times.take(frequency).forEachIndexed { index, time ->
                            TimeRow(doseLabels[index], time) { editingTime = index }
                        }
                    }
                    RepeatType.WEEKLY -> {
                        StepTitle("في أي أيام الأسبوع؟", "اختر ${if (frequency == 1) "يومًا واحدًا" else "$frequency أيام"}.")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                            Format.weekOrder.forEach { day ->
                                Chip(Format.weekdayNames[day].orEmpty(), day in days, Modifier.weight(1f)) { toggleDay(day); error = null }
                            }
                        }
                        TimeRow("وقت الدواء", times[0]) { editingTime = 0 }
                    }
                    else -> {
                        StepTitle("في أي أيام الشهر؟", "اختر ${if (frequency == 1) "يومًا واحدًا" else "$frequency أيام"}.")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 7) {
                            (1..31).forEach { day ->
                                DayNumber(day, day in days, Modifier.weight(1f)) { toggleDay(day); error = null }
                            }
                        }
                        if (days.any { it > 28 }) Text("في الأشهر الأقصر لا تُذكَّر في الأيام غير الموجودة.", style = tajawal(15, color = Brand.secondaryText))
                        TimeRow("وقت الدواء", times[0]) { editingTime = 0 }
                    }
                }
                5 -> {
                    StepTitle("كم لديك منه الآن؟", "نذكّرك قبل أن ينفد.")
                    Row(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).clickable(role = Role.Switch) { trackStock = !trackStock }.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("تنبيهي عند قرب النفاد", style = tajawal(18, FontWeight.Medium), modifier = Modifier.weight(1f))
                        Switch(
                            trackStock, { trackStock = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Brand.mint, checkedThumbColor = Brand.text, uncheckedTrackColor = Brand.text.copy(alpha = 0.1f)),
                        )
                    }
                    if (trackStock) {
                        Card {
                            Stepper(
                                "$stock", onMinus = { stock = (stock - 1).coerceAtLeast(0) }, onPlus = { stock = (stock + 1).coerceAtMost(1000) },
                                minusEnabled = stock > 0, caption = unit,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 20, 30, 60, 90).forEach { preset -> Chip("$preset", stock == preset, Modifier.weight(1f)) { stock = preset } }
                        }
                    }
                }
                else -> {
                    StepTitle("تأكد من البيانات", "اضغط «حفظ الدواء» إذا كان كل شيء صحيحًا.")
                    val value = draft()
                    Card {
                        Text(value.name, style = tajawal(28, FontWeight.Bold))
                        Text(Format.dose(value.doseAmount, value.doseUnit), style = tajawal(19, color = Brand.secondaryText))
                        Spacer(Modifier.height(10.dp))
                        ReviewLine(Icons.Filled.Schedule, Format.schedule(value.repeatType, value.repeatDays, value.times)) { step = 4 }
                        ReviewLine(Icons.Filled.Today, "${Format.repeatTitle(value.repeatType)} · ${Format.times(value.frequency)}") { step = 3 }
                        ReviewLine(Icons.Filled.Check, value.stockCount?.let { "المخزون: $it" } ?: "بدون متابعة المخزون") { step = 5 }
                    }
                }
            }
            error?.let { Text(it, style = tajawal(17, FontWeight.Medium, Brand.danger)) }
            Spacer(Modifier.height(8.dp))
        }

        // الأسفل: التالي والسابق
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                if (step == STEPS) "حفظ الدواء" else "التالي", Modifier.weight(1f),
                icon = if (step == STEPS) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
            ) { next() }
            if (step > 1) GlassButton("السابق", Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.ArrowBack) { error = null; step-- }
        }
    }

    editingTime?.let { index ->
        TimeDialog(
            title = if (repeatType == RepeatType.DAILY) "وقت ${doseLabels[index]}" else "وقت الدواء",
            initial = times.getOrElse(index) { "08:00" },
            onDismiss = { editingTime = null },
            onConfirm = { value -> if (index < times.size) times[index] = value; editingTime = null },
        )
    }
    duplicateOf?.let { existing ->
        AfiatiDialog(
            title = "الدواء موجود",
            text = "يوجد «$existing» بنفس الجرعة والمواعيد. هل تريد إضافته مرة أخرى؟",
            confirm = "إضافة على أي حال", onConfirm = { duplicateOf = null; save(force = true) },
            dismiss = "إلغاء", onDismiss = { duplicateOf = null },
        )
    }
    if (confirmClose) {
        AfiatiDialog(
            title = "إلغاء إضافة الدواء؟",
            text = "لن يُحفظ ما كتبته.",
            confirm = "إلغاء الإضافة", destructive = true, onConfirm = { confirmClose = false; onClose() },
            dismiss = "متابعة", onDismiss = { confirmClose = false },
        )
    }
}

@Composable
private fun StepTitle(title: String, body: String?) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(title, style = tajawal(30, FontWeight.Bold))
        if (body != null) Text(body, style = tajawal(17, color = Brand.secondaryText))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(18.dp)) { content() }
}

@Composable
private fun RepeatOption(title: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val background = if (selected) Modifier.background(Brush.verticalGradient(listOf(Brand.mint.copy(alpha = 0.8f), Brand.deepGreen.copy(alpha = 0.9f))), shape) else Modifier
    Column(
        modifier.then(background).glass(shape, if (selected) 0.6f else 0.22f).clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = if (selected) Brand.text else Brand.glow, modifier = Modifier.size(32.dp))
        Text(title, style = tajawal(18, FontWeight.Bold))
    }
}

@Composable
private fun TimeRow(label: String, time: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClickLabel = "تغيير الوقت", onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = tajawal(19, FontWeight.Bold))
            Text("اضغط لتغيير الوقت", style = tajawal(15, color = Brand.secondaryText))
        }
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Brand.glow.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Schedule, null, tint = Brand.glow, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(Format.time(time), style = tajawal(22, FontWeight.Bold, Brand.glow))
        }
    }
}

@Composable
private fun DayNumber(day: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val background = if (selected) Modifier.background(Brush.verticalGradient(listOf(Brand.glow, Brand.mint)), shape) else Modifier
    Box(
        modifier.height(50.dp).then(background).glass(shape, if (selected) 0.6f else 0.18f).clickable(role = Role.Checkbox, onClickLabel = "يوم $day", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("$day", style = tajawal(18, FontWeight.Bold, if (selected) Brand.ink else Brand.text))
    }
}

@Composable
private fun ReviewLine(icon: ImageVector, text: String, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClickLabel = "تعديل", onClick = onEdit).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Brand.glow, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = tajawal(18), modifier = Modifier.weight(1f))
        Text("تعديل", style = tajawal(15, color = Brand.glow))
    }
}

/**
 * اختيار الوقت بأزرار كبيرة بدل الساعة الدوّارة: الساعة، الدقائق (كل 5 دقائق)، صباحًا أو مساءً، وأوقات جاهزة.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val start = Schedule.parseTime(initial) ?: LocalTime.of(8, 0)
    var hour24 by remember { mutableIntStateOf(start.hour) }
    var minute by remember { mutableIntStateOf(start.minute) }
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    val morning = hour24 < 12
    // Locale.US: الأرقام اللاتينية دائمًا، لأن الخادم يقبل HH:mm بها فقط
    fun value() = String.format(java.util.Locale.US, "%02d:%02d", hour24, minute)

    AfiatiDialog(
        title = title, confirm = "تم", onConfirm = { onConfirm(value()) }, dismiss = "إلغاء", onDismiss = onDismiss,
    ) {
        Text(Format.time(LocalTime.of(hour24, minute)), style = tajawal(44, FontWeight.Bold, Brand.glow), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        MiniStepper("الساعة", "$hour12", onMinus = { hour24 = (hour24 + 23) % 24 }, onPlus = { hour24 = (hour24 + 1) % 24 })
        MiniStepper("الدقائق", minute.toString().padStart(2, '0'), onMinus = { minute = ((minute / 5) * 5 - 5 + 60) % 60 }, onPlus = { minute = ((minute / 5) * 5 + 5) % 60 })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("صباحًا", morning, Modifier.weight(1f)) { if (!morning) hour24 -= 12 }
            Chip("مساءً", !morning, Modifier.weight(1f)) { if (morning) hour24 += 12 }
        }
        FieldLabel("أوقات شائعة")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("07:00", "08:00", "12:00", "14:00", "18:00", "20:00", "22:00").forEach { preset ->
                val selected = value() == preset
                Chip(Format.time(preset), selected) {
                    val parsed = Schedule.parseTime(preset) ?: return@Chip
                    hour24 = parsed.hour
                    minute = parsed.minute
                }
            }
        }
    }
}

@Composable
private fun MiniStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clickable(role = Role.Button, onClickLabel = "زيادة $label", onClick = onPlus), contentAlignment = Alignment.Center) {
            Text("+", style = tajawal(28, FontWeight.Bold, Brand.glow), maxLines = 1)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = tajawal(26, FontWeight.Bold), maxLines = 1, softWrap = false)
            Text(label, style = tajawal(13, color = Brand.secondaryText), maxLines = 1)
        }
        Box(Modifier.size(56.dp).clickable(role = Role.Button, onClickLabel = "إنقاص $label", onClick = onMinus), contentAlignment = Alignment.Center) {
            Text("−", style = tajawal(28, FontWeight.Bold, Brand.glow), maxLines = 1)
        }
    }
}
