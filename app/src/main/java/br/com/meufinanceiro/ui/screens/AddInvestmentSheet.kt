package br.com.meufinanceiro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.meufinanceiro.data.Investment
import br.com.meufinanceiro.ui.components.FilterPill
import br.com.meufinanceiro.ui.components.SegmentedControl
import br.com.meufinanceiro.ui.theme.Bg
import br.com.meufinanceiro.ui.theme.Danger
import br.com.meufinanceiro.ui.theme.Mint
import br.com.meufinanceiro.ui.theme.Surface1
import br.com.meufinanceiro.ui.theme.Surface2
import br.com.meufinanceiro.ui.theme.TextFaint
import br.com.meufinanceiro.ui.theme.TextMain
import br.com.meufinanceiro.ui.theme.TextMuted
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/** Tipos que o usuário escolhe ao cadastrar um investimento manualmente. */
private data class InvestmentKind(
    val label: String,
    val type: String,
    val subtype: String? = null,
)

private val investmentKinds = listOf(
    InvestmentKind("Renda fixa", "FIXED_INCOME"),
    InvestmentKind("Tesouro", "TREASURY"),
    InvestmentKind("Fundos", "MUTUAL_FUND"),
    InvestmentKind("Ações", "EQUITY", "STOCK"),
    InvestmentKind("FIIs", "EQUITY", "REAL_ESTATE_FUND"),
    InvestmentKind("ETFs", "ETF"),
    InvestmentKind("Previdência", "SECURITY"),
    InvestmentKind("COE", "COE"),
    InvestmentKind("Outros", "OTHER"),
)

/** Subtipo usado para as ações da bolsa. */
private const val STOCK_SUBTYPE = "STOCK"

/**
 * Cadastro manual de investimento. Serve para os dois casos: criar um novo
 * (quando [existing] é nulo) ou editar/remover um já adicionado à mão.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInvestmentSheet(
    existing: Investment?,
    onDismiss: () -> Unit,
    onSave: (Investment) -> Unit,
    onDelete: ((Investment) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var balance by remember(existing?.id) { mutableStateOf(inputValue(existing?.balance)) }
    var invested by remember(existing?.id) {
        mutableStateOf(inputValue(existing?.amountOriginal ?: existing?.amount))
    }
    var institution by remember(existing?.id) {
        mutableStateOf(existing?.institution ?: existing?.issuer.orEmpty())
    }
    var rate by remember(existing?.id) { mutableStateOf(inputValue(existing?.annualRate)) }
    var kindIndex by remember(existing?.id) { mutableStateOf(kindIndexOf(existing)) }
    var active by remember(existing?.id) { mutableStateOf(existing?.isActive ?: true) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Posição em ações: código, cotas e preços.
    var ticker by remember(existing?.id) { mutableStateOf(existing?.ticker.orEmpty()) }
    var quantity by remember(existing?.id) { mutableStateOf(inputValue(existing?.quantity)) }
    var averagePrice by remember(existing?.id) { mutableStateOf(inputValue(existing?.averagePrice)) }
    var currentPrice by remember(existing?.id) {
        mutableStateOf(
            existing?.let { investment ->
                val shares = investment.quantity
                if (shares != null && shares > 0.0) inputValue(investment.balance / shares) else ""
            } ?: "",
        )
    }

    val kind = investmentKinds[kindIndex]
    // Ações, FIIs e ETFs se negociam por cota e código: o formulário de
    // posição vale para os três.
    val isQuoted = kind.type == "EQUITY" || kind.type == "ETF"
    val positionLabel = when {
        kind.subtype == "REAL_ESTATE_FUND" -> "POSIÇÃO EM FIIs"
        kind.type == "ETF" -> "POSIÇÃO EM ETFs"
        else -> "POSIÇÃO EM AÇÕES"
    }

    val parsedQuantity = parseAmountInput(quantity)?.takeIf { it > 0.0 }
    val parsedAverage = parseAmountInput(averagePrice)?.takeIf { it > 0.0 }
    val parsedCurrent = parseAmountInput(currentPrice)?.takeIf { it > 0.0 }
    // Com cotas e preço, o saldo e o valor investido saem da multiplicação.
    val derivedBalance = if (parsedQuantity != null && parsedCurrent != null) {
        parsedQuantity * parsedCurrent
    } else {
        null
    }
    val derivedInvested = if (parsedQuantity != null && parsedAverage != null) {
        parsedQuantity * parsedAverage
    } else {
        null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface1,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(
                text = if (existing == null) "Novo investimento" else "Editar investimento",
                color = TextMain,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (existing == null) {
                    "Adicione o que você tem e atualize o saldo quando quiser."
                } else {
                    "Ajuste o saldo, o tipo ou marque como resgatado."
                },
                color = TextMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(18.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome do investimento") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )

            Spacer(Modifier.height(16.dp))
            FieldLabel("TIPO")
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(investmentKinds, key = { it.label }) { option ->
                    FilterPill(option.label, kind == option) {
                        kindIndex = investmentKinds.indexOf(option)
                    }
                }
            }

            if (isQuoted) {
                Spacer(Modifier.height(18.dp))
                FieldLabel(positionLabel)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ticker,
                    onValueChange = { ticker = it.uppercase().take(12) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Código da ação (PETR4)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Cotas") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                    )
                    OutlinedTextField(
                        value = averagePrice,
                        onValueChange = { averagePrice = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Preço médio") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = currentPrice,
                    onValueChange = { currentPrice = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Preço atual por cota (R$)") },
                    supportingText = {
                        Text(
                            "Com cotas e preço atual, o saldo da posição é calculado.",
                            color = TextFaint,
                            fontSize = 10.sp,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                )
                if (derivedBalance != null || derivedInvested != null) {
                    Spacer(Modifier.height(12.dp))
                    StockPreview(
                        quantity = parsedQuantity,
                        balance = derivedBalance,
                        invested = derivedInvested,
                    )
                }
            }

            if (derivedBalance == null) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Saldo atual (R$)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                )
            }

            if (derivedInvested == null) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = invested,
                    onValueChange = { invested = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Valor investido (opcional)") },
                    supportingText = {
                        Text(
                            "Com o valor investido calculamos o rendimento automaticamente.",
                            color = TextFaint,
                            fontSize = 10.sp,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = institution,
                onValueChange = { institution = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Banco ou corretora (opcional)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )

            if (!isQuoted) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Taxa anual % (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            FieldLabel("SITUAÇÃO")
            Spacer(Modifier.height(8.dp))
            SegmentedControl(
                options = listOf("Ativo", "Resgatado"),
                selectedIndex = if (active) 0 else 1,
                onSelect = { active = it == 0 },
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(message, color = Danger, fontSize = 12.sp)
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Surface2,
                        contentColor = TextMain,
                    ),
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        val parsedBalance = parseAmountInput(balance)
                        val parsedInvested = parseAmountInput(invested)?.takeIf { it > 0.0 }
                        val parsedRate = parseAmountInput(rate)?.takeIf { it > 0.0 }
                        val finalName = name.trim().ifBlank { ticker.trim() }
                        // Com cotas e preço, saldo e investido saem da multiplicação.
                        val finalBalance = derivedBalance ?: parsedBalance
                        val finalInvested = derivedInvested ?: parsedInvested
                        when {
                            finalName.isBlank() -> error = "Informe o nome do investimento."
                            finalBalance == null || finalBalance < 0.0 ->
                                error = if (isQuoted) {
                                    "Informe as cotas e o preço atual, ou o saldo da posição."
                                } else {
                                    "Informe um saldo válido."
                                }
                            else -> {
                                val profit = finalInvested
                                    ?.let { roundMoney(finalBalance - it) }
                                    ?.takeIf { abs(it) >= 0.005 }
                                onSave(
                                    Investment(
                                        id = existing?.id ?: UUID.randomUUID().toString(),
                                        itemId = existing?.itemId,
                                        name = finalName,
                                        type = kind.type,
                                        subtype = kind.subtype,
                                        balance = roundMoney(finalBalance),
                                        amountOriginal = finalInvested,
                                        amountProfit = profit,
                                        currency = existing?.currency ?: "BRL",
                                        status = if (active) "ACTIVE" else "REDEEMED",
                                        institution = institution.trim().takeIf { it.isNotBlank() },
                                        annualRate = parsedRate,
                                        ticker = if (isQuoted) {
                                            ticker.trim().uppercase().takeIf { it.isNotBlank() }
                                        } else {
                                            null
                                        },
                                        quantity = if (isQuoted) parsedQuantity else null,
                                        averagePrice = if (isQuoted) parsedAverage else null,
                                    ),
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Bg),
                ) {
                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Salvar", fontWeight = FontWeight.Bold)
                }
            }

            if (existing != null && onDelete != null) {
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        null,
                        tint = Danger,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Excluir investimento", color = Danger, fontSize = 13.sp)
                }
            }
        }
    }

    if (confirmDelete && existing != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Surface1,
            title = { Text("Excluir investimento", color = TextMain, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Remover \"" + existing.name + "\" da carteira?",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(existing)
                }) {
                    Text("Excluir", color = Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancelar", color = TextMuted)
                }
            },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = TextFaint,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
    )
}

/**
 * Prévia da posição em ações: quanto a posição vale hoje, quanto foi
 * investido e o resultado. Aparece enquanto o usuário preenche.
 */
@Composable
private fun StockPreview(
    quantity: Double?,
    balance: Double?,
    invested: Double?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            balance?.let {
                Text(
                    text = "Saldo da posição: " + formatMoney(it),
                    color = TextMain,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            invested?.let {
                Text(
                    text = "Investido: " + formatMoney(it),
                    color = TextMuted,
                    fontSize = 11.sp,
                )
            }
            if (balance != null && invested != null) {
                val profit = balance - invested
                val percent = if (invested > 0.0) profit / invested * 100.0 else 0.0
                Text(
                    text = "Resultado: " + formatMoneySigned(profit) +
                        " (" + String.format(Locale("pt", "BR"), "%.2f", percent) + "%)",
                    color = if (profit >= 0.0) Mint else Danger,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            quantity?.let {
                Text(
                    text = formatQuantity(it) + (if (it == 1.0) " cota" else " cotas"),
                    color = TextFaint,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

private fun formatMoney(value: Double): String =
    "R$ " + String.format(Locale("pt", "BR"), "%,.2f", value)

private fun formatMoneySigned(value: Double): String =
    (if (value >= 0.0) "+" else "−") + formatMoney(abs(value))

/** Quantidade de cotas sem casas decimais desnecessárias. */
private fun formatQuantity(value: Double): String =
    if (abs(value - value.toLong()) < 0.0001) {
        value.toLong().toString()
    } else {
        String.format(Locale("pt", "BR"), "%.4f", value).trimEnd('0').trimEnd(',', '.')
    }

private fun kindIndexOf(investment: Investment?): Int {
    if (investment == null) return 0
    val index = investmentKinds.indexOfFirst {
        it.type.equals(investment.type, true) &&
            (it.subtype ?: "").equals(investment.subtype ?: "", true)
    }
    if (index >= 0) return index
    // Ações importadas do banco, sem subtipo, continuam sendo "Ações".
    if (investment.type.equals("EQUITY", true) && investment.subtype.isNullOrBlank()) {
        val stock = investmentKinds.indexOfFirst { it.subtype == STOCK_SUBTYPE }
        if (stock >= 0) return stock
    }
    // Um tipo vindo do banco que não está na lista entra como "Outros".
    return investmentKinds.lastIndex
}

private fun inputValue(value: Double?): String {
    if (value == null) return ""
    val rounded = roundMoney(value)
    return if (abs(rounded - rounded.toLong()) < 0.005) {
        rounded.toLong().toString()
    } else {
        String.format(Locale("pt", "BR"), "%.2f", rounded)
    }
}
