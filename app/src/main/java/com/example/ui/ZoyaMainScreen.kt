package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.gemini.ConnectionStatus
import com.example.gemini.ZoyaPersonality
import com.example.ui.components.ZoyaOrbVisualizer
import com.example.ui.components.ZoyaSettingsDialog
import com.example.ui.components.ZoyaToolCard
import com.example.ui.theme.ZoyaAccentCyan
import com.example.ui.theme.ZoyaAccentPink
import com.example.ui.theme.ZoyaBgDark
import com.example.ui.theme.ZoyaCardDark
import com.example.ui.theme.ZoyaCardElevated
import com.example.ui.theme.ZoyaPrimaryMagenta
import com.example.ui.theme.ZoyaSecondaryViolet
import com.example.ui.theme.ZoyaSurfaceDark
import com.example.ui.theme.ZoyaTextMuted
import com.example.ui.theme.ZoyaTextPrimary
import com.example.ui.theme.ZoyaTextSecondary

@Composable
fun ZoyaMainScreen(
    viewModel: ZoyaViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showTranscriptHistory by remember { mutableStateOf(false) }

    // Request audio permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setAudioPermissionGranted(isGranted)
        if (isGranted) {
            Toast.makeText(context, "Microphone enabled for Zoya", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Microphone permission needed to talk with Zoya", Toast.LENGTH_LONG).show()
        }
    }

    // Check permission on startup
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setAudioPermissionGranted(hasPermission)
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Handle browser intent events from Function Calling
    LaunchedEffect(Unit) {
        viewModel.browserIntentEvent.collect { url ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open $url", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = ZoyaBgDark,
        modifier = Modifier
            .fillMaxSize()
            .testTag("zoya_main_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ZoyaBgDark,
                            Color(0xFF140B22),
                            ZoyaSurfaceDark
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top App Header
                TopAppBarSection(
                    connectionStatus = uiState.connectionStatus,
                    onOpenSettings = { showSettingsDialog = true },
                    onToggleHistory = { showTranscriptHistory = !showTranscriptHistory }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Personality Tagline Badge
                PersonalityBadge()

                Spacer(modifier = Modifier.height(12.dp))

                // Function calling Tool Notification Card if active
                ZoyaToolCard(
                    toolEvent = uiState.lastToolEvent,
                    onOpenUrl = { url -> viewModel.launchUrlInBrowser(url) },
                    onDismiss = { viewModel.dismissToolEvent() },
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Central Hero Area: Glowing Orb Visualizer
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (showTranscriptHistory && uiState.transcripts.isNotEmpty()) {
                        TranscriptHistoryView(
                            transcripts = uiState.transcripts,
                            onClose = { showTranscriptHistory = false }
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            ZoyaOrbVisualizer(
                                connectionStatus = uiState.connectionStatus,
                                isSpeaking = uiState.isSpeaking,
                                isListening = uiState.isMicActive,
                                zoyaAmplitude = uiState.zoyaAmplitude,
                                userAmplitude = uiState.userAmplitude,
                                onClick = {
                                    if (!uiState.hasAudioPermission) {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        if (uiState.isSpeaking) {
                                            viewModel.interrupt()
                                        } else if (uiState.connectionStatus != ConnectionStatus.CONNECTED) {
                                            viewModel.connectSession()
                                        } else {
                                            viewModel.toggleMic()
                                        }
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Status Indicator Chip
                            StatusMessageCard(
                                message = uiState.statusMessage,
                                isSpeaking = uiState.isSpeaking,
                                isListening = uiState.isMicActive,
                                onInterrupt = { viewModel.interrupt() }
                            )
                        }
                    }
                }

                // Quick Cheeky Banter Prompts
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CHEEKY BANTER & TOOLS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ZoyaAccentCyan,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = if (showTranscriptHistory) "Show Orb" else "Show Chat (${uiState.transcripts.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ZoyaPrimaryMagenta,
                            modifier = Modifier
                                .clickable { showTranscriptHistory = !showTranscriptHistory }
                                .testTag("toggle_transcript_text")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ZoyaPersonality.QUICK_PROMPTS.forEachIndexed { index, prompt ->
                            BanterChip(
                                text = prompt,
                                onClick = {
                                    if (!uiState.hasAudioPermission) {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        viewModel.sendPrompt(prompt)
                                    }
                                },
                                testTag = "banter_chip_$index"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Floating Control Bar
                BottomControlsBar(
                    uiState = uiState,
                    onToggleSession = {
                        if (!uiState.hasAudioPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.toggleConnection()
                        }
                    },
                    onToggleMic = {
                        if (!uiState.hasAudioPermission) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.toggleMic()
                        }
                    },
                    onInterrupt = { viewModel.interrupt() }
                )
            }
        }
    }

    if (showSettingsDialog) {
        ZoyaSettingsDialog(
            currentApiKey = uiState.apiKey,
            onSaveApiKey = { newKey -> viewModel.updateApiKey(newKey) },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun TopAppBarSection(
    connectionStatus: ConnectionStatus,
    onOpenSettings: () -> Unit,
    onToggleHistory: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ZOYA",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = ZoyaPrimaryMagenta,
                        letterSpacing = 2.sp
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ZoyaPrimaryMagenta.copy(alpha = 0.2f),
                    modifier = Modifier.border(1.dp, ZoyaPrimaryMagenta.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "LIVE AUDIO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZoyaPrimaryMagenta,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "Young • Witty • Sassy AI Assistant",
                fontSize = 11.sp,
                color = ZoyaTextMuted
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Live Status Pill
            ConnectionStatusPill(connectionStatus)

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onToggleHistory,
                modifier = Modifier
                    .size(38.dp)
                    .background(ZoyaCardDark, CircleShape)
                    .testTag("history_button")
            ) {
                Icon(
                    imageVector = Icons.Default.QuestionAnswer,
                    contentDescription = "Chat transcript history",
                    tint = ZoyaTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(38.dp)
                    .background(ZoyaCardDark, CircleShape)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Open Settings",
                    tint = ZoyaTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusPill(status: ConnectionStatus) {
    val (color, text) = when (status) {
        ConnectionStatus.CONNECTED, ConnectionStatus.LISTENING, ConnectionStatus.SPEAKING -> Pair(Color(0xFF10B981), "LIVE")
        ConnectionStatus.CONNECTING -> Pair(ZoyaAccentCyan, "CONNECTING")
        ConnectionStatus.ERROR -> Pair(Color(0xFFEF4444), "ERROR")
        else -> Pair(ZoyaTextMuted, "STANDBY")
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun PersonalityBadge() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ZoyaCardDark.copy(alpha = 0.7f),
        modifier = Modifier
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(ZoyaPrimaryMagenta.copy(alpha = 0.4f), ZoyaSecondaryViolet.copy(alpha = 0.4f))
                ),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = ZoyaAccentPink,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Flirty • Playful • Smart • Sarcastic Banter",
                fontSize = 11.sp,
                color = ZoyaTextSecondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatusMessageCard(
    message: String,
    isSpeaking: Boolean,
    isListening: Boolean,
    onInterrupt: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSpeaking) ZoyaPrimaryMagenta.copy(alpha = 0.15f)
            else if (isListening) ZoyaSecondaryViolet.copy(alpha = 0.15f)
            else ZoyaCardDark
        ),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .border(
                width = 1.dp,
                color = if (isSpeaking) ZoyaPrimaryMagenta.copy(alpha = 0.5f)
                else if (isListening) ZoyaSecondaryViolet.copy(alpha = 0.5f)
                else ZoyaCardElevated,
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("status_message_card")
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = message,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSpeaking) ZoyaTextPrimary else ZoyaTextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (isSpeaking) {
                Spacer(modifier = Modifier.width(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ZoyaPrimaryMagenta,
                    modifier = Modifier
                        .clickable(onClick = onInterrupt)
                        .testTag("interrupt_badge_button")
                ) {
                    Text(
                        text = "Interrupt",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BanterChip(
    text: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ZoyaCardDark,
        modifier = Modifier
            .border(1.dp, ZoyaCardElevated, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = ZoyaTextPrimary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun TranscriptHistoryView(
    transcripts: List<TranscriptItem>,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(transcripts.size) {
        if (transcripts.isNotEmpty()) {
            listState.animateScrollToItem(transcripts.size - 1)
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ZoyaCardDark.copy(alpha = 0.95f)),
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .border(1.dp, ZoyaCardElevated, RoundedCornerShape(20.dp))
            .testTag("transcript_history_view")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE SPOKEN TRANSCRIPT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZoyaAccentCyan,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Close",
                    fontSize = 11.sp,
                    color = ZoyaPrimaryMagenta,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(onClick = onClose)
                        .testTag("close_transcript_button")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(transcripts, key = { it.id }) { item ->
                    val isUser = item.isUser
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                    ) {
                        Text(
                            text = if (isUser) "You" else "Zoya",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) ZoyaAccentCyan else ZoyaPrimaryMagenta
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 14.dp,
                                topEnd = 14.dp,
                                bottomStart = if (isUser) 14.dp else 2.dp,
                                bottomEnd = if (isUser) 2.dp else 14.dp
                            ),
                            color = if (isUser) ZoyaSecondaryViolet.copy(alpha = 0.25f) else ZoyaCardElevated,
                            modifier = Modifier.border(
                                1.dp,
                                if (isUser) ZoyaSecondaryViolet.copy(alpha = 0.4f) else ZoyaPrimaryMagenta.copy(alpha = 0.3f),
                                RoundedCornerShape(14.dp)
                            )
                        ) {
                            Text(
                                text = item.text,
                                fontSize = 13.sp,
                                color = ZoyaTextPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomControlsBar(
    uiState: ZoyaUiState,
    onToggleSession: () -> Unit,
    onToggleMic: () -> Unit,
    onInterrupt: () -> Unit
) {
    val isConnected = uiState.connectionStatus == ConnectionStatus.CONNECTED ||
            uiState.connectionStatus == ConnectionStatus.LISTENING ||
            uiState.connectionStatus == ConnectionStatus.SPEAKING

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Interrupt / Stop Speaking Button
        IconButton(
            onClick = onInterrupt,
            enabled = uiState.isSpeaking,
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (uiState.isSpeaking) ZoyaAccentPink.copy(alpha = 0.2f) else ZoyaCardDark,
                    CircleShape
                )
                .border(
                    1.dp,
                    if (uiState.isSpeaking) ZoyaAccentPink else ZoyaCardElevated,
                    CircleShape
                )
                .testTag("interrupt_action_button")
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Interrupt speech",
                tint = if (uiState.isSpeaking) ZoyaAccentPink else ZoyaTextMuted,
                modifier = Modifier.size(22.dp)
            )
        }

        // Center Big FAB: Start/Stop Voice Session
        FloatingActionButton(
            onClick = onToggleSession,
            containerColor = if (isConnected) ZoyaPrimaryMagenta else ZoyaSecondaryViolet,
            contentColor = Color.White,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
            modifier = Modifier
                .size(68.dp)
                .testTag("main_session_fab")
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.CallEnd else Icons.Default.PowerSettingsNew,
                contentDescription = if (isConnected) "End live voice session" else "Start live voice session",
                modifier = Modifier.size(30.dp)
            )
        }

        // Mic Mute / Unmute Button
        IconButton(
            onClick = onToggleMic,
            enabled = isConnected,
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (uiState.isMicActive) ZoyaPrimaryMagenta.copy(alpha = 0.2f) else ZoyaCardDark,
                    CircleShape
                )
                .border(
                    1.dp,
                    if (uiState.isMicActive) ZoyaPrimaryMagenta else ZoyaCardElevated,
                    CircleShape
                )
                .testTag("mic_toggle_button")
        ) {
            Icon(
                imageVector = if (uiState.isMicActive) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = if (uiState.isMicActive) "Mute microphone" else "Unmute microphone",
                tint = if (uiState.isMicActive) ZoyaPrimaryMagenta else ZoyaTextMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
