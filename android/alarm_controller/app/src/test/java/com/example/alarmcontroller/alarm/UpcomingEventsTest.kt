package com.example.alarmcontroller.alarm

import com.example.alarmcontroller.ui.upcomingEvents
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone

class UpcomingEventsTest {
    private val timezone = TimeZone.getDefault()
    @Before fun utc() { TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun restore() { TimeZone.setDefault(timezone) }
    private fun time(value: String) = Instant.parse(value).toEpochMilli()
    private fun routine(id: String = "a", recurrence: Recurrence = Recurrence.Daily) = AlarmItem(
        id = id, title = id, recurrence = recurrence, lockPhone = RingtoneSlot(22, 0),
        bedtime = RingtoneSlot(23, 0, enabled = false), wakeup = RingtoneSlot(7, 0))

    @Test fun midnightAndMultipleRoutinesUseActualDates() {
        val events = upcomingEvents(listOf(routine(), routine("b").copy(wakeup = RingtoneSlot(6, 0))),
            time("2026-11-06T23:30:00Z"))
        assertEquals("b", events.first().alarm.id)
        assertEquals(time("2026-11-07T06:00:00Z"), events.first().timeMillis)
        assertTrue(events.zipWithNext().all { (a, b) -> a.timeMillis <= b.timeMillis })
    }
    @Test fun skipAdvancesOneOccurrenceInsteadOfHidingEveryFutureWakeup() {
        val alarm = routine().copy(dismissedRingtones = setOf(RingtoneType.WAKEUP))
        val events = upcomingEvents(listOf(alarm), time("2026-11-06T06:00:00Z"))
        assertEquals(RingtoneType.LOCK_PHONE, events.first().type)
        assertEquals(time("2026-11-07T07:00:00Z"), events.last().timeMillis)
    }
    @Test fun pausedRoutinesAndDisabledBedtimeAreNotDisplayed() {
        val events = upcomingEvents(listOf(routine(), routine("paused").copy(isEnabled = false)),
            time("2026-11-06T06:00:00Z"))
        assertEquals(2, events.size)
        assertTrue(events.none { it.type == RingtoneType.BEDTIME || it.alarm.id == "paused" })
    }
    @Test fun weeklySkipAndIntervalRespectTheirNextScheduledDate() {
        val weekly = routine("weekly", Recurrence.Weekly(setOf(1))).copy(
            dismissedRingtones = setOf(RingtoneType.WAKEUP))
        val interval = routine("interval", Recurrence.EveryDays(3,
            LocalDate.parse("2026-11-06").toEpochDay()))
        val events = upcomingEvents(listOf(weekly, interval), time("2026-11-06T08:00:00Z"))
        assertEquals(time("2026-11-16T07:00:00Z"), events.single {
            it.alarm.id == "weekly" && it.type == RingtoneType.WAKEUP }.timeMillis)
        assertEquals(time("2026-11-09T07:00:00Z"), events.single {
            it.alarm.id == "interval" && it.type == RingtoneType.WAKEUP }.timeMillis)
    }
}
