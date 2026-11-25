package com.example.alarmcontroller.alarm

import com.example.alarmcontroller.communication.SyncPayload
import com.example.alarmcontroller.ui.upcomingEvents
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.TimeZone
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class CalendarRoutineTest {
    private val zone = TimeZone.getDefault()
    @Before fun utc() { TimeZone.setDefault(TimeZone.getTimeZone("UTC")) }
    @After fun restore() { TimeZone.setDefault(zone) }
    private fun time(value: String) = ZonedDateTime.parse(value).toInstant().toEpochMilli()
    private val date = LocalDate.parse("2026-11-30").toEpochDay()
    private fun routine() = AlarmItem(title = "Monday appointment", recurrence = Recurrence.Once(date),
        lockPhone = RingtoneSlot(22, 0, ringOnDevice = false, lightAction = 1),
        bedtime = RingtoneSlot(23, 0, lightAction = 0), wakeup = RingtoneSlot(7, 0))

    @Test fun eveningBelongsToPreviousDay() {
        val events = upcomingEvents(listOf(routine()), time("2026-11-29T20:00:00Z"))
        assertEquals(listOf(time("2026-11-29T22:00:00Z"), time("2026-11-29T23:00:00Z"),
            time("2026-11-30T07:00:00Z")), events.map { it.timeMillis })
    }
    @Test fun expiredRoutineHasNoFutureEvents() {
        assertTrue(upcomingEvents(listOf(routine()), time("2026-11-30T07:00:00Z")).isEmpty())
    }
    @Test fun skippedDatedEventDoesNotRepeat() {
        val events = upcomingEvents(listOf(routine().withDismissed(RingtoneType.WAKEUP, true)),
            time("2026-11-30T06:00:00Z"))
        assertTrue(events.isEmpty())
    }
    @Test fun savedRoutineRetainsDateAndLampActions() {
        val original = routine()
        assertEquals(original, AlarmItem.fromJson(original.toJson()))
        assertEquals(-1, RingtoneSlot.fromJson(JSONObject().put("h", 7)).lightAction)
    }
    @Test fun wireCarriesIndividualDatesAndLampActionForSilentClockSlot() {
        val alarm = JSONObject(SyncPayload.buildPushSyncJson(listOf(routine()))).getJSONArray("alarms").getJSONObject(0)
        assertEquals("once", alarm.getJSONObject("recurrence").getString("type"))
        assertEquals(date - 1, alarm.getJSONObject("lock").getLong("epochDay"))
        assertEquals(date, alarm.getJSONObject("wake").getLong("epochDay"))
        assertEquals(1, alarm.getJSONObject("lock").getInt("lightAction"))
        assertFalse(alarm.getJSONObject("lock").getBoolean("ringDevice"))
    }
    @Test fun onceUsesLocalDateAtDaylightSavingBoundary() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"))
        val rule = Recurrence.Once(LocalDate.parse("2026-10-25").toEpochDay())
        assertEquals(time("2026-10-25T07:00:00+01:00"),
            rule.nextOccurrenceTime(7, 0, time("2026-10-24T20:00:00+02:00")))
    }
}
