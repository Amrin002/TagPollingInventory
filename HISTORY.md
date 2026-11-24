# Riwayat Pengembangan, Integrasi, & Pengujian (TagPolling Inventory)

Dokumen ini mencatat riwayat perubahan kode, arsitektur integrasi, dan pengujian fisik sinkronisasi dua arah antara aplikasi Android **TagPolling Inventory** dengan backend lokal **RNIMS / eNims** dan **Redis**.

---

## 📅 Rangkuman Sesi: 11 November 2026

### Pengamanan Sesi & Enkripsi Token Pengguna (Security Hardening)
- **Implementasi**:
  1. **Integrasi `androidx.security:security-crypto:1.1.0-alpha06`**:
     - Ditambahkan ke `libs.versions.toml` dan `app/build.gradle.kts`.
  2. **`EncryptedSharedPreferences` dengan Hardware MasterKey (`UserSessionManager.kt`)**:
     - Seluruh data sesi pengguna, profil, role, dan token sesi kini disimpan dalam file terenkripsi `secure_user_session_prefs`.
     - Kunci enkripsi dikelola langsung oleh chip hardware Android Keystore (`MasterKey.KeyScheme.AES256_GCM`).
     - Menggunakan skema enkripsi kunci `AES256_SIV` dan skema enkripsi nilai `AES256_GCM`.
     - Dilengkapi fallback otomatis yang aman jika perangkat tidak memiliki dukungan hardware KeyStore.
  3. **Migrasi Otomatis & Pembersihan Data Polos (Zero-Downtime Migration)**:
     - Saat sesi dimuat, jika data belum ada di penyimpanan terenkripsi, aplikasi membaca preferensi lama (`user_session_prefs`), memindahkannya ke `secure_user_session_prefs`, lalu menghapus teks polosnya dari preferensi lama.
  4. **Penangkapan Token Backend Resmi (`RnimsApiClient.kt`)**:
     - Ditambahkan properti `token` pada `RnimsUser`. Jika server mengembalikan token JWT/Bearer asli, token tersebut otomatis ditangkap dan disimpan secara terenkripsi ke dalam sesi pengguna.
  5. **Proteksi Cadangan Data (`AndroidManifest.xml`)**:
     - Mengubah `android:allowBackup="false"` untuk mencegah penyalinan file basis data atau sesi melalui antarmuka perintah ADB tanpa izin.
- **Poin yang Ditunda Sesuai Arahan Pengguna**:
  - Konfigurasi HTTPS / migrasi dari `usesCleartextTraffic`: Ditunda hingga H-3 rilis produksi.
  - Isolasi data tiang multi-akun: Ditunda setelah versi web manajemen pengguna selesai dibuat.
  - Login offline: Dipertahankan seperti semula untuk keperluan pengujian lokal.
- **Hasil Verifikasi**:
  - Kompilasi `assembleDebug`: **BERHASIL** (`BUILD SUCCESSFUL`).
  - Unit test `testDebugUnitTest`: **BERHASIL** (`BUILD SUCCESSFUL`).

---

## 📅 Rangkuman Sesi: 10 November 2026

### Perbaikan Bug Modul Tag Polling PM (Preventive Maintenance / Survei Lengkap)
- **Akar Masalah**:
  1. Pada `InventoryRepository.kt`, fungsi `setDraftPhotoForSlot(slotIndex, photoPath)` sebelumnya hanya mengeksekusi `current.copy(photoPath = photoPath)`, tanpa memasukkan `photoPath` ke dalam array 10 slot `photoPaths`. Akibatnya, `draftPole.photoPaths` tetap kosong (`emptyList()`), menyebabkan kartu preview slot foto pada `PhotoCaptureScreen` tetap berstatus `"BELUM ADA FOTO"` dan thumbnail foto tidak pernah muncul di pratinjau.
  2. Fungsi `removeDraftPhotoFromSlot(slotIndex)` sebelumnya menghapus `current.photoPath` tunggal dan mengabaikan `slotIndex`.
  3. Pada `saveDraftPole()`, kode sebelumnya memangkas multi-slot foto menjadi hanya 1 foto (`listOf(draft.photoPath!!)`).
  4. Pada `ReviewPoleScreen.kt`, kalkulasi jumlah foto sebelumnya menghitung seluruh ukuran list (10 slot) bukan jumlah foto aktif yang valid, serta belum mendukung dialog perbesar foto penuh (full screen inspection).
- **Solusi yang Diterapkan**:
  - **`InventoryRepository.kt`**:
    - Memperbaiki `setDraftPhotoForSlot(slotIndex, photoPath)` untuk menggunakan `normalizePhotoSlots(baseList)`, memperbarui slot tepat pada `slotIndex`, menghapus file lama jika di-retake, menghitung SHA-256 berkas foto, serta menyimpan `photoPaths` 10 slot secara reaktif ke `_currentDraftPole`, `_poles`, dan Room DB.
    - Memperbaiki `removeDraftPhotoFromSlot(slotIndex)` agar hanya menghapus foto pada slot target dan mengosongkan string slot tersebut tanpa merusak slot lainnya.
    - Memperbaiki `saveDraftPole()` untuk mempertahankan seluruh slot foto (`preservedPhotoPaths`) dan menandai status tiang menjadi `COMPLETED` jika minimal terdapat 1 foto aktif tersimpan.
  - **`PhotoCaptureScreen.kt`**:
    - Memperbaiki state `slotPaths` dengan `remember(rawPhotoPaths, singlePhoto)` dan fallback otomatis slot 0 ke `draftPole.photoPath`.
    - Pengecekan status slot `isFilled` kini memvalidasi `currentPath.isNotEmpty() && File(currentPath).exists()` sehingga thumbnail `AsyncImage` langsung tampil setelah foto diambil.
    - Menambahkan `CircularProgressIndicator` pada tombol shutter kamera saat foto sedang diproses/diberi watermark agar pengguna mendapatkan feedback visual yang jelas.
  - **`ReviewPoleScreen.kt`**:
    - Menyaring foto valid dengan `validPhotos = currentDraft?.photoPaths?.filter { it.isNotBlank() && File(it).exists() }`.
    - Menampilkan badge jumlah foto terlampir secara akurat (`✓ X Foto Terlampir`).
    - Menambahkan fitur klik pratinjau foto untuk memperbesar foto layar penuh (full-screen inspection dialog) lengkap dengan tombol tutup.
  - **Unit Testing**:
    - Menambahkan file pengujian unit `PmSurveyPhotoSlotTest.kt` yang menguji normalisasi 10 slot, penempatan slot terpisah, retake, penghapusan per slot, dan penyimpanan status draft. Semua 4 pengujian berhasil lolos (`BUILD SUCCESSFUL`).

---

## 📅 Rangkuman Sesi: 09 November 2026

### 1. Lingkungan Pengujian (Test Environment)
| Komponen | Spesifikasi / Konfigurasi | Status |
| :--- | :--- | :--- |
| **Perangkat Fisik** | Motorola Moto G67 Power 5G (`ZP2222V74L`), Android 15 | Terhubung USB ADB |
| **Tunneling / Network** | `adb reverse tcp:8000 tcp:8000` (Latensi: 18 ms) | Aktif (Hijau) |
| **Backend API Server** | Docker `test-api` (Python 3.11 FastAPI) di port `8000` | Berjalan |
| **Cache & Realtime DB** | Docker `rnims-redis-local` (Redis 7 Alpine) di port `6379` | Berjalan |
| **Relational Database** | Docker `rnims-db-local` (MariaDB 10.5) di port `3306` (`RegionalOperation` & `cias`) | Berjalan |
| **Integritas Server** | Nol baris perubahan pada kode backend (100% untouched) | Terpenuhi |

---

## 2. Riwayat Perubahan & Perbaikan Fitur

### A. Autentikasi & Validasi Input (`LoginScreen.kt` & `UserSessionManager.kt`)
- **Validasi Format Email RFC-5322**:
  - Mengharuskan format email yang valid jika pengguna memasukkan karakter `@`.
  - Menampilkan banner peringatan ramah pengguna: *"Gunakan format email valid (contoh: surveyor.mpo@lintasarta.co.id)"*.
- **Pembersihan Otomatis Spasi Keyboard**:
  - Mengatasi masalah autocorrect keyboard virtual Android yang menyisipkan spasi setelah tanda titik pada email.

### B. Dynamic Role Badges (`SettingsScreen.kt`)
- Pengguna yang login kini mendapatkan penanda badge role dan kategori operasi yang sesuai profil:
  - `admin@bewithdhanu.in` âž” **System Administrator** (Badge Ungu: *Super Admin*)
  - `manager@bewithdhanu.in` âž” **Manager Operasional** (Badge Biru: *Operations Management*)
  - `surveyor.mpo@lintasarta.co.id` âž” **Field Engineer MPO** (Badge Hijau: *Field Operations*)
  - `amrin@lintasarta.co.id` âž” **Field Engineer MPO** (Badge Hijau: *Field Operations*)

### C. Penyesuaian Endpoint Sinkronisasi Tiang (`RnimsApiClient.kt` & `MainViewModel.kt`)
- **Akar Masalah Sebelumnya**:
  Aplikasi sebelumnya mengarah ke `POST /api/otdr/asset-assurance/sync`. Di backend lokal `test-api`, rute tersebut tidak ada dan diteruskan (*relay*) oleh `routes/relay.py` ke IP Data Center internal `123.231.138.31` yang unreachable di luar intranet Lintasarta, menyebabkan timeout 20 detik (HTTP 500 / HTTP 0).
- **Solusi Sisi Client**:
  Mengalihkan target sinkronisasi batch tiang ke endpoint realtime data storage backend yang sudah tersedia:
  ðŸ‘‰ **`POST /api/redis/data`** dengan key path: `survey:poles:area:<areaId>`.
- **Format Payload JSON**:
  ```json
  {
    "path": "survey:poles:area:18",
    "data": {
      "area_id": 18,
      "total_poles": 2,
      "timestamp": "2026-11-09T14:41:41+09:00",
      "poles": [ ... ]
    },
    "type": "json"
  }
  ```
- **Fallback Hierarchy**: Tetap mempertahankan rute cadangan ke `/api/msfo/ta/sync` dan `/api/otdr/asset-assurance/sync` jika server target menggunakan arsitektur relay.

---

## 3. Hasil Pengujian Aliran Data Dua Arah (Bidirectional Flow)

### âœ… Aliran 1: Backend âž” Aplikasi Mobile (Tarik Segmen)
- **Endpoint**: `GET http://127.0.0.1:8000/api/msfo/ta/routes?area_id=18`
- **Hasil**: Server merespons `HTTP 200 OK`.
- **Data Tersimpan**: Sebanyak **143 Segmen resmi Area 18 (MPO)** berhasil disinkronkan dan tersimpan ke database SQLite Room lokal pada aplikasi, siap digunakan secara offline di menu Peta & Segmen.

### âœ… Aliran 2: Aplikasi Mobile âž” Backend (Push Data Tiang Survei)
- **Aksi Pengguna**: Menekan tombol *"Sinkronisasi ke API Alpaca RNIMS / eNims"* di Tab Sync.
- **Respon Client**: Pop-up konfirmasi sukses muncul di layar HP:
  > *"Sinkronisasi Berhasil: Berhasil mensinkronkan 2 tiang ke server RNIMS & Redis (key: survey:poles:area:18)!"*
- **Status UI di HP**: Status tiang lapangan (`POL-MPO-ABN-0001`) langsung ter-update dari `PENDING` menjadi badge hijau `SYNCED`.
- **Verifikasi Realtime di Redis (`rnims-redis-local`)**:
  Perintah `docker exec rnims-redis-local redis-cli GET survey:poles:area:18` mengonfirmasi data tersimpan utuh:
  ```json
  {
    "timestamp": "2026-11-09T12:41:43+07:00",
    "data": {
      "area_id": 18,
      "total_poles": 2,
      "timestamp": "2026-11-09T14:41:41+09:00",
      "poles": [
        {
          "pole_code": "POL-MPO-ABN-0001",
          "segment_id": "SEG-1715",
          "sequence": 1,
          "latitude": -3.6953999308669836,
          "longitude": 128.18139883682937,
          "accuracy": 2.8,
          "timestamp": "09 Nov 2026 â€” 10:02",
          "pole_type": "Steel",
          "condition": "Good",
          "ownership": "Lintasarta",
          "height": "9m",
          "tag_number": "",
          "has_fo_cable": true,
          "cable_condition": "Good",
          "equipment": ["ODP", "Closure"],
          "notes": "",
          "photos_count": 1,
          "status": "Completed",
          "infra_type_id": 2,
          "infra_state": 1,
          "infra_sys_id": "POL-MPO-ABN-0001"
        },
        {
          "pole_code": "POL-MPO-ABN-0002",
          "segment_id": "SEG-1715",
          "sequence": 2,
          "latitude": -3.689133732537286,
          "longitude": 128.18710694662644,
          "accuracy": 9.8574,
          "timestamp": "04 Nov 2026 â€” 09:11",
          "pole_type": "Steel",
          "condition": "Good",
          "ownership": "Lintasarta",
          "height": "7m",
          "tag_number": "",
          "has_fo_cable": true,
          "cable_condition": "Good",
          "equipment": ["ODP", "Closure"],
          "notes": "",
          "photos_count": 1,
          "status": "Completed",
          "infra_type_id": 2,
          "infra_state": 1,
          "infra_sys_id": "POL-MPO-ABN-0002"
        }
      ]
    }
  }
  ```
- **Verifikasi Query API**: `GET http://127.0.0.1:8000/api/redis/data?path=survey:poles:area:18` mengembalikan status `success` dengan `total_poles: 2`.

---

## 4. Riwayat Pembersihan & Manajemen Repositori Git

1. **Pembersihan Root Project**:
   Semua file dokumentasi non-Android, prompt, spreadsheet, dan log evaluasi dipindahkan ke direktori arsip:
   ðŸ“ `D:\PROJECT RNIMS\TagPollingInventory_Archive\`
2. **Proteksi File Arsitektur**:
   File `POLE_INVENTORY_ARCHITECTURE.md` dipertahankan di root lokal dan dijaga dalam `.gitignore` sehingga tidak pernah ikut ter-commit ke git.
3. **Commit & Push Git**:
   - **Commit SHA**: `448d022` & `6ab026b`
   - **Pesan Commit**: `refactor: clean up non-android files to archive, add email validation & dynamic roles, and configure pole sync to local redis endpoint` & `docs: add comprehensive project and sync history documentation`
   - **Remote Branch**: `https://github.com/Amrin002/TagPollingInventory.git` (`origin/master`)
   - **Status Working Tree**: `working tree clean`

---

## 5. Perbaikan Bug Export KMZ Google Earth (Parse Error Line 5 Column 44)

### Masalah yang Ditemukan
Saat file `Alpaca_Master.kmz` hasil export dibuka di aplikasi Google Earth Desktop, muncul dialog error:
> `Open of file "Alpaca_Master.kmz" failed: Parse error at line 5, column 44: not well-formed (invalid token)`

### Root Cause Analysis (Penyebab Utama)
1. **Unescaped XML Entity `&`**:
   Pada `ExportEngine.kt`, tag `<description>` dituliskan:
   ```xml
   <description>Project Alpaca PRA Survey & As-Built Poles (Regional MPO)</description>
   ```
   Karakter literal `&` pada kolom 44 melanggar spesifikasi standar XML 1.0 (Google Earth XML parser menolak token `&` tanpa entitas `&amp;`).
2. **Karakter Dinamis Tanpa Sanitasi**:
   Semua data dinamis seperti `segmentName`, `effectiveAssetUid`, `assetClass`, `assetType`, `poId`, `segmentId`, `matchStatus`, dan `syncStatus` sebelumnya belum disanitasi terhadap karakter khusus XML (`&`, `<`, `>`, `"`, `'`).
3. **Packaging ZIP KMZ**:
   Entry foto yang kosong atau duplicate dapat menimbulkan korupsi arsip ZIP.

### Tindakan Perbaikan (`ExportEngine.kt`)
1. **Menambahkan Fungsi Sanitasi Entitas XML**:
   ```kotlin
   private fun escapeXml(input: String?): String {
       if (input == null) return ""
       return input
           .replace("&", "&amp;")
           .replace("<", "&lt;")
           .replace(">", "&gt;")
           .replace("\"", "&quot;")
           .replace("'", "&apos;")
   }
   ```
2. **Sanitasi Tag Description & Seluruh Node Dinamis**:
   - Header Dokumen: `<description>Project Alpaca PRA Survey &amp; As-Built Poles (Regional MPO)</description>`
   - Semua nama Placemark, rute kabel FO, dan nilai `<ExtendedData>` dibungkus dengan `escapeXml()`.
3. **Deduplikasi dan Validasi File Foto Aktual**:
   Menambahkan `mutableSetOf<String>()` untuk mencegah duplikasi entry dalam arsip ZIP KMZ dan memastikan hanya foto fisik yang ada (`exists() && length() > 0`) yang dimasukkan ke folder `images/`.

### Hasil Verifikasi & Pengujian
- **Kompilasi & Build**: Berhasil dikompilasi ke APK debug (`BUILD SUCCESSFUL`).
- **Instalasi ke Perangkat Fisik**: Terpasang live di Motorola Moto G67 Power 5G (`ZP2222V74L`).
- **Uji Ekspor Langsung**: Tombol "Export Alpaca Master KMZ" ditekan di aplikasi, menghasilkan file KMZ baru sebesar Â±14.6 MB.
- **Validasi Parsing XML**: File `doc.kml` di dalam `Alpaca_Master.kmz` diekstrak dan diuji menggunakan XML Parser `xml.etree.ElementTree`. Hasil: **100% Valid & Well-Formed**, tanpa ada syntax error.
- **Git Commit**: `fe2cf57` (`fix(export): escape XML entities in KML and ensure valid KMZ for Google Earth`) telah di-push ke branch `master`.
- **Salinan File Siap Pakai**: Salinan file KMZ yang telah terverifikasi telah diletakkan di direktori Downloads lokal (`%USERPROFILE%\Downloads\Alpaca_Master.kmz`).

---

## 6. Implementasi Live User GPS Marker & Accuracy Halo di Seluruh Peta

### Masalah yang Ditemukan
Titik penanda lokasi pengguna (Surveyor GPS marker) tidak muncul pada tampilan peta (`AreaOverviewMapScreen` / Tab Peta, `SegmentDetailScreen` / Detail Segmen, dan `OsmMapView` / Peta Lapangan).

### Root Cause Analysis (Penyebab Utama)
1. **Pembaruan Lokasi Tidak Diaktifkan Otomatis**:
   `MainViewModel.startLocationUpdates()` hanya dipanggil saat masuk ke alur survei tiang lapangan (`FieldMapScreen`, `QuickTaggingSessionScreen`), tetapi **tidak pernah dipanggil** saat membuka Tab Peta (`AreaOverviewMapScreen`) maupun Layar Detail Segmen (`SegmentDetailScreen`).
2. **Filter Akurasi Terlalu Ketat di `LocationProvider.kt`**:
   Filter awal `accuracy > 50f` langsung membuang seluruh sampel lokasi jika akurasi > 50 meter. Pada saat pengujian di dalam ruangan / meja kerja, sinyal Fused Location atau BTS seluler/Wi-Fi umumnya memiliki akurasi 50â€“100m sehingga status lokasi selalu dianggap `isAvailable = false`.
3. **Parameter `setWaitForAccurateLocation(true)`**:
   Membuat Fused Location Provider menunggu kuncian satelit GNSS murni dan menunda pengiriman lokasi instan.
4. **Visual Marker Kurang Kontras**:
   Marker lama berupa lingkaran biru kecil 36px tanpa lingkaran radius akurasi (`accuracy circle`), sehingga mudah tertutup oleh garis polyline segmen fiber optik.
5. **Tombol "Lokasi Saya" Salah Arah di `OsmMapView.kt`**:
   Floating Action Button dengan ikon GPS sebelumnya memusatkan kamera ke rute kabel FO (`"Peta dipusatkan ke rute segmen"`), bukan ke koordinat GPS surveyor.

### Tindakan Perbaikan
1. **Komponen Standar [`UserLocationOverlayHelper.kt`](file:///d:/PROJECT%20RNIMS/TagPollingInventory/app/src/main/java/co/id/lintasarta/tagpollinginventory/ui/components/UserLocationOverlayHelper.kt)**:
   - Dibuat ikon penanda surveyor modern ala Google Maps / GIS: lingkaran luar putih tebal, inti Electric Blue (`#1A73E8`), dan titik putih poros presisi.
   - Menambahkan lingkaran radius akurasi GPS dinamis (`Polygon.pointsAsCircle`) dengan warna soft translucent blue (`#261A73E8`) yang merefleksikan radius akurasi aktual.
   - Memastikan overlay user selalu diposisikan di lapisan paling atas (`Z-Index`).
2. **Optimasi `LocationProvider.kt`**:
   - Pengambilan instan `lastKnownLocation` dari semua provider (`GPS`, `NETWORK`, `PASSIVE`) saat `startLocationUpdates()` dipanggil.
   - Menonaktifkan blocking: `setWaitForAccurateLocation(false)`.
   - Menaikkan batas filter awal hingga 300m agar titik lokasi langsung muncul di peta saat awal aplikasi dibuka, lalu otomatis menyempit seiring kuncian satelit GNSS.
3. **Pemicu Otomatis di ViewModel & Siklus Hidup**:
   - `MainViewModel.init`: Otomatis memanggil `startLocationUpdates()` jika izin GPS sudah diberikan.
   - `MainActivity.onResume`: Memastikan listener lokasi selalu aktif saat aplikasi kembali ke foreground.
   - `AreaOverviewMapScreen` & `SegmentDetailScreen`: Menambahkan `LaunchedEffect(Unit) { viewModel.startLocationUpdates() }`.
4. **Pemisahan Kontrol Peta di `OsmMapView.kt`**:
   - Tombol **"Lokasi Saya"** (ikon GPS): Memusatkan kamera langsung ke koordinat surveyor (`GeoPoint(userLocation.latitude, userLocation.longitude)`).
   - Tombol **"Pusatkan Rute"** (ikon CenterFocus): Memusatkan kamera ke rute kabel fiber optik segmen.

### Hasil Verifikasi & Deploy
- **Build**: `BUILD SUCCESSFUL in 5m 23s`.
- **Deploy**: APK debug terbaru berhasil diinstal langsung ke perangkat fisik Motorola Moto G67 Power 5G (`ZP2222V74L`).
- **Verifikasi Dumpsys**: Listener lokasi terdaftar aktif di sistem Android (`Request[@+1s0ms HIGH_ACCURACY, WorkSource{co.id.lintasarta.tagpollinginventory}]`).
- **Git Commit**: `a7d3fe1` (`feat(map): display prominent live surveyor GPS location marker and accuracy halo across all map views`) telah di-push ke branch `master`.



---

## 7. Rangkuman Sesi: 10 November 2026

### A. Lingkungan Pengujian & Status Perangkat Fisik
| Komponen | Spesifikasi / Konfigurasi | Status |
| :--- | :--- | :--- |
| **Perangkat Fisik** | Motorola Moto G67 Power 5G (`ZP2222V74L`), Android 15 | Terhubung USB ADB |
| **Tunneling / Port Forwarding** | `adb reverse tcp:8000 tcp:8000` | Aktif (Hijau) |
| **Backend API Server** | Docker `test-api` (FastAPI) di port `8000` | Berjalan (100% Untouched) |
| **Cache & Realtime DB** | Docker `rnims-redis-local` di port `6379` | Berjalan |
| **Database Aplikasi** | SQLite Room (`tagpolling_inventory_db`) versi 6 | Terverifikasi |

---

### B. Perancangan & Implementasi Universal Tag Polling (Alpaca PRA Standard)
Sesuai arahan arsitektur, aplikasi dirancang agar bersifat **universal dan data-driven** untuk seluruh Area ID di Indonesia, tanpa hardcoded nama kota:
1. **Segmen dengan Tiang Eksisting (Alpaca PRA)**:
   - Segmen yang sudah memiliki inventaris tiang (misal: Tiakur, Sentani-Abepura, Manokwari, Dobo).
   - Saat segmen dibuka, tiang referensi otomatis dipetakan ke minimap dan daftar tiang untuk diverifikasi oleh surveyor di lapangan.
2. **Segmen Tanpa Tiang Eksisting (Luar PRA / Survei Mandiri)**:
   - Segmen yang belum memiliki data tiang di server (misal: Ambon, Tual, Saumlaki).
   - Segmen tetap memuat jalur rute kabel FO dengan total tiang 0, dan surveyor dapat langsung menambahkan tiang baru mandiri (*Provisional Pole*) dari nol.
3. **Audit Trail Rekonsiliasi Lapangan (Status MISSING)**:
   - Tiang referensi server yang di lapangan terbukti tidak ada/hilang **tidak dihapus secara permanen** dari database.
   - Tiang ditandai sebagai `MISSING` (Nihil Lapangan) dengan opsi alasan cepat (misal: *Tiang Roboh / Rusak Berat*, *Rute Dialihkan*, *Tiang Dibongkar*, *Lokasi Tidak Dapat Diakses*).
   - Disediakan tombol pemulihan (*Restore*) jika surveyor keliru menandai nihil.

---

### C. Detail Perubahan Kode & Modul
* **`RouteModels.kt`**:
  - Menambahkan model DTO standar Alpaca PRA GeoJSON: `AlpacaMapResponseDto`, `AlpacaAssetItemDto`, `AlpacaIftGeometryDto`, `AlpacaAssetGeometryDto`.
* **`Models.kt`**:
  - Menambahkan enum status `MatchStatus.MISSING` dan `TagStatus.MISSING`.
  - Menambahkan properti `missingReason: String?` pada `PoleEntity`.
* **`AppDatabase.kt` & `InventoryDao.kt`**:
  - Peningkatan skema Room DB ke **Versi 6**.
  - Menambahkan query `@Query` untuk update status tiang nihil (`updatePoleMissing`) dan penghitungan tiang per segmen.
* **`InventoryRepository.kt`**:
  - Menambahkan fungsi `importSegmentAssetMap()` untuk parsing payload Alpaca PRA GeoJSON menjadi entitas segmen dan tiang.
  - Menambahkan fungsi `pullPolesForSegment()` untuk pengambilan data tiang secara *on-demand*.
  - Menambahkan `markPoleAsMissing()`, `restoreMissingPole()`, dan `addProvisionalPole()`.
  - Proteksi data survei lapangan: Tiang berstatus `COMPLETED` yang sudah disurvei surveyor tidak akan tertimpa saat proses re-import/pull server.
* **`SegmentDetailScreen.kt`**:
  - Dialog interaktif *"Tandai Nihil / Tidak Ditemukan"* dengan pilihan alasan cepat (*quick chips*).
  - Indikator badge merah untuk tiang yang ditandai nihil beserta tombol *Pulihkan Tiang*.
  - Tombol tarik data server on-demand (*Tarik Server Alpaca*).
* **`SegmentExplorerScreen.kt`**:
  - Memperbaiki format tampilan jumlah tiang pada kartu segmen menjadi `"${completedPoles} / ${totalPoles} tiang"` dengan indikator warna dinamis:
    - Hijau: Segmen selesai disurvei (misal: `2 / 2 tiang`).
    - Oranye: Segmen memiliki tiang referensi server yang siap divalidasi (misal: `0 / 373 tiang`).
    - Abu-abu: Segmen mandiri baru (misal: `0 tiang`).
* **`ExportEngine.kt`**:
  - Menambahkan kolom `missing_reason` dan `is_existing_pole` pada ekspor CSV.
  - Menambahkan *StyleMap* `#missingPole` dengan ikon khusus pada ekspor KMZ/KML.

---

### D. Hasil Pengujian Fisik Lapangan & Audit Data Lokal
1. **Uji Survei Lapangan di Kota Ambon**:
   - Pengguna melakukan uji coba fisik langsung di Kota Ambon menggunakan perangkat Moto G67 5G.
   - Berhasil mengambil geotag foto dan menyimpan **2 tiang survei** pada `SEGMENT_2843` (`POL-MPO-ABN-0001` dan `POL-MPO-ABN-0002`).
2. **Audit Database Lokal HP (`tagpolling_inventory_db`)**:
   - Pengecekan langsung membuktikan bahwa di database lokal sebelumnya **tidak ada data tiang server yang ikut ter-pull**.
   - Katalog 143 rute bawaan (`mpo_segments.json`) murni hanya berisi koordinat jalur kabel optik (PolyLine), tanpa data titik tiang.
   - Di server pusat Alpaca web live, data tiang memang baru tersedia pada segmen pilot PRA (seperti Tiakur PO 089, Sentani PO 210, Manokwari PO 224, Dobo PO 063), sedangkan Ambon belum memiliki inventaris tiang PRA di server.
3. **Penghapusan Data Tiakur (373 Tiang)**:
   - Sesuai instruksi pengguna, data 373 tiang Tiakur (`mock_alpaca_po_089_tiakur.json`) beserta segmen `SEG-089` telah **dihapus bersih** dari codebase, assets, dan database SQLite lokal di HP fisik Moto G67.
   - Database lokal HP kini murni memuat 143 rute bawaan MPO dan 3 tiang riil hasil survei mandiri pengguna di Kota Ambon (`SEGMENT_2843`).
4. **Rencana Langkah Lanjutan (Next Step)**:
   - Mendiskusikan struktur dan URL endpoint asli dari web live (melalui inspect Network F12 di web Alpaca) sebelum melanjutkan implementasi pengambilan tiang otomatis dari server.
