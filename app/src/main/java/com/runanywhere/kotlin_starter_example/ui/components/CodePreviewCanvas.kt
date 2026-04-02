package com.runanywhere.kotlin_starter_example.ui.components

import android.webkit.WebView
import android.webkit.WebSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.runanywhere.kotlin_starter_example.ui.theme.*

/**
 * Code Preview Canvas - Interactive WebView for HTML/CSS/JS code execution
 * Similar to ChatGPT Canvas or Claude Artifacts
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodePreviewCanvas(
    code: String,
    language: String,
    onDismiss: () -> Unit,
    onEditCode: ((String) -> Unit)? = null
) {
    var showCodeEditor by remember { mutableStateOf(false) }
    var editableCode by remember { mutableStateOf(code) }
    var isPreviewMode by remember { mutableStateOf(true) }
    val context = LocalContext.current
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            color = BackgroundDark
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Surface(
                    color = SurfaceSecondary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Code,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                "Code Canvas",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = AccentBlue.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    language.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentBlue,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Toggle between code and preview
                            IconButton(onClick = { isPreviewMode = !isPreviewMode }) {
                                Icon(
                                    if (isPreviewMode) Icons.Rounded.Code else Icons.Rounded.Preview,
                                    contentDescription = if (isPreviewMode) "Show Code" else "Show Preview",
                                    tint = AccentBlue
                                )
                            }
                            
                            // Edit button
                            if (onEditCode != null) {
                                IconButton(onClick = { showCodeEditor = true }) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        contentDescription = "Edit Code",
                                        tint = AccentBlue
                                    )
                                }
                            }
                            
                            // Copy button
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) 
                                    as android.content.ClipboardManager
                                clipboard.setPrimaryClip(
                                    android.content.ClipData.newPlainText("Code", editableCode)
                                )
                            }) {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy Code",
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            
                            // Close button
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
                
                HorizontalDivider(color = AccentBlue.copy(alpha = 0.2f))
                
                // Content Area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    if (isPreviewMode && (language.lowercase() in listOf("html", "javascript", "css", "js"))) {
                        // WebView Preview
                        CodeWebViewPreview(
                            code = editableCode,
                            language = language
                        )
                    } else {
                        // Code Display
                        CodeDisplayView(code = editableCode)
                    }
                }
            }
        }
    }
    
    // Code Editor Dialog
    if (showCodeEditor && onEditCode != null) {
        CodeEditorDialog(
            initialCode = editableCode,
            language = language,
            onDismiss = { showCodeEditor = false },
            onSave = { newCode ->
                editableCode = newCode
                onEditCode(newCode)
                showCodeEditor = false
            }
        )
    }
}

@Composable
private fun CodeWebViewPreview(
    code: String,
    language: String
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webView = this
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = false
                    setSupportZoom(false)
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }
                setBackgroundColor(android.graphics.Color.WHITE)
            }
        },
        update = { view ->
            val htmlContent = when (language.lowercase()) {
                "html" -> {
                    // If it's pure HTML, wrap it to ensure proper rendering
                    if (code.contains("<html") || code.contains("<!DOCTYPE")) {
                        code
                    } else {
                        """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body { font-family: system-ui; padding: 16px; margin: 0; }
                            </style>
                        </head>
                        <body>
                            $code
                        </body>
                        </html>
                        """.trimIndent()
                    }
                }
                "javascript", "js" -> {
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body { font-family: system-ui; padding: 16px; margin: 0; }
                            #output { 
                                background: #f5f5f5; 
                                padding: 12px; 
                                border-radius: 8px; 
                                border: 1px solid #ddd;
                                min-height: 50px;
                                white-space: pre-wrap;
                            }
                        </style>
                    </head>
                    <body>
                        <h3>JavaScript Output:</h3>
                        <div id="output"></div>
                        <script>
                            const originalLog = console.log;
                            const outputDiv = document.getElementById('output');
                            console.log = function(...args) {
                                originalLog.apply(console, args);
                                outputDiv.textContent += args.join(' ') + '\\n';
                            };
                            try {
                                $code
                            } catch (error) {
                                outputDiv.textContent = 'Error: ' + error.message;
                                outputDiv.style.color = 'red';
                            }
                        </script>
                    </body>
                    </html>
                    """.trimIndent()
                }
                "css" -> {
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            $code
                        </style>
                    </head>
                    <body>
                        <h1>CSS Demo</h1>
                        <p>This demonstrates your CSS styles.</p>
                        <button>Sample Button</button>
                        <div class="container">
                            <div class="box">Box 1</div>
                            <div class="box">Box 2</div>
                            <div class="box">Box 3</div>
                        </div>
                    </body>
                    </html>
                    """.trimIndent()
                }
                else -> code
            }
            
            view.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun CodeDisplayView(code: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = code,
            color = Color(0xFF9CDCFE),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeEditorDialog(
    initialCode: String,
    language: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var editedCode by remember { mutableStateOf(initialCode) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.8f)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BackgroundDark
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceSecondary)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Edit Code",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, null, tint = Color.White)
                    }
                }
                
                // Code Editor
                TextField(
                    value = editedCode,
                    onValueChange = { editedCode = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF9CDCFE)
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                
                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceSecondary)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(editedCode) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Apply Changes")
                    }
                }
            }
        }
    }
}
