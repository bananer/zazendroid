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
        const val DEFAULT_DATA_SERVER_URL = "https://example.com/meditation/"
        val KEY_URL = stringPreferencesKey("data_server_url")

        fun normalize(url: String): String {
            val trimmed = url.trim()
            require(trimmed.isNotEmpty()) { "Server URL must not be empty" }
            val withoutTrailing = trimmed.trimEnd('/')
            require(withoutTrailing.isNotEmpty()) { "Server URL must not be empty" }
            val parsed = (withoutTrailing + "/").toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Invalid server URL: $url")
            require(parsed.scheme == "https" || parsed.scheme == "http") {
                "Invalid server URL: $url"
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

    /** True when a value was explicitly stored (drives first-launch check). */
    suspend fun isCustomUrl(): Boolean =
        store.data.map { it.contains(KEY_URL) }.first()
}
