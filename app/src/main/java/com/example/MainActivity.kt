package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Recording
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VoiceEnhancerAppScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceEnhancerAppScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(LocalContext.current.applicationContext as android.app.Application))
) {
    val context = LocalContext.current
    var hasAudioPermission by remember { mutableStateOf(false) }

    // Dynamic Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
    }

    // Verify permission on launch
    LaunchedEffect(Unit) {
        hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background // Warm natural off-white background
    ) {
        if (!hasAudioPermission) {
            PermissionOnboardingView(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )
        } else {
            StudioDashboard(viewModel = viewModel)
        }
    }
}

@Composable
fun PermissionOnboardingView(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0x1A3D4B37), CircleShape) // Translucent ForestGreen
                .border(2.dp, Color(0xFF3D4B37), CircleShape), // ForestGreen border
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Microphone Icon",
                tint = Color(0xFF3D4B37),
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Enable Studio Microphone",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2D2A24), // NaturalTextTitle
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Voice Enhancer needs microphone access to capture, remove background noise, and boost clarity in real time.",
            fontSize = 15.sp,
            color = Color(0xFF5D5A53), // NaturalTextSecondary
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D4B37)), // ForestGreen
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(52.dp)
                .testTag("request_permission_button")
        ) {
            Text(
                text = "Grant Microphone Access",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioDashboard(viewModel: MainViewModel) {
    // Collect UI state from viewModel
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val rawAmp by viewModel.rawAmplitude.collectAsState()
    val enhancedAmp by viewModel.enhancedAmplitude.collectAsState()

    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackProgress by viewModel.playbackProgress.collectAsState()
    val playingRecordingId by viewModel.playingRecordingId.collectAsState()
    val recordings by viewModel.recordings.collectAsState()

    var sessionTitle by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf("studio") } // "studio" or "history"

    // Dialog state for renaming
    var editingRecording by remember { mutableStateOf<Recording?>(null) }
    var renameDialogText by remember { mutableStateOf("") }

    // Dialog state for exporting with effects
    var exportingRecording by remember { mutableStateOf<Recording?>(null) }
    var exportDialogText by remember { mutableStateOf("") }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.importAudioFile(uri, sessionTitle.ifBlank { "Imported Audio Session" })
            sessionTitle = ""
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Dynamic Elevated Custom Header
        HeaderBar(
            activeTab = activeTab,
            onTabSelected = { activeTab = it },
            historyCount = recordings.size
        )

        // Main Dynamic Scrollable Layout based on tab selection
        if (activeTab == "studio") {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(12.dp)) }

                // A. Real-time Animated Waveform Visualizer
                item {
                    VisualizerCard(
                        isRecording = isRecording,
                        isPlaying = isPlaying,
                        rawAmp = rawAmp,
                        enhancedAmp = enhancedAmp,
                        playbackProgress = playbackProgress
                    )
                }

                // B. Console Controller (Title, Record Trigger, Live Monitor, Import)
                item {
                    ConsoleControllerCard(
                        isRecording = isRecording,
                        sessionTitle = sessionTitle,
                        onTitleChange = { sessionTitle = it },
                        onToggleRecord = {
                            if (isRecording) {
                                viewModel.stopRecording()
                                sessionTitle = "" // Clear title for next
                            } else {
                                viewModel.startRecording(sessionTitle)
                            }
                        },
                        recordingDuration = recordingDuration,
                        isMonitorEnabled = viewModel.isMonitorEnabled,
                        onToggleMonitor = { viewModel.isMonitorEnabled = it },
                        onImportClick = { audioPickerLauncher.launch("audio/*") }
                    )
                }

                // C. Horizontal Studio Filters Selector
                item {
                    FiltersSelectorSection(
                        selectedPreset = viewModel.selectedPreset,
                        onPresetSelected = { viewModel.selectedPreset = it }
                    )
                }

                // D. Smart Expandable DSP Precision Tweaks
                item {
                    DspPrecisionPanel(
                        isDeRumbleEnabled = viewModel.isDeRumbleEnabled,
                        onToggleDeRumble = { viewModel.isDeRumbleEnabled = it },
                        isNoiseGateEnabled = viewModel.isNoiseGateEnabled,
                        onToggleNoiseGate = { viewModel.isNoiseGateEnabled = it },
                        isCompressorEnabled = viewModel.isCompressorEnabled,
                        onToggleCompressor = { viewModel.isCompressorEnabled = it },
                        noiseThreshold = viewModel.noiseThreshold,
                        onNoiseThresholdChange = { viewModel.noiseThreshold = it }
                    )
                }

                item { Spacer(modifier = Modifier.height(30.dp)) }
            }
        } else {
            // History tab: Display enhanced items list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(12.dp)) }

                if (recordings.isEmpty()) {
                    item {
                        EmptyHistoryPlaceholder()
                    }
                } else {
                    items(recordings) { recording ->
                        RecordingItemRow(
                            recording = recording,
                            isPlaying = isPlaying && playingRecordingId == recording.id,
                            playbackProgress = if (playingRecordingId == recording.id) playbackProgress else 0f,
                            onPlayToggle = {
                                if (isPlaying && playingRecordingId == recording.id) {
                                    viewModel.stopPlayback()
                                } else {
                                    viewModel.startPlayback(recording)
                                }
                            },
                            onDelete = { viewModel.deleteRecording(recording) },
                            onRenameClick = {
                                editingRecording = recording
                                renameDialogText = recording.title
                            },
                            onExportClick = {
                                exportingRecording = recording
                                exportDialogText = "${recording.title} (Enhanced)"
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(30.dp)) }
            }
        }
    }

    // Rename Dialog
    if (editingRecording != null) {
        Dialog(onDismissRequest = { editingRecording = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF5F2EA), // CardBg
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Rename Studio Session",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B16) // NaturalTextPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = renameDialogText,
                        onValueChange = { renameDialogText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3D4B37), // ForestGreen
                            unfocusedBorderColor = Color(0xFFEBE6D9), // MutedTanBorder
                            focusedTextColor = Color(0xFF1D1B16),
                            unfocusedTextColor = Color(0xFF1D1B16)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("rename_input")
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { editingRecording = null }
                        ) {
                            Text("Cancel", color = Color(0xFF5D5A53)) // NaturalTextSecondary
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                editingRecording?.let {
                                    viewModel.updateRecordingTitle(it, renameDialogText)
                                }
                                editingRecording = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D4B37)) // ForestGreen
                        ) {
                            Text("Save", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Export with effects Dialog
    if (exportingRecording != null) {
        Dialog(onDismissRequest = { exportingRecording = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF5F2EA), // CardBg
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Apply Effects & Export",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B16) // NaturalTextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "This will bake the currently selected preset (${viewModel.selectedPreset}) and any active noise suppression directly into a new permanent audio file.",
                        fontSize = 12.sp,
                        color = Color(0xFF5D5A53),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = exportDialogText,
                        onValueChange = { exportDialogText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3D4B37), // ForestGreen
                            unfocusedBorderColor = Color(0xFFEBE6D9), // MutedTanBorder
                            focusedTextColor = Color(0xFF1D1B16),
                            unfocusedTextColor = Color(0xFF1D1B16)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("export_input")
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { exportingRecording = null }
                        ) {
                            Text("Cancel", color = Color(0xFF5D5A53)) // NaturalTextSecondary
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                exportingRecording?.let {
                                    viewModel.applyFiltersAndExport(it, exportDialogText)
                                }
                                exportingRecording = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D4B37)) // ForestGreen
                        ) {
                            Text("Export", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderBar(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    historyCount: Int
) {
    Surface(
        color = Color(0xFFFDFCF9), // NaturalBg
        tonalElevation = 8.dp,
        modifier = Modifier.shadow(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Branding Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.RecordVoiceOver,
                    contentDescription = "App Logo",
                    tint = Color(0xFF3D4B37), // ForestGreen
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "VOICE ENHANCER",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    color = Color(0xFF2D2A24) // NaturalTextTitle
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Animated live dot if recording
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF3D4B37), CircleShape) // ForestGreen
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub-navigation Tabs: Studio Controls vs. History
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(Color(0xFFEBE6D9), RoundedCornerShape(12.dp)) // MutedTanBorder
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val tabs = listOf(
                    "studio" to "Studio",
                    "history" to "History ($historyCount)"
                )
                tabs.forEach { (key, label) ->
                    val isSelected = activeTab == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable { onTabSelected(key) }
                            .padding(vertical = 10.dp)
                            .testTag("tab_$key"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF3D4B37) else Color(0xFF7D7767) // ForestGreen vs Muted/Subtitle
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun VisualizerCard(
    isRecording: Boolean,
    isPlaying: Boolean,
    rawAmp: Float,
    enhancedAmp: Float,
    playbackProgress: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = { it }),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F2EA)), // CardBg
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isRecording) "RECORDING REAL-TIME AUDIO" else if (isPlaying) "PLAYBACK PREVIEWING DSP" else "STUDIO GRAPH VIEW",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isRecording) Color(0xFFEF4444) else Color(0xFF5D5A53), // NaturalTextSecondary
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Waveform Graphic Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color(0xFFFDFCF9), RoundedCornerShape(12.dp)) // NaturalBg
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val middleY = height / 2

                    // Horizontal baseline grid line
                    drawLine(
                        color = Color(0x308C8471), // Tan accent
                        start = Offset(0f, middleY),
                        end = Offset(width, middleY),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Draw 45 vertical grid lines for studio monitor look
                    val spacing = width / 45
                    for (i in 0..45) {
                        drawLine(
                            color = Color(0x108C8471), // Tan accent light
                            start = Offset(i * spacing, 0f),
                            end = Offset(i * spacing, height),
                            strokeWidth = 0.5.dp.toPx()
                        )
                    }

                    // A. DRAW RAW AUDIO WAVE (Muted Olive Wave)
                    val rawScaleFactor = if (isRecording) rawAmp * 1.5f else if (isPlaying) 0.1f else 0.05f
                    val rawWavePath = androidx.compose.ui.graphics.Path()
                    rawWavePath.moveTo(0f, middleY)

                    for (x in 0..width.toInt() step 4) {
                        val normalizedX = x.toFloat() / width
                        val sineVal = sin(normalizedX * 4 * Math.PI + waveOffset) * 0.7f +
                                sin(normalizedX * 12 * Math.PI - waveOffset * 0.5f) * 0.3f
                        val y = middleY + (sineVal * rawScaleFactor * (height / 2))
                        rawWavePath.lineTo(x.toFloat(), y.toFloat())
                    }

                    drawPath(
                        path = rawWavePath,
                        color = Color(0x905A634D), // Muted Olive
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // B. DRAW ENHANCED AUDIO WAVE (Solid Forest Green Wave)
                    val enhancedScaleFactor = if (isRecording) enhancedAmp * 1.5f else if (isPlaying) 0.35f else 0.08f
                    val enhancedWavePath = androidx.compose.ui.graphics.Path()
                    enhancedWavePath.moveTo(0f, middleY)

                    for (x in 0..width.toInt() step 4) {
                        val normalizedX = x.toFloat() / width
                        val sineVal = sin(normalizedX * 5 * Math.PI - waveOffset * 1.2f) * 0.8f +
                                sin(normalizedX * 15 * Math.PI + waveOffset * 0.8f) * 0.2f
                        val y = middleY + (sineVal * enhancedScaleFactor * (height / 2))
                        enhancedWavePath.lineTo(x.toFloat(), y.toFloat())
                    }

                    drawPath(
                        path = enhancedWavePath,
                        color = Color(0xFF3D4B37), // ForestGreen
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    // C. IF PLAYING: Draw vertical seek tracking cursor line
                    if (isPlaying) {
                        val cursorX = width * playbackProgress
                        drawLine(
                            color = Color(0xFF5A634D), // Muted Olive
                            start = Offset(cursorX, 0f),
                            end = Offset(cursorX, height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic Real-time dB Legend Label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF5A634D), CircleShape)) // Muted Olive
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Raw Audio (Input Noise)", fontSize = 11.sp, color = Color(0xFF5D5A53))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF3D4B37), CircleShape)) // ForestGreen
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clean Enhanced (Studio Out)", fontSize = 11.sp, color = Color(0xFF3D4B37))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleControllerCard(
    isRecording: Boolean,
    sessionTitle: String,
    onTitleChange: (String) -> Unit,
    onToggleRecord: () -> Unit,
    recordingDuration: Long,
    isMonitorEnabled: Boolean,
    onToggleMonitor: (Boolean) -> Unit,
    onImportClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F2EA)), // CardBg
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title Input Bar (Only visible when not recording)
            if (!isRecording) {
                OutlinedTextField(
                    value = sessionTitle,
                    onValueChange = onTitleChange,
                    placeholder = { Text("Untitled Studio Session", color = Color(0xFF7D7767)) }, // Subtitle/Muted
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFFDFCF9), // NaturalBg
                        unfocusedContainerColor = Color(0xFFFDFCF9), // NaturalBg
                        focusedBorderColor = Color(0xFF3D4B37), // ForestGreen
                        unfocusedBorderColor = Color(0xFFEBE6D9), // MutedTanBorder
                        focusedTextColor = Color(0xFF1D1B16), // NaturalTextPrimary
                        unfocusedTextColor = Color(0xFF1D1B16)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_title_input"),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit Title",
                            tint = Color(0xFF7D7767)
                        )
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // Active Session Title Display during Recording
                Text(
                    text = if (sessionTitle.isBlank()) "Active Studio Recording" else sessionTitle,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D1B16), // NaturalTextPrimary
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Duration Clock Counter
            Text(
                text = formatDuration(recordingDuration),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = if (isRecording) Color(0xFFEF4444) else Color(0xFF2D2A24) // NaturalTextTitle
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Main Trigger Buttons Block
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Import Audio Button
                if (!isRecording) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0xFFEBE6D9), CircleShape)
                            .clickable { onImportClick() }
                            .testTag("import_audio_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = "Import Audio",
                            tint = Color(0xFF3D4B37),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Large Pulsing Record Button
                val scale = if (isRecording) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.15f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(700, easing = { it }),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseScale"
                    ).value
                } else 1f

                Box(
                    modifier = Modifier
                        .scale(scale)
                        .size(76.dp)
                        .background(
                            if (isRecording) Color(0xFFEF4444) else Color(0xFF3D4B37), // ForestGreen
                            CircleShape
                        )
                        .clickable { onToggleRecord() }
                        .testTag("record_trigger_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Filled.Pause else Icons.Filled.Mic,
                        contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Real-time headphones monitoring switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEBE6D9), RoundedCornerShape(12.dp)) // MutedTanBorder
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Headphones,
                        contentDescription = "Monitor Icon",
                        tint = Color(0xFF3D4B37), // ForestGreen
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Live Loopback Monitor",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D1B16) // NaturalTextPrimary
                        )
                        Text(
                            text = "Hear your vocal output (use headphones!)",
                            fontSize = 11.sp,
                            color = Color(0xFF5D5A53) // NaturalTextSecondary
                        )
                    }
                }

                Switch(
                    checked = isMonitorEnabled,
                    onCheckedChange = onToggleMonitor,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF3D4B37), // ForestGreen
                        checkedTrackColor = Color(0xFFDCE5C8), // SoftLightGreen
                        uncheckedThumbColor = Color(0xFF7D7767),
                        uncheckedTrackColor = Color(0xFFEBE6D9)
                    ),
                    modifier = Modifier.testTag("monitor_toggle")
                )
            }
        }
    }
}

@Composable
fun FiltersSelectorSection(
    selectedPreset: String,
    onPresetSelected: (String) -> Unit
) {
    val filters = listOf(
        FilterPreset("Original", "Bypass EQ", Icons.Filled.SettingsInputComponent, "Raw unmodified mic sound"),
        FilterPreset("Crispy Studio", "Crispy Studio", Icons.Filled.RecordVoiceOver, "Airy high presence & pro sizzle"),
        FilterPreset("Studio Mic", "Studio Mic", Icons.Filled.GraphicEq, "Warm lows, crisp highs & presence"),
        FilterPreset("Clear Voice", "Clear Voice", Icons.Filled.RecordVoiceOver, "Low cut & speech mid-band focus"),
        FilterPreset("Podcast Pro", "Podcast", Icons.Filled.HeadsetMic, "Intimate broadcast-level clarity"),
        FilterPreset("Deep Bass", "Deep Bass", Icons.Filled.MusicNote, "Enriched deep baritone body boost"),
        FilterPreset("Radio Broadcast", "AM Radio", Icons.Filled.Tune, "Retro telephone AM bandwidth vibe"),
        FilterPreset("Ambient Space", "Eco Studio", Icons.Filled.Headset, "EQ combined with rich room reverb")
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "STUDIO QUALITY FILTERS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF3D4B37), // ForestGreen
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filters) { item ->
                val isSelected = selectedPreset == item.presetKey
                Card(
                    modifier = Modifier
                        .width(135.dp)
                        .height(105.dp)
                        .clickable { onPresetSelected(item.presetKey) }
                        .testTag("filter_card_${item.presetKey}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFFEBE6D9) else Color(0xFFF5F2EA) // Active vs Inactive CardBg
                    ),
                    border = if (isSelected) BorderStroke(1.5.dp, Color(0xFF3D4B37)) else BorderStroke(1.dp, Color(0xFFEBE6D9))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (isSelected) Color(0xFF3D4B37) else Color(0xFF5A634D), // ForestGreen vs MutedOlive
                            modifier = Modifier.size(20.dp)
                        )

                        Column {
                            Text(
                                text = item.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D1B16), // NaturalTextPrimary
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.description,
                                fontSize = 9.sp,
                                color = Color(0xFF5D5A53), // NaturalTextSecondary
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

data class FilterPreset(
    val presetKey: String,
    val title: String,
    val icon: ImageVector,
    val description: String
)

@Composable
fun DspPrecisionPanel(
    isDeRumbleEnabled: Boolean,
    onToggleDeRumble: (Boolean) -> Unit,
    isNoiseGateEnabled: Boolean,
    onToggleNoiseGate: (Boolean) -> Unit,
    isCompressorEnabled: Boolean,
    onToggleCompressor: (Boolean) -> Unit,
    noiseThreshold: Float,
    onNoiseThresholdChange: (Float) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F2EA)), // CardBg
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "DSP PRECISION CONTROLS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF3D4B37) // ForestGreen
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Noise Suppressor Slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEBE6D9), RoundedCornerShape(12.dp)) // MutedTanBorder
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Noise Gate",
                            tint = Color(0xFF3D4B37), // ForestGreen
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Smart Noise Gate Floor",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D1B16) // NaturalTextPrimary
                        )
                    }

                    Switch(
                        checked = isNoiseGateEnabled,
                        onCheckedChange = onToggleNoiseGate,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF3D4B37), // ForestGreen
                            checkedTrackColor = Color(0xFFDCE5C8), // SoftLightGreen
                            uncheckedThumbColor = Color(0xFF7D7767),
                            uncheckedTrackColor = Color(0xFFEBE6D9)
                        ),
                        modifier = Modifier.scale(0.8f).testTag("noisegate_switch")
                    )
                }

                AnimatedVisibility(
                    visible = isNoiseGateEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = noiseThreshold,
                            onValueChange = onNoiseThresholdChange,
                            valueRange = 15f..400f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF3D4B37), // ForestGreen
                                activeTrackColor = Color(0xFF3D4B37),
                                inactiveTrackColor = Color(0xFFEBE6D9)
                            ),
                            modifier = Modifier.testTag("noise_threshold_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mild De-noising", fontSize = 10.sp, color = Color(0xFF5D5A53))
                            Text(
                                "Gate Cutoff: ${noiseThreshold.toInt()} RMS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3D4B37) // ForestGreen
                            )
                            Text("Aggressive (Hiss cut)", fontSize = 10.sp, color = Color(0xFF5D5A53))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. High-Pass Filter Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEBE6D9), RoundedCornerShape(12.dp)) // MutedTanBorder
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "AC/Wind De-Rumble (HPF)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B16) // NaturalTextPrimary
                    )
                    Text(
                        text = "Cuts room drone hums below 85Hz",
                        fontSize = 11.sp,
                        color = Color(0xFF5D5A53) // NaturalTextSecondary
                    )
                }

                Switch(
                    checked = isDeRumbleEnabled,
                    onCheckedChange = onToggleDeRumble,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF3D4B37), // ForestGreen
                        checkedTrackColor = Color(0xFFDCE5C8), // SoftLightGreen
                        uncheckedThumbColor = Color(0xFF7D7767),
                        uncheckedTrackColor = Color(0xFFEBE6D9)
                    ),
                    modifier = Modifier.scale(0.85f).testTag("derumble_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Compressor AGC Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEBE6D9), RoundedCornerShape(12.dp)) // MutedTanBorder
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Vocal Compressor (AGC)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B16) // NaturalTextPrimary
                    )
                    Text(
                        text = "Evens voice spikes & whispering levels",
                        fontSize = 11.sp,
                        color = Color(0xFF5D5A53) // NaturalTextSecondary
                    )
                }

                Switch(
                    checked = isCompressorEnabled,
                    onCheckedChange = onToggleCompressor,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF3D4B37), // ForestGreen
                        checkedTrackColor = Color(0xFFDCE5C8), // SoftLightGreen
                        uncheckedThumbColor = Color(0xFF7D7767),
                        uncheckedTrackColor = Color(0xFFEBE6D9)
                    ),
                    modifier = Modifier.scale(0.85f).testTag("compressor_switch")
                )
            }
        }
    }
}

@Composable
fun RecordingItemRow(
    recording: Recording,
    isPlaying: Boolean,
    playbackProgress: Float,
    onPlayToggle: () -> Unit,
    onDelete: () -> Unit,
    onRenameClick: () -> Unit,
    onExportClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F2EA)), // CardBg
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Play Trigger button
                IconButton(
                    onClick = onPlayToggle,
                    modifier = Modifier
                        .background(
                            if (isPlaying) Color(0xFFEBE6D9) else Color(0x2A3D4B37), // Translucent ForestGreen vs MutedTanBorder
                            CircleShape
                        )
                        .testTag("play_recording_${recording.id}")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play Control",
                        tint = if (isPlaying) Color(0xFF5A634D) else Color(0xFF3D4B37), // MutedOlive vs ForestGreen
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Mid Block: Session Title and Meta Details
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = recording.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D1B16), // NaturalTextPrimary
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onRenameClick,
                            modifier = Modifier.size(16.dp).testTag("rename_recording_${recording.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Rename Button",
                                tint = Color(0xFF7D7767), // Subtitle/Muted
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatDuration(recording.durationMs),
                            fontSize = 11.sp,
                            color = Color(0xFF5D5A53) // NaturalTextSecondary
                        )
                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            color = Color(0xFF8C8471) // Tan accent
                        )
                        Text(
                            text = formatFileSize(recording.fileSize),
                            fontSize = 11.sp,
                            color = Color(0xFF5D5A53) // NaturalTextSecondary
                        )
                        Text(
                            text = "•",
                            fontSize = 11.sp,
                            color = Color(0xFF8C8471) // Tan accent
                        )
                        // Filter Badge
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEBE6D9), RoundedCornerShape(4.dp)) // MutedTanBorder
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = if (recording.isEnhanced) "${recording.filterPreset} (Baked)" else recording.filterPreset,
                                fontSize = 8.sp,
                                color = Color(0xFF3D4B37), // ForestGreen
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Delete Action button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_recording_${recording.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete Recording",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Real-time interactive Seekable slider if active
            if (isPlaying) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Seek:",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF5A634D) // MutedOlive
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(Color(0xFFEBE6D9), RoundedCornerShape(2.dp)) // MutedTanBorder
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(playbackProgress)
                                .height(4.dp)
                                .background(Color(0xFF3D4B37), RoundedCornerShape(2.dp)) // ForestGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onExportClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D4B37)), // ForestGreen
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("export_with_effects_${recording.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.RecordVoiceOver,
                        contentDescription = "Bake & Export",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply Active Filters & Export New File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = "History Empty",
            tint = Color(0xFF5A634D), // MutedOlive
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Studio Sessions Yet",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D1B16) // NaturalTextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Create your first high-quality audio recording to apply studio presets and noise suppressors.",
            fontSize = 12.sp,
            color = Color(0xFF5D5A53), // NaturalTextSecondary
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
            lineHeight = 18.sp
        )
    }
}

// ==========================================
// Formatting Helpers
// ==========================================

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = (ms % 1000) / 10
    return String.format(Locale.US, "%02d:%02d:%02d", minutes, seconds, millis)
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format(Locale.US, "%.1f %cB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

