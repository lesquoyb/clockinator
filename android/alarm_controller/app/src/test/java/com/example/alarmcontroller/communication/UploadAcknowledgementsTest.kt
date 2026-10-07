package com.example.alarmcontroller.communication

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadAcknowledgementsTest {
    @Test fun immediateAckIsReceivedAndCompletesTheWait() = runBlocking {
        val replies = MutableSharedFlow<String>()
        val ack = sendAndAwaitUploadAck(replies, 1000, { it == "CHUNK_ACK:0" }) {
            replies.emit("CHUNK_ACK:0")
            Result.success(Unit)
        }
        assertEquals("CHUNK_ACK:0", ack)
    }

    @Test fun unrelatedAckIsIgnored() = runBlocking {
        val replies = MutableSharedFlow<String>()
        val ack = sendAndAwaitUploadAck(replies, 1000, { it == "CHUNK_ACK:1" }) {
            replies.emit("CHUNK_ACK:0")
            replies.emit("CHUNK_ACK:1")
            Result.success(Unit)
        }
        assertEquals("CHUNK_ACK:1", ack)
    }

    @Test fun deviceErrorStopsTheWait() = runBlocking {
        val replies = MutableSharedFlow<String>()
        val ack = sendAndAwaitUploadAck(replies, 1000, { it == "UPLOAD_OK" }) {
            replies.emit("UPLOAD_ERR")
            Result.success(Unit)
        }
        assertEquals("UPLOAD_ERR", ack)
    }

    @Test fun failedSendDoesNotLeaveASubscriberBehind() = runBlocking {
        val replies = MutableSharedFlow<String>()
        assertNull(sendAndAwaitUploadAck(replies, 1000, { true }) {
            Result.failure(IllegalStateException("Disconnected"))
        })
        assertEquals(0, replies.subscriptionCount.value)
    }

    @Test fun missingAckTimesOutAndUnsubscribes() = runBlocking {
        val replies = MutableSharedFlow<String>()
        assertNull(sendAndAwaitUploadAck(replies, 25, { true }) { Result.success(Unit) })
        assertEquals(0, replies.subscriptionCount.value)
    }
}
