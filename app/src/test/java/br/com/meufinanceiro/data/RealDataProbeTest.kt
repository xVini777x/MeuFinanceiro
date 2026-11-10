package br.com.meufinanceiro.data

import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class RealDataProbeTest {

    private fun load(name: String): List<List<String>> {
        val stream = javaClass.classLoader!!.getResourceAsStream(name)
            ?: error("missing resource $name")
        return stream.bufferedReader(Charsets.UTF_8).readLines()
            .filter { it.isNotBlank() }
            .map { it.split("\t") }
    }

    private fun opt(v: String?): String? = v?.takeIf { it.isNotBlank() && it != "null" }

    private fun movements(): List<Movement> = load("real_movements.tsv").drop(1).map { c ->
        Movement(
            id = c[0],
            title = c[1],
            amount = c[2].toDouble(),
            type = MovementType.valueOf(c[3]),
            category = c[4],
            date = c[5].toLong(),
            accountId = opt(c[6]),
            accountName = opt(c[7]),
            isCreditCard = c[8].toBoolean(),
            installmentNumber = opt(c[9])?.toInt(),
            totalInstallments = opt(c[10])?.toInt(),
            billId = opt(c[11]),
            billMonth = opt(c[12]),
            status = c[13],
            source = MovementSource.valueOf(c[14]),
        )
    }

    private fun bills(): List<CreditBill> = load("real_bills.tsv").drop(1).map { c ->
        CreditBill(
            id = c[0],
            accountId = c[1],
            dueDate = c[2].toLong(),
            closingDate = opt(c[3])?.toLong(),
            totalAmount = c[4].toDouble(),
            minimumPayment = opt(c[5])?.toDouble(),
            paidAmount = opt(c[6])?.toDouble() ?: 0.0,
            financeCharges = opt(c[7])?.toDouble() ?: 0.0,
            lastPaymentDate = opt(c[8])?.toLong(),
        )
    }

    private fun account(): FinanceAccount {
        val c = load("real_account.tsv")[1]
        return FinanceAccount(
            id = c[0],
            name = c[1],
            type = c[2],
            subtype = c[3],
            balance = c[4].toDouble(),
            creditLimit = opt(c[5])?.toDouble(),
            availableCreditLimit = opt(c[6])?.toDouble(),
            closeDate = opt(c[7]),
            dueDate = opt(c[8]),
        )
    }

    private fun money(v: Double) = String.format(Locale.US, "%.2f", v)

    @Test
    fun probe() {
        val account = account()
        val bills = bills()
        val movements = movements()
        val today = LocalDate.of(2026, 11, 9)
        val entries = FinanceCalculator.invoiceEntries(account, bills, movements, today)
        println("=== INVOICE ENTRIES (today=$today) ===")
        entries.forEach { e ->
            val sum = e.summary.movements.sumOf { it.amount }
            println(
                "${e.summary.month} status=${e.status} current=${e.isCurrent} projected=${e.isProjected} " +
                    "total=${money(e.summary.total)} paid=${money(e.summary.paid)} items=${e.summary.movements.size} sumItems=${money(sum)}"
            )
        }
        for (m in listOf(YearMonth.of(2026, 12), YearMonth.of(2027, 1))) {
            val e = entries.firstOrNull { it.summary.month == m } ?: continue
            println("--- $m detail (${e.summary.movements.size}) ---")
            e.summary.movements.sortedByDescending { it.date }.forEach { mv ->
                println(
                    "  ${FinanceCalculator.toLocalDate(mv.date)} ${money(mv.amount)} " +
                        "inst=${mv.installmentNumber}/${mv.totalInstallments} bill=${mv.billId?.take(8)} fc=${mv.billMonth} ${mv.title}"
                )
            }
        }
    }
}