package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.OsmMapView
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import com.example.ml.HotspotRecommendation
import com.example.ml.SmartHeatmapPredictor
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

data class RecommendedSpot(
    val name: String,
    val lat: Double,
    val lng: Double,
    val distanceKm: Double,
    val primaryType: String,
    val avgEarnings: Double,
    val score: Int,
    val reason: String
)

private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartHeatmapScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val predictor = remember(viewModel.importedOnnxUri) {
        SmartHeatmapPredictor(context, viewModel.importedOnnxUri)
    }

    var sortBy by remember { mutableStateOf("score") }
    var filterType by remember { mutableStateOf("Semua") }

    val currentDay = remember {
        SimpleDateFormat("EEEE", Locale.forLanguageTag("id-ID")).format(Date())
    }
    val currentHour = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    val localeID = remember { Locale.forLanguageTag("id-ID") }
    val formatter = remember(localeID) {
        NumberFormat.getCurrencyInstance(localeID).apply {
            maximumFractionDigits = 0
        }
    }

    // Compute recommendations efficiently - only recompute when critical params change
    val recommendedSpots = remember(
        viewModel.lastKnownLatitude,
        viewModel.lastKnownLongitude,
        viewModel.smartHeatmapEnabled
    ) {
        if (!viewModel.smartHeatmapEnabled) return@remember emptyList<RecommendedSpot>()

        val calendar = Calendar.getInstance()
        val currentHourInt = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeekInt = calendar.get(Calendar.DAY_OF_WEEK)

        val isWeekend = if (dayOfWeekInt == Calendar.SATURDAY || dayOfWeekInt == Calendar.SUNDAY) 1 else 0
        val isSchoolHoliday = if (viewModel.holidayAnalysisEnabled) 1 else 0

        val spotsList = predictor.predictHotspots(
            hour = currentHourInt,
            dayOfWeek = dayOfWeekInt,
            month = calendar.get(Calendar.MONTH) + 1,
            isWeekend = isWeekend,
            isSchoolHoliday = isSchoolHoliday,
            isCollegeHoliday = isSchoolHoliday,
            isRamadhan = 0,
            ramadhanPhase = "AFTER_EID",
            tripCategory = if (filterType == "Semua") "Umum/Lainnya" else filterType,
            jenisOrder = if (filterType == "Semua") "RIDE" else filterType,
            driverLat = viewModel.lastKnownLatitude,
            driverLng = viewModel.lastKnownLongitude,
            inputLayerName = viewModel.onnxInputLayerName,
            outputLayerName = viewModel.onnxOutputLayerName
        )

        spotsList.map { spot ->
            val distance = calculateDistance(
                viewModel.lastKnownLatitude,
                viewModel.lastKnownLongitude,
                spot.latitude,
                spot.longitude
            )

            val earnings = (spot.skorPotensi * 2500).toDouble()

            RecommendedSpot(
                name = spot.keterangan,
                lat = spot.latitude,
                lng = spot.longitude,
                distanceKm = distance,
                primaryType = "Lokasi Potensial",
                avgEarnings = earnings,
                score = spot.skorPotensi,
                reason = "Prediksi berdasarkan pola order jam $currentHourInt di $currentDay"
            )
        }
    }

    // Apply sorting and filtering separately - more efficient
    val sortedSpots = remember(recommendedSpots, sortBy, filterType) {
        recommendedSpots.sortedWith(
            when (sortBy) {
                "distance" -> compareBy { it.distanceKm }
                "earnings" -> compareByDescending { it.avgEarnings }
                else -> compareByDescending { it.score }
            }
        ).take(8)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        SmartHeatmapHeader(currentDay, currentHour)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(16.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            OsmMapView(
                latitude = viewModel.lastKnownLatitude,
                longitude = viewModel.lastKnownLongitude,
                isDarkMode = viewModel.isDarkMode,
                currentPathPoints = emptyList(),
                historicalOrders = viewModel.allOrders.collectAsState().value,
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .background(
                        color = if (viewModel.isDarkMode) Color(0xDD121620) else Color(0xDDFFFFFF),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        if (viewModel.isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(GojekGreen)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "GPS Live",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            val mapLabel = when (viewModel.mapSource) {
                "manual_pbf" -> viewModel.importedPbfFileName?.let { "Offline: $it" } ?: "Peta Offline"
                else -> "Peta Online"
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = GojekGreen),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Text(
                    text = mapLabel,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (viewModel.smartHeatmapEnabled) {
                HeatmapFilterPanel(sortBy = sortBy, onSortChange = { sortBy = it }, filterType = filterType, onFilterChange = { filterType = it })

                RecommendationsContent(sortedSpots, viewModel, formatter, predictor.isModelLoaded)
            } else {
                HeatmapDisabledCard()
            }
        }
    }
}

@Composable
private fun SmartHeatmapHeader(currentDay: String, currentHour: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Smart Radar",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = GojekGreen,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "$currentDay � $currentHour",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeatmapFilterPanel(sortBy: String, onSortChange: (String) -> Unit, filterType: String, onFilterChange: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterSection(
                title = "Layanan",
                icon = Icons.Default.FilterList,
                options = listOf(
                    Triple("Semua", "Semua", Icons.Default.AllInclusive),
                    Triple("Penumpang", "Penumpang", Icons.Default.DirectionsCar),
                    Triple("Makanan", "Makanan", Icons.Default.RestaurantMenu),
                    Triple("Paket", "Paket", Icons.Default.LocalShipping)
                ),
                selectedKey = filterType,
                onSelected = onFilterChange
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            FilterSection(
                title = "Urutkan",
                icon = Icons.Default.Sort,
                options = listOf(
                    Triple("score", "Potensi", Icons.Default.TrendingUp),
                    Triple("distance", "Jarak", Icons.Default.NearMe),
                    Triple("earnings", "Pendapatan", Icons.Default.Payments)
                ),
                selectedKey = sortBy,
                onSelected = onSortChange
            )
        }
    }
}
@Composable
private fun FilterSection(
    title: String,
    icon: ImageVector,
    options: List<Triple<String, String, ImageVector>>,
    selectedKey: String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GojekGreen,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (key, label, chipIcon) ->
                val isSelected = selectedKey == key
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelected(key) },
                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    leadingIcon = {
                        Icon(
                            imageVector = chipIcon,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GojekGreen,
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = GojekGreen
                    )
                )
            }
        }
    }
}

@Composable
private fun RecommendationsContent(spots: List<RecommendedSpot>, viewModel: MainViewModel, formatter: NumberFormat, isModelLoaded: Boolean = false) {
    if (spots.isEmpty()) {
        if (!isModelLoaded) {
            NoModelCard()
        } else {
            EmptyRecommendationsCard()
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Radar,
                contentDescription = null,
                tint = GojekGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Rekomendasi Top ${spots.size} Lokasi",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        spots.forEachIndexed { index, spot ->
            RecommendationCard(spot, index, viewModel)
        }
    }
}

@Composable
private fun RecommendationCard(spot: RecommendedSpot, index: Int, viewModel: MainViewModel) {
    val isTopSpot = index == 0
    val backgroundColor = if (isTopSpot) {
        GojekGreen.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(16.dp),
        border = if (isTopSpot) {
            BorderStroke(2.dp, GojekGreen)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isTopSpot) GojekGreen else MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#${index + 1}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = spot.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${spot.distanceKm.toBigDecimal().setScale(1, java.math.RoundingMode.HALF_UP)} km",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isTopSpot) GojekGreen else GojekYellow)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${spot.score}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Jarak", "${spot.distanceKm.toBigDecimal().setScale(1, java.math.RoundingMode.HALF_UP)} km")
                StatItem("Potensi", "${spot.score}%")
                StatItem("Est. Earnings", "Rp ${(spot.avgEarnings.toInt() / 1000)}k")
            }

            Button(
                onClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTopSpot) GojekGreen else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Lihat Peta",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (viewModel.isDarkMode) Color(0xFF1E242B) else Color(0xFFF3F4F6)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = spot.reason,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmptyRecommendationsCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Radar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "Tidak Ada Rekomendasi",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Tidak ada rekomendasi lokasi potensial saat ini. Pastikan model ONNX telah diimpor di halaman Pengaturan, atau coba ubah filter.",
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}


@Composable
private fun NoModelCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "Model ONNX Belum Dimuat",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Impor file model ONNX di halaman Pengaturan untuk mendapatkan prediksi hotspot berbasis machine learning.",
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun HeatmapDisabledCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Radar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "Smart Radar Dinonaktifkan",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Silakan aktifkan fitur Smart Heatmap di halaman Pengaturan dan pastikan model ONNX telah diimpor untuk melihat analisis radar potensi.",
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}
