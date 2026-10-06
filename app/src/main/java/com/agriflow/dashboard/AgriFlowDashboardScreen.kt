package com.agriflow.dashboard

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agriflowapplication.AdminDashboardActivity
import com.example.agriflowapplication.DashboardActivity
import com.example.agriflowapplication.FarmerListActivity
import com.example.agriflowapplication.FirebaseManager
import com.example.agriflowapplication.ProfileActivity

// ---------------------------------------------------------------------------
// Data models
// ---------------------------------------------------------------------------

enum class AlertSeverity { CRITICAL, WARNING }

data class SystemAlert(
    val id: Int,
    val title: String,
    val timeAgo: String,
    val severity: AlertSeverity
)

data class ScheduleEvent(
    val zone: String,
    val time: String,
    val completed: Boolean
)

data class TelemetryState(
    val soilMoisturePercent: Int = 42,
    val waterTankPercent: Int = 0,
    val temperatureCelsius: Float = 27.4f,
    val humidityPercent: Int = 61,
    val lightIntensityPercent: Float = 85.0f,
    val isOnline: Boolean = true
)

// ---------------------------------------------------------------------------
// Color tokens
// ---------------------------------------------------------------------------

data class DashboardColors(
    val primary: Color,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val successContainer: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val accent: Color,
    val border: Color
)

private val FarmerColors = DashboardColors(
    primary = Color(0xFF386A20),
    background = Color(0xFFFCFDF6),
    surface = Color(0xFFEFF2E5),
    surfaceHigh = Color(0xFFE3E6D9),
    onSurface = Color(0xFF1A1C16),
    onSurfaceVariant = Color(0xFF43483D),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    successContainer = Color(0xFFDCF5DC),
    warningContainer = Color(0xFFFFF3D6),
    onWarningContainer = Color(0xFF5C3D00),
    accent = Color(0xFF386A20),
    border = Color(0xFFE3E6D9)
)

private val AdminColors = DashboardColors(
    primary = Color(0xFF4F46E5),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    surfaceHigh = Color(0xFF26364D),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF94A3B8),
    errorContainer = Color(0xFFEF4444).copy(alpha = 0.2f),
    onErrorContainer = Color(0xFFEF4444),
    successContainer = Color(0xFF14B8A6).copy(alpha = 0.2f),
    warningContainer = Color(0xFFF59E0B).copy(alpha = 0.2f),
    onWarningContainer = Color(0xFFF59E0B),
    accent = Color(0xFF22D3EE),
    border = Color(0xFF334155)
)

val LocalDashboardColors = staticCompositionLocalOf { FarmerColors }

// ---------------------------------------------------------------------------
// Screen entry point
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgriFlowDashboardScreen() {
    val context = LocalContext.current
    val mAuth = remember { FirebaseManager.getInstance().auth }
    val db = remember { FirebaseManager.getInstance().db }
    
    // --- Hoisted state -----------------------------------------------------
    var telemetry by remember { mutableStateOf(TelemetryState()) }
    var isWaterPumpOn by remember { mutableStateOf(false) }
    var isFertilizerPumpOn by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf("User") }
    var userRole by remember { mutableStateOf("farmer") }

    val scheduleEvents = remember {
        listOf(
            ScheduleEvent(zone = "Zone 1", time = "08:00 AM", completed = true),
            ScheduleEvent(zone = "Zone 2", time = "12:30 PM", completed = true),
            ScheduleEvent(zone = "Zone 3", time = "04:00 PM", completed = false)
        )
    }

    var alerts by remember {
        mutableStateOf(
            listOf(
                SystemAlert(1, "Water tank is too low", "10 min ago", AlertSeverity.CRITICAL),
                SystemAlert(2, "High light density detected", "42 min ago", AlertSeverity.WARNING)
            )
        )
    }

    // Fetch user data
    LaunchedEffect(Unit) {
        val userId = mAuth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val fullName = document.getString("fullName")
                        if (!fullName.isNullOrEmpty()) {
                            userName = fullName.split(" ")[0]
                        }
                        userRole = document.getString("role") ?: "farmer"
                    }
                }
        }
    }

    // Real-time telemetry listener
    DisposableEffect(Unit) {
        val telemetryRef = db.collection("system_data").document("live_telemetry")
        val registration = telemetryRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                telemetry = telemetry.copy(isOnline = false)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data ?: emptyMap()
                
                fun getSafeInt(field: String): Int? {
                    return (data[field] as? Number)?.toInt()
                }
                fun getSafeFloat(field: String): Float? {
                    return (data[field] as? Number)?.toFloat()
                }

                val moisture = getSafeInt("soilMoisture") ?: 0
                val tank = getSafeInt("waterTankLevel") ?: getSafeInt("water_level") ?: 0
                val temp = getSafeFloat("temperature") ?: 0f
                val hum = getSafeInt("humidity") ?: 0
                
                val light = getSafeFloat("lux") ?: getSafeFloat("lightIntensity") ?: getSafeFloat("light_density") ?: 0f

                val pump1 = snapshot.getBoolean("waterPump") ?: false
                val pump2 = snapshot.getBoolean("fertilizerPump") ?: false

                telemetry = telemetry.copy(
                    soilMoisturePercent = moisture,
                    waterTankPercent = tank,
                    temperatureCelsius = temp,
                    humidityPercent = hum,
                    lightIntensityPercent = light,
                    isOnline = true
                )
                isWaterPumpOn = pump1
                isFertilizerPumpOn = pump2
            }
        }
        onDispose {
            registration.remove()
        }
    }

    val isAdmin = userRole.equals("admin", ignoreCase = true)
    val colorScheme = if (isAdmin) AdminColors else FarmerColors

    CompositionLocalProvider(LocalDashboardColors provides colorScheme) {
        Scaffold(
            containerColor = colorScheme.background,
            topBar = {
                AgriFlowTopBar(
                    userName = userName,
                    isOnline = telemetry.isOnline
                )
            },
            bottomBar = {
                AgriFlowBottomBar(
                    isAdmin = isAdmin,
                    onNavigate = { activityClass ->
                        context.startActivity(Intent(context, activityClass))
                    }
                )
            }
        ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                SectionLabel("Live telemetry")
                Spacer(Modifier.height(10.dp))
                TelemetryGrid(telemetry)
                Spacer(Modifier.height(24.dp))
            }

            item {
                SectionLabel("Irrigation controls")
                Spacer(Modifier.height(10.dp))
                IrrigationControlsCard(
                    isWaterPumpOn = isWaterPumpOn,
                    onWaterPumpToggle = { newValue ->
                        isWaterPumpOn = newValue
                        val stateStr = if (newValue) "ON" else "OFF"
                        val map = mapOf<String, Any>(
                            "waterPump" to newValue,
                            "water_pump" to newValue,
                            "waterPumpState" to stateStr
                        )
                        db.collection("system_data").document("device_control").update(map)
                        db.collection("system_data").document("live_telemetry").update(map)
                    },
                    isFertilizerPumpOn = isFertilizerPumpOn,
                    onFertilizerPumpToggle = { newValue ->
                        isFertilizerPumpOn = newValue
                        val stateStr = if (newValue) "ON" else "OFF"
                        val map = mapOf<String, Any>(
                            "fertilizerPump" to newValue,
                            "fertilizer_pump" to newValue,
                            "fertilizerPumpState" to stateStr
                        )
                        db.collection("system_data").document("device_control").update(map)
                        db.collection("system_data").document("live_telemetry").update(map)
                    }
                )
                Spacer(Modifier.height(24.dp))
            }

            item {
                SectionLabel("Today's schedule")
                Spacer(Modifier.height(10.dp))
                ScheduleCard(events = scheduleEvents)
                Spacer(Modifier.height(24.dp))
            }

            item {
                SectionLabel("Recent alerts")
                Spacer(Modifier.height(10.dp))
            }

            if (alerts.isEmpty()) {
                item { EmptyAlertsCard() }
            } else {
                items(alerts, key = { it.id }) { alert ->
                    AlertCard(
                        alert = alert,
                        onDismiss = { dismissed ->
                            alerts = alerts.filterNot { it.id == dismissed.id }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgriFlowTopBar(userName: String, isOnline: Boolean) {
    val colors = LocalDashboardColors.current
    TopAppBar(
        title = {
            Column {
                Text(
                    text = userName,
                    fontSize = 14.sp,
                    color = colors.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "AgriFlow Dashboard",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = colors.onSurface
                )
            }
        },
        actions = {
            StatusBadge(isOnline = isOnline)
            Spacer(Modifier.width(12.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.background
        )
    )
}

@Composable
private fun StatusBadge(isOnline: Boolean) {
    val colors = LocalDashboardColors.current
    val backgroundColor = if (isOnline) colors.successContainer else colors.errorContainer
    val dotColor = if (isOnline) colors.primary else colors.onErrorContainer
    val label = if (isOnline) "ONLINE" else "OFFLINE"

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isOnline) colors.primary else colors.onErrorContainer
        )
    }
}

// ---------------------------------------------------------------------------
// Bottom bar
// ---------------------------------------------------------------------------

@Composable
private fun AgriFlowBottomBar(isAdmin: Boolean, onNavigate: (Class<*>) -> Unit) {
    val colors = LocalDashboardColors.current
    NavigationBar(
        containerColor = colors.background,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = true,
            onClick = { /* Already on Home */ },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = colors.primary,
                selectedTextColor = colors.primary,
                indicatorColor = colors.successContainer,
                unselectedIconColor = colors.onSurfaceVariant,
                unselectedTextColor = colors.onSurfaceVariant
            )
        )
        
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Schedule, contentDescription = "Schedule") },
            label = { Text("Schedule") },
            selected = false,
            onClick = { 
                // Using DashboardActivity as the "Schedule/History" view for now
                onNavigate(DashboardActivity::class.java)
            },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = colors.onSurfaceVariant,
                unselectedTextColor = colors.onSurfaceVariant
            )
        )

        if (isAdmin) {
            NavigationBarItem(
                icon = { Icon(Icons.Filled.People, contentDescription = "Farmers") },
                label = { Text("Farmers") },
                selected = false,
                onClick = { onNavigate(FarmerListActivity::class.java) },
                colors = NavigationBarItemDefaults.colors(
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant
                )
            )
        }

        NavigationBarItem(
            icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
            label = { Text("Profile") },
            selected = false,
            onClick = { onNavigate(ProfileActivity::class.java) },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = colors.onSurfaceVariant,
                unselectedTextColor = colors.onSurfaceVariant
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Section label
// ---------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalDashboardColors.current
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = colors.onSurfaceVariant
    )
}

// ---------------------------------------------------------------------------
// Telemetry grid (2x2)
// ---------------------------------------------------------------------------

@Composable
private fun TelemetryGrid(telemetry: TelemetryState) {
    val colors = LocalDashboardColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val moisture = telemetry.soilMoisturePercent
            val (moistureStatus, moistureStatusColor) = when {
                moisture < 25 -> "Critical Dry" to colors.onErrorContainer
                moisture <= 40 -> "Dry/Moderate" to colors.onWarningContainer
                else -> "Normal" to colors.primary
            }
            
            RingMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.WaterDrop,
                label = "Soil moisture",
                value = moisture,
                isWarning = moisture < 25,
                status = moistureStatus,
                statusColor = moistureStatusColor
            )

            val tank = telemetry.waterTankPercent
            val (tankStatus, tankStatusColor) = when {
                tank < 20 -> "Critical Low" to colors.onErrorContainer
                tank <= 40 -> "Low" to colors.onWarningContainer
                else -> "Normal" to colors.primary
            }

            RingMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Water,
                label = "Water tank level",
                value = tank,
                isWarning = tank < 20,
                status = tankStatus,
                statusColor = tankStatusColor
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val temp = telemetry.temperatureCelsius
            val (tempStatus, tempStatusColor) = when {
                temp < 15 || temp > 38 -> "Critical" to colors.onErrorContainer
                temp < 18 || temp > 32 -> "Warning" to colors.onWarningContainer
                else -> "Normal" to colors.primary
            }

            TemperatureMetricCard(
                modifier = Modifier.weight(1f),
                temperatureCelsius = temp,
                status = tempStatus,
                statusColor = tempStatusColor
            )

            val hum = telemetry.humidityPercent
            val (humStatus, humStatusColor) = when {
                hum < 30 || hum > 85 -> "Critical" to colors.onErrorContainer
                hum < 40 || hum > 75 -> "Warning" to colors.onWarningContainer
                else -> "Normal" to colors.primary
            }

            RingMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Cloud,
                label = "Humidity",
                value = hum,
                isWarning = hum < 30 || hum > 85,
                status = humStatus,
                statusColor = humStatusColor
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val light = telemetry.lightIntensityPercent
            val (lightStatus, lightStatusColor) = when {
                light > 95 -> "Extreme" to colors.onErrorContainer
                light > 85 -> "Excessive" to colors.onWarningContainer
                else -> "Normal" to colors.primary
            }

            RingMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.LightMode,
                label = "Light Intensity (%)",
                value = light,
                isWarning = light > 95,
                unit = "%",
                maxValue = 100f,
                precision = 0,
                status = lightStatus,
                statusColor = lightStatusColor
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * A metric card with a small circular progress ring (0-100%), matching the
 * style used for soil moisture, water tank level, and humidity.
 */
@Composable
private fun RingMetricCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: Number,
    isWarning: Boolean,
    unit: String = "%",
    maxValue: Float = 100f,
    precision: Int = 0,
    status: String? = null,
    statusColor: Color = Color.Unspecified
) {
    val colors = LocalDashboardColors.current
    val backgroundColor = if (isWarning) colors.errorContainer else colors.surface
    val contentColor = if (isWarning) colors.onErrorContainer else colors.onSurface
    val ringColor = if (isWarning) colors.onErrorContainer else colors.primary
    val finalStatusColor = if (statusColor == Color.Unspecified) contentColor.copy(alpha = 0.8f) else statusColor

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                CircularProgressRing(
                    progress = value.toFloat() / maxValue,
                    trackColor = colors.surfaceHigh,
                    progressColor = ringColor,
                    size = 34.dp,
                    strokeWidth = 4.dp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(text = label, fontSize = 12.sp, color = contentColor.copy(alpha = 0.75f))
            Row(verticalAlignment = Alignment.Bottom) {
                val formattedValue = if (precision > 0) {
                    "%.${precision}f".format(value.toFloat())
                } else {
                    value.toInt().toString()
                }
                
                Text(
                    text = formattedValue,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = unit,
                    fontSize = 14.sp,
                    color = contentColor.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
                )
            }
            if (status != null) {
                Text(
                    text = status,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = finalStatusColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** Simple circular progress indicator drawn with Canvas — no rotation animation needed. */
@Composable
private fun CircularProgressRing(
    progress: Float,
    trackColor: Color,
    progressColor: Color,
    size: androidx.compose.ui.unit.Dp,
    strokeWidth: androidx.compose.ui.unit.Dp
) {
    Canvas(modifier = Modifier.size(size)) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke,
            size = Size(this.size.width, this.size.height)
        )
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            style = stroke,
            size = Size(this.size.width, this.size.height)
        )
    }
}

/** Temperature card with a horizontal gradient gauge and a position marker. */
@Composable
private fun TemperatureMetricCard(
    modifier: Modifier = Modifier,
    temperatureCelsius: Float,
    minTemp: Float = 10f,
    maxTemp: Float = 42f,
    status: String? = null,
    statusColor: Color = Color.Unspecified
) {
    val colors = LocalDashboardColors.current
    val finalStatusColor = if (statusColor == Color.Unspecified) colors.onSurfaceVariant else statusColor

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = Icons.Filled.Thermostat,
                contentDescription = "Temperature",
                tint = colors.onSurface,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(text = "Temperature", fontSize = 12.sp, color = colors.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%.1f".format(temperatureCelsius),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Text(
                    text = "°C",
                    fontSize = 14.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
                )
            }
            if (status != null) {
                Text(
                    text = status,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = finalStatusColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            TemperatureGaugeBar(
                temperature = temperatureCelsius,
                minTemp = minTemp,
                maxTemp = maxTemp
            )
        }
    }
}

@Composable
private fun TemperatureGaugeBar(
    temperature: Float,
    minTemp: Float,
    maxTemp: Float
) {
    val colors = LocalDashboardColors.current
    val fraction = ((temperature - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF4FA8E0),
            Color(0xFF8FD07F),
            Color(0xFFF2C14E),
            Color(0xFFE2574C)
        )
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val markerOffsetDp = maxWidth * fraction

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(gradient)
        )
        Box(
            modifier = Modifier
                .offset(x = markerOffsetDp - 6.dp, y = (-3).dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(width = 2.dp, color = colors.onSurface, shape = CircleShape)
        )
    }
}

// ---------------------------------------------------------------------------
// Irrigation controls
// ---------------------------------------------------------------------------

@Composable
private fun IrrigationControlsCard(
    isWaterPumpOn: Boolean,
    onWaterPumpToggle: (Boolean) -> Unit,
    isFertilizerPumpOn: Boolean,
    onFertilizerPumpToggle: (Boolean) -> Unit
) {
    val colors = LocalDashboardColors.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
            ControlRow(
                icon = Icons.Filled.WaterDrop,
                title = "Water pump",
                subtitle = if (isWaterPumpOn) "On" else "Off",
                checked = isWaterPumpOn,
                onCheckedChange = onWaterPumpToggle
            )
            HorizontalDivider(color = colors.surfaceHigh)
            ControlRow(
                icon = Icons.Filled.Science,
                title = "Fertilizer pump",
                subtitle = if (isFertilizerPumpOn) "On" else "Off",
                checked = isFertilizerPumpOn,
                onCheckedChange = onFertilizerPumpToggle
            )
        }
    }
}

@Composable
private fun ControlRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalDashboardColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = colors.onSurface, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
                Text(text = subtitle, fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.onSurfaceVariant,
                uncheckedTrackColor = colors.surfaceHigh
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Schedule summary
// ---------------------------------------------------------------------------

@Composable
private fun ScheduleCard(events: List<ScheduleEvent>) {
    val colors = LocalDashboardColors.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
            events.forEachIndexed { index, event ->
                ScheduleRow(event)
                if (index != events.lastIndex) {
                    HorizontalDivider(color = colors.surfaceHigh)
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(event: ScheduleEvent) {
    val colors = LocalDashboardColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(colors.accent.copy(alpha = 0.2f))
                .padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Text(
                text = event.time,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent
            )
        }
        Text(
            text = event.zone,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (event.completed) "Completed" else "Upcoming",
            fontSize = 11.sp,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.End
        )
    }
}

// ---------------------------------------------------------------------------
// Alerts
// ---------------------------------------------------------------------------

@Composable
private fun AlertCard(alert: SystemAlert, onDismiss: (SystemAlert) -> Unit) {
    val colors = LocalDashboardColors.current
    val isCritical = alert.severity == AlertSeverity.CRITICAL
    val backgroundColor = if (isCritical) colors.errorContainer else colors.warningContainer
    val titleColor = if (isCritical) colors.onErrorContainer else colors.onWarningContainer
    val icon = if (isCritical) Icons.Filled.Warning else Icons.Filled.LightMode

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = titleColor,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = alert.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
                Text(text = alert.timeAgo, fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
            IconButton(onClick = { onDismiss(alert) }, modifier = Modifier.size(20.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss alert",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyAlertsCard() {
    val colors = LocalDashboardColors.current
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No active alerts", fontSize = 13.sp, color = colors.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun AgriFlowDashboardPreview() {
    MaterialTheme {
        AgriFlowDashboardScreen()
    }
}
