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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.meufinanceiro.data.CategoryTotal
import br.com.meufinanceiro.data.FinanceCalculator
import br.com.meufinanceiro.ui.AppUiState
import br.com.meufinanceiro.ui.components.CardHeader
import br.com.meufinanceiro.ui.components.ChartSlice
import br.com.meufinanceiro.ui.components.DonutChart
import br.com.meufinanceiro.ui.components.EmptyState
import br.com.meufinanceiro.ui.components.GlassCard
import br.com.meufinanceiro.ui.components.IconBadge
import br.com.meufinanceiro.ui.components.MonthSelector
import br.com.meufinanceiro.ui.components.ProgressBar
import br.com.meufinanceiro.ui.components.ScreenHeader
import br.com.meufinanceiro.ui.components.SectionHeader
import br.com.meufinanceiro.ui.components.StatTile
import br.com.meufinanceiro.ui.components.Tag
import br.com.meufinanceiro.ui.theme.Amber
import br.com.meufinanceiro.ui.theme.Danger
import br.com.meufinanceiro.ui.theme.Income
import br.com.meufinanceiro.ui.theme.Mint
import br.com.meufinanceiro.ui.theme.Surface1
import br.com.meufinanceiro.ui.theme.TextFaint
import br.com.meufinanceiro.ui.theme.TextMain
import br.com.meufinanceiro.ui.theme.TextMuted
import br.com.meufinanceiro.ui.theme.categoryColor
import br.com.meufinanceiro.ui.theme.categoryStyle
import java.time.YearMonth

@Composable
fun CategoriesScreen(
    state: AppUiState,
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSetBudget: (String, Double) -> Unit,
    onRemoveCategoryRule: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val monthMovements = FinanceCalculator.filterMonth(state.realMovements, month)
    val totals = FinanceCalculator.totals(monthMovements)
    val categoryTotals = FinanceCalculator.categoryTotals(monthMovements)
    val slices = categoryTotals.map { ChartSlice(it.category, it.amount, categoryColor(it.category)) }
    var editingCategory by remember { mutableStateOf<CategoryTotal?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "Análise",
                title = "Categorias",
                trailing = { MonthSelector(month, onPreviousMonth, onNextMonth) },
            )
        }

        if (categoryTotals.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Analytics,
                    title = "Sem despesas no mês",
                    subtitle = "As categorias aparecem quando houver gastos registrados.",
                )
            }
        } else {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    CardHeader(
                        icon = Icons.Rounded.Analytics,
                        tint = Mint,
                        title = "Distribuição do mês",
                        subtitle = if (categoryTotals.size == 1) {
                            "1 categoria ativa"
                        } else {
                            categoryTotals.size.toString() + " categorias ativas"
                        },
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DonutChart(
                            slices = slices,
                            diameter = 148.dp,
                            strokeWidth = 16.dp,
                            centerLabel = "gasto total",
                            centerValue = FinanceCalculator.brl(totals.expense),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            categoryTotals.take(4).forEach { category ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(categoryColor(category.category)),
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        text = category.category,
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = ((category.amount / totals.expense) * 100).toInt().toString() + "%",
                                        color = categoryColor(category.category),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            if (categoryTotals.size > 4) {
                                Text(
                                    text = "+ " + (categoryTotals.size - 4) + " categorias",
                                    color = TextFaint,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(
                            label = "Média",
                            value = FinanceCalculator.brl(totals.expense / categoryTotals.size),
                            valueSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Maior gasto",
                            value = categoryTotals.first().category,
                            accent = categoryColor(categoryTotals.first().category),
                            valueSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item { SectionHeader("Detalhamento", "Toque para orçar", caption = "Metas por categoria") }

            items(categoryTotals, key = { it.category }) { category ->
                CategoryCard(
                    category = category,
                    total = totals.expense,
                    budget = state.budgets[category.category],
                    onClick = { editingCategory = category },
                )
            }
        }

        if (state.categoryRules.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Regras de categoria",
                    caption = "Ajustes que você fez por estabelecimento",
                )
            }
            items(state.categoryRules.entries.toList(), key = { it.key }) { entry ->
                CategoryRuleCard(
                    title = entry.key,
                    category = entry.value,
                    onRemove = { onRemoveCategoryRule?.invoke(entry.key) },
                )
            }
        }
    }

    val editing = editingCategory
    if (editing != null) {
        BudgetDialog(
            category = editing.category,
            current = state.budgets[editing.category],
            spent = editing.amount,
            onDismiss = { editingCategory = null },
            onConfirm = { limit ->
                onSetBudget(editing.category, limit)
                editingCategory = null
            },
        )
    }
}

@Composable
private fun CategoryCard(
    category: CategoryTotal,
    total: Double,
    budget: Double?,
    onClick: () -> Unit,
) {    val style = categoryStyle(category.category)
    val share = if (total > 0.0) (category.amount / total).toFloat() else 0f
    val budgetUsage = if (budget != null && budget > 0.0) {
        (category.amount / budget).toFloat()
    } else {
        null
    }
    val budgetColor = when {
        budgetUsage == null -> Income
        category.amount > (budget ?: 0.0) -> Danger
        budgetUsage >= 0.8f -> Amber
        else -> Income
    }
    GlassCard(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        contentPadding = 16.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = style.icon,
                tint = style.color,
                size = 42.dp,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = category.category,
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = category.count.toString() +
                        (if (category.count == 1) " lançamento • " else " lançamentos • ") +
                        (share * 100).toInt() + "% do total",
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = FinanceCalculator.brl(category.amount),
                    color = TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                if (budgetUsage != null) {
                    Spacer(Modifier.height(3.dp))
                    Tag(
                        text = if (category.amount > (budget ?: 0.0)) {
                            "Acima"
                        } else {
                            (budgetUsage * 100).toInt().toString() + "%"
                        },
                        color = budgetColor,
                        showDot = false,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(progress = share, color = style.color, height = 6.dp)
        if (budgetUsage != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Orçamento " + FinanceCalculator.brl(budget ?: 0.0),
                    color = TextFaint,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = (budgetUsage * 100).toInt().toString() + "% usado",
                    color = budgetColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(5.dp))
            ProgressBar(
                progress = budgetUsage,
                color = budgetColor,
                height = 5.dp,
            )
        }
    }
}

/**
 * Regra salva pelo usuário: todo lançamento com esse establishment é
 * classificado na categoria escolhida. Apagar a regra devolve os lançamentos
 * para a categoria automática.
 */
@Composable
private fun CategoryRuleCard(
    title: String,
    category: String,
    onRemove: () -> Unit,
) {
    val style = categoryStyle(category)
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = style.icon,
                tint = style.color,
                size = 36.dp,
                iconSize = 18.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title.toDisplayName(),
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Sempre em " + category,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Remover regra de " + title,
                    tint = Danger,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** "MAGALU*MAGALU" vira "Magalu*Magalu" para leitura. */
private fun String.toDisplayName(): String =
    split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

@Composable
private fun BudgetDialog(
    category: String,
    current: Double?,
    spent: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {    var value by remember { mutableStateOf(current?.let { it.toString().replace(".", ",") }.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = {
            Column {
                Text("Orçamento de " + category, color = TextMain, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Gasto atual: " + FinanceCalculator.brl(spent),
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Limite mensal (R$)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "O orçamento acompanha a categoria todos os meses.",
                    color = TextFaint,
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = value.trim().replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                onConfirm(parsed)
            }) {
                Text("Salvar", color = Mint, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                if (current != null && current > 0.0) {
                    TextButton(onClick = { onConfirm(0.0) }) {
                        Icon(Icons.Rounded.Close, null, tint = Danger, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Remover", color = Danger)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMuted) }
            }
        },
    )
}