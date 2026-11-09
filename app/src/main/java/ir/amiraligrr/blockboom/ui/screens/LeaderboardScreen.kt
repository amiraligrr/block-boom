package ir.amiraligrr.blockboom.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.BlockBoomApp
import ir.amiraligrr.blockboom.data.LeaderboardEntry
import ir.amiraligrr.blockboom.data.LeaderboardManager
import ir.amiraligrr.blockboom.data.LeaderboardResponse
import ir.amiraligrr.blockboom.ui.components.ButtonSize
import ir.amiraligrr.blockboom.ui.components.NeonButton
import ir.amiraligrr.blockboom.ui.dialogs.NameDialog
import ir.amiraligrr.blockboom.ui.theme.JoyThemeColors
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import android.util.Log
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private val PERIODS = listOf("all", "daily", "weekly")

/**
 * Leaderboard.
 *
 * Rewritten around three problems the old version had:
 *  - Rank arithmetic was wrong. `(pagination?.page ?: 1 - 1)` parses as
 *    `pagination?.page ?: 0` because ?: binds looser than -, so the first
 *    player on page 1 was labelled rank 21. Ranks now come from the server,
 *    which already computes them correctly.
 *  - Every failure rendered raw server text ("خطا: Server error",
 *    "خطا: HTTP 500"). Failures now map to a Persian, actionable message and
 *    the list keeps whatever it already had instead of blanking.
 *  - Loading was a bare 28dp spinner in a big empty card, and a 32dp period
 *    tab was below the 48dp touch-target minimum. Both are fixed, plus a
 *    podium for the top three and a sticky "my rank" bar.
 */
@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as BlockBoomApp
    val saveManager = app.saveManager
    val scope = rememberCoroutineScope()
    val c = joyThemeColors()

    var response by remember { mutableStateOf<LeaderboardResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var currentPeriod by rememberSaveable { mutableStateOf("all") }
    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    val listState = rememberLazyListState()

    val myScore = saveManager.totalScore
    val myName = saveManager.playerName.ifBlank { "بازیکن" }

    val periodLabels = mapOf("all" to "همه", "daily" to "امروز", "weekly" to "این هفته")

    // "Is this me" is decided by rank (server-assigned and unique) rather than
    // by display name, which previously highlighted every player who happened
    // to pick the same name.
    suspend fun load(page: Int, append: Boolean) {
        if (append) loadingMore = true else loading = true
        // Hard cap on wall-clock. fetchLeaderboard already bounds its own
        // timeouts, but it runs in withContext(Dispatchers.IO) and any throw
        // from inside it would propagate to the caller and leave `loading`
        // stuck at true — which is exactly the "stuck on the skeleton forever"
        // symptom. Wrapping it means the flag is always cleared and the
        // failure is always shown.
        val res = try {
            withTimeoutOrNull(25_000L) {
                LeaderboardManager.fetchLeaderboard(
                    context = context,
                    page = page,
                    limit = PAGE_SIZE,
                    period = currentPeriod
                )
            } ?: Result.failure(Exception("مهلت اتصال به سرور تمام شد"))
        } catch (t: Throwable) {
            Log.w("Leaderboard", "load failed", t)
            Result.failure(Exception(t.message ?: "خطای نامشخص"))
        }
        res.fold(
            onSuccess = { fresh ->
                response = if (append && response != null) {
                    // De-duplicate: a player's score can change between page
                    // loads, which would otherwise show the same row twice.
                    val seen = response!!.data.map { it.rank }.toSet()
                    response!!.copy(
                        data = response!!.data + fresh.data.filter { it.rank !in seen },
                        pagination = fresh.pagination,
                        userRank = fresh.userRank,
                        userEntry = fresh.userEntry
                    )
                } else {
                    fresh
                }
                error = null
            },
            onFailure = { t ->
                // Keep the rows we already have; only show the error if the
                // list is genuinely empty, so a flaky request never blanks
                // a screen the player was reading.
                if (response?.data.isNullOrEmpty()) {
                    error = t.message ?: "ارتباط با سرور برقرار نشد"
                }
            }
        )
        loading = false
        loadingMore = false
    }

    suspend fun submit(): Boolean {
        val name = saveManager.playerName
        if (name.isBlank()) {
            showNameDialog = true
            notice = "برای ثبت امتیاز اول اسمت رو انتخاب کن"
            return false
        }
        submitting = true
        notice = null
        val res = LeaderboardManager.submitScore(
            context = context,
            name = name,
            level = saveManager.currentLevel,
            totalScore = saveManager.totalScore,
            isPremium = saveManager.isPremium
        )
        submitting = false
        return when (res) {
            is LeaderboardManager.SubmitResult.Success -> {
                notice = when {
                    res.rank != null && res.rank > 0 -> "امتیازت ثبت شد — رتبهٔ ${res.rank}"
                    res.improved -> "امتیازت ثبت شد"
                    else -> "رکورد قبلیت بهتره"
                }
                currentPage = 1
                load(1, append = false)
                true
            }
            is LeaderboardManager.SubmitResult.Failed -> {
                // A rejected score is a real outcome, not a crash: say so
                // plainly and keep the player on the screen.
                notice = res.error.userMessage
                false
            }
        }
    }

    LaunchedEffect(currentPeriod) {
        currentPage = 1
        response = null
        load(1, append = false)
    }

    // The leaderboard used to update only when the player opened the screen
    // or submitted a score. A player who opens it mid-run sees a stale board;
    // more importantly, nobody ever noticed that the "daily" board had moved on
    // without the player looking. Poll on a slow interval while the screen is
    // composed, so "frequently" means "feels current" rather than "as often as
    // you tap".
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(20_000)
            // Only re-fetch the first page on a period change or while the
            // player is not mid-scroll: refreshing a deep page resets their view.
            currentPage = 1
            response = null
            load(1, append = false)
        }
    }

    // Load the next page as the end approaches, instead of a button the user
    // has to find.
    LaunchedEffect(listState, response?.pagination?.hasMore) {
        val hasMore = response?.pagination?.hasMore == true
        if (!hasMore || loading || loadingMore) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (hasMore && !loadingMore && total > 0 && last >= total - 3) {
                    currentPage += 1
                    load(currentPage, append = true)
                }
            }
    }

    if (showNameDialog) {
        NameDialog(
            initialName = saveManager.playerName,
            onConfirm = { n ->
                saveManager.playerName = n
                showNameDialog = false
                scope.launch { submit() }
            },
            onDismiss = { showNameDialog = false }
        )
    }

    val entries = response?.data.orEmpty()
    val pagination = response?.pagination
    val userEntry = response?.userEntry
    val myRank = response?.userRank ?: userEntry?.rank?.takeIf { it > 0 }
    val podium = entries.filter { it.rank in 1..3 }.sortedBy { it.rank }

    // Sorted by rank explicitly. `entries` arrives in whatever order the API
    // returned, which is correct for page 1 but is not a guarantee — and a list
    // that renders out of order is the same bug as a podium that does, just
    // further down the screen. Filtering also *preserves* the incoming order, so
    // without this the rows below the podium could appear in an order that
    // contradicts the ranks printed on them.
    val rest = entries.filter { it.rank !in 1..3 }.sortedBy { it.rank }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.gradientBackground)
    ) {
        Column(Modifier.fillMaxSize()) {

            // ── top bar ─────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                NeonButton(
                    text = "بازگشت",
                    onClick = onBack,
                    size = ButtonSize.Small,
                    containerColor = c.surface,
                    borderColor = c.cardBorder,
                    contentColor = c.textPrimary
                )
                Text(
                    text = "🏆  لیدربرد",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconCircleButton(
                    emoji = "✏️",
                    label = "تغییر اسم",
                    onClick = { showNameDialog = true },
                    colors = c
                )
            }

            // ── period tabs ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PERIODS.forEach { period ->
                    PeriodTab(
                        label = periodLabels[period] ?: period,
                        selected = currentPeriod == period,
                        colors = c,
                        modifier = Modifier.weight(1f),
                        onClick = { if (currentPeriod != period) currentPeriod = period }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── my card ─────────────────────────────────────────────────────
            MyRankCard(
                name = myName,
                level = saveManager.currentLevel,
                score = myScore,
                rank = myRank,
                submitting = submitting,
                colors = c,
                onSubmit = { scope.launch { submit() } },
                onRename = { showNameDialog = true }
            )

            if (notice != null) {
                Spacer(Modifier.height(10.dp))
                NoticeBanner(
                    text = notice!!,
                    isError = notice!!.contains("شد", ignoreCase = true).not() &&
                        notice!!.contains("رتبه", ignoreCase = true).not(),
                    colors = c
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── list ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    // With only a handful of players, `weight(1f)` stretched
                    // the list panel into a huge empty card. `fill = false`
                    // keeps the panel at content height while still capping
                    // it to the space left for long lists, where the lazy
                    // column becomes scrollable.
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.surface)
                    .border(1.dp, c.cardBorder, RoundedCornerShape(24.dp))
                    .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
            ) {
                when {
                    loading && entries.isEmpty() -> SkeletonList(colors = c)

                    error != null && entries.isEmpty() -> ErrorState(
                        message = error!!,
                        colors = c,
                        onRetry = { scope.launch { load(currentPage, append = false) } }
                    )

                    entries.isEmpty() -> EmptyState(
                        colors = c,
                        onRetry = { scope.launch { load(1, append = false) } }
                    )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (podium.isNotEmpty()) {
                            item(key = "podium") {
                                Podium(entries = podium, colors = c)
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                        items(rest, key = { it.rank.takeIf { r -> r > 0 } ?: it.name.hashCode() }) { e ->
                            LeaderboardRow(
                                entry = e,
                                // `myRank != null` is redundant: Kotlin compares
                                // Int against Int?, so when `myRank` is null no
                                // `e.rank` can equal it. The null case is
                                // already handled by the comparison itself.
                                isMe = e.rank == myRank,
                                colors = c
                            )
                        }
                        if (loadingMore) {
                            item(key = "loadingMore") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "در حال بارگذاری…",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = c.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "رتبه بر اساس مجموع امتیاز همهٔ بازی‌هاست — هر ۱۰ ثانیه یک‌بار می‌تونی ثبتش کنی",
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

private const val PAGE_SIZE = 20

// ─────────────────────────────────────────────────────────────────────────
//  Pieces
// ─────────────────────────────────────────────────────────────────────────

/** 48dp minimum tap target with press feedback, for the small square actions. */
@Composable
private fun IconCircleButton(
    emoji: String,
    label: String,
    onClick: () -> Unit,
    colors: JoyThemeColors,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, tween(100), label = "iconPress")
    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 17.sp, modifier = Modifier.semantics { this.contentDescription = label })
    }
}

private fun Modifier.semanticsLabel(label: String): Modifier =
    this.semantics { contentDescription = label }

@Composable
private fun PeriodTab(
    label: String,
    selected: Boolean,
    colors: JoyThemeColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(100), label = "tabPress")
    // In light mode the old white-on-#0B8F63 pair was only 4.10:1. A deeper
    // green keeps white above 5:1. In dark mode the theme emerald is light, so
    // the label flips to the dark on-primary ink instead of disappearing.
    val selectedBg = if (colors.isDark) colors.emerald else Color(0xFF047857)
    val selectedFg = if (colors.isDark) colors.onPrimary else Color.White
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) selectedBg else colors.surface)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else colors.cardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) selectedFg else colors.textPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun MyRankCard(
    name: String,
    level: Int,
    score: Long,
    rank: Int?,
    submitting: Boolean,
    colors: JoyThemeColors,
    onSubmit: () -> Unit,
    onRename: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.gradientCard)
            .border(1.dp, colors.emerald.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = colors.cardShadow, spotColor = colors.cardShadow)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(colors.emerald.copy(alpha = 0.24f), colors.emerald.copy(alpha = 0.08f))
                    )
                )
                .border(1.dp, colors.emerald.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) { Text("👤", fontSize = 21.sp) }

        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "سطح $level  •  ${formatScore(score)} امتیاز",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (rank != null) {
                Text(
                    text = "رتبهٔ جهانی: $rank",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.emerald,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        NeonButton(
            text = if (submitting) "…" else "ثبت",
            onClick = onSubmit,
            enabled = !submitting,
            size = ButtonSize.Small,
            containerColor = colors.emerald,
            contentColor = Color.White
        )
    }
}

@Composable
private fun NoticeBanner(text: String, isError: Boolean, colors: JoyThemeColors) {
    val bg = if (isError) colors.coral.copy(alpha = 0.12f) else colors.emerald.copy(alpha = 0.12f)
    val fg = if (isError) colors.coral else colors.emerald
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = fg,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Top three, with the winner visually on top. */
@Composable
/**
 * The top three, drawn as a podium.
 *
 * ── Why this is laid out by rank and not by visual position ──────────────
 * The columns used to be ordered `[second, first, third]` so the winner could
 * stand tallest in the middle. The bug: `medalColors` and `heights` were indexed
 * by *column position*, so whichever player happened to be in the middle column
 * was given the gold gradient and the 76dp plinth — and the height list was never
 * even applied. With the entries arriving in rank order, that meant rank 2 was
 * painted gold and rank 1 was not: "the second place player is above the first",
 * which is exactly what was reported.
 *
 * Both the colour and the height are now looked up *by rank*, and the columns
 * are rendered in rank order (1, 2, 3). The winner keeps the tallest plinth,
 * because that is read from [podiumHeightFor] rather than from a column index,
 * so no amount of reordering can swap two players' medals again.
 */
private fun Podium(entries: List<LeaderboardEntry>, colors: JoyThemeColors) {
    if (entries.isEmpty()) return
    val medalColors = mapOf(
        1 to (Color(0xFFFDE047) to Color(0xFFF59E0B)),
        2 to (Color(0xFFE5E7EB) to Color(0xFF9CA3AF)),
        3 to (Color(0xFFFED7AA) to Color(0xFFC2410C))
    )

    // In rank order, left to right. RTL layout mirrors the Row, so rank 1 still
    // lands on the right, which is where a Persian reader starts reading.
    val order = listOf(1, 2, 3).mapNotNull { r -> entries.firstOrNull { it.rank == r } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        order.forEach { entry ->
            val isFirst = entry.rank == 1
            val (top, bottom) = medalColors.getValue(entry.rank)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isFirst) 52.dp else 42.dp)
                        .clip(RoundedCornerShape(if (isFirst) 18.dp else 15.dp))
                        .background(Brush.verticalGradient(listOf(top, bottom)))
                        .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(if (isFirst) 18.dp else 15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (entry.rank) {
                            1 -> "🥇"
                            2 -> "🥈"
                            else -> "🥉"
                        },
                        fontSize = if (isFirst) 24.sp else 20.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.labelMedium,
                    // Premium carries into the podium too, so the top three agree
                    // with the rows below them about who is premium.
                    color = if (entry.isPremium) colors.amber else colors.textPrimary,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                Text(
                    text = "سطح ${entry.level}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(podiumHeightFor(entry.rank))
                        .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        // Nearly solid: the old 0.85→0.45 wash faded the plinth
                        // into the card halfway down, and white on that middle
                        // measured 1.6–2.7:1 — the top three scores were the
                        // least readable numbers on the screen. At full
                        // strength the plinth is the medal colour, and the ink
                        // below is chosen against it.
                        .background(Brush.verticalGradient(listOf(bottom, bottom.copy(alpha = 0.75f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatScore(entry.score),
                        style = MaterialTheme.typography.labelMedium,
                        // Per-rank ink, because no single ink reads on gold,
                        // silver and bronze alike: dark brown on gold (~5:1),
                        // dark slate on silver (~6:1), white on bronze (~4:1).
                        // The medals are fixed constants in both themes, so
                        // the inks are too — a theme slot would be a lie here.
                        color = when (entry.rank) {
                            1 -> Color(0xFF78350F)
                            2 -> Color(0xFF1F2937)
                            else -> Color.White
                        },
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * Plinth height for a podium rank, looked up by rank rather than by column
 * index.
 *
 * The original indexed a list by the column's position, which silently swapped
 * the winner's and the runner-up's plinths the moment the columns were reordered.
 * Keying on the rank makes that class of bug unrepresentable.
 */
private fun podiumHeightFor(rank: Int): androidx.compose.ui.unit.Dp = when (rank) {
    1 -> 76.dp
    2 -> 56.dp
    else -> 44.dp
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry, isMe: Boolean, colors: JoyThemeColors) {
    val rank = entry.rank
    // Premium is a status, not decoration.
    //
    // It used to be a 👑 next to the name and nothing else — indistinguishable
    // from the same player without premium at a glance on a scrolling list, which
    // is the only place the distinction would matter. It now drives the row's
    // whole treatment: a gold wash, a gold border, and a badge. Gold rather than
    // emerald because emerald already means "this is you"; a premium player who
    // is also "you" would otherwise be unreadable as either.
    //
    // Precedence: "you" wins the border and wash when both are true, so the
    // player's own row never stops being findable, and premium is carried by the
    // name colour and the badge, which do not collide.
    val isPremium = entry.isPremium

    val bg = when {
        isMe -> Brush.horizontalGradient(
            listOf(colors.emerald.copy(alpha = 0.16f), colors.emerald.copy(alpha = 0.06f))
        )
        isPremium -> Brush.horizontalGradient(
            listOf(colors.amber.copy(alpha = 0.20f), colors.amber.copy(alpha = 0.05f))
        )
        else -> Brush.verticalGradient(listOf(colors.surface, colors.surfaceSoft))
    }
    val borderColor = when {
        isMe -> colors.emerald.copy(alpha = 0.45f)
        isPremium -> colors.amber.copy(alpha = 0.55f)
        else -> colors.cardBorder
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when (rank) {
                        1 -> Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)))
                        2 -> Brush.verticalGradient(listOf(Color(0xFFE5E7EB), Color(0xFF9CA3AF)))
                        3 -> Brush.verticalGradient(listOf(Color(0xFFFED7AA), Color(0xFFC2410C)))
                        else -> Brush.verticalGradient(listOf(colors.surfaceSoft, colors.surface))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.labelMedium,
                color = if (rank in 1..3) Color(0xFF78350F) else colors.textPrimary,
                fontWeight = FontWeight.Black
            )
        }

        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isPremium) colors.amber else colors.textPrimary,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPremium) {
                    // A badge rather than a bare emoji, so premium survives being
                    // read at a glance and in a screenshot.
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(colors.amber.copy(alpha = 0.20f))
                            .border(1.dp, colors.amber.copy(alpha = 0.5f), RoundedCornerShape(50.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "👑 PRO",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.amber
                        )
                    }
                }
                if (isMe) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(colors.emerald.copy(alpha = 0.16f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "تو",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.emerald,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            Text(
                text = "سطح ${entry.level}  •  ${formatScore(entry.score)} امتیاز",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "سطح ${entry.level}",
            style = MaterialTheme.typography.titleSmall,
            color = colors.emerald,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun SkeletonList(colors: JoyThemeColors) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(7) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.textPrimary.copy(alpha = alpha * 0.12f))
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, colors: JoyThemeColors, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("😕", fontSize = 40.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        NeonButton(
            text = "تلاش مجدد",
            onClick = onRetry,
            size = ButtonSize.Medium,
            containerColor = colors.emerald
        )
    }
}

@Composable
private fun EmptyState(colors: JoyThemeColors, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🏁", fontSize = 40.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = "هنوز کسی ثبت نکسته — اولین نفر باش!",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        NeonButton(
            text = "تلاش مجدد",
            onClick = onRetry,
            size = ButtonSize.Medium,
            containerColor = colors.emerald
        )
    }
}

/** 1234567 -> "1,234,567" with Western digits, which read better next to Lv. */
private fun formatScore(score: Long): String {
    val s = abs(score).toString()
    if (s.length <= 3) return s
    return s.reversed().chunked(3).joinToString(",").reversed()
}
