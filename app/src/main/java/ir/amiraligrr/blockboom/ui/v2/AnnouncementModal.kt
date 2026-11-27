package ir.amiraligrr.blockboom.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ir.amiraligrr.blockboom.data.RewardService
import ir.amiraligrr.blockboom.ui.components.AppIcon
import ir.amiraligrr.blockboom.ui.components.AppIconView
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors

/**
 * One server announcement, shown as a modal.
 *
 * The backend has had announcements and per-device read receipts for a long
 * time, but the new home never rendered them. This closes that loop: one
 * announcement at a time, explicit close, then the next item in the queue.
 */
@Composable
fun AnnouncementModal(
    announcement: RewardService.Announcement,
    onClose: () -> Unit,
) {
    val c = joyThemeColors()
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(c.gradientCardElevated, RoundedCornerShape(26.dp))
                .border(1.dp, c.cardBorder, RoundedCornerShape(26.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.radialGradient(
                            listOf(c.emerald.copy(alpha = 0.22f), Color.Transparent)
                        )
                    )
                    .padding(12.dp)
            ) {
                AppIconView(icon = AppIcon.BELL, tint = c.emerald, size = 30.dp, strokeWidth = 2.dp)
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = announcement.title.ifBlank { "اطلاعیه" },
                color = c.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            if (announcement.createdAt.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = announcement.createdAt,
                    color = c.textMuted,
                    fontSize = 10.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = announcement.message,
                    color = c.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.emerald.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .border(1.dp, c.emerald.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconView(icon = AppIcon.CLOSE, tint = c.emerald, size = 15.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "بستن",
                    color = c.emerald,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
