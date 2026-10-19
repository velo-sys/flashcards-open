package de.flashcards.open.ui.deck

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import de.flashcards.open.AppContainer
import de.flashcards.open.R
import de.flashcards.open.data.db.Card
import de.flashcards.open.data.db.Counts
import de.flashcards.open.domain.card.CardType
import de.flashcards.open.domain.card.Cloze
import de.flashcards.open.domain.card.Matching
import de.flashcards.open.domain.markdown.CardMarkdown
import de.flashcards.open.domain.scheduler.CardState
import de.flashcards.open.domain.scheduler.Intervals
import de.flashcards.open.ui.common.ConfirmDialog
import de.flashcards.open.ui.common.CountsLine
import de.flashcards.open.ui.common.NumberInputDialog
import de.flashcards.open.ui.common.OriginChip
import de.flashcards.open.ui.common.TextInputDialog
import de.flashcards.open.ui.common.appViewModel
import de.flashcards.open.ui.common.cardTypeLabel
import de.flashcards.open.ui.common.focusFrame
import de.flashcards.open.ui.common.launchSafe
import de.flashcards.open.ui.markdown.CardContent
import de.flashcards.open.ui.nav.StudyRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class DeckViewModel(private val c: AppContainer, val deckId: Long) : ViewModel() {
    val overview = c.repo.overview
    val deck = c.repo.observeDeck(deckId)
    val settings = c.settings.settings
    val query = MutableStateFlow("")
    val cards = query.debounce(200).flatMapLatest { c.repo.pagedCards(deckId, it) }.cachedIn(viewModelScope)
    val tags = MutableStateFlow<List<String>>(emptyList())

    init {
        refreshTags()
    }

    fun refreshTags() = launchSafe(c.messenger) { tags.value = c.repo.tags(listOf(deckId)) }

    fun rename(name: String) = launchSafe(c.messenger) { c.repo.renameDeck(deckId, name) }
    fun setNewPerDay(v: Int?) = launchSafe(c.messenger) { c.repo.setDeckNewPerDay(deckId, v) }
    fun reset() = launchSafe(c.messenger) { c.repo.resetProgress(listOf(deckId)); c.messenger.show(R.string.msg_progress_reset) }
    fun delete(after: () -> Unit) = launchSafe(c.messenger) { c.repo.deleteDecks(listOf(deckId)); after() }
    fun addCard(d: CardDraft) = launchSafe(c.messenger) {
        c.repo.addCard(deckId, d.front, d.back, d.tags, d.type, d.options)
        refreshTags(); c.messenger.show(R.string.msg_card_added)
    }
    fun updateCard(card: Card, d: CardDraft) = launchSafe(c.messenger) {
        c.repo.updateCardContent(card, d.front, d.back, d.tags, d.type, d.options); refreshTags()
    }
    fun deleteCard(card: Card) = launchSafe(c.messenger) { c.repo.deleteCard(card.id) }
    fun export(uri: Uri, resolver: android.content.ContentResolver) = launchSafe(c.messenger) {
        c.backup.exportDeckCsv(resolver, deckId, uri); c.messenger.show(R.string.msg_export_done)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(deckId: Long, back: () -> Unit, study: (StudyRoute) -> Unit, reimport: (Uri, Long) -> Unit) {
    val vm = appViewModel(key = "deck$deckId") { c, _ -> DeckViewModel(c, deckId) }
    val ov by vm.overview.collectAsStateWithLifecycle()
    val deck by vm.deck.collectAsStateWithLifecycle(initialValue = null)
    val settings by vm.settings.collectAsState(initial = null)
    val query by vm.query.collectAsState()
    val tags by vm.tags.collectAsState()
    val cards = vm.cards.collectAsLazyPagingItems()
    val resolver = LocalContext.current.contentResolver
    val counts = ov.deckCounts[deckId] ?: Counts()
    val topic = deck?.let { ov.topicById[it.topicId] }

    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var limitDialog by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<Card?>(null) }
    var addCard by remember { mutableStateOf(false) }
    var viewCard by remember { mutableStateOf<Card?>(null) }
    var tag by remember { mutableStateOf("") }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> reimport(u, deckId) } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { u -> vm.export(u, resolver) } }

    LaunchedEffect(cards.itemCount) { vm.refreshTags() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deck?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { addCard = true }) { Icon(Icons.Default.Add, stringResource(R.string.card_add)) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.action_more)) }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menu = false; rename = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.deck_new_limit)) }, onClick = { menu = false; limitDialog = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.deck_reimport)) }, onClick = { menu = false; csvPicker.launch(arrayOf("*/*")) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_export_csv)) }, onClick = { menu = false; exportLauncher.launch("${deck?.name ?: "export"}.csv") })
                            DropdownMenuItem(text = { Text(stringResource(R.string.deck_reset)) }, onClick = { menu = false; confirmReset = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    deck?.let { OriginChip(it.name, topic) }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            CountsLine(counts)
                            Text(
                                pluralStringResource(R.plurals.deck_stats_line, counts.total, counts.total, ov.unseen[deckId] ?: 0),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Text(
                                stringResource(
                                    R.string.deck_limit_line,
                                    deck?.newPerDay ?: settings?.newPerDay ?: 20,
                                    if (deck?.newPerDay == null) stringResource(R.string.deck_limit_global) else "",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { study(StudyRoute.of(listOf(deckId), title = deck?.name ?: "")) },
                            enabled = !counts.isEmpty,
                            modifier = Modifier.weight(1f).focusFrame(),
                        ) { Text(stringResource(R.string.action_study)) }
                        OutlinedButton(
                            onClick = { study(StudyRoute.of(listOf(deckId), cram = true, tag = tag, title = deck?.name ?: "")) },
                            enabled = counts.total > 0,
                            modifier = Modifier.weight(1f).focusFrame(),
                        ) { Text(stringResource(R.string.action_cram)) }
                    }
                    if (tags.isNotEmpty()) TagPicker(tags, tag) { tag = it }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text(stringResource(R.string.search_cards)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(cards.itemCount, key = cards.itemKey { it.id }) { i ->
                cards[i]?.let { card -> CardRow(card) { viewCard = card } }
            }
            if (cards.itemCount == 0) {
                item(key = "empty") {
                    Text(stringResource(R.string.deck_no_cards), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }

    if (rename) {
        TextInputDialog(
            title = stringResource(R.string.deck_rename), label = stringResource(R.string.label_name), initial = deck?.name ?: "",
            onConfirm = { vm.rename(it); rename = false }, onDismiss = { rename = false },
        )
    }
    if (limitDialog) {
        NumberInputDialog(
            title = stringResource(R.string.deck_new_limit), label = stringResource(R.string.label_new_per_day),
            initial = deck?.newPerDay, allowEmpty = true,
            supporting = stringResource(R.string.deck_limit_hint, settings?.newPerDay ?: 20),
            onConfirm = { vm.setNewPerDay(it); limitDialog = false }, onDismiss = { limitDialog = false },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.deck_reset), text = stringResource(R.string.deck_reset_text),
            confirmLabel = stringResource(R.string.action_reset), danger = true,
            onConfirm = { vm.reset(); confirmReset = false }, onDismiss = { confirmReset = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = pluralStringResource(R.plurals.deck_delete_title, 1, 1), text = pluralStringResource(R.plurals.deck_delete_text, 1, 1, counts.total),
            confirmLabel = stringResource(R.string.action_delete), danger = true,
            onConfirm = { confirmDelete = false; vm.delete(back) }, onDismiss = { confirmDelete = false },
        )
    }
    viewCard?.let { card ->
        CardDetailDialog(
            card = card,
            fontScale = settings?.cardFontScale ?: 1f,
            onEdit = { viewCard = null; editor = card },
            onDelete = { vm.deleteCard(card); viewCard = null },
            onDismiss = { viewCard = null },
        )
    }
    if (addCard) {
        CardEditorDialog(null, onSave = { d -> vm.addCard(d); addCard = false }, onDismiss = { addCard = false })
    }
    editor?.let { card ->
        CardEditorDialog(card, onSave = { d -> vm.updateCard(card, d); editor = null }, onDismiss = { editor = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagPicker(tags: List<String>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val all = stringResource(R.string.tag_all)
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected.ifEmpty { all },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.cram_tag_filter)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(all) }, onClick = { onSelect(""); open = false })
            tags.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { onSelect(t); open = false }) }
        }
    }
}

@Composable
private fun CardRow(card: Card, onClick: () -> Unit) {
    val now = System.currentTimeMillis()
    val status = when {
        card.state == CardState.NEW -> stringResource(R.string.card_status_new)
        card.dueAt <= now -> stringResource(R.string.card_status_due)
        else -> stringResource(R.string.card_status_in, Intervals.format(card.dueAt - now))
    }
    Card(
        modifier = Modifier.fillMaxWidth().focusFrame().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(12.dp)) {
            // Gaps are revealed in the preview, otherwise the brace notation would show up
            val front = if (card.type == CardType.CLOZE) Cloze.render(card.frontMd, reveal = true) else card.frontMd
            Text(CardMarkdown.plainPreview(front), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(
                CardMarkdown.plainPreview(card.backMd),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(if (card.type == CardType.BASIC) "" else cardTypeLabel(card.type), card.tags, status)
                    .filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CardDetailDialog(card: Card, fontScale: Float, onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                CardContent(card.frontMd, Modifier.fillMaxWidth(), fontScale)
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                // Match cards show their pairs as a list instead of the raw “A => 1 | B => 2”
                CardContent(
                    if (card.type == CardType.MATCH) Matching.render(card.backMd) else card.backMd,
                    Modifier.fillMaxWidth(),
                    fontScale,
                )
                if (card.tags.isNotBlank()) {
                    Text(card.tags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
    )
    if (confirm) {
        ConfirmDialog(
            title = stringResource(R.string.card_delete_title), text = stringResource(R.string.card_delete_text),
            confirmLabel = stringResource(R.string.action_delete), danger = true,
            onConfirm = { confirm = false; onDelete() }, onDismiss = { confirm = false },
        )
    }
}

