package dev.hush.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hush.client.ui.AppViewModel
import dev.hush.client.ui.HushRoot
import dev.hush.client.ui.theme.HushTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            // Wait one beat for saved settings so the first frame uses the chosen theme.
            settings?.let { s ->
                HushTheme(s) { HushRoot(vm) }
            }
        }
    }
}
