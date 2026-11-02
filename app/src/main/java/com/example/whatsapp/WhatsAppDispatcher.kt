package com.example.whatsapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.model.ChecklistTask
import com.example.model.Employee
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object WhatsAppDispatcher {

    fun sanitizePhoneNumber(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return when {
            digits.length == 10 -> "91$digits" // Default Indian country code prefix
            digits.length == 11 && digits.startsWith("0") -> "91${digits.substring(1)}"
            digits.startsWith("91") && digits.length >= 12 -> digits
            else -> digits
        }
    }

    fun buildReminderMessage(
        template: String,
        task: ChecklistTask,
        employee: Employee?,
        companyName: String
    ): String {
        val empName = employee?.name ?: task.employeeName
        val dept = employee?.department ?: task.department
        val pendingItemsList = task.subtasks
            .filter { !it.isCompleted }
            .joinToString("\n") { "• ${it.title}" }
            .ifEmpty { "• ${task.title}" }

        return template
            .replace("{employee_name}", empName)
            .replace("{task_title}", task.title)
            .replace("{category}", task.category)
            .replace("{department}", dept)
            .replace("{due_time}", "${task.dueDate} at ${task.dueTime}")
            .replace("{pending_count}", task.pendingSubtasksCount.toString())
            .replace("{pending_items}", pendingItemsList)
            .replace("{company_name}", companyName)
    }

    fun openWhatsApp(
        context: Context,
        phoneNumber: String,
        message: String,
        onError: ((String) -> Unit)? = null
    ): Boolean {
        val cleanPhone = sanitizePhoneNumber(phoneNumber)
        if (cleanPhone.isEmpty()) {
            val err = "Invalid phone number: $phoneNumber"
            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            onError?.invoke(err)
            return false
        }

        try {
            val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.name())
            val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage"
            val uri = Uri.parse(url)

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Check if standard WhatsApp or WhatsApp Business is installed
            val pm = context.packageManager
            val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
            var targeted = false

            for (pkg in packages) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    intent.setPackage(pkg)
                    targeted = true
                    break
                } catch (_: Exception) {
                    // Try next package
                }
            }

            if (!targeted) {
                intent.setPackage(null) // Fallback to browser or system intent resolver
            }

            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            val errorMsg = "Could not open WhatsApp: ${e.localizedMessage ?: "Unknown error"}"
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            onError?.invoke(errorMsg)
            return false
        }
    }
}
