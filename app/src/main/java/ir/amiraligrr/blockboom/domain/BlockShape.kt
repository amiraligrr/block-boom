package ir.amiraligrr.blockboom.domain

/**
 * Visual variety for blocks. Chosen with fixed weights at spawn so levels stay
 * lively without feeling random.
 *
 * ── Why the set grew ─────────────────────────────────────────────────────
 * Three shapes on a 30-block field meant the screen was a field of squares with
 * the occasional circle and the occasional hexagon — a pattern the eye groups
 * immediately and then stops seeing. Recognisably *different* silhouettes are
 * what make a crowded board readable, because the eye separates objects by
 * outline before it looks at colour at all. Five shapes with five distinct
 * outlines (rounded square, circle, hexagon, triangle, five-point star) give the
 * board something to actually track.
 *
 * ── Hitboxes are still rectangles ─────────────────────────────────────────
 * CollisionDetector tests every block as an axis-aligned rect and never reads
 * this enum, so adding shapes costs nothing in fairness or performance: a star
 * occupies the same box it always would. That is deliberate — a star whose
 * corners are not hittable is the kind of detail that reads as a bug, and
 * players who can see a corner they cannot hit stop trusting the whole board.
 *
 * New shapes must therefore be drawn to *fill* their bounding box as well as a
 * circle and a square do, so what is drawn matches what is hittable.
 */
enum class BlockShape {
    /** Rounded square — the workhorse. */
    SQUARE,

    CIRCLE,

    HEXAGON,

    /** Pointing up. Reads clearly against a square at the same size. */
    TRIANGLE,

    /** Five-pointed star. The most distinct silhouette in the set. */
    STAR;

    /**
     * Never splits into fragments.
     *
     * Splitting assumed convex blocks: a square or hexagon cut into four pieces
     * still looks like four plausible blocks. A star or a triangle cut the same
     * way produces shapes that read as glitches, so round shapes (circle, star,
     * triangle) keep the one-shot behaviour and the polygonal ones split.
     */
    val canSplit: Boolean
        get() = this == SQUARE || this == HEXAGON
}