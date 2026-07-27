package dev.sivarj.assistant.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sivarj.assistant.AssistantApp
import dev.sivarj.assistant.ai.EnrichResult
import dev.sivarj.assistant.domain.streakHistory
import dev.sivarj.assistant.domain.streakSeries
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val CHART_WINDOW_DAYS = 42 // 6 weeks
private const val GRID_WEEKS = 8

/**
 * Detail view for one habit: streak stats, a 6-week trend chart, an 8-week
 * tappable check-in grid (backfill), and an LLM motivation button.
 */
@Composable
fun HabitDetailContent(
    item: HabitWithStreak,
    onToggleDay: (Long) -> Unit,
    onDescriptionChange: (String) -> Unit,
) {
    val today = LocalDate.now().toEpochDay()
    var motivation by remember { mutableStateOf<String?>(null) }
    var motivating by remember { mutableStateOf(false) }
    var description by remember(item.habit.id) { mutableStateOf(item.habit.description) }
    val scope = rememberCoroutineScope()
    val app = LocalContext.current.applicationContext as AssistantApp

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(item.habit.name, style = MaterialTheme.typography.titleLarge)
        Text(
            "Current streak ${item.streak.current} days · best ${item.streak.longest} · " +
                "${item.checkinDays.size} total check-ins",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        androidx.compose.material3.OutlinedTextField(
            value = description,
            onValueChange = {
                description = it
                onDescriptionChange(it)
            },
            label = { Text("What & why (helps AI motivate you)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        // --- Trend chart ---
        Text("Streak trend — last 6 weeks", style = MaterialTheme.typography.titleSmall)
        StreakTrendChart(
            series = streakSeries(item.checkinDays, today, CHART_WINDOW_DAYS),
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        )

        // --- Backfill grid ---
        Text("Check-ins — tap a day to toggle", style = MaterialTheme.typography.titleSmall)
        CheckinGrid(
            checkinDays = item.checkinDays,
            today = today,
            onToggleDay = onToggleDay,
        )

        // --- Motivation ---
        TextButton(
            enabled = !motivating,
            onClick = {
                motivating = true
                motivation = null
                scope.launch {
                    val history = streakHistory(item.checkinDays)
                    val stats = buildString {
                        appendLine("Habit: ${item.habit.name}")
                        if (description.isNotBlank()) {
                            appendLine("What this habit is about: $description")
                        }
                        appendLine("Current streak: ${item.streak.current} days")
                        appendLine("Longest streak ever: ${item.streak.longest} days")
                        appendLine("Total check-ins: ${item.checkinDays.size}")
                        appendLine("All streak lengths so far (oldest first): ${history.joinToString(", ")}")
                        appendLine("Checked in today: ${item.streak.checkedToday}")
                    }
                    when (val r = app.enrichmentService.motivate(stats)) {
                        is EnrichResult.Success -> motivation = r.text
                        is EnrichResult.Failure -> motivation = "⚠ ${r.error}"
                    }
                    motivating = false
                }
            },
        ) {
            if (motivating) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(6.dp))
                Text("Thinking…")
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Motivate me")
            }
        }
        if (motivation != null) {
            Card {
                Text(
                    motivation!!,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Single-series bar chart of running streak length per day.
 * Marks in the primary color with rounded tops and square baselines; hairline
 * baseline in a recessive outline tone; no legend (title carries identity).
 */
@Composable
private fun StreakTrendChart(series: List<Int>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val maxValue = (series.maxOrNull() ?: 0).coerceAtLeast(1)

    Canvas(modifier) {
        val baselineY = size.height - 1.dp.toPx()
        // Hairline baseline
        drawLine(
            color = baselineColor,
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = 1.dp.toPx(),
        )
        if (series.isEmpty()) return@Canvas

        val gap = 2.dp.toPx()
        val slot = size.width / series.size
        val barWidth = (slot - gap).coerceAtMost(24.dp.toPx()).coerceAtLeast(1f)
        val chartHeight = baselineY - 4.dp.toPx()
        val corner = 4.dp.toPx()

        series.forEachIndexed { i, value ->
            if (value <= 0) return@forEachIndexed
            val h = (value.toFloat() / maxValue) * chartHeight
            val left = i * slot + (slot - barWidth) / 2f
            val top = baselineY - h
            // Rounded data-end (top), square at the baseline.
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left, top = top,
                        right = left + barWidth, bottom = baselineY,
                        topLeftCornerRadius = CornerRadius(corner),
                        topRightCornerRadius = CornerRadius(corner),
                        bottomLeftCornerRadius = CornerRadius.Zero,
                        bottomRightCornerRadius = CornerRadius.Zero,
                    )
                )
            }
            drawPath(path, color = barColor)
        }
    }
}

/**
 * 8-week grid, one row per week (Mon..Sun), most recent week last.
 * Checked days are filled; today has an outline; future days are blank.
 */
@Composable
private fun CheckinGrid(
    checkinDays: Set<Long>,
    today: Long,
    onToggleDay: (Long) -> Unit,
) {
    val todayDate = LocalDate.ofEpochDay(today)
    // Start from the Monday GRID_WEEKS-1 weeks before this week's Monday.
    val thisMonday = todayDate.with(DayOfWeek.MONDAY)
    val firstMonday = thisMonday.minusWeeks((GRID_WEEKS - 1).toLong())

    val filled = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val monthFormat = remember { DateTimeFormatter.ofPattern("MMM d") }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Day-of-week header
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(1.6f)) {}
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                Text(
                    d,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        (0 until GRID_WEEKS).forEach { week ->
            val monday = firstMonday.plusWeeks(week.toLong())
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    monday.format(monthFormat),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.6f),
                )
                (0 until 7).forEach { dow ->
                    val date = monday.plusDays(dow.toLong())
                    val epochDay = date.toEpochDay()
                    val isFuture = epochDay > today
                    val isChecked = epochDay in checkinDays
                    val isToday = epochDay == today
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when {
                                    isFuture -> Color.Transparent
                                    isChecked -> filled
                                    else -> empty
                                }
                            )
                            .let { m ->
                                if (isToday) m.background(
                                    if (isChecked) filled else empty,
                                    RoundedCornerShape(4.dp),
                                ) else m
                            }
                            .clickable(enabled = !isFuture) { onToggleDay(epochDay) },
                    )
                }
            }
        }
    }
}
