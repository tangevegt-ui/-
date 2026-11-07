package com.example.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.TransactionItem
import com.example.data.model.UserProfile
import com.example.data.repository.TransactionRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class TransactionFilter {
    ALL,
    EXPENSE,
    INCOME
}

class HomeViewModel(
    private val repository: TransactionRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(TransactionFilter.ALL)
    val selectedFilter: StateFlow<TransactionFilter> = _selectedFilter.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    // 0 = current month, -1 = previous, etc.
    private val _monthOffset = MutableStateFlow(0)
    val monthOffset: StateFlow<Int> = _monthOffset.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val rawTransactions: StateFlow<UiState<List<TransactionItem>>> = repository
        .observeTransactions(currentUserId)
        .map<List<TransactionItem>, UiState<List<TransactionItem>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w("HomeVM", "Error observing transactions", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to observe transactions"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val userProfile: StateFlow<UserProfile?> = repository
        .observeUserProfile(currentUserId)
        .catch { error ->
            Log.w("HomeVM", "Error observing user profile", error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    fun setFilter(filter: TransactionFilter) {
        _selectedFilter.value = filter
    }

    fun setCategoryFilter(category: String?) {
        _selectedCategory.value = category
    }

    fun setMonthOffset(offset: Int) {
        _monthOffset.value = offset
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun addTransaction(
        type: String,
        amount: Double,
        category: String,
        note: String?,
        imageBase64: String?,
        date: Timestamp = Timestamp.now()
    ) {
        viewModelScope.launch {
            val item = TransactionItem(
                userId = currentUserId,
                type = type,
                amount = amount,
                category = category,
                note = note?.takeIf { it.isNotBlank() },
                imageBase64 = imageBase64,
                date = date
            )
            val result = repository.addTransaction(currentUserId, item)
            if (result.isSuccess) {
                _actionMessage.value = "บันทึกรายการสำเร็จแล้ว"
            } else {
                _actionMessage.value = "เกิดข้อผิดพลาด: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun updateTransaction(
        id: String,
        type: String,
        amount: Double,
        category: String,
        note: String?,
        imageBase64: String?,
        date: Timestamp
    ) {
        viewModelScope.launch {
            val item = TransactionItem(
                id = id,
                userId = currentUserId,
                type = type,
                amount = amount,
                category = category,
                note = note?.takeIf { it.isNotBlank() },
                imageBase64 = imageBase64,
                date = date
            )
            val result = repository.updateTransaction(currentUserId, item)
            if (result.isSuccess) {
                _actionMessage.value = "อัปเดตรายการเรียบร้อยแล้ว"
            } else {
                _actionMessage.value = "อัปเดตไม่สำเร็จ: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            val result = repository.deleteTransaction(currentUserId, transactionId)
            if (result.isSuccess) {
                _actionMessage.value = "ลบรายการเรียบร้อยแล้ว"
            } else {
                _actionMessage.value = "ลบไม่สำเร็จ: ${result.exceptionOrNull()?.localizedMessage}"
            }
        }
    }

    fun updateMonthlyBudget(budget: Double) {
        viewModelScope.launch {
            val result = repository.saveUserProfile(currentUserId, null, budget)
            if (result.isSuccess) {
                _actionMessage.value = "ตั้งค่างบประมาณสำเร็จ"
            }
        }
    }
}
