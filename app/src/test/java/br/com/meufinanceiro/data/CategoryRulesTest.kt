package br.com.meufinanceiro.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryRulesTest {

    private fun movement(id: String, title: String, category: String) = Movement(
        id = id,
        title = title,
        amount = 10.0,
        type = MovementType.EXPENSE,
        category = category,
    )

    @Test
    fun `sem regra vale a categoria automatica`() {
        val movement = movement("1", "PADARIA", Categories.MARKET)

        assertEquals(
            Categories.MARKET,
            CategoryRules.resolve(movement, emptyMap(), emptyMap()),
        )
    }

    @Test
    fun `regra do estabelecimento vale para todos os lancamentos com o mesmo titulo`() {
        val rules = mapOf(CategoryRules.titleKey("PADARIA") to Categories.SHOPPING)

        assertEquals(
            Categories.SHOPPING,
            CategoryRules.resolve(movement("1", "PADARIA", Categories.MARKET), emptyMap(), rules),
        )
        assertEquals(
            Categories.SHOPPING,
            CategoryRules.resolve(movement("2", "  padaria  ", Categories.MARKET), emptyMap(), rules),
        )
    }

    @Test
    fun `escolha do lancamento tem prioridade sobre a regra`() {
        val rules = mapOf(CategoryRules.titleKey("PADARIA") to Categories.SHOPPING)
        val manual = mapOf("1" to Categories.HEALTH)

        assertEquals(
            Categories.HEALTH,
            CategoryRules.resolve(movement("1", "PADARIA", Categories.MARKET), manual, rules),
        )
    }
}
