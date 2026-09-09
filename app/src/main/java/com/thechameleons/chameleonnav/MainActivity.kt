package com.thechameleons.chameleonnav

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thechameleons.chameleonnav.ui.components.TopBar
import com.thechameleons.chameleonnav.ui.screens.HomeScreen
import com.thechameleons.chameleonnav.ui.screens.SettingsScreen
import com.thechameleons.chameleonnav.ui.screens.SystemMonitorScreen
import com.thechameleons.chameleonnav.ui.theme.*
import com.thechameleons.chameleonnav.ui.viewmodel.NavigationViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChameleonNavTheme {
                val viewModel: NavigationViewModel = viewModel()
                RealAppScreen(viewModel = viewModel)
            }
        }
    }
}

data class NavTabItem(
    val title: String,
    val icon: ImageVector,
    val badge: String? = null
)

@Composable
fun RealAppScreen(viewModel: NavigationViewModel) {
    val context = LocalContext.current
    val telemetry by viewModel.telemetryState.collectAsState()
    val referencePath by viewModel.referencePath.collectAsState()
    val estimatedPath by viewModel.estimatedPath.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val showReferencePath by viewModel.showReferencePath.collectAsState()
    val showDestinationPicker by viewModel.showDestinationPicker.collectAsState()

    // Permission launcher for Location
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.setLocationPermissionGranted(fineGranted || coarseGranted)
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val fineCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                if (fineCheck == PackageManager.PERMISSION_GRANTED) {
                    viewModel.setLocationPermissionGranted(true)
                    viewModel.checkLocationProviders()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        val fineCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fineCheck == PackageManager.PERMISSION_GRANTED) {
            viewModel.setLocationPermissionGranted(true)
            viewModel.checkLocationProviders()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val navTabs = listOf(
        NavTabItem("Home", Icons.Default.Navigation),
        NavTabItem("Monitor", Icons.Default.Analytics),
        NavTabItem("Settings", Icons.Default.Tune)
    )

    Scaffold(
        topBar = {
            TopBar(
                satellitesInView = telemetry.satellitesInView,
                satellitesUsedInFix = telemetry.satellitesUsedInFix
            )
        },
        bottomBar = {
            // Floating Pill Bottom Navigation Bar (matches reference image exactly)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(32.dp))
                        .background(SurfaceLight)
                        .border(1.dp, CardBorder, RoundedCornerShape(32.dp))
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navTabs.forEachIndexed { index, tab ->
                        val isSelected = selectedTab == index
                        if (isSelected) {
                            // Active Tab: Solid Black Pill
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(AccentDark)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = TextOnDark,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.title,
                                    color = TextOnDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            // Inactive Tab: Clean Minimal Icon
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { viewModel.selectTab(index) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = TextMuted,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = AppBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(AppBackground)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    telemetry = telemetry,
                    referencePath = referencePath,
                    estimatedPath = estimatedPath,
                    showReferencePath = showReferencePath,
                    onStartNavigation = { viewModel.startNavigation() },
                    onStopNavigation = { viewModel.stopNavigation() },
                    onToggleForceOutage = { viewModel.toggleForceOutage() },
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onReset = { viewModel.resetNavigation() },
                    destinations = viewModel.offlineDestinations,
                    showDestinationPicker = showDestinationPicker,
                    onOpenDestinationPicker = { viewModel.openDestinationPicker() },
                    onCloseDestinationPicker = { viewModel.closeDestinationPicker() },
                    onSelectDestination = { viewModel.selectDestination(it) },
                    onClearRoute = { viewModel.clearRoute() }
                )

                1 -> SystemMonitorScreen(
                    telemetry = telemetry,
                    onReset = { viewModel.resetNavigation() }
                )

                2 -> SettingsScreen(
                    showReferencePath = showReferencePath,
                    showDebugData = true,
                    onToggleReferencePath = { viewModel.toggleReferencePath(it) },
                    onToggleDebugData = {},
                    onReset = { viewModel.resetNavigation() }
                )
            }
        }
    }
}
