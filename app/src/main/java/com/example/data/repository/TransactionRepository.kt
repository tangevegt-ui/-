package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.TransactionItem
import com.example.data.model.UserProfile
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TransactionRepository(
    private val firestore: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeTransactions(userId: String): Flow<List<TransactionItem>> {
        val path = "users/$userId/transactions"
        return firestore.collection("users")
            .document(userId)
            .collection("transactions")
            .orderBy("date", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(TransactionItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
    }

    suspend fun fetchTransactionHistory(userId: String): Result<List<TransactionItem>> {
        val path = "users/$userId/transactions"
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("transactions")
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()
            val list = snapshot.toObjects(
                TransactionItem::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        return firestore.collection("users")
            .document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
    }

    suspend fun addTransaction(userId: String, transaction: TransactionItem): Result<String> {
        val docId = if (transaction.id.isNotBlank()) transaction.id else UUID.randomUUID().toString()
        val docRef = firestore.collection("users")
            .document(userId)
            .collection("transactions")
            .document(docId)

        val payload = mutableMapOf<String, Any>(
            "userId" to userId,
            "type" to transaction.type,
            "amount" to transaction.amount,
            "category" to transaction.category,
            "date" to transaction.date,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (!transaction.note.isNullOrBlank()) {
            payload["note"] = transaction.note
        }
        if (!transaction.imageBase64.isNullOrBlank()) {
            payload["imageBase64"] = transaction.imageBase64
        }

        return try {
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateTransaction(userId: String, transaction: TransactionItem): Result<Unit> {
        require(transaction.id.isNotBlank()) { "Transaction ID required for update" }
        val docRef = firestore.collection("users")
            .document(userId)
            .collection("transactions")
            .document(transaction.id)

        val payload = mutableMapOf<String, Any>(
            "type" to transaction.type,
            "amount" to transaction.amount,
            "category" to transaction.category,
            "date" to transaction.date,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!transaction.note.isNullOrBlank()) {
            payload["note"] = transaction.note
        }
        if (!transaction.imageBase64.isNullOrBlank()) {
            payload["imageBase64"] = transaction.imageBase64
        }

        return try {
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(userId: String, transactionId: String): Result<Unit> {
        require(transactionId.isNotBlank()) { "Transaction ID required for delete" }
        val docRef = firestore.collection("users")
            .document(userId)
            .collection("transactions")
            .document(transactionId)

        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveUserProfile(userId: String, displayName: String?, monthlyBudget: Double?): Result<Unit> {
        val docRef = firestore.collection("users").document(userId)
        val payload = mutableMapOf<String, Any>(
            "userId" to userId,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (displayName != null) payload["displayName"] = displayName
        if (monthlyBudget != null) payload["monthlyBudget"] = monthlyBudget

        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }
}
