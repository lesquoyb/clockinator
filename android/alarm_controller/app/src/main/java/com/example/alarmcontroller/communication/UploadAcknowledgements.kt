package com.example.alarmcontroller.communication

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Subscribe before sending so even an immediate reply cannot be lost. */
internal suspend fun sendAndAwaitUploadAck(
    acknowledgements: Flow<String>,
    timeoutMillis: Long,
    matches: (String) -> Boolean,
    send: suspend () -> Result<Unit>
): String? = coroutineScope {
    val reply = async(start = CoroutineStart.UNDISPATCHED) {
        withTimeoutOrNull(timeoutMillis) {
            acknowledgements.first { matches(it) || it == SyncPayload.ACK_UPLOAD_ERR }
        }
    }
    try {
        if (send().isFailure) null else reply.await()
    } finally {
        reply.cancel()
    }
}
