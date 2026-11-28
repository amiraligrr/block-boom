package ir.amiraligrr.blockboom.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.data.GateStatus
import ir.amiraligrr.blockboom.ui.components.AppIcon
import ir.amiraligrr.blockboom.ui.components.AppIconView
import ir.amiraligrr.blockboom.ui.components.ButtonSize
import ir.amiraligrr.blockboom.ui.components.NeonButton
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors

@Composable
fun OnlineGateScreen(
    status: GateStatus,
    onRetry: () -> Unit,
    onContinueOffline: () -> Unit = {},
) {
    val c = joyThemeColors()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.gradientBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(c.emerald.copy(alpha = 0.22f), c.surface)
                        )
                    )
                    .border(1.dp, c.cardBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (status == GateStatus.CHECKING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(34.dp),
                        color = c.emerald,
                        strokeWidth = 3.dp,
                    )
                } else {
                    AppIconView(
                        icon = if (status == GateStatus.OFFLINE) AppIcon.BELL else AppIcon.SUPPORT,
                        tint = c.emerald,
                        size = 34.dp,
                        strokeWidth = 2.dp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = when (status) {
                    GateStatus.CHECKING -> "در حال بررسی اتصال…"
                    GateStatus.OFFLINE -> "اتصال به اینترنت برقرار نیست"
                    GateStatus.SERVER_ERROR -> "سرور بازی در دسترس نیست"
                    GateStatus.ONLINE -> ""
                },
                color = c.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = when (status) {
                    GateStatus.CHECKING ->
                        "در حال بررسی اینترنت، سرور و وضعیت حساب…"
                    GateStatus.OFFLINE ->
                        "برای ورود به بازی به اینترنت نیاز داریم؛ چون باید اطلاعات حساب، پیشرفت بازی و رکوردهای شما را برای ثبت و همگام‌سازی با لیدربورد در سرور ذخیره کنیم.\n\nلطفاً اتصال اینترنت خود را بررسی کنید و دوباره تلاش کنید."
                    GateStatus.SERVER_ERROR ->
                        "اینترنت وصل است، اما سرور بازی پاسخ نمی‌دهد. اطلاعات شما بدون تأیید سرور همگام‌سازی نمی‌شود؛ لطفاً چند لحظه بعد دوباره تلاش کنید."
                    GateStatus.ONLINE -> ""
                },
                color = c.textSecondary,
                fontSize = 13.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )

            if (status != GateStatus.CHECKING) {
                Spacer(Modifier.height(22.dp))
                NeonButton(
                    text = "تلاش مجدد",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Large,
                    containerColor = c.emerald,
                    borderColor = c.emerald.copy(alpha = 0.5f),
                    contentColor = c.onPrimary,
                    icon = AppIcon.BOLT,
                )
                Spacer(Modifier.height(10.dp))
                NeonButton(
                    text = "ادامه با اطلاعات محلی",
                    onClick = onContinueOffline,
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Medium,
                    containerColor = c.surface,
                    borderColor = c.cardBorder,
                    contentColor = c.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "در این حالت لیدربورد و همگام‌سازی تا وصل‌شدن سرور غیرفعال است.",
                    color = c.textMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
