package com.example.alarmcontroller

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.alarmcontroller.communication.ble.BleAutoConnectService
import com.example.alarmcontroller.ui.MainScreen
import com.example.alarmcontroller.ui.MainViewModel
import com.example.alarmcontroller.ui.theme.AlarmControllerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    // Tracks whether the app is currently in write-tag mode (set from NfcTagConfigScreen)
    var nfcWriteMode: String? = null  // "ODODO" | "OBOULO" | null

    fun beginNfcTagWrite(payload: String) {
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_SHORT).show()
            return
        }
        if (nfcAdapter?.isEnabled != true) {
            Toast.makeText(this, "Enable NFC, then tap Write again.", Toast.LENGTH_SHORT).show()
            return
        }
        nfcWriteMode = payload
        Toast.makeText(this, "Ready to write $payload. Present an NFC tag.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        checkNfcIntent(intent)

        // Start the BLE background service so autoConnect keeps working when the
        // app is not in the foreground.
        // Retire the old waiting notification, including an already running service.
        BleAutoConnectService.stop(this)

        setContent {
            AlarmControllerTheme {
                var hasPermissions by remember { mutableStateOf(checkRequiredPermissions()) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { map -> hasPermissions = map.values.all { it } }

                LaunchedEffect(hasPermissions) {
                    if (hasPermissions && !viewModel.isBluetoothEnabled) {
                        try {
                            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        } catch (_: SecurityException) {}
                    }
                }

                MainScreen(
                    viewModel = viewModel,
                    hasPermissions = hasPermissions,
                    onRequestPermissions = { permissionLauncher.launch(getRequiredPermissions()) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Enable foreground NFC dispatch so the activity gets priority over all tags
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        nfcAdapter?.enableForegroundDispatch(this, pi, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkNfcIntent(intent)
    }

    /**
     * Routes NFC intents depending on the current mode:
     *  - Write mode: write the configured payload onto the scanned tag.
     *  - Normal mode: read the tag payload and dispatch to the ViewModel.
     */
    private fun checkNfcIntent(intent: Intent?) {
        val action = intent?.action ?: return
        val isNfcAction = action == NfcAdapter.ACTION_TAG_DISCOVERED ||
                          action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
                          action == NfcAdapter.ACTION_TECH_DISCOVERED
        if (!isNfcAction) return

        val writePayload = nfcWriteMode
        if (writePayload != null) {
            // Write mode: encode the payload onto the tag, then exit write mode
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, android.nfc.Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            if (tag == null) {
                Toast.makeText(this, "Could not read the NFC tag. Try again.", Toast.LENGTH_SHORT).show()
                return
            }
            nfcWriteMode = null
            viewModel.writeNfcTag(tag, writePayload)
        } else {
            // Normal mode: read and dispatch
            val tagPayload = readNdefTextPayload(intent)
            viewModel.onNfcTagDetected(tagPayload)
        }
    }

    // ------------------------------------------------------------------
    // NDEF helpers
    // ------------------------------------------------------------------

    /**
     * Reads the first NDEF TEXT record from the intent and returns its text,
     * or null if no TEXT record is found.
     *
     * NDEF TEXT record payload layout:
     *   Byte 0    : status byte  (bits 0-5 = lang length, bit 7 = UTF-16 flag)
     *   Bytes 1..N: language code (e.g. "en")
     *   Rest      : actual text (UTF-8 or UTF-16)
     */
    private fun readNdefTextPayload(intent: Intent): String? {
        val rawMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        } ?: return null

        for (raw in rawMessages) {
            val msg = raw as? NdefMessage ?: continue
            for (record in msg.records) {
                val text = extractTextFromRecord(record)
                if (text != null) return text
            }
        }
        return null
    }

    private fun extractTextFromRecord(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_WELL_KNOWN) return null
        if (!record.type.contentEquals(NdefRecord.RTD_TEXT)) return null
        return try {
            val payload    = record.payload
            val statusByte = payload[0].toInt() and 0xFF
            val isUtf16    = (statusByte and 0x80) != 0
            val langLen    = statusByte and 0x3F
            val encoding   = if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8
            String(payload, 1 + langLen, payload.size - 1 - langLen, encoding).trim()
        } catch (_: Exception) { null }
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    private fun getRequiredPermissions(): Array<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            perms += Manifest.permission.BLUETOOTH
            perms += Manifest.permission.BLUETOOTH_ADMIN
            perms += Manifest.permission.ACCESS_FINE_LOCATION
            perms += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        return perms.toTypedArray()
    }

    private fun checkRequiredPermissions() =
        getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
}
