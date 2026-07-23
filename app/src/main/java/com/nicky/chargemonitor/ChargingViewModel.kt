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
import com.nicky.chargemonitor.session.GuidedTestRun
import com.nicky.chargemonitor.session.SessionExportFormat
import com.nicky.chargemonitor.session.SessionExporter
import com.nicky.chargemonitor.session.sliceForTest
import com.nicky.chargemonitor.service.DiagnosticRecordingService
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
    private val chargeMonitorApplication = application as ChargeMonitorApplication
    private val batteryMonitor = chargeMonitorApplication.batteryMonitor
    private val sessionRecorder = chargeMonitorApplication.sessionRecorder
    private val accessoryProfileStore = chargeMonitorApplication.accessoryProfileStore
    private val extendedRecordingController =
        chargeMonitorApplication.extendedRecordingController
    private val sessionExporter = SessionExporter(application)
    private val mutableState = MutableStateFlow(
        DashboardState(sessionSummaries = sessionRecorder.summaries()),
    )

    val state: StateFlow<DashboardState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            extendedRecordingController.status.collect { recordingStatus ->
                mutableState.update {
                    it.copy(extendedRecordingActive = recordingStatus.active)
                }
                if (recordingStatus.revision > 0L) {
                    val selectedSessionId = mutableState.value.selectedSession?.id
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
                val selectedSessionId = mutableState.value.selectedSession?.id
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
                    current.withReading(update.reading).copy(
                        currentAccessory = update.accessoryProfile,
                    ).let { withReading ->
                        if (update.sessionSnapshot != null) {
                            withReading.withSessionSnapshot(update.sessionSnapshot)
                        } else {
                            withReading
                        }
                    }
                }
                advanceGuidedTest(update.reading, update.timestampMillis)
                val sampleDuration = SystemClock.elapsedRealtime() - sampleStartedAt
                delay((SAMPLE_INTERVAL_MILLIS - sampleDuration).coerceAtLeast(16L))
            }
        } finally {
            extendedRecordingController.dashboardStopped()
            val selectedSessionId = mutableState.value.selectedSession?.id
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

    fun selectComparison(sessionIds: List<String>) {
        viewModelScope.launch {
            val sessions = withContext(Dispatchers.IO) {
                sessionIds.take(2).mapNotNull(sessionRecorder::session)
            }
            mutableState.update { it.copy(comparisonSessions = sessions) }
        }
    }

    fun clearComparison() {
        mutableState.update { it.copy(comparisonSessions = emptyList()) }
    }

    fun beginGuidedTest() {
        mutableState.update {
            it.copy(
                guidedTest = GuidedChargingTestState(phase = GuidedTestPhase.PREPARE_A),
                comparisonSessions = emptyList(),
            )
        }
    }

    fun armGuidedTestPhase() {
        mutableState.update { state ->
            if (state.reading?.isPowerConnected == true) return@update state
            val nextPhase = when (state.guidedTest.phase) {
                GuidedTestPhase.PREPARE_A -> GuidedTestPhase.WAITING_FOR_A
                GuidedTestPhase.PREPARE_B -> GuidedTestPhase.WAITING_FOR_B
                else -> return@update state
            }
            state.copy(guidedTest = state.guidedTest.copy(phase = nextPhase))
        }
    }

    fun finishGuidedTestPhase() {
        val timestampMillis = System.currentTimeMillis()
        var completedState: GuidedChargingTestState? = null
        mutableState.update { state ->
            val test = state.guidedTest
            val updated = when (test.phase) {
                GuidedTestPhase.RUNNING_A -> test.copy(
                    phase = GuidedTestPhase.PREPARE_B,
                    firstRun = test.firstRun?.copy(endedAtMillis = timestampMillis),
                )
                GuidedTestPhase.RUNNING_B -> test.copy(
                    phase = GuidedTestPhase.COMPLETE,
                    secondRun = test.secondRun?.copy(endedAtMillis = timestampMillis),
                ).also { completedState = it }
                else -> return@update state
            }
            state.copy(guidedTest = updated)
        }
        completedState?.let(::prepareGuidedComparison)
    }

    fun cancelGuidedTest() {
        mutableState.update {
            it.copy(guidedTest = GuidedChargingTestState())
        }
    }

    fun saveCurrentAccessoryName(name: String) {
        val reading = mutableState.value.reading ?: return
        if (!reading.isPowerConnected) return
        viewModelScope.launch {
            val update = withContext(Dispatchers.IO) {
                val profile = accessoryProfileStore.saveName(reading, name)
                sessionRecorder.renameAccessory(profile)
                profile to recorderSnapshot(mutableState.value.selectedSession?.id)
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
            selectedSession = selectedSessionId?.let(sessionRecorder::session),
            accessoryBaselines = sessionRecorder.accessoryBaselines(),
        )

    private fun advanceGuidedTest(reading: BatteryReading, timestampMillis: Long) {
        var completedState: GuidedChargingTestState? = null
        mutableState.update { state ->
            val test = state.guidedTest
            val activeSessionId = state.activeSessionId
            val updated = when {
                test.phase == GuidedTestPhase.WAITING_FOR_A &&
                    reading.isPowerConnected &&
                    activeSessionId != null -> test.copy(
                        phase = GuidedTestPhase.RUNNING_A,
                        firstRun = GuidedTestRun(
                            sessionId = activeSessionId,
                            startedAtMillis = timestampMillis,
                            endedAtMillis = null,
                        ),
                    )
                test.phase == GuidedTestPhase.RUNNING_A && !reading.isPowerConnected ->
                    test.copy(
                        phase = GuidedTestPhase.PREPARE_B,
                        firstRun = test.firstRun?.copy(endedAtMillis = timestampMillis),
                    )
                test.phase == GuidedTestPhase.WAITING_FOR_B &&
                    reading.isPowerConnected &&
                    activeSessionId != null -> test.copy(
                        phase = GuidedTestPhase.RUNNING_B,
                        secondRun = GuidedTestRun(
                            sessionId = activeSessionId,
                            startedAtMillis = timestampMillis,
                            endedAtMillis = null,
                        ),
                    )
                test.phase == GuidedTestPhase.RUNNING_B && !reading.isPowerConnected ->
                    test.copy(
                        phase = GuidedTestPhase.COMPLETE,
                        secondRun = test.secondRun?.copy(endedAtMillis = timestampMillis),
                    ).also { completedState = it }
                else -> return@update state
            }
            state.copy(guidedTest = updated)
        }
        completedState?.let(::prepareGuidedComparison)
    }

    private fun prepareGuidedComparison(test: GuidedChargingTestState) {
        val firstRun = test.firstRun ?: return
        val secondRun = test.secondRun ?: return
        viewModelScope.launch {
            val sessions = withContext(Dispatchers.IO) {
                listOfNotNull(
                    sessionRecorder.session(firstRun.sessionId)?.sliceForTest(firstRun),
                    sessionRecorder.session(secondRun.sessionId)?.sliceForTest(secondRun),
                )
            }
            mutableState.update { it.copy(comparisonSessions = sessions) }
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
    val selectedSession: ChargingSession?,
    val accessoryBaselines: Map<String, AccessoryBaseline>,
)

private fun DashboardState.withSessionSnapshot(snapshot: SessionSnapshot): DashboardState = copy(
    sessionSummaries = snapshot.summaries,
    activeSessionId = snapshot.activeSessionId,
    selectedSession = snapshot.selectedSession,
    currentAccessoryBaseline = currentAccessory?.id?.let(snapshot.accessoryBaselines::get),
)
