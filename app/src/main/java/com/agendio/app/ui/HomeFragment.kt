package com.agendio.app.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.agendio.app.MainActivity
import com.agendio.app.R
import com.agendio.app.data.AppointmentStatus
import com.agendio.app.data.DbHelper
import com.agendio.app.util.Formats
import com.agendio.app.util.PaymentFees
import com.agendio.app.util.SocialLinks
import com.google.android.material.button.MaterialButton
import java.util.Calendar

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var db: DbHelper
    private lateinit var tvGreeting: TextView
    private lateinit var tvTodayCount: TextView
    private lateinit var tvWeekCount: TextView
    private lateinit var tvHomeReceived: TextView
    private lateinit var tvHomePending: TextView
    private lateinit var tvNextDetails: TextView
    private lateinit var tvHomeBirthdays: TextView
    private lateinit var tvHomeSummary: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = DbHelper(requireContext())
        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvTodayCount = view.findViewById(R.id.tvTodayCount)
        tvWeekCount = view.findViewById(R.id.tvWeekCount)
        tvHomeReceived = view.findViewById(R.id.tvHomeReceived)
        tvHomePending = view.findViewById(R.id.tvHomePending)
        tvNextDetails = view.findViewById(R.id.tvNextDetails)
        tvHomeBirthdays = view.findViewById(R.id.tvHomeBirthdays)
        tvHomeSummary = view.findViewById(R.id.tvHomeSummary)

        view.findViewById<TextView>(R.id.tvHomeDate).text =
            Formats.fullDate(System.currentTimeMillis())

        view.findViewById<View>(R.id.btnInstagramHome).setOnClickListener {
            SocialLinks.openInstagram(requireContext())
        }
        view.findViewById<MaterialButton>(R.id.btnHomeNew).setOnClickListener {
            showAppointmentDialog(
                requireContext(),
                db,
                null,
                Formats.dayStart(System.currentTimeMillis())
            ) { load() }
        }
        view.findViewById<MaterialButton>(R.id.btnHomeClients).setOnClickListener {
            (activity as? MainActivity)?.openTab(R.id.nav_clients)
        }
        view.findViewById<MaterialButton>(R.id.btnHomeImportContacts).setOnClickListener {
            (activity as? MainActivity)?.importContacts()
                ?: showImportHint()
        }
        view.findViewById<MaterialButton>(R.id.btnHomeImportCalendar).setOnClickListener {
            (activity as? MainActivity)?.importCalendar()
                ?: showImportHint()
        }
        view.findViewById<MaterialButton>(R.id.btnViewAgenda).setOnClickListener {
            (activity as? MainActivity)?.openTab(R.id.nav_agenda)
        }
        view.findViewById<MaterialButton>(R.id.btnHomeFinance).setOnClickListener {
            (activity as? MainActivity)?.openTab(R.id.nav_finance)
        }
        view.findViewById<MaterialButton>(R.id.btnHomeUpdate).setOnClickListener {
            showUpdateDialog(requireContext())
        }

        load()
    }

    override fun onResume() {
        super.onResume()
        if (::tvGreeting.isInitialized) load()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::db.isInitialized) db.close()
    }

    private fun load() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..11 -> "Bom dia"
            in 12..17 -> "Boa tarde"
            else -> "Boa noite"
        }
        tvGreeting.text = "$greeting! 💜"

        val today = Formats.dayStart(System.currentTimeMillis())
        val todayItems = db.getAppointmentsForDay(today, Formats.addDays(today, 1))
            .filter { isBillable(it) }
        val weekItems = db.getAppointmentsForRange(today, Formats.addDays(today, 7))
            .filter { isBillable(it) }
        tvTodayCount.text = todayItems.size.toString()
        tvWeekCount.text = weekItems.size.toString()

        val monthStart = Formats.monthStart(System.currentTimeMillis())
        val monthEnd = Formats.addMonths(monthStart, 1)
        val monthItems = db.getAppointmentsForRange(monthStart, monthEnd).filter { isBillable(it) }
        val paidItems = monthItems.filter { it.paid }
        val received = paidItems.sumOf { it.price }
        val fees = paidItems.sumOf {
            PaymentFees.feeAmount(it.price, it.paymentMethod, it.feePercent)
        }
        val netReceived = received - fees
        val pending = monthItems.filter { !it.paid }.sumOf { it.price }
        tvHomeReceived.text = Formats.money(netReceived)
        tvHomePending.text = Formats.money(pending)

        val next = db.getNextAppointment(System.currentTimeMillis())
        tvNextDetails.text = if (next == null) {
            "Nenhum atendimento futuro.\nUse o atalho \"Novo horário\" para agendar."
        } else {
            "${Formats.fullDate(next.startMillis)} às ${Formats.time(next.startMillis)}\n" +
                "${next.clientName} • ${next.serviceName}\n" +
                "${Formats.money(next.price)} • ${AppointmentStatus.label(next.status)}"
        }

        val birthdays = db.getBirthdaysForMonth(Calendar.getInstance().get(Calendar.MONTH))
        tvHomeBirthdays.text = if (birthdays.isEmpty()) {
            "Nenhum aniversariante cadastrado neste mês."
        } else {
            val shown = birthdays.take(6).joinToString("\n") { client ->
                "• ${Formats.birthdayLabel(client.birthday)} — ${client.name}"
            }
            if (birthdays.size > 6) "$shown\n• + ${birthdays.size - 6} aniversariante(s)" else shown
        }

        val completed = monthItems.count { it.status == AppointmentStatus.COMPLETED }
        val expenses = db.getExpensesForRange(monthStart, monthEnd).sumOf { it.amount }
        tvHomeSummary.text =
            "Atendimentos: ${monthItems.size} • Concluídos: $completed\n" +
                "Recebido bruto: ${Formats.money(received)}\n" +
                "Taxas de cartão/Pix: ${Formats.money(fees)}\n" +
                "Recebido líquido: ${Formats.money(netReceived)}\n" +
                "Despesas: ${Formats.money(expenses)}\n" +
                "Lucro líquido: ${Formats.money(netReceived - expenses)}"
    }

    private fun isBillable(item: com.agendio.app.data.AppointmentView): Boolean =
        item.status != AppointmentStatus.CANCELED && item.status != AppointmentStatus.NO_SHOW

    private fun showImportHint() {
        android.widget.Toast.makeText(
            requireContext(),
            "Abra o menu do app para importar.",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}
