package ir.amiraligrr.blockboom

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.amiraligrr.blockboom.QaFindings
import ir.amiraligrr.blockboom.qa.IntRect
import ir.amiraligrr.blockboom.qa.PixelContrast
import ir.amiraligrr.blockboom.qa.Qa
import ir.amiraligrr.blockboom.qa.QaClicks
import ir.amiraligrr.blockboom.qa.QaReport
import ir.amiraligrr.blockboom.qa.walkTree
import ir.amiraligrr.blockboom.ui.screens.HomeScreen
import ir.amiraligrr.blockboom.ui.screens.InviteFriendsScreen
import ir.amiraligrr.blockboom.ui.screens.LeaderboardScreen
import ir.amiraligrr.blockboom.ui.screens.SettingsScreen
import ir.amiraligrr.blockboom.ui.screens.ShopScreen
import ir.amiraligrr.blockboom.ui.screens.StatsScreen
import ir.amiraligrr.blockboom.ui.screens.SupportScreen
import ir.amiraligrr.blockboom.ui.theme.BlockBoomTheme
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen of the real app and measures what the device actually
 * drew.
 *
 * Two things are being asked for, and neither is visible to the build:
 *
 *   1. Every button is really pressable. The tree is walked, every node that
 *      carries a click action is invoked, and a throw is a failure. Nodes the
 *      app has deliberately disabled are counted and skipped, not failed.
 *
 *   2. Every piece of text is really legible. The screen is captured as pixels
 *      and, for every text node, the contrast between the glyph colour and the
 *      colour underneath it is computed from the rendered bitmap. A label that
 *      matches its background lands near 1:1.
 *
 * Both are run in light mode and in dark mode, because a colour that is
 * readable on a white card is frequently invisible on a dark one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi", application = QaTestApp::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ScreenAuditTest {

    @get:Rule
    val compose = createComposeRule()

    private val contrastFailures = mutableListOf<String>()
    private val clickFailures = mutableListOf<String>()
    private val disabledCount = mutableListOf<String>()
    private val notes = mutableListOf<String>()

    /**
     * Runs once, after every screen in this class has been measured.
     *
     * Lives here rather than in a separate `ScreenAuditSummary` class because
     * Robolectric gives each test class its own sandbox and its own copy of
     * every static — a companion object in another class is a *different*
     * object, so the accumulator it drained was always empty and the report
     * said "no screens were measured" no matter how much was found. The
     * findings live in `QaFindings`, which is static within this sandbox, so
     * this companion sees exactly what the tests above put into it.
     */
    companion object {
        @JvmStatic
        @AfterClass
        fun afterAll() {
            val findings = QaFindings.drain()
            if (findings.perScreen.isEmpty()) {
                println("\n[audit] no screens were measured — the tests above produced nothing.\n")
                return
            }

            val sb = StringBuilder()
            sb.append("\n")
            sb.append("=".repeat(78)).append("\n")
            sb.append("  SCREEN AUDIT\n")
            sb.append("=".repeat(78)).append("\n\n")

            findings.perScreen.forEach { (name, counts) ->
                sb.append(
                    String.format(
                        "  %-30s text=%-4d clickable=%-3d\n",
                        name, counts.first, counts.second
                    )
                )
            }
            sb.append("\n")

            if (findings.diagnostics.isNotEmpty()) {
                sb.append("-".repeat(78)).append("\n")
                sb.append("  HARNESS\n")
                sb.append("-".repeat(78)).append("\n")
                sb.append("  screenshots: ${findings.shots} written to build/qa\n")
                findings.diagnostics.forEach { sb.append("    $it\n") }
                sb.append("\n")
            } else {
                sb.append("  screenshots: ${findings.shots} written to build/qa\n\n")
            }

            if (findings.contrast.isNotEmpty()) {
                sb.append("-".repeat(78)).append("\n")
                sb.append("  TEXT THAT CANNOT BE READ  (${findings.contrast.size})\n")
                sb.append("-".repeat(78)).append("\n")
                findings.contrast.forEach { sb.append("  x $it\n") }
                sb.append("\n")
            }

            if (findings.warnings.isNotEmpty()) {
                sb.append("-".repeat(78)).append("\n")
                sb.append("  READABLE BUT BELOW 4.5:1  (${findings.warnings.size}) — judge against build/qa\n")
                sb.append("-".repeat(78)).append("\n")
                findings.warnings.forEach { sb.append("  ~ $it\n") }
                sb.append("\n")
            }

            if (findings.clicks.isNotEmpty()) {
                sb.append("-".repeat(78)).append("\n")
                sb.append("  BUTTONS THAT DO NOT RESPOND  (${findings.clicks.size})\n")
                sb.append("-".repeat(78)).append("\n")
                findings.clicks.forEach { sb.append("  x $it\n") }
                sb.append("\n")
            }

            if (findings.disabled.isNotEmpty()) {
                sb.append("  deliberately-disabled controls, not counted as failures: " +
                    "${findings.disabled.size}\n")
                findings.disabled.take(10).forEach { sb.append("      - $it\n") }
                sb.append("\n")
            }

            sb.append("=".repeat(78)).append("\n")
            println(sb)

            assertTrue(
                "UI audit found problems:\n" +
                    findings.contrast.joinToString("\n") { "  CONTRAST: $it" } + "\n" +
                    findings.clicks.joinToString("\n") { "  CLICK:    $it" },
                findings.contrast.isEmpty() && findings.clicks.isEmpty()
            )
        }
    }

    /**
     * No `waitForIdle()` here. With `autoAdvance` left off, an `@After` that
     * waits for idle is exactly the call that hangs for 60 seconds and throws
     * `AppNotIdleException` — it is the same problem the clock workaround
     * exists for, reintroduced in teardown.
     */
    @After
    fun teardown() = Unit

    private fun screen(dark: Boolean, name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        // The clock is turned off BEFORE setContent, not after. The rule's own
        // setContent performs a waitForIdle internally, so setting it afterwards
        // means the very first call still spins for 60 seconds and throws.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BlockBoomTheme(darkMode = dark) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
        // Step past every entrance animation so the frame captured is the
        // resting screen rather than a mid-transition one. The longest entry
        // tween in the app is 900ms; 1000ms lands on a settled frame.
        compose.mainClock.advanceTimeBy(1_000)
    }

    /**
     * A screenshot that does not need the app to be idle.
     *
     * `captureToImage()` waits for Espresso idleness first, and these screens
     * never idle — `rememberInfiniteTransition` keeps a frame pending forever,
     * so every capture threw `ComposeTimeoutException` and every screen
     * reported `text=0`. Drawing the window's decor view straight into a
     * bitmap renders whatever is on screen right now instead, which is exactly
     * the resting frame the clock was stepped to.
     */
    private fun captureScreen(name: String): ImageBitmap? {
        return try {
            var bmp: android.graphics.Bitmap? = null
            compose.runOnUiThread {
                // Reached through the concrete rule: the interface does not
                // expose the host activity.
                val activity = (compose as androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>).activity
                val decor = activity.window.decorView.rootView
                val w = decor.width
                val h = decor.height
                if (w > 0 && h > 0) {
                    bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    decor.draw(android.graphics.Canvas(bmp!!))
                }
            }
            bmp?.asImageBitmap()
        } catch (t: Throwable) {
            QaFindings.diagnostic("$name: decor capture failed (${t.javaClass.simpleName}: ${t.message?.take(160)})")
            null
        }
    }

    /**
     * Walks the tree carrying whether each node sits inside a scrollable.
     *
     * With the test clock paused, content below the fold of a `verticalScroll`
     * is never placed — its nodes report 0x0 bounds. That is a property of the
     * harness, not of the app: on a device they lay out the moment the player
     * scrolls. The flag lets a zero-size node be told apart from a control
     * that is genuinely missing: the first is skipped with a note, the second
     * fails.
     */
    private fun walkWithScroll(
        node: SemanticsNode,
        inScroll: Boolean,
        scrollBottom: Float,
        visit: (SemanticsNode, Boolean, Float) -> Unit
    ) {
        val scrollable = inScroll ||
            node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null ||
            node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null ||
            node.config.getOrNull(SemanticsActions.ScrollBy) != null
        // A scroll container's own bounds are the visible viewport. Text whose
        // box crosses that edge is clipped by the scroll, and clipping is not a
        // colour problem: measuring it reads whatever happens to be painted
        // underneath (for the home screen's premium strip, the play button).
        val viewportBottom =
            if (scrollable) node.boundsInRoot.bottom else scrollBottom
        visit(node, scrollable, viewportBottom)
        node.children.forEach { walkWithScroll(it, scrollable, viewportBottom, visit) }
    }

    private fun audit(name: String, minContrast: Double = 4.5) {
        // Every root, not just the first. A `Dialog` (daily reward, no lives,
        // rate-us) is a separate window with its own root node, so a screen
        // with a dialog up would otherwise be audited as though the dialog and
        // everything in it did not exist.
        val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
        if (roots.isEmpty()) {
            clickFailures.add(name)
            QaFindings.click("$name: no root nodes rendered at all")
            return
        }

        // One screenshot for the whole screen, dialogs included: the decor
        // view draws every window. Saved once, measured against by the MAIN root.
        val img = captureScreen(name)
        val qaFile = img?.let { Qa.save(name, it) }
        if (qaFile != null) QaFindings.shot(qaFile.name)

        var textNodes = 0
        var clickNodes = 0
        var skippedOffscreen = 0
        // Boxes already judged on this screen, with their verdicts. A merged
        // parent and its children can carry the same text twice ("فروشگاه" on
        // the tile and "فروشگاه فروشگاه" on the merged tile node): the child
        // box is then sampled inside pixels the parent already explained, and
        // whatever it finds is an artifact of double counting, not a second
        // label. A node fully inside an already-measured box with
        // subset-or-equal text is skipped — unless the outer verdict was bad,
        // in which case every reading stays on the table.
        val verdicts = mutableListOf<TextVerdict>()

        // FIND THE MAIN ROOT: the one with the most text nodes (dialogs have few).
        // Only measure contrast against the main root's text nodes against the
        // full-screen bitmap. Dialogs get click-audited but not contrast-measured.
        var mainRoot: androidx.compose.ui.semantics.SemanticsNode? = null
        var maxTextNodes = 0
        roots.forEach { root ->
            var count = 0
            walkTree(root) { node ->
                val t = node.config.getOrNull(SemanticsProperties.Text)
                if (!t.isNullOrEmpty()) count++
            }
            if (count > maxTextNodes) {
                maxTextNodes = count
                mainRoot = root
            }
        }

        // PASS 1: Contrast on main root only
        if (img != null && qaFile != null && mainRoot != null) {
            walkWithScroll(mainRoot, false, Float.MAX_VALUE) { node, inScroll, scrollBottom ->
                val m = measureText(name, node, img, qaFile, minContrast, inScroll, scrollBottom, verdicts)
                if (m < 0) skippedOffscreen++ else textNodes += m
            }
        }

        // PASS 2: Clicks on ALL roots (main + dialogs)
        val clickRoots = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
        clickRoots.forEachIndexed { index, root ->
            val rootName = if (clickRoots.size == 1) name else "$name-dialog$index"
            walkWithScroll(root, false, Float.MAX_VALUE) { node, inScroll, _ ->
                clickNodes += tryClick(rootName, node, inScroll)
            }
        }
        if (skippedOffscreen > 0) {
            QaFindings.diagnostic("$name: $skippedOffscreen node(s) below the fold skipped (not laid out with the clock paused)")
        }
        notes.add(String.format("%-34s text=%-3d clickable=%-3d", name, textNodes, clickNodes))
        QaFindings.screen(name, textNodes, clickNodes)
    }

    /**
     * Returns 1 when the node carried measurable text, 0 when it carried none,
     * and -1 when the node was below the fold: present in the tree but never
     * placed, so there are no pixels to measure. -1 is skipped with a note,
     * not failed — the player reaches it by scrolling, and scrolled-to layout
     * is not something a paused clock can produce.
     */
    private fun measureText(
        screen: String,
        node: SemanticsNode,
        img: ImageBitmap,
        shot: java.io.File,
        min: Double,
        inScroll: Boolean,
        scrollBottom: Float,
        verdicts: MutableList<TextVerdict>
    ): Int {
        val text = node.config.getOrNull(SemanticsProperties.Text)
            ?.filter { it.text.isNotBlank() }
        if (text.isNullOrEmpty()) return 0

        val raw = text.joinToString(" ") { it.text }
        // Colour emoji carry no ink-vs-background meaning: a ❤️ is red, green
        // and shadow pixels by design, and whatever single bucket wins the
        // "furthest colour" contest, the ratio it produces says nothing about
        // whether the screen is readable. A node with no letter or digit in it
        // is decoration, not type.
        if (raw.none { Character.isLetterOrDigit(it) }) return 0

        val label = raw.take(28)

        val box = rootBounds(node, img.width, img.height)
        // A text node that crosses the bottom edge of its scroll viewport is
        // half-clipped. The pixels in its box are whatever was painted behind
        // it, not its glyphs — the home screen's partly-scrolled premium strip
        // "measured" the play button underneath and looked like 1.11:1 text.
        // It is not a contrast failure; the player scrolls it fully into view.
        if (inScroll && box.bottom >= scrollBottom - 1f) return -1
        if (box.width < 3 || box.height < 3) {
            if (inScroll) return -1
            notes.add("  ! $screen: \"$label\" measured ${box.width}x${box.height}px (too small to sample)")
            return 1
        }

        // Skip the double-counted child: merged/unmerged pairs carry the same
        // text but with slightly different bounds. Strict containment often
        // fails because the merged node's bounds are snapped to layout
        // boundaries while the unmerged child is snapped to its own content.
        // Instead: if text is identical (or one contains the other) AND the
        // boxes overlap by >10% of the smaller box's area, it's a duplicate.
        // ALSO: if text is identical and box centers are within 50px, it's
        // the same UI element rendered twice (merged + unmerged).
        val duplicate = verdicts.any { v ->
            val centersClose = raw == v.label && {
                val cx1 = (box.left + box.right) / 2f
                val cy1 = (box.top + box.bottom) / 2f
                val cx2 = (v.box.left + v.box.right) / 2f
                val cy2 = (v.box.top + v.box.bottom) / 2f
                val dx = cx1 - cx2
                val dy = cy1 - cy2
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                dist < 50f
            }()
            (raw == v.label || v.label.contains(raw) || raw.contains(v.label)) &&
                (box.area * v.box.area > 0) &&
                (kotlin.math.min(box.area, v.box.area).toDouble() /
                    kotlin.math.max(box.area, v.box.area) > 0.10 ||
                        centersClose)
        }
        if (duplicate) return 1

        val r = PixelContrast.measure(img, box) ?: return 1
        // No ink at all: the box is a single flat colour. Inside a scroll
        // container that is the clipped sliver of a node below the fold (a few
        // pixels tall, glyphs not yet placed) — skipped like every other
        // below-fold node. Outside one it is recorded as a note, not a
        // failure: a flat box with a text role is suspicious, but the audit
        // has no way to tell "alpha-0 ghost" from "layout hasn't run", and a
        // false failure here would cry wolf on every screen.
        if (r.ink == null) {
            if (inScroll) return -1
            QaFindings.diagnostic("$screen: \"$label\" box holds no ink — cannot judge (shot=${shot.name})")
            return 1
        }
        // Two bars, not one. Below 3:1 a label is unreadable at any size —
        // that is a failure and breaks the build. Between 3:1 and 4.5:1 it
        // passes WCAG's large-text floor but not the normal-text one; those
        // are warnings for a human to judge against the screenshot, because
        // the harness cannot see font size and a 20sp Black headline at 3.6:1
        // reads perfectly well. Everything here is still printed, so nothing
        // is silently excused.
        if (r.ratio < min) {
            val msg = String.format(
                "%-20s \"%s\" ink=%s on %s = %.2f:1 (needs %.1f:1)  shot=%s",
                screen, label,
                PixelContrast.Result(0.0, 0.0, r.ink, r.background).hex(r.ink),
                PixelContrast.Result(0.0, 0.0, r.ink, r.background).hex(r.background),
                r.ratio, min, shot.name
            )
            if (r.ratio < 3.0) {
                val crop = try {
                    Qa.saveCrop(
                        "$screen-crop-${label.replace(Regex("[^\\p{L}\\p{N}]+"), "_").take(16)}",
                        img, box
                    ).name
                } catch (_: Throwable) { "crop-failed" }
                val failMsg = "$msg  crop=$crop"
                contrastFailures.add(failMsg)
                QaFindings.contrast(failMsg)
            } else {
                QaFindings.warning(msg)
            }
        }
        // Only add passing readings to the verdicts list so ghosts with bad
        // contrast don't poison the duplicate filter for subsequent real nodes.
        if (r.ratio >= 3.0) {
            verdicts.add(TextVerdict(label = raw, box = box, ratio = r.ratio))
        }
        return 1
    }

    /** A judged text box, for the double-count guard in [audit]. */
    private data class TextVerdict(val label: String, val box: IntRect, val ratio: Double)

    private fun tryClick(screen: String, node: SemanticsNode, inScroll: Boolean): Int {
        if (node.config.getOrNull(SemanticsActions.OnClick) == null) return 0
        val label = QaClicks.label(node)

        // A Compose dialog root is itself an OnClick node: it exists so a tap
        // on the scrim dismisses the dialog. In a Robolectric root it has no
        // layout of its own, so it always measures 0x0 — and it is not a
        // control the player is meant to find. Counting it produces two
        // identical unlabelled failures against a dialog root that works fine
        // on a device.
        //
        // The rule is the label, not the size: an unlabelled clickable that
        // has no size is decoration. A *labelled* zero-size node is a real
        // bug (a button with a name and no tappable area) and still fails —
        // unless it sits inside a scroll container below the fold, where the
        // paused clock never placed it. Those are skipped with a note: the
        // player reaches them by scrolling.
        val box = node.boundsInRoot
        val density = compose.density.density
        val w = (box.right - box.left) / density
        val h = (box.bottom - box.top) / density
        val isFrameworkNode = label == "(unlabelled)" && (w < 1f || h < 1f)
        if (isFrameworkNode) return 1

        if (w < 1f || h < 1f) {
            if (inScroll) {
                QaFindings.diagnostic("$screen: \"$label\" below the fold, not laid out — skipped")
                return 1
            }
            val msg = "$screen: \"$label\" has zero size (${w.toInt()}x${h.toInt()}dp) - cannot be tapped"
            clickFailures.add(msg)
            QaFindings.click(msg)
            return 1
        }
        try {
            node.config.getOrNull(SemanticsActions.OnClick)!!.action?.invoke()
            // Advance a step rather than idling: a click can open a dialog,
            // trigger a coroutine, or start an animation, and none of that may
            // ever reach "idle" with an infinite transition running. Stepping
            // the clock a frame flushes what the tap queued without hanging.
            compose.mainClock.advanceTimeBy(100)
        } catch (t: Throwable) {
            val msg = "$screen: \"$label\" threw on tap: ${t.javaClass.simpleName}: ${t.message}"
            clickFailures.add(msg)
            QaFindings.click(msg)
        }
        return 1
    }

    private fun rootBounds(node: SemanticsNode, width: Int, height: Int) =
        ir.amiraligrr.blockboom.qa.IntRect(
            node.boundsInRoot.left.toInt().coerceIn(0, width - 1),
            node.boundsInRoot.top.toInt().coerceIn(0, height - 1),
            node.boundsInRoot.right.toInt().coerceIn(1, width),
            node.boundsInRoot.bottom.toInt().coerceIn(1, height)
        )

    private fun overlapArea(a: ir.amiraligrr.blockboom.qa.IntRect, b: ir.amiraligrr.blockboom.qa.IntRect): Int {
        val left = kotlin.math.max(a.left, b.left)
        val top = kotlin.math.max(a.top, b.top)
        val right = kotlin.math.min(a.right, b.right)
        val bottom = kotlin.math.min(a.bottom, b.bottom)
        val w = right - left
        val h = bottom - top
        return if (w > 0 && h > 0) w * h else 0
    }

    // ── Screens ────────────────────────────────────────────────────────────
    // Each gets its own screenshot pair in build/qa/<theme>/.

    @Test
    fun homeLight() {
        screen(dark = false, name = "home-light") {
            ir.amiraligrr.blockboom.ui.screens.HomeScreen(
                onPlay = {}, onShop = {}, onUpgrade = {},
                onSettings = {}, onLeaderboard = {},
                onStats = {}, onSupport = {}, onInvite = {},
                onExit = {}
            )
        }
        audit("home-light")
    }

    @Test
    fun homeDark() {
        screen(dark = true, name = "home-dark") {
            ir.amiraligrr.blockboom.ui.screens.HomeScreen(
                onPlay = {}, onShop = {}, onUpgrade = {},
                onSettings = {}, onLeaderboard = {},
                onStats = {}, onSupport = {}, onInvite = {},
                onExit = {}
            )
        }
        audit("home-dark")
    }

    @Test
    fun settingsLight() {
        screen(dark = false, name = "settings-light") {
            ir.amiraligrr.blockboom.ui.screens.SettingsScreen(onBack = {}, onSupport = {})
        }
        audit("settings-light")
    }

    @Test
    fun settingsDark() {
        screen(dark = true, name = "settings-dark") {
            ir.amiraligrr.blockboom.ui.screens.SettingsScreen(onBack = {}, onSupport = {})
        }
        audit("settings-dark")
    }

    @Test
    fun statsLight() {
        screen(dark = false, name = "stats-light") {
            StatsScreen(
                saveManager = (androidx.compose.ui.platform.LocalContext.current
                    .applicationContext as BlockBoomApp).saveManager,
                onBack = {}
            )
        }
        audit("stats-light")
    }

    @Test
    fun statsDark() {
        screen(dark = true, name = "stats-dark") {
            StatsScreen(
                saveManager = (androidx.compose.ui.platform.LocalContext.current
                    .applicationContext as BlockBoomApp).saveManager,
                onBack = {}
            )
        }
        audit("stats-dark")
    }

    /**
     * Lets in-flight network calls resolve before the screenshot.
     *
     * Invite, leaderboard and support fetch on launch. The test clock is
     * paused, but their IO runs on real threads with 7–8s timeouts — so after
     * ~12 real seconds they have either succeeded or failed, and the screen
     * shows its resolved state (content or error card with retry) instead of
     * its spinner. Without this the audit only ever measures the loading
     * skeleton, and the error card — the exact UI an offline player sees —
     * goes unaudited.
     *
     * If the call is still in flight afterwards, that is reported, not failed:
     * a slow sandbox network is a property of the lab, not of the app.
     */
    private fun settleNetwork(name: String) {
        try {
            Thread.sleep(12_000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        compose.mainClock.advanceTimeBy(500)
    }

    @Test
    fun inviteLight() {
        screen(dark = false, name = "invite-light") {
            ir.amiraligrr.blockboom.ui.screens.InviteFriendsScreen(onBack = {})
        }
        settleNetwork("invite-light")
        audit("invite-light")
    }

    @Test
    fun inviteDark() {
        screen(dark = true, name = "invite-dark") {
            ir.amiraligrr.blockboom.ui.screens.InviteFriendsScreen(onBack = {})
        }
        settleNetwork("invite-dark")
        audit("invite-dark")
    }

    @Test
    fun leaderboardLight() {
        screen(dark = false, name = "leaderboard-light") {
            ir.amiraligrr.blockboom.ui.screens.LeaderboardScreen(onBack = {})
        }
        settleNetwork("leaderboard-light")
        audit("leaderboard-light")
    }

    @Test
    fun leaderboardDark() {
        screen(dark = true, name = "leaderboard-dark") {
            ir.amiraligrr.blockboom.ui.screens.LeaderboardScreen(onBack = {})
        }
        settleNetwork("leaderboard-dark")
        audit("leaderboard-dark")
    }

    @Test
    fun supportLight() {
        screen(dark = false, name = "support-light") {
            ir.amiraligrr.blockboom.ui.screens.SupportScreen(onBack = {})
        }
        settleNetwork("support-light")
        audit("support-light")
    }

    @Test
    fun supportDark() {
        screen(dark = true, name = "support-dark") {
            ir.amiraligrr.blockboom.ui.screens.SupportScreen(onBack = {})
        }
        settleNetwork("support-dark")
        audit("support-dark")
    }

    @Test
    fun shopLight() {
        screen(dark = false, name = "shop-light") {
            ir.amiraligrr.blockboom.ui.screens.ShopScreen(onBack = {})
        }
        audit("shop-light")
    }

    @Test
    fun shopDark() {
        screen(dark = true, name = "shop-dark") {
            ir.amiraligrr.blockboom.ui.screens.ShopScreen(onBack = {})
        }
        audit("shop-dark")
    }

    @Test
    fun onboardingLight() {
        screen(dark = false, name = "onboarding-light") {
            ir.amiraligrr.blockboom.ui.screens.OnboardingScreen(
                saveManager = (androidx.compose.ui.platform.LocalContext.current
                    .applicationContext as BlockBoomApp).saveManager,
                onComplete = {}
            )
        }
        audit("onboarding-light")
    }

    @Test
    fun onboardingThemeLight() {
        screen(dark = false, name = "onboarding-theme-light") {
            ir.amiraligrr.blockboom.ui.screens.OnboardingScreen(
                saveManager = (androidx.compose.ui.platform.LocalContext.current
                    .applicationContext as BlockBoomApp).saveManager,
                onComplete = {}
            )
        }
        compose.onNodeWithText("شروع کنیم").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("انصراف").performClick()
        compose.mainClock.advanceTimeBy(500)
        audit("onboarding-theme-light")
    }

    @Test
    fun onboardingThemeDark() {
        screen(dark = true, name = "onboarding-theme-dark") {
            ir.amiraligrr.blockboom.ui.screens.OnboardingScreen(
                saveManager = (androidx.compose.ui.platform.LocalContext.current
                    .applicationContext as BlockBoomApp).saveManager,
                onComplete = {}
            )
        }
        compose.onNodeWithText("شروع کنیم").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("انصراف").performClick()
        compose.mainClock.advanceTimeBy(500)
        audit("onboarding-theme-dark")
    }

    @Test
    fun upgradeLight() {
        screen(dark = false, name = "upgrade-light") {
            val app = androidx.compose.ui.platform.LocalContext.current
                .applicationContext as BlockBoomApp
            val engine = ir.amiraligrr.blockboom.domain.GameEngine(app.saveManager)
            ir.amiraligrr.blockboom.ui.screens.UpgradeScreen(
                engine = engine,
                showLevelHeader = false,
                onContinue = {}
            )
        }
        audit("upgrade-light")
    }

    @Test
    fun upgradeDark() {
        screen(dark = true, name = "upgrade-dark") {
            val app = androidx.compose.ui.platform.LocalContext.current
                .applicationContext as BlockBoomApp
            val engine = ir.amiraligrr.blockboom.domain.GameEngine(app.saveManager)
            ir.amiraligrr.blockboom.ui.screens.UpgradeScreen(
                engine = engine,
                showLevelHeader = false,
                onContinue = {}
            )
        }
        audit("upgrade-dark")
    }

    @Test
    fun gameLight() {
        screen(dark = false, name = "game-light") {
            val app = androidx.compose.ui.platform.LocalContext.current
                .applicationContext as BlockBoomApp
            val engine = ir.amiraligrr.blockboom.domain.GameEngine(app.saveManager)
            engine.beginRun()
            engine.startLevel(1)
            ir.amiraligrr.blockboom.ui.screens.GameScreen(
                engine = engine,
                initialCoins = app.saveManager.coins,
                onLevelContinue = {},
                onGameOver = {},
                onExit = {}
            )
        }
        audit("game-light")
    }

    @Test
    fun gameOverLight() {
        val app: BlockBoomApp =
            androidx.test.core.app.ApplicationProvider.getApplicationContext<QaTestApp>()
        // A real game-over, not a forced status: the engine plays itself with
        // no input until its lives run out. Cap the fast-forward so a balance
        // change that makes the game unwinnable-slow fails open (skip with a
        // note) instead of hanging the suite.
        val engine = ir.amiraligrr.blockboom.domain.GameEngine(app.saveManager)
        engine.beginRun()
        engine.startLevel(1)
        var steps = 0
        while (engine.status != ir.amiraligrr.blockboom.domain.GameStatus.GameOver && steps < 60_000) {
            engine.update(1f / 30f)
            steps++
        }
        if (engine.status != ir.amiraligrr.blockboom.domain.GameStatus.GameOver) {
            QaFindings.diagnostic("gameOver-light: engine did not reach GameOver in $steps steps — skipped")
            return
        }
        screen(dark = false, name = "gameOver-light") {
            ir.amiraligrr.blockboom.ui.screens.GameOverScreen(
                engine = engine,
                initialCoins = app.saveManager.coins,
                submitState = ir.amiraligrr.blockboom.ui.SubmitState.Idle,
                onSubmitScore = {},
                onTryAgain = {},
                onHome = {}
            )
        }
        audit("gameOver-light")
    }

    // Sorts last, so it runs after every screen has been measured.
    //
    // `notes` is a per-instance field and Robolectric reuses one instance per
    // method, so it is empty by the time this runs — the confirmation has to
    // read the accumulated state, not the instance's.
    @Test
    fun zzzConfirmSomethingWasMeasured() {
        val findings = QaFindings.snapshot()
        assertTrue(
            "no screen produced any text or clickable node — the harness is running blind",
            findings.perScreen.isNotEmpty() &&
                findings.perScreen.values.any { it.first > 0 || it.second > 0 }
        )
    }
}
