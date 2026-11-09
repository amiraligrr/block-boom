package ir.amiraligrr.blockboom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.amiraligrr.blockboom.BlockBoomApp
import ir.amiraligrr.blockboom.R
import ir.amiraligrr.blockboom.data.TicketManager
import ir.amiraligrr.blockboom.ui.components.AppIcon
import ir.amiraligrr.blockboom.ui.components.AppIconView
import ir.amiraligrr.blockboom.ui.components.AppTextField
import ir.amiraligrr.blockboom.ui.components.ButtonSize
import ir.amiraligrr.blockboom.ui.components.NeonButton
import ir.amiraligrr.blockboom.ui.theme.JoyThemeColors
import ir.amiraligrr.blockboom.ui.theme.NeonCyan
import ir.amiraligrr.blockboom.ui.theme.NeonGold
import ir.amiraligrr.blockboom.ui.theme.NeonRed
import ir.amiraligrr.blockboom.ui.theme.joyThemeColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One question and its answer, in the order a player would ask them. */
private data class Faq(val question: String, val answer: String)

/** How often an open thread re-checks for an admin reply, in ms. */
private const val THREAD_POLL_MS = 8_000L

/**
 * Support, as a real conversation — not a mailto: link.
 *
 * Three panes in one screen: the thread list, one open thread, and the
 * new-ticket composer. The FAQ stays at the bottom of the list, because the
 * four questions it answers are still the fastest answer for most players
 * and need no network at all.
 *
 * An open thread polls every few seconds while it is on screen, so a reply
 * the admin sends from the panel lands in front of the player without a
 * manual refresh — the thing that makes it feel like a chat rather than a
 * form. Nothing polls when the screen is not open; the home badge is updated
 * by a single refresh on launch instead.
 */
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val c = joyThemeColors()
    val context = LocalContext.current
    val app = context.applicationContext as BlockBoomApp
    val saveManager = app.saveManager
    val scope = rememberCoroutineScope()

    var tickets by remember { mutableStateOf<List<TicketManager.Ticket>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var openTicketId by remember { mutableStateOf<Int?>(null) }
    var showComposer by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loadError = null
            val res = TicketManager.list(context)
            if (res.isSuccess) {
                tickets = res.getOrNull().orEmpty()
            } else {
                loadError = res.exceptionOrNull()?.message
                if (tickets == null) tickets = emptyList()
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    // Hoisted out of the LazyColumn scope below: LazyListScope content is not
    // a @Composable context, so stringResource() cannot be read there.
    val faqs = faqItems()

    val openId = openTicketId
    if (openId != null) {
        TicketThread(
            c = c,
            ticketId = openId,
            playerName = saveManager.playerName,
            onBack = {
                openTicketId = null
                reload()
            }
        )
        return
    }

    if (showComposer) {
        NewTicketComposer(
            c = c,
            playerName = saveManager.playerName,
            onBack = { showComposer = false },
            onCreated = {
                showComposer = false
                reload()
                openTicketId = it
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.gradientBackground)
    ) {
        SupportHeader(c = c, title = stringResource(R.string.support_title), onBack = onBack)

        val list = tickets
        when {
            list == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.emerald)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        NeonButton(
                            text = stringResource(R.string.support_new_ticket),
                            onClick = { showComposer = true },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = c.emerald,
                            borderColor = Color.White,
                            contentColor = Color.White,
                            size = ButtonSize.Large,
                            icon = AppIcon.SEND
                        )
                    }

                    if (loadError != null && list.isEmpty()) {
                        item {
                            ErrorCard(c = c, message = loadError!!, onRetry = { reload() })
                        }
                    }

                    items(list, key = { it.id }) { ticket ->
                        TicketRow(
                            c = c,
                            ticket = ticket,
                            onClick = { openTicketId = ticket.id }
                        )
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.support_faq_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = c.textPrimary,
                                fontWeight = FontWeight.Black
                            )
                        )
                    }

                    faqs.forEachIndexed { i, faq ->
                        item(key = "faq$i") { FaqCard(c = c, index = i, faq = faq) }
                    }
                }
            }
        }
    }
}

/** One thread, as chat bubbles, with a reply box and live polling. */
@Composable
private fun TicketThread(
    c: JoyThemeColors,
    ticketId: Int,
    playerName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var thread by remember(ticketId) { mutableStateOf<TicketManager.Thread?>(null) }
    var error by remember(ticketId) { mutableStateOf<String?>(null) }
    var draft by remember(ticketId) { mutableStateOf("") }
    var sending by remember(ticketId) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    suspend fun load(silent: Boolean = false) {
        if (!silent) error = null
        val res = TicketManager.thread(context, ticketId)
        if (res.isSuccess) {
            thread = res.getOrNull()
            error = null
        } else if (!silent) {
            error = res.exceptionOrNull()?.message
        }
    }

    LaunchedEffect(ticketId) { load() }

    // Poll while the thread is open and alive. Stops with the composition —
    // leaving the screen stops the loop, so there is no background polling.
    LaunchedEffect(ticketId, thread?.status) {
        if (thread?.status == "closed") return@LaunchedEffect
        while (isActive) {
            delay(THREAD_POLL_MS)
            load(silent = true)
        }
    }

    // Pin to the newest message when the thread grows.
    val msgCount = thread?.messages?.size ?: 0
    LaunchedEffect(msgCount) {
        if (msgCount > 0) listState.animateScrollToItem(msgCount - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.gradientBackground)
    ) {
        SupportHeader(
            c = c,
            title = thread?.subject ?: "…",
            onBack = onBack
        )

        val t = thread
        when {
            t == null && error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorCard(c = c, message = error!!, onRetry = { scope.launch { load() } })
                }
            }
            t == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.emerald)
                }
            }
            else -> {
                if (t.status == "closed") {
                    ClosedBanner(c = c)
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    state = listState,
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(t.messages, key = { it.id }) { msg ->
                        ChatBubble(c = c, msg = msg, playerName = playerName)
                    }
                }
                if (t.status != "closed") {
                    ReplyBar(
                        c = c,
                        draft = draft,
                        onDraft = { draft = it },
                        sending = sending,
                        onSend = {
                            if (draft.isBlank() || sending) return@ReplyBar
                            sending = true
                            scope.launch {
                                val res = TicketManager.reply(context, ticketId, draft)
                                sending = false
                                if (res.isSuccess) {
                                    draft = ""
                                    load(silent = true)
                                } else {
                                    error = res.exceptionOrNull()?.message
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/** The new-ticket form. Subject, category, message — nothing else. */
@Composable
private fun NewTicketComposer(
    c: JoyThemeColors,
    playerName: String,
    onBack: () -> Unit,
    onCreated: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(TicketManager.Category.BUG) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.gradientBackground)
    ) {
        SupportHeader(c = c, title = stringResource(R.string.support_new_ticket), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.support_subject),
                style = MaterialTheme.typography.labelLarge.copy(
                    color = c.textPrimary, fontWeight = FontWeight.Bold
                )
            )
            AppTextField(
                value = subject,
                onValueChange = { subject = it.take(160) },
                placeholder = stringResource(R.string.support_subject_hint),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.support_category),
                style = MaterialTheme.typography.labelLarge.copy(
                    color = c.textPrimary, fontWeight = FontWeight.Bold
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TicketManager.Category.entries.forEach { cat ->
                    val selected = cat == category
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) c.emerald.copy(alpha = 0.22f)
                                else c.surface
                            )
                            .border(
                                1.dp,
                                if (selected) c.emerald else c.cardBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { category = cat }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat.fa,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
                            color = if (selected) c.textPrimary else c.textSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.support_message),
                style = MaterialTheme.typography.labelLarge.copy(
                    color = c.textPrimary, fontWeight = FontWeight.Bold
                )
            )
            AppTextField(
                value = body,
                onValueChange = { body = it.take(2000) },
                placeholder = stringResource(R.string.support_message_hint),
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 5,
                maxLength = 2000
            )

            if (error != null) {
                ErrorCard(c = c, message = error!!, onRetry = null)
            }

            NeonButton(
                text = if (sending) "…" else stringResource(R.string.support_send),
                onClick = {
                    if (subject.isBlank() || body.isBlank() || sending) return@NeonButton
                    sending = true
                    error = null
                    scope.launch {
                        val res = TicketManager.create(
                            context, subject, body, category,
                            playerName.ifBlank { "بازیکن" }
                        )
                        sending = false
                        if (res.isSuccess) {
                            onCreated(res.getOrNull() ?: 0)
                        } else {
                            error = res.exceptionOrNull()?.message
                        }
                    }
                },
                enabled = !sending && subject.isNotBlank() && body.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                containerColor = c.emerald,
                borderColor = Color.White,
                contentColor = Color.White,
                size = ButtonSize.Large,
                icon = AppIcon.SEND
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── pieces ───────────────────────────────────────────────────────────────

@Composable
private fun SupportHeader(c: JoyThemeColors, title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeonButton(
            text = "→",
            onClick = onBack,
            containerColor = c.surfaceSoft,
            borderColor = c.cardBorder,
            size = ButtonSize.Small,
            icon = AppIcon.CLOSE
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                color = c.textPrimary, fontWeight = FontWeight.Black
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TicketRow(
    c: JoyThemeColors,
    ticket: TicketManager.Ticket,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, c.cardBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ticket.subject,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = c.textPrimary, fontWeight = FontWeight.Black
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (ticket.unread) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(NeonRed)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = ticket.lastBody.ifBlank { "…" },
                style = MaterialTheme.typography.bodySmall.copy(color = c.textMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(c = c, ticket = ticket)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = ticket.category.fa,
                    fontSize = 11.sp,
                    color = c.textMuted
                )
            }
        }
        Text(text = "‹", fontSize = 22.sp, color = c.textMuted)
    }
}

@Composable
private fun StatusChip(c: JoyThemeColors, ticket: TicketManager.Ticket) {
    val (bg, fg) = when (ticket.status) {
        "closed" -> c.cardBorder to c.textMuted
        "answered" -> NeonCyan.copy(alpha = 0.20f) to NeonCyan
        else -> NeonGold.copy(alpha = 0.20f) to NeonGold
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text = ticket.statusFa(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun ChatBubble(
    c: JoyThemeColors,
    msg: TicketManager.TicketMessage,
    playerName: String
) {
    // Admin on the left, player on the right — the one layout every chat
    // user already knows, so nobody has to learn which side is which.
    val mine = !msg.fromAdmin
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (mine) 16.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 16.dp
                    )
                )
                .background(
                    if (mine) c.emerald.copy(alpha = 0.85f)
                    else c.surface
                )
                .border(
                    1.dp,
                    if (mine) Color.Transparent else c.cardBorder,
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (mine) 16.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            if (!mine) {
                Text(
                    text = "🛡️ " + stringResource(R.string.support_admin),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonCyan
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = msg.body,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (mine) Color.White else c.textPrimary,
                    lineHeight = 20.sp
                )
            )
        }
    }
}

@Composable
private fun ReplyBar(
    c: JoyThemeColors,
    draft: String,
    onDraft: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .border(1.dp, c.cardBorder)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTextField(
            value = draft,
            onValueChange = onDraft,
            placeholder = stringResource(R.string.support_reply_hint),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        NeonButton(
            text = if (sending) "…" else "",
            onClick = onSend,
            enabled = !sending && draft.isNotBlank(),
            containerColor = c.emerald,
            borderColor = Color.White,
            contentColor = Color.White,
            size = ButtonSize.Small,
            icon = AppIcon.SEND
        )
    }
}

@Composable
private fun ClosedBanner(c: JoyThemeColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surfaceSoft)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.support_closed),
            fontSize = 12.sp,
            color = c.textMuted
        )
    }
}

@Composable
private fun ErrorCard(c: JoyThemeColors, message: String, onRetry: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, NeonRed.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall.copy(color = c.textSecondary),
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Spacer(Modifier.height(8.dp))
            NeonButton(
                text = stringResource(R.string.retry),
                onClick = onRetry,
                containerColor = c.surfaceSoft,
                borderColor = c.cardBorder,
                size = ButtonSize.Small
            )
        }
    }
}

/** One FAQ: the question as a header, the answer under it, numbered. */
@Composable
private fun FaqCard(c: JoyThemeColors, index: Int, faq: Faq) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = c.cardShadow, spotColor = c.cardShadow)
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, c.cardBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelMedium.copy(
                    // Was white on `NeonGold`: 1.3:1, so the FAQ numbers were
                    // pale ghosts in their yellow chips. `c.onGold` is the
                    // token made for exactly this — ink that sits on gold —
                    // 6.7:1 in light, ~10:1 in dark.
                    color = c.onGold,
                    fontWeight = FontWeight.Black
                ),
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonGold),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = faq.question,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = c.textPrimary,
                    fontWeight = FontWeight.Black
                )
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = faq.answer,
            style = MaterialTheme.typography.bodySmall.copy(
                color = c.textSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        )
    }
}

@Composable
private fun faqItems(): List<Faq> = listOf(
    Faq(stringResource(R.string.support_q1), stringResource(R.string.support_a1)),
    Faq(stringResource(R.string.support_q2), stringResource(R.string.support_a2)),
    Faq(stringResource(R.string.support_q3), stringResource(R.string.support_a3)),
    Faq(stringResource(R.string.support_q4), stringResource(R.string.support_a4))
)
