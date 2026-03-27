package com.runanywhere.kotlin_starter_example.ui.screens.algsoch

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.runanywhere.kotlin_starter_example.data.models.custom.CompanionRelationshipType
import com.runanywhere.kotlin_starter_example.data.models.custom.CustomMode
import com.runanywhere.kotlin_starter_example.data.store.CustomModeStore
import com.runanywhere.kotlin_starter_example.services.ToolRegistry
import com.runanywhere.kotlin_starter_example.ui.theme.AccentBlue
import com.runanywhere.kotlin_starter_example.ui.theme.AccentCyan
import com.runanywhere.kotlin_starter_example.ui.theme.AccentViolet
import com.runanywhere.kotlin_starter_example.ui.theme.BackgroundDark
import com.runanywhere.kotlin_starter_example.ui.theme.SurfaceSecondary
import com.runanywhere.kotlin_starter_example.ui.theme.TextMuted

@Composable
fun CustomAssistantDialog(
    onDismiss: () -> Unit,
    onSave: (CustomMode) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selectedTools by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceSecondary,
        title = { Text("New Assistant", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = BackgroundDark,
                        unfocusedContainerColor = BackgroundDark
                    )
                )
                TextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Objective") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = BackgroundDark,
                        unfocusedContainerColor = BackgroundDark
                    )
                )

                Text(
                    "Select Capabilities",
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentBlue,
                    modifier = Modifier.padding(top = 8.dp)
                )

                ToolRegistry.getAvailableTools().forEach { tool ->
                    val isSelected = selectedTools.contains(tool.id)
                    Surface(
                        onClick = {
                            selectedTools = if (isSelected) selectedTools - tool.id else selectedTools + tool.id
                        },
                        color = if (isSelected) AccentCyan.copy(alpha = 0.15f) else BackgroundDark,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                tool.icon,
                                contentDescription = null,
                                tint = if (isSelected) AccentCyan else TextMuted
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(tool.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            if (isSelected) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = AccentCyan)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        CustomMode(
                            id = name.lowercase().replace(" ", "_"),
                            name = name,
                            description = desc,
                            basePrompt = "Assist in $name",
                            enabledTools = selectedTools.toList()
                        )
                    )
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Create Assistant")
            }
        }
    )
}

@Composable
fun CompanionSetupDialog(
    onDismiss: () -> Unit,
    onSave: (CustomMode) -> Unit
) {
    var companionName by remember { mutableStateOf("") }
    var personalityHint by remember { mutableStateOf("Warm, playful, emotionally attentive") }
    var relationshipType by remember { mutableStateOf(CompanionRelationshipType.GIRLFRIEND) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = AccentViolet)
                Spacer(Modifier.width(10.dp))
                Text("Create Companion", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Choose the relationship style and name. This companion will remember chats, react warmly to photos, and let the bond grow over time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )

                Text(
                    "Relationship",
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentViolet,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompanionRelationshipType.entries.forEach { type ->
                        FilterChip(
                            modifier = Modifier.fillMaxWidth(),
                            selected = relationshipType == type,
                            onClick = { relationshipType = type },
                            label = { Text(type.displayName) }
                        )
                    }
                }

                TextField(
                    value = companionName,
                    onValueChange = { companionName = it },
                    label = { Text("${relationshipType.displayName} name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = BackgroundDark,
                        unfocusedContainerColor = BackgroundDark
                    )
                )

                TextField(
                    value = personalityHint,
                    onValueChange = { personalityHint = it },
                    label = { Text("Personality vibe") },
                    supportingText = { Text("Example: sweet, calm, teasing, poetic, protective, shy") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = BackgroundDark,
                        unfocusedContainerColor = BackgroundDark
                    )
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    "The companion will feel affectionate and emotionally consistent, while still staying honest that it is an AI companion and not pretending to be a real human.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        CustomModeStore.createCompanionMode(
                            companionName = companionName,
                            relationshipType = relationshipType,
                            personalityHint = personalityHint
                        )
                    )
                },
                enabled = companionName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
            ) {
                Text("Create Companion")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = BackgroundDark)
            ) {
                Text("Cancel")
            }
        }
    )
}
