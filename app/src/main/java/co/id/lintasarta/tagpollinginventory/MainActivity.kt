package co.id.lintasarta.tagpollinginventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import co.id.lintasarta.tagpollinginventory.ui.navigation.MainNavigationScreen
import co.id.lintasarta.tagpollinginventory.ui.theme.TagPollingInventoryTheme
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TagPollingInventoryTheme {
                val viewModel: MainViewModel = viewModel()
                MainNavigationScreen(viewModel = viewModel)
            }
        }
    }
}
