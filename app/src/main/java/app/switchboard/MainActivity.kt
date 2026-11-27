package app.switchboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import org.matrix.rustcomponents.sdk.ClientBuilder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val builder = ClientBuilder()
        setContent { Text("Switchboard " + builder.hashCode()) }
    }
}
