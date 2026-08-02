package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.data.TrackPoint
import com.example.ui.theme.GojekGreen
import com.example.ui.theme.GojekYellow
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ─── Card Themes ─────────────────────────────────────────────────────────────
enum class StravaCardTheme(
    val id: String,
    val displayName: String,
    val bgColor: ComposeColor,
    val accentColor: ComposeColor,
    val cardColor: ComposeColor,
    val textPrimary: ComposeColor
) {
    GOJEK_EMERALD("gojek_emerald", "Sleek Emerald",
        ComposeColor(0xFF041E15), ComposeColor(0xFF00AA13), ComposeColor(0xFF0C2C20), ComposeColor(0xFFFFFFFF)),
    STRAVA_ORANGE("strava_orange", "Strava Sunset",
        ComposeColor(0xFF0F1012), ComposeColor(0xFFFC6100), ComposeColor(0xFF1B1D21), ComposeColor(0xFFFFFFFF)),
    MIDNIGHT_ONYX("midnight_onyx", "Midnight Onyx",
        ComposeColor(0xFF020911), ComposeColor(0xFF00E5FF), ComposeColor(0xFF0A1625), ComposeColor(0xFFFFFFFF)),
    ROYAL_CARBON("royal_carbon", "Royal Violet",
        ComposeColor(0xFF110720), ComposeColor(0xFFFF007F), ComposeColor(0xFF1D0F34), ComposeColor(0xFFFFFFFF)),
    CYBER_PULSE("cyber_pulse", "Cyber Pulse",
        ComposeColor(0xFF000000), ComposeColor(0xFFCCFF00), ComposeColor(0xFF121212), ComposeColor(0xFFFFFFFF)),
    SOLAR_GOLD("solar_gold", "Solar Gold",
        ComposeColor(0xFF160F03), ComposeColor(0xFFFFB300), ComposeColor(0xFF2B1D08), ComposeColor(0xFFFFFFFF)),
    OCEAN_DEEP("ocean_deep", "Ocean Deep",
        ComposeColor(0xFF020E1C), ComposeColor(0xFF00FFCC), ComposeColor(0xFF0A203A), ComposeColor(0xFFFFFFFF)),
    RETRO_PINK("retro_pink", "Retro Synth",
        ComposeColor(0xFF21002F), ComposeColor(0xFF00FFFF), ComposeColor(0xFF330B45), ComposeColor(0xFFFFFFFF)),
    VINTAGE_WARM("vintage_warm", "Warm Vintage",
        ComposeColor(0xFF2A231C), ComposeColor(0xFFE5A93B), ComposeColor(0xFF3D342B), ComposeColor(0xFFFFFFFF))
}

// ─── Background Styles ───────────────────────────────────────────────────────
enum class MapDisplayMode(val label: String) {
    AUTOMATIC("Auto Color Match"),
    MANUAL("Manual Color Selection"),
    VECTOR("Vector Route Path")
}

enum class ColorLookupPreset(val label: String) {
    DARK_THEME("Dark Theme"),
    LIGHT_THEME("Light Theme"),
    SATELLITE_MATCH("Satellite Match"),
    TERRAIN_MATCH("Terrain Match"),
    COLOR_MATCH("Color Match")
}

enum class PosterBgStyle(val label: String, val isMap: Boolean) {
    MAP_OSM("Peta OSM", true),
    DARK_SOLID("Gelap Solid", false),
    LIGHT_SOLID("Terang Solid", false),
    GRADIENT_DARK("Gradien Gelap", false),
    GRADIENT_GREEN("Gradien Hijau", false),
    PATTERN_GRID("Pola Grid", false),
    TRANSPARENT("Transparan", false)
}

// Tile source ngikut tema app (Voyager = terang, Dark Matter = gelap), konsisten dengan OsmMapView
private fun cartoTileSource(name: String, url: String): org.osmdroid.tileprovider.tilesource.XYTileSource {
    return org.osmdroid.tileprovider.tilesource.XYTileSource(name, 0, 20, 256, ".png", arrayOf(url))
}
private val TILE_CARTO_VOYAGER = cartoTileSource("CartoVoyager", "https://a.basemaps.cartocdn.com/rastertiles/voyager/")
private val TILE_CARTO_DARK = cartoTileSource("CartoDark", "https://a.basemaps.cartocdn.com/dark_all/")

private fun mapTileSourceForTheme(isDark: Boolean): org.osmdroid.tileprovider.tilesource.XYTileSource {
    return if (isDark) TILE_CARTO_DARK else TILE_CARTO_VOYAGER
}

enum class MapBgStyle(val label: String) {
    MAP_TILES("Gambar Peta"),
    SOLID("Warna Solid"),
    TRANSPARENT("Transparan")
}

// ─── Design Template Presets ──────────────────────────────────────────────────────
enum class TrackerTemplatePreset(
    val id: String,
    val displayName: String,
    val description: String,
    val theme: StravaCardTheme,
    val posterBgStyle: PosterBgStyle,
    val mapBgStyle: MapBgStyle,
    val selectedMapMode: MapDisplayMode,
    val selectedColorPreset: ColorLookupPreset,
    val showGrid: Boolean
) {
    GOJEK_SLEEK(
        "gojek_sleek",
        "Gojek (Hijau)",
        "Rute hijau khas Gojek di atas peta.",
        StravaCardTheme.GOJEK_EMERALD,
        PosterBgStyle.GRADIENT_GREEN,
        MapBgStyle.MAP_TILES,
        MapDisplayMode.AUTOMATIC,
        ColorLookupPreset.COLOR_MATCH,
        true
    ),
    CLEAN_LIGHT(
        "clean_light",
        "Terang Bersih",
        "Latar terang simpel, cocok untuk cetak.",
        StravaCardTheme.SOLAR_GOLD,
        PosterBgStyle.LIGHT_SOLID,
        MapBgStyle.MAP_TILES,
        MapDisplayMode.AUTOMATIC,
        ColorLookupPreset.LIGHT_THEME,
        false
    ),
    DARK_MODE(
        "dark_mode",
        "Gelap",
        "Tema gelap untuk malam hari.",
        StravaCardTheme.MIDNIGHT_ONYX,
        PosterBgStyle.GRADIENT_DARK,
        MapBgStyle.MAP_TILES,
        MapDisplayMode.AUTOMATIC,
        ColorLookupPreset.DARK_THEME,
        true
    )
}

// ─── Helper for Custom Markers ───────────────────────────────────────────────────
private fun createSimpleMarkerIcon(context: Context, colorInt: Int, text: String): android.graphics.drawable.Drawable {
    val sizePx = 64
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { isAntiAlias = true }
    
    // Shadow
    paint.color = android.graphics.Color.argb(60, 0, 0, 0)
    canvas.drawCircle(sizePx / 2f, sizePx * 0.8f, 8f, paint)
    
    // Pin shape
    paint.color = colorInt
    canvas.drawCircle(sizePx / 2f, sizePx * 0.4f, 20f, paint)
    
    // Outer white dot in center
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(sizePx / 2f, sizePx * 0.4f, 14f, paint)
    
    // Text style
    paint.color = colorInt
    paint.textSize = sizePx * 0.3f
    paint.isFakeBoldText = true
    paint.textAlign = Paint.Align.CENTER
    
    val fm = paint.fontMetrics
    val yOffset = (fm.descent - fm.ascent) / 2f - fm.descent
    canvas.drawText(text, sizePx / 2f, sizePx * 0.4f + yOffset, paint)
    
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

@Composable
fun MapViewComposable(
    viewModel: MainViewModel,
    trackPoints: List<TrackPoint>,
    accentColor: ComposeColor,
    showMapTiles: Boolean,
    mapBgStyle: MapBgStyle,
    themeCardColor: ComposeColor,
    mapMode: MapDisplayMode,
    colorPreset: ColorLookupPreset,
    manualOverlayColor: ComposeColor,
    manualMapBgColor: ComposeColor,
    showStartMarker: Boolean,
    showEndMarker: Boolean,
    modifier: Modifier = Modifier,
    onMapViewCreated: (org.osmdroid.views.MapView) -> Unit
) {
    val context = LocalContext.current
    val ms = viewModel.mapSource
    val puriStr = viewModel.importedPbfUri
    val osmV = remember(ms, viewModel.isDarkMode, puriStr) {
        org.osmdroid.views.MapView(context).apply {
            org.osmdroid.config.Configuration.getInstance().userAgentValue = context.packageName
            when {
                ms == "manual_pbf" && puriStr != null -> {
                    val tf = File(context.getExternalFilesDir(null), "osmdroid/tiles")
                    val mf = tf.listFiles()?.firstOrNull { it.name.startsWith("offline_map") }
                    if (mf != null && mf.exists()) {
                        try {
                            val op = org.osmdroid.tileprovider.MapTileProviderBasic(context)
                            op.tileSource = org.osmdroid.tileprovider.tilesource.XYTileSource("OfflineMap", 1, 19, 256, ".png", arrayOf())
                            setTileProvider(op)
                        } catch (_: Exception) { setTileSource(mapTileSourceForTheme(viewModel.isDarkMode)) }
                    } else setTileSource(mapTileSourceForTheme(viewModel.isDarkMode))
                }
                else -> setTileSource(mapTileSourceForTheme(viewModel.isDarkMode))
            }
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            onMapViewCreated(this)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { osmV },
        update = { view ->
            val tilesEnabled = showMapTiles && mapMode != MapDisplayMode.VECTOR
            view.overlayManager.tilesOverlay.isEnabled = tilesEnabled
            
            if (!tilesEnabled) {
                if (mapMode == MapDisplayMode.MANUAL) {
                    view.setBackgroundColor(manualMapBgColor.toArgb())
                } else if (mapBgStyle == MapBgStyle.SOLID) {
                    view.setBackgroundColor(themeCardColor.toArgb())
                } else {
                    view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            } else {
                view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }

            val colorFilter = when (mapMode) {
                MapDisplayMode.VECTOR -> null
                MapDisplayMode.MANUAL -> {
                    val r = manualOverlayColor.red
                    val g = manualOverlayColor.green
                    val b = manualOverlayColor.blue
                    val cm = android.graphics.ColorMatrix()
                    cm.set(floatArrayOf(
                        r, 0f, 0f, 0f, 0f,
                        0f, g, 0f, 0f, 0f,
                        0f, 0f, b, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                    android.graphics.ColorMatrixColorFilter(cm)
                }
                MapDisplayMode.AUTOMATIC -> {
                    when (colorPreset) {
                        ColorLookupPreset.DARK_THEME -> {
                            val cm = android.graphics.ColorMatrix()
                            cm.set(floatArrayOf(-0.65f, 0f, 0f, 0f, 210f, 0f, -0.65f, 0f, 0f, 210f, 0f, 0f, -0.65f, 0f, 210f, 0f, 0f, 0f, 1f, 0f))
                            android.graphics.ColorMatrixColorFilter(cm)
                        }
                        ColorLookupPreset.SATELLITE_MATCH -> {
                            val cm = android.graphics.ColorMatrix()
                            cm.set(floatArrayOf(
                                0.15f, 0f, 0f, 0f, 10f,
                                0f, 0.45f, 0f, 0f, 20f,
                                0f, 0f, 0.40f, 0f, 40f,
                                0f, 0f, 0f, 1f, 0f
                            ))
                            android.graphics.ColorMatrixColorFilter(cm)
                        }
                        ColorLookupPreset.TERRAIN_MATCH -> {
                            val cm = android.graphics.ColorMatrix()
                            cm.set(floatArrayOf(
                                0.85f, 0f, 0f, 0f, 20f,
                                0f, 0.70f, 0f, 0f, 15f,
                                0f, 0f, 0.40f, 0f, 5f,
                                0f, 0f, 0f, 1f, 0f
                            ))
                            android.graphics.ColorMatrixColorFilter(cm)
                        }
                        ColorLookupPreset.COLOR_MATCH -> {
                            // Subtly convert map to grayscale and overlay accent color, keeping map details perfectly readable and gorgeous
                            val r = accentColor.red
                            val g = accentColor.green
                            val b = accentColor.blue
                            val cm = android.graphics.ColorMatrix()
                            cm.set(floatArrayOf(
                                0.26f * r + 0.12f, 0.58f * r, 0.08f * r, 0f, 15f * r,
                                0.26f * g, 0.58f * g + 0.12f, 0.08f * g, 0f, 15f * g,
                                0.26f * b, 0.58f * b, 0.08f * b + 0.12f, 0f, 15f * b,
                                0f, 0f, 0f, 1f, 0f
                            ))
                            android.graphics.ColorMatrixColorFilter(cm)
                        }
                        ColorLookupPreset.LIGHT_THEME -> null
                    }
                }
            }
            view.overlayManager.tilesOverlay.setColorFilter(colorFilter)

            view.overlays.clear()
            val resolvedPathColor = when (mapMode) {
                MapDisplayMode.MANUAL -> manualOverlayColor
                else -> accentColor
            }
            
            if (trackPoints.isNotEmpty()) {
                val pts = trackPoints.map { org.osmdroid.util.GeoPoint(it.lat, it.lng) }
                view.overlays.add(org.osmdroid.views.overlay.Polyline(view).apply {
                    outlinePaint.color = resolvedPathColor.toArgb()
                    outlinePaint.strokeWidth = 9f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    outlinePaint.strokeJoin = Paint.Join.ROUND
                    setPoints(pts)
                })

                if (showStartMarker) {
                    val startPt = org.osmdroid.util.GeoPoint(trackPoints.first().lat, trackPoints.first().lng)
                    view.overlays.add(org.osmdroid.views.overlay.Marker(view).apply {
                        position = startPt
                        title = "Titik Awal (Start)"
                        setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                        icon = createSimpleMarkerIcon(context, android.graphics.Color.rgb(0, 170, 19), "A")
                    })
                }

                if (showEndMarker) {
                    val endPt = org.osmdroid.util.GeoPoint(trackPoints.last().lat, trackPoints.last().lng)
                    view.overlays.add(org.osmdroid.views.overlay.Marker(view).apply {
                        position = endPt
                        title = "Titik Akhir (End)"
                        setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                        icon = createSimpleMarkerIcon(context, android.graphics.Color.rgb(229, 57, 53), "B")
                    })
                }

                val lats = trackPoints.map { it.lat }
                val lngs = trackPoints.map { it.lng }
                val minLat = lats.min()
                val maxLat = lats.max()
                val minLng = lngs.min()
                val maxLng = lngs.max()
                
                val buffer = 0.0002
                val boundingBox = if (maxLat == minLat && maxLng == minLng) {
                    org.osmdroid.util.BoundingBox(maxLat + buffer, maxLng + buffer, minLat - buffer, minLng - buffer)
                } else {
                    org.osmdroid.util.BoundingBox(maxLat, maxLng, minLat, minLng)
                }

                if (view.width > 0 && view.height > 0) {
                    try {
                        view.zoomToBoundingBox(boundingBox, false, 72)
                    } catch (e: Exception) {
                        view.controller.setCenter(org.osmdroid.util.GeoPoint(boundingBox.centerLatitude, boundingBox.centerLongitude))
                        view.controller.setZoom(14.5)
                    }
                } else {
                    view.addOnLayoutChangeListener(object : android.view.View.OnLayoutChangeListener {
                        override fun onLayoutChange(
                            v: android.view.View?, left: Int, top: Int, right: Int, bottom: Int,
                            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                        ) {
                            view.removeOnLayoutChangeListener(this)
                            try {
                                view.zoomToBoundingBox(boundingBox, false, 72)
                            } catch (e: Exception) {
                                view.controller.setCenter(org.osmdroid.util.GeoPoint(boundingBox.centerLatitude, boundingBox.centerLongitude))
                                view.controller.setZoom(14.5)
                            }
                        }
                    })
                }
            }
        }
    )
}

// ─── Main Screen Composable ──────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val orders by viewModel.allOrders.collectAsState()

    val availableDates = remember(orders) {
        orders.map { it.tanggal }.distinct().sortedDescending()
    }
    val defaultDate = remember(availableDates) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (availableDates.contains(today)) today
        else availableDates.firstOrNull() ?: today
    }

    var selectedDate      by remember { mutableStateOf(defaultDate) }
    var driverNameInput   by remember { mutableStateOf("") }
    var selectedTheme     by remember { mutableStateOf(StravaCardTheme.GOJEK_EMERALD) }

    var showDistance   by remember { mutableStateOf(true) }
    var showEarnings   by remember { mutableStateOf(true) }
    var showDuration   by remember { mutableStateOf(true) }
    var showTrips      by remember { mutableStateOf(true) }
    var showWatermark  by remember { mutableStateOf(true) }
    var showGridInMap  by remember { mutableStateOf(true) }
    var distanceType   by remember { mutableStateOf("total") }
    var orderTypeFilter by remember { mutableStateOf("Semua") }

    // Background style - default terang (sesuai tema app)
    var posterBgStyle  by remember { mutableStateOf(PosterBgStyle.LIGHT_SOLID) }
    var mapBgStyle     by remember { mutableStateOf(MapBgStyle.MAP_TILES) }

    // Map overlay and customization state variables
    var selectedMapMode by remember { mutableStateOf(MapDisplayMode.AUTOMATIC) }
    var selectedColorPreset by remember { mutableStateOf(ColorLookupPreset.COLOR_MATCH) }
    
    val overlayColors = remember {
        listOf(
            "Gojek Green" to ComposeColor(0xFF00AA13),
            "Strava Orange" to ComposeColor(0xFFFC6100),
            "Cyan Blue" to ComposeColor(0xFF00E5FF),
            "Pink Orchid" to ComposeColor(0xFFFF007F),
            "Amber Gold" to ComposeColor(0xFFFFB300),
            "Cobalt Blue" to ComposeColor(0xFF2979FF),
            "Deep Purple" to ComposeColor(0xFF7C4DFF),
            "Slate Gray" to ComposeColor(0xFF607D8B)
        )
    }
    var manualOverlayColor by remember { mutableStateOf(overlayColors[0].second) }

    val mapBackgroundColors = remember {
        listOf(
            "Black Onyx" to ComposeColor(0xFF0A0E14),
            "Slate Dark" to ComposeColor(0xFF1E293B),
            "Cream Gray" to ComposeColor(0xFFF1F5F9),
            "White Solid" to ComposeColor(0xFFFFFFFF),
            "Forest Green" to ComposeColor(0xFF0B241A),
            "Navy Deep" to ComposeColor(0xFF0B132B)
        )
    }
    var manualMapBgColor by remember { mutableStateOf(mapBackgroundColors[1].second) }

    var showStartMarker by remember { mutableStateOf(true) }
    var showEndMarker by remember { mutableStateOf(true) }

    // Map reference for screenshot capture
    var mapViewRef by remember { mutableStateOf<org.osmdroid.views.MapView?>(null) }
    
    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var selectedTemplatePreset by remember { mutableStateOf<TrackerTemplatePreset?>(TrackerTemplatePreset.GOJEK_SLEEK) }

    // === Strava-style state baru ===
    // Multi-pilih hari (kalender) - default 1 hari terpilih
    var selectedDates by remember { mutableStateOf(setOf(selectedDate)) }
    // Sumber latar poster: "peta" | "warna" | "galeri"
    var bgSource by remember { mutableStateOf("peta") }
    var galleryUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var solidColor by remember { mutableStateOf(ComposeColor(0xFF041E15)) }
    // Rasio ekspor: "4:3" | "vertikal" | "wide"
    var exportRatio by remember { mutableStateOf("vertikal") }
    var showCustomSheet by remember { mutableStateOf(false) }
    // launcher galeri
    val galleryLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? -> galleryUri = uri }

    // Data
    val dayOrders = remember(orders, selectedDates, orderTypeFilter) {
        orders.filter { o ->
            selectedDates.contains(o.tanggal) && (
                orderTypeFilter == "Semua" ||
                (orderTypeFilter == "Penumpang" && o.jenisOrder.equals("Penumpang", ignoreCase = true)) ||
                (orderTypeFilter == "Paket" && (o.jenisOrder.equals("Barang", ignoreCase = true) || o.jenisOrder.equals("Paket", ignoreCase = true) || o.jenisOrder.equals("GoSend", ignoreCase = true))) ||
                (orderTypeFilter == "Makanan" && (o.jenisOrder.equals("Food", ignoreCase = true) || o.jenisOrder.equals("GoFood", ignoreCase = true) || o.jenisOrder.equals("Makanan", ignoreCase = true)))
            )
        }
    }
    val consolidatedTrackPoints = remember(dayOrders) {
        val pts = mutableListOf<TrackPoint>()
        // urutkan per tanggal biar rute tersusun rapi
        dayOrders.sortedBy { it.tanggal }.forEach { pts.addAll(it.getTrackPoints()) }
        pts.toList()
    }

    val snappedTrackPoints = consolidatedTrackPoints
    val totalDistance    = remember(dayOrders, distanceType) { if (distanceType == "antar") dayOrders.sumOf { it.jarakKeTujuan } else dayOrders.sumOf { it.jarakTempuh } }
    val earningsToShow   = remember(dayOrders) { dayOrders.sumOf { it.pendapatan } }
    val orderCount       = remember(dayOrders) { dayOrders.size }
    val totalDurationMin = remember(dayOrders) { dayOrders.sumOf { it.durasi.toInt() }.coerceAtLeast(dayOrders.size * 15) }

    val localeID          = remember { Locale.forLanguageTag("id-ID") }
    val currencyFormatter = remember(localeID) { NumberFormat.getCurrencyInstance(localeID).apply { maximumFractionDigits = 0 } }
    val formattedDateTitle = remember(selectedDate) {
        try { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.forLanguageTag("id-ID")).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(selectedDate) ?: Date()) }
        catch (e: Exception) { selectedDate }
    }

    val posterBgColor = when (posterBgStyle) {
        PosterBgStyle.MAP_OSM        -> ComposeColor.Transparent
        PosterBgStyle.TRANSPARENT    -> ComposeColor.Transparent
        PosterBgStyle.DARK_SOLID     -> selectedTheme.bgColor
        PosterBgStyle.LIGHT_SOLID    -> ComposeColor(0xFFF5F5F5)
        PosterBgStyle.GRADIENT_DARK  -> selectedTheme.bgColor
        PosterBgStyle.GRADIENT_GREEN -> ComposeColor(0xFF041E15)
        PosterBgStyle.PATTERN_GRID   -> selectedTheme.bgColor
    }
    val posterTextColor = when (posterBgStyle) {
        PosterBgStyle.LIGHT_SOLID -> ComposeColor(0xFF111111)
        else                       -> ComposeColor.White
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pembuat Poster Rute", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Desain dan bagikan poster rute harian Anda.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.currentScreen = "dashboard_pendapatan" }, modifier = Modifier.testTag("strava_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            // Section 1: Kalender Pilih Hari (record order)
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Pilih Hari (record order)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = GojekGreen
                        )
                        Text(
                            text = "${selectedDates.size} hari dipilih",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Kalender bulan berjalan (bulan dari selectedDate pertama)
                    val calBase = Calendar.getInstance().apply {
                        try {
                            val d = selectedDates.firstOrNull() ?: selectedDate
                            time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d) ?: Date()
                        } catch (_: Exception) {}
                    }
                    val month = calBase.get(Calendar.MONTH)
                    val year = calBase.get(Calendar.YEAR)
                    val daysInMonth = calBase.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val firstDayOfWeek = Calendar.getInstance().apply {
                        set(year, month, 1)
                    }.get(Calendar.DAY_OF_WEEK) // 1=Sun..7=Sat

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("id-ID")).format(calBase.time),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                        Row {
                            IconButton(onClick = { /* prev month: pindah selectedDate ke awal bulan sblm */ }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = GojekGreen, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* next month */ }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.ArrowForward, null, tint = GojekGreen, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Header hari
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Min","Sen","Sel","Rab","Kam","Jum","Sab").forEach { d ->
                            Text(d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Grid tanggal
                    val totalCells = firstDayOfWeek - 1 + daysInMonth
                    val weeks = (totalCells + 6) / 7
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (w in 0 until weeks) {
                            Row(Modifier.fillMaxWidth()) {
                                for (c in 0 until 7) {
                                    val idx = w * 7 + c
                                    val dayNum = idx - (firstDayOfWeek - 1) + 1
                                    if (dayNum in 1..daysInMonth) {
                                        val dateStr = String.format("%04d-%02d-%02d", year, month + 1, dayNum)
                                        val hasOrder = availableDates.contains(dateStr)
                                        val isSel = selectedDates.contains(dateStr)
                                        val isToday = dateStr == SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                        Box(
                                            modifier = Modifier.weight(1f).aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    when {
                                                        isSel -> GojekGreen
                                                        hasOrder -> GojekGreen.copy(alpha = 0.12f)
                                                        else -> ComposeColor.Transparent
                                                    }
                                                )
                                                .border(1.dp, if (isToday) GojekGreen else ComposeColor.Transparent, RoundedCornerShape(8.dp))
                                                .clickable(enabled = hasOrder) { selectedDates = selectedDates.toMutableSet().apply { if (isSel) remove(dateStr) else add(dateStr) } },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = dayNum.toString(),
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSel || hasOrder) FontWeight.Bold else FontWeight.Normal,
                                                    color = when {
                                                        isSel -> ComposeColor.White
                                                        hasOrder -> GojekGreen
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                                if (hasOrder) Box(modifier = Modifier.size(4.dp).background(if (isSel) ComposeColor.White else GojekGreen, CircleShape))
                                            }
                                        }
                                    } else {
                                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                    }
                                }
                            }
                        }
                    }
                    Text("● titik = ada record order", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (dayOrders.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f))
                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                    Text(
                        text = "Belum ada riwayat order. Rekam aktivitas Anda pada tab Perekam.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Section 2: Poster Preview (Instant visual response at the top!)
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(if (posterBgStyle == PosterBgStyle.TRANSPARENT) 0.dp else 12.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        when (bgSource) {
                            "warna" -> SolidColor(solidColor)
                            "galeri" -> SolidColor(solidColor)
                            else -> SolidColor(ComposeColor.Transparent) // peta -> diisi MapView di bawah
                        }
                    )
                    .border(
                        BorderStroke(1.dp, selectedTheme.accentColor.copy(alpha = 0.2f)),
                        RoundedCornerShape(20.dp)
                    )
                    .testTag("strava_poster_preview")
            ) {
                // Background transparent checkerboard
                if (posterBgStyle == PosterBgStyle.TRANSPARENT) {
                    ComposeCanvas(Modifier.fillMaxSize()) {
                        val checkSize = 16.dp.toPx()
                        var x = 0f; while (x < size.width) {
                            var y = 0f; val isEven = (x / checkSize).toInt() % 2 == 0
                            while (y < size.height) {
                                if (isEven == ((y / checkSize).toInt() % 2 == 0)) {
                                    drawRect(ComposeColor.LightGray.copy(alpha = 0.15f), Offset(x, y), androidx.compose.ui.geometry.Size(checkSize, checkSize))
                                }
                                y += checkSize
                            }
                            x += checkSize
                        }
                    }
                }

                // If Full Map Background is active
                if (bgSource == "peta" && snappedTrackPoints.isNotEmpty()) {
                    if (selectedMapMode == MapDisplayMode.VECTOR) {
                        RoutePreviewCanvas(snappedTrackPoints, selectedTheme.accentColor, showStartMarker, showEndMarker, Modifier.fillMaxSize())
                    } else if (mapBgStyle == MapBgStyle.MAP_TILES) {
                        MapViewComposable(viewModel, snappedTrackPoints, selectedTheme.accentColor, true, mapBgStyle, selectedTheme.cardColor, selectedMapMode, selectedColorPreset, manualOverlayColor, manualMapBgColor, showStartMarker, showEndMarker) { mapViewRef = it }
                    } else {
                        RoutePreviewCanvas(snappedTrackPoints, selectedTheme.accentColor, showStartMarker, showEndMarker, Modifier.fillMaxSize())
                    }
                    if (selectedMapMode != MapDisplayMode.VECTOR && mapBgStyle == MapBgStyle.MAP_TILES) {
                        Box(Modifier.fillMaxSize().background(ComposeColor.Black.copy(alpha = 0.35f)))
                    }
                }

                if (showGridInMap && posterBgStyle == PosterBgStyle.PATTERN_GRID) {
                    ComposeCanvas(Modifier.fillMaxSize()) {
                        val gc = selectedTheme.accentColor.copy(alpha = 0.065f)
                        val gap = 38.dp.toPx()
                        var x = 0f; while (x < size.width) { drawLine(gc, Offset(x, 0f), Offset(x, size.height), 0.6f); x += gap }
                        var y = 0f; while (y < size.height) { drawLine(gc, Offset(0f, y), Offset(size.width, y), 0.6f); y += gap }
                    }
                }

                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    // Header inside poster
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).background(selectedTheme.accentColor, CircleShape))
                                Spacer(Modifier.width(5.dp))
                                Text("RUTE PERJALANAN HARIAN", fontWeight = FontWeight.Black, fontSize = 8.sp, letterSpacing = 1.2.sp, color = selectedTheme.accentColor)
                            }
                            Spacer(Modifier.height(2.dp))
                            if (showWatermark && driverNameInput.isNotBlank()) {
                                Text(driverNameInput.uppercase(), fontWeight = FontWeight.Black, fontSize = 14.sp, color = if (posterBgStyle == PosterBgStyle.MAP_OSM) ComposeColor.White else posterTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(formattedDateTitle, fontSize = 8.sp, color = if (posterBgStyle == PosterBgStyle.MAP_OSM) ComposeColor.White.copy(alpha = 0.7f) else posterTextColor.copy(alpha = 0.55f))
                        }
                    }

                    // Map card frame
                    if (posterBgStyle != PosterBgStyle.MAP_OSM) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (mapBgStyle != MapBgStyle.TRANSPARENT) {
                                        Modifier.background(
                                            when (mapBgStyle) {
                                                MapBgStyle.MAP_TILES -> selectedTheme.cardColor.copy(alpha = 0.32f)
                                                MapBgStyle.SOLID -> selectedTheme.cardColor
                                                else -> selectedTheme.cardColor.copy(alpha = 0.32f)
                                            }
                                        ).border(BorderStroke(1.dp, selectedTheme.accentColor.copy(alpha = 0.1f)), RoundedCornerShape(12.dp))
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (snappedTrackPoints.isNotEmpty()) {
                                if (selectedMapMode == MapDisplayMode.VECTOR) {
                                    RoutePreviewCanvas(snappedTrackPoints, selectedTheme.accentColor, showStartMarker, showEndMarker, Modifier.fillMaxSize())
                                } else if (mapBgStyle == MapBgStyle.MAP_TILES) {
                                    MapViewComposable(viewModel, snappedTrackPoints, selectedTheme.accentColor, true, mapBgStyle, selectedTheme.cardColor, selectedMapMode, selectedColorPreset, manualOverlayColor, manualMapBgColor, showStartMarker, showEndMarker) { mapViewRef = it }
                                } else {
                                    RoutePreviewCanvas(snappedTrackPoints, selectedTheme.accentColor, showStartMarker, showEndMarker, Modifier.fillMaxSize())
                                }
                            } else {
                                ComposeCanvas(Modifier.fillMaxSize()) {
                                    val cx = size.width / 2f; val cy = size.height / 2f
                                    drawCircle(selectedTheme.accentColor.copy(alpha = 0.18f), size.minDimension / 3.5f, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                                    drawLine(selectedTheme.accentColor.copy(alpha = 0.22f), Offset(0f, cy), Offset(size.width, cy), 1f)
                                    drawLine(selectedTheme.accentColor.copy(alpha = 0.22f), Offset(cx, 0f), Offset(cx, size.height), 1f)
                                    drawCircle(selectedTheme.accentColor, 6f, Offset(cx, cy))
                                }
                                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.Route, "No Data", tint = selectedTheme.accentColor.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.height(4.dp))
                                    Text("Belum ada rute", color = selectedTheme.accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Bottom Stats row inside poster
                    val pStats = mutableListOf<Pair<String, String>>()
                    if (showDistance) pStats.add((if (distanceType == "antar") "JARAK ANTAR" else "JARAK TEMPUH") to String.format(Locale.US, "%.2f km", totalDistance))
                    if (showEarnings) pStats.add("PENDAPATAN" to currencyFormatter.format(earningsToShow))
                    if (showDuration) pStats.add("DURASI" to "${totalDurationMin / 60}j ${totalDurationMin % 60}m")
                    if (showTrips) pStats.add("MUTASI" to "$orderCount Trip")

                    if (pStats.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (posterBgStyle == PosterBgStyle.MAP_OSM || posterBgStyle == PosterBgStyle.TRANSPARENT) {
                                        Modifier
                                            .background(ComposeColor.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                            .padding(vertical = 6.dp, horizontal = 4.dp)
                                    } else Modifier
                                ),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            pStats.forEach { (lbl, valStr) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = lbl,
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (posterBgStyle == PosterBgStyle.MAP_OSM || posterBgStyle == PosterBgStyle.TRANSPARENT) ComposeColor.White.copy(alpha = 0.7f) else posterTextColor.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = valStr,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = selectedTheme.accentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Action Buttons (Save & Share right below the poster preview!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (dayOrders.isEmpty() && consolidatedTrackPoints.isEmpty()) {
                            Toast.makeText(context, "Tidak ada riwayat order pada tanggal ini.", Toast.LENGTH_LONG).show()
                            return@OutlinedButton
                        }
                        val mapBitmap = mapViewRef?.let { view ->
                            try {
                                val bm = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                                view.draw(Canvas(bm))
                                bm
                            } catch (e: Exception) { null }
                        }
                        saveTrackPoster(context, selectedDate, formattedDateTitle, if (showWatermark) driverNameInput else "", selectedTheme, consolidatedTrackPoints, showDistance, totalDistance, distanceType, showEarnings, earningsToShow, showDuration, totalDurationMin, showTrips, orderCount, showGridInMap, posterBgStyle, mapBgStyle, selectedTheme.accentColor.toArgb(), currencyFormatter, mapBitmap, false)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, GojekGreen),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GojekGreen)
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Simpan Gambar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        if (dayOrders.isEmpty() && consolidatedTrackPoints.isEmpty()) {
                            Toast.makeText(context, "Tidak ada riwayat order pada tanggal ini.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        val mapBitmap = mapViewRef?.let { view ->
                            try {
                                val bm = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                                view.draw(Canvas(bm))
                                bm
                            } catch (e: Exception) { null }
                        }
                        saveTrackPoster(context, selectedDate, formattedDateTitle, if (showWatermark) driverNameInput else "", selectedTheme, consolidatedTrackPoints, showDistance, totalDistance, distanceType, showEarnings, earningsToShow, showDuration, totalDurationMin, showTrips, orderCount, showGridInMap, posterBgStyle, mapBgStyle, selectedTheme.accentColor.toArgb(), currencyFormatter, mapBitmap, true)
                    },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("strava_generate_share_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = GojekGreen),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(2.dp)
                ) {
                    Icon(Icons.Default.Share, null, tint = ComposeColor.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Bagikan Rute", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ComposeColor.White)
                }
            }

            // Section 4: Sumber Latar Poster + Rasio
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Sumber latar
                    Text("SUMBER LATAR POSTER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("peta" to "Peta", "warna" to "Warna", "galeri" to "Gambar Galeri").forEach { (v, lbl) ->
                            val isSel = bgSource == v
                            Button(
                                onClick = {
                                    bgSource = v
                                    if (v == "galeri") galleryLauncher.launch("image/*")
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isSel) GojekGreen else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Text(lbl, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSel) ComposeColor.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    // Warna picker (kalau pilih Warna)
                    if (bgSource == "warna") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0xFF041E15 to "Hijau Tua", 0xFF0A3320 to "Forest", 0xFFFC6100 to "Orange", 0xFF1B1D21 to "Hitam", 0xFFE53935 to "Merah", 0xFFF5F5F5 to "Putih").forEach { (c, _) ->
                                val col = ComposeColor(c)
                                Box(
                                    modifier = Modifier.size(30.dp).clip(CircleShape)
                                        .background(col)
                                        .border(2.dp, if (solidColor == col) GojekGreen else ComposeColor.Transparent, CircleShape)
                                        .clickable { solidColor = col }
                                )
                            }
                        }
                    }

                    // Galeri thumbnail (kalau pilih Galeri)
                    if (bgSource == "galeri") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (galleryUri != null) "Gambar dipilih ✓" else "Belum ada gambar",
                                fontSize = 11.sp, color = GojekGreen, fontWeight = FontWeight.Bold
                            )
                            OutlinedButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.5.dp, GojekGreen),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GojekGreen)
                            ) {
                                Icon(Icons.Default.Image, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ganti Gambar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Rasio ekspor
                    Text("RASIO EKSPOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("4:3" to "4:3", "vertikal" to "Vertikal (Story)", "wide" to "Wide").forEach { (v, lbl) ->
                            val isSel = exportRatio == v
                            Button(
                                onClick = { exportRatio = v },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isSel) GojekGreen else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Text(lbl, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSel) ComposeColor.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    // Tombol buka bottom sheet kustomisasi
                    OutlinedButton(
                        onClick = { showCustomSheet = true },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.5.dp, GojekGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GojekGreen)
                    ) {
                        Icon(Icons.Default.Tune, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sesuaikan Lainnya", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // Section 5: Expandable Accordion for Advanced Settings
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Settings, null, tint = GojekGreen, modifier = Modifier.size(18.dp))
                            Text("PENGATURAN KUSTOMISASI LANJUTAN", fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.3.sp)
                        }
                        Icon(if (isAdvancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (isAdvancedExpanded) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = driverNameInput,
                                onValueChange = { driverNameInput = it },
                                label = { Text("Nama Pengendara (Watermark)") },
                                modifier = Modifier.fillMaxWidth().testTag("strava_driver_name_input"),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Person, null, tint = GojekGreen) },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GojekGreen, focusedLabelColor = GojekGreen),
                                shape = RoundedCornerShape(10.dp)
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))

                            Text("JENIS LAYANAN:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf("Semua", "Penumpang", "Paket", "Makanan").forEach { t ->
                                    val isS = orderTypeFilter == t
                                    FilterChip(
                                        selected = isS,
                                        onClick = { orderTypeFilter = t },
                                        label = { Text(t, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GojekGreen, selectedLabelColor = ComposeColor.White),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))

                            Text("METODE PENGUKURAN JARAK:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("total" to "Total Tempuh", "antar" to "Jarak Antar").forEach { (v, l) ->
                                    val isS = distanceType == v
                                    FilterChip(
                                        selected = isS,
                                        onClick = { distanceType = v },
                                        label = { Text(l, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GojekGreen, selectedLabelColor = ComposeColor.White),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))

                            Text("MARKER PETA (START & END):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))) {
                                ToggleRow(showStartMarker, { showStartMarker = it }, Icons.Default.Room, "Titik Awal (Start Marker)", "Tampilkan ikon awal (A)", GojekGreen)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                                ToggleRow(showEndMarker, { showEndMarker = it }, Icons.Default.Room, "Titik Akhir (End Marker)", "Tampilkan ikon akhir (B)", ComposeColor(0xFFE53935))
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))

                            Text("TAMPILKAN ELEMEN STATS:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val dText = if (distanceType == "antar") "Jarak Antar" else "Jarak Tempuh"
                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))) {
                                ToggleRow(showDistance, { showDistance = it }, Icons.Default.Route, dText, String.format(Locale.getDefault(), "%.1f km", totalDistance), GojekGreen)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                                ToggleRow(showEarnings, { showEarnings = it }, Icons.Default.Payments, "Pendapatan", currencyFormatter.format(earningsToShow), GojekYellow)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                                ToggleRow(showDuration, { showDuration = it }, Icons.Default.Timer, "Durasi", "${totalDurationMin / 60}j ${totalDurationMin % 60}m", ComposeColor(0xFF00E5FF))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                                ToggleRow(showTrips, { showTrips = it }, Icons.Default.ConfirmationNumber, "Jumlah Order", "$orderCount Trip", ComposeColor(0xFFFF6B35))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                                ToggleRow(showWatermark, { showWatermark = it }, Icons.Default.Person, "Nama Pengendara", driverNameInput.ifEmpty { "(belum diisi)" }, ComposeColor(0xFFBB86FC))
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                            Text("OVERRIDE MANUAL / GAYA CUSTOM:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GojekGreen)

                            Text("LATAR BELAKANG POSTER:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PosterBgStyle.entries.forEach { style ->
                                    val isS = posterBgStyle == style
                                    FilterChip(
                                        selected = isS,
                                        onClick = { posterBgStyle = style; selectedTemplatePreset = null },
                                        label = { Text(style.label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GojekGreen, selectedLabelColor = ComposeColor.White)
                                    )
                                }
                            }

                            Text("TEMA WARNA ACCENT:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StravaCardTheme.entries.forEach { tm ->
                                    val isS = selectedTheme == tm
                                    FilterChip(
                                        selected = isS,
                                        onClick = { selectedTheme = tm; selectedTemplatePreset = null },
                                        label = { Text(tm.displayName, fontSize = 9.5.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GojekGreen, selectedLabelColor = ComposeColor.White)
                                    )
                                }
                            }

                            Text("GAYA FRAME WILAYAH PETA:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MapBgStyle.entries.forEach { mb ->
                                    val isS = mapBgStyle == mb
                                    FilterChip(
                                        selected = isS,
                                        onClick = { mapBgStyle = mb; selectedTemplatePreset = null },
                                        label = { Text(mb.label, fontSize = 9.5.sp, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GojekGreen, selectedLabelColor = ComposeColor.White)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            if (showCustomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showCustomSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Sesuaikan Poster", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GojekGreen)
                        HorizontalDivider()

                        Text("NAMA PENGENDARA (WATERMARK)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = driverNameInput,
                            onValueChange = { driverNameInput = it },
                            label = { Text("Nama Pengendara") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = GojekGreen) },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GojekGreen, focusedLabelColor = GojekGreen),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Text("JENIS LAYANAN:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("Semua", "Penumpang", "Paket", "Makanan").forEach { t ->
                                FilterChip(
                                    selected = orderTypeFilter == t,
                                    onClick = { orderTypeFilter = t },
                                    label = { Text(t, fontSize = 9.5.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GojekGreen, selectedLabelColor = ComposeColor.White),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Text("TAMPILKAN ELEMEN STATS:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))) {
                            val dText = if (distanceType == "antar") "Jarak Antar" else "Jarak Tempuh"
                            ToggleRow(showDistance, { showDistance = it }, Icons.Default.Route, dText, String.format(Locale.getDefault(), "%.1f km", totalDistance), GojekGreen)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                            ToggleRow(showEarnings, { showEarnings = it }, Icons.Default.Payments, "Pendapatan", currencyFormatter.format(earningsToShow), GojekYellow)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                            ToggleRow(showDuration, { showDuration = it }, Icons.Default.Timer, "Durasi", "${totalDurationMin / 60}j ${totalDurationMin % 60}m", ComposeColor(0xFF00E5FF))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                            ToggleRow(showTrips, { showTrips = it }, Icons.Default.ConfirmationNumber, "Jumlah Order", "$orderCount Trip", ComposeColor(0xFFFF6B35))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                            ToggleRow(showWatermark, { showWatermark = it }, Icons.Default.Person, "Nama Pengendara", driverNameInput.ifEmpty { "(belum diisi)" }, ComposeColor(0xFFBB86FC))
                        }

                        Text("MARKER PETA (START & END):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))) {
                            ToggleRow(showStartMarker, { showStartMarker = it }, Icons.Default.Room, "Titik Awal (Start Marker)", "Tampilkan ikon awal (A)", GojekGreen)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                            ToggleRow(showEndMarker, { showEndMarker = it }, Icons.Default.Room, "Titik Akhir (End Marker)", "Tampilkan ikon akhir (B)", ComposeColor(0xFFE53935))
                        }

                        Button(
                            onClick = { showCustomSheet = false },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GojekGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Selesai", fontWeight = FontWeight.Bold, color = ComposeColor.White)
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────────
@Composable
private fun SectionTitle(number: String, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(GojekGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .border(1.dp, GojekGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = GojekGreen
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title.uppercase(),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun ToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: ComposeColor
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(tint.copy(alpha = if (checked) 0.18f else 0.07f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (checked) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(17.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                value,
                fontSize = 11.sp,
                color = if (checked) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ComposeColor.White,
                checkedTrackColor = tint,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun QuickStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    customBadge: (@Composable () -> Unit)? = null,
    label: String,
    value: String,
    tint: ComposeColor,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.Start) {
            if (customBadge != null) {
                customBadge()
            } else if (icon != null) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(5.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RoutePreviewCanvas(
    trackPoints: List<TrackPoint>,
    accentColor: ComposeColor,
    showStartMarker: Boolean,
    showEndMarker: Boolean,
    modifier: Modifier = Modifier
) {
    ComposeCanvas(modifier = modifier.fillMaxSize()) {
        if (trackPoints.isEmpty()) return@ComposeCanvas
        
        val lats = trackPoints.map { it.lat }
        val lngs = trackPoints.map { it.lng }
        val minLat = lats.min()
        val maxLat = lats.max()
        val minLng = lngs.min()
        val maxLng = lngs.max()
        
        val latRange = (maxLat - minLat).coerceAtLeast(0.0001)
        val lngRange = (maxLng - minLng).coerceAtLeast(0.0001)
        
        val padding = size.minDimension * 0.15f
        val drawW = size.width - padding * 2
        val drawH = size.height - padding * 2
        
        val scale = kotlin.math.min(drawW / lngRange, drawH / latRange)
        
        val offsetX = (size.width - lngRange * scale) / 2
        val offsetY = (size.height - latRange * scale) / 2
        
        val pts = trackPoints.map { pt ->
            val x = (pt.lng - minLng) * scale + offsetX
            val y = (maxLat - pt.lat) * scale + offsetY
            Offset(x.toFloat(), y.toFloat())
        }
        
        val path = androidx.compose.ui.graphics.Path().apply {
            if (pts.isNotEmpty()) {
                moveTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) {
                    lineTo(pts[i].x, pts[i].y)
                }
            }
        }
        
        drawPath(
            path = path,
            color = accentColor.copy(alpha = 0.15f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 14.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )

        drawPath(
            path = path,
            color = accentColor.copy(alpha = 0.35f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 8.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )

        drawPath(
            path = path,
            color = accentColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 4.50.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
        
        if (showStartMarker && pts.isNotEmpty()) {
            drawCircle(
                color = GojekGreen.copy(alpha = 0.3f),
                radius = 11.dp.toPx(),
                center = pts.first()
            )
            drawCircle(
                color = GojekGreen,
                radius = 6.5.dp.toPx(),
                center = pts.first()
            )
            drawCircle(
                color = ComposeColor.White,
                radius = 3.dp.toPx(),
                center = pts.first()
            )
        }
        
        if (showEndMarker && pts.isNotEmpty()) {
            val finishColor = ComposeColor(0xFFE53935)
            drawCircle(
                color = finishColor.copy(alpha = 0.3f),
                radius = 11.dp.toPx(),
                center = pts.last()
            )
            drawCircle(
                color = finishColor,
                radius = 6.5.dp.toPx(),
                center = pts.last()
            )
            drawCircle(
                color = ComposeColor.White,
                radius = 3.dp.toPx(),
                center = pts.last()
            )
        }
    }
}

// ─── Poster Generator ──────────────────────────────────────────────────
private fun saveTrackPoster(
    context: Context,
    selectedDate: String,
    formattedDateTitle: String,
    driverName: String,
    theme: StravaCardTheme,
    trackPoints: List<TrackPoint>,
    showDistance: Boolean,
    totalDistance: Double,
    distanceType: String,
    showEarnings: Boolean,
    earnings: Double,
    showDuration: Boolean,
    durationMin: Int,
    showTrips: Boolean,
    tripsCount: Int,
    showGrid: Boolean,
    bgStyle: PosterBgStyle,
    mapBgStyle: MapBgStyle,
    accentColor: Int,
    currencyFormatter: NumberFormat,
    mapBitmap: Bitmap?,
    shareAfterSave: Boolean
) {
    try {
        val SIZE = 1080
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // ── Background ───────────────────────────────────────────────
        when (bgStyle) {
            PosterBgStyle.MAP_OSM -> {
                if (mapBitmap != null) {
                    canvas.drawBitmap(mapBitmap, null, RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat()), Paint().apply { isAntiAlias = true })
                } else {
                    val p = Paint().apply { color = theme.bgColor.toArgb(); style = Paint.Style.FILL; isAntiAlias = true }
                    canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), p)
                }
                canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Paint().apply {
                    color = android.graphics.Color.BLACK
                    alpha = (255 * 0.35f).toInt()
                    style = Paint.Style.FILL
                })
            }
            PosterBgStyle.TRANSPARENT -> {
                // Keep the canvas transparent (do not draw background solid color)
            }
            PosterBgStyle.DARK_SOLID -> {
                val p = Paint().apply { color = theme.bgColor.toArgb(); style = Paint.Style.FILL; isAntiAlias = true }
                canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), p)
            }
            PosterBgStyle.LIGHT_SOLID -> {
                val p = Paint().apply { color = android.graphics.Color.parseColor("#F5F5F5"); style = Paint.Style.FILL; isAntiAlias = true }
                canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), p)
            }
            PosterBgStyle.GRADIENT_DARK -> {
                val shader = android.graphics.LinearGradient(0f, 0f, 0f, SIZE.toFloat(), intArrayOf(0xFF1A1A2E.toInt(), 0xFF0F0F1A.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
                val p = Paint().apply { this.shader = shader; style = Paint.Style.FILL; isAntiAlias = true }
                canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), p)
            }
            PosterBgStyle.GRADIENT_GREEN -> {
                val shader = android.graphics.LinearGradient(0f, 0f, 0f, SIZE.toFloat(), intArrayOf(0xFF0A3320.toInt(), 0xFF041E15.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
                val p = Paint().apply { this.shader = shader; style = Paint.Style.FILL; isAntiAlias = true }
                canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), p)
            }
            PosterBgStyle.PATTERN_GRID -> {
                val p = Paint().apply { color = theme.bgColor.toArgb(); style = Paint.Style.FILL; isAntiAlias = true }
                canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), p)
            }
        }

        val textColorInt = if (bgStyle == PosterBgStyle.LIGHT_SOLID) {
            android.graphics.Color.parseColor("#111111")
        } else {
            android.graphics.Color.WHITE
        }

        if (showGrid && bgStyle == PosterBgStyle.PATTERN_GRID) {
            val gp = Paint().apply { color = accentColor; alpha = (255 * 0.07f).toInt(); strokeWidth = 1f; style = Paint.Style.STROKE; isAntiAlias = true }
            var x = 0f; while (x < SIZE) { canvas.drawLine(x, 0f, x, SIZE.toFloat(), gp); x += 42f }
            var y = 0f; while (y < SIZE) { canvas.drawLine(0f, y, SIZE.toFloat(), y, gp); y += 42f }
        }

        // ── Header ────────────────────────────────────────────────────
        val dotPaint = Paint().apply { color = accentColor; isAntiAlias = true }
        canvas.drawCircle(88f, 98f, 11f, dotPaint)
        val labelP = Paint().apply { color = accentColor; textSize = 25f; isFakeBoldText = true; letterSpacing = 0.12f; isAntiAlias = true }
        canvas.drawText("RUTE PERJALANAN HARIAN", 112f, 106f, labelP)

        if (driverName.isNotBlank()) {
            val nameP = Paint().apply { color = textColorInt; textSize = 50f; isFakeBoldText = true; isAntiAlias = true }
            canvas.drawText(driverName.uppercase().take(22), 88f, 174f, nameP)
        }
        val dateP = Paint().apply { color = textColorInt; alpha = (255 * 0.55f).toInt(); textSize = 25f; isAntiAlias = true }
        canvas.drawText(formattedDateTitle, 88f, if (driverName.isNotBlank()) 212f else 152f, dateP)

        // ── Map Area/Frame Drawing ────────────────────────────────────
        val mapTop = if (driverName.isNotBlank()) 246f else 186f
        val mapRect = RectF(88f, mapTop, 992f, 738f)

        if (bgStyle != PosterBgStyle.MAP_OSM) {
            if (mapBgStyle != MapBgStyle.TRANSPARENT) {
                if (mapBitmap == null || mapBitmap.isRecycled) {
                    val framePaint = Paint().apply { color = theme.cardColor.toArgb(); alpha = (255 * 0.38f).toInt(); isAntiAlias = true; style = Paint.Style.FILL }
                    canvas.drawRoundRect(mapRect, 32f, 32f, framePaint)
                }
                val borderP = Paint().apply { color = textColorInt; alpha = (255 * 0.06f).toInt(); strokeWidth = 2f; style = Paint.Style.STROKE; isAntiAlias = true }
                canvas.drawRoundRect(mapRect, 32f, 32f, borderP)
            }

            if (mapBitmap != null && !mapBitmap.isRecycled) {
                canvas.drawBitmap(mapBitmap, null, mapRect, Paint().apply { isAntiAlias = true })
            } else if (trackPoints.isNotEmpty()) {
                val lats = trackPoints.map { it.lat }
                val lngs = trackPoints.map { it.lng }
                val minLat = lats.minOrNull() ?: 0.0
                val maxLat = lats.maxOrNull() ?: 0.0
                val minLng = lngs.minOrNull() ?: 0.0
                val maxLng = lngs.maxOrNull() ?: 0.0
                
                val latRange = (maxLat - minLat).coerceAtLeast(0.0001)
                val lngRange = (maxLng - minLng).coerceAtLeast(0.0001)
                
                val mapWidth = mapRect.width()
                val mapHeight = mapRect.height()
                val minDimension = kotlin.math.min(mapWidth, mapHeight)
                val padding = minDimension * 0.15f
                val drawW = mapWidth - padding * 2
                val drawH = mapHeight - padding * 2
                
                val scale = kotlin.math.min(drawW / lngRange, drawH / latRange)
                val offsetX = mapRect.left + (mapWidth - lngRange * scale) / 2f
                val offsetY = mapRect.top + (mapHeight - latRange * scale) / 2f
                
                val pts = trackPoints.map { pt ->
                    val x = (pt.lng - minLng) * scale + offsetX
                    val y = (maxLat - pt.lat) * scale + offsetY
                    android.graphics.PointF(x.toFloat(), y.toFloat())
                }
                
                if (pts.isNotEmpty()) {
                    val path = Path()
                    path.moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        path.lineTo(pts[i].x, pts[i].y)
                    }
                    
                    val outerPaint = Paint().apply {
                        color = accentColor
                        alpha = (255 * 0.15f).toInt()
                        strokeWidth = 28f
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        isAntiAlias = true
                    }
                    canvas.drawPath(path, outerPaint)

                    val innerGlowPaint = Paint().apply {
                        color = accentColor
                        alpha = (255 * 0.35f).toInt()
                        strokeWidth = 16f
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        isAntiAlias = true
                    }
                    canvas.drawPath(path, innerGlowPaint)

                    val corePaint = Paint().apply {
                        color = accentColor
                        strokeWidth = 9f
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        isAntiAlias = true
                    }
                    canvas.drawPath(path, corePaint)
                    
                    val startGlow = Paint().apply { color = 0xFF4CAF50.toInt(); alpha = (255 * 0.3f).toInt(); style = Paint.Style.FILL; isAntiAlias = true }
                    val startPaintOuter = Paint().apply { color = 0xFF4CAF50.toInt(); style = Paint.Style.FILL; isAntiAlias = true }
                    val startPaintInner = Paint().apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
                    canvas.drawCircle(pts[0].x, pts[0].y, 22f, startGlow)
                    canvas.drawCircle(pts[0].x, pts[0].y, 13f, startPaintOuter)
                    canvas.drawCircle(pts[0].x, pts[0].y, 6f, startPaintInner)
                    
                    val finishColor = 0xFFE53935.toInt()
                    val finishGlow = Paint().apply { color = finishColor; alpha = (255 * 0.3f).toInt(); style = Paint.Style.FILL; isAntiAlias = true }
                    val finishPaintOuter = Paint().apply { color = finishColor; style = Paint.Style.FILL; isAntiAlias = true }
                    canvas.drawCircle(pts.last().x, pts.last().y, 22f, finishGlow)
                    canvas.drawCircle(pts.last().x, pts.last().y, 13f, finishPaintOuter)
                    canvas.drawCircle(pts.last().x, pts.last().y, 6f, startPaintInner)
                }
            }
        }

        if (trackPoints.isEmpty() && bgStyle != PosterBgStyle.MAP_OSM) {
            val rP = Paint().apply { color = accentColor; style = Paint.Style.STROKE; isAntiAlias = true }
            rP.alpha = (255 * 0.2f).toInt(); rP.strokeWidth = 3f
            canvas.drawCircle(mapRect.centerX(), mapRect.centerY(), 120f, rP)
            rP.alpha = (255 * 0.1f).toInt(); rP.strokeWidth = 1.5f
            canvas.drawCircle(mapRect.centerX(), mapRect.centerY(), 200f, rP)
            rP.alpha = (255 * 0.28f).toInt()
            canvas.drawLine(mapRect.left + 50f, mapRect.centerY(), mapRect.right - 50f, mapRect.centerY(), rP)
            canvas.drawLine(mapRect.centerX(), mapRect.top + 35f, mapRect.centerX(), mapRect.bottom - 35f, rP)
            rP.style = Paint.Style.FILL; rP.alpha = 255
            canvas.drawCircle(mapRect.centerX(), mapRect.centerY(), 10f, rP)
        }

        // ── Stats ─────────────────────────────────────────────────────
        val statsList = mutableListOf<Pair<String, String>>()
        if (showDistance) statsList.add((if (distanceType == "antar") "JARAK ANTAR" else "JARAK TEMPUH") to String.format(Locale.US, "%.2f km", totalDistance))
        if (showEarnings) statsList.add("PENDAPATAN" to currencyFormatter.format(earnings))
        if (showDuration) { val h = durationMin / 60; val m = durationMin % 60; statsList.add("DURASI" to "${h}j ${m}m") }
        if (showTrips) statsList.add("MUTASI" to "$tripsCount Trip")

        if (statsList.isNotEmpty()) {
            val cellW = 904f / statsList.size
            val statsTop = 760f

            if (bgStyle == PosterBgStyle.MAP_OSM || bgStyle == PosterBgStyle.TRANSPARENT) {
                canvas.drawRoundRect(RectF(88f, statsTop, 992f, 876f), 20f, 20f, Paint().apply {
                    color = android.graphics.Color.BLACK
                    alpha = (255 * 0.4f).toInt()
                    style = Paint.Style.FILL
                    isAntiAlias = true
                })
            }

            for (i in statsList.indices) {
                val cx = 88f + i * cellW + cellW / 2f
                val lp = Paint().apply {
                    color = if (bgStyle == PosterBgStyle.MAP_OSM || bgStyle == PosterBgStyle.TRANSPARENT) android.graphics.Color.WHITE else textColorInt
                    alpha = if (bgStyle == PosterBgStyle.MAP_OSM || bgStyle == PosterBgStyle.TRANSPARENT) (255 * 0.7f).toInt() else (255 * 0.5f).toInt()
                    textSize = 21f
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                    letterSpacing = 0.06f
                    isAntiAlias = true
                }
                canvas.drawText(statsList[i].first, cx, statsTop + 38f, lp)
                val vp = Paint().apply { color = accentColor; textSize = 38f; isFakeBoldText = true; textAlign = Paint.Align.CENTER; isAntiAlias = true }
                canvas.drawText(statsList[i].second, cx, statsTop + 86f, vp)
            }
        }

        // ── Save/Share to MediaStore Library ───────────────────────
        val fileName = "track_poster_$selectedDate.png"
        var savedUri: Uri? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DriverTracker")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { os: OutputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                }
                savedUri = uri
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "DriverTracker")
            dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { fos -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos) }
            savedUri = Uri.fromFile(file)
        }

        if (!shareAfterSave) {
            Toast.makeText(context, "Poster berhasil disimpan di galeri.", Toast.LENGTH_LONG).show()
            return
        }

        // ── share ────────────────────────────────────────────────────
        val cacheDir = File(context.cacheDir, "shared_images")
        cacheDir.mkdirs()
        val cacheFile = File(cacheDir, "track_share_$selectedDate.png")
        FileOutputStream(cacheFile).use { fos -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos) }
        val shareUri: Uri = FileProvider.getUriForFile(context, "com.example.fileprovider", cacheFile)

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, shareUri)
            type = "image/png"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Bagikan Track Harian"))

    } catch (e: Exception) {
        Toast.makeText(context, "Gagal buat poster: ${e.message}", Toast.LENGTH_LONG).show()
        e.printStackTrace()
    }
}

// ─── Map Matching API (Road Snap) ───────────────────────────
private suspend fun snapRouteToRoads(points: List<TrackPoint>): List<TrackPoint> = withContext(Dispatchers.IO) {
    if (points.size < 2) return@withContext points

    try {
        val coords = points.joinToString(";") { "${it.lng},${it.lat}" }
        val url = URL("https://router.project-osrm.org/match/v1/driving/$coords?overview=full&geometries=geojson")
        val connection = url.openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            if (json.optString("code") == "Ok") {
                val matchings = json.optJSONArray("matchings")
                if (matchings != null && matchings.length() > 0) {
                    val geometry = matchings.getJSONObject(0).getJSONObject("geometry")
                    val coordinates = geometry.getJSONArray("coordinates")
                    val snappedPoints = mutableListOf<TrackPoint>()
                    for (i in 0 until coordinates.length()) {
                        val coord = coordinates.getJSONArray(i)
                        snappedPoints.add(TrackPoint(coord.getDouble(1), coord.getDouble(0), 0L))
                    }
                    return@withContext snappedPoints
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    points
}
