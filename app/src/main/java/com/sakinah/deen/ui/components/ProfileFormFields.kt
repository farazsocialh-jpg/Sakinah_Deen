package com.sakinah.deen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sakinah.deen.profile.UserProfile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Gender values stored on [UserProfile]; labels are the only user-facing part. */
val GenderValues = listOf("MALE" to "Male", "FEMALE" to "Female")

/**
 * Gender picker shared by the profile-completion step and the profile screen. The stored value
 * mirrors `PrayerGuideGender`, so the Visual Salah Guide follows the same choice.
 */
@Composable
fun GenderSelector(
    gender: String,
    onGenderChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Gender",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GenderValues.forEach { (value, label) ->
                FilterChip(
                    selected = gender == value,
                    onClick = { onGenderChange(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}

/**
 * Date-of-birth field storing an ISO `YYYY-MM-DD` string and showing the derived age, which is
 * always recomputed so it cannot go stale.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateOfBirthField(
    dateOfBirth: String,
    onDateChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val age = remember(dateOfBirth) { UserProfile(dateOfBirth = dateOfBirth).ageYears() }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Date of birth",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            val formatted = formatIsoDate(dateOfBirth)
            Text(
                when {
                    formatted == null -> "Set date of birth"
                    age != null -> "$formatted · $age years old"
                    else -> formatted
                }
            )
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = isoToUtcMillis(dateOfBirth),
            selectableDates = PastOnlySelectableDates
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onDateChange(utcMillisToIsoDate(it)) }
                        showPicker = false
                    }
                ) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

/** Birthdays can only be today or earlier. */
@OptIn(ExperimentalMaterial3Api::class)
private object PastOnlySelectableDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        utcTimeMillis <= System.currentTimeMillis()

    override fun isSelectableYear(year: Int): Boolean =
        year <= LocalDate.now().year
}

internal fun isoToUtcMillis(iso: String): Long? = runCatching {
    LocalDate.parse(iso.trim()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}.getOrNull()

internal fun utcMillisToIsoDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

/** "12 March 1994", or null when the value is blank/unparseable. */
internal fun formatIsoDate(iso: String): String? = runCatching {
    LocalDate.parse(iso.trim())
        .format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
}.getOrNull()
