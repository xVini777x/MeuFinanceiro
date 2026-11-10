package br.com.meufinanceiro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class InstallmentPlanTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun day(value: String): Long =
        LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun movement(
        id: String,
        title: String,
        amount: Double,
        date: String,
        installment: Int,
        total: Int,
        accountId: String = "card-1",
    ) = Movement(
        id = id,
        title = title,
        amount = amount,
        type = MovementType.EXPENSE,
        category = Categories.SHOPPING,
        date = day(date),
        accountId = accountId,
        accountName = "Santander",
        isCreditCard = true,
        installmentNumber = installment,
        totalInstallments = total,
    )

    @Test
    fun `compra em aberto do mesmo lojista nao some por causa de uma compra antiga`() {
        // Mesmo título e mesmo total de parcelas: uma compra já quitada (R$
        // 239,14 em 4x) e outra recém-feita (R$ 94,98 em 4x, só a 1ª parcela
        // lançada). Agrupando só por título, a compra nova sumia da lista.
        val movements = listOf(
            movement("a1", "MERCADOLIVRE*MERCADOLIVRE", 239.14, "2026-06-05", 1, 4),
            movement("a2", "MERCADOLIVRE*MERCADOLIVRE", 239.14, "2026-06-25", 2, 4),
            movement("a3", "MERCADOLIVRE*MERCADOLIVRE", 239.12, "2026-07-27", 3, 4),
            movement("a4", "MERCADOLIVRE*MERCADOLIVRE", 239.12, "2026-08-26", 4, 4),
            movement("b1", "MERCADOLIVRE*MERCADOLIVRE", 94.98, "2026-11-02", 1, 4),
        )

        val plans = FinanceCalculator.installmentPlans(movements)

        assertEquals(2, plans.size)
        val open = plans.first { it.remaining > 0.0 }
        assertEquals(1, open.current)
        assertEquals(4, open.total)
        assertEquals(284.94, open.remaining, 0.01)
        assertEquals(94.98, open.installmentAmount, 0.01)
    }

    @Test
    fun `compra em aberto continua visivel com o restante calculado`() {
        val movements = listOf(
            movement("a1", "MAGALU*Magalu", 279.90, "2026-11-02", 1, 10),
        )

        val plan = FinanceCalculator.installmentPlans(movements).single()

        assertEquals(1, plan.current)
        assertEquals(10, plan.total)
        assertEquals(2519.10, plan.remaining, 0.01)
        assertEquals("Santander", plan.accountName)
        assertEquals(Categories.SHOPPING, plan.category)
        assertEquals(java.time.YearMonth.of(2026, 12), plan.nextMonth)
    }

    @Test
    fun `compras iguais em epocas diferentes viram parcelamentos separados`() {
        val movements = listOf(
            movement("a1", "HNA", 51.92, "2026-02-06", 1, 5),
            movement("a2", "HNA", 51.92, "2026-03-26", 3, 5),
            movement("b1", "HNA", 51.92, "2026-08-06", 1, 5),
            movement("b2", "HNA", 51.92, "2026-09-26", 3, 5),
        )

        val plans = FinanceCalculator.installmentPlans(movements)

        assertEquals(2, plans.size)
        assertTrue(plans.all { it.current == 3 && it.total == 5 })
    }

    @Test
    fun `parcelas ficam separadas por cartao`() {
        val movements = listOf(
            movement("a1", "NOTEBOOK", 400.0, "2026-09-10", 1, 10, accountId = "card-1"),
            movement("b1", "NOTEBOOK", 400.0, "2026-09-10", 1, 10, accountId = "card-2"),
        )

        val plans = FinanceCalculator.installmentPlans(movements)

        assertEquals(2, plans.size)
        assertEquals(setOf("card-1", "card-2"), plans.mapNotNull { it.accountId }.toSet())
    }
}
