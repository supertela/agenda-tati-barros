package com.agendio.app.util

import android.content.Context
import com.agendio.app.data.PaymentMethods

/**
 * Percentuais padrão de taxa por forma de pagamento.
 *
 * O percentual usado em cada atendimento é copiado para o próprio agendamento,
 * para que mudanças futuras nas taxas não alterem relatórios antigos.
 */
object PaymentFees {

    private const val PREFS = "agendio_payment_fees"
    private const val KEY_CREDIT = "fee_credit"
    private const val KEY_DEBIT = "fee_debit"
    private const val KEY_PIX = "fee_pix"

    data class Rates(
        val credit: Double = 0.0,
        val debit: Double = 0.0,
        val pix: Double = 0.0
    ) {
        fun percentFor(method: String): Double = when (method) {
            PaymentMethods.CREDIT -> credit
            PaymentMethods.DEBIT -> debit
            PaymentMethods.PIX -> pix
            else -> 0.0
        }

        fun amountFor(price: Double, method: String): Double {
            if (price <= 0.0) return 0.0
            return price * percentFor(method) / 100.0
        }

        fun summary(): String {
            if (credit == 0.0 && debit == 0.0 && pix == 0.0) {
                return "Nenhuma taxa configurada"
            }
            return listOf(
                "Crédito ${Formats.rate(credit)}",
                "Débito ${Formats.rate(debit)}",
                "Pix ${Formats.rate(pix)}"
            ).joinToString(" • ")
        }
    }

    fun load(context: Context): Rates {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Rates(
            credit = prefs.getFloat(KEY_CREDIT, 0f).toDouble(),
            debit = prefs.getFloat(KEY_DEBIT, 0f).toDouble(),
            pix = prefs.getFloat(KEY_PIX, 0f).toDouble()
        )
    }

    fun save(context: Context, rates: Rates) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_CREDIT, rates.credit.coerceIn(0.0, 100.0).toFloat())
            .putFloat(KEY_DEBIT, rates.debit.coerceIn(0.0, 100.0).toFloat())
            .putFloat(KEY_PIX, rates.pix.coerceIn(0.0, 100.0).toFloat())
            .apply()
    }

    fun feeAmount(price: Double, method: String, feePercent: Double): Double {
        if (price <= 0.0 || feePercent <= 0.0) return 0.0
        if (!PaymentMethods.chargesFee(method)) return 0.0
        return price * feePercent.coerceIn(0.0, 100.0) / 100.0
    }
}
