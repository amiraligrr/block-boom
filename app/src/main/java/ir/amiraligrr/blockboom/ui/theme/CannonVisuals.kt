package ir.amiraligrr.blockboom.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The cannon's complete appearance: body, trim and halo.
 *
 * ── Why this exists ────────────────────────────────────────────────────────
 * A cannon skin used to be a single [Color] handed to the renderer, so every
 * cannon on sale was the same cannon in a different hue: one body circle, one
 * base, one barrel, all of them filled with the same value. Two skins side by
 * side in the shop were two coloured discs, and on a crowded board — where the
 * cannon is one shape among a hundred — the difference was not readable at all.
 *
 * Three values is the minimum for the cannon to read as an object: a **body**
 * (the mass), a **trim** (the ring, the base and a band across the top, which is
 * the part the eye separates on a busy field) and a **glow** (the halo, which is
 * how a premium cannon announces itself across the whole play area).
 */
data class CannonVisual(
    val body: Color,
    val trim: Color,
    val glow: Color
) {
    /** Lighter body tone, for the top-left lit face. */
    val lit: Color get() = lightenColor(body, 0.40f)
}

/** A hue's own lighter version — the same shading rule the balls use. */
private fun lightenColor(c: Color, amount: Float): Color = Color(
    c.red + (1f - c.red) * amount,
    c.green + (1f - c.green) * amount,
    c.blue + (1f - c.blue) * amount,
    c.alpha
)

/**
 * Every purchasable cannon, keyed by the ids the save file already uses.
 *
 * The pre-existing ids are kept so an install that already bought `steel_cannon`
 * keeps it; only the trim and glow are new. Each entry is a *different cannon*,
 * not a recolour — that is the whole reason this table exists.
 */
object CannonVisuals {

    val all: Map<String, CannonVisual> = linkedMapOf(
        "cannon_default" to CannonVisual(
            body = Color(0xFF22D3EE),
            trim = Color(0xFF0E7490),
            glow = Color(0xFF67E8F9)
        ),
        "steel_cannon" to CannonVisual(
            body = Color(0xFFB0BEC5),
            trim = Color(0xFF37474F),
            glow = Color(0xFFCFD8DC)
        ),
        "chrome_cannon" to CannonVisual(
            body = Color(0xFFE2E8F0),
            // Near-white body with a *blue* trim: the value contrast is what
            // makes chrome look like chrome rather than like a pale ball.
            trim = Color(0xFF475569),
            glow = Color(0xFFF8FAFC)
        ),
        "dragon_cannon" to CannonVisual(
            body = Color(0xFFF59E0B),
            trim = Color(0xFF7F1D1D),
            glow = Color(0xFFFBBF24)
        ),
        "laser_cannon" to CannonVisual(
            body = Color(0xFFA855F7),
            trim = Color(0xFF2E1065),
            glow = Color(0xFFE879F9)
        ),
        "emerald_cannon" to CannonVisual(
            body = Color(0xFF10B981),
            trim = Color(0xFF064E3B),
            glow = Color(0xFF6EE7B7)
        ),
        "frost_cannon" to CannonVisual(
            body = Color(0xFF38BDF8),
            trim = Color(0xFFE0F2FE),
            // Light trim on a mid body: the only skin whose highlight is the
            // *lighter* of the two, which is what ice looks like.
            glow = Color(0xFFBAE6FD)
        ),
        "void_cannon" to CannonVisual(
            body = Color(0xFF1A1A2E),
            trim = Color(0xFF7C3AED),
            glow = Color(0xFF4C1D95)
        )
    )

    val defaultId = "cannon_default"

    fun byId(id: String?): CannonVisual {
        // `default` is the pre-account save's spelling of the starter cannon.
        val key = if (id == "default") defaultId else id
        return all[key] ?: all.getValue(defaultId)
    }
}
