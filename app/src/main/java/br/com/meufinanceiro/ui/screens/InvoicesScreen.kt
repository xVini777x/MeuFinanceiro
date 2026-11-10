package br.com.meufinanceiro.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.meufinanceiro.data.FinanceAccount
import br.com.meufinanceiro.data.FinanceCalculator
import br.com.meufinanceiro.data.InstallmentPlan
import br.com.meufinanceiro.data.InvoiceEntry
import br.com.meufinanceiro.data.InvoiceStatus
import br.com.meufinanceiro.data.InvoiceSummary
import br.com.meufinanceiro.ui.AppUiState
import br.com.meufinanceiro.ui.components.CardHeader
import br.com.meufinanceiro.ui.components.EmptyState
import br.com.meufinanceiro.ui.components.GlassCard
import br.com.meufinanceiro.ui.components.GradientCard
import br.com.meufinanceiro.ui.components.IconBadge
import br.com.meufinanceiro.ui.components.MovementRow
import br.com.meufinanceiro.ui.components.Pill
import br.com.meufinanceiro.ui.components.ProgressBar
import br.com.meufinanceiro.ui.components.ScreenHeader
import br.com.meufinanceiro.data.Movement
import br.com.meufinanceiro.ui.components.AppSearchField
import br.com.meufinanceiro.ui.components.EditCategoryDialog
import br.com.meufinanceiro.ui.components.SectionHeader
import br.com.meufinanceiro.ui.components.SegmentedControl
import br.com.meufinanceiro.ui.components.StatTile
import br.com.meufinanceiro.ui.components.Tag
import br.com.meufinanceiro.ui.theme.Blue
import br.com.meufinanceiro.ui.theme.Danger
import br.com.meufinanceiro.ui.theme.GradientCard as GradientCardBrush
import br.com.meufinanceiro.ui.theme.Income
import br.com.meufinanceiro.ui.theme.Mint
import br.com.meufinanceiro.ui.theme.Purple
import br.com.meufinanceiro.ui.theme.Surface2
import br.com.meufinanceiro.ui.theme.TextFaint
import br.com.meufinanceiro.ui.theme.TextMain
import br.com.meufinanceiro.ui.theme.TextMuted
import br.com.meufinanceiro.ui.theme.categoryStyle
import java.time.LocalDate
import java.time.YearMonth

private val OverdueGradient = Brush.linearGradient(
    listOf(Color(0xFF2A1216), Color(0xFF1A0C10), Color(0xFF0C0C0E)),
)

@Composable
fun InvoicesScreen(
    state: AppUiState,
    onSync: () -> Unit,
    onUpdateMovementCategory: ((movementId: String, newCategory: String, applyToSameTitle: Boolean) -> Unit)? = null,
    onDeleteMovement: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Apenas cartões reais: o cartão de teste (sandbox) não entra nos totais
    // nem na lista de faturas. Ele continua visível em Contas.
    val creditAccounts = state.realAccounts.filter { it.isCredit }
    val today = LocalDate.now()
    val entriesByAccount = creditAccounts.associate { account ->
        account.id to FinanceCalculator.invoiceEntries(
            account,
            state.realBills,
            state.realMovements,
            today,
        )
    }
    val allEntries = entriesByAccount.values.flatten()
    val openEntries = allEntries.filter {
        it.status == InvoiceStatus.OPEN || it.status == InvoiceStatus.OVERDUE
    }
    val openTotal = openEntries.sumOf { it.pending }
    val overdueTotal = allEntries.filter { it.status == InvoiceStatus.OVERDUE }.sumOf { it.pending }
    val futureTotal = allEntries.filter { it.status == InvoiceStatus.FUTURE }.sumOf { it.summary.total }
    val paidTotal = allEntries.filter { it.status == InvoiceStatus.PAID }.sumOf { it.summary.total }
    val allPlans = FinanceCalculator.installmentPlans(state.realMovements, state.realBills)
    // Só entram em "Compras parceladas" os parcelamentos que ainda estão em
    // aberto. Os quitados (pagos de uma vez ou cancelados) ficam de fora.
    val plans = allPlans.filter { it.remaining > 0.0 && !it.settled }
    // Quantos apareceriam como ativos se o app não conferisse as faturas.
    val settledPlans = allPlans.count { it.settled && it.remaining > 0.0 }

    var editingMovement by remember { mutableStateOf<Movement?>(null) }

    // Com mais de um cartão, mostra um por vez: empilhar as faturas de todos
    // os bancos deixa a tela pesada e difícil de ler.
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    val pendingByAccount = remember(entriesByAccount) {
        creditAccounts.associate { account ->
            account.id to entriesByAccount[account.id].orEmpty()
                .filter { it.status == InvoiceStatus.OPEN || it.status == InvoiceStatus.OVERDUE }
                .sumOf { it.pending }
        }
    }
    val activeAccountId = selectedAccountId?.takeIf { id -> creditAccounts.any { it.id == id } }
        ?: pendingByAccount.maxByOrNull { it.value }?.key
        ?: creditAccounts.firstOrNull()?.id
    val activeAccount = creditAccounts.firstOrNull { it.id == activeAccountId }

    // Planos agrupados pelo cartão em que a compra foi feita, para o usuário
    // comparar o que ainda falta pagar em cada conta conectada.
    val plansByAccount: List<Pair<FinanceAccount?, List<InstallmentPlan>>> =
        remember(state.realMovements, state.accounts) {
            val accountsMap = state.accounts.associateBy { it.id }
            val grouped = plans.groupBy { it.accountId }
            val result = mutableListOf<Pair<FinanceAccount?, List<InstallmentPlan>>>()
            creditAccounts.forEach { account ->
                val accPlans = grouped[account.id].orEmpty()
                if (accPlans.isNotEmpty()) result.add(account to accPlans)
            }
            grouped.forEach { (accId, accPlans) ->
                if (accId != null && creditAccounts.none { it.id == accId } && accPlans.isNotEmpty()) {
                    result.add(accountsMap[accId] to accPlans)
                }
            }
            val unassigned = grouped[null].orEmpty()
            if (unassigned.isNotEmpty()) {
                result.add(null to unassigned)
            }
            result.sortedByDescending { (_, accPlans) -> accPlans.sumOf { it.remaining } }
        }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "Cartões",
                title = "Faturas",
                subtitle = "Atual em aberto, futuras e fechadas",
            )
        }

        if (creditAccounts.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.CreditCard,
                    title = "Nenhum cartão conectado",
                    subtitle = "Conecte um banco com cartão de crédito para acompanhar as faturas.",
                )
            }
        } else {
            item {
                InvoiceSummaryCard(
                    openTotal = openTotal,
                    futureTotal = futureTotal,
                    paidTotal = paidTotal,
                    overdueTotal = overdueTotal,
                )
            }

            if (creditAccounts.size > 1) {
                item {
                    CreditCardSelector(
                        accounts = creditAccounts,
                        pendingByAccount = pendingByAccount,
                        selectedId = activeAccountId,
                        onSelect = { selectedAccountId = it },
                    )
                }
            }

            activeAccount?.let { account ->
                item(key = "account-" + account.id) {
                    AccountInvoicesSection(
                        account = account,
                        entries = entriesByAccount[account.id].orEmpty(),
                        onSync = onSync,
                        onMovementClick = { editingMovement = it },
                    )
                }
            }

            if (plans.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Compras parceladas",
                        caption = plans.size.toString() +
                            (if (plans.size == 1) " parcelamento ativo" else " parcelamentos ativos") +
                            " • separados por cartão" +
                            (if (settledPlans > 0) {
                                " • $settledPlans já quitado" +
                                    (if (settledPlans == 1) "" else "s")
                            } else {
                                ""
                            }),
                    )
                }
                plansByAccount.forEach { (account, accountPlans) ->
                    item(key = "installments-account-" + (account?.id ?: "unassigned")) {
                        AccountInstallmentsSection(
                            account = account,
                            plans = accountPlans,
                        )
                    }
                }
            }
        }
    }

    val toEdit = editingMovement
    if (toEdit != null) {
        EditCategoryDialog(
            movement = toEdit,
            onDismiss = { editingMovement = null },
            onSaveCategory = { newCategory, applyToSameTitle ->
                onUpdateMovementCategory?.invoke(toEdit.id, newCategory, applyToSameTitle)
            },
            onDelete = onDeleteMovement?.let { deleteFn ->
                {
                    deleteFn(toEdit.id)
                    editingMovement = null
                }
            },
        )
    }
}

@Composable
private fun CreditCardSelector(
    accounts: List<FinanceAccount>,
    pendingByAccount: Map<String, Double>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(accounts, key = { it.id }) { account ->
            val selected = account.id == selectedId
            val pending = pendingByAccount[account.id] ?: 0.0
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) Mint.copy(alpha = 0.14f) else Surface2)
                    .border(
                        width = 1.dp,
                        color = if (selected) Mint.copy(alpha = 0.6f) else Color(0x14FFFFFF),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { onSelect(account.id) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = account.displayName,
                    color = if (selected) TextMain else TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (pending > 0.0) {
                        FinanceCalculator.brl(pending)
                    } else {
                        "Sem fatura em aberto"
                    },
                    color = if (pending > 0.0) Mint else TextFaint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun InvoiceSummaryCard(
    openTotal: Double,
    futureTotal: Double,
    paidTotal: Double,
    overdueTotal: Double,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        CardHeader(
            icon = Icons.Rounded.Payments,
            tint = Mint,
            title = "Resumo das faturas",
            subtitle = "Aberta, futuras e pagas",
            trailing = {
                if (overdueTotal > 0.0) {
                    Tag("Em atraso", Danger)
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "Em aberto",
                value = FinanceCalculator.brl(openTotal),
                accent = if (overdueTotal > 0.0) Danger else Mint,
                valueSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Futuras",
                value = FinanceCalculator.brl(futureTotal),
                accent = Blue,
                valueSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Pagas",
                value = FinanceCalculator.brl(paidTotal),
                accent = TextMuted,
                valueSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
        }
        if (overdueTotal > 0.0) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = Danger, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Você tem " + FinanceCalculator.brl(overdueTotal) + " em faturas vencidas.",
                    color = Danger,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun AccountInvoicesSection(
    account: FinanceAccount,
    entries: List<InvoiceEntry>,
    onSync: () -> Unit,
    onMovementClick: ((Movement) -> Unit)? = null,
) {
    val ordered = entries.sortedBy { it.summary.month }
    val current = ordered.firstOrNull { it.isCurrent }
        ?: ordered.lastOrNull { it.status != InvoiceStatus.PAID }
        ?: ordered.lastOrNull()
    var selectedMonth by remember(account.id) { mutableStateOf(current?.summary?.month) }
    LaunchedEffect(ordered) {
        if (selectedMonth != null && ordered.none { it.summary.month == selectedMonth }) {
            selectedMonth = current?.summary?.month
        }
    }
    val selected = ordered.firstOrNull { it.summary.month == selectedMonth } ?: current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AccountHeader(account, current)
        if (ordered.isEmpty()) {
            NoBillsCard(onSync)
        } else {
            InvoiceMonthSelector(
                entries = ordered,
                selected = selected?.summary?.month,
                onSelect = { selectedMonth = it },
            )
            if (selected != null) {
                InvoiceHero(account, selected)
                InvoiceMovementsCard(
                    summary = selected.summary,
                    title = "Lançamentos de " + FinanceCalculator.formatMonth(selected.summary.month),
                    defaultExpanded = true,
                    onMovementClick = onMovementClick,
                )
            }
        }
    }
}

@Composable
private fun InvoiceMonthSelector(
    entries: List<InvoiceEntry>,
    selected: YearMonth?,
    onSelect: (YearMonth) -> Unit,
) {
    val index = entries.indexOfFirst { it.summary.month == selected }
    val hasPrevious = index > 0
    val hasNext = index in 0 until entries.lastIndex
    val currentEntry = entries.getOrNull(index)
    val listState = rememberLazyListState()
    // Mantém o mês selecionado visível na faixa de meses (inclusive ao abrir
    // a fatura atual, que fica depois dos meses passados).
    LaunchedEffect(index, entries.size) {
        if (index >= 0) listState.scrollToItem(index)
    }
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (hasPrevious) onSelect(entries[index - 1].summary.month) },
                enabled = hasPrevious,
            ) {
                Icon(
                    Icons.Rounded.ChevronLeft,
                    contentDescription = "Mês anterior",
                    tint = if (hasPrevious) TextMain else TextFaint,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = selected?.let { FinanceCalculator.formatMonth(it) } ?: "Faturas",
                    color = TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
                Text(
                    text = when {
                        currentEntry == null -> "Selecione um mês"
                        currentEntry.isCurrent -> "Fatura atual"
                        currentEntry.status == InvoiceStatus.PAID -> "Fatura paga"
                        currentEntry.status == InvoiceStatus.OVERDUE -> "Fatura vencida"
                        currentEntry.isProjected -> "Projeção"
                        else -> "Fatura futura"
                    },
                    color = currentEntry?.let { statusColor(it.status) } ?: TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            IconButton(
                onClick = { if (hasNext) onSelect(entries[index + 1].summary.month) },
                enabled = hasNext,
            ) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = "Próximo mês",
                    tint = if (hasNext) TextMain else TextFaint,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries, key = { it.bill.id }) { entry ->
                InvoiceMonthChip(
                    entry = entry,
                    selected = entry.summary.month == selected,
                    onClick = { onSelect(entry.summary.month) },
                )
            }
        }
    }
}

@Composable
private fun InvoiceMonthChip(
    entry: InvoiceEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = statusColor(entry.status)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) color.copy(alpha = 0.16f) else Surface2)
            .border(
                width = 1.dp,
                color = if (selected) color.copy(alpha = 0.75f) else Color(0x14FFFFFF),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = FinanceCalculator.formatMonthShort(entry.summary.month),
            color = if (selected) TextMain else TextMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
        )
        Text(
            text = entry.summary.month.year.toString().takeLast(2),
            color = TextFaint,
            fontSize = 9.sp,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(100))
                .background(color),
        )
    }
}

@Composable
private fun AccountHeader(account: FinanceAccount, current: InvoiceEntry?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(
            icon = Icons.Rounded.CreditCard,
            tint = Mint,
            size = 44.dp,
            iconSize = 22.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = account.displayName,
                color = TextMain,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (!account.number.isNullOrBlank()) {
                        append("•••• ")
                        append(account.number)
                    } else {
                        append("Cartão de crédito")
                    }
                    account.creditLimit?.let {
                        append(" • limite ")
                        append(FinanceCalculator.brl(it))
                    }
                },
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (current != null && current.status != InvoiceStatus.PAID) {
            Tag(current.status.label, statusColor(current.status))
        }
    }
}

@Composable
private fun InvoiceHero(account: FinanceAccount, entry: InvoiceEntry) {
    val summary = entry.summary
    val overdue = entry.status == InvoiceStatus.OVERDUE
    val badgeColor = statusColor(entry.status)
    val mainValue = if (summary.pending > 0.0) summary.pending else summary.total
    val label = when {
        entry.isCurrent && entry.isProjected -> "FATURA ATUAL • PROJEÇÃO"
        entry.isCurrent -> "FATURA ATUAL"
        entry.status == InvoiceStatus.PAID -> "FATURA PAGA"
        entry.status == InvoiceStatus.OVERDUE -> "FATURA VENCIDA"
        entry.isProjected -> "PROJEÇÃO"
        else -> "FATURA FUTURA"
    }
    GradientCard(
        brush = if (overdue) OverdueGradient else GradientCardBrush,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = Color(0xCCFFFFFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = FinanceCalculator.formatMonth(summary.month).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .background(Color(0x33000000))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    statusIcon(entry.status),
                    null,
                    tint = badgeColor,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = entry.status.label.uppercase(),
                    color = badgeColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = FinanceCalculator.brl(mainValue),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
        )
        Text(
            text = when {
                overdue -> "Pagamento em atraso"
                summary.pending <= 0.0 && summary.paid > 0.0 -> "Fatura paga"
                summary.pending > 0.0 && summary.paid > 0.0 -> "Restante da fatura"
                else -> "Valor em aberto"
            },
            color = Color(0xCCFFFFFF),
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            summary.dueDate?.let { due ->
                HeroDate("VENCIMENTO", FinanceCalculator.formatDate(due))
            }
            summary.closingDate?.let { closing ->
                HeroDate("FECHAMENTO", FinanceCalculator.formatDate(closing))
            }
            if (summary.paid > 0.0) {
                HeroDate("PAGO", FinanceCalculator.brl(summary.paid))
            }
        }
        if (summary.pending > 0.0 && summary.paid > 0.0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Total da fatura: " + FinanceCalculator.brl(summary.total),
                color = Color(0xCCFFFFFF),
                fontSize = 11.sp,
            )
        }
        account.limitUsage?.let { usage ->
            Spacer(Modifier.height(14.dp))
            ProgressBar(progress = usage.toFloat(), color = Color.White, height = 6.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Limite usado " + (usage * 100).toInt() + "% • Disponível " +
                    FinanceCalculator.brl(
                        ((account.creditLimit ?: 0.0) - kotlin.math.abs(account.balance)).coerceAtLeast(0.0),
                    ),
                color = Color(0xB3FFFFFF),
                fontSize = 10.sp,
            )
        }
        summary.minimumPayment?.takeIf { it > 0.0 }?.let { minimum ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Pagamento mínimo: " + FinanceCalculator.brl(minimum),
                color = Color(0xCCFFFFFF),
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Os lançamentos desta fatura estão logo abaixo.",
            color = Color(0x99FFFFFF),
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun HeroDate(label: String, value: String) {
    Column {
        Text(label, color = Color(0x99FFFFFF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun InvoiceMovementsCard(
    summary: InvoiceSummary,
    title: String,
    onMovementClick: ((Movement) -> Unit)? = null,
    defaultExpanded: Boolean = false,
) {
    var expanded by remember(summary.bill?.id) { mutableStateOf(defaultExpanded) }
    GlassCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        ) {
            CardHeader(
                icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                tint = Mint,
                title = title,
                subtitle = summary.movements.size.toString() +
                    (if (summary.movements.size == 1) " item" else " itens"),
                trailing = {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(12.dp))
                InvoiceMovementsContent(
                    summary = summary,
                    onMovementClick = onMovementClick,
                )
            }
        }
    }
}

private enum class InvoiceViewMode(val label: String) {
    BY_DAY("Por data"),
    BY_CATEGORY("Por categoria"),
    BY_MERCHANT("Por loja"),
}

@Composable
private fun InvoiceMovementsContent(
    summary: InvoiceSummary,
    onMovementClick: ((Movement) -> Unit)? = null,
) {
    if (summary.movements.isEmpty()) {
        Text(
            text = "Sem lançamentos nesta fatura ainda.",
            color = TextMuted,
            fontSize = 12.sp,
        )
        return
    }

    var viewMode by remember { mutableStateOf(InvoiceViewMode.BY_DAY) }
    var searchQuery by remember { mutableStateOf("") }
    var showAllItems by remember { mutableStateOf(false) }
    var expandedGroup by remember { mutableStateOf<String?>(null) }

    val filteredMovements = remember(summary.movements, searchQuery) {
        if (searchQuery.isBlank()) summary.movements
        else summary.movements.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SegmentedControl(
            options = InvoiceViewMode.values().map { it.label },
            selectedIndex = viewMode.ordinal,
            onSelect = { viewMode = InvoiceViewMode.values()[it] },
        )

        if (summary.movements.size > 5) {
            AppSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Buscar lançamento na fatura...",
            )
        }

        InvoiceMovementsStats(filteredMovements)

        if (filteredMovements.isEmpty()) {
            Text(
                text = "Nenhum lançamento encontrado para \"$searchQuery\".",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else if (viewMode == InvoiceViewMode.BY_CATEGORY) {
            val movementsByCategory = remember(filteredMovements) {
                filteredMovements.groupBy { it.category }
                    .toList()
                    .sortedByDescending { (_, list) -> list.sumOf { it.amount } }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                movementsByCategory.forEach { (category, movements) ->
                    val style = categoryStyle(category)
                    val catTotal = movements.sumOf { it.amount }
                    InvoiceGroupCard(
                        title = category,
                        count = movements.size,
                        total = catTotal,
                        percent = percentOf(catTotal, summary.total),
                        icon = style.icon,
                        color = style.color,
                        expanded = expandedGroup == category,
                        onToggle = {
                            expandedGroup = if (expandedGroup == category) null else category
                        },
                        movements = movements,
                        onMovementClick = onMovementClick,
                    )
                }
            }
        } else if (viewMode == InvoiceViewMode.BY_MERCHANT) {
            // Agrupar por estabelecimento é o que resume a fatura: em vez de
            // dezenas de linhas, o usuário vê onde concentrou o gasto.
            val movementsByMerchant = remember(filteredMovements) {
                filteredMovements.groupBy { FinanceCalculator.merchantName(it.title) }
                    .toList()
                    .sortedByDescending { (_, list) -> list.sumOf { it.amount } }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                movementsByMerchant.forEach { (merchant, movements) ->
                    val merchantTotal = movements.sumOf { it.amount }
                    InvoiceGroupCard(
                        title = merchant,
                        count = movements.size,
                        total = merchantTotal,
                        percent = percentOf(merchantTotal, summary.total),
                        icon = Icons.Rounded.Storefront,
                        color = Blue,
                        expanded = expandedGroup == merchant,
                        onToggle = {
                            expandedGroup = if (expandedGroup == merchant) null else merchant
                        },
                        movements = movements,
                        onMovementClick = onMovementClick,
                    )
                }
            }
        } else {
            val maxInitialItems = 8
            val visibleMovements = if (showAllItems || filteredMovements.size <= maxInitialItems) {
                filteredMovements
            } else {
                filteredMovements.take(maxInitialItems)
            }
            val visibleGrouped = visibleMovements.groupBy { FinanceCalculator.dayStart(it.date) }
                .toSortedMap(compareByDescending { it })

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleGrouped.forEach { (dayStart, dayMovements) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = FinanceCalculator.formatFullDay(dayStart),
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = FinanceCalculator.brl(dayMovements.sumOf { it.amount }),
                            color = TextFaint,
                            fontSize = 11.sp,
                        )
                    }
                    dayMovements.forEach { movement ->
                        MovementRow(
                            movement = movement,
                            onClick = onMovementClick?.let { { it(movement) } },
                        )
                    }
                }

                if (filteredMovements.size > maxInitialItems) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface2)
                            .clickable { showAllItems = !showAllItems }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (showAllItems) {
                                "Recolher lançamentos"
                            } else {
                                "Ver todos os ${filteredMovements.size} lançamentos (+${filteredMovements.size - maxInitialItems})"
                            },
                            color = Mint,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        InvoiceTotalRow("Total da fatura", summary.total, TextMain)
        if (summary.paid > 0.0) {
            Spacer(Modifier.height(2.dp))
            InvoiceTotalRow("Pago", summary.paid, Income)
        }
        if (summary.pending > 0.0) {
            Spacer(Modifier.height(2.dp))
            InvoiceTotalRow("Em aberto", summary.pending, TextMain)
        }
    }
}

@Composable
private fun InvoiceTotalRow(label: String, value: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            text = FinanceCalculator.brl(value),
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

/** Porcentagem do grupo em relação ao total da fatura. */
private fun percentOf(part: Double, total: Double): Int =
    if (total > 0.0) ((part / total) * 100).toInt() else 0

/**
 * Resumo da fatura em uma linha: quantos lançamentos, ticket médio e maior
 * compra. Dá o panorama antes de o usuário abrir a lista.
 */
@Composable
private fun InvoiceMovementsStats(movements: List<Movement>) {
    if (movements.isEmpty()) return
    val expenses = movements.filter { !it.isIncome }
    val average = if (expenses.isEmpty()) 0.0 else expenses.sumOf { it.amount } / expenses.size
    val biggest = movements.maxByOrNull { it.amount }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            label = "Itens",
            value = movements.size.toString(),
            valueSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Ticket médio",
            value = FinanceCalculator.brl(average),
            valueSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Maior",
            value = biggest?.let { FinanceCalculator.brl(it.amount) } ?: "-",
            accent = Mint,
            valueSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Grupo de lançamentos (por categoria ou por estabelecimento) colapsável.
 * O grupo mostra o quanto concentrou da fatura; o detalhe só aparece quando o
 * usuário abre, para a lista não virar uma parede de linhas.
 */
@Composable
private fun InvoiceGroupCard(
    title: String,
    count: Int,
    total: Double,
    percent: Int,
    icon: ImageVector,
    color: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    movements: List<Movement>,
    onMovementClick: ((Movement) -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .border(
                1.dp,
                if (expanded) color.copy(alpha = 0.5f) else Color(0x14FFFFFF),
                RoundedCornerShape(14.dp),
            )
            .clickable { onToggle() }
            .padding(10.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = icon,
                    tint = color,
                    size = 32.dp,
                    iconSize = 16.dp,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            color = TextMain,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        Tag(
                            text = count.toString() + (if (count == 1) " item" else " itens"),
                            color = color,
                            showDot = false,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = percent.toString() + "% da fatura",
                        color = TextFaint,
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = FinanceCalculator.brl(total),
                    color = TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    movements.forEach { movement ->
                        MovementRow(
                            movement = movement,
                            onClick = onMovementClick?.let { { it(movement) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoBillsCard(onSync: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        CardHeader(
            icon = Icons.Rounded.Event,
            tint = TextMuted,
            title = "Sem faturas sincronizadas",
            subtitle = "Sincronize para buscar as faturas deste cartão.",
            trailing = {
                Text(
                    text = "Sincronizar",
                    color = Mint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .clickable { onSync() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            },
        )
    }
}

@Composable
private fun AccountInstallmentsSection(
    account: FinanceAccount?,
    plans: List<InstallmentPlan>,
) {
    val totalRemaining = plans.sumOf { it.remaining }
    var expanded by remember(account?.id) { mutableStateOf(true) }

    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = Icons.Rounded.CreditCard,
                tint = Purple,
                size = 38.dp,
                iconSize = 19.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = account?.displayName ?: "Compras manuais / Outros cartões",
                    color = TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        if (!account?.number.isNullOrBlank()) {
                            append("•••• ")
                            append(account.number)
                            append(" • ")
                        }
                        append(plans.size)
                        append(if (plans.size == 1) " parcelamento ativo" else " parcelamentos ativos")
                    },
                    color = TextMuted,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("Total a pagar", color = TextFaint, fontSize = 9.sp)
                Text(
                    text = FinanceCalculator.brl(totalRemaining),
                    color = Purple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    plans.forEach { plan ->
                        InstallmentCard(plan)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallmentCard(plan: InstallmentPlan) {
    val style = categoryStyle(plan.category)
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = style.icon,
                tint = style.color,
                size = 40.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plan.title,
                        color = TextMain,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Tag(
                        text = plan.current.toString() + "/" + plan.total,
                        color = Purple,
                        showDot = false,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = plan.current.toString() + " de " + plan.total + " parcelas de " +
                        FinanceCalculator.brl(plan.installmentAmount),
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Tag(text = plan.category, color = style.color, showDot = false)
                    plan.nextMonth?.let { next ->
                        Text(
                            text = "Próxima em " + FinanceCalculator.formatMonthShort(next),
                            color = TextFaint,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("Falta", color = TextFaint, fontSize = 10.sp)
                Text(
                    text = FinanceCalculator.brl(plan.remaining),
                    color = TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(
            progress = (plan.current.toFloat() / plan.total).coerceIn(0f, 1f),
            color = Purple,
            height = 6.dp,
        )
    }
}

private fun statusColor(status: InvoiceStatus): Color = when (status) {
    InvoiceStatus.OPEN -> Mint
    InvoiceStatus.FUTURE -> Blue
    InvoiceStatus.PAID -> Income
    InvoiceStatus.OVERDUE -> Danger
}

private fun statusIcon(status: InvoiceStatus): ImageVector = when (status) {
    InvoiceStatus.OPEN -> Icons.AutoMirrored.Rounded.ReceiptLong
    InvoiceStatus.FUTURE -> Icons.Rounded.Schedule
    InvoiceStatus.PAID -> Icons.Rounded.CheckCircle
    InvoiceStatus.OVERDUE -> Icons.Rounded.Warning
}
