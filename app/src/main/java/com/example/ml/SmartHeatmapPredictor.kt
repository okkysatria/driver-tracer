package com.example.ml

import android.content.Context
import android.net.Uri
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.uber.h3core.H3Core

class SmartHeatmapPredictor(private val context: Context, private val modelUriString: String? = null) {
    private val TAG = "SmartHeatmapPredictor"
    private var ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    var isModelLoaded: Boolean = false
        private set

    val h3: H3Core? by lazy {
        try {
            H3Core.newInstance()
        } catch (e: Throwable) {
            Log.e("H3Core", "Gagal inisialisasi H3Core: ${e.message}")
            null
        }
    }

    init {
        try {
            var modelBytes: ByteArray? = null
            if (!modelUriString.isNullOrEmpty()) {
                try {
                    val uri = Uri.parse(modelUriString)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        modelBytes = stream.readBytes()
                        Log.d(TAG, "Berhasil memuat model ONNX dari URI: $modelUriString")
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Gagal memuat model dari URI: ${e.message}")
                }
            }

            if (modelBytes == null) {
                // Mencoba memuat model.onnx default dari assets
                try {
                    context.assets.open("model.onnx").use { stream ->
                        modelBytes = stream.readBytes()
                        Log.d(TAG, "Berhasil memuat model.onnx dari Assets")
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Tidak ada model.onnx di Assets. Impor model ONNX di Pengaturan untuk prediksi hotspot.")
                }
            }

            modelBytes?.let { bytes ->
                ortSession = ortEnv.createSession(bytes)
                isModelLoaded = true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Gagal menginisialisasi sesi ONNX: ${e.message}")
        }
    }

    fun getInputNames(): Set<String> {
        return ortSession?.inputNames ?: emptySet()
    }

    fun getOutputNames(): Set<String> {
        return ortSession?.outputNames ?: emptySet()
    }

    fun getNearestLandmark(lat: Double, lng: Double): String {
        return getNearestLandmarkInfo(lat, lng).first
    }

    fun getNearestLandmarkInfo(lat: Double, lng: Double): Triple<String, Double, Double> {
        val landmarks = listOf(
            Triple("Rungkut Industri (SIER)", -7.3255, 112.7595),
            Triple("Kampus UPN Veteran Jawa Timur", -7.3331, 112.7888),
            Triple("Kawasan Gunung Anyar Rungkut", -7.3385, 112.7935),
            Triple("Trans Icon Ahmad Yani", -7.3411, 112.7291),
            Triple("Stasiun Gubeng Baru", -7.2654, 112.7520),
            Triple("Kampus ITS Sukolilo", -7.2754, 112.7938),
            Triple("Pusat Belanja Tunjungan Plaza", -7.2616, 112.7383),
            Triple("MERR Kuliner Pandugo", -7.3180, 112.7810),
            Triple("Galaxy Mall Dharmahusada", -7.2670, 112.7825),
            Triple("Royal Plaza Wonokromo", -7.3005, 112.7350),
            Triple("Maspion Square Margorejo", -7.3185, 112.7345),
            Triple("Pasar Atom Kota Surabaya", -7.2425, 112.7420),
            Triple("Stasiun Pasar Turi Kota", -7.2475, 112.7330),
            Triple("Pintu Masuk Bundaran Waru", -7.3485, 112.7285),
            Triple("Klampis Jaya / Manyar Kertoarjo", -7.2855, 112.7710),
            Triple("Sektor Keputih Sukolilo", -7.2965, 112.8020),
            Triple("Bandara Internasional Juanda", -7.3732, 112.7885),
            Triple("Taman Hiburan Pantai Kenjeran", -7.2485, 112.8010),
            Triple("Ciputra World Mayjen Sungkono", -7.2915, 112.7125),
            Triple("Pakuwon Mall Surabaya Barat", -7.2885, 112.6765),
            Triple("Terminal Purabaya Bungurasih", -7.3525, 112.7245),
            Triple("Perumahan Tenggilis Mejoyo", -7.3195, 112.7525),
            Triple("Sektor Kampus Unesa Ketintang", -7.3135, 112.7275),
            Triple("Kawasan Kuliner Bratang", -7.2975, 112.7570),
            Triple("Sentra Bisnis Dharmahusada", -7.2725, 112.7685)
        )

        var nearestName = "Sektor Radar"
        var nearestLat = lat
        var nearestLng = lng
        var minDistanceSq = Double.MAX_VALUE
        for (landmark in landmarks) {
            val dLat = lat - landmark.second
            val dLng = lng - landmark.third
            val distSq = dLat * dLat + dLng * dLng
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq
                nearestName = landmark.first
                nearestLat = landmark.second
                nearestLng = landmark.third
            }
        }
        
        // If close enough to one of our cataloged regions (distanceSq < 0.0003, approx 1.5 - 2km), return its name but preserve the real location coordinates to prevent duplicates
        if (minDistanceSq < 0.0003) {
            return Triple(nearestName, lat, lng)
        }
        
        return Triple("Sektor %.4f, %.4f".format(lat, lng), lat, lng)
    }

    // Fungsi prediksi area hotspot tergacor saat ini
    fun predictHotspots(
        hour: Int,
        dayOfWeek: Int,
        month: Int,
        isWeekend: Int,
        isSchoolHoliday: Int,
        isCollegeHoliday: Int,
        isRamadhan: Int,
        ramadhanPhase: String, // e.g., "LATE_RAMADHAN"
        tripCategory: String,  // e.g., "Food Delivery (Sebelum Buka)"
        jenisOrder: String,    // e.g., "FOOD"
        driverLat: Double,
        driverLng: Double,
        inputLayerName: String? = null,
        outputLayerName: String? = null
    ): List<HotspotRecommendation> {
        val recommendations = ArrayList<HotspotRecommendation>()
        var inferenceSuccess = false

        // 1. Jalankan model ONNX dengan aman
        if (isModelLoaded && ortSession != null) {
            try {
                val session = ortSession!!
                val inputNames = session.inputNames
                val inputsMap = mutableMapOf<String, OnnxTensor>()

                // Memeriksa metadata layer masukan
                val inputInfo = session.inputInfo
                if (inputInfo.isNotEmpty()) {
                    for ((name, info) in inputInfo) {
                        try {
                            val tensorInfo = info.info as? ai.onnxruntime.TensorInfo
                            if (tensorInfo != null) {
                                val shape = tensorInfo.shape
                                // Deteksi jumlah fitur masukan yang diharapkan model
                                val numFeatures = if (shape.isNotEmpty()) {
                                    val lastDim = shape[shape.size - 1]
                                    if (lastDim > 0) lastDim.toInt() else 10
                                } else {
                                    10
                                }

                                // Menyusun matriks fitur numerik masukan
                                val features = FloatArray(numFeatures) { index ->
                                    when (index) {
                                        0 -> hour.toFloat()
                                        1 -> dayOfWeek.toFloat()
                                        2 -> month.toFloat()
                                        3 -> isWeekend.toFloat()
                                        4 -> isSchoolHoliday.toFloat()
                                        5 -> isCollegeHoliday.toFloat()
                                        6 -> isRamadhan.toFloat()
                                        7 -> if (jenisOrder.contains("FOOD", ignoreCase = true)) 1.0f else 0.0f
                                        8 -> if (tripCategory.contains("Food", ignoreCase = true)) 1.0f else 0.0f
                                        else -> 0.0f
                                    }
                                }

                                // Membuat object OnnxTensor sesuai dengan tipe data target model
                                val tensor = when (tensorInfo.type) {
                                    ai.onnxruntime.OnnxJavaType.FLOAT -> {
                                        if (shape.size == 2) OnnxTensor.createTensor(ortEnv, arrayOf(features))
                                        else OnnxTensor.createTensor(ortEnv, features)
                                    }
                                    ai.onnxruntime.OnnxJavaType.DOUBLE -> {
                                        val doubleFeatures = DoubleArray(numFeatures) { features[it].toDouble() }
                                        if (shape.size == 2) OnnxTensor.createTensor(ortEnv, arrayOf(doubleFeatures))
                                        else OnnxTensor.createTensor(ortEnv, doubleFeatures)
                                    }
                                    ai.onnxruntime.OnnxJavaType.INT64 -> {
                                        val longFeatures = LongArray(numFeatures) { features[it].toLong() }
                                        if (shape.size == 2) OnnxTensor.createTensor(ortEnv, arrayOf(longFeatures))
                                        else OnnxTensor.createTensor(ortEnv, longFeatures)
                                    }
                                    else -> {
                                        if (shape.size == 2) OnnxTensor.createTensor(ortEnv, arrayOf(features))
                                        else OnnxTensor.createTensor(ortEnv, features)
                                    }
                                }
                                inputsMap[name] = tensor
                            }
                        } catch (e: Throwable) {
                            Log.w(TAG, "Gagal inisialisasi tensor input '$name': ${e.message}")
                        }
                    }
                }

                // Jika pemetaan metadata otomatis gagal atau tidak valid, coba pendekatan input tunggal atau terpisah
                if (inputsMap.isEmpty()) {
                    if (inputNames.contains("hour")) {
                        inputsMap["hour"] = OnnxTensor.createTensor(ortEnv, arrayOf(floatArrayOf(hour.toFloat())))
                    }
                    if (inputNames.contains("day_of_week")) {
                        inputsMap["day_of_week"] = OnnxTensor.createTensor(ortEnv, arrayOf(floatArrayOf(dayOfWeek.toFloat())))
                    }
                    if (inputNames.contains("month")) {
                        inputsMap["month"] = OnnxTensor.createTensor(ortEnv, arrayOf(floatArrayOf(month.toFloat())))
                    }
                    if (inputNames.contains("is_weekend")) {
                        inputsMap["is_weekend"] = OnnxTensor.createTensor(ortEnv, arrayOf(floatArrayOf(isWeekend.toFloat())))
                    }
                    if (inputNames.contains("is_school_holiday")) {
                        inputsMap["is_school_holiday"] = OnnxTensor.createTensor(ortEnv, arrayOf(floatArrayOf(isSchoolHoliday.toFloat())))
                    }

                    val firstInputName = if (inputNames.iterator().hasNext()) inputNames.iterator().next() else null
                    if (inputsMap.isEmpty() && !firstInputName.isNullOrEmpty()) {
                        val features = floatArrayOf(
                            hour.toFloat(), dayOfWeek.toFloat(), month.toFloat(),
                            isWeekend.toFloat(), isSchoolHoliday.toFloat(), isCollegeHoliday.toFloat(), isRamadhan.toFloat()
                        )
                        inputsMap[firstInputName] = OnnxTensor.createTensor(ortEnv, arrayOf(features))
                    }
                }

                if (inputsMap.isNotEmpty()) {
                    val results = session.run(inputsMap)
                    if (results.size() > 0) {
                        // Memilih output layer yang sesuai
                        var matchedOutput: Any? = null
                        if (!outputLayerName.isNullOrEmpty() && results.get(outputLayerName).isPresent) {
                            matchedOutput = results.get(outputLayerName).get().value
                        } else {
                            matchedOutput = results[results.size() - 1].value
                        }

                        if (matchedOutput != null) {
                            if (matchedOutput is Map<*, *>) {
                                val probabilities = matchedOutput as Map<String, Float>
                                for ((h3Index, confidence) in probabilities) {
                                    if (confidence > 0.05f) {
                                        try {
                                            val latLng = h3?.cellToLatLng(h3Index)
                                            if (latLng != null) {
                                                val potency = (confidence * 100).toInt().coerceIn(10, 99)
                                                val info = getNearestLandmarkInfo(latLng.lat, latLng.lng)
                                                recommendations.add(
                                                    HotspotRecommendation(
                                                        h3Index = h3Index,
                                                        latitude = latLng.lat,
                                                        longitude = latLng.lng,
                                                        skorPotensi = potency,
                                                        keterangan = "Prediksi ML (${info.first}): " + getContextualDescription(tripCategory, confidence)
                                                    )
                                                )
                                            }
                                        } catch (e: Throwable) {
                                            Log.e(TAG, "Gagal decode H3: $h3Index")
                                        }
                                    }
                                }
                            } else if (matchedOutput is FloatArray) {
                                val driverLatVal = if (driverLat == 0.0) -7.2754 else driverLat
                                val driverLngVal = if (driverLng == 0.0) 112.7938 else driverLng
                                val centerAddress = h3?.latLngToCellAddress(driverLatVal, driverLngVal, 8)
                                if (centerAddress != null) {
                                    val cells = h3?.gridDisk(centerAddress, 2) ?: emptyList()
                                    matchedOutput.forEachIndexed { index, weight ->
                                        if (index < cells.size) {
                                            val cell = cells[index]
                                            val latLng = h3?.cellToLatLng(cell)
                                            if (latLng != null) {
                                                val potency = (weight * 100).toInt().coerceIn(10, 99)
                                                val info = getNearestLandmarkInfo(latLng.lat, latLng.lng)
                                                recommendations.add(
                                                    HotspotRecommendation(
                                                        h3Index = cell,
                                                        latitude = latLng.lat,
                                                        longitude = latLng.lng,
                                                        skorPotensi = potency,
                                                        keterangan = "Prediksi Regresi ONNX (${info.first}): Sinyal Kepadatan Baik"
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (matchedOutput is Array<*> && matchedOutput.isNotEmpty() && matchedOutput[0] is FloatArray) {
                                val driverLatVal = if (driverLat == 0.0) -7.2754 else driverLat
                                val driverLngVal = if (driverLng == 0.0) 112.7938 else driverLng
                                val centerAddress = h3?.latLngToCellAddress(driverLatVal, driverLngVal, 8)
                                if (centerAddress != null) {
                                    val cells = h3?.gridDisk(centerAddress, 3) ?: emptyList()
                                    val floatArray = matchedOutput[0] as FloatArray
                                    floatArray.forEachIndexed { index, weight ->
                                        if (index < cells.size) {
                                            val cell = cells[index]
                                            val latLng = h3?.cellToLatLng(cell)
                                            if (latLng != null) {
                                                val potency = (weight * 100).toInt().coerceIn(10, 99)
                                                val info = getNearestLandmarkInfo(latLng.lat, latLng.lng)
                                                recommendations.add(
                                                    HotspotRecommendation(
                                                        h3Index = cell,
                                                        latitude = latLng.lat,
                                                        longitude = latLng.lng,
                                                        skorPotensi = potency,
                                                        keterangan = "Prediksi Dense ONNX (${info.first}): Konsentrasi Mandiri Tinggi"
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            inferenceSuccess = true
                        }
                    }
                    results.close()
                }

                inputsMap.values.forEach { it.close() }
            } catch (e: Throwable) {
                Log.e(TAG, "Error fatal saat inference ONNX (Model mismatch?): ${e.message}")
            }
        }

        return recommendations.sortedByDescending { it.skorPotensi }
    }

    private fun getContextualDescription(tripCategory: String, score: Float): String {
        return if (score > 0.4f) "Sinyal Super Gacor ($tripCategory)" else "Konsentrasi Order Sedang ($tripCategory)"
    }
}

data class HotspotRecommendation(
    val h3Index: String,
    val latitude: Double,
    val longitude: Double,
    val skorPotensi: Int,
    val keterangan: String
)
