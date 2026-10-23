package com.agendio.app.util

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import android.provider.ContactsContract
import com.agendio.app.data.Appointment
import com.agendio.app.data.AppointmentStatus
import com.agendio.app.data.Client
import com.agendio.app.data.DbHelper
import com.agendio.app.data.PaymentMethods
import com.agendio.app.data.Service
import com.agendio.app.notify.ReminderScheduler
import java.util.Calendar
import java.util.TimeZone

data class ImportResult(
    val inserted: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
    val updated: Int = 0
)

object ImportManager {

    fun importContacts(context: Context, db: DbHelper, defaultDdd: String): ImportResult {
        var inserted = 0
        var skipped = 0
        var updated = 0
        try {
            val emails = try {
                readContactEmails(context)
            } catch (_: Exception) {
                emptyMap()
            }
            val birthdays = try {
                readContactBirthdays(context)
            } catch (_: Exception) {
                emptyMap()
            }
            val photos = try {
                readContactPhotos(context)
            } catch (_: Exception) {
                emptyMap()
            }
            val existingByPhone = db.getClients()
                .mapNotNull { client ->
                    val key = phoneKey(client.phone)
                    if (key.isBlank()) null else key to client
                }
                .toMap()
                .toMutableMap()
            val seenPhones = existingByPhone.keys.toMutableSet()
            val seenContacts = HashSet<Long>()

            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                )
                val nameIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )
                val numberIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                while (cursor.moveToNext()) {
                    val contactId = cursor.getLong(idIndex)
                    if (!seenContacts.add(contactId)) {
                        skipped++
                        continue
                    }
                    val name = cursor.getString(nameIndex)?.trim().orEmpty()
                    val rawPhone = cursor.getString(numberIndex)?.trim().orEmpty()
                    if (name.isBlank() || rawPhone.isBlank()) {
                        skipped++
                        continue
                    }
                    val phone = withDefaultDdd(rawPhone, defaultDdd)
                    val key = phoneKey(phone)
                    if (key.isBlank()) {
                        skipped++
                        continue
                    }
                    val photoUri = photos[contactId].orEmpty()
                    val existing = existingByPhone[key]
                    if (existing != null || !seenPhones.add(key)) {
                        if (
                            existing != null &&
                            photoUri.isNotBlank() &&
                            existing.photoUri != photoUri
                        ) {
                            db.updateClient(existing.copy(photoUri = photoUri))
                            updated++
                        } else {
                            skipped++
                        }
                        continue
                    }
                    db.insertClient(
                        Client(
                            name = name,
                            phone = phone,
                            email = emails[contactId].orEmpty(),
                            birthday = birthdays[contactId].orEmpty(),
                            notes = "Importado dos contatos do celular",
                            photoUri = photoUri
                        )
                    )
                    inserted++
                }
            }
        } catch (exception: SecurityException) {
            return ImportResult(
                inserted = inserted,
                skipped = skipped,
                error = "Permissão para ler contatos não concedida.",
                updated = updated
            )
        } catch (exception: Exception) {
            return ImportResult(
                inserted = inserted,
                skipped = skipped,
                error = exception.message ?: "Falha ao importar contatos.",
                updated = updated
            )
        }
        return ImportResult(inserted = inserted, skipped = skipped, updated = updated)
    }

    fun importCalendar(
        context: Context,
        db: DbHelper,
        startMillis: Long,
        endMillis: Long
    ): ImportResult {
        var inserted = 0
        var skipped = 0
        var defaultService: Service? = null
        try {
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startMillis)
            ContentUris.appendId(builder, endMillis)
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
            )
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val eventIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)

                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(eventIdIndex)
                    val rawTitle = cursor.getString(titleIndex)?.trim().orEmpty()
                    val begin = cursor.getLong(beginIndex)
                    val instanceKey = calendarInstanceKey(eventId, begin)
                    if (
                        rawTitle.isBlank() || isBirthdayTitle(rawTitle) ||
                        db.hasCalendarEvent(instanceKey)
                    ) {
                        skipped++
                        continue
                    }
                    val allDay = cursor.getInt(allDayIndex) == 1
                    val end = cursor.getLong(endIndex)
                    val start = if (allDay) allDayAtNine(begin) else begin
                    val duration = if (allDay) {
                        60
                    } else {
                        ((end - begin) / 60000L).toInt().coerceIn(15, 480)
                    }
                    val clientName = rawTitle.take(100)
                    val client = db.findClientByName(clientName) ?: run {
                        val id = db.insertClient(
                            Client(
                                name = clientName,
                                notes = "Criado ao importar a agenda do celular"
                            )
                        )
                        db.getClient(id)
                    }
                    if (client == null) {
                        skipped++
                        continue
                    }
                    val service = defaultService ?: run {
                        val existing = db.findServiceByName(DEFAULT_SERVICE_NAME)
                        val created = existing ?: run {
                            val id = db.insertService(
                                Service(
                                    name = DEFAULT_SERVICE_NAME,
                                    durationMinutes = 60,
                                    price = 0.0
                                )
                            )
                            db.getService(id)
                        }
                        defaultService = created
                        created
                    }
                    if (service == null) {
                        skipped++
                        continue
                    }
                    val status = if (start < System.currentTimeMillis()) {
                        AppointmentStatus.COMPLETED
                    } else {
                        AppointmentStatus.SCHEDULED
                    }
                    val appointment = Appointment(
                        clientId = client.id,
                        serviceId = service.id,
                        startMillis = start,
                        durationMinutes = duration,
                        notes = "Importado da agenda do celular",
                        status = status,
                        paymentMethod = PaymentMethods.NONE,
                        price = 0.0,
                        calendarEventId = instanceKey
                    )
                    val id = db.insertAppointment(appointment)
                    if (id > 0) {
                        inserted++
                        if (status == AppointmentStatus.SCHEDULED) {
                            ReminderScheduler.schedule(context, appointment.copy(id = id))
                        }
                    } else {
                        skipped++
                    }
                }
            }
        } catch (exception: SecurityException) {
            return ImportResult(inserted, skipped, "Permissão para ler a agenda não concedida.")
        } catch (exception: Exception) {
            return ImportResult(inserted, skipped, exception.message ?: "Falha ao importar a agenda.")
        }
        return ImportResult(inserted, skipped)
    }

    private fun readContactEmails(context: Context): Map<Long, String> {
        val result = HashMap<Long, String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            ContactsContract.CommonDataKinds.Email.ADDRESS
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID
            )
            val emailIndex = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Email.ADDRESS
            )
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val email = cursor.getString(emailIndex)?.trim().orEmpty()
                if (id > 0 && email.isNotBlank() && !result.containsKey(id)) {
                    result[id] = email
                }
            }
        }
        return result
    }

    private fun readContactPhotos(context: Context): Map<Long, String> {
        val result = HashMap<Long, String>()
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
        )
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val photoIndex = cursor.getColumnIndexOrThrow(
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
            )
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val uri = cursor.getString(photoIndex)?.trim().orEmpty()
                if (id > 0 && uri.isNotBlank()) {
                    result[id] = uri
                }
            }
        }
        return result
    }

    private fun readContactBirthdays(context: Context): Map<Long, String> {
        val result = HashMap<Long, String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Event.CONTACT_ID,
            ContactsContract.CommonDataKinds.Event.START_DATE
        )
        val selection =
            "${ContactsContract.CommonDataKinds.Event.MIMETYPE} = ? AND " +
                "${ContactsContract.CommonDataKinds.Event.TYPE} = ?"
        val args = arrayOf(
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
        )
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Event.CONTACT_ID
            )
            val dateIndex = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Event.START_DATE
            )
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val normalized = normalizeBirthday(cursor.getString(dateIndex).orEmpty())
                if (id > 0 && normalized.isNotBlank() && !result.containsKey(id)) {
                    result[id] = normalized
                }
            }
        }
        return result
    }

    private fun normalizeBirthday(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return ""
        val yearFirst = Regex("""(\d{4})-(\d{2})-(\d{2})$""").find(value)
        if (yearFirst != null) {
            val (year, month, day) = yearFirst.destructured
            return "$day/$month/$year"
        }
        val noYear = Regex("""(?:--)?(\d{2})-(\d{2})$""").find(value)
        if (noYear != null) {
            val (month, day) = noYear.destructured
            return "$day/$month/2000"
        }
        return ""
    }

    private fun isBirthdayTitle(title: String): Boolean {
        val normalized = Formats.normalize(title)
        return normalized.contains("aniversario") || normalized.contains("birthday")
    }

    private fun calendarInstanceKey(eventId: Long, beginMillis: Long): Long {
        val minute = (beginMillis / 60000L) % 10_000_000L
        return eventId * 10_000_000L + minute
    }

    private fun allDayAtNine(beginMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = beginMillis
        }
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, utc.get(Calendar.YEAR))
            set(Calendar.MONTH, utc.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun withDefaultDdd(rawPhone: String, ddd: String): String {
        var digits = rawPhone.filter { it.isDigit() }
        if (digits.startsWith("0")) digits = digits.dropWhile { it == '0' }
        val cleanDdd = ddd.filter { it.isDigit() }.take(2)
        if (digits.length in 8..9 && cleanDdd.length == 2) {
            return "$cleanDdd$digits"
        }
        return digits
    }

    fun phoneKey(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.length >= 9 -> digits.takeLast(9)
            digits.length >= 8 -> digits.takeLast(8)
            else -> digits
        }
    }

    private const val DEFAULT_SERVICE_NAME = "Atendimento importado"
}
