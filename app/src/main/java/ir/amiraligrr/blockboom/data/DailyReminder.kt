package ir.amiraligrr.blockboom.data

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.amiraligrr.blockboom.MainActivity
import ir.amiraligrr.blockboom.R
import kotlin.math.min

/**
 * One local reminder, a day apart.
 *
 * ── Why this exists ────────────────────────────────────────────────────────
 * The leaderboard is a board of *lifetime* score that everyone on it is adding
 * to every day, and it has no rhythm of its own: from outside the app, a
 * player's row is identical whether they played an hour ago or a month ago. A
 * day is the shortest interval at which "you have fallen down the board" becomes
 * true for someone who has stopped playing, so it is the interval at which the
 * nudge is worth anything.
 *
 * The daily login reward is mentioned in the copy when it is actually
 * claimable, because it is a real reason to open the app — but it is not what
 * the reminder is *for* any more. It used to be: the reminder was scheduled
 * around the reward's window and cancelled outright when the reward was sitting
 * unclaimed, so a player who had not opened the app for a week got nothing.
 * That is exactly backwards, and the whole schedule is now a plain 24-hour
 * timer instead.
 *
 * ── Why AlarmManager and not WorkManager ───────────────────────────────────
 * WorkManager is the right tool for anything with a guarantee, and there is no
 * guarantee wanted here. A reminder that is late by an hour is still useful; a
 * reminder that is a no-op because a dependency could not be resolved is worse
 * than not shipping it. `AlarmManager.setWindow` also sidesteps
 * `SCHEDULE_EXACT_ALARM`, which on API 31+ is a user-revocable permission — for a
 * "your reward is ready" nudge, being inexact is not a defect.
 *
 * The alarm re-arms itself in [ReminderReceiver] and again on boot, so the chain
 * never expires. It is also re-armed on every app launch, which makes it
 * self-healing: if a device clears alarms, or the user changes the clock, the
 * next launch repairs it rather than leaving a dead chain.
 */
object DailyReminder {

    private const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"


    private const val CHANNEL_ID = "blockboom_daily"
    private const val CHANNEL_NAME = "یادآوری روزانه"
    private const val NOTIFICATION_ID = 4101
    private const val REQUEST_CODE = 4102

    /** Idempotent; safe to call on every launch. */
    private const val ACTION_FIRE = "ir.amiraligrr.blockboom.DAILY_REMINDER"
    private const val ACTION_BOOT = "ir.amiraligrr.blockboom.BOOT_COMPLETED"

    /**
     * How wide the delivery window is.
     *
     * An hour. Narrow enough that "yesterday's reward is ready" is true when the
     * notification arrives, wide enough that the system is free to batch it with
     * other work instead of waking the device for one app's reminder.
     */
    private const val WINDOW_MS = 60L * 60 * 1000

    // ── Permission ──────────────────────────────────────────────────────────

    /**
     * Whether the OS will actually show a notification.
     *
     * Two separate things, and both have to be true: the player has not switched
     * the feature off in our own settings, and the OS has not revoked the
     * permission. Checking only our own flag is the bug that ships reminders to
     * devices that will silently drop them.
     */
    fun canPost(context: Context, save: SaveManager): Boolean {
        if (!save.notificationsEnabled) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Needs the runtime permission on API 33+. */
    fun needsRuntimePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // ── Scheduling ──────────────────────────────────────────────────────────

    /**
     * (Re)arms the reminder, or cancels it when notifications are off.
     *
     * Safe and cheap to call on every launch; that is the point.
     *
     * The schedule is a flat day from the last reminder, not a follower of the
     * daily reward. It used to be the latter, and it had the property that a
     * player who had *not* claimed the reward — the player most worth bringing
     * back — was the one player who got no reminder at all, because a claimable
     * reward read as "they are already in there".
     */
    fun sync(context: Context, save: SaveManager) {
        if (!canPost(context, save)) {
            cancel(context)
            return
        }
        val at = save.nextReminderAtMillis()
        if (at == Long.MIN_VALUE) {
            cancel(context)
            return
        }
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = pendingIntent(context)
        runCatching {
            // setWindow, not setExactAndAllowWhileIdle: inexact is the correct
            // behaviour for this and needs no special permission on API 31+.
            alarm.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, pi)
        }
    }

    fun cancel(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { alarm.cancel(pendingIntent(context)) }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ── Delivery ────────────────────────────────────────────────────────────

    /**
     * Builds the reminder copy from live state.
     *
     * The body names the board, because that is what the reminder is for and a
     * notification that has to be acted on abstractly is one that gets swiped
     * away. The daily reward is added to it only when it is genuinely claimable —
     * promising coins that are not there is how a daily nudge teaches people to
     * ignore it.
     */
    private fun buildNotification(context: Context, save: SaveManager): Notification {
        val streak = save.dailyStreak
        val title = if (streak >= 3) {
            context.getString(R.string.reminder_title_streak, streak + 1)
        } else {
            context.getString(R.string.reminder_title)
        }
        val rewardClaimable = save.isDailyRewardAvailable()
        val body = if (rewardClaimable) {
            context.getString(
                R.string.reminder_body_reward,
                save.dailyRewardForStreak(min(streak + 1, SaveManager.DAILY_REWARD_MAX_STREAK))
            )
        } else {
            context.getString(R.string.reminder_body)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(context))
            // Swipe-away should be possible. A daily reminder is not a
            // conversation, and a notification the player cannot dismiss is one
            // they will revoke permission for.
            .setAutoCancel(true)
            .build()
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Posts, then re-arms. Called by [ReminderReceiver]. */
    fun fire(context: Context, save: SaveManager) {
        if (!canPost(context, save)) return
        val nm = NotificationManagerCompat.from(context)
        // `canPost` checks the app's own flag and whether the OS has notifications
        // switched on at all, which is not the same question as "is this specific
        // permission granted" — on API 33+ the two can disagree while the user is
        // in the system settings dialog. So the permission is checked directly too,
        // and the call is still guarded, because a permission can be revoked
        // between the check and the post.
        if (needsRuntimePermission() &&
            ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { nm.notify(NOTIFICATION_ID, buildNotification(context, save)) }
        // The anchor for tomorrow. Written here rather than derived from the
        // reward's claim time, which is what made the old schedule collapse to
        // "no reminder" for exactly the players who needed one.
        save.lastReminderFiredAt = System.currentTimeMillis()
        // Keep the chain alive. Without this the reminder is genuinely one-shot:
        // the player would be reminded once and then never again.
        sync(context, save)
    }

    /** Creates the channel. No-op below API 26, where channels do not exist. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val ch = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            // DEFAULT, not HIGH. This is a daily nudge; a high-importance channel
            // heads it and it is exactly the kind that teaches players to revoke
            // the permission, which would cost every future reminder.
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.reminder_channel_desc)
        }
        runCatching { nm.createNotificationChannel(ch) }
    }
}

/** Posts the reminder when the alarm fires. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        DailyReminder.ensureChannel(app)
        val save = SaveManager(app)
        DailyReminder.fire(app, save)
    }
}

/**
 * Re-arms the reminder after a reboot.
 *
 * Alarms do not survive a restart. Without this the chain silently dies the first
 * time the player reboots, which on a phone that is restarted nightly is *every*
 * night — so this is not a rare edge case, it is the normal one.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext
        DailyReminder.ensureChannel(app)
        DailyReminder.sync(app, SaveManager(app))
    }
}

/**
 * When the next reminder is due.
 *
 * A fixed day from the last one, with a floor so it can never land in the past
 * (a device whose clock moved, or an alarm that survived a reboot) and a ceiling
 * so a player who has not opened the app in a month is not immediately buried
 * under a backlog of reminders — the last one missed is the only one that is
 * still true.
 */
private fun SaveManager.nextReminderAtMillis(): Long {
    if (!notificationsEnabled) return Long.MIN_VALUE
    val now = System.currentTimeMillis()
    val anchor = lastReminderFiredAt
    if (anchor <= 0L) return now + FIRST_REMINDER_MS
    return anchor.coerceIn(now - DAY_MS, now) + DAY_MS
}

/** One reminder a day. The interval the leaderboard actually moves on. */
private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * How long to wait on a player who has never had one.
 *
 * Two hours. A brand-new install has no anchor to work from, and nudging someone
 * on day one — before they have played, and while they are still deciding whether
 * to keep the app — is the single most effective way to get a notification
 * turned off permanently. An hour is enough to be past that.
 */
private const val FIRST_REMINDER_MS = 2L * 60 * 60 * 1000