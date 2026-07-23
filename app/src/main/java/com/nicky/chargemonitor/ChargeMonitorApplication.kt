package com.nicky.chargemonitor

import android.app.Application
import com.nicky.chargemonitor.accessory.AccessoryProfileStore
import com.nicky.chargemonitor.battery.AndroidBatteryMonitor
import com.nicky.chargemonitor.session.ChargingSessionRecorder
import com.nicky.chargemonitor.service.ExtendedRecordingController

class ChargeMonitorApplication : Application() {
    val batteryMonitor: AndroidBatteryMonitor by lazy {
        AndroidBatteryMonitor(this)
    }

    val sessionRecorder: ChargingSessionRecorder by lazy {
        ChargingSessionRecorder(this)
    }

    val accessoryProfileStore: AccessoryProfileStore by lazy {
        AccessoryProfileStore(this)
    }

    val extendedRecordingController = ExtendedRecordingController()
}
