package com.example.alarmcontroller.communication

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumePayloadTest {
    @Test fun allFiveLevelsUseTheStandaloneCommand() {
        for (level in 1..5) {
            val json = JSONObject(SyncPayload.buildSetVolume(level))
            assertEquals("SET_VOLUME", json.getString("action"))
            assertEquals(level, json.getInt("level"))
            assertEquals(false, json.has("alarms"))
        }
    }
    @Test(expected = IllegalArgumentException::class)
    fun muteCannotBeSent() { SyncPayload.buildSetVolume(0) }

    @Test(expected = IllegalArgumentException::class)
    fun outOfRangeCannotBeSent() { SyncPayload.buildSetVolume(6) }

    @Test fun reconnectSyncCarriesTheSavedVolume() {
        val json = JSONObject(SyncPayload.buildPushSyncJson(emptyList(), 4))
        assertEquals("SYNC", json.getString("action"))
        assertEquals(4, json.getInt("volumeLevel"))
    }

    @Test fun testCommandsDoNotChangeTheAlarmSchedule() {
        val builtin = JSONObject(SyncPayload.buildTestRingtone(null))
        assertEquals("TEST_RINGTONE", builtin.getString("action"))
        assertEquals("", builtin.getString("filename"))
        val wav = JSONObject(SyncPayload.buildTestRingtone("alarm.wav"))
        assertEquals("alarm.wav", wav.getString("filename"))
        assertEquals(false, wav.has("alarms"))
        assertEquals("STOP_TEST_RINGTONE", JSONObject(SyncPayload.buildStopRingtoneTest()).getString("action"))
    }
}
