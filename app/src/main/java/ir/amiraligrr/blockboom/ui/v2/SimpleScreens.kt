package ir.amiraligrr.blockboom.ui.v2

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.data.Achievement
import ir.amiraligrr.blockboom.data.Achievements
import ir.amiraligrr.blockboom.data.AdManager
import ir.amiraligrr.blockboom.data.AdRewards
import ir.amiraligrr.blockboom.data.SaveManager
import ir.amiraligrr.blockboom.data.ProgressState
import ir.amiraligrr.blockboom.ui.formatNumber

private val V2BgTop = Color(0xFF0A0420)
private val V2BgBottom = Color(0xFF05020F)
private val V2Card = Color(0xFF1B0C36)
private val V2Line = Color(0xFF7048B8)
private val V2Text = Color(0xFFF6EDFF)
private val V2Muted = Color(0xFFB9A8D9)
private val V2Gold = Color(0xFFFFC93C)
private val V2Purple = Color(0xFF9B4DFF)
private val V2Green = Color(0xFF35E08B)

@Composable
private fun V2ScreenScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(V2BgTop, V2BgBottom)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(V2Card)
                    .border(1.dp, V2Line, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("‹", color = V2Text, fontSize = 26.sp) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = V2Text, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = V2Muted, fontSize = 11.sp)
            }
        }
        content()
    }
}

// ── Missions ─────────────────────────────────────────────────────────────────

@Composable
fun MissionsScreen(saveManager: SaveManager, onBack: () -> Unit) {
    LaunchedEffect(saveManager) { saveManager.refreshMissions() }
    val blocks = saveManager.missionBlocks
    val coins = saveManager.missionCoins
    val lucky = saveManager.missionLucky
    val claimed = saveManager.missionClaimed
    val complete = saveManager.canClaimMissions()

    V2ScreenScaffold("ماموریت روزانه", "هر روز سه کار، یک جایزه", onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(V2Card)
                    .border(1.dp, V2Line.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MissionProgress("🧱", "۳۰ بلاک بشکن", blocks, 30)
                    MissionProgress("🪙", "۸۰ سکه جمع کن", coins, 80)
                    MissionProgress("🍀", "۲ بلاک شانسی", lucky, 2)
                }
            }

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (complete) Brush.horizontalGradient(listOf(V2Gold, Color(0xFFFF8A2A)))
                        else Brush.verticalGradient(listOf(Color(0xFF2A174B), Color(0xFF1A0D33)))
                    )
                    .border(
                        1.dp,
                        if (complete) Color.White.copy(alpha = 0.55f) else V2Line.copy(alpha = 0.5f),
                        RoundedCornerShape(18.dp)
                    )
                    .clickable(enabled = complete) {
                        if (complete) {
                            saveManager.claimMissions()
                            ProgressState.sync(saveManager)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        claimed -> "✅ جایزه امروز گرفته شد"
                        complete -> "🎁 دریافت ${formatNumber(SaveManager.MISSION_REWARD_COINS)} سکه"
                        else -> "جایزه: ${formatNumber(SaveManager.MISSION_REWARD_COINS)} سکه"
                    },
                    color = if (complete) Color(0xFF2A1600) else V2Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun MissionProgress(emoji: String, label: String, current: Int, max: Int) {
    val fraction = (current.toFloat() / max).coerceIn(0f, 1f)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(label, color = V2Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("$current / $max", color = if (fraction >= 1f) V2Green else V2Muted, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF2A174B))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(V2Purple, V2Green)))
            )
        }
    }
}

// ── Achievements ─────────────────────────────────────────────────────────────

@Composable
fun AchievementsScreen(saveManager: SaveManager, onBack: () -> Unit) {
    val achievements = remember(saveManager) { Achievements(saveManager) }

    V2ScreenScaffold(
        "دستاوردها",
        "${formatNumber(achievements.unlockedCount())} از ${achievements.total()} باز شده",
        onBack
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(Achievement.entries, key = { it.id }) { a ->
                val unlocked = achievements.isUnlocked(a)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (unlocked) V2Card else Color(0xFF120825))
                        .border(
                            1.dp,
                            if (unlocked) V2Gold.copy(alpha = 0.55f) else V2Line.copy(alpha = 0.35f),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(a.emoji, fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(a.titleRes),
                            color = if (unlocked) V2Text else V2Muted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            stringResource(a.descRes),
                            color = V2Muted,
                            fontSize = 10.sp,
                            maxLines = 2
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (unlocked) "✓" else "🔒",
                            color = if (unlocked) V2Green else V2Muted,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "+${formatNumber(a.coins)} 🪙",
                            color = V2Gold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ── Free coins ───────────────────────────────────────────────────────────────

@Composable
fun FreeCoinsScreen(saveManager: SaveManager, onBack: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val left = saveManager.adWatchesLeftToday()
    val reward = AdRewards.coinsForLevel(saveManager.currentLevel)

    V2ScreenScaffold("سکه رایگان", "با تماشای تبلیغ، سکه بگیر", onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(V2Gold.copy(alpha = 0.35f), Color.Transparent))),
                contentAlignment = Alignment.Center
            ) {
                Text("🪙", fontSize = 76.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "+${formatNumber(reward)} سکه",
                color = V2Gold,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "برای هر تماشا",
                color = V2Muted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (left > 0 && !busy) Brush.verticalGradient(listOf(V2Gold, Color(0xFFFF8A2A)))
                        else Brush.verticalGradient(listOf(Color(0xFF2A174B), Color(0xFF1A0D33)))
                    )
                    .clickable(enabled = left > 0 && !busy) {
                        busy = true
                        message = null
                        AdManager.showRewarded(
                            onReward = {
                                busy = false
                                saveManager.addCoins(reward)
                                saveManager.consumeAdWatch()
                                ProgressState.sync(saveManager)
                                message = "🎉 ${formatNumber(reward)} سکه اضافه شد"
                            },
                            onFail = {
                                busy = false
                                message = "تبلیغ آماده نیست؛ چند لحظه بعد دوباره تلاش کن"
                            },
                            onShow = { busy = true },
                            onClose = { busy = false }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when {
                        busy -> "در حال آماده‌سازی…"
                        left <= 0 -> "سهم امروز تمام شد"
                        else -> "تماشای تبلیغ و دریافت سکه"
                    },
                    color = if (left > 0 && !busy) Color(0xFF2A1600) else V2Muted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "$left از ${AdRewards.MAX_ADS_PER_DAY} تماشا امروز باقی مانده",
                color = V2Muted,
                fontSize = 11.sp
            )
            message?.let {
                Spacer(Modifier.height(14.dp))
                Text(
                    it,
                    color = V2Text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
