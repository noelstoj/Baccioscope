package com.aamon.baccioscope.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.annotations.SerializedName
import com.aamon.baccioscope.R
import com.aamon.baccioscope.ui.theme.BinkFamily
import com.aamon.baccioscope.ui.theme.handWriting
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
    val clusterCount: Int = 1
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

                        val rawModels = members.map { member ->
                            val prefix = member.aka ?: member.name
                            val nomarchDevice = nomarchResponse.devices.find { it.name == member.name }
                            val deviceStatus = nomarchDevice?.piToServerStatus ?: "Unknown"

                            val latestFile = previews.files.find { it.filename == "$prefix.latest.jpg" }
                            val midnightFile = previews.files.find { it.filename == "$prefix.midnight.jpg" }

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
                                yPercent = baseY
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
// 3. COMPOSE UI
// ============================================================================

@Composable
fun CameraPreviewsScreen(viewModel: CameraPreviewsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedCamera by remember { mutableStateOf<CameraUiModel?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is PreviewsUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            is PreviewsUiState.Error -> Text("Error: ${state.message}", color = Color.Red, modifier = Modifier.align(Alignment.Center).padding(24.dp))
            is PreviewsUiState.Success -> {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 24.dp)) {
                    InteractiveMapView(cameras = state.cameras, onCameraClick = { selectedCamera = it })
                }
            }
        }
    }

    selectedCamera?.let { camera ->
        PolaroidDialog(camera = camera, onDismiss = { selectedCamera = null })
    }
}

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
        }
        Text(text = camera.deviceId.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp).background(Color(0xAA000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
    }
}

@Composable
fun PolaroidDialog(camera: CameraUiModel, onDismiss: () -> Unit) {
    var showMidnight by remember { mutableStateOf(false) }
    var polaroidScale by remember { mutableFloatStateOf(1f) }
    var polaroidOffset by remember { mutableStateOf(Offset.Zero) }

    val baseUrl = "http://gifted-kirch.apsys.nl:8000/api/v1/previews/media"
    val currentTimestamp = if (showMidnight) camera.midnightTimestamp else camera.latestTimestamp
    val currentUrl = if (currentTimestamp != null) {
        if (showMidnight) "$baseUrl/${camera.imagePrefix}.midnight.jpg?ts=$currentTimestamp"
        else "$baseUrl/${camera.imagePrefix}.latest.jpg?ts=$currentTimestamp"
    } else null

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(4.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(16.dp).shadow(16.dp, RoundedCornerShape(4.dp))) {
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

                            // Dynamic clamping based on new aspect ratio
                            val maxX = (boxW * (newScale - 1f))
                            val maxY = (boxH * (newScale - 1f))

                            polaroidScale = newScale
                            polaroidOffset = Offset(
                                (polaroidOffset.x + pan.x).coerceIn(-maxX, maxX),
                                (polaroidOffset.y + pan.y).coerceIn(-maxY, maxY)
                            )
                        }
                    }, contentAlignment = Alignment.Center) {
                        if (currentUrl != null) {
                            AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(currentUrl).crossfade(true).build(),
                                contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    scaleX = polaroidScale
                                    scaleY = polaroidScale
                                    translationX = polaroidOffset.x
                                    translationY = polaroidOffset.y
                                }
                            )
                        } else {
                            // FALLBACK ICON FOR MISSING IMAGE
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
                        text = if (showMidnight) camera.midnightDateString
                            ?: "No Data" else camera.latestDateString ?: "No Data",
                        color = Color.DarkGray,
                        fontFamily = handWriting,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(0.dp))
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 0.dp)
                    ) {
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