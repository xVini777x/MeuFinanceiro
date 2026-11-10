package br.com.meufinanceiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import br.com.meufinanceiro.data.AmountPrivacy
import br.com.meufinanceiro.ui.theme.Border
import br.com.meufinanceiro.ui.theme.Mint
import br.com.meufinanceiro.ui.theme.Surface1
import br.com.meufinanceiro.ui.theme.TextMuted

/**
 * Botão de olho que mostra/oculta todos os valores do app.
 * Começa sempre oculto ao abrir o app (ver [AmountPrivacy]).
 */
@Composable
fun PrivacyToggle(modifier: Modifier = Modifier) {
    val hidden = AmountPrivacy.hidden
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Surface1)
            .border(1.dp, Border, CircleShape)
            .clickable { AmountPrivacy.toggle() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            contentDescription = if (hidden) "Mostrar valores" else "Ocultar valores",
            tint = if (hidden) TextMuted else Mint,
            modifier = Modifier.size(20.dp),
        )
    }
}
