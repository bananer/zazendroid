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
import de.bananer.zazendroid.ui.CourseDetailScreen
import de.bananer.zazendroid.ui.CourseDetailViewModel
import de.bananer.zazendroid.ui.CoursesScreen
import de.bananer.zazendroid.ui.HomeScreen
import de.bananer.zazendroid.ui.LibraryViewModel
import de.bananer.zazendroid.ui.PlayerScreen
import de.bananer.zazendroid.ui.PlayerViewModel
import de.bananer.zazendroid.ui.ServerSetupScreen
import de.bananer.zazendroid.ui.ServerSetupViewModel
import de.bananer.zazendroid.ui.SetupUiState
import de.bananer.zazendroid.ui.SinglesScreen
import de.bananer.zazendroid.ui.theme.ZazenDroidTheme

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("courses", "Courses", Icons.Filled.List),
    Tab("singles", "Singles", Icons.Filled.PlayArrow),
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
                            if (vm.uiState.value is SetupUiState.Saved) {
                                LaunchedEffect(Unit) { vm.backToSetup() }
                            }
                            ServerSetupScreen(vm)
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
                                                        route.startsWith("course/") -> "Course"
                                                        route == "player" -> "Now playing"
                                                        else -> ""
                                                    },
                                                )
                                            },
                                            navigationIcon = {
                                                IconButton(onClick = { nav.navigateUp() }) {
                                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                                                    icon = { Icon(tab.icon, tab.label) },
                                                    label = { Text(tab.label) },
                                                )
                                            }
                                        }
                                    }
                                },
                            ) { inner ->
                                NavHost(nav, startDestination = "home", modifier = Modifier.padding(inner)) {
                                    composable("home") {
                                        HomeScreen(
                                            libraryVm,
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
