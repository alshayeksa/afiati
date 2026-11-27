package com.alshayeksa.afiati

import com.alshayeksa.afiati.data.Conflict
import com.alshayeksa.afiati.data.DoseStatus
import com.alshayeksa.afiati.data.Ids
import com.alshayeksa.afiati.data.Iso
import com.alshayeksa.afiati.data.RepeatType
import com.alshayeksa.afiati.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LogicTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    @Test
    fun doseLogIdMatchesExpoAndServerAlgorithm() {
        // المتجه نفسه محسوب بخوارزمية lib/sync/ids.ts
        val id = Ids.doseLogId("3f2b8c1e-1111-4222-8333-444455556666", "2026-11-27T05:00:00.000Z")
        assertEquals("f09b9f25-92e5-8872-b39f-f790423e0939", id)
    }

    @Test
    fun differentDosesGetDifferentIds() {
        assertNotEquals(
            Ids.doseLogId("a", "2026-11-27T05:00:00.000Z"),
            Ids.doseLogId("a", "2026-11-27T17:00:00.000Z"),
        )
    }

    @Test
    fun isoFormatAlwaysHasMilliseconds() {
        assertEquals("2026-11-27T05:00:00.000Z", Iso.format(Instant.parse("2026-11-27T05:00:00Z")))
        assertTrue(Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$").matches(Iso.now()))
    }

    @Test
    fun scheduledAtUsesLocalTime() {
        // 8:00 صباحًا في الرياض = 05:00 UTC
        assertEquals("2026-11-27T05:00:00.000Z", Schedule.scheduledAt(LocalDate.of(2026, 11, 27), "08:00", riyadh))
        assertNull(Schedule.scheduledAt(LocalDate.of(2026, 11, 27), "8am", riyadh))
    }

    @Test
    fun weekdayNumberingStartsWithSunday() {
        assertEquals(1, Schedule.weekday(LocalDate.of(2026, 11, 29))) // الأحد
        assertEquals(5, Schedule.weekday(LocalDate.of(2026, 11, 26))) // الخميس
        assertEquals(7, Schedule.weekday(LocalDate.of(2026, 11, 28))) // السبت
    }

    @Test
    fun scheduleRules() {
        val thursday = LocalDate.of(2026, 11, 26)
        assertTrue(Schedule.isScheduledOn(RepeatType.DAILY, emptyList(), thursday))
        assertTrue(Schedule.isScheduledOn(RepeatType.WEEKLY, listOf(5), thursday))
        assertFalse(Schedule.isScheduledOn(RepeatType.WEEKLY, listOf(1), thursday))
        assertTrue(Schedule.isScheduledOn(RepeatType.MONTHLY, listOf(26), thursday))
        assertEquals(listOf("08:00"), Schedule.timesFor(RepeatType.WEEKLY, listOf("08:00", "20:00")))
        assertEquals(listOf("08:00", "20:00"), Schedule.timesFor(RepeatType.DAILY, listOf("08:00", "20:00")))
    }

    @Test
    fun nextOccurrence() {
        val after = Instant.parse("2026-11-26T06:00:00.000Z") // 9:00 صباح الخميس في الرياض
        // يومي 08:00: فات اليوم، فالموعد غدًا
        assertEquals(Instant.parse("2026-11-27T05:00:00.000Z"), Schedule.nextOccurrence(RepeatType.DAILY, emptyList(), "08:00", after, riyadh))
        // يومي 20:00: اليوم نفسه
        assertEquals(Instant.parse("2026-11-26T17:00:00.000Z"), Schedule.nextOccurrence(RepeatType.DAILY, emptyList(), "20:00", after, riyadh))
        // أسبوعي الأحد 08:00
        assertEquals(Instant.parse("2026-11-29T05:00:00.000Z"), Schedule.nextOccurrence(RepeatType.WEEKLY, listOf(1), "08:00", after, riyadh))
        // شهري يوم 31: ديسمبر 31
        assertEquals(Instant.parse("2026-12-31T05:00:00.000Z"), Schedule.nextOccurrence(RepeatType.MONTHLY, listOf(31), "08:00", after, riyadh))
    }

    @Test
    fun takenDoseNeverReverts() {
        val taken = Conflict.DoseState(DoseStatus.TAKEN, "2026-11-27T05:10:00.000Z", "2026-11-27T05:10:00.000Z")
        val newerPending = Conflict.DoseState(DoseStatus.PENDING, null, "2026-11-27T09:00:00.000Z")
        assertEquals(taken, Conflict.mergeDose(taken, newerPending))
        assertEquals(taken, Conflict.mergeDose(newerPending, taken))
    }

    @Test
    fun newerVersionWins() {
        assertTrue(Conflict.shouldApplyRemote(null, "2026-11-27T05:00:00.000Z"))
        assertTrue(Conflict.shouldApplyRemote("2026-11-27T05:00:00.000Z", "2026-11-27T06:00:00.000Z"))
        assertFalse(Conflict.shouldApplyRemote("2026-11-27T06:00:00.000Z", "2026-11-27T05:00:00.000Z"))
        assertFalse(Conflict.shouldApplyRemote("2026-11-27T06:00:00.000Z", null))
    }
}
