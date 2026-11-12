package com.sakinah.deen.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sakinah.deen.ui.theme.EmeraldDeep

/** A Home city choice with the real coordinates Safar distance is measured from. */
data class HomeCity(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

val homeCityPresets: List<HomeCity> = listOf(
    HomeCity("Doha, Qatar", 25.2854, 51.5310),
    HomeCity("Makkah, Saudi Arabia", 21.4225, 39.8262),
    HomeCity("Madinah, Saudi Arabia", 24.4686, 39.6142),
    HomeCity("Riyadh, Saudi Arabia", 24.7136, 46.6753),
    HomeCity("Dubai, UAE", 25.2048, 55.2708),
    HomeCity("Abu Dhabi, UAE", 24.4539, 54.3773),
    HomeCity("Karachi, Pakistan", 24.8607, 67.0011),
    HomeCity("Lahore, Pakistan", 31.5204, 74.3587),
    HomeCity("Islamabad, Pakistan", 33.6844, 73.0479),
    HomeCity("Istanbul, Türkiye", 41.0082, 28.9784),
    HomeCity("Cairo, Egypt", 30.0444, 31.2357),
    HomeCity("Kuala Lumpur, Malaysia", 3.1390, 101.6869),
    HomeCity("Jakarta, Indonesia", -6.2088, 106.8456),
    HomeCity("Dhaka, Bangladesh", 23.8103, 90.4125),
    HomeCity("London, UK", 51.5074, -0.1278),
    HomeCity("Birmingham, UK", 52.4862, -1.8904),
    HomeCity("New York, USA", 40.7128, -74.0060),
    HomeCity("Toronto, Canada", 43.6532, -79.3832),
    HomeCity("Sydney, Australia", -33.8688, 151.2093),
    HomeCity("Cape Town, South Africa", -33.9249, 18.4241)
)

/**
 * Searchable city picker shared by Settings and Travel so both save a Home the
 * same way: a real city name plus its real coordinates.
 */
@Composable
fun HomeLocationCityPickerDialog(
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
                    text = "Choose Home City",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = EmeraldDeep
                )
                Text(
                    text = "Safar (Qasr) is measured from Home.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(results) { city ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = EmeraldDeep.copy(alpha = 0.05f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCitySelected(city) }
                            ) {
                                Text(
                                    text = city.name,
                                    modifier = Modifier.padding(12.dp),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = EmeraldDeep
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}
