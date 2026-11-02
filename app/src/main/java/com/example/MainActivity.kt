package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.TaskStatus
import com.example.ui.components.AddEmployeeDialog
import com.example.ui.components.AddTaskDialog
import com.example.ui.components.BatchReminderDialog
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.WorkAlertViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: WorkAlertViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                WorkAlertApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkAlertApp(viewModel: WorkAlertViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val logs by viewModel.reminderLogs.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()

    // Handle toast messages
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // BackHandler: if not on Dashboard, return to Dashboard
    BackHandler(enabled = uiState.selectedTab != 0) {
        viewModel.selectTab(0)
    }

    val pendingCount = tasks.count { it.status == TaskStatus.PENDING || it.isOverdue }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BrandPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = "Logo",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "WorkAlert",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (pendingCount > 0) {
                                    Surface(
                                        color = AlertPending,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "$pendingCount Pending",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "WhatsApp Alert & Web Service Sync",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    // Quick Web Service Sync Button in Top Bar
                    IconButton(
                        onClick = { viewModel.syncWithWebService() },
                        modifier = Modifier.testTag("topbar_sync_btn")
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = BrandPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync Web Service",
                                tint = BrandPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (pendingCount > 0) {
                                    Badge(containerColor = AlertPending) {
                                        Text(pendingCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Checklist, contentDescription = "Checklists")
                        }
                    },
                    label = { Text("Checklists") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandPrimary,
                        selectedTextColor = BrandPrimary,
                        indicatorColor = BrandPrimary.copy(alpha = 0.15f)
                    )
                )

                NavigationBarItem(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = {
                        Icon(imageVector = Icons.Default.CloudSync, contentDescription = "Web Sync")
                    },
                    label = { Text("Web Sync") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandPrimary,
                        selectedTextColor = BrandPrimary,
                        indicatorColor = BrandPrimary.copy(alpha = 0.15f)
                    )
                )

                NavigationBarItem(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "WhatsApp")
                    },
                    label = { Text("WhatsApp") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = WhatsAppGreen,
                        selectedTextColor = WhatsAppGreen,
                        indicatorColor = WhatsAppGreen.copy(alpha = 0.15f)
                    )
                )

                NavigationBarItem(
                    selected = uiState.selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = {
                        Icon(imageVector = Icons.Default.People, contentDescription = "Employees")
                    },
                    label = { Text("Staff") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandPrimary,
                        selectedTextColor = BrandPrimary,
                        indicatorColor = BrandPrimary.copy(alpha = 0.15f)
                    )
                )

                NavigationBarItem(
                    selected = uiState.selectedTab == 4,
                    onClick = { viewModel.selectTab(4) },
                    icon = {
                        Icon(imageVector = Icons.Default.History, contentDescription = "History")
                    },
                    label = { Text("Logs") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandPrimary,
                        selectedTextColor = BrandPrimary,
                        indicatorColor = BrandPrimary.copy(alpha = 0.15f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.selectedTab) {
                0 -> DashboardScreen(
                    viewModel = viewModel,
                    tasks = tasks,
                    uiState = uiState
                )
                1 -> WebSyncScreen(
                    viewModel = viewModel,
                    config = config,
                    uiState = uiState
                )
                2 -> WhatsAppAutomationScreen(
                    viewModel = viewModel,
                    templates = templates,
                    tasks = tasks,
                    employees = employees,
                    companyName = config.companyName
                )
                3 -> EmployeesScreen(
                    viewModel = viewModel,
                    employees = employees,
                    tasks = tasks
                )
                4 -> ReminderHistoryScreen(
                    viewModel = viewModel,
                    logs = logs
                )
            }
        }
    }

    // Batch WhatsApp Reminder Dialog
    if (uiState.isBatchModalVisible) {
        BatchReminderDialog(
            queue = uiState.batchQueue,
            currentIndex = uiState.batchCurrentIndex,
            employees = employees,
            templates = templates,
            companyName = config.companyName,
            onDispatchCurrent = { viewModel.dispatchNextBatchItem(context) },
            onSkipCurrent = { viewModel.skipCurrentBatchItem() },
            onDismiss = { viewModel.dismissBatchModal() }
        )
    }

    // Add Task Dialog
    if (uiState.isAddTaskModalVisible) {
        AddTaskDialog(
            employees = employees,
            onDismiss = { viewModel.showAddTaskModal(false) },
            onSaveTask = { newTask -> viewModel.addNewTask(newTask) }
        )
    }

    // Add Employee Dialog
    if (uiState.isAddEmployeeModalVisible) {
        AddEmployeeDialog(
            onDismiss = { viewModel.showAddEmployeeModal(false) },
            onSaveEmployee = { newEmp -> viewModel.addNewEmployee(newEmp) }
        )
    }
}
