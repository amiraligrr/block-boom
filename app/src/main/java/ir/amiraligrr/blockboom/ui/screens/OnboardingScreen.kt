package ir.amiraligrr.blockboom.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.BlockBoomApp
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.data.PlayerSyncManager
import ir.amiraligrr.blockboom.data.SaveManager
import ir.amiraligrr.blockboom.data.ThemeMode
import ir.amiraligrr.blockboom.ui.components.AppTextField
import ir.amiraligrr.blockboom.ui.components.ButtonSize
import ir.amiraligrr.blockboom.ui.components.NeonButton
import ir.amiraligrr.blockboom.ui.theme.NeonCyan
import ir.amiraligrr.blockboom.ui.theme.NeonGold
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import android.os.Handler
import android.os.Looper

@Composable
fun OnboardingScreen(
    saveManager: SaveManager,
    onComplete: () -> Unit
) {
    val c = joyThemeColors()
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) } // 0=welcome, 1=name, 2=theme, 3=language
    var playerName by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(saveManager.language) }

    // A first-run player should make an explicit choice instead of silently
    // inheriting whatever the phone happens to be set to. The old AUTO default
    // meant a player on a dark phone got a dark app and a player on a light
    // phone got a light one, with no visible decision anywhere. The system
    // setting is still used as the *preselected* option, but the choice is
    // stored as LIGHT or DARK and can be changed from Settings later.
    val systemDark = isSystemInDarkTheme()
    var selectedTheme by remember {
        mutableStateOf(
            when (saveManager.themeMode) {
                ThemeMode.AUTO -> if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT
                else -> saveManager.themeMode
            }
        )
    }

    val progress = (step + 1) / 4f

    Box(modifier = Modifier.fillMaxSize()) {
        // Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(joyThemeColors().gradientBackground)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Pinned to the top. It used to be the first child of a centred
            // column, which dragged the progress bar down to the middle of the
            // screen and left a large empty gap above the content.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(c.track)
                    .border(1.dp, c.trackBorder, RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(listOf(NeonGold, NeonCyan, c.emerald)))
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Without this the step content overflowed at a large font
                    // scale and the Continue button became unreachable.
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                when (step) {
                    0 -> WelcomeStep(onNext = { step = 1 })
                    1 -> NameStep(
                        playerName = playerName,
                        onNameChange = {
                            playerName = it
                            // Write as the player types, so a process death
                            // between the name screen and the finish button
                            // cannot leave the local save (and the next sync)
                            // with an empty name.
                            if (it.isNotBlank()) saveManager.playerName = it
                        },
                        onNext = {
                            if (playerName.trim().length >= 2) {
                                // Persist and push the name immediately. Waiting
                                // until the last onboarding screen left a fresh
                                // account with an empty player_name on the
                                // server while the player was already visible in
                                // the app, which is why admin/players.php kept
                                // showing unnamed accounts.
                                saveManager.playerName = playerName.trim()
                                PlayerSyncManager.syncNow(force = true)
                                step = 2
                            }
                        },
                        onSkip = { step = 2 }
                    )
                    2 -> ThemeStep(
                        selectedTheme = selectedTheme,
                        onThemeChange = {
                            selectedTheme = it
                            // Preview the choice immediately, not only after
                            // the finish button. This also means the selection
                            // survives a process death mid-onboarding.
                            saveManager.themeMode = it
                        },
                        onNext = { step = 3 },
                        onBack = { step = 1 }
                    )
                    3 -> LanguageStep(
                        selectedLanguage = selectedLanguage,
                        onLanguageChange = { selectedLanguage = it },
                        onComplete = {
                            if (playerName.trim().length >= 2) {
                                saveManager.playerName = playerName.trim()
                            }
                            saveManager.language = selectedLanguage
                            saveManager.themeMode = selectedTheme
                            saveManager.onboardingCompleted = true
                            // Register the name, theme, onboarding flag and
                            // today's bucket in one round-trip as soon as the
                            // player finishes signing up.
                            PlayerSyncManager.syncNow(force = true)
                            // Deliberately NOT marking the tutorial seen here.
                            //
                            // This line used to set `tutorialSeen = true`, which
                            // read as "the new player has been taught the game"
                            // three screens before they had played a single
                            // frame of it. HomeScreen keys its play-button hint,
                            // its pulse ring and its "new" premium badge off that
                            // flag, so setting it here made all three
                            // unreachable on a genuine first run — the first-time
                            // player was the exact audience for the only
                            // first-time affordance the app has.
                            //
                            // Nothing is lost by leaving it false: the player
                            // arrives in the game with the coach armed
                            // (`coachSeenVersion` is still 0), they are taught
                            // there, and the flag is set when they actually start.
                            onComplete()
                        },
                        onBack = { step = 2 }
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    val c = joyThemeColors()
    val floatAnim = rememberInfiniteTransition(label = "welcomeFloat")
    val floatY by floatAnim.animateFloat(-10f, 10f, infiniteRepeatable(
        animation = androidx.compose.animation.core.tween(1200),
        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
    ), label = "floatY")

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer { translationY = floatY }
        ) {
            // Ball animation
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF10B981), Color(0xFF059669))))
                    .border(4.dp, Color.White.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
            ) {
                Text(text = "🎮", fontSize = 60.sp, modifier = Modifier.align(Alignment.Center))
            }
        }

        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                color = c.textPrimary,
                fontWeight = FontWeight.Black
            ),
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.onboarding_welcome_desc),
            style = MaterialTheme.typography.bodyLarge.copy(color = c.textMuted),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        NeonButton(
            text = stringResource(R.string.onboarding_start),
            onClick = onNext,
            containerColor = c.emerald,
            borderColor = Color.White,
            contentColor = Color.White,
            size = ButtonSize.Large,
            // fillMaxWidth + widthIn, not a hard 280dp: 280 inside the screen's
            // 24dp padding needs 328dp, which overflows a 320dp phone.
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 320.dp)
        )
    }
}

@Composable
private fun NameStep(
    playerName: String,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val c = joyThemeColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.onboarding_name_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                color = c.textPrimary,
                fontWeight = FontWeight.Black
            ),
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.onboarding_name_desc),
            style = MaterialTheme.typography.bodyLarge.copy(color = c.textMuted),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        // Name input — the shared field, so the text and hint are guaranteed
        // readable against the surface. This used to be a filled TextField
        // with a 50%-alpha placeholder and a hard .width(280.dp) that
        // overrode fillMaxWidth, so it overflowed narrow screens and the hint
        // was close to invisible.
        AppTextField(
            value = playerName,
            onValueChange = onNameChange,
            placeholder = stringResource(R.string.onboarding_name_hint),
            maxLength = 16,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            textStyle = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Black,
                fontSize = 18.sp
            )
        )

        Text(
            text = stringResource(R.string.onboarding_name_rules),
            style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonButton(
                text = stringResource(R.string.onboarding_skip),
                onClick = onSkip,
                containerColor = c.chip,
                borderColor = c.cardBorder,
                contentColor = c.textMuted,
                size = ButtonSize.Medium,
                modifier = Modifier.weight(1f)
            )
            NeonButton(
                text = stringResource(R.string.onboarding_continue),
                onClick = onNext,
                enabled = playerName.trim().length >= 2,
                containerColor = c.emerald,
                borderColor = Color.White,
                contentColor = Color.White,
                size = ButtonSize.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ThemeStep(
    selectedTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val c = joyThemeColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = stringResource(R.string.onboarding_theme_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                color = c.textPrimary,
                fontWeight = FontWeight.Black
            ),
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.onboarding_theme_desc),
            style = MaterialTheme.typography.bodyLarge.copy(color = c.textMuted),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        ThemeOptionRow(
            icon = "☀️",
            title = stringResource(R.string.theme_light),
            description = stringResource(R.string.theme_light_desc),
            selected = selectedTheme == ThemeMode.LIGHT,
            onClick = { onThemeChange(ThemeMode.LIGHT) }
        )
        ThemeOptionRow(
            icon = "🌙",
            title = stringResource(R.string.theme_dark),
            description = stringResource(R.string.theme_dark_desc),
            selected = selectedTheme == ThemeMode.DARK,
            onClick = { onThemeChange(ThemeMode.DARK) }
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonButton(
                text = stringResource(R.string.back),
                onClick = onBack,
                containerColor = c.chip,
                borderColor = c.cardBorder,
                contentColor = c.textMuted,
                size = ButtonSize.Medium,
                modifier = Modifier.weight(1f)
            )
            NeonButton(
                text = stringResource(R.string.onboarding_continue),
                onClick = onNext,
                containerColor = c.emerald,
                borderColor = Color.White,
                contentColor = Color.White,
                size = ButtonSize.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    icon: String,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = joyThemeColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) c.emerald.copy(alpha = 0.10f) else c.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) c.emerald else c.cardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = icon, fontSize = 26.sp)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = c.textPrimary,
                    fontWeight = FontWeight.Black
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted)
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) c.emerald else Color.Transparent)
                .border(
                    width = 2.dp,
                    color = if (selected) c.emerald else c.cardBorder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Text(
                    text = "✓",
                    color = c.onPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun LanguageStep(
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val c = joyThemeColors()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.onboarding_language_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                color = c.textPrimary,
                fontWeight = FontWeight.Black
            ),
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.onboarding_language_desc),
            style = MaterialTheme.typography.bodyLarge.copy(color = c.textMuted),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        // Persian only, so the single option is shown as a confirmation rather
        // than a two-way choice. The English option used to be here, and it
        // led straight to a half-translated UI once the English strings were
        // removed — the card said "English" and everything after it stayed
        // Persian.
        LanguageOption(
            langCode = "fa",
            label = "فارسی",
            icon = "🇮🇷",
            isSelected = true,
            onClick = { onLanguageChange("fa") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonButton(
                text = stringResource(R.string.back),
                onClick = onBack,
                containerColor = c.chip,
                borderColor = c.cardBorder,
                contentColor = c.textMuted,
                size = ButtonSize.Medium,
                modifier = Modifier.weight(1f)
            )
            NeonButton(
                text = stringResource(R.string.onboarding_finish),
                onClick = onComplete,
                containerColor = NeonGold,
                borderColor = Color.White,
                contentColor = Color(0xFF78350F),
                size = ButtonSize.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LanguageOption(
    langCode: String,
    label: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = joyThemeColors()
    Box(
        modifier = modifier
            // weight(1f), not fillMaxWidth(): inside a Row each fillMaxWidth
            // child claimed the full width, so the second card was pushed
            // completely off-screen and English was impossible to pick.
            .height(104.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected)
                    // A solid emerald, not an emerald-to-cyan gradient. The
                    // old cyan end was so light that the white label sat on it
                    // at roughly 1.8:1. In dark mode the label is dark ink on
                    // the light emerald instead of white on a pale cyan.
                    Brush.verticalGradient(listOf(c.emerald, c.emerald))
                else
                    Brush.verticalGradient(listOf(c.surface, c.chip))
            )
            .border(
                width = if (isSelected) 3.dp else 1.5.dp,
                color = if (isSelected) NeonGold else c.cardBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .shadow(
                elevation = if (isSelected) 12.dp else 4.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = if (isSelected) NeonGold.copy(alpha = 0.3f) else c.cardShadow,
                spotColor = if (isSelected) NeonGold.copy(alpha = 0.2f) else c.cardShadow
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = icon, fontSize = 28.sp)
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    // Dark ink on the light dark-theme emerald, white on the
                    // deep light-theme emerald. Both keep the selected label
                    // readable instead of relying on one fixed foreground.
                    color = if (isSelected) {
                        if (c.isDark) c.onPrimary else Color.White
                    } else {
                        c.textPrimary
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                ),
                maxLines = 1
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(NeonGold, RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = "✓",
                        color = c.onGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}