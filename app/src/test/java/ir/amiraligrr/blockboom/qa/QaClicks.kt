package ir.amiraligrr.blockboom.qa

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties

object QaClicks {

    /**
     * Does this node represent a control the player can press?
     *
     * A node carrying [SemanticsActions.OnClick] is a button, which covers
     * `Modifier.clickable`, `Button`, and the app's own NeonButton. A node with
     * an `OnClick` action whose label has already been disabled is one the app
     * decided is not clickable; those are skipped rather than failed, so a
     * genuinely greyed-out control does not look like a bug while a crashed tap
     * still does.
     */
    fun isClickable(node: SemanticsNode): Boolean =
        node.config.getOrNull(SemanticsActions.OnClick) != null

    fun isDisabled(node: SemanticsNode): Boolean =
        node.config.getOrNull(SemanticsProperties.Disabled) != null

    fun label(node: SemanticsNode): String {
        val text = node.config.getOrNull(SemanticsProperties.Text)
        if (text.isNullOrEmpty().not()) return text!!.joinToString(" ") { it.text }
        val cd = node.config.getOrNull(SemanticsProperties.ContentDescription)
        if (cd.isNullOrEmpty().not()) return cd!!.joinToString(" ") { it }
        return "(unlabelled)"
    }
}
