package dk.galvo.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import java.util.Calendar

object ReminderScheduler {
    private const val CHANNEL = "galvo_daily"

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 100, Intent(ctx, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Next occurrence of the chosen time, then every day. Inexact so no special permission is needed. */
    fun schedule(ctx: Context) {
        if (!GalvoStore.reminderOn(ctx)) return
        val parts = GalvoStore.reminderTime(ctx).split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 7
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 30
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pending(ctx))
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Daglig påmindelse", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Galvo minder dig om dagens lektion"
                }
            )
        }
    }

    fun notify(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val s = GalvoStore.summary(ctx)
        if (s.doneToday && s.todayXp >= s.goal) return   // already done for today
        ensureChannel(ctx)
        val (title, text) = when {
            s.streak > 0 && !s.doneToday -> "Din streak på ${s.streak} dage er i fare!" to "Én lektion på 3 minutter redder den. Galvo venter."
            s.doneToday -> "Næsten i mål!" to "${s.goal - s.todayXp} XP mere, så er dagens mål nået."
            else -> "Galvo venter på dig" to "Tag dagens lektion – det tager kun 3 minutter."
        }
        val open = PendingIntent.getActivity(
            ctx, 101, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setLargeIcon(BitmapFactory.decodeResource(ctx.resources, GalvoStore.moodDrawable(if (s.streak > 0 && !s.doneToday) "worried" else "happy")))
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        ReminderScheduler.notify(ctx)
        GalvoStore.refreshWidgets(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        ReminderScheduler.schedule(ctx)
        GalvoStore.refreshWidgets(ctx)
    }
}
