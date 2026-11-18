package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.AccountSessionManager
import com.example.data.firebase.OperationType
import com.example.data.firebase.PasswordHasher
import com.example.data.firebase.PhoneFormatUtils
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.SavedAccountRecord
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Star Max", appName)
  }

  @Test
  fun `main activity launches safely even when firebase is uninitialized`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    FirebaseApp.getApps(context).toList().forEach { app ->
      runCatching { app.delete() }
    }
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    assertTrue(!activity.isFinishing)
    controller.pause().stop().destroy()
  }

  @Test
  fun `firestore error handler does not throw when firebase is uninitialized`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    FirebaseApp.getApps(context).toList().forEach { app ->
      runCatching { app.delete() }
    }
    val json = handleFirestoreError(
      exception = IllegalStateException("Offline test error"),
      operationType = OperationType.GET,
      path = "profiles/test"
    )
    assertTrue(json.contains("Offline test error"))
  }

  @Test
  fun `phone format utils and password hasher validate Indian mobile and passwords`() {
    assertEquals("9876543210", PhoneFormatUtils.normalizeIndianPhoneDigits("9876543210"))
    assertEquals("9876543210", PhoneFormatUtils.normalizeIndianPhoneDigits("+91 98765 43210"))
    assertEquals("9876543210", PhoneFormatUtils.normalizeIndianPhoneDigits("09876543210"))
    assertNull(PhoneFormatUtils.normalizeIndianPhoneDigits("12345"))
    assertEquals("+919876543210", PhoneFormatUtils.toIndianE164("9876543210"))

    val salt = PasswordHasher.generateSalt()
    val hash = PasswordHasher.hashPassword("SecretPass99", salt)
    assertTrue(PasswordHasher.verifyPassword("SecretPass99", salt, hash))
    assertFalse(PasswordHasher.verifyPassword("WrongPass", salt, hash))
  }

  @Test
  fun `account session manager persists multiple accounts and clears on logout`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = AccountSessionManager(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    val acct1 = SavedAccountRecord(
      uid = "uid_one",
      slotName = "",
      username = "aarav_star",
      fullName = "Aarav",
      phoneE164 = "+919876543210",
      verified = true
    )
    manager.saveAccount(acct1, makeActive = true)
    assertEquals("uid_one", manager.activeUid.value)

    val secondSlot = manager.allocateSlotForAccount()
    assertTrue(secondSlot.startsWith("starmax_acct_"))

    val acct2 = SavedAccountRecord(
      uid = "uid_two",
      slotName = secondSlot,
      username = "diya_lens",
      fullName = "Diya",
      phoneE164 = "+919123456789",
      verified = true
    )
    manager.saveAccount(acct2, makeActive = true)
    assertEquals("uid_two", manager.activeUid.value)
    assertEquals(2, manager.savedAccounts.value.size)

    // Log out active account removes acct2 and clears activeUid
    manager.logoutActiveAccount(credentialManager = null, scope = scope)
    assertNull(manager.activeUid.value)
    assertEquals(1, manager.savedAccounts.value.size)
    assertEquals("uid_one", manager.savedAccounts.value.first().uid)

    // Log out all clears remaining saved accounts
    manager.logoutAllAccounts(credentialManager = null, scope = scope)
    assertNull(manager.activeUid.value)
    assertTrue(manager.savedAccounts.value.isEmpty())
  }
}
