package ir.amiraligrr.blockboom.data

/**
 * What a rewarded advert is worth.
 *
 * ── Why these are in one place ──────────────────────────────────────────
 * The game pays out a rewarded view in four places — hearts when the player is
 * out, a doubling on the daily gift, a doubling on the coins a run earned, and a
 * free upgrade level — and every one of them used to carry its own magic number.
 * Increasing "what an ad is worth" meant finding four numbers in four screens and
 * changing three of them, which is why it was never done even though it is the
 * single highest-RPM format in the game.
 *
 * ── Why the coin reward scales with the level ───────────────────────────
 * A flat figure is wrong at both ends. 500 coins is most of an early level's
 * income and nothing against a late one, so a flat reward is either a broken
 * economy at level 5 or an insult at level 400. Scaling keeps it a fixed share
 * of what a level is actually worth, which is the only definition of "worth
 * watching" that survives an endless game.
 *
 * ── Why there is a daily cap ────────────────────────────────────────────
 * An uncapped coin tap is a coin faucet. It does not make the game more
 * generous, it makes the shop pointless — and a shop nobody uses is how the
 * upgrade track stops being a track. The cap is high enough that a player who
 * wants to can genuinely earn their way through an upgrade tier in an evening,
 * and low enough that the packs still mean something.
 */
object AdRewards {

    /** Hearts handed over by the "watch for a heart" advert when the player is out. */
    const val HEARTS_PER_AD = 3

    /** Free upgrade levels a rewarded advert can buy in one day. */
    const val FREE_UPGRADES_PER_DAY = 2

    /**
     * Coins for one rewarded watch, at [level].
     *
     * Tuned against the income curve the engine runs on: roughly a third of a
     * level's coins early, and a flat 2,500 once a level pays several thousand.
     * Three or four of these buys a tier of any upgrade track.
     */
    fun coinsForLevel(level: Int): Long {
        val raw = 70L + level.coerceAtLeast(1) * 9L
        return raw.coerceAtMost(MAX_COINS_PER_AD)
    }

    /** Ceiling on the level-scaled figure, so a deep level cannot print coins. */
    const val MAX_COINS_PER_AD = 2_500L

    /**
     * How many rewarded coin watches a device may be paid for in one day.
     *
     * Five, not fifteen. This counter gates the free-coins card in the shop —
     * the one tap that prints coins for time — and fifteen of those a day was
     * a second income track that ran next to the packs without ever meeting
     * them. Five keeps the free path real (a tier of upgrades in an evening
     * is still reachable) while leaving the packs the faster way to a big
     * balance, which is the only arrangement in which both can coexist.
     *
     * Per device rather than per install, because coins are a client-local
     * balance and the only thing that can be checked without a server round
     * trip is the device. It is not a security boundary — it is a politeness
     * bound, and there is a real one on the ad network's side (frequency caps
     * and fill) that this is designed to stay well inside of.
     */
    const val MAX_ADS_PER_DAY = 5
}
