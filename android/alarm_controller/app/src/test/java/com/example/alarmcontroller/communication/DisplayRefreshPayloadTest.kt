package com.example.alarmcontroller.communication

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DisplayRefreshPayloadTest {
    @Test fun disabledByDefaultAndIncludedInSync() {
        assertEquals(0, JSONObject(SyncPayload.buildPushSyncJson(emptyList())).getInt("displayRefreshMinutes"))
        assertEquals(30, JSONObject(SyncPayload.buildPushSyncJson(emptyList(), 3, 30)).getInt("displayRefreshMinutes"))
    }
    @Test fun explicitCommands() {
        assertEquals("REFRESH_DISPLAY", JSONObject(SyncPayload.buildRefreshDisplay()).getString("action"))
        assertEquals(0, JSONObject(SyncPayload.buildDisplayRefreshSettings(0)).getInt("minutes"))
        assertEquals(1440, JSONObject(SyncPayload.buildDisplayRefreshSettings(1440)).getInt("minutes"))
    }
    @Test fun rejectsInvalidIntervals() {
        for (minutes in listOf(-1, 1441)) {
            assertThrows(IllegalArgumentException::class.java) { SyncPayload.buildDisplayRefreshSettings(minutes) }
            assertThrows(IllegalArgumentException::class.java) { SyncPayload.buildPushSyncJson(emptyList(), 3, minutes) }
        }
    }
}
