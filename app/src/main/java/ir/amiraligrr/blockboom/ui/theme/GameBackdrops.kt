package ir.amiraligrr.blockboom.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * How a [GameBackdrop] builds its horizon.
 *
 * The previous background had no concept of this at all: one function drew
 * space stars, a neon grid *and* teal mountains with trees and clouds, all at
 * once, over a dark green gradient. Nothing in the image agreed with anything
 * else, which is most of why it read as "a dry blue screen with some shapes on
 * it" rather than as a place. Each style below is a coherent scene, drawn from
 * the same set of parts.
 */
enum class BackdropStyle {
    /** Rolling hills, clouds and a sun. Daylight, cheerful. */
    DAYLIGHT,

    /** A city skyline at night, lit windows, a moon. */
    SKYLINE,

    /** Open space: a nebula, a ringed planet, a dense starfield. */
    NEBULA,

    /** Arctic night: aurora ribbons over snow. */
    AURORA,

    /** Open water: light shafts from the surface, bubbles, swaying kelp. */
    OCEAN,

    /** A night storm: driving rain, drifting cloud, lightning. */
    STORM,

    /** Low fog over dark pines. */
    FOG
}

/**
 * What stands on the ground.
 *
 * [BackdropStyle] decides the *sky*; this decides the landscape, and it is what
 * makes two daylight backgrounds different places rather than the same hill in
 * two colours. Nine DAYLIGHT scenes — meadow, sunrise, sunset, forest, beach,
 * desert, sakura, alps, volcano — were all drawing the same mountains, the same
 * row of conifers and the same pebbles, so buying a new one changed the gradient
 * and nothing else. A desert with trees in it is not a desert.
 */
enum class TerrainMotif {
    /** Rolling hills with conifers. The default landscape. */
    TREES,

    /** Bare dune ridges and scrub. Nothing vertical, nothing green. */
    DUNES,

    /** Broad blossom canopies, with petals coming down through the frame. */
    BLOSSOM,

    /** Snow drifts and hard rock peaks. */
    SNOW,

    /** Black lava ridges with ember cracks glowing along them. */
    ROCK,

    /** A sea line, then sand. Palms and nothing else vertical. */
    COAST
}

/**
 * One complete background: everything the play field needs to paint a scene,
 * and nothing else.
 *
 * A backdrop is a *palette*, not an image — the shapes are drawn by
 * `drawBackdrop` in GameCanvas so they scale to any screen and animate for
 * free. Every colour here is chosen to sit *behind* the obstacles rather than
 * compete with them: skies are mid-to-deep and desaturated, terrain is darker
 * still, and the brightest thing on screen is always a block.
 */
data class GameBackdrop(
    val id: String,
    val name: String,
    val style: BackdropStyle,
    /** Sky gradient, top to horizon. */
    val sky: List<Color>,
    /** The sun / moon / planet at the top of the scene. */
    val orb: Color,
    val orbGlow: Color,
    /** Terrain, far to near. */
    val farTerrain: Color,
    val midTerrain: Color,
    val nearTerrain: Color,
    /** Cloud puffs, and how strongly they show. */
    val cloud: Color,
    val cloudAlpha: Float,
    /** Star density, 0 for a daylight sky. */
    val starDensity: Float,
    /** Edge falloff, matched to the sky so it never greys out the corners. */
    val vignette: Color,
    /** Accent used by the style-specific layer: window glow, aurora, nebula. */
    val accent: Color,
    /** What stands on the ground. Defaults to the landscape the first scenes used. */
    val motif: TerrainMotif = TerrainMotif.TREES
)

/**
 * The purchasable backgrounds, in shop order.
 *
 * The default is deliberately a bright daylight scene with green hills and a
 * warm sun. It is the one most players will see, and it is the one that reads
 * as "game" rather than as "dark screen" the instant the app opens — a dark
 * space field behind bright candy blocks made every level look like the same
 * dim screen.
 */
object GameBackdrops {

    val all: List<GameBackdrop> = listOf(
        GameBackdrop(
            id = "bg_default",
            name = "دشت سبز",
            style = BackdropStyle.DAYLIGHT,
            sky = listOf(
                Color(0xFF5CBAFF), // high sky
                Color(0xFF9ADFFF), // mid
                Color(0xFFDFF4FF)  // pale haze at the horizon
            ),
            orb = Color(0xFFFFF3C4),       // warm sun
            orbGlow = Color(0xFFFFD166),
            // Terrain lifted well above the old values. Blocks are saturated
            // mid-tones, and against the previous #2C8B60 near-ground the weakest
            // fill in the palette measured 1.00:1 contrast — literally the same
            // luminance — so reds, greens and mid blues disappeared into the hills.
            // These are light enough that every block clears them, and the
            // per-block dark rim (blockOutlineColor) supplies the silhouette.
            farTerrain = Color(0xFFA8E6C0),
            midTerrain = Color(0xFF7FD4A6),
            nearTerrain = Color(0xFF5CBE8C),
            cloud = Color.White,
            cloudAlpha = 0.9f,
            starDensity = 0f,
            vignette = Color(0xFF4FA37A),
            accent = Color(0xFFFFF3C4),
            motif = TerrainMotif.TREES
        ),
        GameBackdrop(
            id = "sunrise",
            name = "طلوع",
            style = BackdropStyle.DAYLIGHT,
            sky = listOf(
                Color(0xFFBFE3FF), // pale blue high sky
                Color(0xFFFFD9C2), // warm haze
                Color(0xFFFFE9D6)  // glowing horizon
            ),
            orb = Color(0xFFFFF6D8),       // low, warm sun
            orbGlow = Color(0xFFFFB27A),
            farTerrain = Color(0xFFFFCBA8),
            midTerrain = Color(0xFFFFAE86),
            nearTerrain = Color(0xF0906E),
            cloud = Color.White,
            cloudAlpha = 0.8f,
            starDensity = 0f,
            vignette = Color(0xFFD98F6A),
            accent = Color(0xFFFFF0D0),
            motif = TerrainMotif.DUNES
        ),
        GameBackdrop(
            id = "sunset",
            name = "غروب",
            style = BackdropStyle.DAYLIGHT,
            // Kept as DAYLIGHT so it still gets the daylight clouds, but the sky
            // is lifted well off the old near-black violet: it was the second
            // scene a player saw and it was dark enough to read as "night mode".
            sky = listOf(
                Color(0xFF7B5BB5), // violet zenith, lifted
                Color(0xFFD97BA8), // pink band
                Color(0xFFFFC48A)  // warm at the horizon
            ),
            orb = Color(0xFFFFF7D6),       // the sun, sitting on the horizon
            orbGlow = Color(0xFFFFA96E),
            farTerrain = Color(0xFFC79AC4),
            midTerrain = Color(0xFFA97BA8),
            nearTerrain = Color(0xFF8E6490),
            cloud = Color(0xFFFFE0C4),
            cloudAlpha = 0.6f,
            starDensity = 0.08f,
            vignette = Color(0xFF9A6E96),
            accent = Color(0xFFFFE2B0),
            motif = TerrainMotif.DUNES
        ),
        GameBackdrop(
            id = "cosmic",
            name = "کهکشان",
            style = BackdropStyle.NEBULA,
            // Night, but no longer near-black. The whole point of buying a space
            // background is a different mood, not an unreadable screen; these are
            // the darkest values here by design, and they keep enough lightness
            // that the saturated blocks still separate from them.
            sky = listOf(
                Color(0xFF241A4D), // deep indigo, not black
                Color(0xFF3E2A73), // violet
                Color(0xFF6A46A8)  // nebula glow at the horizon
            ),
            orb = Color(0xFFE4D4FF),
            orbGlow = Color(0xFFA97BFF),
            farTerrain = Color(0xFF6B4BA8),
            midTerrain = Color(0xFF52378A),
            nearTerrain = Color(0xFF412C70),
            cloud = Color(0xFFD8C4FF),
            cloudAlpha = 0.28f,
            starDensity = 1f,
            vignette = Color(0xFF2E2159),
            accent = Color(0xFFC4A3FF),
            motif = TerrainMotif.ROCK
        ),
        GameBackdrop(
            id = "neon_city",
            name = "شهر نئونی",
            style = BackdropStyle.SKYLINE,
            // Dusk rather than deep night, so the skyline still reads as a
            // skyline. The old values (#0A0620 sky over #0E0620 ground) put the
            // buildings almost exactly the same darkness as the sky behind them.
            sky = listOf(
                Color(0xFF3B2A6B), // dusk violet
                Color(0xFF6B4A9E), // haze
                Color(0xFFA87FC4)  // city glow at the horizon
            ),
            orb = Color(0xFFFFF6E0),       // moon
            orbGlow = Color(0xFFC9B8FF),
            farTerrain = Color(0xFF8A6BB8),
            midTerrain = Color(0xFF6B5095),
            nearTerrain = Color(0xFF553E7A),
            cloud = Color(0xFFE0C8F0),
            cloudAlpha = 0.32f,
            starDensity = 0.35f,
            vignette = Color(0xFF5E4790),
            accent = Color(0xFFFF8FE8),
            motif = TerrainMotif.ROCK
        ),
        GameBackdrop(
            id = "aurora",
            name = "شفق قطبی",
            style = BackdropStyle.AURORA,
            // Twilight, not arctic midnight. Kept cool so the aurora still has
            // somewhere to glow against, but lifted enough that blocks read.
            sky = listOf(
                Color(0xFF16394F), // deep teal, not black
                Color(0xFF1F5566), // teal
                Color(0xFF2E7A80)  // faint dawn at the horizon
            ),
            orb = Color(0xFFF0FBFF),       // small cold moon
            orbGlow = Color(0xFFA8E8F0),
            farTerrain = Color(0xFF4E9AA6),
            midTerrain = Color(0xFF3A7E8C),
            nearTerrain = Color(0xFF2E6878),
            cloud = Color(0xFFDDF6FA),
            cloudAlpha = 0.24f,
            starDensity = 0.55f,
            vignette = Color(0xFF357F8C),
            accent = Color(0xFF7CFAD0),
            motif = TerrainMotif.SNOW
        ),
        GameBackdrop(
            id = "forest",
            name = "جنگل",
            style = BackdropStyle.DAYLIGHT,
            // The scene the landscape detail was built for: daylight, so it gets
            // the mountains and the trees. Cooler and denser than the default
            // meadow so it reads as a different place rather than a tint.
            sky = listOf(
                Color(0xFF3E8FD0), // sky through the canopy
                Color(0xFF8FCBE8),
                Color(0xFFD9F0DC)  // haze at the horizon
            ),
            orb = Color(0xFFFFF0C0),
            orbGlow = Color(0xFFFFD98A),
            farTerrain = Color(0xFF8FC4A0),
            midTerrain = Color(0xFF5FA87C),
            nearTerrain = Color(0xFF3F7D5C),
            cloud = Color.White,
            cloudAlpha = 0.55f,
            starDensity = 0f,
            vignette = Color(0xFF3A7057),
            accent = Color(0xFFE8FFD9),
            motif = TerrainMotif.TREES
        ),
        GameBackdrop(
            id = "alps",
            name = "کوهستان",
            style = BackdropStyle.DAYLIGHT,
            // Cold and high. The terrain colours are desaturated greys rather than
            // greens, which is what makes the snow-capped peaks read as snow
            // rather than as more of the same foliage.
            sky = listOf(
                Color(0xFF2E7BC4),
                Color(0xFF8FC4E8),
                Color(0xFFEAF6FF)
            ),
            orb = Color(0xFFFFFDF2),
            orbGlow = Color(0xFFDCEEFF),
            farTerrain = Color(0xFFB9CBD8),
            midTerrain = Color(0xFF8FA6B8),
            nearTerrain = Color(0xFF6D8598),
            cloud = Color.White,
            cloudAlpha = 0.75f,
            starDensity = 0f,
            vignette = Color(0xFF7590A5),
            accent = Color(0xFFF4FBFF),
            motif = TerrainMotif.SNOW
        ),
        GameBackdrop(
            id = "beach",
            name = "ساحل",
            style = BackdropStyle.DAYLIGHT,
            // Sand instead of grass. The warm terrain colours are the point: they
            // are the only backdrop where the landscape detail is *not* green,
            // so it reads as somewhere else at a glance.
            sky = listOf(
                Color(0xFF1E9BD8), // open sky
                Color(0xFF7FD0F0),
                Color(0xFFF5E9C8)  // haze where the sea meets the sky
            ),
            orb = Color(0xFFFFF6C4),
            orbGlow = Color(0xFFFFC978),
            farTerrain = Color(0xFFE8D7A8), // sand, far
            midTerrain = Color(0xFFD9C08A),
            nearTerrain = Color(0xFFC9A96E),
            cloud = Color.White,
            cloudAlpha = 0.7f,
            starDensity = 0f,
            vignette = Color(0xFFB99863),
            accent = Color(0xFFFFF8E2),
            motif = TerrainMotif.COAST
        ),
        GameBackdrop(
            id = "volcano",
            name = "آتشفشان",
            style = BackdropStyle.DAYLIGHT,
            // The most aggressive scene on offer, and the most expensive for a
            // reason: near-black ground against the brightest blocks, so it is
            // the hardest to read and therefore the one that looks best when it
            // is legible. Deliberately the hardest background to play on.
            sky = listOf(
                Color(0xFF7A1F2B), // ember-lit ash
                Color(0xFFC4502F),
                Color(0xFFFFA23C)  // glow at the horizon
            ),
            orb = Color(0xFFFFE08A),
            orbGlow = Color(0xFFFF6B35),
            farTerrain = Color(0xFF5E2020),
            midTerrain = Color(0xFF3E1414),
            nearTerrain = Color(0xFF2A0C0C),
            cloud = Color(0xFFFF9A6A),
            cloudAlpha = 0.42f,
            starDensity = 0.1f,
            vignette = Color(0xFF3A1212),
            accent = Color(0xFFFFB25E),
            motif = TerrainMotif.ROCK
        ),
        // ── Appended, never inserted ────────────────────────────────────────
        //
        // `SkinCatalog` addresses these by *position* (`all[n]`), because a
        // backdrop's preview colour is read off one of its own fields. Inserting
        // a scene anywhere but the end silently renames every skin after it —
        // the shop would show one scene's name and price over another scene's
        // colour, which is invisible to the compiler. New scenes go last and the
        // skin is added with the matching index.
        GameBackdrop(
            id = "desert",
            name = "کویر",
            style = BackdropStyle.DAYLIGHT,
            // Sand at every depth, under a hot sky. The one backdrop where the
            // hills are dunes: warm, low-contrast, and nothing green anywhere,
            // so it cannot be mistaken for the default meadow in a screenshot.
            sky = listOf(
                Color(0xFF2E9BD6), // high sky
                Color(0xFF8FD3EE), // haze
                Color(0xFFF6E3C0)  // heat at the horizon
            ),
            orb = Color(0xFFFFF3D0),
            orbGlow = Color(0xFFFFB865),
            farTerrain = Color(0xFFE7C79B),
            midTerrain = Color(0xFFD9AE79),
            nearTerrain = Color(0xFFC08F5D),
            cloud = Color.White,
            cloudAlpha = 0.45f,
            starDensity = 0f,
            vignette = Color(0xFFA9764A),
            accent = Color(0xFFFFE9C4),
            motif = TerrainMotif.DUNES
        ),
        GameBackdrop(
            id = "sakura",
            name = "بهار",
            style = BackdropStyle.DAYLIGHT,
            // The pastel one. Pale pink terrain and blossom-coloured clouds, and
            // deliberately the brightest backdrop here — the blocks have to clear
            // it, so the terrain is desaturated enough that every saturated block
            // still separates from the hills behind them.
            sky = listOf(
                Color(0xFF7FB6E8), // spring sky
                Color(0xFFBFD9F2),
                Color(0xFFFBE3EC)  // blossom haze at the horizon
            ),
            orb = Color(0xFFFFF7E8),
            orbGlow = Color(0xFFFFC9D8),
            farTerrain = Color(0xFFF2C6D4),
            midTerrain = Color(0xFFE3A6BC),
            nearTerrain = Color(0xFFC98AA3),
            cloud = Color(0xFFFFF0F5),
            cloudAlpha = 0.85f,
            starDensity = 0f,
            vignette = Color(0xFFB07E96),
            accent = Color(0xFFFFEAF2),
            motif = TerrainMotif.BLOSSOM
        ),
        GameBackdrop(
            id = "lagoon",
            name = "خلیج",
            style = BackdropStyle.DAYLIGHT,
            // The coast scene. The only backdrop whose middle band is water
            // rather than land, so it is the one place the play field reads as
            // being *next to* something instead of sitting on top of it.
            sky = listOf(
                Color(0xFF1B9DD1), // open sky
                Color(0xFF63CFE8), // haze
                Color(0xFFD8F4F2)  // sea meeting the sky
            ),
            orb = Color(0xFFFFF8DC),
            orbGlow = Color(0xFFFFC46B),
            farTerrain = Color(0xFF59C6D8), // the sea
            midTerrain = Color(0xFF3FA8BE), // shallows
            nearTerrain = Color(0xFFEBD9A8), // sand
            cloud = Color.White,
            cloudAlpha = 0.8f,
            starDensity = 0f,
            vignette = Color(0xFF2E8C9E),
            accent = Color(0xFFFFF6DE),
            motif = TerrainMotif.COAST
        ),
        GameBackdrop(
            id = "canyon",
            name = "دره",
            style = BackdropStyle.DAYLIGHT,
            // Red rock and hard shadow. The other ROCK scene, and unlike the
            // volcano it is lit rather than glowing — the same silhouette in a
            // completely different light, which is the point of it being a
            // separate purchase rather than a recolour of the lava field.
            sky = listOf(
                Color(0xFF3C86C4),
                Color(0xFF9EC9E8),
                Color(0xFFF0D3AE)
            ),
            orb = Color(0xFFFFF2C8),
            orbGlow = Color(0xFFFFB067),
            farTerrain = Color(0xFFCE7B4E),
            midTerrain = Color(0xFFB05F39),
            nearTerrain = Color(0xFF8A4526),
            cloud = Color.White,
            cloudAlpha = 0.5f,
            starDensity = 0f,
            vignette = Color(0xFF7A3D22),
            accent = Color(0xFFFFD9A8),
            motif = TerrainMotif.DUNES
        ),
        // ── Appended, never inserted ────────────────────────────────────────
        //
        // Same rule as above: SkinCatalog addresses these by position, so a
        // new scene goes last and its skin is added with the matching index.
        GameBackdrop(
            id = "ocean",
            name = "اقیانوس",
            style = BackdropStyle.OCEAN,
            // Light from the surface, so the gradient runs the other way round
            // to every other scene: the top of the water is the bright end.
            sky = listOf(
                Color(0xFF0E5E86), // sunlit surface water
                Color(0xFF073A5C), // mid water
                Color(0xFF041E33)  // the deep, at the horizon line
            ),
            orb = Color(0xFFD9F4FF),       // the sun, seen through water
            orbGlow = Color(0xFF7DD3FC),
            farTerrain = Color(0xFF0D4A66),
            midTerrain = Color(0xFF093350),
            nearTerrain = Color(0xFF052033),
            cloud = Color(0xFF9ADFFF),
            cloudAlpha = 0.15f,
            starDensity = 0f,
            vignette = Color(0xFF02121F),
            accent = Color(0xFF67E8F9),
            motif = TerrainMotif.ROCK
        ),
        GameBackdrop(
            id = "storm",
            name = "طوفان",
            style = BackdropStyle.STORM,
            // Slate, not black: a storm sky that is pure black hides the rain,
            // and the rain is the whole scene.
            sky = listOf(
                Color(0xFF1A2233), // high storm cloud
                Color(0xFF2A3448), // mid
                Color(0xFF3D4A63)  // lit from below by the city that is not there
            ),
            orb = Color(0xFFE8EEF7),       // a dim moon behind the cloud
            orbGlow = Color(0xFF8FA3C0),
            farTerrain = Color(0xFF3A4456),
            midTerrain = Color(0xFF2A3242),
            nearTerrain = Color(0xFF1C2230),
            cloud = Color(0xFF5A6A84),
            cloudAlpha = 0.5f,
            starDensity = 0f,
            vignette = Color(0xFF10141F),
            accent = Color(0xFFBFD0E8),
            motif = TerrainMotif.ROCK
        ),
        GameBackdrop(
            id = "fog",
            name = "مه",
            style = BackdropStyle.FOG,
            // Pale and desaturated. The fog bands are drawn over this, so the
            // base sky has to be the colour the fog is made of.
            sky = listOf(
                Color(0xFF5A6B7A), // high mist
                Color(0xFF7A8A96), // mid
                Color(0xFF9AA8B0)  // dense at the horizon
            ),
            orb = Color(0xFFF2F6F8),       // the sun, a pale disc in the murk
            orbGlow = Color(0xFFD5DEE4),
            farTerrain = Color(0xFF8B99A4),
            midTerrain = Color(0xFF6E7C8A),
            nearTerrain = Color(0xFF55606C),
            cloud = Color.White,
            cloudAlpha = 0.6f,
            starDensity = 0f,
            vignette = Color(0xFF4A5560),
            accent = Color(0xFFE8EEF2),
            motif = TerrainMotif.TREES
        ),
        GameBackdrop(
            id = "midnight",
            name = "نیمه‌شب",
            style = BackdropStyle.NEBULA,
            // The densest starfield in the game, and the one scene with a comet.
            // Darker than the cosmic backdrop so the stars have more contrast.
            sky = listOf(
                Color(0xFF0E0A24), // near-black indigo
                Color(0xFF1A1440), // violet
                Color(0xFF2E2159)  // faint glow at the horizon
            ),
            orb = Color(0xFFE4D4FF),
            orbGlow = Color(0xFFA97BFF),
            farTerrain = Color(0xFF4A3786),
            midTerrain = Color(0xFF3A2A6E),
            nearTerrain = Color(0xFF2A1E54),
            cloud = Color(0xFFD8C4FF),
            cloudAlpha = 0.22f,
            starDensity = 1f,
            vignette = Color(0xFF1A1440),
            accent = Color(0xFFC4A3FF),
            motif = TerrainMotif.ROCK
        )
    )

    fun byId(id: String?): GameBackdrop =
        all.firstOrNull { it.id == id } ?: all.first()
}
