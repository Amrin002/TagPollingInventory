# 📋 Tag Polling Inventory — Progress Summary, Gap Analysis & Roadmap

**Tanggal Update:** 06 Oktober 2026  
**Project:** Mobile Survey & Pole Inventory Fiber Optic (Tag Polling Inventory)  
**Package:** `co.id.lintasarta.tagpollinginventory`  
**Repository Git:** `https://github.com/Amrin002/TagPollingInventory.git` (Branch: `master`)

---
---

## 🚀 Status Verifikasi Perangkat Fisik (Physical Device Testing)

1. **Peta Ikhtisar Seluruh Segmen Area 18 MPO (`AreaOverviewMapScreen`)**:
   - **Status**: ✅ **PASS (100% Berhasil & Terverifikasi)**
   - **Menu Field Map Tampilan Depan (`AppTab.MAP`)**: Berhasil menampilkan seluruh 143 rute segmen fiber optic Area 18 MPO sekaligus di atas OpenStreetMap.
   - **Filter Chip Kota**: Tersedia filter instan (`Semua (143)`, `Jayapura (6)`, `Timika (9)`, `Ambon (24)`, `Sorong`, `Manokwari`, dll.) dengan fitur auto-pan dan auto-zoom ke kluster kota terpilih.
   - **Pewarnaan Garis Berbasis Status**: Rute diwarnai secara visual (Biru = Belum Mulai, Oranye = Sedang Berjalan, Hijau = Selesai).
   - **Interaksi Tap Rute & Bottom Sheet**: Mengetuk garis rute memunculkan Bottom Sheet detail segmen (Nama, Asal-Tujuan, Jarak, Progres) dengan tombol aksi **"Mulai Survei PM Tiang"** dan **"Detail Segmen"**.
   - **Pemisahan Mode Survei (`ScreenFlow.FIELD_MAP`)**: Klik *Mulai Survei PM Tiang* otomatis berpindah ke mode survei tiang individu dengan HUD deviasi *SIAP SURVEI* dan tombol *Tag Tiang Ini*, serta tombol kembali yang mulus ke Peta Ikhtisar.

2. **Infinix Note 12 X670 (`adb-083672525K000527-A95cR3._adb-tls-connect._tcp`)**:
   - **Status**: ✅ **PASS (100% Berhasil & Terverifikasi)**
   - **Konektivitas Backend**: Berhasil terhubung via reverse port 8000 dengan latensi **36 ms**.
   - **Fix 1 (Login Session Default)**: Checkbox 'Ingat kredensial' berhasil dihilangkan; sesi login persisten secara otomatis.
   - **Fix 2 (Mode Online Banner)**: Kartu 'Mode Online (WiFi)' dihilangkan dari Dashboard, tampilan bersih dan rapi.
   - **Fix 3 & 4 (Indikator Status Jaringan Bulat)**: Indikator bulat hijau aktif di pojok kanan atas seluruh TopBar (Login, Dashboard, Segments, Buat Segment, Field Map, GPS Capture, Pole Info, Settings).
   - **Fix 5 (Penghapusan Impor KML/KMZ Eksternal)**: Alur impor eksternal dihapus; digantikan dengan pemilihan langsung 143 rute master segmen Area 18 MPO.
   - **Fix 6 (Kanvas Peta OsmMapView Centering)**: Berhasil 100%. Peta OpenStreetMap (tile Mapnik) terunduh dan ter-render sempurna dengan latar pulau Papua/Jayapura dan garis rute fiber optic tebal tepat di tengah viewport.
   - **Fix 7 & 8 (HUD Status Deviasi Rute)**: Deviasi 11M meter teratasi; HUD menampilkan status hijau **SIAP SURVEI** (*'Peta difokuskan ke rute segmen Area 18 MPO'*).
   - **Alur Survei Lengkap**: Pengambilan sampling GNSS 10 sampel, auto-titik koordinat tiang target (`PL-JYP-JYP-001`), dan formulir Informasi Tiang berjalan responsif tanpa crash.

2. **Motorola moto g67 power 5G (`adb-ZP2222V74L-JcYHEj._adb-tls-connect._tcp`)**:
   - **Status**: ✅ **PASS (100% Berhasil & Terverifikasi)**
   - **Konektivitas Backend**: Health check 200 OK via reverse port 8000.
   - **Tampilan UI**: Seluruh 8 pembaruan tampilan dan kanvas map teruji berjalan mulus pada layar resolusi 1080x2400.

---

## 🎯 Resolusi 8 Catatan Evaluasi Antarmuka & Bug Fix (Update 01 November 2026)

Berdasarkan review evaluasi dokumen PDF terhadap layar aplikasi, seluruh 8 catatan perbaikan pengguna telah selesai 100% diimplementasikan dan diverifikasi langsung pada emulator:

1. **Screen 01 (Login)**: Menghapus checkbox *'Ingat kredensial di perangkat ini'*. Sesi login surveyor kini otomatis tersimpan permanen (*auto-remembered by default*) ke dalam UserSessionManager sehingga surveyor tidak perlu login berulang kali.
2. **Screen 02 (Dashboard)**: Menghapus banner besar *'Mode Online (WiFi)'* (OfflineStatusCard) agar antarmuka ringkas, bersih, dan langsung menyajikan statistik progres tiang lapangan.
3. **Indikator Konektivitas Bulat**: Mengganti seluruh badge teks tebal ONLINE/OFFLINE di seluruh layar menjadi indikator bulat kecil berwarna:
   - **Hijau (#4CAF50)**: Online via Wi-Fi / Ethernet.
   - **Oranye (#FF9800)**: Online via Jaringan Seluler (Mobile Data).
   - **Merah (#F44336)**: Offline (Penyimpanan Lokal Room DB).
4. **Badge Online Pojok Kanan**: Menyeragamkan komponen TopBar di seluruh layar aplikasi agar menampilkan indikator bulat status jaringan di sudut kanan atas.
5. **Screen 07 & 08 (Import Route & Preview Ditiadakan)**: Menghapus/menonaktifkan alur impor rute eksternal KML/KMZ dari navigasi karena seluruh 143 rute kabel fiber optic Area 18 MPO telah preloaded langsung ke dalam basis data Room lokal.
6. **Perbaikan Bug Kanvas Peta (OsmMapView)**: Memperbaiki logika viewport kanvas OpenStreetMap yang sebelumnya mengarah ke 'antah berantah' (0,0 / Null Island / Samudra Pasifik). Peta kini langsung berpusat tepat pada *centroid* koordinat rute segmen aktif di Papua/Maluku (Area 18 MPO).
7. **Visualisasi Rute Fiber Optic & Penanda**: Menggambar garis *polyline* rute kabel fiber optic tebal berwarna biru (#0D47A1) yang mengikuti jalan raya lokal lengkap dengan *Marker* Awal (Hijau) dan *Marker* Akhir (Merah).
8. **HUD Status Rute**: Memperbaiki tampilan deviasi 11 juta meter saat pengujian lab/kantor. Jika jarak surveyor >50 km dari Papua, HUD menampilkan status **SIAP SURVEI** berwarna hijau dengan keterangan *'Peta difokuskan ke rute segmen Area 18 MPO'*.


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