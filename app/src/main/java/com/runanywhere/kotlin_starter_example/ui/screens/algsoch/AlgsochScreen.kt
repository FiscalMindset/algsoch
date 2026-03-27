package com.runanywhere.kotlin_starter_example.ui.screens.algsoch

import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.runanywhere.kotlin_starter_example.data.local.ChatSession
import com.runanywhere.kotlin_starter_example.data.models.custom.CustomMode
import com.runanywhere.kotlin_starter_example.data.models.enums.*
import com.runanywhere.kotlin_starter_example.data.store.CustomModeStore
import com.runanywhere.kotlin_starter_example.domain.models.ReasoningStep
import com.runanywhere.kotlin_starter_example.services.ModelService
import com.runanywhere.kotlin_starter_example.services.ToolRegistry
import com.runanywhere.kotlin_starter_example.ui.components.ModelLoaderWidget
import com.runanywhere.kotlin_starter_example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlgsochScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    viewModel: AlgsochViewModel = viewModel()
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showModeSelector by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showCustomModeDialog by remember { mutableStateOf(false) }
    
    // Image selection state
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedImageUri = uri
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    Scaffold(
        topBar = {
            AlgsochTopBar(
                viewModel = viewModel,
                onBackClick = onNavigateBack,
                onHistoryClick = { showHistory = true },
                onAnalyticsClick = { viewModel.showAnalytics() }
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                
                // Model loader
                if (!modelService.isLLMLoaded) {
                    ModelLoaderWidget(
                        modelName = "SmolLM2 360M",
                        isDownloading = modelService.isLLMDownloading,
                        isLoading = modelService.isLLMLoading,
                        isLoaded = modelService.isLLMLoaded,
                        downloadProgress = modelService.llmDownloadProgress,
                        onLoadClick = { modelService.downloadAndLoadLLM() }
                    )
                }
                
                // Messages
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
                ) {
                    items(viewModel.messages) { message ->
                        MessageBubble(
                            message = message,
                            context = context,
                            onFeedback = { feedbackType ->
                                viewModel.provideFeedback(message.id, feedbackType)
                            },
                            onSeeHow = {
                                val userMsg = viewModel.messages
                                    .takeWhile { it.id != message.id }
                                    .lastOrNull { it.isUser }
                                if (userMsg != null) {
                                    viewModel.showReasoningFor(userMsg.text, message.text)
                                }
                            }
                        )
                    }
                    
                    if (viewModel.isGenerating) {
                        item { ThinkingIndicator() }
                    }
                }
                
                // Unified Input Bar
                if (modelService.isLLMLoaded) {
                    UnifiedInputBar(
                        value = inputText,
                        onValueChange = { inputText = it },
                        onSend = {
                            viewModel.sendMessage(inputText, selectedImageUri, modelService.isVLMLoaded)
                            inputText = ""
                            selectedImageUri = null
                            scope.launch {
                                kotlinx.coroutines.delay(100)
                                listState.animateScrollToItem(viewModel.messages.size)
                            }
                        },
                        onCancel = { viewModel.cancelGeneration() },
                        onImageClick = { imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onModeClick = { showModeSelector = true },
                        selectedMode = viewModel.selectedCustomMode?.name ?: viewModel.selectedMode.displayName(),
                        isGenerating = viewModel.isGenerating,
                        selectedImageUri = selectedImageUri,
                        onClearImage = { selectedImageUri = null },
                        isVisionReady = modelService.isVLMLoaded,
                        isVisionLoading = modelService.isVLMLoading || modelService.isVLMDownloading,
                        onLoadVisionModel = { modelService.downloadAndLoadVLM() }
                    )
                }
            }
        }
    }
    
    // Improved Dialogs & Sheets
    if (showHistory) {
        PremiumHistorySheet(
            sessions = viewModel.chatSessions,
            currentSessionPath = viewModel.currentSessionPath,
            onNewSession = { viewModel.startNewSession(); showHistory = false },
            onLoadSession = { viewModel.loadChatSession(it); showHistory = false },
            onDeleteSession = { viewModel.deleteChatSession(it) },
            onDismiss = { showHistory = false }
        )
    }

    if (showModeSelector) {
        PremiumModeSelectorSheet(
            viewModel = viewModel,
            onCreateCustomMode = { showModeSelector = false; showCustomModeDialog = true },
            onDismiss = { showModeSelector = false }
        )
    }

    if (showCustomModeDialog) {
        ModernCustomModeDialog(
            onDismiss = { showCustomModeDialog = false },
            onSave = { mode ->
                CustomModeStore.addMode(mode)
                showCustomModeDialog = false
            }
        )
    }

    if (viewModel.showAnalyticsDialog) {
        PremiumAnalyticsDialog(
            data = viewModel.analyticsData,
            isLoading = viewModel.isLoadingAnalytics,
            onDismiss = { viewModel.dismissAnalyticsDialog() }
        )
    }

    if (viewModel.showReasoningDialog) {
        ReasoningStepsDialog(
            reasoningSteps = viewModel.reasoningSteps,
            onDismiss = { viewModel.dismissReasoningDialog() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlgsochTopBar(
    viewModel: AlgsochViewModel,
    onBackClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onAnalyticsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = AccentBlue.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Smart Chat", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(viewModel.selectedLevel.displayName(), style = MaterialTheme.typography.labelSmall, color = AccentBlue)
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color.White)
            }
        },
        actions = {
            IconButton(onClick = onAnalyticsClick) {
                Icon(Icons.Rounded.Analytics, null, tint = TextMuted)
            }
            IconButton(onClick = onHistoryClick) {
                Icon(Icons.Rounded.History, null, tint = TextMuted)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
    )
}

@Composable
private fun UnifiedInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onImageClick: () -> Unit,
    onModeClick: () -> Unit,
    selectedMode: String,
    isGenerating: Boolean,
    selectedImageUri: Uri?,
    onClearImage: () -> Unit,
    isVisionReady: Boolean,
    isVisionLoading: Boolean,
    onLoadVisionModel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceSecondary)
            .padding(16.dp)
            .navigationBarsPadding()
    ) {
        if (selectedImageUri != null && !isVisionReady) {
            Surface(
                color = AccentBlue.copy(alpha = 0.2f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Vision model not loaded.",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onLoadVisionModel, enabled = !isVisionLoading) {
                        Text(if (isVisionLoading) "Loading..." else "Load Vision Model", color = AccentBlue)
                    }
                }
            }
        }

        // Image preview if selected
        selectedImageUri?.let { uri ->
            Box(modifier = Modifier.padding(bottom = 12.dp)) {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = BackgroundDark
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                IconButton(
                    onClick = onClearImage,
                    modifier = Modifier
                        .size(24.dp)
                        .offset(x = 64.dp, y = (-8).dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mode Selector
            Surface(
                onClick = onModeClick,
                color = AccentBlue.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Tune, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(selectedMode, style = MaterialTheme.typography.labelMedium, color = AccentBlue, fontWeight = FontWeight.Bold)
                }
            }

            // Input Field
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask anything...", color = TextMuted) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = BackgroundDark,
                    unfocusedContainerColor = BackgroundDark,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp),
                maxLines = 4,
                leadingIcon = {
                    IconButton(onClick = onImageClick) {
                        Icon(Icons.Rounded.AddAPhoto, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    }
                }
            )

            // Send / Cancel Button
            FloatingActionButton(
                onClick = {
                    if (isGenerating) {
                        onCancel()
                    } else if ((value.isNotBlank() || selectedImageUri != null) && !isGenerating) {
                        onSend()
                    }
                },
                containerColor = if (isGenerating) Color(0xFFFF5252) else AccentBlue,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isGenerating) {
                    Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(22.dp))
                } else {
                    Icon(Icons.AutoMirrored.Rounded.Send, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = SurfaceSecondary,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = AccentBlue, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Thinking...", color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: com.runanywhere.kotlin_starter_example.ui.screens.algsoch.ChatMessage,
    context: Context,
    onFeedback: (FeedbackType) -> Unit,
    onSeeHow: () -> Unit
) {
    val isUser = message.isUser
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            Column(horizontalAlignment = Alignment.End) {
                message.imageUri?.let { imageUri ->
                    Surface(
                        color = SurfaceSecondary,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                        modifier = Modifier.padding(bottom = if (message.text.isNotBlank()) 8.dp else 0.dp)
                    ) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(180.dp)
                        )
                    }
                }

                if (message.text.isNotBlank()) {
                    Surface(
                        color = AccentBlue,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                    ) {
                        Text(message.text, color = Color.White, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            // AI response
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .border(2.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)),
                colors = CardDefaults.cardColors(containerColor = BackgroundDark),
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    message.structuredResponse?.let { response ->
                        // Combine all response parts
                        val fullContent = buildString {
                            append(response.directAnswer)
                            if (response.quickExplanation.isNotBlank()) {
                                append("\n\n")
                                append(response.quickExplanation)
                            }
                            if (response.deepExplanation?.isNotBlank() == true) {
                                append("\n\n")
                                append(response.deepExplanation)
                            }
                        }
                        SelectionContainer {
                            Text(
                                text = fullContent,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                softWrap = true
                            )
                        }

                        // Metadata display below response
                        if (response.tokensUsed > 0 || response.responseTimeMs > 0) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = AccentBlue.copy(alpha = 0.2f), thickness = 1.dp)
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${response.modelName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentBlue.copy(alpha = 0.7f),
                                    modifier = Modifier.weight(1f)
                                )
                                if (response.tokensUsed > 0) {
                                    Text(
                                        text = "${response.tokensUsed} tokens",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentGreen.copy(alpha = 0.7f)
                                    )
                                }
                                if (response.responseTimeMs > 0) {
                                    Text(
                                        text = "${(response.responseTimeMs / 1000f).let { String.format("%.1f", it) }}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentCyan.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    } ?: run {
                        // For plain text responses
                        SelectionContainer {
                            Text(
                                text = message.text,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                                lineHeight = 1.6.sp
                            )
                        }
                    }
                }
            }

            // Interaction row
            Row(modifier = Modifier.padding(top = 8.dp, start = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { onFeedback(FeedbackType.LIKE) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.ThumbUp, null, tint = if (message.feedbackType == FeedbackType.LIKE) AccentGreen else TextMuted, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        val textToCopy = message.structuredResponse?.toDisplayText() ?: message.text
                        copyToClipboard(context, textToCopy)
                    },
                    modifier = Modifier.size(36.dp),
                    enabled = message.structuredResponse != null || message.text.isNotBlank()
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun TinyMetaChip(label: String) {
    Surface(
        color = BackgroundDark,
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}

private fun formatResponseTime(ms: Long): String {
    if (ms <= 0L) return "0.0s"
    val seconds = ms / 1000.0
    return if (seconds >= 60) {
        String.format("%.1f min", seconds / 60.0)
    } else {
        String.format("%.1f s", seconds)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumHistorySheet(
    sessions: List<ChatSession>,
    currentSessionPath: String?,
    onNewSession: () -> Unit,
    onLoadSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
    ) {
        Column(modifier = Modifier.padding(24.dp).fillMaxHeight(0.8f)) {
            Text("Chat History", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onNewSession,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Start New Session", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sessions) { session ->
                    HistoryItem(session, session.path == currentSessionPath, { onLoadSession(session.path) }, { onDeleteSession(session.path) })
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(session: ChatSession, isActive: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isActive) AccentBlue.copy(alpha = 0.15f) else SurfaceSecondary,
        shape = RoundedCornerShape(14.dp),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, AccentBlue) else null
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ChatBubbleOutline, null, tint = if (isActive) AccentBlue else TextMuted)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(session.name, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${session.messageCount} messages", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, null, tint = Color.Red.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun ModernCustomModeDialog(
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
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = BackgroundDark, unfocusedContainerColor = BackgroundDark)
                )
                TextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("Objective") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = BackgroundDark, unfocusedContainerColor = BackgroundDark)
                )

                Text("Select Capabilities", style = MaterialTheme.typography.labelLarge, color = AccentBlue, modifier = Modifier.padding(top = 8.dp))

                ToolRegistry.getAvailableTools().forEach { tool ->
                    val isSelected = selectedTools.contains(tool.id)
                    Surface(
                        onClick = { selectedTools = if (isSelected) selectedTools - tool.id else selectedTools + tool.id },
                        color = if (isSelected) AccentCyan.copy(alpha = 0.15f) else BackgroundDark,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(tool.icon, null, tint = if (isSelected) AccentCyan else TextMuted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(tool.name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            if (isSelected) Icon(Icons.Rounded.Check, null, tint = AccentCyan)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(CustomMode(name.lowercase().replace(" ","_"), name, desc, "Assist in $name", selectedTools.toList()))
            }, enabled = name.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                Text("Create Assistant")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumModeSelectorSheet(
    viewModel: AlgsochViewModel,
    onCreateCustomMode: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundDark
    ) {
        Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
            Text("Switch Mode", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            // First row of modes
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ResponseMode.DIRECT, ResponseMode.ANSWER, ResponseMode.EXPLAIN).forEach { mode ->
                    val isSelected = viewModel.selectedMode == mode && viewModel.selectedCustomMode == null
                    Surface(
                        onClick = { viewModel.changeMode(mode); onDismiss() },
                        modifier = Modifier.weight(1f),
                        color = if (isSelected) AccentBlue else SurfaceSecondary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(mode.displayName(), modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center, color = if (isSelected) Color.White else TextSecondary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Second row of modes
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ResponseMode.NOTES, ResponseMode.DIRECTION, ResponseMode.CREATIVE, ResponseMode.THEORY).forEach { mode ->
                    val isSelected = viewModel.selectedMode == mode && viewModel.selectedCustomMode == null
                    Surface(
                        onClick = { viewModel.changeMode(mode); onDismiss() },
                        modifier = Modifier.weight(1f),
                        color = if (isSelected) AccentBlue else SurfaceSecondary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(mode.displayName(), modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center, color = if (isSelected) Color.White else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Your AI Assistants", style = MaterialTheme.typography.labelLarge, color = AccentViolet)
            Spacer(Modifier.height(12.dp))

            CustomModeStore.getModes().forEach { mode ->
                AssistantItemInSheet(mode) { viewModel.changeCustomMode(mode); onDismiss() }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onCreateCustomMode,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentViolet)
            ) {
                Icon(Icons.Rounded.Add, null, tint = AccentViolet)
                Spacer(Modifier.width(8.dp))
                Text("Create New Assistant", color = AccentViolet)
            }
        }
    }
}

@Composable
private fun AssistantItemInSheet(mode: CustomMode, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = SurfaceSecondary,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Psychology, null, tint = AccentViolet)
            Spacer(Modifier.width(16.dp))
            Text(mode.name, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumAnalyticsDialog(
    data: Map<String, Any>,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val totalQuestions = (data["totalQuestions"] as? Number)?.toInt() ?: 0
    val totalSessions = ((data["totalSessions"] ?: data["totalConversations"]) as? Number)?.toInt() ?: 0
    val totalMessages = (data["totalMessages"] as? Number)?.toInt() ?: 0
    val totalTokens = (data["totalTokens"] as? Number)?.toInt() ?: 0
    val topicsCovered = (data["topicsCovered"] as? Number)?.toInt() ?: 0
    val timeSpentMinutes = (data["timeSpentMinutes"] as? Number)?.toInt() ?: 0
    val avgResponseTimeMs = (data["avgResponseTime"] as? Number)?.toLong() ?: 0L
    val activeDays = (data["activeDays"] as? Number)?.toInt() ?: 0
    val currentStudyStreak = (data["currentStudyStreak"] as? Number)?.toInt() ?: 0
    val longestStudyStreak = (data["longestStudyStreak"] as? Number)?.toInt() ?: 0
    val questionsThisWeek = (data["questionsThisWeek"] as? Number)?.toInt() ?: 0
    val avgQuestionsPerActiveDay = (data["avgQuestionsPerActiveDay"] as? Number)?.toDouble() ?: 0.0
    val avgMessagesPerSession = (data["avgMessagesPerSession"] as? Number)?.toDouble() ?: 0.0
    val peakStudyWindow = data["peakStudyWindow"]?.toString()?.takeUnless { it.isBlank() || it == "null" } ?: "No data yet"
    val topTopic = data["topTopic"]?.toString()?.takeUnless { it.isBlank() || it == "null" } ?: "General Learning"
    val preferredMode = data["preferredMode"]?.toString()?.takeUnless { it.isBlank() || it == "null" } ?: "Answer"
    val preferredLanguage = data["preferredLanguage"]?.toString()?.takeUnless { it.isBlank() || it == "null" } ?: "English"
    val preferredLevel = data["preferredLevel"]?.toString()?.takeUnless { it.isBlank() || it == "null" } ?: "Smart"
    val topicInsights = (data["topicInsights"] as? List<*>)?.mapNotNull { it as? TopicInsight }.orEmpty()
    val topicsList = (data["topicsList"] as? List<*>)?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.orEmpty()
    val writingStyle = data["writingStyle"] as? Map<*, *>
    val queryStyle = writingStyle?.get("queryStyle")?.toString()?.takeUnless { it.isBlank() || it == "null" } ?: "Balanced"
    val avgQueryLength = (writingStyle?.get("avgQueryLength") as? Number)?.toDouble() ?: 0.0
    val questionsPerSession = if (totalSessions > 0) totalQuestions.toDouble() / totalSessions else 0.0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = AccentBlue.copy(alpha = 0.16f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Analytics, null, tint = AccentBlue, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Learning Analytics",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Track your learning stats - questions asked, topics covered, and time spent learning.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, null, tint = TextMuted)
                }
            }

            Spacer(Modifier.height(20.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentBlue)
                        Spacer(Modifier.height(16.dp))
                        Text("Building your learning dashboard...", color = TextSecondary)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        AccentBlue.copy(alpha = 0.24f),
                                        AccentCyan.copy(alpha = 0.18f),
                                        SurfaceSecondary
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                "Your study pulse",
                                style = MaterialTheme.typography.labelLarge,
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "A clearer view of how much you have been learning and where your effort is going.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                lineHeight = 20.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnalyticsHighlightCard(
                                    label = "Questions Asked",
                                    value = totalQuestions.toString(),
                                    supportingText = if (totalQuestions == 1) "1 prompt across your chats" else "$totalQuestions prompts across your chats",
                                    icon = Icons.Rounded.Forum,
                                    color = AccentBlue,
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsHighlightCard(
                                    label = "Topics Covered",
                                    value = topicsCovered.toString(),
                                    supportingText = if (topicsCovered == 1) "1 learning cluster found" else "$topicsCovered learning clusters found",
                                    icon = Icons.Rounded.Psychology,
                                    color = AccentGreen,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            AnalyticsHighlightCard(
                                label = "Time Spent Learning",
                                value = formatStudyTime(timeSpentMinutes),
                                supportingText = if (timeSpentMinutes > 0) "Most active topic: $topTopic" else "Start chatting to build your timeline",
                                icon = Icons.Rounded.Schedule,
                                color = AccentOrange,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    AnalyticsSectionHeader(
                        title = "Overview",
                        subtitle = "The quick numbers behind your learning activity"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatBox(
                            label = "Sessions",
                            value = totalSessions.toString(),
                            color = AccentCyan,
                            icon = Icons.Rounded.History,
                            modifier = Modifier.weight(1f)
                        )
                        StatBox(
                            label = "Messages",
                            value = totalMessages.toString(),
                            color = AccentBlue,
                            icon = Icons.Rounded.ChatBubbleOutline,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatBox(
                            label = "Tokens Used",
                            value = totalTokens.toString(),
                            color = AccentViolet,
                            icon = Icons.Rounded.Bolt,
                            modifier = Modifier.weight(1f)
                        )
                        StatBox(
                            label = "Avg. Reply",
                            value = formatResponseTime(avgResponseTimeMs),
                            color = AccentGreen,
                            icon = Icons.Rounded.Speed,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    AnalyticsSectionHeader(
                        title = "Study Habits",
                        subtitle = "How regularly and when you tend to learn"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatBox(
                            label = "Current Streak",
                            value = if (currentStudyStreak > 0) "$currentStudyStreak days" else "0 days",
                            color = AccentOrange,
                            icon = Icons.Rounded.Bolt,
                            modifier = Modifier.weight(1f),
                            supportingText = "Consecutive active study days"
                        )
                        StatBox(
                            label = "Longest Streak",
                            value = if (longestStudyStreak > 0) "$longestStudyStreak days" else "0 days",
                            color = AccentPink,
                            icon = Icons.Rounded.History,
                            modifier = Modifier.weight(1f),
                            supportingText = "Best run so far"
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatBox(
                            label = "Active Days",
                            value = activeDays.toString(),
                            color = AccentGreen,
                            icon = Icons.Rounded.CalendarMonth,
                            modifier = Modifier.weight(1f),
                            supportingText = "Days with study activity"
                        )
                        StatBox(
                            label = "This Week",
                            value = questionsThisWeek.toString(),
                            color = AccentBlue,
                            icon = Icons.Rounded.DateRange,
                            modifier = Modifier.weight(1f),
                            supportingText = "Questions asked in the last 7 days"
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatBox(
                            label = "Peak Study Time",
                            value = peakStudyWindow,
                            color = AccentCyan,
                            icon = Icons.Rounded.Schedule,
                            modifier = Modifier.weight(1f),
                            supportingText = "Your busiest study hour"
                        )
                        StatBox(
                            label = "Questions / Active Day",
                            value = String.format("%.1f", avgQuestionsPerActiveDay),
                            color = AccentViolet,
                            icon = Icons.Rounded.Analytics,
                            modifier = Modifier.weight(1f),
                            supportingText = "Average daily intensity"
                        )
                    }

                    AnalyticsSectionHeader(
                        title = "Study Profile",
                        subtitle = "Patterns the app can infer from your questions"
                    )
                    Surface(
                        color = SurfaceSecondary,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatBox(
                                    label = "Learning Style",
                                    value = queryStyle,
                                    color = AccentOrange,
                                    icon = Icons.Rounded.AutoAwesome,
                                    modifier = Modifier.weight(1f),
                                    supportingText = "${avgQueryLength.toInt()} avg chars / question"
                                )
                                StatBox(
                                    label = "Questions / Session",
                                    value = String.format("%.1f", questionsPerSession),
                                    color = AccentPink,
                                    icon = Icons.Rounded.Analytics,
                                    modifier = Modifier.weight(1f),
                                    supportingText = "Average pace"
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatBox(
                                    label = "Messages / Session",
                                    value = String.format("%.1f", avgMessagesPerSession),
                                    color = AccentCyan,
                                    icon = Icons.Rounded.ChatBubbleOutline,
                                    modifier = Modifier.weight(1f),
                                    supportingText = "Conversation depth"
                                )
                                StatBox(
                                    label = "Top Topic",
                                    value = topTopic,
                                    color = AccentGreen,
                                    icon = Icons.Rounded.LocalOffer,
                                    modifier = Modifier.weight(1f),
                                    supportingText = "Most repeated study theme"
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AnalyticsTag(
                                    label = "Mode: $preferredMode",
                                    color = AccentBlue,
                                    icon = Icons.Rounded.Tune,
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsTag(
                                    label = "Language: $preferredLanguage",
                                    color = AccentGreen,
                                    icon = Icons.Rounded.Language,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AnalyticsTag(
                                    label = "Level: $preferredLevel",
                                    color = AccentViolet,
                                    icon = Icons.Rounded.School,
                                    modifier = Modifier.weight(1f)
                                )
                                AnalyticsTag(
                                    label = if (topicsCovered > 0) "Topic map ready" else "Topic map growing",
                                    color = AccentOrange,
                                    icon = Icons.Rounded.Analytics,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    AnalyticsSectionHeader(
                        title = "Topics Covered",
                        subtitle = "Subjects detected from the kinds of questions you ask"
                    )
                    if (topicsList.isEmpty()) {
                        Surface(
                            color = SurfaceSecondary,
                            shape = RoundedCornerShape(18.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                        ) {
                            Text(
                                "Ask a few more study questions and your topic map will start appearing here.",
                                modifier = Modifier.padding(18.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                lineHeight = 20.sp
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            topicInsights.take(6).forEachIndexed { index, topic ->
                                TopicInsightCard(
                                    rank = index + 1,
                                    topic = topic,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (topicInsights.isEmpty()) {
                                topicsList.chunked(2).forEach { topicRow ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        topicRow.forEach { topic ->
                                            AnalyticsTag(
                                                label = topic,
                                                color = AccentCyan,
                                                icon = Icons.Rounded.LocalOffer,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (topicRow.size == 1) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    topicInsights.drop(6).take(2).forEach { topic ->
                                        AnalyticsTag(
                                            label = topic.name,
                                            color = AccentCyan,
                                            icon = Icons.Rounded.LocalOffer,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (topicInsights.drop(6).take(2).size == 1) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Close Dashboard", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AnalyticsHighlightCard(
    label: String,
    value: String,
    supportingText: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.16f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun AnalyticsSectionHeader(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
            }
            Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            supportingText?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun AnalyticsTag(
    label: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TopicInsightCard(
    rank: Int,
    topic: TopicInsight,
    modifier: Modifier = Modifier
) {
    Surface(
        color = SurfaceSecondary,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = AccentBlue.copy(alpha = 0.16f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(rank.toString(), color = AccentBlue, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    topic.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${topic.mentionCount} matching signals",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (topic.matchedKeywords.isNotEmpty()) {
                    Text(
                        "Matched: ${topic.matchedKeywords.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

private fun formatStudyTime(minutes: Int): String {
    if (minutes <= 0) return "0 min"
    if (minutes < 60) return "$minutes min"

    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (remainingMinutes == 0) {
        "$hours h"
    } else {
        "$hours h $remainingMinutes min"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReasoningStepsDialog(
    reasoningSteps: List<ReasoningStep>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
    ) {
        Column(modifier = Modifier.padding(24.dp).navigationBarsPadding().fillMaxHeight(0.85f)) {
            Text("How the model reasoned through this", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(reasoningSteps) { step ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = SurfaceSecondary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Step ${step.stepNumber}: ${step.title}", 
                                style = MaterialTheme.typography.labelLarge, 
                                color = AccentBlue, 
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(step.description, 
                                style = MaterialTheme.typography.bodySmall, 
                                color = TextSecondary)
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Got it!")
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Algsoch", text))
}
