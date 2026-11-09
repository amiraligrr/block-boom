package ir.amiraligrr.blockboom.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.BlockBoomApp
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.data.RewardService
import ir.amiraligrr.blockboom.ui.components.AppIcon
import ir.amiraligrr.blockboom.ui.components.AppTextField
import ir.amiraligrr.blockboom.ui.components.ButtonSize
import ir.amiraligrr.blockboom.ui.components.NeonButton
import ir.amiraligrr.blockboom.ui.formatNumber
import ir.amiraligrr.blockboom.ui.theme.JoyThemeColors
import ir.amiraligrr.blockboom.ui.theme.NeonCyan
import ir.amiraligrr.blockboom.ui.theme.NeonGold
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import kotlinx.coroutines.launch

/**
 * Invite friends.
 *
 * ── Why this screen exists ───────────────────────────────────────────────
 * The server has had a complete referral pipeline the whole time
 * (`referral.php` — generate a code, redeem one, pay both sides), and
 * `RewardService` has had a typed client for it. Nothing in the app called
 * either. So the feature was fully built and entirely invisible: no code was
 * ever minted, and a player who had heard about it had nowhere to enter one.
 *
 * A referral is also the cheapest revenue there is — it costs nothing to pay
 * for, it only pays when someone installs who would not otherwise have, and
 * it hands the new player a reason to come back that does not involve money.
 *
 * The code is shown large and copyable, because a referral code that has to
 * be read off a screen and typed by hand loses most of its uses — the share
 * sheet is the path that actually converts.
 */
@Composable
fun InviteFriendsScreen(onBack: () -> Unit) {
    val c = joyThemeColors()
    val context = LocalContext.current
    val app = context.applicationContext as BlockBoomApp
    val saveManager = app.saveManager
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf<RewardService.ReferralStatus?>(null) }
    var loading by remember { mutableStateOf(true) }
    var redeemCode by remember { mutableStateOf("") }
    var redeeming by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        status = RewardService.referralStatus(context)
        loading = false
    }

    // Read once: stringResource is @Composable and the redeem result is
    // handled inside a coroutine, which is not one.
    val alreadyUsed = stringResource(R.string.invite_already_used)
    val badCode = stringResource(R.string.invite_bad_code)
    val offlineBody = stringResource(R.string.invite_offline_body)
    val rejected = stringResource(R.string.invite_rejected)

    fun copyCode(code: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("BlockBoom", code))
        message = "کد کپی شد ✅"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.gradientBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeonButton(
                text = "→",
                onClick = onBack,
                containerColor = c.surfaceSoft,
                borderColor = c.cardBorder,
                size = ButtonSize.Small
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.invite_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    color = c.textPrimary, fontWeight = FontWeight.Black
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (loading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.emerald)
                }
            } else {
                val s = status
                if (s == null || s.code.isBlank()) {
                    // The server was unreachable or refused to mint a code.
                    // Nothing is faked here: an invented code would silently
                    // fail at the friend's end, which is worse than saying so.
                    InfoCard(
                        c = c,
                        emoji = "📡",
                        title = stringResource(R.string.invite_offline_title),
                        body = stringResource(R.string.invite_offline_body),
                        accent = NeonCyan
                    )
                } else {
                    MyCodeCard(
                        c = c,
                        code = s.code,
                        onCopy = { copyCode(s.code) }
                    )
                }

                if (s != null && s.coinsRedeemedToday > 0) {
                    StatRow(c = c, label = stringResource(R.string.invite_used_today), value = "${s.coinsRedeemedToday}")
                }
                if (s != null && s.pendingCount > 0) {
                    StatRow(
                        c = c,
                        label = stringResource(R.string.invite_pending),
                        value = "${s.pendingCount} (+${formatNumber(s.pendingCoins)})"
                    )
                }
            }

            // ── Redeem a friend's code ────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.surface)
                    .border(1.dp, c.cardBorder, RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.invite_have_code),
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = c.textPrimary, fontWeight = FontWeight.Black
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.invite_have_code_body),
                    style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    value = redeemCode,
                    onValueChange = { redeemCode = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(8) },
                    placeholder = "ABC12345",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    minHeight = 52
                )
                Spacer(Modifier.height(10.dp))
                NeonButton(
                    text = if (redeeming) "…" else stringResource(R.string.invite_redeem),
                    onClick = {
                        if (redeemCode.length != 8 || redeeming) return@NeonButton
                        redeeming = true
                        scope.launch {
                            val res = RewardService.redeemReferral(context, redeemCode)
                            redeeming = false
                            if (res.isSuccess) {
                                val coins = res.getOrDefault(0)
                                saveManager.addCoins(coins.toLong())
                                ir.amiraligrr.blockboom.data.ProgressState.sync(saveManager)
                                message = "${formatNumber(coins.toLong())} سکه گرفتی 🎉"
                                redeemCode = ""
                                status = RewardService.referralStatus(context)
                            } else {
                                message = when (res.exceptionOrNull()?.message) {
                                    "already_used" -> alreadyUsed
                                    "bad_format" -> badCode
                                    "offline" -> offlineBody
                                    else -> rejected
                                }
                            }
                        }
                    },
                    enabled = !redeeming && redeemCode.length == 8,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = c.emerald,
                    borderColor = Color.White,
                    contentColor = Color.White,
                    size = ButtonSize.Medium,
                    icon = AppIcon.GIFT
                )
            }

            if (message != null) {
                Text(
                    text = message!!,
                    style = MaterialTheme.typography.bodyMedium.copy(color = c.textPrimary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MyCodeCard(c: JoyThemeColors, code: String, onCopy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
            .clip(RoundedCornerShape(22.dp))
            .background(c.gradientCard)
            .border(1.5.dp, NeonGold.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.invite_your_code),
            style = MaterialTheme.typography.titleMedium.copy(
                color = c.textPrimary, fontWeight = FontWeight.Black
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.invite_your_code_body),
            style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted),
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.chip)
                .border(1.dp, NeonGold.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .clickable(onClick = onCopy)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = code,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = c.amber,
                letterSpacing = 6.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.invite_tap_to_copy),
            style = MaterialTheme.typography.labelSmall.copy(color = c.textMuted),
            fontSize = 11.sp
        )
        Spacer(Modifier.height(14.dp))
        NeonButton(
            text = stringResource(R.string.invite_copy),
            onClick = onCopy,
            modifier = Modifier.fillMaxWidth(),
            containerColor = NeonGold,
            borderColor = Color.White,
            contentColor = Color(0xFF3A2A00),
            size = ButtonSize.Medium,
            icon = AppIcon.COPY
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.invite_reward_note),
            style = MaterialTheme.typography.labelSmall.copy(color = c.emerald),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatRow(c: JoyThemeColors, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.cardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(color = c.textSecondary),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                color = c.textPrimary, fontWeight = FontWeight.Black
            )
        )
    }
}

@Composable
private fun InfoCard(c: JoyThemeColors, emoji: String, title: String, body: String, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = emoji, fontSize = 30.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                color = c.textPrimary, fontWeight = FontWeight.Black
            ),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted),
            textAlign = TextAlign.Center,
            fontSize = 12.sp
        )
    }
}