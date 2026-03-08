package com.aamon.baccioscope.ui.screens

import androidx.compose.foundation.Image // <-- ADD THIS IMPORT
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aamon.baccioscope.R // Ensure this matches your package name for R.drawable
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
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.*
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
    @SerializedName("download_url") val downloadUrl: String
)

// Fleet member data from the provided JSON
data class FleetMember(
    @SerializedName("Name") val name: String,
    @SerializedName("AKA") val aka: String?,
    @SerializedName("Lat") val lat: String, // Arrives as a string
    @SerializedName("Lon") val lon: String  // Arrives as a string
)

data class NomarchDeviceStatus(
    @SerializedName("device_id") val deviceId: String,
    val status: String
)

interface BaccioscopeApi {
    @GET("api/v1/previews/status")
    suspend fun getPreviewsStatus(): PreviewsStatusResponse

    @GET("api/v1/nomarch/status")
    suspend fun getNomarchStatus(): List<NomarchDeviceStatus>

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
// 2. VIEW MODEL (State & Chaos Management)
// ============================================================================

data class CameraUiModel(
    val deviceId: String,
    val status: String,
    val latestTimestamp: Double?,
    val midnightTimestamp: Double?,
    val latestDateString: String?,
    val midnightDateString: String?,
    val imagePrefix: String,
    val xPercent: Float, // Calculated relative position on map (0.0 to 1.0)
    val yPercent: Float
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

    // Bounding Box for NE Netherlands / Wadden (Adjust these to fit your map image exactly)
    private val mapMinLat = 52.3402
    private val mapMaxLat = 53.7020
    private val mapMinLon = 5.1717
    private val mapMaxLon = 7.1472

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
                        val nomarch = nomarchDeferred.await()
                        val previews = previewsDeferred.await()

                        // 1. Create base models and map Lat/Lon to X/Y percentages
                        val rawModels = members.map { member ->
                            val prefix = member.aka ?: member.name
                            val deviceStatus = nomarch.find { it.deviceId == member.name }?.status ?: "Unknown"

                            val latestFile = previews.files.find { it.filename == "$prefix.latest.jpg" }
                            val midnightFile = previews.files.find { it.filename == "$prefix.midnight.jpg" }

                            val latDouble = member.lat.toDoubleOrNull() ?: 0.0
                            val lonDouble = member.lon.toDoubleOrNull() ?: 0.0

                            // Convert GPS to percentages (0f to 1f) for the map
                            val baseX = ((lonDouble - mapMinLon) / (mapMaxLon - mapMinLon)).toFloat().coerceIn(0f, 1f)
                            // Invert Y because latitude goes up, but screen Y goes down
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
                                yPercent = baseY
                            )
                        }

                        // 2. Solve overlaps (Cameras sharing the same coordinates)
                        val scatteredModels = resolveOverlaps(rawModels)
                        _uiState.value = PreviewsUiState.Success(scatteredModels)
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

    // A simple scatter algorithm to prevent perfect overlaps
    private fun resolveOverlaps(cameras: List<CameraUiModel>): List<CameraUiModel> {
        val threshold = 0.02f // If points are within 2% of screen width/height
        val grouped = cameras.groupBy {
            // Group points by a rough grid to find neighbors
            Pair((it.xPercent / threshold).toInt(), (it.yPercent / threshold).toInt())
        }

        val result = mutableListOf<CameraUiModel>()
        for ((_, group) in grouped) {
            if (group.size == 1) {
                result.add(group.first())
            } else {
                // If there are multiple in the same area, push them into a circle
                val centerX = group.map { it.xPercent }.average().toFloat()
                val centerY = group.map { it.yPercent }.average().toFloat()
                val radius = 0.04f // 4% map distance offset

                group.forEachIndexed { index, cam ->
                    val angle = (2 * Math.PI * index) / group.size
                    val offsetX = radius * cos(angle).toFloat()
                    val offsetY = radius * sin(angle).toFloat()

                    result.add(cam.copy(
                        xPercent = (centerX + offsetX).coerceIn(0.02f, 0.98f),
                        yPercent = (centerY + offsetY).coerceIn(0.02f, 0.98f)
                    ))
                }
            }
        }
        return result
    }

    // Helper to format ISO dates
    private fun String.formatToReadableDate(): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            val date = parser.parse(this.substringBeforeLast(".")) // Strip milliseconds if present
            date?.let { formatter.format(it) } ?: this
        } catch (e: Exception) {
            this
        }
    }
}

// ============================================================================
// 3. COMPOSE UI
// ============================================================================

@Composable
fun CameraPreviewsScreen(viewModel: CameraPreviewsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedCamera by remember { mutableStateOf<CameraUiModel?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is PreviewsUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is PreviewsUiState.Error -> {
                Text("Error: ${state.message}", color = Color.Red, modifier = Modifier.align(Alignment.Center))
            }
            is PreviewsUiState.Success -> {
                InteractiveMapView(
                    cameras = state.cameras,
                    onCameraClick = { selectedCamera = it }
                )
            }
        }
    }

    // Show the Polaroid Popup if a camera is selected
    selectedCamera?.let { camera ->
        PolaroidDialog(
            camera = camera,
            onDismiss = { selectedCamera = null }
        )
    }
}

@Composable
fun InteractiveMapView(cameras: List<CameraUiModel>, onCameraClick: (CameraUiModel) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A192F)) // Dark sea/sky background
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    // Simple panning (can be improved with strict bounds later)
                    offset += pan
                }
            }
    ) {
        val mapWidth = constraints.maxWidth.toFloat()
        val mapHeight = constraints.maxHeight.toFloat()

        // Map Layer (Graphics layer applies zoom and pan)
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Use the lambda version of graphicsLayer for better performance with state variables
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            // 1. The Map Image
            // TODO: Make sure you add 'nl_map_bg' to your res/drawable folder!
            // If you don't have it right now, comment this Image out, and the dark blue Box will act as the sea.
            Image(
                painter = painterResource(id = R.drawable.nl_map_bg), // PLACEHOLDER! Replace with R.drawable.nl_map_bg
                contentDescription = "Map of Netherlands",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.3f // Dim the map so markers show clearly
            )

            // 2. The Markers Layer
            cameras.forEach { camera ->
                // Calculate absolute X,Y within the map Box
                val absoluteX = camera.xPercent * mapWidth
                val absoluteY = camera.yPercent * mapHeight

                Box(
                    modifier = Modifier
                        .offset { IntOffset(absoluteX.toInt(), absoluteY.toInt()) }
                        // The tricky part: we need to center the marker over the exact coordinate.
                        // We also apply an INVERSE scale so the markers don't get massive when you zoom in on the map!
                        // Using the lambda version here gives us access to `.toPx()` automatically!
                        .graphicsLayer {
                            translationX = -24.dp.toPx() // offset by half the marker width (assume 48dp total width)
                            translationY = -24.dp.toPx()
                            scaleX = 1f / scale
                            scaleY = 1f / scale
                        }
                ) {
                    MapThumbnailMarker(camera = camera, onClick = { onCameraClick(camera) })
                }
            }
        }
    }
}

@Composable
fun MapThumbnailMarker(camera: CameraUiModel, onClick: () -> Unit) {
    val baseUrl = "http://gifted-kirch.apsys.nl:8000/api/v1/previews/media"
    val thumbUrl = camera.latestTimestamp?.let { "$baseUrl/${camera.imagePrefix}.latest.jpg?ts=$it" }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        // Thumbnail Box
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(2.dp, if (camera.status.equals("online", true) || camera.status.equals("ok", true)) Color.Green else Color.Red, RoundedCornerShape(8.dp))
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            if (thumbUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(thumbUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Missing image: Grey square with red No Photography icon
                Icon(
                    imageVector = Icons.Default.NoPhotography,
                    contentDescription = "No Image",
                    tint = Color.Red,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Device Name underneath
        Text(
            text = camera.deviceId,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Color(0xAA000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun PolaroidDialog(camera: CameraUiModel, onDismiss: () -> Unit) {
    var showMidnight by remember { mutableStateOf(false) }

    val baseUrl = "http://gifted-kirch.apsys.nl:8000/api/v1/previews/media"
    val latestUrl =
        camera.latestTimestamp?.let { "$baseUrl/${camera.imagePrefix}.latest.jpg?ts=$it" }
    val midnightUrl =
        camera.midnightTimestamp?.let { "$baseUrl/${camera.imagePrefix}.midnight.jpg?ts=$it" }

    val currentUrl = if (showMidnight) midnightUrl else latestUrl
    val currentDateStr = if (showMidnight) camera.midnightDateString else camera.latestDateString

    Dialog(onDismissRequest = onDismiss) {
        // Polaroid Frame
        Card(
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(16.dp, RoundedCornerShape(4.dp))
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Image Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(currentUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Large Camera View",
                            contentScale = ContentScale.Fit, // Fit to preserve aspect ratio in polaroid
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.NoPhotography,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No Image", color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Caption Area (Black text because Polaroid border is white)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = camera.deviceId,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.Black
                        )
                        Text(
                            text = currentDateStr ?: "Unknown Date",
                            color = Color.DarkGray,
                            fontSize = 14.sp
                        )
                    }

                    // Toggle Button
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Midnight", fontSize = 12.sp, color = Color.DarkGray)
                        Switch(
                            checked = !showMidnight,
                            onCheckedChange = { showMidnight = !it },
                            modifier = Modifier.padding(horizontal = 8.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.LightGray
                            )
                        )
                        Text(text = "Latest", fontSize = 12.sp, color = Color.DarkGray)
                    }
                }
            }
        }
    }
}