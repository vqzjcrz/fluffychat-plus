package app.sift

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.sift.ui.SiftRoot
import app.sift.ui.SiftTheme

class MainActivity : ComponentActivity() {
    private val vm: SiftViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            SiftTheme {
                SiftRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        val text = when (i?.action) {
            Intent.ACTION_SEND -> i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: i.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            Intent.ACTION_PROCESS_TEXT -> i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (!text.isNullOrBlank()) vm.scan(text)
    }
}
