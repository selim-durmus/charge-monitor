package com.nicky.chargemonitor.session

/** Each window closes automatically at this length so A and B are equally long. */
const val GUIDED_TEST_WINDOW_MILLIS = 5 * 60_000L

enum class GuidedTestPhase {
    IDLE,
    PREPARE_A,
    WAITING_FOR_A,
    RUNNING_A,
    PREPARE_B,
    WAITING_FOR_B,
    RUNNING_B,
    COMPLETE,
}

data class GuidedTestRun(
    val sessionId: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
)

data class GuidedChargingTestState(
    val phase: GuidedTestPhase = GuidedTestPhase.IDLE,
    val firstRun: GuidedTestRun? = null,
    val secondRun: GuidedTestRun? = null,
) {
    val isActive: Boolean
        get() = phase != GuidedTestPhase.IDLE && phase != GuidedTestPhase.COMPLETE

    val isRunning: Boolean
        get() = phase == GuidedTestPhase.RUNNING_A || phase == GuidedTestPhase.RUNNING_B

    val currentRun: GuidedTestRun?
        get() = when (phase) {
            GuidedTestPhase.RUNNING_A -> firstRun
            GuidedTestPhase.RUNNING_B -> secondRun
            else -> null
        }
}

fun ChargingSession.sliceForTest(run: GuidedTestRun): ChargingSession {
    val end = run.endedAtMillis ?: samples.lastOrNull()?.timestampMillis ?: run.startedAtMillis
    return copy(
        startedAtMillis = run.startedAtMillis,
        endedAtMillis = end,
        joinedInProgress = false,
        samples = samples.filter { it.timestampMillis in run.startedAtMillis..end },
        events = events.filter { it.timestampMillis in run.startedAtMillis..end },
    )
}

/** Moves from a PREPARE phase to waiting for power; the phone must be unplugged first. */
fun GuidedChargingTestState.arm(isPowerConnected: Boolean): GuidedChargingTestState {
    if (isPowerConnected) return this
    return when (phase) {
        GuidedTestPhase.IDLE, GuidedTestPhase.PREPARE_A ->
            copy(phase = GuidedTestPhase.WAITING_FOR_A)
        GuidedTestPhase.PREPARE_B -> copy(phase = GuidedTestPhase.WAITING_FOR_B)
        else -> this
    }
}

/**
 * Applies one live reading: starts a window when power connects, and closes it when power
 * disconnects or the window reaches [GUIDED_TEST_WINDOW_MILLIS].
 */
fun GuidedChargingTestState.advance(
    isPowerConnected: Boolean,
    activeSessionId: String?,
    timestampMillis: Long,
): GuidedChargingTestState {
    val run = currentRun
    return when {
        phase == GuidedTestPhase.WAITING_FOR_A && isPowerConnected && activeSessionId != null ->
            copy(
                phase = GuidedTestPhase.RUNNING_A,
                firstRun = GuidedTestRun(activeSessionId, timestampMillis, endedAtMillis = null),
            )

        phase == GuidedTestPhase.WAITING_FOR_B && isPowerConnected && activeSessionId != null ->
            copy(
                phase = GuidedTestPhase.RUNNING_B,
                secondRun = GuidedTestRun(activeSessionId, timestampMillis, endedAtMillis = null),
            )

        run != null && !isPowerConnected -> finishWindow(timestampMillis)

        run != null && timestampMillis - run.startedAtMillis >= GUIDED_TEST_WINDOW_MILLIS ->
            finishWindow(run.startedAtMillis + GUIDED_TEST_WINDOW_MILLIS)

        else -> this
    }
}

fun GuidedChargingTestState.finishWindow(timestampMillis: Long): GuidedChargingTestState =
    when (phase) {
        GuidedTestPhase.RUNNING_A -> copy(
            phase = GuidedTestPhase.PREPARE_B,
            firstRun = firstRun?.copy(endedAtMillis = timestampMillis),
        )
        GuidedTestPhase.RUNNING_B -> copy(
            phase = GuidedTestPhase.COMPLETE,
            secondRun = secondRun?.copy(endedAtMillis = timestampMillis),
        )
        else -> this
    }
