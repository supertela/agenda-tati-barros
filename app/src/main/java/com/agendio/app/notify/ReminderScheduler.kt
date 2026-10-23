package com.agendio.app.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.agendio.app.R
import com.agendio.app.data.Appointment
import com.agendio.app.data.DbHelper

object ReminderScheduler {
    const val CHANNEL_ID = "agendio_reminders"
    const val EXTRA_APPOINTMENT_ID = "appointment_id"

    private const val REMINDER_OFFSET_MS = 30 * 60 * 1000L

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_description)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun schedule(context: Context, appointment: Appointment) {
        val triggerAt = appointment.startMillis - REMINDER_OFFSET_MS
        if (triggerAt <= System.currentTimeMillis()) return

        ensureChannel(context)
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, appointment.id, PendingIntent.FLAG_UPDATE_CURRENT)

        try {
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                    manager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }

                manager.canScheduleExactAlarms() -> {
                    manager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }

                else -> {
                    manager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context, appointmentId: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, appointmentId, PendingIntent.FLAG_UPDATE_CURRENT)
        manager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun rescheduleAll(context: Context) {
        val db = DbHelper(context)
        try {
            db.getUpcomingAppointments(System.currentTimeMillis()).forEach { appointment ->
                schedule(context, appointment)
            }
        } finally {
            db.close()
        }
    }

    private fun pendingIntent(context: Context, appointmentId: Long, flags: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_APPOINTMENT_ID, appointmentId)
        }
        return PendingIntent.getBroadcast(
            context,
            appointmentId.toInt(),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
