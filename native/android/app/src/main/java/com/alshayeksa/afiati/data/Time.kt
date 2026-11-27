package com.alshayeksa.afiati.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** الوقت بصيغة Date#toISOString() بالضبط (ملّي ثانية وZ)، لأن الخادم يرفض غيرها ويرتّب بها نصيًا. */
object Iso {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).withZone(ZoneOffset.UTC)
    fun format(instant: Instant): String = formatter.format(instant)
    fun now(): String = format(Instant.now())
    fun parse(value: String): Instant = Instant.parse(value)
}

/** مواعيد الدواء: أي الأيام، وأي الأوقات، والموعد القادم. منطق خالص يُختبر على JVM. */
object Schedule {
    /** ترقيم أيام الأسبوع في التطبيق: 1 الأحد … 7 السبت */
    fun weekday(date: LocalDate): Int = date.dayOfWeek.value % 7 + 1

    fun isScheduledOn(repeatType: String, repeatDays: List<Int>, date: LocalDate): Boolean = when (repeatType) {
        RepeatType.WEEKLY -> weekday(date) in repeatDays
        RepeatType.MONTHLY -> date.dayOfMonth in repeatDays
        else -> true
    }

    /** اليومي له حتى 4 أوقات، والأسبوعي والشهري وقت واحد في الأيام المختارة */
    fun timesFor(repeatType: String, times: List<String>): List<String> =
        if (repeatType == RepeatType.DAILY) times else times.take(1)

    fun parseTime(time: String): LocalTime? = runCatching {
        val (h, m) = time.split(":").map { it.toInt() }
        LocalTime.of(h, m)
    }.getOrNull()

    fun scheduledAt(date: LocalDate, time: String, zone: ZoneId = ZoneId.systemDefault()): String? {
        val local = parseTime(time) ?: return null
        return Iso.format(date.atTime(local).atZone(zone).toInstant())
    }

    /** أول موعد لهذا الوقت بعد اللحظة المعطاة، خلال 62 يومًا (يكفي لأي يوم في الشهر) */
    fun nextOccurrence(repeatType: String, repeatDays: List<Int>, time: String, after: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant? {
        val local = parseTime(time) ?: return null
        val start = after.atZone(zone).toLocalDate()
        for (offset in 0..62L) {
            val date = start.plusDays(offset)
            if (!isScheduledOn(repeatType, repeatDays, date)) continue
            val at = date.atTime(local).atZone(zone).toInstant()
            if (at.isAfter(after)) return at
        }
        return null
    }

    fun dayRange(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<String, String> =
        Iso.format(date.atStartOfDay(zone).toInstant()) to Iso.format(date.plusDays(1).atStartOfDay(zone).toInstant())
}

/** قواعد التعارض نفسها في الخادم ونسخة Expo: الأحدث يفوز، والجرعة المأخوذة لا تعود. */
object Conflict {
    fun shouldApplyRemote(localUpdatedAt: String?, remoteUpdatedAt: String?): Boolean {
        if (localUpdatedAt == null) return true
        if (remoteUpdatedAt == null) return false
        return runCatching { !Iso.parse(remoteUpdatedAt).isBefore(Iso.parse(localUpdatedAt)) }.getOrDefault(false)
    }

    data class DoseState(val status: String, val takenAt: String?, val updatedAt: String)

    fun mergeDose(local: DoseState?, remote: DoseState): DoseState {
        if (local?.status == DoseStatus.TAKEN) return local
        if (remote.status == DoseStatus.TAKEN) return remote
        return if (shouldApplyRemote(local?.updatedAt, remote.updatedAt)) remote else local ?: remote
    }
}
