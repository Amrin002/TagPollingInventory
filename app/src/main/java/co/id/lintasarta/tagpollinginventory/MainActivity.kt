package co.id.lintasarta.tagpollinginventory

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import co.id.lintasarta.tagpollinginventory.ui.navigation.MainNavigationScreen
import co.id.lintasarta.tagpollinginventory.ui.theme.TagPollingInventoryTheme
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        requestStoragePermissions()
        
        setContent {
            TagPollingInventoryTheme {
                val viewModel: MainViewModel = viewModel()
                
                // Save data when app goes to background
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val lifecycleObserver = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP ||
                            event == Lifecycle.Event.ON_PAUSE) {
                            viewModel.repository.saveData()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                    }
                }
                
                MainNavigationScreen(viewModel = viewModel)
            }
        }
    }

    private fun requestStoragePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}

