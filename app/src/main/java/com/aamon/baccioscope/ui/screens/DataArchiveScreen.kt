package com.aamon.baccioscope.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ============================================================================
// 1. DATA MODELS
// ============================================================================

data class ArchiveDeviceStatus(
    val name: String,
    val piCount: Int,
    val serverCount: Int,
    val archiveCount: Int,
    val piToServerStatus: String,
    val serverToArchiveStatus: String,
    val hasPreviousResiduals: Boolean,
    val isLowCountAnomaly: Boolean
)

// ============================================================================
// 2. VIEW MODEL
// ============================================================================

class DataArchiveViewModel : ViewModel() {
    private val _targetDate = MutableStateFlow(
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    )
    val targetDate: StateFlow<Calendar> = _targetDate

    private val _devices = MutableStateFlow<List<ArchiveDeviceStatus>>(emptyList())
    val devices: StateFlow<List<ArchiveDeviceStatus>> = _devices

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                fetchData()
                delay(15000) // Poll every 15 seconds silently
            }
        }
    }

    fun setDate(millis: Long) {
        val newDate = Calendar.getInstance().apply { timeInMillis = millis }
        _targetDate.value = newDate
        fetchData()
    }

    fun fetchData() {
        if (_isLoading.value) return
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val serverUrl = "http://gifted-kirch.apsys.nl:8000"
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateStr = sdf.format(_targetDate.value.time)
                val url = URL("$serverUrl/api/v1/nomarch/status?target_date=$dateStr")

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 45000
                connection.readTimeout = 45000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(responseStr)
                    val devicesArray = jsonObject.getJSONArray("devices")

                    val fetchedDevices = mutableListOf<ArchiveDeviceStatus>()
                    for (i in 0 until devicesArray.length()) {
                        val obj = devicesArray.getJSONObject(i)
                        fetchedDevices.add(
                            ArchiveDeviceStatus(
                                name = obj.getString("name"),
                                piCount = obj.getInt("pi_count"),
                                serverCount = obj.getInt("server_count"),
                                archiveCount = obj.getInt("archive_count"),
                                piToServerStatus = obj.getString("pi_to_server_status"),
                                serverToArchiveStatus = obj.getString("server_to_archive_status"),
                                hasPreviousResiduals = obj.getBoolean("has_previous_residuals"),
                                isLowCountAnomaly = obj.getBoolean("is_low_count_anomaly")
                            )
                        )
                    }

                    withContext(Dispatchers.Main) {
                        _devices.value = fetchedDevices
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }
}

// ============================================================================
// 3. COMPOSE UI
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataArchiveScreen(viewModel: DataArchiveViewModel = viewModel()) {
    val date by viewModel.targetDate.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                onClick = { showDatePicker = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Date: ${sdf.format(date.time)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                }
            }

            if (isLoading && devices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    items(devices) { device ->
                        ArchiveDeviceCard(device)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = date.timeInMillis)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            viewModel.setDate(it)
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
fun ArchiveDeviceCard(device: ArchiveDeviceStatus) {
    val borderColor = if (device.isLowCountAnomaly) Color(0xFFE57373) else Color.Transparent
    val borderWidth = if (device.isLowCountAnomaly) 2.dp else 0.dp
    val hasWarning = device.piToServerStatus == "warning" || device.serverToArchiveStatus == "warning"

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.name.uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (device.hasPreviousResiduals) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Residuals", tint = Color(0xFFFFB74D))
                        Spacer(Modifier.width(4.dp))
                        Text("Old Residuals", color = Color(0xFFFFB74D), fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                val totalFiles = maxOf(0, device.piCount) + maxOf(0, device.serverCount) + maxOf(0, device.archiveCount)

                DeviceNode(
                    icon = Icons.Default.DeveloperBoard,
                    iconTint = Color(0xFF6AC416),
                    count = device.piCount,
                    totalFiles = totalFiles,
                    hasWarning = hasWarning
                )

                Box(modifier = Modifier.padding(top = 30.dp)) {
                    PipelineArrow(device.piToServerStatus)
                }

                DeviceNode(
                    icon = Icons.Default.DeviceHub,
                    iconTint = Color(0xFFFFA000),
                    count = device.serverCount,
                    totalFiles = totalFiles,
                    hasWarning = hasWarning
                )

                Box(modifier = Modifier.padding(top = 30.dp)) {
                    PipelineArrow(device.serverToArchiveStatus)
                }

                DeviceNode(
                    icon = Icons.Default.AssuredWorkload,
                    iconTint = Color(0xFFDC002D),
                    count = device.archiveCount,
                    totalFiles = totalFiles,
                    hasWarning = hasWarning
                )
            }
        }
    }
}

@Composable
fun DeviceNode(icon: ImageVector, iconTint: Color, count: Int, totalFiles: Int, hasWarning: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))

        CountBox(count = count, total = totalFiles, hasWarning = hasWarning)
    }
}

@Composable
fun CountBox(count: Int, total: Int, hasWarning: Boolean) {
    val isAllZero = total == 0
    val isNa = count == -1

    val baseTeal = Color(0xFF26A69A)
    val baseOrange = Color(0xFFFF5722)
    val baseColor = if (hasWarning) baseOrange else baseTeal

    val bgColor = if (isNa) {
        baseOrange.copy(alpha = 0.8f)
    } else if (isAllZero) {
        Color(0xFF78909C)
    } else {
        val pct = if (total > 0) count.toFloat() / total.toFloat() else 0f
        val alphaScale = 0.5f + (pct * 0.5f)
        baseColor.copy(alpha = alphaScale)
    }

    Box(
        modifier = Modifier
            .size(width = 54.dp, height = 32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (isNa) {
            Icon(
                imageVector = Icons.Default.MobiledataOff,
                contentDescription = "N/A",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = count.toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun PipelineArrow(status: String) {
    val icon: ImageVector
    val color: Color

    when (status) {
        "running" -> {
            icon = Icons.Default.Sync
            color = Color(0xFF64B5F6)
        }
        "warning" -> {
            icon = Icons.Default.ErrorOutline
            color = Color(0xFFE57373)
        }
        "complete" -> {
            icon = Icons.Default.CheckCircle
            color = Color(0xFF81C784)
        }
        "unknown" -> {
            icon = Icons.AutoMirrored.Filled.HelpOutline
            color = Color.Gray
        }
        else -> {
            icon = Icons.AutoMirrored.Filled.ArrowForward
            color = Color(0xFFB0BEC5)
        }
    }

    Icon(
        imageVector = icon,
        contentDescription = status,
        tint = color,
        modifier = Modifier.size(28.dp)
    )
}