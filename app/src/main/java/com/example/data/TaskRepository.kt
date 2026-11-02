package com.example.data

import android.content.Context
import com.example.model.*
import com.example.util.DueTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class TaskRepository private constructor(private val context: Context) {

    companion object {
        @Volatile private var instance: TaskRepository? = null

        /**
         * One shared repository per process, so the screens and the background
         * sync worker never write the same JSON files from two copies at once.
         */
        fun get(context: Context): TaskRepository =
            instance ?: synchronized(this) {
                instance ?: TaskRepository(context.applicationContext).also { instance = it }
            }
    }

    private val loaded = CompletableDeferred<Unit>()

    /** Suspends until the saved data has been read from disk. */
    suspend fun awaitLoaded() = loaded.await()

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _tasks = MutableStateFlow<List<ChecklistTask>>(emptyList())
    val tasks: StateFlow<List<ChecklistTask>> = _tasks.asStateFlow()

    private val _employees = MutableStateFlow<List<Employee>>(emptyList())
    val employees: StateFlow<List<Employee>> = _employees.asStateFlow()

    private val _templates = MutableStateFlow<List<MessageTemplate>>(emptyList())
    val templates: StateFlow<List<MessageTemplate>> = _templates.asStateFlow()

    private val _reminderLogs = MutableStateFlow<List<ReminderLog>>(emptyList())
    val reminderLogs: StateFlow<List<ReminderLog>> = _reminderLogs.asStateFlow()

    private val _webServiceConfig = MutableStateFlow(WebServiceConfig())
    val webServiceConfig: StateFlow<WebServiceConfig> = _webServiceConfig.asStateFlow()

    init {
        scope.launch {
            loadAllData()
        }
    }

    private suspend fun loadAllData() {
        mutex.withLock {
            loadEmployeesInternal()
            loadTemplatesInternal()
            loadConfigInternal()
            loadTasksInternal()
            loadReminderLogsInternal()
        }
        loaded.complete(Unit)
        refreshOverdueFlags()
    }

    /** Re-evaluates every task's overdue flag against the current time. */
    suspend fun refreshOverdueFlags(now: Long = System.currentTimeMillis()) {
        mutex.withLock {
            var changed = false
            val updated = _tasks.value.map { task ->
                val overdue = DueTime.isOverdue(task, now)
                if (overdue != task.isOverdue) {
                    changed = true
                    task.copy(isOverdue = overdue)
                } else task
            }
            if (changed) {
                _tasks.value = updated
                saveTasksInternal(updated)
            }
        }
    }

    // --- EMPLOYEES ---
    private fun loadEmployeesInternal() {
        val file = File(context.filesDir, "employees.json")
        if (file.exists()) {
            try {
                val json = file.readText()
                val array = JSONArray(json)
                val list = mutableListOf<Employee>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        Employee(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            phone = obj.getString("phone"),
                            department = obj.getString("department"),
                            role = obj.getString("role"),
                            active = obj.optBoolean("active", true)
                        )
                    )
                }
                _employees.value = list
                return
            } catch (_: Exception) {
                // fallback to initial
            }
        }

        // Seed Initial Employees
        val initialEmployees = listOf(
            Employee("emp_1", "Rahul Sharma", "+91 98765 43210", "Store Operations", "Shift Incharge"),
            Employee("emp_2", "Priya Singh", "+91 98111 22334", "Kitchen & Quality", "Quality Lead"),
            Employee("emp_3", "Amit Verma", "+91 97222 33445", "Logistics & Dispatch", "Dispatch Supervisor"),
            Employee("emp_4", "Neha Patel", "+91 99333 44556", "Warehouse", "Inventory Manager"),
            Employee("emp_5", "Suresh Kumar", "+91 98444 55667", "Maintenance", "Facilities Tech")
        )
        _employees.value = initialEmployees
        saveEmployeesInternal(initialEmployees)
    }

    fun addEmployee(employee: Employee) {
        scope.launch {
            mutex.withLock {
                val updated = _employees.value + employee
                _employees.value = updated
                saveEmployeesInternal(updated)
            }
        }
    }

    fun updateEmployee(employee: Employee) {
        scope.launch {
            mutex.withLock {
                val updated = _employees.value.map { if (it.id == employee.id) employee else it }
                _employees.value = updated
                saveEmployeesInternal(updated)
            }
        }
    }

    private fun saveEmployeesInternal(list: List<Employee>) {
        try {
            val array = JSONArray()
            list.forEach { emp ->
                val obj = JSONObject().apply {
                    put("id", emp.id)
                    put("name", emp.name)
                    put("phone", emp.phone)
                    put("department", emp.department)
                    put("role", emp.role)
                    put("active", emp.active)
                }
                array.put(obj)
            }
            File(context.filesDir, "employees.json").writeText(array.toString())
        } catch (_: Exception) {}
    }

    // --- TEMPLATES ---
    private fun loadTemplatesInternal() {
        val file = File(context.filesDir, "templates.json")
        if (file.exists()) {
            try {
                val json = file.readText()
                val array = JSONArray(json)
                val list = mutableListOf<MessageTemplate>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        MessageTemplate(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            language = obj.getString("language"),
                            content = obj.getString("content"),
                            isDefault = obj.optBoolean("isDefault", false)
                        )
                    )
                }
                _templates.value = list
                return
            } catch (_: Exception) {}
        }

        // Default Templates (Hinglish, Hindi, English)
        val initialTemplates = listOf(
            MessageTemplate(
                id = "tpl_hinglish",
                title = "Hinglish Work Reminder (Recommended)",
                language = "Hinglish",
                content = """
नमस्ते {employee_name} ji 🙏,
{company_name} ki taraf se reminder:

Aapka checklist work *"{task_title}"* abhi tak *PENDING* hai.
⏰ Due Time: {due_time}
📌 Category: {category} ({department})

Remaining Checklist Items ({pending_count}):
{pending_items}

Kripya is kaam ko turant complete karein aur checklist update karein. Agar koi issue ho toh inform karein.
Dhanyawaad!
                """.trimIndent(),
                isDefault = true
            ),
            MessageTemplate(
                id = "tpl_hindi",
                title = "Hindi Official Reminder",
                language = "Hindi",
                content = """
नमस्ते {employee_name} जी,
{company_name} से कार्य रिमाइंडर:

आपकी चेकलिस्ट *"{task_title}"* अभी तक *अपूर्ण (पेंडिंग)* है।
⏰ नियत समय: {due_time}
📌 विभाग: {department}

बाकी कार्य ({pending_count}):
{pending_items}

कृपया इस कार्य को जल्द से जल्द पूरा करें।
धन्यवाद!
                """.trimIndent(),
                isDefault = false
            ),
            MessageTemplate(
                id = "tpl_english",
                title = "English Corporate Reminder",
                language = "English",
                content = """
Hello {employee_name},
Work reminder from {company_name}:

Your assigned checklist *"{task_title}"* is currently *PENDING*.
⏰ Due Time: {due_time}
📌 Category: {category}

Incomplete Items ({pending_count}):
{pending_items}

Please review and complete these items promptly.
Thank you!
                """.trimIndent(),
                isDefault = false
            )
        )
        _templates.value = initialTemplates
        saveTemplatesInternal(initialTemplates)
    }

    fun updateTemplate(template: MessageTemplate) {
        scope.launch {
            mutex.withLock {
                val updated = _templates.value.map {
                    if (it.id == template.id) template else if (template.isDefault) it.copy(isDefault = false) else it
                }
                _templates.value = updated
                saveTemplatesInternal(updated)
            }
        }
    }

    fun setDefaultTemplate(templateId: String) {
        scope.launch {
            mutex.withLock {
                val updated = _templates.value.map {
                    it.copy(isDefault = (it.id == templateId))
                }
                _templates.value = updated
                saveTemplatesInternal(updated)
            }
        }
    }

    private fun saveTemplatesInternal(list: List<MessageTemplate>) {
        try {
            val array = JSONArray()
            list.forEach { t ->
                val obj = JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("language", t.language)
                    put("content", t.content)
                    put("isDefault", t.isDefault)
                }
                array.put(obj)
            }
            File(context.filesDir, "templates.json").writeText(array.toString())
        } catch (_: Exception) {}
    }

    // --- TASKS ---
    private fun loadTasksInternal() {
        val file = File(context.filesDir, "tasks.json")
        if (file.exists()) {
            try {
                val json = file.readText()
                val array = JSONArray(json)
                val list = mutableListOf<ChecklistTask>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val subArray = obj.optJSONArray("subtasks") ?: JSONArray()
                    val subtasks = mutableListOf<SubTask>()
                    for (j in 0 until subArray.length()) {
                        val subObj = subArray.getJSONObject(j)
                        subtasks.add(
                            SubTask(
                                id = subObj.getString("id"),
                                title = subObj.getString("title"),
                                isCompleted = subObj.getBoolean("isCompleted")
                            )
                        )
                    }

                    list.add(
                        ChecklistTask(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            description = obj.optString("description", ""),
                            employeeId = obj.getString("employeeId"),
                            employeeName = obj.getString("employeeName"),
                            employeePhone = obj.getString("employeePhone"),
                            department = obj.optString("department", "General"),
                            category = obj.optString("category", "Checklist"),
                            subtasks = subtasks,
                            dueDate = obj.getString("dueDate"),
                            dueTime = obj.getString("dueTime"),
                            status = TaskStatus.valueOf(obj.getString("status")),
                            priority = TaskPriority.valueOf(obj.getString("priority")),
                            lastReminderSentAt = if (obj.has("lastReminderSentAt") && !obj.isNull("lastReminderSentAt")) obj.getLong("lastReminderSentAt") else null,
                            reminderCount = obj.optInt("reminderCount", 0),
                            isOverdue = obj.optBoolean("isOverdue", false),
                            syncedFromApi = obj.optBoolean("syncedFromApi", false)
                        )
                    )
                }
                _tasks.value = list
                return
            } catch (_: Exception) {}
        }

        // Seed Sample Checklists
        val seedTasks = listOf(
            ChecklistTask(
                id = "task_001",
                title = "Morning Store Opening & Safety Checklist",
                description = "Complete opening checks before 09:30 AM customer entry",
                employeeId = "emp_1",
                employeeName = "Rahul Sharma",
                employeePhone = "+91 98765 43210",
                department = "Store Operations",
                category = "Daily Opening",
                dueDate = "Today",
                dueTime = "09:30 AM",
                status = TaskStatus.PENDING,
                priority = TaskPriority.HIGH,
                isOverdue = true,
                syncedFromApi = true,
                subtasks = listOf(
                    SubTask("sub_1", "Main shutter & glass door inspection", true),
                    SubTask("sub_2", "Billing counter cash float check", false),
                    SubTask("sub_3", "AC and Lighting circuit breaker switch on", false),
                    SubTask("sub_4", "Display aisle floor sanitization verify", false)
                )
            ),
            ChecklistTask(
                id = "task_002",
                title = "Kitchen Cold Storage & Hygiene Log",
                description = "Daily morning food safety temperature and sanitization logging",
                employeeId = "emp_2",
                employeeName = "Priya Singh",
                employeePhone = "+91 98111 22334",
                department = "Kitchen & Quality",
                category = "Quality Control",
                dueDate = "Today",
                dueTime = "11:00 AM",
                status = TaskStatus.PENDING,
                priority = TaskPriority.HIGH,
                isOverdue = true,
                syncedFromApi = true,
                subtasks = listOf(
                    SubTask("sub_5", "Deep freezer temperature check (-18°C)", false),
                    SubTask("sub_6", "Chilled prep counter sanitize & wipe", false),
                    SubTask("sub_7", "Raw meat & dairy FIFO sticker audit", false)
                )
            ),
            ChecklistTask(
                id = "task_003",
                title = "Afternoon Customer Order Dispatch",
                description = "Audit outgoing courier shipments and invoice matching",
                employeeId = "emp_3",
                employeeName = "Amit Verma",
                employeePhone = "+91 97222 33445",
                department = "Logistics & Dispatch",
                category = "Dispatch Checklist",
                dueDate = "Today",
                dueTime = "02:00 PM",
                status = TaskStatus.IN_PROGRESS,
                priority = TaskPriority.MEDIUM,
                isOverdue = false,
                syncedFromApi = true,
                subtasks = listOf(
                    SubTask("sub_8", "Manifest barcode scanning for 45 parcels", true),
                    SubTask("sub_9", "Fragile packaging bubble-wrap check", true),
                    SubTask("sub_10", "Delivery agent signature capture", false)
                )
            ),
            ChecklistTask(
                id = "task_004",
                title = "Warehouse Stock Count & Reconciliation",
                description = "Weekly rolling count for high-value SKUs",
                employeeId = "emp_4",
                employeeName = "Neha Patel",
                employeePhone = "+91 99333 44556",
                department = "Warehouse",
                category = "Inventory Audit",
                dueDate = "Today",
                dueTime = "06:00 PM",
                status = TaskStatus.PENDING,
                priority = TaskPriority.MEDIUM,
                isOverdue = false,
                syncedFromApi = true,
                subtasks = listOf(
                    SubTask("sub_11", "Physical count vs ERP match for Bay A", false),
                    SubTask("sub_12", "Damaged goods return documentation", false),
                    SubTask("sub_13", "Update shortage log in software", false)
                )
            ),
            ChecklistTask(
                id = "task_005",
                title = "DG Generator & Fire Extinguisher Check",
                description = "Facilities preventative maintenance checklist",
                employeeId = "emp_5",
                employeeName = "Suresh Kumar",
                employeePhone = "+91 98444 55667",
                department = "Maintenance",
                category = "Safety & Compliance",
                dueDate = "Yesterday",
                dueTime = "05:00 PM",
                status = TaskStatus.COMPLETED,
                priority = TaskPriority.LOW,
                isOverdue = false,
                syncedFromApi = false,
                subtasks = listOf(
                    SubTask("sub_14", "Diesel fuel level gauge check", true),
                    SubTask("sub_15", "Fire extinguisher pin and pressure needle green check", true)
                )
            )
        )
        _tasks.value = seedTasks
        saveTasksInternal(seedTasks)
    }

    fun addTask(task: ChecklistTask) {
        scope.launch {
            mutex.withLock {
                val updated = listOf(task) + _tasks.value
                _tasks.value = updated
                saveTasksInternal(updated)
            }
        }
    }

    fun updateTask(task: ChecklistTask) {
        scope.launch {
            mutex.withLock {
                val updated = _tasks.value.map { if (it.id == task.id) task else it }
                _tasks.value = updated
                saveTasksInternal(updated)
            }
        }
    }

    fun deleteTask(taskId: String) {
        scope.launch {
            mutex.withLock {
                val updated = _tasks.value.filter { it.id != taskId }
                _tasks.value = updated
                saveTasksInternal(updated)
            }
        }
    }

    fun toggleSubtask(taskId: String, subtaskId: String) {
        scope.launch {
            mutex.withLock {
                val updated = _tasks.value.map { task ->
                    if (task.id == taskId) {
                        val newSubtasks = task.subtasks.map { sub ->
                            if (sub.id == subtaskId) sub.copy(isCompleted = !sub.isCompleted) else sub
                        }
                        val allCompleted = newSubtasks.isNotEmpty() && newSubtasks.all { it.isCompleted }
                        val anyCompleted = newSubtasks.any { it.isCompleted }
                        val newStatus = when {
                            allCompleted -> TaskStatus.COMPLETED
                            anyCompleted -> TaskStatus.IN_PROGRESS
                            else -> TaskStatus.PENDING
                        }
                        task.copy(
                            subtasks = newSubtasks,
                            status = newStatus,
                            isOverdue = if (newStatus == TaskStatus.COMPLETED) false else task.isOverdue
                        )
                    } else task
                }
                _tasks.value = updated
                saveTasksInternal(updated)
            }
        }
    }

    fun updateTaskStatus(taskId: String, status: TaskStatus) {
        scope.launch {
            mutex.withLock {
                val updated = _tasks.value.map { task ->
                    if (task.id == taskId) {
                        val newSubtasks = if (status == TaskStatus.COMPLETED) {
                            task.subtasks.map { it.copy(isCompleted = true) }
                        } else task.subtasks
                        task.copy(
                            status = status,
                            subtasks = newSubtasks,
                            isOverdue = if (status == TaskStatus.COMPLETED) false else task.isOverdue
                        )
                    } else task
                }
                _tasks.value = updated
                saveTasksInternal(updated)
            }
        }
    }

    fun recordReminderDispatched(
        taskId: String,
        employeeName: String,
        employeePhone: String,
        message: String,
        status: String = ReminderLog.STATUS_OPENED,
        channel: String = ReminderLog.CHANNEL_DIRECT
    ) {
        scope.launch { recordReminderNow(taskId, employeeName, employeePhone, message, status, channel) }
    }

    /**
     * Logs a reminder. A failed webhook attempt is logged but doesn't count as a
     * reminder, so the next sync tries that task again.
     */
    suspend fun recordReminderNow(
        taskId: String,
        employeeName: String,
        employeePhone: String,
        message: String,
        status: String,
        channel: String
    ) {
        run {
            val now = System.currentTimeMillis()
            val log = ReminderLog(
                id = UUID.randomUUID().toString(),
                employeeName = employeeName,
                employeePhone = employeePhone,
                taskTitle = _tasks.value.firstOrNull { it.id == taskId }?.title ?: "Checklist Reminder",
                messageText = message,
                timestamp = now,
                status = status,
                channel = channel
            )

            mutex.withLock {
                val counts = status != ReminderLog.STATUS_FAILED
                val updatedTasks = _tasks.value.map { task ->
                    if (task.id == taskId && counts) {
                        task.copy(
                            lastReminderSentAt = now,
                            reminderCount = task.reminderCount + 1
                        )
                    } else task
                }
                _tasks.value = updatedTasks
                saveTasksInternal(updatedTasks)

                val updatedLogs = listOf(log) + _reminderLogs.value
                _reminderLogs.value = updatedLogs
                saveReminderLogsInternal(updatedLogs)
            }
        }
    }

    fun mergeSyncedTasks(newTasks: List<ChecklistTask>) {
        scope.launch { mergeSyncedTasksNow(newTasks) }
    }

    suspend fun mergeSyncedTasksNow(newTasks: List<ChecklistTask>) {
        run {
            val now = System.currentTimeMillis()
            mutex.withLock {
                val existingMap = _tasks.value.associateBy { it.id }.toMutableMap()
                for (newTask in newTasks) {
                    val existing = existingMap[newTask.id]
                    if (existing != null) {
                        // Preserve local reminder stats if matching
                        val merged = newTask.copy(
                            lastReminderSentAt = existing.lastReminderSentAt,
                            reminderCount = existing.reminderCount
                        )
                        existingMap[newTask.id] = merged.copy(isOverdue = DueTime.isOverdue(merged, now))
                    } else {
                        existingMap[newTask.id] = newTask.copy(isOverdue = DueTime.isOverdue(newTask, now))
                    }
                }
                val updatedList = existingMap.values.toList().sortedByDescending { it.priority == TaskPriority.HIGH }
                _tasks.value = updatedList
                saveTasksInternal(updatedList)

                // Also discover new employees from synced tasks
                val currentEmployees = _employees.value.toMutableList()
                var employeesChanged = false
                newTasks.forEach { t ->
                    if (currentEmployees.none { it.id == t.employeeId || (it.name.equals(t.employeeName, true) && it.phone == t.employeePhone) }) {
                        currentEmployees.add(
                            Employee(
                                id = t.employeeId.ifBlank { "emp_${UUID.randomUUID()}" },
                                name = t.employeeName,
                                phone = t.employeePhone,
                                department = t.department,
                                role = "Staff / Assignee"
                            )
                        )
                        employeesChanged = true
                    }
                }
                if (employeesChanged) {
                    _employees.value = currentEmployees
                    saveEmployeesInternal(currentEmployees)
                }
            }
        }
    }

    private fun saveTasksInternal(list: List<ChecklistTask>) {
        try {
            val array = JSONArray()
            list.forEach { task ->
                val obj = JSONObject().apply {
                    put("id", task.id)
                    put("title", task.title)
                    put("description", task.description)
                    put("employeeId", task.employeeId)
                    put("employeeName", task.employeeName)
                    put("employeePhone", task.employeePhone)
                    put("department", task.department)
                    put("category", task.category)
                    put("dueDate", task.dueDate)
                    put("dueTime", task.dueTime)
                    put("status", task.status.name)
                    put("priority", task.priority.name)
                    put("lastReminderSentAt", task.lastReminderSentAt ?: JSONObject.NULL)
                    put("reminderCount", task.reminderCount)
                    put("isOverdue", task.isOverdue)
                    put("syncedFromApi", task.syncedFromApi)

                    val subArray = JSONArray()
                    task.subtasks.forEach { sub ->
                        val subObj = JSONObject().apply {
                            put("id", sub.id)
                            put("title", sub.title)
                            put("isCompleted", sub.isCompleted)
                        }
                        subArray.put(subObj)
                    }
                    put("subtasks", subArray)
                }
                array.put(obj)
            }
            File(context.filesDir, "tasks.json").writeText(array.toString())
        } catch (_: Exception) {}
    }

    // --- REMINDER LOGS ---
    private fun loadReminderLogsInternal() {
        val file = File(context.filesDir, "reminder_logs.json")
        if (file.exists()) {
            try {
                val json = file.readText()
                val array = JSONArray(json)
                val list = mutableListOf<ReminderLog>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ReminderLog(
                            id = obj.getString("id"),
                            employeeName = obj.getString("employeeName"),
                            employeePhone = obj.getString("employeePhone"),
                            taskTitle = obj.getString("taskTitle"),
                            messageText = obj.getString("messageText"),
                            timestamp = obj.getLong("timestamp"),
                            status = obj.optString("status", ReminderLog.STATUS_OPENED),
                            channel = obj.optString("channel", "WhatsApp Direct")
                        )
                    )
                }
                _reminderLogs.value = list
                return
            } catch (_: Exception) {}
        }
    }

    fun clearReminderLogs() {
        scope.launch {
            mutex.withLock {
                _reminderLogs.value = emptyList()
                File(context.filesDir, "reminder_logs.json").delete()
            }
        }
    }

    private fun saveReminderLogsInternal(list: List<ReminderLog>) {
        try {
            val array = JSONArray()
            list.take(200).forEach { log ->
                val obj = JSONObject().apply {
                    put("id", log.id)
                    put("employeeName", log.employeeName)
                    put("employeePhone", log.employeePhone)
                    put("taskTitle", log.taskTitle)
                    put("messageText", log.messageText)
                    put("timestamp", log.timestamp)
                    put("status", log.status)
                    put("channel", log.channel)
                }
                array.put(obj)
            }
            File(context.filesDir, "reminder_logs.json").writeText(array.toString())
        } catch (_: Exception) {}
    }

    // --- CONFIG ---
    private fun loadConfigInternal() {
        val file = File(context.filesDir, "config.json")
        if (file.exists()) {
            try {
                val obj = JSONObject(file.readText())
                _webServiceConfig.value = WebServiceConfig(
                    url = obj.optString("url", "https://api.mycompany.com/v1/employee-checklists/pending"),
                    method = obj.optString("method", "GET"),
                    authHeader = obj.optString("authHeader", ""),
                    autoSyncEnabled = obj.optBoolean("autoSyncEnabled", false),
                    autoSyncMinutes = obj.optInt("autoSyncMinutes", 30),
                    lastSyncTimestamp = if (obj.has("lastSyncTimestamp") && !obj.isNull("lastSyncTimestamp")) obj.getLong("lastSyncTimestamp") else null,
                    lastSyncStatus = obj.optString("lastSyncStatus", null),
                    lastSyncCount = obj.optInt("lastSyncCount", 0),
                    companyName = obj.optString("companyName", "Apex Retail & Ops"),
                    whatsappCloudWebhookUrl = obj.optString("whatsappCloudWebhookUrl", ""),
                    whatsappApiKey = obj.optString("whatsappApiKey", ""),
                    autoSendWebhookAlerts = obj.optBoolean("autoSendWebhookAlerts", false),
                    reminderCooldownMinutes = obj.optInt("reminderCooldownMinutes", 120)
                )
                return
            } catch (_: Exception) {}
        }
    }

    fun updateConfig(config: WebServiceConfig) {
        scope.launch { updateConfigNow(config) }
    }

    suspend fun updateConfigNow(config: WebServiceConfig) {
        run {
            mutex.withLock {
                _webServiceConfig.value = config
                try {
                    val obj = JSONObject().apply {
                        put("url", config.url)
                        put("method", config.method)
                        put("authHeader", config.authHeader)
                        put("autoSyncEnabled", config.autoSyncEnabled)
                        put("autoSyncMinutes", config.autoSyncMinutes)
                        put("lastSyncTimestamp", config.lastSyncTimestamp ?: JSONObject.NULL)
                        put("lastSyncStatus", config.lastSyncStatus ?: JSONObject.NULL)
                        put("lastSyncCount", config.lastSyncCount)
                        put("companyName", config.companyName)
                        put("whatsappCloudWebhookUrl", config.whatsappCloudWebhookUrl)
                        put("whatsappApiKey", config.whatsappApiKey)
                        put("autoSendWebhookAlerts", config.autoSendWebhookAlerts)
                        put("reminderCooldownMinutes", config.reminderCooldownMinutes)
                    }
                    File(context.filesDir, "config.json").writeText(obj.toString())
                } catch (_: Exception) {}
            }
        }
    }
}
