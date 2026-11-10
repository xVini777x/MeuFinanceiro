package br.com.meufinanceiro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressão sobre os dados reais do cartão: garante que a classificação
 * automática continua boa e que parcelamentos do mesmo lojista não se
 * misturam.
 */
class RealDataCategoryTest {

    private fun load(name: String): List<List<String>> {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
            ?: error("missing resource $name")
        return stream.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }
            .map { it.split("\t") }
    }

    private fun opt(v: String?): String? = v?.takeIf { it.isNotBlank() && it != "null" }

    private fun movements(): List<Movement> = load("real_movements.tsv").drop(1).map { c ->
        Movement(
            id = c[0],
            title = c[1],
            amount = c[2].toDouble(),
            type = MovementType.valueOf(c[3]),
            category = c[4],
            date = c[5].toLong(),
            accountId = opt(c[6]),
            accountName = opt(c[7]),
            isCreditCard = c[8].toBoolean(),
            installmentNumber = opt(c[9])?.toInt(),
            totalInstallments = opt(c[10])?.toInt(),
            billId = opt(c[11]),
            billMonth = opt(c[12]),
            status = c[13],
            source = MovementSource.valueOf(c[14]),
        )
    }

    @Test
    fun `poucos lancamentos ficam sem categoria`() {
        // Antes das regras por estabelecimento, 350 dos 940 lançamentos
        // caíam em "Outros". Mesmo sem a categoria do banco, a descrição
        // sozinha já resolve a maior parte.
        val movements = movements()
        val unresolved = movements.count {
            CategoryClassifier.classify(null, it.title) == Categories.OTHER
        }

        assertTrue(
            "lançamentos sem categoria: $unresolved de ${movements.size}",
            unresolved <= movements.size * 0.15,
        )
    }

    @Test
    fun `estabelecimentos do dia a dia sao classificados`() {
        val expectations = mapOf(
            "PRATINHO DA MADRUGADA" to Categories.FOOD,
            "JV ACAI NORDESTE LTDA" to Categories.FOOD,
            "PASTEL DO SEU OSMAR" to Categories.FOOD,
            "Mc Donalds - Arcos Dourados" to Categories.FOOD,
            "CHURRASCARIA JOAO FILHO" to Categories.FOOD,
            "MERCADOLIVRE*MERCADOLIVRE" to Categories.SHOPPING,
            "AMAZON BR" to Categories.SHOPPING,
            "DL *Alipay Alipay Brasil" to Categories.SHOPPING,
            "SHOPEE *ReidasTelaseIm" to Categories.SHOPPING,
            "POSTO ITAPERY" to Categories.TRANSPORT,
            "OFICINA MECANICA" to Categories.TRANSPORT,
            "IOF DESPESA NO EXTERIOR" to Categories.FEES,
            "GITHUB, INC." to Categories.SUBSCRIPTIONS,
            "Steam" to Categories.LEISURE,
            "PAGUE MENOS         06" to Categories.HEALTH,
            "MERCADINHO DEUS TE PAG" to Categories.MARKET,
            "PANIFICADORA VO JANDIR" to Categories.MARKET,
        )

        expectations.forEach { (title, expected) ->
            assertEquals(title, expected, CategoryClassifier.classify(null, title))
        }
    }

    @Test
    fun `parcelamentos do mesmo lojista ficam separados`() {
        val plans = FinanceCalculator.installmentPlans(movements())

        // "MERCADOLIVRE*MERCADOLIVRE" aparece em compras de valores diferentes:
        // 162, 196, 92,51, 239,14 e 94,98. Se fossem misturados, o parcelamento
        // em aberto desapareceria.
        val mercadoLivre = plans.filter { it.title.contains("MERCADOLIVRE") }
        assertTrue("parcelamentos do Mercado Livre: ${mercadoLivre.size}", mercadoLivre.size >= 4)

        // Nenhum parcelamento pode ter mais parcelas pagas do que o total.
        assertTrue(plans.all { it.current <= it.total })
    }

    @Test
    fun `magalu de 10x continua em aberto`() {
        val plans = FinanceCalculator.installmentPlans(movements())
        val magalu = plans.first { it.title.startsWith("MAGALU") }

        assertEquals(1, magalu.current)
        assertEquals(10, magalu.total)
        assertEquals(2519.10, magalu.remaining, 0.01)
    }
}
