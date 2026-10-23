package com.agendio.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "agendio.db"
        private const val DB_VERSION = 5
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE clients (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT NOT NULL DEFAULT '',
                email TEXT NOT NULL DEFAULT '',
                birthday TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                photo_uri TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE services (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                duration_minutes INTEGER NOT NULL DEFAULT 30,
                price REAL NOT NULL DEFAULT 0,
                cost REAL NOT NULL DEFAULT 0,
                commission REAL NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE appointments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                client_id INTEGER NOT NULL,
                service_id INTEGER NOT NULL,
                start_millis INTEGER NOT NULL,
                duration_minutes INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
                paid INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'scheduled',
                payment_method TEXT NOT NULL DEFAULT 'none',
                price REAL NOT NULL DEFAULT 0,
                fee_percent REAL NOT NULL DEFAULT 0,
                calendar_event_id INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(client_id) REFERENCES clients(id) ON DELETE CASCADE,
                FOREIGN KEY(service_id) REFERENCES services(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                description TEXT NOT NULL,
                amount REAL NOT NULL DEFAULT 0,
                date_millis INTEGER NOT NULL,
                category TEXT NOT NULL DEFAULT 'Outros'
            )
            """.trimIndent()
        )
        createIndexes(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Migração sem perder nenhum dado da versão 1.x.
            db.execSQL("ALTER TABLE clients ADD COLUMN email TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE clients ADD COLUMN birthday TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE services ADD COLUMN cost REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE services ADD COLUMN commission REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE appointments ADD COLUMN status TEXT NOT NULL DEFAULT 'scheduled'")
            db.execSQL("ALTER TABLE appointments ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'none'")
            db.execSQL("ALTER TABLE appointments ADD COLUMN price REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE appointments ADD COLUMN calendar_event_id INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                UPDATE appointments
                SET price = COALESCE(
                    (SELECT price FROM services WHERE services.id = appointments.service_id),
                    0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                UPDATE appointments
                SET status = 'completed', payment_method = 'cash'
                WHERE paid = 1
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS expenses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    description TEXT NOT NULL,
                    amount REAL NOT NULL DEFAULT 0,
                    date_millis INTEGER NOT NULL,
                    category TEXT NOT NULL DEFAULT 'Outros'
                )
                """.trimIndent()
            )
            createIndexes(db)
        }
        if (oldVersion < 3) {
            // Guarda a taxa usada no atendimento para não alterar relatórios antigos.
            db.execSQL("ALTER TABLE appointments ADD COLUMN fee_percent REAL NOT NULL DEFAULT 0")
        }
        if (oldVersion < 4) {
            // Duração personalizada; 0 usa a duração do serviço (atendimentos antigos).
            db.execSQL("ALTER TABLE appointments ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 5) {
            // Foto do contato salva no aparelho. Vazio mostra o monograma.
            db.execSQL("ALTER TABLE clients ADD COLUMN photo_uri TEXT NOT NULL DEFAULT ''")
        }
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_appointments_start ON appointments(start_millis)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_expenses_date ON expenses(date_millis)")
    }

    // ---------- Clientes ----------

    fun getClients(): List<Client> {
        readableDatabase.query(
            "clients", null, null, null, null, null, "name COLLATE NOCASE"
        ).use { cursor ->
            val result = mutableListOf<Client>()
            while (cursor.moveToNext()) {
                result.add(readClient(cursor))
            }
            return result
        }
    }

    fun getClient(id: Long): Client? {
        readableDatabase.query(
            "clients", null, "id = ?", arrayOf(id.toString()), null, null, null
        ).use { cursor ->
            return if (cursor.moveToFirst()) readClient(cursor) else null
        }
    }

    fun findClientByName(name: String): Client? {
        readableDatabase.query(
            "clients",
            null,
            "name = ? COLLATE NOCASE",
            arrayOf(name.trim()),
            null,
            null,
            "id ASC"
        ).use { cursor ->
            return if (cursor.moveToFirst()) readClient(cursor) else null
        }
    }

    fun insertClient(client: Client): Long {
        val values = ContentValues().apply {
            put("name", client.name)
            put("phone", client.phone)
            put("email", client.email)
            put("birthday", client.birthday)
            put("notes", client.notes)
            put("photo_uri", client.photoUri)
        }
        return writableDatabase.insert("clients", null, values)
    }

    fun updateClient(client: Client) {
        val values = ContentValues().apply {
            put("name", client.name)
            put("phone", client.phone)
            put("email", client.email)
            put("birthday", client.birthday)
            put("notes", client.notes)
            put("photo_uri", client.photoUri)
        }
        writableDatabase.update("clients", values, "id = ?", arrayOf(client.id.toString()))
    }

    fun deleteClient(id: Long) {
        writableDatabase.delete("clients", "id = ?", arrayOf(id.toString()))
    }

    fun getBirthdaysForMonth(month: Int): List<Client> {
        return getClients().filter { birthdayMonth(it.birthday) == month }
            .sortedBy { birthdayDay(it.birthday) }
    }

    private fun birthdayMonth(value: String): Int {
        val calendar = parseBirthday(value) ?: return -1
        return calendar.get(Calendar.MONTH)
    }

    private fun birthdayDay(value: String): Int {
        val calendar = parseBirthday(value) ?: return 99
        return calendar.get(Calendar.DAY_OF_MONTH)
    }

    fun parseBirthday(value: String): Calendar? {
        if (value.isBlank()) return null
        val patterns = listOf("dd/MM/yyyy", "yyyy-MM-dd", "dd/MM")
        for (pattern in patterns) {
            try {
                val formatter = SimpleDateFormat(pattern, Locale("pt", "BR"))
                formatter.isLenient = false
                val date = formatter.parse(value) ?: continue
                return Calendar.getInstance().apply { time = date }
            } catch (_: Exception) {
                // tenta o próximo formato
            }
        }
        return null
    }

    private fun readClient(cursor: Cursor): Client {
        return Client(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            phone = cursor.getString(cursor.getColumnIndexOrThrow("phone")),
            email = cursor.getString(cursor.getColumnIndexOrThrow("email")),
            birthday = cursor.getString(cursor.getColumnIndexOrThrow("birthday")),
            notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")),
            photoUri = cursor.getString(cursor.getColumnIndexOrThrow("photo_uri"))
        )
    }

    // ---------- Serviços ----------

    fun getServices(): List<Service> {
        readableDatabase.query(
            "services", null, null, null, null, null, "name COLLATE NOCASE"
        ).use { cursor ->
            val result = mutableListOf<Service>()
            while (cursor.moveToNext()) {
                result.add(readService(cursor))
            }
            return result
        }
    }

    fun getService(id: Long): Service? {
        readableDatabase.query(
            "services", null, "id = ?", arrayOf(id.toString()), null, null, null
        ).use { cursor ->
            return if (cursor.moveToFirst()) readService(cursor) else null
        }
    }

    fun findServiceByName(name: String): Service? {
        readableDatabase.query(
            "services",
            null,
            "name = ? COLLATE NOCASE",
            arrayOf(name.trim()),
            null,
            null,
            "id ASC"
        ).use { cursor ->
            return if (cursor.moveToFirst()) readService(cursor) else null
        }
    }

    fun insertService(service: Service): Long {
        val values = ContentValues().apply {
            put("name", service.name)
            put("duration_minutes", service.durationMinutes)
            put("price", service.price)
            put("cost", service.cost)
            put("commission", service.commissionPercent)
        }
        return writableDatabase.insert("services", null, values)
    }

    fun updateService(service: Service) {
        val values = ContentValues().apply {
            put("name", service.name)
            put("duration_minutes", service.durationMinutes)
            put("price", service.price)
            put("cost", service.cost)
            put("commission", service.commissionPercent)
        }
        writableDatabase.update("services", values, "id = ?", arrayOf(service.id.toString()))
    }

    fun deleteService(id: Long) {
        writableDatabase.delete("services", "id = ?", arrayOf(id.toString()))
    }

    private fun readService(cursor: Cursor): Service {
        return Service(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            durationMinutes = cursor.getInt(cursor.getColumnIndexOrThrow("duration_minutes")),
            price = cursor.getDouble(cursor.getColumnIndexOrThrow("price")),
            cost = cursor.getDouble(cursor.getColumnIndexOrThrow("cost")),
            commissionPercent = cursor.getDouble(cursor.getColumnIndexOrThrow("commission"))
        )
    }

    // ---------- Agendamentos ----------

    fun getAppointmentsForDay(dayStart: Long, dayEnd: Long): List<AppointmentView> {
        return queryAppointments(
            "a.start_millis >= ? AND a.start_millis < ?",
            arrayOf(dayStart.toString(), dayEnd.toString()),
            "a.start_millis ASC"
        )
    }

    fun getAppointmentsForRange(startMillis: Long, endMillis: Long): List<AppointmentView> {
        return queryAppointments(
            "a.start_millis >= ? AND a.start_millis < ?",
            arrayOf(startMillis.toString(), endMillis.toString()),
            "a.start_millis ASC"
        )
    }

    fun getAllAppointments(): List<AppointmentView> {
        return queryAppointments(null, null, "a.start_millis DESC")
    }

    fun getAppointmentsForClient(clientId: Long): List<AppointmentView> {
        return queryAppointments(
            "a.client_id = ?",
            arrayOf(clientId.toString()),
            "a.start_millis DESC"
        )
    }

    fun getAppointmentView(id: Long): AppointmentView? {
        return queryAppointments("a.id = ?", arrayOf(id.toString()), "a.start_millis ASC").firstOrNull()
    }

    fun getNextAppointment(now: Long): AppointmentView? {
        return queryAppointments(
            "a.start_millis >= ? AND a.status IN ('scheduled', 'confirmed')",
            arrayOf(now.toString()),
            "a.start_millis ASC",
            limit = 1
        ).firstOrNull()
    }

    fun getAppointment(id: Long): Appointment? {
        readableDatabase.query(
            "appointments", null, "id = ?", arrayOf(id.toString()), null, null, null
        ).use { cursor ->
            return if (cursor.moveToFirst()) readAppointment(cursor) else null
        }
    }

    fun getUpcomingAppointments(now: Long): List<Appointment> {
        readableDatabase.query(
            "appointments",
            null,
            "start_millis > ? AND status IN ('scheduled', 'confirmed')",
            arrayOf(now.toString()),
            null,
            null,
            "start_millis ASC"
        ).use { cursor ->
            val result = mutableListOf<Appointment>()
            while (cursor.moveToNext()) {
                result.add(readAppointment(cursor))
            }
            return result
        }
    }

    fun hasCalendarEvent(calendarEventId: Long): Boolean {
        if (calendarEventId <= 0L) return false
        readableDatabase.query(
            "appointments",
            arrayOf("id"),
            "calendar_event_id = ?",
            arrayOf(calendarEventId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun insertAppointment(appointment: Appointment): Long {
        val values = ContentValues().apply {
            put("client_id", appointment.clientId)
            put("service_id", appointment.serviceId)
            put("start_millis", appointment.startMillis)
            put("duration_minutes", appointment.durationMinutes)
            put("notes", appointment.notes)
            put("paid", if (appointment.paid) 1 else 0)
            put("status", appointment.status)
            put("payment_method", appointment.paymentMethod)
            put("price", appointment.price)
            put("fee_percent", appointment.feePercent)
            put("calendar_event_id", appointment.calendarEventId)
        }
        return writableDatabase.insert("appointments", null, values)
    }

    fun updateAppointment(appointment: Appointment) {
        val values = ContentValues().apply {
            put("client_id", appointment.clientId)
            put("service_id", appointment.serviceId)
            put("start_millis", appointment.startMillis)
            put("duration_minutes", appointment.durationMinutes)
            put("notes", appointment.notes)
            put("paid", if (appointment.paid) 1 else 0)
            put("status", appointment.status)
            put("payment_method", appointment.paymentMethod)
            put("price", appointment.price)
            put("fee_percent", appointment.feePercent)
            put("calendar_event_id", appointment.calendarEventId)
        }
        writableDatabase.update(
            "appointments", values, "id = ?", arrayOf(appointment.id.toString())
        )
    }

    fun deleteAppointment(id: Long) {
        writableDatabase.delete("appointments", "id = ?", arrayOf(id.toString()))
    }

    private fun readAppointment(cursor: Cursor): Appointment {
        return Appointment(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            clientId = cursor.getLong(cursor.getColumnIndexOrThrow("client_id")),
            serviceId = cursor.getLong(cursor.getColumnIndexOrThrow("service_id")),
            startMillis = cursor.getLong(cursor.getColumnIndexOrThrow("start_millis")),
            durationMinutes = cursor.getInt(cursor.getColumnIndexOrThrow("duration_minutes")),
            notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")),
            status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
            paymentMethod = cursor.getString(cursor.getColumnIndexOrThrow("payment_method")),
            price = cursor.getDouble(cursor.getColumnIndexOrThrow("price")),
            feePercent = cursor.getDouble(cursor.getColumnIndexOrThrow("fee_percent")),
            calendarEventId = cursor.getLong(cursor.getColumnIndexOrThrow("calendar_event_id"))
        )
    }

    private fun queryAppointments(
        selection: String?,
        selectionArgs: Array<String>?,
        orderBy: String,
        limit: Int? = null
    ): List<AppointmentView> {
        val baseSql = """
            SELECT a.id, a.client_id, c.name AS client_name, c.phone AS client_phone,
                   c.email AS client_email, a.service_id, s.name AS service_name,
                   a.start_millis, a.notes, a.status, a.payment_method,
                   a.price, a.fee_percent, a.calendar_event_id,
                   COALESCE(NULLIF(a.duration_minutes, 0), s.duration_minutes) AS duration_minutes,
                   s.cost AS service_cost, s.commission AS commission_percent
            FROM appointments a
            INNER JOIN clients c ON c.id = a.client_id
            INNER JOIN services s ON s.id = a.service_id
        """.trimIndent()
        val sql = buildString {
            append(baseSql)
            if (!selection.isNullOrBlank()) append(" WHERE ").append(selection)
            append(" ORDER BY ").append(orderBy)
            if (limit != null) append(" LIMIT ").append(limit)
        }
        readableDatabase.rawQuery(sql, selectionArgs).use { cursor ->
            val result = mutableListOf<AppointmentView>()
            while (cursor.moveToNext()) {
                result.add(
                    AppointmentView(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        clientId = cursor.getLong(cursor.getColumnIndexOrThrow("client_id")),
                        clientName = cursor.getString(cursor.getColumnIndexOrThrow("client_name")),
                        clientPhone = cursor.getString(cursor.getColumnIndexOrThrow("client_phone")),
                        clientEmail = cursor.getString(cursor.getColumnIndexOrThrow("client_email")),
                        serviceId = cursor.getLong(cursor.getColumnIndexOrThrow("service_id")),
                        serviceName = cursor.getString(cursor.getColumnIndexOrThrow("service_name")),
                        startMillis = cursor.getLong(cursor.getColumnIndexOrThrow("start_millis")),
                        notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")),
                        status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
                        paymentMethod = cursor.getString(cursor.getColumnIndexOrThrow("payment_method")),
                        price = cursor.getDouble(cursor.getColumnIndexOrThrow("price")),
                        feePercent = cursor.getDouble(cursor.getColumnIndexOrThrow("fee_percent")),
                        calendarEventId = cursor.getLong(cursor.getColumnIndexOrThrow("calendar_event_id")),
                        durationMinutes = cursor.getInt(cursor.getColumnIndexOrThrow("duration_minutes")),
                        serviceCost = cursor.getDouble(cursor.getColumnIndexOrThrow("service_cost")),
                        commissionPercent = cursor.getDouble(cursor.getColumnIndexOrThrow("commission_percent"))
                    )
                )
            }
            return result
        }
    }

    // ---------- Despesas ----------

    fun getExpensesForRange(startMillis: Long, endMillis: Long): List<Expense> {
        readableDatabase.query(
            "expenses",
            null,
            "date_millis >= ? AND date_millis < ?",
            arrayOf(startMillis.toString(), endMillis.toString()),
            null,
            null,
            "date_millis DESC, id DESC"
        ).use { cursor ->
            val result = mutableListOf<Expense>()
            while (cursor.moveToNext()) {
                result.add(readExpense(cursor))
            }
            return result
        }
    }

    fun insertExpense(expense: Expense): Long {
        val values = ContentValues().apply {
            put("description", expense.description)
            put("amount", expense.amount)
            put("date_millis", expense.dateMillis)
            put("category", expense.category)
        }
        return writableDatabase.insert("expenses", null, values)
    }

    fun updateExpense(expense: Expense) {
        val values = ContentValues().apply {
            put("description", expense.description)
            put("amount", expense.amount)
            put("date_millis", expense.dateMillis)
            put("category", expense.category)
        }
        writableDatabase.update("expenses", values, "id = ?", arrayOf(expense.id.toString()))
    }

    fun deleteExpense(id: Long) {
        writableDatabase.delete("expenses", "id = ?", arrayOf(id.toString()))
    }

    private fun readExpense(cursor: Cursor): Expense {
        return Expense(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
            amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount")),
            dateMillis = cursor.getLong(cursor.getColumnIndexOrThrow("date_millis")),
            category = cursor.getString(cursor.getColumnIndexOrThrow("category"))
        )
    }
}
