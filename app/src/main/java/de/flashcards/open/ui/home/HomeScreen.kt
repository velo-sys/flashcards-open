package de.flashcards.open.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.flashcards.open.AppContainer
import de.flashcards.open.R
import de.flashcards.open.data.db.Topic
import de.flashcards.open.data.repo.PresetInfo
import de.flashcards.open.ui.common.ConfirmDialog
import de.flashcards.open.ui.common.CountsLine
import de.flashcards.open.ui.common.TextInputDialog
import de.flashcards.open.ui.common.TopicBadge
import de.flashcards.open.ui.common.TopicEditDialog
import de.flashcards.open.ui.common.appViewModel
import de.flashcards.open.ui.common.focusFrame
import de.flashcards.open.ui.common.launchSafe
import de.flashcards.open.ui.nav.StudyRoute

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val overview = c.repo.overview

    fun createTopic(name: String, color: Int, emoji: String?) = launchSafe(c.messenger) { c.repo.createTopic(name, color, emoji) }
    fun updateTopic(t: Topic) = launchSafe(c.messenger) { c.repo.updateTopic(t) }
    fun deleteTopic(t: Topic) = launchSafe(c.messenger) {
        c.repo.deleteTopic(t); c.messenger.show(R.string.msg_topic_deleted, t.name)
    }
    fun moveTopic(t: Topic, delta: Int) = launchSafe(c.messenger) { c.repo.moveTopic(t.id, delta) }
    fun renamePreset(id: Long, name: String) = launchSafe(c.messenger) { c.repo.renamePreset(id, name) }
    fun deletePreset(id: Long) = launchSafe(c.messenger) { c.repo.deletePreset(id) }
    fun exportTopic(topicId: Long, uri: Uri, resolver: android.content.ContentResolver) = launchSafe(c.messenger) {
        c.backup.exportTopicCsv(resolver, topicId, uri); c.messenger.show(R.string.msg_export_done)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    openTopic: (Long) -> Unit,
    study: (StudyRoute) -> Unit,
    openCombo: (Long?) -> Unit,
    importCsv: (Uri) -> Unit,
    bulkImport: (Uri, Boolean) -> Unit,
    openStats: () -> Unit,
    openSettings: () -> Unit,
) {
    val vm = appViewModel { c, _ -> HomeViewModel(c) }
    val ov by vm.overview.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver

    var menu by remember { mutableStateOf(false) }
    var editTopic by remember { mutableStateOf<Topic?>(null) }
    var newTopic by remember { mutableStateOf(false) }
    var deleteTopic by remember { mutableStateOf<Topic?>(null) }
    var exportTopicId by remember { mutableStateOf<Long?>(null) }
    var renamePreset by remember { mutableStateOf<PresetInfo?>(null) }
    var deletePreset by remember { mutableStateOf<PresetInfo?>(null) }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(importCsv) }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { u -> bulkImport(u, false) } }
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { u -> bulkImport(u, true) } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val id = exportTopicId
        if (uri != null && id != null) vm.exportTopic(id, uri, resolver)
        exportTopicId = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = openStats) { Icon(Icons.Default.BarChart, stringResource(R.string.stats_title)) }
                    IconButton(onClick = openSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings_title)) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.action_more)) }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_csv)) },
                                leadingIcon = { Icon(Icons.Default.FileOpen, null) },
                                onClick = { menu = false; csvPicker.launch(arrayOf("*/*")) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_folder)) },
                                leadingIcon = { Icon(Icons.Default.Folder, null) },
                                onClick = { menu = false; treePicker.launch(null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_zip)) },
                                leadingIcon = { Icon(Icons.Default.FolderZip, null) },
                                onClick = { menu = false; zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { newTopic = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.topic_new)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "actions") {
                val all = ov.countsOf(ov.decks.map { it.id })
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { study(StudyRoute.of(ov.decks.map { it.id }, title = "")) },
                            enabled = !all.isEmpty,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp).focusFrame(),
                        ) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.study_all_due), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                        FilledTonalButton(onClick = { openCombo(null) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp).focusFrame()) {
                            Icon(Icons.Default.Layers, null); Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.combo_title), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                    if (ov.decks.isNotEmpty()) CountsLine(all, Modifier.padding(start = 4.dp), showTotal = true)
                }
            }
            if (ov.presets.isNotEmpty()) {
                item(key = "presetsHeader") { SectionHeader(stringResource(R.string.presets_title)) }
                items(ov.presets, key = { "p" + it.preset.id }) { p ->
                    PresetRow(
                        p,
                        onStudy = { study(StudyRoute.of(p.deckIds, mode = p.preset.shuffleMode.name, newLimit = p.preset.newLimit, title = p.preset.name)) },
                        onEdit = { openCombo(p.preset.id) },
                        onRename = { renamePreset = p },
                        onDelete = { deletePreset = p },
                    )
                }
            }
            item(key = "topicsHeader") { SectionHeader(stringResource(R.string.topics_title)) }
            if (ov.loaded && ov.topics.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.home_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            items(ov.topics, key = { "t" + it.id }) { t ->
                val deckIds = ov.decksByTopic[t.id].orEmpty().map { it.id }
                TopicRow(
                    topic = t,
                    deckCount = deckIds.size,
                    counts = ov.topicCounts(t.id),
                    onClick = { openTopic(t.id) },
                    onStudy = { study(StudyRoute.of(deckIds, title = t.name)) },
                    onCram = { study(StudyRoute.of(deckIds, cram = true, title = t.name)) },
                    onEdit = { editTopic = t },
                    onUp = { vm.moveTopic(t, -1) },
                    onDown = { vm.moveTopic(t, 1) },
                    onExport = { exportTopicId = t.id; exportLauncher.launch("${t.name}.csv") },
                    onDelete = { deleteTopic = t },
                )
            }
        }
    }

    if (newTopic) {
        TopicEditDialog(null, ov.topics.size, onSave = { n, c, e -> vm.createTopic(n, c, e); newTopic = false }, onDismiss = { newTopic = false })
    }
    editTopic?.let { t ->
        TopicEditDialog(t, 0, onSave = { n, c, e -> vm.updateTopic(t.copy(name = n, colorArgb = c, emoji = e)); editTopic = null }, onDismiss = { editTopic = null })
    }
    deleteTopic?.let { t ->
        ConfirmDialog(
            title = stringResource(R.string.topic_delete_title),
            text = ov.decksByTopic[t.id].orEmpty().size.let { n -> pluralStringResource(R.plurals.topic_delete_text, n, t.name, n) },
            confirmLabel = stringResource(R.string.action_delete),
            danger = true,
            onConfirm = { vm.deleteTopic(t); deleteTopic = null },
            onDismiss = { deleteTopic = null },
        )
    }
    renamePreset?.let { p ->
        TextInputDialog(
            title = stringResource(R.string.preset_rename), label = stringResource(R.string.label_name), initial = p.preset.name,
            onConfirm = { vm.renamePreset(p.preset.id, it); renamePreset = null }, onDismiss = { renamePreset = null },
        )
    }
    deletePreset?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.preset_delete_title),
            text = stringResource(R.string.preset_delete_text, p.preset.name),
            confirmLabel = stringResource(R.string.action_delete),
            danger = true,
            onConfirm = { vm.deletePreset(p.preset.id); deletePreset = null },
            onDismiss = { deletePreset = null },
        )
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetRow(p: PresetInfo, onStudy: () -> Unit, onEdit: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().focusFrame().combinedClickable(onClick = onStudy, onLongClick = { menu = true }),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(p.preset.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.preset_deck_count, p.deckIds.size, p.deckIds.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CountsLine(p.counts)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.action_more)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_study)) }, onClick = { menu = false; onStudy() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.preset_edit_decks)) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopicRow(
    topic: Topic,
    deckCount: Int,
    counts: de.flashcards.open.data.db.Counts,
    onClick: () -> Unit,
    onStudy: () -> Unit,
    onCram: () -> Unit,
    onEdit: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().focusFrame().combinedClickable(onClick = onClick, onLongClick = { menu = true }),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TopicBadge(topic)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(topic.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.topic_deck_count, deckCount, deckCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CountsLine(counts)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.action_more)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_study)) }, enabled = !counts.isEmpty, onClick = { menu = false; onStudy() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_cram)) }, enabled = deckCount > 0, onClick = { menu = false; onCram() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_move_up)) }, onClick = { menu = false; onUp() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_move_down)) }, onClick = { menu = false; onDown() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_export_csv)) }, onClick = { menu = false; onExport() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
