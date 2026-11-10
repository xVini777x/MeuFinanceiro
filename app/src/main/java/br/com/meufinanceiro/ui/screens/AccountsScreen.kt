package br.com.meufinanceiro.ui.screens

import android.content.Intent
import android.widget.Toast
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.meufinanceiro.data.FinanceAccount
import br.com.meufinanceiro.data.FinanceCalculator
import br.com.meufinanceiro.data.PluggyItemSummary
import br.com.meufinanceiro.ui.AppUiState
import br.com.meufinanceiro.ui.BiometricStatus
import br.com.meufinanceiro.ui.components.CardHeader
import br.com.meufinanceiro.ui.components.EmptyState
import br.com.meufinanceiro.ui.components.GlassCard
import br.com.meufinanceiro.ui.components.IconBadge
import br.com.meufinanceiro.ui.components.InfoRow
import br.com.meufinanceiro.ui.components.ProgressBar
import br.com.meufinanceiro.ui.components.ScreenHeader
import br.com.meufinanceiro.ui.components.SectionHeader
import br.com.meufinanceiro.ui.components.Tag
import br.com.meufinanceiro.ui.theme.Amber
import br.com.meufinanceiro.ui.theme.Bg
import br.com.meufinanceiro.ui.theme.Border
import br.com.meufinanceiro.ui.theme.Danger
import br.com.meufinanceiro.ui.theme.Income
import br.com.meufinanceiro.ui.theme.Mint
import br.com.meufinanceiro.ui.theme.Purple
import br.com.meufinanceiro.ui.theme.Surface1
import br.com.meufinanceiro.ui.theme.Surface2
import br.com.meufinanceiro.ui.theme.TextFaint
import br.com.meufinanceiro.ui.theme.TextMain
import br.com.meufinanceiro.ui.theme.TextMuted
import kotlin.math.abs

@Composable
fun AccountsScreen(
    state: AppUiState,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onUpdate: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onTest: () -> Unit,
    onAddItem: (String) -> Unit,
    onOwnerNameChange: (String) -> Unit,
    biometricStatus: BiometricStatus,
    onBiometricLockChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val duplicateNames = state.items.groupingBy { it.connectorName }.eachCount()
    var showDiagnostics by remember { mutableStateOf(false) }
    var showItemDialog by remember { mutableStateOf(false) }
    var itemIdInput by remember { mutableStateOf("") }
    var showOwnerDialog by remember { mutableStateOf(false) }
    var ownerInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ScreenHeader(
                eyebrow = "Open Finance",
                title = "Contas e bancos",
                subtitle = "Conexão segura via Pluggy",
            )
        }

        if (!state.configured) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    CardHeader(
                        icon = Icons.Rounded.Warning,
                        tint = Amber,
                        title = "Credenciais não configuradas",
                        subtitle = "Adicione pluggy.clientId e pluggy.clientSecret ao local.properties e reconstrua o app.",
                    )
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                CardHeader(
                    icon = Icons.Rounded.CreditCard,
                    tint = Mint,
                    title = "Integração bancária",
                    subtitle = if (state.items.isEmpty()) {
                        "Nenhum banco conectado"
                    } else if (state.items.size == 1) {
                        "1 banco conectado"
                    } else {
                        state.items.size.toString() + " bancos conectados"
                    },
                    trailing = {
                        Tag(
                            text = if (state.items.isEmpty()) "Desconectado" else "Conectado",
                            color = if (state.items.isEmpty()) TextMuted else Income,
                            showDot = false,
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "A conexão acontece no ambiente seguro da Pluggy. Nenhuma senha bancária é digitada neste aplicativo.",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Uso pessoal (MeuPluggy): autorize seus bancos em meu.pluggy.ai. Depois, no dashboard.pluggy.ai (mesmo login), use Applications -> Demo -> Connect Account e escolha o conector MeuPluggy. O trial de 15 dias é do plano normal e não bloqueia esse uso.",
                    color = TextFaint,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Abrir meu.pluggy.ai",
                        color = Mint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://meu.pluggy.ai")),
                                )
                            }
                            .padding(vertical = 4.dp),
                    )
                    Text(
                        "Abrir dashboard.pluggy.ai",
                        color = Mint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://dashboard.pluggy.ai")),
                                )
                            }
                            .padding(vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onConnect,
                        enabled = state.configured && !state.loadingToken && state.connectToken == null,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Bg),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (state.loadingToken) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Bg)
                        } else {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (state.items.isEmpty()) "Conectar banco" else "Conectar",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onSync,
                        enabled = state.items.isNotEmpty() && !state.syncing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (state.syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Mint)
                        } else {
                            Icon(Icons.Rounded.Sync, null, modifier = Modifier.size(18.dp), tint = Mint)
                            Spacer(Modifier.width(6.dp))
                            Text("Sincronizar", color = Mint)
                        }
                    }
                }
                if (state.items.any { it.connectorId == 200 }) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Sincronizar lê o que o MeuPluggy já coletou. Se a compra é recente, abra meu.pluggy.ai para atualizar o banco primeiro.",
                        color = TextFaint,
                        fontSize = 10.sp,
                    )
                }
                if (state.configured) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (state.testing) "Testando conexão..." else "Testar conexão com a Pluggy",
                        color = TextFaint,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100))
                            .clickable(enabled = !state.testing) { onTest() }
                            .padding(vertical = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showItemDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp), tint = Mint)
                        Spacer(Modifier.width(6.dp))
                        Text("Adicionar por itemId", color = Mint, fontSize = 12.sp)
                    }
                }
            }
        }

        if (state.items.isNotEmpty()) {
            item { SectionHeader("Bancos conectados") }
            if (state.hasSandboxData) {
                item {
                    Text(
                        "Dados de teste (sandbox) não entram nos totais.",
                        color = TextMuted,
                        fontSize = 11.sp,
                    )
                }
            }
            items(state.items, key = { it.id }) { item ->
                ConnectedBankRow(
                    item = item,
                    displayName = if ((duplicateNames[item.connectorName] ?: 0) > 1) {
                        item.connectorName + " - " + item.id.takeLast(6)
                    } else {
                        item.connectorName
                    },
                    accounts = state.accounts.filter { it.itemId == item.id },
                    updateLabel = if (item.connectorId == 200) {
                        "Atualizar no MeuPluggy"
                    } else {
                        "Atualizar conexão"
                    },
                    onUpdate = {
                        if (item.connectorId == 200) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://meu.pluggy.ai")),
                            )
                        } else {
                            onUpdate(item.id)
                        }
                    },
                    onDisconnect = { onDisconnect(item.id) },
                )
            }
        }

        val looseAccounts = state.accounts.filter { account ->
            account.itemId == null || state.items.none { it.id == account.itemId }
        }
        if (looseAccounts.isNotEmpty()) {
            item { SectionHeader("Outras contas") }
            items(looseAccounts, key = { it.id }) { account -> AccountCard(account) }
        } else if (state.items.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    title = "Nenhuma conta sincronizada",
                    subtitle = "Conecte seu banco para importar contas, cartões e transações.",
                    actionLabel = "Conectar banco",
                    onAction = onConnect,
                )
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                CardHeader(
                    icon = Icons.Rounded.SwapHoriz,
                    tint = Mint,
                    title = "Transferências entre contas",
                    subtitle = "PIX/TED para você mesmo",
                    trailing = {
                        Tag(
                            text = "Editar",
                            color = Mint,
                            showDot = false,
                            modifier = Modifier.clickable {
                                ownerInput = state.ownerName
                                showOwnerDialog = true
                            },
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
                InfoRow(
                    label = "Nome reconhecido",
                    value = state.ownerName.ifBlank { "não definido" },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Não entram como receita nem despesa. Também identificamos o par saída/entrada de mesmo valor entre bancos conectados.",
                    color = TextFaint,
                    fontSize = 10.sp,
                )
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth()) {
                CardHeader(
                    icon = Icons.Rounded.Fingerprint,
                    tint = Mint,
                    title = "Segurança",
                    subtitle = "Desbloqueio por biometria",
                    trailing = {
                        Tag(
                            text = if (state.biometricLock) "Ativado" else "Desativado",
                            color = if (state.biometricLock) Income else TextMuted,
                            showDot = false,
                        )
                    },
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = biometricStatus != BiometricStatus.UNSUPPORTED) {
                            onBiometricLockChange(!state.biometricLock)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Pedir biometria ao abrir",
                            color = TextMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = when {
                                biometricStatus == BiometricStatus.UNSUPPORTED ->
                                    "Aparelho não possui leitor de biometria disponível."
                                biometricStatus == BiometricStatus.NONE_ENROLLED && !state.biometricLock ->
                                    "Nenhuma biometria cadastrada. Toque para cadastrar no aparelho."
                                state.biometricLock ->
                                    "Ativo. Bloqueia ao abrir e após 1 minuto em segundo plano."
                                else ->
                                    "Ative para pedir sua biometria ao entrar no app."
                            },
                            color = if (biometricStatus == BiometricStatus.NONE_ENROLLED && !state.biometricLock) Amber else TextFaint,
                            fontSize = 10.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = state.biometricLock,
                        onCheckedChange = onBiometricLockChange,
                        enabled = biometricStatus != BiometricStatus.UNSUPPORTED || state.biometricLock,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Bg,
                            checkedTrackColor = Mint,
                            checkedBorderColor = Mint,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = Surface2,
                            uncheckedBorderColor = Border,
                        ),
                    )
                }
            }
        }

        if (state.diagnostics.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDiagnostics = !showDiagnostics },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Diagnóstico da conexão",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                clipboard.setText(
                                    AnnotatedString(state.diagnostics.joinToString("\n")),
                                )
                                Toast.makeText(
                                    context,
                                    "Diagnóstico copiado",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copiar diagnóstico",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Icon(
                            if (showDiagnostics) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null,
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (showDiagnostics) {
                        Spacer(Modifier.height(10.dp))
                        state.diagnostics.forEach { line ->
                            Text(
                                text = "• " + line,
                                color = TextFaint,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showItemDialog) {
        AlertDialog(
            onDismissRequest = { showItemDialog = false },
            containerColor = Surface1,
            title = { Text("Adicionar conexão", color = TextMain, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Cole o itemId de cada banco. Passo a passo:\n1) dashboard.pluggy.ai com o mesmo login do meu.pluggy.ai.\n2) Applications -> Demo -> Connect Account.\n3) Escolha o conector MeuPluggy (não o banco).\n4) Em Connected Items, copie o itemId de cada banco.\n\nO trial de 15 dias é do plano normal e não afeta o MeuPluggy.\n\nImportante: não use itens do conector Sandbox (dados de teste). Escolha o conector MeuPluggy.",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = itemIdInput,
                        onValueChange = { itemIdInput = it },
                        label = { Text("itemId") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = itemIdInput.trim()
                    if (id.isNotBlank()) {
                        onAddItem(id)
                        itemIdInput = ""
                        showItemDialog = false
                    }
                }) {
                    Text("Adicionar", color = Mint, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showItemDialog = false }) { Text("Cancelar", color = TextMuted) }
            },
        )
    }

    if (showOwnerDialog) {
        AlertDialog(
            onDismissRequest = { showOwnerDialog = false },
            containerColor = Surface1,
            title = { Text("Seu nome", color = TextMain, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Usamos esse nome para reconhecer PIX/TED enviados para você mesmo. Separe mais de um nome por vírgula.",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ownerInput,
                        onValueChange = { ownerInput = it },
                        label = { Text("Nome completo") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (ownerInput.isNotBlank()) {
                        onOwnerNameChange(ownerInput.trim())
                        showOwnerDialog = false
                    }
                }) {
                    Text("Salvar", color = Mint, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOwnerDialog = false }) { Text("Cancelar", color = TextMuted) }
            },
        )
    }
}

@Composable
private fun ConnectedBankRow(
    item: PluggyItemSummary,
    displayName: String,
    accounts: List<FinanceAccount>,
    updateLabel: String,
    onUpdate: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val (statusLabel, color) = statusInfo(item.status)
    val lastSync = FinanceCalculator.formatIsoDateTime(item.lastUpdatedAt)
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = Icons.Rounded.AccountBalanceWallet,
                tint = Mint,
                size = 42.dp,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(statusLabel, color, showDot = false)
                    if (item.isSandbox) {
                        Tag("SANDBOX", Amber, showDot = false)
                    }
                }
                if (lastSync != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Última coleta do banco: " + lastSync,
                        color = TextFaint,
                        fontSize = 10.sp,
                    )
                }
                if (item.connectorId == 200) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Este banco é atualizado pelo MeuPluggy. O app não consegue forçar a coleta: abra meu.pluggy.ai e depois sincronize aqui.",
                        color = TextFaint,
                        fontSize = 10.sp,
                    )
                }
            }
            IconButton(onClick = onUpdate) {
                Icon(Icons.Rounded.Refresh, updateLabel, tint = Mint, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDisconnect) {
                Icon(Icons.Rounded.DeleteOutline, "Desconectar", tint = TextMuted, modifier = Modifier.size(20.dp))
            }
        }
        if (accounts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            accounts.forEach { account ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        icon = if (account.isCredit) Icons.Rounded.CreditCard else Icons.Rounded.AccountBalanceWallet,
                        tint = if (account.isCredit) Purple else Income,
                        size = 26.dp,
                        iconSize = 14.dp,
                        corner = 9.dp,
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = account.displayName,
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = FinanceCalculator.brl(if (account.isCredit) abs(account.balance) else account.balance),
                        color = if (account.isCredit) Purple else TextMain,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountCard(account: FinanceAccount) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = if (account.isCredit) Icons.Rounded.CreditCard else Icons.Rounded.AccountBalanceWallet,
                tint = if (account.isCredit) Purple else Income,
                size = 42.dp,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = account.displayName,
                    color = TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = account.subtype.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() } +
                        (account.number?.let { " • " + it } ?: ""),
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val shownBalance = if (account.isCredit) abs(account.balance) else account.balance
            Text(
                text = FinanceCalculator.brl(shownBalance),
                color = if (account.isCredit) Purple else if (shownBalance >= 0) Income else Danger,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
        if (account.isCredit) {
            val usage = account.limitUsage
            if (usage != null) {
                Spacer(Modifier.height(12.dp))
                ProgressBar(progress = usage.toFloat(), color = Purple, height = 6.dp)
                Spacer(Modifier.height(8.dp))
                InfoRow("Limite total", FinanceCalculator.brl(account.creditLimit ?: 0.0))
                InfoRow(
                    "Limite disponível",
                    FinanceCalculator.brl(((account.creditLimit ?: 0.0) - abs(account.balance)).coerceAtLeast(0.0)),
                    Mint,
                )
                val due = FinanceCalculator.parseIsoDate(account.dueDate)
                if (due != null) {
                    InfoRow("Próximo vencimento", FinanceCalculator.formatDate(due))
                }
            }
        }
    }
}

private fun statusInfo(status: String): Pair<String, Color> = when (status.uppercase()) {
    "UPDATED", "SUCCESS", "CONNECTED" -> "Conectado" to Income
    "UPDATING", "UPDATING_ITEM", "CREATED" -> "Sincronizando" to Amber
    "LOGIN_ERROR", "INVALID_CREDENTIALS" -> "Credenciais inválidas" to Danger
    "WAITING_USER_INPUT" -> "Aguardando informação" to Amber
    "WAITING_USER_ACTION", "USER_AUTHORIZATION_PENDING" -> "Aguardando ação no banco" to Amber
    "OUTDATED" -> "Desatualizado" to Amber
    else -> status to TextMuted
}