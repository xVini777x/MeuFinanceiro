package br.com.meufinanceiro.data

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Totals(val income: Double, val expense: Double) {
    val balance: Double get() = income - expense
}

data class CategoryTotal(val category: String, val amount: Double, val count: Int)

data class MonthlyTotal(val month: YearMonth, val income: Double, val expense: Double)

data class InvoiceSummary(
    val account: FinanceAccount,
    val bill: CreditBill?,
    val month: YearMonth,
    val total: Double,
    val paid: Double,
    val minimumPayment: Double?,
    val dueDate: Long?,
    val closingDate: Long?,
    val movements: List<Movement>,
) {
    val pending: Double get() = (total - paid).coerceAtLeast(0.0)
}

enum class InvoiceStatus(val label: String) {
    OPEN("Em aberto"),
    FUTURE("Futura"),
    PAID("Paga"),
    OVERDUE("Vencida"),
}

data class InvoiceEntry(
    val bill: CreditBill,
    val status: InvoiceStatus,
    val summary: InvoiceSummary,
    val isCurrent: Boolean,
    val isProjected: Boolean = false,
) {
    val pending: Double get() = summary.pending
}

data class InvestmentTypeTotal(val label: String, val amount: Double, val count: Int)

data class InstallmentPlan(
    val title: String,
    val current: Int,
    val total: Int,
    val installmentAmount: Double,
    val remaining: Double,
    val movements: List<Movement>,
    val accountId: String? = null,
    val accountName: String? = null,
    val category: String = Categories.OTHER,
    /** Mês estimado da próxima parcela, quando ainda falta alguma. */
    val nextMonth: YearMonth? = null,
    /**
     * Verdadeiro quando o parcelamento já foi quitado (pago de uma vez, cancelado
     * ou com todas as parcelas em faturas já pagas). Parcelamentos encerrados
     * não aparecem em "Compras parceladas".
     */
    val settled: Boolean = false,
)

object Categories {
    const val FOOD = "Alimentação"
    const val MARKET = "Mercado"
    const val SHOPPING = "Compras"
    const val TRANSPORT = "Transporte"
    const val HOUSING = "Moradia"
    const val HEALTH = "Saúde"
    const val LEISURE = "Lazer"
    const val SUBSCRIPTIONS = "Assinaturas"
    const val EDUCATION = "Educação"
    const val TRAVEL = "Viagens"
    const val PETS = "Pets"
    const val SALARY = "Salário"
    const val INVESTMENTS = "Investimentos"
    const val TRANSFERS = "Transferências"
    /** IOF, tarifas, juros e multas: custo do cartão, não gasto do dia a dia. */
    const val FEES = "Taxas"
    const val OTHER = "Outros"

    val all = listOf(
        FOOD, MARKET, SHOPPING, TRANSPORT, HOUSING, HEALTH, LEISURE,
        SUBSCRIPTIONS, EDUCATION, TRAVEL, PETS, SALARY, INVESTMENTS, TRANSFERS,
        FEES, OTHER,
    )

    val expense = listOf(
        FOOD, MARKET, SHOPPING, TRANSPORT, HOUSING, HEALTH, LEISURE,
        SUBSCRIPTIONS, EDUCATION, TRAVEL, PETS, TRANSFERS, FEES, OTHER,
    )

    val income = listOf(
        SALARY, INVESTMENTS, TRANSFERS, OTHER,
    )
}

object FinanceCalculator {

    private const val MONEY_EPS = 0.02
    private const val PROJECTED_PREFIX = "projected-"
    private const val DAY_MS = 86_400_000L
    private const val PAYMENT_WINDOW_DAYS = 20L

    /**
     * Faturas mais antigas que isso são tratadas como históricas quando existe uma
     * fatura mais nova. Bancos não mantêm faturas de meses atrás em aberto; quando
     * a Pluggy não devolve o pagamento, isso evita um falso "em atraso".
     */
    /**
     * Meses sem movimento até o parcelamento ser considerado antigo (usado
     * quando o banco não informa a fatura da parcela).
     */
    private const val STALE_INSTALLMENT_MONTHS = 3

    private const val STALE_BILL_MONTHS = 3L

    private val locale = Locale("pt", "BR")
    private val zone: ZoneId = ZoneId.systemDefault()
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    private val dayFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
    private val shortDayFormatter = DateTimeFormatter.ofPattern("dd/MM", locale)
    private val fullDayFormatter = DateTimeFormatter.ofPattern("dd 'de' MMMM", locale)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", locale)

    fun brl(value: Double): String =
        if (AmountPrivacy.hidden) {
            AmountPrivacy.MASK
        } else {
            NumberFormat.getCurrencyInstance(locale).format(value)
        }

    /** Arredonda para centavos para não acumular erro de ponto flutuante. */
    private fun roundCents(value: Double): Double = Math.round(value * 100.0) / 100.0

    fun brlSigned(value: Double): String {
        if (AmountPrivacy.hidden) return AmountPrivacy.MASK
        val prefix = if (value < 0) "− " else "+ "
        return prefix + brl(kotlin.math.abs(value))
    }

    fun formatDate(epoch: Long): String = dayFormatter.format(toLocalDate(epoch))

    /** Data e hora curtas (dd/MM HH:mm), para "atualizado às". */
    fun formatTime(epoch: Long): String =
        DateTimeFormatter.ofPattern("dd/MM HH:mm", locale)
            .format(Instant.ofEpochMilli(epoch).atZone(zone).toLocalDateTime())

    /** Data curta (dd/MM), usada quando o mês/ano já aparece no título. */
    fun formatDayMonth(epoch: Long): String = shortDayFormatter.format(toLocalDate(epoch))

    fun formatFullDay(epoch: Long): String = fullDayFormatter.format(toLocalDate(epoch))

    fun formatMonth(month: YearMonth): String {
        val text = monthFormatter.format(month.atDay(1))
        return text.replaceFirstChar { it.uppercase(locale) }
    }

    fun formatMonthShort(month: YearMonth): String {
        val text = month.atDay(1).format(DateTimeFormatter.ofPattern("MMM", locale))
        return text.replace(".", "").replaceFirstChar { it.uppercase(locale) }
    }

    fun monthOf(epoch: Long): YearMonth = YearMonth.from(toLocalDate(epoch))

    /**
     * Nome do estabelecimento a partir da descrição do banco.
     *
     * As descrições do Open Finance trazem o meio de pagamento na frente
     * ("MP*ALIEXPRESS", "DL *UberRides"), que não ajuda a agrupar: o que
     * interessa é o lojista. Quando o prefixo é curto demais para ser
     * informativo ("MP", "DL"), usa-se o trecho depois do `*`.
     */
    fun merchantName(title: String): String {
        val cleaned = title.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return ""
        val head = cleaned.substringBefore("*").trim()
        val tail = cleaned.substringAfter("*", "").trim()
        return when {
            head.length >= 4 -> head.uppercase(locale)
            tail.isNotBlank() -> tail.uppercase(locale)
            else -> cleaned.uppercase(locale)
        }
    }

    fun toLocalDate(epoch: Long): LocalDate =
        Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()

    fun dayStart(epoch: Long): Long =
        toLocalDate(epoch).atStartOfDay(zone).toInstant().toEpochMilli()

    fun monthStart(month: YearMonth): Long =
        month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun monthEnd(month: YearMonth): Long =
        month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L

    fun filterMonth(movements: List<Movement>, month: YearMonth): List<Movement> {
        val start = monthStart(month)
        val end = monthEnd(month)
        return movements.filter { it.date in start..end }
    }

    fun totals(movements: List<Movement>): Totals = Totals(
        income = movements.filter { it.isIncome && !it.internalTransfer }.sumOf { it.amount },
        expense = movements.filter { !it.isIncome && !it.internalTransfer }.sumOf { it.amount },
    )

    fun categoryTotals(movements: List<Movement>): List<CategoryTotal> =
        movements.filter { !it.isIncome && !it.internalTransfer }
            .groupBy { it.category.ifBlank { Categories.OTHER } }
            .map { (category, items) ->
                CategoryTotal(category = category, amount = items.sumOf { it.amount }, count = items.size)
            }
            .sortedByDescending { it.amount }

    fun monthlySeries(movements: List<Movement>, anchor: YearMonth, count: Int = 6): List<MonthlyTotal> =
        (count - 1 downTo 0).map { offset ->
            val month = anchor.minusMonths(offset.toLong())
            val items = filterMonth(movements, month)
            val totals = totals(items)
            MonthlyTotal(month = month, income = totals.income, expense = totals.expense)
        }

    fun invoiceFor(
        account: FinanceAccount,
        bills: List<CreditBill>,
        movements: List<Movement>,
        month: YearMonth,
    ): InvoiceSummary {
        val allBills = billsWithProjections(account, bills, movements, LocalDate.now())
        val bill = allBills
            .filter { it.accountId == account.id }
            .sortedBy { it.dueDate }
            .firstOrNull { monthOf(it.dueDate) == month }
        return buildInvoice(account, allBills, movements, month, bill, LocalDate.now())
    }

    fun currentInvoiceFor(
        account: FinanceAccount,
        bills: List<CreditBill>,
        movements: List<Movement>,
        today: LocalDate = LocalDate.now(),
    ): InvoiceSummary {
        val allBills = billsWithProjections(account, bills, movements, today)
        val bill = currentBillFor(account, allBills, movements, today)
        val month = bill?.let { monthOf(it.dueDate) } ?: YearMonth.now()
        return buildInvoice(account, allBills, movements, month, bill, today)
    }

    fun currentBillFor(
        account: FinanceAccount,
        bills: List<CreditBill>,
        today: LocalDate = LocalDate.now(),
    ): CreditBill? = currentBillFor(account, bills, emptyList(), today)

    fun currentBillFor(
        account: FinanceAccount,
        bills: List<CreditBill>,
        movements: List<Movement>,
        today: LocalDate = LocalDate.now(),
    ): CreditBill? = currentBillOf(
        account,
        billsWithProjections(account, bills, movements, today),
        movements,
        today,
    )

    /**
     * Fatura atual a partir de uma lista que já passou pelas projeções.
     * Evita projetar duas vezes o mesmo cartão.
     */
    private fun currentBillOf(
        account: FinanceAccount,
        accountBills: List<CreditBill>,
        movements: List<Movement>,
        today: LocalDate,
    ): CreditBill? {
        if (accountBills.isEmpty()) return null
        val cycle = currentCycleMonth(account, accountBills, today)
        val settled = settledBillIds(accountBills, movements, today, cycle)
        // Inclui faturas projetadas com total zero: o mês aberto atual pode ainda
        // não ter compras, mas precisa aparecer como "atual" (ex.: dezembro).
        val unpaid = accountBills.filter { it.id !in settled }
        val fromCycle = if (cycle != null) {
            unpaid.filter { !monthOf(it.dueDate).isBefore(cycle) }
        } else {
            emptyList()
        }
        return fromCycle.minByOrNull { it.dueDate }
            ?: unpaid.minByOrNull { it.dueDate }
            ?: accountBills.lastOrNull()
    }

    /**
     * Mês do ciclo atual do cartão. Usa a data de vencimento informada pela conta
     * (balanceDueDate) e, quando ela não é confiável, a última fatura até 45 dias
     * à frente.
     */
    private fun currentCycleMonth(
        account: FinanceAccount,
        bills: List<CreditBill>,
        today: LocalDate,
    ): YearMonth? {
        val current = YearMonth.from(today)
        val accountDue = parseIsoDate(account.dueDate)
        if (accountDue != null) {
            val month = monthOf(accountDue)
            if (!month.isBefore(current.minusMonths(2)) && !month.isAfter(current.plusMonths(2))) {
                return month
            }
        }
        val horizon = today.plusDays(45).atStartOfDay(zone).toInstant().toEpochMilli()
        return bills
            .filter { !isProjectedBill(it) && it.dueDate <= horizon }
            .maxByOrNull { it.dueDate }
            ?.let { monthOf(it.dueDate) }
    }

    fun invoiceForBill(
        account: FinanceAccount,
        bill: CreditBill,
        bills: List<CreditBill>,
        movements: List<Movement>,
        today: LocalDate = LocalDate.now(),
    ): InvoiceSummary = buildInvoice(account, bills, movements, monthOf(bill.dueDate), bill, today)

    /**
     * Classifica todas as faturas de um cartao em atual (aberta/vencida),
     * futuras e pagas, cada uma com os seus proprios lancamentos.
     */
    fun invoiceEntries(
        account: FinanceAccount,
        bills: List<CreditBill>,
        movements: List<Movement>,
        today: LocalDate = LocalDate.now(),
    ): List<InvoiceEntry> {
        val accountBills = billsWithProjections(account, bills, movements, today)
        if (accountBills.isEmpty()) return emptyList()
        val cycle = currentCycleMonth(account, accountBills, today)
        val settled = settledBillIds(accountBills, movements, today, cycle)
        val current = currentBillOf(account, accountBills, movements, today)
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        // Um mês aparece uma única vez. Alguns bancos devolvem a fatura do mês
        // aberto ainda vazia, e a projeção do mesmo mês entrava junto: a faixa
        // de faturas mostrava "Nov" duas vezes.
        return accountBills
            .map { bill ->
                val projected = isProjectedBill(bill)
                val isSettled = bill.id in settled
                val isCurrent = bill.id == current?.id
                val status = when {
                    isSettled -> InvoiceStatus.PAID
                    projected -> if (isCurrent) InvoiceStatus.OPEN else InvoiceStatus.FUTURE
                    isCurrent -> if (bill.dueDate < todayStart) InvoiceStatus.OVERDUE else InvoiceStatus.OPEN
                    bill.dueDate < todayStart -> InvoiceStatus.OVERDUE
                    else -> InvoiceStatus.FUTURE
                }
                InvoiceEntry(
                    bill = bill,
                    status = status,
                    summary = invoiceForBill(account, bill, accountBills, movements, today),
                    isCurrent = isCurrent,
                    isProjected = projected,
                )
            }
            .sortedWith(compareBy({ it.summary.month }, { if (it.isProjected) 1 else 0 }))
            .fold(mutableListOf<InvoiceEntry>()) { acc, entry ->
                val index = acc.indexOfFirst { it.summary.month == entry.summary.month }
                if (index < 0) {
                    acc.add(entry)
                } else {
                    acc[index] = betterEntry(acc[index], entry)
                }
                acc
            }
    }

    /**
     * Entre duas faturas do mesmo mês, qual vale a pena mostrar. Uma fatura
     * real já fechada tem prioridade; sem ela, fica a de maior total (a
     * projeção calculada a partir dos lançamentos).
     */
    private fun betterEntry(a: InvoiceEntry, b: InvoiceEntry): InvoiceEntry {
        val aClosed = !a.isProjected && a.summary.total > MONEY_EPS
        val bClosed = !b.isProjected && b.summary.total > MONEY_EPS
        return when {
            aClosed && !bClosed -> a
            bClosed && !aClosed -> b
            a.summary.total >= b.summary.total -> a
            else -> b
        }
    }

    fun currentInvoiceMonth(
        accounts: List<FinanceAccount>,
        bills: List<CreditBill>,
        movements: List<Movement> = emptyList(),
        today: LocalDate = LocalDate.now(),
    ): YearMonth? = accounts
        .filter { it.isCredit }
        .mapNotNull { currentBillFor(it, bills, movements, today)?.let { bill -> monthOf(bill.dueDate) } }
        .maxOrNull()

    /**
     * Quitação de fatura. Alguns bancos (Pluggy/Open Finance) registram o pagamento
     * na própria fatura; outros registram o pagamento da fatura N na fatura N+1.
     * A regra abaixo aceita os dois formatos e evita o falso positivo de usar o
     * pagamento da fatura anterior para quitar a fatura atual.
     */
    fun settledBillIds(
        bills: List<CreditBill>,
        movements: List<Movement> = emptyList(),
        today: LocalDate = LocalDate.now(),
        cycleMonth: YearMonth? = null,
    ): Set<String> {
        val settled = mutableSetOf<String>()
        val staleCutoff = today.minusMonths(STALE_BILL_MONTHS)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val cycleStart = cycleMonth?.atDay(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()

        bills.groupBy { it.accountId }.values.forEach { group ->
            val sorted = group.sortedBy { it.dueDate }
            val convention = detectSettlementConvention(sorted)
            val movementPaid = movementPayments(sorted, movements)

            sorted.forEachIndexed { index, bill ->
                if (bill.totalAmount <= MONEY_EPS && !isProjectedBill(bill)) {
                    settled += bill.id
                    return@forEachIndexed
                }
                val prev = sorted.getOrNull(index - 1)
                val next = sorted.getOrNull(index + 1)

                val ownPaid = bill.paidAmount
                val ownCovers = ownPaid > MONEY_EPS && ownPaid + MONEY_EPS >= bill.totalAmount
                val nextPaid = next?.paidAmount ?: 0.0
                val nextCovers = next != null && nextPaid > MONEY_EPS &&
                    nextPaid + MONEY_EPS >= bill.totalAmount

                val ownSettles = ownCovers &&
                    !ownPaymentBelongsToPrevious(bill, prev, convention)
                val nextSettles = nextCovers &&
                    nextPaymentBelongsToCurrent(bill, next, convention)
                val movementSettles = bill.totalAmount > MONEY_EPS &&
                    (movementPaid[bill.id] ?: 0.0) + MONEY_EPS >= bill.totalAmount
                if (ownSettles || nextSettles || movementSettles) settled += bill.id
            }

            // Faturas anteriores ao ciclo atual são históricas: o banco já as
            // fechou. Sem essa regra, uma fatura antiga sem pagamento na Pluggy
            // apareceria como vencida (ex.: maio) mesmo com o cartão em dia.
            sorted.forEachIndexed { index, bill ->
                if (isProjectedBill(bill) || bill.totalAmount <= MONEY_EPS) return@forEachIndexed
                val beforeCurrentCycle = cycleStart != null && bill.dueDate < cycleStart
                val stale = bill.dueDate < staleCutoff && index < sorted.lastIndex
                if (beforeCurrentCycle || stale) settled += bill.id
            }
        }
        return settled
    }

    private fun paidOnOrBeforeClosing(bill: CreditBill): Boolean {
        val paidAt = bill.lastPaymentDate ?: return false
        val closing = bill.closingDate ?: return false
        return !toLocalDate(paidAt).isAfter(toLocalDate(closing))
    }

    private fun paidAfterClosing(bill: CreditBill): Boolean {
        val paidAt = bill.lastPaymentDate ?: return false
        val closing = bill.closingDate ?: return false
        return toLocalDate(paidAt).isAfter(toLocalDate(closing))
    }

    /**
     * Um pagamento registrado na propria fatura pertence a fatura anterior quando o
     * valor (ou, em caso de empate, a data em relacao ao fechamento) aponta para ela.
     */
    private fun ownPaymentBelongsToPrevious(
        bill: CreditBill,
        prev: CreditBill?,
        convention: SettlementConvention,
    ): Boolean {
        if (prev == null) return false
        val paid = bill.paidAmount
        if (paid <= MONEY_EPS) return false
        val expectedPrevious = prev.totalAmount + bill.financeCharges
        val ownExpected = bill.totalAmount
        val nearPrevious = near(paid, expectedPrevious, prev.totalAmount)
        val nearOwn = near(paid, ownExpected, ownExpected)
        return when {
            nearPrevious && closer(paid, expectedPrevious, ownExpected) -> true
            nearOwn && closer(paid, ownExpected, expectedPrevious) -> false
            nearPrevious || nearOwn -> when {
                paidOnOrBeforeClosing(bill) -> true
                paidAfterClosing(bill) -> false
                else -> convention == SettlementConvention.NEXT
            }
            else -> false
        }
    }

    /**
     * Um pagamento registrado na fatura seguinte pertence a fatura atual quando o
     * valor (ou, em caso de empate, a data em relacao ao fechamento) aponta para ela.
     */
    private fun nextPaymentBelongsToCurrent(
        current: CreditBill,
        next: CreditBill?,
        convention: SettlementConvention,
    ): Boolean {
        if (next == null) return false
        val paid = next.paidAmount
        if (paid <= MONEY_EPS) return false
        val expectedOwn = next.totalAmount
        val expectedCurrent = current.totalAmount + next.financeCharges
        val nearOwn = near(paid, expectedOwn, expectedOwn)
        val nearCurrent = near(paid, expectedCurrent, current.totalAmount)
        return when {
            nearOwn && closer(paid, expectedOwn, expectedCurrent) -> false
            nearCurrent && closer(paid, expectedCurrent, expectedOwn) -> true
            paidOnOrBeforeClosing(next) -> true
            paidAfterClosing(next) -> false
            else -> convention != SettlementConvention.OWN
        }
    }

    private fun paidForBill(
        bill: CreditBill,
        bills: List<CreditBill>,
        movements: List<Movement>,
        settled: Set<String>,
    ): Double {
        if (bill.totalAmount <= MONEY_EPS) return 0.0
        if (bill.id in settled) return bill.totalAmount
        val accountBills = bills.filter { it.accountId == bill.accountId }.sortedBy { it.dueDate }
        val convention = detectSettlementConvention(accountBills)
        val prev = previousBillFor(bill, bills)
        val next = nextBillFor(bill, bills)
        val candidates = mutableListOf<Double>()
        if (bill.paidAmount > MONEY_EPS &&
            !ownPaymentBelongsToPrevious(bill, prev, convention)
        ) {
            candidates += bill.paidAmount
        }
        if (nextPaymentBelongsToCurrent(bill, next, convention)) {
            candidates += next?.paidAmount ?: 0.0
        }
        val fromMovements = movementPayments(accountBills, movements)[bill.id] ?: 0.0
        if (fromMovements > MONEY_EPS) candidates += fromMovements
        return (candidates.maxOrNull() ?: 0.0).coerceAtMost(bill.totalAmount)
    }

    private data class PaymentEvent(val amount: Double, val date: Long, val title: String)

    /**
     * Alguns bancos (ex.: Santander) não preenchem bill.payments. O pagamento chega
     * como crédito no cartão ou como débito na conta corrente ("PAGAMENTO FATURA").
     * Aqui os lançamentos são associados às faturas por valor e data.
     */
    private fun paymentEventsFor(accountId: String, movements: List<Movement>): List<PaymentEvent> =
        movements.filter { movement ->
            if (movement.amount <= MONEY_EPS) return@filter false
            if (movement.accountId == accountId && movement.isCreditCard) {
                movement.isIncome || isCardPaymentDescription(movement.title)
            } else if (!movement.isCreditCard && !movement.isIncome) {
                isCardPaymentDescription(movement.title)
            } else {
                false
            }
        }
            .filterNot { movement ->
                val title = movement.title.uppercase(locale)
                title.contains("ESTORNO") || title.contains("CASHBACK")
            }
            .map { PaymentEvent(it.amount, it.date, it.title) }
            .sortedBy { it.date }

    private fun isCardPaymentDescription(title: String): Boolean {
        val value = title.uppercase(locale)
        val payment = value.contains("PAGAMENTO") || value.contains("PAGTO") ||
            value.contains("PAG.")
        val card = value.contains("FATURA") || value.contains("CARTAO") ||
            value.contains("CARTÃO") || value.contains("VISA") ||
            value.contains("MASTER") || value.contains("ELO") ||
            value.contains("AMEX") || value.contains("CREDITO") ||
            value.contains("CRÉDITO")
        return payment && card
    }

    private fun movementPayments(
        accountBills: List<CreditBill>,
        movements: List<Movement>,
    ): Map<String, Double> {
        if (accountBills.isEmpty()) return emptyMap()
        val events = paymentEventsFor(accountBills.first().accountId, movements)
        if (events.isEmpty()) return emptyMap()
        val sorted = accountBills.sortedBy { it.dueDate }
        val used = BooleanArray(events.size)
        val paid = mutableMapOf<String, Double>()

        fun windowFor(index: Int, bill: CreditBill): LongRange {
            val previous = sorted.getOrNull(index - 1)
            val start = previous?.closingDate ?: previous?.dueDate
                ?: (bill.dueDate - 45L * DAY_MS)
            val end = bill.dueDate + PAYMENT_WINDOW_DAYS * DAY_MS
            return start..end
        }

        sorted.forEachIndexed { index, bill ->
            if (bill.totalAmount <= MONEY_EPS) return@forEachIndexed
            val window = windowFor(index, bill)
            val candidates = events.indices.filter { eventIndex ->
                !used[eventIndex] &&
                    events[eventIndex].date in window &&
                    nearPayment(events[eventIndex].amount, bill.totalAmount)
            }
            if (candidates.isNotEmpty()) {
                val chosen = candidates.minByOrNull { eventIndex ->
                    kotlin.math.abs(events[eventIndex].date - bill.dueDate)
                } ?: return@forEachIndexed
                used[chosen] = true
                paid[bill.id] = events[chosen].amount
            }
        }

        // Pagamentos fracionados: soma dos lançamentos do ciclo.
        sorted.forEachIndexed { index, bill ->
            if (bill.id in paid || bill.totalAmount <= MONEY_EPS) return@forEachIndexed
            val window = windowFor(index, bill)
            val indices = events.indices.filter { eventIndex ->
                !used[eventIndex] && events[eventIndex].date in window
            }
            if (indices.isEmpty()) return@forEachIndexed
            val sum = indices.sumOf { events[it].amount }
            if (nearPayment(sum, bill.totalAmount)) {
                indices.forEach { used[it] = true }
                paid[bill.id] = sum
            }
        }

        return paid
    }

    private fun nearPayment(amount: Double, expected: Double): Boolean =
        kotlin.math.abs(amount - expected) <=
            kotlin.math.max(0.05, kotlin.math.abs(expected) * 0.001)

    private fun buildInvoice(
        account: FinanceAccount,
        bills: List<CreditBill>,
        movements: List<Movement>,
        month: YearMonth,
        bill: CreditBill?,
        today: LocalDate = LocalDate.now(),
    ): InvoiceSummary {
        val openMonth = resolveOpenMonth(account, bills, movements, today)
        val billMovements = if (bill != null) {
            if (isProjectedBill(bill)) {
                movementsForProjectedBill(account, bill, bills, movements, today, openMonth)
            } else {
                assignMovementsToBills(account, bills, movements, today, openMonth)[bill.id]
                    .orEmpty()
            }
        } else {
            movements.filter {
                it.accountId == account.id && it.isCreditCard && monthOf(it.date) == month
            }
        }

        val projected = bill != null && isProjectedBill(bill)
        val total = when {
            // Faturas fechadas usam o total oficial do banco.
            bill != null && !projected && bill.totalAmount > 0.0 -> bill.totalAmount
            else -> projectedTotalFor(billMovements)
        }
        val cycle = currentCycleMonth(account, bills, today)
        val settled = settledBillIds(bills, movements, today, cycle)
        val paid = when {
            bill == null -> 0.0
            else -> paidForBill(bill, bills, movements, settled)
        }

        return InvoiceSummary(
            account = account,
            bill = bill,
            month = bill?.let { monthOf(it.dueDate) } ?: month,
            total = total,
            paid = paid,
            minimumPayment = bill?.minimumPayment,
            dueDate = bill?.dueDate ?: parseIsoDate(account.dueDate),
            closingDate = bill?.closingDate ?: parseIsoDate(account.closeDate),
            // Pagamentos ficam representados em `paid`; a lista mostra compras e
            // estornos para que o total da fatura possa ser conferido linha a linha.
            movements = billMovements
                .filter { !it.isIncome || !isPaymentMovement(it) }
                .sortedByDescending { it.date },
        )
    }

    /**
     * Total de uma fatura projetada: compras menos estornos (créditos que não são
     * pagamento de fatura). Pagamentos não entram no total; eles apenas quitam a
     * fatura anterior e aparecem em `paid`.
     */
    private fun projectedTotalFor(movements: List<Movement>): Double {
        val expenses = movements.filter { !it.isIncome }.sumOf { it.amount }
        val refunds = movements.filter { it.isIncome && !isPaymentMovement(it) }
            .sumOf { it.amount }
        return roundCents((expenses - refunds).coerceAtLeast(0.0))
    }

    private fun isPaymentMovement(movement: Movement): Boolean {
        if (!movement.isIncome) return false
        val title = movement.title.uppercase(locale)
        return title.contains("PAGAMENTO") || title.contains("PAGTO") ||
            title.contains("PAG.")
    }

    /**
     * Mês de referência da fatura para os lançamentos do cartão. A Pluggy informa
     * billForecastDate no mês de fechamento; quando a fatura não tem fechamento,
     * usa o mês do vencimento.
     */
    private fun billForecastMonth(bill: CreditBill): YearMonth =
        bill.closingDate?.let { YearMonth.from(toLocalDate(it)) } ?: monthOf(bill.dueDate)

    private fun movementBillMonth(movement: Movement): YearMonth? =
        movement.billMonth?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

    private fun Movement.invoiceKey(): String {
        val cents = Math.round(amount * 100)
        return listOf(
            title.trim().lowercase(locale),
            cents.toString(),
            date.toString(),
            (installmentNumber ?: 0).toString(),
        ).joinToString("|")
    }

    /**
     * Associa cada lançamento do cartão a exatamente uma fatura, nesta ordem:
     * billForecastDate (mês de fechamento informado pelo banco), billId e, por
     * último, a janela de datas. Isso evita o mesmo lançamento em duas faturas,
     * corrige conectores que repetem o billId na fatura seguinte e remove
     * duplicatas exatas devolvidas pelo banco.
     */
    private fun assignMovementsToBills(
        account: FinanceAccount,
        bills: List<CreditBill>,
        movements: List<Movement>,
        today: LocalDate = LocalDate.now(),
        openMonth: YearMonth = resolveOpenMonth(account, bills, movements, today),
    ): Map<String, List<Movement>> {
        val accountMovements = dedupedCardMovements(account, movements)
        if (accountMovements.isEmpty() || bills.isEmpty()) return emptyMap()
        val accountBills = bills.filter { it.accountId == account.id }.sortedBy { it.dueDate }
        if (accountBills.isEmpty()) return emptyMap()
        val actualBills = accountBills.filter { !isProjectedBill(it) }
        if (actualBills.isEmpty()) return emptyMap()
        val byForecast = actualBills.groupBy { billForecastMonth(it) }
        val byDueMonth = actualBills.groupBy { monthOf(it.dueDate) }
        val knownBillIds = actualBills.mapTo(HashSet()) { it.id }
        val openBill = accountBills.firstOrNull { monthOf(it.dueDate) == openMonth }
            ?: accountBills.firstOrNull { isProjectedBill(it) }
        val result = LinkedHashMap<String, MutableList<Movement>>()
        val used = HashSet<String>()

        fun place(movement: Movement, bill: CreditBill) {
            if (!used.add(movement.id)) return
            result.getOrPut(bill.id) { mutableListOf() } += movement
        }

        // 1) billId: a fatura já foi fechada pelo banco e o lançamento pertence a ela.
        accountMovements.forEach { movement ->
            val billId = movement.billId ?: return@forEach
            if (billId !in knownBillIds) return@forEach
            val bill = actualBills.firstOrNull { it.id == billId } ?: return@forEach
            place(movement, bill)
        }
        // 2) billForecastDate (mês de fechamento) para lançamentos já postados.
        //    PENDING sem billId fica para a fatura aberta: o banco costuma
        //    rolá-los para o ciclo seguinte mesmo com previsão anterior.
        accountMovements.forEach { movement ->
            if (movement.id in used) return@forEach
            if (movement.status.equals("PENDING", true)) return@forEach
            val month = movementBillMonth(movement) ?: return@forEach
            val bill = byForecast[month]?.firstOrNull()
                ?: byDueMonth[month]?.firstOrNull()
                ?: return@forEach
            place(movement, bill)
        }
        // 3) Janela de datas para lançamentos postados sem previsão.
        accountMovements.forEach { movement ->
            if (movement.id in used) return@forEach
            if (movement.status.equals("PENDING", true)) return@forEach
            val bill = actualBills.firstOrNull { candidate ->
                val index = actualBills.indexOf(candidate)
                val previous = actualBills.getOrNull(index - 1)
                val end = candidate.closingDate ?: candidate.dueDate
                val start = cycleStartFor(end, previous)
                movement.date > start && movement.date <= dayStart(end)
            } ?: return@forEach
            place(movement, bill)
        }
        // 4) PENDING sem billId pertence à fatura aberta (atual).
        if (openBill != null) {
            val openForecast = billForecastMonth(openBill)
            accountMovements.forEach { movement ->
                if (movement.id in used) return@forEach
                if (!movement.status.equals("PENDING", true)) return@forEach
                val month = movementBillMonth(movement)
                if (month != null && month > openForecast) return@forEach
                place(movement, openBill)
            }
        }
        return result.mapValues { (_, list) ->
            list.distinctBy { it.id }.distinctBy { it.invoiceKey() }
        }
    }

    /**
     * Remove a duplicata que alguns conectores devolvem no mesmo dia: a mesma
     * compra aparece uma vez com billForecastDate e outra sem. A versão com mês
     * previsto é a confiável.
     */
    private fun dedupedCardMovements(
        account: FinanceAccount,
        movements: List<Movement>,
    ): List<Movement> {
        val raw = movements.filter { it.accountId == account.id && it.isCreditCard }
        return raw.filterNot { movement ->
            movementBillMonth(movement) == null && raw.any { other ->
                other.id != movement.id &&
                    other.title.trim().equals(movement.title.trim(), ignoreCase = true) &&
                    Math.round(other.amount * 100) == Math.round(movement.amount * 100) &&
                    toLocalDate(other.date) == toLocalDate(movement.date) &&
                    movementBillMonth(other) != null
            }
        }
    }

    /** Primeira fatura real ainda não quitada a partir do ciclo atual. */
    private fun openActualBill(
        actual: List<CreditBill>,
        movements: List<Movement>,
        today: LocalDate,
        cycleMonth: YearMonth?,
    ): CreditBill? {
        val settled = settledBillIds(actual, movements, today, cycleMonth)
        return actual
            .filter { bill ->
                bill.id !in settled && bill.totalAmount > MONEY_EPS &&
                    (cycleMonth == null || !monthOf(bill.dueDate).isBefore(cycleMonth))
            }
            .minByOrNull { it.dueDate }
    }

    /**
     * Mês da fatura aberta. Quando o banco não devolve a fatura aberta em
     * /bills, ela é a primeira fatura projetada.
     */
    private fun resolveOpenMonth(
        account: FinanceAccount,
        bills: List<CreditBill>,
        movements: List<Movement>,
        today: LocalDate,
    ): YearMonth {
        val actual = bills
            .filter { it.accountId == account.id && !isProjectedBill(it) }
            .sortedBy { it.dueDate }
        val projected = bills
            .filter { it.accountId == account.id && isProjectedBill(it) }
            .sortedBy { it.dueDate }
        val cycleMonth = currentCycleMonth(account, actual, today)
        val openActual = openActualBill(actual, movements, today, cycleMonth)
        return openActual?.let { monthOf(it.dueDate) }
            ?: projected.firstOrNull()?.let { monthOf(it.dueDate) }
            ?: YearMonth.from(today)
    }

    /**
     * Lançamentos de uma fatura projetada. A fatura aberta usa uma janela de 45
     * dias antes do fechamento para capturar compras PENDING que o banco rolou
     * para o ciclo seguinte (ex.: PIX de 12–25/10 na fatura de dezembro). As
     * faturas futuras usam a janela normal mais as parcelas sintéticas.
     */
    private fun movementsForProjectedBill(
        account: FinanceAccount,
        bill: CreditBill,
        bills: List<CreditBill>,
        movements: List<Movement>,
        today: LocalDate,
        openMonth: YearMonth,
    ): List<Movement> {
        val deduped = dedupedCardMovements(account, movements)
        val actualBills = bills
            .filter { it.accountId == account.id && !isProjectedBill(it) }
            .sortedBy { it.dueDate }
        val knownBillIds = actualBills.mapTo(HashSet()) { it.id }
        val forecastMonth = billForecastMonth(bill)
        val closing = bill.closingDate ?: bill.dueDate
        val closingDay = dayStart(closing)
        val previous = bills
            .filter { it.accountId == account.id && it.dueDate < bill.dueDate }
            .maxByOrNull { it.dueDate }
        val isOpen = monthOf(bill.dueDate) == openMonth
        val windowStart = if (isOpen) {
            closingDay - 45L * DAY_MS
        } else {
            cycleStartFor(closing, previous)
        }
        val base = deduped.filter { movement ->
            val billId = movement.billId
            if (billId != null && billId in knownBillIds) return@filter false
            val movementMonth = movementBillMonth(movement)
            val inWindow = if (isOpen) {
                movement.date >= windowStart && movement.date <= closingDay
            } else {
                movement.date > windowStart && movement.date <= closingDay
            }
            if (isOpen) {
                val pending = movement.status.equals("PENDING", true)
                inWindow && (movementMonth == null || movementMonth == forecastMonth ||
                    (pending && movementMonth < forecastMonth))
            } else {
                movementMonth == forecastMonth || (movementMonth == null && inWindow)
            }
        }
        val installments = projectedInstallmentsForMonth(
            account, bill, bills, movements, today, openMonth,
        )
        return (base + installments)
            .distinctBy { it.id }
            .distinctBy { it.invoiceKey() }
    }
    /**
     * Início do ciclo de cobrança de uma fatura. Usa o fechamento da fatura
     * anterior, mas limita a janela a 45 dias. Sem esse limite, quando a Pluggy
     * não devolve as faturas intermediárias, a primeira fatura projetada acaba
     * somando meses de lançamentos antigos (ex.: faturas de 2023 e compras de 2026).
     */
    private fun cycleStartFor(end: Long, previous: CreditBill?): Long {
        val fallback = toLocalDate(end).minusMonths(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val raw = previous?.closingDate ?: previous?.dueDate ?: fallback
        return maxOf(raw, end - 45L * DAY_MS)
    }

    private fun nextBillFor(bill: CreditBill, bills: List<CreditBill>): CreditBill? =
        bills
            .filter { it.accountId == bill.accountId && it.dueDate > bill.dueDate }
            .minByOrNull { it.dueDate }

    private fun previousBillFor(bill: CreditBill, bills: List<CreditBill>): CreditBill? =
        bills
            .filter { it.accountId == bill.accountId && it.dueDate < bill.dueDate }
            .maxByOrNull { it.dueDate }

    private fun isProjectedBill(bill: CreditBill): Boolean = bill.id.startsWith(PROJECTED_PREFIX)

    /**
     * Cria faturas projetadas para os próximos meses quando o banco não devolve
     * essas faturas em /bills, mas já existem compras/parcelas futuras nos
     * lançamentos. As projeções não são persistidas; são recalculadas a cada sync.
     */
    private fun billsWithProjections(
        account: FinanceAccount,
        bills: List<CreditBill>,
        movements: List<Movement>,
        today: LocalDate,
    ): List<CreditBill> {
        val actual = bills
            .filter { it.accountId == account.id && !isProjectedBill(it) }
            .sortedBy { it.dueDate }
        if (actual.isEmpty()) return emptyList()

        val currentMonth = YearMonth.from(today)
        // Faturas reais com total 0 não devem esconder a projeção do mesmo mês.
        val meaningful = actual.filter { it.totalAmount > MONEY_EPS }
        val coveredMonths = meaningful.mapTo(HashSet()) { monthOf(it.dueDate) }
        val anchor = meaningful.lastOrNull { monthOf(it.dueDate) <= currentMonth.plusMonths(1) }
            ?: meaningful.lastOrNull()
            ?: actual.last()
        val lastDue = toLocalDate(anchor.dueDate)
        val dueDay = lastDue.dayOfMonth
        val closingDay = anchor.closingDate?.let { toLocalDate(it).dayOfMonth }
            ?: (dueDay - 7).coerceAtLeast(1)
        val cycleMonth = currentCycleMonth(account, actual, today)
        val openActual = openActualBill(actual, movements, today, cycleMonth)
        // A projeção começa no mês seguinte à última fatura real com total. Se a
        // última fatura real vier vazia (total 0), o próprio mês dela ainda
        // precisa de projeção — senão o mês sumia da faixa de faturas.
        val anchorMonth = monthOf(anchor.dueDate)
        val startMonth = maxOf(
            if (anchor.totalAmount > MONEY_EPS) anchorMonth.plusMonths(1) else anchorMonth,
            cycleMonth ?: currentMonth.minusMonths(2),
        )
        val openMonth = openActual?.let { monthOf(it.dueDate) } ?: startMonth
        val endMonth = currentMonth.plusMonths(8)
        val combined = actual.toMutableList()

        var month = startMonth
        var guard = 0
        var firstProjection = true
        while (month <= endMonth && guard++ < 24) {
            if (month !in coveredMonths) {
                // Uma fatura real vazia (total 0) do mesmo mês é substituída
                // pela projeção. Mantendo as duas, o mês apareceria duplicado
                // na faixa de faturas e a projeção não contaria os lançamentos
                // que apontam para a fatura vazia.
                val emptyReal = combined.filter { bill ->
                    !isProjectedBill(bill) &&
                        bill.totalAmount <= MONEY_EPS &&
                        monthOf(bill.dueDate) == month
                }
                if (emptyReal.isNotEmpty()) {
                    combined.removeAll(emptyReal.toSet())
                }
                val dueDate = month.atDay(kotlin.math.min(dueDay, month.lengthOfMonth()))
                // O fechamento pode cair no mês anterior ao vencimento (ex.: vence
                // 02/12 e fecha 26/11). Usar o mesmo mês do vencimento somava o
                // ciclo errado e escondia as compras de dezembro.
                val closingMonth = if (closingDay <= dueDay) month else month.minusMonths(1)
                val closingDate = closingMonth.atDay(
                    kotlin.math.min(closingDay, closingMonth.lengthOfMonth()),
                )
                val dueEpoch = dueDate.atStartOfDay(zone).toInstant().toEpochMilli()
                val closingEpoch = closingDate.atStartOfDay(zone).toInstant().toEpochMilli()
                val draft = CreditBill(
                    id = PROJECTED_PREFIX + account.id + "-" + month,
                    accountId = account.id,
                    dueDate = dueEpoch,
                    closingDate = closingEpoch,
                    totalAmount = 0.0,
                    paidAmount = 0.0,
                )
                val projectedMovements = movementsForProjectedBill(
                    account, draft, combined, movements, today, openMonth,
                )
                val projectedTotal = projectedTotalFor(projectedMovements)
                // A primeira fatura projetada entra mesmo com total zero para que
                // o mês aberto atual apareça (ex.: dezembro ainda sem compras).
                if (projectedTotal > MONEY_EPS || firstProjection) {
                    combined += draft.copy(totalAmount = projectedTotal)
                }
                firstProjection = false
            }
            month = month.plusMonths(1)
        }
        return combined.sortedBy { it.dueDate }
    }

    /**
     * Última fatura real conhecida, usada como referência para o dia de fechamento
     * e para projetar as parcelas que ainda não chegaram pela Pluggy.
     */
    private fun anchorBill(bills: List<CreditBill>, today: LocalDate): CreditBill? {
        val actual = bills
            .filter { !isProjectedBill(it) && it.totalAmount > MONEY_EPS }
            .sortedBy { it.dueDate }
        val current = YearMonth.from(today)
        return actual.lastOrNull { monthOf(it.dueDate) <= current.plusMonths(1) }
            ?: actual.lastOrNull()
    }

    private fun billMonthForPurchase(date: Long, anchor: CreditBill): YearMonth {
        val dueDay = toLocalDate(anchor.dueDate).dayOfMonth
        val closingDay = anchor.closingDate?.let { toLocalDate(it).dayOfMonth }
            ?: (dueDay - 7).coerceAtLeast(1)
        val offset = if (closingDay <= dueDay) 0L else 1L
        val purchase = toLocalDate(date)
        val closingThisMonth = purchase.withDayOfMonth(
            kotlin.math.min(closingDay, purchase.lengthOfMonth()),
        )
        val closingMonth = if (purchase.isAfter(closingThisMonth)) purchase.plusMonths(1) else purchase
        return YearMonth.from(closingMonth).plusMonths(offset)
    }

    /**
     * Parcelas futuras sintéticas quando a Pluggy não devolve as transações dos
     * meses seguintes. Elas só entram em faturas projetadas e não são persistidas.
     */
    private fun projectedInstallmentsForMonth(
        account: FinanceAccount,
        bill: CreditBill,
        bills: List<CreditBill>,
        movements: List<Movement>,
        today: LocalDate,
        openMonth: YearMonth,
    ): List<Movement> {
        val billMonth = monthOf(bill.dueDate)
        val anchor = anchorBill(bills, today) ?: return emptyList()
        val accountMovements = dedupedCardMovements(account, movements)
        val result = mutableListOf<Movement>()

        val plans = accountMovements
            .filter {
                (it.totalInstallments ?: 0) > 1 && (it.installmentNumber ?: 0) >= 1
            }
            .groupBy { planKey(it) }

        plans.forEach { (_, rows) ->
            if (isRefundedPlan(rows, accountMovements)) return@forEach
            val latest = rows.maxByOrNull { it.installmentNumber ?: 0 } ?: return@forEach
            val first = rows.minByOrNull { it.installmentNumber ?: Int.MAX_VALUE }
                ?: return@forEach
            val total = latest.totalInstallments ?: return@forEach
            val current = latest.installmentNumber ?: return@forEach
            if (current >= total) return@forEach
            val byBill = latest.billId?.let { id ->
                bills.firstOrNull { it.id == id && !isProjectedBill(it) }
                    ?.let { monthOf(it.dueDate) }
            }
            val anchorMonth = byBill ?: if (latest.status.equals("PENDING", true)) {
                openMonth
            } else {
                billMonthForPurchase(latest.date, anchor)
            }

            for (number in (current + 1)..total) {
                if (anchorMonth.plusMonths((number - current).toLong()) != billMonth) continue
                val exists = accountMovements.any {
                    planKey(it) == planKey(latest) && it.installmentNumber == number
                }
                if (exists) continue
                result += Movement(
                    id = "projected-installment-" + latest.id + "-" + number,
                    title = first.title,
                    // Usa o valor da parcela mais recente: bancos ajustam centavos
                    // depois da primeira (ex.: 298,35 → 298,31).
                    amount = latest.amount,
                    type = MovementType.EXPENSE,
                    category = first.category,
                    date = bill.dueDate,
                    accountId = account.id,
                    accountName = account.displayName,
                    isCreditCard = true,
                    installmentNumber = number,
                    totalInstallments = total,
                    billId = bill.id,
                    status = "PENDING",
                    source = MovementSource.PLUGGY,
                )
            }
        }
        return result
    }

    /**
     * Identifica um parcelamento pelo título, número de parcelas e valor
     * arredondado. O arredondamento agrupa variações de centavos da mesma
     * compra (ex.: 298,35 e 298,31) sem juntar compras diferentes com o mesmo
     * título (ex.: 94,98 e 239,14).
     */
    private fun planKey(movement: Movement): String {
        val roundedAmount = Math.round(movement.amount)
        return listOf(
            movement.accountId.orEmpty(),
            movement.title.trim().lowercase(locale),
            (movement.totalInstallments ?: 0).toString(),
            roundedAmount.toString(),
        ).joinToString("|")
    }

    /** Parcelamento estornado/cancelado: existe um crédito do valor total. */
    private fun isRefundedPlan(
        rows: List<Movement>,
        allMovements: List<Movement>,
    ): Boolean {
        val first = rows.minByOrNull { it.installmentNumber ?: Int.MAX_VALUE } ?: return false
        val total = first.totalInstallments ?: return false
        val planTotal = first.amount * total
        val title = first.title.trim()
        return allMovements.any { movement ->
            movement.isIncome &&
                movement.title.trim().equals(title, ignoreCase = true) &&
                kotlin.math.abs(movement.amount - planTotal) <=
                kotlin.math.max(1.0, planTotal * 0.01)
        }
    }

    private fun near(amount: Double, expected: Double, reference: Double): Boolean =
        kotlin.math.abs(amount - expected) <=
            kotlin.math.max(MONEY_EPS, kotlin.math.abs(reference) * 0.01)

    private fun closer(amount: Double, first: Double, second: Double): Boolean =
        kotlin.math.abs(amount - first) + MONEY_EPS < kotlin.math.abs(amount - second)

    private enum class SettlementConvention { OWN, NEXT, UNKNOWN }

    private fun detectSettlementConvention(sorted: List<CreditBill>): SettlementConvention {
        var ownScore = 0
        var nextScore = 0
        sorted.forEachIndexed { index, bill ->
            val paid = bill.paidAmount
            if (paid <= MONEY_EPS) return@forEachIndexed
            if (near(paid, bill.totalAmount, bill.totalAmount)) ownScore++
            val prev = sorted.getOrNull(index - 1)
            if (prev != null) {
                val expected = prev.totalAmount + bill.financeCharges
                if (near(paid, expected, prev.totalAmount)) nextScore++
            }
        }
        return when {
            ownScore > nextScore -> SettlementConvention.OWN
            nextScore > ownScore -> SettlementConvention.NEXT
            else -> SettlementConvention.UNKNOWN
        }
    }

    /**
     * Parcelamentos agrupados por conta conectada.
     *
     * A chave de agrupamento inclui o valor arredondado da parcela: o mesmo
     * lojista costuma ter compras parceladas de preços diferentes (ex.:
     * "MERCADOLIVRE*MERCADOLIVRE" em 239,14 e em 94,98). Sem o valor na chave,
     * as duas compras viravam um único parcelamento e a que ainda tinha
     * parcelas em aberto sumia da lista.
     *
     * Cada plano recebe o aviso [InstallmentPlan.settled]: nem todo
     * parcelamento com parcelas faltando continua em aberto. Quando a compra foi
     * quitada de uma vez (antecipadamente) ou cancelada, o banco para de
     * devolver as parcelas restantes e o parcelamento apareceria como ativo para
     * sempre. A fatura é a prova: se todas as parcelas já caíram em faturas
     * pagas, o parcelamento acabou.
     */
    fun installmentPlans(
        movements: List<Movement>,
        bills: List<CreditBill> = emptyList(),
        today: LocalDate = LocalDate.now(),
    ): List<InstallmentPlan> {
        val settledBills = bills
            .groupBy { it.accountId }
            .mapValues { (_, accountBills) -> settledBillIds(accountBills, movements, today) }

        return movements
            .filter { it.isCreditCard && !it.isIncome && (it.totalInstallments ?: 0) > 1 }
            .groupBy { planKey(it) }
            .flatMap { (_, items) -> splitPlans(items) }
            .mapNotNull { items ->
                val latest = items.maxByOrNull { it.installmentNumber ?: 0 } ?: return@mapNotNull null
                val total = latest.totalInstallments ?: return@mapNotNull null
                val current = latest.installmentNumber ?: 0
                InstallmentPlan(
                    title = latest.title,
                    current = current,
                    total = total,
                    installmentAmount = latest.amount,
                    remaining = roundCents(((total - current).coerceAtLeast(0)) * latest.amount),
                    movements = items.sortedBy { it.installmentNumber ?: 0 },
                    accountId = latest.accountId,
                    accountName = latest.accountName,
                    category = latest.category.ifBlank { Categories.OTHER },
                    nextMonth = nextInstallmentMonth(latest, total, current),
                    settled = isPlanSettled(items, current, total, movements, settledBills, today),
                )
            }
            .sortedByDescending { it.remaining }
    }

    /**
     * Decide se o parcelamento ainda está em aberto.
     *
     * - Todas as parcelas caíram: quitado.
     * - Existe um estorno do valor total: compra cancelada.
     * - Todas as parcelas estão em faturas já pagas: quitado de uma vez.
     * - Alguma parcela ainda sem fatura: está em aberto (ciclo atual).
     * - Banco sem informação de fatura: parcelamento sem movimento há muito
     *   tempo também é tratado como quitado.
     */
    private fun isPlanSettled(
        items: List<Movement>,
        current: Int,
        total: Int,
        allMovements: List<Movement>,
        settledBills: Map<String, Set<String>>,
        today: LocalDate,
    ): Boolean {
        if (current >= total) return true
        if (isRefundedPlan(items, allMovements)) return true

        val accountId = items.firstOrNull()?.accountId
        val planBills = items.mapNotNull { it.billId?.takeIf { id -> id.isNotBlank() } }.toSet()
        val paidBills = settledBills[accountId].orEmpty()
        if (planBills.isEmpty() || paidBills.isEmpty()) {
            // Sem informação de fatura: vale a data da última parcela.
            val latestDate = items.maxOfOrNull { it.date } ?: return false
            return monthsSince(latestDate, today) > STALE_INSTALLMENT_MONTHS
        }
        // Parcela sem fatura ainda é do ciclo atual: o parcelamento continua.
        if (items.any { it.billId.isNullOrBlank() }) return false
        return planBills.all { it in paidBills }
    }

    /** Meses transcorridos desde uma data. */
    private fun monthsSince(epoch: Long, today: LocalDate): Int {
        val date = toLocalDate(epoch)
        return (today.year - date.year) * 12 + (today.monthValue - date.monthValue)
    }

    /**
     * Separa compras distintas que compartilham título, total de parcelas e
     * valor (ex.: duas compras de R$ 51,92 em 5x no mesmo lojista). Cada compra
     * começa na menor parcela disponível e as demais entram na compra cuja data
     * esperada é a mais próxima.
     */
    private fun splitPlans(items: List<Movement>): List<List<Movement>> {
        val numbers = items.mapNotNull { it.installmentNumber }.filter { it > 0 }
        val minNumber = numbers.minOrNull() ?: return listOf(items)
        val starts = items.filter { it.installmentNumber == minNumber }.sortedBy { it.date }
        if (starts.size <= 1) return listOf(items)

        val clusters = starts.map { mutableListOf(it) }.toMutableList()
        items.filter { it.installmentNumber != minNumber }.forEach { row ->
            val number = row.installmentNumber ?: 0
            val target = clusters.minByOrNull { cluster ->
                val start = cluster.first()
                kotlin.math.abs(monthsBetween(start.date, row.date) - (number - minNumber))
            }
            target?.add(row)
        }
        return clusters.filter { it.isNotEmpty() }
    }

    /** Diferença em meses de calendário entre duas datas. */
    private fun monthsBetween(from: Long, to: Long): Int {
        val start = toLocalDate(from)
        val end = toLocalDate(to)
        return (end.year - start.year) * 12 + (end.monthValue - start.monthValue)
    }

    /** Mês em que cai a próxima parcela, quando ainda falta alguma. */
    private fun nextInstallmentMonth(
        latest: Movement,
        total: Int,
        current: Int,
    ): YearMonth? {
        if (current >= total) return null
        return monthOf(latest.date).plusMonths(1)
    }

    fun parseIsoDate(value: String?): Long? {
        if (value.isNullOrBlank() || value == "null") return null
        return runCatching {
            LocalDate.parse(value.take(10)).atStartOfDay(zone).toInstant().toEpochMilli()
        }.getOrNull()
    }

    /** Converte um timestamp ISO da Pluggy (ex.: 2026-11-09T17:12:39.585Z) em dd/MM/yyyy HH:mm. */
    fun formatIsoDateTime(value: String?): String? {
        if (value.isNullOrBlank() || value == "null") return null
        return runCatching {
            dateTimeFormatter.format(Instant.parse(value).atZone(zone))
        }.getOrNull()
    }

    // ------------------------------------------------------------- investimentos

    fun investmentTypeLabel(type: String, subtype: String? = null): String = when (type.uppercase()) {
        "FIXED_INCOME" -> "Renda fixa"
        "MUTUAL_FUND" -> "Fundos"
        "EQUITY" -> if (subtype.equals("REAL_ESTATE_FUND", true)) "Fundos imobiliários" else "Ações"
        "ETF" -> "ETFs"
        "SECURITY" -> "Previdência"
        "COE" -> "COE"
        "TREASURY" -> "Tesouro"
        else -> type.replace('_', ' ').lowercase(locale)
            .replaceFirstChar { it.uppercase(locale) }
    }

    fun investmentSubtypeLabel(subtype: String?): String? = subtype
        ?.takeIf { it.isNotBlank() }
        ?.let {
            when (it.uppercase()) {
                "STOCK" -> "Ação"
                "REAL_ESTATE_FUND" -> "FII"
                "INVESTMENT_FUND", "FIXED_INCOME_FUND", "MULTIMARKET_FUND", "STOCK_FUND",
                "FIP_FUND", "OFFSHORE_FUND", "EXCHANGE_FUND" -> "Fundo"
                "DEBENTURES", "DEBENTURE" -> "Debênture"
                "RETIREMENT" -> "Previdência"
                "TREASURY" -> "Tesouro"
                "PGBL", "VGBL", "CDB", "LCI", "LCA", "CRI", "CRA", "ETF", "FII", "COE" ->
                    it.uppercase()
                else -> it.replace('_', ' ').lowercase(locale)
                    .replaceFirstChar { char -> char.uppercase(locale) }
            }
        }

    fun investmentRateLabel(investment: Investment): String? {
        val parts = mutableListOf<String>()
        val rate = investment.rate
        val rateType = investment.rateType
        if (rate != null && !rateType.isNullOrBlank()) {
            parts += formatRate(rate) + "% do " + rateType
        }
        val fixed = investment.fixedAnnualRate
        if (fixed != null && fixed > 0.0) {
            parts += formatRate(fixed) + "% a.a."
        }
        if (parts.isEmpty()) {
            investment.annualRate?.let { parts += formatRate(it) + "% a.a." }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" + ")
    }

    fun investmentProfit(investment: Investment): Double? = investment.amountProfit

    /** Preço atual por cota, deduzido do saldo e da quantidade de ações. */
    fun investmentCurrentPrice(investment: Investment): Double? {
        val quantity = investment.quantity ?: return null
        if (quantity <= 0.0) return null
        return investment.balance / quantity
    }

    /** Quantidade de cotas, sem casas decimais desnecessárias. */
    fun investmentQuantityLabel(investment: Investment): String? {
        val quantity = investment.quantity ?: return null
        if (quantity <= 0.0) return null
        return if (kotlin.math.abs(quantity - quantity.toLong()) < 0.0001) {
            quantity.toLong().toString()
        } else {
            String.format(locale, "%.4f", quantity).trimEnd('0').trimEnd(',', '.')
        }
    }

    fun investmentProfitPercent(investment: Investment): Double? {
        val profit = investment.amountProfit ?: return null
        val base = investment.amountOriginal ?: investment.amount ?: return null
        if (base <= 0.0) return null
        return profit / base * 100.0
    }

    fun investmentTotal(investments: List<Investment>): Double =
        investments.filter { it.isActive }.sumOf { it.balance }

    fun investmentTypeTotals(investments: List<Investment>): List<InvestmentTypeTotal> =
        investments
            .filter { it.isActive && it.balance > 0.0 }
            .groupBy { investmentTypeLabel(it.type, it.subtype) }
            .map { (label, items) ->
                InvestmentTypeTotal(label, items.sumOf { it.balance }, items.size)
            }
            .sortedByDescending { it.amount }

    private fun formatRate(value: Double): String {
        if (kotlin.math.abs(value - value.toLong().toDouble()) < 0.001) {
            return value.toLong().toString()
        }
        return String.format(locale, "%.2f", value).trimEnd('0').trimEnd(',', '.')
    }

    fun greeting(): String {
        val hour = java.time.LocalTime.now().hour
        return when {
            hour < 12 -> "Bom dia"
            hour < 18 -> "Boa tarde"
            else -> "Boa noite"
        }
    }
}