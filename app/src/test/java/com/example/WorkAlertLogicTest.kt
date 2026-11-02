package com.example

import com.example.model.ChecklistTask
import com.example.model.TaskStatus
import com.example.model.WebServiceConfig
import com.example.network.WebServiceManager
import com.example.sync.SyncEngine
import com.example.util.DueTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

// Robolectric provides the real org.json used by the parser.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkAlertLogicTest {

    private fun at(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 2, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun task(
        dueDate: String = "Today",
        dueTime: String = "09:30 AM",
        status: TaskStatus = TaskStatus.PENDING,
        serverOverdue: Boolean = false,
        phone: String = "+91 98765 43210",
        lastReminder: Long? = null
    ) = ChecklistTask(
        id = "t1", title = "Opening checklist", employeeId = "e1", employeeName = "Rahul",
        employeePhone = phone, dueDate = dueDate, dueTime = dueTime, status = status,
        isOverdue = serverOverdue, lastReminderSentAt = lastReminder
    )

    @Test
    fun `today 9_30 AM is overdue at 10 and not at 9`() {
        assertTrue(DueTime.isOverdue(task(), now = at(10, 0)))
        assertFalse(DueTime.isOverdue(task(), now = at(9, 0)))
    }

    @Test
    fun `24 hour times and ISO dates are understood`() {
        assertTrue(DueTime.isOverdue(task(dueDate = "2026-11-01", dueTime = "18:00"), now = at(9, 0)))
        assertFalse(DueTime.isOverdue(task(dueDate = "03/11/2026", dueTime = "18:00"), now = at(9, 0)))
        assertFalse(DueTime.isOverdue(task(dueDate = "Tomorrow", dueTime = "6:00 am"), now = at(23, 0)))
    }

    @Test
    fun `completed tasks are never overdue`() {
        assertFalse(DueTime.isOverdue(task(status = TaskStatus.COMPLETED, serverOverdue = true), now = at(23, 0)))
    }

    @Test
    fun `unparseable due date falls back to the server flag`() {
        assertNull(DueTime.dueAtMillis("End of shift", "soon"))
        assertTrue(DueTime.isOverdue(task(dueDate = "End of shift", serverOverdue = true), now = at(9, 0)))
        assertFalse(DueTime.isOverdue(task(dueDate = "End of shift", serverOverdue = false), now = at(9, 0)))
    }

    @Test
    fun `parser no longer marks every unfinished task overdue`() {
        val json = """[{"id":"a","title":"x","employee_name":"Rahul","status":"PENDING"}]"""
        assertFalse(WebServiceManager.parseJsonToTasks(json).single().isOverdue)
    }

    @Test
    fun `auto alerts respect cooldown, phone and overdue state`() {
        val config = WebServiceConfig(reminderCooldownMinutes = 120)
        val now = at(12, 0)
        val due = task(serverOverdue = true)
        val remindedRecently = task(serverOverdue = true, lastReminder = now - 30 * 60_000L)
        val remindedLongAgo = task(serverOverdue = true, lastReminder = now - 3 * 60 * 60_000L)
        val noPhone = task(serverOverdue = true, phone = "")
        val notOverdue = task(serverOverdue = false)

        assertEquals(1, SyncEngine.tasksDueForAutoAlert(listOf(due), config, now).size)
        assertEquals(0, SyncEngine.tasksDueForAutoAlert(listOf(remindedRecently), config, now).size)
        assertEquals(1, SyncEngine.tasksDueForAutoAlert(listOf(remindedLongAgo), config, now).size)
        assertEquals(0, SyncEngine.tasksDueForAutoAlert(listOf(noPhone, notOverdue), config, now).size)
    }
}
