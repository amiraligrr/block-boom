package ir.amiraligrr.blockboom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.BlockBoomApp
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.data.UpgradeId
import ir.amiraligrr.blockboom.data.UpgradeManager
import ir.amiraligrr.blockboom.data.ProgressState
import ir.amiraligrr.blockboom.domain.GameEngine
import ir.amiraligrr.blockboom.data.AdManager
import ir.amiraligrr.blockboom.ui.formatNumber
import ir.amiraligrr.blockboom.ui.components.ButtonSize
import ir.amiraligrr.blockboom.ui.components.NeonButton
import ir.amiraligrr.blockboom.ui.components.UpgradeCard
import ir.amiraligrr.blockboom.ui.theme.DarkSurfaceVariant
import ir.amiraligrr.blockboom.ui.theme.JoyThemeColors
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import ir.amiraligrr.blockboom.ui.theme.DeepSpace
import ir.amiraligrr.blockboom.ui.theme.NeonCyan
import ir.amiraligrr.blockboom.ui.theme.NeonGold

@Composable
fun UpgradeScreen(
    engine: GameEngine,
    onContinue: () -> Unit,
    showLevelHeader: Boolean = true
) {
    val c = joyThemeColors()
    val context = LocalContext.current
    val saveManager = (context.applicationContext as BlockBoomApp).saveManager
    val upgradeManager = remember(context) { UpgradeManager(context, saveManager) }

    var coins by remember { mutableStateOf(saveManager.coins) }
    var boughtUpgrade by remember { mutableStateOf<UpgradeId?>(null) }
    var notEnough by remember { mutableStateOf(false) }
    // Persisted and day-stamped in SaveManager, not remembered here. A
    // `remember` counter reset on every disposal and rotation, so the advertised
    // "2 free upgrades a day" was actually "2 per visit" — and because the grant
    // skips the coin check, that made an ad strictly better value than buying
    // coins, which is the opposite of the intent.
    var freeLeftToday by remember { mutableIntStateOf(saveManager.freeUpgradesLeftToday()) }
    var adPending by remember { mutableStateOf<UpgradeId?>(null) }
    val scope = rememberCoroutineScope()

    // Bumped after every purchase so the cards redraw.
    //
    // ── Why this was broken ───────────────────────────────────────────────
    // It was declared, incremented on buy (`tick++`), and never *read*. Compose
    // only recomposes when a snapshot state value that was actually read during
    // composition changes; writing to an unread one registers no observation, so
    // `tick++` did nothing at all.
    //
    // The bug was invisible for every upgrade that costs coins, because the
    // `coins` state does change on those and that alone was enough to redraw the
    // grid. It showed up on exactly the two upgrades whose first level is free —
    // PIERCE and MAGNET start at level 0, and `costOf()` returns 0 at level 0.
    // Buying the first level of either cost
    // nothing, so neither `coins` nor anything else observable changed, the grid
    // never recomposed, and the card sat there still reading "0/10" with a
    // "🪙 0" button until the player left and came back.
    //
    // It is now read below (see `bumpCards`), which is what makes it a real
    // invalidation signal rather than a write to nowhere.
    var cardRevision by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(joyThemeColors().gradientBackground)
    ) {
        // Glow orbs — pastel joy
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopEnd)
                .padding(top = 10.dp)
                .background(c.emerald.copy(alpha = 0.10f), RoundedCornerShape(110.dp))
        )
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.BottomStart)
                .padding(bottom = 80.dp)
                .background(c.sky.copy(alpha = 0.08f), RoundedCornerShape(90.dp))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(c.surface)
                        .border(1.dp, c.cardBorder, RoundedCornerShape(50.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (showLevelHeader) "✦ ${stringResource(R.string.level_complete)}!" else "⚡ ${stringResource(R.string.upgrade)}",
                        style = MaterialTheme.typography.titleMedium.copy(color = c.textPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                    )
                }
                Text(
                    text = "${stringResource(R.string.level)} ${engine.level}",
                    style = MaterialTheme.typography.titleSmall.copy(color = c.sky, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.8.sp),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        if (showLevelHeader) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                StatChip(
                    label = stringResource(R.string.final_score),
                    value = formatNumber(engine.score),
                    accent = NeonCyan
                )
                Spacer(Modifier.width(12.dp))
                StatChip(
                    label = stringResource(R.string.level_earned_coins),
                    value = "+${formatNumber(engine.coinsInLevel)}",
                    accent = NeonGold
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Animated coins pill — glass
        val animatedCoins by androidx.compose.animation.core.animateIntAsState(
            targetValue = coins.toInt(),
            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 200f),
            label = "coinsAnim"
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(50.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFFDE68A).copy(alpha = 0.92f), Color(0xFFF59E0B).copy(alpha = 0.92f))))
                .border(1.2.dp, Color.White.copy(alpha = 0.62f), RoundedCornerShape(50.dp))
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "🪙", fontSize = 18.sp)
                Text(
                    text = "${stringResource(R.string.coins)}  ${formatNumber(animatedCoins)}",
                    style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFF78350F), fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Read `cardRevision` so the increment in the buy handlers actually
        // registers an observation. Reading it here — on the composable that
        // owns the grid — is what makes the redraw happen.
        //
        // It is deliberately NOT used as a `key()` on the grid: keying on it
        // rebuilt the whole LazyVerticalGrid on every purchase, which threw away
        // the scroll position and replayed the staggered entrance for all six
        // cards. Reading it is enough to invalidate; no key is needed.
        @Suppress("UNUSED_EXPRESSION")
        cardRevision

        // No key(cardRevision) wrapper: see above. `coins` is already snapshot
        // state, so the cards that depend on affordability recompose on their
        // own — but a free first level changes neither `coins` nor anything else
        // observable, which is exactly why this read is required.
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(upgradeManager.defs, key = { _, d -> d.id }) { index, def ->
                    val enterAlpha = remember(def.id) { androidx.compose.animation.core.Animatable(0f) }
                    val enterOffset = remember(def.id) { androidx.compose.animation.core.Animatable(28f) }
                    androidx.compose.runtime.LaunchedEffect(def.id) {
                        kotlinx.coroutines.delay(index * 40L)
                        launch { enterAlpha.animateTo(1f, androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
                        launch { enterOffset.animateTo(0f, androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
                    }
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.graphicsLayer {
                            alpha = enterAlpha.value
                            translationY = enterOffset.value
                        }
                    ) {
                    val level = upgradeManager.levelOf(def.id)
                    val maxed = upgradeManager.isMaxed(def.id)
                    // Read off the *save*, not off the engine: the upgrade screen
                    // is also reachable from home, where no level is running, and
                    // a lock that follows the current run would unlock and relock
                    // as the player moved between screens.
                    val locked = upgradeManager.isLocked(def.id)
                    val cost = upgradeManager.costOf(def.id)
                    val afford = !maxed && !locked && coins >= cost
                    UpgradeCard(
                        name = stringResource(def.nameRes),
                        icon = def.icon,
                        accent = accentFor(def.id, c),
                        currentLevel = level,
                        maxLevel = def.maxLevel,
                        effect = upgradeManager.currentEffect(def.id),
                        nextEffect = if (maxed) "-" else upgradeManager.nextEffect(def.id),
                        cost = cost,
                        listPrice = upgradeManager.listPriceOf(def.id),
                        maxed = maxed,
                        unowned = upgradeManager.isUnowned(def.id),
                        locked = locked,
                        lockedUntilLevel = upgradeManager.unlockLevelOf(def.id),
                        canAfford = afford,
                        onBuy = {
                            if (upgradeManager.buy(def.id)) {
                                coins = saveManager.coins
                                ProgressState.sync(saveManager)
                                // Read below, so this is a real invalidation.
                                cardRevision++
                                engine.refreshCannonStats()
                                boughtUpgrade = def.id
                                // Haptic
                                try { ir.amiraligrr.blockboom.utils.VibrationHelper.vibrate(context, 30, 120) } catch (_: Throwable) {}
                            } else {
                                // Re-evaluate afford after failure (e.g. not enough coins)
                                coins = saveManager.coins
                                cardRevision++
                                notEnough = true
                            }
                        }
                    )
                }
            }
        }

        NeonButton(
            text = stringResource(R.string.btn_continue),
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            containerColor = c.emerald,
            borderColor = NeonCyan,
            size = ButtonSize.Large
        )

        if (notEnough) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.not_enough_coins),
                style = MaterialTheme.typography.bodyMedium,
                // Was `NeonGold` — 1.28:1 on this screen's light background,
                // so the "you cannot afford this" warning was invisible in
                // light mode, the exact situation it exists to warn about.
                color = c.amber,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // ── Free upgrade for a short ad ─────────────────────────────────────
        // The highest-value ad placement in a free-to-play game, and the one
        // that does NOT cannibalise the shop: it sits at the exact moment the
        // player has just cleared a level and is looking at the thing they
        // cannot afford. Rewarded video pays several times a banner's RPM, and
        // asking here is voluntary — unlike the "out of lives" prompt, which
        // is the same ad shown to someone who is frustrated.
        //
        // Capped per day so it supplements coin income rather than replacing
        // it; the cap is what keeps the coin packs worth buying.
        if (!ProgressState.isPremium.value && freeLeftToday > 0) {
            Spacer(Modifier.height(8.dp))
            NeonButton(
                text = if (adPending != null) "در حال باز کردن تبلیغ…"
                       else "🎬  یک سطح ارتقا رایگان — با تماشای تبلیغ ($freeLeftToday باقی‌مانده)",
                onClick = {
                    // Cheapest upgrade that actually costs something.
                    //
                    // It used to be a plain minByOrNull over every not-maxed
                    // upgrade, and PIERCE/MAGNET cost 0 at level 0 (see
                    // costOf's `base * level`), so those always sorted first.
                    // The player would watch a rewarded ad — the most valuable
                    // thing the game asks for — and be handed a level that was
                    // already free, while the expensive upgrade they were saving
                    // toward stayed exactly as expensive. Filtering to a real
                    // price is what makes the ad worth anything.
                    val target = upgradeManager.defs
                        .filter {
                            !upgradeManager.isMaxed(it.id) &&
                                !upgradeManager.isLocked(it.id) &&
                                upgradeManager.costOf(it.id) > 0
                        }
                        .minByOrNull { upgradeManager.costOf(it.id) }
                        // Every remaining upgrade is either maxed or free, so
                        // there is nothing an ad could be worth here.
                        ?: run { adPending = null; onContinue(); return@NeonButton }
                    adPending = target.id
                    AdManager.showRewarded(
                        onReward = {
                            adPending = null
                            // Claim the day's slot first, then grant. Both are
                            // idempotent per tap because the button is disabled
                            // while adPending is set.
                            freeLeftToday = saveManager.consumeFreeUpgrade()
                            upgradeManager.grantFreeLevel(target.id)
                            coins = saveManager.coins
                            ProgressState.sync(saveManager)
                            cardRevision++
                            engine.refreshCannonStats()
                            boughtUpgrade = target.id
                        },
                        onFail = {
                            adPending = null
                            notEnough = true   // no fill: point them at coins
                        }
                    )
                },
                enabled = adPending == null,
                modifier = Modifier.fillMaxWidth(),
                size = ButtonSize.Medium,
                // Themed surface, not a literal white fill: the label is
                // c.textPrimary, which is light in dark mode.
                containerColor = c.surface,
                contentColor = c.textPrimary,
                borderColor = NeonGold
            )
        }

    }
    }

    // Transient feedback popup for a successful purchase.
    // Keyed on BOTH flags. With only `boughtUpgrade` as the key, a failed
    // purchase left the key unchanged, the effect never relaunched, and the
    // "not enough coins" message stayed on screen until a purchase succeeded.
    androidx.compose.runtime.LaunchedEffect(boughtUpgrade, notEnough) {
        when {
            boughtUpgrade != null -> {
                kotlinx.coroutines.delay(1200)
                boughtUpgrade = null
            }
            notEnough -> {
                kotlinx.coroutines.delay(1500)
                notEnough = false
            }
        }
    }

    if (boughtUpgrade != null) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = "✅ ${stringResource(R.string.upgrade)}",
                style = MaterialTheme.typography.titleMedium,
                // This one is correct either way: it sits on a hard-coded dark
                // chip in both themes, so `NeonGold` clears 9.33:1. Kept as
                // `c.amberLight`, which is #FDE68A in dark and therefore
                // unchanged there, and #FCD34D in light — still on the dark
                // chip, still >=8:1 — so the light theme no longer imports a
                // constant it has no reason to reach for. `c.amber` would drop
                // this to ~3:1 here; the pill's own fill decides the token.
                color = c.amberLight,
                modifier = Modifier
                    .background(DarkSurfaceVariant, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, accent: Color) {
    val c = joyThemeColors()
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF143D2E).copy(alpha = 0.92f), Color(0xFF0E2E22).copy(alpha = 0.96f))))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(color = c.textMuted, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.6.sp),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(color = accent, fontWeight = androidx.compose.ui.text.font.FontWeight.Black),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

private fun accentFor(id: UpgradeId, c: JoyThemeColors): Color = when (id) {
    UpgradeId.DAMAGE -> Color(0xFFA855F7)
    // Was NeonCyan (#22D3EE): 1.59:1 on the upgrade card's white surface.
    // c.sky is the theme slot that clears ≥5:1 on every card in both themes.
    UpgradeId.FIRE_RATE -> c.sky
    // Was NeonGreen (#34D399): 1.69:1 on the upgrade card's white surface.
    // c.emerald clears 4.06:1 on light card and 7.08:1 on dark — the right
    // token for a damage/level accent.
    UpgradeId.MULTI_BALL -> c.emerald
    UpgradeId.PIERCE -> Color(0xFFFB923C)
    UpgradeId.MAGNET -> Color(0xFFFBBF24)
    UpgradeId.BULLET_SPEED -> Color(0xFF818CF8)
}
