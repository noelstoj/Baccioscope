package com.aamon.baccioscope

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.aamon.baccioscope.ui.screens.CameraPreviewsScreen
import com.aamon.baccioscope.ui.screens.ConnectivityScreen
import com.aamon.baccioscope.ui.screens.DataArchiveScreen
import com.aamon.baccioscope.ui.screens.FleetStatusScreen
import com.aamon.baccioscope.ui.theme.Typography

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    primary = Color(0xFFBB86FC),
                    onPrimary = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                ),
                typography = Typography
            ) {
                BaccioscopeApp()
            }
        }
    }
}

@Composable
fun BaccioscopeApp() {
    var showSplash by remember { mutableStateOf(true) }
    if (showSplash) {
        SplashScreen(onTimeout = { showSplash = false })
    } else {
        BaccioscopeMainContent()
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Initialize ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val videoUri =
                "android.resource://${context.packageName}/${R.raw.baccio_splash}".toUri()
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true

            // Listen for the end of the video to transition to the main content
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        onTimeout()
                    }
                }
            })
        }
    }

    // Clean up player when the composable leaves the screen
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), // Background usually black for video
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Hide play/pause buttons
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM // Fill screen
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BaccioscopeMainContent() {
    var isRedFilterEnabled by remember { mutableStateOf(false) }

    val pages = listOf("Live", "Aamon", "Nomarch", "Wormgat")
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF401050))
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    // This bar stays at the very top under system icons
                    Spacer(Modifier.height(4.dp))
                }
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.footer_banner),
                        contentDescription = "Baccioscope Banner",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                    Text(
                        text = "© 2026, AccessAstronomy",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp)
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {

                // --- LAYER 1: The Scrolling World ---
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues) // Matches Scaffold top bar
                        .background(MaterialTheme.colorScheme.background),
                    userScrollEnabled = true,
                    beyondViewportPageCount = 1
                ) { pageIndex ->
                    Column(modifier = Modifier.fillMaxSize()) {

                        // 1. The Banner Image
                        val bannerResId = when (pageIndex) {
                            0 -> R.drawable.banner_live
                            1 -> R.drawable.banner_aamon
                            2 -> R.drawable.banner_nomarch
                            3 -> R.drawable.banner_wormgat
                            else -> R.drawable.banner_live
                        }

                        Image(
                            painter = painterResource(id = bannerResId),
                            contentDescription = "Banner ${pages[pageIndex]}",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )

                        // 2. The Content Section
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (pageIndex) {
                                0 -> CameraPreviewsScreen()
                                1 -> FleetStatusScreen()
                                2 -> DataArchiveScreen()
                                3 -> ConnectivityScreen()
                            }
                        }
                    }
                }

                // --- LAYER 2: The Fixed Menu Overlay ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(paddingValues) // Aligns top edge with the Pager's top edge
                        .aspectRatio(21f / 9f)   // Forces the bottom edge to match the banner's bottom
                ) {
                    PrimaryTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        divider = {}
                    ) {
                        pages.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                text = {
                                    Text(
                                        title,
                                        color = if (pagerState.currentPage == index)
                                            MaterialTheme.colorScheme.primary
                                        else Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Night Vision Overlay
        if (isRedFilterEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x55FF0000))
            )
        }
    }
}