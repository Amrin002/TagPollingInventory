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
- [x] **Kamera Resolusi Tinggi Tanpa Crash**:
  - Implementasi *ImageCapture* murni tanpa intervensi latar belakang yang memberatkan, kamera merespons secara *real-time* saat mengambil gambar.
- [x] **Multi-Foto per Tiang**:
  - Dukungan pengambilan hingga 3 foto (Pondasi Tiang, Label Identitas, & Closure/ODP atas tiang).
  - Tampilan UI dinamis dengan Galeri *Thumbnail* Mini di layar kamera.
- [x] **Offline Map Downloader (Custom Radius)**:
  - Fitur di dalam pengaturan (*Settings*) untuk mengunduh peta jalan (*street map*) radius 1KM - 15KM secara dinamis.
  - Peta dapat langsung dibuka tanpa sinyal seluler di dalam `Field Map`.

### D. Data Persistence & Lifecycle
- [x] **Penghapusan Multi-Segmen (Multi-Select Delete)**:
  - Kemampuan menghapus banyak segmen sekaligus menggunakan tekan tahan (*Long Press*) di daftar segmen.
  - Menghapus otomatis semua data tiang (*Poles*) yang menumpang pada segmen tersebut dari penyimpanan lokal.
- [x] **Arsitektur Database SQL Modern (Room SQLite Database)**:
  - Telah bermigrasi sepenuhnya dari file JSON mentah menuju Android Jetpack Room Database untuk menjamin performa pencarian yang *lightning fast* saat menyimpan puluhan ribu tiang di aplikasi.
  - Implementasi *TypeConverters* kompleks seperti `List<String>` dan koordinat.
- [x] **App Background Autosave**: Otomatis menyimpan state ke database ketika layar terkunci atau aplikasi diminimalkan.

### E. UI/UX, Navigasi, & Export Engine
- [x] **Pencegahan Aplikasi Keluar (Navigation Backstack)**: Termasuk koreksi *backstack* agar kembali ke layar yang benar pasca-simpan (*Save Success*).
- [x] **Engine Export Multiformat**: CSV, KML, dan KMZ (Paket ZIP yang memuat multi-foto `photo_xxx_0.jpg`).
- [x] **File Management & Sharing**: Kemampuan berbagi format rute (via FileProvider) langsung ke WhatsApp / Email.

---

## ⏳ 2. Hal-Hal yang Belum Dikerjakan & Perlu Ditingkatkan (Roadmap / Gap Analysis terhadap POLE_INVENTORY_ARCHITECTURE.md)

Berdasarkan pencocokan antara aplikasi Android yang sudah dibangun dengan spesifikasi arsitektur platform nasional pada **`POLE_INVENTORY_ARCHITECTURE.md` (v2.3)**, berikut adalah daftar item gap dan roadmap pengembangan tahap selanjutnya:

### A. Integrasi Backend Cloud & Sync Engine (`.31` FastAPI / PostGIS)
1. **Autentikasi OIDC & Pendaftaran Perangkat**:
   - Autentikasi teknisi via OIDC / Keycloak menggunakan browser PKCE (AppAuth-Android) tanpa WebView tersemat.
   - Registrasi instalasi perangkat (`POST /devices/register`) dan pengikatan kunci publik Android Keystore.
2. **Engine Synchronisation Idempotent**:
   - Implementasi pengiriman data batch berbasis `sync/operations` dengan UUID operasi tunggal untuk mencegah duplikasi.
   - Penanganan kursor area (*delta sync*), pemantauan `base_version`, serta mekanisme *conflict resolution* dan *projection eligibility* (`APPLIED`, `STATE_CONFLICT`, `HELD`).
3. **Resumable Photo Upload (`tusd`)**:
   - Pengunggahan foto bukti menggunakan protokol `tusd` (`/evidence/sessions`) terpisah dari payload metadata.
   - Dukungan *pause/resume*, penanganan jaringan tidak stabil, dan antrean pengunggahan latar belakang via WorkManager.
4. **Penerimaan Assignment & Work Package**:
   - Pengunduhan paket kerja terenkripsi (*work package version*) dan *assignment grant* bertanda tangan digital.

### B. Kamera Tingkat Lanjut & Framing Guidance (CameraX Overlay)
1. **CameraX Augmented Framing Guidance**:
   - Overlay kamera interaktif: Siluet tiang portrait, garis tengah, batas aman *base* & *top*, indikator *level/pitch* (kemiringan), panduan jarak (8–25m), dan indikator pencahayaan/fokus.
2. **Evidence Manifest & Keamanan Bukti Foto**:
   - Perhitungan hash SHA-256 untuk foto JPEG asli sebelum masuk antrean upload.
   - Penulisan metadata EXIF GPS lengkap dan generasi *Device Capture Manifest* bertanda tangan digital (Android Keystore) untuk menjamin otentisitas bukti foto.

### C. Akurasi GPS, GNSS Sampling Averaging & Telemetri Satelit
1. **GNSS Multi-Sample Averaging**:
   - Pengumpulan dan perataan sampel GPS (minimal 10 sampel selama 10 detik) saat tombol *Validate Position* ditekan, dilengkapi penolakan *outliers* dan kalkulasi dispersi sampel.
2. **Kategorisasi Kualitas GPS**:
   - Indikator kualitas lokasi: **Hijau** ($\le 8\text{m}$), **Kuning** ($>8\text{m} \dots 15\text{m}$ - *warning/retry*), dan **Merah** ($>15\text{m}$ - *explicit override dengan alasan*).
3. **Telemetri Status Satelit (`GnssStatus`)**:
   - Perekaman telemetri satelit (satelit digunakan vs terlihat, C/N0, dan konstelasi) sebagai bukti pendukung kualitas GPS.

### D. Migrasi Peta Vektor MapLibre & MBTiles Offline
1. **Peta Vektor MapLibre Native**:
   - Migrasi dari OsmDroid (`OsmMapView`) ke **MapLibre Native Android SDK** untuk performa *rendering* dan visualisasi lapisan vektor yang lebih responsif.
2. **Paket Peta Vektor Offline (`MBTiles`)**:
   - Pemuatan peta dasar vektor offline berformat `.mbtiles` (`mbtiles:///...`) yang bersumber dari master OpenStreetMap (Planetiler/Martin) beserta aset font/glyphs dan sprites lokal.

### E. Alur Keselamatan & Penundaan Field Work (*Deferral Workflow*)
1. **Workflow Penundaan Tiang (*Do Not Proceed / Defer*)**:
   - Opsi penundaan survei tiang yang tidak dapat dijangkau atau berbahaya (seperti bahaya listrik, cuaca ekstrem, properti tertutup, tiang roboh) dengan kode alasan terstruktur tanpa memalsukan status survei selesai.
2. **Pelaporan Bahaya Darurat (*Emergency Escalation*)**:
   - Jalur eskalasi darurat offline untuk tiang berisiko tinggi (misal kawat terputus/terbakar).

### F. Deteksi Peringatan Duplikat Spasial (*Nearby Pole Warning*)
1. **Spatial Duplicate Warning**:
   - Pencarian otomatis tiang eksisting dalam radius terdekat (misal 20m) sebelum teknisi membuat tiang baru untuk mencegah duplikasi tiang akibat kesalahan teknisi/GPS.

### G. Penyelarasan Model Data Room & Entitas Identitas
1. **Penyelarasan Schema SQLite (Room)**:
   - Penyelarasan tabel lokal dengan spesifikasi arsitektur: `pole_id` (UUID), `pole_code` (`PL-[AREA]-[UUID_PREFIX]`), lapisan *append-only* `pole_observation`, `gnss_capture`, `photo_evidence`, `field_visit_outcome`, dan versi kamus data (`inventory_dictionary_version`).

### H. Backend Server (`.31`) & Dashboard eNims (`.38`)
1. **Backend FastAPI & PostGIS Service (`.31`)**:
   - Pembangunan modul backend FastAPI (`.31\pole-inventory\api`), worker background (`.31\pole-inventory\worker`), database PostgreSQL/PostGIS, `tusd`, dan Martin tile server.
2. **Dashboard eNims CodeIgniter 3 (`.38`)**:
   - Pembangunan modul dashboard eNims (`.38\pole-inventory-dashboard`) dengan MapLibre GL JS, SSE progress sync, QA exception queue, resolusi duplikat, dan ekspor data multiformat.

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
- **Latest Commit**: `fix: Resolve camera capture crash and implement segment multi-deletion logic`
- **Status Aplikasi**: **Build Success & Code Pushed to Git**