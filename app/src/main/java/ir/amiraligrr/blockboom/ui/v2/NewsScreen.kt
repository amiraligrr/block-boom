package ir.amiraligrr.blockboom.ui.v2

import androidx.compose.foundation.background
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.data.NewsItem
import ir.amiraligrr.blockboom.ui.components.AppIcon
import ir.amiraligrr.blockboom.ui.components.AppIconView
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Full news reader: one page per published item, horizontal swipe, explicit
 * page dots. The body scrolls vertically; long posts stay readable.
 */
@Composable
fun NewsScreen(news: List<NewsItem>, onBack: () -> Unit) {
    val c = joyThemeColors()
    val pager = rememberPagerState(pageCount = { news.size.coerceAtLeast(1) })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.gradientBackground)
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
                    .background(c.surface)
                    .border(1.dp, c.cardBorder, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                AppIconView(icon = AppIcon.BACK, tint = c.textPrimary, size = 20.dp, strokeWidth = 2.dp)
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text("اخبار بازی", color = c.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text(
                    if (news.isEmpty()) "خبری منتشر نشده" else "بین خبرها ورق بزن",
                    color = c.textSecondary,
                    fontSize = 11.sp
                )
            }
        }

        if (news.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AppIconView(icon = AppIcon.BELL, tint = c.emerald, size = 38.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "هنوز خبری منتشر نشده است",
                    color = c.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            return
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("newsPager")
        ) { page ->
            val item = news[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                NewsImage(item.imageUrl)
                Text(
                    text = item.title,
                    color = c.textPrimary,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 32.sp
                )
                if (item.publishedAt.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(item.publishedAt, color = c.textMuted, fontSize = 10.sp)
                }
                if (item.summary.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.surface)
                            .border(1.dp, c.cardBorder.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            item.summary,
                            color = c.textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 22.sp
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = item.body,
                    color = c.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 25.sp
                )
                Spacer(Modifier.height(32.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(news.size.coerceAtMost(12)) { i ->
                val selected = i == pager.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (selected) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (selected) c.emerald else c.textMuted.copy(alpha = 0.35f))
                )
            }
        }
    }
}


/** Loads an optional remote image; a broken URL simply renders nothing. */
@Composable
private fun NewsImage(url: String) {
    if (url.isBlank()) return
    var bitmap by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                URL(url).openConnection().apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                }.getInputStream().use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            } catch (_: Throwable) {
                null
            }
        }
    }
    bitmap?.let {
        Spacer(Modifier.height(10.dp))
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(10.dp))
    }
}
