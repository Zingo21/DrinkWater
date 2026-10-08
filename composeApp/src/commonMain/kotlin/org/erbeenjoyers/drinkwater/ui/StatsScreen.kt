package org.erbeenjoyers.drinkwater.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.erbeenjoyers.drinkwater.StatsState
import org.erbeenjoyers.drinkwater.api.DayTotalDto
import org.erbeenjoyers.drinkwater.api.DrinkLogEntryDto
import org.erbeenjoyers.drinkwater.api.MAX_DAILY_GOAL_ML
import org.erbeenjoyers.drinkwater.api.MIN_DAILY_GOAL_ML
import org.erbeenjoyers.drinkwater.api.StatsDto

@Composable
fun StatsScreen(
    state: StatsState,
    onSetGoal: (goalMl: Int, onSaved: () -> Unit) -> Unit,
    onDeleteDrink: (DrinkLogEntryDto) -> Unit,
    onRetry: () -> Unit,
) {
    val stats = state.stats
    if (stats == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.loadError == null) {
                CircularProgressIndicator()
            } else {
                Text(state.loadError, color = MaterialTheme.colors.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("Try again") }
            }
        }
        return
    }

    var editingGoal by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item(key = "today") {
            Today(stats, onChangeGoal = { editingGoal = true })
        }

        item(key = "week") {
            SectionHeader("Last 7 days")
            WeekChart(stats)
        }

        item(key = "recent-header") { SectionHeader("Latest drinks") }
        if (state.recent.isEmpty()) {
            item(key = "no-drinks") {
                Text("Nothing logged yet.", style = MaterialTheme.typography.body2)
            }
        }
        items(state.recent, key = { it.id }) { entry ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${entry.amountMl} ml ${entry.drink.name}",
                        style = MaterialTheme.typography.body1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        Instant.fromEpochMilliseconds(entry.timestamp).dayAndClockTime(),
                        style = MaterialTheme.typography.caption,
                        color = mutedText(),
                    )
                }
                TextButton(onClick = { onDeleteDrink(entry) }, enabled = !state.working) { Text("Remove") }
            }
        }
    }

    if (editingGoal) {
        GoalDialog(
            currentGoalMl = stats.goalMl,
            saving = state.working,
            onSave = { goal -> onSetGoal(goal) { editingGoal = false } },
            onDismiss = { editingGoal = false },
        )
    }
}

@Composable
private fun Today(stats: StatsDto, onChangeGoal: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("Today", style = MaterialTheme.typography.subtitle2, color = MaterialTheme.colors.primary)
        Spacer(Modifier.height(4.dp))
        Text("${stats.todayMl} ml", style = MaterialTheme.typography.h3)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (stats.todayMl >= stats.goalMl) "Goal of ${stats.goalMl} ml reached" else "of ${stats.goalMl} ml",
                style = MaterialTheme.typography.body2,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onChangeGoal) { Text("Change goal") }
        }
        LinearProgressIndicator(
            progress = (stats.todayMl.toFloat() / stats.goalMl).coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            when (stats.streakDays) {
                0 -> "No streak yet. Reach your goal today to start one."
                1 -> "1 day in a row with your goal reached"
                else -> "${stats.streakDays} days in a row with your goal reached"
            },
            style = MaterialTheme.typography.body1,
        )
    }
}

private val CHART_HEIGHT = 140.dp

/**
 * One bar per day with a line at the goal. Tapping a day shows its exact total above the chart;
 * today is shown until then.
 */
@Composable
private fun WeekChart(stats: StatsDto) {
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = stats.days.firstOrNull { it.date == selectedDate } ?: stats.days.last()
    // Leave headroom above the goal line so its label never sits on top of a bar that just reached it.
    val scaleMax = maxOf(stats.goalMl, stats.days.maxOf { it.totalMl }) * 1.15f
    val goalFraction = stats.goalMl / scaleMax
    val recessive = MaterialTheme.colors.onSurface.copy(alpha = 0.25f)

    Column(Modifier.fillMaxWidth()) {
        Text(
            "${selected.label(long = true)}: ${selected.totalMl} ml",
            style = MaterialTheme.typography.body1,
        )
        Spacer(Modifier.height(12.dp))

        Box(Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
            Row(Modifier.fillMaxSize()) {
                stats.days.forEach { day ->
                    val isSelected = day.date == selected.date
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { selectedDate = day.date }
                            .semantics { contentDescription = "${day.label(long = true)}: ${day.totalMl} ml" },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight(day.totalMl / scaleMax)
                                .fillMaxWidth(0.5f)
                                .widthIn(max = 28.dp)
                                .background(
                                    MaterialTheme.colors.primary.copy(alpha = if (isSelected) 1f else 0.45f),
                                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                ),
                        )
                    }
                }
            }
            // The goal line, with its label resting on it.
            Column(Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(bottom = CHART_HEIGHT * goalFraction)) {
                Text("Goal ${stats.goalMl} ml", style = MaterialTheme.typography.caption, color = mutedText())
                Box(Modifier.fillMaxWidth().height(1.dp).background(recessive))
            }
        }
        Divider(color = recessive)

        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            stats.days.forEach { day ->
                val isSelected = day.date == selected.date
                Text(
                    day.label(long = false),
                    style = MaterialTheme.typography.caption,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colors.onSurface else mutedText(),
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** `Thu` under a bar, `Thu 8 Oct` where there is room. */
private fun DayTotalDto.label(long: Boolean): String {
    val day = LocalDate.parse(date)
    return if (long) "${day.shortWeekday()} ${day.dayAndMonth()}" else day.shortWeekday()
}

@Composable
private fun GoalDialog(currentGoalMl: Int, saving: Boolean, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(currentGoalMl.toString()) }
    val goal = text.toIntOrNull()?.takeIf { it in MIN_DAILY_GOAL_ML..MAX_DAILY_GOAL_ML }
    val save = {
        if (goal != null && !saving) onSave(goal)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily goal") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() }.take(5) },
                    label = { Text("Millilitres per day") },
                    singleLine = true,
                    isError = goal == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Between $MIN_DAILY_GOAL_ML and $MAX_DAILY_GOAL_ML ml",
                    style = MaterialTheme.typography.caption,
                    color = if (goal == null) MaterialTheme.colors.error else mutedText(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = save, enabled = goal != null && !saving) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.subtitle2,
        color = MaterialTheme.colors.primary,
        modifier = Modifier.padding(top = 32.dp, bottom = 8.dp),
    )
}

@Composable
private fun mutedText() = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
