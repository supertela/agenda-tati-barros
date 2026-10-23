package com.agendio.app.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Formats {
    val ptBr: Locale = Locale("pt", "BR")

    fun date(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy", ptBr).format(Date(millis))

    fun dateWithWeekday(millis: Long): String {
        val weekday = SimpleDateFormat("EEEE", ptBr).format(Date(millis))
        return "${date(millis)} (${weekday.lowercase(ptBr)})"
    }

    fun hourLabel(millis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return if (minute == 0) "${hour}h" else "${hour}h${String.format(ptBr, "%02d", minute)}"
    }

    fun shortDate(millis: Long): String =
        SimpleDateFormat("dd/MM", ptBr).format(Date(millis))

    fun time(millis: Long): String =
        SimpleDateFormat("HH:mm", ptBr).format(Date(millis))

    fun dateTime(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", ptBr).format(Date(millis))

    fun weekdayShort(millis: Long): String {
        val value = SimpleDateFormat("EEE", ptBr).format(Date(millis))
        return value.replace(".", "").replaceFirstChar { it.titlecase(ptBr) }
    }

    fun fullDate(millis: Long): String {
        val value = SimpleDateFormat("EEEE, dd 'de' MMMM", ptBr).format(Date(millis))
        return value.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(ptBr) else char.toString()
        }
    }

    fun monthTitle(millis: Long): String {
        val value = SimpleDateFormat("MMMM 'de' yyyy", ptBr).format(Date(millis))
        return value.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(ptBr) else char.toString()
        }
    }

    fun birthdayLabel(value: String): String {
        val parts = value.split("/")
        return if (parts.size >= 2) "${parts[0]}/${parts[1]}" else value
    }

    fun money(value: Double): String =
        NumberFormat.getCurrencyInstance(ptBr).format(value)

    fun percent(value: Double): String =
        String.format(ptBr, "%.0f%%", value)

    fun rate(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(ptBr).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
        return "${formatter.format(value)}%"
    }

    fun dayStart(millis: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun monthStart(millis: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun addMonths(monthStartMillis: Long, amount: Int): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = monthStartMillis
            add(Calendar.MONTH, amount)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun addMonthsSameDay(millis: Long, amount: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            add(Calendar.MONTH, amount)
        }.timeInMillis
    }

    fun addDaysSameTime(millis: Long, amount: Int): Long {
        return Calendar.getInstance().apply {
            timeInMillis = millis
            add(Calendar.DAY_OF_MONTH, amount)
        }.timeInMillis
    }

    fun addDays(dayStartMillis: Long, amount: Int): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = dayStartMillis
            add(Calendar.DAY_OF_MONTH, amount)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun defaultStart(dayStartMillis: Long): Long {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = dayStartMillis
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now) {
            calendar.timeInMillis = now
            calendar.add(Calendar.MINUTE, 30)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val minute = calendar.get(Calendar.MINUTE)
            val rounded = ((minute + 4) / 5) * 5
            calendar.set(Calendar.MINUTE, rounded)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase(ptBr)
            else -> (parts.first().take(1) + parts.last().take(1)).uppercase(ptBr)
        }
    }

    fun normalize(text: String): String {
        val withoutAccents = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return withoutAccents.lowercase(ptBr).trim()
    }
}
