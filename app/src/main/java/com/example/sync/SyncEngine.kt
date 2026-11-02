package com.example.sync

import com.example.data.TaskRepository
import com.example.model.ChecklistTask
import com.example.model.ReminderLog
import com.example.model.TaskStatus
import com.example.model.WebServiceConfig
import com.example.network.SyncResult
import com.example.network.WebServiceManager
import com.example.whatsapp.WhatsAppDispatcher

/** Sync + automatic alert logic shared by the "Sync now" button and the background worker. */
object SyncEngine {

    data class AlertSummary(val sent: Int, val failed: Int)

    /** Pulls checklists from the configured web service and merges them in. */
    suspend fun syncFromConfiguredService(repository: TaskRepository): SyncResult {
        repository.awaitLoaded()
        val config = repository.webServiceConfig.value
        val result = WebServiceManager.syncFromWebService(config.url, config.method, config.authHeader)
        val now = System.currentTimeMillis()
        when (result) {
            is SyncResult.Success -> {
                repository.mergeSyncedTasksNow(result.tasks)
                repository.updateConfigNow(
                    repository.webServiceConfig.value.copy(
                        lastSyncTimestamp = now,
                        lastSyncStatus = "Success (${result.tasks.size} checklists received)",
                        lastSyncCount = result.tasks.size
                    )
                )
            }
            is SyncResult.Error -> repository.updateConfigNow(
                repository.webServiceConfig.value.copy(
                    lastSyncTimestamp = now,
                    lastSyncStatus = "Failed: ${result.message}"
                )
            )
        }
        return result
    }

    /** Tasks that should get an automatic reminder right now. */
    fun tasksDueForAutoAlert(
        tasks: List<ChecklistTask>,
        config: WebServiceConfig,
        now: Long = System.currentTimeMillis()
    ): List<ChecklistTask> {
        val cooldownMs = config.reminderCooldownMinutes.coerceAtLeast(1) * 60_000L
        return tasks.filter { task ->
            task.status != TaskStatus.COMPLETED &&
                task.isOverdue &&
                task.employeePhone.isNotBlank() &&
                (task.lastReminderSentAt == null || now - task.lastReminderSentAt >= cooldownMs)
        }
    }

    /**
     * Sends overdue reminders through the configured WhatsApp API webhook
     * (EasySocial, Meta Cloud API relay, etc.). Does nothing unless auto-send is
     * switched on and a webhook URL is set.
     */
    suspend fun sendDueWebhookAlerts(repository: TaskRepository): AlertSummary {
        repository.awaitLoaded()
        repository.refreshOverdueFlags()
        val config = repository.webServiceConfig.value
        if (!config.autoSendWebhookAlerts || config.whatsappCloudWebhookUrl.isBlank()) {
            return AlertSummary(0, 0)
        }

        val templates = repository.templates.value
        val template = (templates.firstOrNull { it.isDefault } ?: templates.firstOrNull())?.content
            ?: "Reminder: Your task {task_title} is pending!"
        val employees = repository.employees.value

        var sent = 0
        var failed = 0
        for (task in tasksDueForAutoAlert(repository.tasks.value, config)) {
            val employee = employees.firstOrNull { it.id == task.employeeId }
            val message = WhatsAppDispatcher.buildReminderMessage(template, task, employee, config.companyName)
            val result = WebServiceManager.triggerCloudWebhookAlert(
                webhookUrl = config.whatsappCloudWebhookUrl,
                apiKey = config.whatsappApiKey,
                phone = WhatsAppDispatcher.sanitizePhoneNumber(task.employeePhone),
                message = message,
                taskTitle = task.title
            )
            val ok = result.isSuccess
            if (ok) sent++ else failed++
            repository.recordReminderNow(
                taskId = task.id,
                employeeName = task.employeeName,
                employeePhone = task.employeePhone,
                message = message,
                status = if (ok) ReminderLog.STATUS_SENT else ReminderLog.STATUS_FAILED,
                channel = ReminderLog.CHANNEL_WEBHOOK
            )
        }
        return AlertSummary(sent, failed)
    }
}
