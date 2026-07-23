package com.nicky.chargemonitor.session

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
