package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionItem
import com.example.data.model.UserProfile
import com.example.ui.add.AddTransactionDialog
import com.example.ui.detail.TransactionDetailDialog
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.google.firebase.Timestamp
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: UiState<List<TransactionItem>>,
    userProfile: UserProfile?,
    selectedFilter: TransactionFilter,
    actionMessage: String?,
    onFilterChange: (TransactionFilter) -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (TransactionItem) -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onClearActionMessage: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTransactionForDetail by remember { mutableStateOf<TransactionItem?>(null) }

    LaunchedEffect(actionMessage) {
        if (actionMessage != null) {
            snackbarHostState.showSnackbar(actionMessage)
            onClearActionMessage()
        }
    }

    val currencyFormatter = remember {
        NumberFormat.getNumberInstance(Locale("th", "TH")).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(60.dp)
                    .testTag("add_transaction_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Transaction",
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "เกิดข้อผิดพลาดในการโหลดข้อมูล: ${uiState.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is UiState.Success -> {
                    val allTransactions = uiState.data

                    // Calculate totals
                    val totalIncome = remember(allTransactions) {
                        allTransactions.filter { it.type == "INCOME" }.sumOf { it.amount }
                    }
                    val totalExpense = remember(allTransactions) {
                        allTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                    }
                    val netBalance = totalIncome - totalExpense

                    var activeCategoryFilter by remember { mutableStateOf<String?>(null) }

                    // Filtered transactions
                    val filteredList = remember(allTransactions, selectedFilter, activeCategoryFilter) {
                        val baseList = when (selectedFilter) {
                            TransactionFilter.ALL -> allTransactions
                            TransactionFilter.EXPENSE -> allTransactions.filter { it.type == "EXPENSE" }
                            TransactionFilter.INCOME -> allTransactions.filter { it.type == "INCOME" }
                        }
                        if (activeCategoryFilter != null) {
                            baseList.filter { it.category == activeCategoryFilter }
                        } else {
                            baseList
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Hero Balance Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.secondary
                                                )
                                            )
                                        )
                                        .padding(22.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "ยอดคงเหลือรวม",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = (if (netBalance >= 0) "฿" else "-฿") + currencyFormatter.format(kotlin.math.abs(netBalance)),
                                            style = MaterialTheme.typography.headlineLarge.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 34.sp
                                            ),
                                            color = Color.White
                                        )

                                        Spacer(modifier = Modifier.height(18.dp))

                                        // Income and Expense pills
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            // Income Pill
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color.White.copy(alpha = 0.2f))
                                                    .padding(vertical = 10.dp, horizontal = 12.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.White.copy(alpha = 0.3f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.ArrowUpward,
                                                            contentDescription = "Income",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = "รายรับ",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                            color = Color.White.copy(alpha = 0.85f)
                                                        )
                                                        Text(
                                                            text = "฿${currencyFormatter.format(totalIncome)}",
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = Color.White,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }

                                            // Expense Pill
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color.White.copy(alpha = 0.2f))
                                                    .padding(vertical = 10.dp, horizontal = 12.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.White.copy(alpha = 0.3f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.ArrowDownward,
                                                            contentDescription = "Expense",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(
                                                            text = "รายจ่าย",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                            color = Color.White.copy(alpha = 0.85f)
                                                        )
                                                        Text(
                                                            text = "฿${currencyFormatter.format(totalExpense)}",
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = Color.White,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Spending Pie Chart Card (Interactive Donut Chart with Category Legend)
                        item {
                            SpendingPieChartCard(
                                transactions = allTransactions,
                                onCategoryFilterSelected = { cat ->
                                    activeCategoryFilter = cat
                                }
                            )
                        }

                        // Filter Chips
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = selectedFilter == TransactionFilter.ALL && activeCategoryFilter == null,
                                    onClick = {
                                        activeCategoryFilter = null
                                        onFilterChange(TransactionFilter.ALL)
                                    },
                                    label = { Text("ทั้งหมด (${allTransactions.size})") },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )

                                FilterChip(
                                    selected = selectedFilter == TransactionFilter.EXPENSE && activeCategoryFilter == null,
                                    onClick = {
                                        activeCategoryFilter = null
                                        onFilterChange(TransactionFilter.EXPENSE)
                                    },
                                    label = { Text("รายจ่าย") },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ExpenseRed.copy(alpha = 0.15f),
                                        selectedLabelColor = ExpenseRed
                                    )
                                )

                                FilterChip(
                                    selected = selectedFilter == TransactionFilter.INCOME && activeCategoryFilter == null,
                                    onClick = {
                                        activeCategoryFilter = null
                                        onFilterChange(TransactionFilter.INCOME)
                                    },
                                    label = { Text("รายรับ") },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = IncomeGreen.copy(alpha = 0.15f),
                                        selectedLabelColor = IncomeGreen
                                    )
                                )

                                if (activeCategoryFilter != null) {
                                    FilterChip(
                                        selected = true,
                                        onClick = { activeCategoryFilter = null },
                                        label = { Text("หมวด: $activeCategoryFilter ✕") },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    )
                                }
                            }
                        }

                        // Transaction List Items or Empty State
                        if (filteredList.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 24.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ReceiptLong,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Text(
                                            text = "ยังไม่มีรายการในหมวดนี้",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = "กดปุ่ม + ด้านล่างเพื่อเพิ่มรายรับหรือรายจ่าย\nและสามารถแนบรูปสลิปหรือใบเสร็จได้ทันที",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(filteredList, key = { it.id }) { tx ->
                                TransactionCard(
                                    transaction = tx,
                                    onClick = { selectedTransactionForDetail = tx }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Dialog
    if (selectedTransactionForDetail != null) {
        TransactionDetailDialog(
            transaction = selectedTransactionForDetail!!,
            onDismiss = { selectedTransactionForDetail = null },
            onEdit = {
                val tx = selectedTransactionForDetail!!
                selectedTransactionForDetail = null
                onNavigateToEdit(tx)
            },
            onDelete = {
                val txId = selectedTransactionForDetail!!.id
                selectedTransactionForDetail = null
                onDeleteTransaction(txId)
            }
        )
    }
}
