package com.runanywhere.kotlin_starter_example.ui.screens.algsoch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.runanywhere.kotlin_starter_example.data.models.custom.CustomMode
import com.runanywhere.kotlin_starter_example.data.store.CustomModeStore
import com.runanywhere.kotlin_starter_example.ui.theme.*

@Composable
fun AlgsochModeSelectionScreen(
    onChatSelected: (String?) -> Unit,
    onVoiceSelected: () -> Unit, // Hidden in UI but kept for Nav compatibility
    onVisionSelected: () -> Unit  // Merged into Chat
) {
    val context = LocalContext.current
    var showCustomModeDialog by remember { mutableStateOf(false) }
    var showCompanionDialog by remember { mutableStateOf(false) }
    var editingCustomMode by remember { mutableStateOf<CustomMode?>(null) }
    var editingCompanionMode by remember { mutableStateOf<CustomMode?>(null) }
    var pendingDeleteMode by remember { mutableStateOf<CustomMode?>(null) }

    LaunchedEffect(Unit) {
        CustomModeStore.initialize(context.applicationContext)
    }

    val customModes = CustomModeStore.getModes()
    val companionModes = customModes.filter { CustomModeStore.isCompanionMode(it) }
    val featuredCompanion = companionModes.lastOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Decorative background glow
        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(x = (-150).dp, y = (-100).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(AccentBlue.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .statusBarsPadding()
        ) {
            // Premium Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = AccentBlue.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = AccentBlue, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Algsoch",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        "Advanced Learning AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                "Modes",
                style = MaterialTheme.typography.labelLarge,
                color = TextMuted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Spacer(Modifier.height(16.dp))

            // Main Merged Mode: Smart Chat (Vision + Text)
            MainActionCard(
                title = "Smart Chat",
                subtitle = "Unified Experience",
                description = "Chat with AI, upload images for analysis, and solve complex problems in one place.",
                icon = Icons.Rounded.ChatBubbleOutline,
                gradient = listOf(AccentBlue, Color(0xFF2563EB)),
                onClick = { onChatSelected(null) }
            )

            Spacer(Modifier.height(18.dp))

            MainActionCard(
                title = featuredCompanion?.name ?: "Create Companion",
                subtitle = featuredCompanion?.let { "Continue Companion" } ?: "Girlfriend, Boyfriend, or Partner",
                description = featuredCompanion?.description
                    ?: "Choose the relationship style, pick the name, and get a warm companion that remembers chats, moods, and photos over time.",
                icon = Icons.Rounded.Favorite,
                gradient = listOf(AccentViolet, Color(0xFFEC4899)),
                onClick = {
                    val savedCompanionId = featuredCompanion?.id
                    if (savedCompanionId != null) {
                        onChatSelected(savedCompanionId)
                    } else {
                        showCompanionDialog = true
                    }
                }
            )

            if (featuredCompanion != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        editingCompanionMode = null
                        showCompanionDialog = true
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentViolet)
                ) {
                    Icon(Icons.Rounded.Add, null, tint = AccentViolet)
                    Spacer(Modifier.width(8.dp))
                    Text("Create Another Companion", color = AccentViolet)
                }
            }

            Spacer(Modifier.height(32.dp))

            // Custom Assistants Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Your Assistants",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                
                IconButton(
                    onClick = {
                        editingCustomMode = null
                        showCustomModeDialog = true
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Rounded.AddCircle, null, tint = AccentViolet)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (customModes.isEmpty()) {
                EmptyAssistantsCard { showCustomModeDialog = true }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(customModes) { mode ->
                        AssistantItem(
                            mode = mode,
                            onEdit = if (!CustomModeStore.isBuiltInMode(mode)) {
                                {
                                    if (CustomModeStore.isCompanionMode(mode)) {
                                        editingCompanionMode = mode
                                        showCompanionDialog = true
                                    } else {
                                        editingCustomMode = mode
                                        showCustomModeDialog = true
                                    }
                                }
                            } else {
                                null
                            },
                            onDelete = if (!CustomModeStore.isBuiltInMode(mode)) {
                                { pendingDeleteMode = mode }
                            } else {
                                null
                            },
                            onClick = { onChatSelected(mode.id) }
                        )
                    }
                }
            }
        }
    }

    if (showCustomModeDialog) {
        CustomAssistantDialog(
            existingMode = editingCustomMode,
            onDismiss = {
                showCustomModeDialog = false
                editingCustomMode = null
            },
            onSave = { mode ->
                showCustomModeDialog = false
                editingCustomMode = null
                onChatSelected(mode.id)
            },
            onDelete = { mode ->
                pendingDeleteMode = mode
                showCustomModeDialog = false
                editingCustomMode = null
            }
        )
    }

    if (showCompanionDialog) {
        CompanionSetupDialog(
            existingMode = editingCompanionMode,
            onDismiss = {
                showCompanionDialog = false
                editingCompanionMode = null
            },
            onSave = { mode ->
                showCompanionDialog = false
                editingCompanionMode = null
                onChatSelected(mode.id)
            },
            onDelete = { mode ->
                pendingDeleteMode = mode
                showCompanionDialog = false
                editingCompanionMode = null
            }
        )
    }

    pendingDeleteMode?.let { mode ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMode = null },
            containerColor = SurfaceSecondary,
            title = {
                Text(
                    if (CustomModeStore.isCompanionMode(mode)) "Delete Companion?" else "Delete Assistant?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "This will remove ${mode.name} from your saved assistants.",
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        CustomModeStore.removeMode(mode.id)
                        pendingDeleteMode = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMode = null }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun MainActionCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceSecondary),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Gradient accent
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(Brush.verticalGradient(gradient))
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = gradient[0].copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = gradient[0], modifier = Modifier.size(26.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = gradient[0], fontWeight = FontWeight.Bold)
                        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = TextMuted)
                }
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun AssistantItem(
    mode: CustomMode,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val isCompanion = CustomModeStore.isCompanionMode(mode)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceSecondary.copy(alpha = 0.5f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background((if (isCompanion) AccentBlue else AccentViolet).copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isCompanion) Icons.Rounded.Favorite else Icons.Rounded.Psychology,
                    null,
                    tint = if (isCompanion) AccentBlue else AccentViolet,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(mode.name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text(mode.description, style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1)
            }
            
            if (isCompanion) {
                Surface(
                    color = AccentBlue.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "Companion",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (mode.enabledTools.isNotEmpty()) {
                Surface(
                    color = AccentCyan.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "${mode.enabledTools.size} Tools",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            onEdit?.let { editAction ->
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = editAction, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Edit, null, tint = AccentBlue)
                }
            }
            onDelete?.let { deleteAction ->
                IconButton(onClick = deleteAction, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun EmptyAssistantsCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceSecondary.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.AddCircleOutline, null, tint = TextMuted, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text("Create a custom AI Assistant", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}
