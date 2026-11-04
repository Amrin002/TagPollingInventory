# 🛡️ RULES.md: Pedoman Ketat Arsitektur & Aturan Domain TagPollingInventory

Dokumen ini memuat **Batasan Arsitektur, Aturan Domain Telekomunikasi, dan Standar Koding** yang **WAJIB DIPATUHI** oleh AI Agent maupun pengembang manusia selama mengerjakan proyek **TagPollingInventory**.

---

## 1. Aturan Mutlak Arsitektur (Strict Architectural Constraints)

| Kode | Aturan | Deskripsi & Larangan Keras |
|---|---|---|
| **RULE-01** | **ZERO NEW DB MIGRATIONS** | **DILARANG KERAS** membuat file migrasi baru, mengeksekusi perintah `CREATE TABLE`, atau melakukan `ALTER TABLE` pada database MariaDB server (`RegionalOperation`, `cias`, atau `eNims`). Seluruh integrasi harus memanfaatkan skema tabel eksisting. |
| **RULE-02** | **ZERO UNAPPROVED BACKEND ROUTERS** | **DILARANG** membuat router kustom liar di backend (seperti `mobile_survey.py`). Wajib memanfaatkan endpoint bawaan RNIMS FastAPI (`/api/auth/login`, `/api/msfo/ta/routes`) dan modul upload dokumen bawaan eNims (`ProjectManagement.php`). |
| **RULE-03** | **100% OFFLINE-FIRST OPERATION** | Seluruh alur kerja inti surveyor (pencarian segmen, visualisasi rute, penguncian GPS, penomoran tiang, pengambilan foto, dan penyimpanan data) **WAJIB TETAP BERFUNGSI SEMPURNA DALAM MODE PESAWAT (AIRPLANE MODE)**. Ketiadaan sinyal seluler di hutan/pedalaman tidak boleh menyebabkan crash atau memblokir kerja surveyor. |
| **RULE-04** | **TARGET WAKTU: < 1 MENIT PER TIANG** | Antarmuka pengguna (UI) harus dioptimasi untuk kecepatan tinggi di bawah terik matahari. Tidak boleh ada dialog konfirmasi berantai, form yang rumit, atau langkah pengetikan teks manual yang memperlambat surveyor di lapangan. |
| **RULE-05** | **HYBRID SYNC: OPSI 1 + OPSI 3** | Pengiriman data kembali ke server menggunakan pendekatan ganda: **Opsi 3 (Unggah KML As-Built)** sebagai dokumen serah terima dan cadangan arsip, serta **Opsi 1 (Tabel `infra` eNims)** sebagai pencatatan aset inventaris OSP resmi. |
| **RULE-06** | **DUAL STAGING CONNECTIVITY** | Aplikasi harus mendukung dua metode pengujian lokal ke laptop developer secara mulus: melalui **Wi-Fi Lokal** (`http://<IP-Laptop>:8000`) dan melalui **Kabel USB ADB** (`http://127.0.0.1:8000` via `adb reverse tcp:8000 tcp:8000`), sebelum beralih ke server produksi live. |
| **RULE-07** | **STANDARISASI ALPACA PRA** | Seluruh klasifikasi tiang wajib menggunakan model **Alpaca PRA (Poles Rental Agreement)**: `pole_7` (T7, 7m) dan `pole_9` (T9, 9m). Format export wajib kompatibel dengan parser `Alpaca_kmz.py` (`Alpaca_Master_Poles.csv` dan `Alpaca_Master.kmz`). |
| **RULE-08** | **MANDATORY GIT DISCIPLINE & PUSH** | Setiap fase rekonstruksi wajib diuji (`./gradlew assembleDebug`), dilakukan git commit dengan pesan terstruktur (*conventional commits*), dan di-push ke remote `origin/master`. |

---

## 2. Aturan Domain & Penamaan Aset (Lintasarta & Alpaca PRA Standards)

### A. Rumus Penamaan Kode Tiang Otomatis
Penamaan tiang otomatis mematuhi format baku Alpaca PRA / eNims:
$$\text{POL}-\text{MPO}-\text{[KODE\_KOTA]}-\text{[NOMOR\_URUT]}$$

* **Komponen:**
  - `POL`: Kode aset standar tiang (*Pole*) pada sistem Alpaca PRA.
  - `MPO`: Regional Area 18 (Maluku Papua Operation).
  - `[KODE_KOTA]`: Singkatan 3 huruf nama kota segmen (contoh: `TMK` untuk Timika, `SRG` untuk Sorong, `JAP` untuk Jayapura, `MNK` untuk Manokwari, `MRK` untuk Merauke, `DBO` untuk Dobo, `TKR` untuk Tiakur).
  - `[NOMOR_URUT]`: Nomor urut 4 digit berurutan: `0001`, `0002`, `0003`, dst. (auto-increment berdasarkan tiang terakhir di segmen tersebut).
* **Contoh Hasil Valid:** `POL-MPO-TMK-0001`, `POL-MPO-MNK-0024`.

### B. Aturan Penyimpanan Media Foto
- **DILARANG** menyimpan foto tiang ke dalam database SQL dalam bentuk BLOB atau base64.
- Foto tiang disimpan di memori internal perangkat Android dalam format JPEG:
  - Resolusi maksimal: 1920x1080 piksel.
  - Kompresi kualitas: 80% (target ukuran: 250 KB – 450 KB per foto).
  - Wajib dibubuhi watermark teks pada sudut kanan bawah: `[KODE TIANG] | [LAT, LNG] | [TANGGAL & WAKTU]`.

### C. Pembatasan Wilayah Kerja (Area Scoping)
- Setiap surveyor memiliki penugasan area operasional (`areaId`).
- Aplikasi wajib menyaring daftar segmen yang tampil di layar hanya untuk area milik pengguna yang sedang login.
- **Prioritas Wilayah Pilot:** Area ID `18` mewakili **MPO (Maluku Papua Operation)** dengan total 143 segmen kabel fiber optik.

---

## 3. Pedoman Teknis & Koding Android (Jetpack Compose & Room)

1. **Pola Arsitektur:** Wajib mengikuti pola **MVVM (Model-View-ViewModel)** dengan *Unidirectional Data Flow (UDF)*:
   - `Room Database / Repository` &rarr; `StateFlow / SharedFlow` &rarr; `Compose Screen`.
2. **Penanganan Error Bersahabat (Graceful Degradation):**
   - Saat sinkronisasi gagal karena sinyal putus, aplikasi tidak boleh crash; data tetap tersimpan aman di antrean lokal Room dan diberi tanda status `NOT_SYNCED`.
   - Gunakan `WorkManager` untuk antrean unggah background yang otomatis mencoba kembali saat perangkat mendeteksi koneksi stabil.
3. **Konfigurasi Jaringan Dinamis:**
   - Dilarang keras meng-hardcode URL server backend di dalam kelas API/Repository.
   - Base URL server harus dibaca dari `SharedPreferences` / `Settings` yang dapat diganti oleh surveyor melalui menu Pengaturan.
4. **Validasi Build Mandatori:**
   - Setiap perubahan kode harus melewati kompilasi `./gradlew compileDebugKotlin` dan `./gradlew testDebugUnitTest` tanpa warning fatal atau error.
