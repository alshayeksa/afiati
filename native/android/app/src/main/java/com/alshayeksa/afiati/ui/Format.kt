package com.alshayeksa.afiati.ui

import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import com.alshayeksa.afiati.Lang
import com.alshayeksa.afiati.data.Iso
import com.alshayeksa.afiati.data.RepeatType
import com.alshayeksa.afiati.data.Schedule
import com.alshayeksa.afiati.tr
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date

/** نصوص الأوقات والجرعات والتواريخ بلغة الواجهة، بأرقام واضحة (لاتينية) */
object Format {
    private val gregorian get() = if (Lang.english) ULocale("en_US@calendar=gregorian") else ULocale("ar_SA@calendar=gregorian;numbers=latn")
    private val hijri get() = if (Lang.english) ULocale("en_US@calendar=islamic-umalqura") else ULocale("ar_SA@calendar=islamic-umalqura;numbers=latn")

    private val arabicWeekdays = mapOf(1 to "الأحد", 2 to "الاثنين", 3 to "الثلاثاء", 4 to "الأربعاء", 5 to "الخميس", 6 to "الجمعة", 7 to "السبت")
    private val englishWeekdays = mapOf(1 to "Sunday", 2 to "Monday", 3 to "Tuesday", 4 to "Wednesday", 5 to "Thursday", 6 to "Friday", 7 to "Saturday")
    val weekdayNames get() = if (Lang.english) englishWeekdays else arabicWeekdays
    /** ترتيب الأسبوع السعودي: يبدأ بالسبت */
    val weekOrder = listOf(7, 1, 2, 3, 4, 5, 6)

    fun time(local: LocalTime): String {
        val hour = local.hour % 12
        val period = if (local.hour < 12) tr("ص", "AM") else tr("م", "PM")
        return "${if (hour == 0) 12 else hour}:${local.minute.toString().padStart(2, '0')} $period"
    }

    fun time(value: String): String = Schedule.parseTime(value)?.let(::time) ?: value

    fun time(instant: Instant): String = time(instant.atZone(ZoneId.systemDefault()).toLocalTime())

    fun isoTime(iso: String): String = runCatching { time(Iso.parse(iso)) }.getOrDefault("")

    fun number(value: Double): String = if (value == Math.floor(value)) value.toLong().toString() else value.toString()

    /** اسم الوحدة للعرض. القيمة المحفوظة تبقى بالعربية («حبة»...) كما يتوقعها الخادم. */
    fun unitName(unit: String): String = when (unit) {
        "حبة" -> tr("حبة", "tablet")
        "مل" -> tr("مل", "ml")
        "قطرة" -> tr("قطرة", "drop")
        "بخة" -> tr("بخة", "puff")
        "other" -> ""
        else -> unit
    }

    /** «حبة واحدة»، «حبتان»، «3 حبات»، «5 مل» / «1 tablet»، «2 tablets»، «5 ml» */
    fun dose(amount: Double, unit: String): String = if (Lang.english) englishDose(amount, unit) else arabicDose(amount, unit)

    private fun englishDose(amount: Double, unit: String): String {
        if (amount == 0.5 && unit == "حبة") return "half a tablet"
        val name = unitName(unit)
        if (name.isEmpty()) return number(amount)
        // الوحدات المعدودة تُجمع، والمل لا يُجمع
        val plural = unit != "مل" && amount != 1.0
        return "${number(amount)} $name${if (plural) "s" else ""}"
    }

    private fun arabicDose(amount: Double, unit: String): String {
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

    fun times(count: Int) = if (Lang.english) {
        when (count) {
            1 -> "once"
            2 -> "twice"
            else -> "$count times"
        }
    } else {
        when (count) {
            1 -> "مرة واحدة"
            2 -> "مرتان"
            else -> "$count مرات"
        }
    }

    fun repeatTitle(type: String) = when (type) {
        RepeatType.WEEKLY -> tr("أسبوعيًا", "Weekly")
        RepeatType.MONTHLY -> tr("شهريًا", "Monthly")
        else -> tr("يوميًا", "Daily")
    }

    /** «يوميًا · 8:00 ص، 8:00 م» أو «الأحد، الخميس · 9:00 م» */
    fun schedule(type: String, days: List<Int>, times: List<String>): String {
        val comma = tr("، ", ", ")
        val timeText = Schedule.timesFor(type, times).joinToString(comma) { time(it) }
        return when (type) {
            RepeatType.WEEKLY -> weekOrder.filter { it in days }.joinToString(comma) { weekdayNames[it].orEmpty() } + " · " + timeText
            RepeatType.MONTHLY -> tr("يوم ", "Day ") + days.sorted().joinToString(comma) + tr(" من كل شهر · ", " of each month · ") + timeText
            else -> tr("يوميًا · ", "Daily · ") + timeText
        }
    }

    private fun date(date: LocalDate) = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())

    /** «الخميس 27 نوفمبر» / «Thursday, November 27» */
    fun gregorianDay(date: LocalDate = LocalDate.now()): String =
        SimpleDateFormat(if (Lang.english) "EEEE, MMMM d" else "EEEE d MMMM", gregorian).format(date(date))

    /** «6 جمادى الآخرة 1448 هـ» / «Jumada II 6, 1448 AH» */
    fun hijriDay(date: LocalDate = LocalDate.now()): String =
        if (Lang.english) SimpleDateFormat("MMMM d, y", hijri).format(date(date)) + " AH"
        else SimpleDateFormat("d MMMM y", hijri).format(date(date)) + " هـ"

    fun monthTitle(date: LocalDate): String = SimpleDateFormat("MMMM y", gregorian).format(date(date))

    fun shortWeekday(date: LocalDate): String {
        val name = weekdayNames[Schedule.weekday(date)].orEmpty()
        return if (Lang.english) name.take(3) else name.removePrefix("ال")
    }

    /** «قبل 5 دقائق» / «5 minutes ago» */
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
