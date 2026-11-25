package com.example.captionstudio.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.captionstudio.domain.model.CaptionProject
import com.example.captionstudio.domain.model.ProjectSortOption
import com.example.captionstudio.domain.model.SafeRegion
import com.example.captionstudio.domain.model.VideoAnalysisReport
import com.example.captionstudio.engine.KineticCompositionRenderer
import com.example.captionstudio.engine.LocalCompositionEngine
import com.example.captionstudio.presets.PresetCatalog
import com.example.captionstudio.ui.components.ProjectVideoThumbnail
import com.example.captionstudio.ui.components.StudioGlassCard
import com.example.captionstudio.ui.components.studioPressScale
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralContainer
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioDisplayFont
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioError
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioHairlineStrong
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun GallerySearchBar(
  query: String,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val focusManager = LocalFocusManager.current
  var isFocused by remember { mutableStateOf(false) }

  val borderColor =
    if (isFocused || query.isNotEmpty()) StudioCoral.copy(alpha = 0.55f) else StudioHairlineStrong

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(50.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(StudioCard)
        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
        .padding(start = 15.dp, end = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = Icons.Rounded.Search,
      contentDescription = stringResource(R.string.gallery_search_cd),
      tint = if (isFocused || query.isNotEmpty()) StudioCoral else StudioTextSecondary,
      modifier = Modifier.size(20.dp),
    )
    Spacer(Modifier.width(10.dp))
    Box(
      modifier = Modifier.weight(1f),
      contentAlignment = Alignment.CenterStart,
    ) {
      if (query.isEmpty()) {
        Text(
          text = stringResource(R.string.gallery_search_placeholder),
          style = MaterialTheme.typography.bodyMedium,
          color = StudioTextMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = StudioTextPrimary),
        cursorBrush = SolidColor(StudioCoral),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier =
          Modifier.fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .testTag("gallery_search_input"),
      )
    }

    AnimatedContent(
      targetState = query.isNotEmpty(),
      transitionSpec = {
        (fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(150)))
          .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.8f, animationSpec = tween(120)))
      },
      label = "gallery_search_clear_anim",
    ) { hasQuery ->
      if (hasQuery) {
        IconButton(
          onClick = {
            onQueryChange("")
            focusManager.clearFocus()
          },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("gallery_search_clear_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.gallery_search_clear_cd),
            tint = StudioTextSecondary,
            modifier = Modifier.size(18.dp),
          )
        }
      } else {
        Spacer(Modifier.width(8.dp))
      }
    }
  }
}

@Composable
internal fun GallerySortDropdown(
  selectedSort: ProjectSortOption,
  onSelectSort: (ProjectSortOption) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val chevronRotation by
    animateFloatAsState(
      targetValue = if (expanded) 180f else 0f,
      animationSpec = tween(220, easing = FastOutSlowInEasing),
      label = "sort_chevron_rotation",
    )

  Box(modifier = modifier) {
    Row(
      modifier =
        Modifier.minimumInteractiveComponentSize()
          .clip(CircleShape)
          .background(StudioElevated)
          .border(
            1.dp,
            if (expanded) StudioCoral.copy(alpha = 0.55f) else StudioHairlineStrong,
            CircleShape,
          )
          .clickable { expanded = true }
          .padding(horizontal = 13.dp, vertical = 8.dp)
          .testTag("gallery_sort_button"),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.Sort,
        contentDescription = stringResource(R.string.gallery_sort_cd),
        tint = StudioCoral,
        modifier = Modifier.size(16.dp),
      )
      Spacer(Modifier.width(6.dp))
      AnimatedContent(
        targetState = selectedSort,
        transitionSpec = {
          (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 })
            .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 3 })
        },
        label = "sort_label_anim",
      ) { option ->
        Text(
          text = sortOptionLabel(option),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Medium,
          color = StudioTextPrimary,
          maxLines = 1,
          softWrap = false,
        )
      }
      Spacer(Modifier.width(4.dp))
      Icon(
        imageVector = Icons.Rounded.KeyboardArrowDown,
        contentDescription = null,
        tint = StudioTextSecondary,
        modifier = Modifier.size(16.dp).rotate(chevronRotation),
      )
    }

    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      shape = RoundedCornerShape(18.dp),
      containerColor = StudioElevated,
      modifier = Modifier.border(1.dp, StudioHairlineStrong, RoundedCornerShape(18.dp)),
    ) {
      ProjectSortOption.entries.forEach { option ->
        val isSelected = option == selectedSort
        val optionTag =
          when (option) {
            ProjectSortOption.DATE_CREATED -> "sort_option_date_created"
            ProjectSortOption.NAME -> "sort_option_name"
            ProjectSortOption.DURATION -> "sort_option_duration"
          }
        DropdownMenuItem(
          modifier = Modifier.testTag(optionTag),
          leadingIcon = {
            Icon(
              imageVector = sortOptionIcon(option),
              contentDescription = null,
              tint = if (isSelected) StudioCoral else StudioTextSecondary,
              modifier = Modifier.size(18.dp),
            )
          },
          text = {
            Text(
              text = sortOptionLabel(option),
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
              color = if (isSelected) StudioCoral else StudioTextPrimary,
            )
          },
          trailingIcon =
            if (isSelected) {
              {
                Icon(
                  imageVector = Icons.Rounded.Check,
                  contentDescription = null,
                  tint = StudioCoral,
                  modifier = Modifier.size(17.dp),
                )
              }
            } else {
              null
            },
          onClick = {
            expanded = false
            onSelectSort(option)
          },
        )
      }
    }
  }
}

@Composable
internal fun GalleryHeroCreateCard(
  isBusy: Boolean,
  onImportVideo: () -> Unit,
  onBrowseFiles: () -> Unit,
  onCreateScript: () -> Unit,
  modifier: Modifier = Modifier,
) {
  StudioGlassCard(
    modifier = modifier.fillMaxWidth(),
    cornerRadius = 24.dp,
    containerColor = StudioCard,
    borderColor = StudioHairlineStrong,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(
            Brush.linearGradient(
              listOf(StudioCoralContainer.copy(alpha = 0.22f), Color.Transparent)
            )
          )
          .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text =
            buildAnnotatedString {
              withStyle(SpanStyle(color = StudioTextPrimary)) {
                append(stringResource(R.string.gallery_hero_lead))
                append(" ")
              }
              withStyle(SpanStyle(color = StudioCoral)) {
                append(stringResource(R.string.gallery_hero_accent))
              }
            },
          fontFamily = StudioDisplayFont,
          fontWeight = FontWeight.Bold,
          fontSize = 26.sp,
          lineHeight = 30.sp,
          letterSpacing = (-0.6).sp,
        )
        Text(
          text = stringResource(R.string.gallery_hero_tagline),
          style = MaterialTheme.typography.bodyMedium,
          color = StudioTextSecondary,
        )
      }

      ImportVideoCompactPill(
        isBusy = isBusy,
        onClick = onImportVideo,
        modifier = Modifier.testTag("import_video_button"),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        SecondaryActionPill(
          icon = Icons.Outlined.FolderOpen,
          label = stringResource(R.string.gallery_browse_files),
          enabled = !isBusy,
          onClick = onBrowseFiles,
          modifier = Modifier.weight(1f).testTag("browse_files_button"),
        )
        SecondaryActionPill(
          icon = Icons.Rounded.Add,
          label = stringResource(R.string.gallery_start_script),
          enabled = !isBusy,
          onClick = onCreateScript,
          modifier = Modifier.weight(1f).testTag("new_project_button"),
        )
      }
    }
  }
}

@Composable
private fun ImportVideoCompactPill(
  isBusy: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(54.dp)
        .studioPressScale(enabled = !isBusy, interactionSource = interactionSource)
        .clip(CircleShape)
        .background(
          Brush.verticalGradient(listOf(Color(0xFF1F1F26), Color(0xFF131318)))
        )
        .border(
          width = 1.dp,
          brush =
            Brush.horizontalGradient(
              listOf(
                Color.White.copy(alpha = 0.14f),
                StudioCoral.copy(alpha = 0.36f),
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
        .padding(horizontal = 16.dp),
  ) {
    Row(
      modifier = Modifier.align(Alignment.Center),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(32.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(StudioCoralLight, StudioCoral))),
        contentAlignment = Alignment.Center,
      ) {
        if (isBusy) {
          CircularProgressIndicator(
            color = StudioObsidian,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp),
          )
        } else {
          Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = StudioObsidian,
            modifier = Modifier.size(19.dp),
          )
        }
      }
      Spacer(Modifier.width(12.dp))
      Text(
        text =
          if (isBusy) stringResource(R.string.gallery_analyzing_video)
          else stringResource(R.string.gallery_import_video),
        style = MaterialTheme.typography.titleMedium,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
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
        modifier = Modifier.align(Alignment.CenterEnd).size(18.dp),
      )
    }
  }
}

@Composable
private fun SecondaryActionPill(
  icon: ImageVector,
  label: String,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  Row(
    modifier =
      modifier
        .height(48.dp)
        .studioPressScale(enabled = enabled, interactionSource = interactionSource)
        .clip(CircleShape)
        .background(StudioObsidian.copy(alpha = 0.72f))
        .border(1.dp, StudioHairlineStrong, CircleShape)
        .clickable(
          enabled = enabled,
          interactionSource = interactionSource,
          indication = ripple(bounded = true, color = Color.White),
          onClick = onClick,
        )
        .padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = StudioTextPrimary.copy(alpha = 0.88f),
      modifier = Modifier.size(17.dp),
    )
    Spacer(Modifier.width(7.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Medium,
      color = StudioTextPrimary,
      maxLines = 1,
      softWrap = false,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
internal fun ProjectGalleryCard(
  project: CaptionProject,
  thumbnailRefreshKey: Int,
  onClick: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  var menuExpanded by remember { mutableStateOf(false) }

  val durationLabel = remember(project.metadata.durationMs) { formatDurationPadded(project.metadata.durationMs) }
  val createdLabel = remember(project.createdAtMs) { formatCreatedDate(project.createdAtMs) }

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
    cornerRadius = 20.dp,
    containerColor = StudioCard,
    borderColor = StudioHairline,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .aspectRatio(16f / 10f)
            .padding(6.dp),
      ) {
        ProjectVideoThumbnail(
          videoUriString = project.videoUriString,
          cornerRadius = 15.dp,
          refreshKey = thumbnailRefreshKey,
          modifier = Modifier.fillMaxSize(),
        )

        // Subtle bottom scrim so duration badge stays legible over bright frames
        Box(
          modifier =
            Modifier.fillMaxSize()
              .clip(RoundedCornerShape(15.dp))
              .background(
                Brush.verticalGradient(
                  colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.62f))
                )
              )
        )

        // Zeigarnik Draft Status Badge
        val hasBehindSubject =
          remember(project.compositions) { project.compositions.any { it.hasBehindSubjectElements() } }
        val (badgeLabel, badgeTint) =
          when {
            project.videoUriString.isBlank() ->
              stringResource(R.string.gallery_badge_script) to StudioCoralLight
            hasBehindSubject ->
              stringResource(R.string.gallery_badge_behind_depth) to StudioCoralLight
            project.compositions.isNotEmpty() ->
              stringResource(R.string.gallery_badge_ready_export) to StudioSuccess
            else ->
              stringResource(R.string.gallery_badge_tap_style) to StudioCoral
          }

        Surface(
          color = StudioObsidian.copy(alpha = 0.78f),
          shape = CircleShape,
          modifier =
            Modifier.align(Alignment.TopStart)
              .padding(8.dp)
              .border(1.dp, badgeTint.copy(alpha = 0.45f), CircleShape),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          ) {
            if (hasBehindSubject && project.videoUriString.isNotBlank()) {
              Icon(
                imageVector = Icons.Rounded.Layers,
                contentDescription = null,
                tint = badgeTint,
                modifier = Modifier.size(11.dp),
              )
            }
            Text(
              text = badgeLabel,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Medium,
              color = badgeTint,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        // Frosted duration pill
        Surface(
          color = StudioObsidian.copy(alpha = 0.78f),
          shape = CircleShape,
          modifier =
            Modifier.align(Alignment.BottomEnd)
              .padding(8.dp)
              .border(1.dp, StudioHairline, CircleShape),
        ) {
          Text(
            text = durationLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = StudioTextPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = project.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = StudioTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(Modifier.height(2.dp))
          Text(
            text = createdLabel,
            style = MaterialTheme.typography.labelSmall,
            color = StudioTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Box {
          IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("project_menu_${project.id}"),
          ) {
            Icon(
              imageVector = Icons.Rounded.MoreHoriz,
              contentDescription = stringResource(R.string.gallery_project_options_cd),
              tint = StudioTextSecondary,
              modifier = Modifier.size(19.dp),
            )
          }
          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = StudioElevated,
            modifier = Modifier.border(1.dp, StudioHairlineStrong, RoundedCornerShape(16.dp)),
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
              text = { Text(stringResource(R.string.gallery_project_rename), color = StudioTextPrimary) },
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
              text = { Text(stringResource(R.string.gallery_project_delete), color = StudioError) },
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
}

@Composable
internal fun GalleryEmptyStateCard(
  onLaunchDemoProject: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val renderer = remember(context) { KineticCompositionRenderer(context) }
  val demoPresets = remember { listOf("editorial", "impact", "neon") }
  var selectedDemoPresetId by remember { mutableStateOf("editorial") }
  var loopPlayheadMs by remember { mutableLongStateOf(650L) }

  LaunchedEffect(Unit) {
    val startNs = System.nanoTime()
    while (true) {
      withFrameMillis {
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        loopPlayheadMs = elapsedMs % 2800L
      }
    }
  }

  val demoComp =
    remember(selectedDemoPresetId) {
      val preset = PresetCatalog.findById(selectedDemoPresetId)
      LocalCompositionEngine.generateCompositions(
        transcript = "Why do many creators add crazy text",
        wordTimings = emptyList(),
        totalDurationMs = 2800L,
        preset = preset,
        analysisReport = VideoAnalysisReport(),
      ).firstOrNull()?.copy(
        startMs = 0L,
        endMs = 2800L,
        anchorXFraction = 0.5f,
        anchorYFraction = 0.52f,
        region = SafeRegion.CENTER,
      )
    }

  StudioGlassCard(
    modifier = modifier.fillMaxWidth(),
    cornerRadius = 24.dp,
    borderColor = StudioCoral.copy(alpha = 0.35f),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.gallery_onboarding_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = StudioTextPrimary,
          )
          Spacer(Modifier.height(2.dp))
          Text(
            text = stringResource(R.string.gallery_onboarding_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = StudioTextSecondary,
          )
        }
        Surface(
          color = StudioCoralContainer.copy(alpha = 0.65f),
          shape = CircleShape,
        ) {
          Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = StudioCoralLight,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
          )
        }
      }

      // Live 60fps Interactive Kinetic Stage Preview (<30s Aha Moment)
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
              Brush.radialGradient(
                colors = listOf(Color(0xFF282034), Color(0xFF13131A), Color(0xFF0A0A0E))
              )
            )
            .border(1.dp, StudioHairlineStrong, RoundedCornerShape(18.dp)),
      ) {
        if (demoComp != null) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
              renderer.drawAtTime(
                canvas = canvas.nativeCanvas,
                canvasWidth = size.width.toInt().coerceAtLeast(1),
                canvasHeight = size.height.toInt().coerceAtLeast(1),
                compositions = listOf(demoComp),
                positionMs = loopPlayheadMs,
              )
            }
          }
        }
      }

      // 3 Tappable Instant Style Chips
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        demoPresets.forEach { presetId ->
          val preset = remember(presetId) { PresetCatalog.findById(presetId) }
          val isSelected = selectedDemoPresetId == presetId
          Surface(
            color = if (isSelected) StudioCoralContainer else StudioElevated,
            shape = CircleShape,
            modifier =
              Modifier.weight(1f)
                .height(48.dp)
                .border(
                  1.dp,
                  if (isSelected) StudioCoral else StudioHairlineStrong,
                  CircleShape,
                )
                .clickable { selectedDemoPresetId = presetId }
                .testTag("onboarding_style_chip_$presetId"),
          ) {
            Box(
              modifier = Modifier.padding(horizontal = 8.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = preset.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) StudioCoralLight else StudioTextPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }

      Button(
        onClick = onLaunchDemoProject,
        shape = CircleShape,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = StudioCoral,
            contentColor = Color.White,
          ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier =
          Modifier.fillMaxWidth()
            .height(48.dp)
            .testTag("onboarding_launch_demo_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.PlayArrow,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.gallery_onboarding_launch_demo),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
internal fun GalleryNoSearchResultsCard(
  query: String,
  onClearSearch: () -> Unit,
  modifier: Modifier = Modifier,
) {
  StudioGlassCard(
    modifier = modifier.fillMaxWidth(),
    cornerRadius = 22.dp,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier =
          Modifier.size(50.dp)
            .clip(CircleShape)
            .background(StudioElevated)
            .border(1.dp, StudioHairlineStrong, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.SearchOff,
          contentDescription = null,
          tint = StudioTextSecondary,
          modifier = Modifier.size(23.dp),
        )
      }
      Text(
        text = stringResource(R.string.gallery_no_results_title),
        style = MaterialTheme.typography.titleMedium,
        color = StudioTextPrimary,
      )
      Text(
        text = stringResource(R.string.gallery_no_results_subtitle, query.trim()),
        style = MaterialTheme.typography.bodyMedium,
        color = StudioTextSecondary,
      )
      Spacer(Modifier.height(2.dp))
      Button(
        onClick = onClearSearch,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = StudioElevated, contentColor = StudioCoral),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("gallery_clear_search_empty_button"),
      ) {
        Text(stringResource(R.string.gallery_clear_search), fontWeight = FontWeight.Medium)
      }
    }
  }
}

@Composable
internal fun NewScriptProjectDialog(
  onDismiss: () -> Unit,
  onConfirm: (title: String, loadReference: Boolean) -> Unit,
) {
  var titleInput by remember { mutableStateOf("Untitled Reel") }
  var loadReference by remember { mutableStateOf(true) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = StudioCard,
    shape = RoundedCornerShape(24.dp),
    title = {
      Text(
        stringResource(R.string.gallery_dialog_new_title),
        style = MaterialTheme.typography.titleLarge,
        color = StudioTextPrimary,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
          value = titleInput,
          onValueChange = { titleInput = it },
          label = { Text(stringResource(R.string.gallery_dialog_project_name)) },
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
                  if (loadReference) stringResource(R.string.gallery_dialog_sample_title)
                  else stringResource(R.string.gallery_dialog_blank_title),
                style = MaterialTheme.typography.titleSmall,
                color = if (loadReference) StudioCoral else StudioTextPrimary,
              )
              Spacer(Modifier.height(3.dp))
              Text(
                text = stringResource(R.string.gallery_dialog_sample_desc),
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
        onClick = { onConfirm(titleInput.ifBlank { "Untitled Reel" }, loadReference) },
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
        modifier = Modifier.testTag("confirm_create_project_button"),
      ) {
        Text(stringResource(R.string.gallery_dialog_create), fontWeight = FontWeight.SemiBold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.gallery_dialog_cancel), color = StudioTextSecondary)
      }
    },
  )
}

@Composable
internal fun RenameProjectDialog(
  project: CaptionProject,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var newName by remember(project.id) { mutableStateOf(project.title) }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = StudioCard,
    shape = RoundedCornerShape(24.dp),
    title = {
      Text(
        stringResource(R.string.gallery_dialog_rename_title),
        style = MaterialTheme.typography.titleLarge,
        color = StudioTextPrimary,
      )
    },
    text = {
      OutlinedTextField(
        value = newName,
        onValueChange = { newName = it },
        label = { Text(stringResource(R.string.gallery_dialog_project_name)) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = StudioCoral,
            unfocusedBorderColor = StudioBorder,
            focusedLabelColor = StudioCoral,
            cursorColor = StudioCoral,
          ),
        modifier = Modifier.fillMaxWidth().testTag("rename_project_input"),
      )
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(newName) },
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = StudioCoral, contentColor = Color.White),
      ) {
        Text(stringResource(R.string.gallery_dialog_save), fontWeight = FontWeight.SemiBold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.gallery_dialog_cancel), color = StudioTextSecondary)
      }
    },
  )
}

@Composable
internal fun DeleteProjectDialog(
  project: CaptionProject,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = StudioCard,
    shape = RoundedCornerShape(24.dp),
    title = {
      Text(
        stringResource(R.string.gallery_dialog_delete_title),
        style = MaterialTheme.typography.titleLarge,
        color = StudioTextPrimary,
      )
    },
    text = {
      Text(
        stringResource(R.string.gallery_dialog_delete_message, project.title),
        style = MaterialTheme.typography.bodyMedium,
        color = StudioTextSecondary,
      )
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = StudioError, contentColor = Color.White),
      ) {
        Text(stringResource(R.string.gallery_project_delete), fontWeight = FontWeight.SemiBold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.gallery_dialog_cancel), color = StudioTextSecondary)
      }
    },
  )
}

@Composable
private fun sortOptionLabel(option: ProjectSortOption): String =
  when (option) {
    ProjectSortOption.DATE_CREATED -> stringResource(R.string.gallery_sort_date_created)
    ProjectSortOption.NAME -> stringResource(R.string.gallery_sort_name)
    ProjectSortOption.DURATION -> stringResource(R.string.gallery_sort_duration)
  }

private fun sortOptionIcon(option: ProjectSortOption): ImageVector =
  when (option) {
    ProjectSortOption.DATE_CREATED -> Icons.Rounded.CalendarToday
    ProjectSortOption.NAME -> Icons.Rounded.SortByAlpha
    ProjectSortOption.DURATION -> Icons.Rounded.Timer
  }

private fun formatDurationPadded(ms: Long): String {
  val totalSec = (ms / 1000L).coerceAtLeast(0L)
  val m = totalSec / 60L
  val s = totalSec % 60L
  return "%02d:%02d".format(m, s)
}

private fun formatCreatedDate(timestampMs: Long): String {
  val diffMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
  val mins = diffMs / 60_000L
  val hours = mins / 60L
  val days = hours / 24L
  return when {
    mins < 1L -> "Just now"
    mins < 60L -> "${mins}m ago"
    hours < 24L -> "${hours}h ago"
    days < 7L -> "${days}d ago"
    else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestampMs))
  }
}
