package com.example.alarmcontroller.alarm.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable

/**
 * Writes a plain-text NDEF record onto a physical NFC tag.
 *
 * The tag must be presented to the device while the app is in write mode
 * (MainActivity.nfcWriteMode is set).  Any writable NDEF or NDEF-formatable
 * tag will work e.g. NTAG213/215/216, Mifare Ultralight.
 *
 * Default payloads used by this app:
 *   "ODODO"    -> tag placed at the "bedtime alarm" spot
 *   "OBOULO"   -> tag placed at the "wake-up alarm" spot
 *
 * Tags that already carry these payloads work out of the box, no configuration needed.
 */
object NfcWriteManager {

    private const val LANGUAGE_CODE = "en"

    /**
     * Writes [text] as an NDEF TEXT record onto [tag].
     * Works on both already-NDEF-formatted tags and blank NDEF-formatable tags.
     *
     * @return Result.success(Unit) on success, Result.failure(exception) on error.
     */
    fun write(tag: Tag, text: String): Result<Unit> {
        val message = buildNdefMessage(text)

        // Try Ndef first (tag is already formatted)
        Ndef.get(tag)?.let { ndef ->
            return try {
                ndef.connect()
                if (!ndef.isWritable) {
                    ndef.close()
                    return Result.failure(Exception("Tag is read-only."))
                }
                if (ndef.maxSize < message.toByteArray().size) {
                    ndef.close()
                    return Result.failure(Exception("Tag capacity too small."))
                }
                ndef.writeNdefMessage(message)
                ndef.close()
                Result.success(Unit)
            } catch (e: Exception) {
                try { ndef.close() } catch (_: Exception) {}
                Result.failure(e)
            }
        }

        // Fall back to NdefFormatable (blank tag)
        NdefFormatable.get(tag)?.let { formatable ->
            return try {
                formatable.connect()
                formatable.format(message)
                formatable.close()
                Result.success(Unit)
            } catch (e: Exception) {
                try { formatable.close() } catch (_: Exception) {}
                Result.failure(e)
            }
        }

        return Result.failure(Exception("Tag does not support NDEF."))
    }

    /**
     * Builds an NDEF message containing a single TEXT record for [text].
     *
     * TEXT record payload layout (RFC 5646):
     *   Byte 0    : status byte  (bit 7 = 0 → UTF-8; bits 0-5 = lang length)
     *   Bytes 1..N: language code (ASCII)
     *   Rest      : text (UTF-8)
     */
    private fun buildNdefMessage(text: String): NdefMessage {
        val langBytes  = LANGUAGE_CODE.toByteArray(Charsets.US_ASCII)
        val textBytes  = text.toByteArray(Charsets.UTF_8)
        val payload    = ByteArray(1 + langBytes.size + textBytes.size)
        payload[0]     = langBytes.size.toByte() // status byte: UTF-8, lang length
        langBytes.copyInto(payload, 1)
        textBytes.copyInto(payload, 1 + langBytes.size)

        val record = NdefRecord(
            NdefRecord.TNF_WELL_KNOWN,
            NdefRecord.RTD_TEXT,
            ByteArray(0),
            payload
        )
        return NdefMessage(arrayOf(record))
    }
}
