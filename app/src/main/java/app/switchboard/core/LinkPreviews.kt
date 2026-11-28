package app.switchboard.core

import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation

/**
 * Link previews come from your homeserver's preview_url endpoint: the server visits the
 * page, never the phone. The picture is only ever loaded as a server-made thumbnail
 * (see [Mxc.thumbnailOnly]), so the phone never decodes the site's original file.
 */
object LinkPreviews {
    data class Preview(
        val url: String,
        val host: String,
        val title: String?,
        val description: String?,
        val image: String?,
        val width: Int,
        val height: Int,
    ) {
        val isEmpty get() = title == null && description == null && image == null
    }

    private class Entry(val preview: Preview?)

    private val cache = LruCache<String, Entry>(300)
    private val none = Preview("", "", null, null, null, 0, 0)

    /** The first web link in a message, skipping Matrix links such as mentions. */
    fun firstUrl(text: AnnotatedString): String? =
        text.getLinkAnnotations(0, text.length).asSequence()
            .mapNotNull {
                when (val item = it.item) {
                    is LinkAnnotation.Url -> item.url
                    is LinkAnnotation.Clickable -> item.tag
                    else -> null
                }
            }
            .firstOrNull { isPreviewable(it) }

    private fun isPreviewable(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase() ?: return false
        return (scheme == "https" || scheme == "http") && host != "matrix.to" && uri.userInfo.isNullOrEmpty()
    }

    /** A preview already fetched this session, if any (an empty one means "nothing to show"). */
    fun cached(url: String): Preview? = cache.get(url)?.let { it.preview ?: none }

    suspend fun get(url: String): Preview {
        cache.get(url)?.let { return it.preview ?: none }
        val p = runCatching { fetch(url) }.getOrNull()
        cache.put(url, Entry(p))
        return p ?: none
    }

    private suspend fun fetch(url: String): Preview? {
        val q = "?url=" + Api.enc(url)
        val j = try {
            Api.get("/_matrix/client/v1/media/preview_url$q")
        } catch (e: ApiException) {
            // Servers without authenticated media only have the older endpoint.
            if (e.status == 404 || e.status == 400) Api.get("/_matrix/media/v3/preview_url$q") else throw e
        }
        val host = Uri.parse(url).host?.removePrefix("www.") ?: return null
        val image = j.optString("og:image").takeIf { it.startsWith("mxc://") }
        return Preview(
            url = url,
            host = host,
            title = clean(j.optString("og:title"), 200),
            description = clean(j.optString("og:description"), 300),
            image = image,
            width = j.optInt("og:image:width"),
            height = j.optInt("og:image:height"),
        )
    }

    // Control and text-direction override characters can disguise text, so they're removed.
    private val hidden = Regex("[\\p{Cc}\\p{Cf}&&[^\\n]]|[\\u202A-\\u202E\\u2066-\\u2069]")

    private fun clean(s: String?, max: Int): String? =
        s?.replace(hidden, "")?.replace(Regex("\\s+"), " ")?.trim()?.take(max)?.takeIf { it.isNotEmpty() }
}
