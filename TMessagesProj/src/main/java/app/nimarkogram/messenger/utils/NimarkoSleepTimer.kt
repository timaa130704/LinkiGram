/*
 * Copyright github.com/arsLan4k1390, 2022-2026.
 * Licensed under GNU GPL v2 or later. See LICENSE.
 */

package app.nimarkogram.messenger.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import app.nimarkogram.messenger.NimarkoConfig
import org.telegram.messenger.ApplicationLoader

/**
 * Schedules the sleep timer.
 *
 * The receiver existed since the Cherrygram port but nothing ever armed it, so
 * the toggle stored a flag nothing read and the feature was inert.
 *
 * Exactness is requested but not required. A sleep timer that drifts by a few
 * minutes is still a sleep timer, and requiring the user to grant an alarm
 * permission just to stop music in half an hour is a bad trade. When the OS
 * allows an exact alarm we take it, otherwise the timer falls back to an
 * inexact one and simply stops a little late.
 */
object NimarkoSleepTimer {

    private const val ACTION_STOP = "app.nimarkogram.messenger.STOP_PLAYBACK"

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(context: Context, flags: Int): PendingIntent {
        val intent = Intent(context, SleepHelper::class.java).setAction(ACTION_STOP)
        return PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or flags,
        )
    }

    @JvmStatic
    fun schedule(context: Context, minutes: Int) {
        cancel(context)
        if (minutes <= 0) {
            NimarkoConfig.setSleepTimerMinutes(0)
            return
        }

        val manager = alarmManager(context)
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val operation = pendingIntent(context, PendingIntent.FLAG_IMMUTABLE)

        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (exact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        } else {
            // No permission to be precise, and asking for it is not worth it:
            // music stops late instead of the timer being unusable.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }

        NimarkoConfig.setSleepTimerMinutes(minutes)
        NimarkoConfig.setSleepTimerAt(triggerAt)
    }

    @JvmStatic
    fun cancel(context: Context) {
        try {
            alarmManager(context).cancel(pendingIntent(context, PendingIntent.FLAG_IMMUTABLE))
        } catch (ignored: Throwable) {
        }
        NimarkoConfig.setSleepTimerMinutes(0)
        NimarkoConfig.setSleepTimerAt(0)
    }

    /** Minutes left, rounded up, or 0 when no timer is running. */
    @JvmStatic
    fun remainingMinutes(): Int {
        val at = NimarkoConfig.sleepTimerAt
        if (at <= 0L || NimarkoConfig.sleepTimerMinutes <= 0) {
            return 0
        }
        val left = at - System.currentTimeMillis()
        return if (left <= 0L) 0 else ((left + 59_999L) / 60_000L).toInt()
    }

    @JvmStatic
    fun apply(context: Context) {
        val minutes = NimarkoConfig.sleepTimerMinutes
        if (minutes <= 0) {
            cancel(context)
        } else {
            schedule(context, minutes)
        }
    }

    @JvmStatic
    fun current(): Context = ApplicationLoader.applicationContext
}