package com.aamon.baccioscope

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Import our individual screen views
import com.aamon.baccioscope.ui.screens.CameraPreviewsScreen
import com.aamon.baccioscope.ui.screens.ConnectivityScreen
import com.aamon.baccioscope.ui.screens.DataArchiveScreen
import com.aamon.baccioscope.ui.screens.FleetStatusScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Force a dark color scheme for the entire app
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    primary = Color(0xFFBB86FC),
                    onPrimary = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                BaccioscopeApp()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BaccioscopeApp() {
    // State for the Night Vision Red Filter
    var isRedFilterEnabled by remember { mutableStateOf(false) }

    // Setup the swipeable pager state
    val pages = listOf("Cameras", "Status", "Archive", "Network")
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        // Main App Scaffold
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("Baccioscope", fontWeight = FontWeight.Bold)
                    },
                    actions = {
                        // Night Vision Toggle Button
                        IconButton(onClick = { isRedFilterEnabled = !isRedFilterEnabled }) {
                            Icon(
                                imageVector = if (isRedFilterEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Night Vision",
                                tint = if (isRedFilterEnabled) Color.Red else LocalContentColor.current
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // View Picker (Tabs that sync with the Pager)
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    pages.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = { Text(title) }
                        )
                    }
                }

                // Swipeable Views Container
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> CameraPreviewsScreen()
                        1 -> FleetStatusScreen()
                        2 -> DataArchiveScreen()
                        3 -> ConnectivityScreen()
                    }
                }
            }
        }

        // The Red Filter Overlay
        // Placed at the very root of the Box so it covers everything including the TopAppBar.
        // It ignores touches automatically because it has no clickable modifier.
        if (isRedFilterEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x55FF0000)) // Semi-transparent pure red
            )
        }
    }
}