package com.aamon.baccioscope.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Tsunami
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aamon.baccioscope.ui.theme.deviceFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

// ============================================================================
// 1. DATA MODELS & NETWORK LAYER
// ============================================================================

data class ConnectivityTunnel(
    val name: String,
    val port: Int,
    val status: String,
    val connections: Int,
    val live_rtt: String,
    val max_rtt: String,
    val total_mb: String,
    val health: String
)

interface ConnectivityApiService {
    @GET("status")
    suspend fun getTunnels(): List<ConnectivityTunnel>
}

object ConnectivityRetrofit {
    val api: ConnectivityApiService by lazy {
        Retrofit.Builder()
            .baseUrl("http://85.215.77.88:8000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ConnectivityApiService::class.java)
    }
}

// ============================================================================
// 2. VIEW MODEL
// ============================================================================

class ConnectivityViewModel : ViewModel() {
    private val _tunnels = MutableStateFlow<List<ConnectivityTunnel>>(emptyList())
    val tunnels: StateFlow<List<ConnectivityTunnel>> = _tunnels

    private val _lastUpdated = MutableStateFlow("Fetching connections...")
    val lastUpdated: StateFlow<String> = _lastUpdated

    init {
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                try {
                    val data = ConnectivityRetrofit.api.getTunnels()
                    _tunnels.value = data
                    _lastUpdated.value = "Updated within last 10 seconds"
                } catch (e: Exception) {
                    _lastUpdated.value = "Error: ${e.message}"
                }
                delay(10000) // Poll every 10 seconds silently
            }
        }
    }
}

// ============================================================================
// 3. COMPOSE UI
// ============================================================================

@Composable
fun ConnectivityScreen(viewModel: ConnectivityViewModel = viewModel()) {
    val tunnels by viewModel.tunnels.collectAsState()
    val lastUpdated by viewModel.lastUpdated.collectAsState()
    var selectedTunnel by remember { mutableStateOf<ConnectivityTunnel?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = lastUpdated,
                fontSize = 12.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tunnels) { tunnel ->
                    TunnelCard(tunnel = tunnel, onClick = { selectedTunnel = tunnel })
                }
            }
        }

        // Detail Dialog (Popup)
        selectedTunnel?.let { tunnel ->
            TunnelDetailDialog(
                tunnel = tunnel,
                onDismiss = { selectedTunnel = null }
            )
        }
    }
}

@Composable
fun TunnelCard(tunnel: ConnectivityTunnel, onClick: () -> Unit) {
    val isOnline = tunnel.status == "ONLINE"
    val hasConnections = tunnel.connections > 0

    val (statusIcon, statusColor) = when {
        !isOnline -> Pair(Icons.Default.ReportProblem, Color.Red)
        hasConnections -> Pair(Icons.Default.Autorenew, Color.Green)
        else -> Pair(Icons.Default.CheckCircleOutline, Color.Cyan)
    }

    val isTransferring = tunnel.live_rtt != "Idle"
    val totalMbValue = tunnel.total_mb.split(" ").firstOrNull()?.toDoubleOrNull() ?: 0.0
    val hasHighDataUsage = totalMbValue > 1500.0

    Card(
        elevation = CardDefaults.cardElevation(6.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = tunnel.name.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontFamily = deviceFont,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Port: ${tunnel.port} • ${tunnel.health}",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (isTransferring) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = "Active Transfer",
                    tint = Color.Yellow,
                    modifier = Modifier.size(28.dp)
                )
            } else if (hasHighDataUsage) {
                Icon(
                    imageVector = Icons.Default.Tsunami,
                    contentDescription = "Large Transfer Done",
                    tint = Color(0xFFBF00FF),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun TunnelDetailDialog(tunnel: ConnectivityTunnel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text(tunnel.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Status Report", fontSize = 12.sp, color = Color.Gray)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                DetailRow(
                    "Status",
                    tunnel.status,
                    if (tunnel.status == "ONLINE") Color(0xFF00E676) else Color.Red
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.DarkGray)

                Text("Tunnel Usage", fontSize = 14.sp, color = Color.Cyan, fontWeight = FontWeight.Bold)

                DetailRow(
                    "Active Connections",
                    tunnel.connections.toString(),
                    color = if (tunnel.connections == 0) Color(0xFF4E50F6) else Color.Green
                )

                DetailRow("Health Grade", tunnel.health,
                    when(tunnel.health) {
                        "Excellent" -> Color(0xFF00C853)
                        "Fair" -> Color(0xFFFFAB00)
                        else -> Color(0xFFD50000)
                    }
                )
                DetailRow("Current Latency", tunnel.live_rtt, color = Color.LightGray)

                Spacer(modifier = Modifier.height(16.dp))

                Text("Data Volume since 00:00 UTC", fontSize = 14.sp, color = Color.Cyan, fontWeight = FontWeight.Bold)

                val maxLatency = tunnel.max_rtt.split(" ").firstOrNull()?.toDoubleOrNull() ?: 0.0
                val isHighLatency = maxLatency > 100
                DetailRow("Peak Latency", tunnel.max_rtt, if(isHighLatency) Color(0xFFFF5722) else Color.LightGray)
                DetailRow("Largest Transfer", tunnel.total_mb, color = Color.LightGray)

            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Bold, color = color)
    }
}