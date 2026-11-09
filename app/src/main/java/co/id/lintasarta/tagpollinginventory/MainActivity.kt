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

    private var mainViewModel: MainViewModel? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            mainViewModel?.startLocationUpdates()
        }
    }

    override fun onResume() {
        super.onResume()
        if (mainViewModel?.locationProvider?.hasLocationPermission() == true) {
            mainViewModel?.startLocationUpdates()
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        handleDebugNavigation(intent)
    }

    private fun handleDebugNavigation(intent: android.content.Intent?) {
        val targetScreen = intent?.getStringExtra("screen")
        if (targetScreen != null) {
            try {
                val flow = co.id.lintasarta.tagpollinginventory.ui.viewmodel.ScreenFlow.valueOf(targetScreen)
                mainViewModel?.navigateTo(flow)
            } catch (_: Exception) {}
        }
        val targetTab = intent?.getStringExtra("tab")
        if (targetTab != null) {
            try {
                val tab = co.id.lintasarta.tagpollinginventory.ui.viewmodel.AppTab.valueOf(targetTab)
                mainViewModel?.switchTab(tab)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            val osmdroidDir = java.io.File(filesDir, "osmdroid").apply { if (!exists()) mkdirs() }
            val tilesDir = java.io.File(osmdroidDir, "tiles").apply { if (!exists()) mkdirs() }
            val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
            org.osmdroid.config.Configuration.getInstance().load(this, prefs)
            org.osmdroid.config.Configuration.getInstance().userAgentValue = packageName
            org.osmdroid.config.Configuration.getInstance().osmdroidBasePath = osmdroidDir
            org.osmdroid.config.Configuration.getInstance().osmdroidTileCache = tilesDir
        } catch (_: Exception) {}

        enableEdgeToEdge()
        
        requestStoragePermissions()
        
        setContent {
            TagPollingInventoryTheme {
                val viewModel: MainViewModel = viewModel()
                mainViewModel = viewModel
                
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    handleDebugNavigation(intent)
                }
                
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

