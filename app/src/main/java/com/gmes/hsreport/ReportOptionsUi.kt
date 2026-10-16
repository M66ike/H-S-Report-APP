package com.gmes.hsreport

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SuggestionField(
    label: String,
    value: String,
    suggestions: List<String>,
    onValue: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (suggestions.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                suggestions.forEach { option ->
                    FilterChip(
                        selected = value.equals(option, ignoreCase = true),
                        onClick = { onValue(option) },
                        label = { Text(option) }
                    )
                }
            }
        }
    }
}

@Composable
fun ReportOptionsDialog(
    sectorOptions: List<String>,
    territoryOptions: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>, List<String>) -> Unit
) {
    var sectors by remember(sectorOptions) { mutableStateOf(sectorOptions.joinToString("\n")) }
    var territories by remember(territoryOptions) { mutableStateOf(territoryOptions.joinToString("\n")) }

    fun clean(value: String): List<String> = value
        .lines()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Suggestions") },
        text = {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Enter one suggestion per line. These appear as quick choices, but you can still type anything into the report.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = sectors,
                    onValueChange = { sectors = it },
                    label = { Text("Sector / Project Type") },
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
                OutlinedTextField(
                    value = territories,
                    onValueChange = { territories = it },
                    label = { Text("Territory") },
                    modifier = Modifier.fillMaxWidth().height(130.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(clean(sectors), clean(territories)) },
                colors = ButtonDefaults.buttonColors(containerColor = GmesTeal)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
