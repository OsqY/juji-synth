package com.jujidaw.ui.project

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    var showTools by rememberSaveable { mutableStateOf(false) }

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
        modifier =
            modifier
                .fillMaxSize()
                .background(Bg0)
                .padding(Spacing.sm)
                .testTag("project-root"),
    ) {
        ProjectToolbar(
            currentProject = state.currentProjectName,
            isExporting = state.isExporting,
            exportProgress = state.exportProgress,
            onNew = viewModel::showNewDialog,
            onSave = viewModel::saveProject,
            toolsVisible = showTools,
            onToggleTools = { showTools = !showTools },
        )

        if (showTools) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            ProjectTools(
                currentProject = state.currentProjectName,
                isExporting = state.isExporting,
                exportProgress = state.exportProgress,
                onExportMix = { pickExportDir.launch(null) },
                onExportStems = { pickStemsDir.launch(null) },
                onImportAudio = { pickAudio.launch("audio/*") },
                onDiagnostics = { showDiagnostics = true },
                trackIndex = state.recordingTrackIndex,
                isRecording = state.isTimelineRecording,
                onTrackChange = viewModel::setRecordingTrack,
                onStartRecording = viewModel::startTimelineRecording,
                onStopRecording = viewModel::stopTimelineRecording,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("project-browser"),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    color = Primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp).align(Alignment.Center),
                )
            } else if (state.projects.isEmpty()) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                ProjectList(
                    projects = state.projects,
                    currentProjectName = state.currentProjectName,
                    onLoad = viewModel::loadProject,
                    onDelete = viewModel::showDeleteConfirm,
                    onRename = viewModel::showRenameDialog,
                    modifier = Modifier.fillMaxSize(),
                )
            }
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
    isExporting: Boolean,
    exportProgress: Float,
    onNew: () -> Unit,
    onSave: () -> Unit,
    toolsVisible: Boolean,
    onToggleTools: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TouchTargetMin)
                .background(SurfaceContainer)
                .padding(horizontal = Spacing.xs)
                .testTag("project-toolbar"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isExporting) "Export ${(exportProgress * 100).toInt()}%" else currentProject ?: "NO PROJECT",
            color = if (currentProject != null) Primary else OnSurfaceVariant,
            style = MonoLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ProjectIconButton(
            icon = Icons.Outlined.Add,
            contentDescription = "New project",
            onClick = onNew,
            modifier = Modifier.testTag("project-new"),
        )
        ProjectActionButton(
            label = "SAVE",
            color = Secondary,
            onClick = onSave,
            enabled = currentProject != null,
        )
        ProjectIconButton(
            icon = Icons.Outlined.Tune,
            contentDescription = if (toolsVisible) "Hide project tools" else "Show project tools",
            onClick = onToggleTools,
            active = toolsVisible,
            selectionAware = true,
            modifier = Modifier.testTag("project-tools-toggle"),
        )
    }
}

@Composable
private fun ProjectTools(
    currentProject: String?,
    isExporting: Boolean,
    exportProgress: Float,
    onExportMix: () -> Unit,
    onExportStems: () -> Unit,
    onImportAudio: () -> Unit,
    onDiagnostics: () -> Unit,
    trackIndex: Int,
    isRecording: Boolean,
    onTrackChange: (Int) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("project-tools"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(TouchTargetMin).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProjectActionButton(
                label = "IMPORT",
                color = Secondary,
                onClick = onImportAudio,
                enabled = currentProject != null,
            )
            if (isExporting) {
                Text(
                    text = "Export ${(exportProgress * 100).toInt()}%",
                    color = Primary,
                    style = LabelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )
            } else {
                ProjectActionButton("MIX", Primary, onExportMix, enabled = currentProject != null)
                ProjectActionButton("STEMS", Primary, onExportStems, enabled = currentProject != null)
            }
            ProjectActionButton(
                label = "DIAG",
                color = OnSurface,
                onClick = onDiagnostics,
                modifier = Modifier.testTag("project-diagnostics"),
            )
        }
        RecordingBar(
            trackIndex = trackIndex,
            isRecording = isRecording,
            onTrackChange = onTrackChange,
            onStart = onStartRecording,
            onStop = onStopRecording,
        )
    }
}

@Composable
private fun ProjectActionButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(TouchTargetMin)
                .widthIn(min = TouchTargetMin)
                .clip(RoundedCornerShape(RadiusSm))
                .background(if (enabled) SurfaceContainerLow else DisabledFill)
                .border(1.dp, if (enabled) OutlineVariant else OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(RadiusSm))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) color else DisabledText,
            style = LabelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ProjectIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    selectionAware: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .size(TouchTargetMin)
                .clip(RoundedCornerShape(RadiusSm))
                .background(if (active) Primary.copy(alpha = 0.18f) else SurfaceContainerLow)
                .border(1.dp, if (active) Primary else OutlineVariant, RoundedCornerShape(RadiusSm))
                .then(
                    if (selectionAware) {
                        Modifier.selectable(selected = active, onClick = onClick, role = Role.Button)
                    } else {
                        Modifier.clickable(onClick = onClick)
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) Primary else OnSurface,
            modifier = Modifier.size(20.dp),
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
        modifier =
            Modifier
                .fillMaxWidth()
                .height(TouchTargetMin)
                .background(SurfaceContainer)
                .padding(horizontal = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "REC",
            color = if (isRecording) StateRecording else TextSecondary,
            style = LabelSmall,
            fontWeight = FontWeight.Bold,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(TouchTargetMin)
                            .clip(RoundedCornerShape(RadiusSm))
                            .background(SurfaceContainerLow)
                            .semantics { contentDescription = "Previous recording track" }
                            .clickable { onTrackChange((trackIndex - 1).coerceAtLeast(0)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("−", color = OnSurface, style = LabelSmall, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "T${(trackIndex + 1).toString().padStart(2, '0')}",
                    color = if (isRecording) StateRecording else TextPrimary,
                    style = MonoLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(40.dp),
                    maxLines = 1,
                )
                Box(
                    modifier =
                        Modifier
                            .size(TouchTargetMin)
                            .clip(RoundedCornerShape(RadiusSm))
                            .background(SurfaceContainerLow)
                            .semantics { contentDescription = "Next recording track" }
                            .clickable { onTrackChange((trackIndex + 1).coerceAtMost(15)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", color = OnSurface, style = LabelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .height(TouchTargetMin)
                    .widthIn(min = 64.dp)
                    .clip(RoundedCornerShape(RadiusSm))
                    .background(if (isRecording) StateRecording.copy(alpha = 0.18f) else SurfaceContainerLow)
                    .border(1.dp, StateRecording, RoundedCornerShape(RadiusSm))
                    .clickable { if (isRecording) onStop() else onStart() }
                    .testTag("project-record"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isRecording) "STOP" else "REC",
                color = StateRecording,
                style = LabelSmall,
                fontWeight = FontWeight.Bold,
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
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
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
    val bgColor = if (isCurrent) Primary.copy(alpha = 0.08f) else SurfaceContainerLow

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusSm))
            .background(bgColor)
            .border(
                width = if (isCurrent) 1.dp else 0.dp,
                color = if (isCurrent) Primary else OutlineVariant,
                shape = RoundedCornerShape(RadiusSm),
            )
            .clickable(onClick = onLoad)
            .padding(Spacing.sm)
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
                    color = TextDisabled,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isCurrent) {
                    SmallActionButton("LOAD", Secondary, onLoad)
                }
                SmallActionButton("RENAME", Primary, onRename)
                SmallActionButton("DELETE", StateRecording, onDelete)
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
            .height(TouchTargetMin)
            .widthIn(min = TouchTargetMin)
            .clip(RoundedCornerShape(RadiusSm))
            .background(SurfaceContainer)
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(RadiusSm))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = color,
            style = LabelSmall,
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
