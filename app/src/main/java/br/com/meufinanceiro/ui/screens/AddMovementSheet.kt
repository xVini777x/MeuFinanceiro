package br.com.meufinanceiro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.meufinanceiro.data.Categories
import br.com.meufinanceiro.data.FinanceAccount
import br.com.meufinanceiro.data.FinanceCalculator
import br.com.meufinanceiro.data.Movement
import br.com.meufinanceiro.data.MovementType
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
import br.com.meufinanceiro.ui.theme.categoryColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMovementSheet(
    accounts: List<FinanceAccount>,
    onDismiss: () -> Unit,
    onSave: (Movement, Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(MovementType.EXPENSE) }
    var category by remember { mutableStateOf(Categories.FOOD) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var installments by remember { mutableStateOf("1") }
    var error by remember { mutableStateOf<String?>(null) }

    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId }
    val isCredit = selectedAccount?.isCredit == true
    val categories = if (type == MovementType.INCOME) {
        listOf(Categories.SALARY, Categories.INVESTMENTS, Categories.TRANSFERS, Categories.OTHER)
    } else {
        Categories.expense
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
            Text("Nova movimentação", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Registre um gasto, receita ou compra parcelada.",
                color = TextMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(18.dp))

            SegmentedControl(
                options = listOf("Despesa", "Receita"),
                selectedIndex = if (type == MovementType.EXPENSE) 0 else 1,
                onSelect = { index ->
                    type = if (index == 0) MovementType.EXPENSE else MovementType.INCOME
                    category = if (index == 0) Categories.FOOD else Categories.SALARY
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Descrição") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Valor (R$)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp),
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "CATEGORIA",
                color = TextFaint,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it }) { option ->
                    FilterPill(option, category == option) { category = option }
                }
            }

            if (accounts.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "CONTA OU CARTÃO",
                    color = TextFaint,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterPill("Sem conta", selectedAccountId == null) { selectedAccountId = null }
                    }
                    items(accounts, key = { it.id }) { account ->
                        FilterPill(account.displayName, selectedAccountId == account.id) {
                            selectedAccountId = account.id
                        }
                    }
                }
            }

            if (isCredit) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = installments,
                    onValueChange = { input ->
                        installments = input.filter { it.isDigit() }.take(2)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Parcelas") },
                    supportingText = {
                        Text(
                            "A compra será dividida em parcelas mensais no cartão.",
                            color = TextFaint,
                            fontSize = 10.sp,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                )
            }

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
                    colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = TextMain),
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        val parsed = parseAmountInput(amount)
                        when {
                            title.isBlank() -> error = "Informe uma descrição."
                            parsed == null || parsed <= 0.0 -> error = "Informe um valor válido."
                            else -> {
                                val count = if (isCredit) {
                                    installments.toIntOrNull()?.coerceIn(1, 48) ?: 1
                                } else {
                                    1
                                }
                                onSave(
                                    Movement(
                                        title = title.trim(),
                                        amount = parsed,
                                        type = type,
                                        category = category,
                                        accountId = selectedAccount?.id,
                                        accountName = selectedAccount?.displayName,
                                        isCreditCard = isCredit,
                                    ),
                                    count,
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
        }
    }
}

