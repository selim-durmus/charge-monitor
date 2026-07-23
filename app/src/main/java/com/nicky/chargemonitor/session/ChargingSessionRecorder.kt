package com.nicky.chargemonitor.session

import android.content.Context
import com.nicky.chargemonitor.battery.BatteryReading
import com.nicky.chargemonitor.battery.ChargeState
import com.nicky.chargemonitor.battery.ChargingProfile
import com.nicky.chargemonitor.battery.PowerSource
import com.nicky.chargemonitor.battery.ThermalStatus
import java.util.UUID

class ChargingSessionRecorder(context: Context) {
    private val store = ChargingSessionStore(context.applicationContext)
    private var sessions = store.loadLatestSessions().map(::closeInterruptedSession)
    private var activeSession: ChargingSession? = null
    private var accumulator: SampleAccumulator? = null
    private var previousState: ReadingState? = null
    private var lastPowerConnected: Boolean? = null

    @Synchronized
    fun record(reading: BatteryReading, timestampMillis: Long): Boolean {
        if (!reading.isPowerConnected) {
            val changed = if (activeSession != null) {
                finishActiveSession(
                    timestampMillis = timestampMillis,
                    reason = SessionEndReason.UNPLUGGED,
                    eventType = SessionEventType.ENDED,
                    eventLabel = "Charger disconnected",
                )
                true
            } else {
                false
            }
            lastPowerConnected = false
            previousState = null
            return changed
        }

        var changed = false
        if (activeSession == null) {
            val joinedInProgress = lastPowerConnected == null
            changed = startOrResumeSession(
                reading = reading,
                timestampMillis = timestampMillis,
                joinedInProgress = joinedInProgress,
            )
        }

        val currentAccumulator = accumulator
        if (currentAccumulator != null &&
            reading.elapsedRealtimeMillis - currentAccumulator.startedAtElapsedRealtime >=
            SESSION_BUCKET_MILLIS
        ) {
            changed = flushBucket() || changed
        }

        changed = recordStateEvents(reading, timestampMillis) || changed
        val bucket = accumulator ?: SampleAccumulator(
            startedAtElapsedRealtime = reading.elapsedRealtimeMillis,
        ).also { accumulator = it }
        bucket.add(reading, timestampMillis)
        previousState = reading.toState()
        lastPowerConnected = true
        return changed
    }

    @Synchronized
    fun onForegroundStopped(timestampMillis: Long): Boolean {
        if (activeSession == null) {
            lastPowerConnected = null
            previousState = null
            return false
        }
        finishActiveSession(
            timestampMillis = timestampMillis,
            reason = SessionEndReason.APP_BACKGROUNDED,
            eventType = SessionEventType.PAUSED,
            eventLabel = "Recording paused when app left foreground",
        )
        lastPowerConnected = null
        previousState = null
        return true
    }

    @Synchronized
    fun summaries(): List<ChargingSessionSummary> =
        sessions.sortedByDescending { it.startedAtMillis }.map(SessionAnalyzer::summarize)

    @Synchronized
    fun session(sessionId: String): ChargingSession? =
        sessions.firstOrNull { it.id == sessionId } ?: store.loadSession(sessionId)

    @Synchronized
    fun activeSessionId(): String? = activeSession?.id

    private fun startOrResumeSession(
        reading: BatteryReading,
        timestampMillis: Long,
        joinedInProgress: Boolean,
    ): Boolean {
        val latest = sessions.maxByOrNull { it.startedAtMillis }
        val latestEnd = latest?.endedAtMillis
        val canResume = latest != null &&
            latest.endReason == SessionEndReason.APP_BACKGROUNDED &&
            latestEnd != null &&
            timestampMillis - latestEnd in 0..SESSION_REJOIN_WINDOW_MILLIS &&
            (latest.samples.lastOrNull()?.powerSource ?: latest.initialSource) ==
            reading.powerSource

        if (canResume) {
            val resumed = latest.copy(endedAtMillis = null, endReason = null)
            activeSession = resumed
            replaceCached(resumed)
            store.updateSessionMetadata(resumed)
            addEvent(
                SessionEvent(
                    timestampMillis = timestampMillis,
                    type = SessionEventType.RESUMED,
                    label = "Recording resumed after a foreground gap",
                ),
            )
            previousState = resumed.samples.lastOrNull()?.toState()
            return true
        }

        val session = ChargingSession(
            id = "${timestampMillis}-${UUID.randomUUID()}",
            startedAtMillis = timestampMillis,
            endedAtMillis = null,
            joinedInProgress = joinedInProgress,
            endReason = null,
            initialSource = reading.powerSource,
            samples = emptyList(),
            events = emptyList(),
        )
        activeSession = session
        sessions = (sessions + session)
            .sortedByDescending { it.startedAtMillis }
            .take(MAX_STORED_SESSIONS)
        store.insertSession(session)
        addEvent(
            SessionEvent(
                timestampMillis = timestampMillis,
                type = SessionEventType.STARTED,
                label = if (joinedInProgress) {
                    "App opened while ${reading.powerSource.shortLabel()} power was connected"
                } else {
                    "${reading.powerSource.shortLabel()} charger connected"
                },
            ),
        )
        previousState = null
        return true
    }

    private fun finishActiveSession(
        timestampMillis: Long,
        reason: SessionEndReason,
        eventType: SessionEventType,
        eventLabel: String,
    ) {
        flushBucket()
        addEvent(
            SessionEvent(
                timestampMillis = timestampMillis,
                type = eventType,
                label = eventLabel,
            ),
        )
        val finished = activeSession?.copy(
            endedAtMillis = timestampMillis,
            endReason = reason,
        ) ?: return
        activeSession = null
        accumulator = null
        previousState = null
        replaceCached(finished)
        store.updateSessionMetadata(finished)
    }

    private fun recordStateEvents(
        reading: BatteryReading,
        timestampMillis: Long,
    ): Boolean {
        val previous = previousState ?: return false
        val events = buildList {
            if (previous.powerSource != reading.powerSource) {
                add(
                    SessionEvent(
                        timestampMillis,
                        SessionEventType.SOURCE_CHANGED,
                        "Source changed from ${previous.powerSource.shortLabel()} to " +
                            reading.powerSource.shortLabel(),
                    ),
                )
            }
            if (previous.chargingProfile != reading.chargingProfile) {
                add(
                    SessionEvent(
                        timestampMillis,
                        SessionEventType.PROFILE_CHANGED,
                        "Profile changed to ${reading.chargingProfile.profileLabel()}",
                    ),
                )
            }
            if (previous.chargeState != reading.chargeState) {
                add(
                    SessionEvent(
                        timestampMillis,
                        SessionEventType.CHARGE_STATE_CHANGED,
                        "Battery state changed to ${reading.chargeState.label}",
                    ),
                )
            }
            if (previous.thermalStatus != reading.thermalStatus) {
                add(
                    SessionEvent(
                        timestampMillis,
                        SessionEventType.THERMAL_CHANGED,
                        "Thermal status changed to ${reading.thermalStatus.label}",
                    ),
                )
            }
        }
        events.forEach(::addEvent)
        return events.isNotEmpty()
    }

    private fun flushBucket(): Boolean {
        val session = activeSession ?: return false
        val bucket = accumulator ?: return false
        val sample = bucket.toDiagnosticSample(session.startedAtMillis) ?: return false
        store.appendSample(session.id, sample)
        val updated = session.copy(samples = session.samples + sample)
        activeSession = updated
        replaceCached(updated)
        accumulator = null
        return true
    }

    private fun addEvent(event: SessionEvent) {
        val session = activeSession ?: return
        store.appendEvent(session.id, event)
        val updated = session.copy(events = session.events + event)
        activeSession = updated
        replaceCached(updated)
    }

    private fun replaceCached(session: ChargingSession) {
        sessions = (sessions.filterNot { it.id == session.id } + session)
            .sortedByDescending { it.startedAtMillis }
            .take(MAX_STORED_SESSIONS)
    }

    private fun closeInterruptedSession(session: ChargingSession): ChargingSession {
        if (!session.isActive) return session
        val endedAt = session.samples.lastOrNull()?.timestampMillis
            ?: session.events.lastOrNull()?.timestampMillis
            ?: session.startedAtMillis
        val event = SessionEvent(
            timestampMillis = endedAt,
            type = SessionEventType.PAUSED,
            label = "Recording was interrupted",
        )
        store.appendEvent(session.id, event)
        val closed = session.copy(
            endedAtMillis = endedAt,
            endReason = SessionEndReason.APP_BACKGROUNDED,
            events = session.events + event,
        )
        store.updateSessionMetadata(closed)
        return closed
    }
}

private class SampleAccumulator(
    val startedAtElapsedRealtime: Long,
) {
    private val readings = mutableListOf<BatteryReading>()
    private var latestTimestampMillis: Long = 0L

    fun add(reading: BatteryReading, timestampMillis: Long) {
        readings += reading
        latestTimestampMillis = timestampMillis
    }

    fun toDiagnosticSample(sessionStartedAtMillis: Long): DiagnosticSample? {
        val latest = readings.lastOrNull() ?: return null
        val powers = readings.mapNotNull { it.powerWatts }.filter { it.isFinite() }
        val currents = readings
            .mapNotNull { it.currentAmps }
            .filter { it.isFinite() }
        return DiagnosticSample(
            timestampMillis = latestTimestampMillis,
            elapsedMillis = (latestTimestampMillis - sessionStartedAtMillis).coerceAtLeast(0L),
            powerMinimumWatts = powers.minOrNull(),
            powerAverageWatts = powers.averageOrNull(),
            powerMaximumWatts = powers.maxOrNull(),
            currentMinimumAmps = currents.minOrNull(),
            currentAverageAmps = currents.averageOrNull(),
            currentMaximumAmps = currents.maxOrNull(),
            systemAverageCurrentAmps = latest.averageCurrentMicroamps?.div(1_000_000.0),
            voltageVolts = readings.mapNotNull { it.voltageVolts }.averageOrNull(),
            levelPercent = latest.levelPercent,
            temperatureCelsius = readings.mapNotNull { it.temperatureCelsius }.averageOrNull(),
            thermalStatus = readings.maxByOrNull { it.thermalStatus.severity }?.thermalStatus
                ?: ThermalStatus.UNKNOWN,
            chargeState = latest.chargeState,
            powerSource = latest.powerSource,
            chargingProfile = latest.chargingProfile,
            sourceCeilingWatts = readings.mapNotNull { it.sourceCeilingWatts }.averageOrNull(),
            chargeCounterMilliampHours = latest.chargeCounterMilliampHours,
            energyCounterWattHours = latest.energyCounterWattHours,
            chargeTimeRemainingMillis = latest.chargeTimeRemainingMillis,
        )
    }
}

private data class ReadingState(
    val chargeState: ChargeState,
    val powerSource: PowerSource,
    val chargingProfile: ChargingProfile?,
    val thermalStatus: ThermalStatus,
)

private fun BatteryReading.toState(): ReadingState = ReadingState(
    chargeState = chargeState,
    powerSource = powerSource,
    chargingProfile = chargingProfile,
    thermalStatus = thermalStatus,
)

private fun DiagnosticSample.toState(): ReadingState = ReadingState(
    chargeState = chargeState,
    powerSource = powerSource,
    chargingProfile = chargingProfile,
    thermalStatus = thermalStatus,
)

private fun PowerSource.shortLabel(): String = when (this) {
    PowerSource.AC -> "AC"
    PowerSource.USB -> "USB"
    PowerSource.WIRELESS -> "Wireless"
    PowerSource.DOCK -> "Dock"
    PowerSource.NONE -> "Disconnected"
    PowerSource.UNKNOWN -> "Unknown"
}

private fun ChargingProfile?.profileLabel(): String = this?.label ?: "System managed"

private fun List<Double>.averageOrNull(): Double? =
    if (isEmpty()) null else average()
