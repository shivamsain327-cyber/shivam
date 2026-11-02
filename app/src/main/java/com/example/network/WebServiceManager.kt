package com.example.network

import com.example.model.ChecklistTask
import com.example.model.SubTask
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class SyncResult {
    data class Success(val tasks: List<ChecklistTask>, val rawResponse: String) : SyncResult()
    data class Error(val message: String, val statusCode: Int? = null) : SyncResult()
}

object WebServiceManager {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun syncFromWebService(
        url: String,
        method: String = "GET",
        authHeader: String = ""
    ): SyncResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext SyncResult.Error("API Endpoint URL cannot be empty")
        }

        try {
            val requestBuilder = Request.Builder().url(url)

            if (authHeader.isNotBlank()) {
                requestBuilder.addHeader("Authorization", authHeader.trim())
            }
            requestBuilder.addHeader("Accept", "application/json")
            requestBuilder.addHeader("User-Agent", "WorkAlert-Android-Sync/1.0")

            if (method.equals("POST", ignoreCase = true)) {
                val emptyBody = "{}".toRequestBody("application/json".toMediaTypeOrNull())
                requestBuilder.post(emptyBody)
            } else {
                requestBuilder.get()
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val code = response.code
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext SyncResult.Error(
                    "HTTP Error $code: ${response.message.ifBlank { "Request failed" }}",
                    code
                )
            }

            val tasks = parseJsonToTasks(bodyString)
            if (tasks.isEmpty()) {
                SyncResult.Error("Sync completed, but no tasks were found in the JSON response.")
            } else {
                SyncResult.Success(tasks, bodyString)
            }
        } catch (e: Exception) {
            SyncResult.Error("Connection error: ${e.localizedMessage ?: "Failed to reach endpoint"}")
        }
    }

    suspend fun triggerCloudWebhookAlert(
        webhookUrl: String,
        apiKey: String,
        phone: String,
        message: String,
        taskTitle: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = JSONObject().apply {
                put("phone", phone)
                put("message", message)
                put("task_title", taskTitle)
                put("timestamp", System.currentTimeMillis())
            }

            val requestBuilder = Request.Builder()
                .url(webhookUrl)
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaTypeOrNull()))

            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                requestBuilder.addHeader("x-api-key", apiKey)
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                Result.success("Webhook alert dispatched successfully (Code ${response.code})")
            } else {
                Result.failure(Exception("Webhook failed with HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseJsonToTasks(jsonString: String): List<ChecklistTask> {
        val tasks = mutableListOf<ChecklistTask>()
        try {
            val trimmed = jsonString.trim()
            val array: JSONArray = if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                when {
                    obj.has("checklists") -> obj.getJSONArray("checklists")
                    obj.has("tasks") -> obj.getJSONArray("tasks")
                    obj.has("data") -> obj.getJSONArray("data")
                    obj.has("items") -> obj.getJSONArray("items")
                    obj.has("pending") -> obj.getJSONArray("pending")
                    else -> JSONArray()
                }
            } else {
                return emptyList()
            }

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val taskId = item.optString("id", item.optString("task_id", UUID.randomUUID().toString()))
                val title = item.optString("title", item.optString("name", "Untitled Checklist"))
                val desc = item.optString("description", "")
                val empName = item.optString("employee_name", item.optString("employee", "Team Member"))
                val empPhone = item.optString("employee_phone", item.optString("phone", ""))
                val empId = item.optString("employee_id", "emp_${empName.lowercase().replace(" ", "_")}")
                val department = item.optString("department", "Operations")
                val category = item.optString("category", "Checklist")
                val dueDate = item.optString("due_date", "Today")
                val dueTime = item.optString("due_time", "06:00 PM")
                val rawStatus = item.optString("status", "PENDING").uppercase()
                val status = when {
                    rawStatus.contains("COMPLET") || rawStatus == "DONE" -> TaskStatus.COMPLETED
                    rawStatus.contains("PROGRESS") -> TaskStatus.IN_PROGRESS
                    else -> TaskStatus.PENDING
                }
                val rawPriority = item.optString("priority", "MEDIUM").uppercase()
                val priority = when {
                    rawPriority.contains("HIGH") || rawPriority.contains("URGENT") -> TaskPriority.HIGH
                    rawPriority.contains("LOW") -> TaskPriority.LOW
                    else -> TaskPriority.MEDIUM
                }
                // Use the server's flag if it sends one; otherwise the due date/time decides
                // (see DueTime). Previously every unfinished task defaulted to overdue.
                val isOverdue = status != TaskStatus.COMPLETED && item.optBoolean("is_overdue", false)

                // Subtasks / Items
                val subtaskList = mutableListOf<SubTask>()
                if (item.has("items")) {
                    val itemsArray = item.getJSONArray("items")
                    for (j in 0 until itemsArray.length()) {
                        val sub = itemsArray.get(j)
                        if (sub is JSONObject) {
                            subtaskList.add(
                                SubTask(
                                    id = sub.optString("id", "sub_${taskId}_$j"),
                                    title = sub.optString("title", sub.optString("name", "Check item ${j + 1}")),
                                    isCompleted = sub.optBoolean("completed", sub.optBoolean("done", false))
                                )
                            )
                        } else {
                            subtaskList.add(
                                SubTask(
                                    id = "sub_${taskId}_$j",
                                    title = sub.toString(),
                                    isCompleted = false
                                )
                            )
                        }
                    }
                } else if (item.has("checklist")) {
                    val itemsArray = item.getJSONArray("checklist")
                    for (j in 0 until itemsArray.length()) {
                        subtaskList.add(
                            SubTask(
                                id = "sub_${taskId}_$j",
                                title = itemsArray.getString(j),
                                isCompleted = false
                            )
                        )
                    }
                }

                tasks.add(
                    ChecklistTask(
                        id = taskId,
                        title = title,
                        description = desc,
                        employeeId = empId,
                        employeeName = empName,
                        employeePhone = empPhone,
                        department = department,
                        category = category,
                        subtasks = subtaskList,
                        dueDate = dueDate,
                        dueTime = dueTime,
                        status = status,
                        priority = priority,
                        isOverdue = isOverdue,
                        syncedFromApi = true
                    )
                )
            }
        } catch (_: Exception) {
            // Error parsing json
        }
        return tasks
    }

    fun getSampleJsonPayload(): String {
        return """
[
  {
    "id": "chk_101",
    "title": "Morning Store Opening & Safety Checklist",
    "employee_name": "Rahul Sharma",
    "employee_phone": "+91 98765 43210",
    "department": "Store Operations",
    "category": "Daily Opening",
    "due_date": "Today",
    "due_time": "09:30 AM",
    "status": "PENDING",
    "priority": "HIGH",
    "is_overdue": true,
    "items": [
      "AC and Lighting inspection",
      "Billing counter cash float verification",
      "Floor sanitization check",
      "Display shelf restocking"
    ]
  },
  {
    "id": "chk_102",
    "title": "Kitchen Hygiene & Temperature Audit",
    "employee_name": "Priya Singh",
    "employee_phone": "+91 98111 22334",
    "department": "Kitchen & Food Prep",
    "category": "Quality Control",
    "due_date": "Today",
    "due_time": "11:00 AM",
    "status": "PENDING",
    "priority": "HIGH",
    "is_overdue": true,
    "items": [
      "Cold storage chiller temp log (-18°C)",
      "Food prep area sanitization",
      "Raw ingredients expiry tag verification"
    ]
  },
  {
    "id": "chk_103",
    "title": "Customer Order Dispatch Verification",
    "employee_name": "Amit Verma",
    "employee_phone": "+91 97222 33445",
    "department": "Logistics & Dispatch",
    "category": "Dispatch Checklist",
    "due_date": "Today",
    "due_time": "01:00 PM",
    "status": "PENDING",
    "priority": "MEDIUM",
    "is_overdue": false,
    "items": [
      "Verify shipping invoice numbers",
      "Tamper-proof seal quality check",
      "Delivery partner handover barcode scan"
    ]
  },
  {
    "id": "chk_104",
    "title": "Warehouse End-of-Day Inventory Count",
    "employee_name": "Neha Patel",
    "employee_phone": "+91 99333 44556",
    "department": "Warehouse",
    "category": "Inventory Audit",
    "due_date": "Today",
    "due_time": "06:00 PM",
    "status": "PENDING",
    "priority": "MEDIUM",
    "is_overdue": false,
    "items": [
      "Stock tally for High-Value Goods",
      "Damaged goods return documentation",
      "Warehouse shutter locking protocol"
    ]
  }
]
        """.trimIndent()
    }
}
