package com.example.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import android.app.Application
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.model.TransactionItem
import com.example.data.repository.TransactionRepository
import com.example.ui.add.AddTransactionScreen
import com.example.ui.chat.ChatScreen
import com.example.ui.detail.TransactionDetailDialog
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.home.UiState
import com.example.ui.profile.ProfileScreen
import com.example.ui.summary.SummaryScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(
    currentUserId: String
) {
    val context = LocalContext.current
    val credentialManager = remember { CredentialManager.create(context) }

    val homeViewModel: HomeViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val dbId = app.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(dbId)
                HomeViewModel(TransactionRepository(firestore), currentUserId)
            }
        }
    )

    var currentTab by remember { mutableIntStateOf(0) } // 0: รายการ, 1: สรุปผล, 2: แชท AI, 3: โปรไฟล์
    var selectedTxForSummaryDetail by remember { mutableStateOf<TransactionItem?>(null) }
    var editingTransactionItem by remember { mutableStateOf<TransactionItem?>(null) }
    var isAddingOrEditing by remember { mutableStateOf(false) }

    val uiState by homeViewModel.rawTransactions.collectAsStateWithLifecycle()
    val userProfile by homeViewModel.userProfile.collectAsStateWithLifecycle()
    val selectedFilter by homeViewModel.selectedFilter.collectAsStateWithLifecycle()
    val actionMessage by homeViewModel.actionMessage.collectAsStateWithLifecycle()

    if (isAddingOrEditing) {
        AddTransactionScreen(
            initialTransaction = editingTransactionItem,
            onBack = {
                isAddingOrEditing = false
                editingTransactionItem = null
            },
            onSaveTransaction = { type, amount, category, note, imageBase64, date ->
                if (editingTransactionItem != null) {
                    homeViewModel.updateTransaction(
                        id = editingTransactionItem!!.id,
                        type = type,
                        amount = amount,
                        category = category,
                        note = note,
                        imageBase64 = imageBase64,
                        date = date
                    )
                } else {
                    homeViewModel.addTransaction(
                        type = type,
                        amount = amount,
                        category = category,
                        note = note,
                        imageBase64 = imageBase64,
                        date = date
                    )
                }
                isAddingOrEditing = false
                editingTransactionItem = null
            }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == 0) Icons.Filled.ReceiptLong else Icons.Outlined.ReceiptLong,
                                contentDescription = "รายการ"
                            )
                        },
                        label = {
                            Text(
                                "รายการ",
                                fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("tab_home"),
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == 1) Icons.Filled.PieChart else Icons.Outlined.PieChart,
                                contentDescription = "สรุปผล"
                            )
                        },
                        label = {
                            Text(
                                "สรุปผล",
                                fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("tab_summary"),
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == 2) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                                contentDescription = "แชท AI"
                            )
                        },
                        label = {
                            Text(
                                "แชท AI",
                                fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("tab_ai_chat"),
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == 3) Icons.Filled.Person else Icons.Outlined.Person,
                                contentDescription = "โปรไฟล์"
                            )
                        },
                        label = {
                            Text(
                                "โปรไฟล์",
                                fontWeight = if (currentTab == 3) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("tab_profile"),
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    0 -> {
                        HomeScreen(
                            uiState = uiState,
                            userProfile = userProfile,
                            selectedFilter = selectedFilter,
                            actionMessage = actionMessage,
                            onFilterChange = homeViewModel::setFilter,
                            onNavigateToAdd = {
                                editingTransactionItem = null
                                isAddingOrEditing = true
                            },
                            onNavigateToEdit = { tx ->
                                editingTransactionItem = tx
                                isAddingOrEditing = true
                            },
                            onDeleteTransaction = homeViewModel::deleteTransaction,
                            onClearActionMessage = homeViewModel::clearActionMessage
                        )
                    }
                    1 -> {
                        val transactionsList = (uiState as? UiState.Success)?.data ?: emptyList()
                        SummaryScreen(
                            transactions = transactionsList,
                            userProfile = userProfile,
                            onTransactionClick = { tx ->
                                selectedTxForSummaryDetail = tx
                            }
                        )
                    }
                    2 -> {
                        ChatScreen()
                    }
                    3 -> {
                        ProfileScreen(
                            userProfile = userProfile,
                            onSaveBudget = homeViewModel::updateMonthlyBudget,
                            onSignOut = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    withContext(NonCancellable) {
                                        try {
                                            credentialManager.clearCredentialState(ClearCredentialStateRequest())
                                        } catch (_: Exception) {
                                        } finally {
                                            Firebase.auth.signOut()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        val summaryTx = selectedTxForSummaryDetail
        if (summaryTx != null) {
            TransactionDetailDialog(
                transaction = summaryTx,
                onDismiss = { selectedTxForSummaryDetail = null },
                onEdit = {
                    editingTransactionItem = summaryTx
                    selectedTxForSummaryDetail = null
                    isAddingOrEditing = true
                },
                onDelete = {
                    selectedTxForSummaryDetail = null
                    homeViewModel.deleteTransaction(summaryTx.id)
                }
            )
        }
    }
}
