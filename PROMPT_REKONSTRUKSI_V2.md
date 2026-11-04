# 🚀 MASTER PROMPT: Rekonstruksi & Implementasi Aplikasi TagPollingInventory (v2.0)

Salin seluruh teks di bawah ini dan tempelkan (*copy-paste*) sebagai pesan pertama saat Anda membuka **Conversation Baru** di workspace `D:\PROJECT RNIMS\TagPollingInventory`.

---

```markdown
<TASK_DIRECTIVE>
Anda adalah Principal Android Mobile Architect & Lintasarta Telecom Systems Engineer kelas dunia. Tugas utama Anda adalah merekonstruksi secara bersih (Clean Restructure) dan mengimplementasikan aplikasi mobile Android "TagPollingInventory" (D:\PROJECT RNIMS\TagPollingInventory) dari kondisi prototype manual lama menjadi aplikasi lapangan produksi offline-first berkecepatan tinggi yang terintegrasi penuh dengan ekosistem backend RNIMS 2.0 dan eNims.
</TASK_DIRECTIVE>

<CONTEXT_MEMORY>
Seluruh analisis arsitektur, riset database, dan keputusan teknis telah matang dari sesi sebelumnya:

1. HUBUNGAN DUA DUNIA BACKEND (RNIMS vs eNIMS):
   - RNIMS (MSFO / Segmen): Menyediakan 143 segmen kabel optik untuk Area 18 (MPO - Maluku Papua) hasil reindexing Redis 2026. Ini digunakan sebagai jalur kabel & penunjuk arah surveyor.
   - eNims (Project / OSP / Coverage): Tempat tiang fisik tercatat di tabel `infra` (infraTypeId = 2 untuk Pole, infraState = 1 untuk Draft, infraState = 2 untuk Published). Modul `CoverageInfo.php` (`getNearestEquipment`) memeriksa tiang-tiang ini untuk acuan penjualan baru (PSB).
   - Acuan Spesifikasi Resmi: Dokumen `POLE_INVENTORY_ARCHITECTURE.md` (Versi 2.3) dan terjemahannya `DOKUMENTASI_ARSITEKTUR_POLE_INVENTORY_ID.pdf`.

2. INFRASTRUKTUR STAGING LOKAL (AKTIF DI LAPTOP):
   - MariaDB: Container `rnims-db-local` port 3306 (Database `RegionalOperation` & `cias`).
   - Redis: Container `rnims-redis-local` port 6379.
   - Web eNims: Container `rnims-web-local` port 8082.
   - RNIMS React / Mock API: Container `rnims-react-app` port 5173 / port 8000.
   - Akun Uji Coba: `surveyor.mpo@lintasarta.co.id` / Password: `123456` (Otomatis Area ID 18 / MPO).
   - Seed Data: `app/src/main/assets/mpo_segments.json` (360 KB, 143 segmen MPO).

3. SOLUSI UX SEGMEN (MENGATASI 143 SEGMEN YANG TERLALU BANYAK):
   - Jangan tampilkan 143 segmen sekaligus dalam daftar panjang!
   - Terapkan 3 Lapis Filter:
     a) GPS Auto-Detect ("Segmen Terdekat dari Posisi Anda < 500m").
     b) Filter Kota / City Chips: [Timika (Kab. Mimika)] [Sorong] [Jayapura] [Ambon] [Merauke].
     c) Pin Segmen Aktif: Segmen yang sedang dikerjakan tetap menempel di halaman utama.
</CONTEXT_MEMORY>

<DESIGN_ARCHITECTURE>
Lakukan "Clean Restructure" di dalam project yang ada saat ini (buang prototype lama yang manual, bangun ulang hanya 4 layar inti yang ramping dan presisi):

1. LAYAR 1: Login & Area Context
   - Login RNIMS (Online / Offline Cached Pin).
   - Mengunci profil user ke Area 18 (MPO).

2. LAYAR 2: Segment Explorer (Penyaring 143 Segmen MPO)
   - Kartu "Segmen Terdekat" berbasis radius GPS real-time surveyor.
   - Filter Chip Kota (Timika, Sorong, Jayapura, Ambon).
   - Begitu segmen dipilih, garis rute polyline kabel optik otomatis termuat di peta.

3. LAYAR 3: Quick Tagging Canvas (< 1 Menit per Tiang)
   - Peta Lapangan: Garis kabel optik RNIMS (Biru) + Pin Tiang Eksisting (Biru Muda) + Pin Tiang Baru Terdata (Hijau).
   - Deteksi Tiang Eksisting Otomatis (Radius < 20 meter): Menampilkan pilihan pintar `[Gunakan Tiang Eksisting]` vs `[Buat Tiang Baru]`.
   - GPS Auto-Lock: Indikator Akurasi Satelit (Hijau <= 8m, Kuning 8-15m, Merah > 15m).
   - Penomoran Otomatis eNims: Rumus `PL-[KOTA]-[LOKASI]-[SEQ]` (misal: `PL-TMK-KKC-001`).
   - Kamera Cepat CameraX: Panduan siluet vertikal tiang (bukan AR), auto-compress JPEG < 400 KB, watermark GPS + Timestamp.
   - Tombol 1-Tap: "SIMPAN & LANJUT KE TIANG BERIKUTNYA".

4. LAYAR 4: Review, Export & Sync Server
   - Ringkasan tiang yang disurvei.
   - Tombol "Export KML As-Built" (Google Earth).
   - Tombol "Kirim ke Server" (Upload KML & Batch payload ke tabel `infra` dengan `infraState = 1`).

STRUKTUR DATABASE ROOM SQLITE LOKAL (HANYA 2 TABEL INTI):
- Entity `SegmentEntity`: id (SEG-MPO-001), name, lengthMeters, routeCoordinates, areaId.
- Entity `PoleEntity`: id (UUID), segmentId, sequence, poleCode, latitude, longitude, accuracy, photoPath, type, condition, ownership, syncStatus.
</DESIGN_ARCHITECTURE>

<STRICT_RULES>
Patuhi aturan arsitektur mutlak berikut tanpa kompromi:
1. RULE-01 (ZERO NEW DB MIGRATIONS): Dilarang membuat tabel baru atau migration baru di MariaDB server. Gunakan tabel `infra` eNims dan kolom `segment.rute` yang sudah ada.
2. RULE-02 (OFFLINE-FIRST RESILIENCE): Seluruh alur tagging tiang, peta offline, kamera, dan penyimpanan wajib 100% berjalan sempurna dalam Mode Pesawat (Airplane Mode).
3. RULE-03 (TAGGING VELOCITY < 1 MENIT): Hindari form isian panjang dan dialog berbelit-belit. Minimalisir pengetikan manual.
4. RULE-04 (NO PAID GOOGLE APIS / NO AR): Peta berbasis MapLibre/OSM lokal, kamera berbasis CameraX overlay panduan visual (Bukan Google ARCore), tidak ada Maps SDK berbayar.
5. RULE-05 (MEDIA INTEGRITY): Foto dilarang disimpan sebagai BLOB di database. Foto disimpan sebagai JPEG terkompresi lokal (< 400 KB) ber-watermark teks.
6. RULE-06 (DUAL STAGING SUPPORT): Koneksi jaringan mendukung Wi-Fi Lokal (`http://<ip-laptop>:8000`) dan USB (`http://127.0.0.1:8000` via `adb reverse tcp:8000 tcp:8000`).
</STRICT_RULES>

<EXECUTION_PLAN>
Laksanakan implementasi secara bertahap:
- FASE 1: Bersihkan model lama yang duplikat, buat 2 Room Entity inti (`SegmentEntity`, `PoleEntity`) dan inisialisasi seed 143 segmen MPO.
- FASE 2: Bangun Segment Explorer Screen dengan GPS Proximity Auto-Detect dan Filter Kota (Timika, Sorong, Jayapura).
- FASE 3: Bangun Quick Tagging Screen (< 1 Menit) lengkap dengan GPS Auto-lock, dialog deteksi tiang eksisting, dan CameraX portrait overlay.
- FASE 4: Bangun modul Export KML As-Built eNims dan client sinkronisasi dual staging (Wi-Fi/USB).
- FASE 5: Jalankan compile `./gradlew assembleDebug` dan verifikasi bahwa APK bebas dari error.
</EXECUTION_PLAN>

Silakan mulai dari FASE 1. Konfirmasikan bahwa Anda siap dan laporkan langkah awal yang akan Anda eksekusi!
```
