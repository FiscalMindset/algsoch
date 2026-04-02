package com.runanywhere.kotlin_starter_example.ui.screens.algsoch

import android.net.Uri
import android.graphics.Bitmap
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Notes
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.runanywhere.kotlin_starter_example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlgsochScreen(
    onNavigateBack: () -> Unit,
    modelService: ModelService = viewModel(),
    viewModel: AlgsochViewModel = viewModel(),
    initialAssistantId: String? = null
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showModeSelector by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showModelStatus by remember { mutableStateOf(false) }
    var showCustomModeDialog by remember { mutableStateOf(false) }
    var showCompanionDialog by remember { mutableStateOf(false) }
    var showImageSourceSheet by remember { mutableStateOf(false) }
    
    // Image selection state
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedImageUri = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        selectedImageUri = bitmap?.let { saveCapturedBitmapToCache(context, it) } ?: selectedImageUri
    }

    LaunchedEffect(initialAssistantId) {
        viewModel.initialize(context)
        viewModel.applyLaunchSelection(initialAssistantId)
    }

    Scaffold(
        topBar = {
            AlgsochTopBar(
                viewModel = viewModel,
                onBackClick = onNavigateBack,
                onModelStatusClick = { showModelStatus = true },
                onHistoryClick = { showHistory = true },
                onAnalyticsClick = { viewModel.showAnalytics() }
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
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
                                val structuredResponse = message.structuredResponse
                                if (userMsg != null && structuredResponse != null) {
                                    viewModel.showReasoningFor(
                                        userQuery = userMsg.text,
                                        response = structuredResponse,
                                        responseText = structuredResponse.toDisplayText(),
                                        assistantLabel = message.assistantLabel
                                    )
                                }
                            }
                        )
                    }
                    
                    if (viewModel.isGenerating && viewModel.messages.lastOrNull()?.isPending != true) {
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
                        onImageClick = { showImageSourceSheet = true },
                        onModeClick = { showModeSelector = true },
                        selectedMode = viewModel.selectedCustomMode?.name ?: viewModel.selectedMode.displayName(),
                        isGenerating = viewModel.isGenerating,
                        selectedImageUri = selectedImageUri,
                        onClearImage = { selectedImageUri = null },
                        isVisionReady = modelService.isVLMLoaded,
                        isVisionDownloaded = modelService.isVLMDownloaded,
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
            onDeleteAllSessions = { viewModel.deleteAllChatSessions() },
            onDismiss = { showHistory = false }
        )
    }

    if (showModelStatus) {
        ModelStatusSheet(
            modelService = modelService,
            onLoadChatModel = { modelService.downloadAndLoadLLM() },
            onLoadVisionModel = { modelService.downloadAndLoadVLM() },
            onDismiss = { showModelStatus = false }
        )
    }

    if (showModeSelector) {
        PremiumModeSelectorSheet(
            viewModel = viewModel,
            onCreateCustomMode = { showModeSelector = false; showCustomModeDialog = true },
            onCreateCompanion = { showModeSelector = false; showCompanionDialog = true },
            onDismiss = { showModeSelector = false }
        )
    }

    if (showImageSourceSheet) {
        ImageSourceSheet(
            onPickFromGallery = {
                showImageSourceSheet = false
                imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCapturePhoto = {
                showImageSourceSheet = false
                cameraLauncher.launch(null)
            },
            onDismiss = { showImageSourceSheet = false }
        )
    }

    if (showCustomModeDialog) {
        CustomAssistantDialog(
            onDismiss = { showCustomModeDialog = false },
            onSave = { mode ->
                CustomModeStore.addMode(mode)
                showCustomModeDialog = false
                viewModel.changeCustomMode(mode)
            }
        )
    }

    if (showCompanionDialog) {
        CompanionSetupDialog(
            onDismiss = { showCompanionDialog = false },
            onSave = { mode ->
                CustomModeStore.addMode(mode)
                showCompanionDialog = false
                viewModel.changeCustomMode(mode)
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
            sourceDetails = viewModel.sourceDetails,
            reasoningSteps = viewModel.reasoningSteps,
            isLoading = viewModel.isLoadingReasoning,
            onDismiss = { viewModel.dismissReasoningDialog() }
        )
    }
}

@Composable
private fun PrimaryModelStatusPanel(
    modelService: ModelService,
    onLoadChatModel: () -> Unit,
    onLoadVisionModel: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val stacked = maxWidth < 560.dp

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompactModelStatusCard(
                        title = "Chat Model",
                        subtitle = "Text conversations and answers",
                        isLoaded = modelService.isLLMLoaded,
                        isLoading = modelService.isLLMLoading,
                        isDownloading = modelService.isLLMDownloading,
                        isDownloaded = modelService.isLLMDownloaded,
                        downloadProgress = modelService.llmDownloadProgress,
                        accent = AccentBlue,
                        onAction = onLoadChatModel
                    )
                    CompactModelStatusCard(
                        title = "Vision Model",
                        subtitle = "Image understanding and screenshots",
                        isLoaded = modelService.isVLMLoaded,
                        isLoading = modelService.isVLMLoading,
                        isDownloading = modelService.isVLMDownloading,
                        isDownloaded = modelService.isVLMDownloaded,
                        downloadProgress = modelService.vlmDownloadProgress,
                        accent = AccentGreen,
                        onAction = onLoadVisionModel
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CompactModelStatusCard(
                        title = "Chat Model",
                        subtitle = "Text conversations and answers",
                        isLoaded = modelService.isLLMLoaded,
                        isLoading = modelService.isLLMLoading,
                        isDownloading = modelService.isLLMDownloading,
                        isDownloaded = modelService.isLLMDownloaded,
                        downloadProgress = modelService.llmDownloadProgress,
                        accent = AccentBlue,
                        onAction = onLoadChatModel,
                        modifier = Modifier.weight(1f)
                    )
                    CompactModelStatusCard(
                        title = "Vision Model",
                        subtitle = "Image understanding and screenshots",
                        isLoaded = modelService.isVLMLoaded,
                        isLoading = modelService.isVLMLoading,
                        isDownloading = modelService.isVLMDownloading,
                        isDownloaded = modelService.isVLMDownloaded,
                        downloadProgress = modelService.vlmDownloadProgress,
                        accent = AccentGreen,
                        onAction = onLoadVisionModel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            modelService.errorMessage?.let { error ->
                Surface(
                    color = Color(0x33FF6B6B),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = Color(0xFFFFA8A8), modifier = Modifier.size(18.dp))
                        Text(
                            text = error,
                            color = Color(0xFFFFD6D6),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelStatusSheet(
    modelService: ModelService,
    onLoadChatModel: () -> Unit,
    onLoadVisionModel: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .navigationBarsPadding()
        ) {
            Text(
                "Model Status",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Text(
                "Check whether chat and vision models are downloaded, loading, or ready, and load either one when needed.",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            PrimaryModelStatusPanel(
                modelService = modelService,
                onLoadChatModel = onLoadChatModel,
                onLoadVisionModel = onLoadVisionModel
            )
        }
    }
}

@Composable
private fun CompactModelStatusCard(
    title: String,
    subtitle: String,
    isLoaded: Boolean,
    isLoading: Boolean,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Float,
    accent: Color,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceSecondary,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.16f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Surface(
                    color = accent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = modelStatusLabel(isLoaded, isLoading, isDownloading, isDownloaded),
                        color = accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { downloadProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                    color = accent,
                    trackColor = BackgroundDark
                )
            } else if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                    color = accent,
                    trackColor = BackgroundDark
                )
            }

            Text(
                text = modelStatusDescription(isLoaded, isLoading, isDownloading, isDownloaded, downloadProgress),
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )

            if (!isLoaded) {
                OutlinedButton(
                    onClick = onAction,
                    enabled = !isLoading && !isDownloading,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.25f))
                ) {
                    Text(
                        when {
                            isLoading || isDownloading -> "Working..."
                            isDownloaded -> "Load"
                            else -> "Download & Load"
                        },
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun modelStatusLabel(
    isLoaded: Boolean,
    isLoading: Boolean,
    isDownloading: Boolean,
    isDownloaded: Boolean
): String = when {
    isLoaded -> "Ready"
    isLoading -> "Loading"
    isDownloading -> "Downloading"
    isDownloaded -> "Downloaded"
    else -> "Missing"
}

private fun modelStatusDescription(
    isLoaded: Boolean,
    isLoading: Boolean,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Float
): String = when {
    isLoaded -> "Already loaded in this session and ready to use."
    isLoading -> "Preparing the model now. You should not need to tap again."
    isDownloading -> "Downloading ${(downloadProgress * 100).toInt()}% and it will auto-load when finished."
    isDownloaded -> "Already downloaded on this device. Tap load if it did not auto-restore."
    else -> "Not on this device yet. Download once and future visits will auto-load it."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlgsochTopBar(
    viewModel: AlgsochViewModel,
    onBackClick: () -> Unit,
    onModelStatusClick: () -> Unit,
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
                    Text(
                        viewModel.selectedCustomMode?.name ?: "Smart Chat",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        viewModel.selectedCustomMode?.let { "AI Assistant" } ?: viewModel.selectedLevel.displayName(),
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentBlue
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color.White)
            }
        },
        actions = {
            IconButton(onClick = onModelStatusClick) {
                Icon(Icons.Rounded.Memory, null, tint = TextMuted)
            }
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
    isVisionDownloaded: Boolean,
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
                        when {
                            isVisionLoading -> "Vision model is getting ready."
                            isVisionDownloaded -> "Vision model is downloaded but not loaded."
                            else -> "Vision model is not downloaded yet."
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onLoadVisionModel, enabled = !isVisionLoading) {
                        Text(
                            when {
                                isVisionLoading -> "Loading..."
                                isVisionDownloaded -> "Load Vision"
                                else -> "Download & Load"
                            },
                            color = AccentBlue
                        )
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
    val isMissingSavedReply = isMissingSavedReplyText(message.text)
    var previewBlock by remember(message.id) { mutableStateOf<CodeBlock?>(null) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            Column(horizontalAlignment = Alignment.End) {
                BubbleHeader(
                    label = "You",
                    timestamp = formatMessageTimestamp(message.timestamp),
                    isUser = true
                )
                Spacer(Modifier.height(6.dp))

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
            Column(horizontalAlignment = Alignment.Start) {
                BubbleHeader(
                    label = message.assistantLabel?.takeIf { it.isNotBlank() } ?: "Reply",
                    timestamp = formatMessageTimestamp(message.timestamp),
                    isUser = false
                )
                Spacer(Modifier.height(6.dp))

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
                        message.imageUri?.let { imageUri ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = AccentBlue.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = "Analyzed Image",
                                        color = AccentBlue,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                                Surface(
                                    color = SurfaceSecondary,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AsyncImage(
                                        model = imageUri,
                                        contentDescription = "Analyzed image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 180.dp, max = 260.dp)
                                    )
                                }
                            }
                        }

                        message.structuredResponse?.let { response ->
                            val fullContent = response.toDisplayText().ifBlank {
                                if (isMissingSavedReply) {
                                    "Older reply could not be restored from saved history."
                                } else {
                                    message.text.ifBlank { "This reply has no visible text available." }
                                }
                            }
                            AssistantResponseContent(
                                content = fullContent,
                                textColor = if (isMissingSavedReply) TextMuted else Color.White,
                                textStyle = if (isMissingSavedReply) {
                                    MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp)
                                } else {
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp
                                    )
                                },
                                context = context,
                                onPreviewCode = { previewBlock = it }
                            )

                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = AccentBlue.copy(alpha = 0.2f), thickness = 1.dp)
                            Spacer(Modifier.height(10.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = displayModelName(response.modelName),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentBlue.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TinyMetaChip(
                                        label = response.mode.displayName(),
                                        textColor = AccentOrange,
                                        borderColor = AccentOrange.copy(alpha = 0.28f)
                                    )
                                    TinyMetaChip(
                                        label = "Source",
                                        textColor = AccentBlue,
                                        borderColor = AccentBlue.copy(alpha = 0.28f),
                                        onClick = onSeeHow
                                    )
                                    if (response.tokensUsed > 0) {
                                        TinyMetaChip("${response.tokensUsed} tokens")
                                    }
                                    if (response.responseTimeMs > 0) {
                                        TinyMetaChip(formatResponseTime(response.responseTimeMs))
                                    }
                                }
                            }
                        } ?: run {
                            // For plain text responses
                            val plainText = message.text.ifBlank {
                                "Older reply could not be restored from saved history."
                            }
                            val displayText = if (isMissingSavedReply) {
                                "Older reply could not be restored from saved history."
                            } else {
                                plainText
                            }
                            val textColor = if (isMissingSavedReply || message.text.isBlank()) TextMuted else Color.White
                            val textStyle = if (isMissingSavedReply) {
                                MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp)
                            } else {
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp
                                )
                            }

                            if (message.isPending) {
                                AssistantResponseContent(
                                    content = displayText,
                                    textColor = textColor,
                                    textStyle = textStyle,
                                    context = context,
                                    onPreviewCode = { previewBlock = it }
                                )
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = AccentBlue.copy(alpha = 0.2f), thickness = 1.dp)
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    message.assistantLabel?.takeIf { it.isNotBlank() }?.let { assistantName ->
                                        TinyMetaChip(assistantName)
                                    }
                                    TinyMetaChip(message.generationStatus ?: "Generating...")
                                }
                            } else {
                                AssistantResponseContent(
                                    content = displayText,
                                    textColor = textColor,
                                    textStyle = textStyle,
                                    context = context,
                                    onPreviewCode = { previewBlock = it }
                                )
                            }
                        }
                    }
                }
            }

            // Interaction row
            if (!isMissingSavedReply && !message.isPending) {
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

        previewBlock?.let { codeBlock ->
            com.runanywhere.kotlin_starter_example.ui.components.CodePreviewCanvas(
                code = codeBlock.code,
                language = codeBlock.language,
                onDismiss = { previewBlock = null }
            )
        }
    }
}

@Composable
private fun AssistantResponseContent(
    content: String,
    textColor: Color,
    textStyle: TextStyle,
    context: Context,
    onPreviewCode: (CodeBlock) -> Unit
) {
    val segments = remember(content) { parseAssistantContentSegments(content) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is AssistantContentSegment.Prose -> {
                    val prose = segment.text.trim('\n')
                    if (prose.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = buildFormattedAssistantText(prose),
                                color = textColor,
                                style = textStyle,
                                modifier = Modifier.fillMaxWidth(),
                                softWrap = true
                            )
                        }
                    }
                }

                is AssistantContentSegment.Code -> {
                    CodeBlockCard(
                        codeBlock = segment.codeBlock,
                        context = context,
                        onPreviewCode = onPreviewCode
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(
    codeBlock: CodeBlock,
    context: Context,
    onPreviewCode: (CodeBlock) -> Unit
) {
    val isPreviewable = codeBlock.language.lowercase() in listOf("html", "javascript", "js", "css")

    Surface(
        color = Color(0xFF101723),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF131F31), Color(0xFF0E1624))
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = AccentBlue.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = codeBlock.language.ifBlank { "code" }.uppercase(Locale.getDefault()),
                        color = AccentBlue,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (isPreviewable) {
                    TextButton(onClick = { onPreviewCode(codeBlock) }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Run", color = AccentGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = { copyToClipboard(context, codeBlock.code) }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy", color = AccentBlue, fontWeight = FontWeight.SemiBold)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B111A))
                    .horizontalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        codeBlock.code.lines().forEachIndexed { index, line ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = (index + 1).toString(),
                                    color = TextMuted.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(32.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = buildHighlightedCodeLine(line),
                                    color = Color(0xFF9CDCFE),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp
                                    ),
                                    softWrap = false
                                )
                            }
                            if (index < codeBlock.code.lines().lastIndex) {
                                Spacer(Modifier.height(2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed class AssistantContentSegment {
    data class Prose(val text: String) : AssistantContentSegment()
    data class Code(val codeBlock: CodeBlock) : AssistantContentSegment()
}

private fun parseAssistantContentSegments(text: String): List<AssistantContentSegment> {
    val codeBlockPattern = Regex("```(\\w+)?\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
    val segments = mutableListOf<AssistantContentSegment>()
    var currentIndex = 0

    codeBlockPattern.findAll(text).forEach { match ->
        if (match.range.first > currentIndex) {
            segments += AssistantContentSegment.Prose(text.substring(currentIndex, match.range.first))
        }

        val language = match.groupValues[1].ifBlank { "code" }
        val code = match.groupValues[2].trimEnd()
        segments += AssistantContentSegment.Code(CodeBlock(code = code, language = language))
        currentIndex = match.range.last + 1
    }

    if (currentIndex < text.length) {
        segments += AssistantContentSegment.Prose(text.substring(currentIndex))
    }

    return segments
}

private fun buildHighlightedCodeLine(line: String): AnnotatedString = buildAnnotatedString {
    highlightCodeLine(line)
}

@Composable
private fun BubbleHeader(
    label: String,
    timestamp: String,
    isUser: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(0.92f),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isUser) {
            SenderBadge(label = label, isUser = false)
            Spacer(Modifier.width(8.dp))
            Text(
                text = timestamp,
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        } else {
            Text(
                text = timestamp,
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.width(8.dp))
            SenderBadge(label = label, isUser = true)
        }
    }
}

@Composable
private fun SenderBadge(label: String, isUser: Boolean) {
    Surface(
        color = if (isUser) AccentBlue.copy(alpha = 0.16f) else SurfaceSecondary,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            color = if (isUser) AccentBlue else Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun buildFormattedAssistantText(text: String): AnnotatedString = buildAnnotatedString {
    val codeBlockPattern = Regex("```(\\w+)?\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
    val inlineCodePattern = Regex("`([^`\n]+)`")
    val numberedTitlePattern = Regex("""^(\d+\.\s+[^:]{1,80}:)(\s*.*)$""")
    val bulletTitlePattern = Regex("""^(-\s+[^:]{1,80}:)(\s*.*)$""")
    val stepPattern = Regex("""^(Step\s+\d+:)(\s*.*)$""", RegexOption.IGNORE_CASE)
    val sectionPattern = Regex("""^(Tips:|Common Mistakes:|Summary:)(\s*.*)$""", RegexOption.IGNORE_CASE)
    val genericSectionPattern = Regex("""^([A-Z][A-Za-z ]{1,30}:)(\s*.*)$""")
    
    var currentIndex = 0
    val codeBlocks = codeBlockPattern.findAll(text).toList()
    
    // Process text with code blocks
    codeBlocks.forEach { match ->
        // Add text before code block
        if (match.range.first > currentIndex) {
            val beforeText = text.substring(currentIndex, match.range.first)
            processNormalText(beforeText, numberedTitlePattern, bulletTitlePattern, stepPattern, sectionPattern, genericSectionPattern, inlineCodePattern)
        }
        
        // Add code block with IDE-like syntax highlighting
        val language = match.groupValues[1].ifBlank { "code" }
        val code = match.groupValues[2].trimEnd()
        
        append("\n\n")
        
        // Language label
        if (language.isNotBlank()) {
            withStyle(SpanStyle(
                color = Color(0xFF569CD6),  // Blue like VS Code
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                background = Color(0xFF1E1E1E)
            )) {
                append("  $language  ")
            }
            append("\n")
        }
        
        // Code content with syntax highlighting
        withStyle(SpanStyle(
            background = Color(0xFF1E1E1E),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )) {
            val lines = code.lines()
            lines.forEachIndexed { index, line ->
                // Add syntax highlighting based on common patterns
                highlightCodeLine(line)
                if (index < lines.lastIndex) append('\n')
            }
        }
        append("\n")
        
        currentIndex = match.range.last + 1
    }
    
    // Process remaining text after last code block
    if (currentIndex < text.length) {
        val remainingText = text.substring(currentIndex)
        processNormalText(remainingText, numberedTitlePattern, bulletTitlePattern, stepPattern, sectionPattern, genericSectionPattern, inlineCodePattern)
    }
}

private fun AnnotatedString.Builder.highlightCodeLine(line: String) {
    // Keywords (Python, JavaScript, Java, etc.)
    val keywordPattern = Regex("\\b(def|class|return|if|else|elif|for|while|import|from|function|const|let|var|async|await|public|private|void|int|String)\\b")
    // Strings
    val stringPattern = Regex("\"([^\"]*)\"|'([^']*)'")
    // Comments
    val commentPattern = Regex("(#.*$|//.*$)")
    // Function calls
    val functionPattern = Regex("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(")
    // Numbers
    val numberPattern = Regex("\\b\\d+\\.?\\d*\\b")
    
    var currentPos = 0
    val segments = mutableListOf<Pair<IntRange, SpanStyle?>>()
    
    // Find all patterns
    commentPattern.find(line)?.let { segments.add(it.range to SpanStyle(color = Color(0xFF6A9955))) }
    stringPattern.findAll(line).forEach { segments.add(it.range to SpanStyle(color = Color(0xFFCE9178))) }
    keywordPattern.findAll(line).forEach { segments.add(it.range to SpanStyle(color = Color(0xFF569CD6), fontWeight = FontWeight.Bold)) }
    functionPattern.findAll(line).forEach { 
        val nameRange = IntRange(it.groups[1]!!.range.first, it.groups[1]!!.range.last)
        segments.add(nameRange to SpanStyle(color = Color(0xFFDCDCAA))) 
    }
    numberPattern.findAll(line).forEach { segments.add(it.range to SpanStyle(color = Color(0xFFB5CEA8))) }
    
    // Sort segments by position
    val sortedSegments = segments.sortedBy { it.first.first }
    
    // Apply highlighting
    sortedSegments.forEach { (range, style) ->
        if (range.first > currentPos) {
            withStyle(SpanStyle(color = Color(0xFF9CDCFE))) {
                append(line.substring(currentPos, range.first))
            }
        }
        if (style != null) {
            withStyle(style) {
                append(line.substring(range.first, range.last + 1))
            }
        }
        currentPos = range.last + 1
    }
    
    // Append remaining text
    if (currentPos < line.length) {
        withStyle(SpanStyle(color = Color(0xFF9CDCFE))) {
            append(line.substring(currentPos))
        }
    }
}

private fun AnnotatedString.Builder.processNormalText(
    text: String,
    numberedTitlePattern: Regex,
    bulletTitlePattern: Regex,
    stepPattern: Regex,
    sectionPattern: Regex,
    genericSectionPattern: Regex,
    inlineCodePattern: Regex
) {
    val lines = text.lines()
    
    lines.forEachIndexed { index, rawLine ->
        val line = rawLine.trimEnd()
        when {
            line.isBlank() -> Unit
            numberedTitlePattern.matches(line) -> {
                val match = numberedTitlePattern.find(line)!!
                appendBoldSegment(match.groupValues[1])
                processInlineCode(match.groupValues[2], inlineCodePattern)
            }

            bulletTitlePattern.matches(line) -> {
                val match = bulletTitlePattern.find(line)!!
                appendBoldSegment(match.groupValues[1])
                processInlineCode(match.groupValues[2], inlineCodePattern)
            }

            stepPattern.matches(line) -> {
                val match = stepPattern.find(line)!!
                appendBoldSegment(match.groupValues[1])
                processInlineCode(match.groupValues[2], inlineCodePattern)
            }

            sectionPattern.matches(line) -> {
                val match = sectionPattern.find(line)!!
                appendBoldSegment(match.groupValues[1])
                processInlineCode(match.groupValues[2], inlineCodePattern)
            }

            genericSectionPattern.matches(line) -> {
                val match = genericSectionPattern.find(line)!!
                appendBoldSegment(match.groupValues[1])
                processInlineCode(match.groupValues[2], inlineCodePattern)
            }

            else -> processInlineCode(line, inlineCodePattern)
        }

        if (index < lines.lastIndex) append('\n')
    }
}

private fun AnnotatedString.Builder.processInlineCode(text: String, inlineCodePattern: Regex) {
    var currentIndex = 0
    inlineCodePattern.findAll(text).forEach { match ->
        // Add text before inline code
        if (match.range.first > currentIndex) {
            append(text.substring(currentIndex, match.range.first))
        }
        
        // Add inline code with subtle styling (not orange, more subtle)
        withStyle(SpanStyle(
            background = Color(0xFF2A2A2A),
            color = Color(0xFFD4D4D4),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 14.sp
        )) {
            append(" ${match.groupValues[1]} ")
        }
        
        currentIndex = match.range.last + 1
    }
    
    // Add remaining text
    if (currentIndex < text.length) {
        append(text.substring(currentIndex))
    }
}

private fun AnnotatedString.Builder.appendBoldSegment(text: String) {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
        append(text)
    }
}

@Composable
private fun TinyMetaChip(
    label: String,
    textColor: Color = TextMuted,
    borderColor: Color = Color.White.copy(alpha = 0.12f),
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        color = BackgroundDark,
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = label,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
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

private fun isMissingSavedReplyText(text: String): Boolean =
    text.contains("[Older saved reply unavailable]", ignoreCase = true) ||
        text.contains("saved reply has no visible text available", ignoreCase = true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumHistorySheet(
    sessions: List<ChatSession>,
    currentSessionPath: String?,
    onNewSession: () -> Unit,
    onLoadSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onDeleteAllSessions: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDeleteAllWarning by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
    ) {
        Column(modifier = Modifier.padding(24.dp).fillMaxHeight(0.8f)) {
            Text("Chat History", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Resume a real conversation, not just a file name.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HistoryOverviewChip("Sessions", sessions.size.toString(), Modifier.weight(1f))
                HistoryOverviewChip("Questions", sessions.sumOf { it.questionCount }.toString(), Modifier.weight(1f))
                HistoryOverviewChip("Messages", sessions.sumOf { it.messageCount }.toString(), Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onNewSession,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start New Session", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { showDeleteAllWarning = true },
                    modifier = Modifier.height(54.dp),
                    enabled = sessions.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A80)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FF8A80))
                ) {
                    Icon(Icons.Rounded.DeleteSweep, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete All", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))

            if (sessions.isEmpty()) {
                Surface(
                    color = SurfaceSecondary,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.History, null, tint = AccentBlue, modifier = Modifier.size(30.dp))
                        Text("No saved chats yet", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "Once you study here, this tab will show question titles, previews, and progress for each session.",
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(sessions) { session ->
                        HistoryItem(
                            session = session,
                            isActive = session.path == currentSessionPath,
                            onClick = { onLoadSession(session.path) },
                            onDelete = { onDeleteSession(session.path) }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteAllWarning) {
        AlertDialog(
            onDismissRequest = { showDeleteAllWarning = false },
            containerColor = SurfaceSecondary,
            title = {
                Text("Delete all conversations?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will permanently remove every saved chat history item. This action cannot be undone.",
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllWarning = false
                        onDeleteAllSessions()
                    }
                ) {
                    Text("Delete All", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllWarning = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryItem(session: ChatSession, isActive: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (isActive) AccentBlue.copy(alpha = 0.15f) else SurfaceSecondary,
        shape = RoundedCornerShape(14.dp),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, AccentBlue) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    color = if (isActive) AccentBlue.copy(alpha = 0.16f) else BackgroundDark,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Rounded.ChatBubbleOutline,
                        null,
                        tint = if (isActive) AccentBlue else TextMuted,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isActive) {
                            Surface(
                                color = AccentBlue.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    "Active",
                                    color = AccentBlue,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            formatHistoryTimestamp(session.lastModified),
                            color = TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        session.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    HistoryPreviewLine(
                        label = "You",
                        value = session.userPreview.ifBlank { session.title },
                        color = TextMuted
                    )
                    HistoryPreviewLine(
                        label = session.assistantName ?: "Reply",
                        value = session.assistantPreview.ifBlank { session.preview },
                        color = Color.White.copy(alpha = 0.86f)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = Color.Red.copy(alpha = 0.6f))
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryMetaChip("${session.questionCount} ${if (session.questionCount == 1) "question" else "questions"}")
                HistoryMetaChip("${session.messageCount} messages")
                session.modeLabel?.let { modeLabel ->
                    HistoryMetaChip(
                        label = modeLabel,
                        textColor = AccentOrange,
                        borderColor = AccentOrange.copy(alpha = 0.28f)
                    )
                }
                session.modelName?.let { modelName ->
                    HistoryMetaChip(modelName)
                }
                if (session.responseTimeMs > 0) {
                    HistoryMetaChip(formatResponseTime(session.responseTimeMs))
                }
            }
        }
    }
}

@Composable
private fun HistoryPreviewLine(label: String, value: String, color: Color) {
    if (value.isBlank()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            color = AccentBlue,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = value,
            color = color,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HistoryOverviewChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = SurfaceSecondary,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HistoryMetaChip(
    label: String,
    textColor: Color = TextMuted,
    borderColor: Color = Color.White.copy(alpha = 0.08f)
) {
    Surface(
        color = BackgroundDark,
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

private fun formatHistoryTimestamp(timestamp: Long): String =
    SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(timestamp))

private fun formatMessageTimestamp(timestamp: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))

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
    onCreateCompanion: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Switch Mode", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "Choose the response style you want. Bigger cards make switching feel smoother and more touch-friendly on smaller screens.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 20.sp
            )

            listOf(
                ResponseMode.DIRECT,
                ResponseMode.ANSWER,
                ResponseMode.EXPLAIN,
                ResponseMode.CODE,
                ResponseMode.DIRECTION,
                ResponseMode.CREATIVE,
                ResponseMode.THEORY
            ).forEach { mode ->
                ModeOptionCard(
                    mode = mode,
                    isSelected = viewModel.selectedMode == mode && viewModel.selectedCustomMode == null,
                    onClick = {
                        viewModel.changeMode(mode)
                        onDismiss()
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
            Text("Your AI Assistants", style = MaterialTheme.typography.labelLarge, color = AccentViolet)

            FilledTonalButton(
                onClick = onCreateCompanion,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = AccentViolet.copy(alpha = 0.16f),
                    contentColor = AccentViolet
                )
            ) {
                Icon(Icons.Rounded.Favorite, null)
                Spacer(Modifier.width(8.dp))
                Text("Create Companion")
            }

            CustomModeStore.getModes().forEach { mode ->
                AssistantItemInSheet(
                    mode = mode,
                    isSelected = viewModel.selectedCustomMode?.id == mode.id
                ) {
                    viewModel.changeCustomMode(mode)
                    onDismiss()
                }
            }

            OutlinedButton(
                onClick = onCreateCustomMode,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentViolet)
            ) {
                Icon(Icons.Rounded.Add, null, tint = AccentViolet)
                Spacer(Modifier.width(8.dp))
                Text("Create New Assistant", color = AccentViolet)
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AssistantItemInSheet(mode: CustomMode, isSelected: Boolean, onClick: () -> Unit) {
    val isCompanion = CustomModeStore.isCompanionMode(mode)
    Surface(
        onClick = onClick,
        color = if (isSelected) AccentViolet.copy(alpha = 0.14f) else SurfaceSecondary,
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, AccentViolet) else null
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isCompanion) Icons.Rounded.Favorite else Icons.Rounded.Psychology,
                null,
                tint = if (isCompanion) AccentBlue else AccentViolet
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(mode.name, color = Color.White, fontWeight = FontWeight.Bold)
                if (mode.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        mode.description,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                }
            }
            if (isSelected) {
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Rounded.CheckCircle, null, tint = AccentViolet, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageSourceSheet(
    onPickFromGallery: () -> Unit,
    onCapturePhoto: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Add Image",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Choose an image from your gallery or capture one live with the camera.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 20.sp
            )

            ImageSourceOption(
                title = "Choose From Gallery",
                description = "Pick an existing photo or screenshot.",
                icon = Icons.Rounded.PhotoLibrary,
                accent = AccentBlue,
                onClick = onPickFromGallery
            )
            ImageSourceOption(
                title = "Capture Live Photo",
                description = "Open the camera and use a fresh image.",
                icon = Icons.Rounded.PhotoCamera,
                accent = AccentGreen,
                onClick = onCapturePhoto
            )
        }
    }
}

@Composable
private fun ImageSourceOption(
    title: String,
    description: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = SurfaceSecondary,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.16f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )
            }
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
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val isCompactLayout = maxWidth < 430.dp

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
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
                                ResponsiveAnalyticsPair(
                                    compact = isCompactLayout,
                                    first = { modifier ->
                                        AnalyticsHighlightCard(
                                            label = "Questions Asked",
                                            value = totalQuestions.toString(),
                                            supportingText = if (totalQuestions == 1) "1 prompt across your chats" else "$totalQuestions prompts across your chats",
                                            icon = Icons.Rounded.Forum,
                                            color = AccentBlue,
                                            modifier = modifier
                                        )
                                    },
                                    second = { modifier ->
                                        AnalyticsHighlightCard(
                                            label = "Topics Covered",
                                            value = topicsCovered.toString(),
                                            supportingText = if (topicsCovered == 1) "1 learning cluster found" else "$topicsCovered learning clusters found",
                                            icon = Icons.Rounded.Psychology,
                                            color = AccentGreen,
                                            modifier = modifier
                                        )
                                    }
                                )
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
                        ResponsiveAnalyticsPair(
                            compact = isCompactLayout,
                            first = { modifier ->
                                StatBox(
                                    label = "Sessions",
                                    value = totalSessions.toString(),
                                    color = AccentCyan,
                                    icon = Icons.Rounded.History,
                                    modifier = modifier
                                )
                            },
                            second = { modifier ->
                                StatBox(
                                    label = "Messages",
                                    value = totalMessages.toString(),
                                    color = AccentBlue,
                                    icon = Icons.Rounded.ChatBubbleOutline,
                                    modifier = modifier
                                )
                            }
                        )
                        ResponsiveAnalyticsPair(
                            compact = isCompactLayout,
                            first = { modifier ->
                                StatBox(
                                    label = "Tokens Used",
                                    value = totalTokens.toString(),
                                    color = AccentViolet,
                                    icon = Icons.Rounded.Bolt,
                                    modifier = modifier
                                )
                            },
                            second = { modifier ->
                                StatBox(
                                    label = "Avg. Reply",
                                    value = formatResponseTime(avgResponseTimeMs),
                                    color = AccentGreen,
                                    icon = Icons.Rounded.Speed,
                                    modifier = modifier
                                )
                            }
                        )

                        AnalyticsSectionHeader(
                            title = "Study Habits",
                            subtitle = "How regularly and when you tend to learn"
                        )
                        ResponsiveAnalyticsPair(
                            compact = isCompactLayout,
                            first = { modifier ->
                                StatBox(
                                    label = "Current Streak",
                                    value = if (currentStudyStreak > 0) "$currentStudyStreak days" else "0 days",
                                    color = AccentOrange,
                                    icon = Icons.Rounded.Bolt,
                                    modifier = modifier,
                                    supportingText = "Consecutive active study days"
                                )
                            },
                            second = { modifier ->
                                StatBox(
                                    label = "Longest Streak",
                                    value = if (longestStudyStreak > 0) "$longestStudyStreak days" else "0 days",
                                    color = AccentPink,
                                    icon = Icons.Rounded.History,
                                    modifier = modifier,
                                    supportingText = "Best run so far"
                                )
                            }
                        )
                        ResponsiveAnalyticsPair(
                            compact = isCompactLayout,
                            first = { modifier ->
                                StatBox(
                                    label = "Active Days",
                                    value = activeDays.toString(),
                                    color = AccentGreen,
                                    icon = Icons.Rounded.CalendarMonth,
                                    modifier = modifier,
                                    supportingText = "Days with study activity"
                                )
                            },
                            second = { modifier ->
                                StatBox(
                                    label = "This Week",
                                    value = questionsThisWeek.toString(),
                                    color = AccentBlue,
                                    icon = Icons.Rounded.DateRange,
                                    modifier = modifier,
                                    supportingText = "Questions asked in the last 7 days"
                                )
                            }
                        )
                        ResponsiveAnalyticsPair(
                            compact = isCompactLayout,
                            first = { modifier ->
                                StatBox(
                                    label = "Peak Study Time",
                                    value = peakStudyWindow,
                                    color = AccentCyan,
                                    icon = Icons.Rounded.Schedule,
                                    modifier = modifier,
                                    supportingText = "Your busiest study hour"
                                )
                            },
                            second = { modifier ->
                                StatBox(
                                    label = "Questions / Active Day",
                                    value = String.format("%.1f", avgQuestionsPerActiveDay),
                                    color = AccentViolet,
                                    icon = Icons.Rounded.Analytics,
                                    modifier = modifier,
                                    supportingText = "Average daily intensity"
                                )
                            }
                        )

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
                                ResponsiveAnalyticsPair(
                                    compact = isCompactLayout,
                                    first = { modifier ->
                                        StatBox(
                                            label = "Learning Style",
                                            value = queryStyle,
                                            color = AccentOrange,
                                            icon = Icons.Rounded.AutoAwesome,
                                            modifier = modifier,
                                            supportingText = "${avgQueryLength.toInt()} avg chars / question"
                                        )
                                    },
                                    second = { modifier ->
                                        StatBox(
                                            label = "Questions / Session",
                                            value = String.format("%.1f", questionsPerSession),
                                            color = AccentPink,
                                            icon = Icons.Rounded.Analytics,
                                            modifier = modifier,
                                            supportingText = "Average pace"
                                        )
                                    }
                                )
                                ResponsiveAnalyticsPair(
                                    compact = isCompactLayout,
                                    first = { modifier ->
                                        StatBox(
                                            label = "Messages / Session",
                                            value = String.format("%.1f", avgMessagesPerSession),
                                            color = AccentCyan,
                                            icon = Icons.Rounded.ChatBubbleOutline,
                                            modifier = modifier,
                                            supportingText = "Conversation depth"
                                        )
                                    },
                                    second = { modifier ->
                                        StatBox(
                                            label = "Top Topic",
                                            value = topTopic,
                                            color = AccentGreen,
                                            icon = Icons.Rounded.LocalOffer,
                                            modifier = modifier,
                                            supportingText = "Most repeated study theme"
                                        )
                                    }
                                )
                                ResponsiveAnalyticsPair(
                                    compact = isCompactLayout,
                                    first = { modifier ->
                                        AnalyticsTag(
                                            label = "Mode: $preferredMode",
                                            color = AccentBlue,
                                            icon = Icons.Rounded.Tune,
                                            modifier = modifier
                                        )
                                    },
                                    second = { modifier ->
                                        AnalyticsTag(
                                            label = "Language: $preferredLanguage",
                                            color = AccentGreen,
                                            icon = Icons.Rounded.Language,
                                            modifier = modifier
                                        )
                                    }
                                )
                                ResponsiveAnalyticsPair(
                                    compact = isCompactLayout,
                                    first = { modifier ->
                                        AnalyticsTag(
                                            label = "Level: $preferredLevel",
                                            color = AccentViolet,
                                            icon = Icons.Rounded.School,
                                            modifier = modifier
                                        )
                                    },
                                    second = { modifier ->
                                        AnalyticsTag(
                                            label = if (topicsCovered > 0) "Topic map ready" else "Topic map growing",
                                            color = AccentOrange,
                                            icon = Icons.Rounded.Analytics,
                                            modifier = modifier
                                        )
                                    }
                                )
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
                                        ResponsiveAnalyticsPair(
                                            compact = isCompactLayout,
                                            first = { modifier ->
                                                AnalyticsTag(
                                                    label = topicRow.first(),
                                                    color = AccentCyan,
                                                    icon = Icons.Rounded.LocalOffer,
                                                    modifier = modifier
                                                )
                                            },
                                            second = { modifier ->
                                                if (topicRow.size > 1) {
                                                    AnalyticsTag(
                                                        label = topicRow[1],
                                                        color = AccentCyan,
                                                        icon = Icons.Rounded.LocalOffer,
                                                        modifier = modifier
                                                    )
                                                } else {
                                                    Spacer(modifier)
                                                }
                                            }
                                        )
                                    }
                                } else if (topicInsights.drop(6).isNotEmpty()) {
                                    ResponsiveAnalyticsPair(
                                        compact = isCompactLayout,
                                        first = { modifier ->
                                            AnalyticsTag(
                                                label = topicInsights.drop(6).first().name,
                                                color = AccentCyan,
                                                icon = Icons.Rounded.LocalOffer,
                                                modifier = modifier
                                            )
                                        },
                                        second = { modifier ->
                                            topicInsights.drop(7).firstOrNull()?.let { topic ->
                                                AnalyticsTag(
                                                    label = topic.name,
                                                    color = AccentCyan,
                                                    icon = Icons.Rounded.LocalOffer,
                                                    modifier = modifier
                                                )
                                            } ?: Spacer(modifier)
                                        }
                                    )
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
private fun ModeOptionCard(
    mode: ResponseMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) AccentBlue.copy(alpha = 0.18f) else SurfaceSecondary,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) AccentBlue else Color.White.copy(alpha = 0.06f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) AccentBlue.copy(alpha = 0.18f) else BackgroundDark
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = modeIcon(mode),
                        contentDescription = null,
                        tint = if (isSelected) AccentBlue else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    mode.displayName(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    modeDescription(mode),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )
            }
            if (isSelected) {
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Rounded.CheckCircle, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun modeIcon(mode: ResponseMode): ImageVector = when (mode) {
    ResponseMode.DIRECT -> Icons.AutoMirrored.Rounded.Chat
    ResponseMode.ANSWER -> Icons.Rounded.TaskAlt
    ResponseMode.EXPLAIN -> Icons.Rounded.AutoStories
    ResponseMode.CODE -> Icons.Rounded.Code
    ResponseMode.DIRECTION -> Icons.Rounded.Route
    ResponseMode.CREATIVE -> Icons.Rounded.Lightbulb
    ResponseMode.THEORY -> Icons.Rounded.Psychology
}

private fun modeDescription(mode: ResponseMode): String = when (mode) {
    ResponseMode.DIRECT -> "Quick and natural replies for fast back-and-forth conversation."
    ResponseMode.ANSWER -> "Clear answers first, followed by a short explanation and examples."
    ResponseMode.EXPLAIN -> "Teacher-style breakdown with step-by-step understanding."
    ResponseMode.CODE -> "Write clean code, fix errors, and apply proper formatting with syntax highlighting."
    ResponseMode.DIRECTION -> "Guided approach focused on how to solve or attempt the problem."
    ResponseMode.CREATIVE -> "Memorable analogies, stories, and real-world connections."
    ResponseMode.THEORY -> "Deeper conceptual explanation with background and big-picture links."
}

@Composable
private fun ResponsiveAnalyticsPair(
    compact: Boolean,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    if (compact) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
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

private fun displayModelName(modelName: String): String = when {
    modelName.contains("SmolVLM", ignoreCase = true) -> "Vision Model: $modelName"
    modelName.contains("SmolLM2", ignoreCase = true) -> "Language Model: $modelName"
    else -> modelName
}

private fun saveCapturedBitmapToCache(context: Context, bitmap: Bitmap): Uri? {
    return try {
        val outputFile = java.io.File(
            context.cacheDir,
            "algsoch_capture_${System.currentTimeMillis()}.jpg"
        )
        outputFile.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        Uri.fromFile(outputFile)
    } catch (_: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReasoningStepsDialog(
    sourceDetails: AnswerSourceDetails?,
    reasoningSteps: List<ReasoningStep>,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundDark,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
    ) {
        Column(modifier = Modifier.padding(24.dp).navigationBarsPadding().fillMaxHeight(0.85f)) {
            Text("How This Answer Was Generated", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Swipe between overview, attempts, and steps. This sheet now shows the real local attempt trace for the answer.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TinyMetaChip(
                    label = "Overview",
                    textColor = if (pagerState.currentPage == 0) AccentBlue else TextMuted,
                    borderColor = if (pagerState.currentPage == 0) AccentBlue.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.12f),
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } }
                )
                TinyMetaChip(
                    label = "Attempts",
                    textColor = if (pagerState.currentPage == 1) AccentBlue else TextMuted,
                    borderColor = if (pagerState.currentPage == 1) AccentBlue.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.12f),
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } }
                )
                TinyMetaChip(
                    label = "Steps",
                    textColor = if (pagerState.currentPage == 2) AccentBlue else TextMuted,
                    borderColor = if (pagerState.currentPage == 2) AccentBlue.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.12f),
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } }
                )
            }

            Spacer(Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item {
                                sourceDetails?.let { source ->
                                    SourceInfoCard(
                                        title = "Question",
                                        content = source.question,
                                        accent = AccentBlue
                                    )
                                    SourceInfoCard(
                                        title = "Answer Shape",
                                        content = "Mode: ${source.modeLabel}${source.assistantLabel?.let { " · Assistant: $it" }.orEmpty()}",
                                        accent = AccentOrange
                                    )
                                    SourceInfoCard(
                                        title = "Generation Info",
                                        content = buildString {
                                            append("Model: ${displayModelName(source.modelName)}")
                                            if (source.tokensUsed > 0) append("\nTokens: ${source.tokensUsed}")
                                            if (source.promptTokens > 0 || source.responseTokens > 0) {
                                                append("\nPrompt/Response: ${source.promptTokens}/${source.responseTokens}")
                                            }
                                            if (source.responseTimeMs > 0) append("\nTime: ${formatResponseTime(source.responseTimeMs)}")
                                            source.timeToFirstTokenMs?.takeIf { it > 0 }?.let {
                                                append("\nFirst token: ${formatResponseTime(it)}")
                                            }
                                        },
                                        accent = AccentCyan
                                    )
                                    SourceInfoCard(
                                        title = "Final Answer",
                                        content = source.answer,
                                        accent = AccentGreen
                                    )
                                    SourceInfoCard(
                                        title = "Final Selection",
                                        content = source.attempts.firstOrNull { it.wasSelected }?.label
                                            ?.let { "$it was kept as the final visible answer." }
                                            ?: "No attempt trace is available for this reply.",
                                        accent = AccentBlue
                                    )
                                } ?: SourceInfoCard(
                                    title = "Source",
                                    content = "No source data is available for this reply yet.",
                                    accent = AccentBlue
                                )
                            }
                        }
                    }

                    else -> {
                        when (page) {
                            1 -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (sourceDetails?.attempts.isNullOrEmpty()) {
                                        Text(
                                            "No attempt-by-attempt trace is available for this reply.",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    } else {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(sourceDetails?.attempts.orEmpty()) { attempt ->
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = if (attempt.wasSelected) AccentBlue.copy(alpha = 0.12f) else SurfaceSecondary,
                                                    shape = RoundedCornerShape(12.dp),
                                                    border = if (attempt.wasSelected) androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.35f)) else null
                                                ) {
                                                    Column(modifier = Modifier.padding(16.dp)) {
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = attempt.label,
                                                                style = MaterialTheme.typography.labelLarge,
                                                                color = if (attempt.wasSelected) AccentBlue else Color.White,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            if (attempt.wasSelected) {
                                                                TinyMetaChip(
                                                                    label = "Final",
                                                                    textColor = AccentBlue,
                                                                    borderColor = AccentBlue.copy(alpha = 0.28f)
                                                                )
                                                            }
                                                            if (attempt.wasStreamed) {
                                                                TinyMetaChip("Visible")
                                                            }
                                                        }
                                                        attempt.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                                                            Spacer(Modifier.height(8.dp))
                                                            Text(
                                                                text = reason,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = TextSecondary
                                                            )
                                                        }
                                                        Spacer(Modifier.height(10.dp))
                                                        SelectionContainer {
                                                            Text(
                                                                text = attempt.text.ifBlank { "[No visible text captured for this attempt]" },
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = Color.White.copy(alpha = 0.92f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            else -> {
                                when {
                                    isLoading -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = AccentBlue)
                                        }
                                    }

                                    reasoningSteps.isEmpty() -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "No step-by-step source details are available for this reply.",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }

                                    else -> {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(reasoningSteps) { step ->
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = SurfaceSecondary,
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(16.dp)) {
                                                        Text(
                                                            "Step ${step.stepNumber}: ${step.title}",
                                                            style = MaterialTheme.typography.labelLarge,
                                                            color = AccentBlue,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Spacer(Modifier.height(8.dp))
                                                        Text(
                                                            step.description,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = TextSecondary
                                                        )
                                                    }
                                                }
                                            }
                                        }
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
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Got it!")
            }
        }
    }
}

@Composable
private fun SourceInfoCard(
    title: String,
    content: String,
    accent: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceSecondary,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Algsoch", text))
}

// Data class for code block extraction
private data class CodeBlock(
    val code: String,
    val language: String
)

// Helper function to extract first code block from text
private fun extractFirstCodeBlock(text: String): CodeBlock? {
    val codeBlockPattern = Regex("```(\\w+)?\\n?([\\s\\S]*?)```", RegexOption.MULTILINE)
    val match = codeBlockPattern.find(text) ?: return null
    
    val language = match.groupValues[1].ifBlank { "code" }
    val code = match.groupValues[2].trim()
    
    return if (code.isNotBlank()) {
        CodeBlock(code, language)
    } else {
        null
    }
}
