package com.agendio.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.agendio.app.R
import com.agendio.app.data.AppointmentView

object SocialLinks {

    private const val INSTAGRAM_FALLBACK = "https://www.instagram.com/tatibarrosmanicure/"

    fun openInstagram(context: Context) {
        val url = context.getString(R.string.instagram_url).ifBlank { INSTAGRAM_FALLBACK }
        openUrl(context, url, context.getString(R.string.whatsapp_unavailable))
    }

    fun openWhatsAppConfirmation(context: Context, appointment: AppointmentView) {
        val phone = normalizePhone(appointment.clientPhone)
        if (phone.isBlank()) {
            Toast.makeText(context, R.string.whatsapp_no_phone, Toast.LENGTH_SHORT).show()
            return
        }
        val message = context.getString(
            R.string.whatsapp_message,
            appointment.clientName.substringBefore(" "),
            Formats.dateWithWeekday(appointment.startMillis),
            Formats.hourLabel(appointment.startMillis),
            appointment.serviceName
        )
        openWhatsApp(context, phone, message)
    }

    fun openWhatsApp(context: Context, phone: String, message: String) {
        val normalized = normalizePhone(phone)
        if (normalized.isBlank()) {
            Toast.makeText(context, R.string.whatsapp_no_phone, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse("https://wa.me/$normalized?text=${Uri.encode(message)}")
        openUrl(context, uri.toString(), context.getString(R.string.whatsapp_unavailable))
    }

    fun normalizePhone(raw: String): String {
        var digits = raw.filter { it.isDigit() }
        if (digits.startsWith("0")) digits = digits.dropWhile { it == '0' }
        if (digits.length in 12..13 && digits.startsWith("55")) {
            return digits
        }
        return if (digits.length in 10..11) "55$digits" else digits
    }

    private fun openUrl(context: Context, url: String, errorMessage: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }
}
