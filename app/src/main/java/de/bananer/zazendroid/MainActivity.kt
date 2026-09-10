package de.bananer.zazendroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import de.bananer.zazendroid.ui.LibraryScreen
import de.bananer.zazendroid.ui.LibraryViewModel
import de.bananer.zazendroid.ui.ServerSetupScreen
import de.bananer.zazendroid.ui.ServerSetupViewModel
import de.bananer.zazendroid.ui.theme.ZazenDroidTheme

class MainActivity : ComponentActivity() {
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
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                        ServerSetupViewModel(container.serverUrlStore) as T
                                },
                            )
                            ServerSetupScreen(vm)
                        }
                        true -> {
                            val vm: LibraryViewModel = viewModel(
                                factory = object : ViewModelProvider.Factory {
                                    @Suppress("UNCHECKED_CAST")
                                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                        LibraryViewModel(
                                            container.catalogRepository,
                                            container.progressRepository,
                                            container.serverUrlStore.hasStoredUrlFlow(),
                                        ) as T
                                },
                            )
                            LibraryScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
