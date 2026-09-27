package com.example.healthcare.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.healthcare.R
import com.example.healthcare.domain.DailyIntakeStatus
import com.example.healthcare.domain.DailyIntakeSummary
import com.example.healthcare.domain.DailyIntakeTimeline
import com.example.healthcare.ui.theme.ButterSoft
import com.example.healthcare.ui.theme.CoralSoft
import com.example.healthcare.ui.theme.LimeSoft
import com.example.healthcare.ui.theme.MintSoft
import com.example.healthcare.ui.theme.SkySoft
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun MonthIntakeCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    timeline: DailyIntakeTimeline,
    onDateSelected: (LocalDate) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value % 7
    val cellCount = ((leading + month.lengthOfMonth() + 6) / 7) * 7
    val dates = List(cellCount) { index ->
        (index - leading + 1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { onMonthChange(-1) }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.calendar_previous_month))
            }
            Text("${month.year}년 ${month.monthValue}월", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { onMonthChange(1) }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(R.string.calendar_next_month))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach { weekday ->
                Text(weekday, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        dates.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Surface(Modifier.weight(1f).heightIn(min = 62.dp), color = MaterialTheme.colorScheme.surface) {}
                    } else {
                        CalendarDay(date, timeline.summary(date), date == selectedDate,
                            date == LocalDate.now(), onDateSelected, Modifier.weight(1f))
                    }
                }
            }
        }
        Text(
            "${stringResource(R.string.calendar_over_symbol)} ${stringResource(R.string.timeline_over_legend)} · " +
                "${stringResource(R.string.calendar_balanced_symbol)} ${stringResource(R.string.timeline_balanced_legend)} · " +
                "${stringResource(R.string.calendar_under_symbol)} ${stringResource(R.string.timeline_under_legend)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "${stringResource(R.string.calendar_warning_symbol)} ${stringResource(R.string.timeline_warning_legend)} · " +
                "${stringResource(R.string.calendar_no_target_symbol)} ${stringResource(R.string.timeline_no_target_legend)} · " +
                stringResource(R.string.calendar_no_record),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    summary: DailyIntakeSummary,
    isSelected: Boolean,
    isToday: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier
) {
    val statusLabel = when (summary.status) {
        DailyIntakeStatus.OVER -> stringResource(R.string.timeline_over_legend)
        DailyIntakeStatus.BALANCED -> stringResource(R.string.timeline_balanced_legend)
        DailyIntakeStatus.UNDER -> stringResource(R.string.timeline_under_legend)
        DailyIntakeStatus.BELOW_BMR -> stringResource(R.string.timeline_warning_legend)
        DailyIntakeStatus.NO_TARGET -> stringResource(R.string.timeline_no_target_legend)
        DailyIntakeStatus.NO_RECORD -> stringResource(R.string.calendar_no_record)
    }
    val symbol = when (summary.status) {
        DailyIntakeStatus.OVER -> stringResource(R.string.calendar_over_symbol)
        DailyIntakeStatus.BALANCED -> stringResource(R.string.calendar_balanced_symbol)
        DailyIntakeStatus.UNDER -> stringResource(R.string.calendar_under_symbol)
        DailyIntakeStatus.BELOW_BMR -> stringResource(R.string.calendar_warning_symbol)
        DailyIntakeStatus.NO_TARGET -> stringResource(R.string.calendar_no_target_symbol)
        DailyIntakeStatus.NO_RECORD -> "·"
    }
    val background = when (summary.status) {
        DailyIntakeStatus.OVER -> CoralSoft
        DailyIntakeStatus.BALANCED -> SkySoft
        DailyIntakeStatus.UNDER -> LimeSoft
        DailyIntakeStatus.BELOW_BMR -> ButterSoft
        DailyIntakeStatus.NO_TARGET -> MintSoft
        DailyIntakeStatus.NO_RECORD -> MaterialTheme.colorScheme.surface
    }
    val description = "${date.monthValue}월 ${date.dayOfMonth}일, $statusLabel" +
        (if (isToday) ", ${stringResource(R.string.calendar_today)}" else "") +
        (if (isSelected) ", ${stringResource(R.string.calendar_selected)}" else "")
    Surface(
        onClick = { onDateSelected(date) },
        modifier = modifier.heightIn(min = 62.dp)
            .semantics { contentDescription = description; selected = isSelected },
        shape = MaterialTheme.shapes.small,
        color = background,
        border = if (isSelected || isToday) BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            MaterialTheme.colorScheme.primary
        ) else null
    ) {
        Column(Modifier.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium)
            Text(symbol, style = MaterialTheme.typography.labelMedium)
        }
    }
}
