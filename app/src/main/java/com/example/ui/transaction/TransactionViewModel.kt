package com.example.ui.transaction

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.TransactionItem
import com.example.data.repository.TransactionRepository
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI State representation for asynchronous transaction operations.
 */
sealed interface TransactionUiState<out T> {
    data object Idle : TransactionUiState<Nothing>
    data object Loading : TransactionUiState<Nothing>
    data class Success<T>(val data: T) : TransactionUiState<T>
    data class Error(val message: String) : TransactionUiState<Nothing>
}

/**
 * Summary state containing transaction list and calculated totals for history display.
 */
data class TransactionHistoryUiState(
    val isLoading: Boolean = false,
    val transactions: List<TransactionItem> = emptyList(),
    val errorMessage: String? = null,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netBalance: Double = 0.0
)

/**
 * TransactionViewModel handles interacting with Cloud Firestore:
 * 1. Saving new transactions (with optional receipts, amount, type, category, timestamp)
 * 2. Updating and deleting existing transactions
 * 3. Fetching and observing transaction history in real-time or via one-shot queries
 */
class TransactionViewModel(
    private val repository: TransactionRepository,
    private val initialUserId: String? = null
) : ViewModel() {

    // Active user id: initialized with provided userId or current authenticated user
    private val _userId = MutableStateFlow<String>(
        initialUserId ?: Firebase.auth.currentUser?.uid ?: ""
    )
    val userId: StateFlow<String> = _userId.asStateFlow()

    // Saving transaction states
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSuccessEvent = MutableSharedFlow<String>() // Emits new transaction document ID
    val saveSuccessEvent: SharedFlow<String> = _saveSuccessEvent.asSharedFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Filter type: null = ALL, "EXPENSE", "INCOME"
    private val _filterType = MutableStateFlow<String?>(null)
    val filterType: StateFlow<String?> = _filterType.asStateFlow()

    // One-shot fetch state
    private val _oneShotHistory = MutableStateFlow<TransactionUiState<List<TransactionItem>>>(TransactionUiState.Idle)
    val oneShotHistory: StateFlow<TransactionUiState<List<TransactionItem>>> = _oneShotHistory.asStateFlow()

    /**
     * Real-time stream of user transactions from Firestore, ordered by date descending.
     */
    val transactionHistory: StateFlow<TransactionUiState<List<TransactionItem>>> = _userId
        .flatMapLatest { uid ->
            if (uid.isBlank()) {
                flowOf(TransactionUiState.Success(emptyList()))
            } else {
                repository.observeTransactions(uid)
                    .map<List<TransactionItem>, TransactionUiState<List<TransactionItem>>> { items ->
                        TransactionUiState.Success(items)
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w("TransactionVM", "Error observing transaction history", rootError)
                        emit(TransactionUiState.Error(rootError.message ?: "Failed to fetch transactions"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = TransactionUiState.Loading
        )

    /**
     * Aggregated history state combining the transaction stream with filter and totals calculation.
     */
    val historySummaryState: StateFlow<TransactionHistoryUiState> = combine(
        transactionHistory,
        _filterType
    ) { historyState, filter ->
        when (historyState) {
            is TransactionUiState.Loading -> TransactionHistoryUiState(isLoading = true)
            is TransactionUiState.Error -> TransactionHistoryUiState(
                isLoading = false,
                errorMessage = historyState.message
            )
            is TransactionUiState.Success -> {
                val allItems = historyState.data
                val filteredItems = if (filter == null) {
                    allItems
                } else {
                    allItems.filter { it.type.equals(filter, ignoreCase = true) }
                }
                val totalIncome = allItems
                    .filter { it.type.equals("INCOME", ignoreCase = true) }
                    .sumOf { it.amount }
                val totalExpense = allItems
                    .filter { it.type.equals("EXPENSE", ignoreCase = true) }
                    .sumOf { it.amount }

                TransactionHistoryUiState(
                    isLoading = false,
                    transactions = filteredItems,
                    totalIncome = totalIncome,
                    totalExpense = totalExpense,
                    netBalance = totalIncome - totalExpense
                )
            }
            TransactionUiState.Idle -> TransactionHistoryUiState()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = TransactionHistoryUiState(isLoading = true)
    )

    fun setUserId(uid: String) {
        _userId.value = uid
    }

    fun setFilter(type: String?) {
        _filterType.value = type
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    /**
     * Saves a transaction to Firestore (creates new if id is empty, otherwise updates).
     */
    fun saveTransaction(
        transaction: TransactionItem,
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val targetUserId = transaction.userId.ifBlank { _userId.value }
        if (targetUserId.isBlank()) {
            val err = "กรุณาเข้าสู่ระบบก่อนทำรายการ"
            _errorMessage.value = err
            onError(err)
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            _errorMessage.value = null
            try {
                val result = if (transaction.id.isNotBlank()) {
                    val updateResult = repository.updateTransaction(targetUserId, transaction)
                    if (updateResult.isSuccess) {
                        Result.success(transaction.id)
                    } else {
                        Result.failure(updateResult.exceptionOrNull() ?: Exception("Update failed"))
                    }
                } else {
                    repository.addTransaction(targetUserId, transaction.copy(userId = targetUserId))
                }

                if (result.isSuccess) {
                    val id = result.getOrThrow()
                    _statusMessage.value = "บันทึกข้อมูลเรียบร้อยแล้ว"
                    onSuccess(id)
                    _saveSuccessEvent.emit(id)
                } else {
                    val err = result.exceptionOrNull()?.localizedMessage ?: "บันทึกข้อมูลไม่สำเร็จ"
                    _errorMessage.value = err
                    onError(err)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "เกิดข้อผิดพลาดในการเชื่อมต่อ Firestore"
                _errorMessage.value = err
                onError(err)
            } finally {
                _isSaving.value = false
            }
        }
    }

    /**
     * Saves a new transaction with raw arguments.
     */
    fun saveNewTransaction(
        type: String,
        amount: Double,
        category: String,
        note: String? = null,
        imageBase64: String? = null,
        date: Timestamp = Timestamp.now(),
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val item = TransactionItem(
            userId = _userId.value,
            type = type,
            amount = amount,
            category = category,
            note = note?.takeIf { it.isNotBlank() },
            imageBase64 = imageBase64,
            date = date
        )
        saveTransaction(item, onSuccess, onError)
    }

    /**
     * Fetches transaction history once from Firestore (one-shot query).
     */
    fun fetchTransactionHistory(
        onResult: (List<TransactionItem>) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val uid = _userId.value
        if (uid.isBlank()) {
            val err = "User is not authenticated"
            _oneShotHistory.value = TransactionUiState.Error(err)
            onError(err)
            return
        }

        viewModelScope.launch {
            _oneShotHistory.value = TransactionUiState.Loading
            val result = repository.fetchTransactionHistory(uid)
            if (result.isSuccess) {
                val data = result.getOrDefault(emptyList())
                _oneShotHistory.value = TransactionUiState.Success(data)
                onResult(data)
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Failed to fetch transactions"
                _oneShotHistory.value = TransactionUiState.Error(err)
                _errorMessage.value = err
                onError(err)
            }
        }
    }

    /**
     * Deletes a transaction from Firestore.
     */
    fun deleteTransaction(
        transactionId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val uid = _userId.value
        if (uid.isBlank() || transactionId.isBlank()) return
        viewModelScope.launch {
            val result = repository.deleteTransaction(uid, transactionId)
            if (result.isSuccess) {
                _statusMessage.value = "ลบรายการเรียบร้อยแล้ว"
                onSuccess()
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Failed to delete transaction"
                _errorMessage.value = err
                onError(err)
            }
        }
    }

    companion object {
        /**
         * Creates a ViewModelFactory using the injected Application context to instantiate
         * FirebaseFirestore with the configured named database ID.
         */
        fun provideFactory(
            application: Application,
            userId: String? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val dbId = application.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(dbId)
                val repo = TransactionRepository(firestore)
                TransactionViewModel(repo, userId)
            }
        }

        fun provideFactory(
            repository: TransactionRepository,
            userId: String? = null
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TransactionViewModel(repository, userId)
            }
        }
    }
}
