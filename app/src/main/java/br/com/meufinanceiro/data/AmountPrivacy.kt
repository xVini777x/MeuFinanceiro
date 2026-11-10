package br.com.meufinanceiro.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Controla a exibição dos valores monetários no app.
 *
 * O padrão é oculto: toda vez que o app é aberto os valores começam mascarados
 * e só aparecem quando o usuário toca no botão de olho.
 *
 * O estado é um [mutableStateOf] porque [FinanceCalculator.brl] o consulta
 * durante a composição. Assim, ao alternar, todas as telas que mostram valores
 * são recompostas automaticamente.
 */
object AmountPrivacy {

    /** Texto exibido no lugar dos valores enquanto estão ocultos. */
    const val MASK = "R$ ••••"

    var hidden by mutableStateOf(true)
        private set

    fun toggle() {
        hidden = !hidden
    }

    /** Volta a ocultar (usado quando o app sai da tela). */
    fun hide() {
        hidden = true
    }
}
