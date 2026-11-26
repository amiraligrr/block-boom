package ir.amiraligrr.blockboom.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import ir.amiraligrr.blockboom.ui.theme.*

/** Skin types for different game elements */
enum class SkinType {
    BALL, CANNON, BACKGROUND
}

/** Represents a purchasable skin */
data class Skin(
    val id: String,
    val name: String,
    val description: String,
    val price: Long,
    val type: SkinType,
    val previewColor: Color,
    val isPremium: Boolean = false
) {
    /**
     * What the player actually pays, after the store-wide price step.
     *
     * Kept as a derived value rather than rewriting forty catalogue rows: the
     * base number still documents the design's relative ladder (a 5,000-coin
     * skin is still the most expensive tier), and one constant changes every
     * price together. Twenty percent off is small enough not to erase the
     * progression and large enough to feel on a first purchase.
     */
    val finalPrice: Long
        get() = if (price <= 0L) 0L else (price * 0.8f).toLong()

    fun isOwned(saveManager: SaveManager): Boolean {
        val owned = when (type) {
            SkinType.BALL -> saveManager.ownedBallSkins
            SkinType.CANNON -> saveManager.ownedCannonSkins
            SkinType.BACKGROUND -> saveManager.ownedBackgroundSkins
        }
        return owned.split(",").contains(id)
    }

    fun select(saveManager: SaveManager) {
        when (type) {
            SkinType.BALL -> saveManager.selectedBallSkin = id
            SkinType.CANNON -> saveManager.selectedCannonSkin = id
            SkinType.BACKGROUND -> saveManager.selectedBackgroundSkin = id
        }
    }

    fun purchase(saveManager: SaveManager): Boolean {
        if (isPremium && !saveManager.isPremium) return false
        if (saveManager.coins < finalPrice) return false
        val owned = when (type) {
            SkinType.BALL -> saveManager.ownedBallSkins
            SkinType.CANNON -> saveManager.ownedCannonSkins
            SkinType.BACKGROUND -> saveManager.ownedBackgroundSkins
        }
        val ownedSet = owned.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
        if (ownedSet.contains(id)) return false
        saveManager.coins = saveManager.coins - finalPrice
        ownedSet.add(id)
        // Ensure type-specific default is always retained
        val defaultId = when (type) {
            SkinType.BALL -> "default"
            SkinType.CANNON -> "cannon_default"
            SkinType.BACKGROUND -> "bg_default"
        }
        if (!ownedSet.contains(defaultId) && !ownedSet.contains("default")) {
            ownedSet.add(defaultId)
        }
        val newOwned = ownedSet.joinToString(",")
        when (type) {
            SkinType.BALL -> saveManager.ownedBallSkins = newOwned
            SkinType.CANNON -> saveManager.ownedCannonSkins = newOwned
            SkinType.BACKGROUND -> saveManager.ownedBackgroundSkins = newOwned
        }
        select(saveManager)
        ir.amiraligrr.blockboom.data.ProgressState.sync(saveManager)
        return true
    }
}

/** All available skins in the game */
object SkinCatalog {
    val allSkins = listOf(
        // Ball skins. Each one is a different *shape*, not a different hue —
        // see BallVisuals, which is also what the game and the shop preview
        // both draw from, so a tile always shows the real thing.
        Skin("default", "کلاسیک", "گوی صاف و براق", 0, SkinType.BALL, BallVisuals.byId("default").primary),
        Skin("fire_ball", "بمب آتشین", "پوستهٔ تیره با هستهٔ داغ و دنبالهٔ اخگر", 500, SkinType.BALL, BallVisuals.byId("fire_ball").primary),
        Skin("ice_ball", "بلور یخ", "شش‌ضلعی شفاف با دنبالهٔ برق", 800, SkinType.BALL, BallVisuals.byId("ice_ball").primary),
        Skin("cannon_ball", "گلولهٔ آهنی", "گوی سنگین با نوار فلزی", 1000, SkinType.BALL, BallVisuals.byId("cannon_ball").primary),
        Skin("neon_ball", "نئونی", "دنبالهٔ نواری ممتد", 1200, SkinType.BALL, BallVisuals.byId("neon_ball").primary),
        Skin("bubble_ball", "حباب", "توپ صابونی با هالهٔ رنگین", 1400, SkinType.BALL, BallVisuals.byId("bubble_ball").primary),
        Skin("bolt_ball", "رعد", "صاعقهٔ زرد با هستهٔ سفید", 1600, SkinType.BALL, BallVisuals.byId("bolt_ball").primary),
        Skin("gem_ball", "نگین", " facet های برش‌خورده و درخشان", 1800, SkinType.BALL, BallVisuals.byId("gem_ball").primary),
        Skin("shield_ball", "سپر", "زرهٔ گلردیس با نوار نقره‌ای", 2000, SkinType.BALL, BallVisuals.byId("shield_ball").primary),
        Skin("plasma_ball", "پلاسما", "قرصی که با فاز می‌چرخد", 2200, SkinType.BALL, BallVisuals.byId("plasma_ball").primary),
        Skin("star_ball", "ستاره", "پنج‌پر و چرخان", 2400, SkinType.BALL, BallVisuals.byId("star_ball").primary),
        Skin("galaxy_ball", "کهکشانی", "خاردار با حلقهٔ مداری", 2600, SkinType.BALL, BallVisuals.byId("galaxy_ball").primary),
        Skin("planet_ball", "سیاره", "گوی نواری با حلقهٔ مورب", 2800, SkinType.BALL, BallVisuals.byId("planet_ball").primary),
        Skin("heart_ball", "قلبی", "قلب قرمز شعله‌ور", 3000, SkinType.BALL, BallVisuals.byId("heart_ball").primary),
        Skin("diamond_ball", "الماس", "تراش هشت‌پر با برق سرد", 3400, SkinType.BALL, BallVisuals.byId("diamond_ball").primary),
        Skin("coin_ball", "سکهٔ چرخان", "سکه‌ای که روی لبه می‌چرخد", 3800, SkinType.BALL, BallVisuals.byId("coin_ball").primary),
        Skin("gold_ball", "طلایی", "نگین طلای خالص", 5000, SkinType.BALL, BallVisuals.byId("gold_ball").primary, isPremium = true),
        Skin("shuriken_ball", "شوریکن", "ستارهٔ نینجا با تیغه‌های خمیده", 4200, SkinType.BALL, BallVisuals.byId("shuriken_ball").primary),
        Skin("rocket_ball", "راکت", "با شعلهٔ دنباله و دود آهسته", 4600, SkinType.BALL, BallVisuals.byId("rocket_ball").primary),
        Skin("skull_ball", "جمجمه", "جمجمهٔ استخوانی با چشمهای تیره", 5200, SkinType.BALL, BallVisuals.byId("skull_ball").primary, isPremium = true),
        Skin("ufo_ball", "یوفو", "بشقاب پرنده با چراغ‌های چرخان", 5800, SkinType.BALL, BallVisuals.byId("ufo_ball").primary, isPremium = true),

        // Cannon skins. The body, trim and glow come from [CannonVisuals], so
        // each one is a different cannon and not one cannon in another hue — the
        // trim is what the eye actually separates on a crowded board.
        Skin("cannon_default", "کلاسیک", "توپخانه پیش‌فرض", 0, SkinType.CANNON, CannonVisuals.byId("cannon_default").body),
        Skin("steel_cannon", "فولادی", "جسم فولادی با حاشیهٔ تیره", 600, SkinType.CANNON, CannonVisuals.byId("steel_cannon").body),
        Skin("chrome_cannon", "کروم", "بدنهٔ آینه‌ای سرد", 1000, SkinType.CANNON, CannonVisuals.byId("chrome_cannon").body),
        Skin("dragon_cannon", "اژدها", "نفس آتشین با حاشیهٔ اخگر", 1500, SkinType.CANNON, CannonVisuals.byId("dragon_cannon").body),
        Skin("laser_cannon", "لیزری", "شلیک لیزری، حاشیهٔ برق", 2000, SkinType.CANNON, CannonVisuals.byId("laser_cannon").body),
        Skin("emerald_cannon", "زمردی", "بدنهٔ سبز تیره و براق", 2400, SkinType.CANNON, CannonVisuals.byId("emerald_cannon").body),
        Skin("frost_cannon", "یخی", "بدنهٔ آبی با حاشیهٔ برفی", 2800, SkinType.CANNON, CannonVisuals.byId("frost_cannon").body),
        Skin("void_cannon", "حفره سیاه", "جذب نور", 3000, SkinType.CANNON, CannonVisuals.byId("void_cannon").body, isPremium = true),

        // Background skins. Names and prices come from [GameBackdrops] so the
        // shop and the play field can never disagree about what is on offer.
        // Background skins. Names and prices come from [GameBackdrops] so the
        // shop and the play field can never disagree about what is on offer.
        //
        // Each `all[n]` index is a position, not a lookup, so it breaks silently
        // if a scene is inserted into GameBackdrops.all — showing one scene's
        // name over another scene's colour. tools/ has no test for this, so the
        // rule is: the index must equal the scene's position in GameBackdrops.all.
        // The shop lists them in a deliberate order (cheap and bright first) which
        // is NOT the order of GameBackdrops.all, so read the indices, not the rows.
        Skin("bg_default", "دشت سبز", "آسمان روشن و تپه‌های سبز", 0, SkinType.BACKGROUND, GameBackdrops.all[0].midTerrain),
        Skin("sunrise", "طلوع", "آفتاب پایین روی افق گرم", 900, SkinType.BACKGROUND, GameBackdrops.all[1].nearTerrain),
        Skin("sunset", "غروب", "آسمان صورتی و بنفش", 1200, SkinType.BACKGROUND, GameBackdrops.all[2].sky[1]),
        Skin("forest", "جنگل", "درخت‌ها و تپه‌های سبز", 1600, SkinType.BACKGROUND, GameBackdrops.all[6].nearTerrain),
        Skin("beach", "ساحل", "شن‌های گرم و آسمان باز", 2000, SkinType.BACKGROUND, GameBackdrops.all[8].nearTerrain),
        Skin("alps", "کوهستان", "قله‌های برفی و هوای سرد", 2400, SkinType.BACKGROUND, GameBackdrops.all[7].midTerrain),
        Skin("desert", "کویر", "تپه‌های شنی و آسمان داغ", 2600, SkinType.BACKGROUND, GameBackdrops.all[10].midTerrain),
        Skin("cosmic", "کهکشان", "سیارهٔ حلقه‌دار و ستاره‌ها", 2800, SkinType.BACKGROUND, GameBackdrops.all[3].orb),
        Skin("neon_city", "شهر نئونی", "آسمان‌خراش‌های غروب", 3200, SkinType.BACKGROUND, GameBackdrops.all[4].accent),
        Skin("sakura", "بهار", "تپه‌های صورتی و غنچه‌های شکفته", 3400, SkinType.BACKGROUND, GameBackdrops.all[11].midTerrain),
        Skin("aurora", "شفق قطبی", "نورهای سبز قطبی", 3600, SkinType.BACKGROUND, GameBackdrops.all[5].accent, isPremium = true),
        Skin("volcano", "آتشفشان", "زمین سیاه و آسمان سرخ", 4200, SkinType.BACKGROUND, GameBackdrops.all[9].nearTerrain, isPremium = true),
        // all[12] and all[13] are the two scenes appended after the originals;
        // see the note on the list above about indices.
        Skin("lagoon", "خلیج", "دریای فیروزه‌ای و ساحل نخل‌دار", 4800, SkinType.BACKGROUND, GameBackdrops.all[12].nearTerrain, isPremium = true),
        Skin("canyon", "دره", "صخره‌های سرخ و سایه‌های سخت", 5200, SkinType.BACKGROUND, GameBackdrops.all[13].midTerrain, isPremium = true),
        // all[14]..all[17] are the scenes appended after the originals; see the
        // note on the list above about indices.
        Skin("ocean", "اقیانوس", "پرتوهای نور، حباب‌ها و جلبک‌های چرخان", 5600, SkinType.BACKGROUND, GameBackdrops.all[14].midTerrain, isPremium = true),
        Skin("storm", "طوفان", "باران و رعد و برق", 6000, SkinType.BACKGROUND, GameBackdrops.all[15].sky[1], isPremium = true),
        Skin("fog", "مه", "مه‌های چرخان میان کاج‌های تیره", 6400, SkinType.BACKGROUND, GameBackdrops.all[16].sky[1], isPremium = true),
        Skin("midnight", "نیمه‌شب", "آسمان پرستاره با شهاب‌سنگ", 6800, SkinType.BACKGROUND, GameBackdrops.all[17].orb, isPremium = true)
    )

    fun getSkinsByType(type: SkinType): List<Skin> = allSkins.filter { it.type == type }

    fun getSkinById(id: String): Skin? = allSkins.firstOrNull { it.id == id }

    fun getSkinByIdAndType(id: String, type: SkinType): Skin? = allSkins.firstOrNull { it.id == id && it.type == type }
}

/** Manages skin state reactively for Compose */
class SkinManager(private val saveManager: SaveManager) {
    var selectedBallSkin by mutableStateOf(saveManager.selectedBallSkin)
        private set

    var selectedCannonSkin by mutableStateOf(saveManager.selectedCannonSkin)
        private set

    var selectedBackgroundSkin by mutableStateOf(saveManager.selectedBackgroundSkin)
        private set

    var ownedBallSkins by mutableStateOf(saveManager.ownedBallSkins)
        private set

    var ownedCannonSkins by mutableStateOf(saveManager.ownedCannonSkins)
        private set

    var ownedBackgroundSkins by mutableStateOf(saveManager.ownedBackgroundSkins)
        private set

    fun refresh() {
        selectedBallSkin = saveManager.selectedBallSkin
        selectedCannonSkin = saveManager.selectedCannonSkin
        selectedBackgroundSkin = saveManager.selectedBackgroundSkin
        ownedBallSkins = saveManager.ownedBallSkins
        ownedCannonSkins = saveManager.ownedCannonSkins
        ownedBackgroundSkins = saveManager.ownedBackgroundSkins
    }

    fun isBallOwned(id: String): Boolean = ownedBallSkins.split(",").map { it.trim() }.contains(id)
    fun isCannonOwned(id: String): Boolean {
        val set = ownedCannonSkins.split(",").map { it.trim() }.toSet()
        return set.contains(id) || (id == "cannon_default" && set.contains("default"))
    }
    fun isBackgroundOwned(id: String): Boolean {
        val set = ownedBackgroundSkins.split(",").map { it.trim() }.toSet()
        return set.contains(id) || (id == "bg_default" && set.contains("default"))
    }

    fun selectBall(id: String): Boolean {
        if (!isBallOwned(id)) return false
        saveManager.selectedBallSkin = id
        selectedBallSkin = id
        return true
    }

    fun selectCannon(id: String): Boolean {
        if (!isCannonOwned(id)) return false
        val norm = if (id == "default") "cannon_default" else id
        // re-check with normalized
        if (!isCannonOwned(norm) && norm != id) return false
        saveManager.selectedCannonSkin = norm
        selectedCannonSkin = norm
        return true
    }

    fun selectBackground(id: String): Boolean {
        if (!isBackgroundOwned(id)) return false
        val norm = if (id == "default") "bg_default" else id
        if (!isBackgroundOwned(norm) && norm != id) return false
        saveManager.selectedBackgroundSkin = norm
        selectedBackgroundSkin = norm
        return true
    }

    sealed class PurchaseResult {
        object Success : PurchaseResult()
        object AlreadyOwned : PurchaseResult()
        object NotEnoughCoins : PurchaseResult()
        object PremiumRequired : PurchaseResult()
        object NotFound : PurchaseResult()
    }

    fun purchaseBall(id: String): PurchaseResult {
        val skin = SkinCatalog.getSkinByIdAndType(id, SkinType.BALL)
            ?: SkinCatalog.getSkinById(id)?.takeIf { it.type == SkinType.BALL }
            ?: return PurchaseResult.NotFound
        if (skin.isPremium && !saveManager.isPremium) return PurchaseResult.PremiumRequired
        if (isBallOwned(id)) return PurchaseResult.AlreadyOwned
        if (saveManager.coins < skin.finalPrice) return PurchaseResult.NotEnoughCoins
        return if (skin.purchase(saveManager)) { refresh(); PurchaseResult.Success } else PurchaseResult.NotFound
    }

    fun purchaseCannon(id: String): PurchaseResult {
        val skin = SkinCatalog.getSkinByIdAndType(id, SkinType.CANNON)
            ?: SkinCatalog.getSkinById(id)?.takeIf { it.type == SkinType.CANNON }
            ?: return PurchaseResult.NotFound
        if (skin.isPremium && !saveManager.isPremium) return PurchaseResult.PremiumRequired
        if (isCannonOwned(id)) return PurchaseResult.AlreadyOwned
        if (saveManager.coins < skin.finalPrice) return PurchaseResult.NotEnoughCoins
        return if (skin.purchase(saveManager)) { refresh(); PurchaseResult.Success } else PurchaseResult.NotFound
    }

    fun purchaseBackground(id: String): PurchaseResult {
        val skin = SkinCatalog.getSkinByIdAndType(id, SkinType.BACKGROUND)
            ?: SkinCatalog.getSkinById(id)?.takeIf { it.type == SkinType.BACKGROUND }
            ?: return PurchaseResult.NotFound
        if (skin.isPremium && !saveManager.isPremium) return PurchaseResult.PremiumRequired
        if (isBackgroundOwned(id)) return PurchaseResult.AlreadyOwned
        if (saveManager.coins < skin.finalPrice) return PurchaseResult.NotEnoughCoins
        return if (skin.purchase(saveManager)) { refresh(); PurchaseResult.Success } else PurchaseResult.NotFound
    }

    // Legacy boolean wrappers for compatibility
    fun purchaseBallBool(id: String): Boolean = purchaseBall(id) is PurchaseResult.Success
    fun purchaseCannonBool(id: String): Boolean = purchaseCannon(id) is PurchaseResult.Success
    fun purchaseBackgroundBool(id: String): Boolean = purchaseBackground(id) is PurchaseResult.Success

    /**
     * The equipped ball's full appearance.
     *
     * Replaces [getBallColor], which handed the renderer a single `Color` and
     * therefore could only ever repaint the same circle. Both the projectile
     * and the shop tile draw from this one value, so what a player previews is
     * exactly what they get.
     */
    fun getBallVisual(): BallVisual = BallVisuals.byId(selectedBallSkin)

    /**
     * The equipped cannon's full appearance.
     *
     * Replaces [getCannonColor], which handed the renderer a single `Color` and
     * so could only ever repaint the same cannon. Body, trim and glow are three
     * different parts of the object, and the trim — the ring, the base and the
     * band — is what actually distinguishes one cannon from another once the
     * board is full of blocks.
     */
    fun getCannonVisual(): CannonVisual = CannonVisuals.byId(selectedCannonSkin)

    /**
     * The live background, resolved from the selected skin.
     *
     * This is what the canvas actually draws. It replaced a `when(id)` that
     * returned a hand-written three-colour list per background and let the
     * canvas paint the *same* mountains, trees, clouds and stars for all of
     * them — so buying a different background changed only the gradient behind
     * an identical scene, which is why they all looked like variations of one
     * dry screen. The scene now comes from the backdrop too, so each purchase
     * changes the whole picture.
     */
    fun getBackdrop(): GameBackdrop = GameBackdrops.byId(
        when (selectedBackgroundSkin) {
            "default" -> "bg_default"
            else -> selectedBackgroundSkin
        }
    )

    fun getSkinsByType(type: SkinType): List<Skin> {
        return SkinCatalog.getSkinsByType(type)
    }
}