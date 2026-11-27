package app.switchboard

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.switchboard.core.MediaLoader
import app.switchboard.core.Prefs
import app.switchboard.core.Sb
import app.switchboard.push.Notifier
import coil3.SingletonImageLoader
import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.initPlatform

class SbApp : Application(), SingletonImageLoader.Factory by MediaLoader {
    override fun onCreate() {
        super.onCreate()
        Sb.context = this
        Prefs.init(this)
        // The SDK checks TLS certificates through Android's trust store; its native
        // side has to be handed the app context first.
        runCatching {
            System.loadLibrary("matrix_sdk_ffi")
            org.matrix.rustsdk.Android.init(this)
        }.onFailure { android.util.Log.e("Switchboard", "Android platform init failed", it) }
        runCatching {
            val logs = app.switchboard.core.Diagnostics.logDir(this)
            initPlatform(
                TracingConfiguration(
                    logLevel = LogLevel.DEBUG,
                    traceLogPacks = emptyList(),
                    extraTargets = listOf("matrix_sdk::http_client", "reqwest", "hyper_util", "rustls", "rustls_platform_verifier"),
                    writeToStdoutOrSystem = true,
                    writeToFiles = org.matrix.rustcomponents.sdk.TracingFileConfiguration(
                        path = logs.absolutePath, filePrefix = "switchboard", fileSuffix = "log",
                        maxTotalSizeBytes = 20_000_000u, maxAgeSeconds = 3u * 24u * 3600u,
                    ),
                    sentryConfig = null,
                ),
                true,
            )
        }
        Notifier.createChannels(this)
        Sb.matrix.restore()
        app.switchboard.widget.ChatWidget.observe(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = Sb.matrix.onForeground()
            override fun onStop(owner: LifecycleOwner) {
                Sb.matrix.onBackground()
                app.switchboard.widget.ChatWidget.updateAll(this@SbApp)
            }
        })
    }
}
