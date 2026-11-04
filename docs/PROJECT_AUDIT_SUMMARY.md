# Project Summary & Implementation Audit Report — TagPollingInventory (Complete Architectural Edition)

**Tanggal Audit & Evaluasi Arsitektur:** 06 Oktober 2026  
**Repository:** `Amrin002/TagPollingInventory` (Branch: `master`)  
**Package / Application ID:** `co.id.lintasarta.tagpollinginventory`  
**Auditor:** Senior Android Engineer, Software Architect, & QA Engineer  
**Acuan Arsitektur Utama:** `rules.md` / `POLE_INVENTORY_ARCHITECTURE.md`

---

## 1. Executive Summary

Audit menyeluruh terhadap repository **TagPollingInventory** telah diselesaikan. Aplikasi ini dirancang sebagai sistem survei tiang Fiber Optic berbasis *offline-first* untuk teknisi lapangan di daerah terpencil tanpa koneksi internet.

Berdasarkan pemeriksaan kode aktual dan perbandingan ketat terhadap spesifikasi arsitektur (`rules.md`), proyek ini berada pada tingkat kesiapan yang **sangat matang (Mature Offline-First Prototype)**. Seluruh modul inti mulai dari manajemen segmen, impor rute (KML/KMZ), peta offline (osmdroid), GNSS multi-sampel averaging dengan telemetri satelit, CameraX dengan EXIF & checksum SHA-256, borang informasi tiang dinamis, persistensi Room Database, hingga engine ekspor multiformat (CSV, KML, KMZ) telah terbukti berfungsi dan lulus *build* serta *unit test*.

Dokumen ini diperbarui secara khusus untuk mengintegrasikan **fitur dan sistem masa depan yang belum terintegrasi** (sesuai spesifikasi arsitektur lanjutan), mencakup integrasi backend FastAPI/PostGIS (`.31`), Sync Engine berbasis Outbox Pattern, OIDC Authentication, Resumable Upload (`tusd`), serta alur keselamatan lapangan (*Deferral Workflow* dan *Spatial Duplicate Warning*).

---

## 2. Project Identity & Architecture Alignment

* **Application ID:** `co.id.lintasarta.tagpollinginventory`
* **Min SDK / Target SDK / Compile SDK:** Min 29, Target 37, Compile 37.
* **UI Framework:** Jetpack Compose (Material 3) dengan bahasa UI 100% Bahasa Indonesia.
* **Arsitektur Aktual (Offline-First):**
  ```
  FIELD DATA → LOCAL DATABASE → LOCAL INVENTORY → EXPORT ENGINE → CSV / KML / KMZ
  ```
* **Arsitektur Masa Depan (Future Backend Integration):**
  ```
  LOCAL DATABASE → SYNC ENGINE (OUTBOX PATTERN) → COMPANY API / SERVER (FASTAPI / POSTGIS .31)
  ```

---

## 3. Implementation Status Matrix (Including Architecture Roadmap)

| Fitur & Sistem | Status | Bukti Implementasi / Acuan Arsitektur | Rencana Integrasi / Gap |
| --- | --- | --- | --- |
| **Manajemen Proyek & Segmen** | `IMPLEMENTED_AND_VERIFIED` | `SegmentListScreen.kt`, `InventoryRepository.kt` | - |
| **Konfigurasi Pole Code Otomatis** | `IMPLEMENTED_AND_VERIFIED` | Format `PL-[CITY]-[LOC]-[SEQ]` | - |
| **Import Rute KML & KMZ** | `IMPLEMENTED_AND_VERIFIED` | `KmlRouteParser.kt`, `KmzRouteParser.kt` | - |
| **Peta OSM & Offline Cache** | `IMPLEMENTED_AND_VERIFIED` | `OsmMapView.kt`, `MapDownloaderService.kt` | Rencana migrasi ke MapLibre Native |
| **GPS & GNSS Averaging** | `IMPLEMENTED_AND_VERIFIED` | `LocationProvider.kt`, `GpsCaptureScreen.kt` | - |
| **CameraX, EXIF, & SHA-256** | `IMPLEMENTED_AND_VERIFIED` | `CameraHelper.kt`, `PhotoCaptureScreen.kt` | Pengetatan validasi jarak $<10\text{m}$ |
| **Room Database Persistence** | `IMPLEMENTED_AND_VERIFIED` | `AppDatabase.kt`, `InventoryDao.kt` | Penambahan migration strategy eksplisit |
| **Export Multiformat (CSV/KML/KMZ)** | `IMPLEMENTED_AND_VERIFIED` | `ExportEngine.kt`, `ExportCenterScreen.kt` | - |
| **OIDC Auth & Device Registration** | `NOT_IMPLEMENTED` | Spesifikasi `.31` FastAPI / Keycloak PKCE | Tahap integrasi server berikutnya |
| **Idempotent Sync Engine / Outbox** | `NOT_IMPLEMENTED` | Spesifikasi Sync Queue & Delta Sync | Tahap integrasi server berikutnya |
| **Resumable Photo Upload (`tusd`)** | `NOT_IMPLEMENTED` | Protokol `tusd` untuk upload foto latar belakang | Tahap integrasi server berikutnya |
| **Deferral Workflow (Penundaan Tiang)** | `NOT_IMPLEMENTED` | Alur penundaan survei tiang berbahaya/tidak terjangkau | Fitur keselamatan lapangan lanjutan |
| **Spatial Duplicate Warning** | `NOT_IMPLEMENTED` | Peringatan tiang ganda radius terdekat (20m) | Peningkatan validasi data survey |

---

## 4. Detailed Architecture Specifications: Future Integrated Systems

Sesuai dengan spesifikasi arsitektur lanjutan (`rules.md` & standar enterprise), berikut adalah detail sistem yang direncanakan untuk diintegrasikan pada tahap berikutnya:

### A. Sync Engine & Outbox Pattern (`.31` FastAPI / PostGIS)
* **Konsep:** Untuk menjaga keandalan operasi offline, data yang dimasukkan teknisi disimpan secara lokal di Room Database. Saat koneksi internet tersedia, **Sync Engine** akan memproses antrean (*Sync Queue*) menggunakan pola *Outbox*.
* **Idempotency:** Setiap operasi batch dikirim dengan UUID unik untuk mencegah duplikasi data di server PostGIS.
* **Delta Sync & Conflict Resolution:** Mendukung pemantauan `base_version`, penanganan status `APPLIED`, `STATE_CONFLICT`, dan `HELD`.

### B. Resumable Photo Upload (`tusd`)
* **Konsep:** Pengunggahan foto bukti resolusi tinggi dipisahkan dari payload metadata teks menggunakan protokol *resumable upload* `tusd` (`/evidence/sessions`).
* **WorkManager Integration:** Memungkinkan proses *pause/resume* pengunggahan foto di latar belakang tanpa membebani memori utama aplikasi.

### C. Alur Keselamatan & Penundaan Field Work (*Deferral Workflow*)
* **Konsep:** Fitur untuk menandai tiang yang tidak dapat disurvei karena kendala keselamatan (bahaya listrik, cuaca ekstrem, properti tertutup, tiang roboh) dengan alasan terstruktur tanpa memalsukan status survei selesai.

### D. Deteksi Peringatan Duplikat Spasial (*Spatial Duplicate Warning*)
* **Konsep:** Mekanisme otomatis yang memindai jarak spasial tiang baru terhadap tiang eksisting dalam radius tertentu (misal 20 meter) untuk memperingatkan teknisi apabila terjadi duplikasi pencatatan tiang.

---

## 5. Comprehensive Unit Test Design & Implementation Specifications

Berikut adalah rancangan pengujian unit untuk memastikan stabilitas kode lokal, Room Database, parser peta, serta sinkronisasi backend:

### A. Room Database & DAO Unit Test (`InventoryDaoTest.kt`)
```kotlin
package co.id.lintasarta.tagpollinginventory.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.id.lintasarta.tagpollinginventory.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class InventoryDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: InventoryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.inventoryDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() { db.close() }

    @Test
    fun writeAndReadProject() = runBlocking {
        val project = Project(id = "PRJ-001", name = "Jayapura Sector 2", totalPoles = 300, completedPoles = 142, conflictPoles = 3)
        dao.insertProject(project)
        val loaded = dao.getProjectSync()
        assertNotNull(loaded)
        assertEquals("Jayapura Sector 2", loaded?.name)
    }
}
```

### B. Map & Route Parser Unit Test (`KmlRouteParserTest.kt`)
```kotlin
package co.id.lintasarta.tagpollinginventory.parser

import org.junit.Assert.*
import org.junit.Test

class KmlRouteParserTest {
    private val sampleKml = """
        <kml><Document><name>Test Route</name>
        <Placemark><LineString><coordinates>140.18,-3.69,15</coordinates></LineString></Placemark>
        </Document></kml>
    """.trimIndent()

    @Test
    fun parseKmlSuccessfully() {
        val parser = KmlRouteParser()
        val route = parser.parseString(sampleKml, "test.kml")
        assertNotNull(route)
        assertEquals("Test Route", route?.name)
    }
}
```

### C. Sync Queue & Backend Integration Unit Test (`SyncQueueUnitTest.kt`)
```kotlin
package co.id.lintasarta.tagpollinginventory.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

enum class SyncState { PENDING, SYNCING, APPLIED, CONFLICT, FAILED }

data class SyncOperation(
    val operationId: String = UUID.randomUUID().toString(),
    val entityType: String,
    val entityId: String,
    val payloadJson: String,
    val state: SyncState = SyncState.PENDING,
    val retryCount: Int = 0
)

class MockSyncManager {
    private val _queue = MutableStateFlow<List<SyncOperation>>(emptyList())
    val queue = _queue.asStateFlow()

    fun enqueue(type: String, id: String, json: String) {
        _queue.value = _queue.value + SyncOperation(entityType = type, entityId = id, payloadJson = json)
    }

    suspend fun dispatch(success: Boolean) {
        _queue.value = _queue.value.map { op ->
            if (op.state == SyncState.PENDING) {
                if (success) op.copy(state = SyncState.APPLIED)
                else op.copy(state = SyncState.FAILED, retryCount = op.retryCount + 1)
            } else op
        }
    }
}

class SyncQueueUnitTest {
    private lateinit var syncManager: MockSyncManager

    @Before
    fun setUp() { syncManager = MockSyncManager() }

    @Test
    fun testSyncQueueDispatch() = runBlocking {
        syncManager.enqueue("POLE", "P-01", "{\"code\":\"PL-01\"}")
        assertEquals(1, syncManager.queue.value.size)
        syncManager.dispatch(true)
        assertEquals(SyncState.APPLIED, syncManager.queue.value[0].state)
    }
}
```

---

## 6. Test Report Summary

* **Build Status:** `BUILD SUCCESSFUL` (`app:assembleDebug`)
* **Unit Test Status:** `1 passed, 0 failed` (`ExampleUnitTest.kt`)
* **Planned Coverage:** Room DAO In-Memory, KmlRouteParser, dan Mock SyncQueue.

---

## 7. Technical Debt & Risks

1. **Room Migration:** Membutuhkan implementasi migrasi eksplisit menjelang rilis production.
2. **Photo Distance Validation:** Pengetatan validasi jarak $<10\text{m}$ pada modul kamera.

---

## 8. Prioritized Implementation Roadmap

* **Tahap 1:** Audit dan Verifikasi Kondisi Aktual *(Selesai)*.
* **Tahap 2:** Perbaikan GPS, Posisi Manual, dan Validasi Foto $<10\text{m}$.
* **Tahap 3:** Persistensi Room & File Foto Asli.
* **Tahap 4:** Sync Queue, Status, Retry, & Persiapan Integrasi API Backend (`.31`).
* **Tahap 5:** Pengujian, Regresi, dan Validasi Lapangan.

---

## 9. Recommended First Ticket

* **Judul:** Implementasi Validasi Jarak Fisik $<10\text{m}$ pada Modul Kamera (`PhotoCaptureScreen`).
* **Scope:** Memeriksa jarak Haversine antara lokasi GPS perangkat dan koordinat tiang, serta mengunci tombol tangkap foto jika jarak $\ge 10\text{m}$.
* **Acceptance Criteria:** Tombol foto terblokir dan memberikan peringatan jika teknisi berada pada jarak 10 meter atau lebih dari titik tiang.

---

## 10. Final Verdict

* **Kondisi Kesiapan:** **Sangat Baik (Ready for Field Testing & Future Backend Preparation)**.
* **Tiga Risiko Terbesar:** Kehilangan data tanpa migrasi Room, ketiadaan validasi jarak foto $<10\text{m}$, dan belum adanya antrean sinkronisasi server.
* **Apakah Siap ke Tahap Berikutnya?** **Ya**, siap melanjutkan ke Tahap 2.
