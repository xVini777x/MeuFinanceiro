package br.com.meufinanceiro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FinanceCalculatorTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val account = FinanceAccount(
        id = "card-1",
        name = "Santander",
        type = "CREDIT",
        subtype = "CREDIT_CARD",
        balance = 0.0,
    )

    private fun day(value: String): Long =
        LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun bill(
        id: String,
        due: String,
        closing: String,
        total: Double,
        paid: Double = 0.0,
        lastPayment: String? = null,
    ) = CreditBill(
        id = id,
        accountId = account.id,
        dueDate = day(due),
        closingDate = day(closing),
        totalAmount = total,
        paidAmount = paid,
        lastPaymentDate = lastPayment?.let { day(it) },
    )

    private fun movement(
        id: String,
        title: String,
        amount: Double,
        date: String,
        income: Boolean,
        creditCard: Boolean = true,
        accountId: String = account.id,
        billId: String? = null,
    ) = Movement(
        id = id,
        title = title,
        amount = amount,
        type = if (income) MovementType.INCOME else MovementType.EXPENSE,
        category = Categories.OTHER,
        date = day(date),
        accountId = accountId,
        accountName = if (creditCard) "Santander" else "Conta corrente",
        isCreditCard = creditCard,
        billId = billId,
    )

    private val bills = listOf(
        bill("may", "2026-05-10", "2026-05-03", 1000.0),
        bill("jun", "2026-06-10", "2026-06-03", 1000.0),
        bill("jul", "2026-07-10", "2026-07-03", 1000.0),
        bill("aug", "2026-08-10", "2026-08-03", 1000.0),
        bill("sep", "2026-09-10", "2026-09-03", 1000.0),
        bill("oct", "2026-10-10", "2026-10-03", 1000.0),
        bill("nov", "2026-11-10", "2026-11-03", 1500.0),
        bill("dec", "2026-12-10", "2026-12-03", 800.0),
    )

    @Test
    fun `santander november paid by credit movement and december is current`() {
        val movements = listOf(
            movement("pay-nov", "PAGAMENTO DE FATURA", 1500.0, "2026-11-10", income = true),
            movement("jan-buy", "PARCELA JANEIRO", 200.0, "2027-01-02", income = false),
            movement("feb-buy", "PARCELA FEVEREIRO", 300.0, "2027-02-02", income = false),
        )
        val today = LocalDate.parse("2026-11-09")

        val entries = FinanceCalculator.invoiceEntries(account, bills, movements, today)
        val byMonth = entries.associateBy { it.summary.month.toString() }

        assertEquals(InvoiceStatus.PAID, byMonth.getValue("2026-11").status)
        assertEquals(InvoiceStatus.OPEN, byMonth.getValue("2026-12").status)
        assertTrue(byMonth.getValue("2026-12").isCurrent)
        assertEquals(InvoiceStatus.FUTURE, byMonth.getValue("2027-01").status)
        assertEquals(InvoiceStatus.FUTURE, byMonth.getValue("2027-02").status)
        assertTrue(byMonth.getValue("2027-01").isProjected)
        assertFalse(entries.any { it.status == InvoiceStatus.OVERDUE })

        val current = FinanceCalculator.currentBillFor(account, bills, movements, today)
        assertNotNull(current)
        assertEquals("dec", current?.id)
        assertEquals(800.0, FinanceCalculator.currentInvoiceFor(account, bills, movements, today).total, 0.01)
    }

    @Test
    fun `santander payment as checking account debit settles bill`() {
        val movements = listOf(
            movement(
                "pay-nov",
                "PAGAMENTO FATURA CARTAO",
                1500.0,
                "2026-11-10",
                income = false,
                creditCard = false,
                accountId = "checking",
            ),
            movement("jan-buy", "PARCELA JANEIRO", 200.0, "2027-01-02", income = false),
        )
        val today = LocalDate.parse("2026-11-09")

        val entries = FinanceCalculator.invoiceEntries(account, bills, movements, today)
        val byMonth = entries.associateBy { it.summary.month.toString() }

        assertEquals(InvoiceStatus.PAID, byMonth.getValue("2026-11").status)
        assertTrue(byMonth.getValue("2026-12").isCurrent)
        assertEquals(InvoiceStatus.FUTURE, byMonth.getValue("2027-01").status)
        assertFalse(entries.any { it.status == InvoiceStatus.OVERDUE })
    }

    @Test
    fun `own bill payment convention still settles`() {
        val ownBills = listOf(
            bill("oct", "2026-10-10", "2026-10-03", 1000.0, paid = 1000.0, lastPayment = "2026-10-08"),
            bill("nov", "2026-11-10", "2026-11-03", 900.0),
        )
        val settled = FinanceCalculator.settledBillIds(ownBills, emptyList(), LocalDate.parse("2026-11-09"))
        assertTrue("oct" in settled)
        assertFalse("nov" in settled)
    }

    @Test
    fun `next bill payment convention still settles`() {
        val nextBills = listOf(
            bill("oct", "2026-10-10", "2026-10-03", 1000.0),
            bill("nov", "2026-11-10", "2026-11-03", 900.0, paid = 1000.0, lastPayment = "2026-10-08"),
        )
        val settled = FinanceCalculator.settledBillIds(nextBills, emptyList(), LocalDate.parse("2026-11-09"))
        assertTrue("oct" in settled)
        assertFalse("nov" in settled)
    }

    @Test
    fun `account due date closes previous bills and keeps december current`() {
        val withDue = account.copy(dueDate = "2026-12-10")
        val today = LocalDate.parse("2026-11-09")
        val entries = FinanceCalculator.invoiceEntries(withDue, bills, emptyList(), today)
        val byMonth = entries.associateBy { it.summary.month.toString() }

        assertEquals(InvoiceStatus.PAID, byMonth.getValue("2026-11").status)
        assertEquals(InvoiceStatus.OPEN, byMonth.getValue("2026-12").status)
        assertTrue(byMonth.getValue("2026-12").isCurrent)
        assertFalse(entries.any { it.status == InvoiceStatus.OVERDUE })
    }

    @Test
    fun `projection after long bill gap does not swallow old movements`() {
        val oldBills = listOf(bill("old", "2023-07-10", "2023-07-03", 1000.0))
        val movements = listOf(
            movement("may", "COMPRA MAIO", 500.0, "2026-05-10", income = false),
            movement("oct", "COMPRA OUTUBRO", 100.0, "2026-10-20", income = false),
            movement("nov", "COMPRA NOVEMBRO", 200.0, "2026-11-10", income = false),
        )
        val today = LocalDate.parse("2026-11-09")
        val withDue = account.copy(dueDate = "2026-11-14")

        val entries = FinanceCalculator.invoiceEntries(withDue, oldBills, movements, today)
        val november = entries.first { it.summary.month.toString() == "2026-11" }
        val december = entries.first { it.summary.month.toString() == "2026-12" }

        // O ciclo de novembro fecha em 03/11: entra só a compra de outubro.
        assertEquals(100.0, november.summary.total, 0.01)
        // A compra de 10/11 cai no ciclo de dezembro.
        assertEquals(200.0, december.summary.total, 0.01)
        assertFalse(november.summary.movements.any { it.id == "may" })
        assertFalse(december.summary.movements.any { it.id == "may" })
    }

    @Test
    fun `projects remaining installments when future transactions are missing`() {
        val withoutDecember = bills.filter { it.id != "dec" }
        val movements = listOf(
            movement(
                "installment-2",
                "NOTEBOOK",
                100.0,
                "2026-12-02",
                income = false,
            ).copy(installmentNumber = 2, totalInstallments = 6),
        )
        val today = LocalDate.parse("2026-11-09")
        val entries = FinanceCalculator.invoiceEntries(account, withoutDecember, movements, today)
        val byMonth = entries.associateBy { it.summary.month.toString() }

        assertEquals(100.0, byMonth.getValue("2026-12").summary.total, 0.01)
        assertEquals(100.0, byMonth.getValue("2027-01").summary.total, 0.01)
        assertEquals(100.0, byMonth.getValue("2027-02").summary.total, 0.01)
        assertEquals(100.0, byMonth.getValue("2027-03").summary.total, 0.01)
        assertEquals(100.0, byMonth.getValue("2027-04").summary.total, 0.01)
        assertTrue(byMonth.getValue("2027-01").isProjected)
        assertTrue(
            byMonth.getValue("2027-01").summary.movements.any {
                it.installmentNumber == 3 && it.totalInstallments == 6
            },
        )
    }

    private val santanderBills = listOf(
        bill("oct", "2026-10-02", "2026-09-26", 4000.0),
        bill("nov", "2026-11-02", "2026-10-26", 5000.0),
    )

    @Test
    fun `santander december projection closes in november and uses forecast month`() {
        val withDue = account.copy(dueDate = "2026-11-02")
        val movements = listOf(
            movement("pay-nov", "PAGAMENTO DE FATURA", 5000.0, "2026-10-25", income = true),
            movement("forecast-nov", "COMPRA CARTAO", 100.0, "2026-11-20", income = false)
                .copy(billMonth = "2026-11"),
            movement("dated-nov", "COMPRA LOJA", 50.0, "2026-11-20", income = false),
            movement("forecast-dec", "COMPRA DEZ", 70.0, "2026-12-10", income = false)
                .copy(billMonth = "2026-12"),
        )
        val today = LocalDate.parse("2026-11-09")

        val entries = FinanceCalculator.invoiceEntries(withDue, santanderBills, movements, today)
        val byMonth = entries.associateBy { it.summary.month.toString() }
        val december = byMonth.getValue("2026-12")
        val january = byMonth.getValue("2027-01")

        // Vence 02/12 e fecha 26/11 (mês anterior ao vencimento).
        assertEquals(day("2026-11-26"), december.summary.closingDate)
        assertEquals(InvoiceStatus.OPEN, december.status)
        assertTrue(december.isCurrent)
        assertTrue(december.summary.movements.any { it.id == "forecast-nov" })
        assertTrue(december.summary.movements.any { it.id == "dated-nov" })
        assertFalse(december.summary.movements.any { it.id == "forecast-dec" })
        assertEquals(150.0, december.summary.total, 0.01)

        assertEquals(InvoiceStatus.FUTURE, january.status)
        assertTrue(january.summary.movements.any { it.id == "forecast-dec" })
        assertEquals(70.0, january.summary.total, 0.01)
        assertFalse(entries.any { it.status == InvoiceStatus.OVERDUE })
    }

    @Test
    fun `current invoice is december even when the open projection has no purchases`() {
        val withDue = account.copy(dueDate = "2026-11-02")
        val movements = listOf(
            movement("pay-nov", "PAGAMENTO DE FATURA", 5000.0, "2026-10-25", income = true),
        )
        val today = LocalDate.parse("2026-11-09")

        val entries = FinanceCalculator.invoiceEntries(withDue, santanderBills, movements, today)
        val december = entries.first { it.summary.month.toString() == "2026-12" }

        assertEquals(InvoiceStatus.PAID, entries.first { it.summary.month.toString() == "2026-11" }.status)
        assertEquals(InvoiceStatus.OPEN, december.status)
        assertTrue(december.isCurrent)
        assertEquals(0.0, december.summary.total, 0.01)
        assertFalse(entries.any { it.status == InvoiceStatus.OVERDUE })
    }

    @Test
    fun `same purchase duplicated with and without forecast counts once`() {
        val dupBills = listOf(bill("oct", "2026-11-02", "2026-10-26", 94.98))
        val movements = listOf(
            movement("forecast", "MERCADOLIVRE*MERCADOLIVRE", 94.98, "2026-11-02", income = false)
                .copy(billMonth = "2026-10", installmentNumber = 1, totalInstallments = 4),
            movement("no-forecast", "MERCADOLIVRE*MERCADOLIVRE", 94.98, "2026-11-02", income = false)
                .copy(installmentNumber = 4, totalInstallments = 4),
        )
        val today = LocalDate.parse("2026-11-09")

        val entries = FinanceCalculator.invoiceEntries(account, dupBills, movements, today)
        val november = entries.first { it.summary.month.toString() == "2026-11" }

        assertEquals(1, november.summary.movements.size)
        assertEquals(94.98, november.summary.total, 0.01)
    }

    @Test
    fun `exact duplicate card movements appear once in the invoice`() {
        val dupBills = listOf(bill("jul", "2026-08-02", "2026-07-27", 345.0))
        val movements = (1..9).map { index ->
            movement("dup-$index", "MP*MERCADOLIVRE", 345.0, "2026-07-29", income = false)
                .copy(billMonth = "2026-07")
        }
        val today = LocalDate.parse("2026-11-09")

        val entries = FinanceCalculator.invoiceEntries(account, dupBills, movements, today)
        // A fatura vence em 02/08 e fecha em 27/07; os lançamentos têm billMonth 2026-07.
        val august = entries.first { it.summary.month.toString() == "2026-08" }

        assertEquals(1, august.summary.movements.count { it.title == "MP*MERCADOLIVRE" })
        assertEquals(345.0, august.summary.movements.sumOf { it.amount }, 0.01)
    }

    @Test
    fun `installmentPlans separates purchases by account and fills account metadata`() {
        val movements = listOf(
            movement("m1", "iPhone 15", 400.0, "2026-09-10", income = false)
                .copy(accountId = "acc-nubank", accountName = "Nubank Roxinho", installmentNumber = 1, totalInstallments = 10),
            movement("m2", "iPhone 15", 400.0, "2026-10-10", income = false)
                .copy(accountId = "acc-nubank", accountName = "Nubank Roxinho", installmentNumber = 2, totalInstallments = 10),
            movement("m3", "iPhone 15", 350.0, "2026-09-05", income = false)
                .copy(accountId = "acc-inter", accountName = "Inter Gold", installmentNumber = 1, totalInstallments = 5),
        )

        val plans = FinanceCalculator.installmentPlans(movements)

        assertEquals(2, plans.size)
        val nubankPlan = plans.first { it.accountId == "acc-nubank" }
        assertEquals("Nubank Roxinho", nubankPlan.accountName)
        assertEquals("iPhone 15", nubankPlan.title)
        assertEquals(2, nubankPlan.current)
        assertEquals(10, nubankPlan.total)
        assertEquals(400.0, nubankPlan.installmentAmount, 0.01)
        assertEquals(3200.0, nubankPlan.remaining, 0.01) // 8 restantes * 400

        val interPlan = plans.first { it.accountId == "acc-inter" }
        assertEquals("Inter Gold", interPlan.accountName)
        assertEquals(1, interPlan.current)
        assertEquals(5, interPlan.total)
        assertEquals(1400.0, interPlan.remaining, 0.01) // 4 restantes * 350
    }
}