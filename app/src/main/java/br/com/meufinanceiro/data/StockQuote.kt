package br.com.meufinanceiro.data

import org.json.JSONObject

/**
 * Cotação de um ativo da bolsa brasileira.
 *
 * @param ticker código nativo, sem sufixo (PETR4, MXRF11, BOVA11).
 * @param price último preço por cota.
 * @param changePercent variação percentual no dia (ex.: -1,23 para -1,23%).
 * @param name nome do ativo, quando a fonte devolve.
 * @param currency moeda da cotação.
 * @param updatedAt momento da cotação, em milissegundos.
 */
data class StockQuote(
    val ticker: String,
    val price: Double,
    val changePercent: Double? = null,
    val name: String? = null,
    val currency: String? = null,
    val updatedAt: Long? = null,
) {
    companion object {
        /** Normaliza o que o usuário digita: "petr4", "PETR4.SA" -> "PETR4". */
        fun normalizeTicker(raw: String?): String {
            val cleaned = raw?.trim()?.uppercase()?.removeSuffix(".SA").orEmpty()
            return cleaned.filter { it.isLetterOrDigit() }
        }

        /** Lê a resposta da brapi.dev. */
        fun fromBrapi(json: JSONObject): StockQuote? {
            val data = json.optJSONObject("data") ?: return null
            val price = data.optDouble("regularMarketPrice", Double.NaN)
            if (price.isNaN()) return null
            val ticker = normalizeTicker(
                json.optString("requestedSymbol").ifBlank { json.optString("symbol") },
            )
            if (ticker.isBlank()) return null
            return StockQuote(
                ticker = ticker,
                price = price,
                changePercent = data.optDouble("regularMarketChangePercent", Double.NaN)
                    .takeUnless { it.isNaN() },
                name = data.optString("longName").ifBlank { data.optString("shortName") }
                    .takeIf { it.isNotBlank() && it != "null" },
                currency = data.optString("currency").takeIf { it.isNotBlank() && it != "null" },
                updatedAt = parseIsoMillis(data.optString("regularMarketTime")),
            )
        }

        /** Lê a resposta do Yahoo Finance (endpoint spark). */
        fun fromYahoo(json: JSONObject): StockQuote? {
            val meta = json.optJSONObject("meta") ?: return null
            val price = meta.optDouble("regularMarketPrice", Double.NaN)
            if (price.isNaN()) return null
            val ticker = normalizeTicker(meta.optString("symbol"))
            if (ticker.isBlank()) return null
            return StockQuote(
                ticker = ticker,
                price = price,
                changePercent = meta.optDouble("regularMarketChangePercent", Double.NaN)
                    .takeUnless { it.isNaN() },
                name = meta.optString("longName").takeIf { it.isNotBlank() && it != "null" },
                currency = meta.optString("currency").takeIf { it.isNotBlank() && it != "null" },
                updatedAt = meta.optLong("regularMarketTime", 0L).takeIf { it > 0L },
            )
        }

        private fun parseIsoMillis(value: String?): Long? {
            if (value.isNullOrBlank() || value == "null") return null
            return runCatching {
                java.time.Instant.parse(value).toEpochMilli()
            }.getOrNull()
        }
    }
}
