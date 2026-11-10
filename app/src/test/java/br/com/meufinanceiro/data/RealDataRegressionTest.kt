package br.com.meufinanceiro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RealDataRegressionTest {

    private fun load(name: String): List<List<String>> {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
            ?: error("missing resource $name")
        return stream.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }
            .map { it.split("\t") }
    }

    private fun opt(v: String?): String? = v?.takeIf { it.isNotBlank() && it != "null" }

    private fun movements(): List<Movement> = load("real_movements.tsv").drop(1).map { c ->
        Movement(
            id = c[0],
            title = c[1],
            amount = c[2].toDouble(),
            type = MovementType.valueOf(c[3]),
            category = c[4],
            date = c[5].toLong(),
            accountId = opt(c[6]),
            accountName = opt(c[7]),
            isCreditCard = c[8].toBoolean(),
            installmentNumber = opt(c[9])?.toInt(),
            totalInstallments = opt(c[10])?.toInt(),
            billId = opt(c[11]),
            billMonth = opt(c[12]),
            status = c[13],
            source = MovementSource.valueOf(c[14]),
        )
    }

    private fun bills(): List<CreditBill> = load("real_bills.tsv").drop(1).map { c ->
        CreditBill(
            id = c[0],
            accountId = c[1],
            dueDate = c[2].toLong(),
            closingDate = opt(c[3])?.toLong(),
            totalAmount = c[4].toDouble(),
            minimumPayment = opt(c[5])?.toDouble(),
            paidAmount = opt(c[6])?.toDouble() ?: 0.0,
            financeCharges = opt(c[7])?.toDouble() ?: 0.0,
            lastPaymentDate = opt(c[8])?.toLong(),
        )
    }

    private fun account(): FinanceAccount {
        val c = load("real_account.tsv")[1]
        return FinanceAccount(
            id = c[0],
            name = c[1],
            type = c[2],
            subtype = c[3],
            balance = c[4].toDouble(),
            creditLimit = opt(c[5])?.toDouble(),
            availableCreditLimit = opt(c[6])?.toDouble(),
            closeDate = opt(c[7]),
            dueDate = opt(c[8]),
        )
    }

    private val today = LocalDate.of(2026, 11, 9)
    private val account by lazy { account() }
    private val bills by lazy { bills() }
    private val movements by lazy { movements() }
    private val entries by lazy {
        FinanceCalculator.invoiceEntries(account, bills, movements, today)
    }

    private fun entry(month: String) =
        entries.first { it.summary.month == YearMonth.parse(month) }

    @Test
    fun `december matches the santander open invoice`() {
        val december = entry("2026-12")
        assertEquals(InvoiceStatus.OPEN, december.status)
        assertTrue(december.isCurrent)
        assertEquals(3728.85, december.summary.total, 0.01)
        assertEquals(3728.85, december.summary.movements.sumOf { it.amount }, 0.01)
    }

    @Test
    fun `january projects only the remaining installments`() {
        val january = entry("2027-01")
        assertEquals(InvoiceStatus.FUTURE, january.status)
        assertFalse(january.isCurrent)
        assertEquals(723.19, january.summary.total, 0.01)
        assertEquals(
            listOf(94.98, 279.90, 298.31, 50.0).sorted(),
            january.summary.movements.map { Math.round(it.amount * 100) / 100.0 }.sorted(),
        )
        assertTrue(
            january.summary.movements.all {
                it.installmentNumber != null && it.totalInstallments != null
            },
        )
    }

    @Test
    fun `november is paid and there is no false overdue`() {
        val november = entry("2026-11")
        assertEquals(InvoiceStatus.PAID, november.status)
        assertEquals(5175.08, november.summary.total, 0.01)
        assertFalse(entries.any { it.status == InvoiceStatus.OVERDUE })
    }

    @Test
    fun `closed bills use the bank total`() {
        assertEquals(4117.14, entry("2026-08").summary.total, 0.01)
        assertEquals(4766.75, entry("2026-10").summary.total, 0.01)
    }

    @Test
    fun `december rolls over pending pix and keeps one meli installment`() {
        val december = entry("2026-12")
        assertTrue(december.summary.movements.any { it.title.contains("PIX Pix Marketplace") })
        assertTrue(december.summary.movements.any { it.title.contains("JD COMERCIO") })
        assertEquals(
            1,
            december.summary.movements.count {
                it.title.contains("MERCADOLIVRE*MERCADOLIVRE") &&
                    Math.abs(it.amount - 94.98) < 0.01
            },
        )
        assertTrue(
            december.summary.movements.any {
                it.title.contains("MAGALU") && Math.abs(it.amount - 279.90) < 0.01
            },
        )
    }
}
