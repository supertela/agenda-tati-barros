package com.agendio.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.agendio.app.MainActivity
import com.agendio.app.R
import com.agendio.app.data.AppointmentStatus
import com.agendio.app.data.DbHelper
import com.agendio.app.util.Formats

class ReminderReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val appointmentId = intent.getLongExtra(ReminderScheduler.EXTRA_APPOINTMENT_ID, -1L)
        if (appointmentId <= 0L) return

        val db = DbHelper(context)
        val appointment = try {
            db.getAppointmentView(appointmentId)
        } finally {
            db.close()
        }
        if (appointment == null) return
        if (!AppointmentStatus.isActive(appointment.status)) return
        if (appointment.startMillis < System.currentTimeMillis() - 60 * 60 * 1000L) return

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ReminderScheduler.ensureChannel(context)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            appointmentId.toInt(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = context.getString(
            R.string.reminder_text,
            appointment.clientName,
            Formats.time(appointment.startMillis),
            appointment.serviceName
        )

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .build()

        NotificationManagerCompat.from(context).notify(appointmentId.toInt(), notification)
    }
}
