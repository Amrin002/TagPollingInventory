# 📋 Tag Polling Inventory — Handover, Gap Analysis & Enhancement Roadmap

**Tanggal Handover:** 04 Oktober 2026  
**Project:** Mobile Survey & Pole Inventory Fiber Optic (Tag Polling Inventory)  
**Package:** `co.id.lintasarta.tagpollinginventory`  
**Repository Git:** `https://github.com/Amrin002/TagPollingInventory.git` (Branch: `master`)

---

## 📌 1. Status Fitur yang Sudah Selesai (Completed)

Aplikasi dibangun 100% **Offline-First** untuk kebutuhan survey tiang Fiber Optic di lapangan tanpa ketergantungan internet/server.

- [x] **Import Rute FO (KML & KMZ)**:
  - System file picker untuk mengimpor berkas `.kml` dan `.kmz` dari MSFO / software GIS.
  - Parser KML/KMZ khusus (`KmlRouteParser` & `KmzRouteParser`) untuk mengekstrak `<LineString>` dan titik tiang acuan (`<Point>`).
- [x] **Peta Interaktif OpenStreetMap (OsmDroid)**:
  - Tampilan peta offline terpusat di **Kota Ambon, Maluku** (Jl. Pattimura, Jembatan Teluk Ambon, Bandara Pattimura, Wayame, Poka).
  - Garis rute FO (*polyine reference* warna oranye) & garis tiang hasil survey (warna biru).
  - Penanda titik tiang (*markers*) interaktif dengan warna status (*Hijau = Selesai, Merah = Konflik, Abu-abu = Belum*).
  - Mekanisme *fallback background vector canvas* agar peta tidak pernah kosong walaupun tanpa internet.
- [x] **Hardware GPS Location Capture**:
  - Integrasi `FusedLocationProviderClient` + `LocationManager` native Android.
  - Tampilan indikator sinyal GPS real-time (Akurasi meter, Ketinggian/Altitude, Provider, Fix Timestamp).
  - Badging kualitas sinyal (*GOOD FIX $\le 5$m, FAIR FIX $\le 15$m, POOR FIX $> 15$m*).
  - Penanganan kondisi tiang terhalang pohon / kanopi rimbun (`[✓] Terhalang Pohon/Semak`).
- [x] **Kalkulator Deviasi Rute (`RouteDeviationService`)**:
  - Menghitung jarak tegak lurus (meter) posisi teknisi ke rute acuan (*0–10m Normal, 10–25m Warning, >25m Check Route*).
- [x] **Pengambilan Foto Kamera (CameraX & Coil)**:
  - Integrasi CameraX viewfinder dengan fitur foto fisik dan pratinjau Coil.
- [x] **Penyimpanan Lokal (Local JSON Persistence)**:
  - Data tiang dan rute disimpan permanen di `inventory_data.json` & `imported_routes.json`.
- [x] **Engine Export Multiformat**:
  - Export data ke CSV, KML (XML dengan ExtendedData), dan KMZ (Zip container dengan file foto `images/photo_*.jpg`).
- [x] **Navigasi & Back Button**:
  - Integrasi `BackHandler` Jetpack Compose dengan `screenStack` di `MainViewModel` agar tombol *Back* tidak keluar dari aplikasi.

---

## ⚠️ 2. Hal-Hal yang Masih Kurang & Perlu Ditingkatkan (Pending / Future Backlog)

Berikut adalah daftar item peningkatan (*enhancements*) yang direkomendasikan untuk pengembangan selanjutnya:

### A. Peta & Fitur Geografis (Map & GIS Enhancements)
1. **Offline Map MBTiles / Tile Caching**:
   - *Kondisi Saat Ini:* Menggunakan tile OSM online + canvas fallback.
   - *Peningkatan:* Integrasi berkas `.mbtiles` atau penyimpanan tile peta offline lokal agar detail jalan/topografi dapat dilihat 100% offline tanpa jaringan seluler sama sekali.
2. **Drag-and-Drop Pin Fine-Tuning**:
   - *Kondisi Saat Ini:* Lokasi tiang mengikuti koordinat GPS hardware aktif.
   - *Peningkatan:* Fitur menggeser *pin marker* secara manual di atas peta untuk melakukan koreksi posisi jika teknisi terhalang pagar/jurang tebal.

### B. Kamera & Pengolahan Foto
1. **Watermark / Timestamp pada Foto**:
   - *Kondisi Saat Ini:* Foto ditangkap dalam bentuk gambar mentah dari CameraX.
   - *Peningkatan:* Menambahkan stempel teks (*watermark*) otomatis pada foto berisi: **ID Tiang, Latitude, Longitude, Tanggal/Jam, dan Logo Perusahaan**.
2. **Kompresi Resolusi Foto**:
   - *Kondisi Saat Ini:* Menggunakan resolusi bawaan kamera.
   - *Peningkatan:* Kompresi otomatis resolusi foto (misal: $1920 \times 1080$ JPEG 80% quality) sebelum dimasukkan ke dalam paket KMZ untuk menghemat ruang penyimpanan HP.
3. **Dukungan Multi-Foto Per Tiang**:
   - *Kondisi Saat Ini:* 1 foto per tiang.
   - *Peningkatan:* Mendukung hingga 3 foto per tiang (misal: Foto Fondasi Tiang, Foto Tagging Label, Foto Closure/ODP).

### C. Arsitektur Data & Kinerja
1. **Migrasi Penyimpanan ke Room Database**:
   - *Kondisi Saat Ini:* Menggunakan file JSON lokal (`inventory_data.json`).
   - *Peningkatan:* Migrasi ke Android Room SQLite DB jika jumlah tiang survey mencapai puluhan ribu tiang agar performa pencarian (*query*) tetap secepat kilat.
2. **Automated Unit Testing & UI Testing**:
   - *Peningkatan:* Menambahkan unit test untuk `KmlRouteParser`, `KmzRouteParser`, `RouteDeviationService`, dan ViewModel test.

### D. Fitur Sinkronisasi Cloud (Masa Depan / Opsional)
- *Catatan:* Inti aplikasi **wajib tetap Offline-First**.
- *Peningkatan:* Menambahkan modul *Sync Manager* background opsional yang mengirimkan paket KMZ/JSON ke server kantor (*MSFO Backend*) jika HP mendapatkan koneksi WiFi/Internet.

---

## 💻 3. Panduan Melanjutkan Pekerjaan di Perangkat Lain

Jika besok Anda berpindah ke laptop/komputer lain, ikuti langkah berikut untuk melanjutkan pekerjaan:

### 1. Clone Repositori Git:
```bash
git clone https://github.com/Amrin002/TagPollingInventory.git
cd TagPollingInventory
```

### 2. Buka di Android Studio:
- Buka **Android Studio** -> **Open Existing Project** -> Pilih folder `TagPollingInventory`.
- Lakukan **Gradle Sync** (`Gradle Sync Now`).

### 3. Jalankan / Build Aplikasi:
- Jalankan via terminal atau Run button Android Studio:
```bash
./gradlew app:assembleDebug
```

---

## 📝 Ringkasan Komitmen Terakhir (Git Commit)

- **Branch**: `master`
- **Last Commit**: `feat: Implement FO Route KML/KMZ Import, Real Hardware GPS Capture, Ambon Survey Route & Navigation Backstack`
- **Author**: Amrin
