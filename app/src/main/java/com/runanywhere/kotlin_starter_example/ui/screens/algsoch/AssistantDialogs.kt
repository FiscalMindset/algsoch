package com.runanywhere.kotlin_starter_example.ui.screens.algsoch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.runanywhere.kotlin_starter_example.data.models.custom.CompanionRelationshipType
import com.runanywhere.kotlin_starter_example.data.models.custom.CustomMode
import com.runanywhere.kotlin_starter_example.data.models.enums.ResponseMode
import com.runanywhere.kotlin_starter_example.data.store.CustomModeStore
import com.runanywhere.kotlin_starter_example.services.ToolRegistry
import com.runanywhere.kotlin_starter_example.ui.theme.AccentBlue
import com.runanywhere.kotlin_starter_example.ui.theme.AccentCyan
import com.runanywhere.kotlin_starter_example.ui.theme.AccentViolet
import com.runanywhere.kotlin_starter_example.ui.theme.BackgroundDark
import com.runanywhere.kotlin_starter_example.ui.theme.SurfaceSecondary
import com.runanywhere.kotlin_starter_example.ui.theme.TextMuted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomAssistantDialog(
    existingMode: CustomMode? = null,
    onDismiss: () -> Unit,
    onSave: (CustomMode) -> Unit,
    onDelete: ((CustomMode) -> Unit)? = null
) {
    val context = LocalContext.current
    val existingDraft = remember(existingMode) { CustomModeStore.getAssistantProfileDraft(existingMode) }
    val isEditing = existingDraft != null
    val canDelete = existingMode != null && !CustomModeStore.isBuiltInMode(existingMode) && onDelete != null

    var name by remember(existingDraft?.id) { mutableStateOf(existingDraft?.name.orEmpty()) }
    var objective by remember(existingDraft?.id) { mutableStateOf(existingDraft?.objective.orEmpty()) }
    var toneHint by remember(existingDraft?.id) {
        mutableStateOf(existingDraft?.toneHint ?: "clear, thoughtful, practical, and human")
    }
    var specialInstructions by remember(existingDraft?.id) {
        mutableStateOf(existingDraft?.specialInstructions.orEmpty())
    }
    var preferredResponseMode by remember(existingDraft?.id) {
        mutableStateOf(existingDraft?.preferredResponseMode ?: ResponseMode.EXPLAIN)
    }
    var selectedTools by remember(existingDraft?.id) {
        mutableStateOf(existingDraft?.enabledTools ?: emptySet())
    }

    val previewText = remember(name, objective, toneHint, preferredResponseMode, specialInstructions) {
        buildAssistantPreviewText(
            name = name,
            objective = objective,
            toneHint = toneHint,
            preferredResponseMode = preferredResponseMode,
            specialInstructions = specialInstructions
        )
    }

    AdaptiveProfileDialog(
        title = if (isEditing) "Edit Assistant" else "New Assistant",
        subtitle = if (isEditing) {
            "Refine the goal, tone, and default reply behavior so this assistant feels more specific and consistent."
        } else {
            "Create a custom assistant with its own purpose, voice, reply style, and capabilities."
        },
        accentColor = AccentBlue,
        accentIcon = Icons.Rounded.AutoAwesome,
        confirmLabel = if (isEditing) "Save Changes" else "Create Assistant",
        confirmEnabled = name.isNotBlank() && objective.isNotBlank(),
        onConfirm = {
            onSave(
                CustomModeStore.saveAssistantMode(
                    assistantName = name,
                    objective = objective,
                    toneHint = toneHint,
                    preferredResponseMode = preferredResponseMode,
                    specialInstructions = specialInstructions,
                    enabledTools = selectedTools.toList(),
                    existingModeId = existingDraft?.id,
                    context = context
                )
            )
        },
        onDismiss = onDismiss,
        onDelete = if (canDelete) {
            { existingMode?.let { mode -> onDelete?.invoke(mode) } }
        } else {
            null
        },
        deleteLabel = "Delete Assistant"
    ) { isCompact ->
        DialogSectionCard(
            title = "Identity",
            caption = "Define what this assistant is for and how it should sound.",
            accentColor = AccentBlue
        ) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("Product strategist, Interview coach, Email expert...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = assistantTextFieldColors()
            )

            TextField(
                value = objective,
                onValueChange = { objective = it },
                label = { Text("Objective / specialty") },
                placeholder = { Text("What should this assistant help with best?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(14.dp),
                colors = assistantTextFieldColors()
            )

            TextField(
                value = toneHint,
                onValueChange = { toneHint = it },
                label = { Text("Tone") },
                supportingText = { Text("Example: calm, strategic, sharp, mentor-like, playful") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = assistantTextFieldColors()
            )
        }

        if (isCompact) {
            AssistantPreviewCard(
                previewText = previewText,
                preferredResponseMode = preferredResponseMode
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ReplyStyleSection(
                        preferredResponseMode = preferredResponseMode,
                        onModeSelected = { preferredResponseMode = it }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    AssistantPreviewCard(
                        previewText = previewText,
                        preferredResponseMode = preferredResponseMode
                    )
                }
            }
        }

        if (isCompact) {
            ReplyStyleSection(
                preferredResponseMode = preferredResponseMode,
                onModeSelected = { preferredResponseMode = it }
            )
        }

        DialogSectionCard(
            title = "Special Instructions",
            caption = "Add workflow rules, habits, or boundaries that make this assistant behave more intentionally.",
            accentColor = AccentBlue
        ) {
            TextField(
                value = specialInstructions,
                onValueChange = { specialInstructions = it },
                label = { Text("Special instructions") },
                placeholder = { Text("Optional: writing style, process, domain rules, do/don't behavior...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                shape = RoundedCornerShape(14.dp),
                colors = assistantTextFieldColors()
            )
        }

        DialogSectionCard(
            title = "Capabilities",
            caption = "Select which tools or abilities this assistant can rely on by default.",
            accentColor = AccentBlue
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolRegistry.getAvailableTools().forEach { tool ->
                    val isSelected = selectedTools.contains(tool.id)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedTools = if (isSelected) {
                                selectedTools - tool.id
                            } else {
                                selectedTools + tool.id
                            }
                        },
                        label = { Text(tool.name) },
                        leadingIcon = {
                            Icon(
                                tool.icon,
                                contentDescription = null,
                                tint = if (isSelected) AccentCyan else TextMuted
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan.copy(alpha = 0.18f),
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = AccentCyan
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) AccentCyan.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                            selectedBorderColor = AccentCyan.copy(alpha = 0.35f)
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompanionSetupDialog(
    existingMode: CustomMode? = null,
    onDismiss: () -> Unit,
    onSave: (CustomMode) -> Unit,
    onDelete: ((CustomMode) -> Unit)? = null
) {
    val context = LocalContext.current
    val existingDraft = remember(existingMode) { CustomModeStore.getCompanionProfileDraft(existingMode) }
    var companionName by remember(existingDraft?.id) {
        mutableStateOf(existingDraft?.name.orEmpty())
    }
    var personalityHint by remember(existingDraft?.id) {
        mutableStateOf(existingDraft?.personalityHint ?: "Warm, playful, emotionally attentive")
    }
    var relationshipType by remember(existingDraft?.id) {
        mutableStateOf(existingDraft?.relationshipType ?: CompanionRelationshipType.GIRLFRIEND)
    }
    val isEditing = existingDraft != null
    val canDelete = existingMode != null && onDelete != null

    val previewText = remember(companionName, personalityHint, relationshipType) {
        buildCompanionPreviewText(
            companionName = companionName,
            relationshipType = relationshipType,
            personalityHint = personalityHint
        )
    }

    AdaptiveProfileDialog(
        title = if (isEditing) "Edit Companion" else "Create Companion",
        subtitle = if (isEditing) {
            "Adjust the relationship style, name, and starting vibe while keeping the same evolving bond."
        } else {
            "Set the relationship style and starting personality. The companion can grow more specific and lived-in over time."
        },
        accentColor = AccentViolet,
        accentIcon = Icons.Rounded.Favorite,
        confirmLabel = if (isEditing) "Save Changes" else "Create Companion",
        confirmEnabled = companionName.isNotBlank(),
        onConfirm = {
            onSave(
                CustomModeStore.saveCompanionMode(
                    companionName = companionName,
                    relationshipType = relationshipType,
                    personalityHint = personalityHint,
                    existingModeId = existingDraft?.id,
                    context = context
                )
            )
        },
        onDismiss = onDismiss,
        onDelete = if (canDelete) {
            { existingMode?.let { mode -> onDelete?.invoke(mode) } }
        } else {
            null
        },
        deleteLabel = "Delete Companion"
    ) { isCompact ->
        DialogSectionCard(
            title = "Relationship",
            caption = "Pick the relationship type and starting vibe for how this companion should feel in conversation.",
            accentColor = AccentViolet
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompanionRelationshipType.entries.forEach { type ->
                    val isSelected = relationshipType == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { relationshipType = type },
                        label = { Text(type.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentViolet.copy(alpha = 0.18f),
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) AccentViolet.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                            selectedBorderColor = AccentViolet.copy(alpha = 0.35f)
                        )
                    )
                }
            }
        }

        if (!isCompact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    CompanionIdentitySection(
                        relationshipType = relationshipType,
                        companionName = companionName,
                        onNameChange = { companionName = it },
                        personalityHint = personalityHint,
                        onPersonalityChange = { personalityHint = it }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    CompanionPreviewCard(previewText = previewText)
                }
            }
        } else {
            CompanionIdentitySection(
                relationshipType = relationshipType,
                companionName = companionName,
                onNameChange = { companionName = it },
                personalityHint = personalityHint,
                onPersonalityChange = { personalityHint = it }
            )
            CompanionPreviewCard(previewText = previewText)
        }
    }
}

@Composable
private fun CompanionIdentitySection(
    relationshipType: CompanionRelationshipType,
    companionName: String,
    onNameChange: (String) -> Unit,
    personalityHint: String,
    onPersonalityChange: (String) -> Unit
) {
    DialogSectionCard(
        title = "Identity",
        caption = "Give the companion a name and a starting emotional texture.",
        accentColor = AccentViolet
    ) {
        TextField(
            value = companionName,
            onValueChange = onNameChange,
            label = { Text("${relationshipType.displayName} name") },
            placeholder = { Text("A name that feels personal and natural") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = assistantTextFieldColors()
        )

        TextField(
            value = personalityHint,
            onValueChange = onPersonalityChange,
            label = { Text("Personality vibe") },
            supportingText = { Text("Example: sweet, calm, teasing, poetic, protective, shy") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = RoundedCornerShape(14.dp),
            colors = assistantTextFieldColors()
        )

        Text(
            "This is only the starting vibe. The companion can become more nuanced, emotionally specific, and more recognizable as the relationship keeps going.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplyStyleSection(
    preferredResponseMode: ResponseMode,
    onModeSelected: (ResponseMode) -> Unit
) {
    DialogSectionCard(
        title = "Default Reply Style",
        caption = "This is the style the assistant prefers before the user manually switches modes.",
        accentColor = AccentBlue
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ResponseMode.entries.forEach { mode ->
                val isSelected = preferredResponseMode == mode
                FilterChip(
                    selected = isSelected,
                    onClick = { onModeSelected(mode) },
                    label = { Text(mode.displayName()) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = AccentBlue
                            )
                        }
                    } else {
                        null
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue.copy(alpha = 0.18f),
                        selectedLabelColor = Color.White
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) AccentBlue.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                        selectedBorderColor = AccentBlue.copy(alpha = 0.35f)
                    )
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AccentBlue.copy(alpha = 0.10f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.16f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    preferredResponseMode.displayName(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    preferredResponseMode.description(),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AssistantPreviewCard(
    previewText: String,
    preferredResponseMode: ResponseMode
) {
    DialogSectionCard(
        title = "Preview",
        caption = "A quick read on how this assistant will likely feel once created.",
        accentColor = AccentBlue
    ) {
        Text(
            previewText,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
        Surface(
            color = BackgroundDark,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.14f))
        ) {
            Text(
                text = "Default mode: ${preferredResponseMode.displayName()}",
                color = TextMuted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun CompanionPreviewCard(
    previewText: String
) {
    DialogSectionCard(
        title = "Preview",
        caption = "How this companion may come across at the start of the relationship.",
        accentColor = AccentViolet
    ) {
        Text(
            previewText,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun DialogSectionCard(
    title: String,
    caption: String,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BackgroundDark.copy(alpha = 0.72f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.size(9.dp),
                    shape = CircleShape,
                    color = accentColor
                ) {}
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(
                caption,
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            content()
        }
    }
}

@Composable
private fun assistantTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = BackgroundDark,
    unfocusedContainerColor = BackgroundDark,
    disabledContainerColor = BackgroundDark,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent
)

@Composable
private fun AdaptiveDialogActions(
    isCompact: Boolean,
    confirmLabel: String,
    confirmEnabled: Boolean,
    accentColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    deleteLabel: String
) {
    if (isCompact) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(confirmLabel)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(deleteLabel, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextMuted)
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(deleteLabel, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text(confirmLabel)
            }
        }
    }
}

@Composable
private fun AdaptiveProfileDialog(
    title: String,
    subtitle: String,
    accentColor: Color,
    accentIcon: ImageVector,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    deleteLabel: String,
    content: @Composable ColumnScope.(isCompact: Boolean) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            val isCompact = maxWidth < 600.dp
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isCompact) {
                            Modifier.fillMaxHeight(0.96f)
                        } else {
                            Modifier
                                .width(720.dp)
                                .fillMaxHeight(0.90f)
                        }
                    ),
                shape = RoundedCornerShape(if (isCompact) 24.dp else 30.dp),
                color = SurfaceSecondary,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.16f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isCompact) 16.dp else 22.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = accentColor.copy(alpha = 0.16f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    accentIcon,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = subtitle,
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = accentColor.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.12f))
                    ) {
                        Text(
                            text = if (isCompact) {
                                "Built to fit smaller screens better, keep actions visible, and stay readable while you edit."
                            } else {
                                "This editor adapts its layout so the form stays readable, scrolls cleanly, and keeps the key actions easy to reach."
                            },
                            color = Color.White.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        content(isCompact)
                        Spacer(Modifier.height(4.dp))
                    }

                    Spacer(Modifier.height(12.dp))

                    AdaptiveDialogActions(
                        isCompact = isCompact,
                        confirmLabel = confirmLabel,
                        confirmEnabled = confirmEnabled,
                        accentColor = accentColor,
                        onConfirm = onConfirm,
                        onDismiss = onDismiss,
                        onDelete = onDelete,
                        deleteLabel = deleteLabel
                    )
                }
            }
        }
    }
}

private fun buildAssistantPreviewText(
    name: String,
    objective: String,
    toneHint: String,
    preferredResponseMode: ResponseMode,
    specialInstructions: String
): String {
    val safeName = name.ifBlank { "This assistant" }
    val safeObjective = objective.ifBlank { "general help" }
    val safeTone = toneHint.ifBlank { "clear and human" }
    val instructionTail = if (specialInstructions.isBlank()) {
        ""
    } else {
        " It also follows custom instructions so its replies stay more intentional and less generic."
    }
    return "$safeName focuses on $safeObjective. It speaks in a $safeTone voice and naturally prefers ${preferredResponseMode.displayName()} replies.$instructionTail"
}

private fun buildCompanionPreviewText(
    companionName: String,
    relationshipType: CompanionRelationshipType,
    personalityHint: String
): String {
    val safeName = companionName.ifBlank { "This companion" }
    val safeVibe = personalityHint.ifBlank { "warm and emotionally attentive" }
    return "$safeName starts as your ${relationshipType.displayName.lowercase()} with a $safeVibe vibe. Over time, that personality can become more specific, more familiar, and more shaped by your shared conversations."
}
