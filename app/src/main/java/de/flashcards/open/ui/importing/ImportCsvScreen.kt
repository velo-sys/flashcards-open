package de.flashcards.open.ui.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.flashcards.open.AppContainer
import de.flashcards.open.R
import de.flashcards.open.data.db.Topic
import de.flashcards.open.data.io.UriFiles
import de.flashcards.open.domain.card.CardType
import de.flashcards.open.ui.common.cardTypeLabel
import de.flashcards.open.domain.card.Cloze
import de.flashcards.open.domain.card.Matching
import de.flashcards.open.domain.csv.CsvCardReader
import de.flashcards.open.domain.csv.ImportDiff
import de.flashcards.open.domain.csv.ParsedCsv
import de.flashcards.open.domain.text.DetectedEncoding
import de.flashcards.open.ui.common.ConfirmDialog
import de.flashcards.open.ui.common.TopicBadge
import de.flashcards.open.ui.common.TopicEditDialog
import de.flashcards.open.ui.common.appViewModel
import de.flashcards.open.ui.common.focusFrame
import de.flashcards.open.ui.common.launchSafe
import de.flashcards.open.ui.markdown.CardContent
import de.flashcards.open.ui.nav.ImportCsvRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

data class DiffInfo(val existingDeckId: Long?, val diff: ImportDiff)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ImportCsvViewModel(private val c: AppContainer, private val route: ImportCsvRoute, private val resolver: android.content.ContentResolver) : ViewModel() {
    val overview = c.repo.overview
    val fileName = MutableStateFlow("")
    val parsed = MutableStateFlow<ParsedCsv?>(null)
    val failed = MutableStateFlow(false)
    val topicId = MutableStateFlow<Long?>(null)
    val deckName = MutableStateFlow("")
    val importing = MutableStateFlow(false)

    val diff = combine(parsed, topicId, deckName.debounce(250)) { p, t, n -> Triple(p, t, n) }
        .mapLatest { (p, t, n) ->
            if (p == null || t == null || n.isBlank()) null else {
                val existing = if (route.deckId >= 0) route.deckId else c.repo.deckByName(t, n.trim())?.id
                DiffInfo(existing, c.repo.previewDiff(existing, p.cards))
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        launchSafe(c.messenger) {
            val uri = route.uri.toUri()
            try {
                val (name, result) = withContext(Dispatchers.IO) {
                    val n = UriFiles.displayName(resolver, uri)
                    n to CsvCardReader.read(UriFiles.readBytes(resolver, uri))
                }
                fileName.value = name
                if (route.deckId >= 0) {
                    val d = c.repo.deck(route.deckId)
                    topicId.value = d?.topicId
                    deckName.value = d?.name ?: UriFiles.stripCsvExtension(name)
                } else {
                    topicId.value = if (route.topicId >= 0) route.topicId else c.repo.allTopics().firstOrNull()?.id
                    deckName.value = UriFiles.stripCsvExtension(name)
                }
                parsed.value = result
            } catch (e: Exception) {
                failed.value = true
                throw e
            }
        }
    }

    fun createTopic(name: String, color: Int, emoji: String?) = launchSafe(c.messenger) {
        topicId.value = c.repo.createTopic(name, color, emoji)
    }

    fun import(done: (Long) -> Unit) {
        val p = parsed.value ?: return
        val t = topicId.value ?: return
        importing.value = true
        launchSafe(c.messenger) {
            try {
                val existing = diff.value?.existingDeckId
                val r = c.repo.importCards(t, deckName.value.trim(), fileName.value, p.cards, existing)
                c.messenger.show(R.string.msg_import_result, deckName.value.trim(), r.added, r.changed, r.removed)
                done(r.deckId)
            } finally {
                importing.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCsvScreen(route: ImportCsvRoute, back: () -> Unit, openDeck: (Long) -> Unit) {
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    val vm = appViewModel(key = "import${route.uri}") { c, _ -> ImportCsvViewModel(c, route, resolver) }
    val ov by vm.overview.collectAsStateWithLifecycle()
    val parsed by vm.parsed.collectAsState()
    val failed by vm.failed.collectAsState()
    val fileName by vm.fileName.collectAsState()
    val topicId by vm.topicId.collectAsState()
    val deckName by vm.deckName.collectAsState()
    val diff by vm.diff.collectAsState()
    val importing by vm.importing.collectAsState()
    var newTopic by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_preview_title)) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        bottomBar = {
            val p = parsed
            if (p != null) {
                Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        diff?.let { d ->
                            Text(
                                stringResource(R.string.import_diff, d.diff.added, d.diff.changed, d.diff.removed),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (d.existingDeckId != null) {
                                Text(stringResource(R.string.import_updates_existing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Button(
                            onClick = { if ((diff?.diff?.removed ?: 0) > 0) confirmRemove = true else vm.import(openDeck) },
                            enabled = !importing && topicId != null && deckName.isNotBlank() && p.cards.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusFrame(),
                        ) {
                            Text(stringResource(if (diff?.existingDeckId != null) R.string.import_update else R.string.import_do))
                        }
                    }
                }
            }
        },
    ) { padding ->
        val p = parsed
        if (p == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (failed) Text(stringResource(R.string.import_failed)) else CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "info") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        InfoRow(stringResource(R.string.import_file), fileName)
                        InfoRow(stringResource(R.string.import_encoding), encodingLabel(p.encoding))
                        InfoRow(stringResource(R.string.import_delimiter), delimiterLabel(p.delimiter))
                        InfoRow(stringResource(R.string.import_header), stringResource(if (p.headerFound) R.string.yes else R.string.no))
                        InfoRow(stringResource(R.string.import_card_count), p.cards.size.toString())
                        val typeCounts = p.cards.groupingBy { it.type }.eachCount()
                            .filterKeys { it != CardType.BASIC }
                        if (typeCounts.isNotEmpty()) {
                            InfoRow(
                                stringResource(R.string.import_types),
                                typeCounts.entries.map { "${it.value}× " + cardTypeLabel(it.key) }.joinToString(" · "),
                            )
                        }
                        if (p.unknownTypes > 0) {
                            Text(
                                pluralStringResource(R.plurals.import_types_unknown, p.unknownTypes, p.unknownTypes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (p.matchWithoutPairs > 0) {
                            Text(
                                pluralStringResource(R.plurals.import_match_without_pairs, p.matchWithoutPairs, p.matchWithoutPairs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            item(key = "target") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TopicDropdown(
                        topics = ov.topics,
                        selected = ov.topicById[topicId ?: -1],
                        enabled = route.deckId < 0,
                        onSelect = { vm.topicId.value = it.id },
                        onNew = { newTopic = true },
                    )
                    OutlinedTextField(
                        value = deckName,
                        onValueChange = { vm.deckName.value = it },
                        label = { Text(stringResource(R.string.import_deck_name)) },
                        singleLine = true,
                        enabled = route.deckId < 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (p.cards.isEmpty()) {
                item(key = "none") { Text(stringResource(R.string.import_no_cards), color = MaterialTheme.colorScheme.error) }
            } else {
                item(key = "pvh") { Text(stringResource(R.string.import_preview_cards), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                itemsIndexed(p.cards.take(5)) { _, card ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            if (card.type != CardType.BASIC) {
                                Text(
                                    cardTypeLabel(card.type),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                            }
                            // Show the gaps revealed in the preview
                            CardContent(
                                if (card.type == CardType.CLOZE) Cloze.render(card.front, reveal = true) else card.front,
                                Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            // Match cards show their pairs as a list, not the raw “A => 1 | B => 2”
                            CardContent(
                                if (card.type == CardType.MATCH) Matching.render(card.back) else card.back,
                                Modifier.fillMaxWidth(),
                            )
                            if (card.tags.isNotBlank()) {
                                Text(card.tags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (newTopic) {
        TopicEditDialog(null, ov.topics.size, onSave = { n, c, e -> vm.createTopic(n, c, e); newTopic = false }, onDismiss = { newTopic = false })
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.import_remove_title),
            text = (diff?.diff?.removed ?: 0).let { n -> pluralStringResource(R.plurals.import_remove_text, n, n) },
            confirmLabel = stringResource(R.string.import_update),
            danger = true,
            onConfirm = { confirmRemove = false; vm.import(openDeck) },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun encodingLabel(e: DetectedEncoding) = stringResource(
    when (e) {
        DetectedEncoding.UTF8_BOM -> R.string.enc_utf8_bom
        DetectedEncoding.UTF16_LE -> R.string.enc_utf16le
        DetectedEncoding.UTF16_BE -> R.string.enc_utf16be
        DetectedEncoding.UTF8 -> R.string.enc_utf8
        DetectedEncoding.WINDOWS_1252 -> R.string.enc_cp1252
    },
)

@Composable
fun delimiterLabel(c: Char) = when (c) {
    ';' -> stringResource(R.string.delim_semicolon)
    ',' -> stringResource(R.string.delim_comma)
    '\t' -> stringResource(R.string.delim_tab)
    '|' -> stringResource(R.string.delim_pipe)
    else -> "“$c”"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicDropdown(topics: List<Topic>, selected: Topic?, enabled: Boolean, onSelect: (Topic) -> Unit, onNew: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { if (enabled) open = it }) {
        OutlinedTextField(
            value = selected?.name ?: stringResource(R.string.import_choose_topic),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.import_target_topic)) },
            leadingIcon = selected?.let { { TopicBadge(it, 24) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            topics.forEach { t ->
                DropdownMenuItem(text = { Text(t.name) }, leadingIcon = { TopicBadge(t, 24) }, onClick = { onSelect(t); open = false })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.topic_new_ellipsis)) }, onClick = { open = false; onNew() })
        }
    }
}
