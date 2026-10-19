package de.flashcards.open.ui.combo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.flashcards.open.AppContainer
import de.flashcards.open.R
import de.flashcards.open.data.db.Counts
import de.flashcards.open.data.db.StudyPreset
import de.flashcards.open.domain.study.MixMode
import de.flashcards.open.ui.common.CountsLine
import de.flashcards.open.ui.common.SelectionBar
import de.flashcards.open.ui.common.TextInputDialog
import de.flashcards.open.ui.common.TopicBadge
import de.flashcards.open.ui.common.appViewModel
import de.flashcards.open.ui.common.focusFrame
import de.flashcards.open.ui.common.launchSafe
import de.flashcards.open.ui.nav.ComboRoute
import de.flashcards.open.ui.nav.StudyRoute
import kotlinx.coroutines.flow.MutableStateFlow

class ComboViewModel(private val c: AppContainer, route: ComboRoute) : ViewModel() {
    val overview = c.repo.overview
    val selection = MutableStateFlow(route.preselectDeckIds.split(',').mapNotNull { it.toLongOrNull() }.toSet())
    val expanded = MutableStateFlow(setOf<Long>())
    val mode = MutableStateFlow(MixMode.MIXED)
    val sessionLimit = MutableStateFlow("")
    val preset = MutableStateFlow<StudyPreset?>(null)

    init {
        launchSafe(c.messenger) {
            if (route.presetId >= 0) {
                c.repo.preset(route.presetId)?.let { p ->
                    preset.value = p
                    mode.value = p.shuffleMode
                    sessionLimit.value = p.newLimit?.toString() ?: ""
                    selection.value = c.repo.presetDeckIds(p.id).toSet()
                }
            } else {
                mode.value = c.settings.current().mixMode
            }
            // Expand topics that have preselected decks
            val decks = c.repo.overview.value.deckById
            expanded.value = selection.value.mapNotNull { decks[it]?.topicId }.toSet()
        }
    }

    fun limit(): Int? = sessionLimit.value.trim().toIntOrNull()

    fun savePreset(name: String) = launchSafe(c.messenger) {
        c.repo.savePreset(name, selection.value.toList(), mode.value, limit())
        c.messenger.show(R.string.msg_preset_saved, name)
    }

    fun updatePreset(after: () -> Unit) = launchSafe(c.messenger) {
        val p = preset.value ?: return@launchSafe
        c.repo.updatePreset(p.copy(shuffleMode = mode.value, newLimit = limit()), selection.value.toList())
        c.messenger.show(R.string.msg_preset_saved, p.name)
        after()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComboScreen(route: ComboRoute, back: () -> Unit, study: (StudyRoute) -> Unit) {
    val vm = appViewModel(key = "combo${route.presetId}_${route.preselectDeckIds}") { c, _ -> ComboViewModel(c, route) }
    val ov by vm.overview.collectAsStateWithLifecycle()
    val selectionRaw by vm.selection.collectAsState()
    val expanded by vm.expanded.collectAsState()
    val mode by vm.mode.collectAsState()
    val limit by vm.sessionLimit.collectAsState()
    val preset by vm.preset.collectAsState()
    var query by remember { mutableStateOf("") }
    var saveDialog by remember { mutableStateOf(false) }

    val selection = selectionRaw.filter { it in ov.deckById }.toSet()
    val q = query.trim()
    val visibleTopics = ov.topics.mapNotNull { t ->
        val decks = ov.decksByTopic[t.id].orEmpty()
        val shown = if (q.isEmpty() || t.name.contains(q, true)) decks else decks.filter { it.name.contains(q, true) }
        if (q.isNotEmpty() && shown.isEmpty()) null else t to shown
    }
    val counts = ov.countsOf(selection).let { c -> limit.toIntOrNull()?.let { c.copy(new = minOf(c.new, it)) } ?: c }
    fun startRoute(cram: Boolean) = StudyRoute.of(selection, cram = cram, mode = mode.name, newLimit = vm.limit(), title = preset?.name ?: "")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(preset?.name ?: stringResource(R.string.combo_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    TextButton(onClick = { vm.selection.value = emptySet() }, enabled = selection.isNotEmpty()) { Text(stringResource(R.string.action_clear)) }
                },
            )
        },
        bottomBar = {
            SelectionBar(
                deckCount = selection.size,
                counts = counts,
                onStudy = { study(startRoute(false)) },
                onCram = { study(startRoute(true)) },
                extra = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (preset != null) {
                            TextButton(onClick = { vm.updatePreset(back) }, enabled = selection.isNotEmpty(), modifier = Modifier.focusFrame()) {
                                Text(stringResource(R.string.preset_update))
                            }
                        }
                        TextButton(onClick = { saveDialog = true }, enabled = selection.isNotEmpty(), modifier = Modifier.focusFrame()) {
                            Text(stringResource(R.string.preset_save_as))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "options") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        placeholder = { Text(stringResource(R.string.search_topics_decks)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.combo_mix_mode), style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        MixMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = mode == m,
                                onClick = { vm.mode.value = m },
                                shape = SegmentedButtonDefaults.itemShape(i, MixMode.entries.size),
                            ) {
                                Text(stringResource(if (m == MixMode.MIXED) R.string.mix_mixed else R.string.mix_by_deck), maxLines = 1)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = limit,
                        onValueChange = { s -> vm.sessionLimit.value = s.filter { it.isDigit() }.take(5) },
                        label = { Text(stringResource(R.string.combo_session_limit)) },
                        supportingText = { Text(stringResource(R.string.combo_session_limit_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (ov.loaded && ov.topics.isEmpty()) {
                item(key = "empty") { Text(stringResource(R.string.home_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            for ((topic, decks) in visibleTopics) {
                val allIds = ov.decksByTopic[topic.id].orEmpty().map { it.id }
                val selectedCount = allIds.count { it in selection }
                val state = when {
                    selectedCount == 0 -> ToggleableState.Off
                    selectedCount == allIds.size -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                }
                val isOpen = topic.id in expanded || q.isNotEmpty()
                item(key = "t${topic.id}") {
                    Row(
                        Modifier.fillMaxWidth().focusFrame().clickable {
                            vm.selection.value = if (state == ToggleableState.On) selection - allIds.toSet() else selection + allIds
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TriStateCheckbox(state = state, onClick = null, modifier = Modifier.padding(horizontal = 12.dp))
                        TopicBadge(topic, 30)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(topic.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.combo_topic_line, selectedCount, allIds.size),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { vm.expanded.value = if (topic.id in expanded) expanded - topic.id else expanded + topic.id }) {
                            Icon(if (isOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, stringResource(R.string.action_expand))
                        }
                    }
                }
                if (isOpen) {
                    items(decks, key = { "d${it.id}" }) { d ->
                        val checked = d.id in selection
                        Row(
                            Modifier.fillMaxWidth().padding(start = 36.dp).focusFrame().clickable {
                                vm.selection.value = if (checked) selection - d.id else selection + d.id
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(horizontal = 12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                CountsLine(ov.deckCounts[d.id] ?: Counts())
                            }
                        }
                    }
                }
            }
        }
    }

    if (saveDialog) {
        TextInputDialog(
            title = stringResource(R.string.preset_save_as), label = stringResource(R.string.label_name),
            onConfirm = { vm.savePreset(it); saveDialog = false }, onDismiss = { saveDialog = false },
        )
    }
}
