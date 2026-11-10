package br.com.meufinanceiro.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Busca cotações dos ativos da bolsa brasileira.
 *
 * Duas fontes, nesta ordem:
 *
 * 1. **brapi.dev** — API brasileira, usa o ticker nativo (PETR4) e devolve
 *    dados ricos (market cap, mínimo/máximo de 52 semanas). Sem chave funciona,
 *    mas o limite é apertado; com uma conta gratuita o app ganha 15.000
 *    requisições por mês.
 * 2. **Yahoo Finance** — não precisa de chave nenhuma e aceita vários
 *    tickers na mesma chamada. Exige o sufixo `.SA` para os ativos da B3.
 *
 * Se a primeira fonte falhar, a segunda cobre o que faltou.
 */
class QuoteService(
    private val brapiToken: String? = null,
    private val http: OkHttpClient = defaultHttpClient(),
) {

    /**
     * Resultado da busca: as cotações e de qual fonte vieram.
     *
     * @param source "brapi", "yahoo" ou "brapi + yahoo".
     * @param brapiError mensagem da falha da brapi, quando houve.
     */
    data class Result(
        val quotes: Map<String, StockQuote>,
        val source: String,
        val brapiError: String? = null,
    )

    /**
     * Cotação de cada ticker pedido. Tickers sem cotação simplesmente não
     * aparecem no mapa; a falha de uma fonte não impede a outra.
     */
    suspend fun quotes(tickers: List<String>): Result {
        val symbols = tickers.mapNotNull { raw ->
            StockQuote.normalizeTicker(raw).takeIf { it.isNotBlank() }
        }.distinct()
        if (symbols.isEmpty()) return Result(emptyMap(), "nenhuma")

        var brapiError: String? = null
        val result = LinkedHashMap<String, StockQuote>()
        val fromBrapi = runCatching { brapiQuotes(symbols) }
            .onFailure { brapiError = it.message ?: "erro desconhecido" }
            .getOrDefault(emptyMap())
        result.putAll(fromBrapi)

        val missing = symbols.filterNot { it in result }
        var usedYahoo = false
        if (missing.isNotEmpty()) {
            val fromYahoo = runCatching { yahooQuotes(missing) }.getOrDefault(emptyMap())
            if (fromYahoo.isNotEmpty()) usedYahoo = true
            result.putAll(fromYahoo)
        }

        val source = when {
            fromBrapi.isNotEmpty() && usedYahoo -> "brapi + yahoo"
            fromBrapi.isNotEmpty() -> "brapi"
            usedYahoo -> "yahoo"
            else -> "nenhuma"
        }
        return Result(result, source, brapiError)
    }

    private suspend fun brapiQuotes(symbols: List<String>): Map<String, StockQuote> =
        withContext(Dispatchers.IO) {
            val url = "https://brapi.dev/api/v2/stocks/quote?symbols=" +
                symbols.joinToString(",")
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .apply {
                    if (!brapiToken.isNullOrBlank()) header("Authorization", "Bearer $brapiToken")
                }
                .build()
            val body = execute(request)
            val results = body.optJSONArray("results") ?: return@withContext emptyMap()
            val quotes = LinkedHashMap<String, StockQuote>()
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val quote = StockQuote.fromBrapi(item) ?: continue
                quotes[quote.ticker] = quote
            }
            quotes
        }

    private suspend fun yahooQuotes(symbols: List<String>): Map<String, StockQuote> =
        withContext(Dispatchers.IO) {
            val url = "https://query1.finance.yahoo.com/v7/finance/spark?symbols=" +
                symbols.joinToString(",") { it + ".SA" } + "&range=1d&interval=1d"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            val body = execute(request)
            val results = body.optJSONObject("spark")?.optJSONArray("result")
                ?: return@withContext emptyMap()
            val quotes = LinkedHashMap<String, StockQuote>()
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val response = item.optJSONArray("response")?.optJSONObject(0) ?: continue
                val quote = StockQuote.fromYahoo(response) ?: continue
                quotes[quote.ticker] = quote
            }
            quotes
        }

    private fun execute(request: Request): JSONObject {
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP " + response.code + ": " + text.take(120))
            }
            if (text.isBlank()) return JSONObject()
            return JSONObject(text)
        }
    }

    companion object {
        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
