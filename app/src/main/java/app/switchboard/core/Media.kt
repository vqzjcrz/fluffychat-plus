package app.switchboard.core

import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.gif.AnimatedImageDecoder
import coil3.key.Keyer
import coil3.memory.MemoryCache
import coil3.request.Options
import coil3.video.VideoFrameDecoder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer
import org.matrix.rustcomponents.sdk.MediaFileHandle
import org.matrix.rustcomponents.sdk.MediaSource
import java.io.File

/**
 * A picture stored on the homeserver: an mxc:// URL (avatars) or a full
 * media source as JSON (message attachments, which may be encrypted).
 */
data class Mxc(
    val url: String? = null,
    val sourceJson: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    /** Only ever load the server-made thumbnail, never the original file. */
    val thumbnailOnly: Boolean = false,
) {
    val isThumbnail get() = width > 0 && height > 0
    val key get() = (url ?: sourceJson ?: "") + "@" + width + "x" + height + if (thumbnailOnly) "!t" else ""

    fun source(): MediaSource = if (sourceJson != null) MediaSource.fromJson(sourceJson) else MediaSource.fromUrl(url!!)

    companion object {
        fun avatar(url: String?, size: Int = 96): Mxc? = url?.takeIf { it.startsWith("mxc://") }?.let { Mxc(url = it, width = size, height = size) }
        fun of(source: MediaSource, width: Int = 0, height: Int = 0) = Mxc(sourceJson = source.toJson(), width = width, height = height)
    }
}

class MxcFetcher(private val data: Mxc, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val client = Sb.matrix.client ?: throw IllegalStateException("No client")
        val source = data.source()
        val bytes = if (data.isThumbnail) {
            try {
                client.getMediaThumbnail(source, data.width.toULong(), data.height.toULong())
            } catch (e: Exception) {
                if (data.thumbnailOnly) throw e
                client.getMediaContent(source)
            }
        } else client.getMediaContent(source)
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory : Fetcher.Factory<Mxc> {
        override fun create(data: Mxc, options: Options, imageLoader: ImageLoader): Fetcher = MxcFetcher(data, options)
    }
}

class MxcKeyer : Keyer<Mxc> {
    override fun key(data: Mxc, options: Options): String = data.key
}

object MediaLoader : SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(MxcKeyer())
                add(MxcFetcher.Factory())
                add(AnimatedImageDecoder.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
            .build()
}

/** Downloads attachments to files (for video, audio and saving). */
object MediaFiles {
    private val handles = LinkedHashMap<String, MediaFileHandle>()
    private val lock = Mutex()

    suspend fun file(source: MediaSource, filename: String?, mime: String, context: Context): File = lock.withLock {
        val key = source.toJson()
        handles[key]?.let { h -> File(h.path()).takeIf { it.exists() }?.let { return it } }
        val client = Sb.matrix.client ?: throw IllegalStateException("No client")
        val dir = File(context.cacheDir, "media").apply { mkdirs() }
        val h = client.getMediaFile(source, filename, mime, true, dir.absolutePath)
        handles[key] = h
        while (handles.size > 30) handles.remove(handles.keys.first())
        File(h.path())
    }

    suspend fun bytes(source: MediaSource): ByteArray =
        (Sb.matrix.client ?: throw IllegalStateException("No client")).getMediaContent(source)
}
