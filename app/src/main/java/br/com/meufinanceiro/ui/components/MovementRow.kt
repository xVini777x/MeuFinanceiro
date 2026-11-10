package br.com.meufinanceiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.meufinanceiro.data.FinanceCalculator
import br.com.meufinanceiro.data.Movement
import br.com.meufinanceiro.ui.theme.Border
import br.com.meufinanceiro.ui.theme.Purple
import br.com.meufinanceiro.ui.theme.Surface1
import br.com.meufinanceiro.ui.theme.TextMain
import br.com.meufinanceiro.ui.theme.TextMuted
import br.com.meufinanceiro.ui.theme.categoryStyle

@Composable
fun MovementRow(
    movement: Movement,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showAccount: Boolean = false,
) {
    val style = categoryStyle(movement.category)
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(lerp(Surface1, Color.White, 0.045f), Surface1),
                ),
            )
            .border(1.dp, Border, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon = style.icon,
            tint = style.color,
            size = 42.dp,
            iconSize = 21.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = movement.title.replace(Regex("\\s+"), " ").trim(),
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val installment = movement.installmentLabel
                if (installment != null) {
                    Spacer(Modifier.width(6.dp))
                    Pill(installment, Purple)
                }
            }
            Spacer(Modifier.height(3.dp))
            val details = buildString {
                append(if (movement.internalTransfer) "Entre contas" else movement.category)
                append(" • ")
                append(FinanceCalculator.formatDate(movement.date))
                if (showAccount && !movement.accountName.isNullOrBlank()) {
                    append(" • ")
                    append(movement.accountName)
                }
            }
            Text(
                text = details,
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        if (movement.internalTransfer) {
            // Sem sinal: o valor não entrou nem saiu do patrimônio.
            Text(
                text = FinanceCalculator.brl(movement.amount),
                color = TextMuted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        } else {
            AmountText(movement.amount, movement.isIncome, fontSize = 14.sp)
        }
    }
}
