package co.id.lintasarta.tagpollinginventory.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import co.id.lintasarta.tagpollinginventory.ui.screens.*
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.AppTab
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.ScreenFlow

@Composable
fun MainNavigationScreen(viewModel: MainViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()

    val showBottomBar = currentScreen == ScreenFlow.TAB_ROOT
    val canGoBack = currentScreen != ScreenFlow.TAB_ROOT || currentTab != AppTab.DASHBOARD

    // Intercept system/hardware back button or gesture to navigate back properly!
    BackHandler(enabled = canGoBack) {
        viewModel.navigateBack()
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color.White,
                    contentColor = TelecomPrimary
                ) {
                    NavigationBarItem(
                        selected = currentTab == AppTab.DASHBOARD,
                        onClick = { viewModel.switchTab(AppTab.DASHBOARD) },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                        label = { Text("Dashboard", fontWeight = if (currentTab == AppTab.DASHBOARD) FontWeight.Bold else FontWeight.Normal) }
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.SEGMENTS,
                        onClick = { viewModel.switchTab(AppTab.SEGMENTS) },
                        icon = { Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Segments") },
                        label = { Text("Segments", fontWeight = if (currentTab == AppTab.SEGMENTS) FontWeight.Bold else FontWeight.Normal) }
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.MAP,
                        onClick = { viewModel.switchTab(AppTab.MAP) },
                        icon = { Icon(Icons.Default.Map, contentDescription = "Map") },
                        label = { Text("Map", fontWeight = if (currentTab == AppTab.MAP) FontWeight.Bold else FontWeight.Normal) }
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.EXPORT,
                        onClick = { viewModel.switchTab(AppTab.EXPORT) },
                        icon = { Icon(Icons.Default.ImportExport, contentDescription = "Export") },
                        label = { Text("Export", fontWeight = if (currentTab == AppTab.EXPORT) FontWeight.Bold else FontWeight.Normal) }
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.SETTINGS,
                        onClick = { viewModel.switchTab(AppTab.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", fontWeight = if (currentTab == AppTab.SETTINGS) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                ScreenFlow.TAB_ROOT -> {
                    when (currentTab) {
                        AppTab.DASHBOARD -> DashboardScreen(
                            viewModel = viewModel,
                            onContinueFieldWork = { viewModel.navigateTo(ScreenFlow.FIELD_MAP) },
                            onViewProject = { viewModel.switchTab(AppTab.SEGMENTS) },
                            onImportRouteClick = { viewModel.navigateTo(ScreenFlow.IMPORT_ROUTE) }
                        )
                        AppTab.SEGMENTS -> SegmentListScreen(
                            viewModel = viewModel,
                            onSegmentClick = { segId -> viewModel.selectSegment(segId) }
                        )
                        AppTab.MAP -> FieldMapScreen(
                            viewModel = viewModel,
                            onBackClick = { viewModel.navigateBack() },
                            onTagThisPoleClick = { viewModel.proceedToGpsCapture() }
                        )
                        AppTab.EXPORT -> ExportCenterScreen(
                            viewModel = viewModel,
                            onViewHistoryClick = { viewModel.navigateTo(ScreenFlow.FILE_MANAGEMENT) }
                        )
                        AppTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                    }
                }

                ScreenFlow.IMPORT_ROUTE -> ImportRouteScreen(
                    viewModel = viewModel,
                    onBackClick = { viewModel.navigateBack() }
                )

                ScreenFlow.IMPORT_PREVIEW -> ImportPreviewScreen(
                    viewModel = viewModel,
                    onBackClick = { viewModel.navigateBack() },
                    onConfirmImportClick = { viewModel.navigateTo(ScreenFlow.FIELD_MAP) }
                )

                ScreenFlow.SEGMENT_DETAIL -> SegmentDetailScreen(
                    viewModel = viewModel,
                    onBackClick = { viewModel.navigateBack() },
                    onStartTagging = { poleId -> viewModel.startTaggingPole(poleId) }
                )

                ScreenFlow.FIELD_MAP -> FieldMapScreen(
                    viewModel = viewModel,
                    onBackClick = { viewModel.navigateBack() },
                    onTagThisPoleClick = { viewModel.proceedToGpsCapture() }
                )

                ScreenFlow.GPS_CAPTURE -> GpsCaptureScreen(
                    viewModel = viewModel,
                    onBackClick = { viewModel.navigateBack() },
                    onContinueClick = { viewModel.navigateTo(ScreenFlow.POLE_INFO) }
                )

                ScreenFlow.POLE_INFO -> PoleInformationScreen(
                    viewModel = viewModel,
                    onBackClick = { viewModel.navigateBack() },
                    onContinueClick = { viewModel.navigateTo(ScreenFlow.PHOTO_CAPTURE) }
                )

                ScreenFlow.PHOTO_CAPTURE -> PhotoCaptureScreen(
                    viewModel = viewModel,
                    onBackClick = { viewModel.navigateBack() },
                    onUsePhotoClick = { viewModel.navigateTo(ScreenFlow.REVIEW_POLE) }
                )

                ScreenFlow.REVIEW_POLE -> ReviewPoleScreen(
                    viewModel = viewModel,
                    onBackClick = { viewModel.navigateBack() },
                    onSaveSuccess = { viewModel.navigateTo(ScreenFlow.SAVE_SUCCESS) }
                )

                ScreenFlow.SAVE_SUCCESS -> SaveSuccessScreen(
                    viewModel = viewModel,
                    onTagNextPole = { viewModel.prepareNextPoleTagging() },
                    onViewPole = { viewModel.navigateTo(ScreenFlow.INVENTORY_LIST) },
                    onBackToSegment = { viewModel.navigateTo(ScreenFlow.SEGMENT_DETAIL) }
                )

                ScreenFlow.INVENTORY_LIST -> PoleInventoryListScreen(
                    viewModel = viewModel,
                    onBackClick = { viewModel.navigateBack() },
                    onPoleClick = { poleId -> viewModel.startTaggingPole(poleId) }
                )

                ScreenFlow.EXPORT_OPTIONS -> ExportCenterScreen(
                    viewModel = viewModel,
                    onViewHistoryClick = { viewModel.navigateTo(ScreenFlow.FILE_MANAGEMENT) }
                )

                ScreenFlow.EXPORT_PROGRESS -> ExportProgressScreen(
                    viewModel = viewModel,
                    onBackClick = { viewModel.navigateBack() },
                    onOpenFolderClick = { viewModel.navigateTo(ScreenFlow.FILE_MANAGEMENT) }
                )

                ScreenFlow.FILE_MANAGEMENT -> FileManagementScreen(
                    viewModel = viewModel,
                    onBackClick = { viewModel.navigateBack() }
                )
            }
        }
    }
}
