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
    val waterTankPercent: Int = 78,
    val temperatureCelsius: Float = 27.4f,
    val humidityPercent: Int = 61,
    val lightIntensityPercent: Float = 85.0f,
    val isOnline: Boolean = true
)

// ---------------------------------------------------------------------------
// Color tokens (light, minimalist, Material 3 aligned)
// ---------------------------------------------------------------------------

private val AgriPrimary = Color(0xFF386A20)
private val SurfaceContainer = Color(0xFFEFF2E5)
private val SurfaceContainerHigh = Color(0xFFE3E6D9)
private val OnSurface = Color(0xFF1A1C16)
private val OnSurfaceVariant = Color(0xFF43483D)
private val ErrorContainer = Color(0xFFFFDAD6)
private val OnErrorContainer = Color(0xFF410002)
private val SuccessContainer = Color(0xFFDCF5DC)
private val WarningContainer = Color(0xFFFFF3D6)
private val OnWarningContainer = Color(0xFF5C3D00)
private val TankLow = Color(0xFFBA1A1A)

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
                val moisture = snapshot.getLong("soilMoisture")?.toInt() ?: 42
                val tank = snapshot.getLong("waterTankLevel")?.toInt() ?: 78
                val temp = snapshot.getDouble("temperature")?.toFloat() ?: 27.4f
                val hum = snapshot.getLong("humidity")?.toInt() ?: 61
                
                val lightVal = snapshot.get("lightIntensity") ?: snapshot.get("lux") ?: snapshot.get("light_density")
                val light = when (lightVal) {
                    is Double -> lightVal.toFloat()
                    is Long -> lightVal.toFloat()
                    is Int -> lightVal.toFloat()
                    else -> 0f
                }

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

    Scaffold(
        containerColor = Color(0xFFFCFDF6),
        topBar = {
            AgriFlowTopBar(
                userName = userName,
                isOnline = telemetry.isOnline
            )
        },
        bottomBar = {
            AgriFlowBottomBar(
                isAdmin = userRole.equals("admin", ignoreCase = true),
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
                        db.collection("system_data").document("live_telemetry")
                            .update("waterPump", newValue)
                    },
                    isFertilizerPumpOn = isFertilizerPumpOn,
                    onFertilizerPumpToggle = { newValue ->
                        isFertilizerPumpOn = newValue
                        db.collection("system_data").document("live_telemetry")
                            .update("fertilizerPump", newValue)
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

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgriFlowTopBar(userName: String, isOnline: Boolean) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = userName,
                    fontSize = 14.sp,
                    color = OnSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "AgriFlow Dashboard",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                )
            }
        },
        actions = {
            StatusBadge(isOnline = isOnline)
            Spacer(Modifier.width(12.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFFFCFDF6)
        )
    )
}

@Composable
private fun StatusBadge(isOnline: Boolean) {
    val backgroundColor = if (isOnline) SuccessContainer else ErrorContainer
    val dotColor = if (isOnline) AgriPrimary else TankLow
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
            color = if (isOnline) Color(0xFF0B3B0F) else OnErrorContainer
        )
    }
}

// ---------------------------------------------------------------------------
// Bottom bar
// ---------------------------------------------------------------------------

@Composable
private fun AgriFlowBottomBar(isAdmin: Boolean, onNavigate: (Class<*>) -> Unit) {
    NavigationBar(
        containerColor = Color(0xFFFCFDF6),
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = true,
            onClick = { /* Already on Home */ },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AgriPrimary,
                selectedTextColor = AgriPrimary,
                indicatorColor = SuccessContainer
            )
        )
        
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Schedule, contentDescription = "Schedule") },
            label = { Text("Schedule") },
            selected = false,
            onClick = { 
                // Using DashboardActivity as the "Schedule/History" view for now
                onNavigate(DashboardActivity::class.java)
            }
        )

        if (isAdmin) {
            NavigationBarItem(
                icon = { Icon(Icons.Filled.People, contentDescription = "Farmers") },
                label = { Text("Farmers") },
                selected = false,
                onClick = { onNavigate(FarmerListActivity::class.java) }
            )
        }

        NavigationBarItem(
            icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
            label = { Text("Profile") },
            selected = false,
            onClick = { onNavigate(ProfileActivity::class.java) }
        )
    }
}

// ---------------------------------------------------------------------------
// Section label
// ---------------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = OnSurfaceVariant
    )
}

// ---------------------------------------------------------------------------
// Telemetry grid (2x2)
// ---------------------------------------------------------------------------

@Composable
private fun TelemetryGrid(telemetry: TelemetryState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val moisture = telemetry.soilMoisturePercent
            val (status, statusColor) = when {
                moisture <= 30 -> "Dry" to Color(0xFFE53935)
                moisture <= 70 -> "Moderate" to Color(0xFFFB8C00)
                else -> "Wet" to Color(0xFF43A047)
            }
            
            RingMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.WaterDrop,
                label = "Soil moisture",
                value = moisture,
                isWarning = moisture < 15,
                status = status,
                statusColor = statusColor
            )
            RingMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Water,
                label = "Water tank level",
                value = telemetry.waterTankPercent,
                isWarning = telemetry.waterTankPercent < 20
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TemperatureMetricCard(
                modifier = Modifier.weight(1f),
                temperatureCelsius = telemetry.temperatureCelsius
            )
            RingMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Cloud,
                label = "Humidity",
                value = telemetry.humidityPercent,
                isWarning = false
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            RingMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.LightMode,
                label = "Light Intensity (%)",
                value = telemetry.lightIntensityPercent,
                isWarning = telemetry.lightIntensityPercent > 80f,
                unit = "%",
                maxValue = 100f,
                precision = 0
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
    val backgroundColor = if (isWarning) ErrorContainer else SurfaceContainer
    val contentColor = if (isWarning) OnErrorContainer else OnSurface
    val ringColor = if (isWarning) TankLow else AgriPrimary
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
                    trackColor = SurfaceContainerHigh,
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
    maxTemp: Float = 42f
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = Icons.Filled.Thermostat,
                contentDescription = "Temperature",
                tint = OnSurface,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(text = "Temperature", fontSize = 12.sp, color = OnSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%.1f".format(temperatureCelsius),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface
                )
                Text(
                    text = "°C",
                    fontSize = 14.sp,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
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
                .border(width = 2.dp, color = OnSurface, shape = CircleShape)
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
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
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
            HorizontalDivider(color = SurfaceContainerHigh)
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
                    .background(SurfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = OnSurface, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                Text(text = subtitle, fontSize = 12.sp, color = OnSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AgriPrimary,
                uncheckedThumbColor = OnSurfaceVariant,
                uncheckedTrackColor = SurfaceContainerHigh
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Schedule summary
// ---------------------------------------------------------------------------

@Composable
private fun ScheduleCard(events: List<ScheduleEvent>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
            events.forEachIndexed { index, event ->
                ScheduleRow(event)
                if (index != events.lastIndex) {
                    HorizontalDivider(color = SurfaceContainerHigh)
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(event: ScheduleEvent) {
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
                .background(Color(0xFFBCEBF0))
                .padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Text(
                text = event.time,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF032F33)
            )
        }
        Text(
            text = event.zone,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = OnSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (event.completed) "Completed" else "Upcoming",
            fontSize = 11.sp,
            color = OnSurfaceVariant,
            textAlign = TextAlign.End
        )
    }
}

// ---------------------------------------------------------------------------
// Alerts
// ---------------------------------------------------------------------------

@Composable
private fun AlertCard(alert: SystemAlert, onDismiss: (SystemAlert) -> Unit) {
    val isCritical = alert.severity == AlertSeverity.CRITICAL
    val backgroundColor = if (isCritical) ErrorContainer else WarningContainer
    val titleColor = if (isCritical) OnErrorContainer else OnWarningContainer
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
                Text(text = alert.timeAgo, fontSize = 11.sp, color = OnSurfaceVariant)
            }
            IconButton(onClick = { onDismiss(alert) }, modifier = Modifier.size(20.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss alert",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyAlertsCard() {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "No active alerts", fontSize = 13.sp, color = OnSurfaceVariant)
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
