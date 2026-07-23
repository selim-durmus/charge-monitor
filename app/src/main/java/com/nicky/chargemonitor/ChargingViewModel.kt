package com.nicky.chargemonitor

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nicky.chargemonitor.battery.AndroidBatteryMonitor
import com.nicky.chargemonitor.battery.BatteryReading
import com.nicky.chargemonitor.session.ChargingSession
import com.nicky.chargemonitor.session.ChargingSessionRecorder
import com.nicky.chargemonitor.session.ChargingSessionSummary
import com.nicky.chargemonitor.session.SessionExportFormat
import com.nicky.chargemonitor.session.SessionExporter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ChargingViewModel(application: Application) : AndroidViewModel(application) {
    private val batteryMonitor = AndroidBatteryMonitor(application)
    private val sessionRecorder = ChargingSessionRecorder(application)
    private val sessionExporter = SessionExporter(application)
    private val mutableState = MutableStateFlow(
        DashboardState(sessionSummaries = sessionRecorder.summaries()),
    )

    val state: StateFlow<DashboardState> = mutableState.asStateFlow()

    /**
     * Called from repeatOnLifecycle(RESUMED), so the loop is cancelled as soon as
     * the app loses focus and restarted when the dashboard is visible again.
     */
    suspend fun monitorWhileResumed() {
        mutableState.update { it.copy(isSampling = true) }
        try {
            while (currentCoroutineContext().isActive) {
                val sampleStartedAt = SystemClock.elapsedRealtime()
                val selectedSessionId = mutableState.value.selectedSession?.id
                val update = withContext(Dispatchers.IO) {
                    val reading = batteryMonitor.read()
                    val sessionsChanged = sessionRecorder.record(
                        reading = reading,
                        timestampMillis = System.currentTimeMillis(),
                    )
                    MonitorUpdate(
                        reading = reading,
                        sessionSnapshot = if (sessionsChanged) {
                            recorderSnapshot(selectedSessionId)
                        } else {
                            null
                        },
                    )
                }
                mutableState.update { current ->
                    current.withReading(update.reading).let { withReading ->
                        update.sessionSnapshot?.let(withReading::withSessionSnapshot)
                            ?: withReading
                    }
                }
                val sampleDuration = SystemClock.elapsedRealtime() - sampleStartedAt
                delay((SAMPLE_INTERVAL_MILLIS - sampleDuration).coerceAtLeast(16L))
            }
        } finally {
            val selectedSessionId = mutableState.value.selectedSession?.id
            val sessionSnapshot = withContext(NonCancellable + Dispatchers.IO) {
                sessionRecorder.onForegroundStopped(System.currentTimeMillis())
                recorderSnapshot(selectedSessionId)
            }
            mutableState.update {
                it.withSessionSnapshot(sessionSnapshot).copy(isSampling = false)
            }
        }
    }

    fun selectSession(sessionId: String) {
        viewModelScope.launch {
            val session = withContext(Dispatchers.IO) {
                sessionRecorder.session(sessionId)
            }
            mutableState.update { it.copy(selectedSession = session) }
        }
    }

    fun clearSelectedSession() {
        mutableState.update { it.copy(selectedSession = null) }
    }

    suspend fun exportSession(sessionId: String, format: SessionExportFormat): File =
        withContext(Dispatchers.IO) {
            val session = sessionRecorder.session(sessionId)
                ?: error("Charging session is no longer available")
            sessionExporter.export(session, format)
        }

    private fun recorderSnapshot(selectedSessionId: String?): SessionSnapshot =
        SessionSnapshot(
            summaries = sessionRecorder.summaries(),
            activeSessionId = sessionRecorder.activeSessionId(),
            selectedSession = selectedSessionId?.let(sessionRecorder::session),
        )
}

private data class MonitorUpdate(
    val reading: BatteryReading,
    val sessionSnapshot: SessionSnapshot?,
)

private data class SessionSnapshot(
    val summaries: List<ChargingSessionSummary>,
    val activeSessionId: String?,
    val selectedSession: ChargingSession?,
)

private fun DashboardState.withSessionSnapshot(snapshot: SessionSnapshot): DashboardState = copy(
    sessionSummaries = snapshot.summaries,
    activeSessionId = snapshot.activeSessionId,
    selectedSession = snapshot.selectedSession,
)
