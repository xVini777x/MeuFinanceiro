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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.meufinanceiro.data.FinanceCalculator
import br.com.meufinanceiro.data.Investment
import br.com.meufinanceiro.data.InvestmentTypeTotal
import br.com.meufinanceiro.ui.AppUiState
import br.com.meufinanceiro.ui.components.CardHeader
import br.com.meufinanceiro.ui.components.ChartSlice
import br.com.meufinanceiro.ui.components.DonutChart
import br.com.meufinanceiro.ui.components.EmptyState
import br.com.meufinanceiro.ui.components.GlassCard
import br.com.meufinanceiro.ui.components.GradientCard
import br.com.meufinanceiro.ui.components.IconBadge
import br.com.meufinanceiro.ui.components.InfoRow
import br.com.meufinanceiro.ui.components.ScreenHeader
import br.com.meufinanceiro.ui.components.SectionHeader
import br.com.meufinanceiro.ui.components.StatTile
import br.com.meufinanceiro.ui.components.Tag
import br.com.meufinanceiro.ui.theme.Amber
import br.com.meufinanceiro.ui.theme.Blue
import br.com.meufinanceiro.ui.theme.Danger
import br.com.meufinanceiro.ui.theme.GradientBalance
import br.com.meufinanceiro.ui.theme.Income
import br.com.meufinanceiro.ui.theme.Mint
import br.com.meufinanceiro.ui.theme.Pink
import br.com.meufinanceiro.ui.theme.Purple
import br.com.meufinanceiro.ui.theme.Teal
import br.com.meufinanceiro.ui.theme.TextFaint
import br.com.meufinanceiro.ui.theme.TextMain
import br.com.meufinanceiro.ui.theme.TextMuted
import java.util.Locale

@Composable
fun InvestmentsScreen(
    state: AppUiState,
    onSaveInvestment: (Investment) -> Unit,
    onDeleteInvestment: (String) -> Unit,
    onRefreshQuotes: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val investments = state.realInvestments
    val active = investments.filter { it.isActive }
    val total = FinanceCalculator.investmentTotal(investments)
    val typeTotals = FinanceCalculator.investmentTypeTotals(investments)
    val profitValues = active.mapNotNull { FinanceCalculator.investmentProfit(it) }
    val totalProfit = profitValues.sumOf { it }
    val hasProfit = profitValues.isNotEmpty()
    val itemNames = state.items.associate { it.id to it.connectorName }
    val duplicateNames = state.items.groupingBy { it.connectorName }.eachCount()
    val bankCount = active.mapNotNull { it.itemId }.distinct().size
    var editorVisible by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Investment?>(null) }
    val hasQuoted = investments.any { !it.ticker.isNullOrBlank() && (it.quantity ?: 0.0) > 0.0 }

    val openEditor: (Investment?) -> Unit = { investment ->
        editing = investment
        editorVisible = true
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "Patrimônio",
                title = "Investimentos",
                subtitle = when {
                    investments.isEmpty() -> "Adicione seus aportes e caixinhas manualmente"
                    active.isEmpty() -> "Todos os investimentos foram resgatados"
                    bankCount <= 0 -> plural(
                        active.size,
                        "ativo adicionado manualmente",
                        "ativos adicionados manualmente",
                    )
                    bankCount == 1 -> plural(active.size, "investimento em 1 banco", "investimentos em 1 banco")
                    else -> plural(
                        active.size,
                        "investimento em " + bankCount + " bancos",
                        "investimentos em " + bankCount + " bancos",
                    )
                },
            )
        }

        if (hasQuoted && onRefreshQuotes != null) {
            item {
                QuoteRefreshRow(
                    syncing = state.syncing,
                    lastUpdate = investments.mapNotNull { it.quoteUpdatedAt }.maxOrNull(),
                    onRefresh = { onRefreshQuotes() },
                )
            }
        }

        if (investments.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.AutoMirrored.Rounded.ShowChart,
                    title = "Sua carteira está vazia",
                    subtitle = "Adicione CDB, Tesouro, fundos ou ações com o saldo atual. " +
                        if (state.items.isEmpty()) {
                            "Você também pode conectar um banco para importar automaticamente."
                        } else {
                            "Se preferir, sincronize o banco para importar automaticamente."
                        },
                    actionLabel = "Adicionar investimento",
                    onAction = { openEditor(null) },
                )
            }
        } else {
            val inactive = investments.filterNot { it.isActive }

            if (active.isNotEmpty()) {
                item {
                    InvestmentHero(
                        total = total,
                        totalProfit = totalProfit,
                        hasProfit = hasProfit,
                        count = active.size,
                        bankCount = bankCount,
                    )
                }

                item {
                    AllocationCard(typeTotals, total)
                }
            }

            item {
                SectionHeader(
                    title = if (active.isEmpty()) "Carteira" else "Seus investimentos",
                    actionLabel = "Adicionar",
                    onAction = { openEditor(null) },
                )
            }

            if (active.isEmpty()) {
                item {
                    Text(
                        "Nenhum ativo em andamento. Adicione um novo ou reative um resgatado.",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            items(active.sortedByDescending { it.balance }, key = { it.id }) { investment ->
                InvestmentCard(
                    investment = investment,
                    bankLabel = bankLabel(investment, itemNames, duplicateNames),
                    onClick = if (investment.itemId == null) {
                        { openEditor(investment) }
                    } else {
                        null
                    },
                )
            }

            if (inactive.isNotEmpty()) {
                item { SectionHeader("Resgatados") }
                items(inactive, key = { "inactive-" + it.id }) { investment ->
                    InvestmentCard(
                        investment = investment,
                        bankLabel = bankLabel(investment, itemNames, duplicateNames),
                        muted = true,
                        onClick = if (investment.itemId == null) {
                            { openEditor(investment) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    if (editorVisible) {
        AddInvestmentSheet(
            existing = editing,
            onDismiss = { editorVisible = false },
            onSave = { investment ->
                onSaveInvestment(investment)
                editorVisible = false
            },
            onDelete = if (editing != null && editing?.itemId == null) {
                { investment: Investment ->
                    onDeleteInvestment(investment.id)
                    editorVisible = false
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun QuoteRefreshRow(
    syncing: Boolean,
    lastUpdate: Long?,
    onRefresh: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = Icons.Rounded.Refresh,
                tint = if (syncing) TextFaint else Blue,
                size = 36.dp,
                iconSize = 18.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Cotações da bolsa",
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        syncing -> "Buscando preços..."
                        lastUpdate != null -> "Atualizado às " +
                            FinanceCalculator.formatTime(lastUpdate)
                        else -> "Toque para buscar o preço de hoje"
                    },
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (syncing) "..." else "Atualizar",
                color = if (syncing) TextFaint else Mint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .clickable(enabled = !syncing) { onRefresh() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun InvestmentHero(
    total: Double,
    totalProfit: Double,
    hasProfit: Boolean,
    count: Int,
    bankCount: Int,
) {
    GradientCard(brush = GradientBalance, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = Icons.AutoMirrored.Rounded.ShowChart,
                tint = Color.White,
                size = 44.dp,
                iconSize = 22.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "PATRIMÔNIO INVESTIDO",
                    color = Color(0xCCFFFFFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                )
                Text(
                    text = plural(count, "ativo na carteira", "ativos na carteira"),
                    color = Color(0xB3FFFFFF),
                    fontSize = 12.sp,
                )
            }
            Tag(
                text = when {
                    bankCount > 1 -> bankCount.toString() + " bancos"
                    bankCount == 1 -> "1 banco"
                    else -> "Manual"
                },
                color = Color.White,
                showDot = false,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = FinanceCalculator.brl(total),
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = if (totalProfit >= 0.0) "Rendimento" else "Resultado",
                value = if (hasProfit) FinanceCalculator.brlSigned(totalProfit) else "—",
                accent = Color.White,
                background = Color(0x1FFFFFFF),
                borderColor = Color(0x24FFFFFF),
                labelColor = Color(0xCCFFFFFF),
                valueSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Ativos",
                value = count.toString(),
                accent = Color.White,
                background = Color(0x1FFFFFFF),
                borderColor = Color(0x24FFFFFF),
                labelColor = Color(0xCCFFFFFF),
                valueSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
        }
        if (!hasProfit) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Informe o valor investido ao cadastrar para ver o rendimento.",
                color = Color(0x99FFFFFF),
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun AllocationCard(typeTotals: List<InvestmentTypeTotal>, total: Double) {
    if (typeTotals.isEmpty()) return
    val slices = typeTotals.map { ChartSlice(it.label, it.amount, investmentTypeColor(it.label)) }
    GlassCard(Modifier.fillMaxWidth()) {
        CardHeader(
            icon = Icons.AutoMirrored.Rounded.ShowChart,
            tint = Mint,
            title = "Alocação por tipo",
            subtitle = if (typeTotals.size == 1) {
                "1 classe de ativo"
            } else {
                typeTotals.size.toString() + " classes de ativo"
            },
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DonutChart(
                slices = slices,
                diameter = 142.dp,
                strokeWidth = 16.dp,
                centerLabel = "total",
                centerValue = FinanceCalculator.brl(total),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                typeTotals.forEach { item ->
                    val color = investmentTypeColor(item.label)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.label,
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = FinanceCalculator.brl(item.amount),
                                color = TextMain,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        val percent = if (total > 0.0) (item.amount / total * 100.0).toInt() else 0
                        Tag(
                            text = percent.toString() + "%",
                            color = color,
                            showDot = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InvestmentCard(
    investment: Investment,
    bankLabel: String?,
    muted: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val typeLabel = FinanceCalculator.investmentTypeLabel(investment.type, investment.subtype)
    val subtypeLabel = FinanceCalculator.investmentSubtypeLabel(investment.subtype)
    val color = investmentTypeColor(typeLabel)
    val profit = FinanceCalculator.investmentProfit(investment)
    val profitPercent = FinanceCalculator.investmentProfitPercent(investment)
    val rate = FinanceCalculator.investmentRateLabel(investment)
    val invested = investment.investedAmount
    val available = investment.availableAmount
    val alpha = if (muted) 0.55f else 1f
    val isManual = investment.itemId == null

    GlassCard(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = investmentIcon(investment.type),
                tint = color.copy(alpha = alpha),
                size = 44.dp,
                iconSize = 22.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = investment.name,
                    color = TextMain.copy(alpha = alpha),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        bankLabel ?: if (isManual) "Manual" else typeLabel,
                        investment.ticker,
                    ).joinToString(" • "),
                    color = TextMuted.copy(alpha = alpha),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Tag(
                text = subtypeLabel ?: typeLabel,
                color = color.copy(alpha = alpha),
                showDot = false,
            )
            if (onClick != null) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "Editar investimento",
                    tint = TextFaint,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "SALDO ATUAL",
                    color = TextFaint,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = FinanceCalculator.brl(investment.balance),
                    color = TextMain.copy(alpha = alpha),
                    fontWeight = FontWeight.Bold,
                    fontSize = 25.sp,
                )
            }
            if (profit != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (profit >= 0.0) "RENDIMENTO" else "RESULTADO",
                        color = TextFaint,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (profit >= 0.0) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                            null,
                            tint = if (profit >= 0.0) Income else Danger,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = FinanceCalculator.brlSigned(profit),
                            color = if (profit >= 0.0) Income else Danger,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                    if (profitPercent != null) {
                        Text(
                            text = (if (profitPercent >= 0.0) "+" else "") +
                                String.format(Locale("pt", "BR"), "%.2f", profitPercent) + "%",
                            color = if (profitPercent >= 0.0) Income else Danger,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }

        val quantityLabel = FinanceCalculator.investmentQuantityLabel(investment)
        val averagePrice = investment.averagePrice
        val currentPrice = FinanceCalculator.investmentCurrentPrice(investment)

        if (rate != null || invested != null || kotlin.math.abs(available - investment.balance) > 0.01) {
            Spacer(Modifier.height(12.dp))
            Column {
                if (rate != null) InfoRow("Taxa", rate)
                if (invested != null && kotlin.math.abs(invested - investment.balance) > 0.01) {
                    InfoRow("Valor investido", FinanceCalculator.brl(invested))
                }
                if (kotlin.math.abs(available - investment.balance) > 0.01) {
                    InfoRow("Disponível para resgate", FinanceCalculator.brl(available), Income)
                }
            }
        }

        if (quantityLabel != null || averagePrice != null || currentPrice != null) {
            Spacer(Modifier.height(12.dp))
            Column {
                if (quantityLabel != null) {
                    InfoRow(
                        "Cotas",
                        quantityLabel + (if (quantityLabel == "1") " cota" else " cotas"),
                    )
                }
                if (averagePrice != null) {
                    InfoRow("Preço médio", FinanceCalculator.brl(averagePrice))
                }
                if (currentPrice != null) {
                    InfoRow("Preço atual", FinanceCalculator.brl(currentPrice))
                }
            }
        }

        val quotedPrice = investment.lastQuotedPrice
        if (quotedPrice != null) {
            Spacer(Modifier.height(12.dp))
            Column {
                val variation = investment.quoteChangePercent
                InfoRow(
                    label = "Cotação",
                    value = buildString {
                        append(FinanceCalculator.brl(quotedPrice))
                        if (variation != null) {
                            append("  ")
                            append(
                                (if (variation >= 0) "+" else "−") +
                                    String.format(Locale("pt", "BR"), "%.2f", kotlin.math.abs(variation)) +
                                    "%",
                            )
                        }
                    },
                    valueColor = when {
                        variation == null -> TextMain
                        variation >= 0 -> Income
                        else -> Danger
                    },
                )
                investment.quoteUpdatedAt?.let { updated ->
                    Text(
                        text = "Atualizado em " + FinanceCalculator.formatTime(updated),
                        color = TextFaint,
                        fontSize = 10.sp,
                    )
                }
            }
        }

        if (investment.status.equals("PENDING", true)) {
            Spacer(Modifier.height(10.dp))
            Tag("Em processamento", Amber)
        }
    }
}

private fun plural(count: Int, singular: String, plural: String): String =
    count.toString() + " " + if (count == 1) singular else plural

private fun bankLabel(
    investment: Investment,
    itemNames: Map<String, String>,
    duplicateNames: Map<String, Int>,
): String? {
    val itemName = investment.itemId?.let { itemNames[it] }
    val base = investment.issuer
        ?: investment.institution
        ?: itemName
    if (base == null) return null
    if (itemName != null && (duplicateNames[itemName] ?: 0) > 1) {
        return base + " • " + (investment.itemId ?: "").takeLast(6)
    }
    return base
}

private fun investmentIcon(type: String): ImageVector = when (type.uppercase()) {
    "EQUITY" -> Icons.AutoMirrored.Rounded.TrendingUp
    "SECURITY" -> Icons.Rounded.AccountBalance
    else -> Icons.AutoMirrored.Rounded.ShowChart
}

private fun investmentTypeColor(label: String): Color = when (label) {
    "Renda fixa" -> Income
    "Fundos" -> Purple
    "Ações" -> Blue
    "ETFs" -> Teal
    "Previdência" -> Amber
    "Fundos imobiliários" -> Pink
    "Tesouro" -> Purple
    else -> Amber
}
