package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.WebServiceConfig
import com.example.network.WebServiceManager
import com.example.ui.theme.*
import com.example.ui.viewmodel.WorkAlertViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSyncScreen(
    viewModel: WorkAlertViewModel,
    config: WebServiceConfig,
    uiState: com.example.ui.viewmodel.WorkAlertUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var urlInput by remember(config.url) { mutableStateOf(config.url) }
    var methodInput by remember(config.method) { mutableStateOf(config.method) }
    var authInput by remember(config.authHeader) { mutableStateOf(config.authHeader) }
    var companyNameInput by remember(config.companyName) { mutableStateOf(config.companyName) }
    var autoSyncEnabled by remember(config.autoSyncEnabled) { mutableStateOf(config.autoSyncEnabled) }
    var autoSyncInterval by remember(config.autoSyncMinutes) { mutableStateOf(config.autoSyncMinutes) }
    var cloudWebhookUrlInput by remember(config.whatsappCloudWebhookUrl) { mutableStateOf(config.whatsappCloudWebhookUrl) }

    var isMethodDropdownExpanded by remember { mutableStateOf(false) }
    var showCustomJsonDialog by remember { mutableStateOf(false) }
    var customJsonInput by remember { mutableStateOf("") }

    val formattedLastSync = remember(config.lastSyncTimestamp) {
        if (config.lastSyncTimestamp != null) {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(config.lastSyncTimestamp))
        } else {
            "Never synced yet"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("web_sync_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title & Explainer
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(BrandPrimary.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = BrandPrimary
                        )
                    }

                    Column {
                        Text(
                            text = "Web Service API Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Apne Web Service / Server se checklist sync karein",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Aapka server jo checklist provide karta hai, yeh app us API se realtime data fetch karta hai. Agar kisi employ ka checklist pending ya overdue hota hai, toh yahan se turant alert trigger ho jata hai.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }

        // Web Service Config Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "API Endpoint Settings",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandPrimary
                )

                // API URL
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Web Service Endpoint URL *") },
                    placeholder = { Text("https://mycompany.com/api/employee-checklists") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_url_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = BrandPrimary)
                    }
                )

                // HTTP Method & Company Name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = isMethodDropdownExpanded,
                        onExpandedChange = { isMethodDropdownExpanded = !isMethodDropdownExpanded },
                        modifier = Modifier.weight(0.4f)
                    ) {
                        OutlinedTextField(
                            value = methodInput,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Method") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isMethodDropdownExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                            shape = RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = isMethodDropdownExpanded,
                            onDismissRequest = { isMethodDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(text = { Text("GET") }, onClick = { methodInput = "GET"; isMethodDropdownExpanded = false })
                            DropdownMenuItem(text = { Text("POST") }, onClick = { methodInput = "POST"; isMethodDropdownExpanded = false })
                        }
                    }

                    OutlinedTextField(
                        value = companyNameInput,
                        onValueChange = { companyNameInput = it },
                        label = { Text("Company / Store Name") },
                        modifier = Modifier.weight(0.6f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }

                // Auth Header
                OutlinedTextField(
                    value = authInput,
                    onValueChange = { authInput = it },
                    label = { Text("Authorization Header (Optional)") },
                    placeholder = { Text("Bearer eyJhbGci... or API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                )

                // Auto Sync Interval Toggles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Auto Background Sync",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (autoSyncEnabled) "Syncs automatically every $autoSyncInterval min" else "Disabled (Manual Sync only)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = { autoSyncEnabled = it }
                    )
                }

                // Action Buttons: Save & Sync Now
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.updateConfig(
                                config.copy(
                                    url = urlInput.trim(),
                                    method = methodInput,
                                    authHeader = authInput.trim(),
                                    companyName = companyNameInput.trim(),
                                    autoSyncEnabled = autoSyncEnabled,
                                    autoSyncMinutes = autoSyncInterval
                                )
                            )
                            viewModel.syncWithWebService()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sync_now_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !uiState.isSyncing
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Sync, contentDescription = null, tint = Color.White)
                                Text("Sync Web Service Now", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Secondary Actions: Sample Mock Data & Custom JSON
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.syncWithSampleMockData() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Sample Data Test", fontSize = 13.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = { showCustomJsonDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Paste JSON", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Sync Status & Diagnostics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Sync Status & Diagnostics",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Last Synced:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formattedLastSync, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Last Status:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = config.lastSyncStatus ?: "Ready to sync",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (config.lastSyncStatus?.contains("Failed") == true) AlertOverdue else AlertCompleted
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Items Received:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${config.lastSyncCount} checklists", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // JSON Format Guide Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Expected Web Service JSON Format",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(WebServiceManager.getSampleJsonPayload()))
                            Toast.makeText(context, "Sample JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy JSON", tint = BrandPrimary)
                    }
                }

                Text(
                    text = "Aapka web service niche diye gaye format me JSON return karega to app use automatically parse karke employees aur tasks populate kar dega:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = WebServiceManager.getSampleJsonPayload(),
                        color = Color(0xFF38BDF8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Paste Custom JSON Dialog
    if (showCustomJsonDialog) {
        AlertDialog(
            onDismissRequest = { showCustomJsonDialog = false },
            title = { Text("Paste Web Service JSON") },
            text = {
                Column {
                    Text(
                        text = "Aapke API response ka JSON text yahan paste karein aur 'Import' par tap karein:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customJsonInput,
                        onValueChange = { customJsonInput = it },
                        placeholder = { Text("[ { \"title\": \"Task 1\", \"employee_name\": \"Rahul\" ... } ]") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customJsonInput.isNotBlank()) {
                            viewModel.importCustomJson(customJsonInput)
                            showCustomJsonDialog = false
                            customJsonInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
                ) {
                    Text("Import Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomJsonDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
