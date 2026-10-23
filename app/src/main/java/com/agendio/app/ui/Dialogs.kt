package com.agendio.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.agendio.app.MainActivity
import com.agendio.app.R
import com.agendio.app.data.Appointment
import com.agendio.app.data.AppointmentStatus
import com.agendio.app.data.AppointmentView
import com.agendio.app.data.Client
import com.agendio.app.data.DbHelper
import com.agendio.app.data.Expense
import com.agendio.app.data.ExpenseCategories
import com.agendio.app.data.PaymentMethods
import com.agendio.app.data.Service
import com.agendio.app.notify.ReminderScheduler
import com.agendio.app.util.AppUpdater
import com.agendio.app.util.Formats
import com.agendio.app.util.PaymentFees
import com.agendio.app.util.SocialLinks
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar
import java.util.Locale

private data class RepeatOption(
    val label: String,
    val count: Int,
    val days: Int,
    val months: Int
)

private val repeatOptions = listOf(
    RepeatOption("Não repetir", 1, 0, 0),
    RepeatOption("Toda semana — 4 vezes", 4, 7, 0),
    RepeatOption("A cada 15 dias — 4 vezes", 4, 14, 0),
    RepeatOption("Todo mês — 4 vezes", 4, 0, 1),
    RepeatOption("Toda semana — 8 vezes", 8, 7, 0),
    RepeatOption("A cada 15 dias — 8 vezes", 8, 14, 0)
)

private fun parseMoney(value: String?): Double =
    value?.trim()?.replace(",", ".")?.toDoubleOrNull() ?: 0.0

private fun parseRate(value: String?): Double? {
    val normalized = value?.trim()?.replace(",", ".").orEmpty()
    if (normalized.isBlank()) return 0.0
    return normalized.toDoubleOrNull()
}

private fun formatRateInput(value: Double): String {
    if (value <= 0.0) return "0"
    return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}

private fun durationLabel(minutes: Int): String {
    val safeMinutes = minutes.coerceAtLeast(0)
    val hours = safeMinutes / 60
    val remaining = safeMinutes % 60
    return when {
        hours > 0 && remaining > 0 -> "$hours h $remaining min"
        hours > 0 -> "$hours h"
        else -> "$remaining min"
    }
}

private fun showClientHistory(context: Context, db: DbHelper, client: Client) {
    val appointments = db.getAppointmentsForClient(client.id)
    if (appointments.isEmpty()) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Histórico • ${client.name}")
            .setMessage("Este cliente ainda não tem agendamentos.")
            .setPositiveButton("Fechar", null)
            .show()
        return
    }
    val received = appointments.filter { it.paid }.sumOf { it.price }
    val lines = appointments.take(40).joinToString("\n\n") { item ->
        "${Formats.date(item.startMillis)} às ${Formats.time(item.startMillis)}\n" +
            "${item.serviceName} • ${AppointmentStatus.label(item.status)} • " +
            "${PaymentMethods.shortLabel(item.paymentMethod)} • ${Formats.money(item.price)}"
    }
    val extra = if (appointments.size > 40) {
        "\n\n+ ${appointments.size - 40} atendimento(s) anteriores"
    } else {
        ""
    }
    MaterialAlertDialogBuilder(context)
        .setTitle("Histórico • ${client.name}")
        .setMessage(
            "${appointments.size} atendimento(s) • ${Formats.money(received)} recebido\n\n$lines$extra"
        )
        .setPositiveButton("Fechar", null)
        .show()
}

fun showClientDialog(
    context: Context,
    db: DbHelper,
    client: Client? = null,
    onSaved: () -> Unit
) {
    val view = LayoutInflater.from(context).inflate(R.layout.dialog_client, null)
    val etName = view.findViewById<TextInputEditText>(R.id.etClientName)
    val etPhone = view.findViewById<TextInputEditText>(R.id.etClientPhone)
    val etEmail = view.findViewById<TextInputEditText>(R.id.etClientEmail)
    val etNotes = view.findViewById<TextInputEditText>(R.id.etClientNotes)
    val btnBirthday = view.findViewById<MaterialButton>(R.id.btnClientBirthday)
    val btnClearBirthday = view.findViewById<MaterialButton>(R.id.btnClearBirthday)
    val layoutActions = view.findViewById<View>(R.id.layoutClientActions)
    val btnHistory = view.findViewById<MaterialButton>(R.id.btnClientHistory)
    val btnWhatsApp = view.findViewById<MaterialButton>(R.id.btnClientWhatsApp)

    var birthday = client?.birthday.orEmpty()

    fun refreshBirthday() {
        btnBirthday.text = if (birthday.isBlank()) {
            "Selecionar aniversário"
        } else {
            "Aniversário: ${Formats.birthdayLabel(birthday)}"
        }
        btnClearBirthday.visibility = if (birthday.isBlank()) View.GONE else View.VISIBLE
    }

    client?.let {
        etName.setText(it.name)
        etPhone.setText(it.phone)
        etEmail.setText(it.email)
        etNotes.setText(it.notes)
    }
    refreshBirthday()

    if (client != null) {
        layoutActions.visibility = View.VISIBLE
        btnHistory.setOnClickListener {
            showClientHistory(context, db, client)
        }
        btnWhatsApp.setOnClickListener {
            val phone = etPhone.text?.toString()?.trim().orEmpty()
            if (phone.isBlank()) {
                Toast.makeText(context, R.string.whatsapp_no_phone, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val name = etName.text?.toString()?.trim().orEmpty().ifBlank { client.name }
            SocialLinks.openWhatsApp(
                context,
                phone,
                "Oi, ${name.substringBefore(" ")}! 💜 Aqui é a Tati Barros. Como posso ajudar?"
            )
        }
    }

    btnBirthday.setOnClickListener {
        val initial = db.parseBirthday(birthday) ?: Calendar.getInstance().apply {
            add(Calendar.YEAR, -30)
        }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                birthday = String.format(Locale.US, "%02d/%02d/%04d", day, month + 1, year)
                refreshBirthday()
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    btnClearBirthday.setOnClickListener {
        birthday = ""
        refreshBirthday()
    }

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(if (client == null) "Novo cliente" else "Editar cliente")
        .setView(view)
        .setPositiveButton("Salvar", null)
        .setNegativeButton("Cancelar", null)
        .setNeutralButton("Excluir", null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = etName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                etName.error = "Informe o nome"
                return@setOnClickListener
            }
            val email = etEmail.text?.toString()?.trim().orEmpty()
            if (email.isNotEmpty() && !email.contains("@")) {
                etEmail.error = "E-mail inválido"
                return@setOnClickListener
            }
            val phone = etPhone.text?.toString()?.trim().orEmpty()
            val notes = etNotes.text?.toString()?.trim().orEmpty()

            if (client == null) {
                db.insertClient(
                    Client(
                        name = name,
                        phone = phone,
                        email = email,
                        birthday = birthday,
                        notes = notes
                    )
                )
            } else {
                db.updateClient(
                    client.copy(
                        name = name,
                        phone = phone,
                        email = email,
                        birthday = birthday,
                        notes = notes
                    )
                )
            }
            onSaved()
            dialog.dismiss()
        }

        if (client == null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility = View.GONE
        } else {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                MaterialAlertDialogBuilder(context)
                    .setTitle("Excluir cliente?")
                    .setMessage("Os agendamentos deste cliente também serão removidos. Esta ação não pode ser desfeita.")
                    .setPositiveButton("Excluir") { _, _ ->
                        db.deleteClient(client.id)
                        onSaved()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    dialog.show()
}

fun showServiceDialog(
    context: Context,
    db: DbHelper,
    service: Service? = null,
    onSaved: () -> Unit
) {
    val view = LayoutInflater.from(context).inflate(R.layout.dialog_service, null)
    val etName = view.findViewById<TextInputEditText>(R.id.etServiceName)
    val etDuration = view.findViewById<TextInputEditText>(R.id.etServiceDuration)
    val etPrice = view.findViewById<TextInputEditText>(R.id.etServicePrice)
    val etCost = view.findViewById<TextInputEditText>(R.id.etServiceCost)
    val etCommission = view.findViewById<TextInputEditText>(R.id.etServiceCommission)

    etDuration.setText((service?.durationMinutes ?: 30).toString())
    etPrice.setText(String.format(Locale.US, "%.2f", service?.price ?: 0.0))
    etCost.setText(String.format(Locale.US, "%.2f", service?.cost ?: 0.0))
    etCommission.setText(
        String.format(Locale.US, "%.0f", service?.commissionPercent ?: 0.0)
    )
    service?.let { etName.setText(it.name) }

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(if (service == null) "Novo serviço" else "Editar serviço")
        .setView(view)
        .setPositiveButton("Salvar", null)
        .setNegativeButton("Cancelar", null)
        .setNeutralButton("Excluir", null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = etName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                etName.error = "Informe o nome"
                return@setOnClickListener
            }

            val duration = etDuration.text?.toString()?.trim()?.toIntOrNull()
            if (duration == null || duration <= 0) {
                etDuration.error = "Duração inválida"
                return@setOnClickListener
            }

            val price = parseMoney(etPrice.text?.toString())
            if (price < 0.0) {
                etPrice.error = "Preço inválido"
                return@setOnClickListener
            }

            val cost = parseMoney(etCost.text?.toString())
            if (cost < 0.0) {
                etCost.error = "Custo inválido"
                return@setOnClickListener
            }

            val commission = parseMoney(etCommission.text?.toString())
            if (commission < 0.0 || commission > 100.0) {
                etCommission.error = "Use um valor entre 0 e 100"
                return@setOnClickListener
            }

            if (service == null) {
                db.insertService(
                    Service(
                        name = name,
                        durationMinutes = duration,
                        price = price,
                        cost = cost,
                        commissionPercent = commission
                    )
                )
            } else {
                db.updateService(
                    service.copy(
                        name = name,
                        durationMinutes = duration,
                        price = price,
                        cost = cost,
                        commissionPercent = commission
                    )
                )
            }
            onSaved()
            dialog.dismiss()
        }

        if (service == null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility = View.GONE
        } else {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                MaterialAlertDialogBuilder(context)
                    .setTitle("Excluir serviço?")
                    .setMessage("Os agendamentos vinculados a este serviço também serão removidos. Esta ação não pode ser desfeita.")
                    .setPositiveButton("Excluir") { _, _ ->
                        db.deleteService(service.id)
                        onSaved()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    dialog.show()
}

fun showAppointmentDialog(
    context: Context,
    db: DbHelper,
    appointmentId: Long?,
    defaultDayStart: Long,
    exactStartMillis: Long? = null,
    onSaved: () -> Unit
) {
    val editing = appointmentId?.let { db.getAppointment(it) }
    if (appointmentId != null && editing == null) return

    val clients = db.getClients()
    val services = db.getServices()
    if (clients.isEmpty() || services.isEmpty()) {
        val missingClient = clients.isEmpty()
        val missingService = services.isEmpty()
        val message = when {
            missingClient && missingService ->
                "Para agendar, cadastre pelo menos um cliente na aba Clientes e um serviço na aba Serviços."

            missingClient ->
                "Para agendar, cadastre pelo menos um cliente na aba Clientes."

            else ->
                "Para agendar, cadastre pelo menos um serviço na aba Serviços."
        }
        val targetTab = if (missingClient) R.id.nav_clients else R.id.nav_services
        val buttonLabel = if (missingClient) "Abrir Clientes" else "Abrir Serviços"

        MaterialAlertDialogBuilder(context)
            .setTitle("Falta um cadastro")
            .setMessage(message)
            .setPositiveButton(buttonLabel) { _, _ ->
                (context as? MainActivity)?.openTab(targetTab)
            }
            .setNegativeButton("Agora não", null)
            .show()
        return
    }

    val view = LayoutInflater.from(context).inflate(R.layout.dialog_appointment, null)
    val spClient = view.findViewById<Spinner>(R.id.spClient)
    val spService = view.findViewById<Spinner>(R.id.spService)
    val spinnerStatus = view.findViewById<Spinner>(R.id.spStatus)
    val spinnerPayment = view.findViewById<Spinner>(R.id.spPayment)
    val btnDate = view.findViewById<MaterialButton>(R.id.btnDate)
    val btnTime = view.findViewById<MaterialButton>(R.id.btnTime)
    val btnEndTime = view.findViewById<MaterialButton>(R.id.btnEndTime)
    val tvDuration = view.findViewById<TextView>(R.id.tvAppointmentDuration)
    val etPrice = view.findViewById<TextInputEditText>(R.id.etAppointmentPrice)
    val etNotes = view.findViewById<TextInputEditText>(R.id.etNotes)
    val btnWhatsApp = view.findViewById<MaterialButton>(R.id.btnWhatsApp)
    val layoutRepeat = view.findViewById<View>(R.id.layoutRepeat)
    val spRepeat = view.findViewById<Spinner>(R.id.spRepeat)
    val layoutFee = view.findViewById<View>(R.id.layoutFee)
    val etFeePercent = view.findViewById<TextInputEditText>(R.id.etFeePercent)
    val feeRates = PaymentFees.load(context)

    val repeatAdapter = ArrayAdapter(
        context,
        android.R.layout.simple_spinner_item,
        repeatOptions.map { it.label }
    )
    repeatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spRepeat.adapter = repeatAdapter
    layoutRepeat.visibility = if (editing == null) View.VISIBLE else View.GONE

    val clientAdapter = ArrayAdapter(
        context,
        android.R.layout.simple_spinner_item,
        clients.map { it.name }
    )
    clientAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spClient.adapter = clientAdapter

    val serviceAdapter = ArrayAdapter(
        context,
        android.R.layout.simple_spinner_item,
        services.map { "${it.name} — ${Formats.money(it.price)}" }
    )
    serviceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spService.adapter = serviceAdapter

    val statusAdapter = ArrayAdapter(
        context,
        android.R.layout.simple_spinner_item,
        AppointmentStatus.all.map { AppointmentStatus.label(it) }
    )
    statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spinnerStatus.adapter = statusAdapter

    val paymentAdapter = ArrayAdapter(
        context,
        android.R.layout.simple_spinner_item,
        PaymentMethods.all.map { PaymentMethods.label(it) }
    )
    paymentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spinnerPayment.adapter = paymentAdapter

    var initializing = true
    editing?.let { appointment ->
        val clientIndex = clients.indexOfFirst { it.id == appointment.clientId }
        if (clientIndex >= 0) spClient.setSelection(clientIndex)
        val serviceIndex = services.indexOfFirst { it.id == appointment.serviceId }
        if (serviceIndex >= 0) spService.setSelection(serviceIndex)
        val statusIndex = AppointmentStatus.all.indexOf(appointment.status)
        if (statusIndex >= 0) spinnerStatus.setSelection(statusIndex)
        val paymentIndex = PaymentMethods.all.indexOf(appointment.paymentMethod)
        if (paymentIndex >= 0) spinnerPayment.setSelection(paymentIndex)
        etNotes.setText(appointment.notes)
        etPrice.setText(String.format(Locale.US, "%.2f", appointment.price))
        etFeePercent.setText(formatRateInput(appointment.feePercent))
    }
    if (editing == null) {
        val first = services.firstOrNull()
        etPrice.setText(String.format(Locale.US, "%.2f", first?.price ?: 0.0))
        etFeePercent.setText("0")
    }
    initializing = false

    var lastFeeMethod = editing?.paymentMethod ?: PaymentMethods.NONE

    fun selectedPaymentMethod(): String =
        PaymentMethods.all.getOrNull(spinnerPayment.selectedItemPosition) ?: PaymentMethods.NONE

    fun refreshFeeVisibility(resetToDefault: Boolean) {
        val method = selectedPaymentMethod()
        if (!PaymentMethods.chargesFee(method)) {
            layoutFee.visibility = View.GONE
            etFeePercent.setText("0")
            return
        }
        layoutFee.visibility = View.VISIBLE
        if (resetToDefault) {
            etFeePercent.setText(formatRateInput(feeRates.percentFor(method)))
        }
    }

    refreshFeeVisibility(false)

    spinnerPayment.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: android.widget.AdapterView<*>?,
            selectedView: View?,
            position: Int,
            id: Long
        ) {
            if (initializing) return
            val method = PaymentMethods.all.getOrNull(position) ?: PaymentMethods.NONE
            if (method == lastFeeMethod) return
            lastFeeMethod = method
            refreshFeeVisibility(true)
        }

        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }

    val selectedService = if (editing != null) {
        services.firstOrNull { it.id == editing.serviceId }
    } else {
        services.firstOrNull()
    }
    val initialDuration = editing?.durationMinutes?.takeIf { it > 0 }
        ?: selectedService?.durationMinutes?.coerceAtLeast(1)
        ?: 30

    val calendar = Calendar.getInstance().apply {
        timeInMillis = editing?.startMillis
            ?: exactStartMillis
            ?: Formats.defaultStart(defaultDayStart)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val endCalendar = Calendar.getInstance().apply {
        timeInMillis = calendar.timeInMillis
        add(Calendar.MINUTE, initialDuration)
    }

    fun currentDurationMinutes(): Int =
        ((endCalendar.timeInMillis - calendar.timeInMillis) / 60_000L).toInt().coerceAtLeast(0)

    fun refreshDateTimeButtons() {
        val duration = currentDurationMinutes()
        val overnight = Formats.dayStart(endCalendar.timeInMillis) !=
            Formats.dayStart(calendar.timeInMillis)
        btnDate.text = "Data: ${Formats.date(calendar.timeInMillis)}"
        btnTime.text = "Início: ${Formats.time(calendar.timeInMillis)}"
        btnEndTime.text = "Término: ${Formats.time(endCalendar.timeInMillis)}"
        tvDuration.text = buildString {
            append("Duração: ")
            append(durationLabel(duration))
            if (overnight) append(" • termina no dia seguinte")
        }
    }
    refreshDateTimeButtons()

    btnDate.setOnClickListener {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val duration = currentDurationMinutes().takeIf { it > 0 } ?: initialDuration
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                endCalendar.timeInMillis = calendar.timeInMillis
                endCalendar.add(Calendar.MINUTE, duration)
                refreshDateTimeButtons()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    btnTime.setOnClickListener {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val duration = currentDurationMinutes().takeIf { it > 0 } ?: initialDuration
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                endCalendar.timeInMillis = calendar.timeInMillis
                endCalendar.add(Calendar.MINUTE, duration)
                refreshDateTimeButtons()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    btnEndTime.setOnClickListener {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val candidate = Calendar.getInstance().apply {
                    timeInMillis = calendar.timeInMillis
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (candidate.timeInMillis <= calendar.timeInMillis) {
                    Toast.makeText(
                        context,
                        "O término deve ser depois do início.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    endCalendar.timeInMillis = candidate.timeInMillis
                    refreshDateTimeButtons()
                }
            },
            endCalendar.get(Calendar.HOUR_OF_DAY),
            endCalendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    var lastServicePosition = spService.selectedItemPosition
    spService.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: android.widget.AdapterView<*>?,
            selectedView: View?,
            position: Int,
            id: Long
        ) {
            if (initializing || position == lastServicePosition) return
            lastServicePosition = position
            val selected = services.getOrNull(position) ?: return
            etPrice.setText(String.format(Locale.US, "%.2f", selected.price))
            endCalendar.timeInMillis = calendar.timeInMillis
            endCalendar.add(Calendar.MINUTE, selected.durationMinutes.coerceAtLeast(1))
            refreshDateTimeButtons()
        }

        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
    }

    if (editing != null) {
        btnWhatsApp.visibility = View.VISIBLE
        btnWhatsApp.setOnClickListener {
            val client = clients.getOrNull(spClient.selectedItemPosition) ?: return@setOnClickListener
            val service = services.getOrNull(spService.selectedItemPosition) ?: return@setOnClickListener
            val status = AppointmentStatus.all[spinnerStatus.selectedItemPosition]
            if (!AppointmentStatus.isActive(status) || calendar.timeInMillis < System.currentTimeMillis()) {
                Toast.makeText(
                    context,
                    "A confirmação está disponível apenas para horários futuros e ativos.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            val preview = AppointmentView(
                id = editing.id,
                clientId = client.id,
                clientName = client.name,
                clientPhone = client.phone,
                clientEmail = client.email,
                serviceId = service.id,
                serviceName = service.name,
                startMillis = calendar.timeInMillis,
                notes = etNotes.text?.toString()?.trim().orEmpty(),
                status = AppointmentStatus.all[spinnerStatus.selectedItemPosition],
                paymentMethod = PaymentMethods.all[spinnerPayment.selectedItemPosition],
                price = parseMoney(etPrice.text?.toString()),
                feePercent = parseRate(etFeePercent.text?.toString()) ?: 0.0,
                calendarEventId = editing.calendarEventId,
                durationMinutes = currentDurationMinutes().coerceAtLeast(1)
            )
            SocialLinks.openWhatsAppConfirmation(context, preview)
        }
    }

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(if (editing == null) "Novo agendamento" else "Editar agendamento")
        .setView(view)
        .setPositiveButton("Salvar", null)
        .setNegativeButton("Cancelar", null)
        .setNeutralButton("Excluir", null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val client = clients.getOrNull(spClient.selectedItemPosition) ?: return@setOnClickListener
            val service = services.getOrNull(spService.selectedItemPosition) ?: return@setOnClickListener
            val price = parseMoney(etPrice.text?.toString())
            if (price < 0.0) {
                etPrice.error = "Valor inválido"
                return@setOnClickListener
            }

            val duration = currentDurationMinutes()
            if (duration <= 0) {
                Toast.makeText(
                    context,
                    "O término deve ser depois do início.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (editing == null && calendar.timeInMillis <= System.currentTimeMillis()) {
                Toast.makeText(context, "Escolha um horário futuro.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val status = AppointmentStatus.all[spinnerStatus.selectedItemPosition]
            val payment = PaymentMethods.all[spinnerPayment.selectedItemPosition]
            val feePercent = if (PaymentMethods.chargesFee(payment)) {
                val parsed = parseRate(etFeePercent.text?.toString())
                if (parsed == null || parsed < 0.0 || parsed > 100.0) {
                    etFeePercent.error = "Use um valor entre 0 e 100"
                    return@setOnClickListener
                }
                parsed
            } else {
                0.0
            }
            val appointment = Appointment(
                id = editing?.id ?: 0L,
                clientId = client.id,
                serviceId = service.id,
                startMillis = calendar.timeInMillis,
                durationMinutes = duration,
                notes = etNotes.text?.toString()?.trim().orEmpty(),
                status = status,
                paymentMethod = payment,
                price = price,
                feePercent = feePercent,
                calendarEventId = editing?.calendarEventId ?: 0L
            )

            if (editing == null) {
                val newId = db.insertAppointment(appointment)
                if (newId <= 0L) {
                    Toast.makeText(context, "Não foi possível salvar.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (AppointmentStatus.isActive(status)) {
                    ReminderScheduler.schedule(context, appointment.copy(id = newId))
                }

                val repeat = repeatOptions.getOrNull(spRepeat.selectedItemPosition)
                    ?: repeatOptions.first()
                var created = 1
                if (repeat.count > 1) {
                    for (index in 1 until repeat.count) {
                        val nextStart = if (repeat.months > 0) {
                            Formats.addMonthsSameDay(
                                calendar.timeInMillis,
                                repeat.months * index
                            )
                        } else {
                            Formats.addDaysSameTime(
                                calendar.timeInMillis,
                                repeat.days * index
                            )
                        }
                        val repeated = appointment.copy(id = 0L, startMillis = nextStart)
                        val repeatedId = db.insertAppointment(repeated)
                        if (repeatedId > 0L) {
                            created++
                            if (AppointmentStatus.isActive(status)) {
                                ReminderScheduler.schedule(
                                    context,
                                    repeated.copy(id = repeatedId)
                                )
                            }
                        }
                    }
                }
                if (created > 1) {
                    Toast.makeText(
                        context,
                        "$created agendamentos criados.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                db.updateAppointment(appointment)
                ReminderScheduler.cancel(context, appointment.id)
                if (AppointmentStatus.isActive(status)) {
                    ReminderScheduler.schedule(context, appointment)
                }
            }

            onSaved()
            dialog.dismiss()
        }

        if (editing == null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility = View.GONE
        } else {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                MaterialAlertDialogBuilder(context)
                    .setTitle("Excluir agendamento?")
                    .setMessage("Esta ação não pode ser desfeita.")
                    .setPositiveButton("Excluir") { _, _ ->
                        db.deleteAppointment(editing.id)
                        ReminderScheduler.cancel(context, editing.id)
                        onSaved()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    dialog.show()
}

fun showExpenseDialog(
    context: Context,
    db: DbHelper,
    expense: Expense? = null,
    defaultDateMillis: Long = System.currentTimeMillis(),
    onSaved: () -> Unit
) {
    val view = LayoutInflater.from(context).inflate(R.layout.dialog_expense, null)
    val etDescription = view.findViewById<TextInputEditText>(R.id.etExpenseDescription)
    val etAmount = view.findViewById<TextInputEditText>(R.id.etExpenseAmount)
    val spCategory = view.findViewById<Spinner>(R.id.spExpenseCategory)
    val btnDate = view.findViewById<MaterialButton>(R.id.btnExpenseDate)

    val categories = ExpenseCategories.all
    val categoryAdapter = ArrayAdapter(
        context,
        android.R.layout.simple_spinner_item,
        categories
    )
    categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    spCategory.adapter = categoryAdapter

    val calendar = Calendar.getInstance().apply {
        timeInMillis = expense?.dateMillis ?: defaultDateMillis
    }

    fun refreshDate() {
        btnDate.text = Formats.date(calendar.timeInMillis)
    }

    expense?.let {
        etDescription.setText(it.description)
        etAmount.setText(String.format(Locale.US, "%.2f", it.amount))
        val index = categories.indexOf(it.category)
        if (index >= 0) spCategory.setSelection(index)
    }
    refreshDate()

    btnDate.setOnClickListener {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                refreshDate()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle(if (expense == null) "Nova despesa" else "Editar despesa")
        .setView(view)
        .setPositiveButton("Salvar", null)
        .setNegativeButton("Cancelar", null)
        .setNeutralButton("Excluir", null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val description = etDescription.text?.toString()?.trim().orEmpty()
            if (description.isEmpty()) {
                etDescription.error = "Informe a descrição"
                return@setOnClickListener
            }
            val amount = parseMoney(etAmount.text?.toString())
            if (amount <= 0.0) {
                etAmount.error = "Informe um valor maior que zero"
                return@setOnClickListener
            }
            val category = categories.getOrNull(spCategory.selectedItemPosition)
                ?: ExpenseCategories.OTHER
            val date = calendar.timeInMillis

            if (expense == null) {
                db.insertExpense(
                    Expense(
                        description = description,
                        amount = amount,
                        dateMillis = date,
                        category = category
                    )
                )
            } else {
                db.updateExpense(
                    expense.copy(
                        description = description,
                        amount = amount,
                        dateMillis = date,
                        category = category
                    )
                )
            }
            onSaved()
            dialog.dismiss()
        }

        if (expense == null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility = View.GONE
        } else {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                MaterialAlertDialogBuilder(context)
                    .setTitle("Excluir despesa?")
                    .setMessage("Esta ação não pode ser desfeita.")
                    .setPositiveButton("Excluir") { _, _ ->
                        db.deleteExpense(expense.id)
                        onSaved()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    dialog.show()
}

fun showPaymentFeesDialog(context: Context, onSaved: () -> Unit) {
    val view = LayoutInflater.from(context).inflate(R.layout.dialog_payment_fees, null)
    val etCredit = view.findViewById<TextInputEditText>(R.id.etCreditFee)
    val etDebit = view.findViewById<TextInputEditText>(R.id.etDebitFee)
    val etPix = view.findViewById<TextInputEditText>(R.id.etPixFee)
    val rates = PaymentFees.load(context)

    etCredit.setText(formatRateInput(rates.credit))
    etDebit.setText(formatRateInput(rates.debit))
    etPix.setText(formatRateInput(rates.pix))

    val dialog = MaterialAlertDialogBuilder(context)
        .setTitle("Taxas de pagamento")
        .setView(view)
        .setPositiveButton("Salvar", null)
        .setNegativeButton("Cancelar", null)
        .create()

    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val credit = parseRate(etCredit.text?.toString())
            val debit = parseRate(etDebit.text?.toString())
            val pix = parseRate(etPix.text?.toString())

            if (credit == null || credit < 0.0 || credit > 100.0) {
                etCredit.error = "Use um valor entre 0 e 100"
                return@setOnClickListener
            }
            if (debit == null || debit < 0.0 || debit > 100.0) {
                etDebit.error = "Use um valor entre 0 e 100"
                return@setOnClickListener
            }
            if (pix == null || pix < 0.0 || pix > 100.0) {
                etPix.error = "Use um valor entre 0 e 100"
                return@setOnClickListener
            }

            PaymentFees.save(
                context,
                PaymentFees.Rates(credit = credit, debit = debit, pix = pix)
            )
            onSaved()
            dialog.dismiss()
        }
    }

    dialog.show()
}

fun showUpdateDialog(context: Context) {
    Toast.makeText(context, "Verificando atualização...", Toast.LENGTH_SHORT).show()
    AppUpdater.checkForUpdate(context) { info, error ->
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            return@checkForUpdate
        }
        if (info == null) {
            Toast.makeText(context, "Você já está na versão mais recente.", Toast.LENGTH_SHORT).show()
            return@checkForUpdate
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("Atualização disponível")
            .setMessage(
                "Nova versão: ${info.versionName} (código ${info.versionCode})\n\n" +
                    "Deseja baixar e instalar agora?"
            )
            .setPositiveButton("Baixar e instalar") { _, _ ->
                Toast.makeText(context, "Baixando atualização...", Toast.LENGTH_SHORT).show()
                AppUpdater.downloadAndInstall(context, info) { status ->
                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Agora não", null)
            .show()
    }
}

fun showAboutDialog(context: Context) {
    val view = LayoutInflater.from(context).inflate(R.layout.dialog_about, null)
    val version = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (_: PackageManager.NameNotFoundException) {
        "2.0"
    }
    view.findViewById<TextView>(R.id.tvAboutVersion).text =
        "Versão $version • Android • dados locais"
    view.findViewById<View>(R.id.btnAboutInstagram).setOnClickListener {
        SocialLinks.openInstagram(context)
    }
    view.findViewById<MaterialButton>(R.id.btnCheckUpdate).setOnClickListener {
        showUpdateDialog(context)
    }
    MaterialAlertDialogBuilder(context)
        .setTitle("Sobre o app")
        .setView(view)
        .setPositiveButton("Fechar", null)
        .show()
}
