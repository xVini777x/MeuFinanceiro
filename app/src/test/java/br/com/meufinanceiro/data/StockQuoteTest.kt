package br.com.meufinanceiro.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StockQuoteTest {

    @Test
    fun `ticker normalizado tira sufixo e caixa`() {
        assertEquals("PETR4", StockQuote.normalizeTicker("petr4"))
        assertEquals("PETR4", StockQuote.normalizeTicker("PETR4.SA"))
        assertEquals("MXRF11", StockQuote.normalizeTicker("  mxrf11 "))
        assertEquals("", StockQuote.normalizeTicker(null))
    }

    @Test
    fun `le a cotacao da brapi`() {
        val json = JSONObject(
            """
            {
              "results": [{
                "requestedSymbol": "PETR4",
                "symbol": "PETR4",
                "data": {
                  "shortName": "PETR4",
                  "longName": "Petroleo Brasileiro SA Pfd",
                  "currency": "BRL",
                  "regularMarketPrice": 45.1,
                  "regularMarketChangePercent": -0.2,
                  "regularMarketTime": "2026-11-10T20:15:30.000Z"
                }
              }]
            }
            """.trimIndent(),
        )
        val item = json.getJSONArray("results").getJSONObject(0)

        val quote = StockQuote.fromBrapi(item)!!

        assertEquals("PETR4", quote.ticker)
        assertEquals(45.1, quote.price, 0.001)
        assertEquals(-0.2, quote.changePercent!!, 0.001)
        assertEquals("BRL", quote.currency)
        assertEquals("Petroleo Brasileiro SA Pfd", quote.name)
    }

    @Test
    fun `le a cotacao do yahoo`() {
        val json = JSONObject(
            """
            {
              "meta": {
                "currency": "BRL",
                "symbol": "MXRF11.SA",
                "regularMarketPrice": 8.81,
                "regularMarketChangePercent": -1.233,
                "regularMarketTime": 1794340965,
                "longName": "Maxi Renda FII"
              }
            }
            """.trimIndent(),
        )

        val quote = StockQuote.fromYahoo(json)!!

        assertEquals("MXRF11", quote.ticker)
        assertEquals(8.81, quote.price, 0.001)
        assertEquals(-1.233, quote.changePercent!!, 0.001)
        assertEquals(1794340965L, quote.updatedAt)
    }

    @Test
    fun `resposta sem preco e ignorada`() {
        assertNull(StockQuote.fromBrapi(JSONObject("""{"data":{"currency":"BRL"}}""")))
        assertNull(StockQuote.fromYahoo(JSONObject("""{"meta":{"currency":"BRL"}}""")))
    }
}
