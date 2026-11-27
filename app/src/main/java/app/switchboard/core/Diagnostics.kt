package app.switchboard.core

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Helps find out why the app can't reach a homeserver. */
object Diagnostics {
    fun logDir(context: Context) = File(context.filesDir, "logs").apply { mkdirs() }

    /** Tries the server with Android's own networking, to tell network trouble from SDK trouble. */
    suspend fun probe(server: String): String = withContext(Dispatchers.IO) {
        val host = server.trim().removePrefix("https://").removePrefix("http://").substringBefore('/').ifEmpty { return@withContext "" }
        val url = "https://$host/_matrix/client/versions"
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 8000
            val code = conn.responseCode
            conn.disconnect()
            "Android itself reaches $host fine (HTTP $code), so the problem is inside Switchboard's network code. Tap Share logs and send them."
        } catch (e: Exception) {
            "Android can't reach $host either (${e.javaClass.simpleName}: ${e.message}). This is the phone's network or VPN, not your password."
        }
    }

    /** Zips the SDK logs and opens the share sheet. */
    fun shareLogs(context: Context) {
        val dir = logDir(context)
        val out = File(File(context.cacheDir, "shared").apply { mkdirs() }, "switchboard-logs.zip")
        ZipOutputStream(out.outputStream()).use { zip ->
            dir.listFiles()?.sortedBy { it.lastModified() }?.takeLast(6)?.forEach { f ->
                zip.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", out)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Share logs",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
