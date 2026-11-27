package app.switchboard.ui.chat

import android.content.Context
import android.media.MediaPlayer
import app.switchboard.core.MediaFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.MediaSource

/** Plays one voice message or audio file at a time. */
object AudioPlayer {
    data class State(val key: String? = null, val playing: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0, val loading: Boolean = false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    fun toggle(context: Context, key: String, source: MediaSource, mime: String?, name: String) {
        val s = _state.value
        val p = player
        if (s.key == key && p != null) {
            if (p.isPlaying) { p.pause(); _state.value = s.copy(playing = false) } else { p.start(); _state.value = s.copy(playing = true); tick() }
            return
        }
        stop()
        _state.value = State(key = key, loading = true)
        scope.launch {
            try {
                val file = MediaFiles.file(source, name, mime ?: "audio/ogg", context)
                val mp = MediaPlayer()
                mp.setDataSource(file.absolutePath)
                mp.prepare()
                mp.setOnCompletionListener { _state.value = _state.value.copy(playing = false, positionMs = 0); ticker?.cancel() }
                mp.start()
                player = mp
                _state.value = State(key = key, playing = true, positionMs = 0, durationMs = mp.duration.toLong())
                tick()
            } catch (e: Exception) {
                _state.value = State()
            }
        }
    }

    fun seek(fraction: Float) {
        val p = player ?: return
        p.seekTo((p.duration * fraction).toInt())
        _state.value = _state.value.copy(positionMs = p.currentPosition.toLong())
    }

    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val p = player ?: break
                runCatching { _state.value = _state.value.copy(positionMs = p.currentPosition.toLong()) }
                if (!p.isPlaying) break
                delay(200)
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        runCatching { player?.stop(); player?.release() }
        player = null
        _state.value = State()
    }
}
