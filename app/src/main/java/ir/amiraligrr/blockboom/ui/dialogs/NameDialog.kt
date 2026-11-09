package ir.amiraligrr.blockboom.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.ui.components.ButtonSize
import ir.amiraligrr.blockboom.ui.components.AppTextField
import ir.amiraligrr.blockboom.ui.components.NeonButton
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors

/** Compiled once at file scope — this used to be rebuilt on every keystroke. */
//
// Includes combining marks (`\p{M}`) and ZWNJ (U+200C). ZWNJ is category Cf,
// not a letter, so a class of `\p{L}\p{N}` alone rejected it — and ZWNJ is how
// Persian is typed on an Android keyboard (`امیرعلی`, `مهدی‌رضا`). The player
// typed their name, the save button stayed disabled, and the error said the name
// contained a special character. This dialog is what recovers a score the player
// has not submitted yet, so it has to accept the name they actually typed.
private val NAME_RE = Regex("^[\\p{L}\\p{N}\\p{M} _\\-.\\u200C]+$")

@Composable
fun NameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = joyThemeColors()
    var name by remember { mutableStateOf(initialName) }
    val trimmed = name.trim()
    val isValid = trimmed.length in 2..16 && trimmed.matches(NAME_RE)

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(c.surface)
                .border(1.2.dp, c.cardBorder, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.gradientGold)
                        .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(text = "👤  نام مستعار", style = MaterialTheme.typography.labelSmall.copy(color = c.onGold, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "برای لیدربرد نامی انتخاب کن",
                    style = MaterialTheme.typography.titleMedium.copy(color = c.textPrimary, fontWeight = FontWeight.Black),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "۲ تا ۱۶ حرف، فقط حروف و عدد",
                    style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "مثلا: AmirAli",
                    maxLength = 16,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
                if (trimmed.isNotEmpty() && !isValid) {
                    Spacer(Modifier.height(6.dp))
                    Text(text = "نام باید ۲-۱۶ کاراکتر و بدون علامت خاص باشد", style = MaterialTheme.typography.bodySmall.copy(color = c.coral), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    NeonButton(
                        text = "ذخیره",
                        onClick = { if (isValid) onConfirm(trimmed) },
                        modifier = Modifier.weight(1f),
                        containerColor = c.emerald,
                        borderColor = Color.White,
                        contentColor = Color.White,
                        enabled = isValid,
                        size = ButtonSize.Medium
                    )
                    NeonButton(
                        text = "بعداً",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        containerColor = c.chip,
                        borderColor = c.cardBorder,
                        contentColor = c.textMuted,
                        size = ButtonSize.Medium
                    )
                }
            }
        }
    }
}
