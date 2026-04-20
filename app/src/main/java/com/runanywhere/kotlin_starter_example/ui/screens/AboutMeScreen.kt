package com.runanywhere.kotlin_starter_example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.runanywhere.kotlin_starter_example.R
import com.runanywhere.kotlin_starter_example.ui.theme.AccentBlue
import com.runanywhere.kotlin_starter_example.ui.theme.AccentCyan
import com.runanywhere.kotlin_starter_example.ui.theme.BackgroundDark
import com.runanywhere.kotlin_starter_example.ui.theme.SurfaceSecondary
import com.runanywhere.kotlin_starter_example.ui.theme.TextMuted

private const val PHONE_NUMBER = "+918383848219"
private const val EMAIL_ID = "npdimagine@gmail.com"
private const val GITHUB_PRIMARY = "https://www.github.com/algsoch"
private const val GITHUB_SECONDARY = "https://www.github.com/fiscalmindset"
private const val LINKEDIN = "https://www.linkedin.com/in/algsoch"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutMeScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        BackgroundDark,
                        Color(0xFF0F1629),
                        Color(0xFF161F33)
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("About Me", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    )
                )
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                val isWide = maxWidth >= 700.dp
                val isVeryNarrow = maxWidth < 360.dp

                Column(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceSecondary.copy(alpha = 0.75f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        if (isWide) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProfileAvatar()
                                Spacer(modifier = Modifier.width(24.dp))
                                ProfileText()
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ProfileAvatar()
                                Spacer(modifier = Modifier.height(16.dp))
                                ProfileText()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ContactCard(
                                    onCall = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$PHONE_NUMBER"))
                                        runCatching { context.startActivity(intent) }
                                    },
                                    onEmail = {
                                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL_ID"))
                                        runCatching { context.startActivity(intent) }
                                    },
                                    stackButtons = isVeryNarrow
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                LinksCard(
                                    onPrimaryGithub = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_PRIMARY))
                                        runCatching { context.startActivity(intent) }
                                    },
                                    onSecondaryGithub = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_SECONDARY))
                                        runCatching { context.startActivity(intent) }
                                    },
                                    onLinkedIn = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LINKEDIN))
                                        runCatching { context.startActivity(intent) }
                                    }
                                )
                            }
                        }
                    } else {
                        ContactCard(
                            onCall = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$PHONE_NUMBER"))
                                runCatching { context.startActivity(intent) }
                            },
                            onEmail = {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL_ID"))
                                runCatching { context.startActivity(intent) }
                            },
                            stackButtons = isVeryNarrow
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        LinksCard(
                            onPrimaryGithub = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_PRIMARY))
                                runCatching { context.startActivity(intent) }
                            },
                            onSecondaryGithub = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_SECONDARY))
                                runCatching { context.startActivity(intent) }
                            },
                            onLinkedIn = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LINKEDIN))
                                runCatching { context.startActivity(intent) }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar() {
    androidx.compose.foundation.Image(
        painter = painterResource(id = R.drawable.avatar_algsoch),
        contentDescription = "Vicky Kumar avatar",
        modifier = Modifier
            .size(132.dp)
            .clip(CircleShape)
            .border(2.dp, AccentCyan.copy(alpha = 0.7f), CircleShape),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun ProfileText() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Vicky Kumar",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Developer",
            style = MaterialTheme.typography.bodyMedium,
            color = AccentBlue,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Let us connect. You can call, email, or reach out on GitHub and LinkedIn.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

@Composable
private fun ContactCard(
    onCall: () -> Unit,
    onEmail: () -> Unit,
    stackButtons: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceSecondary.copy(alpha = 0.75f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Contact",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = AccentCyan)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Name: Vicky Kumar", color = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Call, contentDescription = null, tint = AccentCyan)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Phone: $PHONE_NUMBER", color = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Email, contentDescription = null, tint = AccentCyan)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Email: $EMAIL_ID", color = Color.White)
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (stackButtons) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onCall, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Call, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Call")
                    }
                    Button(onClick = onEmail, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Email, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Email")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onCall) {
                        Icon(Icons.Rounded.Call, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Call")
                    }
                    Button(onClick = onEmail) {
                        Icon(Icons.Rounded.Email, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Email")
                    }
                }
            }
        }
    }
}

@Composable
private fun LinksCard(
    onPrimaryGithub: () -> Unit,
    onSecondaryGithub: () -> Unit,
    onLinkedIn: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceSecondary.copy(alpha = 0.75f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Profiles",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Button(onClick = onPrimaryGithub, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Code, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("GitHub: algsoch")
            }

            OutlinedButton(onClick = onSecondaryGithub, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Code, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("GitHub: fiscalmindset")
            }

            OutlinedButton(onClick = onLinkedIn, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Public, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("LinkedIn: algsoch")
            }
        }
    }
}
