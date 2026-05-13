package net.kimptoc.timerwithauto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import net.kimptoc.timerwithauto.ui.TimerScreen
import net.kimptoc.timerwithauto.ui.TimerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TimerViewModel by viewModels {
        TimerViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TimerScreen(viewModel = viewModel)
        }
    }
}
