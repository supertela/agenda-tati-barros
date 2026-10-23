package com.agendio.app.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.agendio.app.R
import com.agendio.app.data.AppointmentStatus
import com.agendio.app.data.AppointmentView
import com.agendio.app.data.DbHelper
import com.agendio.app.util.Formats
import com.agendio.app.util.SocialLinks
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton

class AgendaFragment : Fragment(R.layout.fragment_agenda) {

    private lateinit var db: DbHelper
    private lateinit var adapter: DayScheduleAdapter
    private lateinit var tvDate: TextView
    private lateinit var tvSummary: TextView
    private lateinit var tvCalendarMonth: TextView
    private lateinit var monthCalendarView: MonthCalendarView
    private lateinit var calendarContainer: View
    private lateinit var listContainer: View
    private lateinit var toggle: MaterialButtonToggleGroup
    private var selectedDay: Long = 0L
    private var calendarMode = false
    private var syncingToggle = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = DbHelper(requireContext())
        selectedDay = savedInstanceState?.getLong(KEY_DAY, 0L)?.takeIf { it > 0L }
            ?: Formats.dayStart(System.currentTimeMillis())
        calendarMode = savedInstanceState?.getBoolean(KEY_CALENDAR_MODE, false) ?: false

        tvDate = view.findViewById(R.id.tvDate)
        tvSummary = view.findViewById(R.id.tvSummary)
        tvCalendarMonth = view.findViewById(R.id.tvCalendarMonth)
        monthCalendarView = view.findViewById(R.id.monthCalendarView)
        calendarContainer = view.findViewById(R.id.calendarContainer)
        listContainer = view.findViewById(R.id.listContainer)
        toggle = view.findViewById(R.id.toggleAgenda)
        val rvAppointments = view.findViewById<RecyclerView>(R.id.rvAppointments)

        adapter = DayScheduleAdapter(
            onAppointmentClick = { appointment ->
                showAppointmentDialog(
                    requireContext(),
                    db,
                    appointment.id,
                    selectedDay
                ) { load() }
            },
            onWhatsApp = { appointment ->
                SocialLinks.openWhatsAppConfirmation(requireContext(), appointment)
            },
            onFreeSlotClick = { startMillis ->
                if (startMillis <= System.currentTimeMillis()) {
                    Toast.makeText(
                        requireContext(),
                        "Esse horário já passou.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    showAppointmentDialog(
                        requireContext(),
                        db,
                        null,
                        selectedDay,
                        startMillis
                    ) { load() }
                }
            }
        )
        rvAppointments.layoutManager = LinearLayoutManager(requireContext())
        rvAppointments.adapter = adapter

        view.findViewById<View>(R.id.btnPrev).setOnClickListener {
            selectedDay = Formats.addDays(selectedDay, -1)
            load()
        }
        view.findViewById<View>(R.id.btnNext).setOnClickListener {
            selectedDay = Formats.addDays(selectedDay, 1)
            load()
        }
        view.findViewById<MaterialButton>(R.id.btnToday).setOnClickListener {
            selectedDay = Formats.dayStart(System.currentTimeMillis())
            load()
        }
        view.findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            showAppointmentDialog(
                requireContext(),
                db,
                null,
                selectedDay
            ) { load() }
        }

        monthCalendarView.onDaySelected = { day ->
            selectedDay = Formats.dayStart(day)
            load()
        }
        view.findViewById<View>(R.id.btnPrevMonth).setOnClickListener {
            changeMonth(-1)
        }
        view.findViewById<View>(R.id.btnNextMonth).setOnClickListener {
            changeMonth(1)
        }

        view.findViewById<MaterialButton>(R.id.btnViewList).setOnClickListener {
            setCalendarMode(false)
        }
        view.findViewById<MaterialButton>(R.id.btnViewCalendar).setOnClickListener {
            setCalendarMode(true)
        }

        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            setCalendarMode(checkedId == R.id.btnViewCalendar)
        }

        toggle.check(if (calendarMode) R.id.btnViewCalendar else R.id.btnViewList)
        setCalendarMode(calendarMode)
        load()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_DAY, selectedDay)
        outState.putBoolean(KEY_CALENDAR_MODE, calendarMode)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::db.isInitialized) db.close()
    }

    private fun setCalendarMode(enabled: Boolean) {
        calendarMode = enabled
        if (!::calendarContainer.isInitialized || !::listContainer.isInitialized) return

        calendarContainer.visibility = if (enabled) View.VISIBLE else View.GONE
        listContainer.visibility = if (enabled) View.GONE else View.VISIBLE

        if (enabled) {
            calendarContainer.requestLayout()
            refreshCalendar()
        }

        if (syncingToggle) return

        val target = if (enabled) R.id.btnViewCalendar else R.id.btnViewList
        if (::toggle.isInitialized && toggle.checkedButtonId != target) {
            syncingToggle = true
            toggle.check(target)
            syncingToggle = false
        }
    }

    private fun changeMonth(amount: Int) {
        selectedDay = Formats.addMonths(Formats.monthStart(selectedDay), amount)
        load()
    }

    private fun refreshCalendar() {
        if (!::monthCalendarView.isInitialized) return
        val monthStart = Formats.monthStart(selectedDay)
        val appointments = db.getAppointmentsForRange(
            monthStart,
            Formats.addMonths(monthStart, 1)
        )
        val counts = appointments.groupingBy { Formats.dayStart(it.startMillis) }.eachCount()
        monthCalendarView.setMonth(monthStart, selectedDay, counts)
        tvCalendarMonth.text = Formats.monthTitle(monthStart)
            .replace(" de ", " ")
            .uppercase(Formats.ptBr)
    }

    private fun load() {
        tvDate.text = Formats.fullDate(selectedDay)
        if (calendarMode) refreshCalendar()

        val appointments = db.getAppointmentsForDay(
            selectedDay,
            Formats.addDays(selectedDay, 1)
        )
        val schedule = buildDaySchedule(appointments)
        adapter.submit(schedule)

        val billable = appointments.filter {
            it.status != AppointmentStatus.CANCELED &&
                it.status != AppointmentStatus.NO_SHOW
        }
        val total = billable.sumOf { it.price }
        val freeCount = schedule.count {
            it is DayScheduleItem.FreeSlot && it.canBook
        }
        val label = if (appointments.size == 1) "atendimento" else "atendimentos"
        val freeLabel = if (freeCount == 1) "livre" else "livres"
        tvSummary.text =
            "${appointments.size} $label • ${Formats.money(total)} • $freeCount $freeLabel"
    }

    private fun buildDaySchedule(appointments: List<AppointmentView>): List<DayScheduleItem> {
        val scheduleStart = Formats.dayStart(selectedDay)
        val scheduleEnd = Formats.addDays(scheduleStart, 1)
        val sorted = appointments.sortedBy { it.startMillis }
        val blocking = sorted.filter {
            it.status != AppointmentStatus.CANCELED &&
                it.status != AppointmentStatus.NO_SHOW
        }
        val now = System.currentTimeMillis()

        val items = mutableListOf<DayScheduleItem>()
        var slot = scheduleStart
        while (slot < scheduleEnd) {
            val slotEnd = slot + DayScheduleAdapter.SLOT_MILLIS

            // Cancelados e faltas continuam visíveis, mas liberam o horário.
            sorted.filter { it.startMillis in slot until slotEnd }
                .forEach { items.add(DayScheduleItem.AppointmentItem(it)) }

            val occupied = blocking.any {
                it.startMillis < slotEnd && appointmentEnd(it) > slot
            }
            if (!occupied) {
                items.add(DayScheduleItem.FreeSlot(slot, slot > now))
            }

            slot = slotEnd
        }
        return items
    }

    private fun appointmentEnd(item: AppointmentView): Long =
        item.startMillis + item.durationMinutes.coerceAtLeast(1) * 60_000L

    companion object {
        private const val KEY_DAY = "selected_day"
        private const val KEY_CALENDAR_MODE = "calendar_mode"
    }
}
