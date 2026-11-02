package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.network.WebServiceManager
import com.example.whatsapp.WhatsAppDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("WorkAlert", appName)
  }

  @Test
  fun `test phone sanitization for India`() {
    val raw = "+91 98765-43210"
    val clean = WhatsAppDispatcher.sanitizePhoneNumber(raw)
    assertEquals("919876543210", clean)

    val tenDigits = "9876543210"
    assertEquals("919876543210", WhatsAppDispatcher.sanitizePhoneNumber(tenDigits))
  }

  @Test
  fun `test sample JSON parsing for Web Service`() {
    val sample = WebServiceManager.getSampleJsonPayload()
    val tasks = WebServiceManager.parseJsonToTasks(sample)
    assertTrue("Should parse sample tasks", tasks.isNotEmpty())
    assertEquals("Rahul Sharma", tasks.first().employeeName)
  }
}
