package net.kimptoc.timerwithauto.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import net.kimptoc.timerwithauto.timer.TimerState

@Composable
fun TimerScreen(viewModel: TimerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()

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
            is TimerState.Idle -> IdleView(recents = recents, onStart = viewModel::start)
            is TimerState.Running -> RunningView(state = s, onCancel = viewModel::cancel)
            is TimerState.Ringing -> RingingView(onStop = viewModel::stopAlarm)
        }
    }
}

@Composable
private fun IdleView(recents: List<Int>, onStart: (Int) -> Unit) {
    var minutes by remember { mutableStateOf(recents.firstOrNull() ?: 10) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Timer With Auto", style = MaterialTheme.typography.headlineSmall)

        MinutesPicker(value = minutes, onValueChange = { minutes = it })

        Text("Recent:", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recents) { mins ->
                AssistChip(
                    onClick = { minutes = mins },
                    label = { Text("$mins") },
                )
            }
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
        Button(onClick = { if (value > 1) onValueChange(value - 1) }) { Text("−") }
        Text("$value min", fontSize = 32.sp)
        Button(onClick = { if (value < 180) onValueChange(value + 1) }) { Text("+") }
    }
}
