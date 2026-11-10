package br.com.meufinanceiro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InvestmentStockTest {

    private fun stock(
        balance: Double = 4_120.0,
        quantity: Double? = 100.0,
        averagePrice: Double? = 38.50,
        ticker: String? = "PETR4",
    ) = Investment(
        id = "i1",
        name = ticker ?: "Ação",
        type = "EQUITY",
        subtype = "STOCK",
        balance = balance,
        amountOriginal = quantity?.let { q -> q * (averagePrice ?: 0.0) },
        quantity = quantity,
        averagePrice = averagePrice,
        ticker = ticker,
    )

    @Test
    fun `preco atual sai do saldo dividido pelas cotas`() {
        assertEquals(41.20, FinanceCalculator.investmentCurrentPrice(stock())!!, 0.001)
    }

    @Test
    fun `investimento sem cotas nao tem preco atual nem quantidade`() {
        val investment = stock(quantity = null)

        assertNull(FinanceCalculator.investmentCurrentPrice(investment))
        assertNull(FinanceCalculator.investmentQuantityLabel(investment))
    }

    @Test
    fun `quantidade sai sem casas decimais desnecessarias`() {
        assertEquals("100", FinanceCalculator.investmentQuantityLabel(stock()))

        val fracionado = stock(quantity = 12.5)
        assertEquals("12,5", FinanceCalculator.investmentQuantityLabel(fracionado))
    }

    @Test
    fun `acao continua sendo do tipo acoes na distribuicao`() {
        assertEquals(
            "Ações",
            FinanceCalculator.investmentTypeLabel("EQUITY", "STOCK"),
        )
        assertEquals(
            "Ação",
            FinanceCalculator.investmentSubtypeLabel("STOCK"),
        )
    }

    @Test
    fun `posicao em acoes sobrevive ao salvamento`() {
        val original = stock()
        val restored = Investment.fromJson(org.json.JSONObject(original.toJson().toString()))

        assertEquals("PETR4", restored?.ticker)
        assertEquals(100.0, restored?.quantity ?: 0.0, 0.001)
        assertEquals(38.50, restored?.averagePrice ?: 0.0, 0.001)
        assertEquals("STOCK", restored?.subtype)
    }
}
