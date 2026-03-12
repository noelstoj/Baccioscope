package com.aamon.baccioscope.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aamon.baccioscope.R
import com.aamon.baccioscope.ui.theme.BinkFamily
import com.aamon.baccioscope.ui.theme.handWriting
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================
// 1. DATA MODELS & NETWORK LAYER
// ============================================================================

data class PreviewsStatusResponse(
    @SerializedName("last_updated") val lastUpdated: Double,
    @SerializedName("total_files") val totalFiles: Int,
    val files: List<PreviewFile>
)

data class PreviewFile(
    val filename: String,
    @SerializedName("size_bytes") val sizeBytes: Long,
    @SerializedName("modified_timestamp") val modifiedTimestamp: Double,
    @SerializedName("modified_date") val modifiedDate: String,
    @SerializedName("is_image") val isImage: Boolean,
    @SerializedName("is_video") val isVideo: Boolean = false,
    @SerializedName("download_url") val downloadUrl: String
)

data class FleetMember(
    @SerializedName("Name") val name: String,
    @SerializedName("AKA") val aka: String?,
    @SerializedName("Lat") val lat: String,
    @SerializedName("Lon") val lon: String
)

data class NomarchStatusResponse(
    val date: String,
    @SerializedName("average_files") val averageFiles: Double,
    val devices: List<NomarchDeviceStatus>
)

data class NomarchDeviceStatus(
    val name: String,
    @SerializedName("pi_to_server_status") val piToServerStatus: String,
    @SerializedName("server_to_archive_status") val serverToArchiveStatus: String,
    @SerializedName("is_low_count_anomaly") val isLowCountAnomaly: Boolean
)

interface BaccioscopeApi {
    @GET("api/v1/previews/status")
    suspend fun getPreviewsStatus(): PreviewsStatusResponse

    @GET("api/v1/nomarch/status")
    suspend fun getNomarchStatus(): NomarchStatusResponse

    @GET("api/v1/fleetmembers")
    suspend fun getFleetMembers(): List<FleetMember>

    companion object {
        private const val BASE_URL = "http://gifted-kirch.apsys.nl:8000/"

        fun create(): BaccioscopeApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BaccioscopeApi::class.java)
        }
    }
}

// ============================================================================
// 2. VIEW MODEL
// ============================================================================

data class CameraUiModel(
    val deviceId: String,
    val status: String,
    val latestTimestamp: Double?,
    val midnightTimestamp: Double?,
    val latestDateString: String?,
    val midnightDateString: String?,
    val imagePrefix: String,
    val xPercent: Float,
    val yPercent: Float,
    val clusterIndex: Int = 0,
    val clusterCount: Int = 1,
    val videoUrl: String? = null,
    val videoDate: String? = null
)

sealed class PreviewsUiState {
    object Loading : PreviewsUiState()
    data class Success(val cameras: List<CameraUiModel>) : PreviewsUiState()
    data class Error(val message: String) : PreviewsUiState()
}

class CameraPreviewsViewModel : ViewModel() {
    private val api = BaccioscopeApi.create()

    private val _uiState = MutableStateFlow<PreviewsUiState>(PreviewsUiState.Loading)
    val uiState: StateFlow<PreviewsUiState> = _uiState.asStateFlow()

    // -------------------------------------------------------------------------
    // CALIBRATED MAP COORDINATES (STRICT SOURCE OF TRUTH)
    // -------------------------------------------------------------------------
    private val mapMinLat = 52.337052
    private val mapMaxLat = 53.704927
    private val mapMinLon = 5.162734
    private val mapMaxLon = 7.150858
    // -------------------------------------------------------------------------

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    coroutineScope {
                        val membersDeferred = async { api.getFleetMembers() }
                        val nomarchDeferred = async { api.getNomarchStatus() }
                        val previewsDeferred = async { api.getPreviewsStatus() }

                        val members = membersDeferred.await()
                        val nomarchResponse = nomarchDeferred.await()
                        val previews = previewsDeferred.await()

                        val videos = previews.files.filter { it.isVideo }

                        val rawModels = members.map { member ->
                            val prefix = member.aka ?: member.name
                            val nomarchDevice = nomarchResponse.devices.find { it.name == member.name }
                            val deviceStatus = nomarchDevice?.piToServerStatus ?: "Unknown"

                            val latestFile = previews.files.find { it.filename == "$prefix.latest.jpg" }
                            val midnightFile = previews.files.find { it.filename == "$prefix.midnight.jpg" }

                            val videoInfo = videos.firstOrNull { it.filename.startsWith(prefix, ignoreCase = true) }
                            val videoUrl = videoInfo?.downloadUrl?.let { "http://gifted-kirch.apsys.nl:8000$it" }
                            val videoDate = videoInfo?.modifiedDate?.formatToReadableDate()

                            val latDouble = member.lat.toDoubleOrNull() ?: 0.0
                            val lonDouble = member.lon.toDoubleOrNull() ?: 0.0

                            val baseX = ((lonDouble - mapMinLon) / (mapMaxLon - mapMinLon)).toFloat().coerceIn(0f, 1f)
                            val baseY = (1.0 - ((latDouble - mapMinLat) / (mapMaxLat - mapMinLat))).toFloat().coerceIn(0f, 1f)

                            CameraUiModel(
                                deviceId = member.name,
                                status = deviceStatus,
                                latestTimestamp = latestFile?.modifiedTimestamp,
                                midnightTimestamp = midnightFile?.modifiedTimestamp,
                                latestDateString = latestFile?.modifiedDate?.formatToReadableDate(),
                                midnightDateString = midnightFile?.modifiedDate?.formatToReadableDate(),
                                imagePrefix = prefix,
                                xPercent = baseX,
                                yPercent = baseY,
                                videoUrl = videoUrl,
                                videoDate = videoDate
                            )
                        }

                        _uiState.value = PreviewsUiState.Success(groupOverlaps(rawModels))
                    }
                } catch (e: Exception) {
                    if (_uiState.value !is PreviewsUiState.Success) {
                        _uiState.value = PreviewsUiState.Error(e.localizedMessage ?: "Network error")
                    }
                }
                delay(30_000)
            }
        }
    }

    private fun groupOverlaps(cameras: List<CameraUiModel>): List<CameraUiModel> {
        val threshold = 0.005f
        val result = cameras.toMutableList()
        val visited = BooleanArray(cameras.size)

        for (i in cameras.indices) {
            if (visited[i]) continue
            val cluster = mutableListOf(i)
            visited[i] = true
            for (j in i + 1 until cameras.size) {
                if (visited[j]) continue
                val dx = cameras[i].xPercent - cameras[j].xPercent
                val dy = cameras[i].yPercent - cameras[j].yPercent
                if (dx * dx + dy * dy < threshold * threshold) {
                    cluster.add(j)
                    visited[j] = true
                }
            }
            cluster.forEachIndexed { index, camIndex ->
                result[camIndex] = result[camIndex].copy(clusterIndex = index, clusterCount = cluster.size)
            }
        }
        return result
    }

    private fun String.formatToReadableDate(): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            val date = parser.parse(this.substringBeforeLast("."))
            date?.let { formatter.format(it) } ?: this
        } catch (e: Exception) { this }
    }
}

// ============================================================================
// 3. UI ENUMS & STATE
// ============================================================================

enum class ViewMode { MAP, GRID, VIDEO_LIST }

// ============================================================================
// 4. MAIN SCREEN
// ============================================================================

@Composable
fun CameraPreviewsScreen(viewModel: CameraPreviewsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedCamera by remember { mutableStateOf<CameraUiModel?>(null) }
    var viewMode by remember { mutableStateOf(ViewMode.MAP) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        // View Switcher Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            SegmentedButtonRow(
                currentMode = viewMode,
                onModeSelected = { viewMode = it }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (val state = uiState) {
                is PreviewsUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is PreviewsUiState.Error -> Text("Error: ${state.message}", color = Color.Red, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                is PreviewsUiState.Success -> {
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp)) {
                        when (viewMode) {
                            ViewMode.MAP -> InteractiveMapView(cameras = state.cameras, onCameraClick = { selectedCamera = it })
                            ViewMode.GRID -> GridLayout(cameras = state.cameras, onCameraClick = { selectedCamera = it })
                            ViewMode.VIDEO_LIST -> VideoListLayout(cameras = state.cameras, onCameraClick = { selectedCamera = it })
                        }
                    }
                }
            }
        }
    }

    selectedCamera?.let { camera ->
        PolaroidDialog(camera = camera, onDismiss = { selectedCamera = null })
    }
}

// ============================================================================
// 5. MAP LAYOUT (WITH ISOLATION-SORTED CLUSTERING)
// ============================================================================

@Composable
fun InteractiveMapView(cameras: List<CameraUiModel>, onCameraClick: (CameraUiModel) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var hasAutoFit by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .clipToBounds()
            .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .background(Color(0xFF0A192F)),
        contentAlignment = Alignment.Center
    ) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val mapAspect = 4f / 5f
        val baseMapW = maxOf(cw, ch * mapAspect)
        val baseMapH = baseMapW / mapAspect

        LaunchedEffect(cameras, cw, ch) {
            if (!hasAutoFit && cameras.isNotEmpty() && cw > 0) {
                val minX = cameras.minOf { it.xPercent }; val maxX = cameras.maxOf { it.xPercent }
                val minY = cameras.minOf { it.yPercent }; val maxY = cameras.maxOf { it.yPercent }
                val targetScale = (cw / ((maxX - minX) * baseMapW * 1.5f)).coerceIn(1f, 4f)
                scale = targetScale
                offset = Offset((0.5f - (minX + maxX)/2f) * baseMapW * scale, (0.5f - (minY + maxY)/2f) * baseMapH * scale)
                hasAutoFit = true
            }
        }

        Box(modifier = Modifier.fillMaxSize().pointerInput(cw, ch) {
            detectTransformGestures { _, pan, zoom, _ ->
                val newScale = (scale * zoom).coerceIn(1f, 5f)
                val maxX = maxOf(0f, (baseMapW * newScale - cw) / 2f)
                val maxY = maxOf(0f, (baseMapH * newScale - ch) / 2f)
                scale = newScale
                offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
            }
        }, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.requiredSize(with(LocalDensity.current) { baseMapW.toDp() }, with(LocalDensity.current) { baseMapH.toDp() })
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
            ) {
                Image(painter = painterResource(id = R.drawable.nl_map_bg), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())

                cameras.forEach { camera ->
                    val scatterRadius = with(LocalDensity.current) { 48.dp.toPx() }
                    val zoomFactor = (1f - (scale - 1f) / 4f).coerceIn(0f, 1f)

                    val angle = (2 * Math.PI * camera.clusterIndex) / camera.clusterCount
                    val clusterOffsetX = (scatterRadius * zoomFactor * cos(angle)).toFloat()
                    val clusterOffsetY = (scatterRadius * zoomFactor * sin(angle)).toFloat()

                    val absoluteX = (camera.xPercent * baseMapW) + (clusterOffsetX / scale)
                    val absoluteY = (camera.yPercent * baseMapH) + (clusterOffsetY / scale)

                    Box(
                        modifier = Modifier.offset { IntOffset(absoluteX.toInt(), absoluteY.toInt()) }
                            .graphicsLayer { translationX = -24.dp.toPx(); translationY = -24.dp.toPx(); scaleX = 1f/scale; scaleY = 1f/scale }
                    ) {
                        MapThumbnailMarker(camera = camera, onClick = { onCameraClick(camera) })
                    }
                }
            }
        }
    }
}

@Composable
fun MapThumbnailMarker(camera: CameraUiModel, onClick: () -> Unit) {
    val baseUrl = "http://gifted-kirch.apsys.nl:8000/api/v1/previews/media"
    val thumbUrl = camera.latestTimestamp?.let { "$baseUrl/${camera.imagePrefix}.latest.jpg?ts=$it" }
    val isWorking = camera.status.equals("complete", ignoreCase = true)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                .border(2.dp, if (isWorking) Color.Green else Color(0xFFFF9800), RoundedCornerShape(8.dp))
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            if (thumbUrl != null) {
                AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(thumbUrl).crossfade(true).build(),
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.NoPhotography, contentDescription = null, tint = Color.Red, modifier = Modifier.size(24.dp))
            }

            // Video indicator
            if (camera.videoUrl != null) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = "Has Video",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                        .size(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(2.dp)
                )
            }
        }
        Text(text = camera.deviceId.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp).background(Color(0xAA000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
    }
}

// ============================================================================
// 6. GRID LAYOUT
// ============================================================================

@Composable
fun GridLayout(cameras: List<CameraUiModel>, onCameraClick: (CameraUiModel) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(cameras) { camera ->
            val baseUrl = "http://gifted-kirch.apsys.nl:8000/api/v1/previews/media"
            val thumbUrl = camera.latestTimestamp?.let { "$baseUrl/${camera.imagePrefix}.latest.jpg?ts=$it" }
            val isWorking = camera.status.equals("complete", ignoreCase = true)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clickable { onCameraClick(camera) },
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (thumbUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(thumbUrl).crossfade(true).build(),
                            contentDescription = camera.deviceId,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.NoPhotography, contentDescription = null, tint = Color.Red, modifier = Modifier.size(40.dp))
                        }
                    }

                    // Status indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isWorking) Color.Green else Color(0xFFFF9800))
                    )
                    // Video indicator
                    if (camera.videoUrl != null) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Has Video",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .padding(4.dp)
                        )
                    }
                    // Name label
                    Text(
                        text = camera.deviceId.uppercase(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ============================================================================
// 7. VIDEO LIST LAYOUT
// ============================================================================

@Composable
fun VideoListLayout(cameras: List<CameraUiModel>, onCameraClick: (CameraUiModel) -> Unit) {
    val camerasWithVideos = cameras.filter { it.videoUrl != null }

    if (camerasWithVideos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No videos currently available on the network.", color = Color.Gray)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(camerasWithVideos) { camera ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onCameraClick(camera) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.Cyan)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(camera.deviceId.uppercase(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Night Date: ${camera.videoDate ?: "Unknown"}", fontSize = 14.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 8. POLAROID / MEDIA DIALOG (IMAGE + INLINE EXOPLAYER)
// ============================================================================

@Composable
fun PolaroidDialog(camera: CameraUiModel, onDismiss: () -> Unit) {
    var showMidnight by remember { mutableStateOf(false) }
    var isVideoMode by remember { mutableStateOf(false) }
    var polaroidScale by remember { mutableFloatStateOf(1f) }
    var polaroidOffset by remember { mutableStateOf(Offset.Zero) }

    val baseUrl = "http://gifted-kirch.apsys.nl:8000/api/v1/previews/media"
    val currentTimestamp = if (showMidnight) camera.midnightTimestamp else camera.latestTimestamp
    val currentUrl = if (currentTimestamp != null) {
        if (showMidnight) "$baseUrl/${camera.imagePrefix}.midnight.jpg?ts=$currentTimestamp"
        else "$baseUrl/${camera.imagePrefix}.latest.jpg?ts=$currentTimestamp"
    } else null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth(0.95f).padding(16.dp).shadow(16.dp, RoundedCornerShape(4.dp))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f / 1f)
                        .clipToBounds()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val boxW = constraints.maxWidth.toFloat()
                    val boxH = constraints.maxHeight.toFloat()

                    Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (polaroidScale * zoom).coerceIn(1f, 5f)
                            val maxPanX = (boxW * (newScale - 1f)) / 2f
                            val maxPanY = (boxH * (newScale - 1f)) / 2f

                            polaroidScale = newScale
                            polaroidOffset = Offset(
                                (polaroidOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                (polaroidOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                            )
                        }
                    }, contentAlignment = Alignment.Center) {
                        if (isVideoMode && camera.videoUrl != null) {
                            ExoPlayerView(
                                videoUrl = camera.videoUrl,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = polaroidScale
                                        scaleY = polaroidScale
                                        translationX = polaroidOffset.x
                                        translationY = polaroidOffset.y
                                    }
                            )
                        } else if (currentUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(currentUrl).crossfade(true).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    scaleX = polaroidScale
                                    scaleY = polaroidScale
                                    translationX = polaroidOffset.x
                                    translationY = polaroidOffset.y
                                }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.NoPhotography,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // SINGLE COLUMN CAPTION AREA
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = camera.deviceId.uppercase(),
                        fontFamily = BinkFamily,
                        fontSize = 24.sp,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (isVideoMode) "Video Night: ${camera.videoDate ?: "Unknown"}"
                        else if (showMidnight) camera.midnightDateString ?: "No Data"
                        else camera.latestDateString ?: "No Data",
                        color = Color.DarkGray,
                        fontFamily = handWriting,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Media Controls Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (camera.videoUrl != null) {
                        Button(
                            onClick = {
                                isVideoMode = !isVideoMode
                                polaroidScale = 1f
                                polaroidOffset = Offset.Zero
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isVideoMode) Color.Cyan else Color(0xFF6200EA),
                                contentColor = if (isVideoMode) Color.Black else Color.White
                            )
                        ) {
                            Icon(if (isVideoMode) Icons.Default.Image else Icons.Default.Movie, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isVideoMode) "Image" else "Video")
                        }
                    } else {
                        Spacer(Modifier.width(8.dp)) // Maintain alignment if no video
                    }

                    if (!isVideoMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Midnight", fontSize = 12.sp, color = Color.Gray)
                            Switch(
                                checked = !showMidnight,
                                onCheckedChange = { showMidnight = !it },
                                modifier = Modifier.padding(horizontal = 8.dp).graphicsLayer {
                                    scaleX = 0.8f
                                    scaleY = 0.8f
                                }
                            )
                            Text("Latest", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ExoPlayerView(videoUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl.toUri()))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true // Keeps default play/pause UI
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier.background(Color.Black)
    )
}

// ============================================================================
// 9. HELPER: SEGMENTED BUTTON
// ============================================================================

@Composable
fun SegmentedButtonRow(currentMode: ViewMode, onModeSelected: (ViewMode) -> Unit) {
    Row(
        modifier = Modifier
            .background(Color.DarkGray, RoundedCornerShape(24.dp))
            .padding(4.dp)
    ) {
        Segment(Icons.Default.Map, currentMode == ViewMode.MAP) { onModeSelected(ViewMode.MAP) }
        Segment(Icons.Default.GridView, currentMode == ViewMode.GRID) { onModeSelected(ViewMode.GRID) }
        Segment(Icons.Default.VideoLibrary, currentMode == ViewMode.VIDEO_LIST) { onModeSelected(ViewMode.VIDEO_LIST) }
    }
}

@Composable
fun Segment(icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) Color.White else Color.LightGray
        )
    }
}