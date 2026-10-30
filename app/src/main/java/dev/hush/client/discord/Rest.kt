package dev.hush.client.discord

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.net.URLEncoder

class ApiException(val code: Int, message: String) : Exception(message)

/** Adds browser-like headers to requests that go to Discord itself (not the CDN). */
object DiscordHeaders : Interceptor {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/130.0.0.0 Mobile Safari/537.36"

    private val superProperties: String by lazy {
        val props = buildJsonObject {
            put("os", "Android")
            put("browser", "Chrome")
            put("device", "")
            put("system_locale", "en-US")
            put("browser_user_agent", USER_AGENT)
            put("browser_version", "130.0.0.0")
            put("os_version", "14")
            put("referrer", "")
            put("referring_domain", "")
            put("release_channel", "stable")
        }
        Base64.encodeToString(props.toString().toByteArray(), Base64.NO_WRAP)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        if (!host.endsWith("discord.com") && !host.endsWith("discord.gg")) return chain.proceed(request)
        val builder = request.newBuilder().header("User-Agent", USER_AGENT)
        if (host == "discord.com") {
            builder.header("X-Super-Properties", superProperties)
                .header("X-Discord-Locale", "en-US")
                .header("Accept-Language", "en-US,en;q=0.9")
        }
        return chain.proceed(builder.build())
    }
}

class DiscordRest(
    private val http: OkHttpClient,
    private val json: Json,
    private val tokenProvider: () -> String?,
) {
    private val base = "https://discord.com/api/v10"
    private val jsonType = "application/json".toMediaType()

    private suspend fun call(builder: Request.Builder, tokenOverride: String? = null): String {
        val token = tokenOverride ?: tokenProvider() ?: throw ApiException(401, "Not signed in")
        val request = builder.header("Authorization", token).build()
        var attempt = 0
        while (true) {
            val (code, body) = withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { it.code to (it.body?.string().orEmpty()) }
            }
            if (code == 429 && attempt < 3) {
                val retry = runCatching {
                    (json.parseToJsonElement(body) as JsonObject).str("retry_after")?.toDouble()
                }.getOrNull() ?: 1.0
                delay((retry * 1000).toLong().coerceIn(250L, 15_000L))
                attempt++
                continue
            }
            if (code !in 200..299) {
                val msg = runCatching {
                    (json.parseToJsonElement(body) as JsonObject).str("message")
                }.getOrNull() ?: "HTTP $code"
                throw ApiException(code, msg)
            }
            return body
        }
    }

    private fun jsonBody(obj: JsonObject): RequestBody = obj.toString().toRequestBody(jsonType)

    suspend fun me(tokenOverride: String? = null): User =
        json.decodeFromString(User.serializer(), call(Request.Builder().url("$base/users/@me").get(), tokenOverride))

    suspend fun messages(channelId: String, before: String? = null, limit: Int = 50): List<Message> {
        val url = buildString {
            append("$base/channels/$channelId/messages?limit=$limit")
            if (before != null) append("&before=$before")
        }
        return json.decodeFromString(ListSerializer(Message.serializer()), call(Request.Builder().url(url).get()))
    }

    suspend fun send(
        channelId: String,
        content: String,
        replyTo: Message?,
        mentionReply: Boolean,
        files: List<UploadFile>,
    ): Message {
        val payload = buildJsonObject {
            put("content", content)
            put("nonce", newNonce())
            if (replyTo != null) {
                putJsonObject("message_reference") {
                    put("message_id", replyTo.id)
                    put("channel_id", replyTo.channelId)
                    put("fail_if_not_exists", false)
                }
                if (!mentionReply) {
                    putJsonObject("allowed_mentions") {
                        putJsonArray("parse") {
                            add("users")
                            add("roles")
                            add("everyone")
                        }
                        put("replied_user", false)
                    }
                }
            }
            if (files.isNotEmpty()) {
                putJsonArray("attachments") {
                    files.forEachIndexed { i, f ->
                        addJsonObject {
                            put("id", i)
                            put("filename", f.name)
                        }
                    }
                }
            }
        }
        val body: RequestBody = if (files.isEmpty()) {
            jsonBody(payload)
        } else {
            MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                addFormDataPart("payload_json", payload.toString())
                files.forEachIndexed { i, f ->
                    addFormDataPart("files[$i]", f.name, f.bytes.toRequestBody(f.mime.toMediaTypeOrNull()))
                }
            }.build()
        }
        val response = call(Request.Builder().url("$base/channels/$channelId/messages").post(body))
        return json.decodeFromString(Message.serializer(), response)
    }

    suspend fun edit(channelId: String, messageId: String, content: String): Message {
        val body = jsonBody(buildJsonObject { put("content", content) })
        val response = call(Request.Builder().url("$base/channels/$channelId/messages/$messageId").patch(body))
        return json.decodeFromString(Message.serializer(), response)
    }

    suspend fun delete(channelId: String, messageId: String) {
        call(Request.Builder().url("$base/channels/$channelId/messages/$messageId").delete())
    }

    suspend fun typing(channelId: String) {
        call(Request.Builder().url("$base/channels/$channelId/typing").post(ByteArray(0).toRequestBody()))
    }

    suspend fun ack(channelId: String, messageId: String) {
        val body = jsonBody(buildJsonObject { put("token", JsonNull) })
        call(Request.Builder().url("$base/channels/$channelId/messages/$messageId/ack").post(body))
    }

    suspend fun react(channelId: String, messageId: String, emoji: ReactionEmoji, add: Boolean) {
        val code = if (emoji.id != null) "${emoji.name ?: "_"}:${emoji.id}" else emoji.name ?: return
        val encoded = URLEncoder.encode(code, "UTF-8")
        val url = "$base/channels/$channelId/messages/$messageId/reactions/$encoded/@me"
        val builder = Request.Builder().url(url)
        if (add) builder.put(ByteArray(0).toRequestBody()) else builder.delete()
        call(builder)
    }
}
