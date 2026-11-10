package br.com.meufinanceiro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DonutSmall
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.meufinanceiro.data.FinanceCalculator
import br.com.meufinanceiro.ui.components.PrivacyToggle
import br.com.meufinanceiro.ui.screens.AccountsScreen
import br.com.meufinanceiro.ui.screens.AddMovementSheet
import br.com.meufinanceiro.ui.screens.CategoriesScreen
import br.com.meufinanceiro.ui.screens.HomeScreen
import br.com.meufinanceiro.ui.screens.InvestmentsScreen
import br.com.meufinanceiro.ui.screens.InvoicesScreen
import br.com.meufinanceiro.ui.screens.StatementScreen
import br.com.meufinanceiro.ui.theme.Bg
import br.com.meufinanceiro.ui.theme.Border
import br.com.meufinanceiro.ui.theme.Mint
import br.com.meufinanceiro.ui.theme.Surface1
import br.com.meufinanceiro.ui.theme.TextFaint
import br.com.meufinanceiro.ui.theme.TextMain
import br.com.meufinanceiro.ui.theme.TextMuted
import java.time.YearMonth

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Rounded.Home),
    STATEMENT("Extrato", Icons.AutoMirrored.Rounded.ReceiptLong),
    CATEGORIES("Categorias", Icons.Rounded.DonutSmall),
    INVOICES("Faturas", Icons.Rounded.CreditCard),
    INVESTMENTS("Investir", Icons.AutoMirrored.Rounded.ShowChart),
    ACCOUNTS("Contas", Icons.Rounded.AccountBalanceWallet),
}

@Composable
fun FinanceApp(
    viewModel: AppViewModel,
    onBiometricLockChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val biometricStatus = remember(activity) {
        activity?.let { BiometricAuth.status(it) } ?: BiometricStatus.UNSUPPORTED
    }

    // O app abre no mês da fatura em aberto: é o mês que o usuário quer ver.
    var month by remember {
        mutableStateOf(
            FinanceCalculator.currentInvoiceMonth(
                state.realAccounts,
                state.realBills,
                state.realMovements,
            ) ?: YearMonth.now(),
        )
    }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var showAdd by remember { mutableStateOf(false) }

    // O botão de adicionar some quando o usuário rola a lista para baixo, para
    // não cobrir o conteúdo.
    var fabVisible by remember { mutableStateOf(true) }
    val fabScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -14f) fabVisible = false
                if (available.y > 14f) fabVisible = true
                return Offset.Zero
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Bg,
        bottomBar = {
            FinanceNavigationBar(
                selected = tab,
                onSelect = { tab = it },
            )
        },
        floatingActionButton = {
            // O botão de adicionar só faz sentido nas telas de movimentações.
            // Em Faturas, Investir e Contas ele cobria linhas do conteúdo.
            val showFab = tab == Tab.HOME || tab == Tab.STATEMENT || tab == Tab.CATEGORIES
            AnimatedVisibility(
                visible = showFab && fabVisible,
                enter = fadeIn(tween(160)) + scaleIn(tween(180), initialScale = 0.85f),
                exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.85f),
            ) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = Mint,
                    contentColor = Bg,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Icon(Icons.Rounded.Add, "Adicionar movimentação")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).nestedScroll(fabScrollConnection)) {
            when (tab) {
                Tab.HOME -> HomeScreen(
                    state = state,
                    month = month,
                    onPreviousMonth = { month = month.minusMonths(1) },
                    onNextMonth = { month = month.plusMonths(1) },
                    onOpenStatement = { tab = Tab.STATEMENT },
                    onOpenInvoices = { tab = Tab.INVOICES },
                    onOpenCategories = { tab = Tab.CATEGORIES },
                    onOpenInvestments = { tab = Tab.INVESTMENTS },
                    onConnectBank = viewModel::connectNewBank,
                    onAddMovement = { showAdd = true },
                    onUpdateMovementCategory = viewModel::updateMovementCategory,
                    onDeleteMovement = viewModel::deleteMovement,
                )

                Tab.STATEMENT -> StatementScreen(
                    state = state,
                    month = month,
                    onPreviousMonth = { month = month.minusMonths(1) },
                    onNextMonth = { month = month.plusMonths(1) },
                    onDeleteMovement = viewModel::deleteMovement,
                    onUpdateMovementCategory = viewModel::updateMovementCategory,
                )

                Tab.CATEGORIES -> CategoriesScreen(
                    state = state,
                    month = month,
                    onPreviousMonth = { month = month.minusMonths(1) },
                    onNextMonth = { month = month.plusMonths(1) },
                    onSetBudget = viewModel::setBudget,
                    onRemoveCategoryRule = viewModel::removeCategoryRule,
                )

                Tab.INVOICES -> InvoicesScreen(
                    state = state,
                    onSync = viewModel::sync,
                    onUpdateMovementCategory = viewModel::updateMovementCategory,
                    onDeleteMovement = viewModel::deleteMovement,
                )

                Tab.INVESTMENTS -> InvestmentsScreen(
                    state = state,
                    onSaveInvestment = viewModel::saveInvestment,
                    onDeleteInvestment = viewModel::deleteInvestment,
                    onRefreshQuotes = viewModel::refreshQuotes,
                )

                Tab.ACCOUNTS -> AccountsScreen(
                    state = state,
                    onConnect = viewModel::connectNewBank,
                    onSync = viewModel::sync,
                    onUpdate = viewModel::updateBank,
                    onDisconnect = viewModel::disconnect,
                    onTest = viewModel::testConnection,
                    onAddItem = viewModel::addItemManually,
                    onOwnerNameChange = viewModel::setOwnerName,
                    biometricStatus = biometricStatus,
                    onBiometricLockChange = onBiometricLockChange,
                )
            }
        }
    }

    if (showAdd) {
        AddMovementSheet(
            // Pseudo-contas de transferência não servem para lançar nada.
            accounts = state.realAccounts.filter { it.isUsableAccount },
            onDismiss = { showAdd = false },
            onSave = { movement, installments ->
                viewModel.addMovement(movement, installments)
                showAdd = false
            },
        )
    }

    if (state.connectToken != null || state.connected) {
        ConnectedOverlay(
            syncing = state.syncing,
            message = state.message,
            onFinish = viewModel::finishConnection,
            onConnectAnother = viewModel::connectAnotherBank,
        )
    }
}

@Composable
private fun FinanceNavigationBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
) {
    Surface(color = Surface1) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.values().forEach { item ->
                FinanceNavigationItem(
                    tab = item,
                    selected = item == selected,
                    onClick = { onSelect(item) },
                )
            }
        }
    }
}

@Composable
private fun FinanceNavigationItem(
    tab: Tab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) Mint.copy(alpha = 0.16f) else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Icon(
                tab.icon,
                contentDescription = tab.label,
                tint = if (selected) Mint else TextFaint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = tab.label,
            color = if (selected) TextMain else TextFaint,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ConnectedOverlay(
    syncing: Boolean,
    message: String?,
    onFinish: () -> Unit,
    onConnectAnother: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE608080A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Surface1)
                .border(1.dp, Border, RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (syncing) "Sincronizando suas contas..." else (message ?: "Banco conectado!"),
                color = TextMain,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Seus dados aparecem em alguns segundos.",
                color = TextMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Bg),
            ) {
                Text("Continuar", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onConnectAnother,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Surface1,
                    contentColor = TextMuted,
                ),
            ) {
                Text("Conectar outro banco", fontSize = 13.sp)
            }
        }
    }
}
