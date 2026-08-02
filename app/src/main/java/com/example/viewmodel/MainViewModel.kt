package com.example.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateListOf
import com.example.data.*
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

enum class WorkflowState {
    IDLE,
    STARTED,
    PICKED_UP
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private var activeInstanceRef: java.lang.ref.WeakReference<MainViewModel>? = null

        var activeInstance: MainViewModel?
            get() = activeInstanceRef?.get()
            set(value) {
                activeInstanceRef = java.lang.ref.WeakReference(value)
            }
    }

    private val repository: OrderRepository
    val allOrders: StateFlow<List<OrderRecord>>

    // GPS Settings & States
    var trackingIntervalSec by mutableStateOf(5) // Default 5 seconds
    var useGpsSimulator by mutableStateOf(false)
    var gpsWarningMessage by mutableStateOf<String?>(null)
    var smartHeatmapEnabled by mutableStateOf(true)
    var holidayAnalysisEnabled by mutableStateOf(true)
    var weatherAnalysisEnabled by mutableStateOf(true)
    var currentWeatherCondition by mutableStateOf("Cerah")
    
    // Theme state (true = dark/mode malam, false = light/mode siang)
    var isDarkMode by mutableStateOf(true) // Default to Gojek dark theme for style

    // Map Source states ("osm", "google_maps", "manual_pbf")
    var mapSource by mutableStateOf("osm")
    var importedPbfFileName by mutableStateOf<String?>(null)
    var importedPbfFileSize by mutableStateOf<String?>(null)
    var importedPbfUri by mutableStateOf<String?>(null) // Persisted URI of the imported map file

    // ONNX ML Model states loaded from external sources
    var importedOnnxFileName by mutableStateOf<String?>(null)
    var importedOnnxFileSize by mutableStateOf<String?>(null)
    var importedOnnxUri by mutableStateOf<String?>(null)
    var useOnnxModelPrediction by mutableStateOf(true)
    var onnxInputLayerName by mutableStateOf("lat_lng_time_features")
    var onnxOutputLayerName by mutableStateOf("predicted_hotspot_intensity_reg")
    var onnxModelTypeInfo by mutableStateOf("ResNet-DeepRegressor (ONNX v1.4)")

    // Navigation and screen state
    var currentScreen by mutableStateOf("rekam_order")

    // Real Location tracking states
    var lastKnownLatitude by mutableStateOf(-7.2754) // Default to Surabaya (ITS)
    var lastKnownLongitude by mutableStateOf(112.7938)
    var currentAddress by mutableStateOf("ITS Campus, Sukolilo, Surabaya")

    // Active order tracking variables
    var workflowState by mutableStateOf(WorkflowState.IDLE)
    
    var currentOrderDate by mutableStateOf("")
    var currentOrderDay by mutableStateOf("")
    var currentOrderStartTime by mutableStateOf("")
    var currentOrderPickupTime by mutableStateOf<String?>(null)
    var currentOrderStopTime by mutableStateOf<String?>(null)
    
    var startLat by mutableStateOf(0.0)
    var startLng by mutableStateOf(0.0)
    var startAddress by mutableStateOf("")
    
    var pickupLat by mutableStateOf<Double?>(null)
    var pickupLng by mutableStateOf<Double?>(null)
    var pickupAddress by mutableStateOf<String?>(null)
    
    var stopLat by mutableStateOf<Double?>(null)
    var stopLng by mutableStateOf<Double?>(null)
    var stopAddress by mutableStateOf<String?>(null)

    // Running values
    var activeTrackPoints = mutableStateListOf<TrackPoint>()
    var activeDistanceKm by mutableStateOf(0.0)
    var activeJarakKePickup by mutableStateOf(0.0)
    var activeJarakKeTujuan by mutableStateOf(0.0)
    var activeDurationMinutes by mutableStateOf(0L)
    var showSaveDialogDirectly by mutableStateOf(false)
    
    private var trackingStartTimeMillis = 0L
    private var timerJob: kotlinx.coroutines.Job? = null

    // Location engine
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)
    private var locationCallback: LocationCallback? = null
    private var nativeLocationListener: android.location.LocationListener? = null

    init {
        activeInstance = this
        val database = AppDatabase.getDatabase(application)
        repository = OrderRepository(database.orderDao())
        
        allOrders = repository.allOrders.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        loadLocalSettings()
        
        // Post first standby notification
        updateNotification()
    }

    private fun loadLocalSettings() {
        val prefs = getApplication<Application>().getSharedPreferences("driver_tracker_prefs", Context.MODE_PRIVATE)
        trackingIntervalSec = prefs.getInt("tracking_interval", 5)
        useGpsSimulator = prefs.getBoolean("use_gps_simulator", false)
        smartHeatmapEnabled = prefs.getBoolean("smart_heatmap", true)
        holidayAnalysisEnabled = prefs.getBoolean("holiday_analysis", true)
        weatherAnalysisEnabled = prefs.getBoolean("weather_analysis", true)
        currentWeatherCondition = prefs.getString("weather_condition", "Cerah") ?: "Cerah"
        isDarkMode = prefs.getBoolean("is_dark_mode", true)
        mapSource = prefs.getString("map_source", "osm") ?: "osm"
        if (mapSource == "google_maps") {
            mapSource = "osm"
        }
        importedPbfFileName = prefs.getString("imported_pbf_file_name", null)
        importedPbfFileSize = prefs.getString("imported_pbf_file_size", null)
        importedPbfUri = prefs.getString("imported_pbf_uri", null)
        importedOnnxFileName = prefs.getString("imported_onnx_file_name", null)
        importedOnnxFileSize = prefs.getString("imported_onnx_file_size", null)
        importedOnnxUri = prefs.getString("imported_onnx_uri", null)
        useOnnxModelPrediction = prefs.getBoolean("use_onnx_model_prediction", true)
        onnxInputLayerName = prefs.getString("onnx_input_layer", "lat_lng_time_features") ?: "lat_lng_time_features"
        onnxOutputLayerName = prefs.getString("onnx_output_layer", "predicted_hotspot_intensity_reg") ?: "predicted_hotspot_intensity_reg"
        onnxModelTypeInfo = prefs.getString("onnx_model_type", "ResNet-DeepRegressor (ONNX v1.4)") ?: "ResNet-DeepRegressor (ONNX v1.4)"
    }

    fun saveLocalSettings() {
        val prefs = getApplication<Application>().getSharedPreferences("driver_tracker_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("tracking_interval", trackingIntervalSec)
            putBoolean("use_gps_simulator", useGpsSimulator)
            putBoolean("smart_heatmap", smartHeatmapEnabled)
            putBoolean("holiday_analysis", holidayAnalysisEnabled)
            putBoolean("weather_analysis", weatherAnalysisEnabled)
            putString("weather_condition", currentWeatherCondition)
            putBoolean("is_dark_mode", isDarkMode)
            putString("map_source", mapSource)
            putString("imported_pbf_file_name", importedPbfFileName)
            putString("imported_pbf_file_size", importedPbfFileSize)
            putString("imported_pbf_uri", importedPbfUri)
            putString("imported_onnx_file_name", importedOnnxFileName)
            putString("imported_onnx_file_size", importedOnnxFileSize)
            putString("imported_onnx_uri", importedOnnxUri)
            putBoolean("use_onnx_model_prediction", useOnnxModelPrediction)
            putString("onnx_input_layer", onnxInputLayerName)
            putString("onnx_output_layer", onnxOutputLayerName)
            putString("onnx_model_type", onnxModelTypeInfo)
            apply()
        }
        // Re-setup GPS with new interval
        setupLocationUpdates()
    }

    fun removeOnnxModel() {
        importedOnnxFileName = null
        importedOnnxFileSize = null
        importedOnnxUri = null
        useOnnxModelPrediction = true
        saveLocalSettings()
    }

    private fun disableEmulatorFallback() {
        driftJob?.cancel()
        driftJob = null
    }

    @SuppressLint("MissingPermission")
    fun stopLocationUpdates() {
        locationCallback?.let {
            try {
                fusedLocationClient.removeLocationUpdates(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            locationCallback = null
        }
        nativeLocationListener?.let { listener ->
            try {
                val locationManager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                locationManager?.removeUpdates(listener)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            nativeLocationListener = null
        }
        disableEmulatorFallback()
    }

    @SuppressLint("MissingPermission")
    fun setupLocationUpdates() {
        val hasFine = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        // Stop any current listeners active
        stopLocationUpdates()

        if (useGpsSimulator) {
            // Attempt to fetch real last known location first to center the simulated drift around the real user
            val hasPermission = hasFine || hasCoarse
            if (hasPermission) {
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                        if (loc != null) {
                            updateCurrentLocation(loc)
                        }
                        startEmulatorDriftFallback()
                    }.addOnFailureListener {
                        startEmulatorDriftFallback()
                    }
                } catch (e: Exception) {
                    startEmulatorDriftFallback()
                }
            } else {
                startEmulatorDriftFallback()
            }
            return
        }

        if (!hasFine && !hasCoarse) {
            // No GPS permission AND simulator is OFF -> do NOT fake location.
            // Stop tracking and warn the user so they know data is not real.
            gpsWarningMessage = "Izin lokasi tidak diberikan. Pelacakan dihentikan — hidupkan GPS & beri izin lokasi."
            resetWorkflow()
            return
        }

        val locationManager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager

        // Instantly query last known location to center on real driver position
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    disableEmulatorFallback()
                    updateCurrentLocation(loc)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (locationManager != null) {
                val lastGps = if (hasFine) locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER) else null
                val lastNet = if (hasCoarse) locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) else null
                val bestLast = if (lastGps != null && lastNet != null) {
                    if (lastGps.time > lastNet.time) lastGps else lastNet
                } else lastGps ?: lastNet
                if (bestLast != null) {
                    disableEmulatorFallback()
                    updateCurrentLocation(bestLast)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Try FusedLocationProviderClient first as standard high accuracy provider
        var fusedStarted = false
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            trackingIntervalSec * 1000L
        ).apply {
            setMinUpdateIntervalMillis(trackingIntervalSec * 500L)
        }.build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                disableEmulatorFallback()
                val loc = locationResult.lastLocation ?: return
                updateCurrentLocation(loc)
            }
        }
        locationCallback = callback

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
            fusedStarted = true
        } catch (e: Exception) {
            e.printStackTrace()
            locationCallback = null
        }

        // Setup native LocationManager only if Fused location requested updates did not start (failsafe native fallback)
        if (!fusedStarted) {
            try {
                if (locationManager != null) {
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            disableEmulatorFallback()
                            updateCurrentLocation(loc)
                        }
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    nativeLocationListener = listener

                    var nativeStarted = false
                    if (hasFine && locationManager.allProviders.contains(android.location.LocationManager.GPS_PROVIDER)) {
                        locationManager.requestLocationUpdates(
                            android.location.LocationManager.GPS_PROVIDER,
                            trackingIntervalSec * 1000L,
                            0f,
                            listener,
                            Looper.getMainLooper()
                        )
                        nativeStarted = true
                    }
                    if (hasCoarse && locationManager.allProviders.contains(android.location.LocationManager.NETWORK_PROVIDER)) {
                        locationManager.requestLocationUpdates(
                            android.location.LocationManager.NETWORK_PROVIDER,
                            trackingIntervalSec * 1000L,
                            0f,
                            listener,
                            Looper.getMainLooper()
                        )
                        nativeStarted = true
                    }
                    if (!nativeStarted) {
                        nativeLocationListener = null
                        gpsWarningMessage = "GPS mati / tidak tersedia. Pelacakan dihentikan agar data tidak palsu. Nyalakan GPS lalu mulai lagi."
                        resetWorkflow()
                    }
                } else {
                    gpsWarningMessage = "Layanan lokasi tidak tersedia. Pelacakan dihentikan agar data tidak palsu."
                    resetWorkflow()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                nativeLocationListener = null
                startEmulatorDriftFallback()
            }
        }
    }

    private var driftJob: kotlinx.coroutines.Job? = null

    private fun startEmulatorDriftFallback() {
        driftJob?.cancel()
        driftJob = viewModelScope.launch {
            while (isActive) {
                delay(4000)
                // If workflow is active, simulate a slow movement/drift
                if (workflowState != WorkflowState.IDLE) {
                    val driftLat = (Random().nextDouble() - 0.5) * 0.0003
                    val driftLng = (Random().nextDouble() - 0.5) * 0.0003
                    val newLat = lastKnownLatitude + driftLat
                    val newLng = lastKnownLongitude + driftLng

                    val simulatedLoc = Location("simulated").apply {
                        latitude = newLat
                        longitude = newLng
                        time = System.currentTimeMillis()
                    }
                    updateCurrentLocation(simulatedLoc)
                }
            }
        }
    }

    private fun updateCurrentLocation(location: Location) {
        lastKnownLatitude = location.latitude
        lastKnownLongitude = location.longitude

        // Update active coordinates and distance if tracking is on
        if (workflowState != WorkflowState.IDLE) {
            val lastPoint = if (activeTrackPoints.isNotEmpty()) activeTrackPoints.last() else null
            val newPt = TrackPoint(location.latitude, location.longitude, System.currentTimeMillis())
            activeTrackPoints.add(newPt)

            if (lastPoint != null) {
                val results = FloatArray(1)
                Location.distanceBetween(
                    lastPoint.lat, lastPoint.lng,
                    newPt.lat, newPt.lng,
                    results
                )
                val distKm = results[0] / 1000.0
                activeDistanceKm += distKm
                if (workflowState == WorkflowState.STARTED) {
                    activeJarakKePickup += distKm
                } else if (workflowState == WorkflowState.PICKED_UP) {
                    activeJarakKeTujuan += distKm
                }
            }
        }

        // Fetch location address in background
        viewModelScope.launch {
            val addr = getAddressFromCoords(location.latitude, location.longitude)
            currentAddress = addr
            if (workflowState != WorkflowState.IDLE) {
                updateNotification()
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun getAddressFromCoords(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(getApplication(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val fullAddr = addr.getAddressLine(0)
                if (!fullAddr.isNullOrEmpty()) {
                    return@withContext fullAddr
                }
                val street = addr.thoroughfare ?: addr.subLocality ?: addr.locality
                if (!street.isNullOrEmpty()) {
                    return@withContext "$street, ${addr.subAdminArea ?: addr.adminArea ?: ""}"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext "%.5f, %.5f".format(lat, lng)
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    // WORKFLOW COMMANDS
    fun startOrder() {
        val sDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val sDay = SimpleDateFormat("EEEE", Locale.forLanguageTag("id-ID")).format(Date()) // Localized day in Indonesian
        val sTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        currentOrderDate = sDate
        currentOrderDay = sDay
        currentOrderStartTime = sTime
        
        startLat = lastKnownLatitude
        startLng = lastKnownLongitude
        startAddress = currentAddress

        activeDistanceKm = 0.0
        activeJarakKePickup = 0.0
        activeJarakKeTujuan = 0.0
        activeDurationMinutes = 0L
        activeTrackPoints.clear()
        // Add start point
        activeTrackPoints.add(TrackPoint(startLat, startLng, System.currentTimeMillis()))

        trackingStartTimeMillis = System.currentTimeMillis()
        workflowState = WorkflowState.STARTED
        startTimer()
        updateNotification()
    }

    fun pickupOrder() {
        val sTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        currentOrderPickupTime = sTime
        pickupLat = lastKnownLatitude
        pickupLng = lastKnownLongitude
        pickupAddress = currentAddress
        
        // Ensure starting tracker records new points
        activeTrackPoints.add(TrackPoint(lastKnownLatitude, lastKnownLongitude, System.currentTimeMillis()))
        workflowState = WorkflowState.PICKED_UP
        updateNotification()
    }

    fun completeOrder() {
        val sTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        currentOrderStopTime = sTime
        stopLat = lastKnownLatitude
        stopLng = lastKnownLongitude
        stopAddress = currentAddress

        stopTimer()
        // Duration in minutes
        val elapsed = System.currentTimeMillis() - trackingStartTimeMillis
        activeDurationMinutes = (elapsed / 60000).coerceAtLeast(1)
        showSaveDialogDirectly = true
    }

    fun saveCompletedOrder(jenis: String, pendapatan: Double, catatan: String) {
        viewModelScope.launch {
            val record = OrderRecord(
                tanggal = currentOrderDate,
                hari = currentOrderDay,
                jamMulai = currentOrderStartTime,
                jamPickup = currentOrderPickupTime,
                jamSelesai = currentOrderStopTime,
                jenisOrder = jenis,
                pendapatan = pendapatan,
                durasi = activeDurationMinutes,
                jarakTempuh = activeDistanceKm,
                jarakKePickup = activeJarakKePickup,
                jarakKeTujuan = activeJarakKeTujuan,
                latitudeAwal = startLat,
                longitudeAwal = startLng,
                alamatAwal = startAddress,
                latitudePickup = pickupLat,
                longitudePickup = pickupLng,
                alamatPickup = pickupAddress ?: "",
                latitudeAkhir = stopLat,
                longitudeAkhir = stopLng,
                alamatAkhir = stopAddress ?: "",
                catatan = catatan,
                trackGps = activeTrackPoints.toList().toJsonString()
            )
            repository.insert(record)
            resetWorkflow()
            showSaveDialogDirectly = false
        }
    }

    fun cancelActiveOrder() {
        resetWorkflow()
    }

    private fun resetWorkflow() {
        stopTimer()
        workflowState = WorkflowState.IDLE
        activeTrackPoints.clear()
        activeDistanceKm = 0.0
        activeJarakKePickup = 0.0
        activeJarakKeTujuan = 0.0
        activeDurationMinutes = 0L
        currentOrderPickupTime = null
        currentOrderStopTime = null
        pickupLat = null
        pickupLng = null
        pickupAddress = null
        stopLat = null
        stopLng = null
        stopAddress = null
        showSaveDialogDirectly = false
        
        // Return to standby notification state
        updateNotification()
    }

    fun updateNotification() {
        com.example.ui.components.OrderNotificationManager.showTrackingNotification(
            getApplication(),
            workflowState,
            activeDistanceKm,
            activeDurationMinutes,
            currentAddress
        )
    }

    private fun startTimer() {
        timerJob?.cancel()
        var lastMinute = -1L
        timerJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - trackingStartTimeMillis
                val currentMinutes = elapsed / 60000
                activeDurationMinutes = currentMinutes
                if (currentMinutes != lastMinute) {
                    lastMinute = currentMinutes
                    updateNotification()
                }
                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    // RESET ALL
    fun deleteOrder(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    // EXPORT & IMPORT UTILITIES
    fun exportDataAsJson(includeTrackGps: Boolean = true): String {
        val list = allOrders.value
        val array = JSONArray()
        for (o in list) {
            val obj = JSONObject().apply {
                put("id", o.id)
                put("tanggal", o.tanggal)
                put("hari", o.hari)
                put("jamMulai", o.jamMulai)
                put("jamPickup", o.jamPickup ?: "")
                put("jamSelesai", o.jamSelesai ?: "")
                put("jenisOrder", o.jenisOrder)
                put("pendapatan", o.pendapatan)
                put("durasi", o.durasi)
                put("jarakTempuh", o.jarakTempuh)
                put("jarakKePickup", o.jarakKePickup)
                put("jarakKeTujuan", o.jarakKeTujuan)
                put("latitudeAwal", o.latitudeAwal)
                put("longitudeAwal", o.longitudeAwal)
                put("alamatAwal", o.alamatAwal)
                put("latitudePickup", o.latitudePickup ?: 0.0)
                put("longitudePickup", o.longitudePickup ?: 0.0)
                put("alamatPickup", o.alamatPickup ?: "")
                put("latitudeAkhir", o.latitudeAkhir ?: 0.0)
                put("longitudeAkhir", o.longitudeAkhir ?: 0.0)
                put("alamatAkhir", o.alamatAkhir ?: "")
                put("catatan", o.catatan ?: "")
                if (includeTrackGps) {
                    val gpsArray = try {
                        if (!o.trackGps.isNullOrEmpty() && o.trackGps != "[]") {
                            JSONArray(o.trackGps)
                        } else {
                            JSONArray()
                        }
                    } catch (e: Exception) {
                        JSONArray()
                    }
                    put("trackGps", gpsArray)
                }
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    fun exportDataAsCsv(): String {
        val list = allOrders.value
        val csv = java.lang.StringBuilder()
        csv.append("ID,Tanggal,Hari,JamMulai,JamPickup,JamSelesai,JenisOrder,Pendapatan,DurasiMenit,JarakKm,AlamatAwal,AlamatAkhir,Catatan\n")
        for (o in list) {
            csv.append("${o.id},\"${o.tanggal}\",\"${o.hari}\",\"${o.jamMulai}\",\"${o.jamPickup ?: ""}\",\"${o.jamSelesai ?: ""}\",\"${o.jenisOrder}\",${o.pendapatan},${o.durasi},${o.jarakTempuh.format(2)},\"${o.alamatAwal.replace("\"", "'")}\",\"${o.alamatAkhir?.replace("\"", "'") ?: ""}\",\"${o.catatan?.replace("\"", "'") ?: ""}\"\n")
        }
        return csv.toString()
    }

    fun exportMonthlyTaxReport(driverName: String, year: Int, monthNumber: Int): String {
        val list = allOrders.value
        val prefix = String.format(Locale.US, "%04d-%02d", year, monthNumber)
        val filtered = list.filter { it.tanggal.startsWith(prefix) }.sortedBy { it.tanggal }

        val months = listOf(
            "Januari", "Februari", "Maret", "April", "Mei", "Juni",
            "Juli", "Agustus", "September", "Oktober", "November", "Desember"
        )
        val monthLabel = if (monthNumber in 1..12) months[monthNumber - 1] else "Bulan-$monthNumber"

        val rupiah = { v: Double -> "Rp ${String.format(Locale.US, "%,.0f", v)}" }
        val nf = { v: Double -> String.format(Locale.US, "%,.2f", v) }

        val rpt = java.lang.StringBuilder()
        rpt.append("LAPORAN REKAPITULASI PENDAPATAN BULANAN\n")
        rpt.append("Driver Tracker\n\n")

        rpt.append("INFORMASI DRIVER\n")
        rpt.append("Nama Pengendara : ${driverName.ifBlank { "DRIVER UTAMA" }.uppercase()}\n")
        rpt.append("Periode         : $monthLabel $year\n")
        rpt.append("Tanggal Cetak   : ${SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("id", "ID")).format(Date())}\n\n")

        val totalTrips = filtered.size
        val totalGross = filtered.sumOf { it.pendapatan }
        val totalDistance = filtered.sumOf { it.jarakTempuh }
        
        val distinctDays = filtered.map { it.tanggal }.distinct().size

        rpt.append("RINGKASAN BULAN INI\n")
        rpt.append("Hari aktif       : $distinctDays hari\n")
        rpt.append("Total order      : $totalTrips order\n")
        rpt.append("Total jarak      : ${nf(totalDistance)} km\n")
        rpt.append("Total durasi     : ${filtered.sumOf { it.durasi }} menit\n")
        rpt.append("Pendapatan kotor : ${rupiah(totalGross)}\n")
        if (totalTrips > 0) {
            rpt.append("Rata-rata/order  : ${rupiah(totalGross / totalTrips)}\n")
            rpt.append("Rata-rata/hari   : ${rupiah(if (distinctDays > 0) totalGross / distinctDays else 0.0)}\n")
        }
        rpt.append("\n")

        // Breakdown per jenis layanan
        rpt.append("RINCIAN PER LAYANAN\n")
        val byType = filtered.groupBy { it.jenisOrder }
        for ((type, orders) in byType) {
            val sum = orders.sumOf { it.pendapatan }
            rpt.append(String.format(Locale.US, "%-12s : %4d order  %s\n", type, orders.size, rupiah(sum)))
        }
        rpt.append("\n")

        // Rekap harian
        rpt.append("REKAP HARIAN\n")
        rpt.append(String.format(Locale.US, "%-12s %6s %12s %16s\n", "Tanggal", "Order", "Jarak(km)", "Pendapatan"))
        rpt.append("------------------------------------------------------------\n")
        val dayGrouped = filtered.groupBy { it.tanggal }
        for ((date, orders) in dayGrouped) {
            val dSum = orders.sumOf { it.pendapatan }
            val dDist = orders.sumOf { it.jarakTempuh }
            rpt.append(String.format(Locale.US, "%-12s %6d %12s %16s\n", date, orders.size, nf(dDist), rupiah(dSum)))
        }
        if (dayGrouped.isEmpty()) {
            rpt.append("(Tidak ada catatan pada periode ini)\n")
        }
        rpt.append("\n")

        // Detail order
        rpt.append("DETAIL ORDER\n")
        rpt.append(String.format(Locale.US, "%-4s %-11s %-6s %-11s %10s %14s\n", "ID", "Tanggal", "Jam", "Layanan", "Jarak", "Pendapatan"))
        rpt.append("------------------------------------------------------------\n")
        for (o in filtered) {
            rpt.append(
                String.format(
                    Locale.US,
                    "%-4d %-11s %-6s %-11s %10s %14s\n",
                    o.id,
                    o.tanggal,
                    o.jamMulai,
                    o.jenisOrder.take(11),
                    nf(o.jarakTempuh),
                    rupiah(o.pendapatan)
                )
            )
        }
        if (filtered.isEmpty()) {
            rpt.append("(Tidak ada riwayat order)\n")
        }
        rpt.append("\n")
        rpt.append("Dokumen dibuat otomatis oleh Driver Tracker.\n")

        return rpt.toString()
    }

    fun importDataFromJson(
        jsonStr: String,
        appendMode: Boolean,
        skipDuplicates: Boolean,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val array = JSONArray(jsonStr)
                var count = 0
                var skippedCount = 0
                val existingList = allOrders.value

                if (!appendMode) {
                    repository.deleteAll()
                }

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val tanggal = obj.optString("tanggal", "2026-06-19")
                    val jamMulai = obj.optString("jamMulai", "12:00")
                    val jenisOrder = obj.optString("jenisOrder", "Penumpang")
                    val pendapatan = obj.optDouble("pendapatan", 0.0)

                    if (appendMode && skipDuplicates) {
                        val isDuplicate = existingList.any { existing ->
                            existing.tanggal == tanggal &&
                            existing.jamMulai == jamMulai &&
                            existing.jenisOrder == jenisOrder &&
                            kotlin.math.abs(existing.pendapatan - pendapatan) < 0.01
                        }
                        if (isDuplicate) {
                            skippedCount++
                            continue
                        }
                    }

                    val jTempuh = obj.optDouble("jarakTempuh", 0.0)
                    val jKePickup = if (obj.has("jarakKePickup")) obj.optDouble("jarakKePickup") else (jTempuh * 0.3)
                    val jKeTujuan = if (obj.has("jarakKeTujuan")) obj.optDouble("jarakKeTujuan") else (jTempuh * 0.7)

                    val record = OrderRecord(
                        tanggal = tanggal,
                        hari = obj.optString("hari", "Jumat"),
                        jamMulai = jamMulai,
                        jamPickup = if (obj.has("jamPickup") && obj.getString("jamPickup").isNotEmpty()) obj.getString("jamPickup") else null,
                        jamSelesai = if (obj.has("jamSelesai") && obj.getString("jamSelesai").isNotEmpty()) obj.getString("jamSelesai") else null,
                        jenisOrder = jenisOrder,
                        pendapatan = pendapatan,
                        durasi = obj.optLong("durasi", 0),
                        jarakTempuh = jTempuh,
                        jarakKePickup = jKePickup,
                        jarakKeTujuan = jKeTujuan,
                        latitudeAwal = obj.optDouble("latitudeAwal", 0.0),
                        longitudeAwal = obj.optDouble("longitudeAwal", 0.0),
                        alamatAwal = obj.optString("alamatAwal", "Lokasi"),
                        latitudePickup = if (obj.has("latitudePickup")) obj.optDouble("latitudePickup") else null,
                        longitudePickup = if (obj.has("longitudePickup")) obj.optDouble("longitudePickup") else null,
                        alamatPickup = if (obj.has("alamatPickup")) obj.optString("alamatPickup") else null,
                        latitudeAkhir = if (obj.has("latitudeAkhir")) obj.optDouble("latitudeAkhir") else null,
                        longitudeAkhir = if (obj.has("longitudeAkhir")) obj.optDouble("longitudeAkhir") else null,
                        alamatAkhir = if (obj.has("alamatAkhir")) obj.optString("alamatAkhir") else null,
                        catatan = if (obj.has("catatan")) obj.optString("catatan") else null,
                        trackGps = if (obj.has("trackGps")) {
                            val trackVal = obj.get("trackGps")
                            if (trackVal is JSONArray) {
                                trackVal.toString()
                            } else {
                                trackVal?.toString() ?: "[]"
                            }
                        } else {
                            "[]"
                        }
                    )
                    repository.insert(record)
                    count++
                }

                val statusMsg = if (appendMode) {
                    if (skippedCount > 0) {
                        "Berhasil menggabungkan $count order (melewati $skippedCount data duplikat)!"
                    } else {
                        "Berhasil menggabungkan $count order baru!"
                    }
                } else {
                    "Berhasil mengimpor $count order (data lama ditumpuk/dihapus)!"
                }
                onSuccess(statusMsg)
            } catch (e: Exception) {
                e.printStackTrace()
                onError("Format berkas JSON tidak sesuai atau rusak.")
            }
        }
    }



    override fun onCleared() {
        super.onCleared()
        stopTimer()
        stopLocationUpdates()
        driftJob?.cancel()
    }

}
