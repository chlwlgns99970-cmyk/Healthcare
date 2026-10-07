package com.example.healthcare.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.healthcare.domain.RecordCompletion

@Composable
fun RecordCompletionScreen(completion: RecordCompletion, onHome: () -> Unit, onHistory: () -> Unit) {
    BackHandler(onBack = onHome)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
        .testTag("record-completion"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(completion.title, style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(completion.record.mealType.displayName, style = MaterialTheme.typography.titleMedium)
                Text(completion.record.foodName, style = MaterialTheme.typography.headlineSmall)
                completion.amountLabel?.let { Text(it, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("completion-amount")) }
                Text(completion.calorieLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
        Button(onClick = onHome, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .testTag("completion-home")) { Text("홈으로") }
        OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .testTag("completion-history")) { Text("기록 보기") }
    }
}
