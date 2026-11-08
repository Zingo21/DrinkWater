package org.erbeenjoyers.drinkwater.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.erbeenjoyers.drinkwater.HomeState
import org.erbeenjoyers.drinkwater.api.DEFAULT_AMOUNT_ML
import org.erbeenjoyers.drinkwater.api.DrinkDto
import org.erbeenjoyers.drinkwater.api.StatsDto

/** A small glass, a glass, a can and a bottle. */
private val AMOUNTS_ML = listOf(150, DEFAULT_AMOUNT_ML, 330, 500)

/** [stats] is null until it has been loaded; the screen works without it. */
@Composable
fun DrinkScreen(
    state: HomeState,
    stats: StatsDto?,
    onLogDrink: (drink: DrinkDto, amountMl: Int) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.loading -> CircularProgressIndicator()

            state.loadError != null -> {
                Text(state.loadError, color = MaterialTheme.colors.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("Try again") }
            }

            state.drinks.isEmpty() -> {
                Text("There are no drinks to log yet.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("Refresh") }
            }

            else -> DrinkPicker(state, stats, onLogDrink)
        }
    }
}

@Composable
private fun ColumnScope.DrinkPicker(
    state: HomeState,
    stats: StatsDto?,
    onLogDrink: (drink: DrinkDto, amountMl: Int) -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    var amountMl by rememberSaveable { mutableStateOf(DEFAULT_AMOUNT_ML) }
    val selected = state.drinks.firstOrNull { it.id == selectedId } ?: state.drinks.first()

    if (stats != null) {
        TodayProgress(stats)
        Spacer(Modifier.height(32.dp))
    }

    // With a single drink there is nothing to choose between, so only the button is shown.
    if (state.drinks.size > 1) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(state.drinks, key = { it.id }) { drink ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = drink.id == selected.id,
                            role = Role.RadioButton,
                            onClick = { selectedId = drink.id },
                        )
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = drink.id == selected.id, onClick = null)
                    Text(drink.name, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.body1)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AMOUNTS_ML.forEach { amount ->
            val isSelected = amount == amountMl
            val modifier = Modifier.weight(1f).semantics { this.selected = isSelected }
            val padding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            if (isSelected) {
                Button(onClick = {}, modifier = modifier, contentPadding = padding) { Text("$amount ml", maxLines = 1) }
            } else {
                OutlinedButton(onClick = { amountMl = amount }, modifier = modifier, contentPadding = padding) {
                    Text("$amount ml", maxLines = 1)
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))

    Button(
        onClick = { onLogDrink(selected, amountMl) },
        enabled = !state.logging,
        modifier = Modifier.fillMaxWidth().height(72.dp),
    ) {
        if (state.logging) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Text("I drank $amountMl ml ${selected.name}", style = MaterialTheme.typography.h6)
        }
    }
}

@Composable
private fun TodayProgress(stats: StatsDto) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${stats.todayMl} ml", style = MaterialTheme.typography.h4)
        Text(
            if (stats.todayMl >= stats.goalMl) "Today's goal of ${stats.goalMl} ml reached" else "of ${stats.goalMl} ml today",
            style = MaterialTheme.typography.body2,
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = (stats.todayMl.toFloat() / stats.goalMl).coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
    }
}
