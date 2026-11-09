# Riwayat Pengembangan, Integrasi, & Pengujian (TagPolling Inventory)

Dokumen ini mencatat riwayat perubahan kode, arsitektur integrasi, dan pengujian fisik sinkronisasi dua arah antara aplikasi Android **TagPolling Inventory** dengan backend lokal **RNIMS / eNims** dan **Redis**.

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
  - `admin@bewithdhanu.in` ➔ **System Administrator** (Badge Ungu: *Super Admin*)
  - `manager@bewithdhanu.in` ➔ **Manager Operasional** (Badge Biru: *Operations Management*)
  - `surveyor.mpo@lintasarta.co.id` ➔ **Field Engineer MPO** (Badge Hijau: *Field Operations*)
  - `amrin@lintasarta.co.id` ➔ **Field Engineer MPO** (Badge Hijau: *Field Operations*)

### C. Penyesuaian Endpoint Sinkronisasi Tiang (`RnimsApiClient.kt` & `MainViewModel.kt`)
- **Akar Masalah Sebelumnya**:
  Aplikasi sebelumnya mengarah ke `POST /api/otdr/asset-assurance/sync`. Di backend lokal `test-api`, rute tersebut tidak ada dan diteruskan (*relay*) oleh `routes/relay.py` ke IP Data Center internal `123.231.138.31` yang unreachable di luar intranet Lintasarta, menyebabkan timeout 20 detik (HTTP 500 / HTTP 0).
- **Solusi Sisi Client**:
  Mengalihkan target sinkronisasi batch tiang ke endpoint realtime data storage backend yang sudah tersedia:
  👉 **`POST /api/redis/data`** dengan key path: `survey:poles:area:<areaId>`.
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

### ✅ Aliran 1: Backend ➔ Aplikasi Mobile (Tarik Segmen)
- **Endpoint**: `GET http://127.0.0.1:8000/api/msfo/ta/routes?area_id=18`
- **Hasil**: Server merespons `HTTP 200 OK`.
- **Data Tersimpan**: Sebanyak **143 Segmen resmi Area 18 (MPO)** berhasil disinkronkan dan tersimpan ke database SQLite Room lokal pada aplikasi, siap digunakan secara offline di menu Peta & Segmen.

### ✅ Aliran 2: Aplikasi Mobile ➔ Backend (Push Data Tiang Survei)
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
          "timestamp": "09 Nov 2026 — 10:02",
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
          "timestamp": "04 Nov 2026 — 09:11",
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
   📁 `D:\PROJECT RNIMS\TagPollingInventory_Archive\`
2. **Proteksi File Arsitektur**:
   File `POLE_INVENTORY_ARCHITECTURE.md` dipertahankan di root lokal dan dijaga dalam `.gitignore` sehingga tidak pernah ikut ter-commit ke git.
3. **Commit & Push Git**:
   - **Commit SHA**: `448d022`
   - **Pesan Commit**: `refactor: clean up non-android files to archive, add email validation & dynamic roles, and configure pole sync to local redis endpoint`
   - **Remote Branch**: `https://github.com/Amrin002/TagPollingInventory.git` (`origin/master`)
   - **Status Working Tree**: `working tree clean`
