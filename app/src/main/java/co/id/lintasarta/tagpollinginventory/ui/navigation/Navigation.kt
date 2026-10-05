package co.id.lintasarta.tagpollinginventory.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.id.lintasarta.tagpollinginventory.ui.screens.*
import co.id.lintasarta.tagpollinginventory.ui.theme.TelecomPrimary
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.AppTab
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel
import co.id.lintasarta.tagpollinginventory.ui.viewmodel.ScreenFlow

data class NavTabItem(
    val tab: AppTab,
    val label: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector
)

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

    val navTabItems = remember {
        listOf(
            NavTabItem(
                tab = AppTab.DASHBOARD,
                label = "Dashboard",
                activeIcon = Icons.Filled.SpaceDashboard,
                inactiveIcon = Icons.Outlined.SpaceDashboard
            ),
            NavTabItem(
                tab = AppTab.SEGMENTS,
                label = "Segments",
                activeIcon = Icons.AutoMirrored.Filled.AltRoute,
                inactiveIcon = Icons.AutoMirrored.Outlined.AltRoute
            ),
            NavTabItem(
                tab = AppTab.MAP,
                label = "Field Map",
                activeIcon = Icons.Filled.Explore,
                inactiveIcon = Icons.Outlined.Explore
            ),
            NavTabItem(
                tab = AppTab.EXPORT,
                label = "Export",
                activeIcon = Icons.Filled.CloudUpload,
                inactiveIcon = Icons.Outlined.CloudUpload
            ),
            NavTabItem(
                tab = AppTab.SETTINGS,
                label = "Settings",
                activeIcon = Icons.Filled.Tune,
                inactiveIcon = Icons.Outlined.Tune
            )
        )
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    shadowElevation = 12.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    NavigationBar(
                        containerColor = Color.White,
                        contentColor = TelecomPrimary,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .height(84.dp)
                            .padding(top = 8.dp)
                    ) {
                        navTabItems.forEach { item ->
                            val isSelected = currentTab == item.tab

                            val scaleAnim by animateFloatAsState(
                                targetValue = if (isSelected) 1.15f else 1.0f,
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                                label = "iconScale"
                            )

                            val iconColor by animateColorAsState(
                                targetValue = if (isSelected) TelecomPrimary else Color(0xFF78909C),
                                animationSpec = tween(durationMillis = 200),
                                label = "iconColor"
                            )

                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { viewModel.switchTab(item.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
                                        contentDescription = item.label,
                                        tint = iconColor,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .scale(scaleAnim)
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) TelecomPrimary else Color(0xFF78909C)
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color(0xFFE3F2FD),
                                    selectedIconColor = TelecomPrimary,
                                    unselectedIconColor = Color(0xFF78909C),
                                    selectedTextColor = TelecomPrimary,
                                    unselectedTextColor = Color(0xFF78909C)
                                )
                            )
                        }
                    }
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

                ScreenFlow.CREATE_SEGMENT -> CreateSegmentScreen(
                    viewModel = viewModel,
                    onBackClick = { viewModel.navigateBack() }
                )

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
                    onViewPole = { viewModel.navigateTo(ScreenFlow.INVENTORY_LIST, clearStack = true) },
                    onBackToSegment = { viewModel.navigateTo(ScreenFlow.SEGMENT_DETAIL, clearStack = true) }
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
