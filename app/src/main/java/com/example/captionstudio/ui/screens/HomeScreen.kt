package com.example.captionstudio.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.captionstudio.domain.model.CaptionProject
import com.example.captionstudio.domain.model.GeminiSettings
import com.example.captionstudio.domain.model.GeminiUsageMode
import com.example.captionstudio.ui.components.ProjectVideoThumbnail
import com.example.captionstudio.ui.components.StudioAmbientBackdrop
import com.example.captionstudio.ui.components.StudioBrandMark
import com.example.captionstudio.ui.components.StudioCircleIconButton
import com.example.captionstudio.ui.components.StudioGlassCard
import com.example.captionstudio.ui.components.studioPressScale
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioDisplayFont
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioError
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioHairlineStrong
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
  projects: List<CaptionProject>,
  geminiSettings: GeminiSettings,
  isBusy: Boolean,
  onOpenProject: (String) -> Unit,
  onImportVideo: suspend (Uri, String) -> String,
  onCreateProject: suspend (String, Boolean) -> String,
  onRenameProject: (String, String) -> Unit,
  onDeleteProject: (String) -> Unit,
  onOpenSettings: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var showNewProjectDialog by remember { mutableStateOf(false) }
  var showAllProjects by remember { mutableStateOf(false) }
  var renameTarget by remember { mutableStateOf<CaptionProject?>(null) }
  var deleteTarget by remember { mutableStateOf<CaptionProject?>(null) }
  var contentVisible by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) { contentVisible = true }

  fun handlePickedVideo(uri: Uri?) {
    if (uri == null) return
    runCatching {
      context.contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
      )
    }
    val displayName = resolveUriDisplayName(context, uri) ?: "Untitled Video"
    scope.launch {
      val id = onImportVideo(uri, displayName)
      onOpenProject(id)
    }
  }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      handlePickedVideo(uri)
    }

  val documentVideoLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      handlePickedVideo(uri)
    }

  val aiActive = geminiSettings.enabled && geminiSettings.usageMode != GeminiUsageMode.OFFLINE
  val displayedProjects = if (showAllProjects) projects else projects.take(3)

  Scaffold(
    containerColor = StudioObsidian,
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().background(StudioObsidian),
      contentAlignment = Alignment.TopCenter,
    ) {
      // Subtle atmospheric obsidian & coral glow backdrop
      StudioAmbientBackdrop()

      LazyColumn(
        modifier =
          Modifier.fillMaxSize()
            .widthIn(max = 640.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding =
          PaddingValues(
            start = 22.dp,
            end = 22.dp,
            top = 12.dp,
            bottom = innerPadding.calculateBottomPadding() + 28.dp,
          ),
      ) {
        // Minimal Header: [CC] Caption Studio ... [Settings]
        item(key = "home_header") {
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              StudioBrandMark(size = 42.dp)
              Spacer(Modifier.width(13.dp))
              Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = StudioTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            StudioCircleIconButton(
              icon = Icons.Outlined.Settings,
              contentDescription = "Settings",
              onClick = onOpenSettings,
              badgeDotColor = if (aiActive) StudioCoral else null,
              modifier = Modifier.testTag("home_settings_button"),
            )
          }
        }

        // Generous Apple-style Hero Typography
        item(key = "home_hero") {
          AnimatedVisibility(
            visible = contentVisible,
            enter =
              fadeIn(animationSpec = tween(420, easing = FastOutSlowInEasing)) +
                slideInVertically(
                  initialOffsetY = { it / 8 },
                  animationSpec = tween(420, easing = FastOutSlowInEasing),
                ),
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 42.dp, bottom = 36.dp)) {
              Text(
                text =
                  buildAnnotatedString {
                    withStyle(SpanStyle(color = StudioTextPrimary)) {
                      append("Turn your\nvideos into\n")
                    }
                    withStyle(SpanStyle(color = StudioCoral)) {
                      append("captions.")
                    }
                  },
                fontFamily = StudioDisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = 46.sp,
                lineHeight = 49.sp,
                letterSpacing = (-1.5).sp,
              )
              Spacer(Modifier.height(16.dp))
              Text(
                text = "Fast. Accurate. Effortless.",
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 17.sp,
                color = StudioTextSecondary,
                letterSpacing = (-0.1).sp,
              )
            }
          }
        }

        // Clean Glass Action Pills
        item(key = "home_actions") {
          AnimatedVisibility(
            visible = contentVisible,
            enter =
              fadeIn(animationSpec = tween(480, delayMillis = 70, easing = FastOutSlowInEasing)) +
                slideInVertically(
                  initialOffsetY = { it / 6 },
                  animationSpec = tween(480, delayMillis = 70, easing = FastOutSlowInEasing),
                ),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              ImportVideoGlassPill(
                isBusy = isBusy,
                onClick = {
                  photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                  )
                },
                modifier = Modifier.testTag("import_video_button"),
              )

              BrowseFilesOutlinePill(
                enabled = !isBusy,
                onClick = { documentVideoLauncher.launch(arrayOf("video/*")) },
                modifier = Modifier.testTag("browse_files_button"),
              )

              // Whisper-subtle tertiary option preserving blank/reference script creation
              Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(
                  onClick = { showNewProjectDialog = true },
                  enabled = !isBusy,
                  contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("new_project_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = StudioTextSecondary,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(Modifier.width(6.dp))
                  Text(
                    text = "Or start from a script",
                    style = MaterialTheme.typography.labelLarge,
                    color = StudioTextSecondary,
                  )
                }
              }
            }
          }
        }

        // Your Projects Section Header
        item(key = "projects_header") {
          Spacer(Modifier.height(26.dp))
          Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Your Projects",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.SemiBold,
              color = StudioTextPrimary,
            )

            if (projects.size > 3) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                  Modifier.clip(CircleShape)
                    .clickable { showAllProjects = !showAllProjects }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("see_all_projects_button"),
              ) {
                Text(
                  text = if (showAllProjects) "Show less" else "See all",
                  style = MaterialTheme.typography.labelLarge,
                  color = StudioTextSecondary,
                )
                if (!showAllProjects) {
                  Spacer(Modifier.width(4.dp))
                  Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = StudioTextSecondary,
                    modifier = Modifier.size(15.dp),
                  )
                }
              }
            } else if (projects.isNotEmpty()) {
              Text(
                text = if (projects.size == 1) "1 project" else "${projects.size} projects",
                style = MaterialTheme.typography.labelMedium,
                color = StudioTextMuted,
              )
            }
          }
        }

        // Projects List or Clean Minimal Empty State
        if (projects.isEmpty()) {
          item(key = "projects_empty") {
            StudioGlassCard(
              modifier = Modifier.fillMaxWidth(),
              cornerRadius = 22.dp,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier =
                    Modifier.size(54.dp)
                      .clip(RoundedCornerShape(15.dp))
                      .background(StudioCoral.copy(alpha = 0.14f))
                      .border(1.dp, StudioCoral.copy(alpha = 0.25f), RoundedCornerShape(15.dp)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = StudioCoral,
                    modifier = Modifier.size(24.dp),
                  )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "No projects yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = StudioTextPrimary,
                  )
                  Spacer(Modifier.height(4.dp))
                  Text(
                    text = "Import a video for automatic speech captions, or start from a script.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudioTextSecondary,
                  )
                }
              }
            }
          }
        } else {
          items(displayedProjects, key = { it.id }) { project ->
            MinimalProjectCard(
              project = project,
              onClick = { onOpenProject(project.id) },
              onRename = { renameTarget = project },
              onDelete = { deleteTarget = project },
              modifier = Modifier.padding(bottom = 12.dp).animateItem(),
            )
          }
        }
      }
    }
  }

  // New Script Project Dialog
  if (showNewProjectDialog) {
    var titleInput by remember { mutableStateOf("Untitled Reel") }
    var loadReference by remember { mutableStateOf(true) }
    AlertDialog(
      onDismissRequest = { showNewProjectDialog = false },
      containerColor = StudioCard,
      shape = RoundedCornerShape(24.dp),
      title = {
        Text(
          "New Script Project",
          style = MaterialTheme.typography.titleLarge,
          color = StudioTextPrimary,
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          OutlinedTextField(
            value = titleInput,
            onValueChange = { titleInput = it },
            label = { Text("Project name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = StudioCoral,
                unfocusedBorderColor = StudioBorder,
                focusedLabelColor = StudioCoral,
                cursorColor = StudioCoral,
              ),
            modifier = Modifier.fillMaxWidth().testTag("new_project_name_input"),
          )
          Surface(
            color = StudioElevated,
            shape = RoundedCornerShape(16.dp),
            modifier =
              Modifier.fillMaxWidth()
                .border(
                  1.dp,
                  if (loadReference) StudioCoral else StudioHairline,
                  RoundedCornerShape(16.dp),
                )
                .clickable { loadReference = !loadReference },
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text =
                    if (loadReference) "Include Sample Script" else "Start Blank",
                  style = MaterialTheme.typography.titleSmall,
                  color = if (loadReference) StudioCoral else StudioTextPrimary,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                  text =
                    "Pre-loads sample kinetic captions so you can preview styles before attaching a video.",
                  style = MaterialTheme.typography.bodySmall,
                  color = StudioTextSecondary,
                )
              }
              if (loadReference) {
                Spacer(Modifier.width(10.dp))
                Box(
                  modifier = Modifier.size(24.dp).clip(CircleShape).background(StudioCoral),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showNewProjectDialog = false
            scope.launch {
              val id = onCreateProject(titleInput.ifBlank { "Untitled Reel" }, loadReference)
              onOpenProject(id)
            }
          },
          shape = CircleShape,
          colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
          modifier = Modifier.testTag("confirm_create_project_button"),
        ) {
          Text("Create", fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showNewProjectDialog = false }) {
          Text("Cancel", color = StudioTextSecondary)
        }
      },
    )
  }

  // Rename Project Dialog
  renameTarget?.let { target ->
    var newName by remember(target.id) { mutableStateOf(target.title) }
    AlertDialog(
      onDismissRequest = { renameTarget = null },
      containerColor = StudioCard,
      shape = RoundedCornerShape(24.dp),
      title = { Text("Rename Project", style = MaterialTheme.typography.titleLarge, color = StudioTextPrimary) },
      text = {
        OutlinedTextField(
          value = newName,
          onValueChange = { newName = it },
          label = { Text("Project name") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = StudioCoral,
              unfocusedBorderColor = StudioBorder,
              focusedLabelColor = StudioCoral,
              cursorColor = StudioCoral,
            ),
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onRenameProject(target.id, newName)
            renameTarget = null
          },
          shape = CircleShape,
          colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
        ) {
          Text("Save", fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { renameTarget = null }) {
          Text("Cancel", color = StudioTextSecondary)
        }
      },
    )
  }

  // Delete Project Dialog
  deleteTarget?.let { target ->
    AlertDialog(
      onDismissRequest = { deleteTarget = null },
      containerColor = StudioCard,
      shape = RoundedCornerShape(24.dp),
      title = { Text("Delete Project?", style = MaterialTheme.typography.titleLarge, color = StudioTextPrimary) },
      text = {
        Text(
          "Remove \"${target.title}\" and its captions? Your original video in Gallery will not be affected.",
          style = MaterialTheme.typography.bodyMedium,
          color = StudioTextSecondary,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteProject(target.id)
            deleteTarget = null
          },
          shape = CircleShape,
          colors = ButtonDefaults.buttonColors(containerColor = StudioError, contentColor = Color.White),
        ) {
          Text("Delete", fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { deleteTarget = null }) {
          Text("Cancel", color = StudioTextSecondary)
        }
      },
    )
  }
}

@Composable
private fun ImportVideoGlassPill(
  isBusy: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(64.dp)
        .studioPressScale(enabled = !isBusy, interactionSource = interactionSource)
        .clip(CircleShape)
        .background(
          Brush.verticalGradient(
            listOf(Color(0xFF1C1C21), Color(0xFF121216))
          )
        )
        .border(
          width = 1.dp,
          brush =
            Brush.horizontalGradient(
              listOf(
                Color.White.copy(alpha = 0.14f),
                StudioCoral.copy(alpha = 0.28f),
                Color.White.copy(alpha = 0.12f),
              )
            ),
          shape = CircleShape,
        )
        .clickable(
          enabled = !isBusy,
          interactionSource = interactionSource,
          indication = ripple(bounded = true, color = Color.White),
          onClick = onClick,
        )
        .padding(horizontal = 20.dp),
  ) {
    Row(
      modifier = Modifier.align(Alignment.Center),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(38.dp)
            .clip(CircleShape)
            .background(
              Brush.linearGradient(listOf(StudioCoralLight, StudioCoral))
            ),
        contentAlignment = Alignment.Center,
      ) {
        if (isBusy) {
          CircularProgressIndicator(
            color = StudioObsidian,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
          )
        } else {
          Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = StudioObsidian,
            modifier = Modifier.size(22.dp),
          )
        }
      }
      Spacer(Modifier.width(14.dp))
      Text(
        text = if (isBusy) "Analyzing Video..." else "Import Video",
        style = MaterialTheme.typography.titleMedium,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        color = StudioTextPrimary,
        maxLines = 1,
        softWrap = false,
      )
    }

    if (!isBusy) {
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
        contentDescription = null,
        tint = StudioTextSecondary,
        modifier = Modifier.align(Alignment.CenterEnd).size(20.dp),
      )
    }
  }
}

@Composable
private fun BrowseFilesOutlinePill(
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(54.dp)
        .studioPressScale(enabled = enabled, interactionSource = interactionSource)
        .clip(CircleShape)
        .background(Color(0xFF0B0B0E).copy(alpha = 0.85f))
        .border(1.dp, StudioHairlineStrong, CircleShape)
        .clickable(
          enabled = enabled,
          interactionSource = interactionSource,
          indication = ripple(bounded = true, color = Color.White),
          onClick = onClick,
        ),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = Icons.Outlined.FolderOpen,
        contentDescription = null,
        tint = StudioTextPrimary.copy(alpha = 0.88f),
        modifier = Modifier.size(19.dp),
      )
      Spacer(Modifier.width(10.dp))
      Text(
        text = "Browse Files",
        style = MaterialTheme.typography.titleSmall,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = StudioTextPrimary,
        maxLines = 1,
        softWrap = false,
      )
    }
  }
}

@Composable
private fun MinimalProjectCard(
  project: CaptionProject,
  onClick: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  var menuExpanded by remember { mutableStateOf(false) }

  val timeSummary =
    remember(project.updatedAtMs, project.metadata.durationMs) {
      "${formatRelativeTime(project.updatedAtMs)} • ${formatDurationPadded(project.metadata.durationMs)}"
    }

  StudioGlassCard(
    modifier =
      modifier
        .fillMaxWidth()
        .studioPressScale(interactionSource = interactionSource)
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(bounded = true, color = Color.White),
          onClick = onClick,
        )
        .testTag("project_card_${project.id}"),
    cornerRadius = 22.dp,
    containerColor = Color(0xFF111115),
    borderColor = StudioHairline,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ProjectVideoThumbnail(
        videoUriString = project.videoUriString,
        modifier = Modifier.width(84.dp).height(58.dp),
        cornerRadius = 14.dp,
      )

      Spacer(Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = project.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Medium,
          color = StudioTextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = timeSummary,
          style = MaterialTheme.typography.bodyMedium,
          color = StudioTextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Box {
        IconButton(
          onClick = { menuExpanded = true },
          modifier = Modifier.testTag("project_menu_${project.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.MoreHoriz,
            contentDescription = "Project options",
            tint = StudioTextSecondary,
          )
        }
        DropdownMenu(
          expanded = menuExpanded,
          onDismissRequest = { menuExpanded = false },
          modifier = Modifier.background(StudioElevated),
        ) {
          DropdownMenuItem(
            leadingIcon = {
              Icon(
                Icons.Outlined.Edit,
                contentDescription = null,
                tint = StudioTextPrimary,
                modifier = Modifier.size(18.dp),
              )
            },
            text = { Text("Rename", color = StudioTextPrimary) },
            onClick = {
              menuExpanded = false
              onRename()
            },
          )
          DropdownMenuItem(
            leadingIcon = {
              Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = null,
                tint = StudioError,
                modifier = Modifier.size(18.dp),
              )
            },
            text = { Text("Delete", color = StudioError) },
            onClick = {
              menuExpanded = false
              onDelete()
            },
          )
        }
      }
    }
  }
}

private fun resolveUriDisplayName(context: android.content.Context, uri: Uri): String? {
  return runCatching {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
          if (cursor.moveToFirst()) {
            cursor.getString(0)?.substringBeforeLast(".")
          } else null
        }
    }
    .getOrNull()
}

private fun formatDurationPadded(ms: Long): String {
  val totalSec = (ms / 1000L).coerceAtLeast(0L)
  val m = totalSec / 60L
  val s = totalSec % 60L
  return "%02d:%02d".format(m, s)
}

private fun formatRelativeTime(timestampMs: Long): String {
  val diffMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
  val mins = diffMs / 60_000L
  val hours = mins / 60L
  val days = hours / 24L
  return when {
    mins < 1L -> "Just now"
    mins < 60L -> "${mins}m ago"
    hours < 24L -> "${hours}h ago"
    days < 7L -> "${days}d ago"
    else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMs))
  }
}
