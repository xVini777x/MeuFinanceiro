package br.com.meufinanceiro.pluggy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class PluggyException(message: String) : IOException(message)

class PluggyClient(
    private val clientId: String,
    private val clientSecret: String,
    private val baseUrl: String = "https://api.pluggy.ai",
    private val http: OkHttpClient = defaultHttpClient(),
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val authMutex = Mutex()

    @Volatile private var apiKey: String? = null
    @Volatile private var apiKeyExpiresAt: Long = 0L

    val isConfigured: Boolean
        get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    suspend fun createConnectToken(itemId: String? = null, oauthRedirectUri: String? = null): String {
        val options = JSONObject()
            .put("clientUserId", "meu-financeiro")
            .put("avoidDuplicates", true)
        if (!oauthRedirectUri.isNullOrBlank()) {
            options.put("oauthRedirectUri", oauthRedirectUri)
        }
        val payload = JSONObject().apply {
            if (itemId != null) put("itemId", itemId)
            put("options", options)
        }
        val response = execute("POST", "/connect_token", payload.toString(), apiKey())
        return response.optString("accessToken").takeIf { it.isNotBlank() }
            ?: response.optString("connectToken").takeIf { it.isNotBlank() }
            ?: throw PluggyException("A Pluggy não retornou um token de conexão.")
    }

    suspend fun listAccounts(itemId: String): List<PluggyAccount> =
        execute("GET", "/accounts?itemId=" + itemId, null, apiKey())
            .results()
            .map { PluggyAccount.fromJson(it) }

    suspend fun listBills(accountId: String): List<PluggyBill> {
        val all = mutableListOf<PluggyBill>()
        var page = 1
        while (true) {
            val root = execute(
                "GET",
                "/bills?accountId=" + accountId + "&page=" + page + "&pageSize=500",
                null,
                apiKey(),
            )
            val results = root.results()
            results.mapNotNullTo(all) { PluggyBill.fromJson(it, accountId) }
            val totalPages = root.optInt("totalPages", 1)
            if (results.isEmpty() || page >= totalPages || page >= 50) break
            page++
        }
        return all
    }

    /**
     * Busca uma fatura pelo id. Alguns bancos (ex.: Santander) referenciam faturas
     * futuras nos lançamentos mas não as listam em /bills; este endpoint recupera
     * essas faturas diretamente.
     */
    suspend fun getBill(billId: String, accountId: String): PluggyBill? {
        val json = execute("GET", "/bills/" + billId, null, apiKey())
        return PluggyBill.fromJson(json, accountId).takeIf { it.id.isNotBlank() }
    }

    /** Investimentos do item, paginados por pagina (page/pageSize). */
    suspend fun listInvestments(itemId: String): List<PluggyInvestment> {
        val all = mutableListOf<PluggyInvestment>()
        var page = 1
        while (true) {
            val root = execute(
                "GET",
                "/investments?itemId=" + itemId + "&page=" + page + "&pageSize=500",
                null,
                apiKey(),
            )
            val results = root.results()
            results.mapNotNullTo(all) { PluggyInvestment.fromJson(it, itemId) }
            val totalPages = root.optInt("totalPages", 1)
            if (results.isEmpty() || page >= totalPages || page >= 50) break
            page++
        }
        return all
    }

    suspend fun getItem(itemId: String): JSONObject =
        execute("GET", "/items/" + itemId, null, apiKey())

    /**
     * PATCH /items/{id}: pede à Pluggy uma nova coleta no banco. Sem isso,
     * /accounts e /transactions devolvem apenas o último snapshot em cache e
     * compras recentes não aparecem.
     */
    suspend fun updateItem(itemId: String): JSONObject =
        execute("PATCH", "/items/" + itemId, "{}", apiKey())

    suspend fun getItemExecutionStatus(itemId: String): String? {
        val item = getItem(itemId)
        return item.optString("executionStatus").takeIf { it.isNotBlank() && it != "null" }
    }

    /**
     * Endpoint v2 com paginação por cursor. A resposta traz "next" pronto para uso
     * (ex.: "?accountId=...&after=..."), que deve ser concatenado ao path.
     */
    suspend fun listTransactions(accountId: String): List<PluggyTransaction> {
        val all = mutableListOf<PluggyTransaction>()
        var query = "?accountId=" + accountId
        var page = 0
        while (true) {
            val root = execute("GET", "/v2/transactions" + query, null, apiKey())
            val results = root.results()
            results.mapTo(all) { PluggyTransaction.fromJson(it) }
            val next = root.optString("next")
            if (results.isEmpty() || next.isBlank() || next == "null" || page++ >= 50) break
            query = next
        }
        return all
    }

    suspend fun deleteItem(itemId: String) {
        execute("DELETE", "/items/" + itemId, null, apiKey())
    }

    private suspend fun apiKey(): String {
        val cached = apiKey
        if (cached != null && System.currentTimeMillis() < apiKeyExpiresAt - 60_000L) return cached
        return authMutex.withLock {
            val again = apiKey
            if (again != null && System.currentTimeMillis() < apiKeyExpiresAt - 60_000L) {
                return@withLock again
            }
            val payload = JSONObject()
                .put("clientId", clientId)
                .put("clientSecret", clientSecret)
                .toString()
            val response = execute("POST", "/auth", payload, null)
            val key = response.optString("apiKey").takeIf { it.isNotBlank() }
                ?: throw PluggyException("Credenciais da Pluggy inválidas.")
            apiKey = key
            apiKeyExpiresAt = System.currentTimeMillis() + 2L * 60L * 60L * 1000L
            key
        }
    }

    private fun JSONObject.results(): List<JSONObject> {
        val array = optJSONArray("results") ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private suspend fun execute(
        method: String,
        path: String,
        body: String?,
        apiKey: String?,
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + path
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply {
                if (apiKey != null) header("X-API-KEY", apiKey)
                if (body != null) {
                    method(method, body.toRequestBody(jsonMedia))
                } else {
                    method(method, null)
                }
            }
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(text).optString("message") }
                    .getOrDefault("")
                val message = if (detail.isBlank()) text.take(240) else detail
                throw PluggyException("Pluggy " + response.code + ": " + message)
            }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    companion object {
        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}