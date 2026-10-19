package de.flashcards.open.ui.topic

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.flashcards.open.AppContainer
import de.flashcards.open.R
import de.flashcards.open.data.db.Counts
import de.flashcards.open.data.db.Deck
import de.flashcards.open.ui.common.ConfirmDialog
import de.flashcards.open.ui.common.CountsLine
import de.flashcards.open.ui.common.SelectionBar
import de.flashcards.open.ui.common.TextInputDialog
import de.flashcards.open.ui.common.TopicBadge
import de.flashcards.open.ui.common.TopicPickerDialog
import de.flashcards.open.ui.common.appViewModel
import de.flashcards.open.ui.common.focusFrame
import de.flashcards.open.ui.common.launchSafe
import de.flashcards.open.ui.nav.StudyRoute

enum class DeckSort { MANUAL, NAME, DUE, RECENT }

class TopicViewModel(private val c: AppContainer) : ViewModel() {
    val overview = c.repo.overview

    fun createDeck(topicId: Long, name: String) = launchSafe(c.messenger) { c.repo.createDeck(topicId, name) }
    fun rename(id: Long, name: String) = launchSafe(c.messenger) { c.repo.renameDeck(id, name) }
    fun move(ids: List<Long>, topicId: Long, topicName: String) = launchSafe(c.messenger) {
        c.repo.moveDecksToTopic(ids, topicId); c.messenger.showPlural(R.plurals.msg_decks_moved, ids.size, ids.size, topicName)
    }
    fun delete(ids: List<Long>) = launchSafe(c.messenger) {
        c.repo.deleteDecks(ids); c.messenger.showPlural(R.plurals.msg_decks_deleted, ids.size, ids.size)
    }
    fun moveInOrder(id: Long, delta: Int) = launchSafe(c.messenger) { c.repo.moveDeck(id, delta) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicScreen(
    topicId: Long,
    back: () -> Unit,
    openDeck: (Long) -> Unit,
    study: (StudyRoute) -> Unit,
    importCsv: (Uri, Long) -> Unit,
    openCombo: (List<Long>) -> Unit,
) {
    val vm = appViewModel(key = "topic$topicId") { c, _ -> TopicViewModel(c) }
    val ov by vm.overview.collectAsStateWithLifecycle()
    val topic = ov.topicById[topicId]
    val allDecks = ov.decksByTopic[topicId].orEmpty()

    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(DeckSort.MANUAL) }
    var selection by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var newDeck by remember { mutableStateOf(false) }
    var renameDeck by remember { mutableStateOf<Deck?>(null) }
    var moveIds by remember { mutableStateOf<List<Long>?>(null) }
    var deleteIds by remember { mutableStateOf<List<Long>?>(null) }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> importCsv(u, topicId) } }

    val decks = remember(allDecks, query, sort, ov.deckCounts) {
        val filtered = if (query.isBlank()) allDecks else allDecks.filter { it.name.contains(query.trim(), ignoreCase = true) }
        when (sort) {
            DeckSort.MANUAL -> filtered
            DeckSort.NAME -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            DeckSort.DUE -> filtered.sortedByDescending { d -> ov.deckCounts[d.id]?.let { it.due + it.learn + it.new } ?: 0 }
            DeckSort.RECENT -> filtered.sortedByDescending { it.updatedAt }
        }
    }
    // drop deleted/moved decks from the selection
    val validSelection = selection.filter { id -> allDecks.any { it.id == id } }.toSet()

    BackHandler(enabled = selecting) { selecting = false; selection = emptySet() }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selection_count, validSelection.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selecting = false; selection = emptySet() }) { Icon(Icons.Default.Close, stringResource(R.string.action_cancel)) }
                    },
                    actions = {
                        IconButton(onClick = { selection = allDecks.map { it.id }.toSet() }) { Icon(Icons.Default.DoneAll, stringResource(R.string.select_all_in_topic)) }
                        if (validSelection.size == 1) {
                            IconButton(onClick = { renameDeck = allDecks.first { it.id == validSelection.first() } }) { Icon(Icons.Default.Edit, stringResource(R.string.action_rename)) }
                        }
                        IconButton(onClick = { moveIds = validSelection.toList() }, enabled = validSelection.isNotEmpty()) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, stringResource(R.string.action_move))
                        }
                        IconButton(onClick = { deleteIds = validSelection.toList() }, enabled = validSelection.isNotEmpty()) {
                            Icon(Icons.Default.Delete, stringResource(R.string.action_delete))
                        }
                        IconButton(onClick = { openCombo(validSelection.toList()) }) { Icon(Icons.Default.Layers, stringResource(R.string.combo_title)) }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TopicBadge(topic, 30)
                            Text(topic?.name ?: "", Modifier.padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                    actions = {
                        IconButton(onClick = { study(StudyRoute.of(allDecks.map { it.id }, title = topic?.name ?: "")) }, enabled = !ov.topicCounts(topicId).isEmpty) {
                            Icon(Icons.Default.PlayArrow, stringResource(R.string.topic_study))
                        }
                        IconButton(onClick = { csvPicker.launch(arrayOf("*/*")) }) { Icon(Icons.Default.FileOpen, stringResource(R.string.import_csv)) }
                        IconButton(onClick = { selecting = true }) { Icon(Icons.Default.DoneAll, stringResource(R.string.action_select)) }
                    },
                )
            }
        },
        bottomBar = {
            if (selecting) {
                SelectionBar(
                    deckCount = validSelection.size,
                    counts = ov.countsOf(validSelection),
                    onStudy = { study(StudyRoute.of(validSelection, title = topic?.name ?: "")) },
                    onCram = { study(StudyRoute.of(validSelection, cram = true, title = topic?.name ?: "")) },
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(onClick = { newDeck = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.deck_new)) })
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CountsLine(ov.topicCounts(topicId), showTotal = true)
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text(stringResource(R.string.search_decks)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeckSort.entries.forEach { s ->
                            FilterChip(
                                selected = sort == s,
                                onClick = { sort = s },
                                label = {
                                    Text(
                                        stringResource(
                                            when (s) {
                                                DeckSort.MANUAL -> R.string.sort_manual
                                                DeckSort.NAME -> R.string.sort_name
                                                DeckSort.DUE -> R.string.sort_due
                                                DeckSort.RECENT -> R.string.sort_recent
                                            },
                                        ),
                                    )
                                },
                                modifier = Modifier.focusFrame(),
                            )
                        }
                    }
                }
            }
            if (allDecks.isEmpty() && ov.loaded) {
                item(key = "empty") {
                    Text(stringResource(R.string.topic_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                }
            }
            items(decks, key = { it.id }) { d ->
                DeckRow(
                    deck = d,
                    counts = ov.deckCounts[d.id] ?: Counts(),
                    selecting = selecting,
                    selected = d.id in validSelection,
                    onClick = {
                        if (selecting) selection = validSelection.toggle(d.id) else openDeck(d.id)
                    },
                    onLongClick = {
                        if (!selecting) { selecting = true; selection = setOf(d.id) } else selection = validSelection.toggle(d.id)
                    },
                    onRename = { renameDeck = d },
                    onMove = { moveIds = listOf(d.id) },
                    onUp = { vm.moveInOrder(d.id, -1) },
                    onDown = { vm.moveInOrder(d.id, 1) },
                    onDelete = { deleteIds = listOf(d.id) },
                    onSelect = { selecting = true; selection = validSelection + d.id },
                    manualSort = sort == DeckSort.MANUAL && query.isBlank(),
                )
            }
        }
    }

    if (newDeck) {
        TextInputDialog(
            title = stringResource(R.string.deck_new), label = stringResource(R.string.label_name),
            onConfirm = { vm.createDeck(topicId, it); newDeck = false }, onDismiss = { newDeck = false },
        )
    }
    renameDeck?.let { d ->
        TextInputDialog(
            title = stringResource(R.string.deck_rename), label = stringResource(R.string.label_name), initial = d.name,
            onConfirm = { vm.rename(d.id, it); renameDeck = null }, onDismiss = { renameDeck = null },
        )
    }
    moveIds?.let { ids ->
        TopicPickerDialog(
            title = pluralStringResource(R.plurals.deck_move_title, ids.size, ids.size),
            topics = ov.topics,
            excludeId = topicId,
            onPick = { t -> vm.move(ids, t.id, t.name); moveIds = null; selection = emptySet(); selecting = false },
            onDismiss = { moveIds = null },
        )
    }
    deleteIds?.let { ids ->
        val cards = ids.sumOf { ov.deckCounts[it]?.total ?: 0 }
        ConfirmDialog(
            title = pluralStringResource(R.plurals.deck_delete_title, ids.size, ids.size),
            text = pluralStringResource(R.plurals.deck_delete_text, ids.size, ids.size, cards),
            confirmLabel = stringResource(R.string.action_delete),
            danger = true,
            onConfirm = { vm.delete(ids); deleteIds = null; selection = emptySet(); selecting = false },
            onDismiss = { deleteIds = null },
        )
    }
}

private fun Set<Long>.toggle(id: Long) = if (id in this) this - id else this + id

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckRow(
    deck: Deck,
    counts: Counts,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    manualSort: Boolean,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().focusFrame().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(Modifier.padding(start = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) Checkbox(checked = selected, onCheckedChange = { onClick() })
            Column(Modifier.weight(1f).padding(start = if (selecting) 0.dp else 8.dp)) {
                Text(deck.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                CountsLine(counts, showTotal = true)
            }
            if (!selecting) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.action_more)) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_select)) }, onClick = { menu = false; onSelect() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_move)) }, onClick = { menu = false; onMove() })
                        if (manualSort) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_move_up)) }, onClick = { menu = false; onUp() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_move_down)) }, onClick = { menu = false; onDown() })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}
