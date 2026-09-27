package com.example.healthcare.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.healthcare.R
import com.example.healthcare.domain.DailyIntakeStatus
import com.example.healthcare.domain.DailyIntakeSummary
import com.example.healthcare.ui.theme.ButterSoft
import com.example.healthcare.ui.theme.CoralSoft
import com.example.healthcare.ui.theme.EditorialText
import com.example.healthcare.ui.theme.LimeSoft
import com.example.healthcare.ui.theme.PaperStrong
import com.example.healthcare.ui.theme.SkySoft
import com.example.healthcare.ui.theme.HealthCareTheme
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

@Composable
fun DailyIntakeStatusCard(
    summary: DailyIntakeSummary,
    streak: Int,
    isToday: Boolean,
    onOpenSettings: () -> Unit,
    onAddRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = summary.status
    val container = when (status) {
        DailyIntakeStatus.OVER -> CoralSoft
        DailyIntakeStatus.BALANCED -> SkySoft
        DailyIntakeStatus.UNDER -> LimeSoft
        DailyIntakeStatus.BELOW_BMR -> ButterSoft
        DailyIntakeStatus.NO_RECORD, DailyIntakeStatus.NO_TARGET -> PaperStrong
    }
    val difference = summary.differenceCalories ?: 0
    val title = when (status) {
        DailyIntakeStatus.OVER -> R.string.status_over_title
        DailyIntakeStatus.BALANCED -> R.string.status_balanced_title
        DailyIntakeStatus.UNDER -> R.string.status_under_title
        DailyIntakeStatus.BELOW_BMR -> R.string.status_warning_title
        DailyIntakeStatus.NO_RECORD -> R.string.status_no_record_title
        DailyIntakeStatus.NO_TARGET -> R.string.status_no_target_title
    }
    val detail = when (status) {
        DailyIntakeStatus.OVER -> stringResource(R.string.status_over_message, number(difference))
        DailyIntakeStatus.BALANCED -> stringResource(R.string.status_balanced_message)
        DailyIntakeStatus.UNDER -> stringResource(R.string.status_under_message, number(-difference))
        DailyIntakeStatus.BELOW_BMR -> stringResource(
            R.string.status_warning_message,
            number((summary.bmrCalories ?: 0) - summary.intakeCalories)
        )
        DailyIntakeStatus.NO_RECORD -> stringResource(R.string.status_no_record_message)
        DailyIntakeStatus.NO_TARGET -> stringResource(R.string.status_no_target_message)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(if (isToday) R.string.today_status_title else R.string.selected_day_status_title),
                style = MaterialTheme.typography.labelLarge,
                color = EditorialText
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleLarge,
                        color = EditorialText, modifier = Modifier.semantics { heading() })
                    Text(detail, style = MaterialTheme.typography.bodyLarge, color = EditorialText)
                    if (streak > 0) {
                        val streakText = when (status) {
                            DailyIntakeStatus.OVER -> R.string.status_over_streak
                            DailyIntakeStatus.BALANCED -> R.string.status_balanced_streak
                            DailyIntakeStatus.UNDER -> R.string.status_under_streak
                            else -> null
                        }
                        if (streakText != null) Text(stringResource(streakText, streak),
                            style = MaterialTheme.typography.labelLarge, color = EditorialText)
                    }
                }
                when (status) {
                    DailyIntakeStatus.OVER -> OverCharacterIllustration(Modifier.size(92.dp))
                    DailyIntakeStatus.BALANCED -> BalancedCharacterIllustration(Modifier.size(92.dp))
                    DailyIntakeStatus.UNDER -> SlimCharacterIllustration(Modifier.size(92.dp))
                    DailyIntakeStatus.BELOW_BMR -> WarningCharacterIllustration(Modifier.size(92.dp))
                    DailyIntakeStatus.NO_RECORD, DailyIntakeStatus.NO_TARGET -> Unit
                }
            }
            if (summary.recordCount > 0) {
                Text(stringResource(R.string.status_intake_value, number(summary.intakeCalories)) +
                    " · " + (summary.targetCalories?.let {
                        stringResource(R.string.status_target_value, number(it))
                    } ?: stringResource(R.string.status_no_target_title)),
                    style = MaterialTheme.typography.bodyMedium, color = EditorialText)
            }
            Text(stringResource(R.string.status_record_count, summary.recordCount),
                style = MaterialTheme.typography.labelMedium, color = EditorialText)
            if (status == DailyIntakeStatus.BELOW_BMR) {
                Text(stringResource(R.string.status_warning_support),
                    style = MaterialTheme.typography.bodySmall, color = EditorialText)
            }
            if (status in setOf(DailyIntakeStatus.OVER, DailyIntakeStatus.BALANCED,
                    DailyIntakeStatus.UNDER, DailyIntakeStatus.BELOW_BMR)) {
                Text(stringResource(R.string.status_visual_disclaimer),
                    style = MaterialTheme.typography.bodySmall, color = EditorialText)
            }
            if (status == DailyIntakeStatus.NO_TARGET) {
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.status_open_settings))
                }
            } else if (status == DailyIntakeStatus.NO_RECORD && isToday) {
                Button(onClick = onAddRecord, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.status_add_record))
                }
            }
        }
    }
}

private fun number(value: Int): String = NumberFormat.getIntegerInstance(Locale.KOREA).format(value)

@Preview(name = "상태 카드 · 초과 360", widthDp = 360, showBackground = true, fontScale = 1.3f)
@Composable
private fun OverStatusPreview() = StatusPreview(2300, DailyIntakeStatus.OVER)

@Preview(name = "상태 카드 · 균형", widthDp = 390, showBackground = true)
@Composable
private fun BalancedStatusPreview() = StatusPreview(1950, DailyIntakeStatus.BALANCED)

@Preview(name = "상태 카드 · 부족", widthDp = 412, showBackground = true)
@Composable
private fun UnderStatusPreview() = StatusPreview(1700, DailyIntakeStatus.UNDER)

@Preview(name = "상태 카드 · BMR 주의", widthDp = 360, showBackground = true, fontScale = 1.3f)
@Composable
private fun WarningStatusPreview() = StatusPreview(1200, DailyIntakeStatus.BELOW_BMR)

@Preview(name = "상태 카드 · 기록 없음", widthDp = 390, showBackground = true)
@Composable
private fun NoRecordStatusPreview() = StatusPreview(0, DailyIntakeStatus.NO_RECORD)

@Preview(name = "상태 카드 · 목표 없음", widthDp = 390, showBackground = true)
@Composable
private fun NoTargetStatusPreview() = StatusPreview(1400, DailyIntakeStatus.NO_TARGET)

@Composable
private fun StatusPreview(intake: Int, status: DailyIntakeStatus) {
    HealthCareTheme(darkTheme = false) {
        DailyIntakeStatusCard(
            summary = DailyIntakeSummary(
                date = LocalDate.of(2026, 9, 20),
                intakeCalories = intake,
                targetCalories = if (status == DailyIntakeStatus.NO_TARGET) null else 2000,
                bmrCalories = 1500,
                recordCount = if (status == DailyIntakeStatus.NO_RECORD) 0 else 1,
                status = status
            ),
            streak = 2,
            isToday = true,
            onOpenSettings = {},
            onAddRecord = {}
        )
    }
}
