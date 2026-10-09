package com.cropadvisor

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/** A phone notification for a re-check reminder. Inexact alarm (no special permission); the Home banner still shows when the app opens. */
object Notify {
    private const val CH = "recheck"

    private fun pi(ctx: Context, cls: String, flags: Int = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent =
        PendingIntent.getBroadcast(ctx, cls.hashCode(), Intent(ctx, ReminderReceiver::class.java).putExtra("cls", cls), flags)

    fun schedule(ctx: Context, cls: String, atMs: Long) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi(ctx, cls))
        } catch (e: Throwable) { /* the banner on Home is the fallback */ }
    }

    fun cancel(ctx: Context, cls: String) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pi(ctx, cls))
        } catch (e: Throwable) {}
    }

    fun show(ctx: Context, cls: String) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val code = ctx.getSharedPreferences("cropadvisor", Context.MODE_PRIVATE).getString("lang", "en")
            val lang = Lang.entries.firstOrNull { it.code == code } ?: Lang.EN
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, tr(lang, "Re-check reminders", "दोबारा जाँच की याद"), NotificationManager.IMPORTANCE_DEFAULT))
            val name = Kb.get(cls, lang)?.name ?: cls.substringAfter("__").replace('_', ' ')
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, CH)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(tr(lang, "Time to re-check", "दोबारा जाँचने का समय"))
                .setContentText(name + ": " + tr(lang, "take a new photo of the same plant", "उसी पौधे की नई फोटो लें"))
                .setContentIntent(open).setAutoCancel(true).build()
            nm.notify(cls.hashCode(), n)
        } catch (e: Throwable) {}
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val cls = i.getStringExtra("cls") ?: return
        if (Kb.get(cls, Lang.EN) == null) { try { Kb.load(ctx) } catch (e: Throwable) {} }
        Notify.show(ctx, cls)
    }
}

/** After a reboot, Android forgets alarms. Put the re-check reminders back; any that came due while the phone was off show at once. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        try { Kb.load(ctx) } catch (e: Throwable) {}
        Rechecks.list(ctx).forEach { r ->
            if (r.due <= System.currentTimeMillis()) Notify.show(ctx, r.cls) else Notify.schedule(ctx, r.cls, r.due)
        }
    }
}
