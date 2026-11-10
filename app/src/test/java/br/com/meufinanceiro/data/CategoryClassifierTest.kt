package br.com.meufinanceiro.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryClassifierTest {

    @Test
    fun `estabelecimento tem prioridade sobre a categoria do banco`() {
        // O banco classificou como "housing", mas posto é transporte.
        assertEquals(
            Categories.TRANSPORT,
            CategoryClassifier.classify("housing", "POSTO ITAPERY"),
        )
        // O banco classificou como "transfer", mas é uma loja de jogos.
        assertEquals(
            Categories.LEISURE,
            CategoryClassifier.classify("transfer", "LEVEL UP INTERACT"),
        )
    }

    @Test
    fun `lojas de comida sao alimentacao`() {
        listOf(
            "PRATINHO DA MADRUGADA",
            "JV ACAI NORDESTE LTDA",
            "PASTEL DO SEU OSMAR",
            "Oxemydoog",
            "Mc Donalds - Arcos Dourados",
            "CHURRASCARIA JOAO FILHO",
            "EITA PIZZA",
            "COMPLEXO CARNES E MASS",
            "ComplexoCarnesE",
            "99Food *99Food",
        ).forEach { title ->
            assertEquals(title, Categories.FOOD, CategoryClassifier.classify(null, title))
        }
    }

    @Test
    fun `marketplaces e lojas caem em compras`() {
        listOf(
            "MERCADOLIVRE*MERCADOLIVRE",
            "MP*MERCADOLIVRE",
            "MP*ALIEXPRESS",
            "AMAZON BR",
            "AMAZON  MARKETPLACE",
            "DL *Alipay Alipay Brasil",
            "SHOPEE *ReidasTelaseIm",
            "SHEIN  *SHEIN.CO",
            "TEMTEM DA CONSTRU",
            "OBRAMIX FERRAMENTAS E",
        ).forEach { title ->
            assertEquals(title, Categories.SHOPPING, CategoryClassifier.classify(null, title))
        }
    }

    @Test
    fun `supermercados e padarias caem em mercado`() {
        listOf(
            "MERCADINHO DEUS TE PAG",
            "PANIFICADORA VO JANDIR",
            "GERARDOS ATACAREJO LTDA",
            "MP*ATACADAOCOSTA",
            "MP*MELIMAIS",
        ).forEach { title ->
            assertEquals(title, Categories.MARKET, CategoryClassifier.classify(null, title))
        }
    }

    @Test
    fun `assinaturas e jogos sao separados`() {
        assertEquals(
            Categories.SUBSCRIPTIONS,
            CategoryClassifier.classify(null, "GITHUB, INC."),
        )
        assertEquals(
            Categories.SUBSCRIPTIONS,
            CategoryClassifier.classify(null, "Google ChatGPT"),
        )
        assertEquals(
            Categories.LEISURE,
            CategoryClassifier.classify(null, "Steam"),
        )
        assertEquals(
            Categories.LEISURE,
            CategoryClassifier.classify(null, "EBN*PLAYSTATION"),
        )
    }

    @Test
    fun `taxas do cartao tem categoria propria`() {
        assertEquals(
            Categories.FEES,
            CategoryClassifier.classify(null, "IOF DESPESA NO EXTERIOR"),
        )
    }

    @Test
    fun `saude e transporte usam as palavras do banco quando nao ha regra`() {
        assertEquals(
            Categories.HEALTH,
            CategoryClassifier.classify("health", "PAGUE MENOS         06"),
        )
        assertEquals(
            Categories.TRANSPORT,
            CategoryClassifier.classify("transport", "DL*UberRides"),
        )
    }

    @Test
    fun `descricao desconhecida cai em outros`() {
        assertEquals(
            Categories.OTHER,
            CategoryClassifier.classify(null, "XYZ COMERCIO DE COISAS"),
        )
    }

    @Test
    fun `normalizacao ignora acentos e caixa`() {
        assertEquals(
            Categories.MARKET,
            CategoryClassifier.classify(null, "padaria são josé"),
        )
    }
}
