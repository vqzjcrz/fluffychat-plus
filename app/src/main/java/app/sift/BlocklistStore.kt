package app.sift

import android.content.Context
import app.sift.engine.Blocklist
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Blocklists live in app-private storage. Nothing here is ever uploaded. */
class BlocklistStore(context: Context) {
    private val dir = File(context.filesDir, "blocklists").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("sift", Context.MODE_PRIVATE)
    private val importedFile = File(dir, "imported.txt")
    private val syncedFile = File(dir, "synced.txt")

    data class Info(
        val importedCount: Int,
        val importedName: String?,
        val syncedCount: Int,
        val lastSyncMillis: Long,
        val lastSyncError: String?,
    ) {
        val total get() = importedCount + syncedCount
    }

    var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(v) = prefs.edit().putString("server_url", v.trim()).apply()

    var serverToken: String
        get() = prefs.getString("server_token", "") ?: ""
        set(v) = prefs.edit().putString("server_token", v.trim()).apply()

    var autoSync: Boolean
        get() = prefs.getBoolean("auto_sync", false)
        set(v) = prefs.edit().putBoolean("auto_sync", v).apply()

    fun info() = Info(
        importedCount = prefs.getInt("imported_count", 0),
        importedName = prefs.getString("imported_name", null),
        syncedCount = prefs.getInt("synced_count", 0),
        lastSyncMillis = prefs.getLong("last_sync", 0L),
        lastSyncError = prefs.getString("last_sync_error", null),
    )

    fun isStale(maxAgeMillis: Long = 24L * 60 * 60 * 1000) =
        System.currentTimeMillis() - prefs.getLong("last_sync", 0L) > maxAgeMillis

    /** Parses all stored lists. Call off the main thread. */
    fun load(): Blocklist {
        val imported = parse(importedFile)
        val synced = parse(syncedFile)
        prefs.edit()
            .putInt("imported_count", imported.size)
            .putInt("synced_count", synced.size)
            .apply()
        return Blocklist.merge(listOf(imported, synced))
    }

    private fun parse(f: File): Blocklist =
        if (f.exists()) f.bufferedReader().useLines { Blocklist.parse(it) } else Blocklist.EMPTY

    /** Copies [input] into storage after checking it actually contains entries. Returns entry count. */
    fun importFrom(input: InputStream, displayName: String?): Int {
        val tmp = File(dir, "imported.tmp")
        input.use { src -> tmp.outputStream().use { dst -> copyLimited(src, dst) } }
        val count = tmp.bufferedReader().useLines { Blocklist.parse(it).size }
        if (count == 0) {
            tmp.delete()
            throw IOException("No domains or URLs found in that file.")
        }
        if (!tmp.renameTo(importedFile)) {
            importedFile.delete(); tmp.renameTo(importedFile)
        }
        prefs.edit().putString("imported_name", displayName).putInt("imported_count", count).apply()
        return count
    }

    fun clearImported() {
        importedFile.delete()
        prefs.edit().remove("imported_name").putInt("imported_count", 0).apply()
    }

    fun clearSynced() {
        syncedFile.delete()
        prefs.edit().putInt("synced_count", 0).remove("last_sync_error").putLong("last_sync", 0L).apply()
    }

    /**
     * Downloads the blocklist from the user's own server. The request carries no information
     * about anything you've scanned — it's a plain GET for a text file.
     * Only callable in the selfhosted flavor (the offline flavor has no INTERNET permission).
     */
    fun syncFromServer(): Int {
        check(BuildConfig.SELF_HOSTED) { "This build has no network access." }
        val url = serverUrl
        require(url.startsWith("http://") || url.startsWith("https://")) { "Server URL must start with http:// or https://" }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 60_000
            conn.useCaches = false
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Sift-blocklist-sync")
            serverToken.takeIf { it.isNotEmpty() }?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("Server answered HTTP $code")
            val tmp = File(dir, "synced.tmp")
            conn.inputStream.use { src -> tmp.outputStream().use { dst -> copyLimited(src, dst) } }
            val count = tmp.bufferedReader().useLines { Blocklist.parse(it).size }
            if (count == 0) {
                tmp.delete()
                throw IOException("Downloaded file had no domains or URLs.")
            }
            if (!tmp.renameTo(syncedFile)) {
                syncedFile.delete(); tmp.renameTo(syncedFile)
            }
            prefs.edit()
                .putInt("synced_count", count)
                .putLong("last_sync", System.currentTimeMillis())
                .remove("last_sync_error")
                .apply()
            return count
        } catch (e: Exception) {
            prefs.edit().putString("last_sync_error", e.message ?: e.javaClass.simpleName).apply()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun copyLimited(src: InputStream, dst: java.io.OutputStream, max: Long = 128L * 1024 * 1024) {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = src.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw IOException("File is larger than 128 MB.")
            dst.write(buf, 0, n)
        }
    }
}
