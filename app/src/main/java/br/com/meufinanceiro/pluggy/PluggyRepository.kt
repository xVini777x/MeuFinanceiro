package br.com.meufinanceiro.pluggy

import br.com.meufinanceiro.BuildConfig
import br.com.meufinanceiro.data.CreditBill
import br.com.meufinanceiro.data.FinanceCalculator
import br.com.meufinanceiro.data.FinanceAccount
import br.com.meufinanceiro.data.InvoiceStatus
import br.com.meufinanceiro.data.Investment
import br.com.meufinanceiro.data.LocalStore
import br.com.meufinanceiro.data.Movement
import br.com.meufinanceiro.data.PluggyItemSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class SyncResult(
    val addedMovements: Int,
    val updatedMovements: Int,
    val totalMovements: Int,
    val accounts: List<FinanceAccount>,
    val bills: List<CreditBill>,
    val investments: List<Investment>,
    val failures: List<String>,
    val diagnostics: List<String>,
) {
    fun message(): String = when {
        failures.isNotEmpty() && addedMovements == 0 -> "Sincronizado com avisos: " + failures.first()
        addedMovements == 0 && investments.isNotEmpty() ->
            "Nenhuma transação nova. " + investments.size +
                " investimento(s). Veja o diagnóstico para a data da última coleta do banco."
        addedMovements == 0 ->
            "Nenhuma transação nova. Veja o diagnóstico para a data da última coleta do banco."
        investments.isNotEmpty() ->
            addedMovements.toString() + " transações novas importadas • " +
                investments.size + " investimento(s)."
        else -> addedMovements.toString() + " transações novas importadas."
    }
}

class PluggyRepository(private val store: LocalStore) {

    private val client = PluggyClient(
        clientId = BuildConfig.PLUGGY_CLIENT_ID,
        clientSecret = BuildConfig.PLUGGY_CLIENT_SECRET,
        baseUrl = BuildConfig.PLUGGY_API_BASE_URL,
    )

    val isConfigured: Boolean
        get() = client.isConfigured

    suspend fun createConnectToken(itemId: String? = null): String =
        client.createConnectToken(itemId, OAUTH_REDIRECT_URI)

    fun saveItemFromWidget(itemId: String, itemJson: String?) {
        val parsed = itemJson
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { PluggyItemSummary.fromJson(JSONObject(it)) }.getOrNull() }
        val item = parsed?.copy(id = itemId)
            ?: PluggyItemSummary(itemId, "Banco conectado", "CONNECTED")
        store.upsertPluggyItem(item)
    }

    /** Importa uma conexão pelo itemId (necessário para MeuPluggy, onde GET /v2/items não está disponível). */
    suspend fun importItem(itemId: String) = withContext(Dispatchers.IO) {
        val json = client.getItem(itemId)
        val summary = PluggyItemSummary.fromJson(json)?.copy(id = itemId)
            ?: PluggyItemSummary(itemId, "Banco conectado", "CONNECTED")
        store.upsertPluggyItem(summary)
    }

    /** Aguarda a Pluggy concluir a coleta de dados do item recém-conectado. */
    suspend fun waitForItem(itemId: String, timeoutMs: Long = 120_000L) = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val status = runCatching { client.getItemExecutionStatus(itemId) }.getOrNull()
            if (status != null && status in TERMINAL_STATUSES) return@withContext
            delay(2_000L)
        }
    }

    /**
     * PATCH /items/{id} dispara uma nova coleta no banco. Sem esse pedido a Pluggy
     * devolve apenas o snapshot em cache e compras recentes ficam de fora.
     * Aguarda a coleta terminar para ler os dados já atualizados.
     */
    private suspend fun requestFreshCollection(
        item: PluggyItemSummary,
        diagnostics: MutableList<String>,
        timeoutMs: Long = 180_000L,
    ) {
        val request = runCatching { client.updateItem(item.id) }
        val failure = request.exceptionOrNull()
        if (failure != null) {
            val message = failure.message ?: "indisponível"
            when {
                message.contains("429") -> {
                    diagnostics += item.connectorName +
                        ": limite de sincronizações da Pluggy atingido. Usando o último snapshot."
                    return
                }
                message.contains("cant be updated", ignoreCase = true) -> {
                    diagnostics += item.connectorName +
                        ": este conector é atualizado pelo MeuPluggy, não pela Pluggy. " +
                        "Abra meu.pluggy.ai, atualize o banco e toque em Sincronizar novamente."
                    return
                }
                message.contains("409") -> {
                    diagnostics += item.connectorName +
                        ": já existe uma coleta em andamento. Aguardando a Pluggy concluir."
                }
                else -> {
                    diagnostics += item.connectorName +
                        ": não foi possível pedir nova coleta (" + message + "). Usando o último snapshot."
                    return
                }
            }
        }

        val initial = request.getOrNull()
            ?.optString("executionStatus")
            ?.takeIf { it.isNotBlank() && it != "null" }
        if (initial != null && initial in TERMINAL_STATUSES) {
            diagnostics += item.connectorName + ": coleta concluída (" + initial + ")."
            return
        }

        val deadline = System.currentTimeMillis() + timeoutMs
        var lastStatus = initial
        while (System.currentTimeMillis() < deadline) {
            delay(3_000L)
            val status = runCatching { client.getItemExecutionStatus(item.id) }.getOrNull()
            if (status.isNullOrBlank()) continue
            lastStatus = status
            if (status in TERMINAL_STATUSES) {
                diagnostics += item.connectorName + ": coleta concluída (" + status + ")."
                return
            }
        }
        diagnostics += item.connectorName + ": a Pluggy ainda está coletando (" +
            (lastStatus ?: "sem status") + "). Usando os dados disponíveis."
    }

    suspend fun syncAll(): SyncResult = withContext(Dispatchers.IO) {
        val items = store.loadPluggyItems()
        if (items.isEmpty()) {
            return@withContext SyncResult(
                addedMovements = 0,
                updatedMovements = 0,
                totalMovements = store.loadMovements().size,
                accounts = store.loadAccounts(),
                bills = store.loadBills(),
                investments = store.loadInvestments(),
                failures = listOf("Nenhum banco conectado. Use Conectar banco primeiro."),
                diagnostics = emptyList(),
            )
        }

        val allAccounts = mutableListOf<FinanceAccount>()
        val allBills = mutableListOf<CreditBill>()
        val allInvestments = mutableListOf<Investment>()
        val imported = mutableListOf<Movement>()
        val failures = mutableListOf<String>()
        val diagnostics = mutableListOf<String>()
        val syncedBillAccountIds = mutableSetOf<String>()
        val syncedInvestmentItemIds = mutableSetOf<String>()

        items.forEach { storedItem ->
            var item = storedItem

            // Pede à Pluggy uma coleta nova antes de ler contas/transações.
            // Sandbox é estático, então não precisa de nova coleta.
            if (!item.isSandbox) {
                requestFreshCollection(item, diagnostics)
            }

            runCatching { client.getItem(item.id) }
                .onSuccess { json ->
                    json.optString("lastUpdatedAt")
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?.let { diagnostics += item.connectorName + ": última coleta na Pluggy em " + it + "." }
                    PluggyItemSummary.fromJson(json)?.copy(id = item.id)?.let { fresh ->
                        item = fresh
                        store.upsertPluggyItem(fresh)
                    }
                }
                .onFailure { failure ->
                    diagnostics += item.connectorName + ": não foi possível atualizar o item (" +
                        (failure.message ?: "indisponível") + ")"
                }

            val products = item.products.joinToString(", ").ifBlank { "não informados" }
            diagnostics += item.connectorName + " (conector " + (item.connectorId ?: "?") +
                "): produtos " + products

            if (item.products.isNotEmpty() && "INVESTMENTS" !in item.products) {
                diagnostics += if (item.connectorId == 206) {
                    item.connectorName + ": conectado pelo conector 206 (antigo), que não expõe " +
                        "investimentos. Reconecte pelo Mercado Pago (conector 606) para trazer CDB/caixinha."
                } else {
                    item.connectorName + ": o conector não expõe investimentos. Reconecte pelo " +
                        "Mercado Pago (conector 606) ou MeuPluggy (conector 200) para trazer CDB/caixinha."
                }
            }

            runCatching { client.listInvestments(item.id) }
                .onSuccess { investments ->
                    syncedInvestmentItemIds += item.id
                    allInvestments += investments.map { it.toInvestment(item.id) }
                    diagnostics += item.connectorName + ": " + investments.size + " investimento(s)."
                    if (investments.isEmpty()) {
                        diagnostics += if (item.connectorId == 606) {
                            item.connectorName + ": o conector 606 expõe investimentos, mas o banco " +
                                "não retornou nenhum. Verifique se a caixinha está ativa e sincronize novamente."
                        } else {
                            item.connectorName + ": nenhum investimento retornado. Se você tem " +
                                "CDB/caixinha, reconecte pelo Mercado Pago (606) ou MeuPluggy (200) e sincronize novamente."
                        }
                    }
                }
                .onFailure { failure ->
                    diagnostics += "Investimentos de " + item.connectorName + ": " +
                        (failure.message ?: "indisponível")
                }

            runCatching {
                val accounts = client.listAccounts(item.id)
                diagnostics += item.connectorName + ": " + accounts.size + " conta(s) encontrada(s)."
                accounts.forEach { account ->
                    allAccounts += account.toFinanceAccount()
                    diagnostics += account.displayName + ": vencimento " +
                        (account.dueDate ?: "não informado") + ", fechamento " +
                        (account.closeDate ?: "não informado") + ", saldo " +
                        FinanceCalculator.brl(account.balance)

                    val transactions = client.listTransactions(account.id)
                    val pendingCount = transactions.count { it.status.equals("PENDING", true) }
                    val futureCount = transactions.count {
                        (FinanceCalculator.parseIsoDate(it.date) ?: 0L) > System.currentTimeMillis()
                    }
                    val withBillCount = transactions.count { !it.billId.isNullOrBlank() }
                    diagnostics += account.displayName + ": " + transactions.size + " transação(ões) • " +
                        pendingCount + " pendente(s) • " + futureCount + " futura(s) • " +
                        withBillCount + " com billId."
                    transactions.forEach { transaction ->
                        transaction.toMovement(account)?.let { imported += it }
                    }
                    if (account.isCredit) {
                        val credits = transactions.filter { it.isCreditIncome() }
                        val positives = transactions.count { it.amount > 0.0 }
                        val negatives = transactions.count { it.amount < 0.0 }
                        diagnostics += account.displayName + ": " +
                            credits.size + " crédito(s) no cartão • " +
                            (transactions.size - credits.size) + " compra(s)/débito(s) • sinais +" +
                            positives + "/−" + negatives + "."
                        if (credits.isNotEmpty()) {
                            diagnostics += "  créditos: " + credits.take(8).joinToString(" | ") { transaction ->
                                transaction.description.take(28) + " " +
                                    FinanceCalculator.brl(kotlin.math.abs(transaction.amount)) +
                                    " [" + transaction.type + "/" +
                                    (transaction.operationType ?: "-") + "]"
                            }
                        }
                    }

                    if (account.isCredit) {
                        val listed = runCatching { client.listBills(account.id) }
                        val listedBills = listed.getOrNull().orEmpty()
                        if (listed.isSuccess) {
                            syncedBillAccountIds += account.id
                            diagnostics += account.displayName + ": " + listedBills.size + " fatura(s)."
                        } else {
                            diagnostics += "Faturas de " + account.displayName + ": " +
                                (listed.exceptionOrNull()?.message ?: "indisponível")
                        }

                        val knownBillIds = listedBills.mapTo(HashSet()) { it.id }
                        val missingBillIds = transactions
                            .mapNotNull { it.billId }
                            .filter { it.isNotBlank() && it !in knownBillIds }
                            .distinct()
                            .take(24)
                        val fetchedBills = mutableListOf<PluggyBill>()
                        missingBillIds.forEach { billId ->
                            runCatching { client.getBill(billId, account.id) }
                                .onSuccess { bill -> if (bill != null) fetchedBills += bill }
                                .onFailure { /* 404 esperado para faturas ainda não emitidas */ }
                        }
                        if (fetchedBills.isNotEmpty()) {
                            diagnostics += account.displayName + ": " + fetchedBills.size +
                                " fatura(s) futura(s) recuperada(s) por id."
                        }
                        val accountBills = (listedBills + fetchedBills).distinctBy { it.id }
                        accountBills
                            .map { it.toCreditBill() }
                            .sortedBy { it.dueDate }
                            .takeLast(4)
                            .forEach { bill ->
                                val paidAt = bill.lastPaymentDate?.let {
                                    ", pago em " + FinanceCalculator.formatDate(it)
                                }.orEmpty()
                                diagnostics += "  " +
                                    FinanceCalculator.formatDate(bill.dueDate) +
                                    ": total " + FinanceCalculator.brl(bill.totalAmount) +
                                    ", pago " + FinanceCalculator.brl(bill.paidAmount) + paidAt
                            }
                        val upcoming = accountBills
                            .map { it.toCreditBill() }
                            .filter { it.dueDate > System.currentTimeMillis() }
                            .sortedBy { it.dueDate }
                            .take(8)
                        if (upcoming.isNotEmpty()) {
                            diagnostics += "  próximas: " + upcoming.joinToString(" | ") { bill ->
                                FinanceCalculator.formatDate(bill.dueDate) + " " +
                                    FinanceCalculator.brl(bill.totalAmount)
                            }
                        }
                        allBills += accountBills.map { it.toCreditBill() }
                    }
                }
            }.onFailure { failure ->
                failures += item.connectorName + ": " + (failure.message ?: "falha ao sincronizar")
                diagnostics += "Erro em " + item.connectorName + ": " + (failure.message ?: "desconhecido")
            }
        }

        store.mergeAccounts(allAccounts)

        // Descarta lançamentos de contas que não existem mais (sandbox/banco removido).
        val pruned = store.pruneOrphanMovements()
        if (pruned > 0) {
            diagnostics += pruned.toString() + " lançamento(s) de conta removida descartado(s)."
        }

        // Substitui faturas/investimentos dos itens sincronizados com sucesso para
        // não manter registros antigos que o banco deixou de devolver.
        val existingBills = store.loadBills()
        val keptBills = existingBills.filterNot { it.accountId in syncedBillAccountIds }
        store.saveBills((keptBills + allBills).distinctBy { it.id })

        val existingInvestments = store.loadInvestments()
        val keptInvestments = existingInvestments.filterNot { it.itemId in syncedInvestmentItemIds }
        store.saveInvestments((keptInvestments + allInvestments).distinctBy { it.id })

        val merge = store.mergeMovements(imported)

        val storedAccounts = store.loadAccounts()
        val storedBills = store.loadBills()
        val storedMovements = store.loadMovements()
        storedAccounts.filter { it.isCredit }.forEach { account ->
            val entries = FinanceCalculator.invoiceEntries(account, storedBills, storedMovements)
            val current = entries.firstOrNull { it.isCurrent }
            val future = entries.filter { it.status == InvoiceStatus.FUTURE }
            val overdue = entries.filter { it.status == InvoiceStatus.OVERDUE }
            diagnostics += account.displayName + ": " + entries.size + " fatura(s) no app • atual " +
                (current?.let {
                    FinanceCalculator.formatMonth(it.summary.month) + " (" + it.status.label + ")"
                } ?: "nenhuma")
            if (overdue.isNotEmpty()) {
                diagnostics += "  vencidas: " + overdue.take(6).joinToString(" | ") {
                    FinanceCalculator.formatMonth(it.summary.month) + " " +
                        FinanceCalculator.brl(it.summary.pending)
                }
            }
            if (future.isNotEmpty()) {
                diagnostics += "  futuras: " + future.take(8).joinToString(" | ") {
                    FinanceCalculator.formatMonth(it.summary.month) + " " +
                        FinanceCalculator.brl(it.summary.total) +
                        if (it.isProjected) " (projeção)" else ""
                }
            }
        }

        SyncResult(
            addedMovements = merge.added,
            updatedMovements = merge.updated,
            totalMovements = merge.total,
            accounts = store.loadAccounts(),
            bills = store.loadBills(),
            investments = store.loadInvestments(),
            failures = failures,
            diagnostics = diagnostics,
        )
    }
    suspend fun disconnect(itemId: String) = withContext(Dispatchers.IO) {
        runCatching { client.deleteItem(itemId) }
        store.removePluggyItem(itemId)
        store.removeItemData(itemId)
        store.pruneOrphanMovements()
    }

    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val token = client.createConnectToken(null, OAUTH_REDIRECT_URI)
        "Conexão com a Pluggy OK. Token gerado com " + token.length + " caracteres."
    }

    companion object {
        const val OAUTH_REDIRECT_URI = "meufinanceiro://pluggy-callback"

        private val TERMINAL_STATUSES = setOf(
            "SUCCESS",
            "PARTIAL_SUCCESS",
            "ERROR",
            "LOGIN_ERROR",
            "INVALID_CREDENTIALS",
            "OUTDATED",
            "USER_AUTHORIZATION_PENDING",
        )
    }
}