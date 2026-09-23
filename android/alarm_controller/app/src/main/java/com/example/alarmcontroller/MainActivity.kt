package com.example.alarmcontroller

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
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
import com.example.alarmcontroller.ui.MainScreen
import com.example.alarmcontroller.ui.MainViewModel
import com.example.alarmcontroller.ui.theme.AlarmControllerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        checkNfcIntent(intent)

        setContent {
            AlarmControllerTheme {
                var hasPermissions by remember {
                    mutableStateOf(checkRequiredPermissions())
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissionsMap ->
                    hasPermissions = permissionsMap.values.all { it }
                }

                // Check Bluetooth is turned ON
                LaunchedEffect(hasPermissions) {
                    if (hasPermissions && !viewModel.isBluetoothEnabled) {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        try {
                            startActivity(enableBtIntent)
                        } catch (e: SecurityException) {
                            // Ignored if permissions not active yet
                        }
                    }
                }

                MainScreen(
                    viewModel = viewModel,
                    hasPermissions = hasPermissions,
                    onRequestPermissions = {
                        permissionLauncher.launch(getRequiredPermissions())
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Active la lecture NFC prioritaire en premier plan
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_MUTABLE
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
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

    private fun checkNfcIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED
        ) {
            viewModel.onNfcTagDetected()
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions.toTypedArray()
    }

    private fun checkRequiredPermissions(): Boolean {
        return getRequiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
    }
}
