package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.TransactionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.UUID

class TransactionRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun addTransaction_validPayload_createsDocumentAndReturnsId() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repository = TransactionRepository(firestore)

    val item = TransactionItem(
      id = UUID.randomUUID().toString(),
      userId = uid,
      type = "EXPENSE",
      amount = 120.0,
      category = "อาหาร",
      note = "ข้าวกะเพรา",
      date = Timestamp.now()
    )

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.addTransaction(uid, item)
    }
    assertTrue(result.isSuccess)
    val docId = result.getOrThrow()
    assertTrue(docId.isNotEmpty())
  }

  @Test
  fun observeTransactions_authenticatedOwner_returnsMatchingTransactions() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repository = TransactionRepository(firestore)

    val item = TransactionItem(
      id = UUID.randomUUID().toString(),
      userId = uid,
      type = "INCOME",
      amount = 35000.0,
      category = "เงินเดือน",
      date = Timestamp.now()
    )

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.addTransaction(uid, item)
    }
    assertTrue(result.isSuccess)
    val docId = result.getOrThrow()

    val emitted = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeTransactions(uid).first { list -> list.any { it.id == docId } }
    }
    assertTrue(emitted.map { it.id }.contains(docId))
  }

  @Test
  fun getTransactions_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = TransactionRepository(firestore)

    val item = TransactionItem(
      id = UUID.randomUUID().toString(),
      userId = aliceUid,
      type = "EXPENSE",
      amount = 500.0,
      category = "ช้อปปิ้ง",
      date = Timestamp.now()
    )
    val addResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.addTransaction(aliceUid, item)
    }
    assertTrue(addResult.isSuccess)

    val bobUid = signInTestUser(BOB_EMAIL)
    val bobRepo = TransactionRepository(firestore)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        bobRepo.observeTransactions(aliceUid).first()
      }
      fail("Expected exception was not thrown")
    } catch (e: Exception) {
      val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
        "Expected nested FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }
  }

  @Test
  fun observeTransactions_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    val aliceUid = "some_user_id"
    auth.signOut()
    val repository = TransactionRepository(firestore)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeTransactions(aliceUid).first()
      }
      fail("Expected exception was not thrown")
    } catch (e: Exception) {
      val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
        "Expected nested FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice@example.com"
    const val BOB_EMAIL = "bob@example.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
