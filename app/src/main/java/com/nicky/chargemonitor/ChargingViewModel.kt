package com.nicky.chargemonitor

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nicky.chargemonitor.accessory.AccessoryBaseline
import com.nicky.chargemonitor.accessory.AccessoryProfile
import com.nicky.chargemonitor.battery.BatteryReading
import com.nicky.chargemonitor.session.ChargingSession
import com.nicky.chargemonitor.session.ChargingSessionSummary
import com.nicky.chargemonitor.session.GuidedChargingTestState
import com.nicky.chargemonitor.session.GuidedTestPhase
import com.nicky.chargemonitor.session.SessionComparator
import com.nicky.chargemonitor.session.SessionExportFormat
import com.nicky.chargemonitor.session.SessionExporter
import com.nicky.chargemonitor.session.advance
import com.nicky.chargemonitor.session.arm
import com.nicky.chargemonitor.session.finishWindow
import com.nicky.chargemonitor.session.sliceForTest
import com.nicky.chargemonitor.service.DiagnosticRecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ChargingViewModel(application: Application) : AndroidViewModel(application) {
    private val chargeMonitorApplication = application as ChargeMonitorApplication
    private val batteryMonitor = chargeMonitorApplication.batteryMonitor
    private val sessionRecorder by lazy { chargeMonitorApplication.sessionRecorder }
    private val accessoryProfileStore = chargeMonitorApplication.accessoryProfileStore
    private val extendedRecordingController =
        chargeMonitorApplication.extendedRecordingController
    private val sessionExporter = SessionExporter(application)
    private val mutableState = MutableStateFlow(DashboardState())
    private var comparisonJob: Job? = null

    val state: StateFlow<DashboardState> = mutableState.asStateFlow()

    init {
        // Opening the database and loading up to five full traces is disk work; keep it
        // off the main thread instead of doing it while the ViewModel is constructed.
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { recorderSnapshot(null) }
            mutableState.update { it.withSessionSnapshot(snapshot) }
        }
        viewModelScope.launch {
            extendedRecordingController.status.collect { recordingStatus ->
                mutableState.update {
                    it.copy(extendedRecordingActive = recordingStatus.active)
                }
                if (recordingStatus.revision > 0L) {
                    val selectedSessionId = mutableState.value.selectedSessionId
                    val snapshot = withContext(Dispatchers.IO) {
                        recorderSnapshot(selectedSessionId)
                    }
                    mutableState.update {
                        it.withSessionSnapshot(snapshot).copy(
                            extendedRecordingActive = recordingStatus.active,
                        )
                    }
                }
            }
        }
    }

    /**
     * Called from repeatOnLifecycle(RESUMED), so the loop is cancelled as soon as
     * the app loses focus and restarted when the dashboard is visible again.
     */
    suspend fun monitorWhileResumed() {
        extendedRecordingController.dashboardStarted()
        mutableState.update { it.copy(isSampling = true) }
        try {
            while (currentCoroutineContext().isActive) {
                val sampleStartedAt = SystemClock.elapsedRealtime()
                val selectedSessionId = mutableState.value.selectedSessionId
                val update = withContext(Dispatchers.IO) {
                    val reading = batteryMonitor.read()
                    val timestampMillis = System.currentTimeMillis()
                    val accessoryProfile = accessoryProfileStore.profileFor(reading)
                    val sessionsChanged =
                        if (extendedRecordingController.status.value.active) {
                            false
                        } else {
                            sessionRecorder.record(
                                reading = reading,
                                timestampMillis = timestampMillis,
                                accessoryProfile = accessoryProfile,
                            )
                        }
                    MonitorUpdate(
                        reading = reading,
                        timestampMillis = timestampMillis,
                        accessoryProfile = accessoryProfile,
                        sessionSnapshot = if (sessionsChanged) {
                            recorderSnapshot(selectedSessionId)
                        } else {
                            null
                        },
                    )
                }
                mutableState.update { current ->
                    val withReading = current.withReading(update.reading).copy(
                        currentAccessory = update.accessoryProfile,
                    )
                    update.sessionSnapshot?.let(withReading::withSessionSnapshot)
                        ?: withReading
                }
                advanceGuidedTest(update.reading, update.timestampMillis)
                val sampleDuration = SystemClock.elapsedRealtime() - sampleStartedAt
                delay((SAMPLE_INTERVAL_MILLIS - sampleDuration).coerceAtLeast(16L))
            }
        } finally {
            extendedRecordingController.dashboardStopped()
            val selectedSessionId = mutableState.value.selectedSessionId
            val sessionSnapshot = withContext(NonCancellable + Dispatchers.IO) {
                if (!extendedRecordingController.status.value.active) {
                    sessionRecorder.onForegroundStopped(System.currentTimeMillis())
                }
                recorderSnapshot(selectedSessionId)
            }
            mutableState.update {
                it.withSessionSnapshot(sessionSnapshot).copy(isSampling = false)
            }
        }
    }

    fun selectSession(sessionId: String) {
        if (mutableState.value.selectedSessionId == sessionId) return
        mutableState.update { it.copy(selectedSessionId = sessionId, selectedSession = null) }
        viewModelScope.launch {
            val session = withContext(Dispatchers.IO) {
                sessionRecorder.session(sessionId)
            }
            mutableState.update {
                if (it.selectedSessionId == sessionId) it.copy(selectedSession = session) else it
            }
        }
    }

    fun selectComparison(sessionIds: List<String>) {
        loadComparison {
            sessionIds.take(2).mapNotNull(sessionRecorder::session)
        }
    }

    fun beginGuidedTest() {
        comparisonJob?.cancel()
        mutableState.update {
            it.copy(
                guidedTest = GuidedChargingTestState(phase = GuidedTestPhase.PREPARE_A),
                comparisonSessions = emptyList(),
                comparison = null,
                comparisonLoading = false,
            )
        }
    }

    fun armGuidedTestPhase() {
        mutableState.update { state ->
            state.copy(
                guidedTest = state.guidedTest.arm(state.reading?.isPowerConnected == true),
            )
        }
    }

    fun finishGuidedTestPhase() {
        updateGuidedTest { it.finishWindow(System.currentTimeMillis()) }
    }

    fun cancelGuidedTest() {
        mutableState.update {
            it.copy(guidedTest = GuidedChargingTestState())
        }
    }

    fun saveCurrentAccessoryName(name: String) {
        val reading = mutableState.value.reading ?: return
        if (!reading.isPowerConnected || name.isBlank()) return
        viewModelScope.launch {
            val update = withContext(Dispatchers.IO) {
                val profile = accessoryProfileStore.saveName(reading, name)
                sessionRecorder.renameAccessory(profile)
                profile to recorderSnapshot(mutableState.value.selectedSessionId)
            }
            mutableState.update {
                it.copy(currentAccessory = update.first)
                    .withSessionSnapshot(update.second)
            }
        }
    }

    fun startExtendedRecording() {
        if (mutableState.value.reading?.isPowerConnected != true) return
        extendedRecordingController.prepareToStart()
        runCatching {
            ContextCompat.startForegroundService(
                getApplication(),
                Intent(
                    getApplication(),
                    DiagnosticRecordingService::class.java,
                ).setAction(DiagnosticRecordingService.ACTION_START),
            )
        }.onFailure {
            extendedRecordingController.stopped()
        }
    }

    fun stopExtendedRecording() {
        getApplication<Application>().stopService(
            Intent(getApplication(), DiagnosticRecordingService::class.java),
        )
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
            selectedSessionId = selectedSessionId,
            selectedSession = selectedSessionId?.let(sessionRecorder::session),
            accessoryBaselines = sessionRecorder.accessoryBaselines(),
        )

    private fun advanceGuidedTest(reading: BatteryReading, timestampMillis: Long) {
        if (!mutableState.value.guidedTest.isActive) return
        updateGuidedTest { test ->
            test.advance(
                isPowerConnected = reading.isPowerConnected,
                activeSessionId = mutableState.value.activeSessionId,
                timestampMillis = timestampMillis,
            )
        }
    }

    private inline fun updateGuidedTest(
        transform: (GuidedChargingTestState) -> GuidedChargingTestState,
    ) {
        val before = mutableState.value.guidedTest
        val after = transform(before)
        if (after == before) return
        mutableState.update { it.copy(guidedTest = after) }
        if (after.phase == GuidedTestPhase.COMPLETE && before.phase != GuidedTestPhase.COMPLETE) {
            prepareGuidedComparison(after)
        }
    }

    private fun prepareGuidedComparison(test: GuidedChargingTestState) {
        val firstRun = test.firstRun ?: return
        val secondRun = test.secondRun ?: return
        loadComparison {
            listOfNotNull(
                sessionRecorder.session(firstRun.sessionId)?.sliceForTest(firstRun),
                sessionRecorder.session(secondRun.sessionId)?.sliceForTest(secondRun),
            )
        }
    }

    /** Loads sessions and runs the comparison off the main thread, once. */
    private fun loadComparison(loadSessions: () -> List<ChargingSession>) {
        comparisonJob?.cancel()
        mutableState.update {
            it.copy(comparisonSessions = emptyList(), comparison = null, comparisonLoading = true)
        }
        comparisonJob = viewModelScope.launch {
            val sessions = withContext(Dispatchers.IO) { loadSessions() }
            val comparison = if (sessions.size == 2) {
                withContext(Dispatchers.Default) {
                    SessionComparator.compare(sessions[0], sessions[1])
                }
            } else {
                null
            }
            mutableState.update {
                it.copy(
                    comparisonSessions = sessions,
                    comparison = comparison,
                    comparisonLoading = false,
                )
            }
        }
    }
}

private data class MonitorUpdate(
    val reading: BatteryReading,
    val timestampMillis: Long,
    val accessoryProfile: AccessoryProfile?,
    val sessionSnapshot: SessionSnapshot?,
)

private data class SessionSnapshot(
    val summaries: List<ChargingSessionSummary>,
    val activeSessionId: String?,
    val selectedSessionId: String?,
    val selectedSession: ChargingSession?,
    val accessoryBaselines: Map<String, AccessoryBaseline>,
)

private fun DashboardState.withSessionSnapshot(snapshot: SessionSnapshot): DashboardState = copy(
    sessionsLoaded = true,
    sessionSummaries = snapshot.summaries,
    activeSessionId = snapshot.activeSessionId,
    // A snapshot only refreshes the open report if it was taken for that same report;
    // otherwise a snapshot started before the user tapped a session would blank it out.
    selectedSession = if (
        snapshot.selectedSessionId != null && snapshot.selectedSessionId == selectedSessionId
    ) {
        snapshot.selectedSession
    } else {
        selectedSession
    },
    currentAccessoryBaseline = currentAccessory?.id?.let(snapshot.accessoryBaselines::get),
)
