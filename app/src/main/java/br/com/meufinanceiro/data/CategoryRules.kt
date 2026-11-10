package br.com.meufinanceiro.data

/**
 * Regras de categoria do usuário, por cima da classificação automática.
 *
 * São duas camadas, na ordem de prioridade:
 *
 * 1. **Escolha por lançamento**: o usuário reclassificou aquele lançamento
 *    específico.
 * 2. **Regra por estabelecimento**: vale para todo lançamento com o mesmo
 *    título normalizado.
 *
 * Quando não há nenhuma das duas, vale a categoria automática (do banco ou
 * das regras do [CategoryClassifier]).
 */
object CategoryRules {

    /** Chave normalizada do estabelecimento. */
    fun titleKey(title: String): String = title.trim().lowercase()

    /** Categoria efetiva do lançamento, depois de aplicar as regras. */
    fun resolve(
        movement: Movement,
        manual: Map<String, String>,
        rules: Map<String, String>,
    ): String {
        manual[movement.id]?.let { return it }
        rules[titleKey(movement.title)]?.let { return it }
        return movement.category
    }
}
