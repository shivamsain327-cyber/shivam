package com.example.util

import com.example.model.ChecklistTask
import com.example.model.TaskStatus
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Works out when a checklist is due and whether it is overdue, from the
 * free-text dueDate / dueTime values the web service sends.
 */
object DueTime {

    private val dateFormats = listOf("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "dd MMM yyyy", "d MMM yyyy")
    private val timeFormats = listOf("hh:mm a", "h:mm a", "HH:mm", "H:mm")

    /** Due moment in epoch millis, or null if the date or time can't be understood. */
    fun dueAtMillis(dueDate: String, dueTime: String, now: Long = System.currentTimeMillis()): Long? {
        val day = parseDay(dueDate.trim(), now) ?: return null
        val time = parseTime(dueTime.trim()) ?: return null
        day.set(Calendar.HOUR_OF_DAY, time.first)
        day.set(Calendar.MINUTE, time.second)
        day.set(Calendar.SECOND, 0)
        day.set(Calendar.MILLISECOND, 0)
        return day.timeInMillis
    }

    /**
     * Completed tasks are never overdue. Otherwise the clock decides when the
     * due date/time can be parsed; if not, the server's flag is kept.
     */
    fun isOverdue(task: ChecklistTask, now: Long = System.currentTimeMillis()): Boolean {
        if (task.status == TaskStatus.COMPLETED) return false
        val dueAt = dueAtMillis(task.dueDate, task.dueTime, now) ?: return task.isOverdue
        return now > dueAt
    }

    private fun parseDay(text: String, now: Long): Calendar? {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        when (text.lowercase(Locale.ROOT)) {
            "today", "aaj" -> return cal
            "tomorrow", "kal" -> return cal.apply { add(Calendar.DAY_OF_YEAR, 1) }
            "yesterday" -> return cal.apply { add(Calendar.DAY_OF_YEAR, -1) }
        }
        for (pattern in dateFormats) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = false }
                val date = fmt.parse(text) ?: continue
                return Calendar.getInstance().apply { time = date }
            } catch (_: ParseException) {
            }
        }
        return null
    }

    private fun parseTime(text: String): Pair<Int, Int>? {
        for (pattern in timeFormats) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = false }
                val date = fmt.parse(text.uppercase(Locale.ENGLISH)) ?: continue
                val cal = Calendar.getInstance().apply { time = date }
                return cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
            } catch (_: ParseException) {
            }
        }
        return null
    }
}
