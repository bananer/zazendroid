package de.bananer.zazendroid.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val Context.settingsDataStore by preferencesDataStore("settings")

/**
 * Single-scalar store for the data-server base URL. Read before anything else;
 * catalog cache is a plain file, everything relational lives in Room.
 */
class ServerUrlStore(
    private val store: DataStore<Preferences>,
) {
    /** Production entry point: DataStore scoped to the app context. */
    constructor(context: Context) : this(context.settingsDataStore)

    companion object {
        const val DEFAULT_DATA_SERVER_URL = "http://10.0.2.2:8042/"
        val KEY_URL = stringPreferencesKey("data_server_url")

        fun normalize(url: String): String {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) throw EmptyServerUrlException()
            val withoutTrailing = trimmed.trimEnd('/')
            if (withoutTrailing.isEmpty()) throw EmptyServerUrlException()
            val parsed = (withoutTrailing + "/").toHttpUrlOrNull()
                ?: throw InvalidServerUrlException(url)
            if (parsed.scheme != "https" && parsed.scheme != "http") {
                throw InvalidServerUrlException(url)
            }
            return withoutTrailing
        }
    }

    /** Stored value or [DEFAULT_DATA_SERVER_URL] when absent. */
    fun serverUrlFlow(): Flow<String> =
        store.data.map { it[KEY_URL] ?: DEFAULT_DATA_SERVER_URL }

    /** Emits true once a value was explicitly stored (drives first-launch gating). */
    fun hasStoredUrlFlow(): Flow<Boolean> =
        store.data.map { it.contains(KEY_URL) }

    /**
     * Normalizes (`trim()`, strip trailing `/`) and validates with OkHttp
     * `HttpUrl`. Invalid → throws [IllegalArgumentException], nothing persisted.
     * Empty → throws, never resets to default silently.
     */
    suspend fun setServerUrl(url: String) {
        val normalized = normalize(url)
        store.edit { it[KEY_URL] = normalized }
    }

    /** Clears the stored URL, returning the app to first-launch setup. */
    suspend fun clear() {
        store.edit { it.remove(KEY_URL) }
    }

    /** True when a value was explicitly stored (drives first-launch check). */
    suspend fun isCustomUrl(): Boolean =
        store.data.map { it.contains(KEY_URL) }.first()
}

/** Empty input: UI resolves to `R.string.setup_error_empty`. */
class EmptyServerUrlException : IllegalArgumentException("Server URL must not be empty")

/** Unparseable or non-http(s) URL: UI resolves to `R.string.setup_error_invalid`. */
class InvalidServerUrlException(val input: String) : IllegalArgumentException("Invalid server URL: $input")
