package lt.bettertamo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.graphics.drawable.toDrawable
import lt.bettertamo.data.AppUpdater
import lt.bettertamo.data.PlannerViewModel
import lt.bettertamo.data.TamoPush
import lt.bettertamo.ui.PlannerApp

class MainActivity : ComponentActivity() {
    private val vm: PlannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        when (vm.initialTheme) {
            "dark" -> window.setBackgroundDrawable(0xFF141B17.toInt().toDrawable())
            "light" -> window.setBackgroundDrawable(0xFFF6F8F4.toInt().toDrawable())
        }
        if (savedInstanceState == null) handle(intent)
        setContent { PlannerApp(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(OPEN_FEED, false) == true) {
            vm.openTab.value = 2
            intent.removeExtra(OPEN_FEED)
        }
        if (intent?.getBooleanExtra(OPEN_MESSAGES, false) == true) {
            vm.openTab.value = 3
            intent.removeExtra(OPEN_MESSAGES)
        }
        if (BuildConfig.DEBUG && intent?.getBooleanExtra(DEBUG_UPDATE_PROMPT, false) == true) {
            AppUpdater.check(this, manual = true, force = true)
            intent.removeExtra(DEBUG_UPDATE_PROMPT)
        }
        TamoPush.destination(intent?.extras)?.let {
            vm.openTab.value = it
            intent?.removeExtra(TamoPush.EXTRA)
            intent?.removeExtra("google.message_id")
        }
    }

    companion object { const val OPEN_FEED = "open_feed"; const val OPEN_MESSAGES = "open_messages"; const val DEBUG_UPDATE_PROMPT = "debug_update_prompt" }
}
