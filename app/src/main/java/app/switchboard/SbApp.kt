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
        runCatching {
            initPlatform(TracingConfiguration(LogLevel.WARN, emptyList(), emptyList(), true, null, null), true)
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
