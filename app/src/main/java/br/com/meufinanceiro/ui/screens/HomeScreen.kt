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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.meufinanceiro.R
import br.com.meufinanceiro.data.CategoryTotal
import br.com.meufinanceiro.data.FinanceAccount
import br.com.meufinanceiro.data.FinanceCalculator
import br.com.meufinanceiro.data.InvoiceSummary
import br.com.meufinanceiro.data.Movement
import br.com.meufinanceiro.ui.AppUiState
import br.com.meufinanceiro.ui.components.BarDatum
import br.com.meufinanceiro.ui.components.BarsChart
import br.com.meufinanceiro.ui.components.CardHeader
import br.com.meufinanceiro.ui.components.EditCategoryDialog
import br.com.meufinanceiro.ui.components.EmptyState
import br.com.meufinanceiro.ui.components.GlassCard
import br.com.meufinanceiro.ui.components.IconBadge
import br.com.meufinanceiro.ui.components.LineChart
import br.com.meufinanceiro.ui.components.LinePoint
import br.com.meufinanceiro.ui.components.MonthSelector
import br.com.meufinanceiro.ui.components.MovementRow
import br.com.meufinanceiro.ui.components.PrivacyToggle
import br.com.meufinanceiro.ui.components.ProgressBar
import br.com.meufinanceiro.ui.components.SectionHeader
import br.com.meufinanceiro.ui.components.SegmentedControl
import br.com.meufinanceiro.ui.components.Tag
import br.com.meufinanceiro.ui.theme.Bg
import br.com.meufinanceiro.ui.theme.Border
import br.com.meufinanceiro.ui.theme.Danger
import br.com.meufinanceiro.ui.theme.GradientBalance
import br.com.meufinanceiro.ui.theme.Income
import br.com.meufinanceiro.ui.theme.Mint
import br.com.meufinanceiro.ui.theme.Surface1
import br.com.meufinanceiro.ui.theme.Surface2
import br.com.meufinanceiro.ui.theme.TextFaint
import br.com.meufinanceiro.ui.theme.TextMain
import br.com.meufinanceiro.ui.theme.TextMuted
import br.com.meufinanceiro.ui.theme.categoryColor
import br.com.meufinanceiro.ui.theme.categoryStyle
import java.time.YearMonth

/** Uma categoria com o gasto do mês e o orçamento definido. */
private data class BudgetLine(
    val category: String,
    val spent: Double,
    val limit: Double,
)

private enum class FlowMode(val label: String) {
    BALANCE("Saldo"),
    INCOME_EXPENSE("Entradas x saídas"),
}

@Composable
fun HomeScreen(
    state: AppUiState,
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenStatement: () -> Unit,
    onOpenInvoices: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenInvestments: () -> Unit,
    onConnectBank: () -> Unit,
    onAddMovement: () -> Unit,
    onUpdateMovementCategory: ((movementId: String, newCategory: String, applyToSameTitle: Boolean) -> Unit)? = null,
    onDeleteMovement: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val monthMovements = FinanceCalculator.filterMonth(state.realMovements, month)
    val totals = FinanceCalculator.totals(monthMovements)
    val categoryTotals = FinanceCalculator.categoryTotals(monthMovements)
    val series = FinanceCalculator.monthlySeries(state.realMovements, month, 6)
    val accounts = state.realAccounts
    // Pseudo-contas de transferência e produtos de investimento (que já
    // aparecem em Investir) não contam como conta conectada.
    val accountCount = state.connectedAccounts.size
    val creditAccounts = accounts.filter { it.isCredit }

    val currentInvoice = creditAccounts
        .mapNotNull { account ->
            FinanceCalculator.currentInvoiceFor(
                account = account,
                bills = state.realBills,
                movements = state.realMovements,
            )
        }
        .maxByOrNull { it.total }

    val investedTotal = FinanceCalculator.investmentTotal(state.realInvestments)

    // Saldo acumulado dos últimos meses, ponto a ponto do gráfico.
    var running = 0.0
    val balancePoints = series.map { point ->
        running += point.income - point.expense
        LinePoint(FinanceCalculator.formatMonthShort(point.month), running)
    }
    val bars = series.map { BarDatum(it.month, it.income, it.expense) }

    val budgetLines = state.budgets.mapNotNull { (category, limit) ->
        if (limit <= 0.0) return@mapNotNull null
        val spent = categoryTotals.firstOrNull { it.category == category }?.amount ?: 0.0
        BudgetLine(category, spent, limit)
    }.sortedByDescending { it.spent / it.limit }

    var flowMode by remember { mutableStateOf(FlowMode.BALANCE) }
    var editingMovement by remember { mutableStateOf<Movement?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = FinanceCalculator.greeting() + ",",
                        color = TextMuted,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = stringResource(R.string.user_name),
                        color = TextMain,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                PrivacyToggle()
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                MonthSelector(month, onPreviousMonth, onNextMonth)
            }
        }

        item {
            BalanceCard(
                balance = totals.balance,
                income = totals.income,
                expense = totals.expense,
                invoiceTotal = currentInvoice?.total ?: 0.0,
                accountCount = accountCount,
                points = balancePoints,
            )
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                CardHeader(
                    icon = Icons.AutoMirrored.Rounded.ShowChart,
                    tint = Mint,
                    title = "Fluxo do período",
                    subtitle = "Saldo acumulado e comparação mensal",
                )
                Spacer(Modifier.height(14.dp))
                SegmentedControl(
                    options = FlowMode.values().map { it.label },
                    selectedIndex = flowMode.ordinal,
                    onSelect = { flowMode = FlowMode.values()[it] },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                if (flowMode == FlowMode.BALANCE) {
                    LineChart(
                        points = balancePoints,
                        modifier = Modifier.fillMaxWidth(),
                        height = 170.dp,
                        color = Mint,
                    )
                } else {
                    BarsChart(
                        data = bars,
                        modifier = Modifier.fillMaxWidth(),
                        height = 170.dp,
                    )
                }
            }
        }

        if (categoryTotals.isNotEmpty()) {
            item {
                SectionHeader("Gastos por categoria", "Ver tudo", onOpenCategories)
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        categoryTotals.take(4).forEach { category ->
                            CategoryLine(category, totals.expense)
                        }
                    }
                }
            }
        }

        if (state.realInvestments.isNotEmpty()) {
            item { SectionHeader("Investimentos", "Ver tudo", onOpenInvestments) }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(
                            icon = Icons.AutoMirrored.Rounded.ShowChart,
                            tint = Mint,
                            size = 40.dp,
                            iconSize = 20.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Patrimônio investido",
                                color = TextMain,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = state.realInvestments.count { it.isActive }
                                    .toString() + " ativos na carteira",
                                color = TextMuted,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = FinanceCalculator.brl(investedTotal),
                            color = TextMain,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        if (creditAccounts.isNotEmpty()) {
            item { SectionHeader("Faturas", "Ver faturas", onOpenInvoices) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(creditAccounts, key = { it.id }) { account ->
                        val invoice = FinanceCalculator.currentInvoiceFor(
                            account = account,
                            bills = state.realBills,
                            movements = state.realMovements,
                        )
                        InvoiceMiniCard(account, invoice, onOpenInvoices)
                    }
                }
            }
        }

        if (budgetLines.isNotEmpty()) {
            item { SectionHeader("Orçamentos", "Gerenciar", onOpenCategories) }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        budgetLines.forEach { line ->
                            BudgetProgress(line)
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                "Últimas movimentações",
                "Ver extrato",
                onOpenStatement,
                caption = "No mês selecionado",
            )
        }

        if (monthMovements.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    title = "Nada por aqui ainda",
                    subtitle = "Conecte um banco ou adicione uma movimentação para começar.",
                    actionLabel = "Adicionar movimentação",
                    onAction = onAddMovement,
                )
            }
        } else {
            items(monthMovements.take(5), key = { it.id }) { movement ->
                MovementRow(
                    movement = movement,
                    showAccount = true,
                    onClick = { editingMovement = movement },
                )
            }
        }

        if (state.realAccounts.isEmpty() && state.realMovements.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    CardHeader(
                        icon = Icons.Rounded.AccountBalance,
                        tint = Mint,
                        title = "Conecte seu banco",
                        subtitle = "Importe contas, cartões, faturas e transações automaticamente.",
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onConnectBank,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Bg),
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Conectar banco", fontWeight = FontWeight.Bold)
                    }
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
                onDeleteMovement?.invoke(movementToEdit.id)
                editingMovement = null
            },
        )
    }
}

@Composable
private fun BalanceCard(
    balance: Double,
    income: Double,
    expense: Double,
    invoiceTotal: Double,
    accountCount: Int,
    points: List<LinePoint>,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "SALDO DISPONÍVEL",
                    color = TextFaint,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = "Resultado das movimentações",
                    color = TextMuted,
                    fontSize = 11.sp,
                )
            }
            if (accountCount > 0) {
                Tag(
                    text = if (accountCount == 1) "1 conta conectada" else accountCount.toString() + " contas conectadas",
                    color = Mint,
                    showDot = false,
                )
            } else {
                Tag(text = "Open Finance", color = TextFaint, showDot = false)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = FinanceCalculator.brl(balance),
            color = TextMain,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        LineChart(
            points = points,
            modifier = Modifier.fillMaxWidth(),
            height = 90.dp,
            color = Mint,
            showLabels = false,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("Entradas", income, Modifier.weight(1f), Income)
            MiniStat("Saídas", expense, Modifier.weight(1f), Danger)
            MiniStat("Fatura", invoiceTotal, Modifier.weight(1f), Mint)
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: Double,
    modifier: Modifier = Modifier,
    color: Color = TextMain,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = TextFaint,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = FinanceCalculator.brl(value),
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CategoryLine(category: CategoryTotal, total: Double) {
    val style = categoryStyle(category.category)
    val share = if (total > 0.0) (category.amount / total).toFloat() else 0f
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = style.icon,
                tint = style.color,
                size = 30.dp,
                iconSize = 15.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = category.category,
                color = TextMain,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = FinanceCalculator.brl(category.amount),
                color = TextMain,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(
            progress = share,
            color = style.color,
            modifier = Modifier.padding(start = 40.dp),
            height = 5.dp,
        )
    }
}

@Composable
private fun InvoiceMiniCard(
    account: FinanceAccount,
    invoice: InvoiceSummary,
    onClick: () -> Unit,
) {
    val pending = invoice.pending
    GlassCard(
        Modifier
            .width(240.dp)
            .clickable { onClick() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = Icons.Rounded.AccountBalance,
                tint = Mint,
                size = 36.dp,
                iconSize = 18.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = account.displayName,
                    color = TextMain,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (pending > 0.0) "Fatura aberta" else "Fatura",
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = FinanceCalculator.brl(if (pending > 0.0) pending else invoice.total),
            color = TextMain,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        invoice.dueDate?.let { due ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Vence em " + FinanceCalculator.formatDate(due),
                color = TextFaint,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun BudgetProgress(line: BudgetLine) {
    val style = categoryStyle(line.category)
    val usage = if (line.limit > 0.0) (line.spent / line.limit).toFloat() else 0f
    val color = when {
        line.spent > line.limit -> Danger
        usage >= 0.8f -> TextMuted
        else -> style.color
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = line.category,
                color = TextMain,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = FinanceCalculator.brl(line.spent) + " / " + FinanceCalculator.brl(line.limit),
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(progress = usage.coerceIn(0f, 1f), color = color, height = 6.dp)
    }
}
