package com.nicky.chargemonitor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import com.nicky.chargemonitor.DashboardState
import com.nicky.chargemonitor.R
import com.nicky.chargemonitor.session.GuidedTestPhase
import com.nicky.chargemonitor.session.SessionExportFormat
import com.nicky.chargemonitor.ui.components.AppIcon
import com.nicky.chargemonitor.ui.live.LiveScreen
import com.nicky.chargemonitor.ui.sessions.ComparisonScreen
import com.nicky.chargemonitor.ui.sessions.GuidedTestScreen
import com.nicky.chargemonitor.ui.sessions.SessionDetailScreen
import com.nicky.chargemonitor.ui.sessions.SessionsScreen

/** Everything the UI can ask the ViewModel or activity to do. */
class ChargeMonitorActions(
    val selectSession: (String) -> Unit,
    val shareSession: (String, SessionExportFormat) -> Unit,
    val nameAccessory: (String) -> Unit,
    val selectComparison: (List<String>) -> Unit,
    val beginGuidedTest: () -> Unit,
    val armGuidedTest: () -> Unit,
    val finishGuidedTestWindow: () -> Unit,
    val cancelGuidedTest: () -> Unit,
    val startExtendedRecording: () -> Unit,
    val stopExtendedRecording: () -> Unit,
)

private sealed interface Route {
    data object Live : Route
    data object Sessions : Route
    data class Detail(val sessionId: String) : Route
    /** Empty [sessionIds] means "show the guided test's results". */
    data class Compare(val sessionIds: List<String>) : Route
    data object GuidedTest : Route
}

private fun Route.encode(): String = when (this) {
    Route.Live -> "live"
    Route.Sessions -> "sessions"
    is Route.Detail -> "detail:$sessionId"
    is Route.Compare -> "compare:${sessionIds.joinToString(",")}"
    Route.GuidedTest -> "guided"
}

private fun decodeRoute(value: String): Route = when {
    value == "sessions" -> Route.Sessions
    value == "guided" -> Route.GuidedTest
    value.startsWith("detail:") -> Route.Detail(value.removePrefix("detail:"))
    value.startsWith("compare:") -> Route.Compare(
        value.removePrefix("compare:").split(',').filter { it.isNotBlank() },
    )
    else -> Route.Live
}

@Composable
fun ChargeMonitorApp(state: DashboardState, actions: ChargeMonitorActions) {
    // The back stack is a newline-joined string so it survives process death trivially.
    var encodedStack by rememberSaveable { mutableStateOf(Route.Live.encode()) }
    val stack = encodedStack.split('\n').map(::decodeRoute)
    val top = stack.last()
    val stateHolder = rememberSaveableStateHolder()
    var confirmLeaveTest by rememberSaveable { mutableStateOf(false) }

    fun setStack(routes: List<Route>) {
        encodedStack = routes.joinToString("\n") { it.encode() }
    }
    fun push(route: Route) = setStack(stack + route)
    fun pop() {
        // Session and comparison data are left in place so the outgoing screen doesn't
        // flash a loading state while it fades out; the next selection replaces them.
        if (top == Route.GuidedTest) actions.cancelGuidedTest()
        if (stack.size > 1) {
            stateHolder.removeState(top.encode())
            setStack(stack.dropLast(1))
        } else {
            setStack(listOf(Route.Live))
        }
    }
    fun requestBack() {
        if (top == Route.GuidedTest && state.guidedTest.isActive &&
            state.guidedTest.phase != GuidedTestPhase.PREPARE_A
        ) {
            confirmLeaveTest = true
        } else {
            pop()
        }
    }

    BackHandler(enabled = top != Route.Live) { requestBack() }

    // Screens reload their data from the route, which also restores them after process death.
    when (top) {
        is Route.Detail -> LaunchedEffect(top.sessionId) { actions.selectSession(top.sessionId) }
        is Route.Compare -> if (top.sessionIds.isNotEmpty()) {
            LaunchedEffect(top.sessionIds) { actions.selectComparison(top.sessionIds) }
        }
        else -> Unit
    }

    val bottomBar: @Composable () -> Unit = {
        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        )
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            NavigationBarItem(
                selected = top == Route.Live,
                onClick = { setStack(listOf(Route.Live)) },
                icon = { AppIcon(R.drawable.ic_bolt, contentDescription = null) },
                label = { Text("Live") },
                colors = itemColors,
            )
            NavigationBarItem(
                selected = top == Route.Sessions,
                onClick = { setStack(listOf(Route.Sessions)) },
                icon = { AppIcon(R.drawable.ic_history, contentDescription = null) },
                label = { Text("Sessions") },
                colors = itemColors,
            )
        }
    }

    AnimatedContent(
        targetState = top,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
        label = "screen",
    ) { route ->
        stateHolder.SaveableStateProvider(route.encode()) {
            when (route) {
                Route.Live -> LiveScreen(
                    state = state,
                    onOpenSession = { push(Route.Detail(it)) },
                    onOpenSessions = { setStack(listOf(Route.Sessions)) },
                    onNameAccessory = actions.nameAccessory,
                    onStartExtendedRecording = actions.startExtendedRecording,
                    onStopExtendedRecording = actions.stopExtendedRecording,
                    bottomBar = bottomBar,
                )

                Route.Sessions -> SessionsScreen(
                    summaries = state.sessionSummaries,
                    activeSessionId = state.activeSessionId,
                    loaded = state.sessionsLoaded,
                    onOpenSession = { push(Route.Detail(it)) },
                    onCompare = { push(Route.Compare(it)) },
                    onStartGuidedTest = {
                        actions.beginGuidedTest()
                        push(Route.GuidedTest)
                    },
                    bottomBar = bottomBar,
                )

                is Route.Detail -> SessionDetailScreen(
                    session = state.selectedSession?.takeIf { it.id == route.sessionId },
                    summary = state.sessionSummaries.firstOrNull { it.id == route.sessionId },
                    isRecording = state.activeSessionId == route.sessionId,
                    onBack = ::requestBack,
                    onShare = { format -> actions.shareSession(route.sessionId, format) },
                )

                is Route.Compare -> ComparisonScreen(
                    sessions = state.comparisonSessions,
                    comparison = state.comparison,
                    loading = state.comparisonLoading,
                    onBack = ::requestBack,
                )

                Route.GuidedTest -> GuidedTestScreen(
                    test = state.guidedTest,
                    reading = state.reading,
                    accessoryName = state.currentAccessory?.name,
                    comparisonReady = state.comparison != null,
                    onBack = ::requestBack,
                    onArm = actions.armGuidedTest,
                    onFinishWindow = actions.finishGuidedTestWindow,
                    onViewResults = { push(Route.Compare(emptyList())) },
                    onRestart = actions.beginGuidedTest,
                )
            }
        }
    }

    if (confirmLeaveTest) {
        AlertDialog(
            onDismissRequest = { confirmLeaveTest = false },
            title = { Text("Stop the A/B test?") },
            text = {
                Text(
                    "The windows captured so far will be discarded. The charging sessions " +
                        "themselves stay in your history.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeaveTest = false
                    pop()
                }) { Text("Stop test") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeaveTest = false }) { Text("Keep going") }
            },
        )
    }
}
