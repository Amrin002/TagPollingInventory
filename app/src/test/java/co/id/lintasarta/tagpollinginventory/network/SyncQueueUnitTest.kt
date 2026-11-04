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

    fun dispatch(success: Boolean) {
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
    fun setUp() {
        syncManager = MockSyncManager()
    }

    @Test
    fun testSyncQueue_enqueueAndSuccessfulDispatch() = runBlocking {
        syncManager.enqueue("POLE", "POLE-001", "{\"code\":\"PL-JPR-CTR-001\"}")
        assertEquals(1, syncManager.queue.value.size)
        assertEquals(SyncState.PENDING, syncManager.queue.value[0].state)

        syncManager.dispatch(success = true)
        assertEquals(SyncState.APPLIED, syncManager.queue.value[0].state)
        assertEquals(0, syncManager.queue.value[0].retryCount)
    }

    @Test
    fun testSyncQueue_failedDispatchIncrementsRetryCount() = runBlocking {
        syncManager.enqueue("POLE", "POLE-002", "{\"code\":\"PL-JPR-CTR-002\"}")

        syncManager.dispatch(success = false)
        assertEquals(SyncState.FAILED, syncManager.queue.value[0].state)
        assertEquals(1, syncManager.queue.value[0].retryCount)
    }
}
