package ir.amiraligrr.blockboom.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.BlockBoomApp
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.data.DailyReminder
import ir.amiraligrr.blockboom.data.PlayerSyncManager
import ir.amiraligrr.blockboom.data.ThemeMode
import ir.amiraligrr.blockboom.ui.components.AppIcon
import ir.amiraligrr.blockboom.ui.components.AppIconView
import ir.amiraligrr.blockboom.ui.components.ButtonSize
import ir.amiraligrr.blockboom.ui.components.NeonButton
import ir.amiraligrr.blockboom.ui.theme.DarkSurfaceVariant
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import ir.amiraligrr.blockboom.ui.theme.DeepSpace
import ir.amiraligrr.blockboom.ui.theme.NeonCyan
import ir.amiraligrr.blockboom.ui.theme.NeonGold

/** Where the author is, and the one link the settings screen carries. */
private const val GITHUB_URL = "https://github.com/amiraligrr"
private const val GITHUB_HANDLE = "amiraligrr"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSupport: () -> Unit = {}
) {
    val c = joyThemeColors()
    val context = LocalContext.current
    val saveManager = (context.applicationContext as BlockBoomApp).saveManager

    val soundManager = (context.applicationContext as BlockBoomApp).soundManager

    /**
     * Opens the author's GitHub.
     *
     * Wrapped because a device with no browser at all is a real thing — and an
     * unwrapped `startActivity` on it is a crash from the settings screen,
     * which is the one place a player goes to when the app is misbehaving.
     */
    val openGitHub: () -> Unit = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
        } catch (_: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.settings_open_github_browser),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // rememberSaveable so a rotation or a locale change does not silently
    // reset every toggle back to the stored value.
    var music by rememberSaveable { mutableStateOf(saveManager.musicEnabled) }
    var sound by rememberSaveable { mutableStateOf(saveManager.soundEnabled) }
    var vibration by rememberSaveable { mutableStateOf(saveManager.vibrationEnabled) }
    var notifications by rememberSaveable { mutableStateOf(saveManager.notificationsEnabled) }

    var recoveryInput by rememberSaveable { mutableStateOf("") }
    var issuedRecovery by remember { mutableStateOf(PlayerSyncManager.recoveryCode()) }
    var lastSyncLabel by remember { mutableStateOf("—") }

    val syncing by PlayerSyncManager.syncing.collectAsState()
    val syncError by PlayerSyncManager.lastError.collectAsState()
    val restoredNotice by PlayerSyncManager.notice.collectAsState()

    LaunchedEffect(restoredNotice) {
        if (restoredNotice != null) PlayerSyncManager.consumeNotice()
    }

    LaunchedEffect(syncing) {
        if (!syncing) {
            val err = PlayerSyncManager.lastError.value
            lastSyncLabel = if (err == null) "به‌روز" else "خطا — دوباره امتحان کن"
        }
    }

    val onSyncNow: () -> Unit = { PlayerSyncManager.syncNow(force = true) }

    val onCopy: () -> Unit = {
        val code = issuedRecovery
        if (code.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("blockboom_recovery", code))
        }
    }

    val onRotate: () -> Unit = {
        PlayerSyncManager.rotateRecoveryCode { result ->
            val issued = result.getOrNull().orEmpty()
            if (issued.isNotBlank()) issuedRecovery = issued
        }
    }

    val onRecover: () -> Unit = {
        val code = recoveryInput.trim()
        if (code.isNotBlank()) {
            PlayerSyncManager.recover(code) { result ->
                val issued = result.getOrNull().orEmpty()
                if (issued.isNotBlank()) issuedRecovery = issued
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(joyThemeColors().gradientBackground)
    ) {
        // Soft glow orbs — pastel joy
        Box(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.TopEnd)
                .padding(top = 40.dp)
                .offset(x = -40.dp, y = 0.dp)
                .blur(46.dp)
                .background(c.emerald.copy(alpha = 0.10f), RoundedCornerShape(120.dp))
        )
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.BottomStart)
                .padding(bottom = 80.dp)
                .offset(x = -30.dp, y = 0.dp)
                .blur(40.dp)
                .background(c.sky.copy(alpha = 0.08f), RoundedCornerShape(100.dp))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header — clean white
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface)
                        .border(1.dp, c.cardBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(text = "⚙️", fontSize = 22.sp) }
                Column {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.headlineMedium.copy(color = c.textPrimary, fontWeight = FontWeight.Black)
                    )
                    Text(
                        text = stringResource(R.string.settings_subtitle),
                        style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted)
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            // Sound section — clean white card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surface)
                    .border(1.dp, c.cardBorder, RoundedCornerShape(20.dp))
                    .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "🔊  صدا و موسیقی", style = MaterialTheme.typography.titleSmall.copy(color = c.amber, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp))
                    Spacer(Modifier.height(6.dp))
                    SettingsRow(
                        title = stringResource(R.string.music),
                        icon = "🎵",
                        vectorIcon = AppIcon.NOTE,
                        checked = music,
                        onToggle = {
                            music = it
                            // Goes through SoundManager, not straight to prefs:
                            // writing the pref alone left the current track
                            // playing for the rest of the session.
                            soundManager.setMusicEnabled(it)
                        }
                    )
                    SettingsRow(
                        title = stringResource(R.string.sound),
                        icon = "🔔",
                        vectorIcon = AppIcon.SPEAKER,
                        checked = sound,
                        onToggle = {
                            sound = it
                            saveManager.soundEnabled = it
                        }
                    )
                    SettingsRow(
                        title = stringResource(R.string.vibration),
                        icon = "📳",
                        vectorIcon = AppIcon.VIBRATE,
                        checked = vibration,
                        onToggle = { vibration = it; saveManager.vibrationEnabled = it }
                    )
                    // The daily reminder.
                    //
                    // This row is the reason the notification permission is safe
                    // to ask for. Without a visible switch, a player whose
                    // reminder is unwanted has exactly one way to stop it — revoking
                    // the OS permission — which is permanent, takes them to system
                    // settings, and silently disables every future notification
                    // the app might want. Here the polite route exists.
                    SettingsRow(
                        title = stringResource(R.string.notifications),
                        icon = "🔔",
                        vectorIcon = AppIcon.BELL,
                        checked = notifications,
                        onToggle = {
                            notifications = it
                            saveManager.notificationsEnabled = it
                            // Flip the alarm as well as the flag. Without this the
                            // switch changes a preference that is only read on
                            // the *next* launch, so turning reminders off appears
                            // to do nothing and the player has no reason to
                            // believe the switch works.
                            //
                            // `sync` cancels when the feature is off, so one call
                            // covers both directions.
                            DailyReminder.sync(context, saveManager)
                        }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Appearance ───────────────────────────────────────────────────
            // Theme used to be an invisible AUTO-only setting: the app followed
            // the phone and the player had no way to override it. This card
            // makes the choice explicit, stores it immediately, and because
            // MainActivity observes SaveManager.themeMode the whole app
            // repaints without a restart.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surface)
                    .border(1.dp, c.cardBorder, RoundedCornerShape(20.dp))
                    .shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_appearance_title),
                        style = MaterialTheme.typography.titleSmall.copy(color = c.amber, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
                    )
                    Text(
                        text = stringResource(R.string.settings_appearance_desc),
                        style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeChoiceChip(
                            label = stringResource(R.string.theme_light),
                            icon = "☀️",
                            selected = saveManager.themeMode == ThemeMode.LIGHT,
                            onClick = { saveManager.themeMode = ThemeMode.LIGHT },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeChoiceChip(
                            label = stringResource(R.string.theme_dark),
                            icon = "🌙",
                            selected = saveManager.themeMode == ThemeMode.DARK,
                            onClick = { saveManager.themeMode = ThemeMode.DARK },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeChoiceChip(
                            label = stringResource(R.string.theme_auto),
                            icon = "📱",
                            selected = saveManager.themeMode == ThemeMode.AUTO,
                            onClick = { saveManager.themeMode = ThemeMode.AUTO },
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Account + recovery ───────────────────────────────────────────
            // The server is where a player's real data lives now: progress,
            // daily activity, installs. This card is the player-facing home
            // for all of it — identity, a recovery code, and a manual sync.
            //
            // It used to have nothing here, which is why an uninstall lost the
            // player entirely: the leaderboard kept the score, the account
            // did not, and the only path back was support.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.surface)
                    .border(1.dp, c.cardBorder, RoundedCornerShape(22.dp))
                    .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "👤 حساب و بازیابی",
                        style = MaterialTheme.typography.titleSmall.copy(color = c.textPrimary, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
                    )
                    Text(
                        text = "با حساب سرور، پیشرفت تو در نصب جدید و گوشی دیگر حفظ می‌شود. امروز همهٔ بازیکنی که بازی می‌کند روی سرور ثبت می‌شود.",
                        style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted)
                    )

                    OutlinedTextField(
                        value = recoveryInput,
                        onValueChange = { recoveryInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("کد بازیابی") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            // NeonCyan is a dark-theme accent and is almost
                            // invisible on the light surface. The theme-aware
                            // sky token is legible in both palettes.
                            focusedBorderColor = c.sky,
                            unfocusedBorderColor = c.cardBorder,
                            focusedLabelColor = c.sky,
                            unfocusedLabelColor = c.textMuted,
                            focusedTextColor = c.textPrimary,
                            unfocusedTextColor = c.textPrimary
                        )
                    )

                    NeonButton(
                        text = "بازیابی از کد",
                        onClick = onRecover,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = c.sky,
                        contentColor = Color.White,
                        size = ButtonSize.Small
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = issuedRecovery.ifBlank { "—" },
                                style = MaterialTheme.typography.labelLarge.copy(color = c.emerald, fontWeight = FontWeight.Black, letterSpacing = 1.sp),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            Text(
                                text = "کد بازیابی تو",
                                style = MaterialTheme.typography.labelSmall.copy(color = c.textMuted)
                            )
                        }
                        NeonButton(
                            text = "کپی",
                            onClick = onCopy,
                            modifier = Modifier.width(104.dp),
                            containerColor = c.sky,
                            contentColor = if (c.isDark) c.onPrimary else Color.White,
                            size = ButtonSize.Small
                        )
                    }

                    Text(
                        text = if (syncing) "در حال همگام‌سازی با سرور…" else if (syncError != null) "همگام‌سازی ناموفق بود — دوباره امتحان کن."
                        else "آخرین همگام‌سازی: ${lastSyncLabel}",
                        style = MaterialTheme.typography.bodySmall.copy(color = if (syncError != null) c.amber else c.textMuted)
                    )

                    NeonButton(
                        text = "همگام‌سازی همین حالا",
                        onClick = onSyncNow,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = c.emerald,
                        contentColor = Color.White,
                        size = ButtonSize.Small
                    )

                    Text(
                        text = "آخرین اتصال حساب: ${PlayerSyncManager.accountId()}",
                        style = MaterialTheme.typography.labelSmall.copy(color = c.textMuted)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Support ───────────────────────────────────────────────────
            // The one link that is actually about the author. Everything else
            // that used to sit here — the version number, the "about" blurb, a
            // language row that offered exactly one language — was prose about
            // the app, and a settings screen full of it is a screen nobody
            // reads. What is left is the thing a player might want: who made
            // this, and where to go to say so.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.surface)
                    .border(1.dp, c.cardBorder, RoundedCornerShape(22.dp))
                    .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
                    .clickable { openGitHub() }
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(NeonGold.copy(alpha = 0.14f))
                                .border(1.dp, NeonGold.copy(alpha = 0.3f), RoundedCornerShape(15.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_github),
                                contentDescription = "GitHub",
                                modifier = Modifier.size(24.dp),
                                colorFilter = ColorFilter.tint(c.textPrimary)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_github_title),
                                style = MaterialTheme.typography.titleMedium.copy(color = c.textPrimary, fontWeight = FontWeight.Black)
                            )
                            Text(
                                text = stringResource(R.string.settings_github_sub),
                                style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(NeonCyan.copy(alpha = 0.14f))
                                .border(1.dp, NeonCyan.copy(alpha = 0.24f), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text(text = "↗", color = c.sky, fontSize = 16.sp, fontWeight = FontWeight.Black) }
                    }

                    // The address itself, underlined. Hiding it behind a tap
                    // means the player cannot see where they are being sent, and
                    // a settings screen that opens unknown links is not one
                    // anyone should trust.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.chip)
                            .border(1.dp, c.cardBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = GITHUB_HANDLE,
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = c.emerald,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "·",
                            color = c.textMuted
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "github.com",
                            style = MaterialTheme.typography.labelSmall.copy(color = c.textMuted)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            NeonButton(
                text = stringResource(R.string.back),
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                containerColor = DarkSurfaceVariant,
                borderColor = NeonCyan
            )

            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(8.dp))

            // The credit line, last thing on the screen.
            //
            // It was inside the About card, directly under the GitHub link, which
            // is the only part of the screen that is actually *about* the author —
            // so the signature sat in the middle of a panel of links. It is at the
            // bottom of the screen now, below everything, where a credit belongs.
            //
            // And it is the only line of its kind. The version number, the
            // "about" blurb and the language row that used to share this space are
            // gone: a settings screen full of prose about the app is a screen
            // nobody reads, and this is the one thing on it worth reading.
            Text(
                text = stringResource(R.string.made_with_love),
                style = MaterialTheme.typography.labelSmall.copy(color = c.textMuted),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 10.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    icon: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    /**
     * Drawn instead of [icon].
     *
     * The four rows in this card were 🎵 🔔 📳 🔔 — two identical bells on
     * adjacent rows, so "sound" and "notifications" were told apart by their
     * label text alone. A vector per row is the difference between a toggle
     * you can find by shape and one you have to read.
     */
    vectorIcon: AppIcon? = null
) {
    val c = joyThemeColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(1.dp, c.cardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    // Was a hardcoded pale mint (#ECFDF5) on every row. In the
                    // dark palette this card actually ships in, it was the one
                    // near-white patch on the screen, which is why the settings
                    // list read as light-mode furniture inside a dark app.
                    .background(c.chip)
                    .border(1.dp, c.cardBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (vectorIcon != null) {
                    AppIconView(
                        icon = vectorIcon,
                        tint = c.textPrimary,
                        size = 16.dp,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(text = icon, fontSize = 16.sp)
                }
            }
            Text(text = title, style = MaterialTheme.typography.titleSmall.copy(color = c.textPrimary, fontWeight = FontWeight.Bold))
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = c.emerald,
                checkedBorderColor = Color.White.copy(alpha = 0.28f),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = c.dotIdle,
                uncheckedBorderColor = c.cardBorder
            )
        )
    }
}

@Composable
private fun ThemeChoiceChip(
    label: String,
    icon: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = joyThemeColors()
    Column(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.emerald.copy(alpha = 0.12f) else c.chip)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) c.emerald else c.cardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = c.textPrimary,
                fontWeight = FontWeight.Bold
            ),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        if (selected) {
            Text(
                text = "✓",
                color = c.emerald,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
