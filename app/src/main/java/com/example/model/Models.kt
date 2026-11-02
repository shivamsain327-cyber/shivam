package com.example.model

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED
}

enum class TaskPriority {
    HIGH,
    MEDIUM,
    LOW
}

data class SubTask(
    val id: String,
    val title: String,
    val isCompleted: Boolean = false
)

data class ChecklistTask(
    val id: String,
    val title: String,
    val description: String = "",
    val employeeId: String,
    val employeeName: String,
    val employeePhone: String,
    val department: String = "General",
    val category: String = "Daily Checklist",
    val subtasks: List<SubTask> = emptyList(),
    val dueDate: String,
    val dueTime: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val lastReminderSentAt: Long? = null,
    val reminderCount: Int = 0,
    val isOverdue: Boolean = false,
    val syncedFromApi: Boolean = false
) {
    val pendingSubtasksCount: Int
        get() = subtasks.count { !it.isCompleted }

    val completedSubtasksCount: Int
        get() = subtasks.count { it.isCompleted }

    val progressPercent: Float
        get() = if (subtasks.isEmpty()) {
            if (status == TaskStatus.COMPLETED) 1f else 0f
        } else {
            completedSubtasksCount.toFloat() / subtasks.size.toFloat()
        }
}

data class Employee(
    val id: String,
    val name: String,
    val phone: String,
    val department: String,
    val role: String,
    val active: Boolean = true
)

data class ReminderLog(
    val id: String,
    val employeeName: String,
    val employeePhone: String,
    val taskTitle: String,
    val messageText: String,
    val timestamp: Long,
    val status: String = "Sent via WhatsApp",
    val channel: String = "WhatsApp Direct"
)

data class MessageTemplate(
    val id: String,
    val title: String,
    val language: String,
    val content: String,
    val isDefault: Boolean = false
)

data class WebServiceConfig(
    val url: String = "https://api.mycompany.com/v1/employee-checklists/pending",
    val method: String = "GET",
    val authHeader: String = "",
    val autoSyncEnabled: Boolean = false,
    val autoSyncMinutes: Int = 30,
    val lastSyncTimestamp: Long? = null,
    val lastSyncStatus: String? = null,
    val lastSyncCount: Int = 0,
    val companyName: String = "Apex Retail & Ops",
    val whatsappCloudWebhookUrl: String = "",
    val whatsappApiKey: String = ""
)
