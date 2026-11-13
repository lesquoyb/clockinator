package com.example.alarmcontroller.communication

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RingTimeoutPayloadTest {
    @Test fun defaultAndCustomDurationsAreIncludedInSync() {
        assertEquals(20, JSONObject(SyncPayload.buildPushSyncJson(emptyList())).getInt("ringTimeoutMinutes"))
        val sync = SyncPayload.buildPushSyncJson(emptyList(), ringTimeoutMinutes = 45)
        assertEquals(45, JSONObject(sync).getInt("ringTimeoutMinutes"))
    }

    @Test fun immediateUpdateUsesSameBoundsAsSync() {
        val command = JSONObject(SyncPayload.buildSetRingTimeout(120))
        assertEquals("SET_RING_TIMEOUT", command.getString("action"))
        assertEquals(120, command.getInt("minutes"))
        for (minutes in listOf(0, 121)) {
            assertThrows(IllegalArgumentException::class.java) { SyncPayload.buildSetRingTimeout(minutes) }
            assertThrows(IllegalArgumentException::class.java) {
                SyncPayload.buildPushSyncJson(emptyList(), ringTimeoutMinutes = minutes)
            }
        }
    }
}
