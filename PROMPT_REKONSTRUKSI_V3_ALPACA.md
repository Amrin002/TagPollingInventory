# 🚀 MASTER PROMPT: Rekonstruksi TagPollingInventory (v3.1 — Full Architecture & Alpaca Backend)

Salin seluruh teks di dalam blok `<TASK_DIRECTIVE>` sampai akhir di bawah ini dan tempelkan (*copy-paste*) sebagai pesan pertama saat Anda membuka **Conversation Baru** di workspace `D:\PROJECT RNIMS\TagPollingInventory`.

---

```markdown
<TASK_DIRECTIVE>
Anda adalah Principal Android Mobile Architect & Lintasarta Telecom Systems Engineer kelas dunia. Tugas utama Anda adalah merekonstruksi secara bersih (Clean Restructure) dan mengimplementasikan aplikasi mobile Android "TagPollingInventory" (D:\PROJECT RNIMS\TagPollingInventory).

PENTING — DUA PILAR UTAMA YANG HARUS DISATUKAN SECARA SEMPURNA:
1. UNSUR APLIKASI LAPANGAN: Wajib mempertahankan seluruh standar dan unsur aplikasi dari dokumen POLE_INVENTORY_ARCHITECTURE.md (alur guided survey 2-stage: "Validate position" & "Take photo", CameraX augmented framing overlay, offline-first 100%, evaluasi keamanan/aksesibilitas, no paid Google APIs/no ARCore dependency).
2. MODEL DATA & BACKEND: Mengacu 100% ke standar Project Alpaca PRA (Poles Rental Agreement) & API Dashboard Alpaca di RNIMS/eNims (mengambil inventory & segmen eksisting dari API Dashboard Alpaca, klasifikasi tiang pole_7 / pole_9, asset_uid, ekspor Alpaca_Master_Poles.csv & KMZ).

ATURAN GIT PENTING:
- File `POLE_INVENTORY_ARCHITECTURE.md` DILARANG KERAS di-push ke git (sudah diproteksi di `.gitignore`).
- Setelah seluruh implementasi, pengujian, dan verifikasi build selesai, lakukan `git add`, `git commit` dengan conventional commit, dan `git push origin master`.
</TASK_DIRECTIVE>

<CONTEXT_MEMORY>
Seluruh analisis arsitektur, riset sistem, tangkapan layar Dashboard Alpaca, dan instruksi WhatsApp resmi dari atasan telah dipetakan:

1. INSTRUKSI RESMI ATASAN & TEMUAN DASHBOARD ALPACA:
   - Chat Atasan: "inventory, segment dll ambil dari API dashboard alpaca. buat apk tagging tiang amrin, nanti database inventory eksisting ambil dari situ ya."
   - URL Dashboard Alpaca: `rnims.lintasarta.net/dashboard/alpaca?mode=msa&incidentView=triage&area=MPO`
   - Tab POLES (PRA - Poles Rental Agreement):
     * Di dashboard Alpaca terdapat tab khusus "POLES" yang menampilkan rute IFT PO, titik tiang eksisting, dan status anomali aset sewa tiang.
     * Endpoint API Dashboard Alpaca:
       - Rute & Segmen: `GET /api/otdr/sia` & `GET /api/otdr/sia.geojson?area=MPO` atau `/api/dev_pgsql/segments-simple?alpacaProject=Yes`
       - Tiang Eksisting (PRA Assets): `GET /api/otdr/asset-assurance/map?po_id={poId}` (menghasilkan daftar aset tiang eksisting: `asset_uid`, `asset_class`, `geometry: [lng, lat]`, `match_status`)
       - Anomali Tiang: `GET /api/otdr/asset-assurance/anomalies`
       - Ringkasan Audit: `GET /api/otdr/asset-assurance`

2. STANDARISASI MODEL ALPACA PRA:
   - Klasifikasi Tiang Resmi:
     * Tiang 7 Meter: `pole_7` (T7)
     * Tiang 9 Meter: `pole_9` (T9)
   - Identitas Tiang: `asset_uid` berformat `POL-MPO-[KOTA]-[SEQ:04d]` (contoh: `POL-MPO-TMK-0001`).
   - Format Output Resmi:
     * CSV: `Alpaca_Master_Poles.csv` (sesuai format controller eNims `Alpaca_kmz.php`).
     * KMZ: `Alpaca_Master.kmz` (bundle Placemark P7/P9 + file foto tiang).

3. STANDAR UNSUR APLIKASI DARI POLE_INVENTORY_ARCHITECTURE.MD:
   - Guided Survey Workflow (Two-Action Cadence):
     a) Aksi 1 — "Validate position": Dilakukan di dekat dasar tiang (1–3 m). GNSS warming & sample averaging stabil (akurasi < 8m). Rekam raw coordinates, averaged coordinates, accuracy (±X.X m), dan visual offset jika terhalang.
     b) Aksi 2 — "Take photo": Surveyor mundur ke jarak pandang optimal (~10–15 m). Buka CameraX dengan portrait silhouette guide (bukan ARCore). Ambil 1 foto tiang utuh, auto-compress JPEG < 400 KB, watermark teks dan hash SHA-256.
   - Evaluasi Aksesibilitas & Keselamatan: Opsi menandai tiang "Unreachable / Approach Safety Deferral" tanpa memalsukan titik koordinat.
   - Deteksi Tiang Eksisting vs Tiang Baru: Otomatis mendeteksi jika surveyor berada dalam radius < 20m dari tiang eksisting yang ditarik dari API Alpaca (`asset-assurance/map`).
   - 100% Self-Hosted & Zero Google Paid APIs: Peta berbasis OpenStreetMap (OSM) offline, no Google Play Services dependency.

4. SEED DATA OFFLINE (AREA 18 / MPO):
   - Seed offline: `app/src/main/assets/mpo_segments.json` (143 segmen MPO hasil reindexing Redis RNIMS).
   - Staging server lokal aktif: MariaDB `rnims-db-local:3306`, Web `rnims-web-local:8082`, Relay `rnims-react-app:5173 / :8000`.
</CONTEXT_MEMORY>

<DESIGN_ARCHITECTURE>
Implementasikan arsitektur aplikasi Android yang ramping, presisi, dan kokoh:

1. LAYAR 1: Login & Konteks Profil (`LoginScreen.kt`)
   - Otentikasi terhubung ke backend RNIMS (`POST /api/auth/login`).
   - Offline Login Cache: Mendukung Quick PIN / offline credentials jika sedang di pedalaman Papua/Maluku tanpa sinyal.
   - Kunci Area Operasional: Mengunci konteks otomatis ke Area 18 (MPO).

2. LAYAR 2: Hybrid Route & Segment Explorer (`SegmentExplorerScreen.kt`)
   - 3 Lapis Penjelajah Rute:
     a) Quick Filter Chips: Rute prioritas Alpaca MPO (Sentani-Abepura, Manokwari, Timika, Sorong, Dobo, Tiakur).
     b) Katalog Lengkap 143 Segmen MPO: Terbaca offline dari `mpo_segments.json` (Jayapura Kota, Merauke, Biak, Nabire, Ambon, dll.).
     c) Input Manual Rute/PO Baru: Tombol "Tambah Rute / PO" untuk jalur baru yang belum terdaftar di Alpaca.
   - Auto-Suggest Proximity: Kartu pintar menampilkan segmen terdekat (< 500m) dari posisi GPS surveyor.
   - Peta Rute Offline: Menampilkan polyline jalur kabel optik segmen yang dipilih.

3. LAYAR 3: Ultra-Fast Tagging Canvas (`QuickTaggingScreen.kt` — Target < 1 Menit)
   - Sesuai standar `POLE_INVENTORY_ARCHITECTURE.md`:
     - Alur 2-Tahap Terpadu (Validate Position & Take Photo) dalam 1 sesi survei tanpa pindah-pindah layar.
     - GNSS Warming & Averaging: Indikator kualitas sinyal (Hijau <= 8m, Kuning 8-15m, Merah > 15m).
     - Tombol "Validate position" di dekat dasar tiang + Tombol "Unreachable / Approach Safety Risk" untuk tiang terhalang tebing/rawa/anjing galak.
     - CameraX Framing Guide: Overlay siluet tiang vertikal portrait, batas aman atas/bawah, centre line, pitch level indicator, dan konfirmasi "Base and Top visible".
     - Kompresi JPEG Otomatis (< 400 KB) + Watermark permanen (Asset UID, GPS, Timestamp).
     - **Pemilih Tipe Tiang Alpaca**: 2 tombol toggle besar 1-tap:
       `[ Tiang 7M (P7) ]`    `[ Tiang 9M (P9) (Default) ]`
     - Deteksi Tiang Eksisting: Menampilkan pill cerdas jika berada < 20m dari tiang Alpaca eksisting `[Gunakan Tiang Eksisting: POL-xxx]` vs `[Buat Tiang Baru]`.
     - Tombol 1-Tap: **"SIMPAN & LANJUT KE TIANG BERIKUTNYA"**.

4. LAYAR 4: Review, Export & Sync Server (`ReviewExportSyncScreen.kt`)
   - Statistik Segmen: Jumlah T7, Jumlah T9, Total Tiang, Estimasi Jarak Rata-rata Span Tiang.
   - Tombol **"Export Alpaca Master CSV"** (`Alpaca_Master_Poles.csv`).
   - Tombol **"Export Alpaca Master KMZ"** (`Alpaca_Master.kmz`).
   - Tombol **"Sinkronisasi ke API Alpaca RNIMS / eNims"**.

STRUKTUR DATABASE ROOM SQLITE LOKAL:
- Entity `SegmentEntity`:
  `id`, `name`, `lengthMeters`, `routeCoordinates`, `areaId`, `cityFilterKey`, `isAlpacaProject`, `status`, `totalPoles`.
- Entity `PoleEntity`:
  `id` (UUID), `assetUid` (`POL-MPO-[KOTA]-[SEQ]`), `segmentId`, `routeName`, `poId`, `assetClass` (`pole_7` / `pole_9`), `assetType` (`concrete` / `steel`), `latitude`, `longitude`, `accuracy`, `rawLatitude`, `rawLongitude`, `photoPath`, `ownership`, `condition`, `isSafetyDeferred`, `matchStatus`, `syncStatus`, `timestamp`.
</DESIGN_ARCHITECTURE>

<STRICT_RULES>
Patuhi aturan berikut tanpa pengecualian:
1. RULE-01 (ZERO NEW DB MIGRATIONS): Dilarang membuat tabel baru atau migration baru pada MariaDB server.
2. RULE-02 (OFFLINE-FIRST 100%): Seluruh alur tagging tiang, peta offline, kamera, dan Room wajib 100% berjalan dalam Airplane Mode.
3. RULE-03 (TAGGING SPEED < 1 MENIT): Pertahankan alur dua aksi (Validate Position + Take Photo) yang cepat dan bebas dialog konfirmasi berbelit-belit.
4. RULE-04 (ALPACAPROJECT BACKEND COMPLIANCE): Wajib menggunakan klasifikasi `pole_7` dan `pole_9`. Data tiang eksisting ditarik dari endpoint `/api/otdr/asset-assurance/*` dan format CSV wajib kompatibel dengan `Alpaca_kmz.py`.
5. RULE-05 (CAMERAX GUIDANCE / NO AR): Gunakan overlay grafis siluet vertikal pada CameraX (Bukan Google ARCore), tidak ada Maps SDK berbayar.
6. RULE-06 (GIT EXCLUSION & PUSH):
   - File `POLE_INVENTORY_ARCHITECTURE.md` DILARANG KERAS di-push ke git (pastikan diabaikan di `.gitignore`).
   - Setelah kompilasi `./gradlew assembleDebug` dan pengujian sukses, lakukan git add, commit, dan push ke `origin master`.
</STRICT_RULES>

<EXECUTION_PLAN>
Laksanakan rekonstruksi dalam 5 fase bertahap:

- FASE 1: Harmonize Domain & Room Entity (Alpaca Models + Architecture V2.3)
  Perbarui `Models.kt`, `RouteModels.kt`, `InventoryDao.kt`, dan `AppDatabase.kt` mengadopsi standar Alpaca PRA (`assetUid`, `assetClass` P7/P9, `matchStatus`, `rawLatitude/rawLongitude`, `isSafetyDeferred`).

- FASE 2: Hybrid Route & Segment Explorer
  Perbarui `SegmentExplorerScreen.kt` dengan 3 lapis seleksi rute: Quick Chips Alpaca MPO + Katalog 143 Segmen MPO (`mpo_segments.json`) + Entri Rute Baru manual + Proximity Auto-Suggest.

- FASE 3: Ultra-Fast Tagging Canvas (< 1 Menit) Sesuai POLE_INVENTORY_ARCHITECTURE.md
  Perbarui `QuickTaggingScreen.kt` dengan GNSS averaging ("Validate position"), CameraX portrait pole framing silhouette ("Take photo"), toggle cepat P7/P9, tombol "Unreachable Safety Deferral", dan deteksi tiang eksisting Alpaca (< 20m).

- FASE 4: Alpaca Export Engine & Sync Client
  Perbarui `ExportEngine.kt` untuk menghasilkan:
  1) `Alpaca_Master_Poles.csv` (kompatibel dengan `Alpaca_kmz.py`).
  2) `Alpaca_Master.kmz` (lengkap dengan foto tiang dan placemark P7/P9).
  3) Klien sinkronisasi ke endpoint API Dashboard Alpaca (`/api/otdr/asset-assurance`).

- FASE 5: Verifikasi Build, Pengujian & Git Push
  1) Jalankan `./gradlew testDebugUnitTest` untuk memvalidasi parser dan logic model.
  2) Jalankan `./gradlew assembleDebug` memastikan build APK sukses tanpa error kompilasi.
  3) Periksa `git status` memastikan `POLE_INVENTORY_ARCHITECTURE.md` TIDAK masuk staging.
  4) Lakukan Git Add, Commit terstruktur (`feat(alpaca): refactor TagPollingInventory to full POLE_INVENTORY_ARCHITECTURE and Alpaca PRA standard`), dan `git push origin master`.
</EXECUTION_PLAN>

Silakan konfirmasi kesiapan Anda, kemudian langsung mulai eksekusi FASE 1!
```
