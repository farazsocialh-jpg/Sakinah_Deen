package com.sakinah.deen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakinah.deen.ui.theme.EmeraldDeep
import com.sakinah.deen.ui.theme.MintGreen

@Composable
fun WealthScreen(
    modifier: Modifier = Modifier
) {
    var isGoldStandard by remember { mutableStateOf(true) }
    val goldPricePerGram = 88.40f
    val silverPricePerGram = 1.15f
    val nisabThreshold = if (isGoldStandard) 85f * goldPricePerGram else 595f * silverPricePerGram

    var cashInBank by remember { mutableFloatStateOf(14200f) }
    var goldHeld by remember { mutableFloatStateOf(4500f) }
    var silverHeld by remember { mutableFloatStateOf(320f) }
    var tradeInventory by remember { mutableFloatStateOf(0f) }
    var stocksValue by remember { mutableFloatStateOf(6800f) }
    var shortTermDebts by remember { mutableFloatStateOf(1800f) }

    var sadaqahJarCollected by remember { mutableFloatStateOf(76f) }
    val sadaqahGoal = 100f

    val grossAssets = cashInBank + goldHeld + silverHeld + tradeInventory + stocksValue
    val netEligible = (grossAssets - shortTermDebts).coerceAtLeast(0f)
    val isEligible = netEligible >= nisabThreshold
    val zakatPayable = if (isEligible) netEligible * 0.025f else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Text(
            text = "Māl, Zakat & Sadaqah Hub",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = EmeraldDeep
        )

        // 1. Nisab Live Valuation Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nisab Valuation Benchmark",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = EmeraldDeep
                    )
                    Row {
                        Button(
                            onClick = { isGoldStandard = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isGoldStandard) EmeraldDeep else Color.LightGray.copy(alpha = 0.3f),
                                contentColor = if (isGoldStandard) Color.White else EmeraldDeep
                            )
                        ) {
                            Text("Gold (85g)", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = { isGoldStandard = false },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isGoldStandard) EmeraldDeep else Color.LightGray.copy(alpha = 0.3f),
                                contentColor = if (!isGoldStandard) Color.White else EmeraldDeep
                            )
                        ) {
                            Text("Silver (595g)", fontSize = 11.sp)
                        }
                    }
                }

                Text(
                    text = "Current Nisab Threshold: $${String.format("%.0f", nisabThreshold)} USD",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EmeraldDeep
                )

                // Status Banner
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isEligible) MintGreen.copy(alpha = 0.15f) else Color.LightGray.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, if (isEligible) MintGreen else Color.LightGray)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isEligible) EmeraldDeep else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEligible) "Eligible for Zakat (Wealth exceeds threshold)" else "Wealth is currently below Nisab threshold",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isEligible) EmeraldDeep else Color.DarkGray
                        )
                    }
                }
            }
        }

        // 2. Multi-Asset Balance Sheet Inputs
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Multi-Asset Balance Sheet",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = EmeraldDeep
                )

                OutlinedTextField(
                    value = cashInBank.toString(),
                    onValueChange = { cashInBank = it.toFloatOrNull() ?: 0f },
                    label = { Text("Cash in Hand & Bank Accounts ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = goldHeld.toString(),
                    onValueChange = { goldHeld = it.toFloatOrNull() ?: 0f },
                    label = { Text("Gold Value Held ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = stocksValue.toString(),
                    onValueChange = { stocksValue = it.toFloatOrNull() ?: 0f },
                    label = { Text("Stocks / Liquid Portfolios ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = shortTermDebts.toString(),
                    onValueChange = { shortTermDebts = it.toFloatOrNull() ?: 0f },
                    label = { Text("Deductible Immediate Debts ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }

        // 3. Zakat Summary Breakdown Card
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
                    text = "$${String.format("%.2f", zakatPayable)}",
                    fontFamily = FontFamily.Serif,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Net Zakat-Eligible Wealth: $${String.format("%.0f", netEligible)} USD",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { /* Export PDF */ },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export PDF", color = EmeraldDeep, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            cashInBank = 0f
                            goldHeld = 0f
                            stocksValue = 0f
                            shortTermDebts = 0f
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

        // 4. Daily Micro-Sadaqah Jar
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = EmeraldDeep)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Daily Micro-Sadaqah Jar",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = EmeraldDeep
                        )
                    }
                    Text(
                        text = "Goal: $${sadaqahGoal.toInt()} / mo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // Jar Graphic with Fill Progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Canvas(modifier = Modifier.size(width = 60.dp, height = 80.dp)) {
                        val stroke = 4f
                        val w = size.width
                        val h = size.height
                        val fillPct = (sadaqahJarCollected / sadaqahGoal).coerceIn(0f, 1f)
                        val fillH = (h - 10f) * fillPct

                        // Jar fill
                        drawRoundRect(
                            color = Color(0xFF10B981),
                            topLeft = Offset(4f, h - 4f - fillH),
                            size = Size(w - 8f, fillH),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        // Jar border
                        drawRoundRect(
                            color = Color(0xFF0B3B2C),
                            topLeft = Offset(2f, 8f),
                            size = Size(w - 4f, h - 10f),
                            cornerRadius = CornerRadius(12f, 12f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$${sadaqahJarCollected.toInt()} Collected",
                            fontFamily = FontFamily.Serif,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldDeep
                        )
                        Text(
                            text = "${((sadaqahJarCollected / sadaqahGoal) * 100).toInt()}% of monthly charity target",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // Quick buttons: +$1, +$5
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { sadaqahJarCollected += 1f },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+$1 Sadaqah", color = EmeraldDeep, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Button(
                        onClick = { sadaqahJarCollected += 5f },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = EmeraldDeep, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+$5 Sadaqah", color = EmeraldDeep, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
