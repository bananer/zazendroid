package de.bananer.zazendroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.bananer.zazendroid.ui.CourseDetailScreen
import de.bananer.zazendroid.ui.CourseDetailViewModel
import de.bananer.zazendroid.ui.LibraryScreen
import de.bananer.zazendroid.ui.LibraryViewModel
import de.bananer.zazendroid.ui.PlayerScreen
import de.bananer.zazendroid.ui.PlayerViewModel
import de.bananer.zazendroid.ui.ServerSetupScreen
import de.bananer.zazendroid.ui.ServerSetupViewModel
import de.bananer.zazendroid.ui.theme.ZazenDroidTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ZazenApp).container
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
                            ServerSetupScreen(vm)
                        }
                        true -> {
                            val nav = rememberNavController()
                            val entry by nav.currentBackStackEntryAsState()
                            val route = entry?.destination?.route
                            Scaffold(
                                topBar = {
                                    // Root (library) has no back stack; every child
                                    // screen gets the arrow.
                                    if (route != null && route != "library") {
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
                            ) { inner ->
                                NavHost(nav, startDestination = "library", modifier = Modifier.padding(inner)) {
                                    composable("library") {
                                        val vm: LibraryViewModel = viewModel(
                                            factory = vmFactory {
                                                LibraryViewModel(
                                                    container.catalogRepository,
                                                    container.progressRepository,
                                                    container.serverUrlStore.hasStoredUrlFlow(),
                                                )
                                            },
                                        )
                                        LibraryScreen(
                                            vm,
                                            onCourseClick = { nav.navigate("course/$it") },
                                            onContinueClick = { course, index ->
                                                container.playbackManager.play(course, index)
                                                nav.navigate("player")
                                            },
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
                                        CourseDetailScreen(vm) { course, index ->
                                            container.playbackManager.queue(course, index)
                                            nav.navigate("player")
                                        }
                                    }
                                    composable("player") {
                                        val vm: PlayerViewModel = viewModel(
                                            factory = vmFactory { PlayerViewModel(container.playbackManager) },
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
}

@Suppress("UNCHECKED_CAST")
private fun <T : ViewModel> vmFactory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <M : ViewModel> create(modelClass: Class<M>): M = create() as M
    }
