package com.example.alarmcontroller.alarm

import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RecurrenceTest {
    private val originalTimezone = TimeZone.getDefault()

    @Before fun useUtc() { TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun restoreTimezone() { TimeZone.setDefault(originalTimezone) }

    private fun time(value: String) = ZonedDateTime.parse(value).toInstant().toEpochMilli()
    private val interval = Recurrence.EveryDays(3, LocalDate.parse("2026-10-07").toEpochDay())

    @Test fun intervalDoesNotMoveWhenRecomputedTheFollowingDay() {
        val expected = time("2026-10-10T07:00:00Z")
        assertEquals(expected, interval.nextOccurrenceTime(7, 0, time("2026-10-07T07:00:00Z")))
        assertEquals(expected, interval.nextOccurrenceTime(7, 0, time("2026-10-08T06:00:00Z")))
        assertEquals(expected, interval.nextOccurrenceTime(7, 0, time("2026-10-09T08:00:00Z")))
    }

    @Test fun otherSlotsKeepTheSameDateAnchor() {
        assertEquals(time("2026-10-07T22:00:00Z"),
            interval.nextOccurrenceTime(22, 0, time("2026-10-07T07:00:00Z")))
        assertEquals(time("2026-10-10T22:00:00Z"),
            interval.nextOccurrenceTime(22, 0, time("2026-10-08T06:00:00Z")))
    }

    @Test fun weeklyUsesTheNextSelectedWeekday() {
        assertEquals(time("2026-10-12T07:00:00Z"),
            Recurrence.Weekly(setOf(1)).nextOccurrenceTime(7, 0, time("2026-10-07T06:00:00Z")))
    }

    @Test fun exactTriggerTimeAdvancesToTheNextDay() {
        assertEquals(time("2026-10-08T07:00:00Z"),
            Recurrence.Daily.nextOccurrenceTime(7, 0, time("2026-10-07T07:00:00Z")))
    }

    @Test fun intervalUsesLocalDatesAcrossDaylightSavingChange() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
        val recurrence = Recurrence.EveryDays(2, LocalDate.parse("2026-10-24").toEpochDay())
        assertEquals(time("2026-10-26T07:00:00+01:00"),
            recurrence.nextOccurrenceTime(7, 0, time("2026-10-24T08:00:00+02:00")))
    }
}
