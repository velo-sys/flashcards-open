package de.flashcards.open.ui.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.flashcards.open.R
import de.flashcards.open.data.db.Card
import de.flashcards.open.domain.card.CardType
import de.flashcards.open.domain.card.Choices
import de.flashcards.open.domain.card.Cloze
import de.flashcards.open.domain.card.Matching
import de.flashcards.open.ui.common.cardTypeLabel

/** What the editor returns – the same fields the CSV import writes. */
data class CardDraft(
    val front: String,
    val back: String,
    val tags: String,
    val type: CardType,
    val options: String,
)

/**
 * Create or edit a card – for **all** card types. The type sits on top, the fields below adapt to
 * it: cloze gets a button for the gap, input and choice get a list for `options`, match gets a list
 * of term and target.
 *
 * What is saved is exactly the format a CSV would deliver (pairs as `A => 1 | B => 2`, options
 * separated by `|`) – which keeps export and re-import compatible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditorDialog(card: Card?, onSave: (CardDraft) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(card?.type ?: CardType.BASIC) }
    var front by remember { mutableStateOf(TextFieldValue(card?.frontMd.orEmpty())) }
    var back by remember { mutableStateOf(card?.backMd.orEmpty()) }
    var tags by remember { mutableStateOf(card?.tags.orEmpty()) }
    // Match: the pairs of the back, edited as term/target instead of as `A => 1` text
    val pairs = remember {
        mutableStateListOf<MutableMatchPair>().apply {
            Matching.pairs(card?.backMd.orEmpty()).forEach { add(MutableMatchPair(it.item, it.target)) }
            if (isEmpty()) { add(MutableMatchPair()); add(MutableMatchPair()) }
        }
    }
    val options = remember {
        mutableStateListOf<String>().apply {
            addAll(Choices.split(card?.options.orEmpty()))
            if (isEmpty()) add("")
        }
    }
    var typeOpen by remember { mutableStateOf(false) }

    val cleanPairs = pairs.filter { it.item.isNotBlank() && it.target.isNotBlank() }
    val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
    val error: Int? = when {
        type == CardType.CLOZE && !Cloze.hasGaps(front.text) -> R.string.card_need_gap
        type == CardType.INPUT && back.isBlank() -> R.string.card_need_answer
        type == CardType.CHOICE && back.isBlank() -> R.string.card_need_answer
        type == CardType.CHOICE && cleanOptions.isEmpty() -> R.string.card_need_wrong
        type == CardType.MATCH && cleanPairs.isEmpty() -> R.string.card_need_pair
        front.text.isBlank() && back.isBlank() -> R.string.card_need_text
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (card == null) R.string.card_add else R.string.card_edit)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExposedDropdownMenuBox(expanded = typeOpen, onExpandedChange = { typeOpen = it }) {
                    OutlinedTextField(
                        value = cardTypeLabel(type),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.card_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeOpen) },
                        modifier = Modifier.fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = typeOpen, onDismissRequest = { typeOpen = false }) {
                        CardType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(cardTypeLabel(t)) },
                                onClick = { type = t; typeOpen = false },
                            )
                        }
                    }
                }
                Hint(
                    when (type) {
                        CardType.BASIC -> R.string.card_hint_basic
                        CardType.CLOZE -> R.string.card_hint_cloze
                        CardType.INPUT -> R.string.card_hint_input
                        CardType.CHOICE -> R.string.card_hint_choice
                        CardType.MATCH -> R.string.card_hint_match
                    },
                )

                OutlinedTextField(
                    value = front,
                    onValueChange = { front = it },
                    label = {
                        Text(
                            stringResource(
                                when (type) {
                                    CardType.BASIC -> R.string.label_front
                                    CardType.CLOZE -> R.string.label_front_cloze
                                    CardType.MATCH -> R.string.label_front_task
                                    else -> R.string.label_front_question
                                },
                            ),
                        )
                    },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type == CardType.CLOZE) {
                    TextButton(onClick = {
                        val (text, cursor) = Cloze.insertGap(front.text, front.selection.start, front.selection.end)
                        front = TextFieldValue(text, TextRange(cursor))
                    }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.cloze_insert_gap), Modifier.padding(start = 6.dp))
                    }
                }

                if (type != CardType.MATCH) {
                    OutlinedTextField(
                        value = back,
                        onValueChange = { back = it },
                        label = {
                            Text(
                                stringResource(
                                    when (type) {
                                        CardType.CLOZE -> R.string.label_back_note
                                        CardType.INPUT -> R.string.label_back_expected
                                        CardType.CHOICE -> R.string.label_back_correct
                                        else -> R.string.label_back
                                    },
                                ),
                            )
                        },
                        minLines = if (type == CardType.BASIC) 3 else 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(stringResource(R.string.match_pairs), style = MaterialTheme.typography.labelLarge)
                    pairs.forEachIndexed { i, pair ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = pair.item,
                                onValueChange = { pairs[i] = pair.copy(item = it) },
                                label = { Text(stringResource(R.string.match_item)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = pair.target,
                                onValueChange = { pairs[i] = pair.copy(target = it) },
                                label = { Text(stringResource(R.string.match_target)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { pairs.removeAt(i) }, enabled = pairs.size > 1) {
                                Icon(Icons.Default.Close, stringResource(R.string.action_remove), Modifier.size(18.dp))
                            }
                        }
                    }
                    TextButton(onClick = { pairs.add(MutableMatchPair()) }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.match_add_pair), Modifier.padding(start = 6.dp))
                    }
                }

                if (type == CardType.INPUT || type == CardType.CHOICE || type == CardType.MATCH) {
                    OptionList(
                        label = when (type) {
                            CardType.INPUT -> R.string.options_input
                            CardType.CHOICE -> R.string.options_choice
                            else -> R.string.options_match
                        },
                        items = options,
                    )
                }

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.label_tags)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Hint(R.string.card_markdown_hint)
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null,
                onClick = {
                    onSave(
                        CardDraft(
                            front = front.text,
                            back = if (type == CardType.MATCH) {
                                Matching.join(cleanPairs.map { Matching.MatchPair(it.item.trim(), it.target.trim()) })
                            } else {
                                back
                            },
                            tags = tags,
                            type = type,
                            // Options only exist for the types that actually ask them
                            options = if (type == CardType.BASIC || type == CardType.CLOZE) {
                                ""
                            } else {
                                cleanOptions.joinToString(Choices.SEPARATOR.toString())
                            },
                        ),
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** One pair inside the editor; [Matching.MatchPair] itself stays immutable. */
private data class MutableMatchPair(val item: String = "", val target: String = "")

/** A list of single-line entries (alternatives, wrong answers, extra targets). */
@Composable
private fun OptionList(label: Int, items: SnapshotStateList<String>) {
    Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
    items.forEachIndexed { i, value ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = { items[i] = it },
                singleLine = true,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            )
            IconButton(onClick = { items.removeAt(i) }, enabled = items.size > 1) {
                Icon(Icons.Default.Close, stringResource(R.string.action_remove), Modifier.size(18.dp))
            }
        }
    }
    TextButton(onClick = { items.add("") }) {
        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
        Text(stringResource(R.string.option_add), Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun Hint(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
