package com.jujidaw.ui.project

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jujidaw.diagnostics.RuntimeHealth
import com.jujidaw.diagnostics.RuntimeHealthReport
import com.jujidaw.project.ProjectInfo
import com.jujidaw.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Project management screen.
 *
 * ### Features
 * - List saved projects with name, date, and size.
 * - New / Save / Load / Delete / Rename actions.
 * - WAV mix and stem export buttons.
 * - Audio clip import via file picker.
 * - Timeline recording controls (arm track, start/stop).
 *
 * ### Assumptions
 * - [ProjectViewModel] is created via the default Compose [viewModel] factory.
 * - [ProjectViewModel.init] is called once from a [LaunchedEffect].
 * - Export and timeline recording have TODOs until the C++ engine
 *   provides offline rendering and master-output capture.
 *
 * @param modifier optional [Modifier] applied to the root [Column].
 * @param viewModel the [ProjectViewModel] instance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
    modifier: Modifier = Modifier,
    viewModel: ProjectViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showDiagnostics by remember { mutableStateOf(false) }

    // Initialise repository on first composition.
    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    // Surface one-shot toast messages.
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    // Audio import file picker.
    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.importAudioClip(uri, context)
    }

    // Directory picker for export output.
    val pickExportDir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Resolve the selected tree URI to a file-system path.
            val path = uri.path?.let { p ->
                // content://com.android.externalstorage.documents/tree/primary%3ADocuments
                val segments = p.split("%3A", ":")
                if (segments.size >= 2) {
                    "/storage/emulated/0/${segments.last()}"
                } else null
            }
            if (path != null) {
                viewModel.exportMix("$path/juji_daw_mix.wav")
            }
        }
    }

    val pickStemsDir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val path = uri.path?.let { p ->
                val segments = p.split("%3A", ":")
                if (segments.size >= 2) "/storage/emulated/0/${segments.last()}" else null
            }
            if (path != null) {
                viewModel.exportStems(path)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgGunmetal)
            .padding(4.dp)
    ) {
        // ── Toolbar: transport, project actions ─────────────────────
        ProjectToolbar(
            currentProject = state.currentProjectName,
            isPlaying = state.isPlaying,
            isExporting = state.isExporting,
            exportProgress = state.exportProgress,
            onNew = viewModel::showNewDialog,
            onSave = viewModel::saveProject,
            onPlay = viewModel::playTransport,
            onStop = viewModel::stopTransport,
            onExportMix = { pickExportDir.launch(null) },
            onExportStems = { pickStemsDir.launch(null) },
            onImportAudio = { pickAudio.launch("audio/*") },
            onDiagnostics = { showDiagnostics = true }
        )

        // ── Timeline recording controls ─────────────────────────────
        RecordingBar(
            trackIndex = state.recordingTrackIndex,
            isRecording = state.isTimelineRecording,
            onTrackChange = viewModel::setRecordingTrack,
            onStart = viewModel::startTimelineRecording,
            onStop = viewModel::stopTimelineRecording
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ── Project list ────────────────────────────────────────────
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = KnobCyan,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp)
                )
            }
        } else if (state.projects.isEmpty()) {
            EmptyState(modifier = Modifier.weight(1f))
        } else {
            ProjectList(
                projects = state.projects,
                currentProjectName = state.currentProjectName,
                onLoad = viewModel::loadProject,
                onDelete = viewModel::showDeleteConfirm,
                onRename = viewModel::showRenameDialog,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────────

    if (state.showNewDialog) {
        NewProjectDialog(
            onConfirm = viewModel::createNewProject,
            onDismiss = viewModel::dismissNewDialog
        )
    }

    if (state.showDeleteConfirm) {
        val info = state.projectToDelete
        if (info != null) {
            DeleteConfirmDialog(
                projectName = info.name,
                onConfirm = viewModel::deleteProject,
                onDismiss = viewModel::dismissDeleteConfirm
            )
        }
    }

    if (state.showRenameDialog) {
        val info = state.projectToRename
        if (info != null) {
            RenameDialog(
                currentName = info.name,
                onConfirm = viewModel::renameProject,
                onDismiss = viewModel::dismissRenameDialog
            )
        }
    }

    if (showDiagnostics) {
        DiagnosticsDialog(
            context = context,
            onDismiss = { showDiagnostics = false }
        )
    }
}

// ── Toolbar ────────────────────────────────────────────────────────────

@Composable
private fun ProjectToolbar(
    currentProject: String?,
    isPlaying: Boolean,
    isExporting: Boolean,
    exportProgress: Float,
    onNew: () -> Unit,
    onSave: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onExportMix: () -> Unit,
    onExportStems: () -> Unit,
    onImportAudio: () -> Unit,
    onDiagnostics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BgPanel)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: project name + transport
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Project name label
            Text(
                text = currentProject ?: "No project",
                color = if (currentProject != null) KnobCyan else TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Transport buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TransportButton(
                    label = if (isPlaying) "Stop" else "Play",
                    color = if (isPlaying) TransportRed else TransportGreen,
                    onClick = if (isPlaying) onStop else onPlay
                )
            }
        }

        // Row 2: action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ProjectActionButton("New", KnobCyan, onNew)
            ProjectActionButton("Save", KnobGreen, onSave, enabled = currentProject != null)
            ProjectActionButton("Import", KnobAmber, onImportAudio, enabled = currentProject != null)
            ProjectActionButton("Diag", KnobPink, onDiagnostics)

            Spacer(modifier = Modifier.weight(1f))

            if (isExporting) {
                Text(
                    text = "Export ${(exportProgress * 100).toInt()}%",
                    color = KnobAmber,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            } else {
                ProjectActionButton("Mix", KnobOrange, onExportMix, enabled = currentProject != null)
                ProjectActionButton("Stems", KnobOrange, onExportStems, enabled = currentProject != null)
            }
        }
    }
}

@Composable
private fun ProjectActionButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) BgGunmetal else BgPanel)
            .border(1.dp, PanelHighlight.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) color else TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TransportButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.2f))
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Recording Bar ─────────────────────────────────────────────────────

@Composable
private fun RecordingBar(
    trackIndex: Int,
    isRecording: Boolean,
    onTrackChange: (Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BgPanel)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Record",
            color = if (isRecording) TransportRed else TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Track",
                color = TextSecondary,
                fontSize = 9.sp,
                modifier = Modifier.padding(end = 4.dp)
            )

            // Track index selector
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BgGunmetal)
                        .clickable { onTrackChange((trackIndex - 1).coerceAtLeast(0)) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("-", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "${trackIndex + 1}",
                    color = if (isRecording) TransportRed else TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(24.dp),
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BgGunmetal)
                        .clickable { onTrackChange((trackIndex + 1).coerceAtMost(15)) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Start/Stop record button
        Box(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isRecording) TransportRed.copy(alpha = 0.2f) else TransportRed)
                .border(
                    1.dp,
                    TransportRed,
                    RoundedCornerShape(6.dp)
                )
                .clickable { if (isRecording) onStop() else onStart() }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isRecording) "STOP" else "REC",
                color = if (isRecording) TransportRed else androidx.compose.ui.graphics.Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Project List ──────────────────────────────────────────────────────

@Composable
private fun ProjectList(
    projects: List<ProjectInfo>,
    currentProjectName: String?,
    onLoad: (ProjectInfo) -> Unit,
    onDelete: (ProjectInfo) -> Unit,
    onRename: (ProjectInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(projects, key = { it.name }) { info ->
            ProjectCard(
                info = info,
                isCurrent = info.name == currentProjectName,
                onLoad = { onLoad(info) },
                onDelete = { onDelete(info) },
                onRename = { onRename(info) }
            )
        }
    }
}

@Composable
private fun ProjectCard(
    info: ProjectInfo,
    isCurrent: Boolean,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    val bgColor = if (isCurrent) KnobCyan.copy(alpha = 0.08f) else BgPanel

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = if (isCurrent) 1.dp else 0.dp,
                color = if (isCurrent) KnobCyan.copy(alpha = 0.4f) else PanelHighlight.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onLoad)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.name,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(formatDate(info.lastModified))
                        append("  ·  ")
                        append(formatSize(info.fileSize))
                    },
                    color = TextMuted,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isCurrent) {
                    SmallActionButton("Load", KnobGreen, onLoad)
                }
                SmallActionButton("Rnm", KnobCyan, onRename)
                SmallActionButton("Del", TransportRed, onDelete)
            }
        }
    }
}

@Composable
private fun SmallActionButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(BgGunmetal)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ── Empty State ───────────────────────────────────────────────────────

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No projects yet",
                color = TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Tap New to create your first project",
                color = TextMuted.copy(alpha = 0.6f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────

@Composable
private fun NewProjectDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgPanel,
        title = {
            Text("New Project", fontWeight = FontWeight.Bold, color = KnobCyan)
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Project name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KnobCyan,
                    unfocusedBorderColor = PanelHighlight,
                    cursorColor = TextPrimary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Create", color = KnobCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(
    projectName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgPanel,
        title = { Text("Delete Project", fontWeight = FontWeight.Bold, color = TransportRed) },
        text = {
            Text(
                "Delete \"$projectName\"? This cannot be undone.",
                color = TextPrimary,
                fontSize = 12.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = TransportRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgPanel,
        title = { Text("Rename Project", fontWeight = FontWeight.Bold, color = KnobCyan) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KnobCyan,
                    unfocusedBorderColor = PanelHighlight,
                    cursorColor = TextPrimary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newName) },
                enabled = newName.isNotBlank() && newName != currentName
            ) {
                Text("Rename", color = KnobCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

// ── Diagnostics Dialog ─────────────────────────────────────────────────

/**
 * Dialog that runs [RuntimeHealth.check] and displays the results.
 */
@Composable
private fun DiagnosticsDialog(
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    // Compute the report once when the dialog opens.
    val report = remember { RuntimeHealth.check(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgPanel,
        title = {
            Text(
                text = if (report.ok) "All Checks Passed" else "Diagnostics",
                fontWeight = FontWeight.Bold,
                color = if (report.ok) KnobGreen else KnobAmber
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                report.messages.forEach { msg ->
                    val isPass = msg.startsWith("PASS")
                    val color = if (isPass) KnobGreen else TransportRed
                    Text(
                        text = msg,
                        color = color,
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = KnobCyan)
            }
        }
    )
}

// ── Formatting helpers ────────────────────────────────────────────────

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

private fun formatDate(millis: Long): String {
    return dateFormat.format(Date(millis))
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
    }
}
