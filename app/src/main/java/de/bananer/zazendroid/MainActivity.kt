package de.bananer.zazendroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.bananer.zazendroid.data.favorites.FavoriteEntry
import de.bananer.zazendroid.ui.screen.CourseDetailScreen
import de.bananer.zazendroid.ui.viewmodel.CourseDetailViewModel
import de.bananer.zazendroid.ui.screen.CoursesScreen
import de.bananer.zazendroid.ui.screen.HomeScreen
import de.bananer.zazendroid.ui.viewmodel.LibraryViewModel
import de.bananer.zazendroid.ui.screen.PlayerScreen
import de.bananer.zazendroid.ui.viewmodel.PlayerViewModel
import de.bananer.zazendroid.ui.screen.ServerSetupScreen
import de.bananer.zazendroid.ui.viewmodel.ServerSetupViewModel
import de.bananer.zazendroid.ui.viewmodel.SetupUiState
import de.bananer.zazendroid.ui.screen.MoodScreen
import de.bananer.zazendroid.ui.screen.SearchScreen
import de.bananer.zazendroid.ui.screen.SinglesScreen
import de.bananer.zazendroid.ui.theme.ZazenDroidTheme
import de.bananer.zazendroid.ui.viewmodel.MoodViewModel

private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", R.string.tab_home, Icons.Filled.Home),
    Tab("courses", R.string.tab_courses, Icons.Filled.List),
    Tab("singles", R.string.tab_singles, Icons.Filled.PlayArrow),
    Tab("search", R.string.tab_search, Icons.Filled.Search),
)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ZazenApp).container
        // Refresh the catalog on every foregrounding (cold start + resume).
        // Repository keeps warm Ready state until the result; first load shows
        val refreshObserver = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = container.catalogRepository.refresh()
        }
        this.refreshObserver = refreshObserver
        ProcessLifecycleOwner.get().lifecycle.addObserver(refreshObserver)
        setContent {
            ZazenDroidTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val hasStoredUrl by remember(container) {
                        container.serverUrlStore.hasStoredUrlFlow()
                    }.collectAsState(initial = null)
                    when (hasStoredUrl) {
                        null -> { /* first-launch check pending; blank frame */ }
                        false -> {
                            val vm: ServerSetupViewModel = viewModel(
                                factory = vmFactory { ServerSetupViewModel(container.serverUrlStore) },
                            )
                            // After a reset the VM still holds its old state
                            // (Saved/InvalidUrl); start fresh with the default
                            // prefill. Typing states survive rotation untouched.
                            val setupState by vm.uiState.collectAsState()
                            if (setupState is SetupUiState.Saved) {
                                LaunchedEffect(Unit) { vm.backToSetup() }
                            }
                            Scaffold { inner ->
                                ServerSetupScreen(vm, modifier = Modifier.padding(inner))
                            }
                        }
                        true -> {
                            val nav = rememberNavController()
                            val entry by nav.currentBackStackEntryAsState()
                            val route = entry?.destination?.route
                            val onTab = route in TABS.map { it.route }
                            // One library VM shared by all three tabs: same
                            // catalog flow, one subscription.
                            val libraryVm: LibraryViewModel = viewModel(
                                factory = vmFactory {
                                    LibraryViewModel(
                                        container.catalogRepository,
                                        container.progressRepository,
                                        container.favoritesRepository,
                                        container.serverUrlStore.hasStoredUrlFlow(),
                                        container.serverUrlStore,
                                    )
                                },
                            )
                            Scaffold(
                                topBar = {
                                    // Tabs have no back stack; detail screens
                                    // get the arrow.
                                    if (!onTab && route != null) {
                                        TopAppBar(
                                            title = {
                                                Text(
                                                    when {
                                                        route.startsWith("course/") -> stringResource(R.string.nav_course)
                                                        route == "player" -> stringResource(R.string.nav_now_playing)
                                                        else -> ""
                                                    },
                                                )
                                            },
                                            navigationIcon = {
                                                IconButton(onClick = { nav.navigateUp() }) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.ArrowBack,
                                                        stringResource(R.string.cd_back),
                                                    )
                                                }
                                            },
                                        )
                                    }
                                },
                                bottomBar = {
                                    if (onTab) {
                                        NavigationBar {
                                            TABS.forEach { tab ->
                                                NavigationBarItem(
                                                    selected = route == tab.route,
                                                    onClick = {
                                                        nav.navigate(tab.route) {
                                                            popUpTo(nav.graph.startDestinationId) {
                                                                saveState = true
                                                            }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    },
                                                    icon = { Icon(tab.icon, stringResource(tab.labelRes)) },
                                                    label = { Text(stringResource(tab.labelRes)) },
                                                )
                                            }
                                        }
                                    }
                                },
                            ) { inner ->
                                val moodVm: MoodViewModel = viewModel(
                                    factory = vmFactory { MoodViewModel(container.moodRepository) },
                                )
                                NavHost(nav, startDestination = "home", modifier = Modifier.padding(inner)) {
                                    composable("home") {
                                        val today by moodVm.today.collectAsState()
                                        HomeScreen(
                                            libraryVm,
                                            moodComplete = today != null,
                                            onMoodClick = {
                                                moodVm.enter(today != null)
                                                nav.navigate("mood")
                                            },
                                            onContinueClick = { course, index ->
                                                container.playbackManager.play(course, index)
                                                nav.navigate("player")
                                            },
                                            onFavoriteClick = { entry ->
                                                when (entry) {
                                                    is FavoriteEntry.UnitFavorite ->
                                                        container.playbackManager.queue(
                                                            entry.course,
                                                            entry.unit.orderIndex,
                                                        )
                                                    is FavoriteEntry.SingleFavorite ->
                                                        container.playbackManager.queueSingle(entry.single)
                                                }
                                                nav.navigate("player")
                                            },
                                            onPreload = { course, index ->
                                                container.playbackManager.preload(course, index)
                                            },
                                            onPreloadSingle = { single ->
                                                container.playbackManager.preloadSingle(single)
                                            },
                                            onResetServer = { libraryVm.resetServer() },
                                        )
                                    }
                                    composable("mood") {
                                        MoodScreen(moodVm, onDone = { nav.navigateUp() })
                                    }
                                    composable("courses") {
                                        CoursesScreen(
                                            libraryVm,
                                            onCourseClick = { nav.navigate("course/$it") },
                                            onPreload = { course, index ->
                                                container.playbackManager.preload(course, index)
                                            },
                                            onResetServer = { libraryVm.resetServer() },
                                        )
                                    }
                                    composable("singles") {
                                        SinglesScreen(
                                            libraryVm,
                                            onSingleClick = { single ->
                                                container.playbackManager.queueSingle(single)
                                                nav.navigate("player")
                                            },
                                            onPreloadSingle = { single ->
                                                container.playbackManager.preloadSingle(single)
                                            },
                                            onResetServer = { libraryVm.resetServer() },
                                        )
                                    }
                                    composable("search") {
                                        SearchScreen(
                                            libraryVm,
                                            onCourseClick = { nav.navigate("course/$it") },
                                            onSingleClick = { single ->
                                                container.playbackManager.queueSingle(single)
                                                nav.navigate("player")
                                            },
                                            onResetServer = { libraryVm.resetServer() },
                                        )
                                    }
                                    composable(
                                        "course/{courseId}",
                                        arguments = listOf(navArgument("courseId") { type = NavType.StringType }),
                                    ) { backEntry ->
                                        val courseId = backEntry.arguments?.getString("courseId") ?: return@composable
                                        val vm: CourseDetailViewModel = viewModel(
                                            key = courseId,
                                            factory = vmFactory {
                                                CourseDetailViewModel(
                                                    courseId,
                                                    container.catalogRepository,
                                                    container.progressRepository,
                                                )
                                            },
                                        )
                                        CourseDetailScreen(
                                            vm,
                                            onUnitClick = { course, index ->
                                                container.playbackManager.queue(course, index)
                                                nav.navigate("player")
                                            },
                                            onPreload = { course, index ->
                                                container.playbackManager.preload(course, index)
                                            },
                                        )
                                    }
                                    composable("player") {
                                        val vm: PlayerViewModel = viewModel(
                                            factory = vmFactory {
                                                PlayerViewModel(
                                                    container.playbackManager,
                                                    container.favoritesRepository,
                                                )
                                            },
                                        )
                                        PlayerScreen(vm)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private var refreshObserver: DefaultLifecycleObserver? = null

    override fun onDestroy() {
        refreshObserver?.let { ProcessLifecycleOwner.get().lifecycle.removeObserver(it) }
        refreshObserver = null
        super.onDestroy()
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T : ViewModel> vmFactory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <M : ViewModel> create(modelClass: Class<M>): M = create() as M
    }
