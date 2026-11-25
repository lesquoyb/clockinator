package com.example.alarmcontroller.ui

import com.example.alarmcontroller.alarm.AlarmItem
import com.example.alarmcontroller.alarm.RingtoneType

data class UpcomingEvent(val alarm: AlarmItem, val type: RingtoneType, val timeMillis: Long)

/** The next non-skipped occurrence of every enabled routine slot. */
fun upcomingEvents(alarms: List<AlarmItem>, nowMillis: Long): List<UpcomingEvent> =
    alarms.filter { it.isEnabled }.flatMap { alarm ->
        RingtoneType.entries.mapNotNull { type ->
            val slot = alarm.slot(type)
            if (!slot.enabled) return@mapNotNull null
            var time = alarm.recurrenceFor(type).nextOccurrenceTime(slot.hour, slot.minute, nowMillis)
            if (time == Long.MAX_VALUE) return@mapNotNull null
            if (alarm.isDismissed(type)) {
                time = alarm.recurrenceFor(type).nextOccurrenceTime(slot.hour, slot.minute, time + 1)
            }
            if (time == Long.MAX_VALUE) return@mapNotNull null
            UpcomingEvent(alarm, type, time)
        }
    }.sortedWith(compareBy<UpcomingEvent> { it.timeMillis }.thenBy { it.alarm.id }.thenBy { it.type.ordinal })
