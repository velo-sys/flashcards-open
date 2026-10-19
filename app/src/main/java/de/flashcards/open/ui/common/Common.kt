package de.flashcards.open.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.flashcards.open.AppContainer
import de.flashcards.open.FlashcardsApp
import de.flashcards.open.R
import de.flashcards.open.data.db.Counts
import de.flashcards.open.data.db.Topic
import de.flashcards.open.domain.card.CardType
import de.flashcards.open.ui.theme.LocalRatingColors

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as FlashcardsApp).container

/** A ViewModel with access to the app container (manual DI). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM {
    val container = appContainer()
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(container, createSavedStateHandle()) } },
    )
}

/** A visible focus outline for D-pad and controller navigation. */
fun Modifier.focusFrame(shape: Shape = RoundedCornerShape(14.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    this
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(3.dp, color, shape) else Modifier)
}

fun Color.Companion.fromArgb(argb: Int) = Color(argb)

@Composable
fun CountsLine(counts: Counts, modifier: Modifier = Modifier, showTotal: Boolean = false) {
    val c = LocalRatingColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CountText(counts.new, stringResource(R.string.count_new), c.easy)
        CountText(counts.learn, stringResource(R.string.count_learn), c.hard)
        CountText(counts.due, stringResource(R.string.count_due), c.good)
        if (showTotal) {
            Text(
                stringResource(R.string.count_total, counts.total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CountText(n: Int, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$n", color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Text(" $label", color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun TopicBadge(topic: Topic?, size: Int = 40) {
    val color = topic?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.outline
    Box(
        Modifier.size(size.dp).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val emoji = topic?.emoji
        if (!emoji.isNullOrBlank()) {
            Text(emoji, fontSize = (size * 0.5).sp)
        } else {
            Text(
                topic?.name?.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size * 0.42).sp,
            )
        }
    }
}

/** Where a card comes from (deck · topic), in the topic's color. */
@Composable
fun OriginChip(deckName: String, topic: Topic?, modifier: Modifier = Modifier) {
    val color = topic?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary
    Row(
        modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(
            text = buildString {
                append(" ")
                topic?.emoji?.let { append(it).append(" ") }
                append(deckName)
                topic?.let { append(" · ").append(it.name) }
            },
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The display name of a card type. */
@Composable
fun cardTypeLabel(t: CardType) = stringResource(
    when (t) {
        CardType.BASIC -> R.string.card_type_basic
        CardType.CLOZE -> R.string.card_type_cloze
        CardType.INPUT -> R.string.card_type_input
        CardType.CHOICE -> R.string.card_type_choice
        CardType.MATCH -> R.string.card_type_match
    },
)
