package com.agendio.app.data

data class Client(
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val birthday: String = "",
    val notes: String = "",
    val photoUri: String = ""
)

data class Service(
    val id: Long = 0,
    val name: String,
    val durationMinutes: Int = 30,
    val price: Double = 0.0,
    val cost: Double = 0.0,
    val commissionPercent: Double = 0.0
)

data class Appointment(
    val id: Long = 0,
    val clientId: Long,
    val serviceId: Long,
    val startMillis: Long,
    val durationMinutes: Int = 0,
    val notes: String = "",
    val status: String = AppointmentStatus.SCHEDULED,
    val paymentMethod: String = PaymentMethods.NONE,
    val price: Double = 0.0,
    val feePercent: Double = 0.0,
    val calendarEventId: Long = 0L
) {
    val paid: Boolean
        get() = paymentMethod != PaymentMethods.NONE
}

data class AppointmentView(
    val id: Long,
    val clientId: Long,
    val clientName: String,
    val clientPhone: String,
    val clientEmail: String,
    val serviceId: Long,
    val serviceName: String,
    val startMillis: Long,
    val notes: String,
    val status: String,
    val paymentMethod: String,
    val price: Double,
    val feePercent: Double = 0.0,
    val calendarEventId: Long,
    val durationMinutes: Int,
    val serviceCost: Double = 0.0,
    val commissionPercent: Double = 0.0
) {
    val paid: Boolean
        get() = paymentMethod != PaymentMethods.NONE
}

data class Expense(
    val id: Long = 0,
    val description: String,
    val amount: Double,
    val dateMillis: Long,
    val category: String = ExpenseCategories.OTHER
)

object AppointmentStatus {
    const val SCHEDULED = "scheduled"
    const val CONFIRMED = "confirmed"
    const val COMPLETED = "completed"
    const val CANCELED = "canceled"
    const val NO_SHOW = "no_show"

    val all = listOf(SCHEDULED, CONFIRMED, COMPLETED, CANCELED, NO_SHOW)

    fun label(value: String): String = when (value) {
        CONFIRMED -> "Confirmado"
        COMPLETED -> "Concluído"
        CANCELED -> "Cancelado"
        NO_SHOW -> "Não compareceu"
        else -> "Agendado"
    }

    fun isActive(value: String): Boolean =
        value == SCHEDULED || value == CONFIRMED
}

object PaymentMethods {
    const val NONE = "none"
    const val CASH = "cash"
    const val PIX = "pix"
    const val DEBIT = "debit"
    const val CREDIT = "credit"
    const val OTHER = "other"

    val all = listOf(NONE, CASH, PIX, DEBIT, CREDIT, OTHER)

    fun label(value: String): String = when (value) {
        CASH -> "Dinheiro"
        PIX -> "Pix"
        DEBIT -> "Cartão de débito"
        CREDIT -> "Cartão de crédito"
        OTHER -> "Outro"
        else -> "A receber"
    }

    fun shortLabel(value: String): String = when (value) {
        CASH -> "Dinheiro"
        PIX -> "Pix"
        DEBIT -> "Débito"
        CREDIT -> "Crédito"
        OTHER -> "Outro"
        else -> "A receber"
    }

    fun chargesFee(value: String): Boolean =
        value == CREDIT || value == DEBIT || value == PIX
}

object ExpenseCategories {
    const val PRODUCTS = "Produtos"
    const val RENT = "Aluguel"
    const val EQUIPMENT = "Equipamentos"
    const val MARKETING = "Marketing"
    const val TAXES = "Impostos"
    const val OTHER = "Outros"

    val all = listOf(PRODUCTS, RENT, EQUIPMENT, MARKETING, TAXES, OTHER)
}
