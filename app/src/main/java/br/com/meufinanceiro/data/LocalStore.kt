package br.com.meufinanceiro.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MergeResult(val added: Int, val updated: Int, val total: Int)

class LocalStore(context: Context) {

    private val prefs = context.getSharedPreferences("private_finance", Context.MODE_PRIVATE)

    /** Nome usado para reconhecer PIX/TED enviados para o próprio usuário. */
    private val defaultOwnerName: String =
        context.applicationContext.getString(br.com.meufinanceiro.R.string.user_name)

    // ---------------------------------------------------------------- movements

    /**
     * Lê um vetor JSON das preferências. Um dado corrompido não pode derrubar
     * o app: devolve um vetor vazio em vez de estourar a exceção.
     */
    private fun readArray(key: String): JSONArray {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    /**
     * Lê os lançamentos como estão gravados, sem aplicar regras de categoria.
     * Usado ao gravar de volta, para que a categoria automática não seja
     * sobrescrita pela categoria efetiva.
     */
    private fun readMovements(): List<Movement> {
        val array = readArray(KEY_MOVEMENTS)
        val parsed = (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { Movement.fromJson(it) }
        }
        // A mesma descrição aparece em dezenas de lançamentos: classificar uma
        // vez por título evita repetir o trabalho a cada leitura do app.
        val cache = HashMap<String, String>()
        return parsed.map { movement ->
            if (movement.source != MovementSource.PLUGGY) {
                movement
            } else {
                val key = movement.bankCategory.orEmpty() + "|" + movement.title
                val category = cache.getOrPut(key) {
                    CategoryClassifier.classify(movement.bankCategory, movement.title)
                }
                movement.copy(category = category)
            }
        }
    }

    /**
     * Lançamentos com a categoria efetiva: primeiro a escolha feita para este
     * lançamento, depois a regra salva para o mesmo estabelecimento e, por
     * último, a categoria automática do banco.
     */
    fun loadMovements(): List<Movement> {
        val rules = loadCategoryOverrides()
        val manual = loadManualCategories()
        val adjusted = readMovements().map { movement ->
            movement.copy(category = CategoryRules.resolve(movement, manual, rules))
        }
        // Transferências entre contas do usuário são marcadas na leitura, então
        // valem para dados antigos e para cada sincronização nova.
        return TransferDetector.mark(adjusted, loadOwnerName()).sortedByDescending { it.date }
    }

    fun saveMovements(items: List<Movement>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_MOVEMENTS, array.toString()).apply()
    }

    fun addMovements(items: List<Movement>) {
        saveMovements((items + readMovements()).sortedByDescending { it.date })
    }

    fun deleteMovement(id: String) {
        saveMovements(readMovements().filterNot { it.id == id })
        removeManualCategory(id)
    }

    /**
     * Reclassifica um lançamento. A escolha fica guardada à parte do dado
     * sincronizado: assim ela sobrevive a novas sincronizações, pode ser
     * desfeita e a categoria automática continua melhorando sozinha.
     *
     * Com [applyToSameTitle] vale a regra do estabelecimento, que já cobre
     * este lançamento — por isso a escolha individual é descartada, para que
     * apagar a regra reverta tudo.
     */
    fun updateMovementCategory(id: String, newCategory: String, applyToSameTitle: Boolean = false) {
        val target = readMovements().firstOrNull { it.id == id } ?: return
        val targetTitleKey = CategoryRules.titleKey(target.title)
        if (applyToSameTitle && targetTitleKey.isNotBlank()) {
            saveCategoryOverride(targetTitleKey, newCategory)
            removeManualCategory(id)
        } else {
            saveManualCategory(id, newCategory)
        }
    }

    /** Regras por estabelecimento: título normalizado -> categoria. */
    fun loadCategoryOverrides(): Map<String, String> {
        val raw = prefs.getString(KEY_CATEGORY_OVERRIDES, "{}") ?: "{}"
        val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        val map = mutableMapOf<String, String>()
        json.keys().forEach { key ->
            val value = json.optString(key)
            if (value.isNotBlank()) map[key] = value
        }
        return map
    }

    fun saveCategoryOverride(titleKey: String, category: String) {
        val current = loadCategoryOverrides().toMutableMap()
        current[CategoryRules.titleKey(titleKey)] = category
        val json = JSONObject()
        current.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_CATEGORY_OVERRIDES, json.toString()).apply()
    }

    /**
     * Remove a regra de um estabelecimento. Os lançamentos voltam para a
     * categoria automática.
     */
    fun removeCategoryOverride(titleKey: String) {
        val current = loadCategoryOverrides().toMutableMap()
        if (current.remove(CategoryRules.titleKey(titleKey)) == null) return
        val json = JSONObject()
        current.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_CATEGORY_OVERRIDES, json.toString()).apply()
    }

    /** Categorias escolhidas manualmente para um lançamento específico. */
    fun loadManualCategories(): Map<String, String> {
        val raw = prefs.getString(KEY_MANUAL_CATEGORIES, "{}") ?: "{}"
        val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        val map = mutableMapOf<String, String>()
        json.keys().forEach { key ->
            val value = json.optString(key)
            if (value.isNotBlank()) map[key] = value
        }
        return map
    }

    private fun saveManualCategory(id: String, category: String) {
        val current = loadManualCategories().toMutableMap()
        current[id] = category
        val json = JSONObject()
        current.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_MANUAL_CATEGORIES, json.toString()).apply()
    }

    private fun removeManualCategory(id: String) {
        val current = loadManualCategories().toMutableMap()
        if (current.remove(id) == null) return
        val json = JSONObject()
        current.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_MANUAL_CATEGORIES, json.toString()).apply()
    }

    fun mergeMovements(imported: List<Movement>): MergeResult {
        val existing = readMovements()
        val rules = loadCategoryOverrides()
        val manual = loadManualCategories()
        // Remove duplicatas exatas que alguns conectores devolvem com ids diferentes
        // (mesmo lançamento, mesmo valor e mesmo horário) e evita reimportá-las.
        val existingDeduped = existing.distinctBy { it.exactKey() }
        val byId = LinkedHashMap<String, Movement>()
        existingDeduped.forEach { byId[it.id] = it }
        val existingKeys = existingDeduped.mapTo(HashSet()) { it.reconcileKey() }
        var added = 0
        var updated = 0
        imported.forEach { movement ->
            val key = movement.reconcileKey()
            // A categoria automática volta a valer; as escolhas do usuário e as
            // regras por estabelecimento são reaplicadas por cima.
            val resolved = movement.copy(
                category = CategoryRules.resolve(movement, manual, rules),
            )
            when {
                byId.containsKey(movement.id) -> {
                    byId[movement.id] = resolved
                    updated++
                }
                existingKeys.contains(key) -> Unit
                else -> {
                    byId[movement.id] = resolved
                    existingKeys += key
                    added++
                }
            }
        }
        val merged = byId.values.sortedByDescending { it.date }
        saveMovements(merged)
        return MergeResult(added = added, updated = updated, total = merged.size)
    }

    private fun Movement.exactKey(): String {
        val cents = Math.round(amount * 100)
        return listOf(
            title.trim().lowercase(),
            cents.toString(),
            date.toString(),
            accountId.orEmpty(),
            (installmentNumber ?: 0).toString(),
        ).joinToString("|")
    }

    // ----------------------------------------------------------------- accounts

    fun loadAccounts(): List<FinanceAccount> {
        val array = readArray(KEY_ACCOUNTS)
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { FinanceAccount.fromJson(it) }
        }
    }

    fun saveAccounts(items: List<FinanceAccount>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply()
    }

    fun mergeAccounts(items: List<FinanceAccount>) {
        val byId = LinkedHashMap<String, FinanceAccount>()
        loadAccounts().forEach { byId[it.id] = it }
        items.forEach { byId[it.id] = it }
        saveAccounts(byId.values.toList())
    }

    // -------------------------------------------------------------------- bills

    fun loadBills(): List<CreditBill> {
        val array = readArray(KEY_BILLS)
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { CreditBill.fromJson(it) }
        }
    }

    fun saveBills(items: List<CreditBill>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_BILLS, array.toString()).apply()
    }

    fun mergeBills(items: List<CreditBill>) {
        val byId = LinkedHashMap<String, CreditBill>()
        loadBills().forEach { byId[it.id] = it }
        items.forEach { byId[it.id] = it }
        saveBills(byId.values.toList())
    }

    // -------------------------------------------------------------- investments

    fun loadInvestments(): List<Investment> {
        val array = readArray(KEY_INVESTMENTS)
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { Investment.fromJson(it) }
        }.sortedByDescending { it.balance }
    }

    fun saveInvestments(items: List<Investment>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_INVESTMENTS, array.toString()).apply()
    }

    fun mergeInvestments(items: List<Investment>) {
        val byId = LinkedHashMap<String, Investment>()
        loadInvestments().forEach { byId[it.id] = it }
        items.forEach { byId[it.id] = it }
        saveInvestments(byId.values.toList())
    }

    /** Cria ou atualiza um investimento cadastrado manualmente. */
    fun upsertInvestment(investment: Investment) {
        val byId = LinkedHashMap<String, Investment>()
        loadInvestments().forEach { byId[it.id] = it }
        byId[investment.id] = investment
        saveInvestments(byId.values.toList())
    }

    fun deleteInvestment(id: String) {
        saveInvestments(loadInvestments().filterNot { it.id == id })
    }
    // ------------------------------------------------------------------ budgets

    fun loadBudgets(): Map<String, Double> {
        val raw = prefs.getString(KEY_BUDGETS, "{}").orEmpty()
        val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        val result = LinkedHashMap<String, Double>()
        json.keys().forEach { key ->
            val value = json.optDouble(key, 0.0)
            if (value > 0.0) result[key] = value
        }
        return result
    }

    fun saveBudgets(budgets: Map<String, Double>) {
        val json = JSONObject()
        budgets.forEach { (key, value) -> json.put(key, value) }
        prefs.edit().putString(KEY_BUDGETS, json.toString()).apply()
    }

    // ------------------------------------------------------------- pluggy items

    fun loadPluggyItems(): List<PluggyItemSummary> {
        val array = readArray(KEY_ITEMS)
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { PluggyItemSummary.fromJson(it) }
        }
    }

    fun savePluggyItems(items: List<PluggyItemSummary>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    fun upsertPluggyItem(item: PluggyItemSummary) {
        savePluggyItems(loadPluggyItems().filterNot { it.id == item.id } + item)
    }

    fun removePluggyItem(itemId: String) {
        savePluggyItems(loadPluggyItems().filterNot { it.id == itemId })
    }

    /** Remove contas, faturas e lançamentos vinculados a um item desconectado. */
    fun removeItemData(itemId: String) {
        val accounts = loadAccounts()
        val removedAccountIds = accounts.filter { it.itemId == itemId }.map { it.id }.toSet()
        saveAccounts(accounts.filterNot { it.itemId == itemId })
        saveInvestments(loadInvestments().filterNot { it.itemId == itemId })
        if (removedAccountIds.isNotEmpty()) {
            saveBills(loadBills().filterNot { it.accountId in removedAccountIds })
            saveMovements(readMovements().filterNot { it.accountId in removedAccountIds })
        }
    }

    /**
     * Remove lançamentos de contas que não existem mais (ex.: item sandbox ou
     * banco desconectado). Sem isso, restos de um cartão removido continuam
     * aparecendo em "Compras parceladas" e em totais.
     */
    fun pruneOrphanMovements(): Int {
        val accountIds = loadAccounts().mapTo(HashSet()) { it.id }
        val movements = readMovements()
        val kept = movements.filter { it.accountId.isNullOrBlank() || it.accountId in accountIds }
        val removed = movements.size - kept.size
        if (removed > 0) saveMovements(kept)
        return removed
    }

    // ------------------------------------------------------------- preferências

    fun loadOwnerName(): String =
        prefs.getString(KEY_OWNER_NAME, null)?.takeIf { it.isNotBlank() } ?: defaultOwnerName

    fun saveOwnerName(name: String) {
        prefs.edit().putString(KEY_OWNER_NAME, name.trim()).apply()
    }

    /** Desbloqueio por biometria ao entrar no app. */
    fun loadBiometricLock(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_LOCK, false)

    fun saveBiometricLock(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, enabled).apply()
    }

    companion object {
        private const val KEY_MOVEMENTS = "movements"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_BILLS = "bills"
        private const val KEY_BUDGETS = "budgets"
        private const val KEY_ITEMS = "pluggy_items"
        private const val KEY_INVESTMENTS = "investments"
        private const val KEY_OWNER_NAME = "owner_name"
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock"
        private const val KEY_CATEGORY_OVERRIDES = "category_overrides"
        private const val KEY_MANUAL_CATEGORIES = "manual_categories"
    }
}