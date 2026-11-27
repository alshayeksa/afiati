package com.alshayeksa.afiati.ui

import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import com.alshayeksa.afiati.data.Iso
import com.alshayeksa.afiati.data.RepeatType
import com.alshayeksa.afiati.data.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

/** نصوص عربية: الأوقات والجرعات والتواريخ، بأرقام واضحة */
object Format {
    private val gregorian = ULocale("ar_SA@calendar=gregorian;numbers=latn")
    private val hijri = ULocale("ar_SA@calendar=islamic-umalqura;numbers=latn")

    val weekdayNames = mapOf(1 to "الأحد", 2 to "الاثنين", 3 to "الثلاثاء", 4 to "الأربعاء", 5 to "الخميس", 6 to "الجمعة", 7 to "السبت")
    /** ترتيب الأسبوع السعودي: يبدأ بالسبت */
    val weekOrder = listOf(7, 1, 2, 3, 4, 5, 6)

    fun time(local: LocalTime): String {
        val hour = local.hour % 12
        val period = if (local.hour < 12) "ص" else "م"
        return "${if (hour == 0) 12 else hour}:${local.minute.toString().padStart(2, '0')} $period"
    }

    fun time(value: String): String = Schedule.parseTime(value)?.let(::time) ?: value

    fun time(instant: Instant): String = time(instant.atZone(ZoneId.systemDefault()).toLocalTime())

    fun isoTime(iso: String): String = runCatching { time(Iso.parse(iso)) }.getOrDefault("")

    fun number(value: Double): String = if (value == Math.floor(value)) value.toLong().toString() else value.toString()

    /** «حبة واحدة»، «حبتان»، «3 حبات»، «5 مل» */
    fun dose(amount: Double, unit: String): String {
        val whole = amount == Math.floor(amount)
        val n = amount.toLong()
        if (amount == 0.5 && unit == "حبة") return "نصف حبة"
        if (whole && unit == "حبة") return when {
            n == 1L -> "حبة واحدة"
            n == 2L -> "حبتان"
            n in 3..10 -> "$n حبات"
            else -> "$n حبة"
        }
        if (whole && unit == "قطرة") return when {
            n == 1L -> "قطرة واحدة"
            n == 2L -> "قطرتان"
            n in 3..10 -> "$n قطرات"
            else -> "$n قطرة"
        }
        if (whole && unit == "بخة") return when {
            n == 1L -> "بخة واحدة"
            n == 2L -> "بختان"
            n in 3..10 -> "$n بخات"
            else -> "$n بخة"
        }
        return "${number(amount)} ${if (unit == "other") "" else unit}".trim()
    }

    fun times(count: Int) = when (count) {
        1 -> "مرة واحدة"
        2 -> "مرتان"
        else -> "$count مرات"
    }

    fun repeatTitle(type: String) = when (type) {
        RepeatType.WEEKLY -> "أسبوعيًا"
        RepeatType.MONTHLY -> "شهريًا"
        else -> "يوميًا"
    }

    /** «يوميًا · 8:00 ص، 8:00 م» أو «الأحد والخميس · 9:00 م» */
    fun schedule(type: String, days: List<Int>, times: List<String>): String {
        val timeText = Schedule.timesFor(type, times).joinToString("، ") { time(it) }
        return when (type) {
            RepeatType.WEEKLY -> weekOrder.filter { it in days }.joinToString("، ") { weekdayNames[it].orEmpty() } + " · " + timeText
            RepeatType.MONTHLY -> "يوم " + days.sorted().joinToString("، ") + " من كل شهر · " + timeText
            else -> "يوميًا · $timeText"
        }
    }


    private fun date(date: LocalDate) = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())

    /** «الخميس 27 نوفمبر» */
    fun gregorianDay(date: LocalDate = LocalDate.now()): String = SimpleDateFormat("EEEE d MMMM", gregorian).format(date(date))

    /** «6 جمادى الآخرة 1448 هـ» */
    fun hijriDay(date: LocalDate = LocalDate.now()): String = SimpleDateFormat("d MMMM y", hijri).format(date(date)) + " هـ"

    fun monthTitle(date: LocalDate): String = SimpleDateFormat("MMMM y", gregorian).format(date(date))

    fun shortWeekday(date: LocalDate): String = weekdayNames[Schedule.weekday(date)].orEmpty().removePrefix("ال")

    /** «قبل 5 دقائق» */
    fun relative(iso: String): String {
        val instant = runCatching { Iso.parse(iso) }.getOrNull() ?: return ""
        val formatter = RelativeDateTimeFormatter.getInstance(gregorian)
        val seconds = ((System.currentTimeMillis() - instant.toEpochMilli()) / 1000).coerceAtLeast(0)
        val (amount, unit) = when {
            seconds < 60 -> return formatter.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
            seconds < 3600 -> seconds / 60 to RelativeDateTimeFormatter.RelativeUnit.MINUTES
            seconds < 86_400 -> seconds / 3600 to RelativeDateTimeFormatter.RelativeUnit.HOURS
            seconds < 7 * 86_400 -> seconds / 86_400 to RelativeDateTimeFormatter.RelativeUnit.DAYS
            else -> seconds / (7 * 86_400) to RelativeDateTimeFormatter.RelativeUnit.WEEKS
        }
        return formatter.format(amount.toDouble(), RelativeDateTimeFormatter.Direction.LAST, unit)
    }
}
