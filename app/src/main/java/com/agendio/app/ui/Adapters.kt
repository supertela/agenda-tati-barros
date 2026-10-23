package com.agendio.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.agendio.app.R
import com.agendio.app.data.AppointmentStatus
import com.agendio.app.data.AppointmentView
import com.agendio.app.data.Client
import com.agendio.app.data.PaymentMethods
import com.agendio.app.data.Service
import com.agendio.app.util.ContactPhotos
import com.agendio.app.util.Formats

fun statusColorRes(status: String): Int = when (status) {
    AppointmentStatus.CONFIRMED -> R.color.accent_dark
    AppointmentStatus.COMPLETED -> R.color.success
    AppointmentStatus.CANCELED -> R.color.danger
    AppointmentStatus.NO_SHOW -> R.color.warning
    else -> R.color.primary
}

class AppointmentAdapter(
    private val onClick: (AppointmentView) -> Unit,
    private val onWhatsApp: ((AppointmentView) -> Unit)? = null
) : RecyclerView.Adapter<AppointmentAdapter.ViewHolder>() {

    private val items = mutableListOf<AppointmentView>()

    fun submit(newItems: List<AppointmentView>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_appointment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onClick, onWhatsApp)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvClient: TextView = itemView.findViewById(R.id.tvClient)
        private val tvService: TextView = itemView.findViewById(R.id.tvService)
        private val tvNotes: TextView = itemView.findViewById(R.id.tvNotes)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvPayment: TextView = itemView.findViewById(R.id.tvPayment)
        private val btnWhatsApp: ImageButton = itemView.findViewById(R.id.btnItemWhatsApp)

        fun bind(
            item: AppointmentView,
            onClick: (AppointmentView) -> Unit,
            onWhatsApp: ((AppointmentView) -> Unit)?
        ) {
            tvTime.text = Formats.time(item.startMillis)
            tvClient.text = item.clientName
            tvService.text = item.serviceName
            tvPrice.text = Formats.money(item.price)
            tvNotes.text = item.notes
            tvNotes.visibility = if (item.notes.isBlank()) View.GONE else View.VISIBLE

            tvStatus.text = AppointmentStatus.label(item.status)
            tvStatus.setTextColor(
                ContextCompat.getColor(itemView.context, statusColorRes(item.status))
            )
            val feeLabel = if (item.feePercent > 0.0) {
                " • taxa ${Formats.rate(item.feePercent)}"
            } else {
                ""
            }
            tvPayment.text = "${PaymentMethods.shortLabel(item.paymentMethod)}$feeLabel"
            tvPayment.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (item.paid) R.color.success else R.color.warning
                )
            )

            val canConfirm = onWhatsApp != null &&
                item.clientPhone.isNotBlank() &&
                AppointmentStatus.isActive(item.status)
            btnWhatsApp.visibility = if (canConfirm) View.VISIBLE else View.GONE
            btnWhatsApp.setOnClickListener { onWhatsApp?.invoke(item) }

            itemView.setOnClickListener { onClick(item) }
        }
    }
}

sealed class DayScheduleItem {
    data class AppointmentItem(val appointment: AppointmentView) : DayScheduleItem()
    data class FreeSlot(val startMillis: Long, val canBook: Boolean) : DayScheduleItem()
}

class DayScheduleAdapter(
    private val onAppointmentClick: (AppointmentView) -> Unit,
    private val onWhatsApp: ((AppointmentView) -> Unit)? = null,
    private val onFreeSlotClick: (Long) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<DayScheduleItem>()

    fun submit(newItems: List<DayScheduleItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is DayScheduleItem.AppointmentItem -> TYPE_APPOINTMENT
        is DayScheduleItem.FreeSlot -> TYPE_FREE_SLOT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_APPOINTMENT -> AppointmentAdapter.ViewHolder(
                inflater.inflate(R.layout.item_appointment, parent, false)
            )

            else -> FreeSlotViewHolder(
                inflater.inflate(R.layout.item_free_slot, parent, false),
                onFreeSlotClick
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DayScheduleItem.AppointmentItem ->
                (holder as AppointmentAdapter.ViewHolder).bind(
                    item.appointment,
                    onAppointmentClick,
                    onWhatsApp
                )

            is DayScheduleItem.FreeSlot ->
                (holder as FreeSlotViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    private class FreeSlotViewHolder(
        itemView: View,
        private val onFreeSlotClick: (Long) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvFreeTime: TextView = itemView.findViewById(R.id.tvFreeTime)
        private val tvFreeLabel: TextView = itemView.findViewById(R.id.tvFreeLabel)
        private val btnFreeAdd: ImageButton = itemView.findViewById(R.id.btnFreeAdd)

        fun bind(item: DayScheduleItem.FreeSlot) {
            tvFreeTime.text =
                "${Formats.time(item.startMillis)} – ${Formats.time(item.startMillis + SLOT_MILLIS)}"

            tvFreeLabel.text = "Livre"
            tvFreeLabel.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (item.canBook) R.color.success else R.color.text_muted
                )
            )
            btnFreeAdd.visibility = if (item.canBook) View.VISIBLE else View.INVISIBLE
            itemView.alpha = if (item.canBook) 1f else 0.62f

            val open = View.OnClickListener {
                if (item.canBook) onFreeSlotClick(item.startMillis)
            }
            itemView.setOnClickListener(open)
            btnFreeAdd.setOnClickListener(open)
        }
    }

    companion object {
        private const val TYPE_APPOINTMENT = 0
        private const val TYPE_FREE_SLOT = 1
        const val SLOT_MILLIS = 30 * 60 * 1000L
    }
}

class ClientAdapter(
    private val onClick: (Client) -> Unit
) : RecyclerView.Adapter<ClientAdapter.ViewHolder>() {

    private val items = mutableListOf<Client>()

    fun submit(newItems: List<Client>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_client, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvInitial: TextView = itemView.findViewById(R.id.tvInitial)
        private val ivPhoto: ImageView = itemView.findViewById(R.id.ivClientPhoto)
        private val tvName: TextView = itemView.findViewById(R.id.tvClientName)
        private val tvPhone: TextView = itemView.findViewById(R.id.tvClientPhone)
        private val tvMeta: TextView = itemView.findViewById(R.id.tvClientMeta)

        fun bind(item: Client, onClick: (Client) -> Unit) {
            tvInitial.text = Formats.initials(item.name)
            ContactPhotos.load(itemView.context, item.photoUri, ivPhoto, tvInitial)
            tvName.text = item.name
            tvPhone.text = item.phone.ifBlank { "Sem telefone" }
            val meta = buildList {
                if (item.birthday.isNotBlank()) add("🎂 ${Formats.birthdayLabel(item.birthday)}")
                if (item.email.isNotBlank()) add(item.email)
            }.joinToString(" • ")
            tvMeta.text = meta
            tvMeta.visibility = if (meta.isBlank()) View.GONE else View.VISIBLE
            itemView.setOnClickListener { onClick(item) }
        }
    }
}

class ServiceAdapter(
    private val onClick: (Service) -> Unit
) : RecyclerView.Adapter<ServiceAdapter.ViewHolder>() {

    private val items = mutableListOf<Service>()

    fun submit(newItems: List<Service>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_service, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvInitial: TextView = itemView.findViewById(R.id.tvServiceInitial)
        private val tvName: TextView = itemView.findViewById(R.id.tvServiceName)
        private val tvDetails: TextView = itemView.findViewById(R.id.tvServiceDetails)

        fun bind(item: Service, onClick: (Service) -> Unit) {
            tvInitial.text = Formats.initials(item.name)
            tvName.text = item.name
            val commission = if (item.commissionPercent > 0.0) {
                " • ${Formats.percent(item.commissionPercent)} comissão"
            } else {
                ""
            }
            val cost = if (item.cost > 0.0) " • custo ${Formats.money(item.cost)}" else ""
            tvDetails.text = "${item.durationMinutes} min • ${Formats.money(item.price)}$cost$commission"
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
