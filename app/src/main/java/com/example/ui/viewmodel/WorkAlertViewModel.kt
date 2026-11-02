package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TaskRepository
import com.example.model.*
import com.example.network.SyncResult
import com.example.network.WebServiceManager
import com.example.sync.AutoSyncScheduler
import com.example.sync.SyncEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.example.whatsapp.WhatsAppDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WorkAlertUiState(
    val selectedTab: Int = 0,
    val taskFilter: String = "PENDING", // PENDING, OVERDUE, ALL, COMPLETED
    val searchQuery: String = "",
    val isSyncing: Boolean = false,
    val syncBannerMessage: String? = null,
    val isSyncSuccess: Boolean = true,
    val batchQueue: List<ChecklistTask> = emptyList(),
    val batchCurrentIndex: Int = 0,
    val isBatchModalVisible: Boolean = false,
    val isAddTaskModalVisible: Boolean = false,
    val isAddEmployeeModalVisible: Boolean = false,
    val selectedTaskForDetail: ChecklistTask? = null,
    val toastMessage: String? = null
)

class WorkAlertViewModel(application: Application) : AndroidViewModel(application) {

    val repository = TaskRepository.get(application)

    val tasks = repository.tasks
    val employees = repository.employees
    val templates = repository.templates
    val reminderLogs = repository.reminderLogs
    val config = repository.webServiceConfig

    private val _uiState = MutableStateFlow(WorkAlertUiState())
    val uiState: StateFlow<WorkAlertUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.awaitLoaded()
            // Make sure background sync matches the saved settings (e.g. after an app update).
            AutoSyncScheduler.apply(getApplication(), config.value)
            // Keep "overdue" in step with the clock while the app is open.
            while (isActive) {
                repository.refreshOverdueFlags()
                delay(60_000)
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun setTaskFilter(filter: String) {
        _uiState.update { it.copy(taskFilter = filter) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun showAddTaskModal(show: Boolean) {
        _uiState.update { it.copy(isAddTaskModalVisible = show) }
    }

    fun showAddEmployeeModal(show: Boolean) {
        _uiState.update { it.copy(isAddEmployeeModalVisible = show) }
    }

    fun selectTaskDetail(task: ChecklistTask?) {
        _uiState.update { it.copy(selectedTaskForDetail = task) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun clearSyncBanner() {
        _uiState.update { it.copy(syncBannerMessage = null) }
    }

    // --- WHATSAPP REMINDER DISPATCH ---
    fun sendWhatsAppReminder(context: Context, task: ChecklistTask) {
        val currentTemplates = templates.value
        val defaultTpl = currentTemplates.firstOrNull { it.isDefault } ?: currentTemplates.firstOrNull()
        val templateContent = defaultTpl?.content ?: "Reminder: Your task {task_title} is pending!"
        val companyName = config.value.companyName
        val employee = employees.value.firstOrNull { it.id == task.employeeId }

        val message = WhatsAppDispatcher.buildReminderMessage(
            template = templateContent,
            task = task,
            employee = employee,
            companyName = companyName
        )

        val success = WhatsAppDispatcher.openWhatsApp(
            context = context,
            phoneNumber = task.employeePhone,
            message = message,
            onError = { errMsg ->
                _uiState.update { it.copy(toastMessage = errMsg) }
            }
        )

        if (success) {
            repository.recordReminderDispatched(
                taskId = task.id,
                employeeName = task.employeeName,
                employeePhone = task.employeePhone,
                message = message,
                status = ReminderLog.STATUS_OPENED,
                channel = ReminderLog.CHANNEL_DIRECT
            )
            _uiState.update { it.copy(toastMessage = "WhatsApp opened for ${task.employeeName}") }
        }
    }

    // --- BATCH REMINDERS (AUTO SEND ALL PENDING) ---
    fun startBatchReminder() {
        val pendingTasks = tasks.value.filter {
            (it.status == TaskStatus.PENDING || it.isOverdue) && it.employeePhone.isNotBlank()
        }
        if (pendingTasks.isEmpty()) {
            _uiState.update { it.copy(toastMessage = "No pending tasks with phone numbers found!") }
            return
        }

        _uiState.update {
            it.copy(
                batchQueue = pendingTasks,
                batchCurrentIndex = 0,
                isBatchModalVisible = true
            )
        }
    }

    fun dismissBatchModal() {
        _uiState.update { it.copy(isBatchModalVisible = false) }
    }

    fun dispatchNextBatchItem(context: Context) {
        val state = _uiState.value
        if (state.batchCurrentIndex < state.batchQueue.size) {
            val task = state.batchQueue[state.batchCurrentIndex]
            sendWhatsAppReminder(context, task)

            val nextIndex = state.batchCurrentIndex + 1
            if (nextIndex >= state.batchQueue.size) {
                _uiState.update {
                    it.copy(
                        batchCurrentIndex = nextIndex,
                        isBatchModalVisible = false,
                        toastMessage = "All ${state.batchQueue.size} pending WhatsApp alerts dispatched!"
                    )
                }
            } else {
                _uiState.update { it.copy(batchCurrentIndex = nextIndex) }
            }
        }
    }

    fun skipCurrentBatchItem() {
        val state = _uiState.value
        val nextIndex = state.batchCurrentIndex + 1
        if (nextIndex >= state.batchQueue.size) {
            _uiState.update { it.copy(isBatchModalVisible = false) }
        } else {
            _uiState.update { it.copy(batchCurrentIndex = nextIndex) }
        }
    }

    // --- WEB SERVICE SYNC ---
    fun syncWithWebService() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncBannerMessage = null) }

            val result = SyncEngine.syncFromConfiguredService(repository)
            val alerts = SyncEngine.sendDueWebhookAlerts(repository)
            val alertNote = if (alerts.sent + alerts.failed > 0) {
                " · Auto WhatsApp: ${alerts.sent} sent, ${alerts.failed} failed"
            } else ""

            when (result) {
                is SyncResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            isSyncSuccess = true,
                            syncBannerMessage = "Successfully synced ${result.tasks.size} checklists from Web Service!",
                            toastMessage = "Web Service sync complete!$alertNote"
                        )
                    }
                }
                is SyncResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            isSyncSuccess = false,
                            syncBannerMessage = "Sync Error: ${result.message}",
                            toastMessage = "Failed to sync: ${result.message}$alertNote"
                        )
                    }
                }
            }
        }
    }

    fun syncWithSampleMockData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            kotlinx.coroutines.delay(600) // Realistic network sensation
            val sampleJson = WebServiceManager.getSampleJsonPayload()
            val tasks = WebServiceManager.parseJsonToTasks(sampleJson)
            repository.mergeSyncedTasks(tasks)
            val currentConfig = config.value
            repository.updateConfig(
                currentConfig.copy(
                    lastSyncTimestamp = System.currentTimeMillis(),
                    lastSyncStatus = "Sample Sync OK (${tasks.size} checklists)",
                    lastSyncCount = tasks.size
                )
            )
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    isSyncSuccess = true,
                    syncBannerMessage = "Sample Web Service response loaded (${tasks.size} checklists refreshed)",
                    toastMessage = "Sample checklists loaded successfully!"
                )
            }
        }
    }

    fun importCustomJson(jsonString: String) {
        val tasks = WebServiceManager.parseJsonToTasks(jsonString)
        if (tasks.isNotEmpty()) {
            repository.mergeSyncedTasks(tasks)
            _uiState.update {
                it.copy(
                    syncBannerMessage = "Imported ${tasks.size} checklists from custom JSON!",
                    toastMessage = "Successfully imported ${tasks.size} tasks!"
                )
            }
        } else {
            _uiState.update {
                it.copy(toastMessage = "Invalid JSON structure. Please check the sample format.")
            }
        }
    }

    // --- TASK ACTIONS ---
    fun toggleSubtask(taskId: String, subtaskId: String) {
        repository.toggleSubtask(taskId, subtaskId)
    }

    fun markTaskCompleted(taskId: String) {
        repository.updateTaskStatus(taskId, TaskStatus.COMPLETED)
        _uiState.update { it.copy(toastMessage = "Checklist marked as Completed!") }
    }

    fun markTaskPending(taskId: String) {
        repository.updateTaskStatus(taskId, TaskStatus.PENDING)
    }

    fun deleteTask(taskId: String) {
        repository.deleteTask(taskId)
        _uiState.update { it.copy(toastMessage = "Task removed") }
    }

    fun addNewTask(task: ChecklistTask) {
        repository.addTask(task)
        _uiState.update { it.copy(isAddTaskModalVisible = false, toastMessage = "New checklist created!") }
    }

    fun addNewEmployee(employee: Employee) {
        repository.addEmployee(employee)
        _uiState.update { it.copy(isAddEmployeeModalVisible = false, toastMessage = "Employee ${employee.name} added!") }
    }

    fun updateConfig(config: WebServiceConfig) {
        repository.updateConfig(config)
        AutoSyncScheduler.apply(getApplication(), config)
        _uiState.update { it.copy(toastMessage = "Web Service settings saved!") }
    }

    fun updateTemplate(template: MessageTemplate) {
        repository.updateTemplate(template)
        _uiState.update { it.copy(toastMessage = "Message template updated!") }
    }

    fun setDefaultTemplate(templateId: String) {
        repository.setDefaultTemplate(templateId)
        _uiState.update { it.copy(toastMessage = "Default template set!") }
    }

    fun clearHistory() {
        repository.clearReminderLogs()
        _uiState.update { it.copy(toastMessage = "Reminder history cleared") }
    }
}
