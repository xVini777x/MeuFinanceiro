package br.com.meufinanceiro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Alguns bancos devolvem contas que não são contas do usuário: a
 * "Transferência entre contas" (pseudo-lançamento das transferências entre
 * contas do próprio usuário) e produtos de investimento como "Segurança".
 */
class AccountKindTest {

    private fun account(name: String, subtype: String = "CHECKING_ACCOUNT") = FinanceAccount(
        id = "acc-" + name,
        name = name,
        type = "BANK",
        subtype = subtype,
        balance = 100.0,
    )

    @Test
    fun `transferencia entre contas e pseudo-conta`() {
        assertTrue(account("Transferência entre contas").isPseudoTransfer)
        assertTrue(account("TRANSFERENCIA ENTRE CONTAS").isPseudoTransfer)
        assertTrue(account("Transferência").isPseudoTransfer)
        assertFalse(account("Transferência entre contas").isUsableAccount)
    }

    @Test
    fun `conta normal nao e pseudo-conta`() {
        val conta = account("Conta Corrente")
        assertFalse(conta.isPseudoTransfer)
        assertTrue(conta.isUsableAccount)
    }

    @Test
    fun `produto de investimento e reconhecido pelo nome`() {
        val seguranca = account("Segurança")
        assertTrue(seguranca.isInvestmentProduct)
        // Continua sendo uma conta usável: o dinheiro existe e aparece.
        assertTrue(seguranca.isUsableAccount)
    }

    @Test
    fun `produto de investimento e reconhecido pelo subtipo`() {
        assertTrue(account("Reserva", subtype = "INVESTMENT").isInvestmentProduct)
    }

    @Test
    fun `nome com acento e normalizado`() {
        // A regra de segurança precisa pegar "Segurança" com acento.
        assertTrue(account("Segurança").isInvestmentProduct)
        assertTrue(account("SEGURANCA").isInvestmentProduct)
    }
}
