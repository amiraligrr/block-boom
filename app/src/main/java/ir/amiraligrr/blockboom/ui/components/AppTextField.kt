package ir.amiraligrr.blockboom.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors

/**
 * The app's only text input.
 *
 * There used to be five separate inputs — an `OutlinedTextField` in the name
 * dialog and bare `TextField`s in onboarding and support — each with a
 * different, partly hand-picked set of colors. That is how a light screen
 * ended up with a field whose placeholder was unreadable: every color that was
 * not explicitly set fell through to a Material default that nobody had
 * checked against the real background behind it.
 *
 * Everything is set explicitly from theme tokens here, so the text, hint,
 * caret and focus ring are guaranteed to contrast with the surface. A new field
 * now means calling this, so the two cannot drift apart again.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    minHeight: Int = 56,
    maxLength: Int = Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle? = null
) {
    val c = joyThemeColors()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = if (focused) c.emerald else c.cardBorder,
        label = "fieldBorder"
    )
    val shape = RoundedCornerShape(16.dp)
    val base = textStyle ?: LocalTextStyle.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight.dp)
            .clip(shape)
            .background(c.surface)
            .border(if (focused) 2.dp else 1.5.dp, borderColor, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(Modifier.fillMaxWidth()) {
            // The hint sits above the value rather than inside it, so it never
            // has to be semi-transparent to make room and can use the full
            // contrast of the muted token.
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = c.textMuted,
                    style = base,
                    maxLines = 1
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= maxLength) onValueChange(it) },
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else minLines,
                textStyle = base.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.emerald),
                keyboardOptions = keyboardOptions,
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
