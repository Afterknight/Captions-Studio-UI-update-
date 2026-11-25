package com.example.captionstudio.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.captionstudio.domain.model.CaptionProject
import com.example.captionstudio.domain.model.ProjectSortOption
import com.example.captionstudio.ui.components.StudioAmbientBackdrop
import com.example.captionstudio.ui.components.StudioBrandMark
import com.example.captionstudio.ui.components.StudioProTopBarPill
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import kotlinx.coroutines.launch

private enum class GalleryListState {
  EMPTY_LIBRARY,
  NO_SEARCH_MATCHES,
  HAS_RESULTS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectGalleryScreen(
  projects: List<CaptionProject>,
  totalProjectCount: Int,
  searchQuery: String,
  sortOption: ProjectSortOption,
  isRefreshing: Boolean,
  thumbnailRefreshKey: Int,
  isBusy: Boolean,
  isProUnlocked: Boolean = false,
  onOpenProSheet: () -> Unit = {},
  onSearchQueryChange: (String) -> Unit,
  onSortOptionChange: (ProjectSortOption) -> Unit,
  onRefreshProjects: () -> Unit,
  onOpenProject: (String) -> Unit,
  onImportVideo: suspend (Uri, String) -> String,
  onCreateProject: suspend (String, Boolean) -> String,
  onRenameProject: (String, String) -> Unit,
  onDeleteProject: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var showNewProjectDialog by remember { mutableStateOf(false) }
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

  val pullToRefreshState = rememberPullToRefreshState()

  val listState =
    when {
      totalProjectCount == 0 -> GalleryListState.EMPTY_LIBRARY
      projects.isEmpty() -> GalleryListState.NO_SEARCH_MATCHES
      else -> GalleryListState.HAS_RESULTS
    }

  Scaffold(
    containerColor = StudioObsidian,
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().background(StudioObsidian),
      contentAlignment = Alignment.TopCenter,
    ) {
      StudioAmbientBackdrop()

      Column(
        modifier =
          Modifier.fillMaxSize()
            .widthIn(max = 1040.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
      ) {
        // Minimal Gallery Header + Persistent Top Search Bar
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              StudioBrandMark(size = 40.dp)
              Spacer(Modifier.width(12.dp))
              Column {
                Text(
                  text = stringResource(R.string.app_name),
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.SemiBold,
                  color = StudioTextPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = stringResource(R.string.gallery_subtitle),
                  style = MaterialTheme.typography.labelSmall,
                  color = StudioTextSecondary,
                  maxLines = 1,
                )
              }
            }

            StudioProTopBarPill(
              isProUnlocked = isProUnlocked,
              onClick = onOpenProSheet,
            )
          }

          GallerySearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
          )
        }

        // Pull-to-Refresh Project Gallery Surface
        PullToRefreshBox(
          isRefreshing = isRefreshing,
          onRefresh = onRefreshProjects,
          state = pullToRefreshState,
          indicator = {
            PullToRefreshDefaults.Indicator(
              state = pullToRefreshState,
              isRefreshing = isRefreshing,
              containerColor = StudioElevated,
              color = StudioCoral,
              modifier = Modifier.align(Alignment.TopCenter),
            )
          },
          modifier = Modifier.weight(1f).fillMaxWidth().testTag("project_gallery_pull_to_refresh"),
        ) {
          BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val gridColumns =
              if (maxWidth >= 600.dp) {
                GridCells.Adaptive(minSize = 190.dp)
              } else {
                GridCells.Fixed(2)
              }

            LazyVerticalGrid(
              columns = gridColumns,
              modifier = Modifier.fillMaxSize().testTag("project_gallery_grid"),
              contentPadding =
                PaddingValues(
                  start = 20.dp,
                  end = 20.dp,
                  top = 6.dp,
                  bottom = innerPadding.calculateBottomPadding() + 28.dp,
                ),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              // Compact Hero Quick-Create Card (smoothly collapses while searching)
              item(key = "gallery_hero_create", span = { GridItemSpan(maxLineSpan) }) {
                androidx.compose.animation.AnimatedVisibility(
                  visible = contentVisible && searchQuery.isBlank(),
                  enter =
                    fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                      expandVertically(tween(320, easing = FastOutSlowInEasing)),
                  exit =
                    fadeOut(tween(180)) +
                      shrinkVertically(tween(220, easing = FastOutSlowInEasing)),
                ) {
                  GalleryHeroCreateCard(
                    isBusy = isBusy,
                    onImportVideo = {
                      photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                      )
                    },
                    onBrowseFiles = { documentVideoLauncher.launch(arrayOf("video/*")) },
                    onCreateScript = { showNewProjectDialog = true },
                    modifier = Modifier.padding(bottom = 6.dp),
                  )
                }
              }

              // Recent Projects Header & Sort Dropdown Row
              item(key = "gallery_toolbar", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Text(
                      text = stringResource(R.string.gallery_recent_projects),
                      style = MaterialTheme.typography.titleLarge,
                      fontWeight = FontWeight.SemiBold,
                      color = StudioTextPrimary,
                    )
                    AnimatedContent(
                      targetState =
                        if (searchQuery.isNotBlank() && totalProjectCount > 0) {
                          "${projects.size}/$totalProjectCount"
                        } else if (totalProjectCount > 0) {
                          "$totalProjectCount"
                        } else {
                          ""
                        },
                      transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.85f, animationSpec = tween(180)))
                          .togetherWith(fadeOut(tween(120)))
                      },
                      label = "gallery_count_badge",
                    ) { countText ->
                      if (countText.isNotEmpty()) {
                        Surface(
                          color = StudioElevated,
                          shape = CircleShape,
                        ) {
                          Text(
                            text = countText,
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioTextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                          )
                        }
                      }
                    }
                  }

                  GallerySortDropdown(
                    selectedSort = sortOption,
                    onSelectSort = onSortOptionChange,
                  )
                }
              }

              // Empty / No-Match States with AnimatedContent, or Populated Visual Gallery Grid
              if (listState != GalleryListState.HAS_RESULTS) {
                item(key = "gallery_empty_or_nomatch", span = { GridItemSpan(maxLineSpan) }) {
                  AnimatedContent(
                    targetState = listState,
                    transitionSpec = {
                      (fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it / 14 } +
                        scaleIn(initialScale = 0.97f, animationSpec = tween(240)))
                        .togetherWith(
                          fadeOut(tween(160)) + scaleOut(targetScale = 0.97f, animationSpec = tween(160))
                        )
                    },
                    label = "gallery_empty_state_transition",
                  ) { state ->
                    when (state) {
                      GalleryListState.EMPTY_LIBRARY ->
                        GalleryEmptyStateCard(
                          onLaunchDemoProject = {
                            scope.launch {
                              val id = onCreateProject("Creator Demo Reel", true)
                              onOpenProject(id)
                            }
                          },
                        )
                      GalleryListState.NO_SEARCH_MATCHES ->
                        GalleryNoSearchResultsCard(
                          query = searchQuery,
                          onClearSearch = { onSearchQueryChange("") },
                        )
                      GalleryListState.HAS_RESULTS -> Spacer(Modifier.height(1.dp))
                    }
                  }
                }
              } else {
                items(projects, key = { it.id }) { project ->
                  ProjectGalleryCard(
                    project = project,
                    thumbnailRefreshKey = thumbnailRefreshKey,
                    onClick = { onOpenProject(project.id) },
                    onRename = { renameTarget = project },
                    onDelete = { deleteTarget = project },
                    modifier = Modifier.animateItem(),
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  if (showNewProjectDialog) {
    NewScriptProjectDialog(
      onDismiss = { showNewProjectDialog = false },
      onConfirm = { title, loadRef ->
        showNewProjectDialog = false
        scope.launch {
          val id = onCreateProject(title, loadRef)
          onOpenProject(id)
        }
      },
    )
  }

  renameTarget?.let { target ->
    RenameProjectDialog(
      project = target,
      onDismiss = { renameTarget = null },
      onConfirm = { newName ->
        onRenameProject(target.id, newName)
        renameTarget = null
      },
    )
  }

  deleteTarget?.let { target ->
    DeleteProjectDialog(
      project = target,
      onDismiss = { deleteTarget = null },
      onConfirm = {
        onDeleteProject(target.id)
        deleteTarget = null
      },
    )
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
