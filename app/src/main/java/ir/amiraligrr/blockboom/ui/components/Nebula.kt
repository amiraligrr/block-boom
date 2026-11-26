package ir.amiraligrr.blockboom.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors

/**
 * The small handful of surfaces every redesigned screen shares.
 *
 * Keeping these together is deliberate: the old app had a different top bar,
 * a different back button and a different card recipe per screen. A player
 * should learn the navigation once.
 */

@Composable
fun NebulaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val c = joyThemeColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (onBack != null) {
            NebulaIconButton(
                icon = AppIcon.BACK,
                contentDescription = "بازگشت",
                onClick = onBack
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = c.textPrimary,
                    fontWeight = FontWeight.Black
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun NebulaIconButton(
    icon: AppIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    tint: Color? = null,
    size: Dp = 44.dp
) {
    val c = joyThemeColors()
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(containerColor ?: c.surface)
            .border(1.dp, c.cardBorder, RoundedCornerShape(size * 0.32f))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        AppIconView(
            icon = icon,
            tint = tint ?: c.textPrimary,
            size = size * 0.46f,
            strokeWidth = 2.1.dp
        )
    }
}

/**
 * A clipped, gradient avatar drawn from the player's own name.
 *
 * No network image and no fabricated avatar: the initials are the player's,
 * and the level badge is the real campaign level.
 */
@Composable
fun NebulaAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    level: Int? = null
) {
    val c = joyThemeColors()
    val initials = remember(name) {
        name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().toString() }
            .ifBlank { "ب" }
    }
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF9B7BFF), Color(0xFF6D46E8), Color(0xFF5B3CC4))
                    )
                )
                .border(2.dp, Color(0xFF42D9FF).copy(alpha = 0.42f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = Color.White,
                fontSize = (size.value * 0.31f).sp,
                fontWeight = FontWeight.Black
            )
        }
        if (level != null && level > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(50))
                    .background(c.surface)
                    .border(1.dp, c.emerald.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = level.toString(),
                    color = c.emerald,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun NebulaPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    accent: Color? = null
) {
    val c = joyThemeColors()
    val ink = accent ?: c.amber
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(c.surface.copy(alpha = if (c.isDark) 0.86f else 1f))
            .border(1.dp, c.cardBorder, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) {
            AppIconView(icon = icon, tint = ink, size = 14.dp, strokeWidth = 2.1.dp)
        }
        Text(
            text = text,
            color = ink,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
            maxLines = 1
        )
    }
}

@Composable
fun NebulaSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val c = joyThemeColors()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = c.textPrimary,
                    fontWeight = FontWeight.Black
                )
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted)
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun NebulaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    height: Dp = 8.dp
) {
    val c = joyThemeColors()
    val safe = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(c.track)
            .border(1.dp, c.trackBorder, RoundedCornerShape(50))
            .padding(1.5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(safe)
                .height(height - 3.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(accent ?: c.emerald, c.sky)
                    )
                )
        )
    }
}

@Composable
fun NebulaNotice(
    text: String,
    modifier: Modifier = Modifier,
    icon: AppIcon = AppIcon.BOLT,
    accent: Color? = null
) {
    val c = joyThemeColors()
    val ink = accent ?: c.sky
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ink.copy(alpha = if (c.isDark) 0.12f else 0.08f))
            .border(1.dp, ink.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(25.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ink.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            AppIconView(icon = icon, tint = ink, size = 14.dp, strokeWidth = 2.15.dp)
        }
        Text(
            text = text,
            color = c.textSecondary,
            style = MaterialTheme.typography.labelSmall.copy(
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun NebulaEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: AppIcon = AppIcon.TROPHY,
    action: (@Composable () -> Unit)? = null
) {
    val c = joyThemeColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.chip)
                .border(1.dp, c.cardBorder, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            AppIconView(icon = icon, tint = c.textMuted, size = 24.dp, strokeWidth = 1.9.dp)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                color = c.textPrimary,
                fontWeight = FontWeight.Black
            ),
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted),
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

/**
 * A bottom sheet that belongs to the same visual family as the rest of the app.
 *
 * Built on [Dialog] rather than a new navigation destination on purpose: the
 * content is a shortcut list, not a screen, and it should not become a place
 * the back stack can strand the player.
 */
@Composable
fun NebulaBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    val c = joyThemeColors()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(280)
                ) + fadeIn(tween(180))
            ) {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(c.gradientCardElevated)
                        .border(
                            1.dp,
                            c.cardBorder.copy(alpha = 0.8f),
                            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                        )
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 18.dp)
                        .padding(top = 10.dp, bottom = 20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(c.textMuted.copy(alpha = 0.42f))
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = c.textPrimary,
                                    fontWeight = FontWeight.Black
                                )
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted)
                                )
                            }
                        }
                        NebulaIconButton(
                            icon = AppIcon.CLOSE,
                            contentDescription = "بستن",
                            onClick = onDismiss,
                            size = 40.dp
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    content()
                }
            }
        }
    }
}

/** One item in the pinned home dock. */
@Composable
fun NebulaDockItem(
    icon: AppIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val c = joyThemeColors()
    val ink = if (selected) c.emerald else c.textSecondary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    if (selected) c.emerald.copy(alpha = 0.16f)
                    else c.chip.copy(alpha = 0.72f)
                )
                .border(
                    1.dp,
                    if (selected) c.emerald.copy(alpha = 0.38f) else c.cardBorder,
                    RoundedCornerShape(13.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            AppIconView(icon = icon, tint = ink, size = 18.dp, strokeWidth = 2.05.dp)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = ink,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                lineHeight = 13.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A compact sheet row for the overflow menu. */
@Composable
fun NebulaSheetAction(
    icon: AppIcon,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = joyThemeColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface.copy(alpha = if (c.isDark) 0.72f else 1f))
            .border(1.dp, c.cardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f))
                .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            AppIconView(icon = icon, tint = accent, size = 18.dp, strokeWidth = 2.05.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = c.textPrimary,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted, fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "‹",
            color = c.textMuted,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
