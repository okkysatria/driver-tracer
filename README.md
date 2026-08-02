<div align="center">
<img width="1200" height="475" alt="Driver Tracker" src="https://img.shields.io/badge/Driver%20Tracker-Android-00AA13?style=for-the-badge&logo=android" />
</div>

# Driver Tracker

Aplikasi Android untuk mencatat rute dan pendapatan driver rideshare, lengkap dengan peta heatmap untuk menemukan area paling gacor.

## Fitur
- 📍 **Perekam Order**: Catat order (penumpang / food / paket) dari pickup sampai selesai, lengkap dengan track GPS.
- 🗺️ **Heatmap Radar**: Prediksi hotspot order berdasarkan waktu, hari, dan lokasi driver (ONNX ML + H3 hex grid).
- 💰 **Dashboard**: Laporan pendapatan harian/mingguan.
- 🔄 **Share Rute**: Ekspor rute perjalanan.
- 🌙 **Dark Mode**: Tampilan gelap ramah mata.

## Cara Menjalankan (Local)
**Prasyarat:** [Android Studio](https://developer.android.com/studio)

1. Buka Android Studio
2. Pilih **Open** dan pilih folder project ini
3. Biarkan Android Studio memperbaiki incompatibilities saat import
4. Jalankan app di emulator atau device fisik

## Build Release
- Konfigurasi signing ada di `app/build.gradle.kts` (blok `signingConfigs`).

## Struktur
```
app/src/main/java/com/example/
├── MainActivity.kt          # Entry point + GPS permission
├── viewmodel/               # Logika inti (order, GPS, timer)
├── data/                    # Room DB (OrderRecord)
├── ui/screens/              # RekamOrder, Heatmap, Dashboard, Track, Settings
├── ui/components/           # OsmMapView (CartoDB), marker
└── ml/                      # SmartHeatmapPredictor (ONNX + H3)
```
