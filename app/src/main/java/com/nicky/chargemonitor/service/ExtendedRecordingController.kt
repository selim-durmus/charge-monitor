package com.nicky.chargemonitor.service

import com.nicky.chargemonitor.battery.BatteryReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ExtendedRecordingStatus(
    val active: Boolean = false,
    val dashboardSamplingActive: Boolean = false,
    val revision: Long = 0L,
    val lastReading: BatteryReading? = null,
)

/**
 * Shared between the main thread (dashboard lifecycle) and the service's IO sampling loop,
 * so every mutation goes through [MutableStateFlow.update] to stay atomic.
 */
class ExtendedRecordingController {
    private val mutableStatus = MutableStateFlow(ExtendedRecordingStatus())
    val status: StateFlow<ExtendedRecordingStatus> = mutableStatus.asStateFlow()

    fun prepareToStart() {
        mutableStatus.update { it.copy(active = true) }
    }

    fun dashboardStarted() {
        mutableStatus.update { it.copy(dashboardSamplingActive = true) }
    }

    fun dashboardStopped() {
        mutableStatus.update { it.copy(dashboardSamplingActive = false) }
    }

    fun publish(reading: BatteryReading) {
        mutableStatus.update {
            it.copy(
                active = true,
                revision = it.revision + 1L,
                lastReading = reading,
            )
        }
    }

    fun stopped() {
        mutableStatus.update {
            it.copy(
                active = false,
                revision = it.revision + 1L,
                lastReading = null,
            )
        }
    }
}
