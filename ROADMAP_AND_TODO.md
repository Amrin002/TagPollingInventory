# 📋 Tag Polling Inventory — Progress Summary, Gap Analysis & Roadmap

**Tanggal Update:** 05 Oktober 2026  
**Project:** Mobile Survey & Pole Inventory Fiber Optic (Tag Polling Inventory)  
**Package:** `co.id.lintasarta.tagpollinginventory`  
**Repository Git:** `https://github.com/Amrin002/TagPollingInventory.git` (Branch: `master`)

---

## ✅ 1. Ringkasan Fitur yang Sudah Selesai (Completed Features)

Seluruh fitur inti aplikasi survey lapangan tiang Fiber Optic telah selesai diimplementasikan, diuji di perangkat fisik, dan di-push ke GitHub:

### A. Core Survey & Data Routing
- [x] **Manajemen Segmen & Proyek**: Pembuatan Segmen Manual dan Impor Rute.
- [x] **Konfigurasi Penamaan Tiang Otomatis (Business Pole Code)**: Format auto-increment `[CITY_CODE][LOCATION_CODE]PL-[SEQUENCE]`.
- [x] **Import Rute FO (KML & KMZ)**: Mengekstrak `<LineString>` rute acuan dan titik tiang acuan (`<Point>`).
- [x] **Peta Interaktif OpenStreetMap (OsmDroid)**: Visualisasi garis rute FO acuan (oranye) dan garis tiang survey (biru).

### B. Hardware GPS Capture & Kondisi Lapangan
- [x] **Real Hardware GPS Capture (`GpsCaptureScreen.kt`)**: Koordinat GPS asli dari hardware perangkat dengan indikator akurasi meter.
- [x] **Drag-and-Drop Pin Adjustment**: Titik lokasi tiang dapat digeser/disesuaikan (*drag and drop*) secara manual di atas peta jika GPS perangkat meleset.

### C. Pengolahan Foto & Kamera Tingkat Lanjut
- [x] **Multi-Foto per Tiang**:
  - Dukungan pengambilan hingga 3 foto (Pondasi Tiang, Label Identitas, & Closure/ODP atas tiang).
  - Tampilan UI dinamis dengan Galeri *Thumbnail* Mini di layar kamera, serta pratinjau format *Carousel* di layar Detail Tiang.
- [x] **Watermark & Timestamp Otomatis pada Foto**:
  - Setiap kali tombol rana *shutter* ditekan, gambar otomatis "dicap" (Watermark) dengan informasi:
    - `Nama Perusahaan (Tag Polling Inventory - Lintasarta)`
    - `Business Pole Code`
    - `Lat/Lng` dan `Waktu Akurat (Timestamp)`.
- [x] **Kompresi Resolusi Foto Otomatis**:
  - Foto dikompres menggunakan rasio 80% (*Downscaling*) sebelum disimpan untuk meminimalisasi ukuran berkas ZIP KMZ.

### D. Data Persistence & Lifecycle
- [x] **Arsitektur Database SQL Modern (Room SQLite Database)**:
  - Telah bermigrasi sepenuhnya dari file JSON mentah menuju Android Jetpack Room Database untuk menjamin performa pencarian yang *lightning fast* saat menyimpan puluhan ribu tiang di aplikasi.
  - Implementasi *TypeConverters* kompleks seperti `List<String>` dan koordinat.
- [x] **App Background Autosave**: Otomatis menyimpan `repository.saveData()` ketika layar terkunci atau aplikasi diminimalkan.

### E. UI/UX, Navigasi, & Export Engine
- [x] **Pencegahan Aplikasi Keluar (Navigation Backstack)**: Termasuk koreksi *backstack* agar kembali ke layar yang benar pasca-simpan (*Save Success*).
- [x] **Engine Export Multiformat**: CSV, KML, dan KMZ (Paket ZIP yang memuat multi-foto `photo_xxx_0.jpg`).
- [x] **File Management & Sharing**: Kemampuan berbagi format rute (via FileProvider) langsung ke WhatsApp / Email.

---

## ⏳ 2. Hal-Hal yang Belum Dikerjakan & Perlu Ditingkatkan (Roadmap / Backlog)

Berikut adalah daftar fitur & peningkatan yang direkomendasikan untuk pengembangan tahap berikutnya (Belum Dikerjakan):

### A. Arsitektur Data Cloud & Keamanan (Next Priority)
1. **Login / Otorisasi Teknisi**:
   - *Rencana:* Menambahkan fitur registrasi / login teknisi lapangan (menggunakan **Firebase Authentication**) agar data hasil survei memiliki identitas pelaksana (Misalnya: Survei dilakukan oleh Budi).
2. **Sinkronisasi Otomatis API Cloud**:
   - *Rencana:* Jika teknisi kembali masuk ke jaringan internet, aplikasi otomatis melakukan proses sinkronisasi (*push*) seluruh data tiang dari SQLite lokal dan mengunggah foto-foto ke Server/Database Pusat secara bertahap (*batching*).

### B. GIS Lanjutan
1. **Peta Offline MBTiles Lanjutan (Kustomisasi)**:
   - *Saat ini:* Sudah disiapkan agar *folder* aplikasi (`files/osmdroid/`) bisa membaca *cache* otomatis.
   - *Rencana lanjutan:* Membuat UI agar pengguna dapat secara mandiri mengunggah dan memilih peta dasar `.mbtiles` dari penyimpanan ponselnya melalui UI aplikasi.

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
- **Latest Commit**: `feat: Implement multi-photo capture (up to 3 photos per pole), add dynamic photo gallery UI, photo watermark/compression, and update KMZ export engine`
- **Status Aplikasi**: **Build Success & Code Pushed to Git**