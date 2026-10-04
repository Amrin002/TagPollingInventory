# 📋 Tag Polling Inventory — Progress Summary, Gap Analysis & Roadmap

**Tanggal Update:** 05 Oktober 2026  
**Project:** Mobile Survey & Pole Inventory Fiber Optic (Tag Polling Inventory)  
**Package:** `co.id.lintasarta.tagpollinginventory`  
**Repository Git:** `https://github.com/Amrin002/TagPollingInventory.git` (Branch: `master`)

---

## ✅ 1. Ringkasan Fitur yang Sudah Selesai (Completed Features)

Seluruh fitur inti aplikasi survey lapangan tiang Fiber Optic telah selesai diimplementasikan, diuji di perangkat fisik, dan di-push ke GitHub:

### A. Core Survey & Data Routing
- [x] **Lokasi Proyek Ambon, Maluku**:
  - Seluruh data sampel dan peta terpusat di Kota Ambon (Jl. Pattimura, Jembatan Teluk Ambon, Bandara Pattimura, Wayame, Poka).
- [x] **Import Rute FO (KML & KMZ)**:
  - System file picker untuk mengimpor berkas `.kml` dan `.kmz` milik engineer.
  - Parser KML/KMZ khusus (`KmlRouteParser` & `KmzRouteParser`) yang mengekstrak `<LineString>` rute acuan dan titik tiang acuan (`<Point>`).
- [x] **Peta Interaktif OpenStreetMap (OsmDroid)**:
  - Visualisasi garis rute FO acuan (oranye) dan garis tiang survey (biru).
  - Penanda titik tiang (*markers*) interaktif dengan warna status (*Hijau = Selesai, Merah = Konflik, Abu-abu = Belum*).
  - Mekanisme *fallback background vector canvas* agar peta tetap tampil meskipun tanpa jaringan internet.
- [x] **Calculasi Deviasi Rute (`RouteDeviationService`)**:
  - Menghitung jarak tegak lurus (meter) posisi teknisi ke rute acuan (*0–10m Normal, 10–25m Warning, >25m Check Route*).

### B. Hardware GPS Capture & Kondisi Lapangan
- [x] **Real Hardware GPS Capture (`GpsCaptureScreen.kt`)**:
  - Mengambil koordinat GPS asli dari hardware perangkat (`FusedLocationProviderClient` + `LocationManager` native).
  - Menampilkan indikator akurasi meter, altitude, provider, timestamp, dan badging kualitas sinyal (*GOOD FIX $\le 5$m, FAIR FIX $\le 15$m, POOR FIX $> 15$m*).
  - Animasi *target reticle radar* untuk memandu teknisi berdiri di bawah tiang.
- [x] **Penanganan Tiang Terhalang Pohon/Semak**:
  - Menyediakan panduan lapangan dan opsi checklist `[✓] Tandai: Lokasi Tiang Terhalang Pohon / Semak`.
  - Otomatis mencatat keterangan terhalang pohon dan batas toleransi akurasi ke dalam *Field Notes*.
  - *Non-blocking GPS Capture*: Tombol penangkapan koordinat tidak pernah terkunci meskipun akurasi di area rimbun berada pada level Fair/Poor.

### C. Mode Online vs Offline Dinamis (`NetworkObserver.kt`)
- [x] **Pemantau Jaringan Real-Time**:
  - Otomatis mendeteksi status koneksi internet (**WiFi**, **Cellular Data**, atau **Offline/Airplane Mode**).
- [x] **Dinamika Status di 15 Layar**:
  - Badge dinamis **`ONLINE (WiFi/Cellular)`** vs **`OFFLINE (Local)`** di *TopBar* seluruh layar aplikasi.
  - *Mode Online:* Mengaktifkan trianggulasi Fused Location WiFi/Cellular untuk penangkapan GPS presisi tinggi ($\pm 1.5 - 2.5\text{m}$) & unduhan peta live.
  - *Mode Offline:* Mempertahankan identitas 100% offline (Hardware GPS + Penyimpanan Lokal JSON `inventory_data.json`).

### D. UI/UX, Navigasi, & Export Engine
- [x] **Pencegahan Aplikasi Keluar (Navigation Backstack)**:
  - Integrasi `BackHandler` Jetpack Compose + `screenStack` di `MainViewModel` agar tombol *Back* fisik navigasi sesuai hierarki layar tanpa mengakhiri aplikasi.
- [x] **Tombol Dinamika Dashboard**:
  - Percabang aksi utama: **`+ New Field Work`** (saat data 0 / aplikasi baru) dialihkan ke Import Route vs **`▶ Continue Field Work`** (saat data rute/tiang ada).
- [x] **Bottom Navigation Bar & Ikon Modern**:
  - Ikon gaya Font Awesome / Material Vector (`SpaceDashboard`, `AltRoute`, `Explore`, `CloudUpload`, `Tune`).
  - Penambahan top padding 8.dp & ketinggian kontainer 84.dp sehingga ikon menu memiliki spasi di bagian atas.
- [x] **Engine Export Multiformat**:
  - Export data survey ke CSV, KML (XML dengan ExtendedData), dan KMZ (Zip container berisi foto aset `images/photo_*.jpg`).

---

## ⏳ 2. Hal-Hal yang Belum Dikerjakan & Perlu Ditingkatkan (Roadmap / Backlog)

Berikut adalah daftar fitur & peningkatan yang direkomendasikan untuk pengembangan tahap berikutnya:

### A. GIS & Fitur Peta Tingkat Lanjut
1. **Peta Offline MBTiles**:
   - *Rencana:* Integrasi berkas `.mbtiles` lokal agar peta topografi/jalan dapat dibuka 100% offline tanpa koneksi seluler di daerah terpencil.
2. **Drag-and-Drop Pin Adjustment**:
   - *Rencana:* Fitur menggeser *pin marker* secara manual di atas peta untuk koreksi posisi tiang jika teknisi berada di tebing/jurang/lokasi terhalang total.

### B. Pengolahan Foto & Kamera
1. **Watermark / Timestamp Otomatis pada Foto**:
   - *Rencana:* Menambahkan stempel teks (*watermark*) otomatis pada foto hasil tangkapan kamera berisi: **ID Tiang, Latitude, Longitude, Tanggal/Jam, dan Logo Perusahaan**.
2. **Kompresi Resolusi Foto Otomatis**:
   - *Rencana:* Kompresi resolusi foto (misal: $1920 \times 1080$ JPEG 80%) sebelum dibungkus ke dalam paket KMZ untuk menghemat memori internal HP.
3. **Multi-Foto per Tiang**:
   - *Rencana:* Mendukung hingga 3 foto per tiang (Foto Fondasi Tiang, Foto Tagging Label, Foto Closure/ODP).

### C. Arsitektur Data & Pengujian
1. **Migrasi Penyimpanan ke Room SQLite Database**:
   - *Rencana:* Migrasi dari file JSON lokal (`inventory_data.json`) ke Android Room DB jika skala data mencapai puluhan ribu tiang agar performa pencarian (*query*) tetap secepat kilat.
2. **Automated Unit Testing**:
   - *Rencana:* Menambahkan unit test untuk `KmlRouteParser`, `KmzRouteParser`, `RouteDeviationService`, dan ViewModel test.

---

## 💻 3. Panduan Melanjutkan Pekerjaan di Komputer Lain

Jika Anda melanjutkan pekerjaan di perangkat/laptop lain:

### 1. Clone Repositori Git:
```bash
git clone https://github.com/Amrin002/TagPollingInventory.git
cd TagPollingInventory
```

### 2. Buka di Android Studio:
- Buka **Android Studio** -> **Open Existing Project** -> Pilih folder `TagPollingInventory`.
- Lakukan **Gradle Sync** (`Gradle Sync Now`).

### 3. Jalankan / Build Aplikasi:
```bash
./gradlew app:assembleDebug
```

---

## 📝 Ringkasan Komitmen Terakhir (Git Status)

- **Repository Remote**: `https://github.com/Amrin002/TagPollingInventory.git`
- **Branch**: `master`
- **Latest Commit**: `feat: Apply dynamic Online and Offline mode monitoring across all app features`
- **Status Aplikasi**: **Build Success & Verified on Hardware Device**
