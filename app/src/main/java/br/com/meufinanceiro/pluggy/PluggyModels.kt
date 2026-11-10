package br.com.meufinanceiro.pluggy

import br.com.meufinanceiro.data.CategoryClassifier
import br.com.meufinanceiro.data.CreditBill
import br.com.meufinanceiro.data.FinanceAccount
import br.com.meufinanceiro.data.Investment
import br.com.meufinanceiro.data.Movement
import br.com.meufinanceiro.data.MovementSource
import br.com.meufinanceiro.data.MovementType
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.abs

data class PluggyAccount(
    val id: String,
    val itemId: String?,
    val name: String,
    val type: String,
    val subtype: String,
    val balance: Double,
    val currencyCode: String,
    val number: String?,
    val creditLimit: Double?,
    val availableCreditLimit: Double?,
    val closeDate: String?,
    val dueDate: String?,
    val brand: String?,
    val level: String?,
) {
    val isCredit: Boolean get() = type.equals("CREDIT", true)

    val displayName: String
        get() = name.ifBlank { subtype.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() } }

    fun toFinanceAccount(): FinanceAccount = FinanceAccount(
        id = id,
        itemId = itemId,
        name = displayName,
        type = type,
        subtype = subtype,
        balance = balance,
        number = number,
        currency = currencyCode,
        creditLimit = creditLimit,
        availableCreditLimit = availableCreditLimit,
        closeDate = closeDate,
        dueDate = dueDate,
        brand = brand,
        level = level,
    )

    companion object {
        fun fromJson(o: JSONObject): PluggyAccount {
            val credit = o.optJSONObject("creditData")
            return PluggyAccount(
                id = o.optString("id"),
                itemId = o.optString("itemId").takeIf { it.isNotBlank() && it != "null" },
                name = o.optString("name"),
                type = o.optString("type", "BANK"),
                subtype = o.optString("subtype", "CHECKING_ACCOUNT"),
                balance = o.optDouble("balance", 0.0),
                currencyCode = o.optString("currencyCode", "BRL"),
                number = o.optString("number").takeIf { it.isNotBlank() && it != "null" },
                creditLimit = credit?.optDouble("creditLimit", Double.NaN)?.takeUnless { it.isNaN() },
                availableCreditLimit = credit?.optDouble("availableCreditLimit", Double.NaN)?.takeUnless { it.isNaN() },
                closeDate = credit?.optString("balanceCloseDate")?.takeIf { it.isNotBlank() && it != "null" },
                dueDate = credit?.optString("balanceDueDate")?.takeIf { it.isNotBlank() && it != "null" },
                brand = credit?.optString("brand")?.takeIf { it.isNotBlank() && it != "null" },
                level = credit?.optString("level")?.takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }
}

data class PluggyTransaction(
    val id: String,
    val description: String,
    val amount: Double,
    val date: String,
    val type: String,
    val category: String?,
    val status: String,
    val operationType: String?,
    val installmentNumber: Int?,
    val totalInstallments: Int?,
    val billId: String?,
    /** Mês de fechamento da fatura informado pelo banco (YYYY-MM). */
    val billForecastDate: String? = null,
) {
    /**
     * No cartão, a Pluggy documenta amount positivo = compra e negativo = pagamento,
     * mas alguns conectores invertem o sinal. O operationType é a fonte mais segura:
     * PAGAMENTO/PAGAMENTO_FATURA/ESTORNO/CASHBACK são créditos; o restante é compra.
     * Quando o operationType vem vazio/OUTROS, a descrição identifica pagamentos e
     * estornos. Qualquer outra coisa é despesa, nunca receita.
     */
    fun isCreditIncome(): Boolean {
        val operation = operationType?.uppercase().orEmpty()
        val typeCredit = type.equals("CREDIT", true)
        val typeDebit = type.equals("DEBIT", true)
        // Alguns conectores (ex.: Santander/MeuPluggy) usam operationType
        // "PAGAMENTO" em TODAS as compras, não só no pagamento da fatura.
        // Por isso o tipo DEBIT tem prioridade: compra nunca vira receita.
        return when {
            isCreditIncomeDescription(description) -> true
            operation in DEBIT_OPERATIONS -> false
            typeDebit -> false
            typeCredit -> true
            operation in CREDIT_OPERATIONS -> amount < 0.0
            else -> amount < 0.0
        }
    }

    fun toMovement(account: PluggyAccount): Movement? {
        if (id.isBlank()) return null
        val isIncome = if (account.isCredit) {
            isCreditIncome()
        } else {
            type.equals("CREDIT", true) || (type.isBlank() && amount > 0.0)
        }

        return Movement(
            id = id,
            title = description.ifBlank { if (isIncome) "Crédito" else "Débito" },
            amount = abs(amount),
            type = if (isIncome) MovementType.INCOME else MovementType.EXPENSE,
            category = mapCategory(category, description),
            bankCategory = category?.takeIf { it.isNotBlank() && it != "null" },
            date = parsePluggyDate(date),
            accountId = account.id,
            accountName = account.displayName,
            isCreditCard = account.isCredit,
            installmentNumber = installmentNumber,
            totalInstallments = totalInstallments,
            billId = billId,
            billMonth = if (account.isCredit) {
                billForecastDate?.takeIf { it.length >= 7 && it != "0001-01" }?.take(7)
            } else {
                null
            },
            status = status,
            source = MovementSource.PLUGGY,
        )
    }

    companion object {
        fun fromJson(o: JSONObject): PluggyTransaction {
            val metadata = o.optJSONObject("creditCardMetadata")
            return PluggyTransaction(
                id = o.optString("id"),
                description = o.optString("description"),
                amount = o.optDouble("amount", 0.0),
                date = o.optString("date"),
                type = o.optString("type", "DEBIT"),
                category = o.optString("category").takeIf { it.isNotBlank() && it != "null" },
                status = o.optString("status", "POSTED"),
                operationType = o.optString("operationType").takeIf { it.isNotBlank() && it != "null" },
                installmentNumber = metadata?.optInt("installmentNumber", 0)?.takeIf { it > 0 },
                totalInstallments = metadata?.optInt("totalInstallments", 0)?.takeIf { it > 0 },
                billId = metadata?.optString("billId")?.takeIf { it.isNotBlank() && it != "null" },
                billForecastDate = metadata?.optString("billForecastDate")
                    ?.takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }
}

data class PluggyBill(
    val id: String,
    val accountId: String,
    val dueDate: String,
    val closingDate: String?,
    val totalAmount: Double,
    val minimumPayment: Double?,
    val paidAmount: Double,
    val financeCharges: Double,
    val lastPaymentDate: Long?,
    val currency: String,
) {
    fun toCreditBill(): CreditBill = CreditBill(
        id = id,
        accountId = accountId,
        dueDate = parsePluggyDate(dueDate),
        closingDate = closingDate?.let { parsePluggyDate(it) },
        totalAmount = totalAmount,
        minimumPayment = minimumPayment,
        paidAmount = paidAmount,
        financeCharges = financeCharges,
        lastPaymentDate = lastPaymentDate,
        currency = currency,
    )

    companion object {
        fun fromJson(o: JSONObject, accountId: String): PluggyBill {
            val payments = o.optJSONArray("payments")
            var paid = 0.0
            var lastPayment: Long? = null
            if (payments != null) {
                for (index in 0 until payments.length()) {
                    val payment = payments.optJSONObject(index) ?: continue
                    paid += payment.optDouble("amount", 0.0)
                    val dateText = payment.optString("paymentDate")
                    if (dateText.isNotBlank() && dateText != "null") {
                        val parsed = parsePluggyDate(dateText)
                        if (lastPayment == null || parsed > lastPayment) lastPayment = parsed
                    }
                }
            }
            val charges = o.optJSONArray("financeCharges")
            var financeCharges = 0.0
            if (charges != null) {
                for (index in 0 until charges.length()) {
                    financeCharges += charges.optJSONObject(index)?.optDouble("amount", 0.0) ?: 0.0
                }
            }
            return PluggyBill(
                id = o.optString("id"),
                accountId = accountId,
                dueDate = o.optString("dueDate"),
                closingDate = o.optString("billClosingDate").takeIf { it.isNotBlank() && it != "null" },
                totalAmount = o.optDouble("totalAmount", 0.0),
                minimumPayment = o.optDouble("minimumPaymentAmount", Double.NaN).takeUnless { it.isNaN() },
                paidAmount = paid,
                financeCharges = financeCharges,
                lastPaymentDate = lastPayment,
                currency = o.optString("totalAmountCurrencyCode", "BRL"),
            )
        }
    }
}

data class PluggyInvestment(
    val id: String,
    val name: String,
    val type: String,
    val subtype: String?,
    val balance: Double,
    val amount: Double?,
    val amountOriginal: Double?,
    val amountProfit: Double?,
    val amountWithdrawal: Double?,
    val currency: String,
    val rate: Double?,
    val rateType: String?,
    val fixedAnnualRate: Double?,
    val status: String,
    val dueDate: String?,
    val issuer: String?,
    val institution: String?,
    val annualRate: Double?,
    val lastMonthRate: Double?,
    val lastTwelveMonthsRate: Double?,
) {
    fun toInvestment(itemId: String?): Investment = Investment(
        id = id,
        itemId = itemId,
        name = name,
        type = type,
        subtype = subtype,
        balance = balance,
        amount = amount,
        amountOriginal = amountOriginal,
        amountProfit = amountProfit,
        amountWithdrawal = amountWithdrawal,
        currency = currency,
        rate = rate,
        rateType = rateType,
        fixedAnnualRate = fixedAnnualRate,
        status = status,
        dueDate = dueDate?.let { parsePluggyDate(it) },
        issuer = issuer,
        institution = institution,
        annualRate = annualRate,
        lastMonthRate = lastMonthRate,
        lastTwelveMonthsRate = lastTwelveMonthsRate,
    )

    companion object {
        fun fromJson(o: JSONObject, itemId: String?): PluggyInvestment? {
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
            return PluggyInvestment(
                id = id,
                name = o.optString("name", "Investimento").ifBlank { "Investimento" },
                type = o.optString("type", "OTHER"),
                subtype = o.optString("subtype").takeIf { it.isNotBlank() && it != "null" },
                balance = o.optDouble("balance", 0.0),
                amount = o.optDouble("amount", Double.NaN).takeUnless { it.isNaN() },
                amountOriginal = o.optDouble("amountOriginal", Double.NaN).takeUnless { it.isNaN() },
                amountProfit = o.optDouble("amountProfit", Double.NaN).takeUnless { it.isNaN() },
                amountWithdrawal = o.optDouble("amountWithdrawal", Double.NaN).takeUnless { it.isNaN() },
                currency = o.optString("currencyCode", "BRL"),
                rate = o.optDouble("rate", Double.NaN).takeUnless { it.isNaN() },
                rateType = o.optString("rateType").takeIf { it.isNotBlank() && it != "null" },
                fixedAnnualRate = o.optDouble("fixedAnnualRate", Double.NaN).takeUnless { it.isNaN() },
                status = o.optString("status", "ACTIVE").ifBlank { "ACTIVE" },
                dueDate = o.optString("dueDate").takeIf { it.isNotBlank() && it != "null" },
                issuer = o.optString("issuer").takeIf { it.isNotBlank() && it != "null" },
                institution = o.optJSONObject("institution")?.optString("name")
                    ?.takeIf { it.isNotBlank() && it != "null" },
                annualRate = o.optDouble("annualRate", Double.NaN).takeUnless { it.isNaN() },
                lastMonthRate = o.optDouble("lastMonthRate", Double.NaN).takeUnless { it.isNaN() },
                lastTwelveMonthsRate = o.optDouble("lastTwelveMonthsRate", Double.NaN).takeUnless { it.isNaN() },
            )
        }
    }
}
private val CREDIT_OPERATIONS = setOf("PAGAMENTO", "PAGAMENTO_FATURA", "ESTORNO", "CASHBACK")
private val DEBIT_OPERATIONS = setOf("TARIFA", "OPERACOES_CREDITO_CONTRATADAS_CARTAO")

/**
 * Pagamentos e estornos no cartão podem vir com operationType vazio em alguns
 * conectores. Nesses casos a descrição é a única pista; sem ela, o lançamento é
 * tratado como compra (despesa) para nunca inflar as receitas.
 */
private fun isCreditIncomeDescription(description: String): Boolean {
    val value = description.uppercase()
    val payment = (value.contains("PAGAMENTO") || value.contains("PAGTO") ||
        value.contains("PAG.")) &&
        (value.contains("FATURA") || value.contains("CARTAO") ||
            value.contains("CARTÃO") || value.contains("RECEBIDO"))
    val refund = value.contains("ESTORNO") || value.contains("CASHBACK") ||
        value.contains("DEVOLUCAO") || value.contains("DEVOLUÇÃO")
    return payment || refund
}

private fun mapCategory(category: String?, description: String?): String =
    CategoryClassifier.classify(category, description)

private fun parsePluggyDate(value: String): Long {
    if (value.isBlank()) return System.currentTimeMillis()
    return runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching {
            LocalDate.parse(value.take(10))
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }
        .getOrDefault(System.currentTimeMillis())
}