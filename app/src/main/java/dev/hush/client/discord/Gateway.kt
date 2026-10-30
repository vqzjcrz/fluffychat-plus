package dev.hush.client.discord

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import kotlin.random.Random

/**
 * Discord gateway connection: zlib-stream compression, heartbeats,
 * resume on drop, exponential backoff on failure.
 */
class Gateway(
    private val http: OkHttpClient,
    private val json: Json,
    private val scope: CoroutineScope,
    private val listener: Listener,
) {
    interface Listener {
        suspend fun onDispatch(type: String, data: JsonObject)
        fun onState(state: ConnectionState)
    }

    private class Frame(val gen: Int, val text: String)

    private val lock = Any()
    private val incoming = Channel<Frame>(Channel.UNLIMITED)

    @Volatile private var generation = 0
    @Volatile private var socket: WebSocket? = null
    @Volatile private var token: String? = null
    @Volatile private var sessionId: String? = null
    @Volatile private var resumeUrl: String? = null
    @Volatile private var seq: Long? = null
    @Volatile private var acked = true
    @Volatile private var heartbeatJob: Job? = null
    @Volatile private var reconnectJob: Job? = null
    @Volatile private var attempts = 0

    @Volatile
    var running = false
        private set

    init {
        scope.launch {
            for (frame in incoming) {
                if (frame.gen != generation) continue
                try {
                    handle(frame.gen, frame.text)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to handle frame", e)
                }
            }
        }
    }

    fun start(token: String) {
        if (running && this.token == token) return
        this.token = token
        running = true
        attempts = 0
        open()
    }

    /** Disconnect. With [keepSession] the next [start] tries to resume instead of re-identifying. */
    fun stop(keepSession: Boolean) {
        running = false
        reconnectJob?.cancel()
        synchronized(lock) {
            generation++
            heartbeatJob?.cancel()
            socket?.close(if (keepSession) 4000 else 1000, null)
            socket = null
        }
        if (!keepSession) {
            sessionId = null
            seq = null
            resumeUrl = null
            token = null
        }
        listener.onState(ConnectionState.DISCONNECTED)
    }

    private fun open() {
        synchronized(lock) {
            val gen = ++generation
            heartbeatJob?.cancel()
            socket?.cancel()
            listener.onState(ConnectionState.CONNECTING)

            val resume = resumeUrl
            val base = if (sessionId != null && resume != null) resume else DEFAULT_URL
            val url = "${base.trimEnd('/')}/?v=10&encoding=json&compress=zlib-stream"
            val inflater = Inflater()
            val buffer = ByteArrayOutputStream()
            val request = Request.Builder().url(url).header("User-Agent", DiscordHeaders.USER_AGENT).build()

            socket = http.newWebSocket(request, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    if (gen != generation) return
                    buffer.write(bytes.toByteArray())
                    val n = bytes.size
                    val complete = n >= 4 &&
                        bytes[n - 4] == 0.toByte() && bytes[n - 3] == 0.toByte() &&
                        bytes[n - 2] == 0xFF.toByte() && bytes[n - 1] == 0xFF.toByte()
                    if (!complete) return
                    val text = try {
                        inflate(inflater, buffer.toByteArray())
                    } catch (e: Exception) {
                        Log.w(TAG, "Inflate failed", e)
                        null
                    } finally {
                        buffer.reset()
                    }
                    if (text != null) incoming.trySend(Frame(gen, text))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (gen == generation) incoming.trySend(Frame(gen, text))
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    runCatching { webSocket.close(1000, null) }
                    onDisconnected(gen, code)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    onDisconnected(gen, code)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "Gateway failure", t)
                    onDisconnected(gen, -1)
                }
            })
        }
    }

    private fun inflate(inflater: Inflater, data: ByteArray): String {
        inflater.setInput(data)
        val out = ByteArrayOutputStream(data.size * 4)
        val chunk = ByteArray(32 * 1024)
        while (true) {
            val n = inflater.inflate(chunk)
            if (n <= 0) break
            out.write(chunk, 0, n)
        }
        return out.toString("UTF-8")
    }

    private fun onDisconnected(gen: Int, code: Int) {
        synchronized(lock) {
            if (gen != generation) return
            generation++ // ignore anything else this socket says
            heartbeatJob?.cancel()
            socket = null
        }
        listener.onState(ConnectionState.DISCONNECTED)
        when (code) {
            4004 -> {
                running = false
                sessionId = null
                seq = null
                listener.onState(ConnectionState.AUTH_FAILED)
                return
            }
            4007, 4009 -> {
                sessionId = null
                seq = null
            }
            4010, 4011, 4012, 4013, 4014 -> {
                running = false
                return
            }
        }
        if (running) scheduleReconnect()
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        val n = attempts++
        val wait = minOf(30_000L, 1_000L shl minOf(n, 5)) + Random.nextLong(0, 1_000)
        reconnectJob = scope.launch {
            delay(wait)
            if (running) open()
        }
    }

    private fun reconnect(resume: Boolean) {
        if (!resume) {
            sessionId = null
            seq = null
        }
        open()
    }

    private suspend fun handle(gen: Int, text: String) {
        val root = json.parseToJsonElement(text) as? JsonObject ?: return
        val op = root.int("op") ?: return
        root.long("s")?.let { seq = it }
        when (op) {
            0 -> {
                val type = root.str("t") ?: return
                val data = root["d"] as? JsonObject ?: JsonObject(emptyMap())
                if (type == "READY") {
                    sessionId = data.str("session_id")
                    resumeUrl = data.str("resume_gateway_url")
                    attempts = 0
                    listener.onState(ConnectionState.CONNECTED)
                } else if (type == "RESUMED") {
                    attempts = 0
                    listener.onState(ConnectionState.CONNECTED)
                }
                listener.onDispatch(type, data)
            }
            1 -> sendHeartbeat()
            7 -> reconnect(resume = true)
            9 -> {
                val resumable = root.bool("d") == true
                if (!resumable) {
                    sessionId = null
                    seq = null
                }
                delay(1_000L + Random.nextLong(0, 4_000))
                if (gen == generation) {
                    if (resumable) sendResume() else sendIdentify()
                }
            }
            10 -> {
                val interval = (root["d"] as? JsonObject)?.long("heartbeat_interval") ?: 41_250L
                startHeartbeat(gen, interval)
                if (sessionId != null && seq != null) sendResume() else sendIdentify()
            }
            11 -> acked = true
        }
    }

    private fun startHeartbeat(gen: Int, interval: Long) {
        heartbeatJob?.cancel()
        acked = true
        heartbeatJob = scope.launch {
            delay((interval * Random.nextDouble()).toLong())
            while (isActive && gen == generation) {
                if (!acked) {
                    Log.i(TAG, "Missed heartbeat ack, reconnecting")
                    reconnect(resume = true)
                    return@launch
                }
                acked = false
                sendHeartbeat()
                delay(interval)
            }
        }
    }

    private fun send(payload: JsonObject) {
        socket?.send(payload.toString())
    }

    private fun sendHeartbeat() {
        val s = seq
        send(buildJsonObject {
            put("op", 1)
            put("d", if (s != null) JsonPrimitive(s) else JsonNull)
        })
    }

    private fun sendIdentify() {
        val t = token ?: return
        send(buildJsonObject {
            put("op", 2)
            putJsonObject("d") {
                put("token", t)
                put("capabilities", 0)
                putJsonObject("properties") {
                    put("os", "Android")
                    put("browser", "Chrome")
                    put("device", "")
                    put("system_locale", "en-US")
                    put("browser_user_agent", DiscordHeaders.USER_AGENT)
                }
                putJsonObject("presence") {
                    put("status", "unknown")
                    put("since", 0)
                    putJsonArray("activities") {}
                    put("afk", false)
                }
                put("compress", false)
                putJsonObject("client_state") {
                    putJsonObject("guild_versions") {}
                }
            }
        })
    }

    private fun sendResume() {
        val t = token ?: return
        val sid = sessionId ?: return sendIdentify()
        send(buildJsonObject {
            put("op", 6)
            putJsonObject("d") {
                put("token", t)
                put("session_id", sid)
                put("seq", seq)
            }
        })
    }

    companion object {
        private const val TAG = "HushGateway"
        private const val DEFAULT_URL = "wss://gateway.discord.gg"
    }
}
