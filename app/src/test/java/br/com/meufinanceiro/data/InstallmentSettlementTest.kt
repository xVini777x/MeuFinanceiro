package br.com.meufinanceiro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Parcelamento quitado é aquele em que todas as parcelas já caíram em faturas
 * pagas. O banco para de devolver as parcelas restantes, então sem conferir a
 * fatura o parcelamento apareceria como ativo para sempre.
 */
class InstallmentSettlementTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun day(value: String): Long =
        LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli()

    private val account = FinanceAccount(
        id = "card-1",
        name = "Cartão",
        type = "CREDIT",
        subtype = "CREDIT_CARD",
        balance = 0.0,
    )

    /** Fatura de janeiro já paga; a de fevereiro em aberto. */
    private val bills = listOf(
        CreditBill(
            id = "jan",
            accountId = "card-1",
            dueDate = day("2026-01-10"),
            closingDate = day("2026-01-03"),
            totalAmount = 100.0,
            paidAmount = 100.0,
        ),
        CreditBill(
            id = "fev",
            accountId = "card-1",
            dueDate = day("2026-02-10"),
            closingDate = day("2026-02-03"),
            totalAmount = 100.0,
            paidAmount = 0.0,
        ),
    )

    private val today = LocalDate.parse("2026-02-15")

    private fun installment(
        id: String,
        number: Int,
        total: Int,
        date: String,
        billId: String? = null,
        amount: Double = 50.0,
    ) = Movement(
        id = id,
        title = "LOJA",
        amount = amount,
        type = MovementType.EXPENSE,
        category = Categories.SHOPPING,
        date = day(date),
        accountId = "card-1",
        accountName = "Cartão",
        isCreditCard = true,
        billId = billId,
        installmentNumber = number,
        totalInstallments = total,
    )

    @Test
    fun `todas as parcelas em faturas pagas - parcelamento quitado`() {
        val movements = listOf(
            installment("a", 1, 4, "2026-01-05", "jan"),
            installment("b", 2, 4, "2026-01-25", "jan"),
        )

        val plan = FinanceCalculator.installmentPlans(movements, bills, today).single()

        assertEquals(2, plan.current)
        assertEquals(4, plan.total)
        assertTrue("deveria estar quitado", plan.settled)
    }

    @Test
    fun `parcela na fatura em aberto - parcelamento continua ativo`() {
        val movements = listOf(
            installment("a", 1, 4, "2026-01-05", "jan"),
            installment("b", 2, 4, "2026-02-05", "fev"),
        )

        val plan = FinanceCalculator.installmentPlans(movements, bills, today).single()

        assertFalse("deveria estar em aberto", plan.settled)
    }

    @Test
    fun `parcela ainda sem fatura - parcelamento continua ativo`() {
        val movements = listOf(
            installment("a", 1, 4, "2026-01-05", "jan"),
            installment("b", 2, 4, "2026-02-05", null),
        )

        val plan = FinanceCalculator.installmentPlans(movements, bills, today).single()

        assertFalse("deveria estar em aberto", plan.settled)
    }

    @Test
    fun `compra estornada - parcelamento quitado`() {
        val movements = listOf(
            installment("a", 1, 2, "2026-02-05", "fev"),
            installment("b", 2, 2, "2026-02-25", null),
            // Estorno do valor total da compra.
            Movement(
                id = "estorno",
                title = "LOJA",
                amount = 100.0,
                type = MovementType.INCOME,
                category = Categories.OTHER,
                date = day("2026-02-26"),
                accountId = "card-1",
                isCreditCard = true,
            ),
        )

        val plan = FinanceCalculator.installmentPlans(movements, bills, today).single()

        assertTrue("deveria estar quitado", plan.settled)
    }

    @Test
    fun `banco sem informacao de fatura - parcelamento parado ha meses e quitado`() {
        val movements = listOf(
            installment("a", 1, 10, "2025-06-05"),
            installment("b", 2, 10, "2025-07-05"),
        )

        val plan = FinanceCalculator.installmentPlans(movements, emptyList(), today).single()

        assertTrue("deveria estar quitado", plan.settled)
    }

    @Test
    fun `banco sem informacao de fatura - parcelamento recente continua ativo`() {
        val movements = listOf(
            installment("a", 1, 10, "2026-02-05"),
            installment("b", 2, 10, "2026-02-25"),
        )

        val plan = FinanceCalculator.installmentPlans(movements, emptyList(), today).single()

        assertFalse("deveria estar em aberto", plan.settled)
    }

    @Test
    fun `dados reais - magalu de 10x continua em aberto e os completos sao quitados`() {
        val movements = readRealMovements()
        val bills = readRealBills()

        val plans = FinanceCalculator.installmentPlans(
            movements,
            bills,
            LocalDate.parse("2026-11-09"),
        )

        val magalu = plans.first { it.title.startsWith("MAGALU") }
        assertFalse("MAGALU 1/10 deveria estar em aberto", magalu.settled)

        // Nenhum parcelamento com parcelas faltando pode estar em fatura já paga.
        plans.filter { it.remaining > 0.0 }.forEach { plan ->
            assertFalse(
                "parcelamento em aberto não deveria estar quitado: " + plan.title,
                plan.settled,
            )
        }
    }

    private fun readRealMovements(): List<Movement> {
        val stream = javaClass.classLoader!!.getResourceAsStream("real_movements.tsv")
            ?: error("recurso ausente")
        val lines = stream.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        val header = lines[0].split("\t")
        return lines.drop(1).map { line ->
            val c = line.split("\t")
            val index = header.withIndex().associate { (i, name) -> name to i }
            fun value(name: String): String? =
                c.getOrNull(index[name] ?: 0)?.takeIf { it.isNotBlank() && it != "null" }

            Movement(
                id = c[0],
                title = c[1],
                amount = c[2].toDouble(),
                type = MovementType.valueOf(c[3]),
                category = c[4],
                date = c[5].toLong(),
                accountId = value("accountId"),
                accountName = value("accountName"),
                isCreditCard = value("isCreditCard")?.toBoolean() ?: false,
                installmentNumber = value("installmentNumber")?.toInt(),
                totalInstallments = value("totalInstallments")?.toInt(),
                billId = value("billId"),
                billMonth = value("billMonth"),
                status = value("status") ?: "POSTED",
                source = MovementSource.valueOf(value("source") ?: "PLUGGY"),
            )
        }
    }

    private fun readRealBills(): List<CreditBill> {
        val stream = javaClass.classLoader!!.getResourceAsStream("real_bills.tsv")
            ?: error("recurso ausente")
        val lines = stream.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        return lines.drop(1).map { line ->
            val c = line.split("\t")
            CreditBill(
                id = c[0],
                accountId = c[1],
                dueDate = c[2].toLong(),
                closingDate = c[3].takeIf { it.isNotBlank() && it != "null" }?.toLong(),
                totalAmount = c[4].toDouble(),
                minimumPayment = c[5].takeIf { it.isNotBlank() && it != "null" }?.toDouble(),
                paidAmount = c[6].takeIf { it.isNotBlank() && it != "null" }?.toDouble() ?: 0.0,
                financeCharges = c[7].takeIf { it.isNotBlank() && it != "null" }?.toDouble() ?: 0.0,
                lastPaymentDate = c[8].takeIf { it.isNotBlank() && it != "null" }?.toLong(),
            )
        }
    }

    @Test
    fun `parcelamento parado ha tres meses ainda conta como em aberto`() {
        // O limite é de 3 meses: parcelamento sem movimento há mais tempo é
        // tratado como quitado, mas o limite não pode engolir um ativo real.
        val movements = listOf(
            installment("a", 1, 10, "2025-11-20"),
        )

        val plan = FinanceCalculator.installmentPlans(movements, emptyList(), today).single()

        assertFalse("deveria estar em aberto", plan.settled)
    }
}
