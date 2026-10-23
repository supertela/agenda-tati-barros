package com.agendio.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.agendio.app.R
import com.agendio.app.data.AppointmentStatus
import com.agendio.app.data.AppointmentView
import com.agendio.app.data.DbHelper
import com.agendio.app.data.Expense
import com.agendio.app.data.PaymentMethods
import com.agendio.app.util.Formats
import com.agendio.app.util.PaymentFees
import com.agendio.app.util.SocialLinks
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class FinanceFragment : Fragment(R.layout.fragment_finance) {

    private lateinit var db: DbHelper
    private lateinit var adapter: AppointmentAdapter
    private lateinit var tvMonth: TextView
    private lateinit var tvReceived: TextView
    private lateinit var tvPending: TextView
    private lateinit var tvExpenses: TextView
    private lateinit var tvProfit: TextView
    private lateinit var tvPaymentBreakdown: TextView
    private lateinit var tvFeesSummary: TextView
    private lateinit var tvMonthSummary: TextView
    private lateinit var tvTopServices: TextView
    private lateinit var tvTopClients: TextView
    private lateinit var tvEmptyExpenses: TextView
    private lateinit var tvEmptyAppointments: TextView
    private lateinit var llExpenses: LinearLayout
    private var selectedMonth: Long = 0L
    private var lastReport: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = DbHelper(requireContext())
        selectedMonth = savedInstanceState?.getLong(KEY_MONTH, 0L)?.takeIf { it > 0L }
            ?: Formats.monthStart(System.currentTimeMillis())

        tvMonth = view.findViewById(R.id.tvMonth)
        tvReceived = view.findViewById(R.id.tvReceived)
        tvPending = view.findViewById(R.id.tvPending)
        tvExpenses = view.findViewById(R.id.tvExpenses)
        tvProfit = view.findViewById(R.id.tvProfit)
        tvPaymentBreakdown = view.findViewById(R.id.tvPaymentBreakdown)
        tvFeesSummary = view.findViewById(R.id.tvFeesSummary)
        tvMonthSummary = view.findViewById(R.id.tvMonthSummary)
        tvTopServices = view.findViewById(R.id.tvTopServices)
        tvTopClients = view.findViewById(R.id.tvTopClients)
        tvEmptyExpenses = view.findViewById(R.id.tvEmptyExpenses)
        tvEmptyAppointments = view.findViewById(R.id.tvEmptyAppointments)
        llExpenses = view.findViewById(R.id.llExpenses)

        val rv = view.findViewById<RecyclerView>(R.id.rvMonthAppointments)
        adapter = AppointmentAdapter(
            onClick = { appointment ->
                showAppointmentDialog(
                    requireContext(),
                    db,
                    appointment.id,
                    appointment.startMillis
                ) { load() }
            },
            onWhatsApp = { appointment ->
                SocialLinks.openWhatsAppConfirmation(requireContext(), appointment)
            }
        )
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        rv.isNestedScrollingEnabled = false

        view.findViewById<View>(R.id.btnPrevMonth).setOnClickListener {
            selectedMonth = Formats.addMonths(selectedMonth, -1)
            load()
        }
        view.findViewById<View>(R.id.btnNextMonth).setOnClickListener {
            selectedMonth = Formats.addMonths(selectedMonth, 1)
            load()
        }

        val addExpense = {
            showExpenseDialog(
                requireContext(),
                db,
                null,
                defaultExpenseDate()
            ) { load() }
        }
        view.findViewById<MaterialButton>(R.id.btnAddExpense).setOnClickListener { addExpense() }
        view.findViewById<FloatingActionButton>(R.id.fabAddExpense).setOnClickListener { addExpense() }
        view.findViewById<MaterialButton>(R.id.btnEditFees).setOnClickListener {
            showPaymentFeesDialog(requireContext()) { load() }
        }
        view.findViewById<MaterialButton>(R.id.btnShareReport).setOnClickListener {
            if (lastReport.isBlank()) return@setOnClickListener
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Relatório • ${tvMonth.text}")
                putExtra(Intent.EXTRA_TEXT, lastReport)
            }
            startActivity(Intent.createChooser(intent, "Compartilhar relatório"))
        }

        load()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_MONTH, selectedMonth)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::db.isInitialized) db.close()
    }

    private fun defaultExpenseDate(): Long {
        val currentMonth = Formats.monthStart(System.currentTimeMillis())
        return if (selectedMonth == currentMonth) System.currentTimeMillis() else selectedMonth
    }

    private fun load() {
        tvMonth.text = Formats.monthTitle(selectedMonth)
        val monthEnd = Formats.addMonths(selectedMonth, 1)
        val appointments = db.getAppointmentsForRange(selectedMonth, monthEnd)
        val billable = appointments.filter { isBillable(it) }
        val received = billable.filter { it.paid }.sumOf { it.price }
        val pending = billable.filter { !it.paid }.sumOf { it.price }
        val feesTotal = totalFees(billable)
        val netReceived = received - feesTotal
        val expenses = db.getExpensesForRange(selectedMonth, monthEnd)
        val expenseTotal = expenses.sumOf { it.amount }
        val profit = netReceived - expenseTotal

        tvReceived.text = Formats.money(received)
        tvPending.text = Formats.money(pending)
        tvExpenses.text = Formats.money(expenseTotal)
        tvProfit.text = Formats.money(profit)
        tvProfit.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                requireContext(),
                if (profit >= 0) R.color.primary else R.color.danger
            )
        )
        tvFeesSummary.text = PaymentFees.load(requireContext()).summary()

        tvPaymentBreakdown.text = paymentBreakdown(billable)
        tvMonthSummary.text = monthSummary(appointments, billable)
        tvTopServices.text = topServices(billable)
        tvTopClients.text = topClients(billable)
        lastReport = buildReport(
            appointments = appointments,
            billable = billable,
            expenses = expenses,
            received = received,
            feesTotal = feesTotal,
            netReceived = netReceived,
            pending = pending,
            expenseTotal = expenseTotal,
            profit = profit
        )

        bindExpenses(expenses)
        adapter.submit(appointments)
        tvEmptyAppointments.visibility = if (appointments.isEmpty()) View.VISIBLE else View.GONE
        tvEmptyExpenses.visibility = if (expenses.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun bindExpenses(expenses: List<Expense>) {
        llExpenses.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        expenses.forEach { expense ->
            val row = inflater.inflate(R.layout.item_expense, llExpenses, false)
            row.findViewById<TextView>(R.id.tvExpenseInitial).text =
                expense.category.take(1).uppercase()
            row.findViewById<TextView>(R.id.tvExpenseDescription).text = expense.description
            row.findViewById<TextView>(R.id.tvExpenseMeta).text =
                "${expense.category} • ${Formats.date(expense.dateMillis)}"
            row.findViewById<TextView>(R.id.tvExpenseAmount).text =
                "- ${Formats.money(expense.amount)}"
            row.setOnClickListener {
                showExpenseDialog(
                    requireContext(),
                    db,
                    expense,
                    defaultExpenseDate()
                ) { load() }
            }
            llExpenses.addView(row)
        }
    }

    private fun paymentBreakdown(billable: List<AppointmentView>): String {
        val lines = mutableListOf<String>()
        PaymentMethods.all
            .filter { it != PaymentMethods.NONE }
            .forEach { method ->
                val items = billable.filter { it.paymentMethod == method }
                val total = items.sumOf { it.price }
                if (total > 0.0) {
                    val fees = totalFees(items)
                    if (fees > 0.0) {
                        lines.add(
                            "• ${PaymentMethods.label(method)}: ${Formats.money(total)}" +
                                " (taxa ${Formats.money(fees)} • líquido ${Formats.money(total - fees)})"
                        )
                    } else {
                        lines.add("• ${PaymentMethods.label(method)}: ${Formats.money(total)}")
                    }
                }
            }
        val pending = billable.filter { !it.paid }.sumOf { it.price }
        if (pending > 0.0) {
            lines.add("• A receber: ${Formats.money(pending)}")
        }
        return if (lines.isEmpty()) "Nenhum pagamento registrado neste mês." else lines.joinToString("\n")
    }

    private fun monthSummary(
        appointments: List<AppointmentView>,
        billable: List<AppointmentView>
    ): String {
        val completed = appointments.count { it.status == AppointmentStatus.COMPLETED }
        val canceled = appointments.count {
            it.status == AppointmentStatus.CANCELED || it.status == AppointmentStatus.NO_SHOW
        }
        val average = if (billable.isEmpty()) 0.0 else billable.sumOf { it.price } / billable.size
        val commissions = billable.filter { it.paid }.sumOf {
            it.price * it.commissionPercent / 100.0
        }
        val serviceCosts = billable.filter { it.paid }.sumOf { it.serviceCost }
        val paid = billable.filter { it.paid }
        val fees = totalFees(paid)
        val netReceived = paid.sumOf { it.price } - fees
        return "Atendimentos: ${appointments.size} • Concluídos: $completed • Cancelados/faltas: $canceled\n" +
            "Ticket médio: ${Formats.money(average)}\n" +
            "Taxas de cartão/Pix: ${Formats.money(fees)} • Recebido líquido: ${Formats.money(netReceived)}\n" +
            "Comissões: ${Formats.money(commissions)} • Custo dos serviços: ${Formats.money(serviceCosts)}"
    }

    private fun topServices(billable: List<AppointmentView>): String {
        if (billable.isEmpty()) return "Sem atendimentos neste mês."
        return billable.groupBy { it.serviceName }
            .map { (name, list) -> Triple(name, list.size, list.sumOf { it.price }) }
            .sortedByDescending { it.third }
            .take(5)
            .joinToString("\n") { (name, count, total) ->
                "• $name — ${count}x — ${Formats.money(total)}"
            }
    }

    private fun topClients(billable: List<AppointmentView>): String {
        if (billable.isEmpty()) return "Sem atendimentos neste mês."
        return billable.groupBy { it.clientName }
            .map { (name, list) -> Triple(name, list.size, list.sumOf { it.price }) }
            .sortedByDescending { it.third }
            .take(5)
            .joinToString("\n") { (name, count, total) ->
                "• $name — ${count}x — ${Formats.money(total)}"
            }
    }

    private fun buildReport(
        appointments: List<AppointmentView>,
        billable: List<AppointmentView>,
        expenses: List<Expense>,
        received: Double,
        feesTotal: Double,
        netReceived: Double,
        pending: Double,
        expenseTotal: Double,
        profit: Double
    ): String {
        val builder = StringBuilder()
        builder.append("RELATÓRIO FINANCEIRO\n")
        builder.append(Formats.monthTitle(selectedMonth)).append("\n")
        builder.append("Agenda Tati Barros\n\n")
        builder.append("Recebido bruto: ").append(Formats.money(received)).append("\n")
        builder.append("Taxas de cartão/Pix: ").append(Formats.money(feesTotal)).append("\n")
        builder.append("Recebido líquido: ").append(Formats.money(netReceived)).append("\n")
        builder.append("A receber: ").append(Formats.money(pending)).append("\n")
        builder.append("Despesas: ").append(Formats.money(expenseTotal)).append("\n")
        builder.append("Lucro líquido: ").append(Formats.money(profit)).append("\n\n")
        builder.append("FORMAS DE PAGAMENTO\n")
        builder.append(paymentBreakdown(billable)).append("\n\n")
        builder.append("RESUMO\n")
        builder.append(monthSummary(appointments, billable)).append("\n\n")
        builder.append("TOP SERVIÇOS\n")
        builder.append(topServices(billable)).append("\n\n")
        builder.append("TOP CLIENTES\n")
        builder.append(topClients(billable)).append("\n\n")
        builder.append("DESPESAS\n")
        if (expenses.isEmpty()) {
            builder.append("Nenhuma despesa registrada.")
        } else {
            expenses.forEachIndexed { index, expense ->
                builder.append("• ").append(Formats.date(expense.dateMillis))
                    .append(" — ").append(expense.category)
                    .append(" — ").append(expense.description)
                    .append(" — ").append(Formats.money(expense.amount))
                if (index < expenses.lastIndex) builder.append("\n")
            }
        }
        builder.append("\n\nDesenvolvido por Andreo Silva")
        return builder.toString()
    }

    private fun feeFor(item: AppointmentView): Double =
        PaymentFees.feeAmount(item.price, item.paymentMethod, item.feePercent)

    private fun totalFees(items: List<AppointmentView>): Double =
        items.filter { it.paid }.sumOf { feeFor(it) }

    private fun isBillable(item: AppointmentView): Boolean =
        item.status != AppointmentStatus.CANCELED && item.status != AppointmentStatus.NO_SHOW

    companion object {
        private const val KEY_MONTH = "selected_month"
    }
}
