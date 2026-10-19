package de.flashcards.open.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import de.flashcards.open.AppContainer
import de.flashcards.open.BuildConfig
import de.flashcards.open.R
import de.flashcards.open.data.io.InvalidBackupException
import de.flashcards.open.data.io.TestDataSeeder
import de.flashcards.open.data.repo.Settings
import de.flashcards.open.data.repo.ThemeMode
import de.flashcards.open.domain.study.MixMode
import de.flashcards.open.ui.common.ConfirmDialog
import de.flashcards.open.ui.common.NumberInputDialog
import de.flashcards.open.ui.common.appViewModel
import de.flashcards.open.ui.common.focusFrame
import de.flashcards.open.ui.common.launchSafe
import de.flashcards.open.ui.markdown.CardContent
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import kotlin.math.roundToInt

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings
    val mediaInfo = MutableStateFlow(0 to 0L)
    val busy = MutableStateFlow(false)

    init {
        refreshMedia()
    }

    private fun refreshMedia() = launchSafe(c.messenger) { mediaInfo.value = c.media.totalSize() }

    fun setNewPerDay(v: Int) = launchSafe(c.messenger) { c.settings.setNewPerDay(v) }
    fun setTheme(v: ThemeMode) = launchSafe(c.messenger) { c.settings.setThemeMode(v) }
    fun setDynamic(v: Boolean) = launchSafe(c.messenger) { c.settings.setDynamicColor(v) }
    fun setFont(v: Float) = launchSafe(c.messenger) { c.settings.setCardFontScale(v) }
    fun setMix(v: MixMode) = launchSafe(c.messenger) { c.settings.setMixMode(v) }

    fun exportBackup(uri: android.net.Uri, resolver: android.content.ContentResolver) = guarded {
        c.backup.export(resolver, uri); c.messenger.show(R.string.msg_backup_saved)
    }

    fun importBackup(uri: android.net.Uri, resolver: android.content.ContentResolver) = guarded {
        try {
            c.backup.import(resolver, uri); c.messenger.show(R.string.msg_backup_restored); refreshMedia()
        } catch (_: InvalidBackupException) {
            c.messenger.show(R.string.msg_backup_invalid)
        }
    }

    fun deleteImages() = guarded { c.media.deleteAll(); refreshMedia() }

    fun seed() = guarded {
        val t = System.currentTimeMillis()
        TestDataSeeder.seed(c.db)
        c.messenger.show(R.string.msg_seeded, System.currentTimeMillis() - t)
    }

    private fun guarded(block: suspend () -> Unit) {
        busy.value = true
        launchSafe(c.messenger) {
            try { block() } finally { busy.value = false }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(back: () -> Unit) {
    val vm = appViewModel { c, _ -> SettingsViewModel(c) }
    val s by vm.settings.collectAsState(initial = Settings())
    val media by vm.mediaInfo.collectAsState()
    val busy by vm.busy.collectAsState()
    val resolver = LocalContext.current.contentResolver
    var newPerDayDialog by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var confirmDeleteImages by remember { mutableStateOf(false) }
    var font by remember { mutableFloatStateOf(s.cardFontScale) }
    LaunchedEffect(s.cardFontScale) { font = s.cardFontScale }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let { u -> vm.exportBackup(u, resolver) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { confirmRestore = it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section(stringResource(R.string.settings_learning))
            ListItem(
                headlineContent = { Text(stringResource(R.string.label_new_per_day)) },
                supportingContent = { Text(stringResource(R.string.settings_new_per_day_hint)) },
                trailingContent = { Text("${s.newPerDay}", style = MaterialTheme.typography.titleMedium) },
                modifier = Modifier.focusFrame().clickable { newPerDayDialog = true },
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.combo_mix_mode), style = MaterialTheme.typography.bodyLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    MixMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(selected = s.mixMode == m, onClick = { vm.setMix(m) }, shape = SegmentedButtonDefaults.itemShape(i, MixMode.entries.size)) {
                            Text(stringResource(if (m == MixMode.MIXED) R.string.mix_mixed else R.string.mix_by_deck), maxLines = 1)
                        }
                    }
                }
            }
            HorizontalDivider()

            Section(stringResource(R.string.settings_appearance))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(selected = s.themeMode == m, onClick = { vm.setTheme(m) }, shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) {
                            Text(
                                stringResource(
                                    when (m) {
                                        ThemeMode.SYSTEM -> R.string.theme_system
                                        ThemeMode.LIGHT -> R.string.theme_light
                                        ThemeMode.DARK -> R.string.theme_dark
                                    },
                                ),
                            )
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                    trailingContent = { Switch(checked = s.dynamicColor, onCheckedChange = vm::setDynamic) },
                    modifier = Modifier.focusFrame().clickable { vm.setDynamic(!s.dynamicColor) },
                )
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_font_size), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${(font * 100).roundToInt()} %")
                }
                Slider(
                    value = font,
                    onValueChange = { font = (it * 20).roundToInt() / 20f },
                    onValueChangeFinished = { vm.setFont(font) },
                    valueRange = 0.7f..2f,
                    modifier = Modifier.focusFrame(),
                )
                CardContent(stringResource(R.string.settings_font_preview), Modifier.fillMaxWidth(), font)
            }
            HorizontalDivider()

            Section(stringResource(R.string.settings_backup))
            Text(
                stringResource(R.string.settings_backup_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("flashcards-backup-${LocalDate.now()}.zip") }, enabled = !busy, modifier = Modifier.focusFrame()) {
                    Text(stringResource(R.string.backup_export))
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "*/*")) }, enabled = !busy, modifier = Modifier.focusFrame()) {
                    Text(stringResource(R.string.backup_import))
                }
            }
            HorizontalDivider()

            Section(stringResource(R.string.settings_images))
            ListItem(
                headlineContent = { Text(pluralStringResource(R.plurals.settings_images_info, media.first, media.first, media.second / 1_048_576.0)) },
                trailingContent = {
                    OutlinedButton(onClick = { confirmDeleteImages = true }, enabled = !busy && media.first > 0, modifier = Modifier.focusFrame()) {
                        Text(stringResource(R.string.settings_images_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
            )

            if (BuildConfig.DEBUG) {
                HorizontalDivider()
                Section(stringResource(R.string.settings_debug))
                OutlinedButton(onClick = vm::seed, enabled = !busy, modifier = Modifier.padding(16.dp).focusFrame()) {
                    Text(stringResource(R.string.debug_seed))
                }
            }
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (newPerDayDialog) {
        NumberInputDialog(
            title = stringResource(R.string.label_new_per_day), label = stringResource(R.string.label_new_per_day),
            initial = s.newPerDay, allowEmpty = false,
            onConfirm = { it?.let(vm::setNewPerDay); newPerDayDialog = false }, onDismiss = { newPerDayDialog = false },
        )
    }
    confirmRestore?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.backup_import), text = stringResource(R.string.backup_import_confirm),
            confirmLabel = stringResource(R.string.backup_restore), danger = true,
            onConfirm = { vm.importBackup(uri, resolver); confirmRestore = null }, onDismiss = { confirmRestore = null },
        )
    }
    if (confirmDeleteImages) {
        ConfirmDialog(
            title = stringResource(R.string.settings_images_delete), text = stringResource(R.string.settings_images_delete_confirm),
            confirmLabel = stringResource(R.string.action_delete), danger = true,
            onConfirm = { vm.deleteImages(); confirmDeleteImages = false }, onDismiss = { confirmDeleteImages = false },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 4.dp),
    )
}
