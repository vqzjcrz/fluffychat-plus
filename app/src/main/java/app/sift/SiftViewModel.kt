package app.sift

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sift.engine.PublicSuffixList
import app.sift.engine.Report
import app.sift.engine.UrlAnalyzer
import app.sift.engine.UrlExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SiftViewModel(app: Application) : AndroidViewModel(app) {
    val store = BlocklistStore(app)

    private var psl: PublicSuffixList? = null
    private var analyzer: UrlAnalyzer? = null
    private var extractor: UrlExtractor? = null
    private var pending: String? = null

    var ready by mutableStateOf(false); private set
    var input by mutableStateOf("")
    var reports by mutableStateOf<List<Report>>(emptyList()); private set
    var selected by mutableIntStateOf(0)
    var message by mutableStateOf<String?>(null)
    var blocklist by mutableStateOf(store.info()); private set
    var busy by mutableStateOf(false); private set

    init {
        viewModelScope.launch {
            reload()
            if (BuildConfig.SELF_HOSTED && store.autoSync && store.serverUrl.isNotEmpty() && store.isStale()) {
                sync(quiet = true)
            }
        }
    }

    private suspend fun reload() {
        withContext(Dispatchers.Default) {
            val p = psl ?: getApplication<Application>().assets.open("public_suffix_list.dat")
                .bufferedReader().useLines { PublicSuffixList(it) }
            psl = p
            val bl = store.load()
            analyzer = UrlAnalyzer(p, bl)
            extractor = UrlExtractor(p)
        }
        blocklist = store.info()
        ready = true
        pending?.let { pending = null; scan(it) }
    }

    fun scan(text: String, keepSelection: Boolean = false) {
        input = text
        val a = analyzer
        val e = extractor
        if (a == null || e == null) {
            pending = text
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { e.extract(text).map { a.analyze(it) } }
            reports = result
            selected = if (keepSelection) selected.coerceIn(0, (result.size - 1).coerceAtLeast(0)) else 0
            if (result.isEmpty()) message = "No link found in that text."
        }
    }

    fun clear() {
        input = ""
        reports = emptyList()
        selected = 0
    }

    fun importFile(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            busy = true
            try {
                val name = runCatching {
                    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                }.getOrNull()
                val count = withContext(Dispatchers.IO) {
                    val stream = ctx.contentResolver.openInputStream(uri) ?: error("Couldn't open that file.")
                    store.importFrom(stream, name)
                }
                message = "Imported %,d entries.".format(count)
                reload()
                rescan()
            } catch (e: Exception) {
                message = "Import failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun clearImported() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clearImported() }
            reload(); rescan()
            message = "Imported list removed."
        }
    }

    fun clearSynced() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.clearSynced() }
            reload(); rescan()
            message = "Synced list removed."
        }
    }

    fun saveServer(url: String, token: String, auto: Boolean) {
        store.serverUrl = url
        store.serverToken = token
        store.autoSync = auto
    }

    fun sync(quiet: Boolean = false) {
        if (!BuildConfig.SELF_HOSTED) return
        viewModelScope.launch {
            busy = true
            try {
                val count = withContext(Dispatchers.IO) { store.syncFromServer() }
                if (!quiet) message = "Synced %,d entries from your server.".format(count)
                reload(); rescan()
            } catch (e: Exception) {
                message = "Sync failed: ${e.message}"
                blocklist = store.info()
            } finally {
                busy = false
            }
        }
    }

    private fun rescan() {
        if (reports.isNotEmpty() && input.isNotBlank()) scan(input, keepSelection = true)
    }
}
