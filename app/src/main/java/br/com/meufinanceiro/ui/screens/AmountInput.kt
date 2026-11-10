package br.com.meufinanceiro.ui.screens

/**
 * Converte o texto digitado em valor. Aceita "1.234,56", "1234,56",
 * "1234.56" e também com "R$" ou espaços.
 */
internal fun parseAmountInput(raw: String): Double? {
    val cleaned = raw.trim().replace("R$", "").replace(" ", "")
    if (cleaned.isBlank()) return null
    val normalized = if (cleaned.contains(",")) {
        cleaned.replace(".", "").replace(",", ".")
    } else {
        cleaned
    }
    return normalized.toDoubleOrNull()
}

/** Arredonda para centavos. */
internal fun roundMoney(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
