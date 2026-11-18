package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.AccountSessionManager
import com.example.data.firebase.OperationType
import com.example.data.firebase.PasswordHasher
import com.example.data.firebase.PhoneFormatUtils
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.SavedAccountRecord
import com.example.data.model.SampleData
import com.example.data.model.UserSettings
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
  fun `fresh install starts unauthenticated and supports full signup, login, verification, switcher, and logout`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = AccountSessionManager(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    // Fresh install must NOT pre-login any dummy user
    assertNull(manager.activeUid.value)
    assertTrue(manager.savedAccounts.value.isEmpty())

    // Register first account
    val reg1 = manager.registerAccountCredentials(
      fullName = "Aarav Sharma",
      username = "aarav_star",
      phoneDigits10 = "9876543210",
      password = "Password123",
      verified = false
    ).getOrThrow()
    assertEquals(reg1.uid, manager.activeUid.value)
    assertFalse(reg1.verified)

    // Duplicate username or phone must fail
    assertFalse(manager.isLocalUsernameAvailable("aarav_star"))
    assertFalse(manager.isLocalPhoneAvailable("+91 98765 43210"))
    assertTrue(
      manager.registerAccountCredentials(
        fullName = "Duplicate",
        username = "aarav_star",
        phoneDigits10 = "9123456780",
        password = "Password123"
      ).isFailure
    )

    // Register second account (+ Add Account)
    val reg2 = manager.registerAccountCredentials(
      fullName = "Diya Lens",
      username = "diya_lens",
      phoneDigits10 = "9123456789",
      password = "DiyaPass99",
      verified = false
    ).getOrThrow()
    assertEquals(reg2.uid, manager.activeUid.value)
    assertEquals(2, manager.savedAccounts.value.size)

    // Toggle Star Max Verified ON for reg2 and verify persistence
    manager.updateAccountVerification(
      uid = reg2.uid,
      verified = true,
      verificationStatus = "verified",
      fullName = "Diya Lens",
      category = "Creator"
    )
    assertTrue(manager.savedAccounts.value.first { it.uid == reg2.uid }.verified)

    // Switch back to reg1 in 1 tap
    assertTrue(manager.switchToSavedAccount(reg1.uid))
    assertEquals(reg1.uid, manager.activeUid.value)

    // Log out active account clears activeUid back to null (AuthScreen gate) while keeping registered credentials
    manager.logoutActiveAccount(credentialManager = null, scope = scope)
    assertNull(manager.activeUid.value)
    assertEquals(1, manager.savedAccounts.value.size)

    // Log back in with 10-digit phone or username + password
    assertTrue(manager.authenticateLocalAccount("aarav_star", "WrongPass").isFailure)
    val loggedBackIn = manager.authenticateLocalAccount("+91 98765 43210", "Password123").getOrThrow()
    assertEquals(reg1.uid, loggedBackIn.uid)
    assertEquals(reg1.uid, manager.activeUid.value)

    // Log out all clears remaining saved sessions
    manager.logoutAllAccounts(credentialManager = null, scope = scope)
    assertNull(manager.activeUid.value)
    assertTrue(manager.savedAccounts.value.isEmpty())
  }

  @Test
  fun `verification defaults and sample verified creators match expected state`() {
    val defaultSettings = UserSettings(userId = "u1")
    assertEquals("none", defaultSettings.verificationStatus)
    assertEquals("", defaultSettings.verificationFullName)
    assertEquals("", defaultSettings.verificationIdProofData)

    val verifiedIds = SampleData.sampleProfiles.filter { it.verified }.map { it.userId }.toSet()
    assertTrue("sample_kai" in verifiedIds)
    assertTrue("sample_maya" in verifiedIds)
    assertTrue("sample_marcus" in verifiedIds)
    assertFalse("sample_elena" in verifiedIds)
  }
}
