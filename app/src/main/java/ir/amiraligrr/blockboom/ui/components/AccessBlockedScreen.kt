package ir.amiraligrr.blockboom.ui.components

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.data.AccessState
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors

/**
 * The full-screen block shown when the server says this device or this build
 * may not play.
 *
 * Deliberately not dismissible and not reachable from navigation: whatever
 * route the app restored, the gate renders instead of it, so there is no
 * back gesture that gets you into a banned game.
 */
@Composable
fun AccessBlockedScreen(
    state: AccessState,
    onContactSupport: () -> Unit
) {
    val c = joyThemeColors()
    val context = LocalContext.current
    val banned = state is AccessState.Banned
    val message = when (state) {
        is AccessState.Banned -> state.reason
        is AccessState.VersionBlocked -> state.message
        else -> ""
    }
    val storeUrl = (state as? AccessState.VersionBlocked)?.storeUrl.orEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.gradientBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(
                        Brush.verticalGradient(
                            if (banned) listOf(Color(0xFFF87171), Color(0xFFDC2626))
                            else listOf(Color(0xFFFBBF24), Color(0xFFF59E0B))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(if (banned) "🚫" else "⬆️", fontSize = 44.sp)
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = if (banned) "حساب شما مسدود شده" else "نیاز به به‌روزرسانی",
                style = MaterialTheme.typography.headlineSmall,
                color = c.textPrimary,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            // One box, 48dp+ touch target, always centred. Long admin-written
            // messages have to stay readable, hence the scroll.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.surface)
                    .border(1.dp, c.cardBorder, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                )
            }

            Spacer(Modifier.height(22.dp))

            if (!banned && storeUrl.isNotBlank()) {
                NeonButton(
                    text = "به‌روزرسانی از فروشگاه",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl))
                                )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.XLarge,
                    containerColor = c.emerald
                )
                Spacer(Modifier.height(10.dp))
            }

            if (banned) {
                Text(
                    text = "اگر فکر می‌کنید این اشتباه است، از دکمهٔ زیر تیکت بزنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                // Said plainly, because the ban is stored on the device and a
                // player who clears app data to "fix it" will find that it
                // did not work — and an unexplained permanent block is what
                // turns a fair decision into a one-star review.
                Text(
                    text = "این محدودیت روی همین دستگاه ذخیره شده؛ با پاک کردن داده‌ها یا نصب دوباره برطرف نمی‌شود.",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
                Spacer(Modifier.height(10.dp))
            }

            NeonButton(
                text = "تماس با پشتیبانی",
                onClick = onContactSupport,
                modifier = Modifier.fillMaxWidth(),
                size = ButtonSize.Large,
                containerColor = c.surface,
                contentColor = c.textPrimary,
                borderColor = c.cardBorder
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "شناسهٔ دستگاه شما:\n${ir.amiraligrr.blockboom.data.LeaderboardManager.getDeviceId(context)}",
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}
