package br.com.meufinanceiro.pluggy

import br.com.meufinanceiro.data.MovementType
import org.junit.Assert.assertEquals
import org.junit.Test

class PluggyModelsTest {

    private val card = PluggyAccount(
        id = "card",
        itemId = "item",
        name = "Santander",
        type = "CREDIT",
        subtype = "CREDIT_CARD",
        balance = 0.0,
        currencyCode = "BRL",
        number = "1234",
        creditLimit = 10_000.0,
        availableCreditLimit = 5_000.0,
        closeDate = null,
        dueDate = null,
        brand = "VISA",
        level = null,
    )

    private fun tx(
        description: String,
        amount: Double,
        type: String = "DEBIT",
        operationType: String? = "OUTROS",
    ) = PluggyTransaction(
        id = description + amount,
        description = description,
        amount = amount,
        date = "2026-11-01T00:00:00.000Z",
        type = type,
        category = null,
        status = "POSTED",
        operationType = operationType,
        installmentNumber = null,
        totalInstallments = null,
        billId = null,
    )

    @Test
    fun `positive purchase is expense`() {
        assertEquals(MovementType.EXPENSE, tx("MERCADO", 150.0).toMovement(card)?.type)
    }

    @Test
    fun `negative purchase is still expense`() {
        assertEquals(MovementType.EXPENSE, tx("MERCADO", -150.0).toMovement(card)?.type)
    }

    @Test
    fun `purchase with blank operation type is expense`() {
        assertEquals(
            MovementType.EXPENSE,
            tx("MERCADO", -150.0, operationType = null).toMovement(card)?.type,
        )
    }

    @Test
    fun `bill payment is income`() {
        assertEquals(
            MovementType.INCOME,
            tx("PAGAMENTO FATURA", -1500.0, type = "CREDIT", operationType = "PAGAMENTO_FATURA")
                .toMovement(card)?.type,
        )
    }

    @Test
    fun `payment identified by description is income`() {
        assertEquals(
            MovementType.INCOME,
            tx("PAGAMENTO DE FATURA CARTAO", -1500.0, type = "CREDIT", operationType = null)
                .toMovement(card)?.type,
        )
    }

    @Test
    fun `refund is income`() {
        assertEquals(
            MovementType.INCOME,
            tx("ESTORNO DE COMPRA", -50.0, type = "CREDIT", operationType = "ESTORNO")
                .toMovement(card)?.type,
        )
    }

    @Test
    fun `merchant named pagamento is still expense`() {
        assertEquals(
            MovementType.EXPENSE,
            tx("PAGAMENTO MERCADO PAGO", -50.0, type = "DEBIT", operationType = null)
                .toMovement(card)?.type,
        )
    }

    @Test
    fun `santander debit purchase with pagamento operation is expense`() {
        assertEquals(
            MovementType.EXPENSE,
            tx("COMPLEXO CARNES E MASS", 120.79, type = "DEBIT", operationType = "PAGAMENTO")
                .toMovement(card)?.type,
        )
        assertEquals(
            MovementType.EXPENSE,
            tx("PIX RAIA DROGASIL S/A", 57.37, type = "DEBIT", operationType = "PAGAMENTO")
                .toMovement(card)?.type,
        )
    }

    @Test
    fun `santander card payment credit is income`() {
        assertEquals(
            MovementType.INCOME,
            tx("PAGAMENTO DE FATURA", -5175.08, type = "CREDIT", operationType = "PAGAMENTO")
                .toMovement(card)?.type,
        )
    }

    @Test
    fun `checking account credit is income and debit is expense`() {
        val bank = card.copy(type = "BANK", subtype = "CHECKING_ACCOUNT")
        assertEquals(
            MovementType.INCOME,
            tx("SALARIO", 5000.0, type = "CREDIT", operationType = "FOLHA_PAGAMENTO")
                .toMovement(bank)?.type,
        )
        assertEquals(
            MovementType.EXPENSE,
            tx("CONTA LUZ", -200.0, type = "DEBIT", operationType = "BOLETO")
                .toMovement(bank)?.type,
        )
    }
}