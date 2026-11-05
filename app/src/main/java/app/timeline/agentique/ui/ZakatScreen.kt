package app.timeline.agentique.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.timeline.agentique.R
import app.timeline.agentique.container
import app.timeline.agentique.core.formatNum
import app.timeline.agentique.core.parseNum
import app.timeline.agentique.zakat.NisabBasis
import app.timeline.agentique.zakat.ZakatCalculator
import app.timeline.agentique.zakat.ZakatInput
import app.timeline.agentique.zakat.ZakatKey
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun ZakatKey.label(): Int = when (this) {
    ZakatKey.CASH -> R.string.zakat_cash
    ZakatKey.GOLD -> R.string.zakat_line_gold
    ZakatKey.SILVER -> R.string.zakat_line_silver
    ZakatKey.INVESTMENTS -> R.string.zakat_investments
    ZakatKey.RECEIVABLES -> R.string.zakat_receivables
    ZakatKey.INVENTORY -> R.string.zakat_inventory
    ZakatKey.GROSS -> R.string.zakat_line_gross
    ZakatKey.DEBTS -> R.string.zakat_line_debts
    ZakatKey.NET -> R.string.zakat_line_net
    ZakatKey.NISAB -> R.string.zakat_line_nisab
}

/** "Canvas" for the complex task: sources, parameters and visible calculation on one screen. */
@Composable
fun ZakatScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var basis by rememberSaveable { mutableStateOf<NisabBasis?>(null) }
    var goldPrice by rememberSaveable { mutableStateOf("") }
    var silverPrice by rememberSaveable { mutableStateOf("") }
    var cash by rememberSaveable { mutableStateOf("") }
    var goldGrams by rememberSaveable { mutableStateOf("") }
    var silverGrams by rememberSaveable { mutableStateOf("") }
    var investments by rememberSaveable { mutableStateOf("") }
    var receivables by rememberSaveable { mutableStateOf("") }
    var inventory by rememberSaveable { mutableStateOf("") }
    var debts by rememberSaveable { mutableStateOf("") }
    var hawl by rememberSaveable { mutableStateOf(false) }
    var approved by remember { mutableStateOf(false) }

    fun n(s: String) = parseNum(s) ?: 0.0
    val result = ZakatCalculator.calculate(
        ZakatInput(
            cash = n(cash), goldGrams = n(goldGrams), silverGrams = n(silverGrams), investments = n(investments),
            receivables = n(receivables), inventory = n(inventory), debtsDue = n(debts),
            goldPrice = n(goldPrice), silverPrice = n(silverPrice), basis = basis, hawlComplete = hawl,
        ),
    )

    Page {
        Note(stringResource(R.string.zakat_sources))

        SectionTitle(stringResource(R.string.zakat_basis_title))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = basis == NisabBasis.GOLD, onClick = { basis = NisabBasis.GOLD; approved = false },
                label = { Text(stringResource(R.string.zakat_basis_gold)) })
            FilterChip(selected = basis == NisabBasis.SILVER, onClick = { basis = NisabBasis.SILVER; approved = false },
                label = { Text(stringResource(R.string.zakat_basis_silver)) })
        }

        SectionTitle(stringResource(R.string.zakat_prices_title))
        NumField(stringResource(R.string.zakat_gold_price), goldPrice, { goldPrice = it; approved = false })
        NumField(stringResource(R.string.zakat_silver_price), silverPrice, { silverPrice = it; approved = false })

        SectionTitle(stringResource(R.string.zakat_assets_title))
        NumField(stringResource(R.string.zakat_cash), cash, { cash = it; approved = false })
        NumField(stringResource(R.string.zakat_gold_grams), goldGrams, { goldGrams = it; approved = false })
        NumField(stringResource(R.string.zakat_silver_grams), silverGrams, { silverGrams = it; approved = false })
        NumField(stringResource(R.string.zakat_investments), investments, { investments = it; approved = false })
        NumField(stringResource(R.string.zakat_receivables), receivables, { receivables = it; approved = false })
        NumField(stringResource(R.string.zakat_inventory), inventory, { inventory = it; approved = false })
        NumField(stringResource(R.string.zakat_debts), debts, { debts = it; approved = false })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = hawl, onCheckedChange = { hawl = it; approved = false })
            Text(stringResource(R.string.zakat_hawl))
        }

        SectionTitle(stringResource(R.string.zakat_result_title))
        if (result == null) {
            Note(stringResource(R.string.zakat_basis_required))
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    result.lines.filter { it.amount != 0.0 || it.key == ZakatKey.NET || it.key == ZakatKey.NISAB }.forEach { line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(line.key.label()))
                            Text(formatNum(line.amount))
                        }
                        if (line.key == ZakatKey.GROSS || line.key == ZakatKey.NET) HorizontalDivider()
                    }
                }
            }
            when {
                !result.reachedNisab -> Text(stringResource(R.string.zakat_below_nisab))
                !result.hawlComplete -> Text(stringResource(R.string.zakat_no_hawl))
                else -> Text(
                    stringResource(R.string.zakat_due, formatNum(result.due)),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        ctx.container.settings.update {
                            it.copy(
                                zakatNetWealth = result.net, zakatNisab = result.nisab,
                                zakatDue = result.due, zakatDay = LocalDate.now().toEpochDay(),
                            )
                        }
                        approved = true
                    }
                }) { Text(stringResource(R.string.zakat_approve)) }
                val header = stringResource(R.string.zakat_share_header)
                val labels = result.lines.associate { it.key to stringResource(it.key.label()) }
                OutlinedButton(onClick = {
                    share(ctx, buildString {
                        appendLine(header)
                        result.lines.forEach { appendLine("${labels[it.key]}: ${formatNum(it.amount)}") }
                        appendLine(ctx.getString(R.string.zakat_due, formatNum(result.due)))
                    })
                }) { Text(stringResource(R.string.halal_ask_scholar)) }
            }
            if (approved) Note(stringResource(R.string.zakat_approved))
        }
    }
}
