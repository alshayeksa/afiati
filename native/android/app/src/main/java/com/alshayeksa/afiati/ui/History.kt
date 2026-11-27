package com.alshayeksa.afiati.ui

import com.alshayeksa.afiati.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alshayeksa.afiati.data.DoseLog
import com.alshayeksa.afiati.data.Iso
import java.time.LocalDate
import java.time.ZoneId

/** إحصاء يوم واحد */
private data class DayStat(val date: LocalDate, val total: Int, val taken: Int) {
    val ratio get() = if (total == 0) null else taken.toFloat() / total
}

private fun localDate(log: DoseLog): LocalDate? = runCatching { Iso.parse(log.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrNull()

@Composable
fun HistoryScreen(header: @Composable () -> Unit) {
    val now = rememberNow()
    val today = AppModel.today
    val logs = AppModel.historyLogs
    val byDay = remember(logs) { logs.groupBy(::localDate) }
    val days = remember(byDay, today, now) {
        (0L until 30L).map { offset ->
            val date = today.minusDays(offset)
            val dayLogs = byDay[date].orEmpty()
            // الجرعات التي لم يحن موعدها لا تدخل في نسبة الالتزام
            val due = dayLogs.filter { it.status == "taken" || it.scheduledAt <= Iso.format(now) }
            DayStat(date, due.size, due.count { it.status == "taken" })
        }
    }
    var selectedEpoch by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)

    TabList(header) {
        item(key = "title") {
            BigTitle(tr("السجل", "History"), tr("التزامك في آخر 30 يومًا", "Your adherence over the last 30 days"))
        }
        item(key = "summary") { Summary(days) }
        item(key = "month") { SectionTitle(Format.monthTitle(selected)) }
        item(key = "strip") {
            LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(days, key = { it.date.toEpochDay() }) { day ->
                    DayCell(day, day.date == selected, day.date == today) { selectedEpoch = day.date.toEpochDay() }
                }
            }
        }
        val selectedLogs = byDay[selected].orEmpty()
        item(key = "day-title") {
            SectionTitle(if (selected == today) tr("اليوم", "Today") else "${Format.weekdayNames[com.alshayeksa.afiati.data.Schedule.weekday(selected)]} ${selected.dayOfMonth}",
                         Modifier.padding(top = 14.dp))
        }
        if (selectedLogs.isEmpty()) {
            item(key = "day-empty") {
                Text(
                    tr("لا توجد جرعات مسجلة في هذا اليوم.", "No doses were logged on this day."), style = tajawal(17, color = Brand.secondaryText), textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(22.dp),
                )
            }
        } else {
            items(selectedLogs, key = { "h-" + it.id }) { log ->
                val state = if (selected == today) doseState(log, now, AppModel.isTaken(log))
                else if (log.status == "taken") DoseState.TAKEN else DoseState.MISSED
                DoseRow(log, state, onTake = null) {}
            }
        }
    }
}

@Composable
private fun Summary(days: List<DayStat>) {
    fun percent(list: List<DayStat>): Int? {
        val total = list.sumOf { it.total }
        return if (total == 0) null else list.sumOf { it.taken } * 100 / total
    }
    val week = percent(days.take(7))
    val month = percent(days)
    // أيام متتالية بالتزام كامل، تبدأ من أمس (اليوم لم ينته بعد) ويُضاف اليوم إن اكتمل
    val todayComplete = days.firstOrNull()?.let { it.total > 0 && it.taken == it.total } == true
    val streak = days.drop(1).takeWhile { it.total > 0 && it.taken == it.total }.size + if (todayComplete) 1 else 0
    Row(Modifier.padding(horizontal = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(tr("آخر 7 أيام", "Last 7 days"), week?.let { tr("$it٪", "$it%") } ?: "—", Modifier.weight(1f))
        StatCard(tr("آخر 30 يومًا", "Last 30 days"), month?.let { tr("$it٪", "$it%") } ?: "—", Modifier.weight(1f))
        StatCard(tr("أيام كاملة متتالية", "Full days in a row"), "$streak", Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.glass(RoundedCornerShape(22.dp)).padding(horizontal = 10.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = tajawal(28, FontWeight.Bold, Brand.glow))
        Text(label, style = tajawal(14, color = Brand.secondaryText), textAlign = TextAlign.Center)
    }
}

@Composable
private fun DayCell(day: DayStat, selected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val ratio = day.ratio
    val dot: Color = when {
        ratio == null -> Brand.text.copy(alpha = 0.15f)
        ratio >= 1f -> Brand.glow
        ratio >= 0.5f -> Brand.warning
        else -> Brand.danger
    }
    val background = if (selected) Modifier.background(Brush.verticalGradient(listOf(Brand.mint.copy(alpha = 0.8f), Brand.deepGreen.copy(alpha = 0.9f))), shape) else Modifier
    Column(
        Modifier.width(64.dp).then(background).glass(shape, if (selected) 0.6f else if (isToday) 0.4f else 0.18f)
            .clickable(role = Role.Button, onClickLabel = tr("عرض يوم ${day.date.dayOfMonth}", "Show day ${day.date.dayOfMonth}"), onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (isToday) tr("اليوم", "Today") else Format.shortWeekday(day.date), style = tajawal(13, FontWeight.Medium, if (selected) Brand.text else Brand.secondaryText), maxLines = 1)
        Text("${day.date.dayOfMonth}", style = tajawal(24, FontWeight.Bold))
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(10.dp).background(dot, CircleShape))
    }
}
