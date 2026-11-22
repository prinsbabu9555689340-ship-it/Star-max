package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.AccountSessionManager
import com.example.data.firebase.OperationType
import com.example.data.firebase.PasswordHasher
import com.example.data.firebase.PhoneFormatUtils
import com.example.data.firebase.SocialRepository
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.SavedAccountRecord
import com.example.data.model.StoryFilter
import com.example.data.model.StoryMusicCatalog
import com.example.data.model.StoryReaction
import com.example.data.model.StoryReactionType
import com.example.data.model.UserProfile
import com.example.data.model.UserSettings
import com.example.data.model.VerificationRequest
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    // Update pending verification state on saved record
    manager.updateAccountVerification(
      uid = reg2.uid,
      verified = false,
      verificationStatus = "pending",
      fullName = "Diya Lens",
      category = "Creator"
    )
    assertEquals("pending", manager.savedAccounts.value.first { it.uid == reg2.uid }.verificationStatus)

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
  fun `verification defaults, effectiveVerified, and pending request model match strict rules`() {
    val defaultSettings = UserSettings(userId = "u1")
    assertEquals("none", defaultSettings.verificationStatus)
    assertEquals("", defaultSettings.verificationFullName)
    assertEquals("", defaultSettings.verificationIdProofData)
    assertFalse(defaultSettings.isVerified)

    val unverifiedEvenWithLegacyFlag = UserProfile(userId = "u1", isVerified = false, verified = true)
    assertFalse(unverifiedEvenWithLegacyFlag.effectiveVerified)

    val verifiedUser = UserProfile(userId = "u2", isVerified = true, verified = false)
    assertTrue(verifiedUser.effectiveVerified)

    val request = VerificationRequest(
      userId = "u1",
      username = "aarav_star",
      fullName = "Aarav Sharma",
      category = "Creator",
      idOrLink = "+919876543210"
    )
    assertEquals("pending", request.status)
  }

  @Test
  fun `story filters and trending music catalog are valid`() {
    assertNull(StoryFilter.NORMAL.toColorFilter())
    assertNotNull(StoryFilter.WARM.toColorFilter())
    assertNotNull(StoryFilter.COOL.toColorFilter())
    assertNotNull(StoryFilter.VINTAGE.toColorFilter())
    assertNotNull(StoryFilter.BW.toColorFilter())
    assertNotNull(StoryFilter.VIVID.toColorFilter())

    assertEquals(StoryFilter.VINTAGE, StoryFilter.fromId("Vintage"))
    assertEquals(StoryFilter.BW, StoryFilter.fromId("b&w"))
    assertEquals(StoryFilter.NORMAL, StoryFilter.fromId("unknown_filter"))

    assertTrue(StoryMusicCatalog.trendingTracks.isNotEmpty())
    StoryMusicCatalog.trendingTracks.forEach { track ->
      assertTrue(track.title.isNotBlank())
      assertTrue(track.artist.isNotBlank())
      assertTrue(track.streamUrl.startsWith("https://"))
      assertTrue(track.streamUrl.endsWith(".mp3"))
    }
  }

  @Test
  fun `story reaction types and model link cleanly to storyId`() {
    assertEquals(StoryReactionType.HEART, StoryReactionType.fromWire("heart"))
    assertEquals(StoryReactionType.FIRE, StoryReactionType.fromWire("FIRE"))
    assertEquals(StoryReactionType.CLAP, StoryReactionType.fromWire(" clap "))
    assertNull(StoryReactionType.fromWire("thumbs_up"))
    assertNull(StoryReactionType.fromWire(null))

    val reaction = StoryReaction(
      storyId = "story_42",
      storyOwnerId = "owner_1",
      userId = "viewer_2",
      username = "aarav_star",
      reactionType = StoryReactionType.FIRE.wireValue
    )
    assertEquals("story_42", reaction.storyId)
    assertEquals("fire", reaction.reactionType)
    assertEquals("public", reaction.visibility)
  }

  @Test
  fun `account session manager safely returns null instead of crashing when firebase is uninitialized`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    FirebaseApp.getApps(context).toList().forEach { app ->
      runCatching { app.delete() }
    }
    val manager = AccountSessionManager(context)
    // Must never throw IllegalStateException on launch even if Firebase is unavailable
    runCatching { manager.resolveAuth() }.getOrThrow()
    runCatching { manager.resolveFirestore() }.getOrThrow()
  }

  @Test
  fun `global crash reporter captures exact exception message and clears cleanly`() {
    AppCrashReporter.clear()
    val report = AppCrashReporter.report(
      IllegalStateException("Simulated startup failure"),
      threadName = "worker-1"
    )
    assertEquals("IllegalStateException", report.exceptionName)
    assertEquals("Simulated startup failure", report.message)
    assertEquals("worker-1", report.threadName)
    assertNotNull(AppCrashReporter.crashReport.value)

    AppCrashReporter.clear()
    assertNull(AppCrashReporter.crashReport.value)
  }

  @Test
  fun `offline error detector matches client offline and UNAVAILABLE without masking permission errors`() {
    val offlineErr = FirebaseFirestoreException(
      "Failed to get document because the client is offline.",
      FirebaseFirestoreException.Code.UNAVAILABLE
    )
    assertTrue(SocialRepository.isOfflineOrUnavailableError(offlineErr))
    assertTrue(
      SocialRepository.isOfflineOrUnavailableError(
        IllegalStateException("Failed to get document because the client is offline")
      )
    )

    val permissionErr = FirebaseFirestoreException(
      "Missing or insufficient permissions.",
      FirebaseFirestoreException.Code.PERMISSION_DENIED
    )
    assertFalse(SocialRepository.isOfflineOrUnavailableError(permissionErr))
    assertFalse(SocialRepository.isOfflineOrUnavailableError(null))
  }
}
