package com.sakinah.deen.ui.screens

import com.sakinah.deen.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.components.SegmentedPillBar

import com.sakinah.deen.ui.viewmodel.WealthUiIntent
import com.sakinah.deen.ui.viewmodel.WealthViewModel
import com.sakinah.deen.util.ZakatPdfExporter
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Honest freshness label: an unset timestamp means the built-in estimate is in use. */
internal fun formatNisabTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Nisab rates: built-in estimate · update when online"
    val date = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
    return "Nisab rates last updated: $date"
}

private fun formatFieldInit(value: Float): String {
    if (value == 0f) return "0"
    val asInt = value.toInt()
    return if (value == asInt.toFloat()) asInt.toString() else value.toString()
}

@Composable
fun WealthScreen(
    modifier: Modifier = Modifier
) {
    val viewModel: WealthViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // String-backed input buffers prevent decimal digits from shifting while typing.
    var cashText by remember { mutableStateOf(formatFieldInit(state.cashInBank)) }
    var goldText by remember { mutableStateOf(formatFieldInit(state.goldHeld)) }
    var silverText by remember { mutableStateOf(formatFieldInit(state.silverHeld)) }
    var inventoryText by remember { mutableStateOf(formatFieldInit(state.tradeInventory)) }
    var stocksText by remember { mutableStateOf(formatFieldInit(state.stocksValue)) }
    var debtsText by remember { mutableStateOf(formatFieldInit(state.shortTermDebts)) }

    // A cleared field is an explicit zero, and any balance the view model changes itself
    // (restored values, reset) re-syncs the buffers so the UI never shows a stale amount.
    LaunchedEffect(
        state.cashInBank,
        state.goldHeld,
        state.silverHeld,
        state.tradeInventory,
        state.stocksValue,
        state.shortTermDebts
    ) {
        if (amountFromInput(cashText) != state.cashInBank) cashText = formatFieldInit(state.cashInBank)
        if (amountFromInput(goldText) != state.goldHeld) goldText = formatFieldInit(state.goldHeld)
        if (amountFromInput(silverText) != state.silverHeld) silverText = formatFieldInit(state.silverHeld)
        if (amountFromInput(inventoryText) != state.tradeInventory) inventoryText = formatFieldInit(state.tradeInventory)
        if (amountFromInput(stocksText) != state.stocksValue) stocksText = formatFieldInit(state.stocksValue)
        if (amountFromInput(debtsText) != state.shortTermDebts) debtsText = formatFieldInit(state.shortTermDebts)
    }

    val ratesUpdatedLabel = remember(state.nisabLastUpdatedEpochMillis) {
        formatNisabTimestamp(state.nisabLastUpdatedEpochMillis)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // The nav host already applied the system-bar insets; keep this Scaffold from
        // adding them a second time.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.wealth_hub_title),
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.wealth_currency_nisab, state.currencyCode, state.rateSource) +
                            (if (state.nisabLastUpdatedEpochMillis > 0L) "  •  ${ratesUpdatedLabel}" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.wealth_zakat_aid),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // 1. Nisab Valuation Benchmark Card
            SakinahCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.wealth_nisab_benchmark),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    SegmentedPillBar(
                        options = listOf("Gold (85g)", "Silver (595g)"),
                        selectedIndex = if (state.isGoldStandard) 0 else 1,
                        onSelect = { idx ->
                            viewModel.onIntent(WealthUiIntent.SetGoldStandard(idx == 0))
                        }
                    )

                    // Currency is part of the Nisab identity, not a bare number: amounts are
                    // always rendered with the currency that priced them.
                    val currencyOptions = remember(state.currencyCode) {
                        listOf(state.currencyCode) +
                            listOf("QAR", "USD", "SAR", "AED", "GBP", "EUR")
                                .filter { it != state.currencyCode }
                    }
                    SegmentedPillBar(
                        options = currencyOptions,
                        selectedIndex = 0,
                        onSelect = { idx ->
                            viewModel.onIntent(WealthUiIntent.SetCurrency(currencyOptions[idx]))
                        }
                    )

                    Text(
                        text = stringResource(
                            R.string.wealth_nisab_threshold,
                            // The threshold is only meaningful in the currency the metal rate
                            // is quoted in; it is never silently converted to another.
                            formatMoney(state.nisabThreshold, state.nisabRateCurrencyCode)
                        ),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = stringResource(
                            R.string.wealth_nisab_rate_currency,
                            state.nisabRateCurrencyCode,
                            if (state.isNisabRateFallback) "built-in estimate" else "live quote"
                        ),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    state.currencyChangeNotice?.let { notice ->
                        Text(
                            text = notice,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable {
                                viewModel.onIntent(WealthUiIntent.DismissCurrencyChangeNotice)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ratesUpdatedLabel,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        if (state.isRefreshingRates) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.wealth_refreshing), fontSize = 11.sp)
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.wealth_refresh_rates),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    viewModel.onIntent(WealthUiIntent.RefreshNisabRates)
                                }
                            )
                        }
                    }

                    state.errorMessage?.let { err ->
                        Text(
                            text = err,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (state.isEligible) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            1.dp,
                            if (state.isEligible) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (state.isEligible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.isEligible) {
                                    stringResource(R.string.wealth_eligible)
                                } else {
                                    stringResource(R.string.wealth_below_threshold)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (state.isEligible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Multi-Asset Balance Sheet Inputs (All 6 fields visible)
            SakinahCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.wealth_balance_sheet),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    ZakatNumberField(
                        value = cashText,
                        label = stringResource(R.string.wealth_cash_label, state.currencyCode),
                        onValueChange = { raw ->
                            cashText = raw
                            // A blank field means zero (amountFromInput maps it) rather than
                            // leaving the previous amount in the view model.
                            amountFromInput(raw)?.let { viewModel.onIntent(WealthUiIntent.UpdateCash(it)) }
                        }
                    )
                    ZakatNumberField(
                        value = goldText,
                        label = stringResource(R.string.wealth_gold_label, state.currencyCode),
                        onValueChange = { raw ->
                            goldText = raw
                            amountFromInput(raw)?.let { viewModel.onIntent(WealthUiIntent.UpdateGoldHeld(it)) }
                        }
                    )
                    ZakatNumberField(
                        value = silverText,
                        label = stringResource(R.string.wealth_silver_label, state.currencyCode),
                        onValueChange = { raw ->
                            silverText = raw
                            amountFromInput(raw)?.let { viewModel.onIntent(WealthUiIntent.UpdateSilverHeld(it)) }
                        }
                    )
                    ZakatNumberField(
                        value = inventoryText,
                        label = stringResource(R.string.wealth_inventory_label, state.currencyCode),
                        onValueChange = { raw ->
                            inventoryText = raw
                            amountFromInput(raw)?.let { viewModel.onIntent(WealthUiIntent.UpdateTradeInventory(it)) }
                        }
                    )
                    ZakatNumberField(
                        value = stocksText,
                        label = stringResource(R.string.wealth_stocks_label, state.currencyCode),
                        onValueChange = { raw ->
                            stocksText = raw
                            amountFromInput(raw)?.let { viewModel.onIntent(WealthUiIntent.UpdateStocksValue(it)) }
                        }
                    )
                    ZakatNumberField(
                        value = debtsText,
                        label = stringResource(R.string.wealth_debts_label, state.currencyCode),
                        onValueChange = { raw ->
                            debtsText = raw
                            amountFromInput(raw)?.let { viewModel.onIntent(WealthUiIntent.UpdateDebts(it)) }
                        }
                    )
                }
            }

            // 3. Zakat Summary Breakdown Card (Explicit Deep Emerald Surface)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.wealth_payable_summary),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatMoney(state.zakatPayable, state.currencyCode),
                        fontFamily = FontFamily.Serif,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(
                            R.string.wealth_net_eligible,
                            formatMoney(state.netEligible, state.currencyCode)
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val result = ZakatPdfExporter.exportAndShare(context, state)
                                    if (result.isSuccess) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.wealth_pdf_ready))
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.wealth_pdf_failed, result.exceptionOrNull()?.message ?: "unknown error")
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.wealth_export_pdf), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                cashText = "0"
                                goldText = "0"
                                silverText = "0"
                                inventoryText = "0"
                                stocksText = "0"
                                debtsText = "0"
                                viewModel.onIntent(WealthUiIntent.ResetBalances)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.wealth_reset), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 4. Daily Micro-Sadaqah Jar (DB-backed via WealthViewModel)
            val jarOutlineColor = MaterialTheme.colorScheme.primary
            SakinahCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.wealth_sadaqah_jar),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = stringResource(R.string.wealth_sadaqah_goal, state.currencyCode, state.sadaqahGoal.toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val jarFillColor = MaterialTheme.colorScheme.primary
                        Canvas(modifier = Modifier.size(width = 60.dp, height = 80.dp)) {
                            val stroke = 4f
                            val w = size.width
                            val h = size.height
                            val fillPct = if (state.sadaqahGoal > 0f) {
                                (state.sadaqahJarCollected / state.sadaqahGoal).coerceIn(0f, 1f)
                            } else 0f
                            val fillH = (h - 10f) * fillPct

                            drawRoundRect(
                                color = jarFillColor,
                                topLeft = Offset(4f, h - 4f - fillH),
                                size = Size(w - 8f, fillH),
                                cornerRadius = CornerRadius(8f, 8f)
                            )
                            drawRoundRect(
                                color = jarOutlineColor,
                                topLeft = Offset(2f, 8f),
                                size = Size(w - 4f, h - 10f),
                                cornerRadius = CornerRadius(12f, 12f),
                                style = Stroke(width = stroke)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(
                                    R.string.wealth_sadaqah_collected,
                                    formatMoney(state.sadaqahJarCollected, state.currencyCode)
                                ),
                                fontFamily = FontFamily.Serif,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val pct = if (state.sadaqahGoal > 0f) {
                                ((state.sadaqahJarCollected / state.sadaqahGoal) * 100).toInt()
                            } else 0
                            Text(
                                text = stringResource(R.string.wealth_sadaqah_progress, pct),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1f, 5f, 10f).forEach { amount ->
                            val label = stringResource(
                                R.string.wealth_sadaqah_add,
                                formatMoney(amount, state.currencyCode)
                            )
                            Button(
                                onClick = { viewModel.onIntent(WealthUiIntent.AddMicroSadaqah(amount)) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(label, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZakatNumberField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val sanitized = input.filter { it.isDigit() || it == '.' || it == '-' }
            if (sanitized.count { it == '.' } <= 1) {
                onValueChange(sanitized)
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = validationError(value) != null,
        supportingText = validationError(value)?.let { error ->
            { Text(error, fontSize = 11.sp) }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    )
}

private fun validationError(raw: String): String? = when {
    raw.isBlank() -> null
    raw.contains('-') -> "Amount cannot be negative"
    raw.count { it == '.' } > 1 -> "Use at most one decimal point"
    raw.length > 18 -> "Amount is too large"
    raw.toDoubleOrNull() == null -> "Enter a valid amount"
    raw.toDouble() > 1_000_000_000_000.0 -> "Amount must be 1 trillion or less"
    else -> null
}

/** "QAR 1,234.00" without assuming a dollar sign anywhere. */
internal fun formatMoney(amount: Number, currencyCode: String): String =
    "${currencyCode.trim().uppercase()} ${String.format(Locale.US, "%,.2f", amount.toDouble())}"

private fun amountFromInput(raw: String): Float? {
    if (raw.isBlank() || raw == "." || raw == "-") return 0f
    if (validationError(raw) != null) return null
    return raw.toDouble().toFloat()
}
