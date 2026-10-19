package de.flashcards.open.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import de.flashcards.open.AppContainer
import de.flashcards.open.R
import de.flashcards.open.domain.scheduler.CardState
import de.flashcards.open.domain.scheduler.DayBounds
import de.flashcards.open.ui.common.appViewModel
import de.flashcards.open.ui.common.launchSafe
import de.flashcards.open.ui.theme.LocalRatingColors
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** label = null means “today” */
data class Bar(val label: String?, val value: Int)

data class StatsUi(
    val reviewsPerDay: List<Bar> = emptyList(),
    val forecast: List<Bar> = emptyList(),
    val reviewsToday: Int = 0,
    val reviews30: Int = 0,
    val states: Map<CardState, Int> = emptyMap(),
)

class StatsViewModel(private val c: AppContainer) : ViewModel() {
    val ui = MutableStateFlow(StatsUi())

    init {
        launchSafe(c.messenger) {
            val now = System.currentTimeMillis()
            val today = DayBounds.dayOf(now)
            val start30 = DayBounds.startOfDay(now) - 29 * 86_400_000L
            val reviews = c.repo.reviewTimesSince(start30).groupingBy { DayBounds.dayOf(it) }.eachCount()
            val fmt = DateTimeFormatter.ofPattern("d.M.", Locale.GERMANY)
            val perDay = (29 downTo 0).map { i -> today.minusDays(i.toLong()) }.map { d -> Bar(d.format(fmt), reviews[d] ?: 0) }

            val until = DayBounds.startOfTomorrow(now) + 6 * 86_400_000L
            val due = c.repo.dueTimesUntil(until).groupingBy { t -> if (t < now) today else DayBounds.dayOf(t) }.eachCount()
            val weekday = DateTimeFormatter.ofPattern("EE", Locale.GERMANY)
            val forecast = (0..6).map { i -> today.plusDays(i.toLong()) }.map { d: LocalDate ->
                Bar(if (d == today) null else d.format(weekday), due[d] ?: 0)
            }
            ui.value = StatsUi(
                reviewsPerDay = perDay,
                forecast = forecast,
                reviewsToday = reviews[today] ?: 0,
                reviews30 = perDay.sumOf { it.value },
                states = c.repo.stateCounts().associate { it.state to it.n },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(back: () -> Unit) {
    val vm = appViewModel { c, _ -> StatsViewModel(c) }
    val s by vm.ui.collectAsState()
    val colors = LocalRatingColors.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatCard(stringResource(R.string.stats_overview)) {
                Text(stringResource(R.string.stats_reviews_today, s.reviewsToday))
                Text(pluralStringResource(R.plurals.stats_reviews_30, s.reviews30, s.reviews30))
                Text(
                    stringResource(
                        R.string.stats_states,
                        s.states[CardState.NEW] ?: 0,
                        (s.states[CardState.LEARN] ?: 0) + (s.states[CardState.RELEARN] ?: 0),
                        s.states[CardState.REVIEW] ?: 0,
                    ),
                )
            }
            StatCard(stringResource(R.string.stats_reviews_per_day)) {
                BarChart(s.reviewsPerDay, colors.easy, labelEvery = 5)
            }
            StatCard(stringResource(R.string.stats_forecast)) {
                BarChart(s.forecast, colors.good, labelEvery = 1)
            }
        }
    }
}

@Composable
private fun StatCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun BarChart(bars: List<Bar>, color: Color, labelEvery: Int) {
    if (bars.isEmpty()) return
    val max = (bars.maxOf { it.value }).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val todayLabel = stringResource(R.string.today)
    Text(stringResource(R.string.stats_max, max), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (bars.size - 1)) / bars.size
        bars.forEachIndexed { i, b ->
            val x = i * (w + gap)
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(4f))
            val h = size.height * b.value / max
            if (h > 0) drawRoundRect(color, Offset(x, size.height - h), Size(w, h), CornerRadius(4f))
        }
    }
    // One label per group of [labelEvery] bars so the dates are not cut off
    Row(Modifier.fillMaxWidth()) {
        bars.chunked(labelEvery).forEach { group ->
            val b = group.first()
            Text(
                if (labelEvery == 1) "${b.label ?: todayLabel}\n${b.value}" else b.label ?: todayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (labelEvery == 1) TextAlign.Center else TextAlign.Start,
                maxLines = 2,
                modifier = Modifier.weight(group.size.toFloat()),
            )
        }
    }
}
