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
    val status: String = ReminderLog.STATUS_OPENED,
    val channel: String = ReminderLog.CHANNEL_DIRECT
) {
    companion object {
        const val STATUS_OPENED = "Opened in WhatsApp"
        const val STATUS_SENT = "Sent via webhook"
        const val STATUS_FAILED = "Webhook failed"
        const val CHANNEL_DIRECT = "WhatsApp Direct"
        const val CHANNEL_WEBHOOK = "WhatsApp API (auto)"
    }
}

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
    val whatsappApiKey: String = "",
    /** When true (and a webhook URL is set), overdue tasks are messaged automatically after each sync. */
    val autoSendWebhookAlerts: Boolean = false,
    /** Minimum gap between two automatic reminders for the same task. */
    val reminderCooldownMinutes: Int = 120
)
