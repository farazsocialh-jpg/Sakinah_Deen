package com.sakinah.deen.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** A Home or manual city choice with coordinates, ISO country code, and IANA timezone. */
data class HomeCity(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val countryCode: String = "",
    val timeZoneId: String = ""
)

val homeCityPresets: List<HomeCity> = listOf(
    HomeCity("Doha, Qatar", 25.2854, 51.5310, "QA", "Asia/Qatar"),
    HomeCity("Makkah, Saudi Arabia", 21.4225, 39.8262, "SA", "Asia/Riyadh"),
    HomeCity("Madinah, Saudi Arabia", 24.4686, 39.6142, "SA", "Asia/Riyadh"),
    HomeCity("Riyadh, Saudi Arabia", 24.7136, 46.6753, "SA", "Asia/Riyadh"),
    HomeCity("Dubai, UAE", 25.2048, 55.2708, "AE", "Asia/Dubai"),
    HomeCity("Abu Dhabi, UAE", 24.4539, 54.3773, "AE", "Asia/Dubai"),
    HomeCity("Karachi, Pakistan", 24.8607, 67.0011, "PK", "Asia/Karachi"),
    HomeCity("Lahore, Pakistan", 31.5204, 74.3587, "PK", "Asia/Karachi"),
    HomeCity("Islamabad, Pakistan", 33.6844, 73.0479, "PK", "Asia/Karachi"),
    HomeCity("Istanbul, Türkiye", 41.0082, 28.9784, "TR", "Europe/Istanbul"),
    HomeCity("Cairo, Egypt", 30.0444, 31.2357, "EG", "Africa/Cairo"),
    HomeCity("Kuala Lumpur, Malaysia", 3.1390, 101.6869, "MY", "Asia/Kuala_Lumpur"),
    HomeCity("Jakarta, Indonesia", -6.2088, 106.8456, "ID", "Asia/Jakarta"),
    HomeCity("Dhaka, Bangladesh", 23.8103, 90.4125, "BD", "Asia/Dhaka"),
    HomeCity("London, UK", 51.5074, -0.1278, "GB", "Europe/London"),
    HomeCity("Birmingham, UK", 52.4862, -1.8904, "GB", "Europe/London"),
    HomeCity("New York, USA", 40.7128, -74.0060, "US", "America/New_York"),
    HomeCity("Los Angeles, USA", 34.0522, -118.2437, "US", "America/Los_Angeles"),
    HomeCity("Chicago, USA", 41.8781, -87.6298, "US", "America/Chicago"),
    HomeCity("Houston, USA", 29.7604, -95.3698, "US", "America/Chicago"),
    HomeCity("Toronto, Canada", 43.6532, -79.3832, "CA", "America/Toronto"),
    HomeCity("Sydney, Australia", -33.8688, 151.2093, "AU", "Australia/Sydney"),
    HomeCity("Cape Town, South Africa", -33.9249, 18.4241, "ZA", "Africa/Johannesburg")
)

/**
 * Searchable city picker shared by Salah, Settings, and Travel so manual locations and Home
 * anchors share country codes and IANA timezones cleanly.
 */
@Composable
fun HomeLocationCityPickerDialog(
    title: String = "Choose Home City",
    subtitle: String = "Safar (Qasr) is measured from Home.",
    onUseDeviceGps: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onCitySelected: (HomeCity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) {
        val term = query.trim()
        if (term.isEmpty()) homeCityPresets
        else homeCityPresets.filter { it.name.contains(term, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search city") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (results.isEmpty()) {
                    Text(
                        text = "No matching city.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(results) { city ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCitySelected(city) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = city.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (city.timeZoneId.isNotBlank()) {
                                        Text(
                                            text = city.timeZoneId.substringAfter('/'),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (onUseDeviceGps != null) {
                        TextButton(onClick = onUseDeviceGps) {
                            Text("Use Device GPS")
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
