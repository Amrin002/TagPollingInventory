# 📋 Tag Polling Inventory — Progress Summary, Gap Analysis & Roadmap

**Tanggal Update:** 05 Oktober 2026  
**Project:** Mobile Survey & Pole Inventory Fiber Optic (Tag Polling Inventory)  
**Package:** `co.id.lintasarta.tagpollinginventory`  
**Repository Git:** `https://github.com/Amrin002/TagPollingInventory.git` (Branch: `master`)

---

## ✅ 1. Ringkasan Fitur yang Sudah Selesai (Completed Features)

Seluruh fitur inti aplikasi survey lapangan tiang Fiber Optic telah selesai diimplementasikan, diuji di perangkat fisik, dan di-push ke GitHub:

### A. Core Survey & Data Routing
- [x] **Manajemen Segmen & Proyek**:
  - Pembuatan Segmen Manual dan Impor Rute.
- [x] **Konfigurasi Penamaan Tiang Otomatis (Business Pole Code)**:
  - Konfigurasi `City Code` (3 digit), `Location Code` (3 digit), dan nomor urut awalan.
  - Implementasi format auto-increment: `[CITY_CODE][LOCATION_CODE]PL-[SEQUENCE]` (contoh: `ABNTKBPL-001`).
- [x] **Import Rute FO (KML & KMZ)**:
  - System file picker untuk mengimpor berkas `.kml` dan `.kmz` milik engineer.
  - Parser KML/KMZ khusus (`KmlRouteParser` & `KmzRouteParser`) yang mengekstrak `<LineString>` rute acuan dan titik tiang acuan (`<Point>`).
- [x] **Peta Interaktif OpenStreetMap (OsmDroid)**:
  - Visualisasi garis rute FO acuan (oranye) dan garis tiang survey (biru).
  - Penanda titik tiang (*markers*) interaktif dengan warna status menggunakan nama tiang *Business Pole Code* (*Hijau = Selesai, Merah = Konflik, Abu-abu = Belum*).
- [x] **Calculasi Deviasi Rute (`RouteDeviationService`)**:
  - Menghitung jarak tegak lurus (meter) posisi teknisi ke rute acuan (*0–10m Normal, 10–25m Warning, >25m Check Route*).

### B. Hardware GPS Capture & Kondisi Lapangan
- [x] **Real Hardware GPS Capture (`GpsCaptureScreen.kt`)**:
  - Mengambil koordinat GPS asli dari hardware perangkat (`FusedLocationProviderClient` + `LocationManager` native).
  - Menampilkan indikator akurasi meter, altitude, provider, timestamp, dan badging kualitas sinyal (*GOOD FIX $\le 5$m, FAIR FIX $\le 15$m, POOR FIX $> 15$m*).
  - Animasi *target reticle radar* untuk memandu teknisi berdiri di bawah tiang.
- [x] **Penanganan Tiang Terhalang Pohon/Semak**:
  - Menyediakan panduan lapangan dan opsi checklist `[✓] Tandai: Lokasi Tiang Terhalang Pohon / Semak`.

### C. Mode Online vs Offline Dinamis (`NetworkObserver.kt`)
- [x] **Pemantau Jaringan Real-Time**:
  - Otomatis mendeteksi status koneksi internet (**WiFi**, **Cellular Data**, atau **Offline/Airplane Mode**).

### D. Data Persistence & Lifecycle
- [x] **Penyimpanan Lokal (JSON Repository)**:
  - Semua progress penandaan (draft tiang) dan atribut disimpan ke dalam berkas `inventory_data.json`.
- [x] **App Background Autosave**:
  - Menggunakan Jetpack Lifecycle `LifecycleEventObserver`.
  - Otomatis melakukan `repository.saveData()` ketika aplikasi diminimalkan (Close, Home, Recent Apps, atau Screen Lock) agar tidak ada data teknisi yang hilang.

### E. UI/UX, Navigasi, & Export Engine
- [x] **Pencegahan Aplikasi Keluar (Navigation Backstack)**:
  - Integrasi `BackHandler` Jetpack Compose + `screenStack` di `MainViewModel`.
  - Tombol Kembali (*Back/Return*) pada layar *Save Success* sekarang langsung kembali murni ke halaman *Segment Detail* atau *List* tanpa menyisakan riwayat.
- [x] **Bottom Navigation Bar & Ikon Modern**.
- [x] **Engine Export Multiformat**:
  - Export data survey ke CSV (dengan kolom *Pole Code* dan *Internal ID* terpisah), KML (XML dengan *ExtendedData*), dan KMZ (Zip container berisi foto aset `images/photo_*.jpg`).
- [x] **File Management & Sharing**:
  - Sinkronisasi `FileProvider` untuk mengirim fail aktual rute `.kmz`, `.kml`, dan `.csv` langsung ke WhatsApp / Email, bukan sekadar teks namanya saja.

---

## ⏳ 2. Hal-Hal yang Belum Dikerjakan & Perlu Ditingkatkan (Roadmap / Backlog)

Berikut adalah daftar fitur & peningkatan yang direkomendasikan untuk pengembangan tahap berikutnya (Belum Dikerjakan):

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

### C. Arsitektur Data & Keamanan
1. **Migrasi Penyimpanan ke Room SQLite Database**:
   - *Rencana:* Migrasi dari file JSON lokal (`inventory_data.json`) ke Android Room DB jika skala data mencapai puluhan ribu tiang agar performa pencarian (*query*) tetap secepat kilat.
2. **Login / Otorisasi Teknisi**:
   - *Rencana:* Menambahkan fitur registrasi / login teknisi lapangan (menggunakan Firebase Auth) agar data hasil survei memiliki identitas pelaksana.
3. **Sinkronisasi Otomatis API Cloud**:
   - *Rencana:* Jika teknisi kembali masuk ke jaringan internet, aplikasi otomatis melakukan proses sinkronisasi (*push*) seluruh data `inventory_data.json` dan foto-foto ke Server/Database Pusat.

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
- **Latest Commit**: `feat: Fix data persistence on app close, correct back navigation after successful save, and implement business Pole Naming configuration`
- **Status Aplikasi**: **Build Success & Code Pushed to Git**