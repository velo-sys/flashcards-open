package de.flashcards.open.ui.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.flashcards.open.AppContainer
import de.flashcards.open.R
import de.flashcards.open.data.io.BulkImporter
import de.flashcards.open.data.io.BulkProgress
import de.flashcards.open.data.io.BulkResult
import de.flashcards.open.data.io.UriFiles
import de.flashcards.open.ui.common.appViewModel
import de.flashcards.open.ui.common.focusFrame
import de.flashcards.open.ui.nav.BulkImportRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BulkState {
    data object Scanning : BulkState
    data class Running(val p: BulkProgress) : BulkState
    data class Done(val r: BulkResult) : BulkState
    data object Cancelled : BulkState
    data class Failed(val message: String) : BulkState
}

class BulkImportViewModel(private val c: AppContainer, route: BulkImportRoute, context: android.content.Context) : ViewModel() {
    val state = MutableStateFlow<BulkState>(BulkState.Scanning)
    private val job: Job

    init {
        val app = context.applicationContext
        job = viewModelScope.launch {
            var zip: java.util.zip.ZipFile? = null
            try {
                val uri = route.uri.toUri()
                val (root, entries) = withContext(Dispatchers.IO) {
                    if (route.zip) {
                        val (r, e, z) = BulkImporter.zipEntries(app.contentResolver, uri, app.cacheDir, UriFiles.displayName(app.contentResolver, uri))
                        zip = z
                        r to e
                    } else {
                        BulkImporter.treeEntries(app, uri)
                    }
                }
                val result = c.bulkImporter.run(root, entries) { p -> state.value = BulkState.Running(p) }
                state.value = BulkState.Done(result)
            } catch (e: CancellationException) {
                state.value = BulkState.Cancelled
                throw e
            } catch (e: Exception) {
                state.value = BulkState.Failed(e.message ?: e.javaClass.simpleName)
                c.messenger.error(e)
            } finally {
                runCatching { zip?.close() }
            }
        }
    }

    fun cancel() {
        job.cancel()
        state.value = BulkState.Cancelled
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkImportScreen(route: BulkImportRoute, back: () -> Unit) {
    val context = LocalContext.current
    val vm = appViewModel(key = "bulk${route.uri}") { c, _ -> BulkImportViewModel(c, route, context) }
    val s by vm.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (route.zip) R.string.import_zip else R.string.import_folder)) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val st = s) {
                BulkState.Scanning -> {
                    Text(stringResource(R.string.bulk_scanning))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedButton(onClick = vm::cancel, modifier = Modifier.focusFrame()) { Text(stringResource(R.string.action_cancel)) }
                }
                is BulkState.Running -> {
                    Text(pluralStringResource(R.plurals.bulk_progress, st.p.total, st.p.done, st.p.total), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { if (st.p.total == 0) 0f else st.p.done.toFloat() / st.p.total }, modifier = Modifier.fillMaxWidth())
                    Text(st.p.current, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = vm::cancel, modifier = Modifier.focusFrame()) { Text(stringResource(R.string.action_cancel)) }
                }
                is BulkState.Done -> {
                    val r = st.r
                    Text(stringResource(R.string.bulk_done), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.bulk_summary_topics, r.topicsCreated))
                    Text(stringResource(R.string.bulk_summary_decks, r.decksCreated, r.decksUpdated))
                    Text(stringResource(R.string.bulk_summary_cards, r.cardsAdded, r.cardsChanged, r.cardsRemoved))
                    Text(stringResource(R.string.bulk_summary_images, r.images))
                    if (r.errors.isNotEmpty()) {
                        Text(stringResource(R.string.bulk_errors, r.errors.size), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
                        r.errors.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                    Button(onClick = back, modifier = Modifier.focusFrame()) { Text(stringResource(R.string.action_done)) }
                }
                BulkState.Cancelled -> {
                    Text(stringResource(R.string.bulk_cancelled))
                    Button(onClick = back, modifier = Modifier.focusFrame()) { Text(stringResource(R.string.action_back)) }
                }
                is BulkState.Failed -> {
                    Text(stringResource(R.string.error_generic, st.message), color = MaterialTheme.colorScheme.error)
                    Button(onClick = back, modifier = Modifier.focusFrame()) { Text(stringResource(R.string.action_back)) }
                }
            }
        }
    }
}
