package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class TransactionItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = "EXPENSE", // "INCOME" or "EXPENSE"
    val amount: Double = 0.0,
    val category: String = "",
    val note: String? = null,
    val imageBase64: String? = null,
    val date: Timestamp = Timestamp.now(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
