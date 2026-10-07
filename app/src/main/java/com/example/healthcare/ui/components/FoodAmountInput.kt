package com.example.healthcare.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.healthcare.domain.FoodAmountPolicy
import com.example.healthcare.domain.FoodAmountUnit
import com.example.healthcare.domain.RecordedAmountSnapshot

/** The same quantity/unit control for a verified food and a stored-record snapshot. */
@Composable
fun FoodAmountInput(amount: String, unit: String, choices: List<FoodAmountUnit>,
    onAmount: (String) -> Unit, onUnit: (String) -> Unit, enabled: Boolean = true, error: String? = null) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().testTag("food-amount-input")) {
        Text("먹은 양", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = amount, onValueChange = onAmount, label = { Text("수량") },
                enabled = enabled, singleLine = true, isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f).testTag("food-amount-quantity"))
            Box(Modifier.widthIn(min = 88.dp, max = 126.dp)) {
                OutlinedButton(onClick = { expanded = true }, enabled = enabled && choices.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 56.dp).testTag("food-amount-unit")) { Text("$unit ▾") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    choices.forEach { choice ->
                        DropdownMenuItem(text = { Text(choice.unit) }, onClick = { expanded = false; onUnit(choice.unit) },
                            modifier = Modifier.testTag("food-amount-choice-${choice.unit}"))
                    }
                }
            }
        }
        val step = if (unit in setOf("g", "ml")) 10.0 else 0.5
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { FoodAmountPolicy.parseAmount(amount)?.let { if (it > step) onAmount(RecordedAmountSnapshot.format(it - step)) } },
                enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("food-amount-minus")) { Text("−") }
            OutlinedButton(onClick = { onAmount(RecordedAmountSnapshot.format((FoodAmountPolicy.parseAmount(amount) ?: 0.0) + step)) },
                enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("food-amount-plus")) { Text("+") }
        }
        choices.firstOrNull { it.unit == unit }?.let { Text(it.evidence.substringBefore("https://").trim().trimEnd('·').trim(),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
