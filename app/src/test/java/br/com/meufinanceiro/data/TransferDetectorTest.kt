package br.com.meufinanceiro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferDetectorTest {

    private val owner = "Vinicius Nogueira Martins"

    private fun movement(
        id: String,
        title: String,
        amount: Double,
        income: Boolean,
        accountId: String = "conta-1",
        isCreditCard: Boolean = false,
        date: Long = 1_790_000_000_000L,
        category: String = Categories.TRANSFERS,
    ) = Movement(
        id = id,
        title = title,
        amount = amount,
        type = if (income) MovementType.INCOME else MovementType.EXPENSE,
        category = category,
        date = date,
        accountId = accountId,
        isCreditCard = isCreditCard,
    )

    @Test
    fun `pix enviado para o proprio nome e transferencia interna`() {
        val movements = listOf(
            movement(
                "1",
                "PIX ENVIADO OPEN FINANCE   Vinicius Nogueira Martins",
                2600.0,
                income = false,
            ),
        )
        val marked = TransferDetector.mark(movements, owner)
        assertTrue(marked.single().internalTransfer)
    }

    @Test
    fun `pix recebido de outra pessoa continua receita`() {
        val movements = listOf(
            movement("1", "PIX RECEBIDO   ROSANGELA GOMES DO NAS", 500.0, income = true),
        )
        val marked = TransferDetector.mark(movements, owner)
        assertFalse(marked.single().internalTransfer)
    }

    @Test
    fun `pix no cartao de credito continua despesa`() {
        val movements = listOf(
            movement(
                "1",
                "PIX NO CARTAO FINAL 2184   Vinicius Nogueira Martins",
                300.0,
                income = false,
                isCreditCard = true,
            ),
        )
        val marked = TransferDetector.mark(movements, owner)
        assertFalse(marked.single().internalTransfer)
    }

    @Test
    fun `compra comum com o nome do titular nao e transferencia`() {
        val movements = listOf(
            movement("1", "VINICIUS NOGUEIRA MARTINS LTDA", 120.0, income = false),
        )
        val marked = TransferDetector.mark(movements, owner)
        assertFalse(marked.single().internalTransfer)
    }

    @Test
    fun `par de saida e entrada entre bancos e marcado dos dois lados`() {
        val movements = listOf(
            movement("1", "PIX ENVIADO", 800.0, income = false, accountId = "santander"),
            movement("2", "PIX RECEBIDO", 800.0, income = true, accountId = "mercadopago"),
        )
        val marked = TransferDetector.mark(movements, owner)
        assertTrue(marked.all { it.internalTransfer })
    }

    @Test
    fun `mesma conta nao forma par`() {
        val movements = listOf(
            movement("1", "PIX ENVIADO", 800.0, income = false, accountId = "santander"),
            movement("2", "PIX RECEBIDO", 800.0, income = true, accountId = "santander"),
        )
        val marked = TransferDetector.mark(movements, owner)
        assertFalse(marked.any { it.internalTransfer })
    }

    @Test
    fun `totais ignoram transferencias internas`() {
        val movements = listOf(
            movement("1", "PIX ENVIADO OPEN FINANCE Vinicius Nogueira Martins", 2600.0, income = false),
            movement("2", "Mercado", 100.0, income = false),
            movement("3", "PIX RECEBIDO OPEN FINANCE Vinicius Nogueira Martins", 2600.0, income = true),
            movement("4", "Salario", 5000.0, income = true),
        )
        val marked = TransferDetector.mark(movements, owner)
        val totals = FinanceCalculator.totals(marked)
        assertEquals(5000.0, totals.income, 0.001)
        assertEquals(100.0, totals.expense, 0.001)
        assertEquals(4900.0, totals.balance, 0.001)
    }

    @Test
    fun `categoria de transferencia nao aparece no donut`() {
        val movements = TransferDetector.mark(
            listOf(
                movement("1", "PIX ENVIADO OPEN FINANCE Vinicius Nogueira Martins", 2600.0, income = false),
                movement("2", "Mercado", 100.0, income = false, category = Categories.MARKET),
            ),
            owner,
        )
        val categories = FinanceCalculator.categoryTotals(movements)
        assertEquals(1, categories.size)
        assertEquals("Mercado", categories.single().category)
    }

    @Test
    fun `nome e normalizado sem acento e caixa`() {
        assertEquals("VINICIUS NOGUEIRA MARTINS", TransferDetector.normalize("Vinícius  Nogueira-Martins"))
        assertEquals(
            listOf("VINICIUS", "MARIA"),
            TransferDetector.parseNames("Vinicius; Maria"),
        )
    }
}
