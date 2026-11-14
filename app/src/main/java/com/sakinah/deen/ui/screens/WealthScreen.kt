package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakinah.deen.ui.components.SakinahCard
import com.sakinah.deen.ui.components.SegmentedPillBar
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.MintGreen
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

    val ratesUpdatedLabel = remember(state.nisabLastUpdatedEpochMillis) {
        formatNisabTimestamp(state.nisabLastUpdatedEpochMillis)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
            Text(
                text = "Māl, Zakat & Sadaqah Hub",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.primary
            )

            // 1. Nisab Valuation Benchmark Card
            SakinahCard(contentPadding = 16.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Nisab Valuation Benchmark",
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

                    Text(
                        text = "Current Nisab Threshold: $${String.format(Locale.US, "%.0f", state.nisabThreshold)} USD",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

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
                                Text("Refreshing…", fontSize = 11.sp)
                            }
                        } else {
                            Text(
                                text = "Refresh rates",
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
                            MintGreen.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            1.dp,
                            if (state.isEligible) MintGreen else MaterialTheme.colorScheme.outline
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
                                    "Eligible for Zakat (Wealth exceeds threshold)"
                                } else {
                                    "Wealth is currently below Nisab threshold"
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
                        text = "Multi-Asset Balance Sheet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    ZakatNumberField(
                        value = cashText,
                        label = "Cash in Hand & Bank Accounts ($)",
                        onValueChange = { raw ->
                            cashText = raw
                            amountFromInput(raw)?.let { viewModel.onIntent(WealthUiIntent.UpdateCash(it)) }
                        }
                    )
                    ZakatNumberField(
                        value = goldText,
                        label = "Gold Value Held ($)",
                        onValueChange = { raw ->
                            goldText = raw
                            amountFromInput(raw)?.let { viewModel.onIntent(WealthUiIntent.UpdateGoldHeld(it)) }
                        }
                    )
                    ZakatNumberField(
                        value = silverText,
                        label = "Silver Value Held ($)",
                        onValueChange = { raw ->
                            silverText = raw
                            amountFromInput(raw)?.let { viewModel.onIntent(WealthUiIntent.UpdateSilverHeld(it)) }
                        }
                    )
                    ZakatNumberField(
                        value = inventoryText,
                        label = "Trade & Business Inventory ($)",
                        onValueChange = { raw ->
                            inventoryText = raw
                            amountFromInput(raw)?.let { viewModel.onIntent(WealthUiIntent.UpdateTradeInventory(it)) }
                        }
                    )
                    ZakatNumberField(
                        value = stocksText,
                        label = "Stocks / Liquid Portfolios ($)",
                        onValueChange = { raw ->
                            stocksText = raw
                            amountFromInput(raw)?.let { viewModel.onIntent(WealthUiIntent.UpdateStocksValue(it)) }
                        }
                    )
                    ZakatNumberField(
                        value = debtsText,
                        label = "Deductible Immediate Debts ($)",
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
                colors = CardDefaults.cardColors(containerColor = EmeraldDeep)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Payable Zakat Summary (2.5%)",
                        color = MintGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", state.zakatPayable)}",
                        fontFamily = FontFamily.Serif,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Net Zakat-Eligible Wealth: $${String.format(Locale.US, "%.0f", state.netEligible)} USD",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val result = ZakatPdfExporter.exportAndShare(context, state)
                                scope.launch {
                                    if (result.isSuccess) {
                                        snackbarHostState.showSnackbar("Zakat summary PDF ready to share.")
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            "Could not export PDF: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export PDF", color = EmeraldDeep, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset", color = Color.White, fontSize = 12.sp)
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
                                text = "Daily Micro-Sadaqah Jar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Goal: $${state.sadaqahGoal.toInt()} / mo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Canvas(modifier = Modifier.size(width = 60.dp, height = 80.dp)) {
                            val stroke = 4f
                            val w = size.width
                            val h = size.height
                            val fillPct = if (state.sadaqahGoal > 0f) {
                                (state.sadaqahJarCollected / state.sadaqahGoal).coerceIn(0f, 1f)
                            } else 0f
                            val fillH = (h - 10f) * fillPct

                            drawRoundRect(
                                color = Color(0xFF10B981),
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
                                text = "$${String.format(Locale.US, "%.0f", state.sadaqahJarCollected)} Collected",
                                fontFamily = FontFamily.Serif,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val pct = if (state.sadaqahGoal > 0f) {
                                ((state.sadaqahJarCollected / state.sadaqahGoal) * 100).toInt()
                            } else 0
                            Text(
                                text = "$pct% of monthly charity target",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1f to "+$1", 5f to "+$5", 10f to "+$10").forEach { (amount, label) ->
                            Button(
                                onClick = { viewModel.onIntent(WealthUiIntent.AddMicroSadaqah(amount)) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(label, color = EmeraldDeep, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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

private fun amountFromInput(raw: String): Float? {
    if (raw.isBlank() || raw == "." || raw == "-") return 0f
    if (validationError(raw) != null) return null
    return raw.toDouble().toFloat()
}
