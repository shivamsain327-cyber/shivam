package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TaskPriority
import com.example.model.TaskStatus
import com.example.ui.components.TaskCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.WorkAlertViewModel

@Composable
fun DashboardScreen(
    viewModel: WorkAlertViewModel,
    tasks: List<com.example.model.ChecklistTask>,
    uiState: com.example.ui.viewmodel.WorkAlertUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val pendingCount = tasks.count { it.status == TaskStatus.PENDING || it.isOverdue }
    val overdueCount = tasks.count { it.isOverdue }
    val completedCount = tasks.count { it.status == TaskStatus.COMPLETED }
    val inProgressCount = tasks.count { it.status == TaskStatus.IN_PROGRESS }

    // Filter tasks based on current filter & search
    val filteredTasks = tasks.filter { task ->
        val matchesFilter = when (uiState.taskFilter) {
            "PENDING" -> task.status == TaskStatus.PENDING || task.isOverdue
            "OVERDUE" -> task.isOverdue
            "COMPLETED" -> task.status == TaskStatus.COMPLETED
            else -> true
        }
        val matchesSearch = if (uiState.searchQuery.isBlank()) true else {
            task.title.contains(uiState.searchQuery, ignoreCase = true) ||
                    task.employeeName.contains(uiState.searchQuery, ignoreCase = true) ||
                    task.department.contains(uiState.searchQuery, ignoreCase = true) ||
                    task.category.contains(uiState.searchQuery, ignoreCase = true)
        }
        matchesFilter && matchesSearch
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sync Notification Banner (if any)
        if (uiState.syncBannerMessage != null) {
            item {
                Surface(
                    color = if (uiState.isSyncSuccess) Color(0xFFE6FFFA) else Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (uiState.isSyncSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (uiState.isSyncSuccess) BrandPrimary else AlertOverdue
                            )
                            Text(
                                text = uiState.syncBannerMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (uiState.isSyncSuccess) BrandPrimaryDark else AlertOverdue
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearSyncBanner() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Top Hero Alert & Stats Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (pendingCount > 0) Color(0xFFFFFBEB) else Color(0xFFF0FDF4)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Pending Work Monitor",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (pendingCount > 0) AlertPending else AlertCompleted
                            )
                            Text(
                                text = if (pendingCount > 0) "$pendingCount checklists require employee attention" else "All checklists completed on time!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // WhatsApp Dispatch Button
                        Button(
                            onClick = { viewModel.startBatchReminder() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WhatsAppGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("auto_send_all_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Send All WhatsApp Alerts",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4 Metric Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            title = "Pending",
                            count = pendingCount,
                            color = AlertPending,
                            bg = AlertPendingContainer,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setTaskFilter("PENDING") }
                        )
                        MetricCard(
                            title = "Overdue",
                            count = overdueCount,
                            color = AlertOverdue,
                            bg = AlertOverdueContainer,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setTaskFilter("OVERDUE") }
                        )
                        MetricCard(
                            title = "In Progress",
                            count = inProgressCount,
                            color = BrandPrimary,
                            bg = Color(0xFFCCFBF1),
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setTaskFilter("ALL") }
                        )
                        MetricCard(
                            title = "Completed",
                            count = completedCount,
                            color = AlertCompleted,
                            bg = AlertCompletedContainer,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setTaskFilter("COMPLETED") }
                        )
                    }
                }
            }
        }

        // Quick Action Bar: Sync Now + Add New Task
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.syncWithWebService() },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("dashboard_sync_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BrandPrimary
                    )
                ) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = BrandPrimary
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Sync Web Service", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Button(
                    onClick = { viewModel.showAddTaskModal(true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("dashboard_add_task_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Naya Checklist", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Search Bar & Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search task, employee, department...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_search_input"),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )

                // Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChipTab(
                        label = "Pending (${pendingCount})",
                        isSelected = uiState.taskFilter == "PENDING",
                        onClick = { viewModel.setTaskFilter("PENDING") }
                    )
                    FilterChipTab(
                        label = "Overdue (${overdueCount})",
                        isSelected = uiState.taskFilter == "OVERDUE",
                        onClick = { viewModel.setTaskFilter("OVERDUE") }
                    )
                    FilterChipTab(
                        label = "All (${tasks.size})",
                        isSelected = uiState.taskFilter == "ALL",
                        onClick = { viewModel.setTaskFilter("ALL") }
                    )
                    FilterChipTab(
                        label = "Done (${completedCount})",
                        isSelected = uiState.taskFilter == "COMPLETED",
                        onClick = { viewModel.setTaskFilter("COMPLETED") }
                    )
                }
            }
        }

        // Section Title
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (uiState.taskFilter) {
                        "PENDING" -> "पेंडिंग चेकलिस्ट्स (Pending Tasks)"
                        "OVERDUE" -> "समय समाप्त चेकलिस्ट्स (Overdue Tasks)"
                        "COMPLETED" -> "पूरे हुए कार्य (Completed Tasks)"
                        else -> "सभी चेकलिस्ट्स (All Checklists)"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${filteredTasks.size} tasks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Empty State
        if (filteredTasks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(BrandPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = null,
                                tint = BrandPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (uiState.taskFilter == "PENDING") "Koi Pending Kaam Nahi Hai!" else "No checklists found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.taskFilter == "PENDING") "Sabhi employees ne apna checklist work complete kar liya hai." else "Try changing search filter or sync fresh data from Web Service.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            // Task Items
            items(filteredTasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onToggleSubtask = { subId -> viewModel.toggleSubtask(task.id, subId) },
                    onSendWhatsApp = { viewModel.sendWhatsAppReminder(context, task) },
                    onMarkCompleted = { viewModel.markTaskCompleted(task.id) },
                    onMarkPending = { viewModel.markTaskPending(task.id) },
                    onDelete = { viewModel.deleteTask(task.id) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    count: Int,
    color: Color,
    bg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = bg,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = color,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FilterChipTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = if (isSelected) BrandPrimary else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
