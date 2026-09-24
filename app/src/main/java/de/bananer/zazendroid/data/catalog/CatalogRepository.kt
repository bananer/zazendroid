package de.bananer.zazendroid.data.catalog

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(val catalog: Catalog, val fromCache: Boolean = false) : CatalogState
    data class Error(val kind: CatalogErrorKind, val cachedCatalog: Catalog? = null) : CatalogState
}

/** Resolved at the UI boundary via `catalog_error_unreachable` / `catalog_error_invalid_content`. */
enum class CatalogErrorKind {
    UNREACHABLE,
    INVALID_CONTENT,
}

/**
 * Single JSON endpoint fetched with plain OkHttp + kotlinx.serialization:
 * `GET <serverUrl>/catalog.zazen.json` (`serverUrl` joined as `trimEnd('/') + "/catalog.zazen.json"`).
 * Fixed filename; no per-course fetching.
 *
 * Load rule: on server-URL change or [refresh], emit [CatalogState.Loading]
 * (unless a warm [CatalogState.Ready] exists — then it stays until the result),
 * fetch with the client's call timeout, parse with
 * `Json { ignoreUnknownKeys = true; explicitNulls = false }`.
 * `Ready(fromCache=false)`. Failure (HTTP != 200, timeout, parse error,
 * validation-empty) emits `Ready(fromCache=true)` when a cache file exists, else
 * `Error` with a [CatalogErrorKind] (UNREACHABLE vs INVALID_CONTENT, resolved
 * to localized strings at the UI boundary — never exception text).
 *
 * Cache key `<host-hash>` is the first 16 hex chars of SHA-256 over the
 * normalized server URL (per-server caches, no cross-server stale content).
 * Missing/corrupt cache file counts as no cache.
 */
class CatalogRepository(
    private val baseUrlFlow: Flow<String>,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val cacheDir: File,
    private val scope: CoroutineScope,
) {
    private val _catalogFlow = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val catalogFlow: StateFlow<CatalogState> = _catalogFlow.asStateFlow()

    @Volatile
    private var currentBaseUrl: String? = null
    private var loadJob: Job? = null

    init {
        scope.launch {
            baseUrlFlow.distinctUntilChanged().collect { url ->
                currentBaseUrl = url
                launchLoad(url)
            }
        }
    }

    fun refresh() {
        currentBaseUrl?.let { launchLoad(it) }
    }

    private fun launchLoad(url: String) {
        loadJob?.cancel()
        loadJob = scope.launch { loadNow(url) }
    }

    suspend fun loadNow(baseUrl: String) {
        if (_catalogFlow.value !is CatalogState.Ready) {
            _catalogFlow.value = CatalogState.Loading
        }
        try {
            val raw = fetch(baseUrl)
            val catalog = parse(raw, baseUrl)
            writeCache(baseUrl, raw)
            _catalogFlow.value = CatalogState.Ready(catalog.copy(fromCache = false), fromCache = false)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val cached = readCache(baseUrl)
            if (cached != null) {
                _catalogFlow.value = CatalogState.Ready(cached.copy(fromCache = true), fromCache = true)
            } else {
                _catalogFlow.value = CatalogState.Error(errorKind(e), cachedCatalog = null)
            }
        }
    }

    private suspend fun fetch(baseUrl: String): String = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/catalog.zazen.json"
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw CatalogHttpException(response.code)
            response.body?.string() ?: throw CatalogParseException()
        }
    }

    private fun parse(raw: String, baseUrl: String): Catalog {
        val dto = try {
            json.decodeFromString(CatalogDto.serializer(), raw)
        } catch (e: Exception) {
            throw CatalogParseException(e)
        }
        try {
            return dto.toDomain(baseUrl)
        } catch (e: CatalogEmptyException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw CatalogParseException(e)
        }
    }

    private fun errorKind(e: Exception): CatalogErrorKind = when (e) {
        is CatalogParseException, is CatalogEmptyException -> CatalogErrorKind.INVALID_CONTENT
        else -> CatalogErrorKind.UNREACHABLE
    }

    private fun cacheFile(baseUrl: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
        val hex = digest.digest(baseUrl.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(cacheDir, hex.take(16) + ".json")
    }

    private fun writeCache(baseUrl: String, raw: String) {
        try {
            cacheDir.mkdirs()
            cacheFile(baseUrl).writeText(raw)
        } catch (_: Exception) {
            // Cache is best-effort; load already succeeded.
        }
    }

    private fun readCache(baseUrl: String): Catalog? {
        return try {
            val file = cacheFile(baseUrl)
            if (!file.exists()) return null
            parse(file.readText(), baseUrl)
        } catch (_: Exception) {
            null
        }
    }
}

class CatalogHttpException(code: Int) : IllegalStateException("HTTP $code")
class CatalogParseException(cause: Throwable? = null) : IllegalStateException("Parse error", cause)
