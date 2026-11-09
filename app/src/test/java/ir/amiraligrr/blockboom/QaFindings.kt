package ir.amiraligrr.blockboom

/**
 * Where the audit's findings land.
 *
 * Kept as a plain object rather than a static field on the test classes so the
 * per-screen tests and the summariser share one list without either of them
 * having to know the other exists. JUnit gives each test class a fresh
 * instance, so a process-wide accumulator is the only way a `@AfterClass` in a
 * different class can see what the screens measured.
 *
 * `synchronized` because Robolectric runs a class's tests on one thread but
 * two classes can overlap in a parallel suite.
 */
object QaFindings {

    private val lock = Any()

    private val perScreen = LinkedHashMap<String, Pair<Int, Int>>()
    private val contrast = mutableListOf<String>()
    private val warnings = mutableListOf<String>()
    private val clicks = mutableListOf<String>()
    private val disabled = mutableListOf<String>()
    private val shots = mutableListOf<String>()
    private val diagnostics = mutableListOf<String>()

    fun screen(name: String, textCount: Int, clickCount: Int) = synchronized(lock) {
        perScreen[name] = Pair(textCount, clickCount)
    }

    fun contrast(msg: String) = synchronized(lock) { contrast.add(msg) }

    /**
     * Between 3:1 and 4.5:1 — passes the large-text floor, fails the
     * normal-text bar. Printed for human judgement, never build-breaking: the
     * harness cannot see font size, and a big bold headline at 3.6:1 reads
     * perfectly well.
     */
    fun warning(msg: String) = synchronized(lock) { warnings.add(msg) }

    fun click(msg: String) = synchronized(lock) { clicks.add(msg) }
    fun disabled(msg: String) = synchronized(lock) { disabled.add(msg) }
    fun shot(msg: String) = synchronized(lock) { shots.add(msg) }

    /** Harness self-diagnostics: where a screenshot went, and if it produced pixels. */
    fun diagnostic(msg: String) = synchronized(lock) { diagnostics.add(msg) }

    class Collected(
        val perScreen: LinkedHashMap<String, Pair<Int, Int>>,
        val contrast: List<String>,
        val warnings: List<String> = emptyList(),
        val clicks: List<String>,
        val disabled: List<String>,
        val diagnostics: List<String> = emptyList(),
        val shots: Int
    )

    /**
     * Reads the accumulated findings without clearing them.
     *
     * Separate from [drain] because the confirming test needs to look at what
     * is there *before* the @AfterClass empties it, and the audit's own
     * screenshots are reported from the drain.
     */
    fun snapshot(): Collected = synchronized(lock) {
        Collected(
            perScreen = LinkedHashMap(perScreen),
            contrast = contrast.toList(),
            warnings = warnings.toList(),
            clicks = clicks.toList(),
            disabled = disabled.toList(),
            diagnostics = diagnostics.toList(),
            shots = shots.size
        )
    }

    /** Takes the accumulated findings and clears them in one step. */
    fun drain(): Collected = synchronized(lock) {
        val c = Collected(
            perScreen = LinkedHashMap(perScreen),
            contrast = contrast.toList(),
            warnings = warnings.toList(),
            clicks = clicks.toList(),
            disabled = disabled.toList(),
            diagnostics = diagnostics.toList(),
            shots = shots.size
        )
        perScreen.clear(); contrast.clear(); warnings.clear(); clicks.clear()
        disabled.clear(); diagnostics.clear(); shots.clear()
        c
    }
}
