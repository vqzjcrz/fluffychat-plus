package app.switchboard.push
import android.content.Context
object Notifier { fun createChannels(c: Context) {} }
object PushSetup { suspend fun unregister(c: Context) {}; fun ensureRegistered(c: Context) {} }
