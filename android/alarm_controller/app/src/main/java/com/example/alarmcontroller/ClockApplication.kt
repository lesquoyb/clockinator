package com.example.alarmcontroller

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.alarmcontroller.communication.ble.BackgroundClockSync
import com.example.alarmcontroller.ui.MainViewModel

/** One transport owner for the UI, worker and optional foreground service. */
class ClockApplication : Application(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    fun clockModel(): MainViewModel = ViewModelProvider(this,
        ViewModelProvider.AndroidViewModelFactory.getInstance(this))[MainViewModel::class.java]
    override fun onCreate() {
        super.onCreate()
        BackgroundClockSync.schedule(this)
    }
}
