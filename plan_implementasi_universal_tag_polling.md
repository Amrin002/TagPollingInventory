# 🏗️ PLAN IMPLEMENTASI: UNIVERSAL & DATA-DRIVEN TAGPOLLING INVENTORY

Dokumen ini adalah rencana kerja komprehensif untuk mengimplementasikan arsitektur *Universal & Data-Driven Ground-Truth Validation* pada aplikasi Android **TagPollingInventory**, sesuai dengan hasil interview kesepakatan desain arsitektur.

---

## 🎯 Tujuan Utama (Objective)
1. **Universal untuk Seluruh Area ID**: Menghilangkan seluruh pengkodean berbasis nama kota (*no hardcoded city/area*).
2. **On-Demand Pull & Offline Cache**:
   - Jika segmen memiliki tiang eksisting di server/bundle &rarr; otomatis dimuat ke Room SQLite dan siap divalidasi.
   - Jika segmen belum memiliki tiang (Ambon, Saumlaki, Tual, dll.) &rarr; otomatis siap untuk inventarisasi baru dari nol (`+ Tag Tiang`).
3. **Audit Trail Tiang Nihil (`MISSING`)**: Tiang eksisting yang tidak ditemukan di lapangan ditandai `MISSING` (tidak dihapus permanen) agar laporan rekonsiliasi ke pusat 100% akurat.
4. **Ekspor Standar Alpaca PRA**: Menghasilkan `Alpaca_Master_Poles.csv` dan `Alpaca_Master.kmz` tervalidasi yang siap dibuka di Excel dan Google Earth.

---

## 🗺️ Roadmap Eksekusi (6 Fase Modular)

```mermaid
flowchart LR
    F1["Fase 1<br/>DTO &amp; Room Model"] --> F2["Fase 2<br/>ApiClient &amp; Repository"]
    F2 --> F3["Fase 3<br/>ViewModel StateFlow"]
    F3 --> F4["Fase 4<br/>UI Detail &amp; Map"]
    F4 --> F5["Fase 5<br/>Export Engine"]
    F5 --> F6["Fase 6<br/>Build, Deploy, &amp; Test"]
```

---

### Fase 1: Pembaruan Model & DTO Parser Polimorfik
**File Target**: 
- `co.id.lintasarta.tagpollinginventory.data.model.RouteModels.kt`
- `co.id.lintasarta.tagpollinginventory.data.model.Models.kt`

**Tugas Spesifik**:
1. Definisikan DTO respon Alpaca PRA yang tangguh:
   - `AlpacaMapResponseDto`: Memuat `ift: AlpacaIftDto` dan `assets: List<AlpacaAssetItemDto>`.
   - `AlpacaIftGeometryDto`: Mendukung konversi `MultiLineString` (183 titik rute) menjadi `List<RouteCoordinate>`.
   - `AlpacaAssetGeometryDto`: Mendukung parsing koordinat `[lng, lat]` baik dari objek GeoJSON `{"type": "Point", "coordinates": [...]}` maupun flat list.
2. Tambahkan status `MISSING` pada enum `MatchStatus` dan `TagStatus`.
3. Tambahkan field penanda audit pada `PoleEntity`:
   - `isExistingPole: Boolean = false`
   - `missingReason: String? = null`
   - `accessories: Set<String> = emptySet()` (`slekan`, `joint_box`, `box_panel`, `suspension`).

---

### Fase 2: Lapisan Akses Data & Offline-First Caching
**File Target**:
- `co.id.lintasarta.tagpollinginventory.network.RnimsApiClient.kt`
- `co.id.lintasarta.tagpollinginventory.data.local.InventoryDao.kt`
- `co.id.lintasarta.tagpollinginventory.data.repository.InventoryRepository.kt`

**Tugas Spesifik**:
1. **`RnimsApiClient.kt`**:
   - `fetchAlpacaPoBundle(baseUrl: String, poId: String)`: Menarik rute IFT + daftar tiang eksisting dari endpoint `GET /api/otdr/asset-assurance/map?po_id={poId}`.
   - `loadBundledAlpacaPoBundle(context: Context, filename: String)`: Fallback offline membaca dari `mock_alpaca_po_089_tiakur.json`.
2. **`InventoryDao.kt`**:
   - Query pembaruan status `MISSING`:
     `UPDATE poles SET matchStatus = 'MISSING', status = 'CONFLICT' WHERE id = :poleId`
   - Query pencarian tiang berdasarkan segmen dengan status filter.
3. **`InventoryRepository.kt`**:
   - `pullPolesForSegment(segmentId: String)`: On-demand checking & fetching. Jika ada tiang di server/bundle, langsung insert massal ke Room (`insertPoles`).
   - `markPoleAsMissing(poleId: String, reason: String)`: Menandai tiang tidak ditemukan di lapangan fisik.
   - `addNewProvisionalPole(...)`: Generator UID otomatis `POL-[AREA]-[KOTA]-[SEQ:04d]`.

---

### Fase 3: State Management di ViewModel
**File Target**:
- `co.id.lintasarta.tagpollinginventory.ui.viewmodel.MainViewModel.kt`

**Tugas Spesifik**:
1. Observasi aktif terhadap perubahan segmen yang dipilih (`selectedSegmentId`):
   - Otomatis memicu `loadOrFetchPolesForSegment(segmentId)`.
2. Fungsi `refreshSegmentPolesOnline()`: Tombol manual tarik ulang tiang saat terkoneksi internet.
3. Fungsi `setPoleMissing(poleId: String)`: Pemicu penandaan tiang nihil.
4. Fungsi `validateExistingPole(poleId, photoPath, lat, lng, accessories, assetClass)`: Memvalidasi tiang eksisting dan mengubah status menjadi `COMPLETED` / `MATCHED` (warna hijau).

---

### Fase 4: Antarmuka Pengguna Adaptif & Seragam
**File Target**:
- `co.id.lintasarta.tagpollinginventory.ui.screens.SegmentDetailScreen.kt`
- `co.id.lintasarta.tagpollinginventory.ui.components.OsmMapView.kt`
- `co.id.lintasarta.tagpollinginventory.ui.screens.ReviewPoleScreen.kt`

**Tugas Spesifik**:
1. **`SegmentDetailScreen.kt`**:
   - Tampilan seragam: Header ringkasan segmen, minimap rute di bagian atas, dan daftar tiang di bagian bawah.
   - Tiang eksisting yang belum divalidasi diberi badge abu-abu: *"Eksisting (Belum Tervalidasi)"*.
   - Tiang yang telah divalidasi diberi badge hijau: *"Tervalidasi Lapangan (Matched)"*.
   - Tiang yang tidak ditemukan diberi badge merah coret: *"Nihil Lapangan (Missing)"*.
   - Aksi ketuk tiang membuka dialog/layar validasi cepat.
   - Opsi geser / menu aksi pada tiang belum tervalidasi: *"Tandai Tidak Ditemukan"*.
   - Tombol Floating Action Button `+ Tag Tiang` selalu hadir untuk menambah tiang baru kapan saja.
2. **`OsmMapView.kt`**:
   - Visualisasi marker tiang:
     - Hijau (`#2E7D32`): Tervalidasi (`COMPLETED` / `MATCHED`).
     - Abu-abu (`#757575`): Eksisting belum tervalidasi (`NOT_TAGGED`).
     - Merah (`#C62828`): Anomali / Missing.
     - Oranye (`#EF6C00`): Tiang baru tambahan (`PROVISIONAL`).

---

### Fase 5: Pembaruan Engine Ekspor Standar Alpaca PRA
**File Target**:
- `co.id.lintasarta.tagpollinginventory.export.ExportEngine.kt`

**Tugas Spesifik**:
1. Ekspor CSV `Alpaca_Master_Poles.csv`:
   - Header kolom standar: `asset_uid`, `asset_class`, `latitude`, `longitude`, `match_status`, `accessories`, `photo_filename`, `surveyor`, `timestamp`.
2. Ekspor KMZ `Alpaca_Master.kmz`:
   - Penataan folder placemark: `Validasi Selesai`, `Tiang Tambahan Baru`, `Tiang Nihil/Missing`.
   - Sanitasi XML menyeluruh (`escapeXml`) untuk mencegah parse error Google Earth.

---

### Fase 6: Kompilasi, Verifikasi Build, & Uji Perangkat Fisik
**Tugas Spesifik**:
1. Jalankan kompilasi Gradle: `./gradlew assembleDebug`.
2. Instal APK ke Motorola Moto G67 Power 5G via ADB.
3. Uji coba fungsionalitas:
   - Buka segmen **089 - Tiakur**: 373 tiang langsung muncul di minimap dan list.
   - Buka segmen tanpa tiang (misal Ambon): peta menampilkan garis rute dengan 0 tiang, tombol `+ Tag Tiang` siap digunakan.
   - Uji validasi 1 tiang dan tandai 1 tiang missing.
   - Uji ekspor KMZ dan verifikasi validitas XML-nya.
4. Git commit & push ke `origin/master`.
