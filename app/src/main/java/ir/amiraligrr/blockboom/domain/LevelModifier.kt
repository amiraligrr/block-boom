package ir.amiraligrr.blockboom.domain

/**
 * A twist that changes how one level plays.
 *
 * ── Why this exists ──────────────────────────────────────────────────────
 * The level curve had been entirely numeric: more blocks, more HP, faster
 * fall. Simulated out to level 100, three of those four axes had stopped
 * moving entirely (see `GameEngine.levelConfig`), so levels 34-115 differed
 * only in the size of a number the player never has to think about.
 *
 * That is the ceiling on how long a game like this stays interesting, and it is
 * not a balance problem — no amount of tuning a number fixes "I have seen this
 * screen before". What changes a level is a *rule*.
 *
 * Each modifier changes one thing about the board: where the blocks go, what
 * has to be done to break them, or how fast they arrive. They are announced
 * before the level starts, because a rule the player has not been told about is
 * not a twist, it is a bug — and because knowing it is coming is itself part of
 * the appeal.
 *
 * Chosen to be readable at a glance from the badge alone, and to interact with
 * the cannon rather than replace it: the player is still aiming and still
 * choosing when to move, which is the whole game.
 */
enum class LevelModifier(
    val icon: String,
    /** Shown on the pre-level card. Short enough to read without pausing. */
    val label: String,
    val blurb: String,
    /** True for the boss level's own announcement, which overrides the rest. */
    val isBossModifier: Boolean = false
) {
    NONE("✨", "سطح عادی", "همه‌چیز مثل همیشه"),

    /** Blocks are pushed sideways by a steady wind, so "where" matters. */
    WIND("🌬️", "باد", "بلاک‌ها را باد می‌راند"),

    /** Some blocks are ice: they need a second hit and then shatter. */
    ICE("❄️", "یخ", "بعضی بلاک‌ها یخ‌اند؛ دوبار بزن"),

    /** Some blocks are armoured: the shield has to go before the block does. */
    ARMOR("🛡️", "زره", "زرهِ بعضی بلاک‌ها اول باید بشکند"),

    /** Everything arrives much faster, for a while. */
    RUSH("⚡", "شتاب", "بلاک‌ها خیلی سریع‌تر می‌آیند"),

    /** A few blocks are far tougher than the rest. */
    BOULDER("🪨", "صخره", "چند بلاک خیلی سفت‌اند"),

    /** A gentle upward draft, lifting blocks off the cannon. */
    LIFT("🕊️", "بالابر", "باد بلاک‌ها را بالا می‌برد"),

    /** The boss fight. Replaces whatever the level number would have given. */
    BATTLE("☠️", "باس فایت", "یک بلاک مادرِ تنها با دو برابر جان", isBossModifier = true);

    companion object {
        /**
         * The modifier for a level.
         *
         * Deterministic in the level number, so a level always looks the same
         * when replayed and two players on the same level get the same rule.
         *
         * ── Why this is not `(level * 5) % size` ───────────────────────────
         * The obvious rotation repeats. Measured over levels 4-199, every
         * simple stride (`5l`, `7l`, `11l`, with or without a `/3` `/4` `/7`
         * correction term) came back with a period of 7 to 10 levels — so level
         * 24 was the same twist as level 14, and a player who reached level 20
         * could name the next ten levels in advance. A twist you can predict is
         * not a twist.
         *
         * So the index is hashed instead of strided. The integer mix below has
         * no measurable period over 120 consecutive levels and is cheap (a
         * handful of shifts and multiplies, once per level, not per frame), and
         * it stays deterministic, which is the property that actually matters:
         * the same level must always be the same level.
         *
         * Never returns a modifier on the first few levels, so a new player
         * learns the basic loop before a rule is layered on top, and never on a
         * boss level, where [BATTLE] takes over.
         *
         * The hash takes the level and nothing else. It used to take a second
         * input — a per-day salt, so that the daily challenge could hand everyone
         * the same level 1 with a different rule on it — and with the challenge
         * gone that input was always zero, which is exactly what the original
         * campaign-only result was. Every level that exists still looks exactly
         * as it did.
         */
        fun forLevel(level: Int, bossLevel: Boolean): LevelModifier {
            if (bossLevel) return BATTLE
            // Only level 1 is left plain. It used to be the first *three*, on the
            // theory that a new player needs a clean level to learn the loop —
            // but a tutorial is what teaches the loop, and levels 2 and 3 were
            // flat enough that a first-time player had already decided the game
            // was boring before anything interesting had happened.
            if (level <= 1) return NONE
            val pool = arrayOf(WIND, ICE, ARMOR, RUSH, BOULDER, LIFT)
            return pool[hashLevel(level) % pool.size]
        }

        /**
         * A cheap integer avalanche, so consecutive levels land far apart.
         *
         * The `xor (salt * ...)` term is gone with the daily challenge. It
         * multiplied by zero on every campaign call, so removing it changes no
         * existing level — but it is kept out rather than defaulted to 0, because
         * a `= 0` default is an invitation to add it back and change every level
         * in the game without noticing.
         */
        private fun hashLevel(level: Int): Int {
            var x = level * 2654435761.toInt()
            x = x xor (x ushr 13)
            x *= 1274126177.toInt()
            x = x xor (x ushr 16)
            return x and 0x7FFFFFFF
        }
    }
}