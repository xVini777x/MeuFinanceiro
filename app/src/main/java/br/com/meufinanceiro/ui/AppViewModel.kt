package br.com.meufinanceiro.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.meufinanceiro.BuildConfig
import br.com.meufinanceiro.data.CreditBill
import br.com.meufinanceiro.data.FinanceAccount
import br.com.meufinanceiro.data.Investment
import br.com.meufinanceiro.data.LocalStore
import br.com.meufinanceiro.data.Movement
import br.com.meufinanceiro.data.PluggyItemSummary
import br.com.meufinanceiro.data.QuoteService
import br.com.meufinanceiro.data.StockQuote
import br.com.meufinanceiro.pluggy.PluggyRepository
import br.com.meufinanceiro.pluggy.SyncResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.round

data class AppUiState(
    val movements: List<Movement> = emptyList(),
    val accounts: List<FinanceAccount> = emptyList(),
    val bills: List<CreditBill> = emptyList(),
    val investments: List<Investment> = emptyList(),
    val budgets: Map<String, Double> = emptyMap(),
    val items: List<PluggyItemSummary> = emptyList(),
    val configured: Boolean = false,
    val loadingToken: Boolean = false,
    val syncing: Boolean = false,
    val testing: Boolean = false,
    val connected: Boolean = false,
    val connectToken: String? = null,
    val updateItemId: String? = null,
    val error: String? = null,
    val message: String? = null,
    val diagnostics: List<String> = emptyList(),
    val dataVersion: Int = 0,
    /** Nome usado para reconhecer transferências entre contas do usuário. */
    val ownerName: String = "",
    /** Pede biometria ao abrir o app e ao voltar do segundo plano. */
    val biometricLock: Boolean = false,
    /**
     * Regras de categoria salvas pelo usuário: estabelecimento (título
     * normalizado) -> categoria. Vão por cima da classificação automática.
     */
    val categoryRules: Map<String, String> = emptyMap(),
) {
    private val sandboxItemIds: Set<String>
        get() = items.filter { it.isSandbox }.mapTo(HashSet()) { it.id }

    private val sandboxAccountIds: Set<String>
        get() = accounts
            .filter { it.itemId != null && it.itemId in sandboxItemIds }
            .mapTo(HashSet()) { it.id }

    /**
     * Dados de teste (sandbox) não entram nos totais pessoais: saldo, faturas,
     * extrato e investimentos consideram apenas as contas reais. O item segue
     * visível em Contas para poder ser desconectado.
     */
    val realAccounts: List<FinanceAccount> get() = accounts.filter { it.id !in sandboxAccountIds }

    /**
     * Contas que o usuário realmente tem: fora as pseudo-contas de transferência
     * entre contas (que o banco devolve para representar transferências do
     * próprio usuário) e os produtos de investimento, que já aparecem em
     * Investir. É o que conta como "conta conectada" na tela inicial.
     */
    val connectedAccounts: List<FinanceAccount>
        get() = realAccounts.filter { it.isUsableAccount && !it.isInvestmentProduct }

    val realBills: List<CreditBill> get() = bills.filter { it.accountId !in sandboxAccountIds }

    val realMovements: List<Movement>
        get() = movements.filter { it.accountId == null || it.accountId !in sandboxAccountIds }

    val realInvestments: List<Investment>
        get() = investments.filter { it.itemId == null || it.itemId !in sandboxItemIds }

    val hasSandboxData: Boolean get() = sandboxItemIds.isNotEmpty()
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val store = LocalStore(application)
    private val repository = PluggyRepository(store)
    private val quoteService = QuoteService(brapiToken = BuildConfig.BRAPI_TOKEN)

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var pendingSync = false
    private var itemSyncJob: Job? = null

    init {
        // Ao abrir o app, relê o snapshot da Pluggy em silêncio. O MeuPluggy
        // atualiza o banco uma vez por dia; sem isso o app só mudaria quando
        // o usuário tocasse em Sincronizar.
        viewModelScope.launch {
            delay(1_200L)
            if (repository.isConfigured && store.loadPluggyItems().isNotEmpty()) {
                startSync(null, silent = true)
            }
        }
    }

    private fun initialState(): AppUiState {
        store.pruneOrphanMovements()
        return AppUiState(
            movements = store.loadMovements(),
            accounts = store.loadAccounts(),
            bills = store.loadBills(),
            investments = store.loadInvestments(),
            budgets = store.loadBudgets(),
            items = store.loadPluggyItems(),
            configured = repository.isConfigured,
            ownerName = store.loadOwnerName(),
            biometricLock = store.loadBiometricLock(),
            categoryRules = store.loadCategoryOverrides(),
        )
    }

    private fun refresh(transform: (AppUiState) -> AppUiState = { it }) {
        _state.update { current ->
            transform(
                current.copy(
                    movements = store.loadMovements(),
                    accounts = store.loadAccounts(),
                    bills = store.loadBills(),
                    investments = store.loadInvestments(),
                    budgets = store.loadBudgets(),
                    items = store.loadPluggyItems(),
                    dataVersion = current.dataVersion + 1,
                    ownerName = store.loadOwnerName(),
                    biometricLock = store.loadBiometricLock(),
                    categoryRules = store.loadCategoryOverrides(),
                ),
            )
        }
    }

    // ------------------------------------------------------------- movimentações

    fun addMovement(movement: Movement, installments: Int = 1) {
        val items = buildInstallments(movement, installments)
        store.addMovements(items)
        refresh { current ->
            current.copy(
                message = if (items.size > 1) {
                    items.size.toString() + " parcelas adicionadas."
                } else {
                    "Movimentação adicionada."
                },
            )
        }
    }

    fun deleteMovement(id: String) {
        store.deleteMovement(id)
        refresh { it.copy(message = "Movimentação removida.") }
    }

    fun updateMovementCategory(id: String, newCategory: String, applyToSameTitle: Boolean = false) {
        store.updateMovementCategory(id, newCategory, applyToSameTitle)
        refresh {
            it.copy(
                message = if (applyToSameTitle) {
                    "Categoria alterada para $newCategory em lançamentos similares."
                } else {
                    "Categoria alterada para $newCategory."
                },
            )
        }
    }

    /**
     * Apaga a regra de um estabelecimento. Os lançamentos voltam para a
     * categoria automática.
     */
    fun removeCategoryRule(titleKey: String) {
        store.removeCategoryOverride(titleKey)
        refresh { it.copy(message = "Regra removida. Os lançamentos voltaram para a categoria automática.") }
    }

    fun setOwnerName(name: String) {
        store.saveOwnerName(name)
        refresh { it.copy(message = "Nome das transferências atualizado.") }
    }

    fun setBiometricLock(enabled: Boolean) {
        store.saveBiometricLock(enabled)
        refresh {
            it.copy(
                message = if (enabled) {
                    "Desbloqueio por biometria ativado."
                } else {
                    "Desbloqueio por biometria desativado."
                },
            )
        }
    }

    // ------------------------------------------------------------- investimentos

    fun saveInvestment(investment: Investment) {
        store.upsertInvestment(investment)
        refresh {
            it.copy(
                message = if (investment.itemId == null) "Investimento salvo." else "Investimento atualizado.",
            )
        }
    }

    /**
     * Busca a cotação das posições em ações e atualiza o valor de mercado.
     *
     * Só entram posições com código e quantidade de cotas: quem cadastrou
     * apenas um saldo manual nunca tem o valor sobrescrito pela bolsa.
     */
    fun refreshQuotes() {
        val quoted = _state.value.realInvestments.filter { investment ->
            !investment.ticker.isNullOrBlank() && (investment.quantity ?: 0.0) > 0.0
        }
        if (quoted.isEmpty()) {
            _state.update { it.copy(message = "Nenhuma posição com código de ação para cotar.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            runCatching {
                quoteService.quotes(quoted.mapNotNull { it.ticker })
            }.onSuccess { result ->
                var updated = 0
                result.quotes.forEach { (ticker, quote) ->
                    val investment = quoted.firstOrNull {
                        StockQuote.normalizeTicker(it.ticker) == ticker
                    } ?: return@forEach
                    val quantity = investment.quantity ?: return@forEach
                    val marketValue = round2(quantity * quote.price)
                    store.upsertInvestment(
                        investment.copy(
                            balance = marketValue,
                            amountProfit = investment.amountOriginal
                                ?.let { round2(marketValue - it) },
                            lastQuotedPrice = quote.price,
                            quoteChangePercent = quote.changePercent,
                            quoteUpdatedAt = System.currentTimeMillis(),
                        ),
                    )
                    updated++
                }
                refresh {
                    it.copy(
                        syncing = false,
                        message = if (updated > 0) {
                            updated.toString() + " cotação(ões) atualizada(s) via " +
                                result.source + "."
                        } else {
                            "Não foi possível atualizar as cotações."
                        },
                    )
                }
            }.onFailure {
                refresh {
                    it.copy(
                        syncing = false,
                        error = "Não foi possível buscar as cotações. Verifique a internet.",
                    )
                }
            }
        }
    }

    fun deleteInvestment(id: String) {
        store.deleteInvestment(id)
        refresh { it.copy(message = "Investimento removido.") }
    }

    fun setBudget(category: String, limit: Double) {
        val budgets = store.loadBudgets().toMutableMap()
        if (limit > 0.0) budgets[category] = limit else budgets.remove(category)
        store.saveBudgets(budgets)
        refresh {
            it.copy(
                message = if (limit > 0.0) {
                    "Orçamento de " + category + " atualizado."
                } else {
                    "Orçamento removido."
                },
            )
        }
    }

    // --------------------------------------------------------------------- Pluggy

    fun connectNewBank() = openWidget(null)

    fun updateBank(itemId: String) = openWidget(itemId)

    private fun openWidget(itemId: String?) {
        if (!repository.isConfigured) {
            _state.update {
                it.copy(error = "Credenciais da Pluggy não configuradas no local.properties.")
            }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loadingToken = true,
                    connected = false,
                    error = null,
                    message = null,
                    diagnostics = emptyList(),
                )
            }
            runCatching { repository.createConnectToken(itemId) }
                .onSuccess { token ->
                    _state.update {
                        it.copy(
                            loadingToken = false,
                            connectToken = token,
                            updateItemId = itemId,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            loadingToken = false,
                            error = failure.friendlyMessage(),
                            diagnostics = it.diagnostics + (failure.message ?: "erro ao criar token"),
                        )
                    }
                }
        }
    }

    fun onWidgetSuccess(itemId: String, itemJson: String?) {
        if (itemId.isBlank()) {
            onWidgetError("A Pluggy não retornou o identificador da conexão.")
            return
        }
        repository.saveItemFromWidget(itemId, itemJson)
        refresh {
            it.copy(
                connected = true,
                message = "Conexão recebida. Sincronizando...",
                error = null,
            )
        }
        viewModelScope.launch {
            runCatching { repository.waitForItem(itemId) }
            startSync("Banco conectado. Sincronizando...")
        }
    }

    /** Fecha o widget apos a conexao, mantendo os dados ja sincronizados. */
    fun finishConnection() {
        _state.update {
            it.copy(connectToken = null, updateItemId = null, connected = false)
        }
    }

    /** Fecha o widget e reabre o fluxo para conectar mais um banco. */
    fun connectAnotherBank() {
        finishConnection()
        connectNewBank()
    }

    fun addItemManually(itemId: String) {
        val trimmed = itemId.trim()
        if (trimmed.isBlank()) {
            _state.update { it.copy(error = "Informe o itemId da conexão.") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(syncing = true, message = "Adicionando conexão...", error = null)
            }
            runCatching {
                repository.importItem(trimmed)
                repository.waitForItem(trimmed)
            }.onSuccess {
                _state.update { it.copy(syncing = false) }
                startSync("Conexão adicionada. Sincronizando...")
            }.onFailure { failure ->
                _state.update {
                    it.copy(
                        syncing = false,
                        error = failure.friendlyMessage(),
                        diagnostics = it.diagnostics + (failure.message ?: "erro ao adicionar item"),
                    )
                }
            }
        }
    }

    fun onWidgetItem(itemId: String, itemJson: String?) {
        if (itemId.isBlank()) return
        repository.saveItemFromWidget(itemId, itemJson)
        refresh { it.copy(connected = true) }
        itemSyncJob?.cancel()
        itemSyncJob = viewModelScope.launch {
            delay(2_000L)
            startSync("Conexões recebidas. Sincronizando...")
        }
    }

    fun onWidgetClosed() {
        _state.update {
            it.copy(connectToken = null, updateItemId = null, connected = false)
        }
    }

    fun onWidgetError(message: String) {
        val friendly = friendlyPluggyError(message)
        _state.update {
            it.copy(
                connectToken = null,
                updateItemId = null,
                connected = false,
                error = friendly,
                diagnostics = it.diagnostics + message,
            )
        }
    }

    private fun friendlyPluggyError(message: String): String {
        if (message.isBlank()) return "Não foi possível conectar o banco."
        return when {
            message.contains("TRIAL_CLIENT_ITEM_CREATE_NOT_ALLOWED", ignoreCase = true) ->
                "Sua conta Pluggy está no modo trial e não pode conectar bancos reais. Para uso pessoal, conecte seus bancos em meu.pluggy.ai e escolha o conector MeuPluggy no widget (gratuito)."
            else -> message
        }
    }

    fun onWidgetDiagnostic(message: String) {
        if (message.isBlank()) return
        _state.update {
            it.copy(diagnostics = (it.diagnostics + message).takeLast(120))
        }
    }

    /** Persiste o item retornado pelo deep link OAuth, mesmo se o WebView tiver sido encerrado. */
    fun saveOAuthItem(itemId: String?) {
        if (itemId.isNullOrBlank()) return
        repository.saveItemFromWidget(itemId, null)
        refresh()
    }

    /** Chamado quando o WebView ainda está aberto e recebeu o retorno do OAuth. */
    fun onOAuthDelivered() {
        _state.update {
            it.copy(
                message = "Autorização concluída. Finalizando conexão...",
                error = null,
            )
        }
    }

    fun onOAuthReturn() {
        _state.update {
            it.copy(
                connectToken = null,
                updateItemId = null,
                connected = false,
                message = "Autorização concluída. Sincronizando...",
                error = null,
            )
        }
        viewModelScope.launch {
            delay(3_000L)
            startSync("Autorização concluída. Sincronizando...")
        }
    }

    fun sync() = startSync(null)

    private fun startSync(message: String?, silent: Boolean = false) {
        if (_state.value.syncing) {
            pendingSync = true
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, error = null, message = message) }
            runCatching { repository.syncAll() }
                .onSuccess { result ->
                    refresh {
                        it.copy(
                            syncing = false,
                            message = if (silent) null else result.message(),
                            error = if (silent) null else result.failures.firstOrNull(),
                            diagnostics = result.diagnostics,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            syncing = false,
                            error = if (silent) null else failure.friendlyMessage(),
                            diagnostics = it.diagnostics + (failure.message ?: "erro ao sincronizar"),
                        )
                    }
                }
            if (pendingSync) {
                pendingSync = false
                startSync("Sincronizando novas conexões...")
            }
        }
    }


    fun testConnection() {
        if (_state.value.testing) return
        viewModelScope.launch {
            _state.update { it.copy(testing = true, error = null, message = null) }
            runCatching { repository.testConnection() }
                .onSuccess { message ->
                    _state.update {
                        it.copy(
                            testing = false,
                            message = message,
                            diagnostics = it.diagnostics + message,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            testing = false,
                            error = failure.friendlyMessage(),
                            diagnostics = it.diagnostics + (failure.message ?: "erro no teste"),
                        )
                    }
                }
        }
    }

    fun disconnect(itemId: String) {
        viewModelScope.launch {
            repository.disconnect(itemId)
            refresh { it.copy(message = "Banco desconectado.", error = null) }
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(error = null, message = null) }
    }

    // -------------------------------------------------------------------- helpers

    private fun buildInstallments(base: Movement, installments: Int): List<Movement> {
        if (installments <= 1 || !base.isCreditCard) return listOf(base)
        val count = installments.coerceIn(2, 48)
        val per = round2(base.amount / count)
        return (1..count).map { index ->
            val value = if (index == count) round2(base.amount - per * (count - 1)) else per
            base.copy(
                id = UUID.randomUUID().toString(),
                amount = value,
                date = addMonths(base.date, index - 1),
                installmentNumber = index,
                totalInstallments = count,
            )
        }
    }

    private fun round2(value: Double): Double = round(value * 100.0) / 100.0

    private fun addMonths(epoch: Long, months: Int): Long {
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(epoch)
            .atZone(zone)
            .toLocalDate()
            .plusMonths(months.toLong())
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun Throwable.friendlyMessage(): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("USER_AUTHORIZATION_PENDING", ignoreCase = true) ->
                "Autorização pendente no banco. Conclua no app do banco e toque em Sincronizar."
            raw.contains("INVALID_CREDENTIALS", ignoreCase = true) ->
                "Credenciais do banco inválidas. Use Atualizar conexão e tente novamente."
            raw.contains("LOGIN_ERROR", ignoreCase = true) ->
                "Não foi possível entrar no banco. Verifique os dados e tente novamente."
            raw.contains("OUTDATED", ignoreCase = true) ->
                "A conexão está desatualizada. Use Atualizar conexão."
            raw.contains("403", ignoreCase = true) ->
                "A Pluggy recusou a operação. Verifique as credenciais e o plano da conta."
            raw.isBlank() ->
                "Não foi possível falar com a Pluggy. Verifique sua conexão."
            else -> raw
        }
    }
}