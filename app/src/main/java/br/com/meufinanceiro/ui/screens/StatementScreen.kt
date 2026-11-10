package br.com.meufinanceiro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.meufinanceiro.data.AmountPrivacy
import br.com.meufinanceiro.data.FinanceCalculator
import br.com.meufinanceiro.data.Movement
import br.com.meufinanceiro.ui.AppUiState
import br.com.meufinanceiro.ui.components.AppSearchField
import br.com.meufinanceiro.ui.components.EditCategoryDialog
import br.com.meufinanceiro.ui.components.EmptyState
import br.com.meufinanceiro.ui.components.FilterPill
import br.com.meufinanceiro.ui.components.MonthSelector
import br.com.meufinanceiro.ui.components.MovementRow
import br.com.meufinanceiro.ui.components.ScreenHeader
import br.com.meufinanceiro.ui.components.SegmentedControl
import br.com.meufinanceiro.ui.components.StatTile
import br.com.meufinanceiro.ui.components.Tag
import br.com.meufinanceiro.ui.theme.Danger
import br.com.meufinanceiro.ui.theme.Income
import br.com.meufinanceiro.ui.theme.Surface1
import br.com.meufinanceiro.ui.theme.Surface2
import br.com.meufinanceiro.ui.theme.TextFaint
import br.com.meufinanceiro.ui.theme.TextMain
import br.com.meufinanceiro.ui.theme.TextMuted
import br.com.meufinanceiro.ui.theme.categoryColor
import java.time.YearMonth

private enum class TypeFilter(val label: String) {
    ALL("Todas"),
    INCOME("Receitas"),
    EXPENSE("Despesas"),
}

@Composable
fun StatementScreen(
    state: AppUiState,
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDeleteMovement: (String) -> Unit,
    onUpdateMovementCategory: ((movementId: String, newCategory: String, applyToSameTitle: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf(TypeFilter.ALL) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var editingMovement by remember { mutableStateOf<Movement?>(null) }

    val monthMovements = FinanceCalculator.filterMonth(state.realMovements, month)
    val categories = monthMovements.map { it.category }.distinct().sorted()

    val filtered = monthMovements.filter { movement ->
        val matchesType = when (typeFilter) {
            TypeFilter.ALL -> true
            // Transferências entre contas do usuário não são receita nem despesa.
            TypeFilter.INCOME -> movement.isIncome && !movement.internalTransfer
            TypeFilter.EXPENSE -> !movement.isIncome && !movement.internalTransfer
        }
        val matchesCategory = categoryFilter == null || movement.category == categoryFilter
        val matchesQuery = query.isBlank() ||
            movement.title.contains(query, ignoreCase = true) ||
            movement.category.contains(query, ignoreCase = true)
        matchesType && matchesCategory && matchesQuery
    }

    val totals = FinanceCalculator.totals(filtered)
    val grouped = filtered.groupBy { FinanceCalculator.dayStart(it.date) }
        .toSortedMap(compareByDescending { it })

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "Extrato",
                title = "Movimentações",
                trailing = { MonthSelector(month, onPreviousMonth, onNextMonth) },
            )
        }

        item {
            AppSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Buscar por descrição ou categoria",
            )
        }

        item {
            SegmentedControl(
                options = TypeFilter.values().map { it.label },
                selectedIndex = typeFilter.ordinal,
                onSelect = { typeFilter = TypeFilter.values()[it] },
            )
        }

        if (categories.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterPill(
                            text = "Todas as categorias",
                            selected = categoryFilter == null,
                            onClick = { categoryFilter = null },
                        )
                    }
                    items(categories, key = { it }) { category ->
                        FilterPill(
                            text = category,
                            selected = categoryFilter == category,
                            onClick = { categoryFilter = if (categoryFilter == category) null else category },
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Toque em um lançamento para corrigir a categoria ou apagá-lo.",
                color = TextFaint,
                fontSize = 11.sp,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Resultado",
                    value = if (AmountPrivacy.hidden) {
                        FinanceCalculator.brl(totals.balance)
                    } else {
                        (if (totals.balance >= 0) "+ " else "− ") +
                            FinanceCalculator.brl(kotlin.math.abs(totals.balance))
                    },
                    accent = if (totals.balance >= 0) Income else Danger,
                    valueSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Receitas",
                    value = FinanceCalculator.brl(totals.income),
                    accent = Income,
                    valueSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Despesas",
                    value = FinanceCalculator.brl(totals.expense),
                    accent = Danger,
                    valueSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    title = "Nenhuma movimentação",
                    subtitle = "Ajuste os filtros ou escolha outro mês.",
                )
            }
        } else {
            grouped.forEach { (dayStart, movements) ->
                item(key = "day-" + dayStart) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = FinanceCalculator.formatFullDay(dayStart),
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Tag(
                            text = movements.size.toString() +
                                (if (movements.size == 1) " lançamento" else " lançamentos"),
                            color = TextFaint,
                            showDot = false,
                        )
                    }
                }
                items(movements, key = { it.id }) { movement ->
                    MovementRow(
                        movement = movement,
                        showAccount = true,
                        onClick = { editingMovement = movement },
                    )
                }
            }
        }
    }

    val movementToEdit = editingMovement
    if (movementToEdit != null) {
        EditCategoryDialog(
            movement = movementToEdit,
            onDismiss = { editingMovement = null },
            onSaveCategory = { newCategory, applyToSameTitle ->
                onUpdateMovementCategory?.invoke(movementToEdit.id, newCategory, applyToSameTitle)
                editingMovement = null
            },
            onDelete = {
                onDeleteMovement(movementToEdit.id)
                editingMovement = null
            },
        )
    }
}
