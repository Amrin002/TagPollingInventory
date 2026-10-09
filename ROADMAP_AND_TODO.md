# 📋 Tag Polling Inventory — Progress Summary, Gap Analysis & Roadmap

**Tanggal Update:** 06 Oktober 2026  
**Project:** Mobile Survey & Pole Inventory Fiber Optic (Tag Polling Inventory)  
**Package:** `co.id.lintasarta.tagpollinginventory`  
**Repository Git:** `https://github.com/Amrin002/TagPollingInventory.git` (Branch: `master`)

---

## ✅ 1. Ringkasan Fitur yang Sudah Selesai (Completed Features)

Seluruh fitur inti aplikasi survey lapangan tiang Fiber Optic telah selesai diimplementasikan, diuji di perangkat fisik, dan diselaraskan dengan spesifikasi [POLE_INVENTORY_ARCHITECTURE.md](file:///D:/PROJECT%20RNIMS/TagPollingInventory/POLE_INVENTORY_ARCHITECTURE.md):

### A. Core Survey & Data Routing
- [x] **Manajemen Segmen & Proyek**: Pembuatan Segmen Manual dan Impor Rute.
- [x] **Konfigurasi Penamaan Tiang Otomatis (Business Pole Code - Architecture Sec. 9)**:
  - Format baku `PL-[CITY_CODE]-[LOCATION_CODE]-[SEQUENCE]` (misal: `PL-JPR-CTR-001`).
  - Ekstraksi otomatis `City Code` & `Loc Code` dari nama segmen / rute secara *real-time* dengan pratinjau interaktif.
- [x] **Import Rute FO (KML & KMZ)**: Mengekstrak `<LineString>` rute acuan dan titik tiang acuan (`<Point>`).
- [x] **Peta Interaktif OpenStreetMap & Offline Cache (`OsmMapView.kt`)**:
  - Visualisasi garis rute FO acuan (oranye) dan garis tiang survey (biru).
  - Pemuatan peta dasar offline via cache SQLite internal dan direktori archive `.mbtiles` lokal.

### B. Hardware GPS Capture & GNSS Telemetri Satelit (`GpsCaptureScreen.kt`)
- [x] **GNSS Multi-Sample Averaging (10 Sampel / 10s)**:
  - Pengumpulan sampel GPS berkala, penolakan *outlier*, dan kalkulasi rata-rata *centroid* serta dispersi sampel.
- [x] **Telemetri Status Satelit (`GnssStatus`)**:
  - Perekaman jumlah satelit digunakan vs terlihat, rata-rata C/N0 ($dBHz$), dan daftar konstelasi aktif (GPS, GLONASS, GALILEO, BEIDOU, QZSS).
- [x] **Kategorisasi Kualitas GPS & Locking Location**:
  - Indikator kualitas lokasi **Hijau** ($\le 8\text{m}$), **Kuning** ($8\text{m}\dots 15\text{m}$), dan **Merah** ($>15\text{m}$).
  - Penguncian koordinat tangkapan (`isLocationLocked = true`) saat tombol *Capture GPS Location* ditekan agar koordinat di `ReviewPoleScreen` **100% fixed & tidak bergeser**, sementara GPS live kamera tetap berjalan aktif.
- [x] **Drag-and-Drop Pin Adjustment**: Titik lokasi tiang dapat digeser/disesuaikan (*drag and drop*) secara manual di atas peta jika GPS perangkat meleset.

### C. Pengolahan Foto & Kamera Tingkat Lanjut (`PhotoCaptureScreen.kt` & `CameraHelper.kt`)
- [x] **CameraX Framing Guidance Overlay & Waterpass Level**:
  - Overlay siluet koridor tiang portrait, garis tengah vertikal (*centre line*), batas aman *base* & *top*, serta indikator kemiringan ponsel (*pitch/roll waterpass* $\le 5^\circ$).
- [x] **Metadata EXIF GPS & Evidence Hashing (SHA-256)**:
  - Penulisan EXIF GPS lengkap (`TAG_GPS_LATITUDE`, `TAG_GPS_LONGITUDE`, timestamp) dan generasi hash SHA-256 foto asli pada EXIF `TAG_USER_COMMENT`.
- [x] **Multi-Foto per Tiang**:
  - Pengambilan hingga 3 foto wajib + 1 foto opsional dengan galeri *thumbnail* mini di layar kamera.
- [x] **Offline Map Downloader (Custom Radius)**:
  - Fitur pengunduhan peta jalan radius 1KM - 15KM secara dinamis di `SettingsScreen`.

### D. Data Persistence & Lifecycle
- [x] **Borang Informasi Tiang Dinamis (`PoleInformationScreen.kt`)**:
  - Atribut tiang (Type, Condition, Ownership, Height, Tag Number, Cable, Equipment, Notes) 100% dinamis, stabil terhadap pemicu GPS, dan tersimpan *real-time*.
- [x] **Penghapusan Multi-Segmen (Multi-Select Delete)**:
  - Kemampuan menghapus banyak segmen sekaligus menggunakan tekan tahan (*Long Press*) di daftar segmen beserta seluruh data tiangnya.
- [x] **Arsitektur Database SQL Modern (Room SQLite Database)**:
  - Penggunaan Room Database dengan *TypeConverters* kompleks dan otomatis menyimpan state (*App Background Autosave*).

### E. UI/UX, Navigasi, & Interactive Export Center
- [x] **Interactive Export Scope Dropdown (`ExportCenterScreen.kt`)**:
  - Pemilihan segmen ekspor interaktif via Dropdown (`ExposedDropdownMenuBox`) yang memperbarui ringkasan statistik secara *real-time*.
  - Relokasi tombol *Export History* ke Header TopBar utama.
- [x] **Pencegahan Aplikasi Keluar (Navigation Backstack)**: Koreksi *backstack* sistem.
- [x] **Engine Export Multiformat**: CSV, KML, dan KMZ (Paket ZIP yang memuat multi-foto `photo_xxx_0.jpg`).
- [x] **File Management & Sharing**: Berbagi format rute via FileProvider.

---

## ⏳ 2. Hal-Hal yang Belum Dikerjakan & Perlu Ditingkatkan (Roadmap / Gap Analysis terhadap POLE_INVENTORY_ARCHITECTURE.md)

Berikut adalah daftar item gap tersisa untuk pengembangan tahap selanjutnya (Integrasi Server Cloud & Dashboard):

### A. Integrasi Backend Cloud & Sync Engine (`.31` FastAPI / PostGIS)
1. **Autentikasi OIDC & Pendaftaran Perangkat**:
   - Autentikasi teknisi via OIDC / Keycloak menggunakan browser PKCE (AppAuth-Android) tanpa WebView tersemat.
   - Registrasi instalasi perangkat (`POST /devices/register`) dan pengikatan kunci publik Android Keystore.
2. **Engine Synchronisation Idempotent**:
   - Pengiriman data batch berbasis `sync/operations` dengan UUID operasi tunggal untuk mencegah duplikasi.
   - Penanganan kursor area (*delta sync*), pemantauan `base_version`, serta mekanisme *conflict resolution* dan *projection eligibility* (`APPLIED`, `STATE_CONFLICT`, `HELD`).
3. **Resumable Photo Upload (`tusd`)**:
   - Pengunggahan foto bukti menggunakan protokol `tusd` (`/evidence/sessions`) terpisah dari payload metadata.
   - Dukungan *pause/resume* dan antrean pengunggahan latar belakang via WorkManager.
4. **Penerimaan Assignment & Work Package**:
   - Pengunduhan paket kerja terenkripsi (*work package version*) dan *assignment grant* bertanda tangan digital.

### B. Migrasi Peta Vektor MapLibre Native
1. **Peta Vektor MapLibre Native**:
   - Migrasi tampilan peta ke **MapLibre Native Android SDK** untuk visualisasi peta vektor yang lebih fleksibel.

### C. Alur Keselamatan & Penundaan Field Work (*Deferral Workflow*)
1. **Workflow Penundaan Tiang (*Do Not Proceed / Defer*)**:
   - Opsi penundaan survei tiang yang tidak dapat dijangkau atau berbahaya (bahaya listrik, cuaca, properti tertutup, tiang roboh) dengan kode alasan terstruktur tanpa memalsukan status survei selesai.
2. **Pelaporan Bahaya Darurat (*Emergency Escalation*)**:
   - Jalur eskalasi darurat offline untuk tiang berisiko tinggi.

### D. Deteksi Peringatan Duplikat Spasial (*Nearby Pole Warning*)
1. **Spatial Duplicate Warning**:
   - Pencarian otomatis tiang eksisting dalam radius terdekat (misal 20m) sebelum teknisi membuat tiang baru untuk mencegah duplikasi.

### E. Backend Server (`.31`) & Dashboard eNims (`.38`)
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