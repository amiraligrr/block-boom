package ir.amiraligrr.blockboom.ui.screens

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.BlockBoomApp
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.data.AdManager
import ir.amiraligrr.blockboom.data.AdRewards
import ir.amiraligrr.blockboom.data.BillingManager
import ir.amiraligrr.blockboom.data.MyketConfig
import ir.amiraligrr.blockboom.data.ProgressState
import ir.amiraligrr.blockboom.data.Skin
import ir.amiraligrr.blockboom.data.SkinManager
import ir.amiraligrr.blockboom.data.SkinType
import ir.amiraligrr.blockboom.ui.components.ButtonSize
import ir.amiraligrr.blockboom.ui.components.NeonButton
import ir.amiraligrr.blockboom.ui.components.PremiumActiveBanner
import ir.amiraligrr.blockboom.ui.formatNumber
import ir.amiraligrr.blockboom.ui.rialToToman
import ir.amiraligrr.blockboom.ui.theme.BallVisual
import ir.amiraligrr.blockboom.ui.theme.BallVisuals
import ir.amiraligrr.blockboom.ui.theme.CannonVisual
import ir.amiraligrr.blockboom.ui.theme.CannonVisuals
import ir.amiraligrr.blockboom.ui.theme.GameBackdrop
import ir.amiraligrr.blockboom.ui.theme.GameBackdrops
import ir.amiraligrr.blockboom.ui.theme.drawBallSprite
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import kotlin.math.abs
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════
//  Shop
//
//  Rewritten. The previous version was not merely ugly, it was broken:
//
//  - The unselected tab and the back button were painted `DarkSurfaceVariant`
//    — a dark forest green — on top of a light mint page. Two dark boxes on a
//    light screen, in every language, since the whole file had been migrated to
//    the theme system except the three constants nobody searched for.
//  - The "best value" card scaled itself between 0.85x and 1.06x forever. At
//    1.06x it overflowed its own bounds and overlapped the row beneath it, and
//    the layout visibly breathed. A promotion should draw the eye, not resize
//    the layout on a 900ms loop.
//  - Skin cards were hardcoded `Color.White` gradients, so the whole tab was a
//    sheet of white cards in dark mode.
//  - The title was centred by `Spacer(1f) / title / Spacer(1f) / Spacer(80dp)`
//    — the trailing spacer pushed it visibly left of centre.
//  - `MyketConfig.OFFER_PACK` (18,000 coins for 290,000 rial, ~58% cheaper per
//    coin than the base pack) was never rendered. The cheapest thing in the
//    store, by a wide margin, was invisible. It is the featured card now.
//
//  Everything here reads its colour from `joyThemeColors()`. There is no
//  literal colour in this file outside the two "on a saturated gold fill"
//  cases, which the palette provides as a slot for exactly that reason.
// ═══════════════════════════════════════════════════════════════════════════

private const val TAB_COINS = 0
private const val TAB_SKINS = 1
private const val TAB_PREMIUM = 2

@Composable
fun ShopScreen(onBack: () -> Unit) {
    val c = joyThemeColors()
    val context = LocalContext.current
    val app = context.applicationContext as BlockBoomApp
    val saveManager = app.saveManager
    val billingManager = app.billingManager
    val skinManager = app.skinManager
    val activity = context as? Activity

    var selectedTab by remember { mutableStateOf(TAB_COINS) }
    var feedback by remember { mutableStateOf<String?>(null) }

    // One toast for the whole screen instead of one scoped to the skins tab,
    // so a failed purchase from the coins tab is not silent.
    LaunchedEffect(feedback) {
        if (feedback != null) {
            kotlinx.coroutines.delay(2200)
            feedback = null
        }
    }

    LaunchedEffect(Unit) {
        ProgressState.sync(saveManager)
        skinManager.refresh()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.gradientBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── header ───────────────────────────────────────────────────────
            // The title is centred with a real centred Box rather than by
            // balancing two weights. Balancing cannot work here: the back button
            // is a fixed width on one side only, so any two-weight trick leaves
            // the title off-centre by half the button's width.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    NeonButton(
                        text = stringResource(R.string.back),
                        onClick = onBack,
                        containerColor = c.surface,
                        borderColor = c.cardBorder,
                        contentColor = c.textPrimary,
                        size = ButtonSize.Small
                    )
                }
                Text(
                    text = stringResource(R.string.shop),
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.textPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // ── balance ──────────────────────────────────────────────────────
            BalancePill(
                coins = ProgressState.coins.longValue,
                isPremium = ProgressState.isPremium.value,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // ── active-premium banner ────────────────────────────────
            //
            // Premium was previously only confirmed by a small "✓ پریمیوم خریداری
            // شد" pill buried in the middle of the premium card, several scrolls
            // down. Nothing said *how long* it lasts, and nothing on the other
            // tabs acknowledged it at all — so a player who had just paid had no
            // way to confirm from the shop that they were still premium, and a
            // player on the coins tab had no indication the purchase had
            // registered.
            //
            // It sits above the tabs, so it is visible from every tab and cannot
            // be scrolled past, and it carries the expiry date, which is the one
            // fact a premium player actually wants from it.
            PremiumActiveBanner(
                saveManager = saveManager,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(14.dp))

            // ── tabs ─────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShopTab(
                    text = stringResource(R.string.coin_packs),
                    selected = selectedTab == TAB_COINS,
                    onClick = { selectedTab = TAB_COINS },
                    modifier = Modifier.weight(1f)
                )
                ShopTab(
                    text = stringResource(R.string.skins_tab),
                    selected = selectedTab == TAB_SKINS,
                    onClick = { selectedTab = TAB_SKINS },
                    modifier = Modifier.weight(1f)
                )
                ShopTab(
                    text = stringResource(R.string.premium_tab),
                    selected = selectedTab == TAB_PREMIUM,
                    onClick = { selectedTab = TAB_PREMIUM },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── content ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    TAB_COINS -> CoinPacksTab(
                        billingManager = billingManager,
                        activity = activity,
                        onFeedback = { feedback = it }
                    )
                    TAB_SKINS -> SkinsTab(
                        skinManager = skinManager,
                        onFeedback = { feedback = it }
                    )
                    else -> PremiumTab(
                        billingManager = billingManager,
                        activity = activity,
                        onFeedback = { feedback = it }
                    )
                }
            }

        }

        // ── toast ──────────────────────────────────────────────────────────
        feedback?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = c.textPrimary,
                    shadowElevation = 10.dp,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Text(
                        text = msg,
                        color = c.backgroundTop,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
//  Header pieces
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun BalancePill(coins: Long, isPremium: Boolean, modifier: Modifier = Modifier) {
    val c = joyThemeColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.cardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.gradientGold),
            contentAlignment = Alignment.Center
        ) { Text(text = "🪙", fontSize = 19.sp) }

        Spacer(Modifier.width(12.dp))

        Column {
            Text(
                text = stringResource(R.string.coins),
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted
            )
            Text(
                text = formatNumber(coins),
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(Modifier.weight(1f))

        if (isPremium) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(c.gradientGold)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "👑", fontSize = 14.sp)
                Text(
                    text = stringResource(R.string.premium),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.onGold,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

/**
 * One tab of the segmented control.
 *
 * Built on `Surface` rather than a `Card` with `.background()` chained onto it.
 * Card paints its own container colour, so a background chained on afterwards
 * is either hidden or fights it depending on the version — which is why the
 * old tab rendered as a flat block with no fill on some builds.
 */
@Composable
private fun ShopTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = joyThemeColors()
    // Light mode needs a deeper green under white; dark mode uses the light
    // theme emerald with dark ink. The previous light pair was 4.1:1.
    val selectedBg = if (c.isDark) c.emerald else Color(0xFF047857)
    val selectedFg = if (c.isDark) c.onPrimary else Color.White
    val container by animateColorAsState(
        targetValue = if (selected) selectedBg else c.surface,
        animationSpec = tween(220),
        label = "tabBg"
    )
    val border by animateColorAsState(
        targetValue = if (selected) selectedBg else c.cardBorder,
        animationSpec = tween(220),
        label = "tabBorder"
    )
    val label by animateColorAsState(
        targetValue = if (selected) selectedFg else c.textSecondary,
        animationSpec = tween(220),
        label = "tabFg"
    )

    Surface(
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, border),
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = label,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

// ───────────────────────────────────────────────────────────────────────────
//  Coins
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun CoinPacksTab(
    billingManager: BillingManager,
    activity: Activity?,
    onFeedback: (String) -> Unit
) {
    val c = joyThemeColors()
    val context = LocalContext.current
    val saveManager = (context.applicationContext as BlockBoomApp).saveManager

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // Free coins, above the packs.
        //
        // The rewarded tap used to exist only as four scattered one-offs — hearts
        // when you are out, the daily gift doubled, the run's coins doubled, a
        // free upgrade level — and none of them were on the screen where a player
        // who wants coins goes looking. So the fourth-best-RPM format in the game
        // had no home, and a player with time rather than money had no way to
        // spend it.
        //
        // It sits above the paid packs on purpose: the honest message is that
        // watching costs you time and buying costs you money, and both are real
        // ways to get the same thing.
        if (saveManager.adWatchesLeftToday() > 0) {
            FreeCoinsCard(
                reward = AdRewards.coinsForLevel(saveManager.currentLevel),
                leftToday = saveManager.adWatchesLeftToday(),
                onWatch = {
                    // Counted before the ad, so closing it early cannot be
                    // retried into an infinite loop of abandoned impressions.
                    if (saveManager.consumeAdWatch() < 0) {
                        onFeedback("سکهٔ امروزت رو گرفتیم — فردا برگرد")
                        return@FreeCoinsCard
                    }
                    AdManager.showRewarded(
                        onReward = {
                            val got = AdRewards.coinsForLevel(saveManager.currentLevel)
                            saveManager.addCoins(got)
                            ProgressState.sync(saveManager)
                            onFeedback("+${formatNumber(got)} سکه گرفتی 🪙")
                        },
                        onFail = {
                            onFeedback("تبلیغ آماده نبود؛ دوباره بزن")
                        }
                    )
                }
            )
            Spacer(Modifier.height(14.dp))
        }

        if (!billingManager.isReady) {
            // A real banner rather than a line of body text: a Myket billing
            // failure looks identical to an empty store otherwise, and the
            // player cannot tell whether there is nothing to buy or something
            // is broken.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.chip)
                    .border(1.dp, c.cardBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(text = "⏳", fontSize = 18.sp)
                Text(
                    text = stringResource(
                        if (billingManager.errorMessage != null) R.string.billing_unavailable
                        else R.string.shop_connecting
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // The featured offer first.
        FeaturedOfferCard(
            pack = MyketConfig.OFFER_PACK,
            billingManager = billingManager,
            activity = activity,
            onFeedback = onFeedback
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.coin_packs),
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))

        MyketConfig.COIN_PACKS.forEach { pack ->
            CoinPackRow(pack, billingManager, activity, onFeedback)
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The special offer, as a full-width hero card.
 *
 * This is the cheapest coin-per-rial in the store by a wide margin and it used
 * to not be rendered at all: `COIN_PACKS` does not contain it and the shop
 * only ever iterated `COIN_PACKS`, so the best price in the game was
 * unreachable. It leads the tab now, which is both the honest ranking and the
 * one that pays.
 *
 * The pulse here animates a *border colour*, not the card's scale. Scaling a
 * list row makes the whole column reflow twice a second.
 */
/**
 * The "watch an advert, get coins" card.
 *
 * The one row on this tab that does not cost money. It is a real earn path
 * rather than a consolation prize: the reward scales with the level so it stays
 * worth the tap at any depth, and the daily count is shown so the player knows
 * what is left rather than discovering it by being refused.
 */
@Composable
private fun FreeCoinsCard(
    reward: Long,
    leftToday: Int,
    onWatch: () -> Unit
) {
    val c = joyThemeColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(110), label = "freeCoinsPress")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(8.dp, RoundedCornerShape(22.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
            .clip(RoundedCornerShape(22.dp))
            .background(c.gradientCard)
            .border(1.5.dp, c.emerald.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onWatch)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(c.emerald.copy(alpha = 0.16f))
                .border(1.dp, c.emerald.copy(alpha = 0.3f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) { Text("🎬", fontSize = 22.sp) }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = "سکهٔ رایگان",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = c.textPrimary,
                    fontWeight = FontWeight.Black
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "+${formatNumber(reward)} سکه با تماشای یک تبلیغ",
                style = MaterialTheme.typography.bodySmall.copy(color = c.textSecondary)
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(c.chip)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "امروز $leftToday بار باقی مانده",
                    style = MaterialTheme.typography.labelSmall.copy(color = c.textMuted),
                    fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        NeonButton(
            text = "تماشا",
            onClick = onWatch,
            size = ButtonSize.Medium,
            containerColor = c.emerald,
            contentColor = Color.White,
            borderColor = c.emerald.copy(alpha = 0.4f)
        )
    }
}

/**
 * The featured "offer" pack — the cheapest thing in the store by a wide margin,
 * and the only one that is not also sold as a graded pack.
 *
 * It animates a *border colour* rather than its own scale. Scaling a list row
 * makes the whole column reflow twice a second.
 */
@Composable
private fun FeaturedOfferCard(
    pack: MyketConfig.CoinPack,
    billingManager: BillingManager,
    activity: Activity?,
    onFeedback: (String) -> Unit
) {
    val c = joyThemeColors()
    val pulse = rememberInfiniteTransition(label = "offerPulse")
    val phase by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "offerPhase"
    )
    val glow = 0.45f + 0.4f * phase

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(22.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
            .clip(RoundedCornerShape(22.dp))
            .background(c.gradientGoldSoft)
            .border(2.dp, c.amber.copy(alpha = glow), RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.gradientGold),
                contentAlignment = Alignment.Center
            ) { Text(text = "🎁", fontSize = 26.sp) }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.best_value),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.onGold,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(c.gradientGold)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    Text(
                        text = formatNumber(pack.coins),
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.textPrimary,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = stringResource(R.string.coins),
                        style = MaterialTheme.typography.labelMedium,
                        color = c.amber,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = rialToToman(pack.priceRial) + " " + stringResource(R.string.toman),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        NeonButton(
            text = stringResource(R.string.buy),
            onClick = {
                activity ?: return@NeonButton
                billingManager.purchase(
                    activity,
                    pack.sku,
                    // Both outcomes are reported. The offer pack used to pass
                    // `{}` for both, so a player who just paid 290,000 toman got
                    // no confirmation at all — the only evidence the sale
                    // happened was their balance quietly going up, and a failed
                    // sale was indistinguishable from a tap that did nothing.
                    onSuccess = { onFeedback("🎁 +${formatNumber(pack.coins)} سکه اضافه شد") },
                    onCancelled = { onFeedback("خرید انجام نشد") },
                    onError = { msg -> onFeedback(msg) }
                )
            },
            containerColor = c.amber,
            borderColor = c.amber.copy(alpha = 0.5f),
            contentColor = c.onGold,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CoinPackRow(
    pack: MyketConfig.CoinPack,
    billingManager: BillingManager,
    activity: Activity?,
    onFeedback: (String) -> Unit
) {
    val c = joyThemeColors()
    val savings = pack.savingsPercent()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // shadow BEFORE clip/background: a shadow chained after the
            // background paints on top of the card face instead of behind it.
            .shadow(4.dp, RoundedCornerShape(18.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, c.cardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.gradientGold),
            contentAlignment = Alignment.Center
        ) { Text(text = "🪙", fontSize = 22.sp) }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = formatNumber(pack.coins),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.coins),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = rialToToman(pack.priceRial) + " " + stringResource(R.string.toman),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    maxLines = 1
                )
                if (savings > 0) {
                    Text(
                        text = stringResource(R.string.save_percent, savings),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.teal,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            // What the pack is actually worth, in the currency the player
            // understands: levels of play. "53,000 coins" is a number; "about
            // 40 levels" is a decision. Priced against the late-game income the
            // engine runs on, which is the only figure that means anything once
            // the upgrade track is the thing being bought.
            Text(
                text = stringResource(
                    R.string.pack_worth_levels,
                    ((pack.coins / LATE_GAME_COINS_PER_LEVEL).coerceAtLeast(1L)).toString()
                ),
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted,
                maxLines = 1
            )
        }

        Spacer(Modifier.width(10.dp))

        NeonButton(
            text = stringResource(R.string.buy),
            onClick = {
                activity ?: return@NeonButton
                billingManager.purchase(
                    activity,
                    pack.sku,
                    onSuccess = { onFeedback("+${formatNumber(pack.coins)} سکه اضافه شد 🪙") },
                    onCancelled = { onFeedback("خرید انجام نشد") },
                    onError = { msg -> onFeedback(msg) }
                )
            },
            containerColor = c.emerald,
            borderColor = c.emerald.copy(alpha = 0.45f),
            size = ButtonSize.Small
        )
    }
}

/**
 * Coins a deep level pays, used to translate a pack into "levels of play".
 *
 * The number the design works against — see the coin-ladder note in [MyketConfig]
 * — rather than an average over the whole game, because a pack is bought by a
 * player who is already deep enough to want one.
 */
private const val LATE_GAME_COINS_PER_LEVEL = 1_275L

// ───────────────────────────────────────────────────────────────────────────
//  Skins
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun SkinsTab(skinManager: SkinManager, onFeedback: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SkinSection(
            title = stringResource(R.string.skins_balls),
            skins = skinManager.getSkinsByType(SkinType.BALL),
            selectedId = skinManager.selectedBallSkin,
            isOwned = { skinManager.isBallOwned(it) },
            onSelect = { skinManager.selectBall(it) },
            onPurchase = { id ->
                when (skinManager.purchaseBall(id)) {
                    is SkinManager.PurchaseResult.Success -> onFeedback("✅ خرید موفق!")
                    is SkinManager.PurchaseResult.NotEnoughCoins -> onFeedback("💰 سکه کم داری!")
                    is SkinManager.PurchaseResult.PremiumRequired -> onFeedback("★ نیاز به پریمیوم داری!")
                    else -> onFeedback("❌ خطا")
                }
            }
        )

        Spacer(Modifier.height(22.dp))

        SkinSection(
            title = stringResource(R.string.skins_cannons),
            skins = skinManager.getSkinsByType(SkinType.CANNON),
            selectedId = skinManager.selectedCannonSkin,
            isOwned = { skinManager.isCannonOwned(it) },
            onSelect = { skinManager.selectCannon(it) },
            onPurchase = { id ->
                when (skinManager.purchaseCannon(id)) {
                    is SkinManager.PurchaseResult.Success -> onFeedback("✅ خرید موفق!")
                    is SkinManager.PurchaseResult.NotEnoughCoins -> onFeedback("💰 سکه کم داری!")
                    is SkinManager.PurchaseResult.PremiumRequired -> onFeedback("★ نیاز به پریمیوم داری!")
                    else -> onFeedback("❌ خطا")
                }
            }
        )

        Spacer(Modifier.height(22.dp))

        SkinSection(
            title = stringResource(R.string.skins_backgrounds),
            skins = skinManager.getSkinsByType(SkinType.BACKGROUND),
            selectedId = skinManager.selectedBackgroundSkin,
            isOwned = { skinManager.isBackgroundOwned(it) },
            onSelect = { skinManager.selectBackground(it) },
            onPurchase = { id ->
                when (skinManager.purchaseBackground(id)) {
                    is SkinManager.PurchaseResult.Success -> onFeedback("✅ خرید موفق!")
                    is SkinManager.PurchaseResult.NotEnoughCoins -> onFeedback("💰 سکه کم داری!")
                    is SkinManager.PurchaseResult.PremiumRequired -> onFeedback("★ نیاز به پریمیوم داری!")
                    else -> onFeedback("❌ خطا")
                }
            }
        )

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SkinSection(
    title: String,
    skins: List<Skin>,
    selectedId: String,
    isOwned: (String) -> Boolean,
    onSelect: (String) -> Unit,
    onPurchase: (String) -> Unit
) {
    val c = joyThemeColors()
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))

        // Two per row. The old version used heightIn(min=...) with free-form
        // content, so a card that grew a "premium" chip pushed its row taller
        // and the second column drifted out of alignment with the one below.
        // Each card now lays out its own footer, so rows stay uniform.
        val rows = skins.chunked(2)
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { skin ->
                    val owned = isOwned(skin.id)
                    SkinCard(
                        skin = skin,
                        isSelected = skin.id == selectedId,
                        isOwned = owned,
                        onClick = { if (owned) onSelect(skin.id) else onPurchase(skin.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Odd last row: an empty equal-weight slot so the lone card
                // keeps the same width as its counterpart above.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            if (index < rows.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SkinCard(
    skin: Skin,
    isSelected: Boolean,
    isOwned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = joyThemeColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Press scales only the card under the finger, and only while pressed. A
    // permanently animating card (the old best-value pulse) makes the whole
    // grid look unstable.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(110),
        label = "skinPress"
    )

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) c.surfaceSoft else c.surface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) c.emerald else c.cardBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The preview.
        //
        // This was a flat rounded square filled with the skin's colour. For a
        // ball that meant the tile for the fire ball, the star and the gold
        // gem were three identical squares in three different colours — the
        // player was asked to buy a shape change and shown a swatch instead.
        //
        // Balls now render the real sprite (animated, so a rotating star or a
        // lit fuse reads as alive), backgrounds render their actual sky and
        // horizon, and cannons keep the disc, which is what a cannon is.
        when (skin.type) {
            SkinType.BALL -> BallSkinPreview(
                visual = BallVisuals.byId(skin.id),
                selected = isSelected,
                accent = c.emerald,
                border = c.cardBorder,
                modifier = Modifier.size(62.dp)
            )
            SkinType.BACKGROUND -> BackdropSkinPreview(
                backdrop = GameBackdrops.byId(skin.id),
                selected = isSelected,
                accent = c.emerald,
                border = c.cardBorder,
                modifier = Modifier.size(62.dp)
            )
            SkinType.CANNON -> Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(CannonVisuals.byId(skin.id).trim.copy(alpha = 0.30f))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) c.emerald else c.cardBorder,
                        shape = RoundedCornerShape(15.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                drawCannonPreview(CannonVisuals.byId(skin.id))
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .offset(x = (-4).dp, y = 4.dp)
                    .size(17.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(c.emerald),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✓", color = c.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = skin.name,
            style = MaterialTheme.typography.labelMedium,
            color = c.textPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        when {
            isSelected -> StatusTag(
                text = stringResource(R.string.selected),
                fg = c.emerald,
                bg = c.emerald.copy(alpha = 0.14f)
            )
            isOwned -> StatusTag(
                text = stringResource(R.string.select_skin),
                fg = c.textSecondary,
                bg = c.chip
            )
            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(c.gradientGold)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = "🪙", fontSize = 11.sp)
                    Text(
                        text = formatNumber(skin.finalPrice),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.onGold,
                        fontWeight = FontWeight.Black
                    )
                }
                if (skin.isPremium) {
                    Text(
                        text = stringResource(R.string.premium),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.teal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * A live preview of a ball skin — the same [drawBallSprite] the game uses, on
 * a slow rotation.
 *
 * The rotation is not decoration: several forms (star, crystal, spiked) are
 * only legible as themselves when they turn, and a static tile of one would
 * make a spiked ball and a smooth orb hard to tell apart.
 */
@Composable
private fun BallSkinPreview(
    visual: BallVisual,
    selected: Boolean,
    accent: Color,
    border: Color,
    modifier: Modifier = Modifier
) {
    val spin = rememberInfiniteTransition(label = "ballPreview").animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
        label = "ballPreviewSpin"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            // A soft dark well, so a pale ball (ice, gold) is not invisible on a
            // white card.
            .background(Color(0xFF0B1220).copy(alpha = 0.10f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else border,
                shape = RoundedCornerShape(15.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawBallSprite(
                visual = visual,
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                radius = size.minDimension * 0.34f,
                phase = spin.value
            )
        }
    }
}

/**
 * A live preview of a background skin: its real sky gradient with its real
 * horizon colours, so the tile shows the scene rather than a single flat
 * colour sampled from it.
 */
@Composable
private fun BackdropSkinPreview(
    backdrop: GameBackdrop,
    selected: Boolean,
    accent: Color,
    border: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else border,
                shape = RoundedCornerShape(15.dp)
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(backdrop.sky))
            // The light source, in roughly its real position.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(backdrop.orbGlow.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width * 0.76f, size.height * 0.24f),
                    radius = size.minDimension * 0.55f
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.76f, size.height * 0.24f)
            )
            drawCircle(
                color = backdrop.orb,
                radius = size.minDimension * 0.11f,
                center = Offset(size.width * 0.76f, size.height * 0.24f)
            )
            // The three terrain bands, in their real order.
            val horizon = size.height * 0.68f
            drawRect(backdrop.farTerrain, topLeft = Offset(0f, horizon - size.height * 0.06f), size = Size(size.width, size.height))
            drawRect(backdrop.midTerrain, topLeft = Offset(0f, horizon + size.height * 0.04f), size = Size(size.width, size.height))
            drawRect(backdrop.nearTerrain, topLeft = Offset(0f, horizon + size.height * 0.16f), size = Size(size.width, size.height))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, backdrop.vignette.copy(alpha = 0.45f))))
        }
    }
}

/**
 * A cannon: barrel, body and base, matching how it is drawn in game.
 *
 * Drawn from the same [CannonVisual] the play field uses, so the tile shows the
 * trim ring and the glow that the game draws. It used to be handed one `Color`
 * and fill everything with it, which meant a shop full of cannons that all
 * looked like the same cannon in a different tint — and a tile that showed less
 * than the purchase delivered.
 */
@Composable
private fun drawCannonPreview(skin: CannonVisual) {
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.58f
        val r = size.minDimension * 0.20f
        drawCircle(skin.glow.copy(alpha = 0.22f), radius = r * 1.7f, center = Offset(cx, cy))
        drawCircle(skin.trim.copy(alpha = 0.75f), radius = r * 1.18f, center = Offset(cx, cy))
        drawCircle(skin.body, radius = r, center = Offset(cx, cy))
        drawCircle(skin.lit.copy(alpha = 0.55f), radius = r * 0.55f, center = Offset(cx - r * 0.2f, cy - r * 0.22f))
        // Barrel, with its trim collar.
        drawRoundRect(
            color = skin.body,
            topLeft = Offset(cx - r * 0.22f, cy - r * 2.1f),
            size = Size(r * 0.44f, r * 1.2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.2f, r * 0.2f)
        )
        drawRoundRect(
            color = skin.trim,
            topLeft = Offset(cx - r * 0.34f, cy - r * 1.05f),
            size = Size(r * 0.68f, r * 0.22f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.1f, r * 0.1f)
        )
        // Base.
        drawRoundRect(
            color = skin.trim,
            topLeft = Offset(cx - r * 0.9f, cy + r * 0.7f),
            size = Size(r * 1.8f, r * 0.6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.25f, r * 0.25f)
        )
    }
}

@Composable
private fun StatusTag(text: String, fg: Color, bg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

// ───────────────────────────────────────────────────────────────────────────
//  Premium
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun PremiumTab(
    billingManager: BillingManager,
    activity: Activity?,
    onFeedback: (String) -> Unit
) {
    val c = joyThemeColors()
    val pulse = rememberInfiniteTransition(label = "crownPulse")
    val crownScale by pulse.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "crownScale"
    )
    val sparkle by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Restart),
        label = "sparkle"
    )

    // Top-aligned, not centre-arranged. A vertically-centred scrollable Column
    // jumps when its content changes height between builds; pinning the top and
    // letting it scroll is stable.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
                .clip(RoundedCornerShape(24.dp))
                .background(c.surface)
                .border(1.5.dp, c.amber.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Decorative sparkles. Drawn in the layer behind the content, and
            // gated on the theme so they are visible in both palettes instead of
            // white-on-white.
            Box(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                    val t = sparkle
                    for (i in 0 until 24) {
                        val x = (0.06f + 0.88f * abs(sin(i * 9.31f))) * size.width
                        val y = (0.05f + 0.9f * abs(sin(i * 4.17f + 0.9f))) * size.height
                        val a = 0.12f + 0.2f * abs(sin(t * 6.28f + i))
                        drawCircle(
                            color = c.amber.copy(alpha = a),
                            radius = 1.2f + (i % 3) * 0.7f,
                            center = androidx.compose.ui.geometry.Offset(x, y)
                        )
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .graphicsLayer { scaleX = crownScale; scaleY = crownScale }
                            .clip(RoundedCornerShape(20.dp))
                            .background(c.gradientGold)
                            .border(1.5.dp, c.cardBorder, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text(text = "👑", fontSize = 34.sp) }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.premium),
                        style = MaterialTheme.typography.headlineMedium,
                        color = c.textPrimary,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = stringResource(R.string.premium_perks),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(20.dp))

                    PerkRow("🚫", stringResource(R.string.premium_perk_no_ads))
                    Spacer(Modifier.height(8.dp))
                    PerkRow("❤️", stringResource(R.string.premium_perk_hearts))
                    Spacer(Modifier.height(8.dp))
                    PerkRow("✨", stringResource(R.string.premium_perk_skins))
                    Spacer(Modifier.height(8.dp))
                    PerkRow("💎", stringResource(R.string.premium_perk_support))
                    Spacer(Modifier.height(8.dp))
                    // The two that were missing.
                    //
                    // The card listed four perks while the product ships six. The
                    // two omitted ones are the only ones with a number on them —
                    // 40% off everything, and double coins every run — which are
                    // also the two that pay for the subscription on their own. A
                    // list that leaves out the most valuable rows is not a value
                    // proposition, it is a description of a different product.
                    PerkRow("🏷️", stringResource(R.string.premium_perk_discount))
                    Spacer(Modifier.height(8.dp))
                    PerkRow("🪙", stringResource(R.string.premium_perk_double_coins))

                    Spacer(Modifier.height(22.dp))

                    if (billingManager.isPremium) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(c.emerald.copy(alpha = 0.14f))
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text(text = "✓", color = c.emerald, fontWeight = FontWeight.Black)
                            Text(
                                text = stringResource(R.string.premium_purchased),
                                style = MaterialTheme.typography.titleSmall,
                                color = c.emerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        NeonButton(
                            text = stringResource(R.string.buy_premium) + "  •  " +
                                rialToToman(MyketConfig.PREMIUM_PRICE_RIAL) + " " +
                                stringResource(R.string.toman),
                            onClick = {
                                activity ?: return@NeonButton
                                billingManager.purchase(
                                    activity, MyketConfig.SKU_PREMIUM,
                                    onSuccess = { onFeedback("👑 پریمیوم فعال شد!") },
                                    onCancelled = {},
                                    onError = { msg -> onFeedback(msg) }
                                )
                            },
                            containerColor = c.amber,
                            borderColor = c.amber.copy(alpha = 0.5f),
                            contentColor = c.onGold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 340.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        // Restore purchases.
                        //
                        // There was no way to do this from the app. Billing
                        // re-queries on every launch, so a purchase is normally
                        // restored silently — but "normally" is doing a lot of
                        // work in that sentence, and when it fails the paying
                        // player's only remaining options are a reinstall or a
                        // support ticket. A player who has already given money
                        // gets a button that asks the store again.
                        NeonButton(
                            text = stringResource(R.string.restore_purchases),
                            onClick = {
                                activity ?: return@NeonButton
                                billingManager.restorePurchases { msg -> onFeedback(msg) }
                            },
                            containerColor = c.surface,
                            contentColor = c.textSecondary,
                            borderColor = c.cardBorder,
                            size = ButtonSize.Small,
                            modifier = Modifier.widthIn(max = 340.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PerkRow(icon: String, text: String) {
    val c = joyThemeColors()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surfaceSoft)
            .border(1.dp, c.cardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.amber.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Text(text = icon, fontSize = 16.sp) }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = c.textPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}
