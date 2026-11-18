package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
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
}
