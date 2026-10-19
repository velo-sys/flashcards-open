package de.flashcards.open.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.flashcards.open.ui.combo.ComboScreen
import de.flashcards.open.ui.deck.DeckScreen
import de.flashcards.open.ui.home.HomeScreen
import de.flashcards.open.ui.importing.BulkImportScreen
import de.flashcards.open.ui.importing.ImportCsvScreen
import de.flashcards.open.ui.settings.SettingsScreen
import de.flashcards.open.ui.stats.StatsScreen
import de.flashcards.open.ui.study.StudyScreen
import de.flashcards.open.ui.topic.TopicScreen

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val back: () -> Unit = { if (!nav.popBackStack()) Unit }
    NavHost(nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                openTopic = { nav.navigate(TopicRoute(it)) },
                study = { nav.navigate(it) },
                openCombo = { presetId -> nav.navigate(ComboRoute(presetId ?: -1)) },
                importCsv = { uri -> nav.navigate(ImportCsvRoute(uri.toString())) },
                bulkImport = { uri, zip -> nav.navigate(BulkImportRoute(uri.toString(), zip)) },
                openStats = { nav.navigate(StatsRoute) },
                openSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<TopicRoute> { entry ->
            TopicScreen(
                topicId = entry.toRoute<TopicRoute>().topicId,
                back = back,
                openDeck = { nav.navigate(DeckRoute(it)) },
                study = { nav.navigate(it) },
                importCsv = { uri, topicId -> nav.navigate(ImportCsvRoute(uri.toString(), topicId = topicId)) },
                openCombo = { ids -> nav.navigate(ComboRoute(preselectDeckIds = ids.joinToString(","))) },
            )
        }
        composable<DeckRoute> { entry ->
            DeckScreen(
                deckId = entry.toRoute<DeckRoute>().deckId,
                back = back,
                study = { nav.navigate(it) },
                reimport = { uri, deckId -> nav.navigate(ImportCsvRoute(uri.toString(), deckId = deckId)) },
            )
        }
        composable<StudyRoute> { entry ->
            StudyScreen(route = entry.toRoute(), back = back)
        }
        composable<ComboRoute> { entry ->
            ComboScreen(route = entry.toRoute(), back = back, study = { nav.navigate(it) })
        }
        composable<ImportCsvRoute> { entry ->
            ImportCsvScreen(route = entry.toRoute(), back = back, openDeck = { deckId ->
                nav.navigate(DeckRoute(deckId)) { popUpTo<ImportCsvRoute> { inclusive = true } }
            })
        }
        composable<BulkImportRoute> { entry ->
            BulkImportScreen(route = entry.toRoute(), back = back)
        }
        composable<StatsRoute> { StatsScreen(back = back) }
        composable<SettingsRoute> { SettingsScreen(back = back) }
    }
}
