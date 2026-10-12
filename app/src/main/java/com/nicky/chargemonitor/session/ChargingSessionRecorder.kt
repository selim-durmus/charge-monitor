package com.nicky.chargemonitor.session

import android.content.Context
import com.nicky.chargemonitor.accessory.AccessoryBaseline
import com.nicky.chargemonitor.accessory.AccessoryProfile
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
    private val summaryCache = HashMap<String, Pair<ChargingSession, ChargingSessionSummary>>()
    private var baselineCache: Pair<List<ChargingSession>, Map<String, AccessoryBaseline>>? = null

    @Synchronized
    fun record(
        reading: BatteryReading,
        timestampMillis: Long,
        accessoryProfile: AccessoryProfile? = null,
    ): Boolean {
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
                accessoryProfile = accessoryProfile,
            )
        }
        if (accessoryProfile != null &&
            activeSession?.accessoryId != accessoryProfile.id
        ) {
            assignAccessoryToActiveSession(accessoryProfile)
            changed = true
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

    /**
     * Called about once per second while recording. Finished sessions never change, so their
     * summaries are cached by instance identity and only the active session is re-analyzed.
     */
    @Synchronized
    fun summaries(): List<ChargingSessionSummary> {
        val ordered = sessions.sortedByDescending { it.startedAtMillis }
        summaryCache.keys.retainAll(ordered.mapTo(HashSet()) { it.id })
        return ordered.map { session ->
            val cached = summaryCache[session.id]
            if (cached != null && cached.first === session) {
                cached.second
            } else {
                SessionAnalyzer.summarize(session).also {
                    summaryCache[session.id] = session to it
                }
            }
        }
    }

    @Synchronized
    fun session(sessionId: String): ChargingSession? =
        sessions.firstOrNull { it.id == sessionId } ?: store.loadSession(sessionId)

    @Synchronized
    fun activeSessionId(): String? = activeSession?.id

    @Synchronized
    fun renameAccessory(profile: AccessoryProfile) {
        sessions = sessions.map { session ->
            if (session.accessoryId == profile.id) {
                session.copy(accessoryName = profile.name).also(store::updateSessionMetadata)
            } else {
                session
            }
        }
        activeSession = activeSession?.let { active ->
            sessions.firstOrNull { it.id == active.id }
        }
    }

    /**
     * Baselines come from finished sessions only, so the live session is compared against
     * history rather than against itself. The result is cached until a session finishes or
     * is renamed.
     */
    @Synchronized
    fun accessoryBaselines(): Map<String, AccessoryBaseline> {
        val finished = sessions.filterNot { it.isActive }
        baselineCache?.let { (key, value) ->
            if (key.size == finished.size && key.indices.all { key[it] === finished[it] }) {
                return value
            }
        }
        return accessoryBaselinesFor(finished).also { baselineCache = finished to it }
    }

    private fun startOrResumeSession(
        reading: BatteryReading,
        timestampMillis: Long,
        joinedInProgress: Boolean,
        accessoryProfile: AccessoryProfile?,
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
            accessoryId = accessoryProfile?.id,
            accessoryName = accessoryProfile?.name,
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

    private fun assignAccessoryToActiveSession(profile: AccessoryProfile) {
        val session = activeSession ?: return
        val updated = session.copy(
            accessoryId = profile.id,
            accessoryName = profile.name,
        )
        activeSession = updated
        replaceCached(updated)
        store.updateSessionMetadata(updated)
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
            thermalHeadroomForecast30Seconds = readings
                .mapNotNull { it.thermalHeadroomForecast30Seconds }
                .maxOrNull(),
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

internal fun accessoryBaselinesFor(
    finished: List<ChargingSession>,
): Map<String, AccessoryBaseline> =
    finished
        .filter { it.accessoryId != null }
        .groupBy { requireNotNull(it.accessoryId) }
        .mapNotNull { (accessoryId, matchingSessions) ->
            val comparableBySession = matchingSessions.map { session ->
                session.samples.filter(DiagnosticSample::isBaselineComparable)
            }
            val powers = comparableBySession
                .flatten()
                .mapNotNull { it.powerAverageWatts }
                .sorted()
            if (powers.size < MINIMUM_BASELINE_BUCKETS) {
                null
            } else {
                AccessoryBaseline(
                    accessoryId = accessoryId,
                    sessionCount = comparableBySession.count { it.isNotEmpty() },
                    comparableBucketCount = powers.size,
                    averagePowerWatts = powers.average(),
                    medianPowerWatts = powers.percentile(0.50),
                    tenthPercentilePowerWatts = powers.percentile(0.10),
                    peakPowerWatts = matchingSessions
                        .flatMap { it.samples }
                        .mapNotNull { it.powerMaximumWatts }
                        .maxOrNull()
                        ?: powers.last(),
                )
            }
        }
        .associateBy { it.accessoryId }

/** Cool, lower-charge, unthrottled samples where the charger rather than taper sets the pace. */
internal fun DiagnosticSample.isBaselineComparable(): Boolean =
    elapsedMillis >= BASELINE_WARMUP_MILLIS &&
        (levelPercent ?: 100) < BASELINE_MAX_BATTERY_PERCENT &&
        chargingProfile != ChargingProfile.LONG_LIFE &&
        thermalStatus.severity < ThermalStatus.MODERATE.severity &&
        (powerAverageWatts ?: 0.0) > 0.0

private fun List<Double>.averageOrNull(): Double? =
    if (isEmpty()) null else average()

private fun List<Double>.percentile(fraction: Double): Double {
    val index = ((size - 1) * fraction).toInt().coerceIn(indices)
    return this[index]
}

private const val BASELINE_WARMUP_MILLIS = 30_000L
private const val BASELINE_MAX_BATTERY_PERCENT = 78
private const val MINIMUM_BASELINE_BUCKETS = 30
