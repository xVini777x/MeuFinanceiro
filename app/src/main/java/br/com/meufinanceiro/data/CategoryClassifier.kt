package br.com.meufinanceiro.data

import java.text.Normalizer
import java.util.Locale

/**
 * Classifica lançamentos nas categorias do app.
 *
 * A decisão acontece em três níveis, do mais forte para o mais fraco:
 *
 * 1. **Estabelecimento**: regras pelo nome do lojista. São as mais confiáveis
 *    porque o mesmo lojista aparece sempre com o mesmo nome. Sem elas,
 *    "POSTO ITAPERY" caía em Moradia e "LEVEL UP INTERACT" em Transferências,
 *    só porque o banco devolveu uma categoria genérica.
 * 2. **Categoria do banco**: quando o Pluggy devolve algo útil ("groceries",
 *    "transport", "shopping").
 * 3. **Palavras-chave da descrição**: último recurso antes de "Outros".
 *
 * O texto é normalizado (sem acentos, em maiúsculas) antes das comparações, de
 * modo que "Padaria São José" e "PADARIA SAO JOSE" caem na mesma regra.
 */
object CategoryClassifier {

    private val locale = Locale("pt", "BR")

    private val combiningMarks = Regex("\\p{Mn}+")
    private val notAllowed = Regex("[^A-Z0-9* ]")
    private val repeatedSpaces = Regex("\\s+")

    fun classify(bankCategory: String?, description: String?): String {
        val normalized = normalize(description)
        merchantRule(normalized)?.let { return it }
        bankRule(bankCategory)?.let { return it }
        return keywordRule(normalized)
    }

    /** Sem acentos, em maiúsculas, sem pontuação (exceto `*`) e com espaços únicos. */
    fun normalize(value: String?): String {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return ""
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .uppercase(locale)
            .replace(notAllowed, " ")
            .replace(repeatedSpaces, " ")
            .trim()
    }

    /**
     * Regras por estabelecimento. A ordem importa: a primeira regra que casa
     * vence, então as mais específicas vêm antes das genéricas.
     */
    private val MERCHANT_RULES: List<Pair<String, String>> = listOf(
        // ------------------------------------------------------- transferências
        "PAGAMENTO DE FATURA" to Categories.TRANSFERS,
        "PAGAMENTO FATURA" to Categories.TRANSFERS,
        "PAGTO FATURA" to Categories.TRANSFERS,
        "INSTITUICAO DE PAG" to Categories.TRANSFERS,
        "RECVIVO" to Categories.TRANSFERS,
        "ASAAS" to Categories.TRANSFERS,
        "LATAM GATEWAY" to Categories.TRANSFERS,
        "EBANX" to Categories.TRANSFERS,
        "MERCURYO" to Categories.TRANSFERS,
        "STAFE BANK" to Categories.TRANSFERS,

        // --------------------------------------------------------------- taxas
        "IOF" to Categories.FEES,
        "TARIFA" to Categories.FEES,
        "ANUIDADE" to Categories.FEES,
        "JUROS DE" to Categories.FEES,
        "MULTA" to Categories.FEES,

        // ----------------------------------------------------------- alimentação
        "PRATINHO DA MADRUGADA" to Categories.FOOD,
        "JV ACAI" to Categories.FOOD,
        "PASTEL DO SEU OSMAR" to Categories.FOOD,
        "OXEMY" to Categories.FOOD,
        "MCDONALD" to Categories.FOOD,
        "MC DONALD" to Categories.FOOD,
        "BURGER KING" to Categories.FOOD,
        "BRAYAN BURGER" to Categories.FOOD,
        "EITA PIZZA" to Categories.FOOD,
        "PIZZARIA" to Categories.FOOD,
        "CHURRASCARIA" to Categories.FOOD,
        "COMPLEXO CARNES" to Categories.FOOD,
        "99FOOD" to Categories.FOOD,
        "IFD*" to Categories.FOOD,
        "GIL LANCHES" to Categories.FOOD,
        "PE NA AREIA" to Categories.FOOD,
        "EL CHANCHO" to Categories.FOOD,
        "MISTURABOA" to Categories.FOOD,
        "SPOLETO" to Categories.FOOD,
        "ILLA MARE" to Categories.FOOD,
        "ILHA MARE" to Categories.FOOD,
        "CASQUINHA" to Categories.FOOD,
        "COMERCIO DE ALIME" to Categories.FOOD,
        "COMERCIO DE CHOCO" to Categories.FOOD,
        "JUSBRASIL" to Categories.FOOD,
        "BEER" to Categories.FOOD,
        "GUERRA ACAI" to Categories.FOOD,
        "ACAI" to Categories.FOOD,
        "BOBS" to Categories.FOOD,

        // -------------------------------------------------------------- mercado
        "MERCADINHO" to Categories.MARKET,
        "PANIFICADORA" to Categories.MARKET,
        "ATACAREJO" to Categories.MARKET,
        "ATACADAO" to Categories.MARKET,
        "SUPERMERCADO" to Categories.MARKET,
        "MELIMAIS" to Categories.MARKET,
        "UNIMAIS" to Categories.MARKET,
        "MIX MATEUS" to Categories.MARKET,
        "CARNEIRO DO ORDONES" to Categories.FOOD,

        // -------------------------------------------------------------- compras
        "MERCADOLIVRE" to Categories.SHOPPING,
        "MERCADOLI" to Categories.SHOPPING,
        "AMAZON" to Categories.SHOPPING,
        "ALIEXPRESS" to Categories.SHOPPING,
        "SHEIN" to Categories.SHOPPING,
        "SHOPEE" to Categories.SHOPPING,
        "ALIPAY" to Categories.SHOPPING,
        "MAGALU" to Categories.SHOPPING,
        "TEMTEM DA CONSTRU" to Categories.SHOPPING,
        "FERRAMENTAS" to Categories.SHOPPING,
        "JIM COM" to Categories.SHOPPING,
        "MODSTORE" to Categories.SHOPPING,
        "4PRODUTOS" to Categories.SHOPPING,
        "ARISTON" to Categories.SHOPPING,
        "MACHOSOFTWARE" to Categories.SHOPPING,
        "VK IMPORTS" to Categories.SHOPPING,
        "COPECART" to Categories.SHOPPING,
        "AMERICANAS SA" to Categories.SHOPPING,
        "COSMETICOS" to Categories.SHOPPING,
        "LOJAS RENNER" to Categories.SHOPPING,
        "PARANGABA SHOPPING" to Categories.SHOPPING,
        "NORTH SHOPPING" to Categories.SHOPPING,
        "QUIOSQUE" to Categories.SHOPPING,
        "MICROSOFT*STORE" to Categories.SHOPPING,
        "HNA" to Categories.SHOPPING,
        "MAGAZINE" to Categories.SHOPPING,
        "JOIAS" to Categories.SHOPPING,
        "CALCAD" to Categories.SHOPPING,
        "ENXOV" to Categories.SHOPPING,
        "LOVEMAKE" to Categories.SHOPPING,
        "HAVAIANAS" to Categories.SHOPPING,
        "PAYPAL" to Categories.SHOPPING,
        "COMERCIO DE ARTIGO" to Categories.SHOPPING,
        "PRESENTES" to Categories.SHOPPING,
        "SOFTWARE" to Categories.SHOPPING,
        "NOTEBOOK" to Categories.SHOPPING,
        "CADEIRA" to Categories.SHOPPING,
        "FONE BLUETOOTH" to Categories.SHOPPING,
        "LASA" to Categories.SHOPPING,
        "CASA PIO" to Categories.SHOPPING,
        "KARLAYANA" to Categories.SHOPPING,
        "BEIRAMA" to Categories.SHOPPING,
        "JD COMERCIO" to Categories.MARKET,

        // --------------------------------------------------------------- lazer
        "STEAM" to Categories.LEISURE,
        "PLAYSTATION" to Categories.LEISURE,
        "GOOGLE PLAY" to Categories.LEISURE,
        "CLASH OF CLANS" to Categories.LEISURE,
        "INGRESSO" to Categories.LEISURE,
        "LEVEL UP INTERACT" to Categories.LEISURE,
        "UNHAS DA TAINAN" to Categories.LEISURE,
        "LOTERIAS" to Categories.LEISURE,
        "STUDIO ANNY" to Categories.LEISURE,
        "WELLHUB" to Categories.LEISURE,
        "GYMPASS" to Categories.LEISURE,
        "JOQUEI" to Categories.LEISURE,
        "BEACH" to Categories.LEISURE,

        // --------------------------------------------------------- assinaturas
        "GITHUB" to Categories.SUBSCRIPTIONS,
        "SPOTIFY" to Categories.SUBSCRIPTIONS,
        "NETFLIX" to Categories.SUBSCRIPTIONS,
        "REAL DEBRID" to Categories.SUBSCRIPTIONS,
        "REAL-DEBRID" to Categories.SUBSCRIPTIONS,
        "BLOCK DEV" to Categories.SUBSCRIPTIONS,
        "GOOGLE CHATG" to Categories.SUBSCRIPTIONS,
        "PRIME VIDEO" to Categories.SUBSCRIPTIONS,

        // --------------------------------------------------------------- saúde
        "SAUDE" to Categories.HEALTH,
        "PAGUE MENOS" to Categories.HEALTH,
        "FARMACIA" to Categories.HEALTH,
        "DROGARIA" to Categories.HEALTH,
        "DROGASIL" to Categories.HEALTH,
        "FARMA" to Categories.HEALTH,
        "MEDICAMEN" to Categories.HEALTH,

        // ----------------------------------------------------------- transporte
        "POSTO" to Categories.TRANSPORT,
        "OFICINA MECANICA" to Categories.TRANSPORT,
        "NICOLAS MOTOS" to Categories.TRANSPORT,
        "AUTO TINTAS" to Categories.TRANSPORT,
        "BILHETEUNICO" to Categories.TRANSPORT,
        "UBER" to Categories.TRANSPORT,
        "99DIESEL" to Categories.TRANSPORT,
        "ESTACIO" to Categories.TRANSPORT,
        "PARKING" to Categories.TRANSPORT,
        "GREEN PARK" to Categories.TRANSPORT,
        "TETRA" to Categories.TRANSPORT,

        // -------------------------------------------------------------- moradia
        "GIGAM" to Categories.HOUSING,
        "FIBRA" to Categories.HOUSING,
        "ALUGUEL" to Categories.HOUSING,
        "CONDOMINIO" to Categories.HOUSING,

        // --------------------------------------------------------- educação
        "WHAT IF CLUB" to Categories.EDUCATION,
        "UDEMY" to Categories.EDUCATION,

        // Regras genéricas por último: precisam vir depois das específicas
        // acima para não roubar a classificação de um lojista conhecido.
        "PARANGABA" to Categories.FOOD,
    )

    /** Categoria do banco, quando ela for reconhecível. */
    private val BANK_RULES: List<Pair<String, String>> = listOf(
        "grocer" to Categories.MARKET,
        "supermarket" to Categories.MARKET,
        "market" to Categories.MARKET,
        "food" to Categories.FOOD,
        "restaurant" to Categories.FOOD,
        "meal" to Categories.FOOD,
        "cafe" to Categories.FOOD,
        "stream" to Categories.SUBSCRIPTIONS,
        "music" to Categories.SUBSCRIPTIONS,
        "video" to Categories.SUBSCRIPTIONS,
        "subscription" to Categories.SUBSCRIPTIONS,
        "gym" to Categories.LEISURE,
        "fitness" to Categories.LEISURE,
        "sport" to Categories.LEISURE,
        "entertain" to Categories.LEISURE,
        "leisure" to Categories.LEISURE,
        "transport" to Categories.TRANSPORT,
        "gas" to Categories.TRANSPORT,
        "fuel" to Categories.TRANSPORT,
        "taxi" to Categories.TRANSPORT,
        "parking" to Categories.TRANSPORT,
        "housing" to Categories.HOUSING,
        "rent" to Categories.HOUSING,
        "home" to Categories.HOUSING,
        "utilit" to Categories.HOUSING,
        "health" to Categories.HEALTH,
        "pharmac" to Categories.HEALTH,
        "medical" to Categories.HEALTH,
        "travel" to Categories.TRAVEL,
        "airline" to Categories.TRAVEL,
        "hotel" to Categories.TRAVEL,
        "salary" to Categories.SALARY,
        "payroll" to Categories.SALARY,
        "income" to Categories.SALARY,
        "invest" to Categories.INVESTMENTS,
        "savings" to Categories.INVESTMENTS,
        "transfer" to Categories.TRANSFERS,
        "bank slip" to Categories.TRANSFERS,
        "pix" to Categories.TRANSFERS,
        "ted" to Categories.TRANSFERS,
        "education" to Categories.EDUCATION,
        "school" to Categories.EDUCATION,
        "course" to Categories.EDUCATION,
        "pet" to Categories.PETS,
        "shopping" to Categories.SHOPPING,
        "clothing" to Categories.SHOPPING,
        "electronics" to Categories.SHOPPING,
    )

    /** Palavras-chave genéricas da descrição, usadas só quando o banco não ajuda. */
    private val KEYWORD_RULES: List<Pair<String, String>> = listOf(
        "prime" to Categories.SUBSCRIPTIONS,
        "youtube" to Categories.SUBSCRIPTIONS,
        "assinatura" to Categories.SUBSCRIPTIONS,
        "uber" to Categories.TRANSPORT,
        "posto" to Categories.TRANSPORT,
        "combust" to Categories.TRANSPORT,
        "estacionamento" to Categories.TRANSPORT,
        "mercado" to Categories.MARKET,
        "supermerc" to Categories.MARKET,
        "padaria" to Categories.MARKET,
        "acougue" to Categories.MARKET,
        "ifood" to Categories.FOOD,
        "restaurante" to Categories.FOOD,
        "lanche" to Categories.FOOD,
        "cafe" to Categories.FOOD,
        "farmacia" to Categories.HEALTH,
        "drogaria" to Categories.HEALTH,
        "hospital" to Categories.HEALTH,
        "clinica" to Categories.HEALTH,
        "aluguel" to Categories.HOUSING,
        "condominio" to Categories.HOUSING,
        "energia" to Categories.HOUSING,
        "internet" to Categories.HOUSING,
        "salario" to Categories.SALARY,
        "pagamento recebido" to Categories.SALARY,
        "escola" to Categories.EDUCATION,
        "curso" to Categories.EDUCATION,
        "faculdade" to Categories.EDUCATION,
        "pet" to Categories.PETS,
        "veterin" to Categories.PETS,
        "passagem" to Categories.TRAVEL,
        "hotel" to Categories.TRAVEL,
        "viagem" to Categories.TRAVEL,
        "loja" to Categories.SHOPPING,
        "shopping" to Categories.SHOPPING,
    )

    /**
     * Procura a regra de estabelecimento. A comparação também é feita sem
     * espaços, porque o banco ora manda "COMPLEXO CARNES E MASSA" e ora
     * "ComplexoCarnesE" para o mesmo lojista.
     */
    private fun merchantRule(normalized: String): String? {
        if (normalized.isEmpty()) return null
        val compact = normalized.replace(" ", "")
        MERCHANT_RULES.forEach { (keyword, category) ->
            val compactKeyword = keyword.replace(" ", "")
            if (normalized.contains(keyword) || compact.contains(compactKeyword)) return category
        }
        return null
    }

    private fun bankRule(bankCategory: String?): String? {
        val value = bankCategory?.lowercase(Locale.ROOT)?.trim().orEmpty()
        if (value.isEmpty()) return null
        BANK_RULES.forEach { (keyword, category) ->
            if (value.contains(keyword)) return category
        }
        return null
    }

    private fun keywordRule(normalized: String): String {
        if (normalized.isEmpty()) return Categories.OTHER
        KEYWORD_RULES.forEach { (keyword, category) ->
            if (normalized.contains(keyword.uppercase(locale))) return category
        }
        return Categories.OTHER
    }
}
