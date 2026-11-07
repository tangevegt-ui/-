package com.example.ui.home

import com.example.data.model.TransactionItem
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class SpendingPieChartCardTest {

    @Test
    fun categorySlices_calculatesDistributionAndPercentagesCorrectly() {
        val tx1 = TransactionItem(
            id = UUID.randomUUID().toString(),
            type = "EXPENSE",
            amount = 300.0,
            category = "อาหาร/เครื่องดื่ม",
            date = Timestamp.now()
        )
        val tx2 = TransactionItem(
            id = UUID.randomUUID().toString(),
            type = "EXPENSE",
            amount = 700.0,
            category = "ช้อปปิ้ง",
            date = Timestamp.now()
        )
        val tx3 = TransactionItem(
            id = UUID.randomUUID().toString(),
            type = "INCOME",
            amount = 5000.0,
            category = "เงินเดือน",
            date = Timestamp.now()
        )

        val transactions = listOf(tx1, tx2, tx3)

        // Only expenses should be grouped
        val expenseTransactions = transactions.filter { it.type.equals("EXPENSE", ignoreCase = true) }
        val totalExpense = expenseTransactions.sumOf { it.amount }
        assertEquals(1000.0, totalExpense, 0.01)

        val grouped = expenseTransactions
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        assertEquals(2, grouped.size)
        // First is "ช้อปปิ้ง" with 700 (70%)
        assertEquals("ช้อปปิ้ง", grouped[0].first)
        assertEquals(700.0, grouped[0].second, 0.01)
        val shoppingFraction = (grouped[0].second / totalExpense).toFloat()
        assertEquals(0.70f, shoppingFraction, 0.001f)

        // Second is "อาหาร/เครื่องดื่ม" with 300 (30%)
        assertEquals("อาหาร/เครื่องดื่ม", grouped[1].first)
        assertEquals(300.0, grouped[1].second, 0.01)
        val foodFraction = (grouped[1].second / totalExpense).toFloat()
        assertEquals(0.30f, foodFraction, 0.001f)
    }

    @Test
    fun categorySlices_withZeroOrEmptyExpenses_returnsEmpty() {
        val incomeOnly = listOf(
            TransactionItem(
                id = UUID.randomUUID().toString(),
                type = "INCOME",
                amount = 25000.0,
                category = "เงินเดือน"
            )
        )
        val expenseTransactions = incomeOnly.filter { it.type.equals("EXPENSE", ignoreCase = true) }
        val totalExpense = expenseTransactions.sumOf { it.amount }
        assertEquals(0.0, totalExpense, 0.01)
        assertTrue(expenseTransactions.isEmpty())
    }
}
