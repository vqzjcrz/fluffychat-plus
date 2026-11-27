package app.switchboard.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.ErrorKind
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Global access to the app's single Matrix manager. */
object Sb {
    lateinit var context: Context
    val matrix: MatrixManager by lazy { MatrixManager(context) }
}

class ApiException(val status: Int, val body: String) : Exception("HTTP $status: $body") {
    val errcode: String? get() = runCatching { JSONObject(body).optString("errcode").ifEmpty { null } }.getOrNull()
    val retryAfterMs: Long? get() = runCatching { JSONObject(body).optLong("retry_after_ms").takeIf { it > 0 } }.getOrNull()
}

/** Plain client-server API calls for the few things the SDK doesn't expose. */
object Api {
    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val s = runCatching { Sb.matrix.client?.session() }.getOrNull() ?: Sb.matrix.stored?.session
            ?: throw IllegalStateException("Not logged in")
        val conn = URL(s.homeserverUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("Authorization", "Bearer ${s.accessToken}")
        conn.setRequestProperty("Accept", "application/json")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) throw ApiException(code, text)
        when {
            text.isBlank() -> JSONObject()
            text.trimStart().startsWith("[") -> JSONObject().put("__array", org.json.JSONArray(text))
            else -> JSONObject(text)
        }
    }

    suspend fun get(path: String) = request("GET", path)
    suspend fun post(path: String, body: JSONObject = JSONObject()) = request("POST", path, body)
    suspend fun put(path: String, body: JSONObject = JSONObject()) = request("PUT", path, body)
    suspend fun delete(path: String, body: JSONObject? = null) = request("DELETE", path, body)

    /** Unstable features the homeserver reports in /versions. */
    suspend fun unstableFeatures(): JSONObject = runCatching {
        get("/_matrix/client/versions").optJSONObject("unstable_features") ?: JSONObject()
    }.getOrDefault(JSONObject())
}

/**
 * Runs [block], waiting and trying again when the server answers "Too many
 * requests" instead of failing.
 */
suspend fun <T> retryOnRateLimit(maxAttempts: Int = 20, block: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: Exception) {
            attempt++
            val wait = rateLimitWait(e) ?: throw e
            if (attempt >= maxAttempts) throw e
            delay(wait)
        }
    }
}

private fun rateLimitWait(e: Exception): Long? {
    if (e is ClientException.MatrixApi) {
        val k = e.kind
        if (k is ErrorKind.LimitExceeded) return (k.retryAfterMs?.toLong() ?: 2000L).coerceIn(500, 60_000) + 250
    }
    if (e is ApiException && (e.status == 429 || e.errcode == "M_LIMIT_EXCEEDED")) {
        return (e.retryAfterMs ?: 2000L).coerceIn(500, 60_000) + 250
    }
    val msg = e.message ?: return null
    if (msg.contains("M_LIMIT_EXCEEDED") || msg.contains("Too Many Requests", ignoreCase = true) || msg.contains("429")) {
        val ms = Regex("retry_after_ms\\D*(\\d+)").find(msg)?.groupValues?.get(1)?.toLongOrNull()
        return (ms ?: 3000L).coerceIn(500, 60_000) + 250
    }
    return null
}
