package com.nicky.chargemonitor.service

import com.nicky.chargemonitor.battery.BatteryReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ExtendedRecordingStatus(
    val active: Boolean = false,
    val dashboardSamplingActive: Boolean = false,
    val revision: Long = 0L,
    val lastReading: BatteryReading? = null,
)

class ExtendedRecordingController {
    private val mutableStatus = MutableStateFlow(ExtendedRecordingStatus())
    val status: StateFlow<ExtendedRecordingStatus> = mutableStatus.asStateFlow()

    fun prepareToStart() {
        mutableStatus.value = mutableStatus.value.copy(active = true)
    }

    fun dashboardStarted() {
        mutableStatus.value = mutableStatus.value.copy(dashboardSamplingActive = true)
    }

    fun dashboardStopped() {
        mutableStatus.value = mutableStatus.value.copy(dashboardSamplingActive = false)
    }

    fun publish(reading: BatteryReading) {
        val previous = mutableStatus.value
        mutableStatus.value = previous.copy(
            active = true,
            revision = previous.revision + 1L,
            lastReading = reading,
        )
    }

    fun stopped() {
        mutableStatus.value = mutableStatus.value.copy(
            active = false,
            lastReading = null,
        )
    }
}
