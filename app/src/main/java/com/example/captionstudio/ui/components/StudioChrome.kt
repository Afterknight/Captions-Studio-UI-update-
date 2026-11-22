package com.example.captionstudio.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCoralDeep
import com.example.ui.theme.StudioCoralGlow
import com.example.ui.theme.StudioCoralLight
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.StudioHairline
import com.example.ui.theme.StudioHairlineStrong
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Spring-animated press feedback modifier for tactile Apple-like interactions.
 */
@Composable
fun Modifier.studioPressScale(
  enabled: Boolean = true,
  pressedScale: Float = 0.965f,
  interactionSource: MutableInteractionSource,
): Modifier {
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (isPressed && enabled) pressedScale else 1f,
      animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
      label = "studio_press_scale",
    )
  return this.scale(scale)
}

/**
 * Custom Caption Studio interlocking "CC" emblem matching the minimal obsidian icon mark.
 */
@Composable
fun StudioBrandMark(modifier: Modifier = Modifier, size: Dp = 40.dp) {
  Box(
    modifier =
      modifier
        .size(size)
        .clip(RoundedCornerShape(12.dp))
        .background(
          Brush.verticalGradient(
            listOf(Color(0xFF1D1D23), Color(0xFF121216))
          )
        )
        .border(1.dp, StudioHairlineStrong, RoundedCornerShape(12.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
      val strokeWidth = this.size.minDimension * 0.13f
      val radius = this.size.minDimension * 0.31f
      val centerY = this.size.height / 2f
      val leftCx = this.size.width * 0.36f
      val rightCx = this.size.width * 0.64f

      // Left C arc (opening right)
      drawArc(
        color = StudioTextPrimary,
        startAngle = 42f,
        sweepAngle = 276f,
        useCenter = false,
        topLeft = Offset(leftCx - radius, centerY - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
      )
      // Right C arc (opening right) in signature Studio Coral matching the launcher logo
      drawArc(
        color = StudioCoral,
        startAngle = 42f,
        sweepAngle = 276f,
        useCenter = false,
        topLeft = Offset(rightCx - radius, centerY - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
      )
    }
  }
}

/**
 * Subtle warm atmospheric glow arc drawn behind the Home screen hero, evoking luxury glass optics.
 * Uses drawWithCache so gradient shaders are allocated once per size change for smooth scrolling.
 */
@Composable
fun StudioAmbientBackdrop(modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .drawWithCache {
          val rightCenter = Offset(size.width * 0.96f, size.height * 0.44f)
          val rightRadius = size.width * 0.78f
          val rightGlow =
            Brush.radialGradient(
              colors =
                listOf(
                  StudioCoralGlow.copy(alpha = 0.30f),
                  StudioCoralGlow.copy(alpha = 0.11f),
                  Color.Transparent,
                ),
              center = rightCenter,
              radius = rightRadius.coerceAtLeast(1f),
            )
          val rimCenter = Offset(size.width * 1.04f, size.height * 0.56f)
          val arcRadius = size.width * 0.62f
          val rimSweep =
            Brush.sweepGradient(
              colors =
                listOf(
                  Color.Transparent,
                  StudioCoral.copy(alpha = 0.04f),
                  StudioCoral.copy(alpha = 0.24f),
                  StudioCoralLight.copy(alpha = 0.10f),
                  Color.Transparent,
                ),
              center = rimCenter,
            )
          val rimStroke = Stroke(width = 1.5.dp.toPx())
          val floorCenter = Offset(size.width * 0.72f, size.height * 1.02f)
          val floorRadius = size.width * 0.75f
          val floorGlow =
            Brush.radialGradient(
              colors =
                listOf(
                  StudioCoralGlow.copy(alpha = 0.22f),
                  Color.Transparent,
                ),
              center = floorCenter,
              radius = floorRadius.coerceAtLeast(1f),
            )
          onDrawBehind {
            drawCircle(brush = rightGlow, radius = rightRadius, center = rightCenter)
            drawCircle(brush = rimSweep, radius = arcRadius, center = rimCenter, style = rimStroke)
            drawCircle(brush = floorGlow, radius = floorRadius, center = floorCenter)
          }
        }
  )
}

/**
 * Minimal dark-glass circular icon button with guaranteed 48dp accessible hit area.
 */
@Composable
fun StudioCircleIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  tint: Color = StudioTextPrimary,
  containerColor: Color = StudioCard,
  badgeDotColor: Color? = null,
  visualSize: Dp = 42.dp,
) {
  val interactionSource = remember { MutableInteractionSource() }
  Box(
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .studioPressScale(enabled = enabled, pressedScale = 0.92f, interactionSource = interactionSource)
        .size(visualSize)
        .clip(CircleShape)
        .background(containerColor.copy(alpha = if (enabled) 1f else 0.45f))
        .border(1.dp, StudioHairline, CircleShape)
        .clickable(
          enabled = enabled,
          interactionSource = interactionSource,
          indication = ripple(bounded = true, color = Color.White),
          onClick = onClick,
        ),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = tint.copy(alpha = if (enabled) 1f else 0.35f),
      modifier = Modifier.size(20.dp),
    )
    if (badgeDotColor != null) {
      Box(
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(top = 8.dp, end = 8.dp)
            .size(7.dp)
            .clip(CircleShape)
            .background(badgeDotColor)
            .border(1.dp, StudioObsidian, CircleShape)
      )
    }
  }
}

/**
 * Signature full-width coral gradient pill CTA matching the reference mockup ("Export Video ->").
 */
@Composable
fun StudioCoralPillButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isLoading: Boolean = false,
  leadingIcon: ImageVector? = null,
  showTrailingArrow: Boolean = true,
  height: Dp = 56.dp,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val gradient =
    remember(enabled) {
      if (enabled) {
        Brush.horizontalGradient(listOf(StudioCoralLight, StudioCoral, StudioCoralDeep))
      } else {
        Brush.horizontalGradient(listOf(StudioElevated, StudioElevated))
      }
    }

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(height)
        .studioPressScale(enabled = enabled && !isLoading, interactionSource = interactionSource)
        .clip(CircleShape)
        .background(gradient)
        .border(
          width = 1.dp,
          color = if (enabled) Color.White.copy(alpha = 0.16f) else StudioHairline,
          shape = CircleShape,
        )
        .clickable(
          enabled = enabled && !isLoading,
          interactionSource = interactionSource,
          indication = ripple(bounded = true, color = Color.White),
          onClick = onClick,
        ),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
      modifier = Modifier.padding(horizontal = 24.dp),
    ) {
      if (isLoading) {
        CircularProgressIndicator(
          color = Color.White,
          strokeWidth = 2.dp,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
      } else if (leadingIcon != null) {
        Icon(
          imageVector = leadingIcon,
          contentDescription = null,
          tint = if (enabled) Color.White else StudioTextSecondary,
          modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(8.dp))
      }
      Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (enabled) Color.White else StudioTextSecondary,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
      )
      if (showTrailingArrow && !isLoading) {
        Spacer(Modifier.width(10.dp))
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
          contentDescription = null,
          tint = if (enabled) Color.White.copy(alpha = 0.92f) else StudioTextSecondary,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

/**
 * Clean centered-title Apple-style top bar with circular back button and optional trailing slot.
 */
@Composable
fun StudioMinimalTopBar(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  backButtonTag: String? = null,
  trailingContent: @Composable RowScope.() -> Unit = {},
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 16.dp, vertical = 10.dp),
  ) {
    StudioCircleIconButton(
      icon = Icons.AutoMirrored.Rounded.ArrowBack,
      contentDescription = "Back",
      onClick = onBack,
      modifier =
        Modifier.align(Alignment.CenterStart).let { base ->
          if (backButtonTag != null) base.then(Modifier.testTag(backButtonTag)) else base
        },
    )
    Column(
      modifier = Modifier.align(Alignment.Center).padding(horizontal = 64.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = StudioTextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = StudioTextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Row(
      modifier = Modifier.align(Alignment.CenterEnd),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      content = trailingContent,
    )
  }
}

/**
 * Minimal frosted obsidian card with hairline white rim border.
 */
@Composable
fun StudioGlassCard(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 20.dp,
  containerColor: Color = StudioCard,
  borderColor: Color = StudioHairline,
  content: @Composable BoxScope.() -> Unit,
) {
  val shape = RoundedCornerShape(cornerRadius)
  Box(
    modifier =
      modifier
        .clip(shape)
        .background(containerColor)
        .border(1.dp, borderColor, shape),
    content = content,
  )
}

/**
 * Lightweight in-memory LRU cache for extracted video thumbnails so Home cards scroll at 60fps.
 */
private object VideoThumbnailCache {
  private val cache =
    object : LruCache<String, Bitmap>(24) {
      override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

  fun get(uri: String): Bitmap? = cache.get(uri)

  fun put(uri: String, bitmap: Bitmap) {
    cache.put(uri, bitmap)
  }

  fun clear() {
    cache.evictAll()
  }
}

/** Clears cached video frame thumbnails so a pull-to-refresh re-decodes fresh previews. */
fun clearVideoThumbnailCache() {
  VideoThumbnailCache.clear()
}

/**
 * Renders a real decoded video frame thumbnail for a project card, falling back cleanly to a
 * dark atmospheric gradient frame if the project is script-only or URI is unavailable.
 */
@Composable
fun ProjectVideoThumbnail(
  videoUriString: String,
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 12.dp,
  refreshKey: Int = 0,
) {
  val context = LocalContext.current
  val bitmap by
    produceState<Bitmap?>(
      initialValue = VideoThumbnailCache.get(videoUriString),
      key1 = videoUriString,
      key2 = refreshKey,
    ) {
      if (videoUriString.isBlank()) {
        value = null
        return@produceState
      }
      val existing = VideoThumbnailCache.get(videoUriString)
      if (existing != null && !existing.isRecycled) {
        value = existing
        return@produceState
      }
      val decoded =
        withContext(Dispatchers.IO) {
          val retriever = MediaMetadataRetriever()
          try {
            retriever.setDataSource(context, Uri.parse(videoUriString))
            val raw =
              retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            raw?.let { frame ->
              val maxDim = 480
              val scale = minOf(maxDim.toFloat() / frame.width, maxDim.toFloat() / frame.height, 1f)
              if (scale < 1f) {
                val scaled =
                  Bitmap.createScaledBitmap(
                    frame,
                    (frame.width * scale).toInt().coerceAtLeast(1),
                    (frame.height * scale).toInt().coerceAtLeast(1),
                    true,
                  )
                if (scaled !== frame) frame.recycle()
                scaled
              } else {
                frame
              }
            }
          } catch (_: Throwable) {
            null
          } finally {
            runCatching { retriever.release() }
          }
        }
      if (decoded != null) {
        VideoThumbnailCache.put(videoUriString, decoded)
        value = decoded
      }
    }

  val shape = RoundedCornerShape(cornerRadius)
  Box(
    modifier =
      modifier
        .clip(shape)
        .background(
          Brush.linearGradient(
            listOf(Color(0xFF23222A), Color(0xFF15151B), Color(0xFF2A1C1B))
          )
        )
        .border(1.dp, StudioHairline, shape),
    contentAlignment = Alignment.Center,
  ) {
    val bmp = bitmap
    if (bmp != null && !bmp.isRecycled) {
      Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Icon(
        imageVector = Icons.Rounded.Movie,
        contentDescription = null,
        tint = StudioCoral.copy(alpha = 0.78f),
        modifier = Modifier.size(22.dp),
      )
    }
  }
}

/**
 * Compact dark pill badge (e.g., aspect ratio, status).
 */
@Composable
fun StudioSubtlePill(
  text: String,
  modifier: Modifier = Modifier,
  tint: Color = StudioTextSecondary,
  containerColor: Color = StudioElevated,
) {
  Surface(
    color = containerColor,
    shape = CircleShape,
    modifier = modifier.border(1.dp, StudioHairline, CircleShape),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = tint,
      maxLines = 1,
      softWrap = false,
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
    )
  }
}
