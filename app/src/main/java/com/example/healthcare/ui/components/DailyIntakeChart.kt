package com.example.healthcare.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.healthcare.R
import com.example.healthcare.domain.DailyIntakeStatus
import com.example.healthcare.domain.DailyIntakeSummary
import com.example.healthcare.ui.theme.Butter
import com.example.healthcare.ui.theme.Coral
import com.example.healthcare.ui.theme.EditorialMuted
import com.example.healthcare.ui.theme.Lime
import com.example.healthcare.ui.theme.MintDeep
import com.example.healthcare.ui.theme.Sky
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DailyIntakeChart(days: List<DailyIntakeSummary>, compact: Boolean = false) {
    if (days.isEmpty() || days.none { it.recordCount > 0 }) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(R.string.timeline_empty), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.timeline_empty_policy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var selectedIndex by remember(days) {
        mutableIntStateOf(days.indexOfLast { it.recordCount > 0 }.coerceAtLeast(0))
    }
    val selected = days[selectedIndex.coerceIn(days.indices)]
    val maxCalories = days.maxOf { maxOf(it.intakeCalories, it.targetCalories ?: 0) }
        .coerceAtLeast(100)
    val graphMax = maxCalories * 1.12f
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val spoken = days.joinToString(". ") { day ->
        "${day.date.monthValue}월 ${day.date.dayOfMonth}일 " +
            if (day.recordCount == 0) "기록 없음" else
                "${day.intakeCalories} kcal, 목표 ${day.targetCalories?.toString() ?: "미설정"} kcal"
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${format(maxCalories)} kcal", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.timeline_goal_legend), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(
            Modifier.fillMaxWidth().height(if (compact) 112.dp else 176.dp)
                .testTag("intake-chart")
                .pointerInput(days) {
                    detectTapGestures { tap ->
                        selectedIndex = ((tap.x / size.width) * days.size).toInt()
                            .coerceIn(days.indices)
                    }
                }
                .semantics { contentDescription = spoken }
        ) {
            val slot = size.width / days.size
            val bottom = size.height - 2.dp.toPx()
            drawLine(axisColor, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
            days.forEachIndexed { index, day ->
                val centerX = slot * (index + 0.5f)
                val barWidth = (slot * 0.58f).coerceAtLeast(2.dp.toPx())
                if (day.recordCount > 0) {
                    val barHeight = ((day.intakeCalories / graphMax) * bottom).coerceAtLeast(2.dp.toPx())
                    drawRect(
                        color = statusColor(day.status),
                        topLeft = Offset(centerX - barWidth / 2f, bottom - barHeight),
                        size = Size(barWidth, barHeight)
                    )
                }
                day.targetCalories?.let { target ->
                    val y = bottom - (target / graphMax) * bottom
                    drawLine(goalColor, Offset(slot * index, y), Offset(slot * (index + 1), y),
                        1.4.dp.toPx())
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { index, day ->
                val show = index == 0 || index == days.lastIndex ||
                    (days.size <= 7 && index == days.size / 2) ||
                    (days.size > 7 && index % 7 == 0)
                if (show) {
                    Text(day.date.format(DateTimeFormatter.ofPattern("M/d")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val selectedText = if (selected.recordCount == 0) {
            "${selected.date.monthValue}/${selected.date.dayOfMonth} · ${stringResource(R.string.calendar_no_record)}"
        } else {
            "${selected.date.monthValue}/${selected.date.dayOfMonth} · ${format(selected.intakeCalories)} kcal" +
                (selected.targetCalories?.let { " / ${stringResource(R.string.timeline_goal_legend)} ${format(it)} kcal" }
                    ?: " / ${stringResource(R.string.status_no_target_title)}")
        }
        Text(selectedText, style = MaterialTheme.typography.bodyMedium)
        if (!compact) {
            ChartLegend()
            Text(stringResource(R.string.timeline_empty_policy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ChartLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendItem(Coral, stringResource(R.string.timeline_over_legend))
            LegendItem(Sky, stringResource(R.string.timeline_balanced_legend))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendItem(Lime, stringResource(R.string.timeline_under_legend))
            LegendItem(Butter, stringResource(R.string.timeline_warning_legend))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendItem(MintDeep, stringResource(R.string.timeline_no_target_legend))
            LegendItem(EditorialMuted, stringResource(R.string.timeline_goal_legend))
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.padding(top = 5.dp).size(9.dp).background(color, MaterialTheme.shapes.extraSmall))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun statusColor(status: DailyIntakeStatus): Color = when (status) {
    DailyIntakeStatus.OVER -> Coral
    DailyIntakeStatus.BALANCED -> Sky
    DailyIntakeStatus.UNDER -> Lime
    DailyIntakeStatus.BELOW_BMR -> Butter
    DailyIntakeStatus.NO_TARGET -> MintDeep
    DailyIntakeStatus.NO_RECORD -> Color.Transparent
}

private fun format(value: Int): String = NumberFormat.getIntegerInstance(Locale.KOREA).format(value)
