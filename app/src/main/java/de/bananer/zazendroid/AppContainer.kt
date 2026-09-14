package de.bananer.zazendroid

import android.app.Application
import android.content.Context
import androidx.room.Room
import de.bananer.zazendroid.data.catalog.CatalogRepository
import de.bananer.zazendroid.data.favorites.FavoritesRepository
import de.bananer.zazendroid.data.favorites.RoomFavoritesRepository
import de.bananer.zazendroid.data.local.MIGRATION_1_2
import de.bananer.zazendroid.data.local.ZazenDb
import de.bananer.zazendroid.data.playback.ExoPlaybackManager
import de.bananer.zazendroid.data.playback.PlaybackManager
import de.bananer.zazendroid.data.progress.ContinueQueue
import de.bananer.zazendroid.data.progress.ProgressRepository
import de.bananer.zazendroid.data.progress.RoomProgressRepository
import de.bananer.zazendroid.data.settings.ServerUrlStore
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class ZazenApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}

/**
 * Hand-written manual DI container. Future repositories (tracking)
 * are added as lazily-built fields here reusing the [ZazenDb] singleton —
 * no new database, no new DI framework.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val json: Json by lazy {
        Json { ignoreUnknownKeys = true; explicitNulls = false }
    }

    val serverUrlStore: ServerUrlStore by lazy { ServerUrlStore(appContext) }

    val db: ZazenDb by lazy {
        Room.databaseBuilder(appContext, ZazenDb::class.java, "zazen.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    val progressRepository: ProgressRepository by lazy {
        RoomProgressRepository(db.progressDao())
    }

    val favoritesRepository: FavoritesRepository by lazy {
        RoomFavoritesRepository(db.favoriteDao())
    }

    val catalogRepository: CatalogRepository by lazy {
        CatalogRepository(
            baseUrlFlow = serverUrlStore.serverUrlFlow(),
            okHttpClient = okHttpClient,
            json = json,
            cacheDir = File(appContext.filesDir, "catalog-cache"),
            scope = appScope,
        )
    }

    val continueQueue: ContinueQueue get() = ContinueQueue
    val playbackManager: PlaybackManager by lazy {
        ExoPlaybackManager(appContext, progressRepository, appScope)
    }
}
