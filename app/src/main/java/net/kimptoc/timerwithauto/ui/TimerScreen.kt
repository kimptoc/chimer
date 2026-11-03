package net.kimptoc.timerwithauto.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import net.kimptoc.timerwithauto.timer.PinnedDurations
import net.kimptoc.timerwithauto.timer.TimerState

@Composable
fun TimerScreen(viewModel: TimerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()

    // POST_NOTIFICATIONS runtime permission on Android 13+
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored; alarm still plays even if denied */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            is TimerState.Idle -> IdleView(
                lastUsedMinutes = viewModel.lastUsedMinutes,
                recents = recents,
                pinned = pinned,
                onStart = viewModel::start,
                onPin = viewModel::pin,
                onUnpin = viewModel::unpin,
            )
            is TimerState.Running -> RunningView(state = s, onCancel = viewModel::cancel)
            is TimerState.Ringing -> RingingView(onStop = viewModel::stopAlarm)
        }
    }
}

@Composable
private fun IdleView(
    lastUsedMinutes: Int?,
    recents: List<Int>,
    pinned: List<Int>,
    onStart: (Int) -> Unit,
    onPin: (Int) -> Unit,
    onUnpin: (Int) -> Unit,
) {
    var minutes by remember { mutableStateOf(lastUsedMinutes ?: pinned.firstOrNull() ?: 10) }
    val isPinned = minutes in pinned

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Chimer", style = MaterialTheme.typography.headlineSmall)

        MinutesPicker(value = minutes, onValueChange = { minutes = it })

        TextButton(
            onClick = { if (isPinned) onUnpin(minutes) else onPin(minutes) },
            enabled = isPinned || pinned.size < PinnedDurations.MAX_SIZE,
        ) {
            Text(if (isPinned) "Unpin $minutes min" else "Pin $minutes min")
        }

        if (pinned.isNotEmpty()) {
            DurationChips(title = "Pinned:", durations = pinned, onSelect = { minutes = it })
        }
        if (recents.isNotEmpty()) {
            DurationChips(title = "Recent:", durations = recents, onSelect = { minutes = it })
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { onStart(minutes) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("START", fontSize = 18.sp)
        }
    }
}

@Composable
private fun DurationChips(title: String, durations: List<Int>, onSelect: (Int) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(durations) { mins ->
            AssistChip(
                onClick = { onSelect(mins) },
                label = { Text("$mins") },
            )
        }
    }
}

@Composable
private fun RunningView(state: TimerState.Running, onCancel: () -> Unit) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.deadlineEpochMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val remainingMs = remainingMillis(state.deadlineEpochMs, nowMs)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(formatRemaining(remainingMs), fontSize = 72.sp)
        Text("(set: ${state.durationMinutes} min)")
        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("CANCEL", fontSize = 18.sp)
        }
    }
}

@Composable
private fun RingingView(onStop: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Timer finished", fontSize = 36.sp)
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier.fillMaxWidth().height(72.dp),
        ) {
            Text("STOP", fontSize = 24.sp)
        }
    }
}

@Composable
private fun MinutesPicker(value: Int, onValueChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RepeatingStepButton(label = "−", onStep = { if (value > 1) onValueChange(value - 1) })
        Text("$value min", fontSize = 32.sp)
        RepeatingStepButton(label = "+", onStep = { if (value < 180) onValueChange(value + 1) })
    }
}

/**
 * A single tap steps once; holding repeats [onStep] with accelerating delay
 * (see [RepeatAcceleration]) so dragging a duration far from its start doesn't
 * require many individual taps.
 */
@Composable
private fun RepeatingStepButton(label: String, onStep: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentOnStep by rememberUpdatedState(onStep)
    var repeated by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (!isPressed) return@LaunchedEffect
        // Reset at the start of a new press (not the end of the previous one) so this
        // write is always causally ordered before that press's own eventual onClick,
        // regardless of how Compose schedules the release-interaction vs. onClick coroutines.
        repeated = false
        delay(RepeatAcceleration.INITIAL_DELAY_MS)
        var delayMs = RepeatAcceleration.INITIAL_DELAY_MS
        while (isPressed) {
            repeated = true
            currentOnStep()
            delayMs = RepeatAcceleration.nextDelayMs(delayMs)
            delay(delayMs)
        }
    }

    Button(
        onClick = { if (!repeated) currentOnStep() },
        interactionSource = interactionSource,
    ) { Text(label) }
}
