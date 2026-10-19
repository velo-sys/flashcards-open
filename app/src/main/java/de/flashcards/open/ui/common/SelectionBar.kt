package de.flashcards.open.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.flashcards.open.R
import de.flashcards.open.data.db.Counts

/** The bottom bar: “x decks · new / learning / due” plus Study / Cram. */
@Composable
fun SelectionBar(
    deckCount: Int,
    counts: Counts,
    onStudy: () -> Unit,
    onCram: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(pluralStringResource(R.plurals.selection_decks, deckCount, deckCount), style = MaterialTheme.typography.titleSmall)
                CountsLine(counts)
            }
            extra?.invoke()
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStudy, enabled = deckCount > 0 && !counts.isEmpty, modifier = Modifier.weight(1f).focusFrame()) {
                    Text(stringResource(R.string.action_study))
                }
                OutlinedButton(onClick = onCram, enabled = deckCount > 0 && counts.total > 0, modifier = Modifier.weight(1f).focusFrame()) {
                    Text(stringResource(R.string.action_cram))
                }
            }
        }
    }
}
