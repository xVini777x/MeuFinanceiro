package br.com.meufinanceiro.data

import org.json.JSONObject
import java.util.UUID

enum class MovementType(val label: String) {
    INCOME("Receita"),
    EXPENSE("Despesa");

    companion object {
        fun from(value: String?): MovementType = when (value?.trim()?.lowercase()) {
            "receita", "income", "credit", "crédito", "credito" -> INCOME
            else -> EXPENSE
        }
    }
}

enum class MovementSource { MANUAL, PLUGGY }

data class Movement(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val amount: Double,
    val type: MovementType,
    val category: String,
    val date: Long = System.currentTimeMillis(),
    val accountId: String? = null,
    val accountName: String? = null,
    val isCreditCard: Boolean = false,
    val installmentNumber: Int? = null,
    val totalInstallments: Int? = null,
    val billId: String? = null,
    /** Mês de fechamento previsto pela Pluggy (creditCardMetadata.billForecastDate). */
    val billMonth: String? = null,
    val status: String = "POSTED",
    val notes: String? = null,
    val source: MovementSource = MovementSource.MANUAL,
    /**
     * Transferência entre contas do próprio usuário (ex.: PIX de um banco para
     * outro). Não é receita nem despesa: fica de fora dos totais, mas continua
     * visível no extrato. Recalculado a cada leitura, não é persistido.
     */
    val internalTransfer: Boolean = false,
    /**
     * Categoria crua devolvida pelo banco (Open Finance). Fica guardada para
     * que a classificação possa ser refeita quando as regras mudarem, sem
     * depender de uma nova sincronização.
     */
    val bankCategory: String? = null,
) {
    val isIncome: Boolean get() = type == MovementType.INCOME
    val installmentLabel: String?
        get() {
            val total = totalInstallments ?: return null
            val current = installmentNumber ?: return null
            return "$current/$total"
        }

    fun reconcileKey(): String {
        val cents = Math.round(amount * 100)
        val day = date / 86_400_000L
        return listOf(day, cents, title.trim().lowercase(), accountId.orEmpty()).joinToString("|")
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("amount", amount)
        put("type", type.name)
        put("category", category)
        put("date", date)
        accountId?.let { put("accountId", it) }
        accountName?.let { put("accountName", it) }
        put("isCreditCard", isCreditCard)
        installmentNumber?.let { put("installmentNumber", it) }
        totalInstallments?.let { put("totalInstallments", it) }
        billId?.let { put("billId", it) }
        billMonth?.let { put("billMonth", it) }
        put("status", status)
        notes?.let { put("notes", it) }
        put("source", source.name)
        bankCategory?.let { put("bankCategory", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): Movement? = runCatching {
            Movement(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = o.optString("title"),
                amount = o.optDouble("amount", 0.0),
                type = MovementType.from(o.optString("type")),
                category = o.optString("category", "Outros").ifBlank { "Outros" },
                date = o.optLong("date", System.currentTimeMillis()),
                accountId = o.optString("accountId").takeIf { it.isNotBlank() && it != "null" },
                accountName = o.optString("accountName").takeIf { it.isNotBlank() && it != "null" },
                isCreditCard = o.optBoolean("isCreditCard", false),
                installmentNumber = o.optInt("installmentNumber", 0).takeIf { it > 0 },
                totalInstallments = o.optInt("totalInstallments", 0).takeIf { it > 0 },
                billId = o.optString("billId").takeIf { it.isNotBlank() && it != "null" },
                billMonth = o.optString("billMonth").takeIf { it.isNotBlank() && it != "null" },
                status = o.optString("status", "POSTED"),
                notes = o.optString("notes").takeIf { it.isNotBlank() && it != "null" },
                source = if (o.optString("source") == MovementSource.PLUGGY.name) {
                    MovementSource.PLUGGY
                } else {
                    MovementSource.MANUAL
                },
                bankCategory = o.optString("bankCategory").takeIf { it.isNotBlank() && it != "null" },
            )
        }.getOrNull()
    }
}

/**
 * Nomes de produtos de investimento que alguns bancos devolvem como se
 * fossem contas. O dinheiro deles já aparece em Investir, então não contam
 * como conta conectada.
 */
private val INVESTMENT_ACCOUNT_NAMES = listOf(
    "SEGURANCA",
    "BLINDADO",
    "INVESTIMENTO",
    "RENDA FIXA",
    "TESOURO",
)

data class FinanceAccount(
    val id: String,
    val itemId: String? = null,
    val name: String,
    val type: String,
    val subtype: String,
    val balance: Double,
    val number: String? = null,
    val currency: String = "BRL",
    val creditLimit: Double? = null,
    val availableCreditLimit: Double? = null,
    val closeDate: String? = null,
    val dueDate: String? = null,
    val brand: String? = null,
    val level: String? = null,
) {
    val isCredit: Boolean get() = type.equals("CREDIT", true)

    /**
     * Pseudo-conta: alguns bancos devolvem uma conta chamada "Transferência
     * entre contas" para representar as transferências entre contas do próprio
     * usuário. Não tem saldo próprio, não é fatura e não serve para lançar
     * nada — então não entra na lista de contas do app.
     */
    val isPseudoTransfer: Boolean
        get() {
            val key = TransferDetector.normalize(name)
            return key.contains("TRANSFERENCIA ENTRE CONTAS") ||
                key == "TRANSFERENCIA" ||
                key == "TRANSFERENCIAS" ||
                key == "TRANSFER"
        }

    /**
     * Produto de investimento que o banco devolve como conta (ex.: "Segurança",
     * "Blindado"). O dinheiro dele já aparece em Investir, então não conta
     * como conta conectada — mas continua visível com o seu saldo.
     */
    val isInvestmentProduct: Boolean
        get() {
            val key = TransferDetector.normalize(name)
            return subtype.equals("INVESTMENT", true) ||
                INVESTMENT_ACCOUNT_NAMES.any { key.contains(it) }
        }

    /** Conta que o usuário realmente pode usar: vale para saldo e lançamentos. */
    val isUsableAccount: Boolean get() = !isPseudoTransfer

    private val normalizedName: String get() = TransferDetector.normalize(name)

    val displayName: String
        get() = name.trim().ifBlank {
            subtype.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        }

    val limitUsage: Double?
        get() {
            val limit = creditLimit ?: return null
            if (limit <= 0.0) return null
            return (kotlin.math.abs(balance) / limit).coerceIn(0.0, 1.0)
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        itemId?.let { put("itemId", it) }
        put("name", name)
        put("type", type)
        put("subtype", subtype)
        put("balance", balance)
        number?.let { put("number", it) }
        put("currency", currency)
        creditLimit?.let { put("creditLimit", it) }
        availableCreditLimit?.let { put("availableCreditLimit", it) }
        closeDate?.let { put("closeDate", it) }
        dueDate?.let { put("dueDate", it) }
        brand?.let { put("brand", it) }
        level?.let { put("level", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): FinanceAccount? = runCatching {
            FinanceAccount(
                id = o.optString("id").takeIf { it.isNotBlank() } ?: return null,
                itemId = o.optString("itemId").takeIf { it.isNotBlank() && it != "null" },
                name = o.optString("name"),
                type = o.optString("type", "BANK"),
                subtype = o.optString("subtype", "CHECKING_ACCOUNT"),
                balance = o.optDouble("balance", 0.0),
                number = o.optString("number").takeIf { it.isNotBlank() && it != "null" },
                currency = o.optString("currency", "BRL"),
                creditLimit = o.optDouble("creditLimit", Double.NaN).takeUnless { it.isNaN() },
                availableCreditLimit = o.optDouble("availableCreditLimit", Double.NaN).takeUnless { it.isNaN() },
                closeDate = o.optString("closeDate").takeIf { it.isNotBlank() && it != "null" },
                dueDate = o.optString("dueDate").takeIf { it.isNotBlank() && it != "null" },
                brand = o.optString("brand").takeIf { it.isNotBlank() && it != "null" },
                level = o.optString("level").takeIf { it.isNotBlank() && it != "null" },
            )
        }.getOrNull()
    }
}

data class CreditBill(
    val id: String,
    val accountId: String,
    val dueDate: Long,
    val closingDate: Long? = null,
    val totalAmount: Double,
    val minimumPayment: Double? = null,
    val paidAmount: Double = 0.0,
    val financeCharges: Double = 0.0,
    val lastPaymentDate: Long? = null,
    val currency: String = "BRL",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("accountId", accountId)
        put("dueDate", dueDate)
        closingDate?.let { put("closingDate", it) }
        put("totalAmount", totalAmount)
        minimumPayment?.let { put("minimumPayment", it) }
        put("paidAmount", paidAmount)
        if (financeCharges > 0.0) put("financeCharges", financeCharges)
        lastPaymentDate?.let { put("lastPaymentDate", it) }
        put("currency", currency)
    }

    companion object {
        fun fromJson(o: JSONObject): CreditBill? = runCatching {
            CreditBill(
                id = o.optString("id").takeIf { it.isNotBlank() } ?: return null,
                accountId = o.optString("accountId").takeIf { it.isNotBlank() } ?: return null,
                dueDate = o.optLong("dueDate", System.currentTimeMillis()),
                closingDate = o.optLong("closingDate", 0L).takeIf { it > 0L },
                totalAmount = o.optDouble("totalAmount", 0.0),
                minimumPayment = o.optDouble("minimumPayment", Double.NaN).takeUnless { it.isNaN() },
                paidAmount = o.optDouble("paidAmount", 0.0),
                financeCharges = o.optDouble("financeCharges", 0.0),
                lastPaymentDate = o.optLong("lastPaymentDate", 0L).takeIf { it > 0L },
                currency = o.optString("currency", "BRL"),
            )
        }.getOrNull()
    }
}

data class Investment(
    val id: String,
    val itemId: String? = null,
    val name: String,
    val type: String,
    val subtype: String? = null,
    val balance: Double,
    val amount: Double? = null,
    val amountOriginal: Double? = null,
    val amountProfit: Double? = null,
    val amountWithdrawal: Double? = null,
    val currency: String = "BRL",
    val rate: Double? = null,
    val rateType: String? = null,
    val fixedAnnualRate: Double? = null,
    val status: String = "ACTIVE",
    val dueDate: Long? = null,
    val issuer: String? = null,
    val institution: String? = null,
    val annualRate: Double? = null,
    val lastMonthRate: Double? = null,
    val lastTwelveMonthsRate: Double? = null,
    /** Código de negociação da ação (ex.: PETR4). */
    val ticker: String? = null,
    /** Quantidade de cotas/actions. */
    val quantity: Double? = null,
    /** Preço médio de aquisição por cota. */
    val averagePrice: Double? = null,
    /** Último preço por cota vindo da cotação automática. */
    val lastQuotedPrice: Double? = null,
    /** Variação percentual da cotação no dia. */
    val quoteChangePercent: Double? = null,
    /** Quando a cotação automática foi busca (milissegundos). */
    val quoteUpdatedAt: Long? = null,
) {
    val isActive: Boolean get() = status.equals("ACTIVE", true)
    val investedAmount: Double? get() = amountOriginal ?: amount
    val availableAmount: Double get() = amountWithdrawal ?: balance

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        itemId?.let { put("itemId", it) }
        put("name", name)
        put("type", type)
        subtype?.let { put("subtype", it) }
        put("balance", balance)
        amount?.let { put("amount", it) }
        amountOriginal?.let { put("amountOriginal", it) }
        amountProfit?.let { put("amountProfit", it) }
        amountWithdrawal?.let { put("amountWithdrawal", it) }
        put("currency", currency)
        rate?.let { put("rate", it) }
        rateType?.let { put("rateType", it) }
        fixedAnnualRate?.let { put("fixedAnnualRate", it) }
        put("status", status)
        dueDate?.let { put("dueDate", it) }
        issuer?.let { put("issuer", it) }
        institution?.let { put("institution", it) }
        annualRate?.let { put("annualRate", it) }
        lastMonthRate?.let { put("lastMonthRate", it) }
        lastTwelveMonthsRate?.let { put("lastTwelveMonthsRate", it) }
        ticker?.let { put("ticker", it) }
        quantity?.let { put("quantity", it) }
        averagePrice?.let { put("averagePrice", it) }
        lastQuotedPrice?.let { put("lastQuotedPrice", it) }
        quoteChangePercent?.let { put("quoteChangePercent", it) }
        quoteUpdatedAt?.let { put("quoteUpdatedAt", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): Investment? = runCatching {
            Investment(
                id = o.optString("id").takeIf { it.isNotBlank() } ?: return null,
                itemId = o.optString("itemId").takeIf { it.isNotBlank() && it != "null" },
                name = o.optString("name", "Investimento").ifBlank { "Investimento" },
                type = o.optString("type", "OTHER"),
                subtype = o.optString("subtype").takeIf { it.isNotBlank() && it != "null" },
                balance = o.optDouble("balance", 0.0),
                amount = o.optDouble("amount", Double.NaN).takeUnless { it.isNaN() },
                amountOriginal = o.optDouble("amountOriginal", Double.NaN).takeUnless { it.isNaN() },
                amountProfit = o.optDouble("amountProfit", Double.NaN).takeUnless { it.isNaN() },
                amountWithdrawal = o.optDouble("amountWithdrawal", Double.NaN).takeUnless { it.isNaN() },
                currency = o.optString("currency", "BRL"),
                rate = o.optDouble("rate", Double.NaN).takeUnless { it.isNaN() },
                rateType = o.optString("rateType").takeIf { it.isNotBlank() && it != "null" },
                fixedAnnualRate = o.optDouble("fixedAnnualRate", Double.NaN).takeUnless { it.isNaN() },
                status = o.optString("status", "ACTIVE").ifBlank { "ACTIVE" },
                dueDate = o.optLong("dueDate", 0L).takeIf { it != 0L },
                issuer = o.optString("issuer").takeIf { it.isNotBlank() && it != "null" },
                institution = o.optString("institution").takeIf { it.isNotBlank() && it != "null" },
                annualRate = o.optDouble("annualRate", Double.NaN).takeUnless { it.isNaN() },
                lastMonthRate = o.optDouble("lastMonthRate", Double.NaN).takeUnless { it.isNaN() },
                lastTwelveMonthsRate = o.optDouble("lastTwelveMonthsRate", Double.NaN).takeUnless { it.isNaN() },
                ticker = o.optString("ticker").takeIf { it.isNotBlank() && it != "null" },
                quantity = o.optDouble("quantity", Double.NaN).takeUnless { it.isNaN() },
                averagePrice = o.optDouble("averagePrice", Double.NaN).takeUnless { it.isNaN() },
                lastQuotedPrice = o.optDouble("lastQuotedPrice", Double.NaN).takeUnless { it.isNaN() },
                quoteChangePercent = o.optDouble("quoteChangePercent", Double.NaN).takeUnless { it.isNaN() },
                quoteUpdatedAt = o.optLong("quoteUpdatedAt", 0L).takeIf { it > 0L },
            )
        }.getOrNull()
    }
}
data class PluggyItemSummary(
    val id: String,
    val connectorName: String,
    val status: String,
    val connectorId: Int? = null,
    val isSandbox: Boolean = false,
    val products: List<String> = emptyList(),
    val lastUpdatedAt: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", connectorName)
        put("status", status)
        connectorId?.let { put("connectorId", it) }
        put("isSandbox", isSandbox)
        if (products.isNotEmpty()) put("products", org.json.JSONArray(products))
        lastUpdatedAt?.let { put("lastUpdatedAt", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): PluggyItemSummary? {
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
            val name = o.optJSONObject("connector")?.optString("name").orEmpty()
                .ifBlank { o.optString("name") }
            val executionStatus = o.optString("executionStatus")
            val status = executionStatus
                .takeIf { it.isNotBlank() && it != "null" }
                ?: o.optString("status")
            val connector = o.optJSONObject("connector")
            val productsArray = o.optJSONArray("products")
            val products = if (productsArray == null) {
                emptyList()
            } else {
                (0 until productsArray.length()).mapNotNull { index ->
                    productsArray.optString(index).takeIf { it.isNotBlank() && it != "null" }
                }
            }
            return PluggyItemSummary(
                id = id,
                connectorName = name.ifBlank { "Banco conectado" },
                status = status.ifBlank { "CONNECTED" },
                connectorId = (connector?.optInt("id", 0)?.takeIf { it > 0 })
                    ?: o.optInt("connectorId", 0).takeIf { it > 0 },
                isSandbox = connector?.optBoolean("isSandbox", false)
                    ?: o.optBoolean("isSandbox", false),
                products = products,
                lastUpdatedAt = o.optString("lastUpdatedAt")
                    .takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }
}