package com.example.ui.transaction

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.TransactionItem
import com.example.data.repository.TransactionRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionViewModelTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveNewTransaction_validInput_savesToFirestoreAndUpdatesState() = runBlocking {
        val uid = signInTestUser("user_tx_${UUID.randomUUID()}@example.com")
        val repo = TransactionRepository(firestore)
        val viewModel = TransactionViewModel(repo, uid)

        var savedDocId: String? = null
        viewModel.saveNewTransaction(
            type = "EXPENSE",
            amount = 250.0,
            category = "อาหาร",
            note = "มื้อเที่ยง",
            date = Timestamp.now(),
            onSuccess = { id -> savedDocId = id }
        )

        // Wait for save operation to finish
        val savedId = withTimeout(5000L) {
            viewModel.saveSuccessEvent.first()
        }
        assertNotNull(savedId)
        assertTrue(savedId.isNotEmpty())
        assertEquals(savedId, savedDocId)
        assertFalse(viewModel.isSaving.value)

        // Verify history flow receives the newly saved item
        val historyState = withTimeout(5000L) {
            viewModel.transactionHistory.first { it is TransactionUiState.Success && it.data.any { item -> item.id == savedId } }
        }
        assertTrue(historyState is TransactionUiState.Success)
        val items = (historyState as TransactionUiState.Success).data
        val found = items.firstOrNull { it.id == savedId }
        assertNotNull(found)
        assertEquals("EXPENSE", found?.type)
        assertEquals(250.0, found?.amount ?: 0.0, 0.01)
        assertEquals("อาหาร", found?.category)
    }

    @Test
    fun fetchTransactionHistory_oneShot_returnsSavedTransactions() = runBlocking {
        val uid = signInTestUser("user_hist_${UUID.randomUUID()}@example.com")
        val repo = TransactionRepository(firestore)
        val viewModel = TransactionViewModel(repo, uid)

        val item1 = TransactionItem(
            id = UUID.randomUUID().toString(),
            userId = uid,
            type = "INCOME",
            amount = 15000.0,
            category = "เงินเดือน",
            date = Timestamp.now()
        )
        val item2 = TransactionItem(
            id = UUID.randomUUID().toString(),
            userId = uid,
            type = "EXPENSE",
            amount = 1200.0,
            category = "ช้อปปิ้ง",
            date = Timestamp.now()
        )

        repo.addTransaction(uid, item1)
        repo.addTransaction(uid, item2)

        var fetchedList: List<TransactionItem>? = null
        viewModel.fetchTransactionHistory(
            onResult = { list -> fetchedList = list }
        )

        val oneShotResult = withTimeout(5000L) {
            viewModel.oneShotHistory.first { it is TransactionUiState.Success && it.data.size >= 2 }
        }
        assertTrue(oneShotResult is TransactionUiState.Success)
        val data = (oneShotResult as TransactionUiState.Success).data
        assertTrue(data.any { it.id == item1.id })
        assertTrue(data.any { it.id == item2.id })
    }

    @Test
    fun historySummaryState_calculatesTotalsCorrectly() = runBlocking {
        val uid = signInTestUser("user_sum_${UUID.randomUUID()}@example.com")
        val repo = TransactionRepository(firestore)
        val viewModel = TransactionViewModel(repo, uid)

        viewModel.saveNewTransaction(
            type = "INCOME",
            amount = 10000.0,
            category = "โบนัส"
        )
        withTimeout(5000L) { viewModel.saveSuccessEvent.first() }

        viewModel.saveNewTransaction(
            type = "EXPENSE",
            amount = 3000.0,
            category = "บิลค่าใช้จ่าย"
        )
        withTimeout(5000L) { viewModel.saveSuccessEvent.first() }

        val summary = withTimeout(5000L) {
            viewModel.historySummaryState.first {
                !it.isLoading && it.transactions.size >= 2
            }
        }
        assertEquals(10000.0, summary.totalIncome, 0.01)
        assertEquals(3000.0, summary.totalExpense, 0.01)
        assertEquals(7000.0, summary.netBalance, 0.01)
    }
}
