package br.com.meufinanceiro.data

import java.text.Normalizer
import java.util.Locale

/**
 * Detecta dinheiro que apenas mudou de bolso: PIX/TED enviado para uma conta do
 * próprio usuário. Esse lançamento não é receita nem despesa — se entrasse nos
 * totais, um PIX de R$ 2.600 para a própria conta inflaria as despesas do mês.
 *
 * Duas regras, aplicadas em conjunto:
 *
 * 1. **Nome**: o título é uma transferência (PIX/TED/DOC/transferência) e cita um
 *    dos nomes configurados pelo usuário ("Vinicius Nogueira Martins"). Funciona
 *    mesmo com apenas um banco conectado, que é o caso mais comum.
 * 2. **Par**: existe um débito e um crédito de mesmo valor, em contas diferentes,
 *    com até 3 dias de diferença. Cobre bancos que mandam só "PIX ENVIADO", sem
 *    o nome do favorecido.
 *
 * Transferências de cartão de crédito (PIX no cartão/saque) continuam sendo
 * despesa: ali houve custo de verdade.
 */
object TransferDetector {

    private const val DAY_MS = 86_400_000L
    private const val PAIR_WINDOW_MS = 3L * DAY_MS

    private val locale = Locale("pt", "BR")

    /** Palavras que indicam movimentação entre contas, não compra. */
    private val transferKeywords = listOf(
        "PIX", "TED", "DOC", "TRANSFER", "TRANSF", "ENTRE CONTAS",
    )

    /** Remove acentos, pontuação e espaços repetidos para comparar títulos. */
    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .uppercase(locale)
            .replace(Regex("[^A-Z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Aceita "Vinicius Nogueira Martins, Maria" ou separado por ; ou |. */
    fun parseNames(raw: String?): List<String> = raw.orEmpty()
        .split(',', ';', '|')
        .map { normalize(it) }
        .filter { it.length >= 3 }
        .distinct()

    fun isSelfTransfer(movement: Movement, names: List<String>): Boolean {
        if (movement.isCreditCard) return false
        val title = normalize(movement.title)
        if (title.isEmpty()) return false
        if (!hasTransferKeyword(title)) return false
        return names.any { name -> title.contains(name) }
    }

    /**
     * Pares débito/crédito do mesmo valor em contas diferentes. Devolve os ids
     * dos dois lados para que o valor não seja contado duas vezes.
     */
    fun pairedIds(movements: List<Movement>): Set<String> {
        val candidates = movements.filter {
            !it.isCreditCard && it.accountId != null && hasTransferKeyword(normalize(it.title))
        }
        if (candidates.size < 2) return emptySet()
        val result = mutableSetOf<String>()
        candidates
            .groupBy { Math.round(it.amount * 100) }
            .values
            .forEach { group ->
                val outgoing = group.filter { !it.isIncome }
                val incoming = group.filter { it.isIncome }
                if (outgoing.isEmpty() || incoming.isEmpty()) return@forEach
                outgoing.forEach { out ->
                    val match = incoming.firstOrNull { income ->
                        income.id !in result &&
                            income.accountId != out.accountId &&
                            kotlin.math.abs(income.date - out.date) <= PAIR_WINDOW_MS
                    }
                    if (match != null) {
                        result += out.id
                        result += match.id
                    }
                }
            }
        return result
    }

    /**
     * Devolve a lista com `internalTransfer` marcado. Puro e sem persistência:
     * pode rodar a cada leitura do armazenamento local.
     */
    fun mark(movements: List<Movement>, ownerNames: String?): List<Movement> {
        val names = parseNames(ownerNames)
        val pairs = pairedIds(movements)
        if (names.isEmpty() && pairs.isEmpty()) return movements
        return movements.map { movement ->
            val internal = movement.id in pairs || isSelfTransfer(movement, names)
            if (internal == movement.internalTransfer) movement else movement.copy(internalTransfer = internal)
        }
    }

    private fun hasTransferKeyword(normalizedTitle: String): Boolean =
        transferKeywords.any { normalizedTitle.contains(it) }
}
