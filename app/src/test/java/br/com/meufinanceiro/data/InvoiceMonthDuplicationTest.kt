package br.com.meufinanceiro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Alguns bancos devolvem a fatura do mês aberto ainda vazia (total zero). A
 * projeção do mesmo mês entrava junto e a faixa de faturas mostrava o mês
 * duplicado ("Nov 26" duas vezes).
 */
class InvoiceMonthDuplicationTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun day(value: String): Long =
        LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli()

    private val account = FinanceAccount(
        id = "card-1",
        name = "Platinum",
        type = "CREDIT",
        subtype = "CREDIT_CARD",
        balance = 0.0,
        dueDate = "2026-11-02",
    )

    private val today = LocalDate.parse("2026-11-09")

    /** Fatura de outubro fechada e paga, mais a de novembro ainda vazia. */
    private val bills = listOf(
        CreditBill(
            id = "out",
            accountId = "card-1",
            dueDate = day("2026-10-10"),
            closingDate = day("2026-10-03"),
            totalAmount = 1000.0,
            paidAmount = 1000.0,
        ),
        CreditBill(
            id = "nov-vazia",
            accountId = "card-1",
            dueDate = day("2026-11-10"),
            closingDate = day("2026-11-03"),
            totalAmount = 0.0,
            paidAmount = 0.0,
        ),
    )

    private fun movement(id: String, date: String, amount: Double) = Movement(
        id = id,
        title = "COMPRA",
        amount = amount,
        type = MovementType.EXPENSE,
        category = Categories.SHOPPING,
        date = day(date),
        accountId = "card-1",
        accountName = "Platinum",
        isCreditCard = true,
        billMonth = "2026-11",
    )

    @Test
    fun `fatura vazia do mes aberto nao gera mes duplicado`() {
        // Compras antes do fechamento (03/11) entram na fatura de novembro.
        val movements = listOf(
            movement("m1", "2026-11-01", 250.0),
            movement("m2", "2026-11-02", 100.0),
        )

        val entries = FinanceCalculator.invoiceEntries(account, bills, movements, today)

        val months = entries.map { it.summary.month }
        assertEquals("meses duplicados: $months", months.size, months.distinct().size)

        val november = entries.first { it.summary.month == YearMonth.of(2026, 11) }
        assertEquals(350.0, november.summary.total, 0.01)
    }

    @Test
    fun `cada mes aparece uma unica vez na faixa de faturas`() {
        val movements = listOf(
            movement("m1", "2026-11-01", 250.0),
            movement("m2", "2026-11-02", 100.0),
        )

        val entries = FinanceCalculator.invoiceEntries(account, bills, movements, today)

        entries.groupBy { it.summary.month }.forEach { (month, group) ->
            assertEquals("mes $month com ${group.size} faturas", 1, group.size)
        }
    }

    @Test
    fun `fatura real fechada tem prioridade sobre a projecao do mesmo mes`() {
        // Fatura de novembro com total fechado e também a projeção do mês:
        // a real vence.
        val withClosedNovember = bills + CreditBill(
            id = "nov-cheia",
            accountId = "card-1",
            dueDate = day("2026-11-10"),
            closingDate = day("2026-11-03"),
            totalAmount = 500.0,
            paidAmount = 0.0,
        )

        val movements = listOf(movement("m1", "2026-11-01", 250.0))

        val entries = FinanceCalculator.invoiceEntries(account, withClosedNovember, movements, today)

        val november = entries.filter { it.summary.month == YearMonth.of(2026, 11) }
        assertEquals(1, november.size)
        assertEquals(500.0, november.single().summary.total, 0.01)
        assertTrue("deveria ser a fatura real", !november.single().isProjected)
    }
}
