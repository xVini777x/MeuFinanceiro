package br.com.meufinanceiro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.meufinanceiro.data.Categories
import br.com.meufinanceiro.data.FinanceCalculator
import br.com.meufinanceiro.data.Movement
import br.com.meufinanceiro.ui.theme.Bg
import br.com.meufinanceiro.ui.theme.Border
import br.com.meufinanceiro.ui.theme.Danger
import br.com.meufinanceiro.ui.theme.Mint
import br.com.meufinanceiro.ui.theme.Surface1
import br.com.meufinanceiro.ui.theme.Surface2
import br.com.meufinanceiro.ui.theme.TextFaint
import br.com.meufinanceiro.ui.theme.TextMain
import br.com.meufinanceiro.ui.theme.TextMuted
import br.com.meufinanceiro.ui.theme.categoryStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditCategoryDialog(
    movement: Movement,
    onDismiss: () -> Unit,
    onSaveCategory: (newCategory: String, applyToSameTitle: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var selectedCategory by remember { mutableStateOf(movement.category) }
    var applyToAll by remember { mutableStateOf(true) }
    var showConfirmDelete by remember { mutableStateOf(false) }

    val currentStyle = categoryStyle(movement.category)
    // Receita e despesa têm listas de categorias diferentes: oferecer
    // "Alimentação" para um salário só gera confusão.
    val availableCategories = remember(movement.isIncome) {
        if (movement.isIncome) Categories.income else Categories.expense
    }

    if (showConfirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            containerColor = Surface1,
            title = { Text("Remover lançamento?", color = TextMain, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "O lançamento \"${movement.title}\" será excluído do seu histórico.",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDelete = false
                    onDelete()
                }) {
                    Text("Excluir", color = Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancelar", color = TextMuted)
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = Icons.Rounded.Category,
                    tint = Mint,
                    size = 36.dp,
                    iconSize = 18.dp,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Alterar categoria",
                        color = TextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    Text(
                        text = "Reclassifique esta movimentação",
                        color = TextMuted,
                        fontSize = 11.sp,
                    )
                }            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Card de detalhes da movimentação
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface2)
                        .border(1.dp, Border, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = movement.title.replace(Regex("\\s+"), " ").trim(),
                                color = TextMain,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = FinanceCalculator.brl(movement.amount),
                                color = if (movement.isIncome) Mint else TextMain,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = FinanceCalculator.formatDate(movement.date) +
                                    (if (!movement.accountName.isNullOrBlank()) " • " + movement.accountName else ""),
                                color = TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Tag(
                                text = movement.category,
                                color = currentStyle.color,
                                showDot = true,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Escolha a nova categoria:",
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    availableCategories.forEach { category ->
                        val isSelected = category == selectedCategory
                        val style = categoryStyle(category)
                        val chipShape = RoundedCornerShape(12.dp)

                        Row(
                            modifier = Modifier
                                .clip(chipShape)
                                .background(
                                    if (isSelected) style.color.copy(alpha = 0.22f) else Surface2,
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) style.color else Border,
                                    shape = chipShape,
                                )
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.Check else style.icon,
                                contentDescription = null,
                                tint = if (isSelected) style.color else TextMuted,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = category,
                                color = if (isSelected) TextMain else TextMuted,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Opção para aplicar em outras movimentações similares
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface2)
                        .clickable { applyToAll = !applyToAll }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Aplicar em outros lançamentos",
                            color = TextMain,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Cria uma regra para \"" + movement.title.take(24) +
                                "\". Dá para remover em Categorias.",
                            color = TextFaint,
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = applyToAll,
                        onCheckedChange = { applyToAll = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Bg,
                            checkedTrackColor = Mint,
                            checkedBorderColor = Mint,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = Surface1,
                            uncheckedBorderColor = Border,
                        ),
                    )
                }

                if (onDelete != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showConfirmDelete = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            null,
                            tint = Danger,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Excluir lançamento",
                            color = Danger,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveCategory(selectedCategory, applyToAll)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Bg),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Salvar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Cancelar", color = TextMuted, fontSize = 13.sp)
            }
        },
    )
}
